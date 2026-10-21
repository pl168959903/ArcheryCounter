package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.16：進階放箭判定。用真實記錄的片段驗證，也確認舊判定（開關關閉）行為不變。 */
class V116Test {
    /** 把片段餵進偵測器，回傳偵測到幾次。[gyro] 為 false 時模擬沒有陀螺儀。 */
    private fun run(data: FloatArray, advanced: Boolean, threshold: Float = 18f, gyro: Boolean = true): Int {
        var n = 0
        val d = ShotDetector(spikeThreshold = threshold, onShot = { _, _ -> n++ })
        d.gapMs = if (advanced) ShotDetector.ADVANCED_GAP_MS else ShotDetector.GAP_MS
        d.gyroStill = if (advanced) ShotDetector.ADVANCED_GYRO_STILL else 0f
        var i = 0
        while (i < data.size) {
            val t = data[i].toLong()
            if (gyro) d.onGyro(data[i + 2])
            d.onSample(data[i + 1], 0f, 0f, t)
            i += 3
        }
        return n
    }

    @Test
    fun normalReleaseDetectedBothWays() {
        assertEquals(1, run(RealReleaseData.release813750, advanced = false))
        assertEquals(1, run(RealReleaseData.release813750, advanced = true))
    }

    @Test
    fun doublePeakReleaseIsMissedByOldCheckAndFoundByAdvanced() {
        assertEquals(0, run(RealReleaseData.doublePeak829600, advanced = false))
        assertEquals(1, run(RealReleaseData.doublePeak829600, advanced = true))
    }

    @Test
    fun unsteadyWristFalseDetectionsAreRejectedByAdvanced() {
        // 放箭前手腕在轉動（陀螺儀 70～90°/s）：舊判定會算成一箭
        assertEquals(1, run(RealReleaseData.false711931, advanced = false))
        assertEquals(0, run(RealReleaseData.false711931, advanced = true))
        assertEquals(1, run(RealReleaseData.bump670113, advanced = false))
        assertEquals(0, run(RealReleaseData.bump670113, advanced = true))
    }

    @Test
    fun peakJustBelowThresholdNeedsLowerThreshold() {
        // 峰值 17.9：閾值 18 抓不到，調到 17 就抓得到（進階判定不改閾值）
        assertEquals(0, run(RealReleaseData.below18At1739100, advanced = true, threshold = 18f))
        assertEquals(1, run(RealReleaseData.below18At1739100, advanced = true, threshold = 17f))
    }

    @Test
    fun withoutGyroscopeOnlyTheBufferChanges() {
        // 沒有陀螺儀：角速度恆為 0，陀螺儀條件不會擋任何東西；加速度穩定條件照舊
        assertEquals(1, run(RealReleaseData.release813750, advanced = true, gyro = false))
        assertEquals(1, run(RealReleaseData.doublePeak829600, advanced = true, gyro = false))
        assertEquals(1, run(RealReleaseData.false711931, advanced = true, gyro = false))
    }

}
