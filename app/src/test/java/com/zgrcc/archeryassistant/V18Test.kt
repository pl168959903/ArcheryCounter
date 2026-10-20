package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V18Test {

    @Test
    fun iconPopsFromEightyToHundredPercent() {
        assertEquals(0.8f, SplashTiming.iconScale(0), 0.0001f)
        assertEquals(1f, SplashTiming.iconScale(SplashTiming.POP_MS), 0.0001f)
        assertEquals(1f, SplashTiming.iconScale(SplashTiming.POP_MS + 5000), 0.0001f)
        // 先快後慢：一半時間時已經放大超過一半
        assertTrue(SplashTiming.iconScale(SplashTiming.POP_MS / 2) > 0.9f)
        var prev = 0f
        for (t in 0L..SplashTiming.POP_MS step 25) {
            val s = SplashTiming.iconScale(t)
            assertTrue(s >= prev)
            prev = s
        }
    }

    @Test
    fun iconFadesInQuickly() {
        assertEquals(0f, SplashTiming.iconAlpha(0), 0.0001f)
        assertEquals(1f, SplashTiming.iconAlpha(SplashTiming.ICON_FADE_MS), 0.0001f)
        assertTrue(SplashTiming.iconAlpha(SplashTiming.ICON_FADE_MS / 2) in 0.4f..0.6f)
    }

    @Test
    fun textLinesFadeInOneAfterAnother() {
        for (i in 0 until SplashTiming.TEXT_LINES) {
            val start = SplashTiming.TEXT_START_MS + i * SplashTiming.TEXT_STEP_MS
            assertEquals(0f, SplashTiming.lineAlpha(start, i), 0.0001f)
            assertEquals(1f, SplashTiming.lineAlpha(start + SplashTiming.TEXT_FADE_MS, i), 0.0001f)
        }
        // 第一行已經完全顯示時，最後一行還沒開始
        val t = SplashTiming.TEXT_START_MS + SplashTiming.TEXT_FADE_MS
        assertEquals(1f, SplashTiming.lineAlpha(t, 0), 0.0001f)
        assertTrue(SplashTiming.lineAlpha(t, SplashTiming.TEXT_LINES - 1) < 1f)
        assertEquals(1f, SplashTiming.lineAlpha(SplashTiming.allTextShownMs(), SplashTiming.TEXT_LINES - 1), 0.0001f)
    }

    @Test
    fun allTextIsShownBeforeAnimationEnds() {
        assertTrue(SplashTiming.allTextShownMs() < SplashTiming.AUTO_TOTAL_MS - SplashTiming.FADE_OUT_MS)
        assertTrue(SplashTiming.HINT_START_MS >= SplashTiming.allTextShownMs() - SplashTiming.TEXT_FADE_MS)
    }

    @Test
    fun glowOnlyAfterIconHasPoppedAndStaysInRange() {
        assertEquals(0f, SplashTiming.glowAlpha(0), 0.0001f)
        assertEquals(0f, SplashTiming.glowAlpha(SplashTiming.POP_MS - 1), 0.0001f)
        for (t in SplashTiming.POP_MS..SplashTiming.POP_MS + 5000 step 37) {
            val g = SplashTiming.glowAlpha(t)
            assertTrue("t=$t g=$g", g in 0.099f..0.501f)
        }
    }

    @Test
    fun hintAppearsLateAndPulsesWithoutVanishing() {
        assertEquals(0f, SplashTiming.hintAlpha(0), 0.0001f)
        assertEquals(0f, SplashTiming.hintAlpha(SplashTiming.HINT_START_MS), 0.0001f)
        // 淡入完成後一直在 0.4～1 之間閃動，不會完全消失
        for (t in SplashTiming.HINT_START_MS + SplashTiming.HINT_FADE_MS..SplashTiming.HINT_START_MS + 10_000 step 41) {
            val a = SplashTiming.hintAlpha(t)
            assertTrue("t=$t a=$a", a in 0.399f..1.001f)
        }
    }

    @Test
    fun autoModeFadesOutAtTheEndAndWaitModeNever() {
        assertEquals(1f, SplashTiming.overallAlpha(0, waitForTap = false), 0.0001f)
        assertEquals(1f, SplashTiming.overallAlpha(SplashTiming.AUTO_TOTAL_MS - SplashTiming.FADE_OUT_MS, false), 0.0001f)
        assertEquals(0f, SplashTiming.overallAlpha(SplashTiming.AUTO_TOTAL_MS, false), 0.0001f)
        assertEquals(0.5f, SplashTiming.overallAlpha(SplashTiming.AUTO_TOTAL_MS - SplashTiming.FADE_OUT_MS / 2, false), 0.0001f)
        // 等點按時不淡出
        assertEquals(1f, SplashTiming.overallAlpha(SplashTiming.AUTO_TOTAL_MS, waitForTap = true), 0.0001f)
        assertEquals(1f, SplashTiming.overallAlpha(SplashTiming.WAIT_TIMEOUT_MS, waitForTap = true), 0.0001f)
    }

    @Test
    fun finishRules() {
        // 不等點按：約 3 秒結束
        assertFalse(SplashTiming.isFinished(SplashTiming.AUTO_TOTAL_MS - 1, waitForTap = false))
        assertTrue(SplashTiming.isFinished(SplashTiming.AUTO_TOTAL_MS, waitForTap = false))
        // 等點按：3 秒時還在，不會自己走；超過保險時間才自己進入
        assertFalse(SplashTiming.isFinished(SplashTiming.AUTO_TOTAL_MS, waitForTap = true))
        assertFalse(SplashTiming.isFinished(SplashTiming.WAIT_TIMEOUT_MS - 1, waitForTap = true))
        assertTrue(SplashTiming.isFinished(SplashTiming.WAIT_TIMEOUT_MS, waitForTap = true))
    }

    @Test
    fun autoDurationIsAboutThreeSeconds() {
        assertEquals(3000L, SplashTiming.AUTO_TOTAL_MS)
        assertTrue(SplashTiming.WAIT_TIMEOUT_MS >= 30_000L)
    }
}
