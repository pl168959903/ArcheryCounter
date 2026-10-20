package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

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
            Text(tr("載入中…", "Loading…"))
        }
        SessionLoad.Missing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("找不到這筆記錄", "Record not found"))
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
    val tagsEnabled by vm.settings.tagsEnabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(Fmt.dateTime(session.startMs), style = MaterialTheme.typography.title3)
        }
        if (session.calibration) {
            item { Body(tr("校準錄製（不計入箭數與趨勢）", "Calibration (not counted in arrows or trends)")) }
            if (session.marks.isNotEmpty()) {
                item {
                    Body(
                        session.marks.joinToString("\n") { m ->
                            val end = m.endRelMs
                            "${Calibration.display(m.label)}　${Fmt.duration(m.startRelMs)}～" + if (end == null) tr("（未結束）", "(not finished)") else Fmt.duration(end)
                        }
                    )
                }
            }
        }
        item { Body(summaryText(session, stats, isActive)) }

        // 比賽模式（v1.9）：逐趟用時、逾時、箭數與晃動變化
        if (!session.calibration && session.comp != null) {
            val compSum = CompSummaryBuilder.build(session)
            if (compSum != null) {
                item { Text(tr("比賽統計", "Match summary"), style = MaterialTheme.typography.title3) }
                item { Body(compSummaryText(compSum, session.comp)) }
            }
        }

        // 條件標籤：記錄時自動帶上，這裡可以事後修改（進行中的不能改）
        if (!session.calibration && (tagsEnabled || session.tags.isNotEmpty())) {
            item { SectionTitle(tr("條件", "Conditions")) }
            if (isActive) {
                item { Body(Tags.label(session.tags) ?: tr("未設定", "Not set")) }
            } else {
                item {
                    Chip(
                        onClick = {
                            val tags = Tags.rebuild(session.tags, Tags.nextDistance(Tags.distanceOf(session.tags)), Tags.bowOf(session.tags))
                            scope.launch { vm.setSessionTags(session.id, tags) }
                        },
                        label = { Text(tr("距離", "Distance"), maxLines = 1) },
                        secondaryLabel = { Text(Tags.distanceOf(session.tags)?.let { Tags.display(it) } ?: tr("未設定", "Not set"), maxLines = 1) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item {
                    Chip(
                        onClick = {
                            val tags = Tags.rebuild(session.tags, Tags.distanceOf(session.tags), Tags.nextBow(Tags.bowOf(session.tags)))
                            scope.launch { vm.setSessionTags(session.id, tags) }
                        },
                        label = { Text(tr("弓種", "Bow"), maxLines = 1) },
                        secondaryLabel = { Text(Tags.bowOf(session.tags)?.let { Tags.display(it) } ?: tr("未設定", "Not set"), maxLines = 1) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item { Note(tr("點一下切換選項", "Tap to change")) }
            }
        }

        item { SectionTitle(tr("統計", "Statistics")) }
        item { Body(statsText(session, stats)) }

        if (showTrend) {
            item { SectionTitle(tr("趨勢圖", "Trend charts")) }
            if (accelSeries.size >= 2) {
                item {
                    TrendChart(
                        title = tr("加速度晃動量", "Accel shake"),
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
                        title = tr("角速度晃動量", "Gyro shake"),
                        unit = "°/s",
                        series = gyroSeries,
                        steadiest = stats.gyroSteadiest,
                        shakiest = stats.gyroShakiest,
                        decimals = 1,
                    )
                }
            }
            if (accelSeries.size < 2 && gyroSeries.size < 2) {
                item { Note(tr("至少要 2 箭有晃動量數據才畫得出趨勢圖", "At least 2 shots with shake data are needed for a trend chart")) }
            }
        }

        if (showEnds && ends.isNotEmpty()) {
            item { SectionTitle(tr("逐趟（每趟 $arrowsPerEnd 箭）", "Per end ($arrowsPerEnd arrows each)")) }
            ends.forEach { e -> item { Body(endText(session, e)) } }
        }

        item { SectionTitle(tr("逐箭明細", "Shot by shot")) }
        if (timeline.isEmpty()) {
            item { Note(tr("這次沒有偵測到放箭", "No release detected this time")) }
        } else {
            item { Note(tr("點一箭可看波形，也可以標記為誤判", "Tap a shot to see its waveform or mark it as a false detection")) }
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
            item { Note(tr("原始資料在手錶上的 CSV 檔，用 adb 取出，檔名：${session.id}.csv", "Raw data is a CSV on the watch; pull it with adb. File: ${session.id}.csv")) }
        }

        // 刪除放在最底，並且要再確認一次
        item {
            when {
                isActive -> Note(tr("進行中，暫停偵測後才能刪除", "In progress; pause detection before deleting"))
                !confirmDelete -> Chip(
                    onClick = { confirmDelete = true },
                    label = { Text(tr("刪除此記錄", "Delete this record"), maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
                else -> Note(tr("確定要刪除這筆記錄嗎？無法復原", "Delete this record? This cannot be undone"))
            }
        }
        if (confirmDelete && !isActive) {
            item {
                Chip(
                    onClick = onDelete,
                    label = { Text(tr("確定刪除", "Delete"), maxLines = 1) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Chip(
                    onClick = { confirmDelete = false },
                    label = { Text(tr("取消", "Cancel"), maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
    }
}

private fun summaryText(s: Session, st: SessionStats, isActive: Boolean): String = buildString {
    appendLine(tr("開始 ${Fmt.clock(s.startMs)}", "Start ${Fmt.clock(s.startMs)}"))
    val end = s.endMs
    when {
        end != null -> appendLine(tr("結束 ${Fmt.clock(end)}", "End ${Fmt.clock(end)}"))
        isActive -> appendLine(tr("進行中", "In progress"))
        else -> appendLine(tr("結束 — （中斷）", "End — (interrupted)"))
    }
    appendLine(tr("時長 ${Fmt.duration(s.durationMs())}", "Duration ${Fmt.duration(s.durationMs())}"))
    appendLine(tr("偵測 ${st.detected} 箭", "Detected ${st.detected} arrows"))
    if (st.cancelled > 0) appendLine(tr("誤判 ${st.cancelled} 筆（已排除）", "False detections ${st.cancelled} (excluded)"))
    appendLine(tr("手動 +${st.manualPlus} / −${st.manualMinus}", "Manual +${st.manualPlus} / −${st.manualMinus}"))
    appendLine(tr("最終箭數 ${st.finalCount}", "Final count ${st.finalCount}"))
    val thr = if (st.thresholdMin == st.thresholdMax) {
        Fmt.num(st.thresholdMin, 0)
    } else {
        "${Fmt.num(st.thresholdMin, 0)}～${Fmt.num(st.thresholdMax, 0)}"
    }
    appendLine(tr("閾值 $thr", "Threshold $thr"))
    append(tr("穩定度視窗 ${s.windowSec} 秒", "Stability window ${s.windowSec} s"))
}

private fun statsText(s: Session, st: SessionStats): String = buildString {
    if (st.detected == 0) {
        append(tr("沒有資料", "No data"))
        return@buildString
    }
    appendLine(tr("峰值 平均 ${Fmt.numOrDash(st.peakAvg, 1)}", "Peak avg ${Fmt.numOrDash(st.peakAvg, 1)}"))
    appendLine(tr("最大 ${Fmt.numOrDash(st.peakMax, 1)} · 最小 ${Fmt.numOrDash(st.peakMin, 1)}", "Max ${Fmt.numOrDash(st.peakMax, 1)} · Min ${Fmt.numOrDash(st.peakMin, 1)}"))
    append(tr("平均間隔 ", "Avg interval "))
    append(if (st.intervalAvgSec == null) "—" else tr("${Fmt.num(st.intervalAvgSec, 0)} 秒", "${Fmt.num(st.intervalAvgSec, 0)} s"))

    if (st.followAvg != null || st.followGyroAvg != null) {
        append(tr("\n\n放箭後晃動（0.3～1 秒）", "\n\nShake after release (0.3-1 s)"))
        if (st.followAvg != null) append(tr("\n加速度 平均 ${Fmt.num(st.followAvg)} m/s²", "\nAccel avg ${Fmt.num(st.followAvg)} m/s²"))
        if (s.hasGyro && st.followGyroAvg != null) append(tr("\n角速度 平均 ${Fmt.num(st.followGyroAvg, 1)} °/s", "\nGyro avg ${Fmt.num(st.followGyroAvg, 1)} °/s"))
    }
    if (st.scoredCount > 0) {
        append(tr("\n\n環數 平均 ${Fmt.numOrDash(st.scoreAvg, 1)}（已輸入 ${st.scoredCount} 箭）", "\n\nScore avg ${Fmt.numOrDash(st.scoreAvg, 1)} (${st.scoredCount} arrows scored)"))
        if (st.scoreAccelCorr != null) {
            append(tr("\n晃動量與環數的相關係數 ${Fmt.num(st.scoreAccelCorr, 2)}", "\nCorrelation of shake and score ${Fmt.num(st.scoreAccelCorr, 2)}"))
            append(tr("\n（−1～1；負值表示晃動越大、環數越低。箭數不多，僅供參考）", "\n(-1 to 1; negative means more shake, lower score. Few arrows, for reference only)"))
        }
    }

    append(tr("\n\n加速度晃動（m/s²）", "\n\nAccel shake (m/s²)"))
    appendStability(st.accelAvg, st.accelSteadiest, st.accelShakiest, st.accelTrend, 2)

    append(tr("\n\n角速度晃動（°/s）", "\n\nGyro shake (°/s)"))
    if (!s.hasGyro) {
        append(tr("\n此手錶沒有陀螺儀", "\nThis watch has no gyroscope"))
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
        append(tr("\n沒有足夠資料", "\nNot enough data"))
        return
    }
    append(tr("\n平均 ${Fmt.num(avg, decimals)}", "\nAvg ${Fmt.num(avg, decimals)}"))
    if (steadiest != null) append(tr("\n最穩 第 ${steadiest.number} 箭 ${Fmt.num(steadiest.value, decimals)}", "\nSteadiest: shot ${steadiest.number} ${Fmt.num(steadiest.value, decimals)}"))
    if (shakiest != null) append(tr("\n最不穩 第 ${shakiest.number} 箭 ${Fmt.num(shakiest.value, decimals)}", "\nShakiest: shot ${shakiest.number} ${Fmt.num(shakiest.value, decimals)}"))
    if (trend != null) {
        append(tr("\n前 1/3 ${Fmt.num(trend.first, decimals)} → 後 1/3 ${Fmt.num(trend.last, decimals)}", "\nFirst third ${Fmt.num(trend.first, decimals)} → last third ${Fmt.num(trend.last, decimals)}"))
    }
}

private fun endText(s: Session, e: EndStats): String = buildString {
    append(tr("第 ${e.number} 趟　第 ${e.firstShot}～${e.lastShot} 箭（${e.count} 箭）", "End ${e.number}  shots ${e.firstShot}-${e.lastShot} (${e.count} arrows)"))
    append("\n${Fmt.clock(s.startMs + e.startRelMs)} ～ ${Fmt.clock(s.startMs + e.endRelMs)}")
    append(tr("\n加速度晃動 ${Fmt.numOrDash(e.accelAvg)} m/s²", "\nAccel shake ${Fmt.numOrDash(e.accelAvg)} m/s²"))
    if (s.hasGyro) append(tr("\n角速度晃動 ${Fmt.numOrDash(e.gyroAvg, 1)} °/s", "\nGyro shake ${Fmt.numOrDash(e.gyroAvg, 1)} °/s"))
    if (e.followAvg != null) append(tr("\n放箭後晃動 ${Fmt.num(e.followAvg)} m/s²", "\nShake after release ${Fmt.num(e.followAvg)} m/s²"))
    if (e.scoreTotal != null) append(tr("\n環數合計 ${e.scoreTotal}", "\nScore total ${e.scoreTotal}"))
}

private fun manualText(s: Session, row: TimelineRow): String {
    val sign = if (row.delta > 0) "+" else "−"
    return tr("${Fmt.clock(s.startMs + row.relMs)} 手動 $sign${kotlin.math.abs(row.delta)}", "${Fmt.clock(s.startMs + row.relMs)} manual $sign${kotlin.math.abs(row.delta)}")
}

private fun shotText(s: Session, row: TimelineRow, shot: ShotEntry): String {
    val time = Fmt.clock(s.startMs + row.relMs)
    if (shot.cancelled) {
        return tr("$time 已取消（誤判）\n峰值 ${Fmt.num(shot.peak, 1)} m/s²", "$time cancelled (false detection)\nPeak ${Fmt.num(shot.peak, 1)} m/s²")
    }
    return buildString {
        append("#${row.shotNo}  $time")
        append(tr("\n峰值 ${Fmt.num(shot.peak, 1)}", "\nPeak ${Fmt.num(shot.peak, 1)}"))
        row.intervalSec?.let { append(tr(" · 距上一箭 ${Fmt.num(it, 0)} 秒", " · ${Fmt.num(it, 0)} s since previous")) }
        append(tr("\n加速度晃動 ${Fmt.numOrDash(shot.accelRms)} m/s²", "\nAccel shake ${Fmt.numOrDash(shot.accelRms)} m/s²"))
        if (s.hasGyro) {
            append(tr("\n角速度晃動 ${Fmt.numOrDash(shot.gyroRms, 1)} °/s", "\nGyro shake ${Fmt.numOrDash(shot.gyroRms, 1)} °/s"))
        }
        if (shot.followAccelRms != null) append(tr("\n放箭後晃動 ${Fmt.num(shot.followAccelRms)} m/s²", "\nShake after release ${Fmt.num(shot.followAccelRms)} m/s²"))
        if (shot.score != null) append(tr("\n環數 ${shot.score}", "\nScore ${shot.score}"))
    }
}

/** 比賽統計文字：總箭數、逾時、逐趟用時與晃動；練習趟標 P，不計入總數。 */
private fun compSummaryText(sum: CompSummary, meta: CompMeta): String = buildString {
    append(tr("正式箭數 ${sum.totalArrows}　每趟限時 ${meta.secondsPerEnd} 秒", "Match arrows ${sum.totalArrows}  limit ${meta.secondsPerEnd} s per end"))
    append(tr("\n時間到的趟數 ${sum.timedOutEnds}　逾時箭 ${sum.lateArrows}", "\nEnds timed out ${sum.timedOutEnds}  late arrows ${sum.lateArrows}"))
    sum.scoreTotal?.let { append(tr("\n環數合計 $it", "\nScore total $it")) }
    sum.ends.forEach { e ->
        val name = if (e.practice) tr("練習 ${e.label.drop(1)}", "Practice ${e.label.drop(1)}") else tr("第 ${e.label} 趟", "End ${e.label}")
        append("\n").append(name).append("　").append(e.arrows).append(tr(" 箭 ", " arrows "))
        append(Fmt.duration(e.usedMs))
        if (e.timedOut) append(tr("（時間到）", " (time up)"))
        if (e.lateArrows > 0) append(tr(" 逾時箭 ${e.lateArrows}", " late ${e.lateArrows}"))
        e.accelAvg?.let { append(tr(" 晃動 ", " shake ")).append(Fmt.num(it)) }
        e.score?.let { append(tr(" 環 $it", " score $it")) }
    }
}
