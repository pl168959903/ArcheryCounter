package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class V1185Test {
    @Test
    fun logNewestFirst() {
        val a = ShareEvent(1000, "1.1.1.1", "x", ShareResult.OK_PIN)
        val b = ShareEvent(3000, "1.1.1.2", "x", ShareResult.OK_TRUST)
        val c = ShareEvent(3000, "1.1.1.3", "x", ShareResult.WRONG_PIN)
        val out = ShareText.newestFirst(listOf(a, b, c))
        assertEquals(listOf("1.1.1.3", "1.1.1.2", "1.1.1.1"), out.map { it.ip })
        assertTrue(ShareText.newestFirst(emptyList()).isEmpty())
    }

    @Test
    fun signatureMatchIsSilentButWarningsRemain() {
        assertEquals("", AppInfo.signatureStatus(AppInfo.FIXED_KEY_PREFIX))
        assertTrue(AppInfo.signatureStatus("AA:BB:CC:DD").isNotEmpty())
        assertTrue(AppInfo.signatureStatus("?").isNotEmpty())
    }
}
