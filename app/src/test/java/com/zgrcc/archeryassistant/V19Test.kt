package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V19Test {

    // ---------- 語言 ----------
    @Test
    fun languageResolve() {
        assertTrue(I18n.resolveEnglish(I18n.EN, "zh"))
        assertFalse(I18n.resolveEnglish(I18n.ZH, "en"))
        assertFalse(I18n.resolveEnglish(I18n.AUTO, "zh"))
        assertFalse(I18n.resolveEnglish(I18n.AUTO, "ZH"))
        assertTrue(I18n.resolveEnglish(I18n.AUTO, "en"))
        assertTrue(I18n.resolveEnglish(I18n.AUTO, "ja"))
    }

    @Test
    fun trPicksByLanguage() {
        I18n.english = false
        assertEquals("你好", tr("你好", "Hello"))
        I18n.english = true
        assertEquals("Hello", tr("你好", "Hello"))
        I18n.english = false
    }

    // ---------- 取樣頻率 ----------
    @Test
    fun samplingRateOptions() {
        assertEquals(20_000, SamplingRate.periodUs(50))
        assertEquals(10_000, SamplingRate.periodUs(100))
        assertEquals(5_000, SamplingRate.periodUs(200))
        assertEquals(100, SamplingRate.valid(77))
        assertEquals(100, SamplingRate.next(50))
        assertEquals(200, SamplingRate.next(100))
        assertEquals(50, SamplingRate.next(200))
        assertTrue(SamplingRate.trackerCapacity(200) > SamplingRate.trackerCapacity(100))
        assertTrue(SamplingRate.trackerCapacity(100) >= 1024)
    }

    @Test
    fun rateMeterMeasuresHz() {
        val m = RateMeter(unitsPerSecond = 1000L)
        // 100 Hz：每 10 ms 一筆，跑 3 秒
        var t = 0L
        repeat(301) { m.onSample(t); t += 10 }
        assertEquals(100f, m.hz, 1.5f)
        m.reset()
        assertEquals(0f, m.hz, 0f)
    }

    // ---------- 疲勞提醒 ----------
    private fun end(n: Int, accel: Float?) =
        EndStats(n, 1, 1, 1, 0, 0, accel, null, null, null)

    @Test
    fun fatigueNeedsEnoughEnds() {
        val d = FatigueRule.decide(listOf(end(1, 1f), end(2, 1f), end(3, 5f)), 25, false)
        assertFalse(d.warnNow)
        assertNull(d.pct)
    }

    @Test
    fun fatigueWarnsOnceThenRearms() {
        val shaky = listOf(end(1, 1f), end(2, 1f), end(3, 1.5f), end(4, 1.5f))
        val d1 = FatigueRule.decide(shaky, 25, false)
        assertTrue(d1.warnNow)
        assertEquals(50f, d1.pct!!, 0.01f)
        val d2 = FatigueRule.decide(shaky, 25, d1.warned)
        assertFalse(d2.warnNow)
        assertTrue(d2.warned)
        // 回穩：低於門檻一半才重新允許
        val calm = listOf(end(1, 1f), end(2, 1f), end(3, 1.05f), end(4, 1.05f))
        val d3 = FatigueRule.decide(calm, 25, true)
        assertFalse(d3.warnNow)
        assertFalse(d3.warned)
        val d4 = FatigueRule.decide(shaky, 25, d3.warned)
        assertTrue(d4.warnNow)
    }

    @Test
    fun fatigueIgnoresMissingData() {
        assertNull(FatigueRule.changePct(listOf(end(1, null), end(2, null), end(3, 1f), end(4, 1f))))
        assertNull(FatigueRule.changePct(listOf(end(1, 0f), end(2, 0f), end(3, 1f), end(4, 1f))))
    }

    @Test
    fun fatigueSteadierIsNegative() {
        val p = FatigueRule.changePct(listOf(end(1, 2f), end(2, 2f), end(3, 1f), end(4, 1f)))!!
        assertEquals(-50f, p, 0.01f)
    }

    // ---------- 比賽模式 ----------
    private fun round(
        prepare: Int = 0, practice: Int = 0, ends: Int = 2, arrows: Int = 3, sec: Int = 120,
        auto: Int = 0, r30: Boolean = true, r10: Boolean = true,
    ) = CompetitionRound(CompFormat(CompFormat.CUSTOM, arrows, ends, sec), practice, prepare, r30, r10, auto)

    @Test
    fun presets() {
        val custom = CompFormat(CompFormat.CUSTOM, 5, 7, 99)
        assertEquals(CompFormat(0, 3, 20, 120), CompFormat.preset(CompFormat.INDOOR_18, custom))
        assertEquals(CompFormat(1, 6, 12, 240), CompFormat.preset(CompFormat.OUTDOOR_70, custom))
        assertEquals(5, CompFormat.preset(CompFormat.CUSTOM, custom).arrowsPerEnd)
    }

    @Test
    fun prepareCountdownThenGo() {
        val r = round(prepare = 10)
        assertTrue(r.start(0).isEmpty())
        assertEquals(CompPhase.PREPARE, r.phase)
        assertEquals(4000L, r.remainingMs(6000))
        assertTrue(r.tick(9_999).isEmpty())
        assertEquals(listOf(CompEvent.GO), r.tick(10_000))
        assertEquals(CompPhase.SHOOTING, r.phase)
        assertEquals(120_000L, r.remainingMs(10_000))
    }

    @Test
    fun noPrepareStartsShootingAtOnce() {
        val r = round(prepare = 0)
        assertEquals(listOf(CompEvent.GO), r.start(500))
        assertEquals(CompPhase.SHOOTING, r.phase)
    }

    @Test
    fun endFinishesWhenArrowsFull() {
        val r = round()
        r.start(0)
        assertTrue(r.onShot(10_000).isEmpty())
        assertTrue(r.onShot(20_000).isEmpty())
        assertEquals(listOf(CompEvent.END_FULL), r.onShot(30_000))
        assertEquals(CompPhase.REST, r.phase)
        val e = r.results.single()
        assertEquals(3, e.arrows)
        assertFalse(e.timedOut)
        assertEquals(30_000L, e.endMs)
    }

    @Test
    fun timeUpClosesEnd() {
        val r = round()
        r.start(0)
        r.onShot(10_000)
        assertEquals(listOf(CompEvent.TIME_UP), r.tick(120_000))
        assertEquals(CompPhase.REST, r.phase)
        val e = r.results.single()
        assertTrue(e.timedOut)
        assertEquals(1, e.arrows)
        assertEquals(120_000L, e.endMs)
        // 時間到後又射一箭 = 逾時箭
        r.onShot(125_000)
        assertEquals(1, r.results.single().lateArrows)
    }

    @Test
    fun remindersFireOnceEach() {
        val r = round()
        r.start(0)
        assertTrue(r.tick(80_000).isEmpty())
        assertEquals(listOf(CompEvent.REMIND_30), r.tick(90_000))
        assertTrue(r.tick(95_000).isEmpty())
        assertEquals(listOf(CompEvent.REMIND_10), r.tick(110_000))
        assertTrue(r.tick(115_000).isEmpty())
    }

    @Test
    fun remindersCanBeDisabledAndShortEndsSkipThem() {
        val off = round(r30 = false, r10 = false)
        off.start(0)
        assertTrue(off.tick(95_000).isEmpty())
        assertTrue(off.tick(115_000).isEmpty())
        val short = round(sec = 30)
        short.start(0)
        // 總長 30 秒：不需要「剩 30 秒」提醒
        assertTrue(short.tick(1_000).isEmpty())
        assertEquals(listOf(CompEvent.REMIND_10), short.tick(21_000))
    }

    @Test
    fun nextEndIsManualByDefault() {
        val r = round()
        r.start(0)
        repeat(3) { r.onShot(10_000L * (it + 1)) }
        assertTrue(r.tick(500_000).isEmpty()) // 不會自己開始
        assertEquals(CompPhase.REST, r.phase)
        assertEquals(listOf(CompEvent.GO), r.next(600_000))
        assertEquals(2, r.endNumber)
        assertEquals(0, r.arrowsInEnd)
    }

    @Test
    fun autoNextStartsAfterRest() {
        val r = round(auto = 60)
        r.start(0)
        repeat(3) { r.onShot(1000L * (it + 1)) }
        assertEquals(60_000L, r.remainingMs(3_000))
        assertTrue(r.tick(62_000).isEmpty())
        assertEquals(listOf(CompEvent.GO), r.tick(63_000))
        assertEquals(2, r.endNumber)
    }

    @Test
    fun matchDoneAfterLastEnd() {
        val r = round(ends = 1)
        r.start(0)
        repeat(2) { r.onShot(1000L * (it + 1)) }
        val ev = r.onShot(3000)
        assertEquals(listOf(CompEvent.END_FULL, CompEvent.MATCH_DONE), ev)
        assertEquals(CompPhase.DONE, r.phase)
        assertTrue(r.next(4000).isEmpty())
    }

    @Test
    fun practiceEndsComeFirst() {
        val r = round(practice = 2, ends = 1)
        r.start(0)
        assertTrue(r.isPractice)
        assertEquals(1, r.displayNumber)
        repeat(3) { r.onShot(1000L * (it + 1)) }
        r.next(10_000)
        assertTrue(r.isPractice)
        assertEquals(2, r.displayNumber)
        repeat(3) { r.onShot(11_000L + 1000L * it) }
        r.next(30_000)
        assertFalse(r.isPractice)
        assertEquals(1, r.displayNumber)
        assertEquals(3, r.totalEnds)
        assertEquals(listOf(true, true), r.results.map { it.practice })
    }

    @Test
    fun cancelLastRemovesAnArrow() {
        val r = round()
        r.start(0)
        r.onShot(1000)
        r.onShot(2000)
        r.cancelLast()
        assertEquals(1, r.arrowsInEnd)
        assertTrue(r.onShot(3000).isEmpty())
        assertEquals(CompPhase.SHOOTING, r.phase)
    }

    @Test
    fun finishNowKeepsPartialEnd() {
        val r = round()
        r.start(0)
        r.onShot(1000)
        r.finishNow(5000)
        assertEquals(CompPhase.DONE, r.phase)
        assertEquals(1, r.results.single().arrows)
    }

    @Test
    fun shotsDuringPrepareAreIgnored() {
        val r = round(prepare = 10)
        r.start(0)
        assertTrue(r.onShot(1000).isEmpty())
        assertEquals(0, r.arrowsInEnd)
    }

    // ---------- 比賽紀錄的存檔與統計 ----------
    private fun shot(rel: Long, accel: Float, score: Int? = null) =
        ShotEntry(relMs = rel, peak = 20f, threshold = 16f, accelRms = accel, gyroRms = null, score = score)

    private fun compSession() = Session(
        id = "x", startMs = 0, endMs = 500_000, threshold = 16f, windowSec = 2, hasGyro = false, rawCsv = false,
        shots = listOf(
            shot(10_000, 1f), shot(20_000, 1f), shot(30_000, 1f, 9),          // 練習趟 P1 滿箭
            shot(100_000, 2f, 10), shot(150_000, 2f, 8),                        // 正式 1：時間到只有 2 箭
            shot(185_000, 2f),                                                  // 逾時箭
            shot(300_000, 3f, 7), shot(310_000, 3f, 7), shot(320_000, 3f, 7),   // 正式 2 滿箭
        ),
        manual = emptyList(),
        marks = listOf(
            Mark("P1", 0, 30_000),
            Mark("1", 60_000, 180_000),
            Mark("2", 250_000, 320_000),
        ),
        comp = CompMeta(secondsPerEnd = 120, arrowsPerEnd = 3, practiceEnds = 1),
    )

    @Test
    fun compMetaRoundTripsThroughJson() {
        val s = compSession()
        val back = SessionJson.decode(SessionJson.encode(s), "x")!!
        assertEquals(s.comp, back.comp)
        assertEquals(3, back.marks.size)
    }

    @Test
    fun oldSessionsHaveNoComp() {
        val s = compSession().copy(comp = null)
        assertNull(SessionJson.decode(SessionJson.encode(s), "x")!!.comp)
        assertNull(CompSummaryBuilder.build(s))
    }

    @Test
    fun summaryCountsFormalEndsOnly() {
        val sum = CompSummaryBuilder.build(compSession())!!
        assertEquals(3, sum.ends.size)
        assertEquals(5, sum.totalArrows)          // 2 + 3，不含練習趟
        assertEquals(1, sum.lateArrows)
        assertEquals(1, sum.timedOutEnds)
        assertEquals(10 + 8 + 21, sum.scoreTotal)
        assertTrue(sum.ends[0].practice)
        assertTrue(sum.ends[1].timedOut)
        assertEquals(1, sum.ends[1].lateArrows)
        assertFalse(sum.ends[2].timedOut)
        assertEquals(70_000L, sum.ends[2].usedMs)
        assertEquals(2f, sum.ends[1].accelAvg!!, 0.001f)
    }

    @Test
    fun summaryScoreNullWhenNoScores() {
        val s = compSession().copy(shots = compSession().shots.map { it.copy(score = null) })
        assertNotNull(CompSummaryBuilder.build(s))
        assertNull(CompSummaryBuilder.build(s)!!.scoreTotal)
    }

    @Test
    fun recorderStoresCompMarks() {
        // 只驗證 Session 的組合；錄製流程在 SessionRecorderTest 已涵蓋
        val s = compSession()
        assertEquals("P1", s.marks.first().label)
        assertEquals(CompMeta(120, 3, 1), s.comp)
    }
}
