package com.zgrcc.archeryassistant

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformStoreTest {

    private val t0 = 1_760_000_000_000L

    private fun newStore(): Pair<SessionStore, File> {
        val base = Files.createTempDirectory("archery-wave").toFile()
        return SessionStore(base) to base
    }

    private fun wave(withGyro: Boolean = true) = Waveform(
        stepMs = 40,
        impactIndex = 3,
        accel = listOf(0.5f, 0.6f, 0.4f, 17.3f, 9.8f, 2.1f),
        gyro = if (withGyro) listOf(1.5f, 1.2f, 0.9f, 40.2f, 12.0f, 3.3f) else emptyList(),
    )

    private fun session(vararg shots: ShotEntry) = Session(
        id = "20261019_211003", startMs = t0, endMs = t0 + 60_000, threshold = 16f, windowSec = 2,
        hasGyro = true, rawCsv = false, shots = shots.toList(), manual = emptyList(),
    )

    @Test
    fun waveformAndCancelledFlagSurviveARoundTrip() {
        val (store, _) = newStore()
        val s = session(
            ShotEntry(10_000, 17.3f, 16f, 0.42f, 0.8f, waveform = wave()),
            ShotEntry(50_000, 18.1f, 16f, 0.5f, null, waveform = wave(withGyro = false), cancelled = true),
        )
        store.saveAsync(s)
        assertEquals(listOf(s), store.listSessions())
    }

    @Test
    fun aShotWithoutWaveformStaysWithoutOne() {
        val (store, _) = newStore()
        val s = session(ShotEntry(10_000, 17.3f, 16f, 0.42f, 0.8f))
        store.saveAsync(s)
        val back = store.load(s.id)!!
        assertNull(back.shots[0].waveform)
        assertFalse(back.shots[0].cancelled)
    }

    @Test
    fun recordsWrittenByV12StillLoad() {
        val (store, base) = newStore()
        // v1.2 写出的格式：没有 wf、没有 x
        store.csvFile("dummy") // 建立目录
        File(base, "sessions/20261018_090000.json").writeText(
            """{"id":"20261018_090000","startMs":1760000000000,"endMs":1760000060000,"threshold":16.0000,""" +
                """"windowSec":2,"hasGyro":true,"rawCsv":false,""" +
                """"shots":[{"rel":10000,"peak":17.3000,"thr":16.0000,"acc":0.4200,"gyr":0.8000}],""" +
                """"manual":[{"rel":20000,"d":-1}]}"""
        )
        val s = store.load("20261018_090000")
        assertNotNull(s)
        s!!
        assertEquals(1, s.shots.size)
        assertNull(s.shots[0].waveform)
        assertFalse(s.shots[0].cancelled)
        assertEquals(0, s.finalCount())
    }

    @Test
    fun jsonUsesTheDocumentedWaveformFields() {
        val text = SessionJson.encode(
            session(ShotEntry(10_000, 17.3f, 16f, 0.42f, 0.8f, waveform = wave(), cancelled = true))
        )
        assertTrue(text.contains("\"x\":1"))
        assertTrue(text.contains("\"wf\":{\"s\":40,\"i\":3,\"a\":[0.5,0.6,0.4,17.3,9.8,2.1],\"g\":[1.5,1.2,0.9,40.2,12.0,3.3]}"))
    }

    @Test
    fun anUncancelledShotWritesNoCancelFlag() {
        val text = SessionJson.encode(session(ShotEntry(10_000, 17.3f, 16f, 0.42f, 0.8f)))
        assertFalse(text.contains("\"x\""))
        assertFalse(text.contains("\"wf\""))
    }

    @Test
    fun aTypicalWaveformCostsAboutAKilobytePerShot() {
        // 放箭前 2.1 秒 + 后 0.3 秒，40ms 一格 = 60 点，加速度与角速度各一条
        val a = List(60) { 12.3f }
        val g = List(60) { 123.4f }
        val w = Waveform(40, 52, a, g)
        val withWave = SessionJson.encode(session(ShotEntry(10_000, 17.3f, 16f, 0.42f, 0.8f, waveform = w)))
        val without = SessionJson.encode(session(ShotEntry(10_000, 17.3f, 16f, 0.42f, 0.8f)))
        val extra = withWave.length - without.length
        assertTrue("extra=$extra", extra < 1_200)
    }

    @Test
    fun corruptWaveformIsIgnoredButTheShotIsKept() {
        val (store, base) = newStore()
        store.csvFile("dummy")
        File(base, "sessions/20261018_100000.json").writeText(
            """{"id":"x","startMs":1760000000000,"endMs":-1,"threshold":16,"windowSec":2,"hasGyro":true,"rawCsv":false,""" +
                """"shots":[{"rel":10000,"peak":17.3,"thr":16,"wf":{"s":40,"i":3,"a":[]}}],"manual":[]}"""
        )
        val s = store.load("20261018_100000")!!
        assertEquals(1, s.shots.size)
        assertNull(s.shots[0].waveform) // 空的波形不要
    }

    @Test
    fun recorderSavesTheWaveformGivenToAddShot() {
        val (store, _) = newStore()
        val r = SessionRecorder(store)
        r.start(t0, 16f, 2, true, false)
        r.addShot(10_000, 17.3f, 16f, 0.42f, 0.8f, wave())
        r.addShot(50_000, 18f, 16f, null, null) // 没有波形
        r.stop(t0 + 60_000)

        val s = store.listSessions().single()
        assertEquals(wave(), s.shots[0].waveform)
        assertNull(s.shots[1].waveform)
    }

    @Test
    fun editedCancelFlagIsPersistedWhenResavingASession() {
        val (store, _) = newStore()
        val s = session(
            ShotEntry(10_000, 17.3f, 16f, 0.42f, 0.8f, waveform = wave()),
            ShotEntry(50_000, 18.1f, 16f, 0.5f, 0.9f),
        )
        store.saveAsync(s)

        val loaded = store.load(s.id)!!
        val shots = loaded.shots.toMutableList()
        shots[0] = shots[0].copy(cancelled = true)
        store.saveAsync(loaded.copy(shots = shots))

        val after = store.load(s.id)!!
        assertTrue(after.shots[0].cancelled)
        assertFalse(after.shots[1].cancelled)
        assertEquals(wave(), after.shots[0].waveform) // 波形没被弄丢
        assertEquals(1, after.finalCount())
    }
}
