package com.zgrcc.archeryassistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

/*
 * 二維晃動圖（v1.12）：像跑車儀表的 G 力圖。點越集中在圓心代表越穩。
 * 軸是手錶表面的 x、y，方向取決於怎麼戴表，只看穩不穩，不看往哪邊晃。
 * 座標與範圍的計算在 TraceMath（有單元測試）。
 */

/**
 * 畫一組 x、y 點（已經過扣平均值）：同心圓、十字線、軌跡線、最後一點（放箭瞬間或目前位置）和均方根半徑的虛線圈。
 * 圖下方用文字寫出外圈大小與均方根半徑，不單靠圖形傳達數值。
 */
@Composable
fun TraceDisc(
    x: List<Float>,
    y: List<Float>,
    gyro: Boolean,
    lastLabel: String,
    sizeDp: Int = 100,
    modifier: Modifier = Modifier,
) {
    val cx = TraceMath.centered(x)
    val cy = TraceMath.centered(y)
    val rms = TraceMath.rmsRadius(cx, cy)
    val range = TraceMath.niceRange(TraceMath.maxRadius(cx, cy), gyro)
    val unit = if (gyro) "°/s" else "m/s²"

    val lineColor = MaterialTheme.colors.primary
    val headColor = MaterialTheme.colors.error
    val rmsColor = MaterialTheme.colors.secondary
    val gridColor = MaterialTheme.colors.onSurfaceVariant.copy(alpha = 0.45f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(0.88f),
    ) {
        Canvas(modifier = Modifier.size(sizeDp.dp)) {
            val pad = 6.dp.toPx()
            val radius = size.minDimension / 2f - pad
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(gridColor, radius, center, style = Stroke(1.dp.toPx()))
            drawCircle(gridColor, radius / 2f, center, style = Stroke(1.dp.toPx()))
            drawLine(gridColor, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
            drawLine(gridColor, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())

            // 均方根半徑：多數點會落在這個圈附近，圈越小越穩
            val rmsPx = (rms / range * radius).coerceAtMost(radius)
            if (rmsPx > 1f) {
                drawCircle(
                    rmsColor, rmsPx, center,
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                )
            }

            val n = minOf(cx.size, cy.size)
            val pts = (0 until n).map { i ->
                Offset(
                    center.x + (cx[i] / range).coerceIn(-1f, 1f) * radius,
                    center.y - (cy[i] / range).coerceIn(-1f, 1f) * radius,
                )
            }
            for (i in 0 until pts.size - 1) {
                drawLine(lineColor, pts[i], pts[i + 1], 1.5.dp.toPx(), StrokeCap.Round)
            }
            pts.forEachIndexed { i, p ->
                if (i != pts.size - 1) drawCircle(lineColor, 2.dp.toPx(), p)
            }
            pts.lastOrNull()?.let { drawCircle(headColor, 4.dp.toPx(), it) }
        }
        Text(
            text = tr(
                "外圈 ±${Fmt.num(range, 0)} $unit　虛線圈 ${Fmt.num(rms, 2)} $unit",
                "Outer ring ±${Fmt.num(range, 0)} $unit  dashed ${Fmt.num(rms, 2)} $unit",
            ),
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "● $lastLabel",
            style = MaterialTheme.typography.caption2,
            color = headColor,
            textAlign = TextAlign.Center,
        )
    }
}

/** 單箭頁／快速檢查用：這一箭放箭前的 2D 軌跡。 */
@Composable
fun ShotTrace(trace: Trace2D, sizeDp: Int = 100) {
    TraceDisc(
        x = trace.x,
        y = trace.y,
        gyro = trace.gyro,
        lastLabel = tr("放箭前一刻", "Just before release"),
        sizeDp = sizeDp,
    )
}

/**
 * 主畫面的即時晃動圓盤：最近約 2 秒的 x、y。每 100ms 重畫一次，
 * 只在偵測中且設定開啟時才會顯示。
 */
@Composable
fun LiveDisc(vm: CounterViewModel, gyro: Boolean, sizeDp: Int, onClick: (() -> Unit)? = null) {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            tick++
        }
    }
    val xy = remember(tick) { vm.liveTraceSnapshot() }
    if (xy.first.size >= 2) {
        TraceDisc(
            x = xy.first.toList(),
            y = xy.second.toList(),
            gyro = gyro,
            lastLabel = tr("目前位置", "Now"),
            sizeDp = sizeDp,
            modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier,
        )
    }
}

/**
 * 全屏圓盤：從主畫面點即時圓盤進來，占滿整個表面。偵測照常運行；點一下或往右滑回到原畫面。
 */
@Composable
fun DiscFullScreen(vm: CounterViewModel, gyro: Boolean, onBack: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            tick++
        }
    }
    val xy = remember(tick) { vm.liveTraceSnapshot() }
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().clickable { onBack() },
        contentAlignment = Alignment.Center,
    ) {
        // 圓形表面四角被切掉，圓盤取較短邊的一部分，下方才放得下文字
        val side = (minOf(maxWidth.value, maxHeight.value) * 0.62f).toInt().coerceAtLeast(100)
        if (xy.first.size >= 2) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TraceDisc(
                    x = xy.first.toList(),
                    y = xy.second.toList(),
                    gyro = gyro,
                    lastLabel = tr("目前位置　再點一下返回", "Now  tap to go back"),
                    sizeDp = side,
                )
            }
        } else {
            Text(
                tr("等待資料…　再點一下返回", "Waiting for data…  tap to go back"),
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
            )
        }
    }
}
