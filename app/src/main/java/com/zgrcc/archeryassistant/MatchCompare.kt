package com.zgrcc.archeryassistant

/** 比賽對比頁的一列：一場比賽。 */
data class MatchRow(
    val id: String,
    val startMs: Long,
    val arrowsPerEnd: Int,
    val secondsPerEnd: Int,
    /** 正式趟數 */
    val ends: Int,
    val totalArrows: Int,
    /** 有輸入環數才有；總環數 */
    val scoreTotal: Int?,
    /** 正式趟的平均加速度晃動；沒有數據為 null */
    val accelAvg: Float?,
    val timedOutEnds: Int,
)

/** 跨場比賽對比（v1.11）。純邏輯。 */
object MatchCompare {
    /** 對比頁最多列幾場（最近的） */
    const val LIMIT = 20

    /** 只取比賽紀錄（非校準、有比賽設定、已結束），由舊到新，最多 [limit] 場最近的。 */
    fun rows(sessions: List<Session>, limit: Int = LIMIT): List<MatchRow> =
        sessions
            .filter { !it.calibration && it.comp != null && it.endMs != null }
            .sortedBy { it.startMs }
            .mapNotNull { s ->
                val meta = s.comp ?: return@mapNotNull null
                val sum = CompSummaryBuilder.build(s) ?: return@mapNotNull null
                val formal = sum.ends.filter { !it.practice }
                val avgs = formal.mapNotNull { it.accelAvg }
                MatchRow(
                    id = s.id,
                    startMs = s.startMs,
                    arrowsPerEnd = meta.arrowsPerEnd,
                    secondsPerEnd = meta.secondsPerEnd,
                    ends = formal.size,
                    totalArrows = sum.totalArrows,
                    scoreTotal = sum.scoreTotal,
                    accelAvg = if (avgs.isEmpty()) null else avgs.average().toFloat(),
                    timedOutEnds = sum.timedOutEnds,
                )
            }
            .takeLast(limit)

    /** 同樣賽制（每趟箭數、趟數、秒數都相同）才好比較。 */
    fun sameFormat(a: MatchRow, b: MatchRow): Boolean =
        a.arrowsPerEnd == b.arrowsPerEnd && a.ends == b.ends && a.secondsPerEnd == b.secondsPerEnd

    /** 只留和最新一場賽制相同的。空清單回傳空清單。 */
    fun sameAsLatest(rows: List<MatchRow>): List<MatchRow> {
        val last = rows.lastOrNull() ?: return emptyList()
        return rows.filter { sameFormat(it, last) }
    }

    /** 趨勢圖用：平均晃動，略過沒數據的場次。 */
    fun shakeSeries(rows: List<MatchRow>): List<SessionPoint> =
        rows.mapNotNull { r -> r.accelAvg?.let { SessionPoint(r.startMs, it) } }

    /** 趨勢圖用：總環數，略過沒輸入環數的場次。 */
    fun scoreSeries(rows: List<MatchRow>): List<SessionPoint> =
        rows.mapNotNull { r -> r.scoreTotal?.let { SessionPoint(r.startMs, it.toFloat()) } }

    /** 最新一場和前一場的總環數差；任一場沒環數為 null。 */
    fun scoreDelta(rows: List<MatchRow>): Int? {
        if (rows.size < 2) return null
        val a = rows[rows.size - 1].scoreTotal ?: return null
        val b = rows[rows.size - 2].scoreTotal ?: return null
        return a - b
    }
}
