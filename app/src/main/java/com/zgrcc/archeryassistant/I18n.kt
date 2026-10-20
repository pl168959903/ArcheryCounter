package com.zgrcc.archeryassistant

/**
 * 介面語言（v1.9）。純 Kotlin，不依賴 Android，方便單元測試。
 *
 * 做法很簡單：畫面上每一句話都用 [tr] 同時寫中文和英文，依目前語言挑一個。
 * 語言變更時由 MainActivity 重新組合整個畫面，所以不用逐個畫面處理。
 */
object I18n {
    const val AUTO = 0
    const val ZH = 1
    const val EN = 2
    const val COUNT = 3

    /** 目前實際使用的語言：true = 英文。由 MainActivity 在語言設定變動時更新。 */
    @Volatile
    var english: Boolean = false

    /** 依語言設定與系統語言（例如 "zh"、"en"）決定是否用英文。自動：系統是中文就用中文，其餘都用英文。 */
    fun resolveEnglish(pref: Int, systemLanguage: String): Boolean = when (pref) {
        ZH -> false
        EN -> true
        else -> !systemLanguage.lowercase().startsWith("zh")
    }

    fun label(pref: Int): String = when (pref) {
        ZH -> "繁體中文"
        EN -> "English"
        else -> tr("自動（跟隨系統）", "Auto (system)")
    }

    fun next(pref: Int): Int = (pref + 1) % COUNT
}

/** 依目前語言挑中文或英文。 */
fun tr(zh: String, en: String): String = if (I18n.english) en else zh
