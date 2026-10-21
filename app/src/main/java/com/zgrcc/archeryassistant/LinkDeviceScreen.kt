package com.zgrcc.archeryassistant

import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

@Composable
private fun LinkField(value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onBackground),
        cursorBrush = SolidColor(MaterialTheme.colors.primary),
        modifier = Modifier.fillMaxWidth(0.9f),
    )
}

/**
 * 區域網裝置頁（v1.18.1）：點進「連線」頁清單裡的一台裝置，先輸入對方畫面上的 PIN；
 * 通過後可以開啟對方的網頁報告，或把對方的資料匯入這支裝置（含預覽、結果、清除結果）。
 */
@Composable
fun LinkDeviceScreen(vm: CounterViewModel, onOpenReport: () -> Unit) {
    val link by vm.link.collectAsStateWithLifecycle()
    val importUi by vm.importUi.collectAsStateWithLifecycle()
    val overwrite by vm.settings.importOverwrite.collectAsStateWithLifecycle()
    var addr by remember(link.address) { mutableStateOf(link.address) }
    var pin by remember { mutableStateOf("") }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle(link.name.ifBlank { tr("裝置", "Device") }) }
        if (!link.verified) {
            if (link.manual) {
                item { Note(tr("對方位址（例如 192.168.1.23:8080）", "Other device address (e.g. 192.168.1.23:8080)")) }
                item { LinkField(addr) { addr = it.take(40) } }
            } else {
                item { Note(link.address) }
            }
            item { Note(tr("PIN（對方「連線」頁開始分享後顯示的 4 位數；以前連過的可以留空）", "PIN (the 4 digits shown on the other device after it starts sharing; leave empty if you connected before)")) }
            item { LinkField(pin) { pin = it.filter { c -> c.isDigit() }.take(4) } }
            item {
                Chip(
                    onClick = { vm.verifyLink(addr, pin) },
                    enabled = !link.busy,
                    label = { Text(if (link.busy) tr("連線中…", "Connecting…") else tr("連線", "Connect"), maxLines = 1) },
                    colors = kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            link.error?.let { e -> item { Note(ImportText.describe(ImportResult(error = e))) } }
            return@ScalingLazyColumn
        }

        item { Note(tr("已連線 ${link.address}", "Connected ${link.address}")) }
        item {
            Chip(
                onClick = onOpenReport,
                label = { Text(tr("開啟網頁報告", "Open web report") + navMark(), maxLines = 1) },
                secondaryLabel = { Text(tr("顯示這台裝置的資料", "This device's data"), maxLines = 1) },
                colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        item {
            Chip(
                onClick = { vm.startImport() },
                enabled = !importUi.running && importUi.preview == null,
                label = {
                    Text(
                        if (importUi.running) {
                            if (importUi.total > 0) tr("匯入中 ${importUi.done}/${importUi.total}", "Importing ${importUi.done}/${importUi.total}") else tr("處理中…", "Working…")
                        } else {
                            tr("開始匯入", "Start import")
                        },
                        maxLines = 1,
                    )
                },
                colors = kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        importUi.preview?.let { p ->
            item { Note(ImportPreviewText.describe(p, overwrite)) }
            item {
                Chip(
                    onClick = { vm.confirmImport() },
                    label = { Text(tr("確認匯入", "Confirm import"), maxLines = 1) },
                    colors = kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item {
                Chip(
                    onClick = { vm.clearImportResult() },
                    label = { Text(tr("取消", "Cancel"), maxLines = 1) },
                    colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
        importUi.result?.let { r ->
            item { Note(ImportText.describe(r)) }
            item {
                Chip(
                    onClick = { vm.clearImportResult() },
                    label = { Text(tr("清除結果", "Clear result"), maxLines = 1) },
                    colors = kindColors(ChipKind.NORMAL, ChipDefaults.secondaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }
    }
}

/** 顯示對方裝置的網頁報告（v1.18.1）。報告不含腳本，所以不開 JavaScript。 */
@Composable
fun RemoteReportScreen(vm: CounterViewModel) {
    val target = remember { vm.remoteReport() }
    val missing = tr("這台裝置沒有網頁檢視元件，或網址無效", "No web view available on this device, or the address is invalid")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (target == null) {
            Text(missing)
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val v: View = try {
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            settings.allowFileAccess = false
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            val token = target.second
                            if (token != null) {
                                // 已被對方信任：帶上憑證，不必再給 PIN
                                CookieManager.getInstance().setCookie(target.first, "${ShareRouter.COOKIE}=$token")
                            }
                            loadUrl(target.first)
                        }
                    } catch (e: Exception) {
                        TextView(ctx).apply { text = missing }
                    }
                    v
                },
            )
        }
    }
}
