package com.zgrcc.archeryassistant

import kotlin.math.sqrt

/** 一箭放箭前的稳定度。数值越小越稳；没有数据（或数据不足）时为 null。 */
data class Stability(
    /** 加速度晃动量，m/s² */
    val accelRms: Float?,
    /** 角速度晃动量，°/s */
    val gyroRms: Float?,
)

/**
 * 稳定度（晃动量）计算。纯 Kotlin，不依赖 Android。
 *
 * 持续接收加速度（去除重力后，m/s²）和角速度（°/s）样本，各自放进固定容量的环形缓冲区。
 * 需要时取某个时间窗口内的晃动量：
 *
 *   晃动量 = √( 平均( |样本 − 窗口平均值|² ) )
 *
 * 先扣掉窗口平均值，是为了去掉缓慢、持续的移动（例如手臂稳定地往一个方向加速），只留下抖动成分。
 * 数值越小代表手臂越稳。
 *
 * 只在传感器线程使用，所以没有加锁。
 */
class StabilityTracker(capacity: Int = DEFAULT_CAPACITY) {

    private val accel = Ring(capacity)
    private val gyro = Ring(capacity)

    fun reset() {
        accel.clear()
        gyro.clear()
    }

    /** x/y/z 单位 m/s²（已扣除重力） */
    fun addAccel(x: Float, y: Float, z: Float, timeMs: Long) = accel.add(timeMs, x, y, z)

    /** x/y/z 单位 °/s */
    fun addGyro(x: Float, y: Float, z: Float, timeMs: Long) = gyro.add(timeMs, x, y, z)

    /**
     * 计算窗口 (endMs − windowMs, endMs] 内的晃动量。
     * 窗口内样本少于 [MIN_SAMPLES] 笔，或数据只覆盖窗口不到一半（刚开始侦测）时，对应的值为 null。
     */
    fun compute(endMs: Long, windowMs: Long): Stability =
        Stability(
            accelRms = accel.shake(endMs, windowMs),
            gyroRms = gyro.shake(endMs, windowMs),
        )

    /**
     * 放箭后的跟随晃动量：放箭后 [FOLLOW_START_MS]～[FOLLOW_END_MS] 毫秒这一段的晃动量
     * （跳过放箭冲击本身）。放箭后的数据还不够（例如太早暂停）时，对应的值为 null。
     */
    fun followThrough(triggerMs: Long): Stability {
        val endMs = triggerMs + FOLLOW_END_MS
        // 加速度数据还没走到放箭后 1 秒（太早暂停），这一段是不完整的，不算
        val latest = accel.latestMs()
        if (latest == null || latest < endMs - FOLLOW_TOLERANCE_MS) return Stability(null, null)
        return compute(endMs = endMs, windowMs = FOLLOW_END_MS - FOLLOW_START_MS)
    }

    /**
     * 取一箭放箭前后的降采样波形：窗口 (triggerMs − preMs, triggerMs + postMs]，
     * 每 [binMs] 毫秒一个点，取这段内合力大小的最大值（所以放箭的尖峰不会被平均掉）。
     * 加速度样本太少时回传 null；没有陀螺仪数据时 gyro 为空。
     */
    fun waveform(triggerMs: Long, preMs: Long, postMs: Long, binMs: Int = DEFAULT_BIN_MS): Waveform? {
        val startMs = triggerMs - preMs
        val endMs = triggerMs + postMs
        val a = accel.binMax(startMs, endMs, binMs) ?: return null
        val g = gyro.binMax(startMs, endMs, binMs)
        return Waveform(
            stepMs = binMs,
            impactIndex = (preMs / binMs).toInt().coerceIn(0, a.size - 1),
            accel = a.toList(),
            gyro = g?.toList() ?: emptyList(),
        )
    }

    private class Ring(private val capacity: Int) {
        private val t = LongArray(capacity)
        private val x = FloatArray(capacity)
        private val y = FloatArray(capacity)
        private val z = FloatArray(capacity)
        private var size = 0
        private var next = 0

        init {
            require(capacity >= 2) { "capacity must be at least 2" }
        }

        fun clear() {
            size = 0
            next = 0
        }

        /** 最新一笔样本的时间；没有样本为 null */
        fun latestMs(): Long? = if (size == 0) null else t[(next - 1 + capacity) % capacity]

        fun add(timeMs: Long, vx: Float, vy: Float, vz: Float) {
            t[next] = timeMs
            x[next] = vx
            y[next] = vy
            z[next] = vz
            next = (next + 1) % capacity
            if (size < capacity) size++
        }

        /** 窗口内每 [binMs] 一格，取合力大小的最大值。空格沿用前一格（开头的空格用第一个有值的）。 */
        fun binMax(startMs: Long, endMs: Long, binMs: Int): FloatArray? {
            val span = endMs - startMs
            if (span <= 0 || binMs <= 0) return null
            val bins = ((span + binMs - 1) / binMs).toInt()
            val out = FloatArray(bins) { Float.NaN }
            val oldest = if (size < capacity) 0 else next

            var n = 0
            for (k in 0 until size) {
                val i = (oldest + k) % capacity
                val ti = t[i]
                if (ti <= startMs || ti > endMs) continue
                val b = ((ti - startMs - 1) / binMs).toInt().coerceIn(0, bins - 1)
                val m = sqrt(x[i] * x[i] + y[i] * y[i] + z[i] * z[i])
                if (out[b].isNaN() || m > out[b]) out[b] = m
                n++
            }
            if (n < MIN_SAMPLES) return null

            var prev = out.first { !it.isNaN() }
            for (j in 0 until bins) {
                if (out[j].isNaN()) out[j] = prev else prev = out[j]
            }
            return out
        }

        fun shake(endMs: Long, windowMs: Long): Float? {
            val startMs = endMs - windowMs
            val oldest = if (size < capacity) 0 else next

            var n = 0
            var firstMs = 0L
            var sx = 0.0
            var sy = 0.0
            var sz = 0.0
            var sxx = 0.0
            var syy = 0.0
            var szz = 0.0

            for (k in 0 until size) {
                val i = (oldest + k) % capacity
                val ti = t[i]
                if (ti <= startMs || ti > endMs) continue
                if (n == 0) firstMs = ti
                val vx = x[i].toDouble()
                val vy = y[i].toDouble()
                val vz = z[i].toDouble()
                sx += vx
                sy += vy
                sz += vz
                sxx += vx * vx
                syy += vy * vy
                szz += vz * vz
                n++
            }

            if (n < MIN_SAMPLES) return null
            if (endMs - firstMs < windowMs / 2) return null

            val mx = sx / n
            val my = sy / n
            val mz = sz / n
            val variance = (sxx / n - mx * mx) + (syy / n - my * my) + (szz / n - mz * mz)
            return sqrt(variance.coerceAtLeast(0.0)).toFloat()
        }
    }

    companion object {
        /** 100Hz 下约 10 秒；比最长的 3 秒窗口加冲击收集时间绰绰有余 */
        const val DEFAULT_CAPACITY = 1024
        const val MIN_SAMPLES = 10
        /** 单箭波形每个点代表 40ms（25Hz） */
        const val DEFAULT_BIN_MS = 40
        /** 放箭后跟随动作的分析区间：0.3～1.0 秒（0.3 秒内还是放箭冲击） */
        const val FOLLOW_START_MS = 300L
        const val FOLLOW_END_MS = 1000L
        /** 最新样本离放箭后 1 秒差多少以内还算数据完整（约 5 个样本） */
        const val FOLLOW_TOLERANCE_MS = 50L
    }
}
