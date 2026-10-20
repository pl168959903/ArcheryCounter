package com.zgrcc.archeryassistant

import kotlin.math.max
import kotlin.math.sqrt

/**
 * 一箭放箭前的二維軌跡（v1.12）：手錶表面 x、y 兩軸的加速度（m/s²）或角速度（°/s），
 * 每 [stepMs] 毫秒一個點（該段的平均）。[gyro] 為 true 表示是角速度。
 */
data class Trace2D(
    val stepMs: Int,
    val gyro: Boolean,
    val x: List<Float>,
    val y: List<Float>,
)

/** 二維圖的計算。純 Kotlin。 */
object TraceMath {
    /** 圖的外圈大小（加速度 m/s²）可選的級距 */
    private val ACCEL_RANGES = floatArrayOf(1f, 2f, 3f, 5f, 8f, 12f, 20f)
    /** 角速度 °/s */
    private val GYRO_RANGES = floatArrayOf(20f, 40f, 80f, 150f, 300f, 600f, 1200f)

    fun mean(v: List<Float>): Float = if (v.isEmpty()) 0f else (v.sum() / v.size)

    /** 扣掉平均值，讓點圍著圓心分布（和晃動量的算法一致：去掉緩慢、持續的移動） */
    fun centered(v: List<Float>): List<Float> {
        val m = mean(v)
        return v.map { it - m }
    }

    /** 均方根半徑：√平均(x² + y²)，x、y 已扣平均值。沒有點為 0。 */
    fun rmsRadius(x: List<Float>, y: List<Float>): Float {
        val n = minOf(x.size, y.size)
        if (n == 0) return 0f
        var s = 0.0
        for (i in 0 until n) s += (x[i] * x[i] + y[i] * y[i]).toDouble()
        return sqrt(s / n).toFloat()
    }

    /** 離圓心最遠的點的距離 */
    fun maxRadius(x: List<Float>, y: List<Float>): Float {
        val n = minOf(x.size, y.size)
        var m = 0f
        for (i in 0 until n) m = max(m, sqrt(x[i] * x[i] + y[i] * y[i]))
        return m
    }

    /** 選一個能放下 [maxDist]（再留 10%）的外圈大小；超過最大級距就用最大級距。 */
    fun niceRange(maxDist: Float, gyro: Boolean): Float {
        val steps = if (gyro) GYRO_RANGES else ACCEL_RANGES
        val need = maxDist * 1.1f
        return steps.firstOrNull { it >= need } ?: steps.last()
    }

    /** 落在半徑 [r] 以內的點的比例（0～1）。沒有點為 0。 */
    fun insideRatio(x: List<Float>, y: List<Float>, r: Float): Float {
        val n = minOf(x.size, y.size)
        if (n == 0) return 0f
        var c = 0
        for (i in 0 until n) if (sqrt(x[i] * x[i] + y[i] * y[i]) <= r) c++
        return c.toFloat() / n
    }
}

/**
 * 即時晃動圓盤用的緩衝區：把 x、y 樣本壓成每 [binMs] 毫秒一格（取平均），只留最近 [capacity] 格
 * （預設 50 格 × 40ms = 2 秒）。傳感器線程寫入、畫面線程讀取，所以加了鎖。
 */
class LiveTrace(private val binMs: Int = 40, private val capacity: Int = 50) {
    private val xs = FloatArray(capacity)
    private val ys = FloatArray(capacity)
    private var count = 0
    private var next = 0
    private var currentBin = NO_BIN
    private var sumX = 0f
    private var sumY = 0f
    private var n = 0

    init {
        require(binMs > 0 && capacity > 0) { "binMs and capacity must be positive" }
    }

    @Synchronized
    fun clear() {
        count = 0
        next = 0
        currentBin = NO_BIN
        sumX = 0f
        sumY = 0f
        n = 0
    }

    @Synchronized
    fun add(timeMs: Long, x: Float, y: Float) {
        val bin = timeMs / binMs
        if (currentBin == NO_BIN) {
            currentBin = bin
        } else if (bin != currentBin) {
            flush()
            currentBin = bin
        }
        sumX += x
        sumY += y
        n++
    }

    private fun flush() {
        if (n == 0) return
        xs[next] = sumX / n
        ys[next] = sumY / n
        next = (next + 1) % capacity
        if (count < capacity) count++
        sumX = 0f
        sumY = 0f
        n = 0
    }

    /** 由舊到新的快照，最後一格是正在累積的那一格。 */
    @Synchronized
    fun snapshot(): Pair<FloatArray, FloatArray> {
        val pending = n > 0
        val size = count + if (pending) 1 else 0
        val ox = FloatArray(size)
        val oy = FloatArray(size)
        val oldest = if (count < capacity) 0 else next
        for (k in 0 until count) {
            ox[k] = xs[(oldest + k) % capacity]
            oy[k] = ys[(oldest + k) % capacity]
        }
        if (pending) {
            ox[count] = sumX / n
            oy[count] = sumY / n
        }
        return ox to oy
    }

    private companion object {
        const val NO_BIN = Long.MIN_VALUE
    }
}
