package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class V1182Test {
    @Test
    fun clashRules() {
        assertTrue(RecordMeta.clashes("aaaa", "bbbb"))
        assertTrue(!RecordMeta.clashes("aaaa", "aaaa"))
        assertTrue(!RecordMeta.clashes("", "bbbb"))
        assertTrue(!RecordMeta.clashes("aaaa", ""))
    }

    @Test
    fun previewTextMentionsClash() {
        val p = ImportPreview(newIds = listOf("a", "b"), collisions = 1, deviceName = "W")
        assertTrue(ImportPreviewText.describe(p, false).contains("另存新編號"))
        assertTrue(!ImportPreviewText.describe(p.copy(collisions = 0), false).contains("另存"))
    }
}
