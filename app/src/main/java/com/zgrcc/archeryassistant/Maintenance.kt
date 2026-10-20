package com.zgrcc.archeryassistant

/*
 * v1.14 的資料管理與自評：自動清理舊資料、每箭自評。純 Kotlin，有單元測試。
 */

// ---------- 自動清理 ----------

data class CleanResult(val count: Int, val bytes: Long, val atMs: Long)

object AutoClean {
    val DAY_OPTIONS = listOf(30, 60, 90)
    const val DEFAULT_DAYS = 60

    fun validDays(days: Int): Int = if (days in DAY_OPTIONS) days else DEFAULT_DAYS

    /** 依序切換 30 → 60 → 90 → 30。 */
    fun nextDays(days: Int): Int {
        val i = DAY_OPTIONS.indexOf(validDays(days))
        return DAY_OPTIONS[(i + 1) % DAY_OPTIONS.size]
    }

    /** 早於這個時間（毫秒）開始的記錄才會被清理。 */
    fun cutoff(nowMs: Long, days: Int): Long = nowMs - validDays(days) * 24L * 60 * 60 * 1000

    /**
     * 這筆記錄需不需要清理：夠舊、已經結束，且還有原始資料、波形或 2D 軌跡可以清。
     * 校準記錄的原始資料是分析用的（校準自動建議），永遠不清。進行中的記錄也不清。
     */
    fun needsClean(s: Session, cutoffMs: Long): Boolean {
        if (s.calibration || s.endMs == null) return false
        if (s.startMs >= cutoffMs) return false
        return s.rawCsv || s.shots.any { it.waveform != null || it.trace != null }
    }

    /** 去掉波形、2D 軌跡和「有原始資料」標記，其他（箭數、晃動量、環數、自評…）都保留。 */
    fun strip(s: Session): Session = s.copy(
        rawCsv = false,
        shots = s.shots.map { it.copy(waveform = null, trace = null) },
    )

    fun describe(r: CleanResult?, formatBytes: (Long) -> String): String =
        if (r == null) {
            tr("還沒有自動清理過", "Nothing cleaned automatically yet")
        } else {
            tr("上次自動清理 ${Fmt.dateTime(r.atMs)}：${r.count} 筆，釋出 ${formatBytes(r.bytes)}", "Last auto-clean ${Fmt.dateTime(r.atMs)}: ${r.count} records, freed ${formatBytes(r.bytes)}")
        }

    fun encode(r: CleanResult): String = "${r.atMs},${r.count},${r.bytes}"

    fun decode(text: String?): CleanResult? {
        val p = text?.split(',') ?: return null
        if (p.size != 3) return null
        val at = p[0].toLongOrNull() ?: return null
        val c = p[1].toIntOrNull() ?: return null
        val b = p[2].toLongOrNull() ?: return null
        return CleanResult(c, b, at)
    }
}

// ---------- 每箭自評 ----------

object Feel {
    const val BAD = 1
    const val OK = 2
    const val GOOD = 3

    fun valid(v: Int?): Int? = v?.takeIf { it in BAD..GOOD }

    fun label(v: Int): String = when (v) {
        BAD -> tr("差", "Bad")
        OK -> tr("普通", "OK")
        else -> tr("好", "Good")
    }

    /** 點已選的那個就取消；點別的就換成別的。 */
    fun toggle(current: Int?, tapped: Int): Int? = if (current == tapped) null else tapped
}

/** 某一種自評的箭數與平均加速度晃動。 */
data class FeelGroup(val feel: Int, val count: Int, val accelAvg: Float?)

object FeelStats {
    /** 有自評的有效箭，各分類（差／普通／好）的箭數與平均晃動；沒有自評回傳空清單。 */
    fun groups(s: Session): List<FeelGroup> {
        val valid = s.validShots().filter { Feel.valid(it.feel) != null }
        return listOf(Feel.GOOD, Feel.OK, Feel.BAD).mapNotNull { f ->
            val list = valid.filter { it.feel == f }
            if (list.isEmpty()) null else FeelGroup(f, list.size, avgOrNull(list.map { it.accelRms }))
        }
    }
}
