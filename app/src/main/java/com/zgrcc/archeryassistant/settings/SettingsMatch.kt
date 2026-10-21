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

/** 設定「比賽模式」分類（SettingsCategories 4）。 */
@Composable
fun SettingsMatch(vm: CounterViewModel) {
    val s = vm.settings
    // 賽制
    val compEnabled by s.compEnabled.collectAsStateWithLifecycle()
    val compFormat by s.compFormat.collectAsStateWithLifecycle()
    val compArrows by s.compArrows.collectAsStateWithLifecycle()
    val compEnds by s.compEnds.collectAsStateWithLifecycle()
    val compSeconds by s.compSeconds.collectAsStateWithLifecycle()
    val compPractice by s.compPractice.collectAsStateWithLifecycle()
    // 提醒與流程
    val compPrepare by s.compPrepare.collectAsStateWithLifecycle()
    val compRemind30 by s.compRemind30.collectAsStateWithLifecycle()
    val compRemind10 by s.compRemind10.collectAsStateWithLifecycle()
    val compAutoNext by s.compAutoNext.collectAsStateWithLifecycle()
    // 已存賽制
    val compSavedText by s.compSaved.collectAsStateWithLifecycle()
    // 環數輸入、單箭限時、配色
    val compScoreEntry by s.compScoreEntry.collectAsStateWithLifecycle()
    val compArrowTimer by s.compArrowTimer.collectAsStateWithLifecycle()
    val compArrowSec by s.compArrowSec.collectAsStateWithLifecycle()
    val savedFormats = SavedFormats.decode(compSavedText)
    var confirmDeleteFormat by remember { mutableStateOf(false) }
    val compColors by s.compColors.collectAsStateWithLifecycle()
    // 聲音
    val soundEnabled by s.soundEnabled.collectAsStateWithLifecycle()
    val soundVolume by s.soundVolume.collectAsStateWithLifecycle()
    val soundStyle by s.soundStyle.collectAsStateWithLifecycle()
    val soundCount by s.soundCount.collectAsStateWithLifecycle()
    val soundGo by s.soundGo.collectAsStateWithLifecycle()
    val soundWarn by s.soundWarn.collectAsStateWithLifecycle()
    val soundEnd by s.soundEnd.collectAsStateWithLifecycle()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(SettingsCategories.name(4)) }
        item { ToggleChip(tr("比賽模式", "Match mode"), compEnabled) { s.setCompEnabled(it) } }
        item { Note(tr("主畫面多「比賽模式」：\n• 每趟有準備倒數、射擊倒數（剩 30、10 秒震動）\n• 射滿箭數自動結束，休息後按「下一趟」\n• 自動鎖定觸控、保持亮屏\n• 記錄標為「比賽」，詳情有逐趟用時與逾時統計", "Adds Match mode to the main screen:\n• Each end has a prep countdown and a shooting countdown (buzz at 30 and 10 s left)\n• Ends automatically when all arrows are shot; tap Next end after resting\n• Touch is locked and the screen stays on\n• Records are marked Match, with per-end time and overtime stats in details")) }
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
            item { Note(tr("• 室內 18 米：每趟 3 箭、20 趟、120 秒\n• 室外 70 米：每趟 6 箭、12 趟、240 秒\n• 自訂：自己設定\n• 我的賽制：最多 5 組，點一下依序切換", "• Indoor 18 m: 3 arrows per end, 20 ends, 120 s\n• Outdoor 70 m: 6 arrows per end, 12 ends, 240 s\n• Custom: set your own\n• My formats: up to 5, tap to cycle")) }
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
            item { Note(tr("每一箭限時：射擊開始或射出一箭後重新倒數，超時震動並響兩聲提醒一次。只提醒，不會結束這一趟，每趟總時間照舊", "Time limit for each arrow: restarts when shooting begins or after each arrow; going over buzzes and beeps twice, once. Only a reminder; it does not end the end, and the end clock is unchanged. Off by default")) }
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
            item { Note(tr("每趟結束進入休息畫面時，多一個「輸入環數」按鈕，逐箭輸入該趟環數（可跳過，賽後在詳情頁也能補）。最後一趟射完後比賽直接結束，那一趟請賽後在詳情頁輸入", "At each break an Enter scores button lets you score that end's arrows (skippable; you can also add them later in record details). The match ends right after the last end, so score that one in record details")) }
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
            item { Note(tr("準備階段紅底白字、射擊階段綠底黑字。關閉則維持一般深色畫面", "Red background with white text while getting ready; green with black text while shooting. Off keeps the normal dark screen")) }
            item { ToggleChip(tr("比賽聲音", "Match sounds"), soundEnabled) { s.setSoundEnabled(it) } }
            item { Note(tr("用手錶喇叭（或已連接的藍牙耳機）播放提示音，和震動各自獨立。沒有喇叭的手錶聽不到；手錶處於勿擾或靜音時可能被系統壓低", "Plays tones through the watch speaker (or connected Bluetooth headphones), independent of vibration. Watches without a speaker are silent; Do Not Disturb or silent mode may mute it")) }
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
}
