package com.zgrcc.archeryassistant

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V14Test {

    private val t0 = 1_760_000_000_000L

    private fun shot(
        sec: Int,
        acc: Float? = 0.5f,
        gyr: Float? = 1f,
        follow: Float? = null,
        score: Int? = null,
        cancelled: Boolean = false,
    ) = ShotEntry(
        relMs = sec * 1000L, peak = 17f, threshold = 16f, accelRms = acc, gyroRms = gyr,
        cancelled = cancelled, followAccelRms = follow, score = score,
    )

    private fun session(shots: List<ShotEntry>, start: Long = t0, id: String = "t") = Session(
        id = id, startMs = start, endMs = start + 900_000L, threshold = 16f, windowSec = 2,
        hasGyro = true, rawCsv = false, shots = shots, manual = emptyList(),
    )

    // ---- 分趟（事后统计）----

    @Test
    fun endsSplitByArrowCount() {
        // 8 箭、每趟 3 箭，间隔都很短 → 3 + 3 + 2
        val s = session((1..8).map { shot(it * 10) })
        val ends = computeEnds(s, 3)
        assertEquals(3, ends.size)
        assertEquals(listOf(3, 3, 2), ends.map { it.count })
        assertEquals(1, ends[0].firstShot)
        assertEquals(3, ends[0].lastShot)
        assertEquals(7, ends[2].firstShot)
        assertEquals(8, ends[2].lastShot)
    }

    @Test
    fun endsSplitByLongGap() {
        // 前 2 箭，隔 5 分钟去取箭，再 2 箭；每趟 6 箭 → 靠间隔切成两趟
        val s = session(listOf(shot(10), shot(30), shot(330), shot(350)))
        val ends = computeEnds(s, 6)
        assertEquals(listOf(2, 2), ends.map { it.count })
    }

    @Test
    fun endsIgnoreCancelledShots() {
        val s = session(listOf(shot(10), shot(20, cancelled = true), shot(30), shot(40)))
        val ends = computeEnds(s, 3)
        assertEquals(1, ends.size)
        assertEquals(3, ends[0].count)
        assertEquals(3, ends[0].lastShot)
    }

    @Test
    fun endsAverageAndScoreTotal() {
        val s = session(
            listOf(
                shot(10, acc = 0.2f, follow = 1.0f, score = 9),
                shot(20, acc = 0.4f, follow = 3.0f, score = 7),
                shot(30, acc = null, follow = null, score = null),
            ),
        )
        val e = computeEnds(s, 6).single()
        assertEquals(0.3f, e.accelAvg!!, 1e-5f)
        assertEquals(2.0f, e.followAvg!!, 1e-5f)
        assertEquals(16, e.scoreTotal)
        assertNull(computeEnds(session(listOf(shot(10))), 6).single().scoreTotal)
    }

    @Test
    fun endsEmptyAndClampedArrowCount() {
        assertTrue(computeEnds(session(emptyList()), 6).isEmpty())
        assertEquals(2, computeEnds(session(listOf(shot(10), shot(20))), 0).size) // 0 当作 1
    }

    // ---- 即时分趟（满趟震动）----

    @Test
    fun endTrackerReportsFullEnd() {
        val t = EndTracker()
        assertFalse(t.onShot(10_000, 3))
        assertFalse(t.onShot(20_000, 3))
        assertTrue(t.onShot(30_000, 3))
        // 下一箭是新的一趟
        assertFalse(t.onShot(40_000, 3))
        assertFalse(t.onShot(50_000, 3))
        assertTrue(t.onShot(60_000, 3))
    }

    @Test
    fun endTrackerRestartsAfterLongGap() {
        val t = EndTracker()
        assertFalse(t.onShot(10_000, 3))
        assertFalse(t.onShot(20_000, 3))
        // 隔 2 分钟：重新数，所以第 3 箭不算满
        assertFalse(t.onShot(140_000, 3))
        assertFalse(t.onShot(150_000, 3))
        assertTrue(t.onShot(160_000, 3))
    }

    @Test
    fun endTrackerCancelLastGivesBackOneArrow() {
        val t = EndTracker()
        t.onShot(10_000, 3)
        t.onShot(20_000, 3)
        t.cancelLast() // 取消第 2 箭
        assertFalse(t.onShot(30_000, 3)) // 现在才是第 2 箭
        assertTrue(t.onShot(40_000, 3))
    }

    @Test
    fun endTrackerSingleArrowEnd() {
        val t = EndTracker()
        assertTrue(t.onShot(10_000, 1))
        assertTrue(t.onShot(20_000, 1))
    }

    // ---- 放箭后跟随晃动量 ----

    @Test
    fun followThroughMeasuresAfterImpact() {
        val tr = StabilityTracker()
        val trigger = 5_000L
        // 放箭后 0.3～1.0 秒：每 10ms 一笔，x 在 ±2 之间来回
        var t = trigger
        var sign = 1f
        while (t <= trigger + 1_000) {
            val big = if (t - trigger <= 300) 30f else 2f * sign // 冲击期间的大值不该算进去
            tr.addAccel(big, 0f, 0f, t)
            sign = -sign
            t += 10
        }
        val f = tr.followThrough(trigger)
        assertNotNull(f.accelRms)
        assertEquals(2.0f, f.accelRms!!, 0.1f)
        assertNull(f.gyroRms) // 没喂过陀螺仪
    }

    @Test
    fun followThroughNeedsEnoughDataAfterRelease() {
        val tr = StabilityTracker()
        val trigger = 5_000L
        var t = trigger - 500
        while (t <= trigger + 400) { // 只有放箭后 0.4 秒的数据
            tr.addAccel(1f, 0f, 0f, t)
            t += 10
        }
        assertNull(tr.followThrough(trigger).accelRms)
    }

    // ---- 环数与相关性 ----

    @Test
    fun scoreStatsAndCorrelation() {
        // 晃动越大分数越低：完全负相关
        val shots = (1..8).map { shot(it * 10, acc = it * 0.1f, score = 10 - it) }
        val st = computeStats(session(shots))
        assertEquals(8, st.scoredCount)
        assertEquals(5.5f, st.scoreAvg!!, 1e-4f)
        assertEquals(-1f, st.scoreAccelCorr!!, 1e-3f)
    }

    @Test
    fun correlationNeedsEnoughShots() {
        val shots = (1..7).map { shot(it * 10, acc = it * 0.1f, score = 10 - it) }
        val st = computeStats(session(shots))
        assertEquals(7, st.scoredCount)
        assertNull(st.scoreAccelCorr)
    }

    @Test
    fun correlationNullWhenNoVariation() {
        val shots = (1..8).map { shot(it * 10, acc = 0.5f, score = 8) }
        assertNull(computeStats(session(shots)).scoreAccelCorr)
    }

    @Test
    fun statsIgnoreCancelledForFollowAndScore() {
        val st = computeStats(
            session(
                listOf(
                    shot(10, follow = 1f, score = 10),
                    shot(20, follow = 9f, score = 0, cancelled = true),
                ),
            ),
        )
        assertEquals(1f, st.followAvg!!, 1e-5f)
        assertEquals(1, st.scoredCount)
        assertEquals(10f, st.scoreAvg!!, 1e-5f)
    }

    @Test
    fun noScoreMeansNoScoreStats() {
        val st = computeStats(session(listOf(shot(10), shot(20))))
        assertEquals(0, st.scoredCount)
        assertNull(st.scoreAvg)
        assertNull(st.followAvg)
    }

    // ---- 存档：新字段 ----

    @Test
    fun jsonRoundTripKeepsNewFields() {
        val original = session(
            listOf(
                shot(10, follow = 1.25f, score = 9).copy(followGyroRms = 7.5f),
                shot(20),
            ),
        )
        val back = SessionJson.decode(SessionJson.encode(original), "t")!!
        assertEquals(1.25f, back.shots[0].followAccelRms!!, 1e-3f)
        assertEquals(7.5f, back.shots[0].followGyroRms!!, 1e-3f)
        assertEquals(9, back.shots[0].score)
        assertNull(back.shots[1].followAccelRms)
        assertNull(back.shots[1].score)
    }

    @Test
    fun oldJsonWithoutNewFieldsStillLoads() {
        val old = """{"id":"x","startMs":1,"endMs":-1,"threshold":16.0,"windowSec":2,"hasGyro":true,"rawCsv":false,
            "shots":[{"rel":1000,"peak":17.0,"thr":16.0,"acc":0.5}],"manual":[]}"""
        val s = SessionJson.decode(old, "x")!!
        assertNull(s.shots[0].followAccelRms)
        assertNull(s.shots[0].score)
    }

    @Test
    fun scoreOutOfRangeIsClamped() {
        val text = """{"id":"x","startMs":1,"endMs":-1,"threshold":16.0,"windowSec":2,"hasGyro":false,"rawCsv":false,
            "shots":[{"rel":1000,"peak":17.0,"thr":16.0,"sc":99}],"manual":[]}"""
        assertEquals(10, SessionJson.decode(text, "x")!!.shots[0].score)
    }

    // ---- 记录器：补跟随数据 ----

    @Test
    fun recorderAttachesFollowToTheRightShot() {
        val base = Files.createTempDirectory("archery-v14").toFile()
        val store = SessionStore(base)
        val rec = SessionRecorder(store)
        val id = rec.start(t0, 16f, 2, true, false)
        val i0 = rec.addShot(1000, 17f, 16f, 0.5f, 1f)
        val i1 = rec.addShot(5000, 18f, 16f, 0.6f, 1.1f)
        assertEquals(0, i0)
        assertEquals(1, i1)
        rec.attachFollow(id, i0, null, 2.5f, 9f)
        rec.stop(t0 + 10_000)
        val s = store.load(id)!!
        assertEquals(2.5f, s.shots[0].followAccelRms!!, 1e-3f)
        assertEquals(9f, s.shots[0].followGyroRms!!, 1e-3f)
        assertNull(s.shots[1].followAccelRms)
    }

    @Test
    fun recorderIgnoresFollowForOtherSession() {
        val base = Files.createTempDirectory("archery-v14b").toFile()
        val store = SessionStore(base)
        val rec = SessionRecorder(store)
        val first = rec.start(t0, 16f, 2, true, false)
        val i = rec.addShot(1000, 17f, 16f, 0.5f, 1f)
        rec.stop(t0 + 5000)
        val second = rec.start(t0 + 100_000, 16f, 2, true, false)
        rec.addShot(1000, 17f, 16f, 0.5f, 1f)
        rec.attachFollow(first, i, null, 9f, 9f) // 上一笔记录的补数据，不能写到新的记录上
        rec.stop(t0 + 110_000)
        assertNull(store.load(second)!!.shots[0].followAccelRms)
        assertNull(store.load(first)!!.shots[0].followAccelRms)
    }

    @Test
    fun recorderStillReturnsMinusOneWithoutSession() {
        val rec = SessionRecorder(SessionStore(Files.createTempDirectory("archery-v14c").toFile()))
        assertEquals(-1, rec.addShot(1000, 17f, 16f, 0.5f, 1f))
    }

    // ---- 侦测：冷却时间可调 ----

    @Test
    fun detectorCooldownIsAdjustable() {
        val hits = ArrayList<Long>()
        val d = ShotDetector(spikeThreshold = 16f, cooldownMs = 1000L) { _, t -> hits.add(t) }
        fun feed(from: Long, to: Long, v: Float) {
            var t = from
            while (t < to) { d.onSample(v, 0f, 0f, t); t += 10 }
        }
        feed(0, 1000, 0.5f)
        feed(1000, 1050, 20f)   // 第一箭
        feed(1050, 2000, 0.5f)  // 静止，同时冷却时间（1 秒）也过了
        feed(2000, 2050, 20f)   // 第二箭
        feed(2050, 2600, 0.5f)
        assertEquals(2, hits.size)
    }

    @Test
    fun detectorLongCooldownBlocksSecondShot() {
        val hits = ArrayList<Long>()
        val d = ShotDetector(spikeThreshold = 16f, cooldownMs = 6000L) { _, t -> hits.add(t) }
        fun feed(from: Long, to: Long, v: Float) {
            var t = from
            while (t < to) { d.onSample(v, 0f, 0f, t); t += 10 }
        }
        feed(0, 1000, 0.5f)
        feed(1000, 1050, 20f)
        feed(1050, 2000, 0.5f)
        feed(2000, 2050, 20f)   // 才 1 秒，被 6 秒冷却挡住
        feed(2050, 2600, 0.5f)
        assertEquals(1, hits.size)
    }

    // ---- 跨次趋势与匯出 ----

    @Test
    fun sessionTrendIsOldestFirstAndLimited() {
        val sessions = (1..25).map { i ->
            session(listOf(shot(10, acc = i * 0.1f)), start = t0 + i * 1_000_000L, id = "s$i")
        }
        val trend = sessionTrend(sessions, limit = 20)
        assertEquals(20, trend.size)
        assertTrue(trend.first().startMs < trend.last().startMs)
        assertEquals(2.5f, trend.last().value, 1e-4f) // 最新的一次 (i = 25)
        assertEquals(0.6f, trend.first().value, 1e-4f) // 第 6 次
    }

    @Test
    fun sessionTrendSkipsSessionsWithoutData() {
        val a = session(listOf(shot(10, acc = null, gyr = null)), id = "a")
        val b = session(listOf(shot(10, acc = 0.4f)), start = t0 + 1_000_000L, id = "b")
        val trend = sessionTrend(listOf(a, b))
        assertEquals(1, trend.size)
        assertEquals(0.4f, trend[0].value, 1e-5f)
    }

    @Test
    fun extremesFindMinAndMax() {
        val (steady, shaky) = extremes(listOf(0.5f, 0.2f, 0.9f))
        assertEquals(2, steady!!.number)
        assertEquals(3, shaky!!.number)
        val none = extremes(listOf(1f))
        assertNull(none.first)
        assertNull(none.second)
    }

    @Test
    fun exportCsvShapes() {
        val s = session(
            listOf(
                shot(10, follow = 1.5f, score = 9),
                shot(20, cancelled = true),
                shot(30, acc = null, gyr = null),
            ),
            id = "20261019_211003",
        )
        val summary = ExportCsv.summary(listOf(s)).trim().lines()
        assertEquals(2, summary.size)
        val header = summary[0].split(",")
        assertEquals(header.size, summary[1].split(",").size)
        assertTrue(summary[1].startsWith("20261019_211003,"))
        assertEquals("2", summary[1].split(",")[header.indexOf("detected")]) // 误判的不算
        assertEquals("1", summary[1].split(",")[header.indexOf("cancelled")])

        val shots = ExportCsv.shots(listOf(s)).trim().lines()
        assertEquals(4, shots.size)
        val h = shots[0].split(",")
        for (line in shots.drop(1)) assertEquals(h.size, line.split(",").size)
        val c = shots[2].split(",")
        assertEquals("", c[h.indexOf("no")])
        assertEquals("1", c[h.indexOf("cancelled")])
        val third = shots[3].split(",")
        assertEquals("2", third[h.indexOf("no")]) // 误判不占编号
        assertEquals("", third[h.indexOf("accel_rms")])
        assertEquals("9", shots[1].split(",")[h.indexOf("score")])
    }

    @Test
    fun storeExportsAndDeletesMany() {
        val base = Files.createTempDirectory("archery-v14d").toFile()
        val store = SessionStore(base)
        store.saveAsync(session(listOf(shot(10)), start = t0, id = "20261019_100000"))
        store.saveAsync(session(listOf(shot(10)), start = t0 + 1_000_000L, id = "20261019_110000"))
        store.saveAsync(session(listOf(shot(10)), start = t0 + 2_000_000L, id = "20261019_120000"))

        // v1.19.3：summary.csv／shots.csv 不再先寫成檔案，分享時即時產生
        val names = store.shareNames()
        assertTrue(names.contains("summary.csv") && names.contains("shots.csv"))
        assertTrue(!java.io.File(java.io.File(base, "sessions"), "summary.csv").exists())
        val summary = String(store.csvBytes("summary.csv")!!, Charsets.UTF_8)
        assertEquals(4, summary.lines().filter { it.isNotEmpty() }.size) // 标题 + 3 笔
        assertEquals(summary.toByteArray(Charsets.UTF_8).size.toLong(), store.shareFile("summary.csv")!!.length)
        assertTrue(store.csvBytes("other.csv") == null)

        store.deleteMany(listOf("20261019_100000", "20261019_110000"))
        val left = store.listSessions()
        assertEquals(1, left.size)
        assertEquals("20261019_120000", left[0].id)
        // 匯出档案不会被当成记录
        assertEquals(1, store.listSessions().size)
    }

    // ---- 震动样式 ----

    @Test
    fun vibrationPatternsAreValid() {
        for (style in listOf(EndVibration.THREE, EndVibration.TWO_SHORT_LONG, EndVibration.LONG)) {
            val p = EndVibration.pattern(style)!!
            assertTrue(p.size >= 2)
            assertEquals(0L, p[0])               // Android 格式：第一个是开始前的等待
            assertTrue(p.drop(1).all { it > 0 })
        }
        assertNull(EndVibration.pattern(EndVibration.OFF))
    }

    @Test
    fun endVibrationIsClearlyDifferentFromShotBuzz() {
        // 满趟的总时长要明显比放箭的单次轻震长
        for (style in listOf(EndVibration.THREE, EndVibration.TWO_SHORT_LONG, EndVibration.LONG)) {
            assertTrue(EndVibration.pattern(style)!!.sum() >= EndVibration.SHOT_MS * 5)
        }
    }

    @Test
    fun vibrationStyleCyclesThroughAll() {
        var s = EndVibration.THREE
        val seen = HashSet<Int>()
        repeat(EndVibration.STYLE_COUNT) { seen.add(s); s = EndVibration.next(s) }
        assertEquals(EndVibration.STYLE_COUNT, seen.size)
        assertEquals(EndVibration.THREE, s) // 转一圈回到起点
    }
}
