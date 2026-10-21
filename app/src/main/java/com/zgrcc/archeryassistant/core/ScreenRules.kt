package com.zgrcc.archeryassistant

/**
 * 畫面「什麼時候顯示什麼」的判斷（v1.15 從畫面程式拆出），純函式，可以直接單元測試。
 */
object ScreenRules {
    /** 記錄列表上方的功能入口：哪些要顯示。 */
    data class HistoryEntries(val trend: Boolean, val matchCompare: Boolean, val groups: Boolean, val overview: Boolean)

    /** 管理模式（勾選刪除）時四個入口都不顯示。 */
    fun historyEntries(
        sessions: List<Session>,
        manage: Boolean,
        trendSetting: Boolean,
        matchCompareSetting: Boolean,
        groupCompareSetting: Boolean,
        overviewSetting: Boolean,
    ): HistoryEntries {
        val matchCount = sessions.count { !it.calibration && it.comp != null && it.endMs != null }
        return HistoryEntries(
            trend = !manage && trendSetting && sessions.size >= 2,
            matchCompare = !manage && matchCompareSetting && matchCount >= 1,
            groups = !manage && groupCompareSetting && GroupCompare.groups(sessions).isNotEmpty(),
            overview = !manage && overviewSetting && sessions.any { !it.calibration },
        )
    }

    /** 單箭頁（和快速檢查）是否顯示「自評」：設定開啟，或這一箭已經有自評；誤判的箭不評。 */
    fun showFeelRow(setting: Boolean, shot: ShotEntry): Boolean =
        (setting || Feel.valid(shot.feel) != null) && !shot.cancelled

    /** 快速檢查只在設定開啟時顯示自評按鈕（不因為已有自評而強制出現）。 */
    fun showFeelRowInQuickCheck(setting: Boolean, shot: ShotEntry): Boolean = setting && !shot.cancelled

    /** 校準分析按鈕：設定開啟、是校準記錄、有原始資料，而且已經結束（進行中的不能分析）。 */
    fun showCalibrationAdvice(setting: Boolean, session: Session, isActive: Boolean): Boolean =
        setting && session.calibration && session.rawCsv && !isActive && session.endMs != null
}

/** 記錄頁的入口分組（v1.18.3）。純規則。 */
object HistoryGroups {
    /** 「趨勢」入口：跨次趨勢或訓練總覽任一個可用就顯示。 */
    fun trendVisible(e: ScreenRules.HistoryEntries) = e.trend || e.overview

    /** 「對比」入口：比賽對比或條件對比任一個可用就顯示。 */
    fun compareVisible(e: ScreenRules.HistoryEntries) = e.matchCompare || e.groups
}
