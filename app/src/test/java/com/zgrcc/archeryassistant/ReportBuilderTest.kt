package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ReportBuilderTest {
    private val utc = ZoneId.of("UTC")
    private val day = 24L * 3600 * 1000
    private val now = 1_792_500_000_000L

    private fun meta(author: String = "ZGrCc", icon: String? = "QUJD", version: String = "v1.16（24）") = ReportMeta(
        author = author, versionLabel = version, signature = "C7:08:6E:C9", signatureNote = "OK",
        generatedAtMs = now, iconBase64 = icon, arrowsPerEnd = 6, zone = utc,
    )

    private fun shot(rel: Long, accel: Float? = 0.5f, gyro: Float? = 4f, score: Int? = null, cancelled: Boolean = false, feel: Int? = null) =
        ShotEntry(rel, 25f, 18f, accel, gyro, cancelled = cancelled, score = score, feel = feel)

    private fun practice(id: String, start: Long, n: Int = 12, tags: List<String> = emptyList()) = Session(
        id, start, start + 600_000, 18f, 2, true, false,
        (1..n).map { shot(it * 18_000L, accel = 0.4f + it * 0.01f, gyro = 3f + it * 0.1f) },
        emptyList(), tags,
    )

    private fun match(id: String, start: Long) = Session(
        id, start, start + 900_000, 18f, 2, true, false,
        listOf(shot(75_000, score = 9), shot(100_000, score = 10), shot(130_000, score = 8)),
        emptyList(), listOf("室內18米", "反曲", "比賽"), false, listOf(Mark("1", 60_000, 130_000)),
        CompMeta(120, 3, 0),
    )

    private fun calibration(id: String, start: Long) = Session(
        id, start, start + 300_000, 18f, 2, true, true, listOf(shot(50_000)), emptyList(),
        emptyList(), true, listOf(Mark("只拉弓不放箭", 0, 40_000)),
    )

    @Test
    fun headerShowsAuthorVersionAndIcon() {
        val html = ReportBuilder.build(listOf(practice("a", now - day)), meta())
        assertTrue(html.contains("<b>ZGrCc</b>"))
        assertTrue(html.contains("v1.16（24）"))
        assertTrue(html.contains("data:image/png;base64,QUJD"))
        assertTrue(html.contains("C7:08:6E:C9"))
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.trimEnd().endsWith("</html>"))
    }

    @Test
    fun noIconMeansNoImage() {
        val html = ReportBuilder.build(listOf(practice("a", now - day)), meta(icon = null))
        assertFalse(html.contains("<img"))
        assertFalse(html.contains("base64"))
    }

    @Test
    fun emptyReportStillHasHeaderAndFooter() {
        val html = ReportBuilder.build(emptyList(), meta())
        assertTrue(html.contains("還沒有任何記錄"))
        assertTrue(html.contains("ZGrCc"))
        assertTrue(html.contains("<footer>"))
    }

    @Test
    fun specialCharactersAreEscaped() {
        val html = ReportBuilder.build(listOf(practice("a", now - day)), meta(author = "<script>alert(1)</script>&\"'"))
        assertFalse(html.contains("<script"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;&amp;&quot;&#39;"))
    }

    @Test
    fun noScriptsAndNoExternalResources() {
        val html = ReportBuilder.build(listOf(practice("a", now - day), match("m", now - 2 * day), calibration("c", now - 3 * day)), meta())
        assertFalse(html.contains("<script"))
        assertFalse(html.contains("src=\"http"))
        assertFalse(html.contains("href=\"http"))
        assertFalse(html.contains("@import"))
    }

    @Test
    fun everySessionHasAnchorAndTableLink() {
        val list = listOf(practice("20261020_100000", now - day), match("20261019_100000", now - 2 * day))
        val html = ReportBuilder.build(list, meta())
        for (s in list) {
            assertTrue(html.contains("id=\"s-${s.id}\""))
            assertTrue(html.contains("href=\"#s-${s.id}\""))
        }
    }

    @Test
    fun calibrationIsListedButNotCounted() {
        val html = ReportBuilder.build(listOf(practice("a", now - day, n = 10), calibration("c", now - 2 * day)), meta())
        assertTrue(html.contains("校準錄製不計入"))
        // 總覽：練習與比賽 1 次、總箭數 10（校準那一箭不算）
        assertTrue(html.contains("<div class=\"v\">1</div><div class=\"l\">練習與比賽（次）</div>"))
        assertTrue(html.contains("<div class=\"v\">10</div><div class=\"l\">總箭數</div>"))
    }

    @Test
    fun matchShowsSummaryAndPace() {
        val html = ReportBuilder.build(listOf(match("m", now - day)), meta())
        assertTrue(html.contains("比賽統計"))
        assertTrue(html.contains("節奏分析"))
        assertTrue(html.contains("比賽對比"))
        assertTrue(html.contains("第 1 趟"))
    }

    @Test
    fun chartAppearsWithEnoughShots() {
        val html = ReportBuilder.build(listOf(practice("a", now - day, n = 12)), meta())
        assertTrue(html.contains("<polyline"))
        assertTrue(html.contains("加速度晃動"))
    }

    @Test
    fun cancelledShotsAreMarkedAndNotNumbered() {
        val s = Session("x", now - day, now - day + 100_000, 18f, 2, true, false,
            listOf(shot(10_000), shot(20_000, cancelled = true), shot(30_000)), emptyList())
        val html = ReportBuilder.build(listOf(s), meta())
        assertTrue(html.contains("class=\"cancel\""))
        assertTrue(html.contains("誤判（已排除）"))
    }

    @Test
    fun feelTableOnlyWhenRated() {
        val plain = ReportBuilder.build(listOf(practice("a", now - day)), meta())
        assertFalse(plain.contains("自評與晃動"))
        val rated = Session("r", now - day, now - day + 100_000, 18f, 2, true, false,
            listOf(shot(10_000, feel = Feel.GOOD), shot(20_000, feel = Feel.BAD)), emptyList())
        assertTrue(ReportBuilder.build(listOf(rated), meta()).contains("自評與晃動"))
    }

    @Test
    fun englishReport() {
        try {
            I18n.english = true
            val html = ReportBuilder.build(listOf(practice("a", now - day)), meta())
            assertTrue(html.contains("lang=\"en\""))
            assertTrue(html.contains("Archery training and match report"))
            assertTrue(html.contains("Author"))
            assertFalse(html.contains("射箭訓練與比賽報告"))
        } finally {
            I18n.english = false
        }
    }

    @Test
    fun newestSessionFirstInTable() {
        val html = ReportBuilder.build(listOf(practice("old", now - 5 * day), practice("new", now - day)), meta())
        assertTrue(html.indexOf("href=\"#s-new\"") < html.indexOf("href=\"#s-old\""))
    }

    @Test
    fun linePathsAreWellFormed() {
        val chart = ReportCharts.line("t", "u", listOf(ShotRank(1, 1f), ShotRank(2, 2f), ShotRank(3, 1.5f)), ShotRank(1, 1f), ShotRank(2, 2f), 2)
        assertTrue(chart.startsWith("<svg"))
        assertTrue(chart.contains("</svg>"))
        assertEquals(1, Regex("<polyline").findAll(chart).count())
        assertEquals("", ReportCharts.line("t", "u", listOf(ShotRank(1, 1f)), null, null, 2))
        assertEquals("", ReportCharts.bars("t", emptyList(), emptyList()))
    }
}
