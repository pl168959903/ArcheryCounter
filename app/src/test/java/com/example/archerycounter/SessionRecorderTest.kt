package com.example.archerycounter

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecorderTest {

    private val t0 = 1_760_000_000_000L

    private fun newStore() = SessionStore(Files.createTempDirectory("archery-rec").toFile())

    @Test
    fun sessionWithoutEventsLeavesNoRecord() {
        val store = newStore()
        val r = SessionRecorder(store)
        r.start(t0, 16f, 2, true, false)
        assertNotNull(r.activeId)
        r.stop(t0 + 5_000)
        assertNull(r.activeId)
        assertEquals(0, store.listSessions().size)
    }

    @Test
    fun shotsAndManualAdjustmentsAreSavedWithEndTime() {
        val store = newStore()
        val r = SessionRecorder(store)
        r.start(t0, 16f, 2, true, false)
        r.addShot(10_000, 17.3f, 16f, 0.4f, 0.9f)
        r.addManual(15_000, +1)
        r.addShot(50_000, 18f, 17f, null, null)
        r.stop(t0 + 60_000)

        val s = store.listSessions().single()
        assertEquals(t0, s.startMs)
        assertEquals(t0 + 60_000, s.endMs)
        assertEquals(2, s.shots.size)
        assertEquals(17f, s.shots[1].threshold, 0.0001f)
        assertEquals(listOf(ManualEntry(15_000, 1)), s.manual)
        assertEquals(3, s.finalCount())
    }

    @Test
    fun applicationKilledMidSessionKeepsShotsAndHasNoEnd() {
        val store = newStore()
        val r = SessionRecorder(store)
        r.start(t0, 16f, 2, false, false)
        r.addShot(10_000, 17f, 16f, 0.4f, null)
        // 不呼叫 stop：模拟应用被系统关掉

        val s = store.listSessions().single()
        assertNull(s.endMs)
        assertEquals(1, s.shots.size)
    }

    @Test
    fun rawCsvIsCreatedAtStartAndFilledWithRows() {
        val store = newStore()
        val r = SessionRecorder(store)
        val id = r.start(t0, 16f, 2, true, true)
        r.writeRaw(0, 0.1f, 0.2f, 0.3f, 1f, 2f, 3f)
        r.writeRaw(10, 0.1f, 0.2f, 0.3f, 1f, 2f, 3f)
        r.stop(t0 + 1_000)

        val lines = store.csvFile(id).readLines()
        assertEquals("t_ms,ax,ay,az,a_mag,gx,gy,gz", lines[0])
        assertEquals(3, lines.size)
        assertTrue(lines[1].startsWith("0,0.1,0.2,0.3,"))

        val s = store.listSessions().single()
        assertTrue(s.rawCsv)
        assertEquals(id, s.id)
    }

    @Test
    fun rawRowsAreIgnoredWhenRecordingIsOff() {
        val store = newStore()
        val r = SessionRecorder(store)
        val id = r.start(t0, 16f, 2, true, false)
        r.writeRaw(0, 0.1f, 0.2f, 0.3f, 1f, 2f, 3f)
        r.stop(t0 + 1_000)
        assertTrue(!store.csvFile(id).exists())
    }

    @Test
    fun twoSessionsStartedInTheSameSecondGetDifferentIds() {
        val store = newStore()
        val r = SessionRecorder(store)
        val a = r.start(t0, 16f, 2, true, false)
        r.addShot(1_000, 17f, 16f, 0.4f, null)
        r.stop(t0 + 500)
        val b = r.start(t0 + 100, 16f, 2, true, false)
        r.addShot(1_000, 17f, 16f, 0.4f, null)
        r.stop(t0 + 600)

        assertTrue(a != b)
        assertEquals(2, store.listSessions().size)
    }
}
