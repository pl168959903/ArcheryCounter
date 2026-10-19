package com.example.archerycounter

/**
 * 记录的条件标签：距离和弓种，用预设选项点选，不用打字。
 * 标签就是存在记录里的文字，所以以后改选项名称不会影响旧记录的比对（旧文字还在）。
 */
object Tags {
    val DISTANCES = listOf("室內18米", "室外30米", "室外50米", "室外70米", "其他距離")
    val BOWS = listOf("反曲", "複合", "其他弓種")

    fun distanceOf(tags: List<String>): String? = tags.firstOrNull { it in DISTANCES }
    fun bowOf(tags: List<String>): String? = tags.firstOrNull { it in BOWS }

    /** 依序切换：未设定 → 第一个 → … → 最后一个 → 未设定。 */
    fun nextDistance(current: String?): String? = next(DISTANCES, current)
    fun nextBow(current: String?): String? = next(BOWS, current)

    private fun next(options: List<String>, current: String?): String? {
        val i = options.indexOf(current)
        return when {
            current == null || i < 0 -> options.first()
            i == options.lastIndex -> null
            else -> options[i + 1]
        }
    }

    /** 组成记录的标签清单；没设定的略过。 */
    fun build(distance: String?, bow: String?): List<String> = listOfNotNull(distance, bow)

    /** 两组标签是否是同一种条件（不管顺序）。 */
    fun same(a: List<String>, b: List<String>): Boolean = a.toSet() == b.toSet()

    /** 显示用：「室外70米 · 反曲」；没有标签为 null。 */
    fun label(tags: List<String>): String? = if (tags.isEmpty()) null else tags.joinToString(" · ")
}
