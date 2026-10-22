package com.zgrcc.archeryassistant

/** 這個 APK 是哪個發行版本（v1.23）：GitHub 版有檢查更新，Google Play 版沒有（由 Play 負責更新）。 */
object Edition {
    val updates: Boolean get() = BuildConfig.UPDATES

    fun label(): String =
        if (BuildConfig.UPDATES) tr("GitHub 版", "GitHub edition") else tr("Google Play 版", "Google Play edition")
}
