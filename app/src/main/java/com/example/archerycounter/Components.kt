package com.example.archerycounter

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
