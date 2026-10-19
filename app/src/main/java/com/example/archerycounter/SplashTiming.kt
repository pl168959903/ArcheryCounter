package com.example.archerycounter

/** 啟動動畫的時間軸（毫秒）。純數字計算，不依賴 Android。 */
object SplashTiming {
    const val FLY_MS = 700L        // 箭飛向靶心
    const val TOTAL_MS = 1500L     // 整段動畫長度
    const val FADE_OUT_MS = 250L   // 最後淡出

    /** 箭飛行進度 0..1，先快後慢（ease-out）。 */
    fun flyProgress(tMs: Long): Float {
        val x = (tMs.toFloat() / FLY_MS).coerceIn(0f, 1f)
        val inv = 1f - x
        return 1f - inv * inv * inv
    }

    /** 靶被射中後的震動圈進度 0..1；還沒射中時是 0。 */
    fun hitProgress(tMs: Long): Float = ((tMs - FLY_MS).toFloat() / 400f).coerceIn(0f, 1f)

    /** 文字淡入：箭飛到一半時開始，約 0.4 秒淡入完成。 */
    fun textAlpha(tMs: Long): Float = ((tMs - FLY_MS / 2).toFloat() / 400f).coerceIn(0f, 1f)

    /** 整個畫面的透明度：最後 [FADE_OUT_MS] 淡出。 */
    fun overallAlpha(tMs: Long): Float {
        val fadeStart = TOTAL_MS - FADE_OUT_MS
        return if (tMs <= fadeStart) 1f else (1f - (tMs - fadeStart).toFloat() / FADE_OUT_MS).coerceIn(0f, 1f)
    }
}
