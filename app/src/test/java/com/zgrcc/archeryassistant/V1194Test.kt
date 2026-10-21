package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class V1194Test {
    private fun shot(relMs: Long, accel: Float = 0.5f, follow: Float? = 2.5f, cancelled: Boolean = false) =
        ShotEntry(relMs, 30f, 18f, accel, 5f, followAccelRms = follow, cancelled = cancelled)

    private fun sess(shots: List<ShotEntry>, id: String = "s", start: Long = 0, cal: Boolean = false, tags: List<String> = emptyList()) =
        Session(id, start, start + 3_600_000, 18f, 2, true, false, shots, emptyList(), tags = tags, calibration = cal)

    /** 每 17 秒一箭 */
    private fun regular(n: Int, accel: (Int) -> Float = { 0.5f }) = (0 until n).map { shot(10_000L + it * 17_000L, accel(it)) }

    @Test
    fun fastShotWithNoFollowThroughIsFlagged() {
        val base = regular(8)
        val bad = shot(base.last().relMs + 6_500, follow = 0.7f)
        val s = sess(base + bad)
        assertEquals(setOf(8), FastShot.flagged(s))
    }

    @Test
    fun normalSessionHasNoFlags() {
        assertEquals(emptySet<Int>(), FastShot.flagged(sess(regular(20))))
    }

    @Test
    fun closeShotButNormalFollowThroughIsNotFlagged() {
        val base = regular(8)
        val quick = shot(base.last().relMs + 6_500, follow = 2.4f)
        assertEquals(emptySet<Int>(), FastShot.flagged(sess(base + quick)))
    }

    @Test
    fun withoutFollowDataOnlyVeryShortGapIsFlagged() {
        val base = (0 until 8).map { shot(10_000L + it * 17_000L, follow = null) }
        val last = base.last().relMs
        assertEquals(setOf(8), FastShot.flagged(sess(base + shot(last + 5_000, follow = null))))
        assertEquals(emptySet<Int>(), FastShot.flagged(sess(base + shot(last + 8_000, follow = null))))
    }

    @Test
    fun tooFewShotsOrCalibrationNotJudged() {
        assertEquals(emptySet<Int>(), FastShot.flagged(sess(listOf(shot(1000), shot(2000, follow = 0.1f)))))
        val base = regular(8)
        assertEquals(emptySet<Int>(), FastShot.flagged(sess(base + shot(base.last().relMs + 3000, follow = 0.1f), cal = true)))
    }

    @Test
    fun cancelledShotsAreIgnoredAndIndexIsInSessionList() {
        val base = regular(8)
        val withCancelled = base + shot(base.last().relMs + 2_000, cancelled = true) + shot(base.last().relMs + 6_500, follow = 0.7f)
        assertEquals(setOf(9), FastShot.flagged(sess(withCancelled)))
    }

    @Test
    fun longRestGapsAreNotCountedAsGaps() {
        // 兩趟各 6 箭，趟間休息 120 秒：不該把休息當成間隔，也不會標記任何箭
        val a = regular(6)
        val b = (0 until 6).map { shot(a.last().relMs + 120_000 + it * 17_000L) }
        assertEquals(emptySet<Int>(), FastShot.flagged(sess(a + b)))
    }

    @Test
    fun positionRowsRestartAtEachEnd() {
        val a = (0 until 8).map { shot(10_000L + it * 17_000L, accel = 0.4f + it * 0.04f) }
        val b = (0 until 8).map { shot(a.last().relMs + 120_000 + it * 17_000L, accel = 0.4f + it * 0.04f) }
        val c = (0 until 8).map { shot(b.last().relMs + 120_000 + it * 17_000L, accel = 0.4f + it * 0.04f) }
        val rows = PositionTrend.rows(listOf(sess(a + b + c)))
        assertEquals(8, rows.size)
        assertEquals(3, rows[0].n)
        assertEquals(0.4f, rows[0].accel!!, 0.001f)
        assertEquals(0.68f, rows[7].accel!!, 0.001f)
        assertTrue(PositionTrend.hint(rows) != null)
    }

    @Test
    fun positionRowsDropThinPositionsAndFlatHasNoHint() {
        val a = regular(6)
        val b = (0 until 8).map { shot(a.last().relMs + 120_000 + it * 17_000L) }
        val c = (0 until 8).map { shot(b.last().relMs + 120_000 + it * 17_000L) }
        val rows = PositionTrend.rows(listOf(sess(a + b + c)))
        assertEquals(listOf(1, 2, 3, 4, 5, 6), rows.map { it.pos })
        assertEquals(6, rows.size) // 第 7、8 箭只有 2 筆，不列
        assertEquals(null, PositionTrend.hint(rows))
        assertEquals(emptyList<PosRow>(), PositionTrend.rows(listOf(sess(regular(10), cal = true))))
    }

    @Test
    fun measuredHzIsAppendedOnce() {
        val det = RecordMeta.detect(true, 100, 18f, 2)
        val w = RecordMeta.withMeasured(det, 49.6f)
        assertEquals("adv=on;hz=100;thr=18.0;win=2s;real=50", w)
        assertEquals(w, RecordMeta.withMeasured(w, 100f))
        assertEquals(det, RecordMeta.withMeasured(det, 0f))
        assertEquals("", RecordMeta.withMeasured("", 50f))
        assertTrue(RecordMeta.detectText(w).contains("100 Hz"))
        assertTrue(RecordMeta.detectText(w).contains("50 Hz"))
        assertEquals(RecordMeta.detectText(det).contains("實測"), false)
        val same = RecordMeta.withMeasured(RecordMeta.detect(true, 50, 18f, 2), 50.2f)
        assertEquals(false, RecordMeta.detectText(same).contains("實測"))
    }

    @Test
    fun sessionExtrasPaceAndFollowTrends() {
        val s1 = sess(regular(8), id = "a", start = 1_000)
        val s2 = sess((0 until 8).map { shot(10_000L + it * 14_000L, follow = 3.5f) }, id = "b", start = 2_000)
        val s3 = sess(regular(8), id = "c", start = 3_000, cal = true)
        val pace = SessionExtras.paceTrend(listOf(s2, s1, s3))
        assertEquals(2, pace.size)
        assertEquals(17f, pace[0].value, 0.01f)
        assertEquals(14f, pace[1].value, 0.01f)
        val fol = SessionExtras.followTrend(listOf(s1, s2))
        assertEquals(listOf(2.5f, 3.5f), fol.map { it.value })
        assertEquals(null, SessionExtras.medianGapSec(sess(regular(3))))
    }

    @Test
    fun newSettingsDefaultOn() {
        val s = AppSettings(FakePrefs())
        assertTrue(s.recordMeasuredHz.value && s.flagFastShots.value && s.positionTrend.value && s.trendExtra.value)
        s.setFlagFastShots(false); s.setTrendExtra(false)
        s.resetDefaults()
        assertTrue(s.flagFastShots.value && s.trendExtra.value)
    }
}
