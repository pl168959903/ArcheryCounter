package com.zgrcc.archeryassistant

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStoreTest {

    private fun newStore(): Pair<SessionStore, File> {
        val base = Files.createTempDirectory("archery-store").toFile()
        return SessionStore(base) to base
    }

    private fun sample(
        id: String = "20261019_211003",
        startMs: Long = 1_000_000L,
        endMs: Long? = 2_000_000L,
    ) = Session(
        id = id,
        startMs = startMs,
        endMs = endMs,
        threshold = 16f,
        windowSec = 2,
        hasGyro = true,
        rawCsv = false,
        shots = listOf(
            ShotEntry(10_000, 17.3f, 16f, 0.42f, 0.8f),
            ShotEntry(50_000, 18.1f, 17f, null, null),
        ),
        manual = listOf(ManualEntry(60_000, 1), ManualEntry(70_000, -1)),
    )

    @Test
    fun savedSessionComesBackIdentical() {
        val (store, _) = newStore()
        val s = sample()
        store.saveAsync(s)
        assertEquals(listOf(s), store.listSessions())
    }

    @Test
    fun missingValuesStayMissing() {
        val (store, _) = newStore()
        val s = sample()
        store.saveAsync(s)
        val back = store.load(s.id)
        assertNotNull(back)
        assertNull(back!!.shots[1].accelRms)
        assertNull(back.shots[1].gyroRms)
        assertEquals(0.42f, back.shots[0].accelRms!!, 0.0001f)
    }

    @Test
    fun sessionWithoutEndTimeIsInterrupted() {
        val (store, _) = newStore()
        store.saveAsync(sample(endMs = null))
        assertNull(store.listSessions().single().endMs)
    }

    @Test
    fun laterSaveReplacesEarlierOne() {
        val (store, _) = newStore()
        val s = sample()
        store.saveAsync(s.copy(shots = s.shots.take(1), endMs = null))
        store.saveAsync(s)
        val list = store.listSessions()
        assertEquals(1, list.size)
        assertEquals(2, list[0].shots.size)
        assertEquals(2_000_000L, list[0].endMs)
    }

    @Test
    fun listIsNewestFirst() {
        val (store, _) = newStore()
        store.saveAsync(sample(id = "a1", startMs = 1_000L))
        store.saveAsync(sample(id = "a3", startMs = 3_000L))
        store.saveAsync(sample(id = "a2", startMs = 2_000L))
        assertEquals(listOf("a3", "a2", "a1"), store.listSessions().map { it.id })
    }

    @Test
    fun existsRightAfterSaveAsync() {
        val (store, _) = newStore()
        assertFalse(store.exists("x1"))
        store.saveAsync(sample(id = "x1"))
        assertTrue(store.exists("x1"))
    }

    @Test
    fun csvLivesNextToTheSessionFile() {
        val (store, base) = newStore()
        val csv = store.csvFile("20261019_211003")
        assertEquals("20261019_211003.csv", csv.name)
        assertEquals(File(base, "sessions"), csv.parentFile)
        assertTrue(csv.parentFile!!.isDirectory)
    }

    @Test
    fun deleteRemovesSessionAndItsCsv() {
        val (store, _) = newStore()
        val s = sample()
        store.csvFile(s.id).writeText("t_ms,ax\n")
        store.saveAsync(s)
        assertEquals(1, store.listSessions().size)

        store.delete(s.id)

        assertEquals(0, store.listSessions().size)
        assertFalse(store.csvFile(s.id).exists())
    }

    @Test
    fun deleteAllKeepsTheActiveOne() {
        val (store, _) = newStore()
        store.saveAsync(sample(id = "old1", startMs = 1_000L))
        store.saveAsync(sample(id = "old2", startMs = 2_000L))
        store.saveAsync(sample(id = "live", startMs = 3_000L, endMs = null))

        store.deleteAll(keepId = "live")

        assertEquals(listOf("live"), store.listSessions().map { it.id })
    }

    @Test
    fun corruptFileIsIgnored() {
        val (store, base) = newStore()
        store.saveAsync(sample())
        store.listSessions() // 等存档完成
        File(base, "sessions/bad.json").writeText("{not json")
        File(base, "sessions/empty.json").writeText("")
        assertEquals(listOf("20261019_211003"), store.listSessions().map { it.id })
    }

    @Test
    fun loadRejectsUnknownAndUnsafeIds() {
        val (store, _) = newStore()
        assertNull(store.load("nope"))
        assertNull(store.load("../secret"))
    }

    @Test
    fun jsonUsesDocumentedFieldNames() {
        val text = SessionJson.encode(sample(endMs = null))
        assertTrue(text.contains("\"endMs\":-1"))
        assertTrue(text.contains("\"rel\":10000"))
        assertTrue(text.contains("\"thr\":16.0000"))
        assertTrue(text.contains("\"acc\":0.4200"))
        assertTrue(text.contains("\"d\":-1"))
    }
}
