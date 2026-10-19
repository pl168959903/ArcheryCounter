package com.example.archerycounter

import java.time.Instant
import java.time.ZoneId

/**
 * v1.6 的訓練輔助規則：自動暫停、低電量、今日目標、每趟結束回饋、亮度檔位。
 * 全部是純 Kotlin，方便單元測試；實際的震動、廣播、畫面在 CounterViewModel 與界面。
 */

/** 偵測中太久沒有放箭就自動暫停。 */
object AutoPause {
    const val MIN_MINUTES = 5
    const val MAX_MINUTES = 30
    const val STEP_MINUTES = 5
    const val DEFAULT_MINUTES = 10

    /** [lastActivityMs] 還沒有（小於 0）時不暫停。時間單位都是毫秒，用同一個時鐘。 */
    fun shouldPause(nowMs: Long, lastActivityMs: Long, minutes: Int): Boolean =
        minutes > 0 && lastActivityMs >= 0 && nowMs - lastActivityMs > minutes * 60_000L
}

data class BatteryDecision(val warnNow: Boolean, val warned: Boolean)

/** 電量低於 [LOW_PERCENT]% 時提醒一次；回到 [LOW_PERCENT]+[REARM_MARGIN]% 以上或開始充電才會重新允許提醒。 */
object BatteryRule {
    const val LOW_PERCENT = 15
    const val REARM_MARGIN = 5

    fun decide(percent: Int, charging: Boolean, warned: Boolean): BatteryDecision = when {
        percent < 0 -> BatteryDecision(false, warned) // 讀不到電量：什麼都不做
        charging || percent > LOW_PERCENT + REARM_MARGIN -> BatteryDecision(false, false)
        percent <= LOW_PERCENT && !warned -> BatteryDecision(true, true)
        else -> BatteryDecision(false, warned)
    }
}

/** 今日目標箭數。 */
object Goal {
    const val STEP = 10
    const val MAX = 300

    /** 這一次讓今日合計從目標以下跨到目標（含）以上，才算達標，每天只會觸發一次。 */
    fun reached(prev: Int, now: Int, goal: Int): Boolean = goal > 0 && prev < goal && now >= goal
}

/** 今天（本地日期）已完成的記錄的最終箭數合計，不含校準，並可排除進行中的那一筆。 */
fun todayDone(
    sessions: List<Session>,
    nowMs: Long,
    excludeId: String?,
    zone: ZoneId = ZoneId.systemDefault(),
): Int {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return sessions
        .filter { !it.calibration && it.id != excludeId }
        .filter { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() == today }
        .sumOf { it.finalCount() }
}

/** 一趟射完的回饋：這一趟的平均晃動量，以及和上一趟、和本次平均比起來變化多少（%，負值＝更穩）。 */
data class EndFeedback(
    val endNumber: Int,
    val count: Int,
    val accelAvg: Float,
    val vsPrevPct: Float?,
    val vsSessionPct: Float?,
)

/**
 * 以最新的一趟為準。這一趟沒有加速度晃動量時回傳 null。
 * 「和本次平均比」要有兩趟以上才有意義（只有一趟時平均就是它自己）。
 */
fun buildEndFeedback(session: Session, ends: List<EndStats>): EndFeedback? {
    val last = ends.lastOrNull() ?: return null
    val cur = last.accelAvg ?: return null
    val prev = ends.getOrNull(ends.size - 2)?.accelAvg
    val sessionAvg = if (ends.size >= 2) avgOrNull(session.validShots().map { it.accelRms }) else null

    fun pct(base: Float?): Float? =
        if (base == null || base <= 1e-6f) null else (cur - base) / base * 100f

    return EndFeedback(last.number, last.count, cur, pct(prev), pct(sessionAvg))
}

/** 變化百分比的文字。變化不到 [SAME_PCT]% 算「差不多」。 */
fun describeChange(pct: Float?): String = when {
    pct == null -> "—"
    kotlin.math.abs(pct) < SAME_PCT -> "差不多"
    pct < 0 -> "較穩 ${kotlin.math.abs(pct).toInt()}%"
    else -> "較抖 ${pct.toInt()}%"
}

private const val SAME_PCT = 3f

/** 偵測中降低亮度的檔位。 */
object Brightness {
    val LEVELS = floatArrayOf(0.2f, 0.4f, 0.6f)
    val LABELS = listOf("低 20%", "中 40%", "高 60%")
    const val DEFAULT_INDEX = 1

    fun level(index: Int): Float = LEVELS[index.coerceIn(0, LEVELS.size - 1)]
    fun label(index: Int): String = LABELS[index.coerceIn(0, LABELS.size - 1)]
    fun next(index: Int): Int = (index + 1) % LEVELS.size
}
