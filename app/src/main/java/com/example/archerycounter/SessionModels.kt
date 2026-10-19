package com.example.archerycounter

/** 一箭的记录。[relMs] 是距这次记录开始的毫秒数。 */
data class ShotEntry(
    val relMs: Long,
    /** 放箭冲击的峰值，m/s² */
    val peak: Float,
    /** 放箭当时使用的阈值 */
    val threshold: Float,
    /** 放箭前窗口内的加速度晃动量，m/s²；没有数据时为 null */
    val accelRms: Float?,
    /** 放箭前窗口内的角速度晃动量，°/s；没有陀螺仪或数据不足时为 null */
    val gyroRms: Float?,
)

/** 手动加减。[delta] 是实际改变的量（+1 或 −1）。 */
data class ManualEntry(val relMs: Long, val delta: Int)

/**
 * 一次记录 = 从按下「開始偵測」到「暫停偵測」。
 * [endMs] 为 null 表示没有正常结束（进行中，或应用被系统中途关掉）。
 * [threshold] 是开始时的阈值；中途调整过的话，每一箭另有自己的阈值。
 */
data class Session(
    val id: String,
    val startMs: Long,
    val endMs: Long?,
    val threshold: Float,
    val windowSec: Int,
    val hasGyro: Boolean,
    val rawCsv: Boolean,
    val shots: List<ShotEntry>,
    val manual: List<ManualEntry>,
) {
    /** 正常结束：结束时间 − 开始时间。没有结束：用最后一个事件的时间。 */
    fun durationMs(): Long {
        val end = endMs
        if (end != null) return (end - startMs).coerceAtLeast(0L)
        val lastShot = shots.maxOfOrNull { it.relMs } ?: 0L
        val lastManual = manual.maxOfOrNull { it.relMs } ?: 0L
        return maxOf(lastShot, lastManual)
    }

    /** 最终箭数 = 偵測箭數 + 手動加減，不小於 0。 */
    fun finalCount(): Int = (shots.size + manual.sumOf { it.delta }).coerceAtLeast(0)
}

/** 某个指标里「第几箭」是最稳／最不稳。[number] 从 1 开始。 */
data class ShotRank(val number: Int, val value: Float)

/** 前 1/3 与后 1/3 的平均，用来看疲劳之后是不是变得更抖。 */
data class Trend(val first: Float, val last: Float)

data class SessionStats(
    val detected: Int,
    val manualPlus: Int,
    val manualMinus: Int,
    val finalCount: Int,
    val thresholdMin: Float,
    val thresholdMax: Float,
    val peakAvg: Float?,
    val peakMax: Float?,
    val peakMin: Float?,
    val intervalAvgSec: Float?,
    val accelAvg: Float?,
    val accelSteadiest: ShotRank?,
    val accelShakiest: ShotRank?,
    val accelTrend: Trend?,
    val gyroAvg: Float?,
    val gyroSteadiest: ShotRank?,
    val gyroShakiest: ShotRank?,
    val gyroTrend: Trend?,
)

private class MetricStats(
    val avg: Float?,
    val steadiest: ShotRank?,
    val shakiest: ShotRank?,
    val trend: Trend?,
)

/** 趋势至少要这么多箭（有数值的）才算，太少没有意义。 */
private const val MIN_SHOTS_FOR_TREND = 6

fun computeStats(s: Session): SessionStats {
    val peaks = s.shots.map { it.peak }
    val intervals = s.shots.zipWithNext { a, b -> (b.relMs - a.relMs) / 1000f }

    // 编号是「实际第几箭」，所以没有数值的箭要略过但不能影响编号
    val accel = metric(s.shots.mapIndexedNotNull { i, sh -> sh.accelRms?.let { ShotRank(i + 1, it) } })
    val gyro = metric(s.shots.mapIndexedNotNull { i, sh -> sh.gyroRms?.let { ShotRank(i + 1, it) } })

    val thresholds = s.shots.map { it.threshold } + s.threshold
    val plus = s.manual.filter { it.delta > 0 }.sumOf { it.delta }
    val minus = s.manual.filter { it.delta < 0 }.sumOf { -it.delta }

    return SessionStats(
        detected = s.shots.size,
        manualPlus = plus,
        manualMinus = minus,
        finalCount = (s.shots.size + plus - minus).coerceAtLeast(0),
        thresholdMin = thresholds.min(),
        thresholdMax = thresholds.max(),
        peakAvg = if (peaks.isEmpty()) null else peaks.average().toFloat(),
        peakMax = peaks.maxOrNull(),
        peakMin = peaks.minOrNull(),
        intervalAvgSec = if (intervals.isEmpty()) null else intervals.average().toFloat(),
        accelAvg = accel.avg,
        accelSteadiest = accel.steadiest,
        accelShakiest = accel.shakiest,
        accelTrend = accel.trend,
        gyroAvg = gyro.avg,
        gyroSteadiest = gyro.steadiest,
        gyroShakiest = gyro.shakiest,
        gyroTrend = gyro.trend,
    )
}

private fun metric(values: List<ShotRank>): MetricStats {
    if (values.isEmpty()) return MetricStats(null, null, null, null)
    val nums = values.map { it.value }
    val k = values.size / 3
    val trend = if (values.size >= MIN_SHOTS_FOR_TREND) {
        Trend(
            first = nums.take(k).average().toFloat(),
            last = nums.takeLast(k).average().toFloat(),
        )
    } else {
        null
    }
    return MetricStats(
        avg = nums.average().toFloat(),
        steadiest = values.minBy { it.value },
        shakiest = values.maxBy { it.value },
        trend = trend,
    )
}
