package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRulesTest {
    private fun shot(rel: Long = 1000, accel: Float? = 1f, cancelled: Boolean = false, feel: Int? = null) =
        ShotEntry(rel, 20f, 16f, accel, null, cancelled = cancelled, feel = feel)

    private fun sess(
        id: String = "s", shots: List<ShotEntry> = listOf(shot()), cal: Boolean = false, comp: CompMeta? = null,
        end: Long? = 5000, raw: Boolean = false, tags: List<String> = emptyList(),
    ) = Session(id, 0, end, 16f, 2, false, raw, shots, emptyList(), tags, cal, emptyList(), comp)

    private fun entries(
        sessions: List<Session>, manage: Boolean = false,
        trend: Boolean = true, match: Boolean = true, groups: Boolean = true, overview: Boolean = true,
    ) = ScreenRules.historyEntries(sessions, manage, trend, match, groups, overview)

    @Test
    fun noSessionsShowsNothing() {
        val e = entries(emptyList())
        assertFalse(e.trend)
        assertFalse(e.matchCompare)
        assertFalse(e.groups)
        assertFalse(e.overview)
    }

    @Test
    fun trendNeedsTwoSessions() {
        assertFalse(entries(listOf(sess("a"))).trend)
        assertTrue(entries(listOf(sess("a"), sess("b"))).trend)
    }

    @Test
    fun matchCompareNeedsFinishedMatch() {
        val comp = CompMeta(120, 3, 0)
        assertFalse(entries(listOf(sess("a"))).matchCompare)
        assertFalse(entries(listOf(sess("a", comp = comp, end = null))).matchCompare)
        assertFalse(entries(listOf(sess("a", comp = comp, cal = true))).matchCompare)
        assertTrue(entries(listOf(sess("a", comp = comp))).matchCompare)
    }

    @Test
    fun overviewIgnoresCalibrationOnly() {
        assertFalse(entries(listOf(sess("a", cal = true))).overview)
        assertTrue(entries(listOf(sess("a", cal = true), sess("b"))).overview)
    }

    @Test
    fun manageModeHidesEverything() {
        val list = listOf(sess("a", comp = CompMeta(120, 3, 0)), sess("b"))
        val e = entries(list, manage = true)
        assertFalse(e.trend)
        assertFalse(e.matchCompare)
        assertFalse(e.groups)
        assertFalse(e.overview)
    }

    @Test
    fun settingsTurnEntriesOff() {
        val list = listOf(sess("a", comp = CompMeta(120, 3, 0)), sess("b"))
        val on = entries(list)
        assertTrue(on.trend && on.matchCompare && on.overview)
        val off = entries(list, trend = false, match = false, groups = false, overview = false)
        assertFalse(off.trend || off.matchCompare || off.groups || off.overview)
    }

    @Test
    fun feelRowRules() {
        assertFalse(ScreenRules.showFeelRow(false, shot()))
        assertTrue(ScreenRules.showFeelRow(true, shot()))
        // 設定關閉了，但這一箭已有自評：仍顯示，才能看到、修改
        assertTrue(ScreenRules.showFeelRow(false, shot(feel = Feel.GOOD)))
        // 誤判的箭不評
        assertFalse(ScreenRules.showFeelRow(true, shot(cancelled = true)))
        assertFalse(ScreenRules.showFeelRow(false, shot(cancelled = true, feel = Feel.OK)))
    }

    @Test
    fun feelRowInQuickCheckOnlyWhenSettingOn() {
        assertFalse(ScreenRules.showFeelRowInQuickCheck(false, shot(feel = Feel.GOOD)))
        assertTrue(ScreenRules.showFeelRowInQuickCheck(true, shot()))
        assertFalse(ScreenRules.showFeelRowInQuickCheck(true, shot(cancelled = true)))
    }

    @Test
    fun calibrationAdviceRules() {
        val ok = sess("c", cal = true, raw = true)
        assertTrue(ScreenRules.showCalibrationAdvice(true, ok, isActive = false))
        assertFalse(ScreenRules.showCalibrationAdvice(false, ok, isActive = false))
        assertFalse(ScreenRules.showCalibrationAdvice(true, ok, isActive = true))
        assertFalse(ScreenRules.showCalibrationAdvice(true, sess("c", cal = true, raw = false), isActive = false))
        assertFalse(ScreenRules.showCalibrationAdvice(true, sess("c", cal = false, raw = true), isActive = false))
        assertFalse(ScreenRules.showCalibrationAdvice(true, sess("c", cal = true, raw = true, end = null), isActive = false))
        assertEquals(true, ScreenRules.showCalibrationAdvice(true, ok, false))
    }
}
