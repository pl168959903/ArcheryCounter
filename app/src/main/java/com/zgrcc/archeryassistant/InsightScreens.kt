package com.zgrcc.archeryassistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import java.time.LocalDate

/** 條件分組對比（v1.14）：依距離＋弓種分組，看各組的平均晃動與環數。 */
@Composable
fun GroupCompareScreen(vm: CounterViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val rows = remember(sessions) { GroupCompare.groups(sessions) }
    val best = remember(rows) { GroupCompare.steadiest(rows) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text(tr("條件對比", "Compare conditions"), style = MaterialTheme.typography.title3) }
        if (rows.isEmpty()) {
            item { Note(tr("還沒有可比較的記錄。記錄時帶上距離、弓種標籤（設定 › 記錄 › 條件標籤），這裡就會分組", "Nothing to compare yet. Record with distance and bow tags (Settings › Records › Condition tags) and they are grouped here")) }
        } else {
            item { Body(tr("依距離和弓種分組（比賽標籤不分組）。晃動越低越穩", "Grouped by distance and bow (the match tag is ignored). Lower shake is steadier")) }
            rows.forEach { g ->
                item {
                    val star = if (best != null && g.tags == best.tags) "★ " else ""
                    Chip(
                        onClick = {},
                        label = { Text(star + GroupCompare.title(g.tags), maxLines = 2) },
                        secondaryLabel = {
                            Text(
                                buildString {
                                    append(tr("${g.sessions} 次 · ${g.arrows} 箭", "${g.sessions} sessions · ${g.arrows} arrows"))
                                    append(tr("\n平均晃動 ${Fmt.numOrDash(g.accelAvg, 2)}", "\nAvg shake ${Fmt.numOrDash(g.accelAvg, 2)}"))
                                    if (g.scoreAvg != null) append(tr(" · 平均環數 ${Fmt.num(g.scoreAvg, 1)}", " · Avg score ${Fmt.num(g.scoreAvg, 1)}"))
                                },
                                maxLines = 3,
                            )
                        },
                        colors = kindColors(ChipKind.LIST, ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
            item {
                Note(
                    if (best != null) tr("★ 是最穩的一組。至少 2 組、每組 ${GroupCompare.MIN_ARROWS_FOR_BEST} 箭以上才標示", "★ marks the steadiest group; shown only with 2+ groups of ${GroupCompare.MIN_ARROWS_FOR_BEST}+ arrows")
                    else tr("至少 2 組、每組 ${GroupCompare.MIN_ARROWS_FOR_BEST} 箭以上才會標出最穩的一組", "The steadiest group is marked only with 2+ groups of ${GroupCompare.MIN_ARROWS_FOR_BEST}+ arrows")
                )
            }
        }
    }
}

/** 每週／每月訓練總覽（v1.14）。校準記錄不計入。 */
@Composable
fun OverviewScreen(vm: CounterViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    var monthly by remember { mutableStateOf(false) }
    val now = remember { System.currentTimeMillis() }
    val weeks = remember(sessions) { Overview.weeks(sessions, now) }
    val months = remember(sessions) { Overview.months(sessions, now) }
    val streak = remember(sessions) { Overview.streak(sessions, now) }
    val delta = remember(weeks) { Overview.weekDelta(weeks) }
    val bars = if (monthly) months else weeks
    val total = bars.sumOf { it.arrows }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text(tr("訓練總覽", "Training overview"), style = MaterialTheme.typography.title3) }
        item {
            Chip(
                onClick = { monthly = !monthly },
                label = { Text(if (monthly) tr("每月（最近 ${Overview.MONTHS} 個月）", "Monthly (last ${Overview.MONTHS})") else tr("每週（最近 ${Overview.WEEKS} 週）", "Weekly (last ${Overview.WEEKS})"), maxLines = 1) },
                secondaryLabel = { Text(tr("點一下切換", "Tap to switch"), maxLines = 1) },
                colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        if (total == 0) {
            item { Note(tr("這段期間沒有射箭記錄", "No shooting in this period")) }
        } else {
            item { BarChart(bars) }
            item {
                Body(
                    buildString {
                        append(tr("共 $total 箭 · 練習 ${bars.sumOf { it.days }} 天", "$total arrows · ${bars.sumOf { it.days }} practice days"))
                        if (!monthly && delta != null) {
                            append("\n")
                            append(
                                if (delta == 0) tr("本週和上週一樣多", "This week equals last week")
                                else tr("本週比上週${if (delta > 0) "多" else "少"} ${kotlin.math.abs(delta)} 箭", "This week is ${kotlin.math.abs(delta)} arrows ${if (delta > 0) "more" else "fewer"} than last week")
                            )
                        }
                        append("\n")
                        append(tr("連續練習 $streak 天", "Streak: $streak days"))
                    }
                )
            }
            bars.reversed().forEach { b ->
                item {
                    Body(barLabel(b, monthly) + "　" + tr("${b.arrows} 箭 · ${b.days} 天", "${b.arrows} arrows · ${b.days} d"))
                }
            }
        }
        item { Note(tr("週從週一算起；校準記錄不計入", "Weeks start on Monday; calibration records are not counted")) }
    }
}

private fun barLabel(b: PeriodBar, monthly: Boolean): String {
    val d: LocalDate = b.start
    return if (monthly) "${d.year}/${d.monthValue}" else "${d.monthValue}/${d.dayOfMonth}"
}

@Composable
private fun BarChart(bars: List<PeriodBar>) {
    val barColor = MaterialTheme.colors.primary
    val gridColor = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.35f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(0.88f)) {
        val max = (bars.maxOfOrNull { it.arrows } ?: 0).coerceAtLeast(1)
        Text(tr("每期箭數（最多 $max）", "Arrows per period (max $max)"), style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center)
        Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
            val pad = 6.dp.toPx()
            val w = size.width - 2 * pad
            val h = size.height - 2 * pad
            drawLine(gridColor, Offset(pad, pad + h), Offset(pad + w, pad + h), strokeWidth = 1.dp.toPx())
            val n = bars.size.coerceAtLeast(1)
            val slot = w / n
            val bw = slot * 0.6f
            bars.forEachIndexed { i, b ->
                val bh = h * b.arrows / max
                drawRect(barColor, Offset(pad + slot * i + (slot - bw) / 2, pad + h - bh), Size(bw, bh))
            }
        }
        Text(
            "${barLabel(bars.first(), bars.size == Overview.MONTHS)} → ${barLabel(bars.last(), bars.size == Overview.MONTHS)}",
            style = MaterialTheme.typography.caption2,
            textAlign = TextAlign.Center,
        )
    }
}
