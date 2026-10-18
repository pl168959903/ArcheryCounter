package com.example.archerycounter

import kotlin.math.max
import kotlin.math.sqrt

/**
 * 放箭侦测器。纯 Kotlin，不依赖 Android，方便单元测试。
 *
 * 输入：去除重力后的线性加速度 (x, y, z)，单位 m/s²，以及时间戳（毫秒）。
 *
 * 判定一次放箭需要同时满足：
 *   1. 加速度合力 >= [spikeThreshold]（放箭瞬间的冲击）
 *   2. 冲击之前约 0.5 秒手腕比较稳（拉满弓后的瞄准阶段），合力始终 < [stillThreshold]
 *   3. 距离上一次计数已超过 [COOLDOWN_MS]（避免一次放箭被算成多次）
 *
 * 触发后会再收集 [CAPTURE_MS] 毫秒数据，取得这次冲击的真正峰值，然后回调 [onShot]。
 * 峰值会显示在手表上，用来帮你调整 [spikeThreshold]。
 */
class ShotDetector(
    var spikeThreshold: Float = DEFAULT_SPIKE_THRESHOLD,
    var stillThreshold: Float = DEFAULT_STILL_THRESHOLD,
    private val onShot: (peak: Float) -> Unit,
) {
    private class Sample(val timeMs: Long, val magnitude: Float)

    private val history = ArrayDeque<Sample>()
    private var lastTriggerMs = NEVER
    private var captureStartMs = NOT_CAPTURING
    private var capturePeak = 0f

    fun reset() {
        history.clear()
        lastTriggerMs = NEVER
        captureStartMs = NOT_CAPTURING
        capturePeak = 0f
    }

    fun onSample(x: Float, y: Float, z: Float, timeMs: Long) {
        val magnitude = sqrt(x * x + y * y + z * z)

        history.addLast(Sample(timeMs, magnitude))
        while (history.isNotEmpty() && timeMs - history.first().timeMs > STILL_WINDOW_MS + GAP_MS) {
            history.removeFirst()
        }

        // 已经触发，正在收集峰值
        if (captureStartMs != NOT_CAPTURING) {
            capturePeak = max(capturePeak, magnitude)
            if (timeMs - captureStartMs >= CAPTURE_MS) {
                captureStartMs = NOT_CAPTURING
                onShot(capturePeak)
            }
            return
        }

        if (magnitude < spikeThreshold) return
        if (timeMs - lastTriggerMs < COOLDOWN_MS) return
        if (!wasStill(timeMs)) return

        lastTriggerMs = timeMs
        captureStartMs = timeMs
        capturePeak = magnitude
    }

    /** 冲击之前（不含最近 [GAP_MS]，那是冲击的上升沿）的 [STILL_WINDOW_MS] 内是否一直很稳。 */
    private fun wasStill(now: Long): Boolean {
        var samples = 0
        for (s in history) {
            val age = now - s.timeMs
            if (age < GAP_MS || age > STILL_WINDOW_MS + GAP_MS) continue
            if (s.magnitude > stillThreshold) return false
            samples++
        }
        // 刚开始侦测、数据不够时不计数
        return samples >= MIN_STILL_SAMPLES
    }

    companion object {
        const val DEFAULT_SPIKE_THRESHOLD = 15f   // m/s²，需要实测调整
        const val DEFAULT_STILL_THRESHOLD = 5f    // m/s²
        const val STILL_WINDOW_MS = 500L
        const val GAP_MS = 100L
        const val CAPTURE_MS = 300L
        const val COOLDOWN_MS = 3000L
        const val MIN_STILL_SAMPLES = 10

        private const val NEVER = -1_000_000L
        private const val NOT_CAPTURING = -1L
    }
}
