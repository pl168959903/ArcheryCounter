package com.zgrcc.archeryassistant

import kotlin.math.PI
import kotlin.math.sin

/** 啟動動畫的時間軸（毫秒）。純數字計算，不依賴 Android，可以直接在 JVM 上測試。 */
object SplashTiming {
    const val POP_MS = 700L              // 圖示放大並淡入
    const val ICON_FADE_MS = 400L        // 圖示淡入
    const val TEXT_START_MS = 600L       // 第一行文字開始淡入
    const val TEXT_STEP_MS = 250L        // 每一行比上一行晚多久開始
    const val TEXT_FADE_MS = 400L        // 每一行淡入要多久
    const val TEXT_LINES = 4             // 名稱、版本、作者、簽章
    const val HINT_START_MS = 1900L      // 「點按螢幕進入」開始出現
    const val HINT_FADE_MS = 300L
    const val PULSE_PERIOD_MS = 1600L    // 光圈與提示文字的呼吸週期

    /** 不等點按時，整段動畫的長度（約 3 秒） */
    const val AUTO_TOTAL_MS = 3000L
    const val FADE_OUT_MS = 250L

    /** 等點按時的保險：超過這麼久沒人點就自己進入，避免動畫一直跑下去 */
    const val WAIT_TIMEOUT_MS = 60_000L

    private const val START_SCALE = 0.8f
    private const val TWO_PI = (2.0 * PI).toFloat()

    private fun easeOutCubic(x: Float): Float {
        val inv = 1f - x.coerceIn(0f, 1f)
        return 1f - inv * inv * inv
    }

    /** 圖示放大進度 0..1，先快後慢。 */
    fun popProgress(tMs: Long): Float = easeOutCubic(tMs.toFloat() / POP_MS)

    /** 圖示大小比例：從 80% 放大到 100%。 */
    fun iconScale(tMs: Long): Float = START_SCALE + (1f - START_SCALE) * popProgress(tMs)

    /** 圖示透明度 0..1。 */
    fun iconAlpha(tMs: Long): Float = (tMs.toFloat() / ICON_FADE_MS).coerceIn(0f, 1f)

    /** 第 [index] 行（從 0 開始）文字的透明度 0..1，一行接一行淡入。 */
    fun lineAlpha(tMs: Long, index: Int): Float =
        ((tMs - TEXT_START_MS - index * TEXT_STEP_MS).toFloat() / TEXT_FADE_MS).coerceIn(0f, 1f)

    /** 所有文字都完全顯示的時間點 */
    fun allTextShownMs(): Long = TEXT_START_MS + (TEXT_LINES - 1) * TEXT_STEP_MS + TEXT_FADE_MS

    /** 呼吸起伏 0..1。 */
    private fun pulse(tMs: Long): Float = 0.5f * (1f + sin(TWO_PI * tMs.toFloat() / PULSE_PERIOD_MS))

    /** 圖示外圈光圈的透明度：圖示放大完成後才出現，在 0.1～0.5 之間起伏。 */
    fun glowAlpha(tMs: Long): Float =
        if (tMs < POP_MS) 0f else 0.1f + 0.4f * pulse(tMs - POP_MS)

    /** 「點按螢幕進入」提示的透明度：先淡入，之後在 0.4～1 之間閃動。 */
    fun hintAlpha(tMs: Long): Float {
        if (tMs < HINT_START_MS) return 0f
        val fadeIn = ((tMs - HINT_START_MS).toFloat() / HINT_FADE_MS).coerceIn(0f, 1f)
        return fadeIn * (0.4f + 0.6f * pulse(tMs - HINT_START_MS))
    }

    /** 整個畫面的透明度。等點按時不淡出；不等點按時在最後 [FADE_OUT_MS] 淡出。 */
    fun overallAlpha(tMs: Long, waitForTap: Boolean): Float {
        if (waitForTap) return 1f
        val fadeStart = AUTO_TOTAL_MS - FADE_OUT_MS
        return if (tMs <= fadeStart) 1f else (1f - (tMs - fadeStart).toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
    }

    /** 這個時間點動畫是不是該自己結束了（沒被點按的情況）。 */
    fun isFinished(tMs: Long, waitForTap: Boolean): Boolean =
        tMs >= if (waitForTap) WAIT_TIMEOUT_MS else AUTO_TOTAL_MS
}
