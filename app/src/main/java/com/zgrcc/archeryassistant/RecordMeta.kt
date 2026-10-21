package com.zgrcc.archeryassistant

import java.util.Locale

/** 記錄檔的來源資訊（v1.18.1）。純 Kotlin。 */
object RecordMeta {

    /** 偵測設定摘要，例：「進階 開 · 50Hz · 閾值 12.0 · 視窗 10s」。 */
    fun detect(advanced: Boolean, hz: Int, threshold: Float, windowSec: Int): String =
        "adv=" + (if (advanced) "on" else "off") + ";hz=$hz;thr=" +
            String.format(Locale.US, "%.1f", threshold) + ";win=${windowSec}s"

    /** 在偵測摘要後面加上實測取樣率（Hz，四捨五入）；量不到（≤0）就原樣回傳，已有就不重複加。 */
    fun withMeasured(det: String, measuredHz: Float): String {
        if (measuredHz < 1f || det.isEmpty() || det.contains("real=")) return det
        return det + ";real=" + Math.round(measuredHz)
    }

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
            m["hz"]?.let { hz ->
                val real = m["real"]
                add(if (real == null || real == hz) "$hz Hz" else tr("$hz Hz（實測 $real Hz）", "$hz Hz (measured $real Hz)"))
            }
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

    /** 同 id 的兩筆記錄是否其實是不同裝置的（雙方都有唯一碼且不同）。 */
    fun clashes(mine: String, theirs: String): Boolean = mine.isNotEmpty() && theirs.isNotEmpty() && mine != theirs

    /** 匯入撞號時的新 id。 */
    fun collisionId(id: String, uid: String): String = id + "_" + uid.take(4)
}
