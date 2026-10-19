package com.example.archerycounter

/**
 * 即时波形用的缓冲区。把传感器样本压成每 [binMs] 毫秒一格（取最大值），
 * 只保留最近 [capacity] 格（预设 125 格 × 40ms = 5 秒）。
 *
 * 传感器线程写入、画面线程读取，所以加了锁。纯 Kotlin。
 */
class LiveBuffer(private val binMs: Int = DEFAULT_BIN_MS, private val capacity: Int = DEFAULT_CAPACITY) {

    private val values = FloatArray(capacity)
    private var count = 0
    private var next = 0
    private var currentBin = NO_BIN
    private var currentMax = 0f

    init {
        require(binMs > 0 && capacity > 0) { "binMs and capacity must be positive" }
    }

    @Synchronized
    fun clear() {
        count = 0
        next = 0
        currentBin = NO_BIN
        currentMax = 0f
    }

    @Synchronized
    fun add(timeMs: Long, value: Float) {
        val bin = timeMs / binMs
        when {
            currentBin == NO_BIN -> {
                currentBin = bin
                currentMax = value
            }
            bin == currentBin -> if (value > currentMax) currentMax = value
            else -> {
                values[next] = currentMax
                next = (next + 1) % capacity
                if (count < capacity) count++
                currentBin = bin
                currentMax = value
            }
        }
    }

    /** 按时间顺序（旧 → 新）的快照，最后一格是正在累积的那一格。 */
    @Synchronized
    fun snapshot(): FloatArray {
        val pending = currentBin != NO_BIN
        val out = FloatArray(count + if (pending) 1 else 0)
        val oldest = if (count < capacity) 0 else next
        for (k in 0 until count) out[k] = values[(oldest + k) % capacity]
        if (pending) out[count] = currentMax
        return out
    }

    companion object {
        const val DEFAULT_BIN_MS = 40
        const val DEFAULT_CAPACITY = 125
        private const val NO_BIN = Long.MIN_VALUE
    }
}
