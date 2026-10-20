package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V111Test {

    private fun shot(rel: Long, accel: Float?, score: Int? = null, cancelled: Boolean = false) =
        ShotEntry(relMs = rel, peak = 20f, threshold = 16f, accelRms = accel, gyroRms = null, score = score, cancelled = cancelled)

    private fun match(id: String, start: Long, scores: Boolean, accel: Float, sec: Int = 120, apn: Int = 3) = Session(
        id = id, startMs = start, endMs = start + 500_000, threshold = 16f, windowSec = 2, hasGyro = false, rawCsv = false,
        shots = listOf(
            shot(100_000, accel, if (scores) 10 else null), shot(110_000, accel, if (scores) 9 else null), shot(120_000, accel, if (scores) 8 else null),
            shot(300_000, accel, if (scores) 7 else null), shot(310_000, accel, if (scores) 7 else null), shot(320_000, accel, if (scores) 7 else null),
        ),
        manual = emptyList(),
        marks = listOf(Mark("1", 60_000, 130_000), Mark("2", 250_000, 330_000)),
        comp = CompMeta(secondsPerEnd = sec, arrowsPerEnd = apn, practiceEnds = 0),
    )

    // ---------- 比賽對比 ----------
    @Test
    fun rowsOnlyMatchesSortedOldToNew() {
        val plain = match("p", 0, false, 1f).copy(comp = null)
        val calib = match("c", 1, false, 1f).copy(calibration = true)
        val open = match("o", 2, false, 1f).copy(endMs = null)
        val a = match("a", 3_000_000, true, 2f)
        val b = match("b", 1_000_000, true, 1f)
        val rows = MatchCompare.rows(listOf(plain, calib, open, a, b))
        assertEquals(listOf("b", "a"), rows.map { it.id })
    }

    @Test
    fun rowFieldsComeFromSummary() {
        val r = MatchCompare.rows(listOf(match("a", 0, true, 2f))).single()
        assertEquals(2, r.ends)
        assertEquals(6, r.totalArrows)
        assertEquals(10 + 9 + 8 + 21, r.scoreTotal)
        assertEquals(2f, r.accelAvg!!, 0.001f)
        assertEquals(3, r.arrowsPerEnd)
        assertEquals(120, r.secondsPerEnd)
    }

    @Test
    fun limitKeepsMostRecent() {
        val list = (1..25).map { match("m$it", it * 1_000_000L, false, 1f) }
        val rows = MatchCompare.rows(list)
        assertEquals(MatchCompare.LIMIT, rows.size)
        assertEquals("m25", rows.last().id)
        assertEquals("m6", rows.first().id)
    }

    @Test
    fun sameAsLatestFiltersFormat() {
        val rows = MatchCompare.rows(
            listOf(
                match("a", 1_000_000, false, 1f, sec = 120),
                match("b", 2_000_000, false, 1f, sec = 240),
                match("c", 3_000_000, false, 1f, sec = 240),
            ),
        )
        assertEquals(listOf("b", "c"), MatchCompare.sameAsLatest(rows).map { it.id })
        assertTrue(MatchCompare.sameAsLatest(emptyList()).isEmpty())
    }

    @Test
    fun seriesSkipMissingAndDelta() {
        val rows = MatchCompare.rows(
            listOf(match("a", 1_000_000, true, 1f), match("b", 2_000_000, false, 2f), match("c", 3_000_000, true, 3f)),
        )
        assertEquals(2, MatchCompare.scoreSeries(rows).size)
        assertEquals(3, MatchCompare.shakeSeries(rows).size)
        assertNull(MatchCompare.scoreDelta(rows))           // 最後一場和前一場比，前一場沒環數
        val two = rows.filter { it.id != "b" }
        assertEquals(0, MatchCompare.scoreDelta(two))
        assertNull(MatchCompare.scoreDelta(rows.take(1)))
    }

    // ---------- 靜止條件建議 ----------
    private fun adviceSession(valid: List<Float>, bad: List<Float>): Session {
        var t = 0L
        val shots = valid.map { t += 10_000; shot(t, it) } + bad.map { t += 10_000; shot(t, it, cancelled = true) }
        return Session("s", 0, t + 1, 16f, 2, false, false, shots, emptyList())
    }

    @Test
    fun stillAdviceNeedsData() {
        val a = adviseStill(listOf(adviceSession(listOf(1f, 2f), listOf(8f))), 5)
        assertEquals(AdviceKind.NOT_ENOUGH, a.kind)
        assertNull(a.suggested)
    }

    @Test
    fun stillAdviceSuggestsTighter() {
        val valid = List(12) { 1.5f }                // 有效：很穩
        val bad = listOf(4.5f, 4.8f, 4.9f, 4.7f)     // 誤判：較晃（但仍低於目前 5）
        val a = adviseStill(listOf(adviceSession(valid, bad)), 5)
        assertEquals(AdviceKind.SUGGEST, a.kind)
        assertEquals(4, a.suggested)                  // 4 就能擋掉全部、保留全部，且離目前最近
        assertEquals(1f, a.validKept, 0.001f)
        assertEquals(1f, a.cancelledExcluded, 0.001f)
    }

    @Test
    fun stillAdviceNotSeparableWhenOverlap() {
        val valid = List(12) { 3f }
        val bad = listOf(3f, 3f, 3f, 3f)
        val a = adviseStill(listOf(adviceSession(valid, bad)), 5)
        assertEquals(AdviceKind.NOT_SEPARABLE, a.kind)
        assertNull(a.suggested)
    }

    @Test
    fun stillAdviceIgnoresCalibrationAndNullRms() {
        val s = adviceSession(List(12) { 1f }, listOf(4.5f, 4.5f, 4.5f)).copy(calibration = true)
        assertEquals(AdviceKind.NOT_ENOUGH, adviseStill(listOf(s), 5).kind)
        val n = Session("n", 0, 1, 16f, 2, false, false, List(20) { shot(it * 1000L, null) }, emptyList())
        assertEquals(AdviceKind.NOT_ENOUGH, adviseStill(listOf(n), 5).kind)
    }

    @Test
    fun stillAdviceCannotGoBelowOne() {
        val a = adviseStill(listOf(adviceSession(List(12) { 0.5f }, listOf(4f, 4f, 4f))), 1)
        assertNull(a.suggested)
    }

    // ---------- 冷卻建議 ----------
    /** 有效箭間隔 [gap] 秒，共 [n] 箭；每箭後 [dbl] 秒再多一筆誤判（一次放箭算成兩次）。 */
    private fun cooldownSession(n: Int, gap: Long, dbl: Long, doubled: Int): Session {
        val shots = ArrayList<ShotEntry>()
        for (i in 0 until n) {
            val t = (i + 1) * gap * 1000L
            shots.add(shot(t, 1f))
            if (i < doubled) shots.add(shot(t + dbl * 1000L, 1f, cancelled = true))
        }
        return Session("c", 0, n * gap * 1000L + 1, 16f, 2, false, false, shots, emptyList())
    }

    @Test
    fun cooldownAdviceNeedsData() {
        val a = adviseCooldown(listOf(cooldownSession(5, 20, 4, 1)), 3)
        assertEquals(AdviceKind.NOT_ENOUGH, a.kind)
    }

    @Test
    fun cooldownAdviceSuggestsLonger() {
        // 有效箭間隔約 24 秒（誤判夾在中間時，相鄰有效仍是 24 秒），誤判離前一筆 4 秒
        val a = adviseCooldown(listOf(cooldownSession(15, 24, 4, 5)), 3)
        assertEquals(AdviceKind.SUGGEST, a.kind)
        assertEquals(5, a.suggested)                  // 5 秒冷卻就能擋掉 4 秒的重複
        assertEquals(1f, a.validKept, 0.001f)
        assertEquals(1f, a.cancelledExcluded, 0.001f)
    }

    @Test
    fun cooldownAdviceStaysAtOrBelowValidGap() {
        // 有效間隔 10 秒、誤判離前一筆 8 秒：冷卻設 9 就擋掉誤判，又不影響 10 秒間隔
        val a = adviseCooldown(listOf(cooldownSession(15, 10, 8, 5)), 3)
        assertEquals(AdviceKind.SUGGEST, a.kind)
        assertEquals(9, a.suggested)
    }

    @Test
    fun cooldownAdviceNotSeparable() {
        // 誤判都離前一筆很久（60 秒），冷卻再長也擋不到
        val s = adviceSession(List(15) { 1f }, List(5) { 1f })
        val shots = s.shots.mapIndexed { i, sh -> sh.copy(relMs = if (i < 15) (i + 1) * 24_000L else 15 * 24_000L + (i - 14) * 60_000L) }
        val a = adviseCooldown(listOf(s.copy(shots = shots)), 3)
        assertEquals(AdviceKind.NOT_SEPARABLE, a.kind)
        assertNull(a.suggested)
    }

    // ---------- 快速檢查 ----------
    @Test
    fun quickCheckMoves() {
        assertEquals(-1, QuickCheck.start(0))
        assertEquals(0, QuickCheck.start(5))
        assertEquals(1, QuickCheck.move(5, 0, 1))
        assertEquals(0, QuickCheck.move(5, 0, -1))
        assertEquals(4, QuickCheck.move(5, 4, 1))
        assertEquals(0, QuickCheck.move(0, 3, 1))
        assertEquals(3, QuickCheck.afterMark(5, 2))
        assertEquals(4, QuickCheck.afterMark(5, 4))
        assertEquals("3 / 5", QuickCheck.progress(5, 2))
        assertNotNull(QuickCheck)
    }

    // ---------- 磁貼 ----------
    @Test
    fun tileLaunchParsing() {
        assertEquals("detect", TileInfo.parse("detect"))
        assertEquals("match", TileInfo.parse("match"))
        assertNull(TileInfo.parse("other"))
        assertNull(TileInfo.parse(null))
        assertNull(TileInfo.parse(""))
    }

    @Test
    fun tileTodayLine() {
        I18n.english = false
        assertEquals("今日 36 箭", TileInfo.todayLine(36, 0))
        assertEquals("今日 36 / 72 箭", TileInfo.todayLine(36, 72))
        assertEquals("今日 0 箭", TileInfo.todayLine(-3, 0))
        I18n.english = true
        assertEquals("Today 36 / 72", TileInfo.todayLine(36, 72))
        assertEquals("Today 5 arrows", TileInfo.todayLine(5, 0))
        I18n.english = false
    }
}
