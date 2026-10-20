package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V110Test {

    private val custom = CompFormat(CompFormat.CUSTOM, 5, 7, 99)

    // ---------- 我的賽制 ----------
    @Test
    fun savedFormatsRoundTrip() {
        val list = listOf(CompFormat(2, 3, 10, 90), CompFormat(2, 6, 6, 200))
        assertEquals("3x10x90;6x6x200", SavedFormats.encode(list))
        val back = SavedFormats.decode("3x10x90;6x6x200")
        assertEquals(listOf(3, 6), back.map { it.arrowsPerEnd })
        assertEquals(listOf(10, 6), back.map { it.ends })
        assertEquals(listOf(90, 200), back.map { it.secondsPerEnd })
    }

    @Test
    fun decodeSkipsGarbageAndClamps() {
        assertTrue(SavedFormats.decode("").isEmpty())
        assertTrue(SavedFormats.decode("abc;1x2").isEmpty())
        val f = SavedFormats.decode("99x999x1").single()
        assertEquals(CompLimits.MAX_ARROWS, f.arrowsPerEnd)
        assertEquals(CompLimits.MAX_ENDS, f.ends)
        assertEquals(CompLimits.MIN_SECONDS, f.secondsPerEnd)
        // 最多 5 組
        assertEquals(SavedFormats.MAX, SavedFormats.decode("3x1x60;3x2x60;3x3x60;3x4x60;3x5x60;3x6x60;3x7x60").size)
    }

    @Test
    fun addRejectsDuplicatesAndOverflow() {
        var list = emptyList<CompFormat>()
        list = SavedFormats.add(list, CompFormat(2, 3, 10, 90))
        assertEquals(1, list.size)
        assertFalse(SavedFormats.canAdd(list, CompFormat(2, 3, 10, 90)))
        assertEquals(1, SavedFormats.add(list, CompFormat(2, 3, 10, 90)).size)
        for (i in 1..10) list = SavedFormats.add(list, CompFormat(2, 3, 10 + i, 90))
        assertEquals(SavedFormats.MAX, list.size)
        assertFalse(SavedFormats.canAdd(list, CompFormat(2, 1, 1, 60)))
    }

    @Test
    fun removeByIndex() {
        val list = listOf(CompFormat(2, 3, 10, 90), CompFormat(2, 6, 6, 200), CompFormat(2, 4, 8, 100))
        assertEquals(listOf(3, 4), SavedFormats.remove(list, 1).map { it.arrowsPerEnd })
        assertEquals(3, SavedFormats.remove(list, 9).size)
        assertEquals(3, SavedFormats.remove(list, -1).size)
    }

    @Test
    fun resolveByIdIncludingSaved() {
        val saved = listOf(CompFormat(2, 4, 8, 100), CompFormat(2, 2, 3, 60))
        assertEquals(3, CompFormat.resolve(CompFormat.INDOOR_18, custom, saved).arrowsPerEnd)
        assertEquals(6, CompFormat.resolve(CompFormat.OUTDOOR_70, custom, saved).arrowsPerEnd)
        assertEquals(5, CompFormat.resolve(CompFormat.CUSTOM, custom, saved).arrowsPerEnd)
        val s1 = CompFormat.resolve(CompFormat.SAVED_BASE + 1, custom, saved)
        assertEquals(2, s1.arrowsPerEnd)
        assertEquals(CompFormat.SAVED_BASE + 1, s1.id)
        // 該組已被刪掉：退回自訂
        assertEquals(5, CompFormat.resolve(CompFormat.SAVED_BASE + 4, custom, saved).arrowsPerEnd)
    }

    @Test
    fun formatCycleIncludesSaved() {
        assertEquals(1, CompFormat.nextId(0, 0))
        assertEquals(2, CompFormat.nextId(1, 0))
        assertEquals(0, CompFormat.nextId(2, 0))          // 沒有存的：自訂之後回到第一個
        assertEquals(3, CompFormat.nextId(2, 2))
        assertEquals(4, CompFormat.nextId(3, 2))
        assertEquals(0, CompFormat.nextId(4, 2))          // 最後一組之後回到第一個
        assertEquals(CompFormat.SAVED_BASE + SavedFormats.MAX - 1, CompFormat.MAX_ID)
    }

    @Test
    fun savedFormatLabel() {
        I18n.english = false
        val saved = listOf(CompFormat(2, 4, 8, 100))
        assertTrue(CompFormat.labelOf(CompFormat.SAVED_BASE, saved).startsWith("我的 1"))
        assertEquals("自訂", CompFormat.labelOf(CompFormat.SAVED_BASE + 3, saved))
        assertEquals("室內18米", CompFormat.labelOf(CompFormat.INDOOR_18, saved))
    }

    // ---------- 單箭限時 ----------
    private fun round(limit: Int) =
        CompetitionRound(CompFormat(CompFormat.CUSTOM, 3, 2, 120), 0, 0, false, false, 0, limit)

    @Test
    fun arrowTimerOffByDefault() {
        val r = round(0)
        r.start(0)
        assertTrue(r.tick(100_000).isEmpty())
        assertEquals(0L, r.arrowRemainingMs(1000))
    }

    @Test
    fun arrowTimeUpFiresOnceThenRestartsAfterShot() {
        val r = round(40)
        r.start(0)
        assertEquals(40_000L, r.arrowRemainingMs(0))
        assertTrue(r.tick(39_000).isEmpty())
        assertEquals(listOf(CompEvent.ARROW_TIME_UP), r.tick(40_000))
        assertTrue(r.tick(45_000).isEmpty())            // 只提醒一次
        assertEquals(0L, r.arrowRemainingMs(45_000))
        r.onShot(50_000)                                  // 射出一箭：重新計時
        assertEquals(40_000L, r.arrowRemainingMs(50_000))
        assertTrue(r.tick(89_000).isEmpty())
        assertEquals(listOf(CompEvent.ARROW_TIME_UP), r.tick(90_000))
    }

    @Test
    fun arrowTimerDoesNotEndTheEnd() {
        val r = round(10)
        r.start(0)
        r.tick(10_000)
        assertEquals(CompPhase.SHOOTING, r.phase)
        assertEquals(0, r.arrowsInEnd)
    }

    @Test
    fun arrowTimerRestartsEachEnd() {
        val r = round(30)
        r.start(0)
        repeat(3) { r.onShot(1_000L * (it + 1)) }
        assertEquals(CompPhase.REST, r.phase)
        assertEquals(0L, r.arrowRemainingMs(5_000))     // 休息中不計
        r.next(10_000)
        assertEquals(30_000L, r.arrowRemainingMs(10_000))
    }

    @Test
    fun uiCarriesArrowTimer() {
        val r = round(40)
        r.start(0)
        val u = r.ui(10_000)
        assertEquals(40, u.arrowLimitSec)
        assertEquals(30_000L, u.arrowRemainingMs)
    }

    @Test
    fun arrowSoundUsesWarnSwitch() {
        assertEquals(2, MatchSound.plan(SoundCue.ARROW_UP).size)
        assertTrue(MatchSound.cueEnabled(SoundCue.ARROW_UP, false, false, true, false))
        assertFalse(MatchSound.cueEnabled(SoundCue.ARROW_UP, true, true, false, true))
    }

    // ---------- 環數輸入 ----------
    @Test
    fun scoreInputRules() {
        assertEquals(10, ScoreInput.plus(null))
        assertEquals(10, ScoreInput.plus(10))
        assertEquals(9, ScoreInput.plus(8))
        assertNull(ScoreInput.minus(null))
        assertEquals(9, ScoreInput.minus(10))
        assertEquals(0, ScoreInput.minus(1))
        assertNull(ScoreInput.minus(0))
    }

    @Test
    fun recorderScoresAndIndices() {
        val dir = java.io.File.createTempFile("v110", "").apply { delete(); mkdirs() }
        val rec = SessionRecorder(SessionStore(dir))
        rec.start(1_000_000L, 16f, 2, false, false)
        rec.addShot(1000, 20f, 16f, 1f, null)
        rec.addShot(2000, 20f, 16f, 1f, null)
        rec.addShot(9000, 20f, 16f, 1f, null)
        assertEquals(listOf(0, 1), rec.validShotIndices(0, 5000))
        assertEquals(listOf(2), rec.validShotIndices(5000, 10_000))
        rec.setScore(1, 9)
        assertEquals(9, rec.scoreAt(1))
        assertNull(rec.scoreAt(0))
        rec.setScore(1, null)
        assertNull(rec.scoreAt(1))
        rec.setScore(7, 5)                                 // 不存在的位置：什麼都不做
        rec.setScore(0, 99)
        assertEquals(10, rec.scoreAt(0))                   // 夾在 0～10
        rec.cancelLastShot()                               // 最後一筆被取消後不算有效
        assertEquals(emptyList<Int>(), rec.validShotIndices(5000, 10_000))
        rec.stop(2_000_000L)
        dir.deleteRecursively()
    }
}
