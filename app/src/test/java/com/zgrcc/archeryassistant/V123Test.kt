package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V123Test {
    @Test
    fun phoneHidesDetectionTrainingAndMatchCategories() {
        val hidden = (0 until SettingsCategories.COUNT).filter { SettingsCategories.hiddenOnPhone(it) }
        assertEquals(listOf(SettingsCategories.DETECTION, SettingsCategories.TRAINING, SettingsCategories.MATCH), hidden)
        // 匯入、顯示、備份、關於在手機上都要看得到
        assertFalse(SettingsCategories.hiddenOnPhone(SettingsCategories.CONNECT))
        assertFalse(SettingsCategories.hiddenOnPhone(SettingsCategories.CHARTS))
        assertFalse(SettingsCategories.hiddenOnPhone(SettingsCategories.RESET))
        assertFalse(SettingsCategories.hiddenOnPhone(SettingsCategories.ABOUT))
    }

    @Test
    fun phoneResolveStillFollowsWatchFeature() {
        assertTrue(PhoneMode.resolve(PhoneMode.AUTO, isWatch = false))
        assertFalse(PhoneMode.resolve(PhoneMode.AUTO, isWatch = true))
    }
}
