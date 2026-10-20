package com.zgrcc.archeryassistant

/**
 * 比賽模式（v1.9）。純 Kotlin 狀態機，不依賴 Android，方便單元測試；
 * 時間由呼叫端傳入（毫秒，同一個時鐘），震動與畫面在 CounterViewModel／CompetitionScreen。
 *
 * 流程：每一趟 準備倒數（可關）→ 射擊（倒數計時，射滿箭數就結束）→ 休息（取箭，手動按「下一趟」或自動）→ 下一趟…
 * 全部趟數射完就結束。前面可有幾趟練習趟（不計入正式趟數）。
 */
data class CompFormat(val id: Int, val arrowsPerEnd: Int, val ends: Int, val secondsPerEnd: Int) {
    companion object {
        const val INDOOR_18 = 0
        const val OUTDOOR_70 = 1
        const val CUSTOM = 2
        const val COUNT = 3

        fun preset(id: Int, custom: CompFormat): CompFormat = when (id) {
            INDOOR_18 -> CompFormat(INDOOR_18, 3, 20, 120)
            OUTDOOR_70 -> CompFormat(OUTDOOR_70, 6, 12, 240)
            else -> custom.copy(id = CUSTOM)
        }

        fun label(id: Int): String = when (id) {
            INDOOR_18 -> tr("室內18米", "Indoor 18 m")
            OUTDOOR_70 -> tr("室外70米", "Outdoor 70 m")
            else -> tr("自訂", "Custom")
        }

        fun next(id: Int): Int = (id + 1) % COUNT
    }
}

/** 比賽模式的選項範圍與預設值。 */
object CompLimits {
    const val MIN_ARROWS = 1
    const val MAX_ARROWS = 12
    const val DEFAULT_ARROWS = 3
    const val MIN_ENDS = 1
    const val MAX_ENDS = 40
    const val DEFAULT_ENDS = 10
    const val MIN_SECONDS = 30
    const val MAX_SECONDS = 600
    const val STEP_SECONDS = 10
    const val DEFAULT_SECONDS = 120
    const val MAX_PRACTICE = 4
    const val DEFAULT_PRACTICE = 2
    const val MAX_PREPARE = 30
    const val DEFAULT_PREPARE = 10
    val AUTO_NEXT_OPTIONS = listOf(0, 30, 60, 90, 120)
    const val DEFAULT_AUTO_NEXT = 0

    fun nextAutoNext(v: Int): Int = AUTO_NEXT_OPTIONS[(AUTO_NEXT_OPTIONS.indexOf(v).coerceAtLeast(0) + 1) % AUTO_NEXT_OPTIONS.size]
}

enum class CompPhase { PREPARE, SHOOTING, REST, DONE }

enum class CompEvent {
    /** 準備倒數結束，開始射擊 */
    GO,
    REMIND_30,
    REMIND_10,
    /** 時間到，這一趟結束 */
    TIME_UP,
    /** 箭數射滿，這一趟結束 */
    END_FULL,
    /** 全部趟數射完 */
    MATCH_DONE,
}

/** 已結束的一趟。[number] 從 1 開始（含練習趟）；[practice] 為 true 表示練習趟。 */
data class CompEndResult(
    val number: Int,
    val practice: Boolean,
    val startMs: Long,
    val endMs: Long,
    val arrows: Int,
    val timedOut: Boolean,
    /** 時間到之後又射出的箭數（不計入比賽，但偵測器仍會記錄） */
    val lateArrows: Int,
)

class CompetitionRound(
    val format: CompFormat,
    val practiceEnds: Int,
    val prepareSec: Int,
    val remind30: Boolean,
    val remind10: Boolean,
    val autoNextSec: Int = 0,
) {
    var phase: CompPhase = CompPhase.PREPARE
        private set

    /** 目前第幾趟（從 1 開始，含練習趟） */
    var endNumber: Int = 1
        private set

    var arrowsInEnd: Int = 0
        private set

    private var phaseStartMs = 0L
    private var shootStartMs = 0L
    private var sent30 = false
    private var sent10 = false
    private val _results = ArrayList<CompEndResult>()
    val results: List<CompEndResult> get() = _results

    val totalEnds: Int get() = practiceEnds + format.ends
    val isPractice: Boolean get() = endNumber <= practiceEnds

    /** 目前這一趟在顯示用的編號：練習趟 1..P，正式趟 1..N */
    val displayNumber: Int get() = if (isPractice) endNumber else endNumber - practiceEnds

    fun start(nowMs: Long): List<CompEvent> {
        endNumber = 1
        return beginEnd(nowMs)
    }

    private fun beginEnd(nowMs: Long): List<CompEvent> {
        arrowsInEnd = 0
        sent30 = false
        sent10 = false
        phaseStartMs = nowMs
        return if (prepareSec > 0) {
            phase = CompPhase.PREPARE
            emptyList()
        } else {
            enterShooting(nowMs)
        }
    }

    private fun enterShooting(nowMs: Long): List<CompEvent> {
        phase = CompPhase.SHOOTING
        shootStartMs = nowMs
        phaseStartMs = nowMs
        return listOf(CompEvent.GO)
    }

    /** 這個階段剩餘的毫秒：準備倒數、射擊倒數；休息（有自動下一趟時）倒數到自動開始；其他為 0。 */
    fun remainingMs(nowMs: Long): Long = when (phase) {
        CompPhase.PREPARE -> prepareSec * 1000L - (nowMs - phaseStartMs)
        CompPhase.SHOOTING -> format.secondsPerEnd * 1000L - (nowMs - shootStartMs)
        CompPhase.REST -> if (autoNextSec > 0) autoNextSec * 1000L - (nowMs - phaseStartMs) else 0L
        CompPhase.DONE -> 0L
    }.coerceAtLeast(0L)

    /** 定時呼叫（約每 200 毫秒一次）。回傳這段時間發生的事件。 */
    fun tick(nowMs: Long): List<CompEvent> {
        when (phase) {
            CompPhase.PREPARE -> {
                if (nowMs - phaseStartMs >= prepareSec * 1000L) return enterShooting(nowMs)
            }
            CompPhase.SHOOTING -> {
                val total = format.secondsPerEnd * 1000L
                val left = total - (nowMs - shootStartMs)
                if (left <= 0) return closeEnd(shootStartMs + total, timedOut = true)
                val out = ArrayList<CompEvent>(2)
                if (remind30 && !sent30 && total > 30_000L && left <= 30_000L) {
                    sent30 = true
                    out.add(CompEvent.REMIND_30)
                }
                if (remind10 && !sent10 && total > 10_000L && left <= 10_000L) {
                    sent10 = true
                    out.add(CompEvent.REMIND_10)
                }
                return out
            }
            CompPhase.REST -> {
                if (autoNextSec > 0 && nowMs - phaseStartMs >= autoNextSec * 1000L) return next(nowMs)
            }
            CompPhase.DONE -> Unit
        }
        return emptyList()
    }

    /** 偵測到一箭。射擊中計入這一趟；時間到之後的箭記為上一趟的逾時箭。 */
    fun onShot(nowMs: Long): List<CompEvent> {
        when (phase) {
            CompPhase.SHOOTING -> {
                arrowsInEnd++
                if (arrowsInEnd >= format.arrowsPerEnd) return closeEnd(nowMs, timedOut = false)
            }
            CompPhase.REST, CompPhase.DONE -> {
                val last = _results.lastOrNull()
                if (last != null && last.timedOut && nowMs - last.endMs < LATE_WINDOW_MS) {
                    _results[_results.lastIndex] = last.copy(lateArrows = last.lateArrows + 1)
                }
            }
            CompPhase.PREPARE -> Unit
        }
        return emptyList()
    }

    /** 取消上一箭（按了「−」）：射擊中的這一趟少一箭。 */
    fun cancelLast() {
        if (phase == CompPhase.SHOOTING && arrowsInEnd > 0) arrowsInEnd--
    }

    private fun closeEnd(endMs: Long, timedOut: Boolean): List<CompEvent> {
        _results.add(
            CompEndResult(endNumber, isPractice, shootStartMs, endMs, arrowsInEnd, timedOut, 0)
        )
        val out = ArrayList<CompEvent>(2)
        out.add(if (timedOut) CompEvent.TIME_UP else CompEvent.END_FULL)
        if (endNumber >= totalEnds) {
            phase = CompPhase.DONE
            out.add(CompEvent.MATCH_DONE)
        } else {
            phase = CompPhase.REST
            phaseStartMs = endMs
        }
        return out
    }

    /** 休息中按「下一趟」（或自動）。不在休息階段時什麼都不做。 */
    fun next(nowMs: Long): List<CompEvent> {
        if (phase != CompPhase.REST) return emptyList()
        endNumber++
        return beginEnd(nowMs)
    }

    /** 提早結束比賽（例如使用者按停止）。射擊中且已有箭時，這一趟照現況算一趟。 */
    fun finishNow(nowMs: Long) {
        if (phase == CompPhase.SHOOTING && arrowsInEnd > 0) closeEnd(nowMs, timedOut = false)
        phase = CompPhase.DONE
    }

    companion object {
        /** 時間到後多久內射出的箭才算逾時箭（再久就是下一趟的事了） */
        const val LATE_WINDOW_MS = 30_000L
    }
}

/** 記在紀錄裡的比賽設定，事後統計用。 */
data class CompMeta(val secondsPerEnd: Int, val arrowsPerEnd: Int, val practiceEnds: Int)

/** 事後統計的一趟。 */
data class CompEndSummary(
    val label: String,
    val practice: Boolean,
    val arrows: Int,
    val usedMs: Long,
    val timedOut: Boolean,
    val lateArrows: Int,
    val accelAvg: Float?,
    val score: Int?,
)

data class CompSummary(
    val ends: List<CompEndSummary>,
    val totalArrows: Int,
    val lateArrows: Int,
    val timedOutEnds: Int,
    val scoreTotal: Int?,
)

object CompSummaryBuilder {
    /**
     * 比賽紀錄的事後統計。每趟的時間範圍存在 [Session.marks]（標籤 "P1"、"P2"… 是練習趟，"1"、"2"… 是正式趟），
     * 箭數和晃動量從範圍內的有效箭算出；範圍結束到下一趟開始之間的箭是逾時箭。
     * 練習趟的箭數不計入總箭數與總環數。
     */
    fun build(s: Session): CompSummary? {
        val meta = s.comp ?: return null
        val valid = s.validShots()
        val marks = s.marks.sortedBy { it.startRelMs }
        val rows = marks.mapIndexed { i, m ->
            val end = m.endRelMs ?: s.shots.lastOrNull()?.relMs ?: m.startRelMs
            val nextStart = marks.getOrNull(i + 1)?.startRelMs ?: Long.MAX_VALUE
            val inRange = valid.filter { it.relMs in m.startRelMs..end }
            val late = valid.count { it.relMs > end && it.relMs < nextStart && it.relMs - end < CompetitionRound.LATE_WINDOW_MS }
            val used = (end - m.startRelMs).coerceAtLeast(0L)
            val scores = inRange.mapNotNull { it.score }
            CompEndSummary(
                label = m.label,
                practice = m.label.startsWith("P"),
                arrows = inRange.size,
                usedMs = used,
                timedOut = used >= meta.secondsPerEnd * 1000L,
                lateArrows = late,
                accelAvg = avgOrNull(inRange.map { it.accelRms }),
                score = if (scores.isEmpty()) null else scores.sum(),
            )
        }
        val formal = rows.filter { !it.practice }
        val scored = formal.mapNotNull { it.score }
        return CompSummary(
            ends = rows,
            totalArrows = formal.sumOf { it.arrows },
            lateArrows = formal.sumOf { it.lateArrows },
            timedOutEnds = formal.count { it.timedOut },
            scoreTotal = if (scored.isEmpty()) null else scored.sum(),
        )
    }
}

/** 給畫面用的比賽狀態快照。 */
data class CompUi(
    val phase: CompPhase,
    val practice: Boolean,
    /** 目前這一趟在顯示用的編號（練習趟 1..P，正式趟 1..N） */
    val number: Int,
    val formalEnds: Int,
    val practiceEnds: Int,
    val arrows: Int,
    val arrowsPerEnd: Int,
    val remainingMs: Long,
    val secondsPerEnd: Int,
    val formatId: Int,
    val autoNext: Boolean,
    /** 最近一趟結束的結果（休息畫面顯示用） */
    val lastEnd: CompEndResult?,
)

fun CompetitionRound.ui(nowMs: Long): CompUi = CompUi(
    phase = phase,
    practice = isPractice,
    number = displayNumber,
    formalEnds = format.ends,
    practiceEnds = practiceEnds,
    arrows = arrowsInEnd,
    arrowsPerEnd = format.arrowsPerEnd,
    remainingMs = remainingMs(nowMs),
    secondsPerEnd = format.secondsPerEnd,
    formatId = format.id,
    autoNext = autoNextSec > 0,
    lastEnd = results.lastOrNull(),
)

/** 一趟結果存進記錄時的標籤："P1" 是練習趟，"1" 是正式趟。 */
fun CompetitionRound.markLabel(r: CompEndResult): String =
    if (r.practice) "P${r.number}" else "${r.number - practiceEnds}"
