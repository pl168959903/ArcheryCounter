package com.zgrcc.archeryassistant

import kotlin.math.max
import kotlin.math.sqrt

/**
 * 放箭侦测器。纯 Kotlin，不依赖 Android，方便单元测试。
 *
 * 输入：去除重力后的线性加速度 (x, y, z)，单位 m/s²，以及时间戳（毫秒）。
 *
 * 判定一次放箭需要同时满足：
 *   1. 加速度合力 >= [spikeThreshold]（放箭瞬间的冲击）
 *   2. 冲击之前约 0.5 秒手腕比较稳（拉满弓后的瞄准阶段），合力始终 < [stillThreshold]；
 *      「進階放箭判定」開啟時，這段穩定窗口和冲击之间留 [gapMs]（預設 250 毫秒，舊判定 100 毫秒），
 *      避免雙峰放箭的前一個小峰被當成「還沒靜止」；並且角速度也要 < [gyroStill]（0 = 不檢查）
 *   3. 距离上一次计数已超过 [cooldownMs]（预设 [COOLDOWN_MS]，避免一次放箭被算成多次）
 *
 * 触发后会再收集 [CAPTURE_MS] 毫秒数据，取得这次冲击的真正峰值，然后回调 [onShot]
 * 回调带两个值：峰值，以及冲击开始（触发）的时间戳，后者用来回头计算放箭前的稳定度。
 * 峰值会显示在手表上，用来帮你调整 [spikeThreshold]。
 */
class ShotDetector(
    @Volatile var spikeThreshold: Float = DEFAULT_SPIKE_THRESHOLD,
    @Volatile var stillThreshold: Float = DEFAULT_STILL_THRESHOLD,
    /** 两次计数之间的最短间隔（毫秒），可在设定页调整 */
    @Volatile var cooldownMs: Long = COOLDOWN_MS,
    /** 冲击之前不檢查穩定的緩衝（毫秒）。舊判定 [GAP_MS]；進階判定 [ADVANCED_GAP_MS] */
    @Volatile var gapMs: Long = GAP_MS,
    /** 穩定窗口內角速度的上限（°/s）；0 = 不檢查。沒有陀螺儀時角速度恆為 0，不受影響 */
    @Volatile var gyroStill: Float = 0f,
    private val onShot: (peak: Float, triggerMs: Long) -> Unit,
) {
    private class Sample(val timeMs: Long, val magnitude: Float, val gyro: Float)

    private val history = ArrayDeque<Sample>()
    private var lastTriggerMs = NEVER
    private var captureStartMs = NOT_CAPTURING
    private var capturePeak = 0f
    @Volatile private var lastGyro = 0f

    /** 最近一筆角速度合力（°/s）。每筆加速度資料會帶上最近的角速度。 */
    fun onGyro(magnitude: Float) {
        lastGyro = magnitude
    }

    fun reset() {
        lastGyro = 0f
        history.clear()
        lastTriggerMs = NEVER
        captureStartMs = NOT_CAPTURING
        capturePeak = 0f
    }

    fun onSample(x: Float, y: Float, z: Float, timeMs: Long) {
        val magnitude = sqrt(x * x + y * y + z * z)

        history.addLast(Sample(timeMs, magnitude, lastGyro))
        while (history.isNotEmpty() && timeMs - history.first().timeMs > STILL_WINDOW_MS + gapMs) {
            history.removeFirst()
        }

        // 已经触发，正在收集峰值
        if (captureStartMs != NOT_CAPTURING) {
            capturePeak = max(capturePeak, magnitude)
            if (timeMs - captureStartMs >= CAPTURE_MS) {
                val triggerMs = captureStartMs
                captureStartMs = NOT_CAPTURING
                onShot(capturePeak, triggerMs)
            }
            return
        }

        if (magnitude < spikeThreshold) return
        if (timeMs - lastTriggerMs < cooldownMs) return
        if (!wasStill(timeMs)) return

        lastTriggerMs = timeMs
        captureStartMs = timeMs
        capturePeak = magnitude
    }

    /** 冲击之前（不含最近 [gapMs]，那是冲击的上升沿）的 [STILL_WINDOW_MS] 内是否一直很稳。 */
    private fun wasStill(now: Long): Boolean {
        var samples = 0
        val gap = gapMs
        val gyroLimit = gyroStill
        for (s in history) {
            val age = now - s.timeMs
            if (age < gap || age > STILL_WINDOW_MS + gap) continue
            if (s.magnitude > stillThreshold) return false
            if (gyroLimit > 0f && s.gyro > gyroLimit) return false
            samples++
        }
        // 刚开始侦测、数据不够时不计数
        return samples >= MIN_STILL_SAMPLES
    }

    companion object {
        const val DEFAULT_SPIKE_THRESHOLD = 16f   // m/s²，实测 16~17 比较稳
        const val DEFAULT_STILL_THRESHOLD = 5f    // m/s²
        const val STILL_WINDOW_MS = 500L
        const val GAP_MS = 100L
        /** 進階放箭判定的緩衝（毫秒），依實測資料：雙峰放箭的前峰約在放箭前 150～250 毫秒 */
        const val ADVANCED_GAP_MS = 250L
        /** 進階放箭判定的角速度穩定上限（°/s）。實測真放箭最高約 36，誤判約 76～88 */
        const val ADVANCED_GYRO_STILL = 40f
        const val CAPTURE_MS = 300L
        const val COOLDOWN_MS = 3000L
        const val MIN_STILL_SAMPLES = 10

        private const val NEVER = -1_000_000L
        private const val NOT_CAPTURING = -1L
    }
}
