package com.zgrcc.archeryassistant

/**
 * 一箭放箭前的動作分段（v1.22）：準備動作（舉弓、拉弓）→ 瞄準保持 → 放箭。純 Kotlin。
 *
 * 這是估計，不是量測：從放箭前約 10 秒的波形，找最後一段明顯的大動作當「準備動作」，
 * 大動作結束到放箭之間手臂幾乎不動的部分當「瞄準保持」。門檻用實際記錄校正過。
 * 波形太短（記錄剛開始、舊記錄只有 2 秒）、沒有陀螺儀、或找不到明顯大動作時回傳 null。
 */
data class DrawPhase(
    /** 準備動作開始與結束的位置（波形的第幾個點） */
    val startBin: Int,
    val endBin: Int,
    /** 放箭位置（同 [Waveform.impactIndex]） */
    val impactBin: Int,
    val stepMs: Int,
) {
    val prepSec: Float get() = (endBin - startBin) * stepMs / 1000f
    val holdSec: Float get() = (impactBin - endBin) * stepMs / 1000f
    val totalSec: Float get() = (impactBin - startBin) * stepMs / 1000f
}

object DrawPhases {
    /** 分析的最小單位（毫秒）；每個單位取波形點的平均 */
    const val BLOCK_MS = 250
    /** 單位內平均超過這些值算「有動作」 */
    const val ACT_ACCEL = 1.2f
    const val ACT_GYRO = 20f
    /** 超過這些值（兩者都要）算「明顯的大動作」 */
    const val STRONG_ACCEL = 2.5f
    const val STRONG_GYRO = 40f
    /** 至少要有這麼多個單位的放箭前波形（約 2 秒）才分析 */
    private const val MIN_BLOCKS = 8
    /** 大動作中間允許的安靜空隙（單位數） */
    private const val MAX_GAP = 1

    fun analyze(w: Waveform): DrawPhase? {
        if (w.gyro.isEmpty() || w.gyro.size != w.accel.size || w.stepMs <= 0) return null
        val k = ((BLOCK_MS + w.stepMs / 2) / w.stepMs).coerceAtLeast(1)
        // 最後一個單位含放箭的上升沿，不分析
        val n = w.impactIndex / k - 1
        if (n < MIN_BLOCKS) return null
        val a = FloatArray(n) { i -> mean(w.accel, i * k, k) }
        val g = FloatArray(n) { i -> mean(w.gyro, i * k, k) }
        fun active(i: Int) = a[i] >= ACT_ACCEL || g[i] >= ACT_GYRO
        var end = -1
        for (i in n - 1 downTo 0) {
            if (a[i] >= STRONG_ACCEL && g[i] >= STRONG_GYRO) { end = i; break }
        }
        if (end < 0) return null
        // 往前找大動作的起點：連續有動作的單位，中間最多容許 MAX_GAP 個安靜單位
        var start = end
        var j = end
        var gap = 0
        while (j > 0) {
            if (active(j - 1)) {
                start = j - 1
                gap = 0
            } else {
                gap++
                if (gap > MAX_GAP) break
            }
            j--
        }
        while (end + 1 < n && active(end + 1)) end++
        // 大動作一直延伸到波形開頭：前面的資料不夠，看不出真正的起點
        if (start == 0) return null
        return DrawPhase(start * k, (end + 1) * k, w.impactIndex, w.stepMs)
    }

    private fun mean(v: List<Float>, from: Int, count: Int): Float {
        var s = 0f
        for (i in from until from + count) s += v[i]
        return s / count
    }

    /** 「準備 6.0 秒 / 保持 3.0 秒」。 */
    fun text(p: DrawPhase): String = tr(
        "準備動作 ${Fmt.num(p.prepSec, 1)} 秒　瞄準保持 ${Fmt.num(p.holdSec, 1)} 秒（估計）",
        "Preparation ${Fmt.num(p.prepSec, 1)} s  Hold ${Fmt.num(p.holdSec, 1)} s (estimate)",
    )

    /** 一趟的平均（逐趟統計用）；沒有可分析的箭回傳 null。 */
    fun average(phases: List<DrawPhase>): Pair<Float, Float>? {
        if (phases.isEmpty()) return null
        return phases.map { it.prepSec }.average().toFloat() to phases.map { it.holdSec }.average().toFloat()
    }
}
