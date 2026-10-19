package com.example.archerycounter

/**
 * 一箭放箭前后的一小段降采样波形。每个点是该时间段（[stepMs]）内合力大小的最大值，
 * 这样放箭冲击这种很短的尖峰不会被平均掉。
 */
data class Waveform(
    val stepMs: Int,
    /** 放箭冲击开始的位置（第几个点，从 0 开始），画图时用来标竖线 */
    val impactIndex: Int,
    /** 线性加速度合力大小，m/s² */
    val accel: List<Float>,
    /** 角速度合力大小，°/s；没有陀螺仪时为空 */
    val gyro: List<Float>,
)

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
    /** 放箭前后的波形；没开「單箭波形」记录或数据不足时为 null */
    val waveform: Waveform? = null,
    /** 被标记为误判。数据保留，但不计入箭数、统计和图表。 */
    val cancelled: Boolean = false,
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
    /** 有效的箭（没有被标记为误判的）。 */
    fun validShots(): List<ShotEntry> = shots.filter { !it.cancelled }

    /** 正常结束：结束时间 − 开始时间。没有结束：用最后一个事件的时间。 */
    fun durationMs(): Long {
        val end = endMs
        if (end != null) return (end - startMs).coerceAtLeast(0L)
        val lastShot = shots.maxOfOrNull { it.relMs } ?: 0L
        val lastManual = manual.maxOfOrNull { it.relMs } ?: 0L
        return maxOf(lastShot, lastManual)
    }

    /** 最终箭数 = 有效的偵測箭数 + 手動加減，不小於 0。 */
    fun finalCount(): Int = (shots.count { !it.cancelled } + manual.sumOf { it.delta }).coerceAtLeast(0)
}

/** 某个指标里「第几箭」是最稳／最不稳。[number] 从 1 开始，只算有效的箭。 */
data class ShotRank(val number: Int, val value: Float)

/** 前 1/3 与后 1/3 的平均，用来看疲劳之后是不是变得更抖。 */
data class Trend(val first: Float, val last: Float)

data class SessionStats(
    /** 有效的偵測箭数（不含误判） */
    val detected: Int,
    /** 被标记为误判的笔数 */
    val cancelled: Int,
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

/**
 * 有效的箭里，某个指标的（第几箭，数值）序列，趋势图和统计都用它。
 * 编号只算有效的箭；没有数值的箭略过，但不影响编号。
 */
fun metricSeries(s: Session, gyro: Boolean): List<ShotRank> =
    s.validShots().mapIndexedNotNull { i, sh ->
        (if (gyro) sh.gyroRms else sh.accelRms)?.let { ShotRank(i + 1, it) }
    }

fun computeStats(s: Session): SessionStats {
    val valid = s.validShots()
    val peaks = valid.map { it.peak }
    val intervals = valid.zipWithNext { a, b -> (b.relMs - a.relMs) / 1000f }

    val accel = metric(metricSeries(s, gyro = false))
    val gyro = metric(metricSeries(s, gyro = true))

    val thresholds = valid.map { it.threshold } + s.threshold
    val plus = s.manual.filter { it.delta > 0 }.sumOf { it.delta }
    val minus = s.manual.filter { it.delta < 0 }.sumOf { -it.delta }

    return SessionStats(
        detected = valid.size,
        cancelled = s.shots.size - valid.size,
        manualPlus = plus,
        manualMinus = minus,
        finalCount = (valid.size + plus - minus).coerceAtLeast(0),
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
