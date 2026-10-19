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

private sealed interface Load {
    object Loading : Load
    object Missing : Load
    class Ready(val session: Session) : Load
}

/** 時間軸上的一列：一箭或一次手動加減。 */
private class TimelineRow(
    val relMs: Long,
    val shotNo: Int,      // 第幾箭；手動加減為 0
    val shot: ShotEntry?, // 手動加減為 null
    val delta: Int,
)

private fun buildTimeline(s: Session): List<TimelineRow> {
    val rows = ArrayList<TimelineRow>(s.shots.size + s.manual.size)
    s.shots.forEachIndexed { i, sh -> rows.add(TimelineRow(sh.relMs, i + 1, sh, 0)) }
    s.manual.forEach { m -> rows.add(TimelineRow(m.relMs, 0, null, m.delta)) }
    rows.sortBy { it.relMs }
    return rows
}

@Composable
fun DetailScreen(vm: CounterViewModel, onDeleted: () -> Unit) {
    val id by vm.selectedId.collectAsStateWithLifecycle()
    val activeId by vm.activeSessionId.collectAsStateWithLifecycle()

    val load by produceState<Load>(initialValue = Load.Loading, key1 = id) {
        val sid = id
        val session = if (sid == null) null else vm.loadSession(sid)
        value = if (session == null) Load.Missing else Load.Ready(session)
    }

    when (val l = load) {
        Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("載入中…")
        }
        Load.Missing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("找不到這筆記錄")
        }
        is Load.Ready -> DetailContent(
            session = l.session,
            isActive = l.session.id == activeId,
            onDelete = {
                vm.deleteSession(l.session.id)
                onDeleted()
            },
        )
    }
}

@Composable
private fun DetailContent(session: Session, isActive: Boolean, onDelete: () -> Unit) {
    val stats = remember(session) { computeStats(session) }
    val timeline = remember(session) { buildTimeline(session) }
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

        item { SectionTitle("逐箭明細") }
        if (timeline.isEmpty()) {
            item { Note("這次沒有偵測到放箭") }
        }
        timeline.forEach { row ->
            item { Body(rowText(session, row)) }
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

private fun rowText(s: Session, row: TimelineRow): String {
    val time = Fmt.clock(s.startMs + row.relMs)
    val shot = row.shot
    if (shot == null) {
        val sign = if (row.delta > 0) "+" else "−"
        return "$time 手動 $sign${kotlin.math.abs(row.delta)}"
    }
    return buildString {
        append("#${row.shotNo}  $time")
        append("\n峰值 ${Fmt.num(shot.peak, 1)}")
        if (row.shotNo >= 2) {
            val prev = s.shots[row.shotNo - 2]
            append(" · 距上一箭 ${Fmt.num((shot.relMs - prev.relMs) / 1000f, 0)} 秒")
        }
        append("\n加速度晃動 ${Fmt.numOrDash(shot.accelRms)} m/s²")
        if (s.hasGyro) {
            append("\n角速度晃動 ${Fmt.numOrDash(shot.gyroRms, 1)} °/s")
        }
    }
}
