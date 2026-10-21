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
    fun run(overwrite: Boolean, withRaw: Boolean, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ImportResult {
        val remote = try {
            source.list()
        } catch (e: ImportException) {
            return ImportResult(error = e.error)
        } catch (e: IOException) {
            return ImportResult(error = ImportError.UNREACHABLE)
        }
        val items = ImportPlanner.wanted(remote, withRaw)
        var imported = 0; var skipped = 0; var failed = 0; var raw = 0
        items.forEachIndexed { i, f ->
            val outcome = try {
                if (f.name.endsWith(".json")) {
                    val bytes = source.open(f.name).use { it.readBytes() }
                    store.importJson(f.name, bytes, overwrite)
                } else {
                    source.open(f.name).use { store.importRaw(f.name, it, overwrite) }
                }
            } catch (e: IOException) {
                ImportOutcome.INVALID
            }
            when (outcome) {
                ImportOutcome.IMPORTED -> if (f.name.endsWith(".json")) imported++ else raw++
                ImportOutcome.SKIPPED -> skipped++
                ImportOutcome.INVALID -> failed++
            }
            onProgress(i + 1, items.size)
        }
        return ImportResult(imported, skipped, failed, raw)
    }
}

class ImportException(val error: ImportError) : IOException(error.name)

/** 透過 HTTP 從另一台裝置的區網分享抓資料。[base] 來自 [ImportAddress.parse]。 */
class HttpSource(private val base: String, private val pin: String) : ImportSource {
    private fun connect(path: String): HttpURLConnection {
        val c = URL("$base$path?pin=$pin").openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 15000
        c.requestMethod = "GET"
        return c
    }

    private fun check(c: HttpURLConnection) {
        when (c.responseCode) {
            200 -> Unit
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
            (if (r.failed > 0) tr("，失敗 ${r.failed} 個", ", ${r.failed} failed") else "")
    }
}
