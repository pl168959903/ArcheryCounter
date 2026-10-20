package com.zgrcc.archeryassistant

/**
 * 「靜止條件」與「冷卻」的建議（v1.11）。結果的 [kind] 沿用 [AdviceKind]。
 * [validKept] 是有效的箭仍會被算到的比例；[cancelledExcluded] 是誤判會被擋掉的比例。
 */
data class ParamAdvice(
    val kind: AdviceKind,
    val validCount: Int,
    val cancelledCount: Int,
    val suggested: Int?,
    val validKept: Float,
    val cancelledExcluded: Float,
)

/** 靜止條件最小可建議值、冷卻最大可建議值 */
const val STILL_ADVICE_MIN = 1
const val COOLDOWN_ADVICE_MAX = 30
private const val KEEP_MIN = 0.9f
private const val EXCL_MIN = 0.5f

private fun pickBest(
    candidates: List<Int>,
    validCount: Int,
    cancelledCount: Int,
    kept: (Int) -> Float,
    excluded: (Int) -> Float,
): ParamAdvice {
    var best: Int? = null
    var bestScore = Float.NEGATIVE_INFINITY
    var bestKept = 0f
    var bestExcl = 0f
    // 候選值由「離目前最近」排到最遠，分數相同時保留離目前最近的（改動最小）
    for (t in candidates) {
        val k = kept(t)
        val e = excluded(t)
        if (k < KEEP_MIN || e < EXCL_MIN) continue
        val score = e - 2f * (1f - k)
        if (score > bestScore) {
            bestScore = score
            best = t
            bestKept = k
            bestExcl = e
        }
    }
    return ParamAdvice(
        kind = if (best == null) AdviceKind.NOT_SEPARABLE else AdviceKind.SUGGEST,
        validCount = validCount,
        cancelledCount = cancelledCount,
        suggested = best,
        validKept = bestKept,
        cancelledExcluded = bestExcl,
    )
}

/**
 * 靜止條件建議：放箭前的晃動量（加速度）。誤判的箭如果放箭前就比較晃，把靜止條件調小可以擋掉；
 * 這裡只能建議「調小（更嚴格）」。晃動量是整個分析視窗的值，和靜止條件的瞬間判斷不完全相同，僅供參考。
 */
fun adviseStill(sessions: List<Session>, current: Int): ParamAdvice {
    val real = sessions.filter { !it.calibration }
    val valid = real.flatMap { s -> s.shots.filter { !it.cancelled }.mapNotNull { it.accelRms } }
    val cancelled = real.flatMap { s -> s.shots.filter { it.cancelled }.mapNotNull { it.accelRms } }
    if (valid.size < ADVICE_MIN_VALID || cancelled.size < ADVICE_MIN_CANCELLED) {
        return ParamAdvice(AdviceKind.NOT_ENOUGH, valid.size, cancelled.size, null, 0f, 0f)
    }
    val cands = (current - 1 downTo STILL_ADVICE_MIN).toList()
    return pickBest(
        cands, valid.size, cancelled.size,
        kept = { t -> valid.count { it <= t }.toFloat() / valid.size },
        excluded = { t -> cancelled.count { it > t }.toFloat() / cancelled.size },
    )
}

/** 同一次記錄裡，每一筆（含誤判）距前一筆的秒數；第一筆沒有前一筆，略過。 */
private fun gapsSec(s: Session, pick: (ShotEntry) -> Boolean): List<Float> {
    val sorted = s.shots.sortedBy { it.relMs }
    val out = ArrayList<Float>()
    for (i in 1 until sorted.size) {
        if (pick(sorted[i])) out.add((sorted[i].relMs - sorted[i - 1].relMs) / 1000f)
    }
    return out
}

/**
 * 冷卻建議：誤判常是「一次放箭被算成兩次」，第二筆離前一筆很近。
 * 找一個冷卻秒數，能擋掉多數誤判的短間隔，又不會擋到真正連續的兩箭（有效箭之間的間隔）。
 * 只能建議調長；校準錄製不參與。
 */
fun adviseCooldown(sessions: List<Session>, currentSec: Int): ParamAdvice {
    val real = sessions.filter { !it.calibration }
    val cancelledGaps = real.flatMap { s -> gapsSec(s) { it.cancelled } }
    // 有效箭之間的間隔：只看相鄰的有效箭
    val validGaps = real.flatMap { s ->
        val v = s.shots.filter { !it.cancelled }.sortedBy { it.relMs }
        (1 until v.size).map { (v[it].relMs - v[it - 1].relMs) / 1000f }
    }
    if (validGaps.size < ADVICE_MIN_VALID || cancelledGaps.size < ADVICE_MIN_CANCELLED) {
        return ParamAdvice(AdviceKind.NOT_ENOUGH, validGaps.size, cancelledGaps.size, null, 0f, 0f)
    }
    val cands = ((currentSec + 1)..COOLDOWN_ADVICE_MAX).toList()
    return pickBest(
        cands, validGaps.size, cancelledGaps.size,
        kept = { t -> validGaps.count { it >= t }.toFloat() / validGaps.size },
        excluded = { t -> cancelledGaps.count { it < t }.toFloat() / cancelledGaps.size },
    )
}
