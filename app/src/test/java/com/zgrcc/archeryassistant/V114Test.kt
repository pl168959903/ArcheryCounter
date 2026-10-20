package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class V114Test {
    private val utc = ZoneId.of("UTC")

    private fun shot(rel: Long, accel: Float? = 1f, score: Int? = null, cancelled: Boolean = false, peak: Float = 20f, feel: Int? = null) =
        ShotEntry(rel, peak, 16f, accel, null, score = score, cancelled = cancelled, feel = feel)

    private fun sess(
        id: String = "s", start: Long = 0, shots: List<ShotEntry> = emptyList(), tags: List<String> = emptyList(),
        cal: Boolean = false, comp: CompMeta? = null, marks: List<Mark> = emptyList(), end: Long? = start + 1000,
        raw: Boolean = false, manual: List<ManualEntry> = emptyList(),
    ) = Session(id, start, end, 16f, 2, false, raw, shots, manual, tags, cal, marks, comp)

    // ---------- 比賽節奏 ----------
    private fun matchSession() = sess(
        comp = CompMeta(120, 3, 1),
        marks = listOf(Mark("P1", 0, 20_000), Mark("1", 60_000, 150_000), Mark("2", 250_000, 360_000), Mark("3", 400_000, 450_000)),
        shots = listOf(
            shot(10_000),                                         // 練習，不算
            shot(75_000), shot(120_000), shot(150_000),           // 正式1：等 15 秒；最後一箭 150 → 剩 30 秒
            shot(260_000), shot(300_000), shot(355_000),          // 正式2：等 10 秒；最後一箭 355 → 剩 15 秒（限 120 秒，期限 370）
            // 正式3 沒有箭
        ),
    )

    @Test
    fun paceAnalysis() {
        val p = PaceAnalyzer.analyze(matchSession())!!
        assertEquals(3, p.ends.size)
        assertEquals(15f, p.ends[0].firstWaitSec!!, 0.001f)
        assertEquals(30f, p.ends[0].lastRemainSec!!, 0.001f)
        assertEquals(10f, p.ends[1].firstWaitSec!!, 0.001f)
        assertEquals(15f, p.ends[1].lastRemainSec!!, 0.001f)
        assertNull(p.ends[2].firstWaitSec)
        assertEquals(12.5f, p.firstWaitAvg!!, 0.001f)
        assertEquals(15f, p.firstWaitMax!!, 0.001f)
        assertEquals("1", p.slowestEnd)
        assertEquals("2", p.tightestEnd)
        assertEquals(15f, p.lastRemainMin!!, 0.001f)
        assertEquals(0, p.tightEnds)                              // 15 秒 > 10 秒
        assertEquals(110f, p.usedMax!!, 0.001f)
    }

    @Test
    fun paceTightEndsAndNonMatch() {
        val s = matchSession().let { it.copy(shots = it.shots.map { sh -> if (sh.relMs == 355_000L) sh.copy(relMs = 366_000L) else sh }) }
        val p = PaceAnalyzer.analyze(s.copy(marks = s.marks.map { if (it.label == "2") it.copy(endRelMs = 366_000) else it }))!!
        assertEquals(1, p.tightEnds)                              // 期限 370，366 放箭 → 剩 4 秒
        assertNull(PaceAnalyzer.analyze(sess()))                  // 不是比賽
        assertNull(PaceAnalyzer.analyze(matchSession().copy(marks = listOf(Mark("P1", 0, 1000)))))
    }

    @Test
    fun matchRowCarriesFirstWait() {
        val r = MatchCompare.rows(listOf(matchSession())).single()
        assertEquals(12.5f, r.firstWaitAvg!!, 0.001f)
    }

    // ---------- 條件分組 ----------
    @Test
    fun groupsByTagsIgnoringMatchTag() {
        val a = sess("a", 1, listOf(shot(1, 1f, 9), shot(2, 1f, 8)), listOf("室外70米", "反曲"))
        val b = sess("b", 2, listOf(shot(1, 3f)), listOf("室外70米", "反曲", Tags.COMPETITION))
        val c = sess("c", 3, listOf(shot(1, 2f)), listOf("室內18米"))
        val d = sess("d", 4, listOf(shot(1, 5f)))
        val e = sess("e", 5, listOf(shot(1, 9f)), cal = true)
        val f = sess("f", 6, emptyList(), listOf("複合"))
        val rows = GroupCompare.groups(listOf(a, b, c, d, e, f))
        assertEquals(3, rows.size)                                 // 校準、沒有箭的不算
        val g = rows.first()
        assertEquals(listOf("室外70米", "反曲"), g.tags)
        assertEquals(2, g.sessions)
        assertEquals(3, g.arrows)
        assertEquals((1f + 1f + 3f) / 3f, g.accelAvg!!, 0.001f)
        assertEquals(8.5f, g.scoreAvg!!, 0.001f)
        I18n.english = false
        assertEquals("未設定", GroupCompare.title(emptyList()))
    }

    @Test
    fun steadiestNeedsTwoGroupsWithEnoughArrows() {
        fun row(tags: List<String>, arrows: Int, avg: Float?) = GroupRow(tags, 2, arrows, avg, null)
        val r1 = row(listOf("a"), 10, 2f)
        val r2 = row(listOf("b"), 10, 1f)
        val r3 = row(listOf("c"), 3, 0.1f)                         // 箭太少不參與
        assertEquals(listOf("b"), GroupCompare.steadiest(listOf(r1, r2, r3))!!.tags)
        assertNull(GroupCompare.steadiest(listOf(r1, r3)))         // 只剩一組可比
        assertNull(GroupCompare.steadiest(emptyList()))
    }

    // ---------- 個人最佳 ----------
    private fun accelSession(id: String, avg: Float, n: Int = 6) = sess(id, 1, List(n) { shot(it * 1000L + 1, avg) })

    @Test
    fun steadiestSession() {
        val list = listOf(accelSession("a", 2f), accelSession("b", 1f), accelSession("c", 3f))
        assertEquals("b", PersonalBest.steadiestId(list))
        assertNull(PersonalBest.steadiestId(list.take(2)))                           // 不夠比
        assertEquals("b", PersonalBest.steadiestId(list + accelSession("d", 0.1f, n = 3)))   // 箭太少的不參與
        assertEquals("b", PersonalBest.steadiestId(list + accelSession("e", 0.1f).copy(calibration = true)))
        assertEquals("b", PersonalBest.steadiestId(list + accelSession("f", 0.1f).copy(endMs = null)))
    }

    private fun row(id: String, apn: Int = 3, ends: Int = 20, sec: Int = 120, score: Int? = null, accel: Float? = null) =
        MatchRow(id, 0, apn, sec, ends, 60, score, accel, 0)

    @Test
    fun bestMatchOnlyWithinSameFormat() {
        val rows = listOf(
            row("a", score = 500), row("b", score = 520), row("c", score = 520),
            row("x", ends = 12, score = 999),                       // 另一種賽制，只有一場
        )
        assertEquals(setOf("b", "c"), PersonalBest.bestScoreIds(rows))
        assertTrue(PersonalBest.bestScoreIds(listOf(row("a", score = 1))).isEmpty())
        val sh = listOf(row("a", accel = 2f), row("b", accel = 1f), row("c"))
        assertEquals(setOf("b"), PersonalBest.steadiestMatchIds(sh))
    }

    // ---------- 訓練總覽 ----------
    private fun ms(date: String) = LocalDate.parse(date).atTime(12, 0).atZone(utc).toInstant().toEpochMilli()
    private fun day(date: String, n: Int, cal: Boolean = false) =
        sess("d$date$n$cal", ms(date), List(n) { shot(it + 1L) }, cal = cal)

    @Test
    fun weeksStartOnMonday() {
        // 2026-10-20 是週二；本週一 = 10/19，上週一 = 10/12
        val list = listOf(day("2026-10-19", 30), day("2026-10-20", 20), day("2026-10-12", 10), day("2026-10-18", 5), day("2026-10-20", 99, cal = true))
        val bars = Overview.weeks(list, ms("2026-10-20"), utc, count = 3)
        assertEquals(3, bars.size)
        assertEquals(LocalDate.parse("2026-10-05"), bars[0].start)
        assertEquals(0, bars[0].arrows)
        assertEquals(15, bars[1].arrows)                           // 10/12 的 10 + 10/18（週日）的 5
        assertEquals(2, bars[1].days)
        assertEquals(50, bars[2].arrows)
        assertEquals(2, bars[2].days)
        assertEquals(35, Overview.weekDelta(bars))
        assertNull(Overview.weekDelta(bars.take(1)))
    }

    @Test
    fun monthsAndStreak() {
        val list = listOf(day("2026-10-20", 10), day("2026-10-19", 10), day("2026-10-18", 10), day("2026-10-16", 10), day("2026-09-30", 7))
        val m = Overview.months(list, ms("2026-10-20"), utc, count = 2)
        assertEquals(LocalDate.parse("2026-09-01"), m[0].start)
        assertEquals(7, m[0].arrows)
        assertEquals(40, m[1].arrows)
        assertEquals(4, m[1].days)
        assertEquals(3, Overview.streak(list, ms("2026-10-20"), utc))            // 20、19、18，16 斷了
        assertEquals(0, Overview.streak(emptyList(), ms("2026-10-20"), utc))
    }

    @Test
    fun streakCountsFromYesterdayWhenNothingToday() {
        val list = listOf(day("2026-10-19", 10), day("2026-10-18", 10))
        assertEquals(2, Overview.streak(list, ms("2026-10-20"), utc))
        assertEquals(0, Overview.streak(list, ms("2026-10-22"), utc))
    }

    @Test
    fun emptyDaysAreIgnored() {
        val s = sess("z", ms("2026-10-20"), emptyList())
        assertTrue(Overview.perDay(listOf(s), utc).isEmpty())
    }

    // ---------- 自動清理 ----------
    @Test
    fun cleanNeedsAndStrip() {
        val wave = Waveform(40, 0, listOf(1f), emptyList())
        val tr = Trace2D(40, false, listOf(1f), listOf(1f))
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        val cutoff = AutoClean.cutoff(now, 60)
        assertEquals(now - 60 * day, cutoff)
        val old = sess("o", 10 * day, listOf(ShotEntry(1, 20f, 16f, 1f, null, waveform = wave, trace = tr, feel = 3)), raw = true)
        assertTrue(AutoClean.needsClean(old, cutoff))
        val stripped = AutoClean.strip(old)
        assertFalse(stripped.rawCsv)
        assertNull(stripped.shots[0].waveform)
        assertNull(stripped.shots[0].trace)
        assertEquals(3, stripped.shots[0].feel)                    // 自評保留
        assertFalse(AutoClean.needsClean(stripped, cutoff))        // 清過就不用再清
        assertFalse(AutoClean.needsClean(old.copy(startMs = 90 * day), cutoff))   // 太新
        assertFalse(AutoClean.needsClean(old.copy(calibration = true), cutoff))   // 校準永遠不清
        assertFalse(AutoClean.needsClean(old.copy(endMs = null), cutoff))         // 進行中
        assertFalse(AutoClean.needsClean(old.copy(rawCsv = false, shots = listOf(shot(1))), cutoff))   // 沒東西可清
    }

    @Test
    fun cleanDaysCycleAndEncode() {
        assertEquals(60, AutoClean.nextDays(30))
        assertEquals(90, AutoClean.nextDays(60))
        assertEquals(30, AutoClean.nextDays(90))
        assertEquals(60, AutoClean.validDays(7))
        val r = CleanResult(3, 1234, 99)
        assertEquals(r, AutoClean.decode(AutoClean.encode(r)))
        assertNull(AutoClean.decode("x,1,2"))
        assertNull(AutoClean.decode(null))
        assertNull(AutoClean.decode("1,2"))
        I18n.english = false
        assertTrue(AutoClean.describe(null) { "$it B" }.contains("還沒有"))
    }

    // ---------- 每箭自評 ----------
    @Test
    fun feelToggleAndValid() {
        assertEquals(3, Feel.toggle(null, 3))
        assertNull(Feel.toggle(3, 3))
        assertEquals(1, Feel.toggle(3, 1))
        assertNull(Feel.valid(0))
        assertNull(Feel.valid(4))
        assertEquals(2, Feel.valid(2))
        I18n.english = false
        assertEquals("好", Feel.label(Feel.GOOD))
    }

    @Test
    fun feelStatsAndJson() {
        val s = sess("f", 0, listOf(
            shot(1, 1f, feel = 3), shot(2, 3f, feel = 3), shot(3, 5f, feel = 1),
            shot(4, 9f, feel = 3, cancelled = true), shot(5, 2f),
        ))
        val g = FeelStats.groups(s)
        assertEquals(listOf(Feel.GOOD, Feel.BAD), g.map { it.feel })
        assertEquals(2, g[0].count)
        assertEquals(2f, g[0].accelAvg!!, 0.001f)
        assertEquals(5f, g[1].accelAvg!!, 0.001f)
        assertTrue(FeelStats.groups(sess()).isEmpty())
        val back = SessionJson.decode(SessionJson.encode(s), "f")!!
        assertEquals(listOf(3, 3, 1, 3, null), back.shots.map { it.feel })
    }

    @Test
    fun recorderSetFeel() {
        val dir = java.io.File.createTempFile("v114", "").apply { delete(); mkdirs() }
        val rec = SessionRecorder(SessionStore(dir))
        rec.start(1_000_000L, 16f, 2, false, false)
        rec.addShot(1000, 20f, 16f, 1f, null)
        rec.setFeel(0, 3)
        assertEquals(3, rec.snapshot()!!.shots[0].feel)
        rec.setFeel(0, 9)
        assertNull(rec.snapshot()!!.shots[0].feel)
        rec.setFeel(5, 1)                                          // 不存在的位置
        rec.stop(2_000_000L)
        dir.deleteRecursively()
    }

    // ---------- 校準分析 ----------
    private val draw = Calibration.STEPS[0].label
    private val normal = Calibration.STEPS[1].label
    private val walk = Calibration.STEPS[2].label

    /** 100 Hz 的樣本：[quiet] 平時合力，[spikes] 指定時間點的尖峰 */
    private fun samples(totalMs: Long, quiet: Float, spikes: Map<Long, Float> = emptyMap()): List<MagSample> =
        (1..(totalMs / 10)).map { i -> MagSample(i * 10, spikes[i * 10] ?: quiet) }

    private val marks = listOf(Mark(draw, 0, 10_000), Mark(normal, 10_000, 30_000), Mark(walk, 30_000, 40_000))

    @Test
    fun parseCsvSkipsHeaderAndGarbage() {
        val lines = sequenceOf("t_ms,ax,ay,az,a_mag,gx,gy,gz", "10,1,2,3,3.74,0,0,0", "abc,1,2,3,4,0,0,0", "20,1,2,3", "30,1,2,3,x,0,0,0", "40,1,2,3,5.5,0,0,0")
        val s = CalibrationAnalyzer.parseCsv(lines)
        assertEquals(listOf(10L, 40L), s.map { it.relMs })
        assertEquals(5.5f, s[1].mag, 0.0001f)
    }

    @Test
    fun calibrationSuggestsBetweenCeilingAndLowestShot() {
        // 非放箭動作最大約 8；正常射擊 5 箭峰值 20～30；放箭前很安靜（約 2）
        val spikes = mapOf(3_000L to 8f, 35_000L to 8f)
        val shots = (0..4).map { shot(12_000L + it * 3_000L, peak = 20f + it * 2.5f) }
        val a = CalibrationAnalyzer.analyze(marks, shots, samples(40_000, 2f, spikes))
        assertEquals(CalibKind.OK, a.kind)
        assertEquals(2f, a.nonShotCeil!!, 0.001f)                   // 99 百分位忽略單點尖峰
        assertEquals(20f, a.shotLow!!, 0.001f)
        assertEquals(11, a.threshold)                               // (2+20)/2
        assertEquals(3, a.still)                                    // ceil(2×1.1) = 3
        assertFalse(a.overlap)
    }

    @Test
    fun calibrationOverlapMeansNoThreshold() {
        val noisy = samples(40_000, 19f)                            // 非放箭動作一直很大
        val shots = (0..4).map { shot(12_000L + it * 3_000L, peak = 20f) }
        val a = CalibrationAnalyzer.analyze(marks, shots, noisy)
        assertEquals(CalibKind.OK, a.kind)
        assertNull(a.threshold)
        assertTrue(a.overlap)
    }

    @Test
    fun calibrationNeedsDataAndShots() {
        assertEquals(CalibKind.NO_DATA, CalibrationAnalyzer.analyze(marks, emptyList(), emptyList()).kind)
        assertEquals(CalibKind.NO_DATA, CalibrationAnalyzer.analyze(marks.take(2), emptyList(), samples(40_000, 2f)).kind)
        val few = (0..2).map { shot(12_000L + it * 3_000L, peak = 25f) }
        val a = CalibrationAnalyzer.analyze(marks, few, samples(40_000, 2f))
        assertEquals(CalibKind.NOT_ENOUGH_SHOTS, a.kind)
        assertEquals(3, a.shotCount)
        // 取消的箭、不在正常射擊那一段的箭不算
        val mixed = (0..2).map { shot(12_000L + it * 3_000L, peak = 25f) } + shot(15_500, peak = 25f, cancelled = true) + shot(35_000, peak = 25f)
        assertEquals(CalibKind.NOT_ENOUGH_SHOTS, CalibrationAnalyzer.analyze(marks, mixed, samples(40_000, 2f)).kind)
    }

    @Test
    fun calibrationStillCoversQuietBeforeRelease() {
        // 放箭前 0.5 秒內有 6 的抖動 → 靜止條件至少要 7
        val shotsAt = (0..4).map { 12_000L + it * 3_000L }
        val spikes = shotsAt.associate { (it - 300) to 6f }
        val shots = shotsAt.map { shot(it, peak = 25f) }
        val a = CalibrationAnalyzer.analyze(marks, shots, samples(40_000, 1f, spikes))
        assertEquals(7, a.still)                                    // ceil(6×1.1)
        // 夠大的抖動被夾在上限
        val big = shotsAt.associate { (it - 300) to 30f }
        assertEquals(CalibrationAnalyzer.STILL_MAX, CalibrationAnalyzer.analyze(marks, shots, samples(40_000, 1f, big)).still)
    }
}
