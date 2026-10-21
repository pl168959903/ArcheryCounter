package com.zgrcc.archeryassistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * 開啟應用時的啟動畫面：圖示放大淡入，下方依序淡入應用名稱、版本、作者與簽章指紋。
 * - [waitForTap] 為 true：動畫播完後停在畫面上，出現「點按螢幕進入」，點一下才進入（太久沒點會自己進入）。
 * - [waitForTap] 為 false：約 3 秒後自動淡出進入。
 * 兩種情況下，隨時點一下螢幕都會立刻進入。
 * 只用 withFrameNanos 逐幀推進時間，不依賴額外的動畫套件。
 */
@Composable
fun SplashScreen(
    versionLabel: String,
    signature: String,
    waitForTap: Boolean,
    onDone: () -> Unit,
    newVersion: String? = null,
) {
    val done by rememberUpdatedState(onDone)
    var elapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(waitForTap) {
        var start = -1L
        while (true) {
            val now = withFrameNanos { it }
            if (start < 0) start = now
            val t = (now - start) / 1_000_000L
            elapsedMs = t
            if (SplashTiming.isFinished(t, waitForTap)) break
        }
        done()
    }

    val t = elapsedMs
    val lines = listOf(
        "Archery Assistant" to MaterialTheme.typography.title3,
        versionLabel to MaterialTheme.typography.caption1,
        tr("作者 ${AppInfo.AUTHOR}", "Author ${AppInfo.AUTHOR}") to MaterialTheme.typography.caption2,
        tr("簽章 $signature", "Signature $signature") to MaterialTheme.typography.caption2,
    )
    val colors = listOf(Color.White, Color(0xFFCFD8DC), Color(0xFFCFD8DC), Color(0xFF90A4AE))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F2227))
            .alpha(SplashTiming.overallAlpha(t, waitForTap))
            // 隨時點一下就進入
            .pointerInput(Unit) { detectTapGestures(onTap = { done() }) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 圖示加一圈呼吸光圈
            AppIconBadge(t)
            Spacer(Modifier.height(4.dp))
            lines.forEachIndexed { i, (text, style) ->
                Text(
                    text = text,
                    style = style,
                    color = colors[i],
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(SplashTiming.lineAlpha(t, i)),
                )
            }
            // 檢查到有新版本才顯示（黃字）；已是最新、檢查失敗、還沒查完都不顯示
            if (!newVersion.isNullOrBlank()) {
                Text(
                    text = tr("有新版本 $newVersion", "New version $newVersion"),
                    style = MaterialTheme.typography.caption1,
                    color = Color(0xFFFFD54F),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (waitForTap) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = tr("點按螢幕進入", "Tap to enter"),
                    style = MaterialTheme.typography.caption2,
                    color = Color(0xFF9CCC3C),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .alpha(SplashTiming.hintAlpha(t)),
                )
            }
        }
    }
}


/**
 * App 圖示加一圈呼吸光圈（v1.19.1，啟動畫面與「關於」共用）。
 * [t] 是動畫開始後的毫秒數。
 */
@Composable
fun AppIconBadge(t: Long, size: androidx.compose.ui.unit.Dp = 84.dp) {
    val time = t
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFF9CCC3C).copy(alpha = SplashTiming.glowAlpha(time)),
                radius = this.size.minDimension / 2f - 1.5f,
                style = Stroke(width = 3f),
            )
        }
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            modifier = Modifier
                .size(size - 10.dp)
                .clip(CircleShape)
                .graphicsLayer {
                    val s = SplashTiming.iconScale(time)
                    scaleX = s
                    scaleY = s
                    alpha = SplashTiming.iconAlpha(time)
                },
        )
    }
}
