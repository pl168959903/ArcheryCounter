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

/** 跨次趨勢：最近 20 次記錄各自的平均晃動量，由舊到新。 */
@Composable
fun TrendScreen(vm: CounterViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    // 以最新一次（非校準）記錄的條件標籤為準，可切換成只看相同條件
    val latestTags = remember(sessions) {
        sessions.filter { !it.calibration }.maxByOrNull { it.startMs }?.tags.orEmpty()
    }
    var sameOnly by remember { mutableStateOf(false) }
    val filter = if (sameOnly && latestTags.isNotEmpty()) latestTags else null

    val extraOn by vm.settings.trendExtra.collectAsStateWithLifecycle()
    val posOn by vm.settings.positionTrend.collectAsStateWithLifecycle()
    val pace = remember(sessions, filter, extraOn) { if (extraOn) SessionExtras.paceTrend(sessions, tags = filter) else emptyList() }
    val follow = remember(sessions, filter, extraOn) { if (extraOn) SessionExtras.followTrend(sessions, tags = filter) else emptyList() }
    val posRows = remember(sessions, filter, posOn) {
        if (posOn) PositionTrend.rows(sessions.filter { filter == null || Tags.same(it.tags, filter) }) else emptyList()
    }
    val accel = remember(sessions, filter) { sessionTrend(sessions, gyro = false, tags = filter) }
    val gyro = remember(sessions, filter) { sessionTrend(sessions, gyro = true, tags = filter) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text(tr("跨次趨勢", "Trend across sessions"), style = MaterialTheme.typography.title3) }
        item { Note(tr("每個點是一次記錄的平均晃動量，越低越穩。由舊到新，最多 20 次。校準錄製不算", "Each point is one session's average shake; lower is steadier. Oldest to newest, up to 20. Calibration is excluded")) }

        if (latestTags.isNotEmpty()) {
            item {
                Chip(
                    onClick = { sameOnly = !sameOnly },
                    label = { Text(tr("只看相同條件", "Same conditions only"), maxLines = 1) },
                    secondaryLabel = { Text(if (sameOnly) tr("開：${Tags.label(latestTags)}", "On: ${Tags.label(latestTags)}") else tr("關：全部記錄", "Off: all records"), maxLines = 2) },
                    colors = if (sameOnly) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }

        if (accel.size >= 2) {
            item { TrendBlock(tr("平均加速度晃動量", "Average accel shake"), "m/s²", accel, decimals = 2) }
        }
        if (gyro.size >= 2) {
            item { TrendBlock(tr("平均角速度晃動量", "Average gyro shake"), "°/s", gyro, decimals = 1) }
        }
        if (follow.size >= 2) {
            item { TrendBlock(tr("平均余姿晃動量", "Average follow-through shake"), "m/s²", follow, decimals = 2, rank = false) }
        }
        if (pace.size >= 2) {
            item { TrendBlock(tr("每箭節奏（趟內間隔中位數）", "Pace (median gap within ends)"), tr("秒", "s"), pace, decimals = 1, rank = false) }
        }
        if (posRows.isNotEmpty()) {
            item { SectionTitle(tr("箭序趨勢（全部記錄）", "Shot-order trend (all records)")) }
            item { Note(PositionTrend.explain()) }
            PositionTrend.hint(posRows)?.let { h -> item { Note(h) } }
            posRows.forEach { r -> item { Body(PositionTrend.rowText(r, hasGyro = true)) } }
        }
        if (accel.size < 2 && gyro.size < 2) {
            item { Note(tr("至少要 2 次記錄有晃動量數據才畫得出趨勢圖", "At least 2 sessions with shake data are needed for a trend chart")) }
        }
        item {
            Note(tr("跨次比較要看條件是否一致：視窗秒數、閾值、當天狀況不同，數字也會不同", "Compare only under the same conditions: window length, threshold and the day's form change the numbers"))
        }
    }
}

@Composable
private fun TrendBlock(title: String, unit: String, points: List<SessionPoint>, decimals: Int, rank: Boolean = true) {
    val series = points.mapIndexed { i, p -> ShotRank(i + 1, p.value) }
    val (steadiest, shakiest) = if (rank) extremes(points.map { it.value }) else (null to null)
    TrendChart(
        title = title,
        unit = unit,
        series = series,
        steadiest = steadiest,
        shakiest = shakiest,
        decimals = decimals,
        noun = tr("次", "session"),
        rangeText = "${Fmt.dateTime(points.first().startMs)} → ${Fmt.dateTime(points.last().startMs)}",
    )
}
