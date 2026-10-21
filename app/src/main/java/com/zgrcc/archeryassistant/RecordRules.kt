package com.zgrcc.archeryassistant

/** 可疑記錄的種類（v1.19；v1.20.1 拿掉「箭數偏多」，新增「有待確認的可疑箭」）。 */
enum class SuspectKind { INTERRUPTED, NO_ARROWS, SHOTS }

/** 一筆記錄的可疑結果：[kind] 是原因，[shots] 是還沒確認的可疑箭數（沒開「標示可疑的箭」時為 0）。 */
data class Suspect(val kind: SuspectKind, val shots: Int = 0)

/**
 * 標示可疑記錄：①沒有正常結束；②結束了但超過 2 分鐘一箭都沒偵測到；③有還沒確認的可疑箭（見 [FastShot]）。
 * 箭數多不再算可疑。校準與進行中的不判斷。純 Kotlin。
 */
object Suspicion {
    const val NO_ARROW_MS = 120_000L

    fun check(s: Session, isActive: Boolean, shotsOn: Boolean = true): Suspect? {
        if (s.calibration || isActive) return null
        val n = if (shotsOn) FastShot.flagged(s).size else 0
        val kind = when {
            s.endMs == null -> SuspectKind.INTERRUPTED
            s.finalCount() == 0 && s.durationMs() > NO_ARROW_MS -> SuspectKind.NO_ARROWS
            n > 0 -> SuspectKind.SHOTS
            else -> return null
        }
        return Suspect(kind, n)
    }

    fun flags(all: List<Session>, activeId: String?, shotsOn: Boolean = true): Map<String, Suspect> {
        val out = LinkedHashMap<String, Suspect>()
        for (s in all) check(s, s.id == activeId, shotsOn)?.let { out[s.id] = it }
        return out
    }

    private fun shotsPart(n: Int) = tr("可疑箭 $n 支", "$n suspicious shot(s)")

    fun text(k: Suspect): String = when (k.kind) {
        SuspectKind.INTERRUPTED -> tr("可疑：這筆記錄沒有正常結束（中斷）", "Suspicious: this record did not end normally") +
            if (k.shots > 0) " · " + shotsPart(k.shots) else ""
        SuspectKind.NO_ARROWS -> tr("可疑：超過 2 分鐘卻一箭都沒偵測到，可能漏判或沒放好", "Suspicious: over 2 minutes with no detected arrows; possibly missed detections") +
            if (k.shots > 0) " · " + shotsPart(k.shots) else ""
        SuspectKind.SHOTS -> tr(
            "可疑：有 ${k.shots} 支箭待確認，進入詳情按「檢查可疑箭」逐支處理",
            "Suspicious: ${k.shots} shot(s) to review; open details and tap Review suspicious shots",
        )
    }

    fun shortText(k: Suspect): String = when (k.kind) {
        SuspectKind.INTERRUPTED -> tr("可疑：中斷", "Suspicious: interrupted") + if (k.shots > 0) " · " + shotsPart(k.shots) else ""
        SuspectKind.NO_ARROWS -> tr("可疑：沒偵測到箭", "Suspicious: no arrows") + if (k.shots > 0) " · " + shotsPart(k.shots) else ""
        SuspectKind.SHOTS -> tr("可疑箭 ${k.shots} 支", "${k.shots} suspicious shot(s)")
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
