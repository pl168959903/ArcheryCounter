package com.example.archerycounter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

/*
 * 手表上的图表。直接用 Canvas 画，不依赖图表套件。
 * 座标的计算在 ChartMath（有单元测试），这里只负责把 0..1 的比例乘上画布大小再画出来。
 * 数字和说明用一般文字放在图的上下，不靠颜色单独传达意思。
 */

/**
 * 整次记录的趋势图：横轴是第几箭，纵轴是晃动量（越低越稳）。
 * 最稳和最不稳的那一箭用较大的点标出，并在下方用文字写明是第几箭、多少。
 */
@Composable
fun TrendChart(
    title: String,
    unit: String,
    series: List<ShotRank>,
    steadiest: ShotRank?,
    shakiest: ShotRank?,
    decimals: Int,
    /** 横轴单位：逐箭的图是「箭」，跨次的图是「次」 */
    noun: String = "箭",
    /** 横轴说明；预设是「第 1 箭 → 第 N 箭」 */
    rangeText: String? = null,
) {
    val lineColor = MaterialTheme.colors.primary
    val steadyColor = MaterialTheme.colors.secondary
    val shakyColor = MaterialTheme.colors.error
    val gridColor = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.35f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(0.88f),
    ) {
        Text(
            text = "$title（$unit）",
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.Center,
        )
        Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
            val pad = 8.dp.toPx()
            val w = size.width - 2 * pad
            val h = size.height - 2 * pad
            val ys = ChartMath.yFractions(series.map { it.value })

            drawLine(gridColor, Offset(pad, pad), Offset(pad + w, pad), 1.dp.toPx())
            drawLine(gridColor, Offset(pad, pad + h), Offset(pad + w, pad + h), 1.dp.toPx())

            val points = series.indices.map { i ->
                Offset(
                    x = pad + w * ChartMath.xFraction(i, series.size),
                    y = pad + h * (1f - ys[i]),
                )
            }
            for (i in 0 until points.size - 1) {
                drawLine(lineColor, points[i], points[i + 1], 2.dp.toPx(), StrokeCap.Round)
            }
            points.forEachIndexed { i, p ->
                val number = series[i].number
                when {
                    number == steadiest?.number -> drawCircle(steadyColor, 5.dp.toPx(), p)
                    number == shakiest?.number -> drawCircle(shakyColor, 5.dp.toPx(), p)
                    else -> drawCircle(lineColor, 2.5.dp.toPx(), p)
                }
            }
        }
        Text(
            text = rangeText ?: "第 ${series.first().number} $noun → 第 ${series.last().number} $noun",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (steadiest != null) {
            Text(
                text = "● 最穩 第 ${steadiest.number} $noun ${Fmt.num(steadiest.value, decimals)}",
                style = MaterialTheme.typography.caption2,
                color = steadyColor,
                textAlign = TextAlign.Center,
            )
        }
        if (shakiest != null) {
            Text(
                text = "● 最不穩 第 ${shakiest.number} $noun ${Fmt.num(shakiest.value, decimals)}",
                style = MaterialTheme.typography.caption2,
                color = shakyColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 單箭波形：放箭前後的加速度（和角速度，若有）合力大小。
 * 豎線標出放箭瞬間；縱軸從 0 開始，各自縮放到自己的最大值。
 */
@Composable
fun ShotWaveform(wave: Waveform) {
    val preSec = wave.impactIndex * wave.stepMs / 1000f
    val postSec = (wave.accel.size - wave.impactIndex) * wave.stepMs / 1000f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(0.88f),
    ) {
        WaveLine(
            label = "加速度（m/s²）",
            values = wave.accel,
            impactIndex = wave.impactIndex,
            lineColor = MaterialTheme.colors.primary,
        )
        if (wave.gyro.isNotEmpty()) {
            WaveLine(
                label = "角速度（°/s）",
                values = wave.gyro,
                impactIndex = wave.impactIndex,
                lineColor = MaterialTheme.colors.secondary,
            )
        }
        Text(
            text = "橫軸：放箭前 ${Fmt.num(preSec, 1)} 秒 ～ 放箭後 ${Fmt.num(postSec, 1)} 秒\n豎線 ＝ 放箭瞬間",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WaveLine(label: String, values: List<Float>, impactIndex: Int, lineColor: Color) {
    val maxValue = values.maxOrNull() ?: 0f
    val gridColor = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.35f)
    val impactColor = MaterialTheme.colors.error

    Text(
        text = "$label　最大 ${Fmt.num(maxValue, 1)}",
        style = MaterialTheme.typography.caption1,
        textAlign = TextAlign.Center,
    )
    Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
        val pad = 4.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val ys = ChartMath.yFractions(values, lo = 0f, hi = if (maxValue < 1e-3f) 1f else maxValue)

        drawLine(gridColor, Offset(pad, pad + h), Offset(pad + w, pad + h), 1.dp.toPx())

        val points = values.indices.map { i ->
            Offset(
                x = pad + w * ChartMath.xFraction(i, values.size),
                y = pad + h * (1f - ys[i]),
            )
        }
        for (i in 0 until points.size - 1) {
            drawLine(lineColor, points[i], points[i + 1], 2.dp.toPx(), StrokeCap.Round)
        }

        val impactX = pad + w * ChartMath.xFraction(impactIndex, values.size)
        drawLine(impactColor, Offset(impactX, pad), Offset(impactX, pad + h), 1.5.dp.toPx())
    }
}

/**
 * 主畫面的即時波形：最近約 5 秒的加速度合力大小，橫虛線是目前的閾值。
 * 每 100ms 重畫一次（不是每個感測器樣本都重畫），只在偵測中且設定開啟時才會顯示。
 */
@Composable
fun LiveWaveform(vm: CounterViewModel, threshold: Float) {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            tick++
        }
    }
    val data = remember(tick) { vm.liveSnapshot().toList() }

    val lineColor = MaterialTheme.colors.primary
    val thresholdColor = MaterialTheme.colors.error
    val gridColor = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.35f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(0.88f),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(52.dp)) {
            val pad = 4.dp.toPx()
            val w = size.width - 2 * pad
            val h = size.height - 2 * pad
            // 縱軸固定從 0 開始，上限至少留到閾值的 1.25 倍，這樣閾值線不會貼在頂端
            val top = maxOf(threshold * 1.25f, data.maxOrNull() ?: 0f, 1f)

            drawLine(gridColor, Offset(pad, pad + h), Offset(pad + w, pad + h), 1.dp.toPx())

            val thresholdY = pad + h * (1f - (threshold / top).coerceIn(0f, 1f))
            drawLine(
                color = thresholdColor,
                start = Offset(pad, thresholdY),
                end = Offset(pad + w, thresholdY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )

            if (data.size >= 2) {
                val ys = ChartMath.yFractions(data, lo = 0f, hi = top)
                val points = data.indices.map { i ->
                    Offset(
                        x = pad + w * ChartMath.xFraction(i, data.size),
                        y = pad + h * (1f - ys[i]),
                    )
                }
                for (i in 0 until points.size - 1) {
                    drawLine(lineColor, points[i], points[i + 1], 1.5.dp.toPx())
                }
            }
        }
        Text(
            text = "即時加速度（m/s²）　虛線 ＝ 閾值 ${Fmt.num(threshold, 0)}",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
