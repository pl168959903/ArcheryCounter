package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V1171Test {
    private val day = UpdatePolicy.INTERVAL_MS

    @Test
    fun everyStartAlwaysChecks() {
        assertTrue(UpdatePolicy.shouldAutoCheck(UpdateMode.EVERY_START, 1000, 1005))
        assertTrue(UpdatePolicy.shouldAutoCheck(UpdateMode.EVERY_START, 0, 0))
    }

    @Test
    fun dailyChecksOncePerDay() {
        assertFalse(UpdatePolicy.shouldAutoCheck(UpdateMode.DAILY, 1000, 1000 + day - 1))
        assertTrue(UpdatePolicy.shouldAutoCheck(UpdateMode.DAILY, 1000, 1000 + day))
        assertTrue(UpdatePolicy.shouldAutoCheck(UpdateMode.DAILY, 0, 5))
    }

    @Test
    fun offNeverChecks() {
        assertFalse(UpdatePolicy.shouldAutoCheck(UpdateMode.OFF, 0, 5))
    }

    @Test
    fun modeCyclesThroughThree() {
        assertEquals(UpdateMode.DAILY, UpdateMode.next(UpdateMode.EVERY_START))
        assertEquals(UpdateMode.OFF, UpdateMode.next(UpdateMode.DAILY))
        assertEquals(UpdateMode.EVERY_START, UpdateMode.next(UpdateMode.OFF))
        assertEquals(UpdateMode.EVERY_START, UpdateMode.valid(99))
    }

    @Test
    fun labels() {
        I18n.english = false
        assertEquals("每次啟動自動檢查", UpdateMode.label(UpdateMode.EVERY_START))
        assertEquals("每天第一次啟動自動檢查", UpdateMode.label(UpdateMode.DAILY))
        assertEquals("無自動檢查", UpdateMode.label(UpdateMode.OFF))
    }

    @Test
    fun legacyConversion() {
        assertEquals(UpdateMode.OFF, UpdateMode.fromLegacy(false, true))
        assertEquals(UpdateMode.OFF, UpdateMode.fromLegacy(false, false))
        assertEquals(UpdateMode.EVERY_START, UpdateMode.fromLegacy(true, true))
        assertEquals(UpdateMode.DAILY, UpdateMode.fromLegacy(true, false))
    }

    @Test
    fun freshInstallDefaultsToEveryStart() {
        assertEquals(UpdateMode.EVERY_START, AppSettings(FakePrefs()).updateMode.value)
    }

    @Test
    fun legacySettingsAreMigrated() {
        val off = FakePrefs().also { it.edit().putBoolean("update_auto", false).apply() }
        assertEquals(UpdateMode.OFF, AppSettings(off).updateMode.value)
        val daily = FakePrefs().also { it.edit().putBoolean("update_auto", true).putBoolean("update_every_start", false).apply() }
        assertEquals(UpdateMode.DAILY, AppSettings(daily).updateMode.value)
        val every = FakePrefs().also { it.edit().putBoolean("update_auto", true).apply() }
        assertEquals(UpdateMode.EVERY_START, AppSettings(every).updateMode.value)
    }

    @Test
    fun newKeyWinsOverLegacy() {
        val p = FakePrefs().also { it.edit().putBoolean("update_auto", false).putInt("update_mode", 1).apply() }
        assertEquals(UpdateMode.DAILY, AppSettings(p).updateMode.value)
    }

    @Test
    fun modePersistsAndResets() {
        val p = FakePrefs()
        val s = AppSettings(p)
        s.setUpdateMode(UpdateMode.OFF)
        assertEquals(UpdateMode.OFF, AppSettings(p).updateMode.value)
        s.resetDefaults()
        assertEquals(UpdateMode.EVERY_START, s.updateMode.value)
    }

    private val md = """
# 版本記錄

這個專案的每個版本改了什麼。

### v1.17
- **從別台匯入**：輸入 `192.168.1.23`。
  - 子項目

- 第二項

### v1.16.3
只有一行。

### v1.16.2
- x
""".trimIndent()

    @Test
    fun parsesVersionsAndSkipsHeader() {
        val e = Changelog.parse(md)
        assertEquals(listOf("v1.17", "v1.16.3", "v1.16.2"), e.map { it.version })
        assertEquals(listOf("只有一行。"), e[1].lines)
        assertTrue(e[0].lines.first().startsWith("- **從別台匯入**"))
    }

    @Test
    fun cleansMarkdown() {
        assertEquals("• 從別台匯入：輸入 192.168.1.23。", Changelog.clean("- **從別台匯入**：輸入 `192.168.1.23`。"))
        assertEquals("◦ 子項目", Changelog.clean("  - 子項目"))
        assertEquals("只有一行。", Changelog.clean("只有一行。"))
        val p = Changelog.paragraphs(Changelog.parse(md)[0])
        assertEquals(listOf("• 從別台匯入：輸入 192.168.1.23。", "◦ 子項目", "• 第二項"), p)
    }

    @Test
    fun publishedTagsSkipDrafts() {
        val json = """[{"tag_name":"v1.17","draft":false},{"tag_name":"v1.16.3"},{"tag_name":"v9","draft":true}]"""
        assertEquals(setOf("v1.17", "v1.16.3"), Changelog.publishedTags(json))
        assertNull(Changelog.publishedTags("not json"))
        assertNull(Changelog.publishedTags("{\"a\":1}"))
    }

    @Test
    fun onlyPublishedFilters() {
        val e = Changelog.parse(md)
        assertEquals(listOf("v1.17", "v1.16.2"), Changelog.onlyPublished(e, setOf("v1.17", "v1.16.2")).map { it.version })
        assertEquals(3, Changelog.onlyPublished(e, null).size)
        assertEquals(0, Changelog.onlyPublished(e, emptySet()).size)
    }

    @Test
    fun realChangelogFileParses() {
        val f = java.io.File("CHANGELOG.md").takeIf { it.exists() } ?: java.io.File("../CHANGELOG.md").takeIf { it.exists() }
        if (f == null) return
        val e = Changelog.parse(f.readText(Charsets.UTF_8))
        assertTrue(e.size >= 5)
        assertTrue(e.all { it.version.startsWith("v") })
        assertTrue(e.all { Changelog.paragraphs(it).isNotEmpty() })
    }
}

class AppLinkTest {
    @org.junit.Test
    fun githubUrlMatchesRepo() {
        org.junit.Assert.assertEquals("https://github.com/" + ReleaseParser.REPO, AppInfo.GITHUB_URL)
        org.junit.Assert.assertEquals(ReleaseParser.REPO, AppInfo.GITHUB_SHORT)
    }
}
