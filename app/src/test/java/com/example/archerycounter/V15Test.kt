package com.example.archerycounter

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V15Test {

    private val t0 = 1_760_000_000_000L

    private fun shot(sec: Int, peak: Float, cancelled: Boolean = false, acc: Float? = 0.5f) = ShotEntry(
        relMs = sec * 1000L, peak = peak, threshold = 16f, accelRms = acc, gyroRms = 1f, cancelled = cancelled,
    )

    private fun session(
        shots: List<ShotEntry>,
        id: String = "t",
        start: Long = t0,
        tags: List<String> = emptyList(),
        calibration: Boolean = false,
    ) = Session(
        id = id, startMs = start, endMs = start + 600_000L, threshold = 16f, windowSec = 2,
        hasGyro = true, rawCsv = false, shots = shots, manual = emptyList(),
        tags = tags, calibration = calibration,
    )

    private fun newStore() = SessionStore(Files.createTempDirectory("archery-v15").toFile())

    // ---- 条件标签 ----

    @Test
    fun tagsCycleThroughOptionsAndBackToNone() {
        var d: String? = null
        val seen = ArrayList<String?>()
        repeat(Tags.DISTANCES.size + 1) { d = Tags.nextDistance(d); seen.add(d) }
        assertEquals(Tags.DISTANCES + listOf<String?>(null), seen)
        assertEquals(Tags.BOWS.first(), Tags.nextBow(null))
        assertNull(Tags.nextBow(Tags.BOWS.last()))
        assertEquals(Tags.BOWS.first(), Tags.nextBow("不认识的旧标签")) // 旧值不在选项里：当作未设定
    }

    @Test
    fun tagsBuildSameAndLabel() {
        assertEquals(listOf("室外70米", "反曲"), Tags.build("室外70米", "反曲"))
        assertEquals(listOf("反曲"), Tags.build(null, "反曲"))
        assertTrue(Tags.same(listOf("a", "b"), listOf("b", "a")))
        assertFalse(Tags.same(listOf("a"), listOf("a", "b")))
        assertNull(Tags.label(emptyList()))
        assertEquals("室外70米 · 反曲", Tags.label(listOf("室外70米", "反曲")))
        assertEquals("室外70米", Tags.distanceOf(listOf("反曲", "室外70米")))
        assertEquals("反曲", Tags.bowOf(listOf("反曲", "室外70米")))
        assertNull(Tags.bowOf(emptyList()))
    }

    @Test
    fun sessionTrendFiltersByTagsAndSkipsCalibration() {
        val a = session(listOf(shot(10, 17f, acc = 0.2f)), id = "a", start = t0, tags = listOf("室外70米", "反曲"))
        val b = session(listOf(shot(10, 17f, acc = 0.9f)), id = "b", start = t0 + 1_000_000L, tags = listOf("室內18米"))
        val c = session(listOf(shot(10, 17f, acc = 0.4f)), id = "c", start = t0 + 2_000_000L, tags = listOf("反曲", "室外70米"))
        val cal = session(listOf(shot(10, 30f, acc = 5f)), id = "cal", start = t0 + 3_000_000L, calibration = true)
        val all = sessionTrend(listOf(a, b, c, cal))
        assertEquals(3, all.size) // 校准不算
        val same = sessionTrend(listOf(a, b, c, cal), tags = listOf("室外70米", "反曲"))
        assertEquals(listOf(0.2f, 0.4f), same.map { it.value })
    }

    // ---- 阈值建议 ----

    private fun adviceSession(valid: List<Float>, cancelled: List<Float>, calibration: Boolean = false): Session {
        val shots = ArrayList<ShotEntry>()
        valid.forEachIndexed { i, p -> shots.add(shot(10 + i * 10, p)) }
        cancelled.forEachIndexed { i, p -> shots.add(shot(1000 + i * 10, p, cancelled = true)) }
        return session(shots, calibration = calibration)
    }

    @Test
    fun adviceNeedsEnoughData() {
        val a = adviseThreshold(listOf(adviceSession(listOf(18f, 19f), listOf(16f))), 16f)
        assertEquals(AdviceKind.NOT_ENOUGH, a.kind)
        assertNull(a.suggested)
        assertEquals(2, a.validCount)
        assertEquals(1, a.cancelledCount)
    }

    @Test
    fun adviceSuggestsRaisingThreshold() {
        val valid = (18..29).map { it.toFloat() }          // 12 笔有效，峰值 18～29
        val cancelled = listOf(16f, 16.5f, 17f)             // 3 笔误判，都在 18 以下
        val a = adviseThreshold(listOf(adviceSession(valid, cancelled)), 16f)
        assertEquals(AdviceKind.SUGGEST, a.kind)
        assertEquals(18, a.suggested)
        assertEquals(1f, a.validKept, 1e-4f)
        assertEquals(1f, a.cancelledExcluded, 1e-4f)
    }

    @Test
    fun adviceNeverSuggestsAtOrBelowCurrent() {
        val valid = (18..29).map { it.toFloat() }
        val cancelled = listOf(16f, 16.5f, 17f)
        val a = adviseThreshold(listOf(adviceSession(valid, cancelled)), 17.5f)
        assertEquals(AdviceKind.SUGGEST, a.kind)
        assertTrue(a.suggested!! > 17.5f)
    }

    @Test
    fun adviceNotSeparableWhenCancelledAreStrong() {
        val valid = (17..28).map { it.toFloat() }
        val cancelled = listOf(30f, 31f, 32f) // 误判的反而更大：调高阈值只会丢掉有效的箭
        val a = adviseThreshold(listOf(adviceSession(valid, cancelled)), 16f)
        assertEquals(AdviceKind.NOT_SEPARABLE, a.kind)
        assertNull(a.suggested)
    }

    @Test
    fun adviceIgnoresCalibrationSessions() {
        val valid = (18..29).map { it.toFloat() }
        val a = adviseThreshold(
            listOf(
                adviceSession(valid, emptyList()),
                adviceSession(emptyList(), listOf(16f, 16.5f, 17f), calibration = true),
            ),
            16f,
        )
        assertEquals(AdviceKind.NOT_ENOUGH, a.kind)
        assertEquals(0, a.cancelledCount)
    }

    // ---- 存档：标签、校准、标注 ----

    @Test
    fun jsonRoundTripKeepsTagsCalibrationAndMarks() {
        val original = session(listOf(shot(10, 17f)), tags = listOf("室外70米", "反曲"), calibration = true)
            .copy(marks = listOf(Mark("只拉弓不放箭", 1000, 20_000), Mark("正常射擊", 20_000, null)))
        val back = SessionJson.decode(SessionJson.encode(original), "t")!!
        assertEquals(listOf("室外70米", "反曲"), back.tags)
        assertTrue(back.calibration)
        assertEquals(2, back.marks.size)
        assertEquals("只拉弓不放箭", back.marks[0].label)
        assertEquals(20_000L, back.marks[0].endRelMs)
        assertNull(back.marks[1].endRelMs)
    }

    @Test
    fun oldJsonWithoutTagsStillLoads() {
        val old = """{"id":"x","startMs":1,"endMs":-1,"threshold":16.0,"windowSec":2,"hasGyro":true,"rawCsv":false,
            "shots":[],"manual":[]}"""
        val s = SessionJson.decode(old, "x")!!
        assertTrue(s.tags.isEmpty())
        assertFalse(s.calibration)
        assertTrue(s.marks.isEmpty())
    }

    @Test
    fun recorderSavesTagsAndMarks() {
        val store = newStore()
        val rec = SessionRecorder(store)
        val id = rec.start(t0, 16f, 2, true, true, tags = listOf("反曲"), calibration = true)
        rec.startMark(1000, "只拉弓不放箭")
        rec.endMark(9000)
        rec.startMark(10_000, "正常射擊")
        rec.startMark(30_000, "取箭走動") // 上一段沒按結束：自動結束在這個時間
        rec.stop(t0 + 60_000)
        val s = store.load(id)!!
        assertTrue(s.calibration)
        assertEquals(listOf("反曲"), s.tags)
        assertEquals(3, s.marks.size)
        assertEquals(9000L, s.marks[0].endRelMs)
        assertEquals(30_000L, s.marks[1].endRelMs)
        assertNull(s.marks[2].endRelMs)
    }

    @Test
    fun marksIgnoredWithoutActiveSession() {
        val rec = SessionRecorder(newStore())
        rec.startMark(1000, "x") // 沒有進行中的記錄，不應出錯
        rec.endMark(2000)
    }

    @Test
    fun exportIncludesTagsAndCalibrationColumns() {
        val s = session(listOf(shot(10, 17f)), id = "20261019_211003", tags = listOf("室外70米", "反曲"))
        val lines = ExportCsv.summary(listOf(s)).trim().lines()
        val h = lines[0].split(",")
        val row = lines[1].split(",")
        assertEquals(h.size, row.size)
        assertEquals("室外70米|反曲", row[h.indexOf("tags")])
        assertEquals("0", row[h.indexOf("calibration")])
    }

    // ---- 储存空间 ----

    @Test
    fun usageAndDeleteRawData() {
        val base = Files.createTempDirectory("archery-v15u").toFile()
        val store = SessionStore(base)
        val rec = SessionRecorder(store)

        val id1 = rec.start(t0, 16f, 2, true, true)
        for (i in 0 until 200) rec.writeRaw(i * 10L, 0.1f, 0.2f, 0.3f, 1f, 2f, 3f)
        rec.addShot(1000, 17f, 16f, 0.5f, 1f)
        rec.stop(t0 + 5000)
        val id2 = rec.start(t0 + 100_000, 16f, 2, true, true)
        for (i in 0 until 100) rec.writeRaw(i * 10L, 0.1f, 0.2f, 0.3f, 1f, 2f, 3f)
        rec.addShot(1000, 17f, 16f, 0.5f, 1f)
        rec.stop(t0 + 105_000)
        store.exportAll()

        val u = store.usage()
        assertEquals(2, u.sessionCount)
        assertEquals(2, u.rawCount)
        assertTrue(u.rawBytes > 0)
        assertTrue(u.jsonBytes > 0)
        assertTrue(u.exportBytes > 0)

        // 保留 id2（假裝它還在進行中）：只刪 id1 的原始資料
        val r = store.deleteRawData(keepId = id2)
        assertEquals(1, r.count)
        assertTrue(r.bytes > 0)
        assertFalse(File(File(base, "sessions"), "$id1.csv").exists())
        assertTrue(File(File(base, "sessions"), "$id2.csv").exists())
        assertFalse(store.load(id1)!!.rawCsv)     // 記錄還在，只是不再說有 CSV
        assertEquals(1, store.load(id1)!!.shots.size)
        assertTrue(store.load(id2)!!.rawCsv)
        assertTrue(File(File(base, "sessions"), "summary.csv").exists()) // 匯出檔不是原始資料

        val u2 = store.usage()
        assertEquals(1, u2.rawCount)
        assertEquals(2, u2.sessionCount)
        assertEquals(0, store.deleteRawData(keepId = id2).count) // 再刪一次沒有東西可刪
    }

    @Test
    fun usageOfEmptyStoreIsZero() {
        val u = newStore().usage()
        assertEquals(0, u.sessionCount)
        assertEquals(0L, u.rawBytes)
        assertNotNull(u)
    }

    @Test
    fun calibrationStepsAreThreeDistinctLabels() {
        assertEquals(3, Calibration.STEPS.size)
        assertEquals(3, Calibration.STEPS.map { it.label }.toSet().size)
        assertTrue(Calibration.STEPS.all { it.hint.isNotBlank() })
    }
}
