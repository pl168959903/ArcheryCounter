package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class V1181bTest {
    private fun base(id: String, uid: String = "", app: String = "", dev: String = "", det: String = "") =
        Session(id, 0, 5000, 16f, 2, false, false, listOf(ShotEntry(1000, 20f, 16f, 1f, null)), emptyList(),
            app = app, dev = dev, uid = uid, det = det)

    @Test
    fun roundTripKeepsMeta() {
        val s = base("a", "ab12cd34", "1.18.1", "Google Pixel Watch", RecordMeta.detect(true, 50, 12f, 10))
        val d = SessionJson.decode(SessionJson.encode(s), "a")!!
        assertEquals("1.18.1", d.app)
        assertEquals("Google Pixel Watch", d.dev)
        assertEquals("ab12cd34", d.uid)
        assertEquals("adv=on;hz=50;thr=12.0;win=10s", d.det)
    }

    @Test
    fun oldRecordsStillReadable() {
        val d = SessionJson.decode(SessionJson.encode(base("a")), "a")!!
        assertEquals("", d.app); assertEquals("", d.dev); assertEquals("", d.uid); assertEquals("", d.det)
        assertTrue(!SessionJson.encode(base("a")).contains("\"uid\""))
    }

    @Test
    fun collisionImportsUnderNewId() {
        val store = SessionStore(Files.createTempDirectory("v1181b").toFile())
        val a = base("20260101_100000", uid = "aaaa1111")
        val b = base("20260101_100000", uid = "bbbb2222")
        assertEquals(ImportOutcome.IMPORTED, store.importJson("20260101_100000.json", SessionJson.encode(a).toByteArray(), false))
        assertEquals(ImportOutcome.IMPORTED, store.importJson("20260101_100000.json", SessionJson.encode(b).toByteArray(), false))
        assertTrue(store.localIds().contains("20260101_100000_bbbb"))
        // 同唯一碼再匯入 = 略過
        assertEquals(ImportOutcome.SKIPPED, store.importJson("20260101_100000.json", SessionJson.encode(a).toByteArray(), false))
    }

    @Test
    fun sourceLineAndCsv() {
        val s = base("a", "ab12cd34", "1.18.1", "Pixel, Watch", RecordMeta.detect(false, 50, 12f, 10))
        val line = RecordMeta.sourceLine(s)
        assertTrue(line.contains("App 1.18.1") && line.contains("ID ab12cd34"))
        assertEquals("", RecordMeta.sourceLine(base("b")))
        val csv = ExportCsv.summary(listOf(s))
        assertTrue(csv.lines()[0].endsWith("app,device,uid"))
        assertTrue(csv.contains("1.18.1,\"Pixel, Watch\",ab12cd34"))
    }
}

class V1182Test {
    @Test
    fun clashRules() {
        assertTrue(RecordMeta.clashes("aaaa", "bbbb"))
        assertTrue(!RecordMeta.clashes("aaaa", "aaaa"))
        assertTrue(!RecordMeta.clashes("", "bbbb"))
        assertTrue(!RecordMeta.clashes("aaaa", ""))
    }

    @Test
    fun previewTextMentionsClash() {
        val p = ImportPreview(newIds = listOf("a", "b"), collisions = 1, deviceName = "W")
        assertTrue(ImportPreviewText.describe(p, false).contains("另存新編號"))
        assertTrue(!ImportPreviewText.describe(p.copy(collisions = 0), false).contains("另存"))
    }
}

class V1183Test {
    private fun e(t: Boolean = false, m: Boolean = false, g: Boolean = false, o: Boolean = false) =
        ScreenRules.HistoryEntries(t, m, g, o)

    @Test
    fun groupsVisibility() {
        assertTrue(!HistoryGroups.trendVisible(e()) && !HistoryGroups.compareVisible(e()))
        assertTrue(HistoryGroups.trendVisible(e(t = true)) && HistoryGroups.trendVisible(e(o = true)))
        assertTrue(!HistoryGroups.compareVisible(e(t = true, o = true)))
        assertTrue(HistoryGroups.compareVisible(e(m = true)) && HistoryGroups.compareVisible(e(g = true)))
        assertTrue(!HistoryGroups.trendVisible(e(m = true, g = true)))
    }

    @Test
    fun reportLinkCarriesPin() {
        assertEquals("http://192.168.1.2:8080/report.html?pin=1234", ReportLink.url("http://192.168.1.2:8080", "1234"))
        assertEquals("http://192.168.1.2:8080/report.html", ReportLink.url("http://192.168.1.2:8080/", ""))
        assertEquals("http://h/report.html?pin=12", ReportLink.url("http://h", "1 2&x"))
    }
}
