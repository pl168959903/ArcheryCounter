package com.zgrcc.archeryassistant

import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material.Text

/**
 * 在這台裝置上直接看網頁報告（v1.17）。報告是不含腳本的單一 HTML，所以 WebView 不開 JavaScript。
 * 系統沒有 WebView 時顯示提示，不會當掉。
 */
@Composable
fun ReportScreen(vm: CounterViewModel) {
    var html by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { html = vm.reportHtml() }
    val page = html
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (page == null) {
            Text(tr("產生報告中…", "Building report…"))
        } else {
            val missing = tr("這台裝置沒有網頁檢視元件", "No web view available on this device")
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val v: View = try {
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            settings.allowFileAccess = false
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
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
