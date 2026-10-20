package com.zgrcc.archeryassistant

import kotlin.math.ceil
import kotlin.math.roundToInt

/** 原始資料裡的一筆：距記錄開始的毫秒數和加速度合力大小。 */
data class MagSample(val relMs: Long, val mag: Float)

enum class CalibKind {
    /** 有建議 */
    OK,
    /** 缺少某一段、或沒有原始資料 */
    NO_DATA,
    /** 「正常射擊」那一段偵測到的箭太少 */
    NOT_ENOUGH_SHOTS,
}

data class CalibAdvice(
    val kind: CalibKind,
    /** 建議的放箭閾值；非放箭動作和放箭峰值分不開時為 null */
    val threshold: Int?,
    /** 建議的靜止條件；資料不夠時為 null */
    val still: Int?,
    /** 「只拉弓不放箭」「取箭走動」兩段的動作上限（99 百分位的合力） */
    val nonShotCeil: Float?,
    /** 「正常射擊」裡最小的放箭峰值 */
    val shotLow: Float?,
    val shotCount: Int,
    /** 閾值為 null 時，原因：非放箭動作的上限已經高過或接近放箭峰值 */
    val overlap: Boolean,
)

/**
 * 校準自動建議（v1.14）。純 Kotlin。
 *
 * 用「只拉弓不放箭」「正常射擊」「取箭走動」三段的原始資料：
 * - 放箭閾值要比非放箭動作的上限高，又比正常射擊中最小的放箭峰值低，取兩者中間。
 * - 靜止條件要讓正常射擊的放箭都算得到：看每一箭放箭前 0.5 秒內的最大合力，取第 90 百分位再留一點餘裕。
 *
 * 限制：正常射擊那一段只看得到「用目前設定偵測到」的箭，目前閾值太高而漏掉的箭看不到。
 */
object CalibrationAnalyzer {
    const val MIN_SHOTS = 4
    private const val MIN_WINDOW_SAMPLES = 10
    // 和 AppSettings、CounterViewModel 的範圍一致
    const val THRESHOLD_MIN = 6
    const val THRESHOLD_MAX = 40
    const val STILL_MIN = 2
    const val STILL_MAX = 10
    private const val OVERLAP_RATIO = 0.95f

    /** 解析原始資料 CSV 的各行（第一行是表頭，會略過）：取第 1 欄時間、第 5 欄合力。壞掉的行略過。 */
    fun parseCsv(lines: Sequence<String>): List<MagSample> {
        val out = ArrayList<MagSample>()
        for (line in lines) {
            val p = line.split(',')
            if (p.size < 5) continue
            val t = p[0].trim().toLongOrNull() ?: continue
            val m = p[4].trim().toFloatOrNull() ?: continue
            if (m.isNaN() || m.isInfinite()) continue
            out.add(MagSample(t, m))
        }
        return out
    }

    private fun percentile(values: List<Float>, p: Float): Float? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val idx = ((sorted.size - 1) * p).roundToInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    private fun segment(marks: List<Mark>, label: String, lastMs: Long): LongRange? {
        val m = marks.firstOrNull { it.label == label } ?: return null
        val end = m.endRelMs ?: lastMs
        return if (end > m.startRelMs) m.startRelMs..end else null
    }

    fun analyze(marks: List<Mark>, shots: List<ShotEntry>, samples: List<MagSample>): CalibAdvice {
        val none = CalibAdvice(CalibKind.NO_DATA, null, null, null, null, 0, false)
        if (samples.isEmpty()) return none
        val lastMs = samples.last().relMs
        val draw = segment(marks, Calibration.STEPS[0].label, lastMs)
        val normal = segment(marks, Calibration.STEPS[1].label, lastMs)
        val walk = segment(marks, Calibration.STEPS[2].label, lastMs)
        if (draw == null || normal == null || walk == null) return none

        val nonShotMags = samples.filter { it.relMs in draw || it.relMs in walk }.map { it.mag }
        val ceiling = percentile(nonShotMags, 0.99f) ?: return none

        val inNormal = shots.filter { !it.cancelled && it.relMs in normal }
        if (inNormal.size < MIN_SHOTS) {
            return CalibAdvice(CalibKind.NOT_ENOUGH_SHOTS, null, null, ceiling, null, inNormal.size, false)
        }
        val low = inNormal.minOf { it.peak }

        val overlap = ceiling >= low * OVERLAP_RATIO
        val threshold = if (overlap) null else ((ceiling + low) / 2f).roundToInt().coerceIn(THRESHOLD_MIN, THRESHOLD_MAX)

        // 靜止條件：每一箭放箭前（避開最近 GAP_MS）0.5 秒內的最大合力
        val maxima = inNormal.mapNotNull { sh ->
            val to = sh.relMs - ShotDetector.GAP_MS
            val from = to - ShotDetector.STILL_WINDOW_MS
            val w = samples.filter { it.relMs in (from + 1)..to }
            if (w.size < MIN_WINDOW_SAMPLES) null else w.maxOf { it.mag }
        }
        val still = if (maxima.size < MIN_SHOTS) {
            null
        } else {
            ceil(percentile(maxima, 0.9f)!! * 1.1f).toInt().coerceIn(STILL_MIN, STILL_MAX)
        }

        return CalibAdvice(CalibKind.OK, threshold, still, ceiling, low, inNormal.size, overlap)
    }
}
