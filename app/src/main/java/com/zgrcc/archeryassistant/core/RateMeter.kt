package com.zgrcc.archeryassistant

/**
 * 取樣頻率（v1.9）：設定選項與實測。純 Kotlin，方便單元測試。
 * 手錶的感測器只把「要求的頻率」當成建議，實際可能更慢或更快，所以另外量測實際頻率並顯示。
 */
object SamplingRate {
    val OPTIONS = listOf(50, 100, 200)
    const val DEFAULT_HZ = 100

    fun valid(hz: Int): Int = if (hz in OPTIONS) hz else DEFAULT_HZ

    fun periodUs(hz: Int): Int = 1_000_000 / valid(hz)

    /** 穩定度緩衝區容量：約能放 10 秒資料再多一點餘量，頻率越高越大。 */
    fun trackerCapacity(hz: Int): Int = valid(hz) * 10 + 24

    fun next(hz: Int): Int = OPTIONS[(OPTIONS.indexOf(valid(hz)) + 1) % OPTIONS.size]
}

/**
 * 量測實際取樣頻率。每筆加速度樣本呼叫一次 [onSample]（時間戳用毫秒或奈秒都可以，但單位要固定，
 * 建構時用 [unitsPerSecond] 指定），每隔約 1 秒更新一次 [hz]。
 * 傳感器線程寫、畫面讀，所以欄位用 @Volatile。
 */
class RateMeter(private val unitsPerSecond: Long = 1_000_000_000L) {
    @Volatile
    var hz: Float = 0f
        private set

    private var windowStart = -1L
    private var count = 0

    fun reset() {
        windowStart = -1L
        count = 0
        hz = 0f
    }

    fun onSample(time: Long) {
        if (windowStart < 0) {
            windowStart = time
            count = 0
            return
        }
        count++
        val elapsed = time - windowStart
        if (elapsed >= unitsPerSecond) {
            hz = count * unitsPerSecond.toFloat() / elapsed
            windowStart = time
            count = 0
        }
    }
}
