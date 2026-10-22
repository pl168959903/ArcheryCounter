package com.zgrcc.archeryassistant

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 手機畫面的記錄匯出（v1.24）：把選到的記錄打包成一個 zip。純 Kotlin。
 * 內容：summary.csv（每趟摘要）、shots.csv（每一箭）、report.html（網頁報告）、每筆的 <id>.json（記錄檔，可再匯入），
 * 勾選「含原始資料」時再加上有原始資料的 <id>.csv。檔名全部是 [ShareRouter.isSafeName] 接受的名稱。
 */
object RecordExport {
    const val ZIP_MIME = "application/zip"

    /** 依開始時間由舊到新，只留 [ids] 裡的。 */
    fun pick(all: List<Session>, ids: Set<String>): List<Session> =
        all.filter { it.id in ids }.sortedBy { it.startMs }

    /** zip 裡的檔案名稱與順序。[rawIds] 是有原始資料檔的記錄；[withRaw] 為 false 時一律不含。 */
    fun entryNames(ids: List<String>, rawIds: Set<String>, withRaw: Boolean): List<String> = buildList {
        add(ExportCsv.SUMMARY_FILE)
        add(ExportCsv.SHOTS_FILE)
        add(ReportBuilder.FILE_NAME)
        ids.forEach { add("$it.json") }
        if (withRaw) ids.filter { it in rawIds }.forEach { add("$it.csv") }
    }

    /** 匯出檔名：單筆 ArcheryAssistant_<id>.zip；多筆 ArcheryAssistant_<日期時間>_<N>records.zip。 */
    fun zipName(ids: List<String>, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): String {
        if (ids.size == 1) return "ArcheryAssistant_${ids[0]}.zip"
        val fmt = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).apply { timeZone = zone }
        return "ArcheryAssistant_${fmt.format(Date(nowMs))}_${ids.size}records.zip"
    }

    /** 有沒有任何一筆被選到的記錄帶原始資料（決定要不要顯示「含原始資料」）。 */
    fun anyRaw(all: List<Session>, ids: Set<String>): Boolean = all.any { it.id in ids && it.rawCsv }
}
