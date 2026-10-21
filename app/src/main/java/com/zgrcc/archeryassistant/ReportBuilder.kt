package com.zgrcc.archeryassistant

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 報告頁首、頁尾要顯示的資訊。 */
data class ReportMeta(
    val appName: String = "Archery Assistant",
    val author: String,
    /** 例如 v1.16（24） */
    val versionLabel: String,
    /** 簽章指紋前幾組；讀不到為 "?" 或 null */
    val signature: String?,
    val signatureNote: String?,
    val generatedAtMs: Long,
    /** App 圖示（PNG）的 Base64，沒有就不顯示圖示 */
    val iconBase64: String?,
    /** 沒有分趟標記的記錄用它切趟 */
    val arrowsPerEnd: Int,
    val zone: ZoneId = ZoneId.systemDefault(),
    /** 只報告帶這個標籤的記錄（v1.18）；null 或空白 = 全部。 */
    val filterTag: String? = null,
    /** 記錄 id → 匯入來源裝置名稱（v1.18）；報告裡標示「來自 X」。 */
    val origins: Map<String, String> = emptyMap(),
)

/**
 * 把全部記錄做成一份可以在電腦瀏覽器直接打開的網頁報告（v1.16）。純邏輯，不依賴 Android。
 * 單一檔案、沒有腳本、沒有外部資源。數字都沿用 App 裡既有的統計，所以和手錶上看到的一致。
 */
object ReportBuilder {
    const val FILE_NAME = "report.html"

    fun build(sessions: List<Session>, meta: ReportMeta): String {
        val tag = meta.filterTag?.takeIf { it.isNotBlank() }
        val sorted = sessions.filter { tag == null || tag in it.tags }.sortedByDescending { it.startMs }
        val real = sorted.filter { !it.calibration }
        val matchRows = MatchCompare.rows(sorted, limit = 200)
        val bestScore = PersonalBest.bestScoreIds(matchRows)
        val bestSteadyMatch = PersonalBest.steadiestMatchIds(matchRows)
        val steadiest = PersonalBest.steadiestId(sorted)

        return buildString {
            append("<!DOCTYPE html>\n<html lang=\"${if (I18n.english) "en" else "zh-Hant"}\">\n<head>\n<meta charset=\"utf-8\">\n")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
            append("<meta name=\"generator\" content=\"${esc(meta.appName)} ${esc(meta.versionLabel)}\">\n")
            append("<title>${esc(meta.appName)} · ${esc(tr("訓練報告", "Training report"))}</title>\n")
            if (meta.iconBase64 != null) append("<link rel=\"icon\" href=\"data:image/png;base64,${meta.iconBase64}\">\n")
            append("<style>\n${ReportStyle.CSS}\n</style>\n</head>\n<body>\n")
            header(this, meta, sorted)
            append("<div class=\"wrap\">\n")
            if (sorted.isEmpty()) {
                append("<div class=\"card\">${esc(tr("還沒有任何記錄。", "No records yet."))}</div>\n")
            } else {
                overview(this, real, meta)
                sessionTable(this, sorted, meta, steadiest, bestScore, bestSteadyMatch)
                groups(this, sorted)
                matches(this, matchRows, bestScore, bestSteadyMatch, meta)
                append("<h2>${esc(tr("每次記錄", "Each session"))}</h2>\n")
                for (s in sorted) session(this, s, meta, steadiest in setOf(s.id), s.id in bestScore, s.id in bestSteadyMatch)
            }
            footer(this, meta)
            append("</div>\n</body>\n</html>\n")
        }
    }

    // ---------- 頁首／頁尾 ----------

    private fun header(sb: StringBuilder, meta: ReportMeta, sessions: List<Session>) {
        sb.append("<header class=\"hero\"><div class=\"wrap\">\n")
        if (meta.iconBase64 != null) {
            sb.append("<img src=\"data:image/png;base64,${meta.iconBase64}\" alt=\"${esc(meta.appName)}\">\n")
        }
        sb.append("<div><h1>${esc(meta.appName)}</h1>\n")
        sb.append("<div class=\"sub\">${esc(tr("射箭訓練與比賽報告", "Archery training and match report"))}</div>\n")
        sb.append("<div class=\"badges\">")
        sb.append("<span class=\"badge\">${esc(tr("作者", "Author"))} <b>${esc(meta.author)}</b></span>")
        sb.append("<span class=\"badge\">${esc(tr("版本", "Version"))} <b>${esc(meta.versionLabel)}</b></span>")
        val sig = meta.signature
        if (sig != null && sig != "?") sb.append("<span class=\"badge\">${esc(tr("簽章", "Signature"))} <b>${esc(sig)}</b></span>")
        sb.append("<span class=\"badge\">${esc(tr("產生於", "Generated"))} <b>${esc(fullDate(meta.generatedAtMs, meta.zone))}</b></span>")
        sb.append("<span class=\"badge\">${esc(tr("記錄", "Records"))} <b>${sessions.size}</b></span>")
        meta.filterTag?.takeIf { it.isNotBlank() }?.let {
            sb.append("<span class=\"badge\">${esc(tr("篩選標籤", "Filtered by tag"))} <b>${esc(it)}</b></span>")
        }
        sb.append("</div></div>\n</div></header>\n")
    }

    private fun footer(sb: StringBuilder, meta: ReportMeta) {
        sb.append("<footer>")
        if (meta.iconBase64 != null) sb.append("<img src=\"data:image/png;base64,${meta.iconBase64}\" alt=\"\">")
        sb.append(esc(tr("由 ${meta.appName} ${meta.versionLabel} 在手錶上產生 · 作者 ${meta.author}", "Generated on the watch by ${meta.appName} ${meta.versionLabel} · Author ${meta.author}")))
        val note = meta.signatureNote
        if (meta.signature != null && meta.signature != "?" && note != null) {
            sb.append("<br>${esc(tr("簽章", "Signature"))} ${esc(meta.signature)} · ${esc(note)}")
        }
        sb.append("<br>${esc(tr("報告是產生當下的快照，之後的記錄需要重新匯出。", "This report is a snapshot; export again to include later records."))}")
        sb.append("</footer>\n")
    }

    // ---------- 總覽 ----------

    private fun origin(meta: ReportMeta, id: String): String =
        meta.origins[id]?.let { "<br><span class=\"star\">${esc(tr("來自 $it", "From $it"))}</span>" } ?: ""

    private fun kpi(sb: StringBuilder, value: String, label: String) {
        sb.append("<div class=\"kpi\"><div class=\"v\">${esc(value)}</div><div class=\"l\">${esc(label)}</div></div>")
    }

    private fun overview(sb: StringBuilder, real: List<Session>, meta: ReportMeta) {
        sb.append("<h2>${esc(tr("總覽", "Overview"))}</h2>\n")
        val arrows = real.sumOf { computeStats(it).finalCount }
        val shake = avgOrNull(real.flatMap { s -> s.validShots().map { it.accelRms } })
        val days = Overview.perDay(real, meta.zone).size
        val streak = Overview.streak(real, meta.generatedAtMs, meta.zone)
        sb.append("<div class=\"kpis\">")
        kpi(sb, real.size.toString(), tr("練習與比賽（次）", "Sessions"))
        kpi(sb, arrows.toString(), tr("總箭數", "Total arrows"))
        kpi(sb, days.toString(), tr("練習天數", "Practice days"))
        kpi(sb, streak.toString(), tr("連續練習（天）", "Streak (days)"))
        kpi(sb, Fmt.numOrDash(shake, 2), tr("平均晃動 m/s²", "Avg shake m/s²"))
        sb.append("</div>\n")
        val weeks = Overview.weeks(real, meta.generatedAtMs, meta.zone)
        if (weeks.any { it.arrows > 0 }) {
            sb.append("<div class=\"card\"><b>${esc(tr("最近 ${Overview.WEEKS} 週的箭數", "Arrows in the last ${Overview.WEEKS} weeks"))}</b>")
            sb.append(ReportCharts.bars(tr("每週箭數", "Arrows per week"), weeks.map { "${it.start.monthValue}/${it.start.dayOfMonth}" }, weeks.map { it.arrows }))
            val delta = Overview.weekDelta(weeks)
            if (delta != null) {
                val text = if (delta == 0) tr("本週和上週一樣多", "This week equals last week")
                else tr("本週比上週${if (delta > 0) "多" else "少"} ${kotlin.math.abs(delta)} 箭", "This week is ${kotlin.math.abs(delta)} arrows ${if (delta > 0) "more" else "fewer"} than last week")
                sb.append("<div class=\"note\">${esc(text)}（${esc(tr("週從週一算起", "weeks start on Monday"))}）</div>")
            }
            sb.append("</div>\n")
        }
    }

    // ---------- 記錄總表 ----------

    private fun typeTag(s: Session): String = when {
        s.calibration -> "<span class=\"tag cal\">${esc(tr("校準", "Calibration"))}</span>"
        s.comp != null -> "<span class=\"tag match\">${esc(tr("比賽", "Match"))}</span>"
        else -> "<span class=\"tag\">${esc(tr("練習", "Practice"))}</span>"
    }

    private fun sessionTable(
        sb: StringBuilder, sessions: List<Session>, meta: ReportMeta,
        steadiest: String?, bestScore: Set<String>, bestSteady: Set<String>,
    ) {
        sb.append("<h2>${esc(tr("記錄一覽", "All sessions"))}</h2>\n<div class=\"card scroll\"><table>\n")
        sb.append("<tr><th class=\"l\">${esc(tr("日期", "Date"))}</th><th class=\"l\">${esc(tr("類型", "Type"))}</th><th class=\"l\">${esc(tr("條件", "Conditions"))}</th>")
        sb.append("<th>${esc(tr("箭數", "Arrows"))}</th><th>${esc(tr("時長", "Duration"))}</th><th>${esc(tr("平均晃動", "Avg shake"))}</th><th>${esc(tr("平均環數", "Avg score"))}</th></tr>\n")
        for (s in sessions) {
            val st = computeStats(s)
            val star = if (s.id == steadiest || s.id in bestScore || s.id in bestSteady) "<span class=\"star\">★</span> " else ""
            sb.append("<tr><td class=\"l\">$star<a href=\"#s-${esc(s.id)}\">${esc(fullDate(s.startMs, meta.zone))}</a></td>")
            sb.append("<td class=\"l\">${typeTag(s)}</td>")
            sb.append("<td class=\"l\">${esc(Tags.label(s.tags.filter { it != "比賽" }) ?: "—")}${origin(meta, s.id)}</td>")
            sb.append("<td>${st.finalCount}</td><td>${esc(Fmt.duration(s.durationMs()))}</td>")
            sb.append("<td>${if (s.calibration) "—" else Fmt.numOrDash(st.accelAvg, 2)}</td><td>${Fmt.numOrDash(st.scoreAvg, 1)}</td></tr>\n")
        }
        sb.append("</table></div>\n<div class=\"note\">${esc(tr("★ 表示個人最佳：最穩的練習，或同賽制下環數最高、最穩的比賽。晃動越低越穩。", "★ marks a personal best: the steadiest practice, or the highest-scoring / steadiest match in the same format. Lower shake is steadier."))}</div>\n")
    }

    // ---------- 條件對比與比賽對比 ----------

    private fun groups(sb: StringBuilder, sessions: List<Session>) {
        val rows = GroupCompare.groups(sessions)
        if (rows.isEmpty()) return
        val best = GroupCompare.steadiest(rows)
        sb.append("<h2>${esc(tr("條件對比", "Compare conditions"))}</h2>\n<div class=\"card scroll\"><table>\n")
        sb.append("<tr><th class=\"l\">${esc(tr("距離與弓種", "Distance and bow"))}</th><th>${esc(tr("次數", "Sessions"))}</th><th>${esc(tr("箭數", "Arrows"))}</th><th>${esc(tr("平均晃動", "Avg shake"))}</th><th>${esc(tr("平均環數", "Avg score"))}</th></tr>\n")
        for (g in rows) {
            val star = if (best != null && g.tags == best.tags) "<span class=\"star\">★</span> " else ""
            sb.append("<tr><td class=\"l\">$star${esc(GroupCompare.title(g.tags))}</td><td>${g.sessions}</td><td>${g.arrows}</td><td>${Fmt.numOrDash(g.accelAvg, 2)}</td><td>${Fmt.numOrDash(g.scoreAvg, 1)}</td></tr>\n")
        }
        sb.append("</table></div>\n<div class=\"note\">${esc(tr("★ 是最穩的一組（至少 2 組、每組 ${GroupCompare.MIN_ARROWS_FOR_BEST} 箭以上才標示）。", "★ marks the steadiest group (shown only with 2+ groups of ${GroupCompare.MIN_ARROWS_FOR_BEST}+ arrows)."))}</div>\n")
    }

    private fun matches(sb: StringBuilder, rows: List<MatchRow>, bestScore: Set<String>, bestSteady: Set<String>, meta: ReportMeta) {
        if (rows.isEmpty()) return
        sb.append("<h2>${esc(tr("比賽對比", "Match comparison"))}</h2>\n<div class=\"card scroll\"><table>\n")
        sb.append("<tr><th class=\"l\">${esc(tr("日期", "Date"))}</th><th class=\"l\">${esc(tr("賽制", "Format"))}</th><th>${esc(tr("總環數", "Total"))}</th><th>${esc(tr("平均晃動", "Avg shake"))}</th><th>${esc(tr("時間到趟數", "Timed-out ends"))}</th><th>${esc(tr("平均第一箭等待（秒）", "Avg first-arrow wait (s)"))}</th></tr>\n")
        for (r in rows.reversed()) {
            val star = if (r.id in bestScore || r.id in bestSteady) "<span class=\"star\">★</span> " else ""
            sb.append("<tr><td class=\"l\">$star<a href=\"#s-${esc(r.id)}\">${esc(fullDate(r.startMs, meta.zone))}</a></td>")
            sb.append("<td class=\"l\">${esc(tr("${r.arrowsPerEnd} 箭×${r.ends} 趟 · ${r.secondsPerEnd} 秒", "${r.arrowsPerEnd} arrows × ${r.ends} ends · ${r.secondsPerEnd} s"))}</td>")
            sb.append("<td>${r.scoreTotal ?: "—"}</td><td>${Fmt.numOrDash(r.accelAvg, 2)}</td><td>${r.timedOutEnds}</td><td>${Fmt.numOrDash(r.firstWaitAvg, 1)}</td></tr>\n")
        }
        sb.append("</table></div>\n")
    }

    // ---------- 單次記錄 ----------

    private fun session(sb: StringBuilder, s: Session, meta: ReportMeta, isSteadiest: Boolean, bestScore: Boolean, bestSteady: Boolean) {
        val st = computeStats(s)
        val valid = s.validShots()
        sb.append("<section class=\"card sess\" id=\"s-${esc(s.id)}\">\n")
        sb.append("<div class=\"head\"><span class=\"t\">${esc(fullDate(s.startMs, meta.zone))}</span><span>${typeTag(s)}")
        Tags.label(s.tags.filter { it != "比賽" })?.let { sb.append("<span class=\"tag\">${esc(it)}</span>") }
        meta.origins[s.id]?.let { sb.append("<span class=\"tag\">${esc(tr("來自 $it", "From $it"))}</span>") }
        sb.append("</span></div>\n")
        val stars = buildList {
            if (isSteadiest) add(tr("★ 個人最佳：最穩的一次練習", "★ Personal best: steadiest practice"))
            if (bestScore) add(tr("★ 同賽制最高環數", "★ Highest score in this format"))
            if (bestSteady) add(tr("★ 同賽制最穩", "★ Steadiest in this format"))
        }
        for (x in stars) sb.append("<div class=\"star\">${esc(x)}</div>")

        sb.append("<div class=\"kpis\">")
        kpi(sb, st.finalCount.toString(), tr("箭數", "Arrows"))
        kpi(sb, Fmt.duration(s.durationMs()), tr("時長", "Duration"))
        if (!s.calibration) {
            kpi(sb, Fmt.numOrDash(st.accelAvg, 2), tr("平均晃動 m/s²", "Avg shake m/s²"))
            if (s.hasGyro) kpi(sb, Fmt.numOrDash(st.gyroAvg, 1), tr("平均晃動 °/s", "Avg shake °/s"))
            kpi(sb, if (st.intervalAvgSec == null) "—" else tr("${Fmt.num(st.intervalAvgSec, 0)} 秒", "${Fmt.num(st.intervalAvgSec, 0)} s"), tr("平均間隔", "Avg interval"))
            if (st.scoredCount > 0) kpi(sb, Fmt.numOrDash(st.scoreAvg, 1), tr("平均環數", "Avg score"))
        }
        sb.append("</div>\n")
        if (s.endMs == null) sb.append("<div class=\"note\">${esc(tr("這筆記錄沒有正常結束（中斷）。", "This record did not end normally (interrupted)."))}</div>\n")
        if (st.cancelled > 0) sb.append("<div class=\"note\">${esc(tr("另有 ${st.cancelled} 筆被標為誤判，已排除。", "${st.cancelled} detections were marked as false and excluded."))}</div>\n")

        if (s.calibration) {
            calibrationBlock(sb, s)
        } else {
            statsTable(sb, s, st)
            val accel = metricSeries(s, gyro = false)
            sb.append(ReportCharts.line(tr("加速度晃動", "Accel shake"), "m/s²", accel, st.accelSteadiest, st.accelShakiest, 2))
            if (s.hasGyro) {
                val gyro = metricSeries(s, gyro = true)
                sb.append(ReportCharts.line(tr("角速度晃動", "Gyro shake"), "°/s", gyro, st.gyroSteadiest, st.gyroShakiest, 1, second = true))
            }
            if (s.comp != null) competitionBlock(sb, s)
            else endsBlock(sb, s, meta)
            feelBlock(sb, s)
            shotsTable(sb, s, meta)
        }
        if (valid.isEmpty() && !s.calibration) sb.append("<div class=\"note\">${esc(tr("這筆記錄沒有偵測到箭。", "No arrows were detected in this record."))}</div>\n")
        sb.append("</section>\n")
    }

    private fun row(sb: StringBuilder, label: String, value: String) {
        sb.append("<tr><td class=\"l\">${esc(label)}</td><td>${esc(value)}</td></tr>\n")
    }

    private fun statsTable(sb: StringBuilder, s: Session, st: SessionStats) {
        if (st.detected == 0) return
        sb.append("<h3>${esc(tr("統計", "Statistics"))}</h3>\n<div class=\"scroll\"><table style=\"max-width:520px\">\n")
        row(sb, tr("峰值（平均）", "Peak (avg)"), "${Fmt.numOrDash(st.peakAvg, 1)} m/s²")
        row(sb, tr("峰值（最大／最小）", "Peak (max / min)"), "${Fmt.numOrDash(st.peakMax, 1)} / ${Fmt.numOrDash(st.peakMin, 1)}")
        if (st.thresholdMin == st.thresholdMax) row(sb, tr("閾值", "Threshold"), Fmt.num(st.thresholdMin, 0))
        else row(sb, tr("閾值（中途有調整）", "Threshold (changed during the session)"), "${Fmt.num(st.thresholdMin, 0)} → ${Fmt.num(st.thresholdMax, 0)}")
        if (st.followAvg != null) row(sb, tr("放箭後晃動（加速度）", "Shake after release (accel)"), "${Fmt.num(st.followAvg)} m/s²")
        if (s.hasGyro && st.followGyroAvg != null) row(sb, tr("放箭後晃動（角速度）", "Shake after release (gyro)"), "${Fmt.num(st.followGyroAvg, 1)} °/s")
        st.accelTrend?.let { row(sb, tr("加速度晃動 前 1/3 → 後 1/3", "Accel shake first → last third"), "${Fmt.num(it.first)} → ${Fmt.num(it.last)}") }
        st.gyroTrend?.let { if (s.hasGyro) row(sb, tr("角速度晃動 前 1/3 → 後 1/3", "Gyro shake first → last third"), "${Fmt.num(it.first, 1)} → ${Fmt.num(it.last, 1)}") }
        if (st.scoredCount > 0) {
            row(sb, tr("環數平均", "Score avg"), "${Fmt.numOrDash(st.scoreAvg, 1)}（${st.scoredCount} ${tr("箭", "arrows")}）")
            st.scoreAccelCorr?.let { row(sb, tr("晃動與環數的相關係數", "Shake vs score correlation"), Fmt.num(it, 2)) }
        }
        sb.append("</table></div>\n<div class=\"note\">${esc(tr("晃動越低越穩；趨勢是前 1/3 與後 1/3 的平均，箭數至少 6 箭才計算。", "Lower shake is steadier; the trend compares the first and last third and needs at least 6 arrows."))}</div>\n")
    }

    private fun endsBlock(sb: StringBuilder, s: Session, meta: ReportMeta) {
        val ends = computeEnds(s, meta.arrowsPerEnd)
        if (ends.size < 2) return
        sb.append("<h3>${esc(tr("分趟", "Ends"))}</h3>\n<div class=\"scroll\"><table>\n")
        sb.append("<tr><th class=\"l\">${esc(tr("趟", "End"))}</th><th>${esc(tr("箭", "Shots"))}</th><th>${esc(tr("加速度晃動", "Accel shake"))}</th><th>${esc(tr("角速度晃動", "Gyro shake"))}</th><th>${esc(tr("環數", "Score"))}</th></tr>\n")
        for (e in ends) {
            sb.append("<tr><td class=\"l\">${e.number}</td><td>${e.firstShot}–${e.lastShot}</td><td>${Fmt.numOrDash(e.accelAvg, 2)}</td><td>${if (s.hasGyro) Fmt.numOrDash(e.gyroAvg, 1) else "—"}</td><td>${e.scoreTotal ?: "—"}</td></tr>\n")
        }
        sb.append("</table></div>\n")
    }

    private fun endLabel(label: String): String =
        if (label.startsWith("P")) tr("練習 ${label.drop(1)}", "Practice ${label.drop(1)}") else tr("第 $label 趟", "End $label")

    private fun competitionBlock(sb: StringBuilder, s: Session) {
        val meta = s.comp ?: return
        val sum = CompSummaryBuilder.build(s) ?: return
        val pace = PaceAnalyzer.analyze(s)
        sb.append("<h3>${esc(tr("比賽統計", "Match summary"))}</h3>\n")
        sb.append("<div class=\"note\">${esc(tr("${meta.arrowsPerEnd} 箭／趟 · 每趟 ${meta.secondsPerEnd} 秒 · 練習 ${meta.practiceEnds} 趟。總箭數 ${sum.totalArrows}，時間到 ${sum.timedOutEnds} 趟，逾時箭 ${sum.lateArrows}${if (sum.scoreTotal != null) "，總環數 ${sum.scoreTotal}" else ""}。練習趟不計入總數。", "${meta.arrowsPerEnd} arrows per end · ${meta.secondsPerEnd} s per end · ${meta.practiceEnds} practice ends. Total ${sum.totalArrows} arrows, ${sum.timedOutEnds} timed-out ends, ${sum.lateArrows} late arrows${if (sum.scoreTotal != null) ", total score ${sum.scoreTotal}" else ""}. Practice ends are not counted."))}</div>\n")
        sb.append("<div class=\"scroll\"><table>\n<tr><th class=\"l\">${esc(tr("趟", "End"))}</th><th>${esc(tr("箭", "Arrows"))}</th><th>${esc(tr("用時", "Used"))}</th><th>${esc(tr("剩餘", "Left"))}</th>")
        sb.append("<th>${esc(tr("第一箭等待", "First wait"))}</th><th>${esc(tr("加速度晃動", "Accel shake"))}</th><th>${esc(tr("環數", "Score"))}</th><th>${esc(tr("狀態", "Status"))}</th></tr>\n")
        for (e in sum.ends) {
            val p = pace?.ends?.firstOrNull { it.label == e.label }
            val left = meta.secondsPerEnd - e.usedMs / 1000f
            val status = buildList {
                if (e.timedOut) add(tr("時間到", "Timed out"))
                if (p != null && p.lastRemainSec != null && p.lastRemainSec <= PaceAnalyzer.TIGHT_SEC) add(tr("壓線", "Tight"))
                if (e.lateArrows > 0) add(tr("逾時 ${e.lateArrows} 箭", "${e.lateArrows} late"))
            }.joinToString("、").ifEmpty { "—" }
            sb.append("<tr><td class=\"l\">${esc(endLabel(e.label))}</td><td>${e.arrows}</td><td>${esc(Fmt.duration(e.usedMs))}</td>")
            sb.append("<td>${if (e.timedOut) "0" else Fmt.num(left.coerceAtLeast(0f), 0)} ${esc(tr("秒", "s"))}</td>")
            sb.append("<td>${if (p?.firstWaitSec != null) Fmt.num(p.firstWaitSec, 1) + " " + esc(tr("秒", "s")) else "—"}</td>")
            sb.append("<td>${Fmt.numOrDash(e.accelAvg, 2)}</td><td>${e.score ?: "—"}</td><td>${esc(status)}</td></tr>\n")
        }
        sb.append("</table></div>\n")
        if (pace != null && !pace.ends.isEmpty()) {
            sb.append("<h3>${esc(tr("節奏分析（正式趟）", "Pace analysis (scored ends)"))}</h3>\n<div class=\"scroll\"><table style=\"max-width:560px\">\n")
            if (pace.firstWaitAvg != null) {
                row(sb, tr("平均第一箭等待", "Avg first-arrow wait"), "${Fmt.num(pace.firstWaitAvg, 1)} ${tr("秒", "s")}" + (pace.slowestEnd?.let { "（${tr("最久：", "longest: ")}${endLabel(it)}）" } ?: ""))
            }
            if (pace.lastRemainMin != null) {
                row(sb, tr("最後一箭最少剩餘", "Least time left on the last arrow"), "${Fmt.num(pace.lastRemainMin, 1)} ${tr("秒", "s")}" + (pace.tightestEnd?.let { "（${endLabel(it)}）" } ?: ""))
            }
            row(sb, tr("壓線趟數（剩 ${PaceAnalyzer.TIGHT_SEC.toInt()} 秒內）", "Tight ends (≤${PaceAnalyzer.TIGHT_SEC.toInt()} s left)"), pace.tightEnds.toString())
            if (pace.usedAvg != null) row(sb, tr("每趟用時（平均／最長）", "Time used per end (avg / max)"), "${Fmt.num(pace.usedAvg, 0)} / ${Fmt.numOrDash(pace.usedMax, 0)} ${tr("秒", "s")}")
            sb.append("</table></div>\n")
        }
    }

    private fun feelBlock(sb: StringBuilder, s: Session) {
        val groups = FeelStats.groups(s)
        if (groups.isEmpty()) return
        sb.append("<h3>${esc(tr("自評與晃動", "Self-rating and shake"))}</h3>\n<div class=\"scroll\"><table style=\"max-width:420px\">\n")
        sb.append("<tr><th class=\"l\">${esc(tr("自評", "Rating"))}</th><th>${esc(tr("箭數", "Arrows"))}</th><th>${esc(tr("平均晃動 m/s²", "Avg shake m/s²"))}</th></tr>\n")
        for (g in groups) sb.append("<tr><td class=\"l\">${esc(Feel.label(g.feel))}</td><td>${g.count}</td><td>${Fmt.numOrDash(g.accelAvg, 2)}</td></tr>\n")
        sb.append("</table></div>\n")
    }

    private fun shotsTable(sb: StringBuilder, s: Session, meta: ReportMeta) {
        if (s.shots.isEmpty()) return
        sb.append("<details><summary>${esc(tr("逐箭明細（${s.shots.size} 筆）", "Shot by shot (${s.shots.size})"))}</summary>\n<div class=\"scroll\"><table>\n")
        sb.append("<tr><th class=\"l\">#</th><th class=\"l\">${esc(tr("時間", "Time"))}</th><th>${esc(tr("距上一箭", "Since previous"))}</th><th>${esc(tr("峰值", "Peak"))}</th><th>${esc(tr("加速度晃動", "Accel shake"))}</th>")
        if (s.hasGyro) sb.append("<th>${esc(tr("角速度晃動", "Gyro shake"))}</th>")
        sb.append("<th>${esc(tr("放箭後晃動", "After release"))}</th><th>${esc(tr("環數", "Score"))}</th><th>${esc(tr("自評", "Rating"))}</th></tr>\n")
        var no = 0
        var prev: Long? = null
        for (sh in s.shots) {
            val time = clock(s.startMs + sh.relMs, meta.zone)
            if (sh.cancelled) {
                sb.append("<tr class=\"cancel\"><td class=\"l\">—</td><td class=\"l\">${esc(time)}</td><td colspan=\"${if (s.hasGyro) 7 else 6}\" style=\"text-align:left\">${esc(tr("誤判（已排除）· 峰值 ${Fmt.num(sh.peak, 1)}", "False detection (excluded) · peak ${Fmt.num(sh.peak, 1)}"))}</td></tr>\n")
                continue
            }
            no++
            val since = prev?.let { Fmt.num((sh.relMs - it) / 1000f, 0) + " " + tr("秒", "s") } ?: "—"
            prev = sh.relMs
            sb.append("<tr><td class=\"l\">$no</td><td class=\"l\">${esc(time)}</td><td>${esc(since)}</td><td>${Fmt.num(sh.peak, 1)}</td><td>${Fmt.numOrDash(sh.accelRms, 2)}</td>")
            if (s.hasGyro) sb.append("<td>${Fmt.numOrDash(sh.gyroRms, 1)}</td>")
            sb.append("<td>${Fmt.numOrDash(sh.followAccelRms, 2)}</td><td>${sh.score ?: "—"}</td><td>${esc(Feel.valid(sh.feel)?.let { Feel.label(it) } ?: "—")}</td></tr>\n")
        }
        sb.append("</table></div></details>\n")
    }

    private fun calibrationBlock(sb: StringBuilder, s: Session) {
        sb.append("<div class=\"note\">${esc(tr("校準錄製不計入箭數、趨勢與統計。", "Calibration records are not counted in arrows, trends or statistics."))}</div>\n")
        if (s.marks.isEmpty()) return
        sb.append("<div class=\"scroll\"><table style=\"max-width:520px\">\n<tr><th class=\"l\">${esc(tr("動作", "Movement"))}</th><th>${esc(tr("開始", "Start"))}</th><th>${esc(tr("結束", "End"))}</th></tr>\n")
        for (m in s.marks) {
            val end = m.endRelMs
            sb.append("<tr><td class=\"l\">${esc(Calibration.display(m.label))}</td><td>${esc(Fmt.duration(m.startRelMs))}</td><td>${esc(if (end == null) tr("（未結束）", "(not finished)") else Fmt.duration(end))}</td></tr>\n")
        }
        sb.append("</table></div>\n")
    }

    // ---------- 時間格式 ----------

    private fun fullDate(ms: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone).format(Instant.ofEpochMilli(ms))

    private fun clock(ms: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone).format(Instant.ofEpochMilli(ms))
}
