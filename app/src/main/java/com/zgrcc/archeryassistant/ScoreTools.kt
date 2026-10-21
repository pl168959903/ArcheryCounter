package com.zgrcc.archeryassistant

/** v1.21：環數輸入模式、環數與晃動分析、取樣率提醒。純 Kotlin。 */

/** 高環數（[HIGH_MIN] 環以上）和其他箭的平均加速度晃動量比較。 */
data class ScoreShake(val highAvg: Float, val highN: Int, val lowAvg: Float, val lowN: Int)

object ScoreShakeAnalysis {
    const val HIGH_MIN = 9
    const val MIN_SCORED = 10
    const val MIN_EACH = 3

    /** 有環數的有效箭少於 [MIN_SCORED] 支、或任一組少於 [MIN_EACH] 支時回傳 null。校準不分析。 */
    fun compute(s: Session): ScoreShake? {
        if (s.calibration) return null
        val scored = s.validShots().filter { it.score != null && it.accelRms != null }
        if (scored.size < MIN_SCORED) return null
        val high = scored.filter { it.score!! >= HIGH_MIN }.map { it.accelRms!! }
        val low = scored.filter { it.score!! < HIGH_MIN }.map { it.accelRms!! }
        if (high.size < MIN_EACH || low.size < MIN_EACH) return null
        return ScoreShake(high.average().toFloat(), high.size, low.average().toFloat(), low.size)
    }

    fun text(r: ScoreShake): String = tr(
        "${HIGH_MIN}～10 環的箭　平均晃動 ${Fmt.num(r.highAvg, 2)}（${r.highN} 支）\\n${HIGH_MIN - 1} 環以下的箭　平均晃動 ${Fmt.num(r.lowAvg, 2)}（${r.lowN} 支）" +
            if (r.highAvg < r.lowAvg) "\\n高環數的箭放箭前比較穩" else "\\n高環數的箭並沒有比較穩，晃動可能不是主要原因",
        "${HIGH_MIN}-10 scoring shots  avg shake ${Fmt.num(r.highAvg, 2)} (${r.highN})\\n${HIGH_MIN - 1} or lower  avg shake ${Fmt.num(r.lowAvg, 2)} (${r.lowN})" +
            if (r.highAvg < r.lowAvg) "\\nHigh-scoring shots were steadier before release" else "\\nHigh-scoring shots were not steadier; shake may not be the main factor",
    )
}

object EndScore {
    /** 有效的第 [validNo] 箭在 [Session.shots] 裡的位置；沒有回傳 −1。 */
    fun validToIndex(s: Session, validNo: Int): Int {
        var n = 0
        for (i in s.shots.indices) {
            if (!s.shots[i].cancelled) {
                n++
                if (n == validNo) return i
            }
        }
        return -1
    }

    /** [index] 這一箭所在那一趟的全部有效箭位置（由小到大）；誤判或找不到回傳空清單。 */
    fun endIndices(s: Session, ends: List<EndStats>, index: Int): List<Int> {
        val sh = s.shots.getOrNull(index) ?: return emptyList()
        if (sh.cancelled) return emptyList()
        val validNo = s.shots.take(index + 1).count { !it.cancelled }
        val e = ends.firstOrNull { validNo in it.firstShot..it.lastShot } ?: return emptyList()
        return (e.firstShot..e.lastShot).map { validToIndex(s, it) }.filter { it >= 0 }
    }

    /** 這幾箭已輸入的環數合計與已輸入的箭數。 */
    fun total(s: Session, indices: List<Int>): Pair<Int, Int> {
        val scores = indices.mapNotNull { s.shots.getOrNull(it)?.score }
        return scores.sum() to scores.size
    }
}

object RateWarn {
    const val RATIO = 0.85f

    /** 實測取樣率明顯低於要求（低於 85%）時回傳 true；還沒量到（≤0）不提醒。 */
    fun low(measured: Float, requested: Int): Boolean = measured > 0f && requested > 0 && measured < requested * RATIO
}
