package com.zgrcc.archeryassistant

/** 改版內容的一個版本：標題（例如 v1.17）與內文各行。 */
data class ChangelogEntry(val version: String, val lines: List<String>)

/**
 * 改版內容（v1.17.1）：解析專案的 CHANGELOG.md、整理成手錶上好讀的純文字，並可只留已發布的版本。純 Kotlin。
 * 格式約定：`### vX.Y.Z` 開頭是一個版本，之後到下一個 `### ` 前都是它的內容。
 */
object Changelog {
    const val RAW_URL = "https://raw.githubusercontent.com/${ReleaseParser.REPO}/main/CHANGELOG.md"
    const val RELEASES_URL = "https://api.github.com/repos/${ReleaseParser.REPO}/releases?per_page=100"

    fun parse(markdown: String): List<ChangelogEntry> {
        val out = mutableListOf<ChangelogEntry>()
        var version: String? = null
        var lines = mutableListOf<String>()
        fun flush() {
            val v = version
            if (v != null) out.add(ChangelogEntry(v, lines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }))
        }
        for (raw in markdown.replace("\r", "").lines()) {
            if (raw.startsWith("### ")) {
                flush()
                version = raw.removePrefix("### ").trim()
                lines = mutableListOf()
            } else if (version != null) {
                lines.add(raw)
            }
        }
        flush()
        return out
    }

    /** 去掉 Markdown 符號：粗體、行內程式碼；列表符號換成 •（子層 ◦）。 */
    fun clean(line: String): String {
        val indent = line.length - line.trimStart().length
        var t = line.trim().replace("**", "").replace("`", "")
        if (t.startsWith("- ")) t = (if (indent >= 2) "◦ " else "• ") + t.removePrefix("- ")
        return t
    }

    /** 把一個版本的內文整理成要顯示的段落（空行省略）。 */
    fun paragraphs(e: ChangelogEntry): List<String> = e.lines.filter { it.isNotBlank() }.map { clean(it) }

    /** 從 releases 清單的 JSON 取出已發布（非草稿）的標籤；解析不了回傳 null。 */
    fun publishedTags(json: String): Set<String>? {
        val root = try {
            SessionJson.Parser(json).parseDocument() as? List<*>
        } catch (e: Exception) {
            null
        } ?: return null
        return root.mapNotNull { it as? Map<*, *> }
            .filter { it["draft"] != true }
            .mapNotNull { (it["tag_name"] as? String)?.trim() }
            .toSet()
    }

    /** 只留已發布的版本；[published] 為 null（查不到）時全部保留。 */
    fun onlyPublished(entries: List<ChangelogEntry>, published: Set<String>?): List<ChangelogEntry> =
        if (published == null) entries else entries.filter { it.version in published }
}

/** 改版內容頁的狀態。 */
data class ChangelogUi(
    val loading: Boolean = false,
    val entries: List<ChangelogEntry> = emptyList(),
    /** 顯示的是上次存下來的內容（這次沒抓到）。 */
    val offline: Boolean = false,
    val failed: Boolean = false,
)
