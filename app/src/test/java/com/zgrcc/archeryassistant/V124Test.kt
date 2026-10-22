package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.TimeZone
import java.util.zip.ZipFile

class V124Test {
    private fun sess(id: String, start: Long, raw: Boolean = false) = Session(
        id, start, start + 60_000L, 18f, 2, true, raw,
        listOf(ShotEntry(10_000L, 30f, 18f, 0.5f, 5f)), emptyList(),
    )

    @Test
    fun pickKeepsOnlyChosenOldestFirst() {
        val all = listOf(sess("b", 2000), sess("a", 1000), sess("c", 3000))
        assertEquals(listOf("a", "c"), RecordExport.pick(all, setOf("c", "a", "zzz")).map { it.id })
        assertTrue(RecordExport.pick(all, emptySet()).isEmpty())
    }

    @Test
    fun entryNamesFollowRawSwitch() {
        val ids = listOf("a", "b")
        val off = RecordExport.entryNames(ids, setOf("a"), withRaw = false)
        assertEquals(listOf("summary.csv", "shots.csv", "report.html", "a.json", "b.json"), off)
        val on = RecordExport.entryNames(ids, setOf("a"), withRaw = true)
        assertEquals(off + "a.csv", on)
        // 沒有原始資料的記錄即使勾選也不會多出檔案
        assertEquals(off, RecordExport.entryNames(ids, emptySet(), withRaw = true))
        assertTrue(on.all { ShareRouter.isSafeName(it) })
    }

    @Test
    fun zipNameSingleAndMultiple() {
        assertEquals("ArcheryAssistant_20261022_231000.zip", RecordExport.zipName(listOf("20261022_231000"), 0L))
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals("ArcheryAssistant_19700101-0000_3records.zip", RecordExport.zipName(listOf("a", "b", "c"), 0L, utc))
        assertEquals("ArcheryAssistant_19700101-0800_2records.zip", RecordExport.zipName(listOf("a", "b"), 0L, TimeZone.getTimeZone("Asia/Taipei")))
    }

    @Test
    fun anyRawOnlyLooksAtChosen() {
        val all = listOf(sess("a", 1, raw = true), sess("b", 2))
        assertTrue(RecordExport.anyRaw(all, setOf("a", "b")))
        assertFalse(RecordExport.anyRaw(all, setOf("b")))
        assertFalse(RecordExport.anyRaw(all, emptySet()))
    }

    private fun entries(zip: File): List<String> = ZipFile(zip).use { z -> z.entries().asSequence().map { it.name }.toList() }

    @Test
    fun writeExportZipPacksChosenRecords() {
        val dir = Files.createTempDirectory("archery-v124").toFile()
        val store = SessionStore(dir)
        val a = sess("a", 1000, raw = true)
        val b = sess("b", 2000)
        store.csvFile("a").writeText("t,x\n1,2\n")
        val out = File(dir, "out/x.zip")

        val n = store.writeExportZip(out, listOf(a, b), "<html>R</html>", withRaw = false)
        assertEquals(5, n)
        assertEquals(listOf("summary.csv", "shots.csv", "report.html", "a.json", "b.json"), entries(out))

        val out2 = File(dir, "out/y.zip")
        assertEquals(6, store.writeExportZip(out2, listOf(a, b), "<html>R</html>", withRaw = true))
        assertTrue("a.csv" in entries(out2))
        ZipFile(out2).use { z ->
            assertEquals("t,x\n1,2\n", z.getInputStream(z.getEntry("a.csv")).readBytes().toString(Charsets.UTF_8))
            assertEquals("<html>R</html>", z.getInputStream(z.getEntry("report.html")).readBytes().toString(Charsets.UTF_8))
            val json = z.getInputStream(z.getEntry("b.json")).readBytes().toString(Charsets.UTF_8)
            assertEquals("b", SessionJson.decode(json, "b")!!.id)
            val summary = z.getInputStream(z.getEntry("summary.csv")).readBytes().toString(Charsets.UTF_8)
            assertEquals(3, summary.trim().lines().size) // 標題列 + 兩筆
        }
    }

    @Test
    fun writeExportZipWithoutRawFileSkipsIt() {
        val dir = Files.createTempDirectory("archery-v124b").toFile()
        val store = SessionStore(dir)
        // rawCsv 標示有，但檔案不在：不該產生 a.csv，也不該失敗
        val out = File(dir, "z.zip")
        assertEquals(4, store.writeExportZip(out, listOf(sess("a", 1000, raw = true)), "r", withRaw = true))
        assertFalse("a.csv" in entries(out))
    }
}
