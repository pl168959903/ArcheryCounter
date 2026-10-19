package com.example.archerycounter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/** 跨次趨勢：最近 20 次記錄各自的平均晃動量，由舊到新。 */
@Composable
fun TrendScreen(vm: CounterViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val accel = remember(sessions) { sessionTrend(sessions, gyro = false) }
    val gyro = remember(sessions) { sessionTrend(sessions, gyro = true) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text("跨次趨勢", style = MaterialTheme.typography.title3) }
        item { Note("每個點是一次記錄的平均晃動量，越低越穩。由舊到新，最多 20 次") }

        if (accel.size >= 2) {
            item { TrendBlock("平均加速度晃動量", "m/s²", accel, decimals = 2) }
        }
        if (gyro.size >= 2) {
            item { TrendBlock("平均角速度晃動量", "°/s", gyro, decimals = 1) }
        }
        if (accel.size < 2 && gyro.size < 2) {
            item { Note("至少要 2 次記錄有晃動量數據才畫得出趨勢圖") }
        }
        item {
            Note("跨次比較要看條件是否一致：視窗秒數、閾值、當天狀況不同，數字也會不同")
        }
    }
}

@Composable
private fun TrendBlock(title: String, unit: String, points: List<SessionPoint>, decimals: Int) {
    val series = points.mapIndexed { i, p -> ShotRank(i + 1, p.value) }
    val (steadiest, shakiest) = extremes(points.map { it.value })
    TrendChart(
        title = title,
        unit = unit,
        series = series,
        steadiest = steadiest,
        shakiest = shakiest,
        decimals = decimals,
        noun = "次",
        rangeText = "${Fmt.dateTime(points.first().startMs)} → ${Fmt.dateTime(points.last().startMs)}",
    )
}
