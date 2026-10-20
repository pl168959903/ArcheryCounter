package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

@Composable
fun SettingsScreen(vm: CounterViewModel, onOpenCalibration: () -> Unit) {
    val s = vm.settings
    val scope = rememberCoroutineScope()

    val splash by s.splash.collectAsStateWithLifecycle()
    val splashTap by s.splashTap.collectAsStateWithLifecycle()
    val keepScreenOn by s.keepScreenOn.collectAsStateWithLifecycle()
    val wakeLock by s.wakeLock.collectAsStateWithLifecycle()
    val recordRaw by s.recordRaw.collectAsStateWithLifecycle()
    val windowSec by s.windowSec.collectAsStateWithLifecycle()
    val showTrend by s.showTrendChart.collectAsStateWithLifecycle()
    val recordWaveform by s.recordWaveform.collectAsStateWithLifecycle()
    val liveWaveform by s.liveWaveform.collectAsStateWithLifecycle()
    val showEnds by s.showEnds.collectAsStateWithLifecycle()
    val arrowsPerEnd by s.arrowsPerEnd.collectAsStateWithLifecycle()
    val endVibration by s.endVibration.collectAsStateWithLifecycle()
    val followThrough by s.followThrough.collectAsStateWithLifecycle()
    val showSessionTrend by s.showSessionTrend.collectAsStateWithLifecycle()
    val recordScore by s.recordScore.collectAsStateWithLifecycle()
    val stillThreshold by s.stillThreshold.collectAsStateWithLifecycle()
    val cooldownSec by s.cooldownSec.collectAsStateWithLifecycle()
    val touchLock by s.touchLock.collectAsStateWithLifecycle()
    val tagsEnabled by s.tagsEnabled.collectAsStateWithLifecycle()
    val distanceTag by s.distanceTag.collectAsStateWithLifecycle()
    val bowTag by s.bowTag.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val threshold by vm.threshold.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val autoPause by s.autoPause.collectAsStateWithLifecycle()
    val autoPauseMin by s.autoPauseMin.collectAsStateWithLifecycle()
    val lowBattery by s.lowBattery.collectAsStateWithLifecycle()
    val dailyGoal by s.dailyGoal.collectAsStateWithLifecycle()
    val endFeedback by s.endFeedback.collectAsStateWithLifecycle()
    val dimWhenRunning by s.dimWhenRunning.collectAsStateWithLifecycle()
    val dimLevel by s.dimLevel.collectAsStateWithLifecycle()
    val language by s.language.collectAsStateWithLifecycle()
    val samplingHz by s.samplingHz.collectAsStateWithLifecycle()
    val measuredHz by vm.measuredHz.collectAsStateWithLifecycle()
    val fatigueAlert by s.fatigueAlert.collectAsStateWithLifecycle()
    val fatiguePct by s.fatiguePct.collectAsStateWithLifecycle()
    val compEnabled by s.compEnabled.collectAsStateWithLifecycle()
    val compFormat by s.compFormat.collectAsStateWithLifecycle()
    val compArrows by s.compArrows.collectAsStateWithLifecycle()
    val compEnds by s.compEnds.collectAsStateWithLifecycle()
    val compSeconds by s.compSeconds.collectAsStateWithLifecycle()
    val compPractice by s.compPractice.collectAsStateWithLifecycle()
    val compPrepare by s.compPrepare.collectAsStateWithLifecycle()
    val compRemind30 by s.compRemind30.collectAsStateWithLifecycle()
    val compRemind10 by s.compRemind10.collectAsStateWithLifecycle()
    val compAutoNext by s.compAutoNext.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshSessions() }

    // null = 還沒按過；true / false = 匯出成功 / 失敗
    var exportResult by remember { mutableStateOf<Boolean?>(null) }

    // 阈值建议：按下才計算；套用後顯示已套用
    var showAdvice by remember { mutableStateOf(false) }
    var adviceApplied by remember { mutableStateOf(false) }

    // 儲存空間：按下才查；刪除原始資料要再確認一次
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    var confirmRawDelete by remember { mutableStateOf(false) }
    var rawDeleteMessage by remember { mutableStateOf<String?>(null) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text(tr("設定", "Settings"), style = MaterialTheme.typography.caption1) }

        // ---- 一般 ----
        item { SectionTitle(tr("一般", "General")) }
        item {
            Chip(
                onClick = { s.setLanguage(I18n.next(language)) },
                label = { Text(tr("語言", "Language"), maxLines = 1) },
                secondaryLabel = { Text(I18n.label(language), maxLines = 1) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item { Note(tr("點一下切換：自動 → 繁體中文 → English。自動＝系統是中文就用中文，其他語言用英文。切換後會回到主畫面", "Tap to change: Auto → Traditional Chinese → English. Auto uses Chinese when the system language is Chinese, otherwise English. You return to the main screen after switching")) }

        item { ToggleChip(tr("偵測中觸控鎖", "Touch lock while detecting"), touchLock) { s.setTouchLock(it) } }
        item { Note(tr("開啟後，偵測中主畫面有「鎖定觸控」按鈕，鎖定時只顯示箭數，長按約 2 秒解鎖，避免拉弓、放箭時誤碰。立即生效", "When on, the main screen has a Lock touch button while detecting. Locked shows only the count; hold about 2 s to unlock, so draws and releases do not cause stray touches. Takes effect immediately")) }

        item { ToggleChip(tr("低電量提醒", "Low battery alert"), lowBattery) { s.setLowBattery(it) } }
        item { Note(tr("偵測中電量低於 15% 時震動一次，主畫面顯示「電量低」，只提醒不會自己動作。立即生效", "Vibrates once when the battery drops below 15% while detecting and shows Battery low on the main screen. Only a reminder. Takes effect immediately")) }

        item { ToggleChip(tr("開啟動畫", "Startup animation"), splash) { s.setSplash(it) } }
        item { Note(tr("開啟應用時先播放約 3 秒的動畫（圖示放大，並顯示版本、作者與簽章）。下次開啟應用時生效", "Plays a short animation when the app opens (icon, version, author and signature). Takes effect next launch")) }
        if (splash) {
            item { ToggleChip(tr("點按後才進入", "Tap to enter"), splashTap) { s.setSplashTap(it) } }
            item { Note(tr("開啟時動畫播完會停在畫面上，出現「點按螢幕進入」，點一下才進入主畫面（太久沒點會自己進入）。關閉則約 3 秒後自動進入。動畫中隨時點一下都會直接進入。下次開啟應用時生效", "The animation stays on screen and shows Tap to enter; tap to continue (it continues by itself after a long time). When off, it continues automatically after about 3 s. Tapping at any time skips it. Takes effect next launch")) }
        }

        item { ToggleChip(tr("保持亮屏", "Keep screen on"), keepScreenOn) { s.setKeepScreenOn(it) } }
        item { Note(tr("應用開著時螢幕不休眠，立即生效", "The screen stays on while the app is open. Takes effect immediately")) }

        item { ToggleChip(tr("CPU 喚醒鎖", "CPU wake lock"), wakeLock) { s.setWakeLock(it) } }
        item { Note(tr("偵測中保持 CPU 運作，即使螢幕被熄滅也繼續取資料。較耗電，立即生效", "Keeps the CPU running while detecting, even if the screen turns off. Uses more battery. Takes effect immediately")) }

        // ---- 偵測 ----
        item { SectionTitle(tr("偵測", "Detection")) }
        item {
            Chip(
                onClick = { s.setSamplingHz(SamplingRate.next(samplingHz)) },
                label = { Text(tr("取樣頻率", "Sampling rate"), maxLines = 1) },
                secondaryLabel = { Text("$samplingHz Hz", maxLines = 1) },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item {
            Note(
                tr(
                    "感測器每秒取樣幾次：50／100／200 Hz，預設 100。越高越能抓到放箭的細節，但越耗電、記錄檔越大。下次開始偵測時生效。" +
                        if (measuredHz > 0f) "上次實測 ${Fmt.num(measuredHz, 1)} Hz（系統可能給得比要求的慢或快）" else "偵測中會量測實際頻率",
                    "How many times per second the sensors are read: 50 / 100 / 200 Hz, default 100. Higher catches more detail of the release but uses more battery and bigger files. Takes effect the next time detection starts. " +
                        if (measuredHz > 0f) "Last measured ${Fmt.num(measuredHz, 1)} Hz (the system may deliver slower or faster than requested)" else "The real rate is measured while detecting",
                )
            )
        }
        item {
            StepRow(
                text = tr("靜止 $stillThreshold", "Still $stillThreshold"),
                onMinus = { s.setStillThreshold(stillThreshold - 1) },
                onPlus = { s.setStillThreshold(stillThreshold + 1) },
            )
        }
        item {
            Note(tr("放箭前要先靜止到合力低於此值（m/s²，預設 5）。調大較容易算進去，但容易誤判；調小較嚴格，可能漏判。立即生效", "Before a release the arm must be quieter than this (m/s², default 5). Higher counts more easily but may false-trigger; lower is stricter and may miss. Takes effect immediately"))
        }
        item {
            StepRow(
                text = tr("冷卻 $cooldownSec 秒", "Cooldown $cooldownSec s"),
                onMinus = { s.setCooldownSec(cooldownSec - 1) },
                onPlus = { s.setCooldownSec(cooldownSec + 1) },
            )
        }
        item { Note(tr("兩次計數之間至少間隔多久（預設 3 秒）。調短可能把一次放箭算成兩次；調長可能漏掉連續的兩箭。立即生效", "Minimum time between two counts (default 3 s). Shorter may count one release twice; longer may miss two quick arrows. Takes effect immediately")) }

        item { ToggleChip(tr("自動暫停", "Auto-pause"), autoPause) { s.setAutoPause(it) } }
        item { Note(tr("偵測中太久沒有放箭就自動暫停並震動，避免忘了按暫停而白白耗電。校準錄製不適用。立即生效", "Pauses and vibrates when no arrow is released for a while, so a forgotten session does not drain the battery. Not used in calibration or match mode. Takes effect immediately")) }
        if (autoPause) {
            item {
                StepRow(
                    text = tr("$autoPauseMin 分鐘", "$autoPauseMin min"),
                    onMinus = { s.setAutoPauseMin(autoPauseMin - AutoPause.STEP_MINUTES) },
                    onPlus = { s.setAutoPauseMin(autoPauseMin + AutoPause.STEP_MINUTES) },
                )
            }
            item { Note(tr("超過這麼久沒有放箭就暫停（5～30 分鐘）。中間休息比較久的話請調長", "Pause after this long without an arrow (5-30 min). Use a longer time if you rest for long")) }
        }

        item {
            Chip(
                onClick = {
                    showAdvice = !showAdvice
                    adviceApplied = false
                },
                label = { Text(tr("閾值建議", "Threshold advice"), maxLines = 1) },
                secondaryLabel = { Text(tr("目前閾值 ${threshold.toInt()}", "Current threshold ${threshold.toInt()}"), maxLines = 1) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        if (showAdvice) {
            val advice = adviseThreshold(sessions, threshold)
            item {
                Body(
                    when (advice.kind) {
                        AdviceKind.NOT_ENOUGH ->
                            tr("資料還不夠。目前有效 ${advice.validCount} 筆、誤判 ${advice.cancelledCount} 筆，", "Not enough data yet. Currently ${advice.validCount} valid and ${advice.cancelledCount} false detections, ") +
                                tr("至少要有效 $ADVICE_MIN_VALID 筆、誤判 $ADVICE_MIN_CANCELLED 筆。", "need at least $ADVICE_MIN_VALID valid and $ADVICE_MIN_CANCELLED false. ") +
                                tr("在記錄詳情裡把不是射箭的動作標為誤判，累積後這裡就能建議", "Mark non-shooting movements as false detections in record details, and advice will appear here")
                        AdviceKind.SUGGEST ->
                            tr("有效的箭峰值中位數 ${Fmt.numOrDash(advice.validMedian, 1)}，", "Median peak of valid arrows ${Fmt.numOrDash(advice.validMedian, 1)}, ") +
                                tr("誤判的 ${Fmt.numOrDash(advice.cancelledMedian, 1)}。\n", "false ones ${Fmt.numOrDash(advice.cancelledMedian, 1)}.\n") +
                                tr("建議閾值 ${advice.suggested}：可擋掉約 ${(advice.cancelledExcluded * 100).toInt()}% 的誤判，", "Suggested threshold ${advice.suggested}: blocks about ${(advice.cancelledExcluded * 100).toInt()}% of false detections, ") +
                                tr("保留約 ${(advice.validKept * 100).toInt()}% 的有效箭", "keeps about ${(advice.validKept * 100).toInt()}% of valid arrows")
                        AdviceKind.NOT_SEPARABLE ->
                            tr("有效和誤判的峰值混在一起，調閾值幫不上。可以試試調整上面的「靜止」條件", "Valid and false peaks overlap, so the threshold cannot help. Try adjusting the Still setting above")
                    }
                )
            }
            if (advice.kind == AdviceKind.SUGGEST && advice.suggested != null) {
                item {
                    Chip(
                        onClick = {
                            vm.setThreshold(advice.suggested.toFloat())
                            adviceApplied = true
                        },
                        label = { Text(if (adviceApplied) tr("已套用 ${advice.suggested}", "Applied ${advice.suggested}") else tr("套用 ${advice.suggested}", "Apply ${advice.suggested}"), maxLines = 1) },
                        colors = if (adviceApplied) ChipDefaults.secondaryChipColors() else ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
            item { Note(tr("只能建議調高來減少誤判；漏判（該算卻沒算到）看不出來，沒辦法建議調低。校準錄製不參與", "It can only suggest raising the threshold to cut false detections; missed arrows cannot be seen, so it never suggests lowering. Calibration is excluded")) }
        }

        // ---- 記錄 ----
        item { SectionTitle(tr("記錄", "Records")) }
        item { ToggleChip(tr("錄製原始資料", "Record raw data"), recordRaw) { s.setRecordRaw(it) } }
        item { Note(tr("每次記錄另存一份 CSV（加速度與角速度）。每 30 分鐘約 10 MB 以上，從下一次記錄開始", "Saves a CSV of accel and gyro for each record. 10 MB or more per 30 min. Starts with the next record")) }

        item {
            StepRow(
                text = tr("視窗 $windowSec 秒", "Window $windowSec s"),
                onMinus = { s.setWindowSec(windowSec - 1) },
                onPlus = { s.setWindowSec(windowSec + 1) },
            )
        }
        item { Note(tr("穩定度分析視窗：放箭前幾秒的晃動量。從下一次記錄開始", "Stability window: seconds of shake before the release. Starts with the next record")) }

        item { ToggleChip(tr("單箭波形", "Per-shot waveform"), recordWaveform) { s.setRecordWaveform(it) } }
        item { Note(tr("每一箭把放箭前後的波形存進記錄，約 1 KB。關閉後記錄檔最小。從下一箭開始生效", "Saves the waveform around each release, about 1 KB. Off gives the smallest files. Starts with the next shot")) }

        item { ToggleChip(tr("放箭後跟隨動作", "Follow-through"), followThrough) { s.setFollowThrough(it) } }
        item { Note(tr("多記放箭後 1 秒的波形與晃動量（持弓手臂放箭後是否穩住）。放箭後 1 秒內就暫停的那一箭沒有這項。從下一箭開始生效", "Also records 1 s after release (does the bow arm stay steady). Not available for a shot paused within 1 s. Starts with the next shot")) }

        item { ToggleChip(tr("每箭環數輸入", "Score entry"), recordScore) { s.setRecordScore(it) } }
        item { Note(tr("在單箭頁輸入環數（0～10），統計與匯出會包含環數。關閉則完全不顯示，立即生效", "Enter a score (0-10) on the shot page; statistics and exports include it. Off hides it completely. Takes effect immediately")) }

        item { ToggleChip(tr("條件標籤", "Condition tags"), tagsEnabled) { s.setTagsEnabled(it) } }
        item { Note(tr("新記錄自動帶上下面選的條件，記錄列表會顯示，跨次趨勢可以只比較相同條件。立即生效，對之後的新記錄有效", "New records get the conditions chosen below, shown in the record list; trends can compare like with like. Applies to later records")) }
        if (tagsEnabled) {
            item {
                Chip(
                    onClick = { s.setDistanceTag(Tags.nextDistance(distanceTag.ifEmpty { null })) },
                    label = { Text(tr("距離", "Distance"), maxLines = 1) },
                    secondaryLabel = { Text(if (distanceTag.isEmpty()) tr("未設定", "Not set") else Tags.display(distanceTag), maxLines = 1) },
                    colors = if (distanceTag.isEmpty()) ChipDefaults.secondaryChipColors() else ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Chip(
                    onClick = { s.setBowTag(Tags.nextBow(bowTag.ifEmpty { null })) },
                    label = { Text(tr("弓種", "Bow"), maxLines = 1) },
                    secondaryLabel = { Text(if (bowTag.isEmpty()) tr("未設定", "Not set") else Tags.display(bowTag), maxLines = 1) },
                    colors = if (bowTag.isEmpty()) ChipDefaults.secondaryChipColors() else ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item { Note(tr("點一下切換選項，最後一個選項之後回到「未設定」", "Tap to cycle; after the last option it goes back to Not set")) }
        }

        // ---- 分趟 ----
        item { SectionTitle(tr("分趟", "Ends")) }
        item { ToggleChip(tr("分趟統計", "End statistics"), showEnds) { s.setShowEnds(it) } }
        item { Note(tr("詳情頁顯示逐趟統計，並在滿一趟時震動提醒。兩箭間隔超過 60 秒也算新的一趟。立即生效", "Shows per-end statistics and vibrates when an end is full. A gap over 60 s between arrows also starts a new end. Takes effect immediately")) }
        if (showEnds) {
            item {
                StepRow(
                    text = tr("每趟 $arrowsPerEnd 箭", "$arrowsPerEnd arrows per end"),
                    onMinus = { s.setArrowsPerEnd(arrowsPerEnd - 1) },
                    onPlus = { s.setArrowsPerEnd(arrowsPerEnd + 1) },
                )
            }
            item {
                Chip(
                    onClick = {
                        val next = EndVibration.next(endVibration)
                        s.setEndVibration(next)
                        vm.buzzEnd(next) // 選了就先震一下讓你感覺
                    },
                    label = { Text(tr("滿趟震動", "End vibration"), maxLines = 1) },
                    secondaryLabel = { Text(EndVibration.label(endVibration), maxLines = 1) },
                    colors = if (endVibration == EndVibration.OFF) ChipDefaults.secondaryChipColors()
                    else ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item { Note(tr("點一下切換樣式並試震：三連震 → 兩短一長 → 一次長震 → 關閉。平常每一箭只輕震一下", "Tap to change and preview: triple buzz → two short, one long → one long buzz → off. Each arrow normally gets one light buzz")) }
        }

        // ---- 訓練 ----
        item { SectionTitle(tr("訓練", "Training")) }
        item {
            StepRow(
                text = if (dailyGoal == 0) tr("目標 關閉", "Goal off") else tr("目標 $dailyGoal 箭", "Goal $dailyGoal arrows"),
                onMinus = { s.setDailyGoal(dailyGoal - Goal.STEP) },
                onPlus = { s.setDailyGoal(dailyGoal + Goal.STEP) },
            )
        }
        item { Note(tr("今日目標箭數（每次加減 10，最多 300，0 = 關閉）。主畫面顯示「今日 36 / 120」，達標時長震一次。校準與誤判不計。立即生效", "Daily arrow goal (steps of 10, up to 300, 0 = off). The main screen shows Today 36 / 120 and buzzes once when reached. Calibration and false detections do not count. Takes effect immediately")) }

        item { ToggleChip(tr("每趟結束回饋", "End feedback"), endFeedback) { s.setEndFeedback(it) } }
        item { Note(tr("滿一趟時在主畫面顯示約 5 秒：這一趟的平均晃動量，以及比上一趟、比本次平均較穩或較抖。需要開啟「分趟統計」。立即生效", "After each end, shows for about 5 s the average shake and whether it is steadier or shakier than the previous end and the session average. Needs End statistics. Takes effect immediately")) }

        item { ToggleChip(tr("疲勞提醒", "Fatigue alert"), fatigueAlert) { s.setFatigueAlert(it) } }
        item { Note(tr("每滿一趟比較「剛開始兩趟」和「最近兩趟」的平均晃動量，最近的比剛開始抖超過下面的百分比，就長震兩下提醒一次，建議休息或收工。需要開啟「分趟統計」，至少射滿 4 趟才會判斷；比賽模式不使用。立即生效", "After each end, compares the average shake of the first two ends with the last two. If the recent ends are shakier by more than the percentage below, it buzzes twice once, suggesting a rest or stopping. Needs End statistics and at least 4 ends; not used in match mode. Takes effect immediately")) }
        if (fatigueAlert) {
            item {
                StepRow(
                    text = tr("門檻 $fatiguePct%", "Limit $fatiguePct%"),
                    onMinus = { s.setFatiguePct(fatiguePct - FatigueRule.STEP_PCT) },
                    onPlus = { s.setFatiguePct(fatiguePct + FatigueRule.STEP_PCT) },
                )
            }
        }

        item { ToggleChip(tr("偵測中降低亮度", "Dim while detecting"), dimWhenRunning) { s.setDimWhenRunning(it) } }
        item { Note(tr("偵測中把螢幕亮度降低以省電，暫停後恢復。部分手錶可能不理會這個設定。立即生效", "Lowers screen brightness while detecting to save power and restores it after pausing. Some watches may ignore this. Takes effect immediately")) }
        if (dimWhenRunning) {
            item {
                Chip(
                    onClick = { s.setDimLevel(Brightness.next(dimLevel)) },
                    label = { Text(tr("亮度", "Brightness"), maxLines = 1) },
                    secondaryLabel = { Text(Brightness.label(dimLevel), maxLines = 1) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item { Note(tr("點一下切換：低 20% → 中 40% → 高 60%", "Tap to change: Low 20% → Mid 40% → High 60%")) }
        }

        // ---- 比賽模式 ----
        item { SectionTitle(tr("比賽模式", "Match mode")) }
        item { ToggleChip(tr("比賽模式", "Match mode"), compEnabled) { s.setCompEnabled(it) } }
        item { Note(tr("開啟後主畫面多一個「比賽模式」：每趟有準備倒數、射擊倒數（剩 30、10 秒震動提醒）、射滿箭數自動結束，休息後按「下一趟」。比賽中自動鎖定觸控、保持亮屏。紀錄標為「比賽」，詳情頁有逐趟用時與逾時統計。立即生效", "Adds a Match mode to the main screen: each end has a prepare countdown and a shooting countdown (buzz at 30 s and 10 s left), ends automatically when the arrows are used, and after the break you press Next end. Touch lock and screen-on are automatic during a match. Records are tagged Match with per-end time and overtime statistics. Takes effect immediately")) }
        if (compEnabled) {
            item {
                Chip(
                    onClick = { s.setCompFormat(CompFormat.next(compFormat)) },
                    label = { Text(tr("賽制", "Format"), maxLines = 1) },
                    secondaryLabel = { Text(CompFormat.label(compFormat), maxLines = 1) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item { Note(tr("室內18米：每趟 3 箭、20 趟、120 秒；室外70米：每趟 6 箭、12 趟、240 秒；自訂：下面自己設", "Indoor 18 m: 3 arrows, 20 ends, 120 s; Outdoor 70 m: 6 arrows, 12 ends, 240 s; Custom: set below")) }
            if (compFormat == CompFormat.CUSTOM) {
                item {
                    StepRow(
                        text = tr("每趟 $compArrows 箭", "$compArrows arrows per end"),
                        onMinus = { s.setCompArrows(compArrows - 1) },
                        onPlus = { s.setCompArrows(compArrows + 1) },
                    )
                }
                item {
                    StepRow(
                        text = tr("共 $compEnds 趟", "$compEnds ends"),
                        onMinus = { s.setCompEnds(compEnds - 1) },
                        onPlus = { s.setCompEnds(compEnds + 1) },
                    )
                }
                item {
                    StepRow(
                        text = tr("每趟 $compSeconds 秒", "$compSeconds s per end"),
                        onMinus = { s.setCompSeconds(compSeconds - CompLimits.STEP_SECONDS) },
                        onPlus = { s.setCompSeconds(compSeconds + CompLimits.STEP_SECONDS) },
                    )
                }
            }
            item {
                StepRow(
                    text = tr("練習 $compPractice 趟", "Practice $compPractice ends"),
                    onMinus = { s.setCompPractice(compPractice - 1) },
                    onPlus = { s.setCompPractice(compPractice + 1) },
                )
            }
            item { Note(tr("正式開始前的練習趟，不計入正式箭數與統計", "Practice ends before the match proper; not counted in match arrows or statistics")) }
            item {
                StepRow(
                    text = if (compPrepare == 0) tr("準備倒數 關閉", "Prepare off") else tr("準備倒數 $compPrepare 秒", "Prepare $compPrepare s"),
                    onMinus = { s.setCompPrepare(compPrepare - 5) },
                    onPlus = { s.setCompPrepare(compPrepare + 5) },
                )
            }
            item { Note(tr("每趟開始射擊前的準備倒數，0 = 關閉，倒數結束長震一下開始計時", "Countdown before each end; 0 = off. A long buzz marks the start of shooting")) }
            item { ToggleChip(tr("剩 30 秒提醒", "30 s warning"), compRemind30) { s.setCompRemind30(it) } }
            item { ToggleChip(tr("剩 10 秒提醒", "10 s warning"), compRemind10) { s.setCompRemind10(it) } }
            item {
                Chip(
                    onClick = { s.setCompAutoNext(CompLimits.nextAutoNext(compAutoNext)) },
                    label = { Text(tr("下一趟", "Next end"), maxLines = 1) },
                    secondaryLabel = {
                        Text(
                            if (compAutoNext == 0) tr("手動按下一趟", "Manual") else tr("休息 $compAutoNext 秒後自動", "Auto after $compAutoNext s"),
                            maxLines = 1,
                        )
                    },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item { Note(tr("取箭休息後如何開始下一趟：手動按鈕，或休息一段時間後自動開始", "How the next end starts after the break: with a button, or automatically after a rest time")) }
        }

        // ---- 圖表與顯示 ----
        item { SectionTitle(tr("圖表", "Charts")) }
        item { ToggleChip(tr("趨勢圖", "Trend charts"), showTrend) { s.setShowTrendChart(it) } }
        item { Note(tr("記錄詳情頁顯示每箭晃動量的趨勢圖。只影響顯示，不影響檔案大小，立即生效", "Shows a per-shot shake trend chart in record details. Display only. Takes effect immediately")) }

        item { ToggleChip(tr("跨次趨勢", "Trend across sessions"), showSessionTrend) { s.setShowSessionTrend(it) } }
        item { Note(tr("記錄列表顯示每次的平均晃動量，並提供最近 20 次的趨勢圖。只影響顯示，立即生效", "Shows each session's average shake in the list and a trend chart of the last 20. Display only. Takes effect immediately")) }

        item { ToggleChip(tr("即時波形", "Live waveform"), liveWaveform) { s.setLiveWaveform(it) } }
        item { Note(tr("偵測中在主畫面顯示即時加速度波形。只影響顯示，但最耗電，立即生效", "Shows a live accel waveform on the main screen while detecting. Display only, but uses the most battery. Takes effect immediately")) }

        // ---- 資料 ----
        item { SectionTitle(tr("資料", "Data")) }
        item {
            Chip(
                onClick = {
                    scope.launch { exportResult = vm.exportSummary() }
                },
                label = { Text(tr("匯出摘要", "Export summary"), maxLines = 1) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item {
            when (exportResult) {
                null -> Note(tr("把全部記錄整理成 summary.csv（每次一行）和 shots.csv（每箭一行），放在 sessions 資料夾，用 adb 一起取出", "Writes summary.csv (one row per session) and shots.csv (one row per shot) into the sessions folder; pull them with adb"))
                true -> Note(tr("已匯出 summary.csv 和 shots.csv 到 sessions 資料夾", "Exported summary.csv and shots.csv to the sessions folder"))
                false -> Note(tr("匯出失敗，請確認手錶儲存空間", "Export failed; check the watch storage"))
            }
        }

        item {
            Chip(
                onClick = onOpenCalibration,
                label = { Text(tr("校準錄製", "Calibration recording"), maxLines = 1) },
                secondaryLabel = { Text(if (running) tr("請先暫停偵測", "Pause detection first") else tr("引導錄三段動作", "Guided 3-step recording"), maxLines = 1) },
                colors = ChipDefaults.secondaryChipColors(),
                enabled = !running,
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item { Note(tr("依序錄「只拉弓不放箭」「正常射擊」「取箭走動」，每一段自動標注並錄原始資料。把 sessions 資料夾取出分析，就能依你的實際資料調整參數", "Records Draw only, Normal shooting and Walk to retrieve in order, labelling each and saving raw data. Pull the sessions folder to analyze it and tune parameters for your own data")) }

        item {
            Chip(
                onClick = {
                    scope.launch {
                        usage = vm.storageUsage()
                        confirmRawDelete = false
                        rawDeleteMessage = null
                    }
                },
                label = { Text(tr("查看佔用空間", "Check storage used"), maxLines = 1) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        usage?.let { u ->
            item {
                Body(
                    tr("記錄 ${u.sessionCount} 筆：${formatBytes(u.jsonBytes)}\n", "Records ${u.sessionCount}: ${formatBytes(u.jsonBytes)}\n") +
                        tr("原始資料 ${u.rawCount} 個：${formatBytes(u.rawBytes)}\n", "Raw files ${u.rawCount}: ${formatBytes(u.rawBytes)}\n") +
                        tr("匯出檔：${formatBytes(u.exportBytes)}", "Export files: ${formatBytes(u.exportBytes)}")
                )
            }
            if (u.rawCount > 0) {
                if (!confirmRawDelete) {
                    item {
                        Chip(
                            onClick = { confirmRawDelete = true },
                            label = { Text(tr("只刪除原始資料", "Delete raw data only"), maxLines = 1) },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                    item { Note(tr("記錄本身會保留，只清掉原始資料 CSV（進行中的那一筆不動）", "Records are kept; only the raw CSV files are removed (the active one is untouched)")) }
                } else {
                    item { Note(tr("確定刪除 ${u.rawCount} 個原始資料檔嗎？無法復原，記錄本身不受影響", "Delete ${u.rawCount} raw data files? This cannot be undone; the records are not affected")) }
                    item {
                        Chip(
                            onClick = {
                                scope.launch {
                                    val r = vm.deleteRawData()
                                    rawDeleteMessage = tr("已刪除 ${r.count} 個，釋出 ${formatBytes(r.bytes)}", "Deleted ${r.count}, freed ${formatBytes(r.bytes)}")
                                    usage = vm.storageUsage()
                                    confirmRawDelete = false
                                }
                            },
                            label = { Text(tr("確定刪除", "Delete"), maxLines = 1) },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                    item {
                        Chip(
                            onClick = { confirmRawDelete = false },
                            label = { Text(tr("取消", "Cancel"), maxLines = 1) },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                }
            }
            rawDeleteMessage?.let { msg -> item { Note(msg) } }
        }

        if (!vm.gyroAvailable) {
            item { Note(tr("此手錶沒有陀螺儀，只會記錄加速度晃動量", "This watch has no gyroscope; only accel shake is recorded")) }
        }

        // ---- 關於與重設 ----
        item { SectionTitle(tr("關於", "About")) }
        item { Body(tr("版本 ${vm.versionLabel}", "Version ${vm.versionLabel}")) }
        item { Body(tr("作者 ${AppInfo.AUTHOR}", "Author ${AppInfo.AUTHOR}")) }
        item { Body(tr("簽章 ${vm.signatureShort}", "Signature ${vm.signatureShort}")) }
        item { Note(AppInfo.signatureStatus(vm.signatureShort)) }
        item { Note(tr("記錄資料夾（用 adb pull 取出）：\n${vm.dataPath}", "Records folder (pull with adb):\n${vm.dataPath}")) }
        if (!confirmReset) {
            item {
                Chip(
                    onClick = { confirmReset = true },
                    label = { Text(tr("恢復預設設定", "Restore default settings"), maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item { Note(tr("把設定頁所有項目還原成預設值。不動記錄、主畫面的箭數和閾值", "Resets every item on this page to its default. Records, the main count and the threshold are untouched")) }
        } else {
            item { Note(tr("確定要把所有設定還原成預設值嗎？", "Reset all settings to defaults?")) }
            item {
                Chip(
                    onClick = {
                        vm.resetSettings()
                        confirmReset = false
                    },
                    label = { Text(tr("確定還原", "Reset"), maxLines = 1) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Chip(
                    onClick = { confirmReset = false },
                    label = { Text(tr("取消", "Cancel"), maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
    }
}

private fun formatBytes(b: Long): String = when {
    b >= 1_048_576L -> String.format(java.util.Locale.US, "%.1f MB", b / 1_048_576.0)
    b >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", b / 1024.0)
    else -> "$b B"
}

/** 「− 文字 +」一列，用來調整數值。 */
@Composable
private fun StepRow(text: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SmallRoundButton("−", onMinus)
        Text(text)
        SmallRoundButton("+", onPlus)
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Chip(
        onClick = { onChange(!on) },
        label = { Text(label, maxLines = 1) },
        secondaryLabel = { Text(if (on) tr("開", "On") else tr("關", "Off")) },
        colors = if (on) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(0.9f),
    )
}
