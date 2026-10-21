package com.zgrcc.archeryassistant

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/** 對方分享的一個檔案。 */
data class RemoteFile(val name: String, val length: Long)

/** 匯入來源：列出檔案、開啟檔案。真實實作是 [HttpSource]，測試用假的。 */
interface ImportSource {
    @Throws(IOException::class)
    fun list(): List<RemoteFile>

    /** 對方的裝置名稱（用來標記來源）；取不到回傳空字串。 */
    fun deviceName(): String = ""

    @Throws(IOException::class)
    fun open(name: String): InputStream
}

/** 匯入結果。[error] 不為 null 表示整體失敗（連不上、PIN 錯等）。 */
data class ImportResult(
    val imported: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val raw: Int = 0,
    val error: ImportError? = null,
    /** 這次實際寫入的記錄 id（用來標記來源）。 */
    val ids: List<String> = emptyList(),
    /** 有取得對方的設定備份檔（放在 sessions 資料夾，要到「連線」頁按還原才會套用）。 */
    val settings: Boolean = false,
    /** 其中本機原本沒有、這次新增的記錄 id（復原匯入只刪這些）（v1.19）。 */
    val freshIds: List<String> = emptyList(),
    /** 覆蓋了本機原有記錄的筆數（這些無法復原）。 */
    val overwritten: Int = 0,
)

/** 匯入預覽：會新增哪些、本機已有哪些（已有的依「覆蓋」設定略過或覆蓋）。 */
data class ImportPreview(
    val newIds: List<String> = emptyList(),
    val existingIds: List<String> = emptyList(),
    val raw: Int = 0,
    /** 同 id 但唯一碼不同、會另存成新 id 的記錄數（v1.18.2） */
    val collisions: Int = 0,
    val hasSettings: Boolean = false,
    val deviceName: String = "",
    val error: ImportError? = null,
)

enum class ImportError { BAD_ADDRESS, UNREACHABLE, WRONG_PIN, LOCKED, SERVER }

/** 「輸入網址」解析：只接受區網位址（私有網段、本機、連結本地）。純字串處理。 */
object ImportAddress {
    /** 回傳 `http://host:port`；不合法回傳 null。省略連線埠用 8080，可帶或不帶 http://。 */
    fun parse(input: String): String? {
        var t = input.trim().removePrefix("http://").trimEnd('/')
        if (t.isEmpty() || t.contains('/') || t.contains('?') || t.contains('@') || t.contains(' ')) return null
        val host: String
        var port = ShareServer.PORT
        if (t.contains(':')) {
            host = t.substringBefore(':')
            port = t.substringAfter(':').toIntOrNull() ?: return null
        } else {
            host = t
        }
        if (port !in 1..65535) return null
        val parts = host.split('.')
        if (parts.size != 4 || parts.any { it.isEmpty() || it.length > 3 || it.toIntOrNull() == null || it.toInt() !in 0..255 }) return null
        val addr = try {
            InetAddress.getByAddress(ByteArray(4) { i -> parts[i].toInt().toByte() })
        } catch (e: Exception) {
            return null
        }
        if (!ShareRouter.isLan(addr)) return null
        return "http://$host:$port"
    }
}

/** 給外部瀏覽器開的對方報告網址（v1.18.3）：有 PIN 就帶上，沒有就開到 PIN 輸入頁。純字串。 */
object ReportLink {
    fun url(base: String, pin: String, ticket: String = ""): String =
        base.trimEnd('/') + "/" + ReportBuilder.FILE_NAME + when {
            ticket.isNotEmpty() -> "?ticket=" + ticket.filter { it.isLetterOrDigit() }
            pin.isNotEmpty() -> "?pin=" + pin.filter { it.isDigit() }
            else -> ""
        }

    /** 憑證的嘗試順序：這個位址存過的先，其餘（位址變了也許還能用）接著，去重。 */
    fun tokenOrder(forBase: String?, others: List<String>): List<String> =
        (listOfNotNull(forBase?.takeIf { it.isNotEmpty() }) + others.filter { it.isNotEmpty() }).distinct()
}

object ImportPlanner {
    /** 要抓哪些：JSON 一律列入（本機已有且不覆蓋的會在寫入時略過），原始資料 CSV 只在 [withRaw] 時列入，且只抓有對應記錄的。 */
    fun wanted(remote: List<RemoteFile>, withRaw: Boolean): List<RemoteFile> {
        val json = remote.filter { it.name.endsWith(".json") }
        if (!withRaw) return json
        val ids = json.map { it.name.removeSuffix(".json") }.toSet()
        val raw = remote.filter { it.name.endsWith(".csv") && it.name !in SessionStore.exportNames && it.name.removeSuffix(".csv") in ids }
        return json + raw
    }

    fun parseList(text: String): List<RemoteFile> = text.lines().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 2 || p[0].isBlank() || !ShareRouter.isSafeName(p[0])) null else RemoteFile(p[0], p[1].toLongOrNull() ?: 0L)
    }
}

/** 執行匯入：依序抓取，寫進 [store]。單一檔案失敗不影響其他。 */
class SessionImporter(private val store: SessionStore, private val source: ImportSource) {
    /** 不寫入任何東西，只看對方有什麼、本機已有什麼。 */
    fun preview(withRaw: Boolean): ImportPreview {
        val remote = try {
            source.list()
        } catch (e: ImportException) {
            return ImportPreview(error = e.error)
        } catch (e: IOException) {
            return ImportPreview(error = ImportError.UNREACHABLE)
        }
        val items = ImportPlanner.wanted(remote, withRaw)
        val local = store.localIds()
        val jsonIds = items.filter { it.name.endsWith(".json") }.map { it.name.removeSuffix(".json") }
        val existing = jsonIds.filter { it in local }
        // 本機已有的 id：抓對方檔案比唯一碼，不同就是另一支裝置同一秒開始的記錄，會另存
        val clash = existing.filter { id ->
            try {
                val mine = store.load(id)?.uid.orEmpty()
                val theirs = SessionJson.decode(String(source.open("$id.json").use { it.readBytes() }, Charsets.UTF_8), id)?.uid.orEmpty()
                RecordMeta.clashes(mine, theirs)
            } catch (e: Exception) {
                false
            }
        }.toSet()
        return ImportPreview(
            newIds = jsonIds.filter { it !in local || it in clash },
            existingIds = existing.filter { it !in clash },
            collisions = clash.size,
            raw = items.count { it.name.endsWith(".csv") },
            hasSettings = remote.any { it.name == SettingsBackup.FILE_NAME },
            deviceName = safeName(),
        )
    }

    private fun safeName(): String = try {
        source.deviceName()
    } catch (e: IOException) {
        ""
    }

    fun run(
        overwrite: Boolean,
        withRaw: Boolean,
        withSettings: Boolean = false,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportResult {
        val remote = try {
            source.list()
        } catch (e: ImportException) {
            return ImportResult(error = e.error)
        } catch (e: IOException) {
            return ImportResult(error = ImportError.UNREACHABLE)
        }
        val items = ImportPlanner.wanted(remote, withRaw)
        var imported = 0; var skipped = 0; var failed = 0; var raw = 0
        val ids = mutableListOf<String>()
        val fresh = mutableListOf<String>()
        var overwritten = 0
        val before = store.localIds()
        items.forEachIndexed { i, f ->
            var savedId = ""
            val outcome = try {
                if (f.name.endsWith(".json")) {
                    val bytes = source.open(f.name).use { it.readBytes() }
                    val (o, sid) = store.importJsonWithId(f.name, bytes, overwrite)
                    savedId = sid
                    o
                } else {
                    source.open(f.name).use { store.importRaw(f.name, it, overwrite) }
                }
            } catch (e: IOException) {
                ImportOutcome.INVALID
            }
            when (outcome) {
                ImportOutcome.IMPORTED -> if (f.name.endsWith(".json")) {
                    imported++
                    ids.add(savedId)
                    if (savedId in before) overwritten++ else fresh.add(savedId)
                } else {
                    raw++
                }
                ImportOutcome.SKIPPED -> skipped++
                ImportOutcome.INVALID -> failed++
            }
            onProgress(i + 1, items.size)
        }
        var gotSettings = false
        if (withSettings && remote.any { it.name == SettingsBackup.FILE_NAME }) {
            gotSettings = try {
                val text = source.open(SettingsBackup.FILE_NAME).use { String(it.readBytes(), Charsets.UTF_8) }
                SettingsBackup.decode(text) != null && store.writeSettingsBackup(text)
            } catch (e: IOException) {
                false
            }
        }
        return ImportResult(imported, skipped, failed, raw, ids = ids, settings = gotSettings, freshIds = fresh, overwritten = overwritten)
    }
}

class ImportException(val error: ImportError) : IOException(error.name)

/** 透過 HTTP 從另一台裝置的區網分享抓資料。[base] 來自 [ImportAddress.parse]。 */
class HttpSource(
    private val base: String,
    private val pin: String,
    private val userAgent: String = "",
    token: String = "",
) : ImportSource {
    /** 目前使用的信任憑證（開始時帶入的，或對方這次發的）。 */
    var token: String = token
        private set
    private fun connect(path: String): HttpURLConnection {
        // PIN 留空時不帶 pin 參數：對方不會把它算成輸錯，用過期憑證試探也不會累積到鎖定
        val c = URL(if (pin.isEmpty()) "$base$path" else "$base$path?pin=$pin").openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 15000
        c.requestMethod = "GET"
        if (userAgent.isNotEmpty()) c.setRequestProperty("User-Agent", userAgent)
        if (token.isNotEmpty()) c.setRequestProperty("Cookie", "${ShareRouter.COOKIE}=$token")
        return c
    }

    private fun check(c: HttpURLConnection) {
        when (c.responseCode) {
            200 -> {
                // 對方信任這支 App 時會發憑證，之後免輸入 PIN
                c.headerFields["Set-Cookie"]?.firstNotNullOfOrNull { ShareRouter.cookieValue(it.substringBefore(';'), ShareRouter.COOKIE) }
                    ?.let { token = it }
            }
            403 -> throw ImportException(ImportError.WRONG_PIN)
            429 -> throw ImportException(ImportError.LOCKED)
            else -> throw ImportException(ImportError.SERVER)
        }
    }

    override fun list(): List<RemoteFile> {
        val c = connect("/list")
        try {
            check(c)
            return ImportPlanner.parseList(c.inputStream.bufferedReader(Charsets.UTF_8).readText())
        } finally {
            c.disconnect()
        }
    }

    override fun deviceName(): String {
        val c = connect("/info")
        try {
            check(c)
            return c.inputStream.bufferedReader(Charsets.UTF_8).readText().trim().take(40)
        } finally {
            c.disconnect()
        }
    }

    /** 向對方要一張一次性通行票（需要有效的信任憑證）。 */
    fun ticket(): String {
        val c = connect("/ticket")
        try {
            check(c)
            return c.inputStream.bufferedReader(Charsets.UTF_8).readText().trim().take(64)
        } finally {
            c.disconnect()
        }
    }

    /** 請對方把這張憑證的裝置移出已信任清單。 */
    fun revoke() {
        val c = connect("/revoke")
        try {
            check(c)
        } finally {
            c.disconnect()
        }
    }

    override fun open(name: String): InputStream {
        val c = connect("/f/$name")
        check(c)
        return c.inputStream
    }
}

/** 匯入畫面狀態。 */
data class ImportUi(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val result: ImportResult? = null,
    /** 預覽結果（等你按確認）；null 表示沒有在預覽。 */
    val preview: ImportPreview? = null,
    /** 剛復原了幾筆匯入（v1.19）；0 = 沒有 */
    val undone: Int = 0,
)

/** 匯入結果的文字。 */
object ImportText {
    fun describe(r: ImportResult): String = when (r.error) {
        ImportError.BAD_ADDRESS -> tr("位址不正確。請輸入對方畫面上的區網位址，例如 192.168.1.23:8080", "Invalid address. Enter the LAN address shown on the other device, e.g. 192.168.1.23:8080")
        ImportError.UNREACHABLE -> tr("連不上對方。確認兩台在同一個 Wi-Fi，對方正在分享", "Cannot reach the other device. Make sure both are on the same Wi-Fi and it is sharing")
        ImportError.WRONG_PIN -> tr("PIN 不正確", "Wrong PIN")
        ImportError.LOCKED -> tr("對方已鎖定（PIN 錯太多次），請在對方重新開始分享", "The other device is locked (too many wrong PINs); restart sharing there")
        ImportError.SERVER -> tr("對方回應異常，請稍後再試", "Unexpected response from the other device; try again later")
        null -> tr("完成：新增 ${r.imported} 筆", "Done: ${r.imported} imported") +
            (if (r.raw > 0) tr("（含原始資料 ${r.raw} 個）", " (+${r.raw} raw files)") else "") +
            (if (r.skipped > 0) tr("，略過 ${r.skipped} 筆已有的", ", ${r.skipped} skipped (already here)") else "") +
            (if (r.failed > 0) tr("，失敗 ${r.failed} 個", ", ${r.failed} failed") else "") +
            (if (r.overwritten > 0) tr("，其中 ${r.overwritten} 筆覆蓋了本機原有記錄，無法復原", ", ${r.overwritten} overwrote local records and cannot be undone") else "") +
            (if (r.settings) tr("。已取得對方的設定備份，要套用請到下方按「還原設定」", ". Got the other device's settings backup; tap Restore settings below to apply it") else "")
    }
}

/** 預覽結果的文字。 */
object ImportPreviewText {
    fun describe(p: ImportPreview, overwrite: Boolean): String {
        val who = if (p.deviceName.isNotBlank()) tr("對方：${p.deviceName}\n", "From: ${p.deviceName}\n") else ""
        val ex = if (p.existingIds.isEmpty()) "" else if (overwrite)
            tr("，本機已有 ${p.existingIds.size} 筆會被覆蓋", ", ${p.existingIds.size} existing here will be overwritten")
        else tr("，本機已有 ${p.existingIds.size} 筆會略過", ", ${p.existingIds.size} existing here will be skipped")
        val raw = if (p.raw > 0) tr("；原始資料 ${p.raw} 個", "; ${p.raw} raw files") else ""
        val clash = if (p.collisions > 0) tr("（其中 ${p.collisions} 筆與本機同一秒但不同裝置，另存新編號）", " (${p.collisions} share a start second with local records from another device and are saved under a new ID)") else ""
        return who + tr("將新增 ${p.newIds.size} 筆$clash$ex$raw", "Will add ${p.newIds.size}$clash$ex$raw")
    }
}

/**
 * 記住每筆記錄是從哪台裝置匯入的（v1.18）。存在 SharedPreferences 的一個字串，格式每行 `id<Tab>名稱`。
 * 不改記錄檔本身，所以原樣保留對方的內容。
 */
class ImportMarks(private val prefs: android.content.SharedPreferences) {
    private fun load(): MutableMap<String, String> {
        val out = LinkedHashMap<String, String>()
        for (line in (prefs.getString(KEY, "") ?: "").lines()) {
            val p = line.split('\t')
            if (p.size == 2 && p[0].isNotBlank() && p[1].isNotBlank()) out[p[0]] = p[1]
        }
        return out
    }

    fun all(): Map<String, String> = load()

    fun get(id: String): String? = load()[id]

    fun put(ids: Collection<String>, label: String) {
        val clean = label.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim().take(30)
        if (ids.isEmpty() || clean.isEmpty()) return
        val m = load()
        for (id in ids) m[id] = clean
        save(m)
    }

    /** 記錄被刪除時一併清掉標記。 */
    fun forget(ids: Collection<String>) {
        val m = load()
        if (ids.any { m.remove(it) != null }) save(m)
    }

    private fun save(m: Map<String, String>) {
        val text = m.entries.joinToString("\n") { it.key + "\t" + it.value }
        prefs.edit().putString(KEY, text).apply()
    }

    private companion object {
        const val KEY = "import_origins"
    }
}

/** 區域網裝置頁的狀態（v1.18.1）：點進某台裝置、輸入 PIN、驗證通過後才能看報告與匯入。 */
data class LinkDevice(
    val address: String = "",
    val name: String = "",
    val pin: String = "",
    val verified: Boolean = false,
    /** 手動輸入位址（自動尋找找不到時）。 */
    val manual: Boolean = false,
    val busy: Boolean = false,
    val error: ImportError? = null,
)
