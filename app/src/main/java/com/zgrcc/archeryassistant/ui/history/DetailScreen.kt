package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.launch

/** 從存檔讀記錄的結果。詳情頁和單箭頁共用。 */
internal sealed interface SessionLoad {
    object Loading : SessionLoad
    object Missing : SessionLoad
    class Ready(val session: Session) : SessionLoad
}

private fun buildTimeline(s: Session): List<TimelineRow> {
    val rows = ArrayList<TimelineRow>(s.shots.size + s.manual.size)
    var validCount = 0
    var prevValidRel: Long? = null
    s.shots.forEachIndexed { index, sh ->
        if (sh.cancelled) {
            rows.add(TimelineRow(sh.relMs, 0, index, sh, null, 0))
        } else {
            validCount++
            val interval = prevValidRel?.let { (sh.relMs - it) / 1000f }
            rows.add(TimelineRow(sh.relMs, validCount, index, sh, interval, 0))
            prevValidRel = sh.relMs
        }
    }
    s.manual.forEach { m -> rows.add(TimelineRow(m.relMs, 0, -1, null, null, m.delta)) }
    rows.sortBy { it.relMs }
    return rows
}

@Composable
fun DetailScreen(vm: CounterViewModel, onOpenShot: (Int) -> Unit, onDeleted: () -> Unit, onOpenReview: (Int) -> Unit, onOpenScoreEntry: (Int) -> Unit) {
    val id by vm.selectedId.collectAsStateWithLifecycle()
    val activeId by vm.activeSessionId.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()

    // 記錄列表有變動（例如在單箭頁標記了誤判）就重新讀一次
    val load by produceState<SessionLoad>(
        initialValue = SessionLoad.Loading,
        key1 = id,
        key2 = sessions,
    ) {
        val sid = id
        val session = if (sid == null) null else vm.loadSession(sid)
        value = if (session == null) SessionLoad.Missing else SessionLoad.Ready(session)
    }

    when (val l = load) {
        SessionLoad.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("載入中…", "Loading…"))
        }
        SessionLoad.Missing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("找不到這筆記錄", "Record not found"))
        }
        is SessionLoad.Ready -> DetailContent(
            vm = vm,
            session = l.session,
            isActive = l.session.id == activeId,
            onOpenShot = onOpenShot,
            onOpenReview = onOpenReview,
            onOpenScoreEntry = onOpenScoreEntry,
            onDelete = {
                vm.deleteSession(l.session.id)
                onDeleted()
            },
        )
    }
}

@Composable
private fun DetailContent(
    vm: CounterViewModel,
    session: Session,
    isActive: Boolean,
    onOpenShot: (Int) -> Unit,
    onOpenReview: (Int) -> Unit,
    onOpenScoreEntry: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val showTrend by vm.settings.showTrendChart.collectAsStateWithLifecycle()
    val showEnds by vm.settings.showEnds.collectAsStateWithLifecycle()
    val quickCheck by vm.settings.quickCheck.collectAsStateWithLifecycle()
    val arrowsPerEnd by vm.settings.arrowsPerEnd.collectAsStateWithLifecycle()
    val stats = remember(session) { computeStats(session) }
    val timeline = remember(session) { buildTimeline(session) }
    val autoEnds by vm.settings.autoEnds.collectAsStateWithLifecycle()
    val ends = remember(session, arrowsPerEnd, autoEnds) { endsFor(session, autoEnds, arrowsPerEnd) }
    val accelSeries = remember(session) { metricSeries(session, gyro = false) }
    val gyroSeries = remember(session) { metricSeries(session, gyro = true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val phone = rememberPhoneMode(vm)
    val tagsEnabled by vm.settings.tagsEnabled.collectAsStateWithLifecycle()
    val paceOn by vm.settings.paceAnalysis.collectAsStateWithLifecycle()
    val bestOn by vm.settings.personalBest.collectAsStateWithLifecycle()
    val calAdvice by vm.settings.calAdvice.collectAsStateWithLifecycle()
    val allSessions by vm.sessions.collectAsStateWithLifecycle()
    val isSteadiest = remember(allSessions, session.id, bestOn) {
        bestOn && !session.calibration && session.comp == null && PersonalBest.steadiestId(allSessions) == session.id
    }
    val matchStars = remember(allSessions, session.id, bestOn) {
        if (!bestOn || session.comp == null) emptyList<String>() else {
            val rows = MatchCompare.rows(allSessions)
            buildList {
                if (session.id in PersonalBest.bestScoreIds(rows)) add(tr("★ 同賽制最高環數", "★ Highest score in this format"))
                if (session.id in PersonalBest.steadiestMatchIds(rows)) add(tr("★ 同賽制最穩", "★ Steadiest in this format"))
            }
        }
    }
    val pace = remember(session, paceOn) { if (paceOn && !session.calibration) PaceAnalyzer.analyze(session) else null }
    val scope = rememberCoroutineScope()
    val noteOn by vm.settings.recordNote.collectAsStateWithLifecycle()
    val flagOn by vm.settings.flagSuspicious.collectAsStateWithLifecycle()
    val fastOn by vm.settings.flagFastShots.collectAsStateWithLifecycle()
    val recordScore by vm.settings.recordScore.collectAsStateWithLifecycle()
    val endChartOn by vm.settings.endChart.collectAsStateWithLifecycle()
    val scoreShakeOn by vm.settings.scoreShake.collectAsStateWithLifecycle()
    val scoreShake = remember(session, scoreShakeOn) { if (scoreShakeOn) ScoreShakeAnalysis.compute(session) else null }
    val posOn by vm.settings.positionTrend.collectAsStateWithLifecycle()
    val fastShots = remember(session, fastOn) { if (fastOn) FastShot.flagged(session) else emptySet() }
    val suspectKind = remember(session, flagOn, fastOn, isActive) {
        if (flagOn) Suspicion.check(session, isActive, fastOn) else null
    }
    val posRows = remember(session, posOn) { if (posOn) PositionTrend.rows(listOf(session)) else emptyList() }
    var noteDraft by remember(session.id, session.note) { mutableStateOf(session.note) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(Fmt.dateTime(session.startMs), style = MaterialTheme.typography.title3)
        }
        // 匯出這筆：只在手機畫面，放在最上面（箭很多時不用滑到底）。進行中的記錄不能匯出
        if (phone && !isActive) {
            item { ExportBlock(vm, setOf(session.id), single = true) }
        }
        if (session.calibration) {
            item { Body(tr("校準錄製（不計入箭數與趨勢）", "Calibration (not counted in arrows or trends)")) }
            if (session.marks.isNotEmpty()) {
                item {
                    Body(
                        session.marks.joinToString("\n") { m ->
                            val end = m.endRelMs
                            "${Calibration.display(m.label)}　${Fmt.duration(m.startRelMs)}～" + if (end == null) tr("（未結束）", "(not finished)") else Fmt.duration(end)
                        }
                    )
                }
            }
        }
        item { Body(summaryText(session, stats, isActive)) }
        RecordMeta.sourceLine(session).takeIf { it.isNotEmpty() }?.let { src ->
            item { Note(tr("來源：", "Source: ") + src) }
        }
        suspectKind?.let { k -> item { Note("⚠ " + Suspicion.text(k)) } }
        if (noteOn && !isActive) {
            item { Text(tr("備註", "Note"), style = MaterialTheme.typography.caption1) }
            item { LinkField(noteDraft, tr("寫一句話（最多 60 字）", "A short note (up to 60 chars)")) { noteDraft = it.take(60) } }
            if (noteDraft.trim() != session.note) {
                item {
                    Chip(
                        onClick = { scope.launch { vm.setSessionNote(session.id, noteDraft) } },
                        label = { Text(tr("儲存備註", "Save note"), maxLines = 1) },
                        colors = kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
        }
        if (isSteadiest) {
            item { Body(tr("★ 個人最佳：這是你最穩的一次練習（平均晃動最低）", "★ Personal best: your steadiest practice session (lowest average shake)")) }
        }
        if (matchStars.isNotEmpty()) {
            item { Body(matchStars.joinToString("\n")) }
        }
        if (ScreenRules.showCalibrationAdvice(calAdvice, session, isActive)) {
            item { CalibrationAdviceBlock(vm, session.id) }
        }

        // 比賽模式（v1.9）：逐趟用時、逾時、箭數與晃動變化
        if (!session.calibration && session.comp != null) {
            val compSum = CompSummaryBuilder.build(session)
            if (compSum != null) {
                item { Text(tr("比賽統計", "Match summary"), style = MaterialTheme.typography.title3) }
                item { Body(compSummaryText(compSum, session.comp)) }
            }
            if (pace != null) {
                item { Text(tr("節奏分析", "Pace analysis"), style = MaterialTheme.typography.title3) }
                item { Body(paceText(pace)) }
            }
        }

        // 條件標籤：記錄時自動帶上，這裡可以事後修改（進行中的不能改）
        if (!session.calibration && (tagsEnabled || session.tags.isNotEmpty())) {
            item { SectionTitle(tr("條件", "Conditions")) }
            if (isActive) {
                item { Body(Tags.label(session.tags) ?: tr("未設定", "Not set")) }
            } else {
                item {
                    Chip(
                        onClick = {
                            val tags = Tags.rebuild(session.tags, Tags.nextDistance(Tags.distanceOf(session.tags)), Tags.bowOf(session.tags))
                            scope.launch { vm.setSessionTags(session.id, tags) }
                        },
                        label = { Text(tr("距離", "Distance"), maxLines = 1) },
                        secondaryLabel = { Text(Tags.distanceOf(session.tags)?.let { Tags.display(it) } ?: tr("未設定", "Not set"), maxLines = 1) },
                        colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item {
                    Chip(
                        onClick = {
                            val tags = Tags.rebuild(session.tags, Tags.distanceOf(session.tags), Tags.nextBow(Tags.bowOf(session.tags)))
                            scope.launch { vm.setSessionTags(session.id, tags) }
                        },
                        label = { Text(tr("弓種", "Bow"), maxLines = 1) },
                        secondaryLabel = { Text(Tags.bowOf(session.tags)?.let { Tags.display(it) } ?: tr("未設定", "Not set"), maxLines = 1) },
                        colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item { Note(tr("點一下切換選項", "Tap to change")) }
            }
        }

        item { SectionTitle(tr("統計", "Statistics")) }
        item { Body(statsText(session, stats)) }
        scoreShake?.let { r ->
            item { SectionTitle(tr("環數與晃動", "Score vs shake")) }
            item { Body(ScoreShakeAnalysis.text(r)) }
        }

        if (showTrend) {
            item { SectionTitle(tr("趨勢圖", "Trend charts")) }
            if (accelSeries.size >= 2) {
                item {
                    TrendChart(
                        title = tr("加速度晃動量", "Accel shake"),
                        unit = "m/s²",
                        series = accelSeries,
                        steadiest = stats.accelSteadiest,
                        shakiest = stats.accelShakiest,
                        decimals = 2,
                    )
                }
            }
            if (session.hasGyro && gyroSeries.size >= 2) {
                item {
                    TrendChart(
                        title = tr("角速度晃動量", "Gyro shake"),
                        unit = "°/s",
                        series = gyroSeries,
                        steadiest = stats.gyroSteadiest,
                        shakiest = stats.gyroShakiest,
                        decimals = 1,
                    )
                }
            }
            if (accelSeries.size < 2 && gyroSeries.size < 2) {
                item { Note(tr("至少要 2 箭有晃動量數據才畫得出趨勢圖", "At least 2 shots with shake data are needed for a trend chart")) }
            }
        }

        if (showEnds && ends.isNotEmpty()) {
            item { SectionTitle(if (autoEnds) tr("逐趟（依間隔自動分趟）", "Per end (auto by gaps)") else tr("逐趟（每趟 $arrowsPerEnd 箭）", "Per end ($arrowsPerEnd arrows each)")) }
            if (autoEnds) item { Body(AutoEnds.summary(ends)) }
            ends.forEachIndexed { i, e ->
                item { Body(endText(session, e, ends.getOrNull(i - 1))) }
                if ((recordScore || e.scoreTotal != null) && !isActive && !session.calibration) {
                    item {
                        Chip(
                            onClick = { onOpenScoreEntry(EndScore.validToIndex(session, e.firstShot)) },
                            label = { Text(tr("輸入第 ${e.number} 趟環數", "Enter end ${e.number} scores") + navMark(), maxLines = 1) },
                            secondaryLabel = e.scoreTotal?.let { t -> { Text(tr("目前合計 $t", "Total so far $t"), maxLines = 1) } },
                            colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                }
            }
            val endPoints = ends.mapNotNull { e -> e.accelAvg?.let { ShotRank(e.number, it) } }
            if (endChartOn && endPoints.size >= 2) {
                val (best, worst) = extremes(endPoints.map { it.value })
                item {
                    TrendChart(
                        title = tr("各趟平均加速度晃動", "Average accel shake per end"),
                        unit = "m/s²",
                        series = endPoints,
                        steadiest = best,
                        shakiest = worst,
                        decimals = 2,
                        noun = tr("趟", "end"),
                    )
                }
            }
        }

        if (posRows.isNotEmpty()) {
            item { SectionTitle(tr("箭序趨勢", "Shot-order trend")) }
            item { Note(PositionTrend.explain()) }
            PositionTrend.hint(posRows)?.let { h -> item { Note(h) } }
            posRows.forEach { r -> item { Body(PositionTrend.rowText(r, session.hasGyro)) } }
        }

        item { SectionTitle(tr("逐箭明細", "Shot by shot")) }
        if (timeline.isEmpty()) {
            item { Note(tr("這次沒有偵測到放箭", "No release detected this time")) }
        } else {
            item { Note(tr("點一箭可看波形，也可以標記為誤判", "Tap a shot to see its waveform or mark it as a false detection")) }
            if (quickCheck && !isActive && !session.calibration && fastShots.isNotEmpty()) {
                item {
                    val labels = fastShots.sorted().map { i ->
                        (if (showEnds) shotEndLabel(session, ends, i) else null)
                            ?: tr("第 ${session.shots.take(i + 1).count { !it.cancelled }} 箭", "#${session.shots.take(i + 1).count { !it.cancelled }}")
                    }
                    val sub = if (labels.size <= 3) labels.joinToString("、")
                    else labels.take(3).joinToString("、") + tr(" 等共 ${labels.size} 支", " and ${labels.size - 3} more")
                    Chip(
                        onClick = { onOpenReview(fastShots.min()) },
                        label = { Text(tr("檢查可疑箭（${fastShots.size}）", "Review suspicious (${fastShots.size})") + navMark(), maxLines = 1) },
                        secondaryLabel = { Text(sub, maxLines = 2) },
                        colors = kindColors(ChipKind.SUSPECT, ChipDefaults.primaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
        }
        timeline.forEach { row ->
            item {
                val shot = row.shot
                if (shot == null) {
                    Body(manualText(session, row))
                } else {
                    Chip(
                        onClick = { onOpenShot(row.shotIndex) },
                        label = { Text((if (autoEnds && showEnds && row.shotNo > 0) AutoEnds.label(ends, row.shotNo)?.let { "[$it] " }.orEmpty() else "") + shotText(session, row, shot) + if (row.shotIndex in fastShots) "\n" + FastShot.shortText() else "", style = MaterialTheme.typography.caption1, maxLines = 5) },
                        colors = when {
                            shot.cancelled -> ChipDefaults.childChipColors()
                            row.shotIndex in fastShots -> kindColors(ChipKind.SUSPECT, ChipDefaults.primaryChipColors())
                            else -> kindColors(ChipKind.LIST, ChipDefaults.secondaryChipColors())
                        },
                        modifier = Modifier.fillMaxWidth(0.95f),
                    )
                }
            }
        }

        if (session.rawCsv) {
            item { Note(tr("原始資料在手錶上的 CSV 檔，用 adb 取出，檔名：${session.id}.csv", "Raw data is a CSV on the watch; pull it with adb. File: ${session.id}.csv")) }
        }

        // 刪除放在最底，並且要再確認一次
        item {
            when {
                isActive -> Note(tr("進行中，暫停偵測後才能刪除", "In progress; pause detection before deleting"))
                !confirmDelete -> Chip(
                    onClick = { confirmDelete = true },
                    label = { Text(tr("刪除此記錄", "Delete this record"), maxLines = 1) },
                    colors = kindColors(ChipKind.DANGER, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
                else -> Note(tr("確定要刪除這筆記錄嗎？無法復原", "Delete this record? This cannot be undone"))
            }
        }
        if (confirmDelete && !isActive) {
            item {
                Chip(
                    onClick = onDelete,
                    label = { Text(tr("確定刪除", "Delete"), maxLines = 1) },
                    colors = kindColors(ChipKind.DANGER, ChipDefaults.primaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Chip(
                    onClick = { confirmDelete = false },
                    label = { Text(tr("取消", "Cancel"), maxLines = 1) },
                    colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
    }
}
