package com.zgrcc.archeryassistant

/**
 * 疲勞／穩定度變化提醒（v1.9）。
 *
 * 比較「剛開始的幾趟」和「最近幾趟」的平均晃動量：最近比剛開始抖超過 [thresholdPct]%，
 * 就提醒一次（建議休息、補水或收工）。提醒過之後，要等晃動量回到門檻一半以下才會重新允許提醒，
 * 避免同一段疲勞一直震。純 Kotlin，方便單元測試。
 */
data class FatigueDecision(val warnNow: Boolean, val warned: Boolean, val pct: Float?)

object FatigueRule {
    const val BASE_ENDS = 2          // 拿最前面幾趟當基準
    const val RECENT_ENDS = 2        // 拿最近幾趟比較
    const val MIN_ENDS = BASE_ENDS + RECENT_ENDS // 至少要有幾趟才判斷（基準和最近不重疊）

    const val MIN_PCT = 10
    const val MAX_PCT = 60
    const val STEP_PCT = 5
    const val DEFAULT_PCT = 25

    /** 最近比基準抖了幾 %（負值＝更穩）。資料不足或基準為 0 時回傳 null。 */
    fun changePct(ends: List<EndStats>): Float? {
        if (ends.size < MIN_ENDS) return null
        val base = avgOrNull(ends.take(BASE_ENDS).map { it.accelAvg }) ?: return null
        val recent = avgOrNull(ends.takeLast(RECENT_ENDS).map { it.accelAvg }) ?: return null
        if (base <= 1e-6f) return null
        return (recent - base) / base * 100f
    }

    fun decide(ends: List<EndStats>, thresholdPct: Int, warned: Boolean): FatigueDecision {
        val pct = changePct(ends) ?: return FatigueDecision(false, warned, null)
        val limit = thresholdPct.toFloat()
        return when {
            pct >= limit && !warned -> FatigueDecision(true, true, pct)
            pct < limit / 2f -> FatigueDecision(false, false, pct)
            else -> FatigueDecision(false, warned, pct)
        }
    }

    /** 震動：兩次長震，和放箭、滿趟的節奏都不同。 */
    val PATTERN = longArrayOf(0, 400, 200, 400)
}
