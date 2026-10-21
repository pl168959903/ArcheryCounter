package com.zgrcc.archeryassistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** 文字 → QR Code 的黑白矩陣（true = 深色）。太長或編碼失敗回傳 null。使用 zxing 的編碼器。 */
object QrMatrix {
    fun of(text: String): Array<BooleanArray>? = try {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0)
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        Array(m.height) { y -> BooleanArray(m.width) { x -> m.get(x, y) } }
    } catch (e: Exception) {
        null
    }
}

/** 白底黑點的 QR Code（四周留白）。手機相機掃描需要白底，所以不跟隨深色主題。 */
@Composable
fun QrCode(text: String, size: Dp, modifier: Modifier = Modifier) {
    val matrix = remember(text) { QrMatrix.of(text) } ?: return
    Canvas(modifier.size(size)) {
        val n = matrix.size
        val quiet = 2
        val cell = this.size.width / (n + quiet * 2)
        drawRect(Color.White, Offset.Zero, this.size)
        for (y in 0 until n) for (x in 0 until n) {
            if (matrix[y][x]) drawRect(Color.Black, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
