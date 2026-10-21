package com.zgrcc.archeryassistant

/**
 * 校准录制的三段动作。每一段开始、结束都会标注在记录里，
 * 之后拿原始资料分析时就知道哪一段时间是什么动作。纯 Kotlin。
 */
object Calibration {
    /** [label] 是存進記錄裡的中文名稱（不隨語言改變）；顯示請用 [title]、[hintText]。 */
    class Step(val label: String, val hint: String, val labelEn: String, val hintEn: String) {
        fun title(): String = tr(label, labelEn)
        fun hintText(): String = tr(hint, hintEn)
    }

    val STEPS: List<Step> = listOf(
        Step(
            "只拉弓不放箭", "拉滿弓、瞄準，然後慢慢收回，不要放箭。重複 5～10 次。",
            "Draw only, no release", "Draw to full, aim, then let down slowly without releasing. Repeat 5-10 times.",
        ),
        Step(
            "正常射擊", "照平常的節奏正常射 6～12 箭。",
            "Normal shooting", "Shoot 6-12 arrows at your usual rhythm.",
        ),
        Step(
            "取箭走動", "走去取箭、拔箭、走回射線，照平常的動作做。",
            "Walk to retrieve", "Walk to the target, pull the arrows, walk back to the line as you normally do.",
        ),
    )

    /** 記錄裡存的步驟名稱換成目前語言；不認識的名稱原樣顯示。 */
    fun display(storedLabel: String): String =
        STEPS.firstOrNull { it.label == storedLabel }?.title() ?: storedLabel
}
