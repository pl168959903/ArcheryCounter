package com.zgrcc.archeryassistant

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawPhasesTest {
    /** 40ms 一點；放箭在第 250 點；安靜底噪 accel 0.3 / gyro 3，[bursts] 區間內 accel 3 / gyro 60。 */
    private fun wave(vararg bursts: IntRange, impact: Int = 250, size: Int = 275, gyro: Boolean = true): Waveform {
        val a = MutableList(size) { 0.3f }
        val g = MutableList(size) { 3f }
        for (r in bursts) for (i in r) { a[i] = 3f; g[i] = 60f }
        return Waveform(40, impact, a, if (gyro) g else emptyList())
    }

    @Test
    fun findsPrepAndHoldInASyntheticShot() {
        val p = DrawPhases.analyze(wave(96..167))!!   // 96 起 72 點 = 2.88 秒
        assertEquals(96, p.startBin)
        assertEquals(168, p.endBin)
        assertEquals(2.88f, p.prepSec, 0.01f)
        assertEquals(3.28f, p.holdSec, 0.01f)         // 168 → 250
        assertEquals(6.16f, p.totalSec, 0.01f)
    }

    @Test
    fun usesTheLastBurstWhenThereAreSeveral() {
        val p = DrawPhases.analyze(wave(18..59, 120..179))!!
        assertEquals(120, p.startBin)
        assertEquals(180, p.endBin)
    }

    @Test
    fun aShortQuietGapInsideTheBurstDoesNotSplitIt() {
        val p = DrawPhases.analyze(wave(96..119, 126..167))!!   // 中間約 0.25 秒安靜
        assertEquals(96, p.startBin)
        assertEquals(168, p.endBin)
    }

    @Test
    fun nullWhenThereIsNothingToAnalyse() {
        assertNull(DrawPhases.analyze(wave()))                             // 全程安靜
        assertNull(DrawPhases.analyze(wave(96..167, gyro = false)))        // 沒有陀螺儀
        assertNull(DrawPhases.analyze(wave(0..60, 70..90).copy(impactIndex = 40)))   // 放箭前太短
        assertNull(DrawPhases.analyze(wave(0..167)))                       // 大動作延伸到波形開頭：起點不明
        assertNull(DrawPhases.analyze(Waveform(40, 250, List(275) { 3f }, List(10) { 60f })))  // 長度不一致
    }

    @Test
    fun weakMotionIsNotMistakenForAPrep() {
        val a = MutableList(275) { 0.3f }
        val g = MutableList(275) { 3f }
        for (i in 96..167) { a[i] = 1.5f; g[i] = 25f }   // 有動作但不夠大
        assertNull(DrawPhases.analyze(Waveform(40, 250, a, g)))
    }

    @Test
    fun averageAndText() {
        val p1 = DrawPhase(96, 168, 250, 40)
        val p2 = DrawPhase(100, 160, 250, 40)
        val (prep, hold) = DrawPhases.average(listOf(p1, p2))!!
        assertEquals((2.88f + 2.4f) / 2f, prep, 0.001f)
        assertEquals((3.28f + 3.6f) / 2f, hold, 0.001f)
        assertNull(DrawPhases.average(emptyList()))
        assertTrue(DrawPhases.text(p1).contains("2.9") || DrawPhases.text(p1).contains("2.88"))
    }

    @Test
    fun trackerKeepsTenSecondsBeforeReleaseAtHighSamplingRate() {
        val t = StabilityTracker()
        var time = 0L
        while (time <= 12_000) {           // 200Hz，12 秒
            val burst = time in 2_000..4_000
            t.addAccel(if (burst) 3f else 0.3f, 0f, 0f, time)
            t.addGyro(if (burst) 60f else 3f, 0f, 0f, time)
            time += 5
        }
        val w = t.waveform(triggerMs = 11_000, preMs = StabilityTracker.WAVE_PRE_MS, postMs = 1_000)!!
        assertEquals(250, w.impactIndex)
        assertEquals(275, w.accel.size)
        assertEquals(3f, w.accel[30], 0.001f)      // 2.0 秒後的大動作還在波形裡（11000 − 10000 = 1000 起）
    }

    @Test
    fun longWaveformSurvivesSaveAndLoad() {
        val base = Files.createTempDirectory("archery-draw").toFile()
        val store = SessionStore(base)
        val w = wave(96..167)
        val s = Session(
            id = "20261022_100000", startMs = 1_760_000_000_000L, endMs = 1_760_000_060_000L, threshold = 16f, windowSec = 2,
            hasGyro = true, rawCsv = false,
            shots = listOf(ShotEntry(12_000, 18f, 16f, 0.4f, 5f, waveform = w)), manual = emptyList(),
        )
        store.saveAsync(s)
        val back = store.load(s.id)!!.shots[0].waveform
        assertNotNull(back)
        assertEquals(275, back!!.accel.size)
        assertEquals(250, back.impactIndex)
        val p = DrawPhases.analyze(back)!!
        assertEquals(96, p.startBin)
    }
}
