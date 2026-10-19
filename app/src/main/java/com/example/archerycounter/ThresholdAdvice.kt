package com.example.archerycounter

enum class AdviceKind {
    /** 误判或有效的箭太少，还不能建议 */
    NOT_ENOUGH,
    /** 找到了一个能排除多数误判、又不会丢掉太多有效的箭的阈值 */
    SUGGEST,
    /** 误判和有效的峰值混在一起，调阈值帮不上 */
    NOT_SEPARABLE,
}

/**
 * 阈值建议的结果。
 * [validKept] 是有效的箭里，峰值仍然 ≥ 建议阈值的比例；[cancelledExcluded] 是误判的箭里，
 * 峰值会被建议阈值挡掉的比例。
 */
data class ThresholdAdvice(
    val kind: AdviceKind,
    val validCount: Int,
    val cancelledCount: Int,
    val validMedian: Float?,
    val cancelledMedian: Float?,
    val suggested: Int?,
    val validKept: Float,
    val cancelledExcluded: Float,
)

/** 建议至少要这么多笔才有意义 */
const val ADVICE_MIN_VALID = 10
const val ADVICE_MIN_CANCELLED = 3
private const val ADVICE_MIN_VALID_KEPT = 0.9f
private const val ADVICE_MIN_CANCELLED_EXCLUDED = 0.5f
private const val ADVICE_MAX_THRESHOLD = 40

/**
 * 用你标记过的「误判」和「有效」的峰值，找出一个更合适的放箭阈值。
 *
 * 限制：低于当时阈值的动作根本不会被记下来，所以这里只能建议「调高」来减少误判；
 * 漏判（该算却没算到的）看不出来，没办法建议调低。校准录制的记录不参与。
 */
fun adviseThreshold(sessions: List<Session>, current: Float): ThresholdAdvice {
    val real = sessions.filter { !it.calibration }
    val valid = real.flatMap { s -> s.shots.filter { !it.cancelled }.map { it.peak } }
    val cancelled = real.flatMap { s -> s.shots.filter { it.cancelled }.map { it.peak } }

    fun median(v: List<Float>): Float? =
        if (v.isEmpty()) null else v.sorted().let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2f }

    if (valid.size < ADVICE_MIN_VALID || cancelled.size < ADVICE_MIN_CANCELLED) {
        return ThresholdAdvice(
            AdviceKind.NOT_ENOUGH, valid.size, cancelled.size,
            median(valid), median(cancelled), null, 0f, 0f,
        )
    }

    var bestT: Int? = null
    var bestScore = Float.NEGATIVE_INFINITY
    var bestKept = 0f
    var bestExcl = 0f
    for (t in (kotlin.math.floor(current).toInt() + 1)..ADVICE_MAX_THRESHOLD) {
        val kept = valid.count { it >= t }.toFloat() / valid.size
        val excl = cancelled.count { it < t }.toFloat() / cancelled.size
        if (kept < ADVICE_MIN_VALID_KEPT || excl < ADVICE_MIN_CANCELLED_EXCLUDED) continue
        val score = excl - 2f * (1f - kept)
        if (score > bestScore) {
            bestScore = score
            bestT = t
            bestKept = kept
            bestExcl = excl
        }
    }

    return ThresholdAdvice(
        kind = if (bestT == null) AdviceKind.NOT_SEPARABLE else AdviceKind.SUGGEST,
        validCount = valid.size,
        cancelledCount = cancelled.size,
        validMedian = median(valid),
        cancelledMedian = median(cancelled),
        suggested = bestT,
        validKept = bestKept,
        cancelledExcluded = bestExcl,
    )
}
