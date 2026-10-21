package com.zgrcc.archeryassistant

/**
 * 分趟统计。射箭通常一趟射 3 或 6 箭，射完去取箭，再射下一趟。
 *
 * 分趟的规则（即时震动提醒和事后统计用同一套）：
 *   - 一趟射满 [arrowsPerEnd] 箭，下一箭就算新的一趟；
 *   - 两箭间隔超过 [END_GAP_MS]（预设 60 秒），也算新的一趟（中间去取箭了）。
 * 分趟是从现有数据算出来的，不另外存档，所以旧记录也有。
 */
const val END_GAP_MS = 60_000L

/** 一趟的统计。[firstShot]、[lastShot] 是有效的第几箭（从 1 开始）。 */
data class EndStats(
    val number: Int,
    val firstShot: Int,
    val lastShot: Int,
    val count: Int,
    val startRelMs: Long,
    val endRelMs: Long,
    val accelAvg: Float?,
    val gyroAvg: Float?,
    val followAvg: Float?,
    /** 这一趟已输入的环数合计；一箭都没输入为 null */
    val scoreTotal: Int?,
)

fun computeEnds(s: Session, arrowsPerEnd: Int, gapMs: Long = END_GAP_MS): List<EndStats> {
    val perEnd = arrowsPerEnd.coerceAtLeast(1)
    val valid = s.validShots()

    // 每个元素是 (有效的第几箭, 那一箭)
    val groups = ArrayList<MutableList<Pair<Int, ShotEntry>>>()
    valid.forEachIndexed { i, sh ->
        val cur = groups.lastOrNull()
        val startNew = cur == null ||
            cur.size >= perEnd ||
            sh.relMs - cur.last().second.relMs > gapMs
        if (startNew) groups.add(ArrayList())
        groups.last().add((i + 1) to sh)
    }

    return groups.mapIndexed { idx, g ->
        val shots = g.map { it.second }
        val scores = shots.mapNotNull { it.score }
        EndStats(
            number = idx + 1,
            firstShot = g.first().first,
            lastShot = g.last().first,
            count = g.size,
            startRelMs = shots.first().relMs,
            endRelMs = shots.last().relMs,
            accelAvg = avgOrNull(shots.map { it.accelRms }),
            gyroAvg = avgOrNull(shots.map { it.gyroRms }),
            followAvg = avgOrNull(shots.map { it.followAccelRms }),
            scoreTotal = if (scores.isEmpty()) null else scores.sum(),
        )
    }
}

/**
 * 即时的「这一趟射到第几箭了」，用来在满一趟时震动提醒。
 * 传感器线程（偵測到放箭）和主线程（按「−」取消）都会用，所以加了同步锁。
 */
class EndTracker(private val gapMs: Long = END_GAP_MS) {
    private var count = 0
    private var lastMs = 0L

    @Synchronized
    fun reset() {
        count = 0
        lastMs = 0L
    }

    /** 记一箭。回传 true 表示这一箭让这一趟满了。 */
    @Synchronized
    fun onShot(timeMs: Long, arrowsPerEnd: Int): Boolean {
        val perEnd = arrowsPerEnd.coerceAtLeast(1)
        if (count >= perEnd || (count > 0 && timeMs - lastMs > gapMs)) count = 0
        count++
        lastMs = timeMs
        return count == perEnd
    }

    /** 最近一箭被取消（按了「−」）：这一趟少一箭。 */
    @Synchronized
    fun cancelLast() {
        if (count > 0) count--
    }
}

/**
 * 自動分趟（v1.19.4）：不看每趟固定箭數，只看兩箭的間隔。
 * 門檻 = 趟內間隔中位數的 [RATIO] 倍，且至少 [MIN_GAP_MS]；趟內間隔樣本少於 [MIN_GAPS] 個時用 [END_GAP_MS]。
 */
object AutoEnds {
    const val RATIO = 2.5f
    const val MIN_GAP_MS = 35_000L
    const val MIN_GAPS = 3

    fun gapMs(s: Session): Long {
        val v = s.validShots()
        val gaps = (1 until v.size).map { v[it].relMs - v[it - 1].relMs }.filter { it in 1..END_GAP_MS }.sorted()
        if (gaps.size < MIN_GAPS) return END_GAP_MS
        val med = if (gaps.size % 2 == 1) gaps[gaps.size / 2].toFloat() else (gaps[gaps.size / 2 - 1] + gaps[gaps.size / 2]) / 2f
        return maxOf(MIN_GAP_MS, (med * RATIO).toLong())
    }

    fun compute(s: Session): List<EndStats> = computeEnds(s, Int.MAX_VALUE / 2, gapMs(s))

    /** 「共 12 趟：6、8、8… 箭」；沒有箭回傳空字串。 */
    fun summary(ends: List<EndStats>): String =
        if (ends.isEmpty()) "" else tr(
            "共 ${ends.size} 趟：" + ends.joinToString("、") { it.count.toString() } + " 箭",
            "${ends.size} ends: " + ends.joinToString(", ") { it.count.toString() } + " arrows",
        )

    /** 有效的第 [shotNo] 箭是「第幾趟-第幾箭」，例如「3-5」；找不到回傳 null。 */
    fun label(ends: List<EndStats>, shotNo: Int): String? {
        val e = ends.firstOrNull { shotNo in it.firstShot..it.lastShot } ?: return null
        return "${e.number}-${shotNo - e.firstShot + 1}"
    }
}

/** 這一份記錄目前顯示用的分趟：開了自動分趟就依間隔，否則依每趟箭數（v1.20.1 起詳情與單箭頁共用）。 */
fun endsFor(s: Session, auto: Boolean, arrowsPerEnd: Int): List<EndStats> =
    if (auto) AutoEnds.compute(s) else computeEnds(s, arrowsPerEnd)

/** [index]（在 [Session.shots] 裡的位置）這一箭是「第幾趟-第幾箭」，例如「9-2」；誤判或找不到回傳 null。 */
fun shotEndLabel(s: Session, ends: List<EndStats>, index: Int): String? {
    val sh = s.shots.getOrNull(index) ?: return null
    if (sh.cancelled) return null
    val validNo = s.shots.take(index + 1).count { !it.cancelled }
    return AutoEnds.label(ends, validNo)
}
