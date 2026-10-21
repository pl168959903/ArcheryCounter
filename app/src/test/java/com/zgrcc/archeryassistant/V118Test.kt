package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files

class V118Test {
    private fun tmp(): File = Files.createTempDirectory("v118").toFile()

    private fun sess(id: String, tags: List<String> = emptyList(), start: Long = 0) =
        Session(id, start, start + 5000, 16f, 2, false, false, listOf(ShotEntry(1000, 20f, 16f, 1f, null)), emptyList(), tags, false, emptyList(), null)

    private fun json(id: String) = SessionJson.encode(sess(id)).toByteArray(Charsets.UTF_8)

    private class Fake(val files: Map<String, ByteArray>, val name: String = "Pixel 8") : ImportSource {
        override fun list() = files.map { RemoteFile(it.key, it.value.size.toLong()) }
        override fun deviceName() = name
        override fun open(name: String): InputStream = ByteArrayInputStream(files.getValue(name))
    }

    // ---- 設定備份 ----
    @Test
    fun backupRoundTrip() {
        val all = mapOf(
            "touch_lock" to true, "arrows_per_end" to 6, "update_last_check" to 123L, "disc_size" to 1.5f,
            "distance_tag" to "18m\n換行", "count" to 42, "share_devices" to "secret",
        )
        val text = SettingsBackup.encode(all, "v1.18（30）")
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("count="))
        assertFalse(text.contains("update_last_check"))
        val back = SettingsBackup.decode(text)!!
        val m = back.associate { it.key to it }
        assertEquals('b', m.getValue("touch_lock").type)
        assertEquals("true", m.getValue("touch_lock").value)
        assertEquals("6", m.getValue("arrows_per_end").value)
        assertEquals("18m\n換行", m.getValue("distance_tag").value)
        assertEquals(4, back.size)
    }

    @Test
    fun backupRejectsBadInput() {
        assertNull(SettingsBackup.decode("hello"))
        val bad = "#ArcheryAssistant-settings v\nb:Bad Key=true\nb:ok_key=maybe\ni:n=abc\ns:count=1\nb:fine=true\nx:q=1\n"
        val r = SettingsBackup.decode(bad)!!
        assertEquals(listOf("fine"), r.map { it.key })
    }

    @Test
    fun backupFileInStoreAndShared() {
        val store = SessionStore(tmp())
        assertNull(store.readSettingsBackup())
        assertTrue(store.writeSettingsBackup(SettingsBackup.encode(mapOf("a" to true), "v")))
        assertNotNull(store.readSettingsBackup())
        assertTrue(SettingsBackup.FILE_NAME in store.shareNames())
    }

    // ---- 預覽 ----
    @Test
    fun previewListsNewAndExistingWithoutWriting() {
        val store = SessionStore(tmp())
        store.importJson("old.json", json("old"), false)
        val src = Fake(mapOf("old.json" to json("old"), "n1.json" to json("n1"), "n2.json" to json("n2"), SettingsBackup.FILE_NAME to "x".toByteArray()))
        val p = SessionImporter(store, src).preview(withRaw = false)
        assertEquals(listOf("n1", "n2"), p.newIds)
        assertEquals(listOf("old"), p.existingIds)
        assertTrue(p.hasSettings)
        assertEquals("Pixel 8", p.deviceName)
        assertEquals(setOf("old"), store.localIds())
    }

    @Test
    fun previewText() {
        I18n.english = false
        val p = ImportPreview(newIds = listOf("a", "b"), existingIds = listOf("c"), deviceName = "Pixel 8")
        assertEquals("對方：Pixel 8\n將新增 2 筆，本機已有 1 筆會略過", ImportPreviewText.describe(p, false))
        assertTrue(ImportPreviewText.describe(p, true).contains("覆蓋"))
    }

    // ---- 設定檔一併取得 ----
    @Test
    fun importCanFetchSettingsBackup() {
        val store = SessionStore(tmp())
        val backup = SettingsBackup.encode(mapOf("touch_lock" to false), "v1")
        val src = Fake(mapOf("a.json" to json("a"), SettingsBackup.FILE_NAME to backup.toByteArray()))
        val r = SessionImporter(store, src).run(false, false, withSettings = true)
        assertTrue(r.settings)
        assertEquals(listOf("a"), r.ids)
        assertNotNull(store.readSettingsBackup())
        val r2 = SessionImporter(SessionStore(tmp()), src).run(false, false, withSettings = false)
        assertFalse(r2.settings)
    }

    @Test
    fun invalidSettingsBackupIsNotStored() {
        val store = SessionStore(tmp())
        val src = Fake(mapOf(SettingsBackup.FILE_NAME to "garbage".toByteArray()))
        assertFalse(SessionImporter(store, src).run(false, false, withSettings = true).settings)
        assertNull(store.readSettingsBackup())
    }

    // ---- 來源標記 ----
    @Test
    fun marksPutGetForget() {
        val p = FakePrefs()
        val m = ImportMarks(p)
        m.put(listOf("a", "b"), "  Pixel\t8 ")
        assertEquals("Pixel 8", ImportMarks(p).get("a"))
        assertEquals(setOf("a", "b"), ImportMarks(p).all().keys)
        m.put(listOf("a"), "Watch")
        assertEquals("Watch", ImportMarks(p).get("a"))
        m.forget(listOf("a"))
        assertNull(ImportMarks(p).get("a"))
        m.put(listOf("c"), "   ")
        assertNull(ImportMarks(p).get("c"))
    }

    // ---- /info ----
    @Test
    fun infoEndpointNeedsAuth() {
        val r = ShareRouter("1234", { emptyList() }, { null }, { "" }, deviceName = "My Watch")
        assertEquals(403, r.handle("GET", "/info").status)
        assertEquals("My Watch", String(r.handle("GET", "/info?pin=1234").stream().readBytes()))
    }

    @Test
    fun httpSourceReadsDeviceName() {
        val remote = SessionStore(tmp())
        remote.importJson("a.json", json("a"), false)
        val router = ShareRouter("4321", { remote.shareNames() }, { remote.shareFile(it) }, { "" }, deviceName = "Galaxy Watch")
        val server = ShareServer(router, 18194)
        assertTrue(server.start())
        try {
            assertEquals("Galaxy Watch", HttpSource("http://127.0.0.1:18194", "4321").deviceName())
        } finally {
            server.stop()
        }
    }

    // ---- 報告篩選與來源 ----
    private fun meta(tag: String? = null, origins: Map<String, String> = emptyMap()) = ReportMeta(
        author = "ZGrCc", versionLabel = "v1.18", signature = null, signatureNote = null,
        generatedAtMs = 0, iconBase64 = null, arrowsPerEnd = 6, filterTag = tag, origins = origins,
    )

    @Test
    fun reportFiltersByTag() {
        I18n.english = false
        val s = listOf(sess("aa", listOf("18m"), 1000), sess("bb", listOf("70m"), 2000), sess("cc", emptyList(), 3000))
        val all = ReportBuilder.build(s, meta())
        assertTrue(all.contains("s-aa") && all.contains("s-bb") && all.contains("s-cc"))
        val f = ReportBuilder.build(s, meta("18m"))
        assertTrue(f.contains("s-aa"))
        assertFalse(f.contains("s-bb"))
        assertFalse(f.contains("s-cc"))
        assertTrue(f.contains("篩選標籤"))
        assertFalse(all.contains("篩選標籤"))
        assertFalse(ReportBuilder.build(s, meta("")).contains("篩選標籤"))
    }

    @Test
    fun reportShowsOrigin() {
        I18n.english = false
        val s = listOf(sess("aa", emptyList(), 1000), sess("bb", emptyList(), 2000))
        val html = ReportBuilder.build(s, meta(origins = mapOf("aa" to "Pixel <8>")))
        assertTrue(html.contains("來自 Pixel &lt;8&gt;"))
        assertFalse(html.contains("Pixel <8>"))
        assertEquals(2, Regex("來自 Pixel").findAll(html).count())   // 表格與該筆標題各一次
    }

    // ---- 設定 ----
    @Test
    fun v118SettingsDefaultsAndReset() {
        val s = AppSettings(FakePrefs())
        assertTrue(s.shareQr.value)
        assertTrue(s.importDiscover.value)
        assertTrue(s.importMark.value)
        assertTrue(s.importPreview.value)
        assertFalse(s.importSettings.value)
        assertTrue(s.settingsBackup.value)
        assertEquals("", s.reportTag.value)
        s.setShareQr(false); s.setImportDiscover(false); s.setImportMark(false); s.setImportPreview(false)
        s.setImportSettings(true); s.setSettingsBackup(false); s.setReportTag("18m")
        s.resetDefaults()
        assertTrue(s.shareQr.value)
        assertTrue(s.importDiscover.value)
        assertTrue(s.importMark.value)
        assertTrue(s.importPreview.value)
        assertFalse(s.importSettings.value)
        assertTrue(s.settingsBackup.value)
        assertEquals("", s.reportTag.value)
    }

    @Test
    fun reportTagPersists() {
        val p = FakePrefs()
        AppSettings(p).setReportTag("反曲")
        assertEquals("反曲", AppSettings(p).reportTag.value)
    }
}
