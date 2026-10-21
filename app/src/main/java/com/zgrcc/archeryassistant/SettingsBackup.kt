package com.zgrcc.archeryassistant

/** 備份檔的一筆設定：型別、鍵、值。 */
data class BackupEntry(val type: Char, val key: String, val value: String)

/**
 * 設定備份與還原（v1.18）。純文字檔，每行 `型別:鍵=值`（b 布林、i 整數、l 長整數、f 小數、s 字串）。
 * 備份時略過不該跟著走的項目（本次箭數、更新檢查時間、信任裝置、連線記錄、改版內容快取等）；
 * 還原時再檢查一次鍵名與型別，不認得的行直接忽略。
 */
object SettingsBackup {
    const val FILE_NAME = "settings_backup.txt"
    private const val HEADER = "#ArcheryAssistant-settings"
    private const val MAX_ENTRIES = 300
    private const val MAX_VALUE = 500

    /** 不屬於「設定」、不該備份也不該還原的鍵。 */
    val SKIP = setOf(
        "count", "update_last_check", "update_skipped", "comp_saved", "clean_last",
        "share_devices", "share_events", "changelog_cache", "changelog_tags", "import_addr", "import_origins",
    )
    private val KEY = Regex("^[a-z0-9_]{1,40}$")

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "")
    private fun unesc(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                sb.append(if (s[i + 1] == 'n') '\n' else s[i + 1])
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /** [all] 是 SharedPreferences.getAll() 的內容。 */
    fun encode(all: Map<String, *>, versionLabel: String): String = buildString {
        append(HEADER).append(' ').append(versionLabel.replace("\n", " ")).append('\n')
        for ((k, v) in all.toSortedMap()) {
            if (k in SKIP || !KEY.matches(k)) continue
            val t = when (v) {
                is Boolean -> 'b'
                is Int -> 'i'
                is Long -> 'l'
                is Float -> 'f'
                is String -> 's'
                else -> continue
            }
            append(t).append(':').append(k).append('=').append(esc(v.toString())).append('\n')
        }
    }

    /** 不是備份檔（沒有檔頭）回傳 null。 */
    fun decode(text: String): List<BackupEntry>? {
        val lines = text.replace("\r", "").lines()
        if (lines.firstOrNull()?.startsWith(HEADER) != true) return null
        val out = mutableListOf<BackupEntry>()
        for (line in lines.drop(1)) {
            if (out.size >= MAX_ENTRIES) break
            if (line.length < 4 || line[1] != ':') continue
            val type = line[0]
            val eq = line.indexOf('=')
            if (eq < 3) continue
            val key = line.substring(2, eq)
            val raw = unesc(line.substring(eq + 1))
            if (!KEY.matches(key) || key in SKIP || raw.length > MAX_VALUE) continue
            val ok = when (type) {
                'b' -> raw == "true" || raw == "false"
                'i' -> raw.toIntOrNull() != null
                'l' -> raw.toLongOrNull() != null
                'f' -> raw.toFloatOrNull() != null
                's' -> true
                else -> false
            }
            if (ok) out.add(BackupEntry(type, key, raw))
        }
        return out
    }
}
