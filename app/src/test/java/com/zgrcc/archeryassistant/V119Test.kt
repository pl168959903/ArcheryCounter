package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

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
