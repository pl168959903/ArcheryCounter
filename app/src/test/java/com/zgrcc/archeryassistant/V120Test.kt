package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class V120Test {
    @Test
    fun autoModeFollowsDeviceType() {
        assertTrue(PhoneMode.resolve(PhoneMode.AUTO, isWatch = false))
        assertTrue(!PhoneMode.resolve(PhoneMode.AUTO, isWatch = true))
    }

    @Test
    fun forcedModesIgnoreDeviceType() {
        assertTrue(PhoneMode.resolve(PhoneMode.ON, isWatch = true))
        assertTrue(!PhoneMode.resolve(PhoneMode.OFF, isWatch = false))
    }

    @Test
    fun nextCyclesAndInvalidFallsBackToAuto() {
        assertEquals(PhoneMode.ON, PhoneMode.next(PhoneMode.AUTO))
        assertEquals(PhoneMode.OFF, PhoneMode.next(PhoneMode.ON))
        assertEquals(PhoneMode.AUTO, PhoneMode.next(PhoneMode.OFF))
        assertEquals(PhoneMode.AUTO, PhoneMode.valid(99))
        assertEquals(PhoneMode.AUTO, PhoneMode.valid(-1))
    }

    @Test
    fun settingDefaultsToAutoAndResets() {
        val s = AppSettings(FakePrefs())
        assertEquals(PhoneMode.AUTO, s.phoneMode.value)
        s.setPhoneMode(PhoneMode.ON)
        assertEquals(PhoneMode.ON, s.phoneMode.value)
        s.setPhoneMode(42)
        assertEquals(PhoneMode.AUTO, s.phoneMode.value)
        s.setPhoneMode(PhoneMode.OFF)
        s.resetDefaults()
        assertEquals(PhoneMode.AUTO, s.phoneMode.value)
    }
}
