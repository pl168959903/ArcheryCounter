package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** QR 矩陣（需要 zxing，所以只在 Gradle 的單元測試跑，本機純邏輯測試不含）。 */
class QrMatrixTest {
    @Test
    fun squareAndHasFinderPattern() {
        val m = QrMatrix.of("http://192.168.1.23:8080/?pin=1234")
        assertNotNull(m)
        m!!
        assertEquals(m.size, m[0].size)
        assertTrue(m.size >= 21)
        // 左上角定位圖樣：外框一圈為深色
        for (i in 0..6) {
            assertTrue(m[0][i]); assertTrue(m[6][i]); assertTrue(m[i][0]); assertTrue(m[i][6])
        }
    }

    @Test
    fun sameTextSameMatrix() {
        val a = QrMatrix.of("http://10.0.0.2:8080/?pin=0001")!!
        val b = QrMatrix.of("http://10.0.0.2:8080/?pin=0001")!!
        assertEquals(a.size, b.size)
        for (y in a.indices) for (x in a.indices) assertEquals(a[y][x], b[y][x])
    }
}
