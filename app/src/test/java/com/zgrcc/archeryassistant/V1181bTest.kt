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
        assertEquals(Suspect(SuspectKind.INTERRUPTED), Suspicion.check(s("x", end = null), false))
        assertEquals(null, Suspicion.check(s("x", end = null), true))             // 進行中不判斷
        assertEquals(Suspect(SuspectKind.NO_ARROWS), Suspicion.check(s("x", shots = 0), false))
        assertEquals(null, Suspicion.check(s("x", shots = 0, end = 60_000L), false)) // 不到 2 分鐘
        assertEquals(null, Suspicion.check(s("x", shots = 300), false))            // 箭數多不再算可疑
        assertEquals(null, Suspicion.check(s("x", end = null, cal = true), false)) // 校準不判斷
        val flags = Suspicion.flags(listOf(s("ok"), s("bad", end = null), s("n0", end = null)), "n0")
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
            flags = mapOf(sess.id to Suspect(SuspectKind.INTERRUPTED)))
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

class V1193bTest {
    @Test
    fun resetAndAboutAreSeparateCategories() {
        assertEquals(10, SettingsCategories.COUNT)
        assertEquals("連線", SettingsCategories.name(SettingsCategories.CONNECT))
        assertEquals("重設與備份", SettingsCategories.name(SettingsCategories.RESET))
        assertEquals("關於", SettingsCategories.name(SettingsCategories.ABOUT))
        assertEquals(SettingsCategories.COUNT - 1, SettingsCategories.ABOUT)
        assertEquals(SettingsCategories.CONNECT + 1, SettingsCategories.RESET)
    }
}

class V1194Test {
    private fun shot(relMs: Long, accel: Float = 0.5f, follow: Float? = 2.5f, cancelled: Boolean = false) =
        ShotEntry(relMs, 30f, 18f, accel, 5f, followAccelRms = follow, cancelled = cancelled)

    private fun sess(shots: List<ShotEntry>, id: String = "s", start: Long = 0, cal: Boolean = false, tags: List<String> = emptyList()) =
        Session(id, start, start + 3_600_000, 18f, 2, true, false, shots, emptyList(), tags = tags, calibration = cal)

    /** 每 17 秒一箭 */
    private fun regular(n: Int, accel: (Int) -> Float = { 0.5f }) = (0 until n).map { shot(10_000L + it * 17_000L, accel(it)) }

    @Test
    fun fastShotWithNoFollowThroughIsFlagged() {
        val base = regular(8)
        val bad = shot(base.last().relMs + 6_500, follow = 0.7f)
        val s = sess(base + bad)
        assertEquals(setOf(8), FastShot.flagged(s))
    }

    @Test
    fun normalSessionHasNoFlags() {
        assertEquals(emptySet<Int>(), FastShot.flagged(sess(regular(20))))
    }

    @Test
    fun closeShotButNormalFollowThroughIsNotFlagged() {
        val base = regular(8)
        val quick = shot(base.last().relMs + 6_500, follow = 2.4f)
        assertEquals(emptySet<Int>(), FastShot.flagged(sess(base + quick)))
    }

    @Test
    fun withoutFollowDataOnlyVeryShortGapIsFlagged() {
        val base = (0 until 8).map { shot(10_000L + it * 17_000L, follow = null) }
        val last = base.last().relMs
        assertEquals(setOf(8), FastShot.flagged(sess(base + shot(last + 5_000, follow = null))))
        assertEquals(emptySet<Int>(), FastShot.flagged(sess(base + shot(last + 8_000, follow = null))))
    }

    @Test
    fun tooFewShotsOrCalibrationNotJudged() {
        assertEquals(emptySet<Int>(), FastShot.flagged(sess(listOf(shot(1000), shot(2000, follow = 0.1f)))))
        val base = regular(8)
        assertEquals(emptySet<Int>(), FastShot.flagged(sess(base + shot(base.last().relMs + 3000, follow = 0.1f), cal = true)))
    }

    @Test
    fun cancelledShotsAreIgnoredAndIndexIsInSessionList() {
        val base = regular(8)
        val withCancelled = base + shot(base.last().relMs + 2_000, cancelled = true) + shot(base.last().relMs + 6_500, follow = 0.7f)
        assertEquals(setOf(9), FastShot.flagged(sess(withCancelled)))
    }

    @Test
    fun longRestGapsAreNotCountedAsGaps() {
        // 兩趟各 6 箭，趟間休息 120 秒：不該把休息當成間隔，也不會標記任何箭
        val a = regular(6)
        val b = (0 until 6).map { shot(a.last().relMs + 120_000 + it * 17_000L) }
        assertEquals(emptySet<Int>(), FastShot.flagged(sess(a + b)))
    }

    @Test
    fun positionRowsRestartAtEachEnd() {
        val a = (0 until 8).map { shot(10_000L + it * 17_000L, accel = 0.4f + it * 0.04f) }
        val b = (0 until 8).map { shot(a.last().relMs + 120_000 + it * 17_000L, accel = 0.4f + it * 0.04f) }
        val c = (0 until 8).map { shot(b.last().relMs + 120_000 + it * 17_000L, accel = 0.4f + it * 0.04f) }
        val rows = PositionTrend.rows(listOf(sess(a + b + c)))
        assertEquals(8, rows.size)
        assertEquals(3, rows[0].n)
        assertEquals(0.4f, rows[0].accel!!, 0.001f)
        assertEquals(0.68f, rows[7].accel!!, 0.001f)
        assertTrue(PositionTrend.hint(rows) != null)
    }

    @Test
    fun positionRowsDropThinPositionsAndFlatHasNoHint() {
        val a = regular(6)
        val b = (0 until 8).map { shot(a.last().relMs + 120_000 + it * 17_000L) }
        val c = (0 until 8).map { shot(b.last().relMs + 120_000 + it * 17_000L) }
        val rows = PositionTrend.rows(listOf(sess(a + b + c)))
        assertEquals(listOf(1, 2, 3, 4, 5, 6), rows.map { it.pos })
        assertEquals(6, rows.size) // 第 7、8 箭只有 2 筆，不列
        assertEquals(null, PositionTrend.hint(rows))
        assertEquals(emptyList<PosRow>(), PositionTrend.rows(listOf(sess(regular(10), cal = true))))
    }

    @Test
    fun measuredHzIsAppendedOnce() {
        val det = RecordMeta.detect(true, 100, 18f, 2)
        val w = RecordMeta.withMeasured(det, 49.6f)
        assertEquals("adv=on;hz=100;thr=18.0;win=2s;real=50", w)
        assertEquals(w, RecordMeta.withMeasured(w, 100f))
        assertEquals(det, RecordMeta.withMeasured(det, 0f))
        assertEquals("", RecordMeta.withMeasured("", 50f))
        assertTrue(RecordMeta.detectText(w).contains("100 Hz"))
        assertTrue(RecordMeta.detectText(w).contains("50 Hz"))
        assertEquals(RecordMeta.detectText(det).contains("實測"), false)
        val same = RecordMeta.withMeasured(RecordMeta.detect(true, 50, 18f, 2), 50.2f)
        assertEquals(false, RecordMeta.detectText(same).contains("實測"))
    }

    @Test
    fun sessionExtrasPaceAndFollowTrends() {
        val s1 = sess(regular(8), id = "a", start = 1_000)
        val s2 = sess((0 until 8).map { shot(10_000L + it * 14_000L, follow = 3.5f) }, id = "b", start = 2_000)
        val s3 = sess(regular(8), id = "c", start = 3_000, cal = true)
        val pace = SessionExtras.paceTrend(listOf(s2, s1, s3))
        assertEquals(2, pace.size)
        assertEquals(17f, pace[0].value, 0.01f)
        assertEquals(14f, pace[1].value, 0.01f)
        val fol = SessionExtras.followTrend(listOf(s1, s2))
        assertEquals(listOf(2.5f, 3.5f), fol.map { it.value })
        assertEquals(null, SessionExtras.medianGapSec(sess(regular(3))))
    }

    @Test
    fun newSettingsDefaultOn() {
        val s = AppSettings(FakePrefs())
        assertTrue(s.recordMeasuredHz.value && s.flagFastShots.value && s.positionTrend.value && s.trendExtra.value)
        s.setFlagFastShots(false); s.setTrendExtra(false)
        s.resetDefaults()
        assertTrue(s.flagFastShots.value && s.trendExtra.value)
    }
}

class V1194bTest {
    private fun shot(relMs: Long, cancelled: Boolean = false) = ShotEntry(relMs, 30f, 18f, 0.5f, 5f, cancelled = cancelled)
    private fun sess(shots: List<ShotEntry>) = Session("s", 0, 3_600_000, 18f, 2, true, false, shots, emptyList())

    /** 依 [sizes] 每趟 N 箭，趟內間隔 [gap] 毫秒，趟間 [rest] 毫秒 */
    private fun build(sizes: List<Int>, gap: Long = 17_000, rest: Long = 100_000): Session {
        val out = ArrayList<ShotEntry>()
        var t = 10_000L
        sizes.forEachIndexed { i, n ->
            if (i > 0) t += rest - gap
            repeat(n) { out.add(shot(t)); t += gap }
        }
        return sess(out)
    }

    @Test
    fun endsAreFoundFromGapsNotFixedCount() {
        val sizes = listOf(6, 8, 8, 8, 8, 10, 8, 6, 6, 9, 9, 9)
        val ends = AutoEnds.compute(build(sizes))
        assertEquals(sizes, ends.map { it.count })
        assertEquals(12, ends.size)
        assertTrue(AutoEnds.summary(ends).contains("12"))
        assertTrue(AutoEnds.summary(ends).contains("6、8、8"))
    }

    @Test
    fun thresholdIsRelativeToMedianButAtLeast35s() {
        assertEquals(42_500L, AutoEnds.gapMs(build(listOf(8, 8))))
        assertEquals(35_000L, AutoEnds.gapMs(build(listOf(8, 8), gap = 8_000))) // 8s×2.5=20s → 下限 35s
        assertEquals(END_GAP_MS, AutoEnds.gapMs(sess(listOf(shot(1000), shot(20_000)))))
    }

    @Test
    fun thirtySecondRestIsAnEndWhenShootingIsFast() {
        // 趟內 8 秒一箭，走去拔箭 40 秒 → 換趟
        val ends = AutoEnds.compute(build(listOf(6, 6), gap = 8_000, rest = 40_000))
        assertEquals(listOf(6, 6), ends.map { it.count })
    }

    @Test
    fun slowShooterWithLongGapsDoesNotSplitEveryShot() {
        // 趟內 30 秒一箭（中位數 30 秒 → 門檻 75 秒），趟間 120 秒
        val ends = AutoEnds.compute(build(listOf(5, 5), gap = 30_000, rest = 120_000))
        assertEquals(listOf(5, 5), ends.map { it.count })
    }

    @Test
    fun labelsAndEmptyCases() {
        val ends = AutoEnds.compute(build(listOf(3, 4)))
        assertEquals("1-1", AutoEnds.label(ends, 1))
        assertEquals("1-3", AutoEnds.label(ends, 3))
        assertEquals("2-1", AutoEnds.label(ends, 4))
        assertEquals("2-4", AutoEnds.label(ends, 7))
        assertEquals(null, AutoEnds.label(ends, 8))
        assertEquals("", AutoEnds.summary(AutoEnds.compute(sess(emptyList()))))
    }

    @Test
    fun cancelledShotsDoNotCountInEnds() {
        val base = build(listOf(6, 6))
        val withFalse = base.copy(shots = base.shots.toMutableList().also { it.add(6, shot(it[5].relMs + 3_000, cancelled = true)) })
        assertEquals(listOf(6, 6), AutoEnds.compute(withFalse).map { it.count })
    }

    @Test
    fun autoEndsSettingDefaultsOn() {
        val s = AppSettings(FakePrefs())
        assertTrue(s.autoEnds.value)
        s.setAutoEnds(false)
        s.resetDefaults()
        assertTrue(s.autoEnds.value)
    }
}

class V120Test {
    @Test
    fun autoModeFollowsDeviceType() {
        assertTrue(PhoneMode.resolve(PhoneMode.AUTO, isWatch = false))
        assertTrue(!PhoneMode.resolve(PhoneMode.AUTO, isWatch = true))
    }

    @Test
    fun forcedModesIgnoreDeviceType() {
        assertTrue(PhoneMode.resolve(PhoneMode.ON, isWatch = true))
        assertTrue(!PhoneMode.resolve(PhoneMode.OFF, isWatch = false))
    }

    @Test
    fun nextCyclesAndInvalidFallsBackToAuto() {
        assertEquals(PhoneMode.ON, PhoneMode.next(PhoneMode.AUTO))
        assertEquals(PhoneMode.OFF, PhoneMode.next(PhoneMode.ON))
        assertEquals(PhoneMode.AUTO, PhoneMode.next(PhoneMode.OFF))
        assertEquals(PhoneMode.AUTO, PhoneMode.valid(99))
        assertEquals(PhoneMode.AUTO, PhoneMode.valid(-1))
    }

    @Test
    fun settingDefaultsToAutoAndResets() {
        val s = AppSettings(FakePrefs())
        assertEquals(PhoneMode.AUTO, s.phoneMode.value)
        s.setPhoneMode(PhoneMode.ON)
        assertEquals(PhoneMode.ON, s.phoneMode.value)
        s.setPhoneMode(42)
        assertEquals(PhoneMode.AUTO, s.phoneMode.value)
        s.setPhoneMode(PhoneMode.OFF)
        s.resetDefaults()
        assertEquals(PhoneMode.AUTO, s.phoneMode.value)
    }
}

class V1201Test {
    private fun shot(relMs: Long, follow: Float? = 2.5f, cancelled: Boolean = false, reviewed: Boolean = false) =
        ShotEntry(relMs, 30f, 18f, 0.5f, 5f, followAccelRms = follow, cancelled = cancelled, reviewed = reviewed)

    private fun sess(shots: List<ShotEntry>, end: Long? = 3_600_000L) =
        Session("s", 0, end, 18f, 2, true, false, shots, emptyList())

    /** 8 箭每 17 秒，再一支離上一箭 6.5 秒、跟隨量 0.7 的可疑箭（位置 8） */
    private fun withSuspect(reviewed: Boolean = false, cancelled: Boolean = false): Session {
        val base = (0 until 8).map { shot(10_000L + it * 17_000L) }
        return sess(base + shot(base.last().relMs + 6_500, follow = 0.7f, reviewed = reviewed, cancelled = cancelled))
    }

    @Test
    fun reviewedFlagRoundTripsAndOldRecordsStayUnreviewed() {
        val s = withSuspect(reviewed = true)
        val d = SessionJson.decode(SessionJson.encode(s), "s")!!
        assertTrue(d.shots[8].reviewed)
        assertTrue(!d.shots[0].reviewed)
        assertTrue(!SessionJson.encode(withSuspect()).contains("\"ok\""))
        assertTrue(!SessionJson.decode(SessionJson.encode(withSuspect()), "s")!!.shots[8].reviewed)
    }

    @Test
    fun reviewedOrCancelledShotIsNoLongerSuspicious() {
        assertEquals(setOf(8), FastShot.flagged(withSuspect()))
        assertEquals(emptySet<Int>(), FastShot.flagged(withSuspect(reviewed = true)))
        assertEquals(emptySet<Int>(), FastShot.flagged(withSuspect(cancelled = true)))
    }

    @Test
    fun recordTagCountsUnreviewedSuspectShotsAndDisappears() {
        assertEquals(Suspect(SuspectKind.SHOTS, 1), Suspicion.check(withSuspect(), false, true))
        assertEquals(null, Suspicion.check(withSuspect(), false, false))             // 沒開「標示可疑的箭」
        assertEquals(null, Suspicion.check(withSuspect(reviewed = true), false, true)) // 處理完標籤消失
        assertEquals(null, Suspicion.check(withSuspect(cancelled = true), false, true))
        assertEquals(null, Suspicion.check(withSuspect(), true, true))               // 進行中不判斷
        assertEquals(Suspect(SuspectKind.INTERRUPTED, 1), Suspicion.check(withSuspect().copy(endMs = null), false, true))
        assertTrue(Suspicion.shortText(Suspect(SuspectKind.SHOTS, 3)).contains("3"))
        assertTrue(Suspicion.text(Suspect(SuspectKind.SHOTS, 2)).contains("2"))
    }

    @Test
    fun shotNavStepsAndJumpsToNextSuspect() {
        val all = listOf(0, 1, 2, 3, 4)
        assertEquals(3, ShotNav.step(all, 2, 1))
        assertEquals(1, ShotNav.step(all, 2, -1))
        assertEquals(4, ShotNav.step(all, 4, 1))   // 到頭不動
        assertEquals(0, ShotNav.step(all, 0, -1))
        val sus = listOf(3, 8, 20)
        assertEquals(8, ShotNav.step(sus, 3, 1))
        assertEquals(8, ShotNav.step(sus, 5, 1))   // 目前不在清單裡：取最接近的
        assertEquals(3, ShotNav.step(sus, 5, -1))
        assertEquals(7, ShotNav.step(emptyList(), 7, 1))
        // 處理完 8：先往後找 20，沒有再往前
        assertEquals(20, ShotNav.nextAfter(listOf(3, 20), 8))
        assertEquals(3, ShotNav.nextAfter(listOf(3), 20))
        assertEquals(null, ShotNav.nextAfter(emptyList(), 8))
        assertEquals("2 / 3", ShotNav.progress(sus, 8))
        assertEquals("- / 3", ShotNav.progress(sus, 5))
    }

    @Test
    fun endLabelFollowsValidNumbersAndSkipsCancelled() {
        val a = (0 until 3).map { shot(10_000L + it * 17_000L) }
        val false1 = shot(a.last().relMs + 3_000, cancelled = true)
        val b = (0 until 3).map { shot(a.last().relMs + 100_000 + it * 17_000L) }
        val s = sess(a + false1 + b)
        val ends = endsFor(s, true, 6)
        assertEquals("1-3", shotEndLabel(s, ends, 2))
        assertEquals(null, shotEndLabel(s, ends, 3))      // 誤判不標
        assertEquals("2-1", shotEndLabel(s, ends, 4))     // 誤判不算有效箭，下一趟第 1 箭
        assertEquals("2-3", shotEndLabel(s, ends, 6))
        assertEquals(null, shotEndLabel(s, ends, 99))
    }
}

class V1202Test {
    @Test
    fun tapLeftHalfIsPreviousRightHalfIsNext() {
        assertEquals(-1, ShotNav.tapDir(10f, 200f))
        assertEquals(-1, ShotNav.tapDir(99.9f, 200f))
        assertEquals(1, ShotNav.tapDir(100f, 200f))
        assertEquals(1, ShotNav.tapDir(190f, 200f))
        assertEquals(1, ShotNav.tapDir(10f, 0f)) // 寬度無效當作右半邊
    }

    @Test
    fun tapNavSettingDefaultsOnAndResets() {
        val s = AppSettings(FakePrefs())
        assertTrue(s.shotTapNav.value)
        s.setShotTapNav(false)
        assertTrue(!s.shotTapNav.value)
        s.resetDefaults()
        assertTrue(s.shotTapNav.value)
    }
}
