package com.zgrcc.archeryassistant

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/*
 * v1.14 的整理類功能（都只讀現有記錄，不改記錄）：比賽節奏分析、條件分組對比、個人最佳、訓練總覽。
 * 純 Kotlin，有單元測試。
 */

// ---------- 比賽節奏分析 ----------

/** 正式趟的節奏：第一箭等了多久、最後一箭離時間到還剩幾秒、用時。 */
data class EndPace(
    val label: String,
    val arrows: Int,
    /** 從這趟開始到放第一箭的秒數；這趟沒有箭為 null */
    val firstWaitSec: Float?,
    /** 最後一箭放出時離這趟時間到還剩幾秒（不小於 0）；沒有箭為 null */
    val lastRemainSec: Float?,
    val usedSec: Float,
)

data class MatchPace(
    val ends: List<EndPace>,
    val firstWaitAvg: Float?,
    val firstWaitMax: Float?,
    /** 第一箭等得最久的那一趟 */
    val slowestEnd: String?,
    /** 最後一箭剩最少時間的那一趟 */
    val tightestEnd: String?,
    val lastRemainMin: Float?,
    /** 壓線的趟數：最後一箭放出時只剩 [PaceAnalyzer.TIGHT_SEC] 秒以內 */
    val tightEnds: Int,
    val usedAvg: Float?,
    val usedMax: Float?,
)

object PaceAnalyzer {
    const val TIGHT_SEC = 10f

    /** 比賽記錄的節奏統計（只算正式趟）。不是比賽記錄、或沒有正式趟時回傳 null。 */
    fun analyze(s: Session): MatchPace? {
        val meta = s.comp ?: return null
        val valid = s.validShots().sortedBy { it.relMs }
        val formal = s.marks.filter { !it.label.startsWith("P") }.sortedBy { it.startRelMs }
        if (formal.isEmpty()) return null
        val limitMs = meta.secondsPerEnd * 1000L
        val lastRel = s.shots.maxOfOrNull { it.relMs } ?: 0L

        val ends = formal.map { m ->
            val end = m.endRelMs ?: lastRel.coerceAtLeast(m.startRelMs)
            val inRange = valid.filter { it.relMs in m.startRelMs..end }
            val first = inRange.firstOrNull()
            val last = inRange.lastOrNull()
            EndPace(
                label = m.label,
                arrows = inRange.size,
                firstWaitSec = first?.let { (it.relMs - m.startRelMs) / 1000f },
                lastRemainSec = last?.let { ((m.startRelMs + limitMs - it.relMs).coerceAtLeast(0L)) / 1000f },
                usedSec = (end - m.startRelMs).coerceAtLeast(0L) / 1000f,
            )
        }
        val waits = ends.filter { it.firstWaitSec != null }
        val remains = ends.filter { it.lastRemainSec != null }
        val slowest = waits.maxByOrNull { it.firstWaitSec!! }
        val tightest = remains.minByOrNull { it.lastRemainSec!! }
        return MatchPace(
            ends = ends,
            firstWaitAvg = if (waits.isEmpty()) null else waits.map { it.firstWaitSec!! }.average().toFloat(),
            firstWaitMax = slowest?.firstWaitSec,
            slowestEnd = slowest?.label,
            tightestEnd = tightest?.label,
            lastRemainMin = tightest?.lastRemainSec,
            tightEnds = remains.count { it.lastRemainSec!! <= TIGHT_SEC },
            usedAvg = ends.map { it.usedSec }.average().toFloat(),
            usedMax = ends.maxOf { it.usedSec },
        )
    }
}

// ---------- 條件分組對比 ----------

data class GroupRow(
    /** 例：「室外70米 · 反曲」；沒有距離也沒有弓種為 null（顯示時換成「未設定」） */
    val tags: List<String>,
    val sessions: Int,
    val arrows: Int,
    val accelAvg: Float?,
    val scoreAvg: Float?,
)

object GroupCompare {
    /** 一組要有這麼多支有晃動量的箭，才參與「最穩」比較 */
    const val MIN_ARROWS_FOR_BEST = 6

    /** 按「距離＋弓種」分組（比賽標籤不影響分組）。校準、沒有有效箭的記錄不算。回傳依記錄數多到少排序。 */
    fun groups(sessions: List<Session>): List<GroupRow> {
        val real = sessions.filter { !it.calibration && it.validShots().isNotEmpty() }
        val by = real.groupBy { Tags.build(Tags.distanceOf(it.tags), Tags.bowOf(it.tags)) }
        return by.map { (tags, list) ->
            val shots = list.flatMap { it.validShots() }
            GroupRow(
                tags = tags,
                sessions = list.size,
                arrows = shots.size,
                accelAvg = avgOrNull(shots.map { it.accelRms }),
                scoreAvg = avgOrNull(shots.map { it.score?.toFloat() }),
            )
        }.sortedWith(compareByDescending<GroupRow> { it.sessions }.thenByDescending { it.arrows })
    }

    /** 最穩（平均晃動最低）的一組；至少要有兩組可以比，且該組有足夠的箭。 */
    fun steadiest(rows: List<GroupRow>): GroupRow? {
        val eligible = rows.filter { it.accelAvg != null && it.arrows >= MIN_ARROWS_FOR_BEST }
        if (eligible.size < 2) return null
        return eligible.minByOrNull { it.accelAvg!! }
    }

    fun title(tags: List<String>): String = Tags.label(tags) ?: tr("未設定", "Not set")
}

// ---------- 個人最佳 ----------

object PersonalBest {
    /** 一次記錄要有這麼多有效且有晃動量的箭，才參與「最穩的一次」 */
    const val MIN_ARROWS = 6
    /** 至少要有這麼多次可以比，標記才有意義 */
    const val MIN_SESSIONS = 3

    /** 最穩的一次（平均晃動最低）的記錄 id；不夠比時為 null。校準不算。 */
    fun steadiestId(sessions: List<Session>): String? {
        val cands = sessions.filter { !it.calibration && it.endMs != null }.mapNotNull { s ->
            val shots = s.validShots().mapNotNull { it.accelRms }
            if (shots.size < MIN_ARROWS) null else s.id to shots.average().toFloat()
        }
        if (cands.size < MIN_SESSIONS) return null
        return cands.minByOrNull { it.second }?.first
    }

    /** 總環數最高的比賽 id：同賽制（每趟箭數、趟數、秒數都相同）至少 2 場才標，並列時全部標。 */
    fun bestScoreIds(rows: List<MatchRow>): Set<String> {
        val out = HashSet<String>()
        rows.filter { it.scoreTotal != null }
            .groupBy { Triple(it.arrowsPerEnd, it.ends, it.secondsPerEnd) }
            .values
            .filter { it.size >= 2 }
            .forEach { g ->
                val best = g.maxOf { it.scoreTotal!! }
                g.filter { it.scoreTotal == best }.forEach { out.add(it.id) }
            }
        return out
    }

    /** 平均晃動最低的比賽 id（同賽制至少 2 場），並列時全部標。 */
    fun steadiestMatchIds(rows: List<MatchRow>): Set<String> {
        val out = HashSet<String>()
        rows.filter { it.accelAvg != null }
            .groupBy { Triple(it.arrowsPerEnd, it.ends, it.secondsPerEnd) }
            .values
            .filter { it.size >= 2 }
            .forEach { g ->
                val best = g.minOf { it.accelAvg!! }
                g.filter { it.accelAvg == best }.forEach { out.add(it.id) }
            }
        return out
    }
}

// ---------- 訓練總覽 ----------

data class PeriodBar(
    /** 這一週（週一起算）或這個月的第一天 */
    val start: LocalDate,
    val arrows: Int,
    /** 有射箭的天數 */
    val days: Int,
)

object Overview {
    const val WEEKS = 8
    const val MONTHS = 6

    private fun dayOf(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    private fun weekStart(d: LocalDate): LocalDate = d.with(DayOfWeek.MONDAY)

    /** 每天的箭數（校準不算，箭數為 0 的記錄不算）。 */
    fun perDay(sessions: List<Session>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Int> =
        sessions.filter { !it.calibration }
            .groupBy { dayOf(it.startMs, zone) }
            .mapValues { (_, list) -> list.sumOf { it.finalCount() } }
            .filterValues { it > 0 }

    /** 最近 [count] 週（含本週），由舊到新。 */
    fun weeks(sessions: List<Session>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault(), count: Int = WEEKS): List<PeriodBar> {
        val perDay = perDay(sessions, zone)
        val thisWeek = weekStart(dayOf(nowMs, zone))
        return (count - 1 downTo 0).map { back ->
            val start = thisWeek.minusWeeks(back.toLong())
            val end = start.plusDays(6)
            val inWeek = perDay.filterKeys { !it.isBefore(start) && !it.isAfter(end) }
            PeriodBar(start, inWeek.values.sum(), inWeek.size)
        }
    }

    /** 最近 [count] 個月（含本月），由舊到新。 */
    fun months(sessions: List<Session>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault(), count: Int = MONTHS): List<PeriodBar> {
        val perDay = perDay(sessions, zone)
        val thisMonth = YearMonth.from(dayOf(nowMs, zone))
        return (count - 1 downTo 0).map { back ->
            val ym = thisMonth.minusMonths(back.toLong())
            val inMonth = perDay.filterKeys { YearMonth.from(it) == ym }
            PeriodBar(ym.atDay(1), inMonth.values.sum(), inMonth.size)
        }
    }

    /**
     * 連續練習天數：從今天往回數，每天都有射箭。今天還沒射的話，從昨天開始數（今天還有機會）。
     */
    fun streak(sessions: List<Session>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val days = perDay(sessions, zone).keys
        var d = dayOf(nowMs, zone)
        if (d !in days) d = d.minusDays(1)
        var n = 0
        while (d in days) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    /** 本週和上週的箭數差（正值＝本週較多）。bars 要是 [weeks] 的結果；不足兩週回傳 null。 */
    fun weekDelta(bars: List<PeriodBar>): Int? {
        if (bars.size < 2) return null
        return bars[bars.size - 1].arrows - bars[bars.size - 2].arrows
    }

    fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)
}
