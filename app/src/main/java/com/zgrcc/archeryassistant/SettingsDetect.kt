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

/** 設定「偵測」分類（SettingsCategories 1）。 */
@Composable
fun SettingsDetect(vm: CounterViewModel, onOpenCalibration: () -> Unit) {
    val s = vm.settings
    val stillThreshold by s.stillThreshold.collectAsStateWithLifecycle()
    val cooldownSec by s.cooldownSec.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val threshold by vm.threshold.collectAsStateWithLifecycle()
    val mainThreshold by s.mainThreshold.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val autoPause by s.autoPause.collectAsStateWithLifecycle()
    val autoPauseMin by s.autoPauseMin.collectAsStateWithLifecycle()
    val samplingHz by s.samplingHz.collectAsStateWithLifecycle()
    val measuredHz by vm.measuredHz.collectAsStateWithLifecycle()
    val calAdvice by s.calAdvice.collectAsStateWithLifecycle()
    val advancedDetect by s.advancedDetect.collectAsStateWithLifecycle()
    val sensitivityAdvice by s.sensitivityAdvice.collectAsStateWithLifecycle()
    // 閾值建議：按下才計算；套用後顯示已套用
    var showAdvice by remember { mutableStateOf(false) }
    var adviceApplied by remember { mutableStateOf(false) }
    var stillApplied by remember { mutableStateOf(false) }
    var cooldownApplied by remember { mutableStateOf(false) }


    LaunchedEffect(Unit) { vm.refreshSessions() }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(SettingsCategories.name(1)) }
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
                text = tr("閾值 ${threshold.toInt()}", "Threshold ${threshold.toInt()}"),
                onMinus = { vm.adjustThreshold(-1f) },
                onPlus = { vm.adjustThreshold(1f) },
            )
        }
        item { Note(tr("越大越不靈敏。偵測中也可以改。", "Higher = less sensitive. Can be changed while detecting.")) }
        item { ToggleChip(tr("主畫面顯示閾值調整", "Threshold on main screen"), mainThreshold) { s.setMainThreshold(it) } }
        item {
            StepRow(
                text = tr("靜止 $stillThreshold", "Still $stillThreshold"),
                onMinus = { s.setStillThreshold(stillThreshold - 1) },
                onPlus = { s.setStillThreshold(stillThreshold + 1) },
            )
        }
        item {
            Note(tr("放箭前須先靜止到合力低於此值（m/s²，預設 5）。\n• 調大：較容易計入，也較容易誤判\n• 調小：較嚴格，可能漏判", "Must be still (net force below this, m/s², default 5) before a release.\n• Higher: counts more easily, more false detections\n• Lower: stricter, may miss shots"))
        }
        item {
            StepRow(
                text = tr("冷卻 $cooldownSec 秒", "Cooldown $cooldownSec s"),
                onMinus = { s.setCooldownSec(cooldownSec - 1) },
                onPlus = { s.setCooldownSec(cooldownSec + 1) },
            )
        }
        item { Note(tr("兩次計數至少間隔多久（預設 3 秒）。\n• 調短：可能把一次放箭算成兩次\n• 調長：可能漏掉連續兩箭", "Minimum gap between two counts (default 3 s).\n• Shorter: one release may count twice\n• Longer: may miss two quick shots")) }

        item { ToggleChip(tr("自動暫停", "Auto-pause"), autoPause) { s.setAutoPause(it) } }
        item { Note(tr("太久沒放箭就自動暫停並震動，避免忘了按暫停。校準錄製不適用。", "Pauses and buzzes after a long time with no release, so you do not forget to pause. Not used for calibration recordings.")) }
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

        item { ToggleChip(tr("進階放箭判定（試用）", "Advanced release check (trial)"), advancedDetect) { s.setAdvancedDetect(it) } }
        item { Note(tr("試用中，之後可能移除。\n• 穩定檢查與衝擊之間留 0.25 秒緩衝（原 0.1 秒），避免雙峰放箭被漏掉\n• 放箭前手腕轉動須在 40°/s 以內，擋掉碰撞、放下弓\n• 關閉＝舊判定\n下次開始偵測生效", "Trial; may be removed later.\n• 0.25 s buffer between the stability check and the impact (was 0.1 s) so double-peak releases are not missed\n• Wrist rotation must stay within 40°/s before the release, to filter bumps and putting the bow down\n• Off = old detection\nTakes effect the next time detection starts.")) }

        item { ToggleChip(tr("校準自動建議", "Calibration advice"), calAdvice) { s.setCalAdvice(it) } }
        item { Note(tr("校準儲存後，以及校準記錄的詳情頁會多一個「分析校準資料」，用拉弓、走動和正常射擊三段資料給閾值與靜止的建議。只建議，由你決定要不要套用", "After a calibration is saved, and on a calibration record's detail page, adds Analyze calibration: it suggests threshold and Still from the three movements. Advice only")) }

        item { ToggleChip(tr("靈敏度建議", "Sensitivity advice"), sensitivityAdvice) { s.setSensitivityAdvice(it) } }
        item { Note(tr("開啟後，下方「閾值建議」也會一併給「靜止」和「冷卻」的建議（需要標記過誤判的記錄）。只建議，由你決定要不要套用", "When on, Threshold advice below also covers Still and Cooldown (needs records with marked false detections). Advice only; you decide whether to apply")) }

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
}
