package com.zgrcc.archeryassistant

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/** 小圆按钮，用于 − / +。 */
@Composable
fun SmallRoundButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        colors = ButtonDefaults.secondaryButtonColors(),
    ) {
        Text(label)
    }
}

/** 灰色的小字说明。 */
@Composable
fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(0.85f),
    )
}

/** 置中的一般文字。 */
@Composable
fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption1,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(0.85f),
    )
}

/** 区块标题。 */
@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.title3,
        color = MaterialTheme.colors.primary,
        textAlign = TextAlign.Center,
    )
}

/** 設定「按鈕分色」是否開啟（v1.11.1）。關閉時所有按鈕維持原本的配色。 */
val LocalChipKinds = androidx.compose.runtime.compositionLocalOf { true }

/** 按鈕的用途分類，決定顏色。 */
enum class ChipKind {
    /** 開始、確認、完成 */
    GO,
    /** 點進去是另一頁或另一個清單 */
    NAV,
    /** 刪除、結束、不可復原 */
    DANGER,
    /** 一般操作、切換選項：灰色 */
    NORMAL,
    /** 清單裡的每一項（每筆記錄、每場比賽、每一箭）：黃色、黑字 */
    LIST,
}

private val GO_BG = androidx.compose.ui.graphics.Color(0xFF2E7D32)
private val NAV_BG = androidx.compose.ui.graphics.Color(0xFF1565C0)
private val DANGER_BG = androidx.compose.ui.graphics.Color(0xFFB71C1C)
private val LIST_BG = androidx.compose.ui.graphics.Color(0xFFFFD54F)

/** 依用途回傳按鈕顏色；「按鈕分色」關閉時回傳 [fallback]（原本的配色）。 */
@Composable
fun kindColors(kind: ChipKind, fallback: androidx.wear.compose.material.ChipColors): androidx.wear.compose.material.ChipColors {
    if (!LocalChipKinds.current) return fallback
    val white = androidx.compose.ui.graphics.Color.White
    val soft = androidx.compose.ui.graphics.Color(0xFFE0E0E0)
    return when (kind) {
        ChipKind.GO -> androidx.wear.compose.material.ChipDefaults.chipColors(
            backgroundColor = GO_BG, contentColor = white, secondaryContentColor = soft,
        )
        ChipKind.NAV -> androidx.wear.compose.material.ChipDefaults.chipColors(
            backgroundColor = NAV_BG, contentColor = white, secondaryContentColor = soft,
        )
        ChipKind.DANGER -> androidx.wear.compose.material.ChipDefaults.chipColors(
            backgroundColor = DANGER_BG, contentColor = white, secondaryContentColor = soft,
        )
        ChipKind.LIST -> androidx.wear.compose.material.ChipDefaults.chipColors(
            backgroundColor = LIST_BG,
            contentColor = androidx.compose.ui.graphics.Color.Black,
            secondaryContentColor = androidx.compose.ui.graphics.Color(0xFF3A3A3A),
        )
        ChipKind.NORMAL -> androidx.wear.compose.material.ChipDefaults.secondaryChipColors()
    }
}

/** 「點進去是另一頁」的按鈕在標籤尾巴加「›」，不單靠顏色分辨。 */
@Composable
fun navMark(): String = if (LocalChipKinds.current) " ›" else ""
