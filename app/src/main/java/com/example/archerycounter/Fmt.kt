package com.example.archerycounter

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 界面上用的格式化工具。 */
object Fmt {
    private val dateTimeFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault())
    private val clockFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    /** 例：10/19 21:10 */
    fun dateTime(epochMs: Long): String = dateTimeFmt.format(Instant.ofEpochMilli(epochMs))

    /** 例：21:10:32 */
    fun clock(epochMs: Long): String = clockFmt.format(Instant.ofEpochMilli(epochMs))

    /** 例：12 分 5 秒、45 秒 */
    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return if (total >= 60) "${total / 60} 分 ${total % 60} 秒" else "$total 秒"
    }

    fun num(v: Float, decimals: Int = 2): String =
        String.format(Locale.US, "%.${decimals}f", v)

    /** 空值显示为「—」 */
    fun numOrDash(v: Float?, decimals: Int = 2): String = if (v == null) "—" else num(v, decimals)
}
