package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.14.1：詳情頁文字從 DetailScreen 拆到 DetailText 後，輸出內容要和拆分前一致。 */
class V1141Test {
    private fun shot(rel: Long, accel: Float? = 1f, cancelled: Boolean = false, feel: Int? = null, score: Int? = null) =
        ShotEntry(rel, 20f, 16f, accel, null, score = score, cancelled = cancelled, feel = feel)

    private fun sess(shots: List<ShotEntry>, end: Long? = 120_000) =
        Session("s", 1_000_000L, end, 16f, 2, false, false, shots, emptyList())

    @Test
    fun summaryTextShowsCountsAndCancelled() {
        val s = sess(listOf(shot(10_000), shot(20_000), shot(30_000, cancelled = true)))
        val text = summaryText(s, computeStats(s), isActive = false)
        assertTrue(text, text.contains("偵測 2 箭"))
        assertTrue(text, text.contains("誤判 1 筆"))
        assertFalse(text.contains("進行中"))
    }

    @Test
    fun summaryTextMarksActiveAndInterrupted() {
        val s = sess(listOf(shot(10_000)), end = null)
        assertTrue(summaryText(s, computeStats(s), isActive = true).contains("進行中"))
        assertTrue(summaryText(s, computeStats(s), isActive = false).contains("中斷"))
    }

    @Test
    fun statsTextHasFeelSectionOnlyWhenRated() {
        val plain = sess(listOf(shot(10_000), shot(20_000)))
        assertFalse(statsText(plain, computeStats(plain)).contains("自評"))
        val rated = sess(listOf(shot(10_000, accel = 1f, feel = Feel.GOOD), shot(20_000, accel = 3f, feel = Feel.BAD)))
        val text = statsText(rated, computeStats(rated))
        assertTrue(text, text.contains("自評與晃動"))
        assertTrue(text, text.contains(Feel.label(Feel.GOOD)))
        assertTrue(text, text.contains(Feel.label(Feel.BAD)))
    }

    @Test
    fun statsTextNoData() {
        val s = sess(emptyList())
        assertEquals(tr("沒有資料", "No data"), statsText(s, computeStats(s)))
    }

    @Test
    fun shotTextCancelledAndNormal() {
        val s = sess(listOf(shot(10_000, score = 9), shot(20_000, cancelled = true)))
        val rows = (0..1).map { TimelineRow(s.shots[it].relMs, if (it == 0) 1 else 0, it, s.shots[it], null, 0) }
        assertTrue(shotText(s, rows[0], s.shots[0]).contains("環數 9"))
        assertTrue(shotText(s, rows[1], s.shots[1]).contains("已取消"))
    }

    @Test
    fun manualTextSigns() {
        val s = sess(emptyList())
        assertTrue(manualText(s, TimelineRow(0, 0, -1, null, null, 2)).contains("+2"))
        assertTrue(manualText(s, TimelineRow(0, 0, -1, null, null, -1)).contains("−1"))
    }

    @Test
    fun paceTextListsEnds() {
        val s = Session(
            "m", 0, 460_000, 16f, 2, false, false,
            listOf(shot(75_000), shot(120_000), shot(150_000)),
            emptyList(), emptyList(), false,
            listOf(Mark("1", 60_000, 150_000)),
            CompMeta(120, 3, 1),
        )
        val p = PaceAnalyzer.analyze(s)!!
        val text = paceText(p)
        assertTrue(text, text.contains("平均第一箭等待"))
        assertTrue(text, text.contains("壓線"))
    }
}
