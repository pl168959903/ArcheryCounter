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
