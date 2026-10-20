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

/** 設定「關於與重設」分類（SettingsCategories 7）。 */
@Composable
fun SettingsAbout(vm: CounterViewModel) {
    val s = vm.settings
    val threshold by vm.threshold.collectAsStateWithLifecycle()
    val updateAuto by s.updateAuto.collectAsStateWithLifecycle()
    val updateUi by vm.update.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }


    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(SettingsCategories.name(7)) }
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
