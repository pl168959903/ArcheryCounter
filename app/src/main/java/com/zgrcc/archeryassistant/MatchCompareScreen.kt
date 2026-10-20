package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

/** 跨場比賽對比（v1.11）：把比賽紀錄放在一起比。 */
@Composable
fun MatchCompareScreen(vm: CounterViewModel, onOpen: (String) -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    var sameOnly by remember { mutableStateOf(false) }

    val all = remember(sessions) { MatchCompare.rows(sessions) }
    val rows = remember(all, sameOnly) { if (sameOnly) MatchCompare.sameAsLatest(all) else all }
    val shake = remember(rows) { MatchCompare.shakeSeries(rows) }
    val score = remember(rows) { MatchCompare.scoreSeries(rows) }
    val delta = remember(rows) { MatchCompare.scoreDelta(rows) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text(tr("比賽對比", "Match comparison"), style = MaterialTheme.typography.title3) }

        if (all.isEmpty()) {
            item { Note(tr("還沒有完成的比賽紀錄。在「比賽模式」打完一場後，這裡就能比較", "No finished matches yet. Complete a match in Match mode and it will show up here")) }
        } else {
            item {
                Chip(
                    onClick = { sameOnly = !sameOnly },
                    label = { Text(tr("只看相同賽制", "Same format only"), maxLines = 1) },
                    secondaryLabel = {
                        Text(
                            if (sameOnly) tr("開：和最新一場同賽制", "On: same as the latest match") else tr("關：全部比賽", "Off: all matches"),
                            maxLines = 2,
                        )
                    },
                    colors = if (sameOnly) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            if (delta != null) {
                item {
                    Body(
                        tr(
                            "最新一場總環數比前一場${if (delta >= 0) "多" else "少"} ${kotlin.math.abs(delta)}",
                            "Latest total is ${kotlin.math.abs(delta)} ${if (delta >= 0) "higher" else "lower"} than the previous match",
                        )
                    )
                }
            }
            if (score.size >= 2) {
                item {
                    TrendChart(
                        title = tr("總環數", "Total score"),
                        unit = tr("環", "pts"),
                        series = score.mapIndexed { i, p -> ShotRank(i + 1, p.value) },
                        steadiest = null,
                        shakiest = null,
                        decimals = 0,
                        noun = tr("場", "match"),
                        rangeText = "${Fmt.dateTime(score.first().startMs)} → ${Fmt.dateTime(score.last().startMs)}",
                    )
                }
            }
            if (shake.size >= 2) {
                item {
                    val (steadiest, shakiest) = extremes(shake.map { it.value })
                    TrendChart(
                        title = tr("平均晃動", "Average shake"),
                        unit = "m/s²",
                        series = shake.mapIndexed { i, p -> ShotRank(i + 1, p.value) },
                        steadiest = steadiest,
                        shakiest = shakiest,
                        decimals = 2,
                        noun = tr("場", "match"),
                        rangeText = "${Fmt.dateTime(shake.first().startMs)} → ${Fmt.dateTime(shake.last().startMs)}",
                    )
                }
            }
            if (score.size < 2 && shake.size < 2) {
                item { Note(tr("至少要 2 場有環數或晃動量數據才畫得出趨勢圖", "At least 2 matches with scores or shake data are needed for a trend chart")) }
            }

            item { SectionTitle(tr("每場比賽（${rows.size}）", "Matches (${rows.size})")) }
            // 新的在上面
            rows.reversed().forEach { r ->
                item {
                    Chip(
                        onClick = { onOpen(r.id) },
                        label = { Text(Fmt.dateTime(r.startMs) + navMark(), maxLines = 1) },
                        secondaryLabel = {
                            Text(
                                buildString {
                                    append(tr("${r.arrowsPerEnd} 箭×${r.ends} 趟 · ${r.secondsPerEnd} 秒", "${r.arrowsPerEnd} arrows × ${r.ends} ends · ${r.secondsPerEnd} s"))
                                    append("\n")
                                    append(if (r.scoreTotal != null) tr("總環數 ${r.scoreTotal}", "Total ${r.scoreTotal}") else tr("總環數 —", "Total —"))
                                    append(tr(" · 晃動 ${Fmt.numOrDash(r.accelAvg, 2)}", " · Shake ${Fmt.numOrDash(r.accelAvg, 2)}"))
                                    append("\n")
                                    append(tr("共 ${r.totalArrows} 箭 · 時間到 ${r.timedOutEnds} 趟", "${r.totalArrows} arrows · ${r.timedOutEnds} timed out"))
                                },
                                maxLines = 4,
                            )
                        },
                        colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
            item { Note(tr("晃動越低越穩。賽制、天氣、當天狀況不同，數字也會不同，建議開「只看相同賽制」比較", "Lower shake is steadier. Format, weather and form change the numbers, so use Same format only for a fair comparison")) }
        }
    }
}
