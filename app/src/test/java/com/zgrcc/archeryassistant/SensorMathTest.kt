package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorMathTest {
    @Test
    fun firstSampleIsTakenAsGravity() {
        val f = GravityFilter()
        val o = FloatArray(3)
        f.remove(0f, 0f, 9.8f, o)
        assertEquals(0f, o[0], 1e-6f); assertEquals(0f, o[1], 1e-6f); assertEquals(0f, o[2], 1e-4f)
    }

    @Test
    fun gravityConvergesAndSpikePassesThrough() {
        val f = GravityFilter()
        val o = FloatArray(3)
        repeat(200) { f.remove(0f, 0f, 9.8f, o) }
        assertEquals(0f, o[2], 1e-3f)
        f.remove(5f, 0f, 9.8f, o)
        assertTrue(o[0] > 4f)
    }

    @Test
    fun resetRestartsFromNewGravity() {
        val f = GravityFilter()
        val o = FloatArray(3)
        repeat(50) { f.remove(0f, 0f, 9.8f, o) }
        f.reset()
        f.remove(9.8f, 0f, 0f, o)
        assertEquals(0f, o[0], 1e-4f); assertEquals(0f, o[2], 1e-4f)
    }

    @Test
    fun buzzPriority() {
        val off = EndVibration.OFF
        val on = EndVibration.THREE
        assertEquals(ShotBuzz.GOAL, ShotBuzzRule.pick(true, true, true, on))
        assertEquals(ShotBuzz.FATIGUE, ShotBuzzRule.pick(false, true, true, on))
        assertEquals(ShotBuzz.END, ShotBuzzRule.pick(false, false, true, on))
        assertEquals(ShotBuzz.SHOT, ShotBuzzRule.pick(false, false, true, off))
        assertEquals(ShotBuzz.SHOT, ShotBuzzRule.pick(false, false, false, on))
    }
}
