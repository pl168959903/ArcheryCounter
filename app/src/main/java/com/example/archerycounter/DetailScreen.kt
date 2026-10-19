package com.example.archerycounter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/** 從存檔讀記錄的結果。詳情頁和單箭頁共用。 */
internal sealed interface SessionLoad {
    object Loading : SessionLoad
    object Missing : SessionLoad
    class Ready(val session: Session) : SessionLoad
}

/** 時間軸上的一列：一箭（有效或誤判）或一次手動加減。 */
private class TimelineRow(
    val relMs: Long,
    val shotNo: Int,          // 有效的第幾箭；誤判或手動加減為 0
    val shotIndex: Int,       // 在 Session.shots 裡的位置；手動加減為 -1
    val shot: ShotEntry?,     // 手動加減為 null
    val intervalSec: Float?,  // 距上一個有效的箭的秒數
    val delta: Int,
)

private fun buildTimeline(s: Session): List<TimelineRow> {
    val rows = ArrayList<TimelineRow>(s.shots.size + s.manual.size)
    var validCount = 0
    var prevValidRel: Long? = null
    s.shots.forEachIndexed { index, sh ->
        if (sh.cancelled) {
            rows.add(TimelineRow(sh.relMs, 0, index, sh, null, 0))
        } else {
            validCount++
            val interval = prevValidRel?.let { (sh.relMs - it) / 1000f }
            rows.add(TimelineRow(sh.relMs, validCount, index, sh, interval, 0))
            prevValidRel = sh.relMs
        }
    }
    s.manual.forEach { m -> rows.add(TimelineRow(m.relMs, 0, -1, null, null, m.delta)) }
    rows.sortBy { it.relMs }
    return rows
}

@Composable
fun DetailScreen(vm: CounterViewModel, onOpenShot: (Int) -> Unit, onDeleted: () -> Unit) {
    val id by vm.selectedId.collectAsStateWithLifecycle()
    val activeId by vm.activeSessionId.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()

    // 記錄列表有變動（例如在單箭頁標記了誤判）就重新讀一次
    val load by produceState<SessionLoad>(
        initialValue = SessionLoad.Loading,
        key1 = id,
        key2 = sessions,
    ) {
        val sid = id
        val session = if (sid == null) null else vm.loadSession(sid)
        value = if (session == null) SessionLoad.Missing else SessionLoad.Ready(session)
    }

    when (val l = load) {
        SessionLoad.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("載入中…")
        }
        SessionLoad.Missing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("找不到這筆記錄")
        }
        is SessionLoad.Ready -> DetailContent(
            vm = vm,
            session = l.session,
            isActive = l.session.id == activeId,
            onOpenShot = onOpenShot,
            onDelete = {
                vm.deleteSession(l.session.id)
                onDeleted()
            },
        )
    }
}

@Composable
private fun DetailContent(
    vm: CounterViewModel,
    session: Session,
    isActive: Boolean,
    onOpenShot: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val showTrend by vm.settings.showTrendChart.collectAsStateWithLifecycle()
    val showEnds by vm.settings.showEnds.collectAsStateWithLifecycle()
    val arrowsPerEnd by vm.settings.arrowsPerEnd.collectAsStateWithLifecycle()
    val stats = remember(session) { computeStats(session) }
    val timeline = remember(session) { buildTimeline(session) }
    val ends = remember(session, arrowsPerEnd) { computeEnds(session, arrowsPerEnd) }
    val accelSeries = remember(session) { metricSeries(session, gyro = false) }
    val gyroSeries = remember(session) { metricSeries(session, gyro = true) }
    var confirmDelete by remember { mutableStateOf(false) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(Fmt.dateTime(session.startMs), style = MaterialTheme.typography.title3)
        }
        item { Body(summaryText(session, stats, isActive)) }

        item { SectionTitle("統計") }
        item { Body(statsText(session, stats)) }

        if (showTrend) {
            item { SectionTitle("趨勢圖") }
            if (accelSeries.size >= 2) {
                item {
                    TrendChart(
                        title = "加速度晃動量",
                        unit = "m/s²",
                        series = accelSeries,
                        steadiest = stats.accelSteadiest,
                        shakiest = stats.accelShakiest,
                        decimals = 2,
                    )
                }
            }
            if (session.hasGyro && gyroSeries.size >= 2) {
                item {
                    TrendChart(
                        title = "角速度晃動量",
                        unit = "°/s",
                        series = gyroSeries,
                        steadiest = stats.gyroSteadiest,
                        shakiest = stats.gyroShakiest,
                        decimals = 1,
                    )
                }
            }
            if (accelSeries.size < 2 && gyroSeries.size < 2) {
                item { Note("至少要 2 箭有晃動量數據才畫得出趨勢圖") }
            }
        }

        if (showEnds && ends.isNotEmpty()) {
            item { SectionTitle("逐趟（每趟 $arrowsPerEnd 箭）") }
            ends.forEach { e -> item { Body(endText(session, e)) } }
        }

        item { SectionTitle("逐箭明細") }
        if (timeline.isEmpty()) {
            item { Note("這次沒有偵測到放箭") }
        } else {
            item { Note("點一箭可看波形，也可以標記為誤判") }
        }
        timeline.forEach { row ->
            item {
                val shot = row.shot
                if (shot == null) {
                    Body(manualText(session, row))
                } else {
                    Chip(
                        onClick = { onOpenShot(row.shotIndex) },
                        label = { Text(shotText(session, row, shot), style = MaterialTheme.typography.caption1, maxLines = 5) },
                        colors = if (shot.cancelled) ChipDefaults.childChipColors() else ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.95f),
                    )
                }
            }
        }

        if (session.rawCsv) {
            item { Note("原始資料在手錶上的 CSV 檔，用 adb 取出，檔名：${session.id}.csv") }
        }

        // 刪除放在最底，並且要再確認一次
        item {
            when {
                isActive -> Note("進行中，暫停偵測後才能刪除")
                !confirmDelete -> Chip(
                    onClick = { confirmDelete = true },
                    label = { Text("刪除此記錄", maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
                else -> Note("確定要刪除這筆記錄嗎？無法復原")
            }
        }
        if (confirmDelete && !isActive) {
            item {
                Chip(
                    onClick = onDelete,
                    label = { Text("確定刪除", maxLines = 1) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Chip(
                    onClick = { confirmDelete = false },
                    label = { Text("取消", maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
    }
}

private fun summaryText(s: Session, st: SessionStats, isActive: Boolean): String = buildString {
    appendLine("開始 ${Fmt.clock(s.startMs)}")
    val end = s.endMs
    when {
        end != null -> appendLine("結束 ${Fmt.clock(end)}")
        isActive -> appendLine("進行中")
        else -> appendLine("結束 — （中斷）")
    }
    appendLine("時長 ${Fmt.duration(s.durationMs())}")
    appendLine("偵測 ${st.detected} 箭")
    if (st.cancelled > 0) appendLine("誤判 ${st.cancelled} 筆（已排除）")
    appendLine("手動 +${st.manualPlus} / −${st.manualMinus}")
    appendLine("最終箭數 ${st.finalCount}")
    val thr = if (st.thresholdMin == st.thresholdMax) {
        Fmt.num(st.thresholdMin, 0)
    } else {
        "${Fmt.num(st.thresholdMin, 0)}～${Fmt.num(st.thresholdMax, 0)}"
    }
    appendLine("閾值 $thr")
    append("穩定度視窗 ${s.windowSec} 秒")
}

private fun statsText(s: Session, st: SessionStats): String = buildString {
    if (st.detected == 0) {
        append("沒有資料")
        return@buildString
    }
    appendLine("峰值 平均 ${Fmt.numOrDash(st.peakAvg, 1)}")
    appendLine("最大 ${Fmt.numOrDash(st.peakMax, 1)} · 最小 ${Fmt.numOrDash(st.peakMin, 1)}")
    append("平均間隔 ")
    append(if (st.intervalAvgSec == null) "—" else "${Fmt.num(st.intervalAvgSec, 0)} 秒")

    if (st.followAvg != null || st.followGyroAvg != null) {
        append("\n\n放箭後晃動（0.3～1 秒）")
        if (st.followAvg != null) append("\n加速度 平均 ${Fmt.num(st.followAvg)} m/s²")
        if (s.hasGyro && st.followGyroAvg != null) append("\n角速度 平均 ${Fmt.num(st.followGyroAvg, 1)} °/s")
    }
    if (st.scoredCount > 0) {
        append("\n\n環數 平均 ${Fmt.numOrDash(st.scoreAvg, 1)}（已輸入 ${st.scoredCount} 箭）")
        if (st.scoreAccelCorr != null) {
            append("\n晃動量與環數的相關係數 ${Fmt.num(st.scoreAccelCorr, 2)}")
            append("\n（−1～1；負值表示晃動越大、環數越低。箭數不多，僅供參考）")
        }
    }

    append("\n\n加速度晃動（m/s²）")
    appendStability(st.accelAvg, st.accelSteadiest, st.accelShakiest, st.accelTrend, 2)

    append("\n\n角速度晃動（°/s）")
    if (!s.hasGyro) {
        append("\n此手錶沒有陀螺儀")
    } else {
        appendStability(st.gyroAvg, st.gyroSteadiest, st.gyroShakiest, st.gyroTrend, 1)
    }
}

private fun StringBuilder.appendStability(
    avg: Float?,
    steadiest: ShotRank?,
    shakiest: ShotRank?,
    trend: Trend?,
    decimals: Int,
) {
    if (avg == null) {
        append("\n沒有足夠資料")
        return
    }
    append("\n平均 ${Fmt.num(avg, decimals)}")
    if (steadiest != null) append("\n最穩 第 ${steadiest.number} 箭 ${Fmt.num(steadiest.value, decimals)}")
    if (shakiest != null) append("\n最不穩 第 ${shakiest.number} 箭 ${Fmt.num(shakiest.value, decimals)}")
    if (trend != null) {
        append("\n前 1/3 ${Fmt.num(trend.first, decimals)} → 後 1/3 ${Fmt.num(trend.last, decimals)}")
    }
}

private fun endText(s: Session, e: EndStats): String = buildString {
    append("第 ${e.number} 趟　第 ${e.firstShot}～${e.lastShot} 箭（${e.count} 箭）")
    append("\n${Fmt.clock(s.startMs + e.startRelMs)} ～ ${Fmt.clock(s.startMs + e.endRelMs)}")
    append("\n加速度晃動 ${Fmt.numOrDash(e.accelAvg)} m/s²")
    if (s.hasGyro) append("\n角速度晃動 ${Fmt.numOrDash(e.gyroAvg, 1)} °/s")
    if (e.followAvg != null) append("\n放箭後晃動 ${Fmt.num(e.followAvg)} m/s²")
    if (e.scoreTotal != null) append("\n環數合計 ${e.scoreTotal}")
}

private fun manualText(s: Session, row: TimelineRow): String {
    val sign = if (row.delta > 0) "+" else "−"
    return "${Fmt.clock(s.startMs + row.relMs)} 手動 $sign${kotlin.math.abs(row.delta)}"
}

private fun shotText(s: Session, row: TimelineRow, shot: ShotEntry): String {
    val time = Fmt.clock(s.startMs + row.relMs)
    if (shot.cancelled) {
        return "$time 已取消（誤判）\n峰值 ${Fmt.num(shot.peak, 1)} m/s²"
    }
    return buildString {
        append("#${row.shotNo}  $time")
        append("\n峰值 ${Fmt.num(shot.peak, 1)}")
        row.intervalSec?.let { append(" · 距上一箭 ${Fmt.num(it, 0)} 秒") }
        append("\n加速度晃動 ${Fmt.numOrDash(shot.accelRms)} m/s²")
        if (s.hasGyro) {
            append("\n角速度晃動 ${Fmt.numOrDash(shot.gyroRms, 1)} °/s")
        }
        if (shot.followAccelRms != null) append("\n放箭後晃動 ${Fmt.num(shot.followAccelRms)} m/s²")
        if (shot.score != null) append("\n環數 ${shot.score}")
    }
}
