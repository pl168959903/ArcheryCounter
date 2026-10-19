package com.example.archerycounter

import java.nio.file.Files
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V16Test {

    private val zone = ZoneId.of("Asia/Taipei")
    // 2026-10-20 12:00 台北時間
    private val noon = java.time.ZonedDateTime.of(2026, 10, 20, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L

    private fun shot(sec: Int, acc: Float?, cancelled: Boolean = false) = ShotEntry(
        relMs = sec * 1000L, peak = 17f, threshold = 16f, accelRms = acc, gyroRms = null, cancelled = cancelled,
    )

    private fun session(
        shots: List<ShotEntry>,
        id: String = "t",
        start: Long = noon,
        calibration: Boolean = false,
        manual: List<ManualEntry> = emptyList(),
    ) = Session(
        id = id, startMs = start, endMs = start + 600_000L, threshold = 16f, windowSec = 2,
        hasGyro = false, rawCsv = false, shots = shots, manual = manual, calibration = calibration,
    )

    // ---- 自動暫停 ----

    @Test
    fun autoPauseOnlyAfterLongIdle() {
        val last = 100_000L
        assertFalse(AutoPause.shouldPause(last + 9 * 60_000L, last, 10))
        assertFalse(AutoPause.shouldPause(last + 10 * 60_000L, last, 10)) // 剛好 10 分鐘還不算
        assertTrue(AutoPause.shouldPause(last + 10 * 60_000L + 1, last, 10))
    }

    @Test
    fun autoPauseIgnoredWithoutActivityOrWithZeroMinutes() {
        assertFalse(AutoPause.shouldPause(999_999_999L, -1L, 10)) // 還沒有任何活動時間
        assertFalse(AutoPause.shouldPause(999_999_999L, 0L, 0))
    }

    // ---- 低電量 ----

    @Test
    fun batteryWarnsOnceWhenLow() {
        var warned = false
        val first = BatteryRule.decide(15, false, warned)
        assertTrue(first.warnNow)
        warned = first.warned
        assertTrue(warned)
        assertFalse(BatteryRule.decide(14, false, warned).warnNow) // 已經提醒過，不重複
        assertFalse(BatteryRule.decide(10, false, warned).warnNow)
    }

    @Test
    fun batteryDoesNotWarnWhenHighOrCharging() {
        assertFalse(BatteryRule.decide(16, false, false).warnNow)
        assertFalse(BatteryRule.decide(5, true, false).warnNow)  // 充電中
        assertFalse(BatteryRule.decide(-1, false, false).warnNow) // 讀不到
    }

    @Test
    fun batteryRearmsAfterRecoveryOrCharging() {
        assertFalse(BatteryRule.decide(18, false, true).warnNow)
        assertTrue(BatteryRule.decide(18, false, true).warned)    // 還沒回到 20% 以上：維持已提醒，避免在邊緣反覆震動
        assertFalse(BatteryRule.decide(21, false, true).warned)   // 回到 20% 以上：重新允許
        assertFalse(BatteryRule.decide(8, true, true).warned)     // 開始充電：重新允許
        assertTrue(BatteryRule.decide(12, false, BatteryRule.decide(21, false, true).warned).warnNow)
    }

    // ---- 今日目標 ----

    @Test
    fun goalReachedOnlyWhenCrossing() {
        assertTrue(Goal.reached(119, 120, 120))
        assertFalse(Goal.reached(120, 121, 120)) // 已經在目標以上
        assertFalse(Goal.reached(50, 51, 120))
        assertFalse(Goal.reached(119, 120, 0))   // 目標關閉
        assertTrue(Goal.reached(118, 121, 120))  // 一次跨過（例如手動加）
    }

    @Test
    fun todayDoneCountsOnlyTodayRealSessions() {
        val today1 = session(listOf(shot(10, 0.5f), shot(20, 0.5f), shot(30, 0.5f)), id = "a", start = noon - 2 * hour)
        val today2 = session(listOf(shot(10, 0.5f), shot(20, 0.5f, cancelled = true)), id = "b", start = noon - hour)
        val yesterday = session(listOf(shot(10, 0.5f)), id = "c", start = noon - 24 * hour)
        val cal = session(listOf(shot(10, 0.5f), shot(20, 0.5f)), id = "d", start = noon - hour, calibration = true)
        val active = session(listOf(shot(10, 0.5f)), id = "e", start = noon)
        val manual = session(emptyList(), id = "f", start = noon - hour, manual = listOf(ManualEntry(1000, 1), ManualEntry(2000, 1)))
        val all = listOf(today1, today2, yesterday, cal, active, manual)
        // a:3 + b:1(誤判不算) + f:2(手動) = 6；昨天、校準、進行中的不算
        assertEquals(6, todayDone(all, noon, excludeId = "e", zone = zone))
        assertEquals(7, todayDone(all, noon, excludeId = null, zone = zone)) // 沒排除進行中的就多 1
    }

    @Test
    fun todayDoneUsesLocalMidnightBoundary() {
        val justBeforeMidnight = java.time.ZonedDateTime.of(2026, 10, 19, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
        val s = session(listOf(shot(10, 0.5f)), id = "x", start = justBeforeMidnight)
        assertEquals(0, todayDone(listOf(s), noon, null, zone))
        assertEquals(1, todayDone(listOf(s), justBeforeMidnight + 30_000L, null, zone))
    }

    // ---- 每趟回饋 ----

    private fun twoEndSession(first: Float, second: Float): Pair<Session, List<EndStats>> {
        // 每趟 2 箭，兩趟之間隔很久
        val s = session(listOf(shot(10, first), shot(20, first), shot(400, second), shot(410, second)))
        return s to computeEnds(s, 2)
    }

    @Test
    fun feedbackComparesWithPreviousEndAndSessionAverage() {
        val (s, ends) = twoEndSession(first = 0.5f, second = 0.4f)
        val fb = buildEndFeedback(s, ends)!!
        assertEquals(2, fb.endNumber)
        assertEquals(0.4f, fb.accelAvg, 1e-5f)
        assertEquals(-20f, fb.vsPrevPct!!, 0.01f)           // (0.4-0.5)/0.5
        assertEquals(-11.11f, fb.vsSessionPct!!, 0.05f)     // 本次平均 0.45
    }

    @Test
    fun feedbackForFirstEndHasNoComparison() {
        val s = session(listOf(shot(10, 0.5f), shot(20, 0.5f)))
        val fb = buildEndFeedback(s, computeEnds(s, 2))!!
        assertEquals(1, fb.endNumber)
        assertNull(fb.vsPrevPct)
        assertNull(fb.vsSessionPct)
    }

    @Test
    fun feedbackNullWithoutShakeData() {
        val s = session(listOf(shot(10, null), shot(20, null)))
        assertNull(buildEndFeedback(s, computeEnds(s, 2)))
        assertNull(buildEndFeedback(s, emptyList()))
    }

    @Test
    fun feedbackSkipsPercentWhenBaselineIsZero() {
        val (s, ends) = twoEndSession(first = 0f, second = 0.4f)
        val fb = buildEndFeedback(s, ends)!!
        assertNull(fb.vsPrevPct)
    }

    @Test
    fun describeChangeWords() {
        assertEquals("—", describeChange(null))
        assertEquals("差不多", describeChange(2.9f))
        assertEquals("差不多", describeChange(-2.9f))
        assertEquals("較穩 12%", describeChange(-12.4f))
        assertEquals("較抖 5%", describeChange(5.2f))
    }

    // ---- 亮度 ----

    @Test
    fun brightnessLevelsAreOrderedAndClamped() {
        assertTrue(Brightness.level(0) < Brightness.level(1))
        assertTrue(Brightness.level(1) < Brightness.level(2))
        assertEquals(Brightness.level(0), Brightness.level(-5), 1e-6f)
        assertEquals(Brightness.level(2), Brightness.level(99), 1e-6f)
        assertEquals(Brightness.LEVELS.size, Brightness.LABELS.size)
        var i = 0
        repeat(Brightness.LEVELS.size) { i = Brightness.next(i) }
        assertEquals(0, i) // 轉一圈回到起點
    }

    // ---- 記錄器：目前箭數與快照 ----

    @Test
    fun recorderCurrentCountAndSnapshot() {
        val store = SessionStore(Files.createTempDirectory("archery-v16").toFile())
        val rec = SessionRecorder(store)
        assertEquals(0, rec.currentFinalCount())
        assertNull(rec.snapshot())

        rec.start(noon, 16f, 2, false, false, tags = listOf("反曲"))
        rec.addShot(1000, 17f, 16f, 0.5f, null)
        rec.addShot(5000, 17f, 16f, 0.6f, null)
        rec.addManual(6000, 1)
        assertEquals(3, rec.currentFinalCount())

        rec.cancelLastShot()
        assertEquals(2, rec.currentFinalCount())

        val snap = rec.snapshot()!!
        assertEquals(2, snap.shots.size)
        assertEquals(listOf("反曲"), snap.tags)
        assertNotNull(snap.id)

        rec.stop(noon + 10_000)
        assertEquals(0, rec.currentFinalCount())
        assertNull(rec.snapshot())
    }
}
