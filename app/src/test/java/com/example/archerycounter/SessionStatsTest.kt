package com.example.archerycounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStatsTest {

    private fun shot(sec: Int, peak: Float, acc: Float?, gyr: Float?, thr: Float = 16f) =
        ShotEntry(relMs = sec * 1000L, peak = peak, threshold = thr, accelRms = acc, gyroRms = gyr)

    private fun session(
        shots: List<ShotEntry>,
        manual: List<ManualEntry> = emptyList(),
        endMs: Long? = 1_000_000L + 600_000L,
        hasGyro: Boolean = true,
    ) = Session(
        id = "t", startMs = 1_000_000L, endMs = endMs, threshold = 16f, windowSec = 2,
        hasGyro = hasGyro, rawCsv = false, shots = shots, manual = manual,
    )

    @Test
    fun countsAndManualAdjustments() {
        val s = session(
            shots = listOf(shot(10, 17f, 0.4f, 1f), shot(50, 18f, 0.5f, 1.2f), shot(90, 19f, 0.6f, 1.4f)),
            manual = listOf(ManualEntry(95_000, +1), ManualEntry(96_000, +1), ManualEntry(97_000, -1)),
        )
        val st = computeStats(s)
        assertEquals(3, st.detected)
        assertEquals(2, st.manualPlus)
        assertEquals(1, st.manualMinus)
        assertEquals(4, st.finalCount)
    }

    @Test
    fun finalCountNeverNegative() {
        val s = session(shots = emptyList(), manual = listOf(ManualEntry(1000, -1), ManualEntry(2000, -1)))
        assertEquals(0, computeStats(s).finalCount)
    }

    @Test
    fun peakStatsAndInterval() {
        val s = session(listOf(shot(10, 16.5f, 0.4f, 1f), shot(50, 20f, 0.5f, 1f), shot(90, 18f, 0.6f, 1f)))
        val st = computeStats(s)
        assertEquals(18.1666f, st.peakAvg!!, 0.01f)
        assertEquals(20f, st.peakMax!!, 0.0001f)
        assertEquals(16.5f, st.peakMin!!, 0.0001f)
        assertEquals(40f, st.intervalAvgSec!!, 0.0001f)
    }

    @Test
    fun singleShotHasNoInterval() {
        val st = computeStats(session(listOf(shot(10, 17f, 0.4f, 1f))))
        assertNull(st.intervalAvgSec)
    }

    @Test
    fun emptySessionHasNoStats() {
        val st = computeStats(session(emptyList()))
        assertEquals(0, st.detected)
        assertNull(st.peakAvg)
        assertNull(st.accelAvg)
        assertNull(st.accelSteadiest)
        assertNull(st.accelTrend)
    }

    @Test
    fun steadiestAndShakiestShotNumbers() {
        val s = session(
            listOf(
                shot(10, 17f, 0.50f, 2.0f),
                shot(50, 17f, 0.20f, 1.0f), // 加速度最稳（第 2 箭）
                shot(90, 17f, 0.90f, 0.5f), // 加速度最晃（第 3 箭），角速度最稳
                shot(130, 17f, 0.40f, 3.0f), // 角速度最晃（第 4 箭）
            )
        )
        val st = computeStats(s)
        assertEquals(2, st.accelSteadiest!!.number)
        assertEquals(3, st.accelShakiest!!.number)
        assertEquals(3, st.gyroSteadiest!!.number)
        assertEquals(4, st.gyroShakiest!!.number)
    }

    @Test
    fun shotsWithoutStabilityAreSkippedButNumbersStayCorrect() {
        val s = session(
            listOf(
                shot(10, 17f, null, null),
                shot(50, 17f, 0.3f, null),
                shot(90, 17f, 0.8f, null),
            ),
            hasGyro = false,
        )
        val st = computeStats(s)
        assertEquals(2, st.accelSteadiest!!.number) // 第 1 箭没有数值，所以编号要从实际第几箭算
        assertEquals(3, st.accelShakiest!!.number)
        assertNull(st.gyroSteadiest)
        assertNull(st.gyroAvg)
    }

    @Test
    fun trendComparesFirstAndLastThird() {
        // 9 箭：前 3 箭平均 0.2，后 3 箭平均 0.8
        val acc = listOf(0.1f, 0.2f, 0.3f, 0.5f, 0.5f, 0.5f, 0.7f, 0.8f, 0.9f)
        val s = session(acc.mapIndexed { i, a -> shot(10 + i * 40, 17f, a, null) }, hasGyro = false)
        val tr = computeStats(s).accelTrend
        assertNotNull(tr)
        assertEquals(0.2f, tr!!.first, 0.001f)
        assertEquals(0.8f, tr.last, 0.001f)
    }

    @Test
    fun trendNeedsAtLeastSixShots() {
        val s = session((0 until 5).map { shot(10 + it * 40, 17f, 0.3f, null) }, hasGyro = false)
        assertNull(computeStats(s).accelTrend)
    }

    @Test
    fun thresholdRangeCoversChangesDuringSession() {
        val s = session(listOf(shot(10, 17f, 0.3f, null, thr = 16f), shot(50, 18f, 0.3f, null, thr = 17f)))
        val st = computeStats(s)
        assertEquals(16f, st.thresholdMin, 0.0001f)
        assertEquals(17f, st.thresholdMax, 0.0001f)
    }

    @Test
    fun durationOfInterruptedSessionUsesLastEvent() {
        val s = session(
            listOf(shot(10, 17f, 0.3f, null), shot(120, 17f, 0.3f, null)),
            manual = listOf(ManualEntry(130_000, +1)),
            endMs = null,
        )
        assertEquals(130_000L, s.durationMs())
    }

    @Test
    fun durationOfFinishedSession() {
        assertEquals(600_000L, session(emptyList()).durationMs())
    }

    @Test
    fun formatHelpers() {
        assertEquals("45 秒", Fmt.duration(45_000))
        assertEquals("12 分 5 秒", Fmt.duration(12 * 60_000L + 5_000))
        assertEquals("0.42", Fmt.num(0.4249f))
        assertEquals("—", Fmt.numOrDash(null))
        assertTrue(Fmt.clock(0L).length == 8)
    }
}
