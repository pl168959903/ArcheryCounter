package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files

class ImporterTest {
    private fun tmp(): File = Files.createTempDirectory("imp").toFile()

    private fun sess(id: String, thr: Float = 16f) =
        Session(id, 0, 5000, thr, 2, false, false, listOf(ShotEntry(1000, 20f, thr, 1f, null)), emptyList(), emptyList(), false, emptyList(), null)

    private fun json(id: String, thr: Float = 16f) = SessionJson.encode(sess(id, thr)).toByteArray(Charsets.UTF_8)

    private class Fake(val files: Map<String, ByteArray>, val broken: Set<String> = emptySet(), val listError: ImportError? = null) : ImportSource {
        override fun list(): List<RemoteFile> {
            listError?.let { throw ImportException(it) }
            return files.map { RemoteFile(it.key, it.value.size.toLong()) }
        }
        override fun open(name: String): InputStream {
            if (name in broken) throw IOException("x")
            return ByteArrayInputStream(files.getValue(name))
        }
    }

    @Test
    fun addressParsing() {
        assertEquals("http://192.168.1.23:8080", ImportAddress.parse("192.168.1.23"))
        assertEquals("http://192.168.1.23:9000", ImportAddress.parse(" http://192.168.1.23:9000/ "))
        assertEquals("http://10.0.0.2:8080", ImportAddress.parse("10.0.0.2:8080"))
        assertNull(ImportAddress.parse("8.8.8.8"))                 // 非區網
        assertNull(ImportAddress.parse("example.com"))
        assertNull(ImportAddress.parse("192.168.1.23/x"))
        assertNull(ImportAddress.parse("192.168.1.23:99999"))
        assertNull(ImportAddress.parse("192.168.1.256"))
        assertNull(ImportAddress.parse(""))
        assertNull(ImportAddress.parse("user@192.168.1.2"))
    }

    @Test
    fun parseListKeepsOnlySafeNames() {
        val l = ImportPlanner.parseList("a.json\t10\n../x\t5\nb.csv\t3\nbad\n\n")
        assertEquals(listOf("a.json", "b.csv"), l.map { it.name })
        assertEquals(10L, l[0].length)
    }

    @Test
    fun wantedFiltersRaw() {
        val r = listOf(RemoteFile("a.json", 1), RemoteFile("a.csv", 1), RemoteFile("b.csv", 1), RemoteFile("summary.csv", 1), RemoteFile("shots.csv", 1))
        assertEquals(listOf("a.json"), ImportPlanner.wanted(r, false).map { it.name })
        assertEquals(listOf("a.json", "a.csv"), ImportPlanner.wanted(r, true).map { it.name })
    }

    @Test
    fun importsNewSkipsExisting() {
        val store = SessionStore(tmp())
        val src = Fake(mapOf("s1.json" to json("s1"), "s2.json" to json("s2")))
        val r1 = SessionImporter(store, src).run(overwrite = false, withRaw = false)
        assertEquals(2, r1.imported)
        assertEquals(setOf("s1", "s2"), store.localIds())
        val r2 = SessionImporter(store, src).run(overwrite = false, withRaw = false)
        assertEquals(0, r2.imported)
        assertEquals(2, r2.skipped)
    }

    @Test
    fun overwriteReplaces() {
        val store = SessionStore(tmp())
        SessionImporter(store, Fake(mapOf("s1.json" to json("s1", 16f)))).run(false, false)
        val r = SessionImporter(store, Fake(mapOf("s1.json" to json("s1", 17f)))).run(true, false)
        assertEquals(1, r.imported)
        assertEquals(17f, store.load("s1")!!.threshold, 0.001f)
    }

    @Test
    fun localOnlyRecordsUntouched() {
        val store = SessionStore(tmp())
        store.saveAsync(sess("mine"))
        SessionImporter(store, Fake(mapOf("s1.json" to json("s1")))).run(true, false)
        assertTrue("mine" in store.localIds())
        assertEquals(setOf("mine", "s1"), store.localIds())
    }

    @Test
    fun invalidAndUnsafeFilesAreFailedNotWritten() {
        val dir = tmp()
        val store = SessionStore(dir)
        val src = Fake(mapOf("good.json" to json("good"), "bad.json" to "not json".toByteArray(), "evil.json" to json("x")), broken = setOf("evil.json"))
        val r = SessionImporter(store, src).run(false, false)
        assertEquals(1, r.imported)
        assertEquals(2, r.failed)
        assertEquals(setOf("good"), store.localIds())
        assertEquals(ImportOutcome.INVALID, store.importJson("..json", json("a"), false))
        assertEquals(ImportOutcome.INVALID, store.importJson("a b.json", json("a"), false))
        assertFalse(File(dir, "sessions").listFiles().orEmpty().any { it.name.endsWith(".tmp") })
    }

    @Test
    fun rawCsvOnlyWithMatchingRecordAndNotExports() {
        val dir = tmp()
        val store = SessionStore(dir)
        val src = Fake(mapOf("s1.json" to json("s1"), "s1.csv" to "t,a\n1,2\n".toByteArray(), "summary.csv" to "x".toByteArray(), "s9.csv" to "z".toByteArray()))
        val r = SessionImporter(store, src).run(false, true)
        assertEquals(1, r.imported)
        assertEquals(1, r.raw)
        assertTrue(File(dir, "sessions/s1.csv").exists())
        assertFalse(File(dir, "sessions/summary.csv").exists())
        assertFalse(File(dir, "sessions/s9.csv").exists())
        assertEquals(ImportOutcome.INVALID, store.importRaw("summary.csv", ByteArrayInputStream(ByteArray(1)), true))
    }

    @Test
    fun listErrorsAreReported() {
        val store = SessionStore(tmp())
        assertEquals(ImportError.WRONG_PIN, SessionImporter(store, Fake(emptyMap(), listError = ImportError.WRONG_PIN)).run(false, false).error)
    }

    @Test
    fun endToEndOverRealSocket() {
        // 對方：真的伺服器 + 路由；這邊：真的 HTTP 來源
        val remoteDir = tmp()
        val remote = SessionStore(remoteDir)
        remote.saveAsync(sess("20261020_193721"))
        remote.saveAsync(sess("20261020_201500"))
        remote.listSessions() // 等寫完
        val router = ShareRouter(
            pin = "4321", listFiles = { remote.shareNames() }, lookup = { remote.shareFile(it) },
            buildReport = { "<html/>" },
        )
        val server = ShareServer(router, 18193)
        assertTrue(server.start())
        try {
            val local = SessionStore(tmp())
            val ok = SessionImporter(local, HttpSource("http://127.0.0.1:18193", "4321")).run(false, false)
            assertNull(ok.error)
            assertEquals(2, ok.imported)
            assertEquals(setOf("20261020_193721", "20261020_201500"), local.localIds())
            val bad = SessionImporter(SessionStore(tmp()), HttpSource("http://127.0.0.1:18193", "0000")).run(false, false)
            assertEquals(ImportError.WRONG_PIN, bad.error)
            assertNotNull(local.load("20261020_193721"))
        } finally {
            server.stop()
        }
        Thread.sleep(300) // 讓接收執行緒收尾
        val down = SessionImporter(SessionStore(tmp()), HttpSource("http://127.0.0.1:18193", "4321")).run(false, false)
        assertTrue("down=$down", down.error == ImportError.UNREACHABLE)
    }

    @Test
    fun resultText() {
        I18n.english = false
        assertEquals("完成：新增 2 筆，略過 1 筆已有的", ImportText.describe(ImportResult(imported = 2, skipped = 1)))
        assertTrue(ImportText.describe(ImportResult(error = ImportError.WRONG_PIN)).contains("PIN"))
    }

    @Test
    fun listEndpointNeedsAuthAndIsPlainText() {
        val r = ShareRouter("1234", { listOf("a.json") }, { ShareFile(5) { ByteArrayInputStream(ByteArray(5)) } }, { "" })
        assertEquals(403, r.handle("GET", "/list").status)
        val ok = r.handle("GET", "/list?pin=1234")
        assertEquals("a.json\t5", String(ok.stream().readBytes(), Charsets.UTF_8))
    }
}
