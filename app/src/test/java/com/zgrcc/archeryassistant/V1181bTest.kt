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

class V1185Test {
    @Test
    fun logNewestFirst() {
        val a = ShareEvent(1000, "1.1.1.1", "x", ShareResult.OK_PIN)
        val b = ShareEvent(3000, "1.1.1.2", "x", ShareResult.OK_TRUST)
        val c = ShareEvent(3000, "1.1.1.3", "x", ShareResult.WRONG_PIN)
        val out = ShareText.newestFirst(listOf(a, b, c))
        assertEquals(listOf("1.1.1.3", "1.1.1.2", "1.1.1.1"), out.map { it.ip })
        assertTrue(ShareText.newestFirst(emptyList()).isEmpty())
    }

    @Test
    fun signatureMatchIsSilentButWarningsRemain() {
        assertEquals("", AppInfo.signatureStatus(AppInfo.FIXED_KEY_PREFIX))
        assertTrue(AppInfo.signatureStatus("AA:BB:CC:DD").isNotEmpty())
        assertTrue(AppInfo.signatureStatus("?").isNotEmpty())
    }
}

class V119Test {
    private fun s(id: String, start: Long = 0L, end: Long? = 600_000L, shots: Int = 12, tags: List<String> = emptyList(), cal: Boolean = false, note: String = "") =
        Session(id, start, end, 16f, 2, false, false, List(shots) { ShotEntry(1000L + it * 1000, 20f, 16f, 1f, null) }, emptyList(), tags = tags, calibration = cal, note = note)

    @Test
    fun noteRoundTripAndOldRecords() {
        val d = SessionJson.decode(SessionJson.encode(s("a", note = "風大，換新箭")), "a")!!
        assertEquals("風大，換新箭", d.note)
        assertEquals("", SessionJson.decode(SessionJson.encode(s("a")), "a")!!.note)
        assertTrue(!SessionJson.encode(s("a")).contains("\"note\""))
    }

    @Test
    fun suspicionRules() {
        val normal = List(6) { s("n$it", shots = 12) }
        val m = Suspicion.median(normal)
        assertEquals(12, m)
        assertEquals(null, Suspicion.median(normal.take(4)))
        assertEquals(SuspectKind.INTERRUPTED, Suspicion.check(s("x", end = null), m, false))
        assertEquals(null, Suspicion.check(s("x", end = null), m, true))          // 進行中不判斷
        assertEquals(SuspectKind.NO_ARROWS, Suspicion.check(s("x", shots = 0), m, false))
        assertEquals(null, Suspicion.check(s("x", shots = 0, end = 60_000L), m, false)) // 不到 2 分鐘
        assertEquals(SuspectKind.TOO_MANY, Suspicion.check(s("x", shots = 30), m, false))
        assertEquals(null, Suspicion.check(s("x", shots = 24), m, false))           // 剛好 2 倍不算
        assertEquals(null, Suspicion.check(s("x", shots = 30), null, false))        // 樣本不足不比
        assertEquals(null, Suspicion.check(s("x", end = null, cal = true), m, false)) // 校準不判斷
        val flags = Suspicion.flags(normal + s("bad", shots = 40), "n0")
        assertEquals(setOf("bad"), flags.keys)
    }

    @Test
    fun historyFilter() {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        val all = listOf(s("a", now - 2 * day, tags = listOf("室外70米")), s("b", now - 20 * day, tags = listOf("室外70米", "反曲")), s("c", now - 60 * day))
        assertEquals(listOf("a", "b", "c"), HistoryFilter.apply(all, "", 0, now).map { it.id })
        assertEquals(listOf("a", "b"), HistoryFilter.apply(all, "室外70米", 0, now).map { it.id })
        assertEquals(listOf("a"), HistoryFilter.apply(all, "", 7, now).map { it.id })
        assertEquals(listOf("a", "b"), HistoryFilter.apply(all, "", 30, now).map { it.id })
        assertEquals(listOf("b"), HistoryFilter.apply(all, "反曲", 30, now).map { it.id })
        assertEquals(listOf("反曲", "室外70米"), HistoryFilter.tags(all))
        assertEquals("室外70米", HistoryFilter.nextTag("", listOf("室外70米", "反曲")))
        assertEquals("", HistoryFilter.nextTag("反曲", listOf("室外70米", "反曲")))
        assertEquals("", HistoryFilter.nextTag("不存在", listOf("反曲")))
        assertEquals(7, HistoryFilter.nextDays(0)); assertEquals(30, HistoryFilter.nextDays(7)); assertEquals(0, HistoryFilter.nextDays(30))
        assertTrue(!HistoryFilter.active("", 0) && HistoryFilter.active("x", 0) && HistoryFilter.active("", 7))
    }

    @Test
    fun zipRoundTripAndRouter() {
        val bos = java.io.ByteArrayOutputStream()
        val n = ShareZip.write(bos, listOf(
            "a.json" to { "{\"x\":1}".byteInputStream() as java.io.InputStream? },
            "../evil.json" to { "bad".byteInputStream() as java.io.InputStream? },
            "gone.json" to { null },
            "b.txt" to { "hi".byteInputStream() as java.io.InputStream? },
        ))
        assertEquals(2, n)
        val z = java.util.zip.ZipInputStream(bos.toByteArray().inputStream())
        val got = HashMap<String, String>()
        while (true) { val e = z.nextEntry ?: break; got[e.name] = String(z.readBytes()) }
        assertEquals(mapOf("a.json" to "{\"x\":1}", "b.txt" to "hi"), got)

        val data = bos.toByteArray()
        val router = ShareRouter("1234", { listOf("a.json") }, { null }, { "" }, zip = { ShareFile(data.size.toLong()) { data.inputStream() } })
        val r = router.handle("GET", "/all.zip?pin=1234")
        assertEquals(200, r.status); assertEquals("application/zip", r.contentType)
        assertEquals(data.size.toLong(), r.length)
        assertTrue(router.handle("GET", "/?pin=1234").stream().readBytes().toString(Charsets.UTF_8).contains("/all.zip?pin=1234"))
        // 沒有提供時：404、首頁沒有連結
        val plain = ShareRouter("1234", { listOf("a.json") }, { null }, { "" })
        assertEquals(404, plain.handle("GET", "/all.zip?pin=1234").status)
        assertTrue(!plain.handle("GET", "/?pin=1234").stream().readBytes().toString(Charsets.UTF_8).contains("all.zip"))
        // 沒 PIN 不給
        assertEquals(403, router.handle("GET", "/all.zip").status)
    }

    @Test
    fun autoStopCycle() {
        assertEquals(10, ShareAutoStop.next(0)); assertEquals(30, ShareAutoStop.next(10))
        assertEquals(60, ShareAutoStop.next(30)); assertEquals(0, ShareAutoStop.next(60))
        assertEquals(10, ShareAutoStop.next(7))
    }

    @Test
    fun importTracksFreshAndOverwritten() {
        val local = SessionStore(Files.createTempDirectory("v119").toFile())
        val have = s("20260101_100000"); local.importJson("20260101_100000.json", SessionJson.encode(have).toByteArray(), false)
        val a = SessionJson.encode(s("20260101_100000", note = "遠端")).toByteArray()
        val b = SessionJson.encode(s("20260102_100000")).toByteArray()
        val src = object : ImportSource {
            override fun list() = listOf(RemoteFile("20260101_100000.json", a.size.toLong()), RemoteFile("20260102_100000.json", b.size.toLong()))
            override fun open(name: String): java.io.InputStream = (if (name.startsWith("20260101")) a else b).inputStream()
        }
        val r = SessionImporter(local, src).run(overwrite = true, withRaw = false)
        assertEquals(2, r.imported)
        assertEquals(listOf("20260102_100000"), r.freshIds)
        assertEquals(1, r.overwritten)
        assertTrue(ImportText.describe(r).contains("無法復原"))
        // 復原只刪新增的：本機原有（被覆蓋）的那筆還在
        local.deleteMany(r.freshIds)
        assertTrue(local.localIds().contains("20260101_100000") && !local.localIds().contains("20260102_100000"))
    }

    @Test
    fun collisionIdIsReported() {
        val local = SessionStore(Files.createTempDirectory("v119b").toFile())
        val a = SessionJson.encode(s("20260101_100000").copy(uid = "aaaa1111")).toByteArray()
        val b = SessionJson.encode(s("20260101_100000").copy(uid = "bbbb2222")).toByteArray()
        assertEquals(ImportOutcome.IMPORTED to "20260101_100000", local.importJsonWithId("20260101_100000.json", a, false))
        assertEquals(ImportOutcome.IMPORTED to "20260101_100000_bbbb", local.importJsonWithId("20260101_100000.json", b, false))
        assertEquals(ImportOutcome.SKIPPED to "20260101_100000", local.importJsonWithId("20260101_100000.json", a, false))
    }

    @Test
    fun reportShowsNoteAndFlag() {
        val sess = s("20260101_100000", end = null, note = "風大")
        val meta = ReportMeta(author = "Z", versionLabel = "v1", signature = null, signatureNote = null, generatedAtMs = 0, iconBase64 = null, arrowsPerEnd = 6,
            flags = mapOf(sess.id to SuspectKind.INTERRUPTED))
        val html = ReportBuilder.build(listOf(sess), meta)
        assertTrue(html.contains("✎ 風大") && html.contains("⚠"))
        val off = ReportBuilder.build(listOf(sess), meta.copy(showNotes = false, flags = emptyMap()))
        assertTrue(!off.contains("✎ 風大") && !off.contains("⚠ 可疑"))
    }
}

class V1193Test {
    @Test
    fun noSessionsMeansNoCsvNames() {
        val store = SessionStore(Files.createTempDirectory("v1193").toFile())
        assertTrue(store.shareNames().isEmpty())
        assertTrue(store.shareFile("summary.csv") == null)
    }

    @Test
    fun zipContainsGeneratedCsvs() {
        val store = SessionStore(Files.createTempDirectory("v1193b").toFile())
        val s = Session("20260101_100000", 0, 5000, 16f, 2, false, false, listOf(ShotEntry(1000, 20f, 16f, 1f, null)), emptyList())
        store.importJson("20260101_100000.json", SessionJson.encode(s).toByteArray(), false)
        val zip = store.shareZip()!!
        val z = java.util.zip.ZipInputStream(zip.open())
        val names = HashSet<String>()
        while (true) { val e = z.nextEntry ?: break; names.add(e.name) }
        assertEquals(setOf("20260101_100000.json", "summary.csv", "shots.csv"), names)
    }
}
