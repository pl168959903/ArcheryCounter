package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class V1194bTest {
    private fun shot(relMs: Long, cancelled: Boolean = false) = ShotEntry(relMs, 30f, 18f, 0.5f, 5f, cancelled = cancelled)
    private fun sess(shots: List<ShotEntry>) = Session("s", 0, 3_600_000, 18f, 2, true, false, shots, emptyList())

    /** 依 [sizes] 每趟 N 箭，趟內間隔 [gap] 毫秒，趟間 [rest] 毫秒 */
    private fun build(sizes: List<Int>, gap: Long = 17_000, rest: Long = 100_000): Session {
        val out = ArrayList<ShotEntry>()
        var t = 10_000L
        sizes.forEachIndexed { i, n ->
            if (i > 0) t += rest - gap
            repeat(n) { out.add(shot(t)); t += gap }
        }
        return sess(out)
    }

    @Test
    fun endsAreFoundFromGapsNotFixedCount() {
        val sizes = listOf(6, 8, 8, 8, 8, 10, 8, 6, 6, 9, 9, 9)
        val ends = AutoEnds.compute(build(sizes))
        assertEquals(sizes, ends.map { it.count })
        assertEquals(12, ends.size)
        assertTrue(AutoEnds.summary(ends).contains("12"))
        assertTrue(AutoEnds.summary(ends).contains("6、8、8"))
    }

    @Test
    fun thresholdIsRelativeToMedianButAtLeast35s() {
        assertEquals(42_500L, AutoEnds.gapMs(build(listOf(8, 8))))
        assertEquals(35_000L, AutoEnds.gapMs(build(listOf(8, 8), gap = 8_000))) // 8s×2.5=20s → 下限 35s
        assertEquals(END_GAP_MS, AutoEnds.gapMs(sess(listOf(shot(1000), shot(20_000)))))
    }

    @Test
    fun thirtySecondRestIsAnEndWhenShootingIsFast() {
        // 趟內 8 秒一箭，走去拔箭 40 秒 → 換趟
        val ends = AutoEnds.compute(build(listOf(6, 6), gap = 8_000, rest = 40_000))
        assertEquals(listOf(6, 6), ends.map { it.count })
    }

    @Test
    fun slowShooterWithLongGapsDoesNotSplitEveryShot() {
        // 趟內 30 秒一箭（中位數 30 秒 → 門檻 75 秒），趟間 120 秒
        val ends = AutoEnds.compute(build(listOf(5, 5), gap = 30_000, rest = 120_000))
        assertEquals(listOf(5, 5), ends.map { it.count })
    }

    @Test
    fun labelsAndEmptyCases() {
        val ends = AutoEnds.compute(build(listOf(3, 4)))
        assertEquals("1-1", AutoEnds.label(ends, 1))
        assertEquals("1-3", AutoEnds.label(ends, 3))
        assertEquals("2-1", AutoEnds.label(ends, 4))
        assertEquals("2-4", AutoEnds.label(ends, 7))
        assertEquals(null, AutoEnds.label(ends, 8))
        assertEquals("", AutoEnds.summary(AutoEnds.compute(sess(emptyList()))))
    }

    @Test
    fun cancelledShotsDoNotCountInEnds() {
        val base = build(listOf(6, 6))
        val withFalse = base.copy(shots = base.shots.toMutableList().also { it.add(6, shot(it[5].relMs + 3_000, cancelled = true)) })
        assertEquals(listOf(6, 6), AutoEnds.compute(withFalse).map { it.count })
    }

    @Test
    fun autoEndsSettingDefaultsOn() {
        val s = AppSettings(FakePrefs())
        assertTrue(s.autoEnds.value)
        s.setAutoEnds(false)
        s.resetDefaults()
        assertTrue(s.autoEnds.value)
    }
}
