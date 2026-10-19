package com.example.archerycounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V17Test {

    @Test
    fun fingerprintOfEmptyInputMatchesKnownSha256() {
        // SHA-256("") = e3b0c442 98fc1c14 ...
        assertEquals("E3:B0:C4:42", AppInfo.fingerprintShort(ByteArray(0)))
    }

    @Test
    fun fingerprintOfAbcMatchesKnownSha256() {
        // SHA-256("abc") = ba7816bf 8f01cfea ...
        assertEquals("BA:78:16:BF", AppInfo.fingerprintShort("abc".toByteArray()))
        assertEquals("BA:78:16:BF:8F:01", AppInfo.fingerprintShort("abc".toByteArray(), groups = 6))
    }

    @Test
    fun fingerprintGroupsAreClamped() {
        assertEquals("BA", AppInfo.fingerprintShort("abc".toByteArray(), groups = 0))
        // 超過 32 組時只給完整的 32 組
        assertEquals(32, AppInfo.fingerprintShort("abc".toByteArray(), groups = 99).split(":").size)
    }

    @Test
    fun versionLabelShowsNameAndCode() {
        assertEquals("v1.7（8）", AppInfo.versionLabel("1.7", 8))
        assertEquals("v1.7", AppInfo.versionLabel("1.7", -1))
    }

    @Test
    fun fixedKeyComparisonIgnoresCase() {
        assertTrue(AppInfo.isFixedKey("C7:08:6E:C9"))
        assertTrue(AppInfo.isFixedKey("c7:08:6e:c9"))
        assertFalse(AppInfo.isFixedKey("AA:BB:CC:DD"))
        assertFalse(AppInfo.isFixedKey("?"))
    }

    @Test
    fun signatureStatusTexts() {
        assertTrue(AppInfo.signatureStatus("C7:08:6E:C9").contains("相符"))
        assertTrue(AppInfo.signatureStatus("AA:BB:CC:DD").contains("不是固定金鑰"))
        assertTrue(AppInfo.signatureStatus("?").contains("讀不到"))
    }

    @Test
    fun authorNameKeepsExactCase() {
        assertEquals("ZGrCc", AppInfo.AUTHOR)
    }

    @Test
    fun splashFlyProgressStartsAtZeroAndEndsAtOne() {
        assertEquals(0f, SplashTiming.flyProgress(0), 0.0001f)
        assertEquals(1f, SplashTiming.flyProgress(SplashTiming.FLY_MS), 0.0001f)
        assertEquals(1f, SplashTiming.flyProgress(SplashTiming.TOTAL_MS), 0.0001f)
        // 先快後慢：一半時間時已經飛過一半以上
        assertTrue(SplashTiming.flyProgress(SplashTiming.FLY_MS / 2) > 0.5f)
        // 單調不減
        var prev = -1f
        for (t in 0..SplashTiming.TOTAL_MS step 50) {
            val p = SplashTiming.flyProgress(t)
            assertTrue(p >= prev)
            prev = p
        }
    }

    @Test
    fun splashHitOnlyAfterArrowLands() {
        assertEquals(0f, SplashTiming.hitProgress(0), 0.0001f)
        assertEquals(0f, SplashTiming.hitProgress(SplashTiming.FLY_MS), 0.0001f)
        assertEquals(1f, SplashTiming.hitProgress(SplashTiming.FLY_MS + 400), 0.0001f)
    }

    @Test
    fun splashTextFadesInAndWholeScreenFadesOut() {
        assertEquals(0f, SplashTiming.textAlpha(0), 0.0001f)
        assertEquals(1f, SplashTiming.textAlpha(SplashTiming.FLY_MS / 2 + 400), 0.0001f)
        assertEquals(1f, SplashTiming.overallAlpha(0), 0.0001f)
        assertEquals(1f, SplashTiming.overallAlpha(SplashTiming.TOTAL_MS - SplashTiming.FADE_OUT_MS), 0.0001f)
        assertEquals(0f, SplashTiming.overallAlpha(SplashTiming.TOTAL_MS), 0.0001f)
        // 淡出一半
        assertEquals(0.5f, SplashTiming.overallAlpha(SplashTiming.TOTAL_MS - SplashTiming.FADE_OUT_MS / 2), 0.0001f)
    }

    @Test
    fun splashFitsInAboutOneAndHalfSeconds() {
        assertTrue(SplashTiming.TOTAL_MS in 1000L..2000L)
        assertTrue(SplashTiming.FLY_MS < SplashTiming.TOTAL_MS - SplashTiming.FADE_OUT_MS)
    }
}
