package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class V1193Test {
    @Test
    fun noSessionsMeansNoCsvNames() {
        val store = SessionStore(Files.createTempDirectory("v1193").toFile())
        assertTrue(store.shareNames().isEmpty())
        assertTrue(store.shareFile("summary.csv") == null)
    }

    @Test
    fun zipContainsGeneratedCsvs() {
        val store = SessionStore(Files.createTempDirectory("v1193b").toFile())
        val s = Session("20260101_100000", 0, 5000, 16f, 2, false, false, listOf(ShotEntry(1000, 20f, 16f, 1f, null)), emptyList())
        store.importJson("20260101_100000.json", SessionJson.encode(s).toByteArray(), false)
        val zip = store.shareZip()!!
        val z = java.util.zip.ZipInputStream(zip.open())
        val names = HashSet<String>()
        while (true) { val e = z.nextEntry ?: break; names.add(e.name) }
        assertEquals(setOf("20260101_100000.json", "summary.csv", "shots.csv"), names)
    }
}
