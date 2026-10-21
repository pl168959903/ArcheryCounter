package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class V1202Test {
    @Test
    fun tapLeftHalfIsPreviousRightHalfIsNext() {
        assertEquals(-1, ShotNav.tapDir(10f, 200f))
        assertEquals(-1, ShotNav.tapDir(99.9f, 200f))
        assertEquals(1, ShotNav.tapDir(100f, 200f))
        assertEquals(1, ShotNav.tapDir(190f, 200f))
        assertEquals(1, ShotNav.tapDir(10f, 0f)) // 寬度無效當作右半邊
    }

    @Test
    fun tapNavSettingDefaultsOnAndResets() {
        val s = AppSettings(FakePrefs())
        assertTrue(s.shotTapNav.value)
        s.setShotTapNav(false)
        assertTrue(!s.shotTapNav.value)
        s.resetDefaults()
        assertTrue(s.shotTapNav.value)
    }
}
