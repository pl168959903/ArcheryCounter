package com.zgrcc.archeryassistant

/**
 * 震动样式。手表的震动马达大多不能调「频率」，只能控制震多久、停多久、重复几次，
 * 所以用节奏来区分：放箭是一次很短的轻震，满一趟是明显不同的节奏。
 * 纯 Kotlin，方便测试；实际播放在 CounterViewModel。
 */
object EndVibration {
    const val THREE = 0
    const val TWO_SHORT_LONG = 1
    const val LONG = 2
    const val OFF = 3

    const val DEFAULT = THREE
    const val STYLE_COUNT = 4

    /** 每一箭的震动：单次，毫秒 */
    const val SHOT_MS = 60L

    /**
     * 满趟的震动时间表（毫秒，Android 格式：先等待、再震、再停、再震…）。
     * 关闭时回传 null。
     */
    fun pattern(style: Int): LongArray? = when (style) {
        THREE -> longArrayOf(0, 150, 100, 150, 100, 150)
        TWO_SHORT_LONG -> longArrayOf(0, 100, 80, 100, 80, 350)
        LONG -> longArrayOf(0, 500)
        else -> null
    }

    fun label(style: Int): String = when (style) {
        THREE -> tr("三連震", "Triple buzz")
        TWO_SHORT_LONG -> tr("兩短一長", "Two short, one long")
        LONG -> tr("一次長震", "One long buzz")
        else -> tr("關閉", "Off")
    }

    /** 比賽模式的震動：開始射擊（一長）、剩 30 秒（一短）、剩 10 秒（兩短）、時間到（長—短—長）。 */
    val COMP_GO = longArrayOf(0, 600)
    val COMP_30 = longArrayOf(0, 120)
    val COMP_10 = longArrayOf(0, 120, 100, 120)
    val COMP_ARROW = longArrayOf(0, 250)
    val COMP_TIME_UP = longArrayOf(0, 500, 150, 120, 150, 500)

    /** 设定页「下一个样式」，循环切换。 */
    fun next(style: Int): Int = (style + 1) % STYLE_COUNT
}
