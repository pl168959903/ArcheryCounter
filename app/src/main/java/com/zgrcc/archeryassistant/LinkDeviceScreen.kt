package com.zgrcc.archeryassistant

import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
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

/** 有外框、沒填時顯示提示字的輸入框，讓人一眼看出哪裡要輸入（v1.18.3）。 */
@Composable
private fun LinkField(value: String, hint: String, numeric: Boolean = false, onChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .border(1.5.dp, MaterialTheme.colors.primary, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(hint, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurfaceVariant)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Uri),
            textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colors.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 區域網裝置頁（v1.18.1）：點進「連線」頁清單裡的一台裝置，先輸入對方畫面上的 PIN；
 * 通過後可以開啟對方的網頁報告，或把對方的資料匯入這支裝置（含預覽、結果、清除結果）。
 */
@Composable
fun LinkDeviceScreen(vm: CounterViewModel) {
    val ctx = LocalContext.current
    var openFailed by remember { mutableStateOf(false) }
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
                item { Body(tr("對方位址", "Other device address")) }
                item { LinkField(addr, "192.168.1.23:8080") { addr = it.take(40) } }
            } else {
                item { Note(link.address) }
            }
            item { Body(tr("輸入 PIN", "Enter PIN")) }
            item { LinkField(pin, tr("對方畫面上的 4 位數", "4 digits on the other device"), numeric = true) { pin = it.filter { c -> c.isDigit() }.take(4) } }
            item {
                Chip(
                    onClick = { vm.verifyLink(addr, pin) },
                    enabled = !link.busy,
                    label = { Text(if (link.busy) tr("連線中…", "Connecting…") else tr("連線", "Connect"), maxLines = 1) },
                    colors = kindColors(ChipKind.GO, ChipDefaults.primaryChipColors()),
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
            item { Note(tr("PIN 在對方「連線」頁按「開始分享」後顯示。以前連過、對方有記住的可以留空", "The PIN appears on the other device's Connect page after Start sharing. Leave it empty if that device remembers you")) }
            link.error?.let { e -> item { Note(ImportText.describe(ImportResult(error = e))) } }
            return@ScalingLazyColumn
        }

        item { Note(tr("已連線 ${link.address}", "Connected ${link.address}")) }
        item {
            Chip(
                onClick = {
                    val u = vm.remoteReportUrl()
                    openFailed = u == null || !ExternalOpen.url(ctx, u)
                },
                label = { Text(tr("開啟網頁報告", "Open web report") + navMark(), maxLines = 1) },
                secondaryLabel = { Text(tr("用瀏覽器開啟對方的資料", "Open its data in the browser"), maxLines = 1) },
                colors = kindColors(ChipKind.NAV, ChipDefaults.secondaryChipColors()),
                modifier = Modifier.fillMaxWidth(0.9f),
            )
        }
        if (openFailed) item { Note(tr("找不到可以開啟網頁的程式（瀏覽器）", "No browser found to open the page")) }
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
