package com.zgrcc.archeryassistant

import java.util.Locale

/** 記錄檔的來源資訊（v1.18.1）。純 Kotlin。 */
object RecordMeta {

    /** 偵測設定摘要，例：「進階 開 · 50Hz · 閾值 12.0 · 視窗 10s」。 */
    fun detect(advanced: Boolean, hz: Int, threshold: Float, windowSec: Int): String =
        "adv=" + (if (advanced) "on" else "off") + ";hz=$hz;thr=" +
            String.format(Locale.US, "%.1f", threshold) + ";win=${windowSec}s"

    /** 把摘要轉成給人看的文字；無法解析就原樣回傳。 */
    fun detectText(det: String): String {
        if (det.isEmpty()) return ""
        val m = det.split(';').mapNotNull { p ->
            val i = p.indexOf('=')
            if (i <= 0) null else p.substring(0, i) to p.substring(i + 1)
        }.toMap()
        if (m.isEmpty()) return det
        val parts = buildList {
            m["adv"]?.let { add(tr("進階判定", "Advanced") + " " + if (it == "on") tr("開", "on") else tr("關", "off")) }
            m["hz"]?.let { add("$it Hz") }
            m["thr"]?.let { add(tr("閾值", "Threshold") + " $it") }
            m["win"]?.let { add(tr("視窗", "Window") + " $it") }
        }
        return parts.joinToString(" · ")
    }

    /** 報告用的一行來源說明；全部為空回傳空字串。 */
    fun sourceLine(s: Session): String {
        val parts = buildList {
            if (s.app.isNotEmpty()) add("App ${s.app}")
            if (s.dev.isNotEmpty()) add(s.dev)
            val d = detectText(s.det)
            if (d.isNotEmpty()) add(d)
            if (s.uid.isNotEmpty()) add("ID ${s.uid}")
        }
        return parts.joinToString(" · ")
    }

    /** 匯入撞號時的新 id。 */
    fun collisionId(id: String, uid: String): String = id + "_" + uid.take(4)
}
