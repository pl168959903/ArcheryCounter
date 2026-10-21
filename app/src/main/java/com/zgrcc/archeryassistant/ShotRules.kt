package com.zgrcc.archeryassistant

/**
 * 單箭層級的可疑判斷與箭序分析（v1.19.4）。純 Kotlin。
 */

/** 間隔過短又幾乎沒有放箭後跟隨動作的箭，多半是碰到弓或動作造成的誤判。 */
object FastShot {
    const val MIN_GAPS = 5
    const val GAP_RATIO = 0.5f
    const val FOLLOW_RATIO = 0.6f
    /** 沒有跟隨量可比時，間隔要更短才標記 */
    const val GAP_RATIO_NO_FOLLOW = 0.4f

    private fun median(v: List<Float>): Float {
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2f
    }

    /**
     * 回傳可疑箭在 [Session.shots] 裡的位置。只看有效的箭；間隔是和上一個有效箭的距離（超過一趟間隔的不算）。
     * 校準不判斷；樣本不足（間隔少於 [MIN_GAPS] 個）不判斷。
     */
    fun flagged(s: Session): Set<Int> {
        if (s.calibration) return emptySet()
        val idx = s.shots.indices.filter { !s.shots[it].cancelled }
        if (idx.size < 2) return emptySet()
        val gaps = ArrayList<Pair<Int, Float>>() // (shots 位置, 距上一箭毫秒)
        for (k in 1 until idx.size) {
            val g = s.shots[idx[k]].relMs - s.shots[idx[k - 1]].relMs
            if (g in 1 until END_GAP_MS) gaps.add(idx[k] to g.toFloat())
        }
        if (gaps.size < MIN_GAPS) return emptySet()
        val medGap = median(gaps.map { it.second })
        val follows = idx.mapNotNull { s.shots[it].followAccelRms }
        val medFollow = if (follows.size >= MIN_GAPS) median(follows) else null
        val out = LinkedHashSet<Int>()
        for ((i, g) in gaps) {
            val f = s.shots[i].followAccelRms
            val hit = if (f != null && medFollow != null) {
                g < medGap * GAP_RATIO && f < medFollow * FOLLOW_RATIO
            } else {
                g < medGap * GAP_RATIO_NO_FOLLOW
            }
            if (hit && !s.shots[i].reviewed) out.add(i)
        }
        return out
    }

    fun text(): String = tr(
        "可疑：離上一箭太近，放箭後又幾乎沒動作，可能是誤判",
        "Suspicious: very close to the previous shot with almost no follow-through; possibly a false detection",
    )

    fun shortText(): String = tr("⚠ 可疑", "⚠ Suspicious")
}

/** 一趟裡第幾箭的平均表現（v1.19.4）。 */
data class PosRow(val pos: Int, val n: Int, val accel: Float?, val gyro: Float?, val follow: Float?)

object PositionTrend {
    const val MIN_N = 3
    const val MAX_POS = 8
    private const val HINT_RATIO = 1.15f

    private fun avg(v: List<Float?>): Float? {
        val x = v.filterNotNull()
        return if (x.isEmpty()) null else x.average().toFloat()
    }

    /** 每趟內有效箭的順位（1 起算）：兩箭間隔超過一趟間隔就重新算。校準略過。順位樣本少於 [MIN_N] 的不列。 */
    fun rows(sessions: List<Session>): List<PosRow> {
        val byPos = HashMap<Int, MutableList<ShotEntry>>()
        for (s in sessions) {
            if (s.calibration) continue
            var pos = 0
            var last = Long.MIN_VALUE
            for (sh in s.validShots()) {
                pos = if (last == Long.MIN_VALUE || sh.relMs - last > END_GAP_MS) 1 else pos + 1
                last = sh.relMs
                if (pos <= MAX_POS) byPos.getOrPut(pos) { ArrayList() }.add(sh)
            }
        }
        return (1..MAX_POS).mapNotNull { p ->
            val l = byPos[p] ?: return@mapNotNull null
            if (l.size < MIN_N) null
            else PosRow(p, l.size, avg(l.map { it.accelRms }), avg(l.map { it.gyroRms }), avg(l.map { it.followAccelRms }))
        }
    }

    /** 後段（最後 2 個順位）的加速度晃動比第 1～2 箭大 15% 以上時給一句提示；資料不足回傳 null。 */
    fun hint(rows: List<PosRow>): String? {
        if (rows.size < 4) return null
        val head = avg(rows.take(2).map { it.accel }) ?: return null
        val tail = avg(rows.takeLast(2).map { it.accel }) ?: return null
        if (head <= 0f || tail < head * HINT_RATIO) return null
        val from = rows[rows.size - 2].pos
        return tr(
            "第 $from 箭之後晃動比前面大 ${((tail / head - 1f) * 100).toInt()}%，可能累了，試試每趟少射幾箭",
            "From shot $from on, shake is ${((tail / head - 1f) * 100).toInt()}% higher than the first shots; you may be tiring. Try fewer arrows per end",
        )
    }

    fun rowText(r: PosRow, hasGyro: Boolean): String = buildString {
        append(tr("每趟第 ${r.pos} 箭（${r.n} 支）", "Shot ${r.pos} of each end (${r.n})"))
        append(tr("\n平均晃動 ${Fmt.numOrDash(r.accel)}", "\nAvg shake ${Fmt.numOrDash(r.accel)}"))
        if (hasGyro) append(tr("　角速度 ${Fmt.numOrDash(r.gyro, 1)}", "  gyro ${Fmt.numOrDash(r.gyro, 1)}"))
        if (r.follow != null) append(tr("　放箭後 ${Fmt.num(r.follow)}", "  after release ${Fmt.num(r.follow)}"))
    }

    /** 標題下的說明，詳情與跨次趨勢共用。 */
    fun explain(): String = tr(
        "把每一趟的第 1 箭、第 2 箭……分別平均，看越後面的箭是不是越抖（可能累了）。晃動越小越穩；每個順位至少 3 支才列",
        "Averages the 1st, 2nd, 3rd… shot of every end to see if later shots get shakier (tiring). Lower shake is steadier; a position needs at least 3 shots",
    )
}

/** 跨次趨勢的另外兩條線（v1.19.4）：節奏與放箭後跟隨量。 */
object SessionExtras {
    /** 一次記錄裡「趟內」兩箭間隔的中位數（秒）；少於 3 個間隔回傳 null。 */
    fun medianGapSec(s: Session): Float? {
        val v = s.validShots()
        val gaps = (1 until v.size).map { v[it].relMs - v[it - 1].relMs }.filter { it in 1 until END_GAP_MS }.sorted()
        if (gaps.size < 3) return null
        val m = if (gaps.size % 2 == 1) gaps[gaps.size / 2].toFloat() else (gaps[gaps.size / 2 - 1] + gaps[gaps.size / 2]) / 2f
        return m / 1000f
    }

    /** 最近 [limit] 次記錄的節奏（秒），由舊到新；[tags] 不為 null 時只留相同條件。 */
    fun paceTrend(sessions: List<Session>, limit: Int = 20, tags: List<String>? = null): List<SessionPoint> =
        sessions.filter { !it.calibration && (tags == null || Tags.same(it.tags, tags)) }
            .mapNotNull { s -> medianGapSec(s)?.let { SessionPoint(s.startMs, it) } }
            .sortedBy { it.startMs }.takeLast(limit)

    /** 最近 [limit] 次記錄的放箭後晃動量平均，由舊到新。 */
    fun followTrend(sessions: List<Session>, limit: Int = 20, tags: List<String>? = null): List<SessionPoint> =
        sessions.filter { !it.calibration && (tags == null || Tags.same(it.tags, tags)) }
            .mapNotNull { s -> computeStats(s).followAvg?.let { SessionPoint(s.startMs, it) } }
            .sortedBy { it.startMs }.takeLast(limit)
}

/** 單箭詳情的換箭（v1.20.1）：在 [list]（箭的位置，由小到大）裡找上一個／下一個。純 Kotlin。 */
object ShotNav {
    /** 往後（[dir] = 1）或往前（−1）一個；到頭回傳 [current]（不動）。[current] 不在清單裡時，取最接近的那一個。 */
    fun step(list: List<Int>, current: Int, dir: Int): Int {
        if (list.isEmpty()) return current
        return if (dir >= 0) list.firstOrNull { it > current } ?: current
        else list.lastOrNull { it < current } ?: current
    }

    /** 處理完 [from] 之後要跳到哪一支：先找後面的，沒有再找前面的；清單空了回傳 null。 */
    fun nextAfter(list: List<Int>, from: Int): Int? =
        list.firstOrNull { it > from } ?: list.firstOrNull { it < from }

    /** 點螢幕的位置在左半邊回傳 −1（上一箭），右半邊回傳 1（下一箭）；寬度無效當作右半邊。 */
    fun tapDir(x: Float, width: Float): Int = if (width > 0f && x < width / 2f) -1 else 1

    /** 「3 / 96」：[current] 在 [list] 裡的順位（找不到顯示 −）。 */
    fun progress(list: List<Int>, current: Int): String {
        val i = list.indexOf(current)
        return (if (i < 0) "-" else (i + 1).toString()) + " / " + list.size
    }
}
