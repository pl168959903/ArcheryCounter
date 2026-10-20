package com.zgrcc.archeryassistant

/** 快速誤判檢查（v1.11）：逐箭滑動、一鍵標記。純邏輯：在 [Session.shots] 裡找要看的箭。 */
object QuickCheck {
    /** 回傳下一個（[step] = 1）或上一個（−1）箭的位置；到頭回傳 [from]（不動）。 */
    fun move(count: Int, from: Int, step: Int): Int {
        if (count <= 0) return 0
        return (from + step).coerceIn(0, count - 1)
    }

    /** 開始檢查時的起點：從 0 開始。沒有箭回傳 −1。 */
    fun start(count: Int): Int = if (count <= 0) -1 else 0

    /** 標記後自動跳下一箭；已是最後一箭就停在原位。 */
    fun afterMark(count: Int, from: Int): Int = move(count, from, 1)

    /** 「第 n / 共 m」。 */
    fun progress(count: Int, index: Int): String = "${index + 1} / $count"
}
