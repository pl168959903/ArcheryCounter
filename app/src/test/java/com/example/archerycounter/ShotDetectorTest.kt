package com.example.archerycounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotDetectorTest {

    private val stepMs = 20L // 50Hz

    /** 在 [from] 到 [to] 毫秒之间持续送入同一个合力大小的数据 */
    private fun feed(d: ShotDetector, from: Long, to: Long, magnitude: Float) {
        var t = from
        while (t < to) {
            d.onSample(magnitude, 0f, 0f, t)
            t += stepMs
        }
    }

    @Test
    fun countsOneShotAfterSteadyHold() {
        val peaks = mutableListOf<Float>()
        val d = ShotDetector { peak, _ -> peaks += peak }

        feed(d, 0, 2000, 1f)        // 稳定瞄准
        feed(d, 2000, 2060, 28f)    // 放箭冲击
        feed(d, 2060, 3000, 2f)     // 随后动作

        assertEquals(1, peaks.size)
        assertEquals(28f, peaks[0], 0.001f)
    }

    @Test
    fun reportsTriggerTimeOfTheSpike() {
        val triggers = mutableListOf<Long>()
        val d = ShotDetector { _, triggerMs -> triggers += triggerMs }

        feed(d, 0, 2000, 1f)
        feed(d, 2000, 2060, 28f)    // 冲击从 2000ms 开始
        feed(d, 2060, 3000, 2f)

        assertEquals(1, triggers.size)
        assertEquals(2000L, triggers[0])
    }

    @Test
    fun doesNotDoubleCountWithinCooldown() {
        var shots = 0
        val d = ShotDetector { _, _ -> shots++ }

        feed(d, 0, 2000, 1f)
        feed(d, 2000, 2060, 28f)
        feed(d, 2060, 2700, 1f)
        feed(d, 2700, 2760, 28f)    // 700ms 后又一次大冲击，应忽略
        feed(d, 2760, 3500, 1f)

        assertEquals(1, shots)
    }

    @Test
    fun countsTwoShotsFarApart() {
        var shots = 0
        val d = ShotDetector { _, _ -> shots++ }

        feed(d, 0, 2000, 1f)
        feed(d, 2000, 2060, 28f)
        feed(d, 2060, 8000, 1f)     // 取箭、再次拉弓、瞄准
        feed(d, 8000, 8060, 28f)
        feed(d, 8060, 9000, 1f)

        assertEquals(2, shots)
    }

    @Test
    fun ignoresSpikeWhenWristWasMoving() {
        var shots = 0
        val d = ShotDetector { _, _ -> shots++ }

        feed(d, 0, 2000, 9f)        // 一直在大幅晃动，不是瞄准
        feed(d, 2000, 2060, 28f)
        feed(d, 2060, 3000, 9f)

        assertEquals(0, shots)
    }

    @Test
    fun ignoresSmallBumps() {
        var shots = 0
        val d = ShotDetector { _, _ -> shots++ }

        feed(d, 0, 2000, 1f)
        feed(d, 2000, 2060, 8f)     // 低于阈值
        feed(d, 2060, 3000, 1f)

        assertEquals(0, shots)
    }

    @Test
    fun ignoresSpikeRightAfterStart() {
        var shots = 0
        val d = ShotDetector { _, _ -> shots++ }

        feed(d, 0, 100, 30f)        // 刚开始侦测，历史数据不足

        assertEquals(0, shots)
    }

    @Test
    fun thresholdCanBeChangedAtRuntime() {
        var shots = 0
        val d = ShotDetector { _, _ -> shots++ }
        d.spikeThreshold = 6f

        feed(d, 0, 2000, 1f)
        feed(d, 2000, 2060, 8f)
        feed(d, 2060, 3000, 1f)

        assertTrue(shots == 1)
    }

    @Test
    fun defaultThresholdIs16() {
        assertEquals(16f, ShotDetector.DEFAULT_SPIKE_THRESHOLD, 0.0001f)
    }
}
