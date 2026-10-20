package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V113Test {

    // ---------- 版本比較 ----------
    @Test
    fun parseVersions() {
        assertEquals(listOf(1, 12, 1), VersionCompare.parse("v1.12.1"))
        assertEquals(listOf(1, 12), VersionCompare.parse("1.12"))
        assertEquals(listOf(1, 0), VersionCompare.parse(" V1.0 "))
        assertNull(VersionCompare.parse(null))
        assertNull(VersionCompare.parse(""))
        assertNull(VersionCompare.parse("v"))
        assertNull(VersionCompare.parse("v1.x"))
        assertNull(VersionCompare.parse("v1..2"))
        assertNull(VersionCompare.parse("v1.-2"))
        assertNull(VersionCompare.parse("1.2-beta"))
    }

    @Test
    fun compareNumericallyNotAsText() {
        assertTrue(VersionCompare.isNewer("v1.10", "1.9"))
        assertTrue(VersionCompare.isNewer("v1.12.1", "1.12"))
        assertTrue(VersionCompare.isNewer("v2.0", "1.99.9"))
        assertFalse(VersionCompare.isNewer("v1.12", "1.12.0"))   // 少一位視為 0
        assertFalse(VersionCompare.isNewer("v1.9", "1.10"))
        assertFalse(VersionCompare.isNewer("v1.12.1", "1.12.1"))
        assertFalse(VersionCompare.isNewer("garbage", "1.12"))
        assertFalse(VersionCompare.isNewer("v1.13", "?"))
        assertEquals(0, VersionCompare.compare("1.2", "1.2.0"))
        assertNull(VersionCompare.compare("a", "1.0"))
    }

    // ---------- 解析 GitHub 回應 ----------
    private val sha = "a".repeat(64)

    private fun release(
        tag: String = "v1.13",
        draft: Boolean = false,
        pre: Boolean = false,
        assetName: String = "ArcheryAssistant-v1.13.apk",
        url: String = "https://github.com/pl168959903/ArcheryCounter/releases/download/v1.13/ArcheryAssistant-v1.13.apk",
        digest: String? = "sha256:$sha",
        body: String = "新增更新功能\\r\\n第二行",
    ) = """
        {"tag_name":"$tag","draft":$draft,"prerelease":$pre,"body":"$body",
         "assets":[
           {"name":"other.txt","browser_download_url":"https://example.com/other.txt","size":5},
           {"name":"$assetName","browser_download_url":"$url","size":4404019${if (digest != null) ",\"digest\":\"$digest\"" else ""}}
         ]}
    """.trimIndent()

    @Test
    fun parsesNormalRelease() {
        val r = ReleaseParser.parse(release())!!
        assertEquals("v1.13", r.tag)
        assertEquals("ArcheryAssistant-v1.13.apk", r.apkName)
        assertTrue(r.apkUrl.startsWith("https://github.com/"))
        assertEquals(4404019L, r.apkSize)
        assertEquals(sha, r.sha256)
        assertTrue(r.notes.startsWith("新增更新功能"))
        assertFalse(r.notes.contains("\r"))
    }

    @Test
    fun rejectsDraftPrereleaseAndBadAssets() {
        assertNull(ReleaseParser.parse(release(draft = true)))
        assertNull(ReleaseParser.parse(release(pre = true)))
        assertNull(ReleaseParser.parse(release(tag = "nightly")))
        assertNull(ReleaseParser.parse(release(assetName = "something.zip")))
        assertNull(ReleaseParser.parse(release(url = "http://example.com/a.apk")))
        assertNull(ReleaseParser.parse("not json"))
        assertNull(ReleaseParser.parse("[]"))
        assertNull(ReleaseParser.parse("""{"tag_name":"v1.13","assets":[]}"""))
    }

    @Test
    fun digestOptionalAndValidated() {
        assertNull(ReleaseParser.parse(release(digest = null))!!.sha256)
        assertNull(ReleaseParser.parse(release(digest = "sha256:xyz"))!!.sha256)
        assertEquals("b".repeat(64), ReleaseParser.parse(release(digest = "sha256:" + "B".repeat(64)))!!.sha256)
    }

    @Test
    fun longNotesAreTrimmed() {
        val r = ReleaseParser.parse(release(body = "x".repeat(1000)))!!
        assertTrue(r.notes.length <= 401)
        assertTrue(r.notes.endsWith("…"))
        assertNotNull(r)
    }

    // ---------- 檢查政策 ----------
    @Test
    fun autoCheckIsDueOncePerDay() {
        val day = UpdatePolicy.INTERVAL_MS
        assertTrue(UpdatePolicy.due(0, 1000))
        assertFalse(UpdatePolicy.due(1000, 1000 + day - 1))
        assertTrue(UpdatePolicy.due(1000, 1000 + day))
        assertTrue(UpdatePolicy.due(5000, 1000))          // 時鐘往回調
    }

    @Test
    fun skippedVersionOnlyHiddenForAutoCheck() {
        assertTrue(UpdatePolicy.shouldOffer("v1.13", "1.12", "", manual = false))
        assertFalse(UpdatePolicy.shouldOffer("v1.13", "1.12", "v1.13", manual = false))
        assertTrue(UpdatePolicy.shouldOffer("v1.13", "1.12", "v1.13", manual = true))
        assertTrue(UpdatePolicy.shouldOffer("v1.14", "1.12", "v1.13", manual = false))   // 更新的版本仍會提示
        assertFalse(UpdatePolicy.shouldOffer("v1.12", "1.12", "", manual = true))        // 沒比較新就不提示
        assertFalse(UpdatePolicy.shouldOffer("v1.11", "1.12", "", manual = true))
    }

    @Test
    fun sizeAndErrorText() {
        I18n.english = false
        assertEquals("4.2 MB", UpdateText.size(4404019))
        assertEquals("?", UpdateText.size(0))
        for (e in UpdateError.values()) assertTrue(UpdateText.error(e).isNotBlank())
        assertTrue(UpdateText.error(UpdateError.NOT_ALLOWED).contains("安裝未知應用程式"))
    }
}
