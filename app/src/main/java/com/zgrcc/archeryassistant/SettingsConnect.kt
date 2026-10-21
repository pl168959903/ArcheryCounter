package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.launch

/**
 * 設定「連線」分類（SettingsCategories 7，v1.16.3）：區網分享、信任裝置、連線記錄。
 * 離開這一頁就停止分享；分享中螢幕保持亮著。
 */
@Composable
fun SettingsConnect(vm: CounterViewModel, onOpenOptions: () -> Unit = {}, onOpenDevice: () -> Unit = {}, onOpenLog: () -> Unit = {}) {
    val s = vm.settings
    val lanShare by s.lanShare.collectAsStateWithLifecycle()
    val logOn by s.shareLog.collectAsStateWithLifecycle()
    val share by vm.share.collectAsStateWithLifecycle()
    val devices by vm.trustedDevices.collectAsStateWithLifecycle()
    val events by vm.shareEvents.collectAsStateWithLifecycle()
    val discover by s.importDiscover.collectAsStateWithLifecycle()
    val shareQr by s.shareQr.collectAsStateWithLifecycle()
    val backupOn by s.settingsBackup.collectAsStateWithLifecycle()
    val found by vm.foundDevices.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var backupResult by remember { mutableStateOf<Boolean?>(null) }
    var backupCount by remember { mutableStateOf<Int?>(null) }
    var backupTick by remember { mutableStateOf(0) }
    var restoreConfirm by remember { mutableStateOf(false) }
    var restored by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(backupOn, backupTick) { backupCount = if (backupOn) vm.settingsBackupCount() else null }
    LaunchedEffect(discover) {
        vm.leaveLinkDevice()
        if (discover) vm.startDiscovery()
    }
    DisposableEffect(Unit) { onDispose { vm.stopDiscovery() } }
    val view = LocalView.current
    // 正在管理的信任裝置（null = 看列表）與改名輸入
    var selectedId by remember { mutableStateOf<String?>(null) }
    var nameDraft by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    DisposableEffect(share.running) {
        view.keepScreenOn = share.running
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) { onDispose { vm.stopShare() } }

    val selected = devices.firstOrNull { it.id == selectedId }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(SettingsCategories.name(SettingsCategories.CONNECT)) }

        if (selected != null) {
            // ── 單一信任裝置：改名、刪除 ──
            item { Body(tr("管理裝置", "Manage device")) }
            item { Note(tr("位址 ${selected.ip}\n${selected.agent.take(60)}\n最近連線 ${ShareText.clock(selected.lastSeenMs)}", "Address ${selected.ip}\n${selected.agent.take(60)}\nLast seen ${ShareText.clock(selected.lastSeenMs)}")) }
            item {
                BasicTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(24) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Chip(
                    onClick = { vm.renameTrusted(selected.id, nameDraft) },
                    label = { Text(tr("儲存名稱", "Save name"), maxLines = 1) },
                    colors = kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Chip(
                    onClick = {
                        vm.removeTrusted(selected.id)
                        selectedId = null
                    },
                    label = { Text(tr("刪除這支裝置", "Remove device"), maxLines = 1) },
                    colors = kindColors(ChipKind.DANGER, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Chip(
                    onClick = { selectedId = null },
                    label = { Text(tr("返回", "Back"), maxLines = 1) },
                    colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            return@ScalingLazyColumn
        }

        // ── 區網分享 ──
        item { ToggleChip(tr("區網分享", "LAN share"), lanShare) { on -> s.setLanShare(on); if (!on) vm.stopShare() } }
        item { Note(tr("開啟後出現「開始分享」。手錶和手機連同一個 Wi-Fi 時，在手機瀏覽器輸入畫面上的網址和 PIN，就能直接看報告、下載資料，不必用 adb。只提供唯讀下載，PIN 錯 10 次就鎖住，10 分鐘後或離開此頁自動關閉。預設關閉", "When on, a Start sharing button appears. With the watch and phone on the same Wi-Fi, enter the address and PIN shown into the phone browser to view the report and download data without adb. Read-only downloads; 10 wrong PINs lock it; it stops after 10 minutes or when you leave this page. Off by default")) }
        if (lanShare) {
            if (!share.running) {
                item {
                    Chip(
                        onClick = { vm.startShare() },
                        label = { Text(tr("開始分享", "Start sharing"), maxLines = 1) },
                        colors = kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                share.error?.let { e ->
                    item {
                        Note(
                            when (e) {
                                ShareError.NO_WIFI -> tr("找不到區網位址，請確認已連上 Wi-Fi", "No LAN address; make sure Wi-Fi is connected")
                                ShareError.PORT -> tr("連線埠 ${ShareServer.PORT} 被占用，請稍後再試", "Port ${ShareServer.PORT} is busy; try again later")
                            }
                        )
                    }
                }
            } else {
                item { Body(tr("瀏覽器輸入：\n${share.url}\nPIN：${share.pin}", "Open in a browser:\n${share.url}\nPIN: ${share.pin}")) }
                if (shareQr) {
                    item { QrCode(text = share.url + "/?pin=" + share.pin, size = 150.dp) }
                    item { Note(tr("手機相機掃這個 QR Code 就能直接開啟（已含 PIN）", "Scan this QR code with the phone camera to open it directly (PIN included)")) }
                }
                item { Note(tr("分享中，螢幕會保持亮著。10 分鐘後自動關閉", "Sharing; the screen stays on. Stops automatically after 10 minutes")) }
                item {
                    Chip(
                        onClick = { vm.stopShare() },
                        label = { Text(tr("停止分享", "Stop sharing"), maxLines = 1) },
                        colors = kindColors(ChipKind.DANGER, ChipDefaults.primaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
        }

        // ── 連結設定 ──
        item {
            Chip(
                onClick = onOpenOptions,
                label = { Text(tr("連結設定", "Link settings") + navMark(), maxLines = 1) },
                secondaryLabel = { Text(tr("信任、QR、尋找、匯入選項…", "Trust, QR, discovery, import options…"), maxLines = 2) },
                colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }

        // ── 區域網裝置 ──
        item { SectionTitle(tr("區域網裝置", "LAN devices")) }
        if (!discover) {
            item { Note(tr("自動尋找已關閉。可以手動輸入位址，或到「連結設定」開啟區域網自動尋找", "Auto-discovery is off. Enter an address manually, or turn it on in Link settings")) }
        } else if (found.isEmpty()) {
            item { Note(tr("尋找中…（對方要開著「區網分享」。找不到時請手動輸入位址）", "Searching… (the other device must be sharing. Enter the address manually if nothing shows up)")) }
        }
        for (d in found) {
            item {
                Chip(
                    onClick = {
                        vm.openLinkDevice(d.address, d.name)
                        onOpenDevice()
                    },
                    label = { Text(d.name + navMark(), maxLines = 1) },
                    secondaryLabel = { Text(d.address, maxLines = 1) },
                    colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
        item {
            Chip(
                onClick = {
                    vm.openManualDevice()
                    onOpenDevice()
                },
                label = { Text(tr("手動輸入位址", "Enter address manually") + navMark(), maxLines = 1) },
                colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item { Note(tr("點進裝置後輸入對方畫面上的 PIN，就能看對方的網頁報告或把資料匯入這支裝置", "Open a device and enter the PIN shown on it to view its web report or import its data here")) }

        // ── 設定備份與還原 ──
        item { ToggleChip(tr("設定備份與還原", "Settings backup and restore"), backupOn) { s.setSettingsBackup(it) } }
        item { Note(tr("開啟後多兩個按鈕：把目前所有設定存成 sessions/settings_backup.txt（也能用區網分享或 adb 取出），以及從這個檔案還原。換手錶或重裝時很方便。不含記錄、本次箭數和信任裝置。還原後要重新開啟 App 才生效。預設開", "When on, adds two buttons: save all current settings to sessions/settings_backup.txt (also available through LAN share or adb) and restore from that file. Handy when changing watches or reinstalling. Does not include records, the current count or trusted devices. Restart the app after restoring. On by default")) }
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

        // ── 已信任的裝置 ──
        item { SectionTitle(tr("已信任的裝置", "Trusted devices")) }
        if (devices.isEmpty()) {
            item { Note(tr("目前沒有。在網頁勾選「記住這個裝置」後會出現在這裡", "None yet. Devices appear here after you tick Remember this device on the web page")) }
        } else {
            val onlineIds = vm.onlineTrusted().map { it.id }.toSet()
            for (d in devices) {
                item {
                    Chip(
                        onClick = {
                            selectedId = d.id
                            nameDraft = d.name
                        },
                        label = { Text((if (d.id in onlineIds) "● " else "") + d.name + navMark(), maxLines = 1) },
                        secondaryLabel = { Text(ShareText.clock(d.lastSeenMs) + " · " + d.ip, maxLines = 1) },
                        colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
            item { Note(tr("● 表示最近 5 分鐘內連過。點一下可改名或刪除", "● = connected in the last 5 minutes. Tap to rename or remove")) }
            item {
                if (!confirmClear) {
                    Chip(
                        onClick = { confirmClear = true },
                        label = { Text(tr("全部清除信任", "Remove all trust"), maxLines = 1) },
                        colors = kindColors(ChipKind.DANGER, ChipDefaults.secondaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                } else {
                    Chip(
                        onClick = {
                            vm.clearTrusted()
                            confirmClear = false
                        },
                        label = { Text(tr("確定清除全部", "Confirm: remove all"), maxLines = 1) },
                        colors = kindColors(ChipKind.DANGER, ChipDefaults.primaryChipColors()),
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
            }
        }

        // ── 連線記錄：獨立一頁（v1.18.5），這裡只顯示按鈕和大致計數 ──
        item {
            Chip(
                onClick = onOpenLog,
                label = { Text(tr("連線記錄", "Connection log") + navMark(), maxLines = 1) },
                secondaryLabel = { Text(if (logOn) ShareText.summary(events) else tr("「記錄連線」已關閉", "Logging is off"), maxLines = 2) },
                colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
    }
}
