package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class V1193bTest {
    @Test
    fun resetAndAboutAreSeparateCategories() {
        assertEquals(10, SettingsCategories.COUNT)
        assertEquals("連線", SettingsCategories.name(SettingsCategories.CONNECT))
        assertEquals("重設與備份", SettingsCategories.name(SettingsCategories.RESET))
        assertEquals("關於", SettingsCategories.name(SettingsCategories.ABOUT))
        assertEquals(SettingsCategories.COUNT - 1, SettingsCategories.ABOUT)
        assertEquals(SettingsCategories.CONNECT + 1, SettingsCategories.RESET)
    }
}
