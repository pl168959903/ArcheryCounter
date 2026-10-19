package com.example.archerycounter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * 開啟應用時的短動畫：一支箭射向靶心，下方顯示應用名稱、版本、作者與簽章指紋，約 1.5 秒後自動結束。
 * 只用 withFrameNanos 逐幀推進時間，不依賴額外的動畫套件。
 */
@Composable
fun SplashScreen(versionLabel: String, signature: String, onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    var elapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        var start = -1L
        while (true) {
            val now = withFrameNanos { it }
            if (start < 0) start = now
            val t = (now - start) / 1_000_000L
            if (t >= SplashTiming.TOTAL_MS) break
            elapsedMs = t
        }
        done()
    }

    val t = elapsedMs
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF16261B))
            .alpha(SplashTiming.overallAlpha(t)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TargetAndArrow(progress = SplashTiming.flyProgress(t), hit = SplashTiming.hitProgress(t))
            Column(
                modifier = Modifier.alpha(SplashTiming.textAlpha(t)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "射箭計數",
                    style = MaterialTheme.typography.title3,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Text(
                    versionLabel,
                    style = MaterialTheme.typography.caption1,
                    color = Color(0xFFCFD8DC),
                    textAlign = TextAlign.Center,
                )
                Text(
                    "作者 ${AppInfo.AUTHOR}",
                    style = MaterialTheme.typography.caption2,
                    color = Color(0xFFCFD8DC),
                    textAlign = TextAlign.Center,
                )
                Text(
                    "簽章 $signature",
                    style = MaterialTheme.typography.caption2,
                    color = Color(0xFF90A4AE),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 靶在右邊，箭從左邊飛來；射中後靶外圈有一圈擴散的白光。 */
@Composable
private fun TargetAndArrow(progress: Float, hit: Float) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .height(64.dp),
    ) {
        val cy = size.height / 2f
        val r = size.height * 0.46f
        val cx = size.width - r - 2f

        // 靶：由外到內 白、紅、白、金
        drawCircle(Color(0xFFECEFF1), radius = r, center = Offset(cx, cy))
        drawCircle(Color(0xFFD32F2F), radius = r * 0.77f, center = Offset(cx, cy))
        drawCircle(Color(0xFFECEFF1), radius = r * 0.54f, center = Offset(cx, cy))
        drawCircle(Color(0xFFFBC02D), radius = r * 0.31f, center = Offset(cx, cy))

        // 射中後的擴散光圈
        if (hit > 0f && hit < 1f) {
            drawCircle(
                color = Color.White.copy(alpha = 1f - hit),
                radius = r * (1f + 0.5f * hit),
                center = Offset(cx, cy),
                style = Stroke(width = 3f),
            )
        }

        // 箭：箭頭從左邊外面飛到靶心
        val shaftLen = size.width * 0.38f
        val startTipX = -shaftLen
        val tipX = startTipX + (cx - startTipX) * progress
        val tailX = tipX - shaftLen
        val shaft = Color(0xFFCFD8DC)
        drawLine(shaft, Offset(tailX, cy), Offset(tipX - 6f, cy), strokeWidth = 5f, cap = StrokeCap.Round)
        // 箭羽
        val feather = Color.White
        drawLine(feather, Offset(tailX, cy), Offset(tailX - 8f, cy - 8f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(feather, Offset(tailX, cy), Offset(tailX - 8f, cy + 8f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(feather, Offset(tailX + 9f, cy), Offset(tailX + 1f, cy - 8f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(feather, Offset(tailX + 9f, cy), Offset(tailX + 1f, cy + 8f), strokeWidth = 3f, cap = StrokeCap.Round)
        // 箭頭
        val head = Path().apply {
            moveTo(tipX + 8f, cy)
            lineTo(tipX - 8f, cy - 7f)
            lineTo(tipX - 8f, cy + 7f)
            close()
        }
        drawPath(head, shaft)
    }
}
