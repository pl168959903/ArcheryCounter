package com.zgrcc.archeryassistant

/** 可疑記錄的種類（v1.19）。 */
enum class SuspectKind { INTERRUPTED, NO_ARROWS, TOO_MANY }

/**
 * 標示可疑記錄：①沒有正常結束；②結束了但超過 2 分鐘一箭都沒偵測到；
 * ③箭數超過其他記錄中位數的 2 倍（至少 [MIN_SAMPLE] 筆比較、且至少 [MIN_ARROWS] 箭）。校準與進行中的不判斷。純 Kotlin。
 */
object Suspicion {
    const val MIN_SAMPLE = 5
    const val MIN_ARROWS = 10
    const val NO_ARROW_MS = 120_000L

    /** 其他記錄的箭數中位數；樣本不足回傳 null。 */
    fun median(all: List<Session>): Int? {
        val counts = all.filter { !it.calibration && it.endMs != null && it.finalCount() > 0 }.map { it.finalCount() }.sorted()
        if (counts.size < MIN_SAMPLE) return null
        return counts[counts.size / 2]
    }

    fun check(s: Session, median: Int?, isActive: Boolean): SuspectKind? {
        if (s.calibration || isActive) return null
        if (s.endMs == null) return SuspectKind.INTERRUPTED
        val n = s.finalCount()
        if (n == 0 && s.durationMs() > NO_ARROW_MS) return SuspectKind.NO_ARROWS
        if (median != null && n >= MIN_ARROWS && n > median * 2) return SuspectKind.TOO_MANY
        return null
    }

    fun flags(all: List<Session>, activeId: String?): Map<String, SuspectKind> {
        val m = median(all)
        val out = LinkedHashMap<String, SuspectKind>()
        for (s in all) check(s, m, s.id == activeId)?.let { out[s.id] = it }
        return out
    }

    fun text(k: SuspectKind): String = when (k) {
        SuspectKind.INTERRUPTED -> tr("可疑：這筆記錄沒有正常結束（中斷）", "Suspicious: this record did not end normally")
        SuspectKind.NO_ARROWS -> tr("可疑：超過 2 分鐘卻一箭都沒偵測到，可能漏判或沒放好", "Suspicious: over 2 minutes with no detected arrows; possibly missed detections")
        SuspectKind.TOO_MANY -> tr("可疑：箭數是其他記錄中位數的 2 倍以上，可能有誤判", "Suspicious: more than twice the median arrow count; possibly false detections")
    }

    fun shortText(k: SuspectKind): String = when (k) {
        SuspectKind.INTERRUPTED -> tr("可疑：中斷", "Suspicious: interrupted")
        SuspectKind.NO_ARROWS -> tr("可疑：沒偵測到箭", "Suspicious: no arrows")
        SuspectKind.TOO_MANY -> tr("可疑：箭數偏多", "Suspicious: many arrows")
    }
}

/** 記錄頁篩選（v1.19）：標籤與日期範圍。只影響列表。純 Kotlin。 */
object HistoryFilter {
    val DAY_OPTIONS = listOf(0, 7, 30)
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun tags(sessions: List<Session>): List<String> = sessions.flatMap { it.tags }.distinct().sorted()

    fun nextDays(cur: Int): Int {
        val i = DAY_OPTIONS.indexOf(cur)
        return if (i < 0) 0 else DAY_OPTIONS[(i + 1) % DAY_OPTIONS.size]
    }

    /** 下一個標籤：「」（全部）→ 各標籤 → 回到全部；目前的標籤已不存在就回到全部。 */
    fun nextTag(cur: String, all: List<String>): String {
        val order = listOf("") + all
        val i = order.indexOf(cur)
        return if (i < 0) "" else order[(i + 1) % order.size]
    }

    fun active(tag: String, days: Int) = tag.isNotEmpty() || days > 0

    fun apply(all: List<Session>, tag: String, days: Int, nowMs: Long): List<Session> =
        all.filter { (tag.isEmpty() || tag in it.tags) && (days <= 0 || it.startMs >= nowMs - days * DAY_MS) }

    fun daysLabel(days: Int): String = if (days <= 0) tr("全部日期", "All dates") else tr("最近 $days 天", "Last $days days")
}
