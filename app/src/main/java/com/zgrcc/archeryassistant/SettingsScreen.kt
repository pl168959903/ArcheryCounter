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

/** [category] 為 -1 時顯示分類列表，否則顯示該分類的項目（見 [SettingsCategories]）。 */
@Composable
fun SettingsScreen(
    vm: CounterViewModel,
    category: Int,
    onOpenCategory: (Int) -> Unit,
    onOpenCalibration: () -> Unit,
) {
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
    val compSavedText by s.compSaved.collectAsStateWithLifecycle()
    val compScoreEntry by s.compScoreEntry.collectAsStateWithLifecycle()
    val compArrowTimer by s.compArrowTimer.collectAsStateWithLifecycle()
    val compArrowSec by s.compArrowSec.collectAsStateWithLifecycle()
    val savedFormats = SavedFormats.decode(compSavedText)
    var confirmDeleteFormat by remember { mutableStateOf(false) }
    val compColors by s.compColors.collectAsStateWithLifecycle()
    val matchCompare by s.matchCompare.collectAsStateWithLifecycle()
    val paceAnalysis by s.paceAnalysis.collectAsStateWithLifecycle()
    val groupCompare by s.groupCompare.collectAsStateWithLifecycle()
    val personalBest by s.personalBest.collectAsStateWithLifecycle()
    val overviewOn by s.overview.collectAsStateWithLifecycle()
    val autoClean by s.autoClean.collectAsStateWithLifecycle()
    val autoCleanDays by s.autoCleanDays.collectAsStateWithLifecycle()
    val shotFeel by s.shotFeel.collectAsStateWithLifecycle()
    val calAdvice by s.calAdvice.collectAsStateWithLifecycle()
    val cleanLast by vm.cleanLast.collectAsStateWithLifecycle()
    val chipKinds by s.chipKinds.collectAsStateWithLifecycle()
    val live2d by s.live2d.collectAsStateWithLifecycle()
    val discSize by s.discSize.collectAsStateWithLifecycle()
    val updateAuto by s.updateAuto.collectAsStateWithLifecycle()
    val updateUi by vm.update.collectAsStateWithLifecycle()
    val discFullscreen by s.discFullscreen.collectAsStateWithLifecycle()
    val recordTrace by s.recordTrace.collectAsStateWithLifecycle()
    val traceGyro by s.traceGyro.collectAsStateWithLifecycle()
    val sensitivityAdvice by s.sensitivityAdvice.collectAsStateWithLifecycle()
    val quickCheck by s.quickCheck.collectAsStateWithLifecycle()
    val tileLaunch by s.tileLaunch.collectAsStateWithLifecycle()
    val soundEnabled by s.soundEnabled.collectAsStateWithLifecycle()
    val soundVolume by s.soundVolume.collectAsStateWithLifecycle()
    val soundStyle by s.soundStyle.collectAsStateWithLifecycle()
    val soundCount by s.soundCount.collectAsStateWithLifecycle()
    val soundGo by s.soundGo.collectAsStateWithLifecycle()
    val soundWarn by s.soundWarn.collectAsStateWithLifecycle()
    val soundEnd by s.soundEnd.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshSessions() }

    // null = 還沒按過；true / false = 匯出成功 / 失敗
    var exportResult by remember { mutableStateOf<Boolean?>(null) }

    // 阈值建议：按下才計算；套用後顯示已套用
    var showAdvice by remember { mutableStateOf(false) }
    var adviceApplied by remember { mutableStateOf(false) }
    var stillApplied by remember { mutableStateOf(false) }
    var cooldownApplied by remember { mutableStateOf(false) }

    // 儲存空間：按下才查；刪除原始資料要再確認一次
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    var confirmRawDelete by remember { mutableStateOf(false) }
    var rawDeleteMessage by remember { mutableStateOf<String?>(null) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (category < 0) {
            // 第一層：分類列表
            item { Text(tr("設定", "Settings"), style = MaterialTheme.typography.caption1) }
            for (c in 0 until SettingsCategories.COUNT) {
                item {
                    Chip(
                        onClick = { onOpenCategory(c) },
                        label = { Text(SettingsCategories.name(c) + navMark(), maxLines = 1) },
                        colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
        } else {
            item { SectionTitle(SettingsCategories.name(category)) }
            if (category == 0) {
                item {
                    Chip(
                        onClick = { s.setLanguage(I18n.next(language)) },
                        label = { Text(tr("語言", "Language"), maxLines = 1) },
                        secondaryLabel = { Text(I18n.label(language), maxLines = 1) },
                        colors = kindColors(ChipKind.NORMAL, ChipDefaults.primaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item { Note(tr("點一下切換：自動 → 繁體中文 → English。自動＝系統是中文就用中文，其他語言用英文。切換後會回到主畫面", "Tap to change: Auto → Traditional Chinese → English. Auto uses Chinese when the system language is Chinese, otherwise English. You return to the main screen after switching")) }

                item { ToggleChip(tr("偵測中觸控鎖", "Touch lock while detecting"), touchLock) { s.setTouchLock(it) } }
                item { Note(tr("開啟後，偵測中主畫面有「鎖定觸控」按鈕，鎖定時只顯示箭數，長按約 2 秒解鎖，避免拉弓、放箭時誤碰。立即生效", "When on, the main screen has a Lock touch button while detecting. Locked shows only the count; hold about 2 s to unlock, so draws and releases do not cause stray touches. Takes effect immediately")) }

                item { ToggleChip(tr("低電量提醒", "Low battery alert"), lowBattery) { s.setLowBattery(it) } }
                item { Note(tr("偵測中電量低於 15% 時震動一次，主畫面顯示「電量低」，只提醒不會自己動作。立即生效", "Vibrates once when the battery drops below 15% while detecting and shows Battery low on the main screen. Only a reminder. Takes effect immediately")) }

                item { ToggleChip(tr("磁貼快捷啟動", "Tile quick start"), tileLaunch) { s.setTileLaunch(it) } }
                item { Note(tr("在手錶的磁貼列表加入「射箭助手」磁貼後，可直接從磁貼開始偵測或開始比賽。關閉時點磁貼只會打開應用。立即生效", "After adding the Archery Assistant tile to your watch, you can start detection or a match straight from it. When off, tapping the tile only opens the app. Takes effect immediately")) }

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


                item { ToggleChip(tr("偵測中降低亮度", "Dim while detecting"), dimWhenRunning) { s.setDimWhenRunning(it) } }
                item { Note(tr("偵測中把螢幕亮度降低以省電，暫停後恢復。部分手錶可能不理會這個設定。立即生效", "Lowers screen brightness while detecting to save power and restores it after pausing. Some watches may ignore this. Takes effect immediately")) }
                if (dimWhenRunning) {
                    item {
                        Chip(
                            onClick = { s.setDimLevel(Brightness.next(dimLevel)) },
                            label = { Text(tr("亮度", "Brightness"), maxLines = 1) },
                            secondaryLabel = { Text(Brightness.label(dimLevel), maxLines = 1) },
                            colors = kindColors(ChipKind.NORMAL, ChipDefaults.primaryChipColors()),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                    item { Note(tr("點一下切換：低 20% → 中 40% → 高 60%", "Tap to change: Low 20% → Mid 40% → High 60%")) }
                }

            }

            if (category == 1) {
                item {
                    Chip(
                        onClick = { s.setSamplingHz(SamplingRate.next(samplingHz)) },
                        label = { Text(tr("取樣頻率", "Sampling rate"), maxLines = 1) },
                        secondaryLabel = { Text("$samplingHz Hz", maxLines = 1) },
                        colors = kindColors(ChipKind.NORMAL, ChipDefaults.primaryChipColors()),
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

                item { ToggleChip(tr("校準自動建議", "Calibration advice"), calAdvice) { s.setCalAdvice(it) } }
                item { Note(tr("校準儲存後，以及校準記錄的詳情頁會多一個「分析校準資料」，用拉弓、走動和正常射擊三段資料給閾值與靜止的建議。只建議，由你決定要不要套用。立即生效", "After a calibration is saved, and on a calibration record's detail page, adds Analyze calibration: it suggests threshold and Still from the three movements. Advice only. Takes effect immediately")) }

                item { ToggleChip(tr("靈敏度建議", "Sensitivity advice"), sensitivityAdvice) { s.setSensitivityAdvice(it) } }
                item { Note(tr("開啟後，下方「閾值建議」也會一併給「靜止」和「冷卻」的建議（需要標記過誤判的記錄）。只建議，由你決定要不要套用。立即生效", "When on, Threshold advice below also covers Still and Cooldown (needs records with marked false detections). Advice only; you decide whether to apply. Takes effect immediately")) }

                item {
                    Chip(
                        onClick = {
                            showAdvice = !showAdvice
                            adviceApplied = false
                            stillApplied = false
                            cooldownApplied = false
                        },
                        label = { Text(tr("閾值建議", "Threshold advice"), maxLines = 1) },
                        secondaryLabel = { Text(tr("目前閾值 ${threshold.toInt()}", "Current threshold ${threshold.toInt()}"), maxLines = 1) },
                        colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
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
                                colors = if (adviceApplied) ChipDefaults.secondaryChipColors() else kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                                modifier = Modifier.fillMaxWidth(0.9f),
                            )
                        }
                    }
                    item { Note(tr("只能建議調高來減少誤判；漏判（該算卻沒算到）看不出來，沒辦法建議調低。校準錄製不參與", "It can only suggest raising the threshold to cut false detections; missed arrows cannot be seen, so it never suggests lowering. Calibration is excluded")) }

                    if (sensitivityAdvice) {
                        val still = adviseStill(sessions, stillThreshold)
                        item { SectionTitle(tr("靜止條件", "Still")) }
                        item {
                            Body(
                                when (still.kind) {
                                    AdviceKind.NOT_ENOUGH ->
                                        tr("資料還不夠。有放箭前晃動量的有效箭 ${still.validCount} 筆、誤判 ${still.cancelledCount} 筆，至少要 $ADVICE_MIN_VALID 與 $ADVICE_MIN_CANCELLED 筆", "Not enough data. ${still.validCount} valid and ${still.cancelledCount} false detections with shake data; need at least $ADVICE_MIN_VALID and $ADVICE_MIN_CANCELLED")
                                    AdviceKind.SUGGEST ->
                                        tr("建議靜止 ${still.suggested}（目前 $stillThreshold）：可擋掉約 ${(still.cancelledExcluded * 100).toInt()}% 的誤判，保留約 ${(still.validKept * 100).toInt()}% 的有效箭", "Suggested Still ${still.suggested} (now $stillThreshold): blocks about ${(still.cancelledExcluded * 100).toInt()}% of false detections, keeps about ${(still.validKept * 100).toInt()}% of valid arrows")
                                    AdviceKind.NOT_SEPARABLE ->
                                        tr("誤判和有效的放箭前晃動量混在一起，調靜止條件幫不上", "False and valid arrows shake alike before release, so Still cannot help")
                                }
                            )
                        }
                        if (still.kind == AdviceKind.SUGGEST && still.suggested != null) {
                            item {
                                Chip(
                                    onClick = {
                                        s.setStillThreshold(still.suggested)
                                        stillApplied = true
                                    },
                                    label = { Text(if (stillApplied) tr("已套用 ${still.suggested}", "Applied ${still.suggested}") else tr("套用 ${still.suggested}", "Apply ${still.suggested}"), maxLines = 1) },
                                    colors = if (stillApplied) ChipDefaults.secondaryChipColors() else kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                                    modifier = Modifier.fillMaxWidth(0.9f),
                                )
                            }
                        }

                        val cool = adviseCooldown(sessions, cooldownSec)
                        item { SectionTitle(tr("冷卻", "Cooldown")) }
                        item {
                            Body(
                                when (cool.kind) {
                                    AdviceKind.NOT_ENOUGH ->
                                        tr("資料還不夠。相鄰有效箭間隔 ${cool.validCount} 筆、誤判 ${cool.cancelledCount} 筆，至少要 $ADVICE_MIN_VALID 與 $ADVICE_MIN_CANCELLED 筆", "Not enough data. ${cool.validCount} valid-arrow gaps and ${cool.cancelledCount} false detections; need at least $ADVICE_MIN_VALID and $ADVICE_MIN_CANCELLED")
                                    AdviceKind.SUGGEST ->
                                        tr("建議冷卻 ${cool.suggested} 秒（目前 $cooldownSec 秒）：可擋掉約 ${(cool.cancelledExcluded * 100).toInt()}% 離前一筆太近的誤判，不影響約 ${(cool.validKept * 100).toInt()}% 的連續兩箭", "Suggested Cooldown ${cool.suggested} s (now $cooldownSec s): blocks about ${(cool.cancelledExcluded * 100).toInt()}% of false detections too close to the previous one, without affecting about ${(cool.validKept * 100).toInt()}% of back-to-back arrows")
                                    AdviceKind.NOT_SEPARABLE ->
                                        tr("誤判不是離前一筆太近造成的，調冷卻幫不上", "False detections are not caused by being too close to the previous one, so Cooldown cannot help")
                                }
                            )
                        }
                        if (cool.kind == AdviceKind.SUGGEST && cool.suggested != null) {
                            item {
                                Chip(
                                    onClick = {
                                        s.setCooldownSec(cool.suggested)
                                        cooldownApplied = true
                                    },
                                    label = { Text(if (cooldownApplied) tr("已套用 ${cool.suggested} 秒", "Applied ${cool.suggested} s") else tr("套用 ${cool.suggested} 秒", "Apply ${cool.suggested} s"), maxLines = 1) },
                                    colors = if (cooldownApplied) ChipDefaults.secondaryChipColors() else kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                                    modifier = Modifier.fillMaxWidth(0.9f),
                                )
                            }
                        }
                        item { Note(tr("靜止的晃動量是整個分析視窗的值，和即時的靜止判斷不完全相同，僅供參考。兩項都只會建議更嚴格（靜止調小、冷卻調長）", "Shake is measured over the whole analysis window, not the instant check, so treat it as a rough guide. Both only suggest stricter settings (lower Still, longer Cooldown)")) }
                    }
                }


                item {
                    Chip(
                        onClick = onOpenCalibration,
                        label = { Text(tr("校準錄製", "Calibration recording") + navMark(), maxLines = 1) },
                        secondaryLabel = { Text(if (running) tr("請先暫停偵測", "Pause detection first") else tr("引導錄三段動作", "Guided 3-step recording"), maxLines = 1) },
                        colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item { Note(tr("依序錄「只拉弓不放箭」「正常射擊」「取箭走動」，每一段自動標注並錄原始資料。把 sessions 資料夾取出分析，就能依你的實際資料調整參數", "Records Draw only, Normal shooting and Walk to retrieve in order, labelling each and saving raw data. Pull the sessions folder to analyze it and tune parameters for your own data")) }
            }

            if (category == 2) {
                item { ToggleChip(tr("快速檢查", "Quick check"), quickCheck) { s.setQuickCheck(it) } }
                item { Note(tr("記錄詳情多一個「快速檢查」，逐箭滑動看波形，一鍵標記誤判或恢復。只影響顯示，立即生效", "Adds Quick check to record details: step through shots with waveforms and mark or restore false detections in one tap. Display only. Takes effect immediately")) }

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

                item { ToggleChip(tr("單箭 2D 軌跡", "Per-shot 2D trace"), recordTrace) { s.setRecordTrace(it) } }
                item { Note(tr("每一箭另存放箭前（分析視窗內）手錶 x、y 兩軸的軌跡，在單箭頁和快速檢查畫成類似跑車 G 力圖的圓盤，點越集中越穩。每箭約多 0.5～1 KB，只對之後的新記錄生效。資料來源見「圖表與顯示」的「2D 圖數據」", "Saves each shot's x/y trace before release (within the analysis window), drawn as a G-force style disc in the shot page and Quick check; tighter means steadier. About 0.5-1 KB more per shot; only new records. The data source is set by 2D data under Charts & display")) }

                item { ToggleChip(tr("單箭波形", "Per-shot waveform"), recordWaveform) { s.setRecordWaveform(it) } }
                item { Note(tr("每一箭把放箭前後的波形存進記錄，約 1 KB。關閉後記錄檔最小。從下一箭開始生效", "Saves the waveform around each release, about 1 KB. Off gives the smallest files. Starts with the next shot")) }

                item { ToggleChip(tr("放箭後跟隨動作", "Follow-through"), followThrough) { s.setFollowThrough(it) } }
                item { Note(tr("多記放箭後 1 秒的波形與晃動量（持弓手臂放箭後是否穩住）。放箭後 1 秒內就暫停的那一箭沒有這項。從下一箭開始生效", "Also records 1 s after release (does the bow arm stay steady). Not available for a shot paused within 1 s. Starts with the next shot")) }

                item { ToggleChip(tr("每箭環數輸入", "Score entry"), recordScore) { s.setRecordScore(it) } }
                item { Note(tr("在單箭頁輸入環數（0～10），統計與匯出會包含環數。關閉則完全不顯示，立即生效", "Enter a score (0-10) on the shot page; statistics and exports include it. Off hides it completely. Takes effect immediately")) }

                item { ToggleChip(tr("每箭自評", "Shot self-rating"), shotFeel) { s.setShotFeel(it) } }
                item { Note(tr("在單箭頁和快速檢查加「差／普通／好」三顆按鈕，記錄詳情會比較各自評的平均晃動。預設關閉，立即生效", "Adds Bad / OK / Good buttons on the shot page and Quick check; the detail page compares average shake per rating. Off by default. Takes effect immediately")) }

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


                if (!vm.gyroAvailable) {
                    item { Note(tr("此手錶沒有陀螺儀，只會記錄加速度晃動量", "This watch has no gyroscope; only accel shake is recorded")) }
                }
            }

            if (category == 3) {
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

            }

            if (category == 4) {
                item { ToggleChip(tr("比賽模式", "Match mode"), compEnabled) { s.setCompEnabled(it) } }
                item { Note(tr("開啟後主畫面多一個「比賽模式」：每趟有準備倒數、射擊倒數（剩 30、10 秒震動提醒）、射滿箭數自動結束，休息後按「下一趟」。比賽中自動鎖定觸控、保持亮屏。紀錄標為「比賽」，詳情頁有逐趟用時與逾時統計。立即生效", "Adds a Match mode to the main screen: each end has a prepare countdown and a shooting countdown (buzz at 30 s and 10 s left), ends automatically when the arrows are used, and after the break you press Next end. Touch lock and screen-on are automatic during a match. Records are tagged Match with per-end time and overtime statistics. Takes effect immediately")) }
                if (compEnabled) {
                    item {
                        Chip(
                            onClick = {
                                confirmDeleteFormat = false
                                s.setCompFormat(CompFormat.nextId(compFormat, savedFormats.size))
                            },
                            label = { Text(tr("賽制", "Format"), maxLines = 1) },
                            secondaryLabel = { Text(CompFormat.labelOf(compFormat, savedFormats), maxLines = 1) },
                            colors = kindColors(ChipKind.NORMAL, ChipDefaults.primaryChipColors()),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                    item { Note(tr("室內18米：每趟 3 箭、20 趟、120 秒；室外70米：每趟 6 箭、12 趟、240 秒；自訂：下面自己設；我的賽制：存下來的自訂賽制，最多 5 組，點一下依序切換", "Indoor 18 m: 3 arrows, 20 ends, 120 s; Outdoor 70 m: 6 arrows, 12 ends, 240 s; Custom: set below; Mine: saved custom formats, up to 5, tap to cycle")) }
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
                        // 把目前的自訂賽制存成「我的賽制」
                        val current = CompFormat(CompFormat.CUSTOM, compArrows, compEnds, compSeconds)
                        item {
                            Chip(
                                onClick = { s.setCompSaved(SavedFormats.encode(SavedFormats.add(savedFormats, current))) },
                                label = { Text(tr("存成我的賽制", "Save as my format"), maxLines = 1) },
                                secondaryLabel = {
                                    Text(
                                        when {
                                            savedFormats.size >= SavedFormats.MAX -> tr("已滿 ${SavedFormats.MAX} 組", "Full (${SavedFormats.MAX})")
                                            !SavedFormats.canAdd(savedFormats, current) -> tr("已經存過", "Already saved")
                                            else -> SavedFormats.describe(current)
                                        },
                                        maxLines = 1,
                                    )
                                },
                                colors = ChipDefaults.secondaryChipColors(),
                                enabled = SavedFormats.canAdd(savedFormats, current),
                                modifier = Modifier.fillMaxWidth(0.9f),
                            )
                        }
                    }
                    // 選到「我的賽制」時：顯示內容，可刪除（要再按一次確認）
                    if (compFormat >= CompFormat.SAVED_BASE && compFormat - CompFormat.SAVED_BASE in savedFormats.indices) {
                        item { Note(SavedFormats.describe(savedFormats[compFormat - CompFormat.SAVED_BASE])) }
                        item {
                            Chip(
                                onClick = {
                                    if (!confirmDeleteFormat) {
                                        confirmDeleteFormat = true
                                    } else {
                                        val idx = compFormat - CompFormat.SAVED_BASE
                                        s.setCompSaved(SavedFormats.encode(SavedFormats.remove(savedFormats, idx)))
                                        s.setCompFormat(CompFormat.CUSTOM)
                                        confirmDeleteFormat = false
                                    }
                                },
                                label = { Text(if (confirmDeleteFormat) tr("再按一次確認刪除", "Tap again to delete") else tr("刪除這組賽制", "Delete this format"), maxLines = 1) },
                                colors = kindColors(ChipKind.DANGER, ChipDefaults.secondaryChipColors()),
                                modifier = Modifier.fillMaxWidth(0.9f),
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
                    item { ToggleChip(tr("單箭限時", "Per-arrow limit"), compArrowTimer) { s.setCompArrowTimer(it) } }
                    item { Note(tr("每一箭限時：射擊開始或射出一箭後重新倒數，超時震動並響兩聲提醒一次。只提醒，不會結束這一趟，每趟總時間照舊。預設關。立即生效", "Time limit for each arrow: restarts when shooting begins or after each arrow; going over buzzes and beeps twice, once. Only a reminder; it does not end the end, and the end clock is unchanged. Off by default. Takes effect immediately")) }
                    if (compArrowTimer) {
                        item {
                            StepRow(
                                text = tr("每箭 $compArrowSec 秒", "$compArrowSec s per arrow"),
                                onMinus = { s.setCompArrowSec(compArrowSec - CompLimits.STEP_ARROW_SEC) },
                                onPlus = { s.setCompArrowSec(compArrowSec + CompLimits.STEP_ARROW_SEC) },
                            )
                        }
                    }
                    item { ToggleChip(tr("休息時輸入環數", "Score entry at breaks"), compScoreEntry) { s.setCompScoreEntry(it) } }
                    item { Note(tr("每趟結束進入休息畫面時，多一個「輸入環數」按鈕，逐箭輸入該趟環數（可跳過，賽後在詳情頁也能補）。最後一趟射完後比賽直接結束，那一趟請賽後在詳情頁輸入。立即生效", "At each break an Enter scores button lets you score that end's arrows (skippable; you can also add them later in record details). The match ends right after the last end, so score that one in record details. Takes effect immediately")) }
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
                            colors = kindColors(ChipKind.NORMAL, ChipDefaults.primaryChipColors()),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                    item { ToggleChip(tr("比賽畫面配色", "Match colors"), compColors) { s.setCompColors(it) } }
                    item { Note(tr("準備階段紅底白字、射擊階段綠底黑字。關閉則維持一般深色畫面。立即生效", "Red background with white text while getting ready; green with black text while shooting. Off keeps the normal dark screen. Takes effect immediately")) }
                    item { ToggleChip(tr("比賽聲音", "Match sounds"), soundEnabled) { s.setSoundEnabled(it) } }
                    item { Note(tr("用手錶喇叭（或已連接的藍牙耳機）播放提示音，和震動各自獨立。沒有喇叭的手錶聽不到；手錶處於勿擾或靜音時可能被系統壓低。立即生效", "Plays tones through the watch speaker (or connected Bluetooth headphones), independent of vibration. Watches without a speaker are silent; Do Not Disturb or silent mode may mute it. Takes effect immediately")) }
                    if (soundEnabled) {
                        item {
                            Chip(
                                onClick = { s.setSoundVolume(MatchSound.nextVolume(soundVolume)); vm.previewSound() },
                                label = { Text(tr("音量", "Volume"), maxLines = 1) },
                                secondaryLabel = { Text(MatchSound.volumeLabel(soundVolume), maxLines = 1) },
                                colors = kindColors(ChipKind.NORMAL, ChipDefaults.primaryChipColors()),
                                modifier = Modifier.fillMaxWidth(0.9f),
                            )
                        }
                        item {
                            Chip(
                                onClick = { s.setSoundStyle(MatchSound.nextStyle(soundStyle)); vm.previewSound() },
                                label = { Text(tr("音色", "Tone"), maxLines = 1) },
                                secondaryLabel = { Text(MatchSound.styleLabel(soundStyle), maxLines = 1) },
                                colors = kindColors(ChipKind.NORMAL, ChipDefaults.primaryChipColors()),
                                modifier = Modifier.fillMaxWidth(0.9f),
                            )
                        }
                        item { Note(tr("點一下切換音量或音色，並試播兩聲", "Tap to change volume or tone; plays two beeps as a preview")) }
                        item { ToggleChip(tr("倒數最後 3 秒", "Last 3 s of prepare"), soundCount) { s.setSoundCount(it) } }
                        item { ToggleChip(tr("開始射擊", "Shooting starts"), soundGo) { s.setSoundGo(it) } }
                        item { ToggleChip(tr("剩 30／10 秒", "30 s / 10 s left"), soundWarn) { s.setSoundWarn(it) } }
                        item { ToggleChip(tr("時間到／射滿", "Time up / end full"), soundEnd) { s.setSoundEnd(it) } }
                    }
                    item { Note(tr("取箭休息後如何開始下一趟：手動按鈕，或休息一段時間後自動開始", "How the next end starts after the break: with a button, or automatically after a rest time")) }
                }

            }

            if (category == 5) {
                item { ToggleChip(tr("趨勢圖", "Trend charts"), showTrend) { s.setShowTrendChart(it) } }
                item { Note(tr("記錄詳情頁顯示每箭晃動量的趨勢圖。只影響顯示，不影響檔案大小，立即生效", "Shows a per-shot shake trend chart in record details. Display only. Takes effect immediately")) }

                item { ToggleChip(tr("跨次趨勢", "Trend across sessions"), showSessionTrend) { s.setShowSessionTrend(it) } }
                item { Note(tr("記錄列表顯示每次的平均晃動量，並提供最近 20 次的趨勢圖。只影響顯示，立即生效", "Shows each session's average shake in the list and a trend chart of the last 20. Display only. Takes effect immediately")) }

                item { ToggleChip(tr("比賽對比", "Match comparison"), matchCompare) { s.setMatchCompare(it) } }
                item { Note(tr("記錄列表多一個「比賽對比」，把比賽紀錄的日期、總環數、平均晃動、時間到的趟數放在一起比，並畫趨勢圖。只影響顯示，立即生效", "Adds Match comparison to the records list: date, total score, average shake and timed-out ends side by side, with trend charts. Display only. Takes effect immediately")) }

                item { ToggleChip(tr("節奏分析", "Pace analysis"), paceAnalysis) { s.setPaceAnalysis(it) } }
                item { Note(tr("比賽紀錄詳情多一段節奏分析（第一箭等待、最後一箭剩幾秒、壓線趟數、用時），比賽對比也會列平均第一箭等待。只影響顯示，立即生效", "Adds pace analysis to match details (first-arrow wait, time left on the last arrow, tight ends, time used), and average first-arrow wait to Match comparison. Display only. Takes effect immediately")) }

                item { ToggleChip(tr("條件對比", "Condition comparison"), groupCompare) { s.setGroupCompare(it) } }
                item { Note(tr("記錄列表多一個「條件對比」，依距離和弓種分組比較平均晃動與環數，並標出最穩的一組。只影響顯示，立即生效", "Adds Compare conditions to the records list, grouping by distance and bow with average shake and score, and marking the steadiest group. Display only. Takes effect immediately")) }

                item { ToggleChip(tr("個人最佳", "Personal best"), personalBest) { s.setPersonalBest(it) } }
                item { Note(tr("在最穩的練習、同賽制下環數最高或最穩的比賽前面標 ★。只影響顯示，立即生效", "Marks the steadiest practice session, and the highest-scoring or steadiest match within the same format, with ★. Display only. Takes effect immediately")) }

                item { ToggleChip(tr("訓練總覽", "Training overview"), overviewOn) { s.setOverview(it) } }
                item { Note(tr("記錄列表多一個「訓練總覽」，看每週、每月的箭數、練習天數和連續練習天數。只影響顯示，立即生效", "Adds Training overview to the records list: arrows per week and month, practice days and streak. Display only. Takes effect immediately")) }

                item { ToggleChip(tr("即時晃動圓盤", "Live shake disc"), live2d) { s.setLive2d(it) } }
                item { Note(tr("偵測中主畫面顯示像跑車 G 力圖的圓盤：點是最近約 2 秒的手錶 x、y 位置，越集中在圓心越穩，旁邊有外圈大小和虛線圈（均方根半徑）。開啟後取代即時波形，畫圖較耗電。軸是手錶表面方向，取決於怎麼戴表。立即生效", "While detecting, the main screen shows a G-force style disc: the dots are the watch's x/y over the last ~2 s; tighter around the center is steadier. Shows the outer ring size and a dashed ring (RMS radius). Replaces the live waveform and uses more battery. Axes follow the watch face, so they depend on how you wear it. Takes effect immediately")) }

                item {
                    Chip(
                        onClick = { s.setTraceGyro(!traceGyro) },
                        label = { Text(tr("2D 圖數據", "2D data"), maxLines = 1) },
                        secondaryLabel = { Text(if (traceGyro) tr("角速度（°/s）", "Gyro (°/s)") else tr("加速度（m/s²）", "Accel (m/s²)"), maxLines = 1) },
                        colors = kindColors(ChipKind.NORMAL, ChipDefaults.primaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item { Note(tr("即時圓盤和單箭 2D 軌跡用的數據：加速度看手腕晃動，角速度看瞄準的轉動（需要陀螺儀）。單箭軌跡只對切換之後的新記錄生效，舊記錄維持原來的類型", "Data used by the live disc and the per-shot 2D trace: accel shows wrist shake, gyro shows aim rotation (needs a gyroscope). Per-shot traces follow the setting at the time of recording; old records keep their type")) }

                item {
                    Chip(
                        onClick = { s.setDiscSize(DiscSize.next(discSize)) },
                        label = { Text(tr("圓盤大小", "Disc size"), maxLines = 1) },
                        secondaryLabel = { Text("${DiscSize.label(discSize)}（${DiscSize.dp(discSize)} dp）", maxLines = 1) },
                        colors = kindColors(ChipKind.NORMAL, ChipDefaults.primaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                item { Note(tr("即時圓盤、單箭頁和快速檢查裡的圓盤大小：小 100、中 130、大 160 dp，預設大。圓盤越大，下面的內容要多滑一點才看得到。立即生效", "Disc size in the live disc, shot page and Quick check: Small 100, Medium 130, Large 160 dp, default Large. A bigger disc means more scrolling to reach what is below. Takes effect immediately")) }

                item { ToggleChip(tr("點圓盤全屏", "Tap disc for full screen"), discFullscreen) { s.setDiscFullscreen(it) } }
                item { Note(tr("偵測中點一下主畫面的即時圓盤，放大成全屏；再點一下或往右滑返回。偵測照常運行。觸控鎖定時點不到。立即生效", "While detecting, tap the live disc on the main screen to go full screen; tap again or swipe right to return. Detection keeps running. Not tappable while touch-locked. Takes effect immediately")) }

                item { ToggleChip(tr("按鈕分色", "Colored buttons"), chipKinds) { s.setChipKinds(it) } }
                item { Note(tr("按鈕依用途上色：綠＝開始／確認，藍＝點進去是另一頁（標籤尾巴有 ›），黃＝清單裡的每一項（每筆記錄、每場比賽、每一箭），紅＝刪除或結束，灰＝一般操作。關閉則全部維持原本配色。立即生效", "Buttons are colored by purpose: green = start/confirm, blue = opens another page (label ends with ›), yellow = each item in a list (record, match, shot), red = delete or end, gray = ordinary actions. Off keeps the original look. Takes effect immediately")) }

                item { ToggleChip(tr("即時波形", "Live waveform"), liveWaveform) { s.setLiveWaveform(it) } }
                item { Note(tr("偵測中在主畫面顯示即時加速度波形。只影響顯示，但最耗電，立即生效", "Shows a live accel waveform on the main screen while detecting. Display only, but uses the most battery. Takes effect immediately")) }

            }

            if (category == 6) {
                item { ToggleChip(tr("自動清理", "Auto clean-up"), autoClean) { s.setAutoClean(it) } }
                if (autoClean) {
                    item {
                        Chip(
                            onClick = { s.setAutoCleanDays(AutoClean.nextDays(autoCleanDays)) },
                            label = { Text(tr("超過 $autoCleanDays 天", "Older than $autoCleanDays days"), maxLines = 1) },
                            secondaryLabel = { Text(tr("點一下切換 30／60／90", "Tap to cycle 30 / 60 / 90"), maxLines = 1) },
                            colors = kindColors(ChipKind.NORMAL, ChipDefaults.primaryChipColors()),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                }
                item { Note(tr("開啟後，每次開 App 會把超過設定天數、已結束的記錄刪掉原始資料和波形／軌跡，統計與環數保留。校準記錄不動。預設關閉，無法復原", "When on, each app start deletes raw data and waveforms/traces of finished records older than the set days; statistics and scores are kept. Calibration records are untouched. Off by default; cannot be undone")) }
                item { Note(AutoClean.describe(cleanLast, ::formatBytes)) }

                item {
                    Chip(
                        onClick = {
                            scope.launch { exportResult = vm.exportSummary() }
                        },
                        label = { Text(tr("匯出摘要", "Export summary"), maxLines = 1) },
                        colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
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
                        onClick = {
                            scope.launch {
                                usage = vm.storageUsage()
                                confirmRawDelete = false
                                rawDeleteMessage = null
                            }
                        },
                        label = { Text(tr("查看佔用空間", "Check storage used"), maxLines = 1) },
                        colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
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
                                    colors = kindColors(ChipKind.DANGER, ChipDefaults.secondaryChipColors()),
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
                                    colors = kindColors(ChipKind.DANGER, ChipDefaults.primaryChipColors()),
                                    modifier = Modifier.fillMaxWidth(0.9f),
                                )
                            }
                            item {
                                Chip(
                                    onClick = { confirmRawDelete = false },
                                    label = { Text(tr("取消", "Cancel"), maxLines = 1) },
                                    colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                                    modifier = Modifier.fillMaxWidth(0.9f),
                                )
                            }
                        }
                    }
                    rawDeleteMessage?.let { msg -> item { Note(msg) } }
                }


            }

            if (category == 7) {
                item { Body(tr("版本 ${vm.versionLabel}", "Version ${vm.versionLabel}")) }
                item { Body(tr("作者 ${AppInfo.AUTHOR}", "Author ${AppInfo.AUTHOR}")) }
                item { Body(tr("簽章 ${vm.signatureShort}", "Signature ${vm.signatureShort}")) }
                item { Note(AppInfo.signatureStatus(vm.signatureShort)) }

                item { SectionTitle(tr("更新", "Update")) }
                item { ToggleChip(tr("啟動時自動檢查更新", "Check for updates on start"), updateAuto) { s.setUpdateAuto(it) } }
                item { Note(tr("開啟後，每次開啟應用最多一天查一次 GitHub 有沒有新的發布，有就在這裡和主畫面的「設定」上提示。不會自己下載，也不會中斷射擊。需要網路（Wi-Fi 或行動網路）。立即生效", "When on, the app checks GitHub for a new release at most once a day when opened, and shows a hint here and on Settings on the main screen. It never downloads by itself or interrupts shooting. Needs a network (Wi-Fi or mobile data). Takes effect immediately")) }
                item {
                    val busy = updateUi.kind == UpdateKind.CHECKING || updateUi.kind == UpdateKind.DOWNLOADING || updateUi.kind == UpdateKind.INSTALLING
                    Chip(
                        onClick = { vm.checkUpdate(manual = true) },
                        label = { Text(tr("檢查更新", "Check for updates"), maxLines = 1) },
                        secondaryLabel = {
                            Text(
                                when (updateUi.kind) {
                                    UpdateKind.CHECKING -> tr("檢查中…", "Checking…")
                                    UpdateKind.UP_TO_DATE -> tr("已是最新版", "Up to date")
                                    UpdateKind.AVAILABLE -> tr("有新版本 ${updateUi.info?.tag}", "New version ${updateUi.info?.tag}")
                                    else -> tr("目前 ${vm.versionName}", "Current ${vm.versionName}")
                                },
                                maxLines = 1,
                            )
                        },
                        colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                val found = updateUi.info
                when (updateUi.kind) {
                    UpdateKind.AVAILABLE -> if (found != null) {
                        item {
                            Body(
                                tr("有新版本 ${found.tag}（目前 ${vm.versionName}），大小約 ${UpdateText.size(found.apkSize)}", "New version ${found.tag} (current ${vm.versionName}), about ${UpdateText.size(found.apkSize)}") +
                                    if (found.notes.isNotEmpty()) "\n\n" + found.notes else ""
                            )
                        }
                        item {
                            Chip(
                                onClick = { vm.downloadAndInstall() },
                                label = { Text(tr("下載並安裝", "Download and install"), maxLines = 1) },
                                colors = kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                                modifier = Modifier.fillMaxWidth(0.9f),
                            )
                        }
                        item {
                            Chip(
                                onClick = { vm.skipUpdate() },
                                label = { Text(tr("略過這個版本", "Skip this version"), maxLines = 1) },
                                colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                                modifier = Modifier.fillMaxWidth(0.9f),
                            )
                        }
                        item { Note(tr("下載完成後，系統會跳出安裝確認畫面，由你按「安裝」。簽章相同，記錄會保留", "When the download finishes, the system asks you to confirm the install. The signature is the same, so your records are kept")) }
                    }
                    UpdateKind.DOWNLOADING -> item { Body(tr("下載中 ${updateUi.percent}%", "Downloading ${updateUi.percent}%")) }
                    UpdateKind.INSTALLING -> item { Body(tr("已交給系統安裝程式。請在系統畫面按「安裝」；安裝完成後應用會重新開啟", "Handed to the system installer. Tap Install on the system screen; the app reopens when it finishes")) }
                    UpdateKind.ERROR -> item { Body(updateUi.error?.let { UpdateText.error(it) } ?: "") }
                    else -> Unit
                }
                item { Note(tr("記錄資料夾（用 adb pull 取出）：\n${vm.dataPath}", "Records folder (pull with adb):\n${vm.dataPath}")) }
                if (!confirmReset) {
                    item {
                        Chip(
                            onClick = { confirmReset = true },
                            label = { Text(tr("恢復預設設定", "Restore default settings"), maxLines = 1) },
                            colors = kindColors(ChipKind.DANGER, ChipDefaults.secondaryChipColors()),
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
                            colors = kindColors(ChipKind.DANGER, ChipDefaults.primaryChipColors()),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                    item {
                        Chip(
                            onClick = { confirmReset = false },
                            label = { Text(tr("取消", "Cancel"), maxLines = 1) },
                            colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                }
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
