package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class V121Test {
    private fun sc(rel: Long, score: Int?, acc: Float?, cancelled: Boolean = false) =
        ShotEntry(rel, 30f, 18f, acc, 5f, score = score, cancelled = cancelled)

    private fun sess(shots: List<ShotEntry>, cal: Boolean = false) =
        Session("s", 0, 3_600_000, 18f, 2, true, false, shots, emptyList(), calibration = cal)

    @Test
    fun scoreShakeComparesHighVsOthers() {
        val high = (0 until 5).map { sc(10_000L + it * 17_000L, 10, 0.4f) }
        val low = (5 until 10).map { sc(10_000L + it * 17_000L, 7, 0.6f) }
        val r = ScoreShakeAnalysis.compute(sess(high + low))!!
        assertEquals(0.4f, r.highAvg, 0.001f); assertEquals(5, r.highN)
        assertEquals(0.6f, r.lowAvg, 0.001f); assertEquals(5, r.lowN)
        assertTrue(ScoreShakeAnalysis.text(r).contains("0.40"))
    }

    @Test
    fun scoreShakeNeedsEnoughData() {
        val few = (0 until 9).map { sc(10_000L + it * 17_000L, if (it < 5) 10 else 7, 0.5f) }
        assertEquals(null, ScoreShakeAnalysis.compute(sess(few)))                  // 少於 10 支
        val allHigh = (0 until 12).map { sc(10_000L + it * 17_000L, 10, 0.5f) }
        assertEquals(null, ScoreShakeAnalysis.compute(sess(allHigh)))               // 只有一組
        val ok = (0 until 12).map { sc(10_000L + it * 17_000L, if (it < 6) 9 else 6, 0.5f) }
        assertEquals(null, ScoreShakeAnalysis.compute(sess(ok, cal = true)))        // 校準不分析
        val noScore = (0 until 12).map { sc(10_000L + it * 17_000L, null, 0.5f) }
        assertEquals(null, ScoreShakeAnalysis.compute(sess(noScore)))
        val cancelledIgnored = ok.mapIndexed { i, s -> if (i < 4) s.copy(cancelled = true) else s }
        assertEquals(null, ScoreShakeAnalysis.compute(sess(cancelledIgnored)))      // 誤判不算，剩 8 支
    }

    @Test
    fun endIndicesTotalAndValidToIndex() {
        val a = (0 until 3).map { sc(10_000L + it * 17_000L, 10 - it, 0.5f) }
        val bad = sc(a.last().relMs + 3_000, 5, 0.5f, cancelled = true)
        val b = (0 until 3).map { sc(a.last().relMs + 100_000 + it * 17_000L, null, 0.5f) }
        val s = sess(a + bad + b)
        val ends = endsFor(s, true, 6)
        assertEquals(2, ends.size)
        assertEquals(listOf(0, 1, 2), EndScore.endIndices(s, ends, 1))
        assertEquals(listOf(4, 5, 6), EndScore.endIndices(s, ends, 5))               // 位置 3 是誤判，跳過
        assertEquals(emptyList<Int>(), EndScore.endIndices(s, ends, 3))              // 誤判的箭沒有所屬的趟
        assertEquals(4, EndScore.validToIndex(s, 4))
        assertEquals(-1, EndScore.validToIndex(s, 99))
        assertEquals(27 to 3, EndScore.total(s, listOf(0, 1, 2)))                    // 10+9+8
        assertEquals(0 to 0, EndScore.total(s, listOf(4, 5, 6)))
    }

    @Test
    fun endTextShowsDurationAndRest() {
        val a = (0 until 4).map { sc(10_000L + it * 17_000L, null, 0.5f) }
        val b = (0 until 4).map { sc(a.last().relMs + 100_000 + it * 17_000L, null, 0.5f) }
        val s = sess(a + b)
        val ends = endsFor(s, true, 6)
        val first = endText(s, ends[0], null)
        val second = endText(s, ends[1], ends[0])
        assertTrue(first.contains("用時 51 秒"))        // 3 個間隔 × 17 秒
        assertTrue(!first.contains("休息"))
        assertTrue(second.contains("休息 1 分 40 秒"))
        val one = endText(sess(listOf(sc(1000, null, 0.5f))), computeEnds(sess(listOf(sc(1000, null, 0.5f))), 6)[0], null)
        assertTrue(!one.contains("用時"))               // 一箭沒有用時
    }

    @Test
    fun rateWarnOnlyWhenClearlyBelowRequest() {
        assertTrue(RateWarn.low(50f, 100))
        assertTrue(RateWarn.low(84f, 100))
        assertTrue(!RateWarn.low(85f, 100))
        assertTrue(!RateWarn.low(99f, 100))
        assertTrue(!RateWarn.low(0f, 100))      // 還沒量到
        assertTrue(!RateWarn.low(50f, 0))
    }

    @Test
    fun positionTextIsPlainAndExplained() {
        val t = PositionTrend.rowText(PosRow(2, 12, 0.46f, 4.3f, 2.4f), true)
        assertTrue(t.contains("每趟第 2 箭") && t.contains("12 支") && t.contains("0.46") && t.contains("4.3") && t.contains("2.4"))
        assertTrue(PositionTrend.explain().isNotEmpty())
    }

    @Test
    fun newSettingsDefaultOnAndReset() {
        val s = AppSettings(FakePrefs())
        assertTrue(s.scoreShake.value && s.endChart.value && s.rateWarn.value)
        s.setScoreShake(false); s.setEndChart(false); s.setRateWarn(false)
        s.resetDefaults()
        assertTrue(s.scoreShake.value && s.endChart.value && s.rateWarn.value)
    }
}
