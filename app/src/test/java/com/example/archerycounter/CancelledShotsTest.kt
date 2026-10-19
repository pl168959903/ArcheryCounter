package com.example.archerycounter

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CancelledShotsTest {

    private val t0 = 1_760_000_000_000L

    private fun shot(sec: Int, peak: Float, acc: Float?, gyr: Float?, cancelled: Boolean = false) =
        ShotEntry(
            relMs = sec * 1000L, peak = peak, threshold = 16f, accelRms = acc, gyroRms = gyr,
            cancelled = cancelled,
        )

    private fun session(shots: List<ShotEntry>, manual: List<ManualEntry> = emptyList()) = Session(
        id = "t", startMs = t0, endMs = t0 + 600_000L, threshold = 16f, windowSec = 2,
        hasGyro = true, rawCsv = false, shots = shots, manual = manual,
    )

    private fun newStore() = SessionStore(Files.createTempDirectory("archery-cancel").toFile())

    // ---- 统计 ----

    @Test
    fun cancelledShotsDoNotCountAsDetected() {
        val s = session(
            listOf(
                shot(10, 17f, 0.4f, 1f),
                shot(30, 25f, 3.0f, 9f, cancelled = true),
                shot(50, 18f, 0.5f, 1.2f),
            ),
        )
        val st = computeStats(s)
        assertEquals(2, st.detected)
        assertEquals(1, st.cancelled)
        assertEquals(2, st.finalCount)
        assertEquals(2, s.finalCount())
    }

    @Test
    fun cancelledShotsAreLeftOutOfPeakAndIntervalStats() {
        val s = session(
            listOf(
                shot(10, 17f, 0.4f, 1f),
                shot(30, 99f, 3.0f, 9f, cancelled = true), // 不该影响最大值和间隔
                shot(50, 19f, 0.5f, 1.2f),
            ),
        )
        val st = computeStats(s)
        assertEquals(19f, st.peakMax!!, 0.0001f)
        assertEquals(18f, st.peakAvg!!, 0.0001f)
        assertEquals(40f, st.intervalAvgSec!!, 0.0001f) // 10 秒 → 50 秒，不是被误判那一笔隔开的两段
        assertEquals(0.45f, st.accelAvg!!, 0.0001f)
    }

    @Test
    fun shotNumbersOnlyCountValidShots() {
        val s = session(
            listOf(
                shot(10, 17f, 0.50f, 1f),
                shot(30, 17f, 0.01f, 0.1f, cancelled = true), // 如果被算进去，它会是最稳的
                shot(50, 17f, 0.20f, 2f),
                shot(70, 17f, 0.90f, 3f),
            ),
        )
        val series = metricSeries(s, gyro = false)
        assertEquals(listOf(1, 2, 3), series.map { it.number })
        val st = computeStats(s)
        assertEquals(2, st.accelSteadiest!!.number) // 有效的第 2 箭（原始第 3 笔）
        assertEquals(3, st.accelShakiest!!.number)
    }

    @Test
    fun manualAdjustmentsStillApplyOnTopOfValidShots() {
        val s = session(
            shots = listOf(shot(10, 17f, 0.4f, 1f), shot(30, 17f, 0.4f, 1f, cancelled = true)),
            manual = listOf(ManualEntry(40_000, +1), ManualEntry(41_000, +1)),
        )
        assertEquals(3, s.finalCount())
        assertEquals(3, computeStats(s).finalCount)
    }

    @Test
    fun aSessionWhereEverythingWasCancelledHasNoStats() {
        val s = session(listOf(shot(10, 17f, 0.4f, 1f, cancelled = true)))
        val st = computeStats(s)
        assertEquals(0, st.detected)
        assertEquals(1, st.cancelled)
        assertNull(st.peakAvg)
        assertNull(st.accelAvg)
        assertEquals(0, metricSeries(s, gyro = true).size)
    }

    // ---- 记录器：取消最近一箭 ----

    @Test
    fun cancelLastShotMarksTheNewestValidShotFirst() {
        val store = newStore()
        val r = SessionRecorder(store)
        r.start(t0, 16f, 2, true, false)
        r.addShot(10_000, 17f, 16f, 0.4f, 1f)
        r.addShot(50_000, 18f, 16f, 0.5f, 1f)

        assertTrue(r.cancelLastShot())
        r.stop(t0 + 60_000)

        val s = store.listSessions().single()
        assertFalse(s.shots[0].cancelled)
        assertTrue(s.shots[1].cancelled)
        assertEquals(1, s.finalCount())
    }

    @Test
    fun cancellingRepeatedlyWalksBackAndEventuallyReturnsFalse() {
        val store = newStore()
        val r = SessionRecorder(store)
        r.start(t0, 16f, 2, true, false)
        r.addShot(10_000, 17f, 16f, 0.4f, 1f)
        r.addShot(50_000, 18f, 16f, 0.5f, 1f)

        assertTrue(r.cancelLastShot())
        assertTrue(r.cancelLastShot())
        assertFalse(r.cancelLastShot()) // 没有可取消的了
        r.stop(t0 + 60_000)

        val s = store.listSessions().single()
        assertTrue(s.shots.all { it.cancelled })
        assertEquals(0, s.finalCount())
    }

    @Test
    fun cancelLastShotDoesNothingWithoutAnActiveSession() {
        val r = SessionRecorder(newStore())
        assertFalse(r.cancelLastShot())

        r.start(t0, 16f, 2, true, false)
        r.addShot(10_000, 17f, 16f, 0.4f, 1f)
        r.stop(t0 + 60_000)
        assertFalse(r.cancelLastShot()) // 已经结束
    }

    @Test
    fun cancelLastShotDoesNothingBeforeAnyShot() {
        val r = SessionRecorder(newStore())
        r.start(t0, 16f, 2, true, false)
        assertFalse(r.cancelLastShot())
    }

    @Test
    fun cancelledFlagIsSavedImmediatelyForAnInterruptedSession() {
        val store = newStore()
        val r = SessionRecorder(store)
        r.start(t0, 16f, 2, true, false)
        r.addShot(10_000, 17f, 16f, 0.4f, 1f)
        r.cancelLastShot()
        // 不呼叫 stop：模拟应用被系统关掉
        val s = store.listSessions().single()
        assertNull(s.endMs)
        assertTrue(s.shots.single().cancelled)
    }
}
