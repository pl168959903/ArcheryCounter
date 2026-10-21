package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class V1201Test {
    private fun shot(relMs: Long, follow: Float? = 2.5f, cancelled: Boolean = false, reviewed: Boolean = false) =
        ShotEntry(relMs, 30f, 18f, 0.5f, 5f, followAccelRms = follow, cancelled = cancelled, reviewed = reviewed)

    private fun sess(shots: List<ShotEntry>, end: Long? = 3_600_000L) =
        Session("s", 0, end, 18f, 2, true, false, shots, emptyList())

    /** 8 箭每 17 秒，再一支離上一箭 6.5 秒、跟隨量 0.7 的可疑箭（位置 8） */
    private fun withSuspect(reviewed: Boolean = false, cancelled: Boolean = false): Session {
        val base = (0 until 8).map { shot(10_000L + it * 17_000L) }
        return sess(base + shot(base.last().relMs + 6_500, follow = 0.7f, reviewed = reviewed, cancelled = cancelled))
    }

    @Test
    fun reviewedFlagRoundTripsAndOldRecordsStayUnreviewed() {
        val s = withSuspect(reviewed = true)
        val d = SessionJson.decode(SessionJson.encode(s), "s")!!
        assertTrue(d.shots[8].reviewed)
        assertTrue(!d.shots[0].reviewed)
        assertTrue(!SessionJson.encode(withSuspect()).contains("\"ok\""))
        assertTrue(!SessionJson.decode(SessionJson.encode(withSuspect()), "s")!!.shots[8].reviewed)
    }

    @Test
    fun reviewedOrCancelledShotIsNoLongerSuspicious() {
        assertEquals(setOf(8), FastShot.flagged(withSuspect()))
        assertEquals(emptySet<Int>(), FastShot.flagged(withSuspect(reviewed = true)))
        assertEquals(emptySet<Int>(), FastShot.flagged(withSuspect(cancelled = true)))
    }

    @Test
    fun recordTagCountsUnreviewedSuspectShotsAndDisappears() {
        assertEquals(Suspect(SuspectKind.SHOTS, 1), Suspicion.check(withSuspect(), false, true))
        assertEquals(null, Suspicion.check(withSuspect(), false, false))             // 沒開「標示可疑的箭」
        assertEquals(null, Suspicion.check(withSuspect(reviewed = true), false, true)) // 處理完標籤消失
        assertEquals(null, Suspicion.check(withSuspect(cancelled = true), false, true))
        assertEquals(null, Suspicion.check(withSuspect(), true, true))               // 進行中不判斷
        assertEquals(Suspect(SuspectKind.INTERRUPTED, 1), Suspicion.check(withSuspect().copy(endMs = null), false, true))
        assertTrue(Suspicion.shortText(Suspect(SuspectKind.SHOTS, 3)).contains("3"))
        assertTrue(Suspicion.text(Suspect(SuspectKind.SHOTS, 2)).contains("2"))
    }

    @Test
    fun shotNavStepsAndJumpsToNextSuspect() {
        val all = listOf(0, 1, 2, 3, 4)
        assertEquals(3, ShotNav.step(all, 2, 1))
        assertEquals(1, ShotNav.step(all, 2, -1))
        assertEquals(4, ShotNav.step(all, 4, 1))   // 到頭不動
        assertEquals(0, ShotNav.step(all, 0, -1))
        val sus = listOf(3, 8, 20)
        assertEquals(8, ShotNav.step(sus, 3, 1))
        assertEquals(8, ShotNav.step(sus, 5, 1))   // 目前不在清單裡：取最接近的
        assertEquals(3, ShotNav.step(sus, 5, -1))
        assertEquals(7, ShotNav.step(emptyList(), 7, 1))
        // 處理完 8：先往後找 20，沒有再往前
        assertEquals(20, ShotNav.nextAfter(listOf(3, 20), 8))
        assertEquals(3, ShotNav.nextAfter(listOf(3), 20))
        assertEquals(null, ShotNav.nextAfter(emptyList(), 8))
        assertEquals("2 / 3", ShotNav.progress(sus, 8))
        assertEquals("- / 3", ShotNav.progress(sus, 5))
    }

    @Test
    fun endLabelFollowsValidNumbersAndSkipsCancelled() {
        val a = (0 until 3).map { shot(10_000L + it * 17_000L) }
        val false1 = shot(a.last().relMs + 3_000, cancelled = true)
        val b = (0 until 3).map { shot(a.last().relMs + 100_000 + it * 17_000L) }
        val s = sess(a + false1 + b)
        val ends = endsFor(s, true, 6)
        assertEquals("1-3", shotEndLabel(s, ends, 2))
        assertEquals(null, shotEndLabel(s, ends, 3))      // 誤判不標
        assertEquals("2-1", shotEndLabel(s, ends, 4))     // 誤判不算有效箭，下一趟第 1 箭
        assertEquals("2-3", shotEndLabel(s, ends, 6))
        assertEquals(null, shotEndLabel(s, ends, 99))
    }
}
