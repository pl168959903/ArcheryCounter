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

class V1184Test {
    private class Gate(val data: ShareData) : TrustGate {
        override val enabled = true
        override fun verify(token: String) = data.verify(token)
        override fun issue(ip: String, agent: String) = data.issue(ip, agent)
        override fun record(event: ShareEvent) { data.log(event) }
        override fun revoke(token: String) = data.revoke(token)
    }

    private var now = 1000L
    private val data = ShareData(FakePrefs())
    private val router = ShareRouter(
        pin = "1234", listFiles = { listOf("a.json") }, lookup = { ShareFile(2) { "{}".byteInputStream() } },
        buildReport = { "<html>rep</html>" }, gate = Gate(data), clock = { now },
    )
    private val app = AgentLabel.userAgent("1.18.4", "Pixel")
    private fun ShareResponse.text() = String(stream().readBytes(), Charsets.UTF_8)

    private fun trustedToken(): String {
        val r = router.handle("GET", "/info?pin=1234", ShareRequest("192.168.1.5", app, ""))
        val h = r.headers.first { it.startsWith("Set-Cookie") }
        return ShareRouter.cookieValue(h.substringAfter(": ").substringBefore(';'), ShareRouter.COOKIE)!!
    }

    @Test
    fun reportWithoutPinShowsFormNotForbidden() {
        val r = router.handle("GET", "/report.html", ShareRequest("192.168.1.9", "Mozilla Chrome", ""))
        assertEquals(200, r.status)
        assertTrue(r.text().contains("<form") && r.text().contains("action=\"/report.html\""))
        // 其他路徑仍然是 Forbidden
        assertEquals(403, router.handle("GET", "/f/a.json", ShareRequest("192.168.1.9", "Mozilla", "")).status)
    }

    @Test
    fun ticketOpensReportOnceAndExpires() {
        val tok = trustedToken()
        val t = router.handle("GET", "/ticket", ShareRequest("192.168.1.5", app, "trust=$tok")).text()
        assertTrue(t.length >= 16)
        val browser = ShareRequest("192.168.1.9", "Mozilla Chrome", "")
        val ok = router.handle("GET", "/report.html?ticket=$t", browser)
        assertEquals(200, ok.status)
        assertTrue(ok.text().contains("<html>rep</html>"))
        // 用過就作廢
        assertTrue(!router.handle("GET", "/report.html?ticket=$t", browser).text().contains("<html>rep</html>"))
        // 過期
        val t2 = router.handle("GET", "/ticket", ShareRequest("192.168.1.5", app, "trust=$tok")).text()
        now += ShareRouter.TICKET_MS + 1
        assertTrue(!router.handle("GET", "/report.html?ticket=$t2", browser).text().contains("<html>rep</html>"))
    }

    @Test
    fun ticketRequiresTrust() {
        assertEquals(403, router.handle("GET", "/ticket", ShareRequest("192.168.1.9", "Mozilla", "")).status)
    }

    @Test
    fun revokeRemovesDeviceAndTokenStopsWorking() {
        val tok = trustedToken()
        assertEquals(1, data.devices.value.size)
        assertEquals(200, router.handle("GET", "/revoke", ShareRequest("192.168.1.5", app, "trust=$tok")).status)
        assertEquals(0, data.devices.value.size)
        assertEquals(403, router.handle("GET", "/list", ShareRequest("192.168.1.5", app, "trust=$tok")).status)
        // 沒有憑證不能取消
        assertEquals(403, router.handle("GET", "/revoke", ShareRequest("192.168.1.9", "Mozilla", "")).status)
    }

    @Test
    fun staleTokenWithoutPinDoesNotCountAsWrongPin() {
        val r = ShareRequest("192.168.1.5", app, "trust=deadbeef")
        repeat(15) { assertEquals(403, router.handle("GET", "/info", r).status) }
        assertTrue(!router.locked)
    }

    @Test
    fun linkHelpers() {
        assertEquals("http://h:8080/report.html?ticket=ab12", ReportLink.url("http://h:8080", "1234", "ab12"))
        assertEquals(listOf("a", "b", "c"), ReportLink.tokenOrder("a", listOf("b", "a", "", "c")))
        assertEquals(listOf("b"), ReportLink.tokenOrder(null, listOf("b")))
    }
}
