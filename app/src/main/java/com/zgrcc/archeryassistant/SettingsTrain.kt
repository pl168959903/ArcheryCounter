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

/** 設定「訓練」分類（SettingsCategories 3）。 */
@Composable
fun SettingsTrain(vm: CounterViewModel) {
    val s = vm.settings
    val showEnds by s.showEnds.collectAsStateWithLifecycle()
    val arrowsPerEnd by s.arrowsPerEnd.collectAsStateWithLifecycle()
    val endVibration by s.endVibration.collectAsStateWithLifecycle()
    val dailyGoal by s.dailyGoal.collectAsStateWithLifecycle()
    val endFeedback by s.endFeedback.collectAsStateWithLifecycle()
    val fatigueAlert by s.fatigueAlert.collectAsStateWithLifecycle()
    val fatiguePct by s.fatiguePct.collectAsStateWithLifecycle()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(SettingsCategories.name(3)) }
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
}
