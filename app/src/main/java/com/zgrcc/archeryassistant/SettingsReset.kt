package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
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
import androidx.wear.compose.material.Text
import kotlinx.coroutines.launch

/** 設定「重設與備份」分類（SettingsCategories.RESET，v1.19.3）：設定備份與還原、恢復預設設定。 */
@Composable
fun SettingsReset(vm: CounterViewModel) {
    val s = vm.settings
    val scope = rememberCoroutineScope()
    val backupOn by s.settingsBackup.collectAsStateWithLifecycle()
    var backupResult by remember { mutableStateOf<Boolean?>(null) }
    var backupCount by remember { mutableStateOf<Int?>(null) }
    var backupTick by remember { mutableStateOf(0) }
    var restoreConfirm by remember { mutableStateOf(false) }
    var restored by remember { mutableStateOf<Int?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(backupOn, backupTick) { backupCount = if (backupOn) vm.settingsBackupCount() else null }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(SettingsCategories.name(SettingsCategories.RESET)) }
        item { ToggleChip(tr("設定備份與還原", "Settings backup and restore"), backupOn) { s.setSettingsBackup(it) } }
        item { Note(tr("• 備份：把全部設定存成 sessions/settings_backup.txt\n• 還原：從該檔套用，重新開啟 App 才生效\n不含記錄、本次箭數、信任裝置。", "• Back up: saves all settings to sessions/settings_backup.txt\n• Restore: applies that file; reopen the app to take effect\nRecords, the current count and trusted devices are not included.")) }
        if (backupOn) {
            item {
                Chip(
                    onClick = {
                        scope.launch {
                            backupResult = vm.exportSettingsBackup()
                            backupTick++
                        }
                    },
                    label = { Text(tr("備份設定", "Back up settings"), maxLines = 1) },
                    colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            backupResult?.let { ok ->
                item { Note(if (ok) tr("已存成 sessions/settings_backup.txt", "Saved to sessions/settings_backup.txt") else tr("備份失敗，請確認儲存空間", "Backup failed; check storage")) }
            }
            val cnt = backupCount
            if (cnt != null && restored == null) {
                item {
                    Chip(
                        onClick = {
                            if (!restoreConfirm) {
                                restoreConfirm = true
                            } else {
                                scope.launch {
                                    restored = vm.restoreSettingsBackup() ?: -1
                                    restoreConfirm = false
                                }
                            }
                        },
                        label = { Text(if (restoreConfirm) tr("確定還原", "Confirm restore") else tr("還原設定", "Restore settings"), maxLines = 1) },
                        secondaryLabel = { Text(tr("備份檔有 $cnt 項", "$cnt items in backup"), maxLines = 1) },
                        colors = kindColors(ChipKind.DANGER, if (restoreConfirm) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                if (restoreConfirm) item { Note(tr("會用備份檔覆蓋目前的設定（記錄不受影響）。再按一次確定", "This overwrites your current settings with the backup (records are not affected). Tap again to confirm")) }
            } else if (cnt == null && restored == null) {
                item { Note(tr("目前沒有備份檔。先按「備份設定」，或用「一併取得設定備份」從另一台取回", "No backup file yet. Tap Back up settings, or fetch one from another device with Also get settings backup")) }
            }
            restored?.let { n ->
                if (n >= 0) {
                    item { Note(tr("已還原 $n 項設定。請關閉 App 後重新開啟才會生效", "Restored $n settings. Close and reopen the app to apply them")) }
                    item {
                        Chip(
                            onClick = { vm.exitApp() },
                            label = { Text(tr("關閉 App", "Close app"), maxLines = 1) },
                            colors = kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                } else {
                    item { Note(tr("還原失敗", "Restore failed")) }
                }
            }
        }

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
