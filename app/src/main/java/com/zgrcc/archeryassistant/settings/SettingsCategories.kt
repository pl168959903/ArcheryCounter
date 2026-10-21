package com.zgrcc.archeryassistant

/**
 * 設定頁的分類（v1.9.2）。設定頁第一層是這些分類的按鈕，點進去才是該類的項目。純 Kotlin。
 * 順序就是畫面上的順序；[names] 的長度要等於 [COUNT]。
 */
object SettingsCategories {
    const val GENERAL = 0
    const val DETECTION = 1
    const val RECORDS = 2
    const val TRAINING = 3
    const val MATCH = 4
    const val CHARTS = 5
    const val DATA = 6
    const val CONNECT = 7
    const val RESET = 8
    const val ABOUT = 9
    const val COUNT = 10

    fun name(category: Int): String = when (category) {
        GENERAL -> tr("一般", "General")
        DETECTION -> tr("偵測", "Detection")
        RECORDS -> tr("記錄", "Records")
        TRAINING -> tr("訓練", "Training")
        MATCH -> tr("比賽模式", "Match mode")
        CHARTS -> tr("圖表與顯示", "Charts & display")
        DATA -> tr("資料", "Data")
        CONNECT -> tr("連線", "Connection")
        RESET -> tr("重設與備份", "Reset & backup")
        ABOUT -> tr("關於", "About")
        else -> ""
    }

    /** 各分類導覽路由，例如 settings_3 */
    fun route(category: Int): String = "settings_$category"

    fun valid(category: Int): Boolean = category in 0 until COUNT
}
