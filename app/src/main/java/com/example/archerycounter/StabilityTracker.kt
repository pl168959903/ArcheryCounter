package com.example.archerycounter

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

        fun add(timeMs: Long, vx: Float, vy: Float, vz: Float) {
            t[next] = timeMs
            x[next] = vx
            y[next] = vy
            z[next] = vz
            next = (next + 1) % capacity
            if (size < capacity) size++
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
    }
}
