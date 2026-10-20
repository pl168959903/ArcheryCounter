package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V112Test {

    // ---------- TraceMath ----------
    @Test
    fun centeredRemovesMean() {
        val c = TraceMath.centered(listOf(1f, 2f, 3f))
        assertEquals(0f, c.sum(), 0.0001f)
        assertEquals(-1f, c[0], 0.0001f)
        assertTrue(TraceMath.centered(emptyList()).isEmpty())
    }

    @Test
    fun rmsAndMaxRadius() {
        val x = listOf(3f, -3f)
        val y = listOf(4f, -4f)
        assertEquals(5f, TraceMath.rmsRadius(x, y), 0.0001f)
        assertEquals(5f, TraceMath.maxRadius(x, y), 0.0001f)
        assertEquals(0f, TraceMath.rmsRadius(emptyList(), emptyList()), 0f)
        assertEquals(0f, TraceMath.maxRadius(emptyList(), emptyList()), 0f)
    }

    @Test
    fun niceRangeLeavesMargin() {
        assertEquals(1f, TraceMath.niceRange(0.5f, false), 0f)
        assertEquals(2f, TraceMath.niceRange(0.95f, false), 0f)       // 0.95×1.1 > 1
        assertEquals(20f, TraceMath.niceRange(100f, false), 0f)       // 超過最大級距就用最大
        assertEquals(20f, TraceMath.niceRange(5f, true), 0f)
        assertEquals(40f, TraceMath.niceRange(30f, true), 0f)
        assertEquals(1200f, TraceMath.niceRange(9999f, true), 0f)
    }

    @Test
    fun insideRatio() {
        val x = listOf(0f, 1f, 3f, 0f)
        val y = listOf(0f, 0f, 0f, 4f)
        assertEquals(0.5f, TraceMath.insideRatio(x, y, 2f), 0.0001f)
        assertEquals(1f, TraceMath.insideRatio(x, y, 5f), 0.0001f)
        assertEquals(0f, TraceMath.insideRatio(emptyList(), emptyList(), 5f), 0f)
    }

    // ---------- LiveTrace ----------
    @Test
    fun liveTraceBinsAverage() {
        val t = LiveTrace(binMs = 40, capacity = 3)
        t.add(0, 1f, 2f)
        t.add(10, 3f, 4f)       // 同一格：平均 (2, 3)
        t.add(50, 10f, 20f)     // 下一格
        val (x, y) = t.snapshot()
        assertEquals(2, x.size)
        assertEquals(2f, x[0], 0.0001f)
        assertEquals(3f, y[0], 0.0001f)
        assertEquals(10f, x[1], 0.0001f)
        assertEquals(20f, y[1], 0.0001f)
    }

    @Test
    fun liveTraceKeepsOnlyCapacityAndClears() {
        val t = LiveTrace(binMs = 10, capacity = 3)
        for (i in 0 until 10) t.add(i * 10L, i.toFloat(), 0f)
        val (x, _) = t.snapshot()
        // 環形緩衝區留 3 格，再加上正在累積的那一格
        assertEquals(4, x.size)
        assertEquals(6f, x[0], 0f)
        assertEquals(9f, x[3], 0f)
        t.clear()
        assertEquals(0, t.snapshot().first.size)
    }

    // ---------- StabilityTracker.trace ----------
    @Test
    fun trackerTraceBinsXy() {
        val tr = StabilityTracker(1024)
        // 100 Hz，1 秒：x = 時間（秒）、y = −x
        for (i in 1..100) tr.addAccel(i / 100f, -i / 100f, 0f, i * 10L)
        val t = tr.trace(endMs = 1000, windowMs = 1000, gyro = false, binMs = 40)
        assertNotNull(t)
        assertFalse(t!!.gyro)
        assertEquals(25, t.x.size)
        assertEquals(t.x.size, t.y.size)
        for (i in t.x.indices) assertEquals(-t.x[i], t.y[i], 0.0001f)
        assertTrue(t.x.last() > t.x.first())
    }

    @Test
    fun trackerTraceGyroAndTooFewSamples() {
        val tr = StabilityTracker(1024)
        assertNull(tr.trace(1000, 1000, gyro = false))
        for (i in 1..100) tr.addGyro(5f, 1f, 0f, i * 10L)
        val g = tr.trace(1000, 1000, gyro = true)
        assertNotNull(g)
        assertTrue(g!!.gyro)
        assertEquals(5f, g.x[0], 0.0001f)
        assertNull(tr.trace(1000, 1000, gyro = false))   // 加速度沒資料
    }

    @Test
    fun trackerTraceFillsGaps() {
        val tr = StabilityTracker(1024)
        // 前 0.5 秒有資料，後 0.5 秒沒有
        for (i in 1..50) tr.addAccel(1f, 2f, 0f, i * 10L)
        val t = tr.trace(endMs = 1000, windowMs = 1000, gyro = false, binMs = 100)!!
        assertEquals(10, t.x.size)
        assertTrue(t.x.all { it == 1f })
        assertTrue(t.y.all { it == 2f })
    }

    // ---------- 存檔 ----------
    @Test
    fun traceRoundTripsThroughJson() {
        val trace = Trace2D(40, true, listOf(0.12f, -0.5f, 1.25f), listOf(0f, 0.3f, -0.07f))
        val shots = listOf(
            ShotEntry(1000, 20f, 16f, 1f, null, trace = trace),
            ShotEntry(2000, 20f, 16f, 1f, null),
        )
        val s = Session("a", 0, 5000, 16f, 2, false, false, shots, emptyList())
        val back = SessionJson.decode(SessionJson.encode(s), "a")!!
        val t = back.shots[0].trace!!
        assertEquals(40, t.stepMs)
        assertTrue(t.gyro)
        assertEquals(listOf(0.12f, -0.5f, 1.25f), t.x)
        assertEquals(listOf(0f, 0.3f, -0.07f), t.y)
        assertNull(back.shots[1].trace)
    }

    @Test
    fun badTraceIsIgnored() {
        val json = """{"startMs":0,"endMs":10,"threshold":16,"shots":[{"rel":1,"peak":20,"tr":{"s":40,"x":[1,2],"y":[1]}}],"manual":[]}"""
        val s = SessionJson.decode(json, "b")!!
        assertEquals(1, s.shots.size)
        assertNull(s.shots[0].trace)
    }

    @Test
    fun recorderKeepsTrace() {
        val dir = java.io.File.createTempFile("v112", "").apply { delete(); mkdirs() }
        val rec = SessionRecorder(SessionStore(dir))
        rec.start(1_000_000L, 16f, 2, false, false)
        val t = Trace2D(40, false, listOf(1f), listOf(2f))
        val i = rec.addShot(1000, 20f, 16f, 1f, null, trace = t)
        assertEquals(0, i)
        rec.setScore(0, 9)                                   // 其他修改不應弄丟軌跡
        val snap = rec.snapshot()!!
        assertEquals(t, snap.shots[0].trace)
        rec.stop(2_000_000L)
        dir.deleteRecursively()
    }
}
