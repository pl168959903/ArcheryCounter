package com.zgrcc.archeryassistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/** 手機上單欄內容的最大寬度，寬螢幕（平板、折疊、橫向）置中，不拉成一條。 */
val PHONE_MAX_WIDTH = 600.dp

/**
 * 手機外框（v1.23）：內容限制最大寬度並置中；不在首頁時上方有明顯的「返回」按鈕。
 * 手錶（[phone] 為 false）原樣顯示，不加任何外框。
 */
@Composable
fun PhoneFrame(phone: Boolean, showBack: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    if (!phone) {
        content()
        return
    }
    // Android 15 起畫面延伸到狀態列與導覽列底下，手機要自己留出系統列的位置，否則最上面會被蓋住
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxHeight().widthIn(max = PHONE_MAX_WIDTH).fillMaxWidth()) {
            if (showBack) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { onBack() }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("‹  " + tr("返回", "Back"), style = MaterialTheme.typography.button)
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

/** 手機畫面的大按鈕：高度至少 48dp，好點。 */
@Composable
fun PhoneButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (primary) Color(0xFF1976D2) else Color(0xFF2B2B2B))
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.button, color = Color.White)
    }
}

/** 手機畫面的卡片：標題一行，下面幾行說明。 */
@Composable
fun PhoneCard(title: String, lines: List<String>, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
            .clickable { onClick() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.title3, color = Color.White)
        for (l in lines) Text(l, style = MaterialTheme.typography.body2, color = Color(0xFFBDBDBD))
    }
}
