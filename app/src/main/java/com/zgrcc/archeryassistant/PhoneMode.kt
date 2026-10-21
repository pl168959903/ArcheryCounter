package com.zgrcc.archeryassistant

/**
 * 手機模式（v1.20）：App 也能裝在手機上，手機沒有戴在手腕上，偵測放箭沒有意義，
 * 所以手機模式隱藏開始偵測、比賽、校準入口，主畫面改成「從手錶匯入」與記錄檢視。純 Kotlin。
 */
object PhoneMode {
    const val AUTO = 0
    const val ON = 1
    const val OFF = 2
    const val COUNT = 3

    fun valid(v: Int): Int = if (v in 0 until COUNT) v else AUTO

    fun next(v: Int): Int = (valid(v) + 1) % COUNT

    /** [mode] 是設定值；[isWatch] 是系統回報這是不是手表。自動：不是手表就進手機模式。 */
    fun resolve(mode: Int, isWatch: Boolean): Boolean = when (valid(mode)) {
        ON -> true
        OFF -> false
        else -> !isWatch
    }

    fun label(v: Int): String = when (valid(v)) {
        ON -> tr("手機模式", "Phone mode")
        OFF -> tr("手錶模式", "Watch mode")
        else -> tr("自動", "Auto")
    }
}
