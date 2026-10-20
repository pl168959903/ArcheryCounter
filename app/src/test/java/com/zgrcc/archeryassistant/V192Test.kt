package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V192Test {
    @Test
    fun allCategoriesHaveUniqueNamesInBothLanguages() {
        for (english in listOf(false, true)) {
            I18n.english = english
            val names = (0 until SettingsCategories.COUNT).map { SettingsCategories.name(it) }
            assertTrue(names.all { it.isNotEmpty() })
            assertEquals(names.size, names.toSet().size)
        }
        I18n.english = false
    }

    @Test
    fun routesAreUniqueAndValidRange() {
        val routes = (0 until SettingsCategories.COUNT).map { SettingsCategories.route(it) }
        assertEquals(routes.size, routes.toSet().size)
        assertEquals("settings_3", SettingsCategories.route(SettingsCategories.TRAINING))
        assertTrue(SettingsCategories.valid(0))
        assertTrue(SettingsCategories.valid(SettingsCategories.COUNT - 1))
        assertFalse(SettingsCategories.valid(-1))
        assertFalse(SettingsCategories.valid(SettingsCategories.COUNT))
        assertEquals("", SettingsCategories.name(-1))
    }

    @Test
    fun orderMatchesDesign() {
        I18n.english = false
        assertEquals(
            listOf("一般", "偵測", "記錄", "訓練", "比賽模式", "圖表與顯示", "資料", "關於與重設"),
            (0 until SettingsCategories.COUNT).map { SettingsCategories.name(it) },
        )
    }
}
