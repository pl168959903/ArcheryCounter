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
    val found by vm.foundDevices.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
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
        item { Note(tr("開啟後出現「開始分享」。同一 Wi-Fi 的手機瀏覽器輸入畫面上的網址和 PIN，可看報告、下載資料。\n• 唯讀\n• PIN 錯 10 次就鎖住\n• 依「分享自動結束」的時間或離開此頁自動關閉", "Turns on Start sharing. A phone browser on the same Wi-Fi can open the address and PIN shown here to view the report and download data.\n• Read-only\n• Locks after 10 wrong PINs\n• Stops after the Auto-stop time or when you leave this page")) }
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
                item { Note(tr("分享中，螢幕保持亮著。", "The screen stays on while sharing.")) }
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
