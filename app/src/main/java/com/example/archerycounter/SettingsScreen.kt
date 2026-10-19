package com.example.archerycounter

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
        item { Text("設定", style = MaterialTheme.typography.caption1) }

        // ---- 一般 ----
        item { SectionTitle("一般") }
        item { ToggleChip("偵測中觸控鎖", touchLock) { s.setTouchLock(it) } }
        item { Note("開啟後，偵測中主畫面有「鎖定觸控」按鈕，鎖定時只顯示箭數，長按約 2 秒解鎖，避免拉弓、放箭時誤碰。立即生效") }

        item { ToggleChip("低電量提醒", lowBattery) { s.setLowBattery(it) } }
        item { Note("偵測中電量低於 15% 時震動一次，主畫面顯示「電量低」，只提醒不會自己動作。立即生效") }

        item { ToggleChip("開啟動畫", splash) { s.setSplash(it) } }
        item { Note("開啟應用時先播放約 1.5 秒的動畫（箭射向靶心，並顯示版本與作者）。下次開啟應用時生效") }

        item { ToggleChip("保持亮屏", keepScreenOn) { s.setKeepScreenOn(it) } }
        item { Note("應用開著時螢幕不休眠，立即生效") }

        item { ToggleChip("CPU 喚醒鎖", wakeLock) { s.setWakeLock(it) } }
        item { Note("偵測中保持 CPU 運作，即使螢幕被熄滅也繼續取資料。較耗電，立即生效") }

        // ---- 偵測 ----
        item { SectionTitle("偵測") }
        item {
            StepRow(
                text = "靜止 $stillThreshold",
                onMinus = { s.setStillThreshold(stillThreshold - 1) },
                onPlus = { s.setStillThreshold(stillThreshold + 1) },
            )
        }
        item {
            Note("放箭前要先靜止到合力低於此值（m/s²，預設 5）。調大較容易算進去，但容易誤判；調小較嚴格，可能漏判。立即生效")
        }
        item {
            StepRow(
                text = "冷卻 $cooldownSec 秒",
                onMinus = { s.setCooldownSec(cooldownSec - 1) },
                onPlus = { s.setCooldownSec(cooldownSec + 1) },
            )
        }
        item { Note("兩次計數之間至少間隔多久（預設 3 秒）。調短可能把一次放箭算成兩次；調長可能漏掉連續的兩箭。立即生效") }

        item { ToggleChip("自動暫停", autoPause) { s.setAutoPause(it) } }
        item { Note("偵測中太久沒有放箭就自動暫停並震動，避免忘了按暫停而白白耗電。校準錄製不適用。立即生效") }
        if (autoPause) {
            item {
                StepRow(
                    text = "$autoPauseMin 分鐘",
                    onMinus = { s.setAutoPauseMin(autoPauseMin - AutoPause.STEP_MINUTES) },
                    onPlus = { s.setAutoPauseMin(autoPauseMin + AutoPause.STEP_MINUTES) },
                )
            }
            item { Note("超過這麼久沒有放箭就暫停（5～30 分鐘）。中間休息比較久的話請調長") }
        }

        item {
            Chip(
                onClick = {
                    showAdvice = !showAdvice
                    adviceApplied = false
                },
                label = { Text("閾值建議", maxLines = 1) },
                secondaryLabel = { Text("目前閾值 ${threshold.toInt()}", maxLines = 1) },
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
                            "資料還不夠。目前有效 ${advice.validCount} 筆、誤判 ${advice.cancelledCount} 筆，" +
                                "至少要有效 $ADVICE_MIN_VALID 筆、誤判 $ADVICE_MIN_CANCELLED 筆。" +
                                "在記錄詳情裡把不是射箭的動作標為誤判，累積後這裡就能建議"
                        AdviceKind.SUGGEST ->
                            "有效的箭峰值中位數 ${Fmt.numOrDash(advice.validMedian, 1)}，" +
                                "誤判的 ${Fmt.numOrDash(advice.cancelledMedian, 1)}。\n" +
                                "建議閾值 ${advice.suggested}：可擋掉約 ${(advice.cancelledExcluded * 100).toInt()}% 的誤判，" +
                                "保留約 ${(advice.validKept * 100).toInt()}% 的有效箭"
                        AdviceKind.NOT_SEPARABLE ->
                            "有效和誤判的峰值混在一起，調閾值幫不上。可以試試調整上面的「靜止」條件"
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
                        label = { Text(if (adviceApplied) "已套用 ${advice.suggested}" else "套用 ${advice.suggested}", maxLines = 1) },
                        colors = if (adviceApplied) ChipDefaults.secondaryChipColors() else ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
            item { Note("只能建議調高來減少誤判；漏判（該算卻沒算到）看不出來，沒辦法建議調低。校準錄製不參與") }
        }

        // ---- 記錄 ----
        item { SectionTitle("記錄") }
        item { ToggleChip("錄製原始資料", recordRaw) { s.setRecordRaw(it) } }
        item { Note("每次記錄另存一份 CSV（加速度與角速度）。每 30 分鐘約 10 MB 以上，從下一次記錄開始") }

        item {
            StepRow(
                text = "視窗 $windowSec 秒",
                onMinus = { s.setWindowSec(windowSec - 1) },
                onPlus = { s.setWindowSec(windowSec + 1) },
            )
        }
        item { Note("穩定度分析視窗：放箭前幾秒的晃動量。從下一次記錄開始") }

        item { ToggleChip("單箭波形", recordWaveform) { s.setRecordWaveform(it) } }
        item { Note("每一箭把放箭前後的波形存進記錄，約 1 KB。關閉後記錄檔最小。從下一箭開始生效") }

        item { ToggleChip("放箭後跟隨動作", followThrough) { s.setFollowThrough(it) } }
        item { Note("多記放箭後 1 秒的波形與晃動量（持弓手臂放箭後是否穩住）。放箭後 1 秒內就暫停的那一箭沒有這項。從下一箭開始生效") }

        item { ToggleChip("每箭環數輸入", recordScore) { s.setRecordScore(it) } }
        item { Note("在單箭頁輸入環數（0～10），統計與匯出會包含環數。關閉則完全不顯示，立即生效") }

        item { ToggleChip("條件標籤", tagsEnabled) { s.setTagsEnabled(it) } }
        item { Note("新記錄自動帶上下面選的條件，記錄列表會顯示，跨次趨勢可以只比較相同條件。立即生效，對之後的新記錄有效") }
        if (tagsEnabled) {
            item {
                Chip(
                    onClick = { s.setDistanceTag(Tags.nextDistance(distanceTag.ifEmpty { null })) },
                    label = { Text("距離", maxLines = 1) },
                    secondaryLabel = { Text(distanceTag.ifEmpty { "未設定" }, maxLines = 1) },
                    colors = if (distanceTag.isEmpty()) ChipDefaults.secondaryChipColors() else ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Chip(
                    onClick = { s.setBowTag(Tags.nextBow(bowTag.ifEmpty { null })) },
                    label = { Text("弓種", maxLines = 1) },
                    secondaryLabel = { Text(bowTag.ifEmpty { "未設定" }, maxLines = 1) },
                    colors = if (bowTag.isEmpty()) ChipDefaults.secondaryChipColors() else ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item { Note("點一下切換選項，最後一個選項之後回到「未設定」") }
        }

        // ---- 分趟 ----
        item { SectionTitle("分趟") }
        item { ToggleChip("分趟統計", showEnds) { s.setShowEnds(it) } }
        item { Note("詳情頁顯示逐趟統計，並在滿一趟時震動提醒。兩箭間隔超過 60 秒也算新的一趟。立即生效") }
        if (showEnds) {
            item {
                StepRow(
                    text = "每趟 $arrowsPerEnd 箭",
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
                    label = { Text("滿趟震動", maxLines = 1) },
                    secondaryLabel = { Text(EndVibration.label(endVibration), maxLines = 1) },
                    colors = if (endVibration == EndVibration.OFF) ChipDefaults.secondaryChipColors()
                    else ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item { Note("點一下切換樣式並試震：三連震 → 兩短一長 → 一次長震 → 關閉。平常每一箭只輕震一下") }
        }

        // ---- 訓練 ----
        item { SectionTitle("訓練") }
        item {
            StepRow(
                text = if (dailyGoal == 0) "目標 關閉" else "目標 $dailyGoal 箭",
                onMinus = { s.setDailyGoal(dailyGoal - Goal.STEP) },
                onPlus = { s.setDailyGoal(dailyGoal + Goal.STEP) },
            )
        }
        item { Note("今日目標箭數（每次加減 10，最多 300，0 = 關閉）。主畫面顯示「今日 36 / 120」，達標時長震一次。校準與誤判不計。立即生效") }

        item { ToggleChip("每趟結束回饋", endFeedback) { s.setEndFeedback(it) } }
        item { Note("滿一趟時在主畫面顯示約 5 秒：這一趟的平均晃動量，以及比上一趟、比本次平均較穩或較抖。需要開啟「分趟統計」。立即生效") }

        item { ToggleChip("偵測中降低亮度", dimWhenRunning) { s.setDimWhenRunning(it) } }
        item { Note("偵測中把螢幕亮度降低以省電，暫停後恢復。部分手錶可能不理會這個設定。立即生效") }
        if (dimWhenRunning) {
            item {
                Chip(
                    onClick = { s.setDimLevel(Brightness.next(dimLevel)) },
                    label = { Text("亮度", maxLines = 1) },
                    secondaryLabel = { Text(Brightness.label(dimLevel), maxLines = 1) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item { Note("點一下切換：低 20% → 中 40% → 高 60%") }
        }

        // ---- 圖表與顯示 ----
        item { SectionTitle("圖表") }
        item { ToggleChip("趨勢圖", showTrend) { s.setShowTrendChart(it) } }
        item { Note("記錄詳情頁顯示每箭晃動量的趨勢圖。只影響顯示，不影響檔案大小，立即生效") }

        item { ToggleChip("跨次趨勢", showSessionTrend) { s.setShowSessionTrend(it) } }
        item { Note("記錄列表顯示每次的平均晃動量，並提供最近 20 次的趨勢圖。只影響顯示，立即生效") }

        item { ToggleChip("即時波形", liveWaveform) { s.setLiveWaveform(it) } }
        item { Note("偵測中在主畫面顯示即時加速度波形。只影響顯示，但最耗電，立即生效") }

        // ---- 資料 ----
        item { SectionTitle("資料") }
        item {
            Chip(
                onClick = {
                    scope.launch { exportResult = vm.exportSummary() }
                },
                label = { Text("匯出摘要", maxLines = 1) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item {
            when (exportResult) {
                null -> Note("把全部記錄整理成 summary.csv（每次一行）和 shots.csv（每箭一行），放在 sessions 資料夾，用 adb 一起取出")
                true -> Note("已匯出 summary.csv 和 shots.csv 到 sessions 資料夾")
                false -> Note("匯出失敗，請確認手錶儲存空間")
            }
        }

        item {
            Chip(
                onClick = onOpenCalibration,
                label = { Text("校準錄製", maxLines = 1) },
                secondaryLabel = { Text(if (running) "請先暫停偵測" else "引導錄三段動作", maxLines = 1) },
                colors = ChipDefaults.secondaryChipColors(),
                enabled = !running,
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item { Note("依序錄「只拉弓不放箭」「正常射擊」「取箭走動」，每一段自動標注並錄原始資料。把 sessions 資料夾交給 Claude，就能依你的實際資料調整參數") }

        item {
            Chip(
                onClick = {
                    scope.launch {
                        usage = vm.storageUsage()
                        confirmRawDelete = false
                        rawDeleteMessage = null
                    }
                },
                label = { Text("查看佔用空間", maxLines = 1) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        usage?.let { u ->
            item {
                Body(
                    "記錄 ${u.sessionCount} 筆：${formatBytes(u.jsonBytes)}\n" +
                        "原始資料 ${u.rawCount} 個：${formatBytes(u.rawBytes)}\n" +
                        "匯出檔：${formatBytes(u.exportBytes)}"
                )
            }
            if (u.rawCount > 0) {
                if (!confirmRawDelete) {
                    item {
                        Chip(
                            onClick = { confirmRawDelete = true },
                            label = { Text("只刪除原始資料", maxLines = 1) },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                    item { Note("記錄本身會保留，只清掉原始資料 CSV（進行中的那一筆不動）") }
                } else {
                    item { Note("確定刪除 ${u.rawCount} 個原始資料檔嗎？無法復原，記錄本身不受影響") }
                    item {
                        Chip(
                            onClick = {
                                scope.launch {
                                    val r = vm.deleteRawData()
                                    rawDeleteMessage = "已刪除 ${r.count} 個，釋出 ${formatBytes(r.bytes)}"
                                    usage = vm.storageUsage()
                                    confirmRawDelete = false
                                }
                            },
                            label = { Text("確定刪除", maxLines = 1) },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                    item {
                        Chip(
                            onClick = { confirmRawDelete = false },
                            label = { Text("取消", maxLines = 1) },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                }
            }
            rawDeleteMessage?.let { msg -> item { Note(msg) } }
        }

        if (!vm.gyroAvailable) {
            item { Note("此手錶沒有陀螺儀，只會記錄加速度晃動量") }
        }

        // ---- 關於與重設 ----
        item { SectionTitle("關於") }
        item { Body("版本 ${vm.versionLabel}") }
        item { Body("作者 ${AppInfo.AUTHOR}") }
        item { Body("簽章 ${vm.signatureShort}") }
        item { Note(AppInfo.signatureStatus(vm.signatureShort)) }
        item { Note("記錄資料夾（用 adb pull 取出）：\n${vm.dataPath}") }
        if (!confirmReset) {
            item {
                Chip(
                    onClick = { confirmReset = true },
                    label = { Text("恢復預設設定", maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item { Note("把設定頁所有項目還原成預設值。不動記錄、主畫面的箭數和閾值") }
        } else {
            item { Note("確定要把所有設定還原成預設值嗎？") }
            item {
                Chip(
                    onClick = {
                        vm.resetSettings()
                        confirmReset = false
                    },
                    label = { Text("確定還原", maxLines = 1) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Chip(
                    onClick = { confirmReset = false },
                    label = { Text("取消", maxLines = 1) },
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
        secondaryLabel = { Text(if (on) "開" else "關") },
        colors = if (on) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(0.9f),
    )
}
