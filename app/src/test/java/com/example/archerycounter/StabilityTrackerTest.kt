package com.example.archerycounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityTrackerTest {

    private val stepMs = 10L // 100Hz

    /** 在 [from, to) 内每 10ms 送一笔加速度：x 轴上下交替 ±amp，叠加固定偏移 [offset] */
    private fun feedAccel(t: StabilityTracker, from: Long, to: Long, amp: Float, offset: Float = 0f) {
        var time = from
        var sign = 1f
        while (time < to) {
            t.addAccel(offset + sign * amp, 0f, 0f, time)
            sign = -sign
            time += stepMs
        }
    }

    private fun feedGyro(t: StabilityTracker, from: Long, to: Long, amp: Float) {
        var time = from
        var sign = 1f
        while (time < to) {
            t.addGyro(0f, sign * amp, 0f, time)
            sign = -sign
            time += stepMs
        }
    }

    @Test
    fun perfectlySteadySignalGivesZero() {
        val t = StabilityTracker()
        feedAccel(t, 0, 3000, amp = 0f)
        val s = t.compute(endMs = 2900, windowMs = 2000)
        assertEquals(0f, s.accelRms!!, 0.0001f)
    }

    @Test
    fun alternatingSignalGivesItsAmplitude() {
        val t = StabilityTracker()
        feedAccel(t, 0, 3000, amp = 0.5f)
        val s = t.compute(endMs = 2900, windowMs = 2000)
        assertEquals(0.5f, s.accelRms!!, 0.01f)
    }

    @Test
    fun constantOffsetIsRemoved() {
        // 手臂在稳定地往一个方向加速，不算抖动
        val t = StabilityTracker()
        feedAccel(t, 0, 3000, amp = 0f, offset = 3f)
        val s = t.compute(endMs = 2900, windowMs = 2000)
        assertEquals(0f, s.accelRms!!, 0.0001f)
    }

    @Test
    fun biggerShakeGivesBiggerNumber() {
        val calm = StabilityTracker()
        val shaky = StabilityTracker()
        feedAccel(calm, 0, 3000, amp = 0.2f)
        feedAccel(shaky, 0, 3000, amp = 1.2f)
        val a = calm.compute(2900, 2000).accelRms!!
        val b = shaky.compute(2900, 2000).accelRms!!
        assertTrue(b > a * 3)
    }

    @Test
    fun onlyWindowBeforeEndIsUsed() {
        val t = StabilityTracker()
        feedAccel(t, 0, 1000, amp = 5f)      // 窗口之前很晃
        feedAccel(t, 1000, 3000, amp = 0.1f) // 窗口内很稳
        feedAccel(t, 3000, 3300, amp = 9f)   // 放箭冲击，不在窗口内
        val s = t.compute(endMs = 2990, windowMs = 1500)
        assertEquals(0.1f, s.accelRms!!, 0.01f)
    }

    @Test
    fun tooFewSamplesGivesNull() {
        val t = StabilityTracker()
        feedAccel(t, 0, 50, amp = 1f) // 只有 5 笔
        val s = t.compute(endMs = 50, windowMs = 2000)
        assertNull(s.accelRms)
    }

    @Test
    fun gyroIsIndependentOfAccel() {
        val t = StabilityTracker()
        feedAccel(t, 0, 3000, amp = 0.3f)
        feedGyro(t, 0, 3000, amp = 4f)
        val s = t.compute(2900, 2000)
        assertEquals(0.3f, s.accelRms!!, 0.01f)
        assertEquals(4f, s.gyroRms!!, 0.05f)
    }

    @Test
    fun noGyroDataGivesNullGyro() {
        val t = StabilityTracker()
        feedAccel(t, 0, 3000, amp = 0.3f)
        val s = t.compute(2900, 2000)
        assertNotNull(s.accelRms)
        assertNull(s.gyroRms)
    }

    @Test
    fun ringBufferKeepsWorkingAfterWrapAround() {
        val t = StabilityTracker(capacity = 256) // 容量故意很小，强制绕圈
        feedAccel(t, 0, 20_000, amp = 0.7f)      // 2000 笔，远超容量
        val s = t.compute(endMs = 19_990, windowMs = 1000)
        assertEquals(0.7f, s.accelRms!!, 0.01f)
    }

    @Test
    fun resetClearsHistory() {
        val t = StabilityTracker()
        feedAccel(t, 0, 3000, amp = 1f)
        t.reset()
        assertNull(t.compute(2900, 2000).accelRms)
    }
}
