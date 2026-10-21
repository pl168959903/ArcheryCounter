package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class V1183Test {
    private fun e(t: Boolean = false, m: Boolean = false, g: Boolean = false, o: Boolean = false) =
        ScreenRules.HistoryEntries(t, m, g, o)

    @Test
    fun groupsVisibility() {
        assertTrue(!HistoryGroups.trendVisible(e()) && !HistoryGroups.compareVisible(e()))
        assertTrue(HistoryGroups.trendVisible(e(t = true)) && HistoryGroups.trendVisible(e(o = true)))
        assertTrue(!HistoryGroups.compareVisible(e(t = true, o = true)))
        assertTrue(HistoryGroups.compareVisible(e(m = true)) && HistoryGroups.compareVisible(e(g = true)))
        assertTrue(!HistoryGroups.trendVisible(e(m = true, g = true)))
    }

    @Test
    fun reportLinkCarriesPin() {
        assertEquals("http://192.168.1.2:8080/report.html?pin=1234", ReportLink.url("http://192.168.1.2:8080", "1234"))
        assertEquals("http://192.168.1.2:8080/report.html", ReportLink.url("http://192.168.1.2:8080/", ""))
        assertEquals("http://h/report.html?pin=12", ReportLink.url("http://h", "1 2&x"))
    }
}
