package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V191Test {

    private fun round(prepare: Int) =
        CompetitionRound(CompFormat(CompFormat.CUSTOM, 3, 2, 120), 0, prepare, true, true, 0)

    @Test
    fun countTicksFireOncePerSecondInLastThree() {
        val r = round(10)
        r.start(0)
        assertTrue(r.tick(5_000).isEmpty())
        assertTrue(r.tick(6_999).isEmpty())
        assertEquals(listOf(CompEvent.COUNT_TICK), r.tick(7_000))   // 剩 3 秒
        assertTrue(r.tick(7_200).isEmpty())                          // 同一秒不重複
        assertTrue(r.tick(7_990).isEmpty())
        assertEquals(listOf(CompEvent.COUNT_TICK), r.tick(8_000))   // 剩 2 秒
        assertEquals(listOf(CompEvent.COUNT_TICK), r.tick(9_000))   // 剩 1 秒
        assertEquals(listOf(CompEvent.GO), r.tick(10_000))
    }

    @Test
    fun shortPrepareOnlyTicksWhatExists() {
        val r = round(2)
        r.start(0)
        assertEquals(listOf(CompEvent.COUNT_TICK), r.tick(10))      // 剩 2 秒
        assertEquals(listOf(CompEvent.COUNT_TICK), r.tick(1_000))   // 剩 1 秒
        assertEquals(listOf(CompEvent.GO), r.tick(2_000))
    }

    @Test
    fun countTicksResetForNextEnd() {
        val r = round(4)
        r.start(0)
        r.tick(1_000); r.tick(2_000); r.tick(3_000)
        assertEquals(listOf(CompEvent.GO), r.tick(4_000))
        repeat(3) { r.onShot(5_000L + it) }
        assertEquals(CompPhase.REST, r.phase)
        r.next(20_000)
        assertEquals(CompPhase.PREPARE, r.phase)
        assertEquals(listOf(CompEvent.COUNT_TICK), r.tick(21_000)) // 新一趟重新計
    }

    @Test
    fun noTicksWhenPrepareIsOff() {
        val r = round(0)
        assertEquals(listOf(CompEvent.GO), r.start(0))
        assertTrue(r.tick(500).isEmpty())
    }

    @Test
    fun soundPlansHaveExpectedBeepCounts() {
        assertEquals(1, MatchSound.plan(SoundCue.COUNT).size)
        assertEquals(1, MatchSound.plan(SoundCue.GO).size)
        assertEquals(1, MatchSound.plan(SoundCue.WARN30).size)
        assertEquals(2, MatchSound.plan(SoundCue.WARN10).size)
        assertEquals(3, MatchSound.plan(SoundCue.TIME_UP).size)
        assertEquals(1, MatchSound.plan(SoundCue.END_FULL).size)
        // GO 是長音
        assertTrue(MatchSound.plan(SoundCue.GO).first().durationMs > MatchSound.plan(SoundCue.COUNT).first().durationMs)
        // 最後一聲後不用再等
        SoundCue.values().forEach { assertEquals(0, MatchSound.plan(it).last().gapMs) }
    }

    @Test
    fun cueSwitches() {
        assertTrue(MatchSound.cueEnabled(SoundCue.COUNT, true, false, false, false))
        assertFalse(MatchSound.cueEnabled(SoundCue.COUNT, false, true, true, true))
        assertTrue(MatchSound.cueEnabled(SoundCue.GO, false, true, false, false))
        assertTrue(MatchSound.cueEnabled(SoundCue.WARN30, false, false, true, false))
        assertTrue(MatchSound.cueEnabled(SoundCue.WARN10, false, false, true, false))
        assertFalse(MatchSound.cueEnabled(SoundCue.WARN10, true, true, false, true))
        assertTrue(MatchSound.cueEnabled(SoundCue.TIME_UP, false, false, false, true))
        assertTrue(MatchSound.cueEnabled(SoundCue.END_FULL, false, false, false, true))
        assertFalse(MatchSound.cueEnabled(SoundCue.END_FULL, true, true, true, false))
    }

    @Test
    fun volumeAndStyleCycle() {
        assertEquals(25, MatchSound.volume(0))
        assertEquals(100, MatchSound.volume(3))
        assertEquals(100, MatchSound.volume(99))   // 超出範圍夾住
        assertEquals(25, MatchSound.volume(-5))
        assertEquals(0, MatchSound.nextVolume(3))
        assertEquals(75, MatchSound.volume(MatchSound.DEFAULT_VOLUME_INDEX))
        assertEquals(1, MatchSound.nextStyle(0))
        assertEquals(0, MatchSound.nextStyle(MatchSound.STYLE_COUNT - 1))
    }

    @Test
    fun paletteMatchesRequest() {
        val prep = MatchPalette.forPhase(CompPhase.PREPARE)!!
        assertEquals(0xFFFFFFFF, prep.text)       // 白字
        assertTrue(((prep.background shr 16) and 0xFF) > 0xA0)  // 紅底：紅色分量高
        assertTrue(((prep.background shr 8) and 0xFF) < 0x50)   // 綠色分量低
        val shoot = MatchPalette.forPhase(CompPhase.SHOOTING)!!
        assertEquals(0xFF000000, shoot.text)      // 黑字
        assertTrue(((shoot.background shr 8) and 0xFF) > 0x90)  // 綠底：綠色分量高
        assertTrue(((shoot.background shr 16) and 0xFF) < 0x90)
        assertNull(MatchPalette.forPhase(CompPhase.REST))
        assertNull(MatchPalette.forPhase(CompPhase.DONE))
        assertNotNull(prep)
    }
}
