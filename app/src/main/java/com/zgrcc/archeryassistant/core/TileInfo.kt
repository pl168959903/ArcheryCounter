package com.zgrcc.archeryassistant

/** 磁貼（v1.11）與應用之間約定的字串，以及磁貼上的文字。純 Kotlin。 */
object TileInfo {
    /** 啟動 Intent 的額外欄位名稱 */
    const val EXTRA_LAUNCH = "launch"
    const val LAUNCH_DETECT = "detect"
    const val LAUNCH_MATCH = "match"

    /** 只認得約定的兩個值，其他一律忽略。 */
    fun parse(value: String?): String? = when (value) {
        LAUNCH_DETECT, LAUNCH_MATCH -> value
        else -> null
    }

    /** 磁貼上的「今日」那一行。有設目標才顯示「/目標」。 */
    fun todayLine(done: Int, goal: Int): String {
        val d = done.coerceAtLeast(0)
        return if (goal > 0) tr("今日 $d / $goal 箭", "Today $d / $goal") else tr("今日 $d 箭", "Today $d arrows")
    }
}
