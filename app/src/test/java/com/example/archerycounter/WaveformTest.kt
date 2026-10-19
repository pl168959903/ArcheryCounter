package com.example.archerycounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformTest {

    private val stepMs = 10L // 100Hz

    /** 在 [from, to) 内每 10ms 送一笔加速度，合力大小固定为 [mag]（沿 x 轴） */
    private fun feedAccel(t: StabilityTracker, from: Long, to: Long, mag: Float) {
        var time = from
        while (time < to) {
            t.addAccel(mag, 0f, 0f, time)
            time += stepMs
        }
    }

    private fun feedGyro(t: StabilityTracker, from: Long, to: Long, mag: Float) {
        var time = from
        while (time < to) {
            t.addGyro(0f, mag, 0f, time)
            time += stepMs
        }
    }

    // ---- StabilityTracker.waveform ----

    @Test
    fun waveformHasExpectedLengthImpactIndexAndSpike() {
        val t = StabilityTracker()
        feedAccel(t, 0, 4000, 1f)
        feedAccel(t, 3000, 3030, 20f) // 放箭冲击，3000ms 开始
        val w = t.waveform(triggerMs = 3000, preMs = 2100, postMs = 300)
        assertNotNull(w)
        w!!
        assertEquals(40, w.stepMs)
        assertEquals(60, w.accel.size)        // (2100 + 300) / 40
        assertEquals(52, w.impactIndex)       // 2100 / 40
        assertEquals(20f, w.accel[52], 0.001f) // 尖峰在冲击位置
        assertEquals(1f, w.accel[10], 0.001f)  // 之前是平稳的
    }

    @Test
    fun waveformKeepsAShortSpikeBecauseItTakesTheBinMaximum() {
        val t = StabilityTracker()
        feedAccel(t, 0, 4000, 1f)
        t.addAccel(30f, 0f, 0f, 3005) // 只有一个样本的尖峰
        val w = t.waveform(3000, 2100, 300)!!
        assertEquals(30f, w.accel.max(), 0.001f)
    }

    @Test
    fun gyroIsEmptyWhenThereIsNoGyroData() {
        val t = StabilityTracker()
        feedAccel(t, 0, 4000, 1f)
        val w = t.waveform(3000, 2100, 300)!!
        assertTrue(w.gyro.isEmpty())
    }

    @Test
    fun gyroWaveformHasSameLengthAsAccel() {
        val t = StabilityTracker()
        feedAccel(t, 0, 4000, 1f)
        feedGyro(t, 0, 4000, 5f)
        val w = t.waveform(3000, 2100, 300)!!
        assertEquals(w.accel.size, w.gyro.size)
        assertEquals(5f, w.gyro[20], 0.001f)
    }

    @Test
    fun waveformIsNullWhenThereIsTooLittleData() {
        val t = StabilityTracker()
        feedAccel(t, 2990, 3030, 1f) // 只有 4 笔
        assertNull(t.waveform(3000, 2100, 300))
    }

    @Test
    fun gapsInTheDataAreFilledSoNothingIsNaN() {
        val t = StabilityTracker()
        feedAccel(t, 1000, 1500, 2f)
        feedAccel(t, 2500, 3300, 3f) // 中间 1 秒没有数据
        val w = t.waveform(3000, 2100, 300)!!
        assertTrue(w.accel.all { !it.isNaN() })
        assertEquals(2f, w.accel[20], 0.001f) // 空洞沿用前一格
    }

    // ---- LiveBuffer ----

    @Test
    fun liveBufferTakesTheMaximumWithinOneBin() {
        val b = LiveBuffer(binMs = 40, capacity = 10)
        b.add(0, 1f)
        b.add(10, 5f)
        b.add(20, 3f)
        assertEquals(listOf(5f), b.snapshot().toList())
    }

    @Test
    fun liveBufferKeepsOnlyTheNewestBins() {
        val b = LiveBuffer(binMs = 40, capacity = 5)
        for (i in 0 until 20) b.add(i * 40L, i.toFloat())
        val s = b.snapshot()
        assertEquals(6, s.size)              // 5 格已完成 + 正在累积的 1 格
        assertEquals(19f, s.last(), 0.0001f) // 最新的在最后
        assertEquals(14f, s.first(), 0.0001f) // 最旧的是 14
    }

    @Test
    fun liveBufferIsOrderedOldestToNewestBeforeItWraps() {
        val b = LiveBuffer(binMs = 40, capacity = 10)
        for (i in 0 until 4) b.add(i * 40L, (i + 1).toFloat())
        assertEquals(listOf(1f, 2f, 3f, 4f), b.snapshot().toList())
    }

    @Test
    fun liveBufferClearEmptiesIt() {
        val b = LiveBuffer()
        b.add(0, 1f)
        b.add(100, 2f)
        b.clear()
        assertEquals(0, b.snapshot().size)
    }

    // ---- ChartMath ----

    @Test
    fun yFractionsPutMaxOnTopAndMinOnBottom() {
        val f = ChartMath.yFractions(listOf(2f, 4f, 3f))
        assertEquals(0f, f[0], 0.0001f)
        assertEquals(1f, f[1], 0.0001f)
        assertEquals(0.5f, f[2], 0.0001f)
    }

    @Test
    fun yFractionsOfConstantDataSitInTheMiddle() {
        assertEquals(listOf(0.5f, 0.5f), ChartMath.yFractions(listOf(3f, 3f)))
    }

    @Test
    fun yFractionsCanUseAFixedRangeAndClamp() {
        val f = ChartMath.yFractions(listOf(5f, 20f), lo = 0f, hi = 10f)
        assertEquals(0.5f, f[0], 0.0001f)
        assertEquals(1f, f[1], 0.0001f) // 超出范围的被夹在顶端
    }

    @Test
    fun yFractionsOfEmptyListIsEmpty() {
        assertEquals(0, ChartMath.yFractions(emptyList()).size)
    }

    @Test
    fun xFractionSpreadsPointsEvenlyAndCentersASinglePoint() {
        assertEquals(0f, ChartMath.xFraction(0, 5), 0.0001f)
        assertEquals(0.5f, ChartMath.xFraction(2, 5), 0.0001f)
        assertEquals(1f, ChartMath.xFraction(4, 5), 0.0001f)
        assertEquals(0.5f, ChartMath.xFraction(0, 1), 0.0001f)
    }
}
