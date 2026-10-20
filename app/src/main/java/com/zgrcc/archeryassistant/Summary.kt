package com.zgrcc.archeryassistant

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 跨次比较用：一次记录一个点。 */
data class SessionPoint(val startMs: Long, val value: Float)

/**
 * 最近 [limit] 次记录的平均晃动量，由旧到新。校准录制不算。没有数据（没有任何有效的箭有晃动量）的记录略过。
 */
fun sessionTrend(
    sessions: List<Session>,
    gyro: Boolean = false,
    limit: Int = 20,
    /** 不为 null 时，只留条件标签和它相同的记录 */
    tags: List<String>? = null,
): List<SessionPoint> =
    sessions
        .filter { !it.calibration && (tags == null || Tags.same(it.tags, tags)) }
        .mapNotNull { s ->
            val st = computeStats(s)
            val v = if (gyro) st.gyroAvg else st.accelAvg
            if (v == null) null else SessionPoint(s.startMs, v)
        }
        .sortedBy { it.startMs }
        .takeLast(limit)

/** 一段序列里最小（最稳）和最大（最不稳）的点，编号从 1 开始。少于 2 点回传 (null, null)。 */
fun extremes(values: List<Float>): Pair<ShotRank?, ShotRank?> {
    if (values.size < 2) return null to null
    val ranked = values.mapIndexed { i, v -> ShotRank(i + 1, v) }
    return ranked.minBy { it.value } to ranked.maxBy { it.value }
}

/** 匯出成 CSV：一次记录一行（summary），和每一箭一行（shots）。UTF-8，数字一律用小数点。 */
object ExportCsv {

    const val SUMMARY_FILE = "summary.csv"
    const val SHOTS_FILE = "shots.csv"

    private fun f(v: Float?, decimals: Int = 3): String =
        if (v == null || v.isNaN() || v.isInfinite()) "" else String.format(Locale.US, "%.${decimals}f", v)

    private fun time(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    fun summary(sessions: List<Session>): String = buildString {
        append("id,start,duration_s,detected,cancelled,manual_plus,manual_minus,final_count,")
        append("threshold_min,threshold_max,peak_avg,interval_avg_s,")
        append("accel_rms_avg,gyro_rms_avg,follow_accel_avg,follow_gyro_avg,scored,score_avg,tags,calibration\n")
        for (s in sessions.sortedBy { it.startMs }) {
            val st = computeStats(s)
            append(s.id).append(',')
            append(time(s.startMs)).append(',')
            append(s.durationMs() / 1000).append(',')
            append(st.detected).append(',')
            append(st.cancelled).append(',')
            append(st.manualPlus).append(',')
            append(st.manualMinus).append(',')
            append(st.finalCount).append(',')
            append(f(st.thresholdMin, 1)).append(',')
            append(f(st.thresholdMax, 1)).append(',')
            append(f(st.peakAvg, 2)).append(',')
            append(f(st.intervalAvgSec, 1)).append(',')
            append(f(st.accelAvg)).append(',')
            append(f(st.gyroAvg)).append(',')
            append(f(st.followAvg)).append(',')
            append(f(st.followGyroAvg)).append(',')
            append(st.scoredCount).append(',')
            append(f(st.scoreAvg, 2)).append(',')
            append(s.tags.joinToString("|")).append(',')
            append(if (s.calibration) 1 else 0).append('\n')
        }
    }

    /** no = 有效的第几箭；误判的箭 no 留空、cancelled = 1。 */
    fun shots(sessions: List<Session>): String = buildString {
        append("session,no,time,rel_ms,peak,threshold,accel_rms,gyro_rms,")
        append("follow_accel_rms,follow_gyro_rms,score,cancelled\n")
        for (s in sessions.sortedBy { it.startMs }) {
            var no = 0
            for (sh in s.shots) {
                if (!sh.cancelled) no++
                append(s.id).append(',')
                append(if (sh.cancelled) "" else no.toString()).append(',')
                append(time(s.startMs + sh.relMs)).append(',')
                append(sh.relMs).append(',')
                append(f(sh.peak, 2)).append(',')
                append(f(sh.threshold, 1)).append(',')
                append(f(sh.accelRms)).append(',')
                append(f(sh.gyroRms)).append(',')
                append(f(sh.followAccelRms)).append(',')
                append(f(sh.followGyroRms)).append(',')
                append(sh.score?.toString() ?: "").append(',')
                append(if (sh.cancelled) 1 else 0).append('\n')
            }
        }
    }
}
