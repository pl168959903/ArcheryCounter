package com.zgrcc.archeryassistant

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.InetAddress
import java.security.MessageDigest

/** 區網分享（v1.16.1）可下載的一個檔案：長度與開啟方式。 */
class ShareFile(val length: Long, val open: () -> InputStream)

/** 一次回應。[file] 有值時以串流送出（大檔不整個讀進記憶體），否則送 [body]。 */
class ShareResponse(
    val status: Int,
    val contentType: String,
    val body: ByteArray = ByteArray(0),
    val file: ShareFile? = null,
) {
    val length: Long get() = file?.length ?: body.size.toLong()
    fun stream(): InputStream = file?.open?.invoke() ?: ByteArrayInputStream(body)
}

/**
 * 區網分享的路由與驗證，不碰網路也不碰 Android，方便單元測試。
 * 只提供唯讀：GET / HEAD；需要 PIN；連錯 [MAX_FAILS] 次後全部拒絕，直到重新開始分享。
 */
class ShareRouter(
    private val pin: String,
    private val listFiles: () -> List<String>,
    private val lookup: (String) -> ShareFile?,
    private val buildReport: () -> String,
    private val zh: Boolean = true,
) {
    private var fails = 0
    val locked: Boolean get() = fails >= MAX_FAILS

    fun handle(method: String, target: String): ShareResponse {
        if (method != "GET" && method != "HEAD") return text(405, "Method Not Allowed")
        val q = target.indexOf('?')
        val path = if (q >= 0) target.substring(0, q) else target
        val params = parseQuery(if (q >= 0) target.substring(q + 1) else "")
        if (locked) return text(429, if (zh) "嘗試次數過多，請在手錶重新開始分享" else "Too many attempts; restart sharing on the watch")
        if (path == "/favicon.ico") return text(404, "Not Found")
        val given = params["pin"]
        if (given == null) return if (path == "/") page(pinForm(null)) else text(403, "Forbidden")
        if (!pinOk(given)) {
            fails++
            return if (path == "/") ShareResponse(403, HTML, pinForm(if (zh) "PIN 不正確" else "Wrong PIN").toByteArray(Charsets.UTF_8))
            else text(403, "Forbidden")
        }
        fails = 0
        return when {
            path == "/" -> page(index())
            path == "/" + ReportBuilder.FILE_NAME -> ShareResponse(200, HTML, buildReport().toByteArray(Charsets.UTF_8))
            path.startsWith("/f/") -> {
                val name = path.removePrefix("/f/")
                val f = if (isSafeName(name) && name in listFiles()) lookup(name) else null
                if (f == null) text(404, "Not Found") else ShareResponse(200, typeOf(name), file = f)
            }
            else -> text(404, "Not Found")
        }
    }

    private fun pinOk(given: String) =
        MessageDigest.isEqual(given.toByteArray(Charsets.UTF_8), pin.toByteArray(Charsets.UTF_8))

    private fun page(html: String) = ShareResponse(200, HTML, html.toByteArray(Charsets.UTF_8))

    private fun pinForm(msg: String?): String =
        head(if (zh) "輸入 PIN" else "Enter PIN") +
            (if (msg != null) "<p class=\"e\">${esc(msg)}</p>" else "") +
            "<form method=\"get\" action=\"/\"><input name=\"pin\" inputmode=\"numeric\" autofocus> " +
            "<button>${if (zh) "進入" else "Open"}</button></form></body></html>"

    private fun index(): String {
        val files = listFiles()
        val sb = StringBuilder(head(if (zh) "射箭助手 · 分享" else "Archery Assistant · Share"))
        sb.append("<h1>").append(if (zh) "射箭助手 · 分享" else "Archery Assistant · Share").append("</h1>")
        sb.append("<p><a class=\"big\" href=\"/${ReportBuilder.FILE_NAME}?pin=$pin\">")
            .append(if (zh) "開啟網頁報告（即時產生）" else "Open web report (built now)").append("</a></p>")
        sb.append("<h2>").append(if (zh) "檔案" else "Files").append("</h2><ul>")
        for (n in files) {
            val len = lookup(n)?.length ?: 0L
            sb.append("<li><a href=\"/f/").append(esc(n)).append("?pin=").append(pin).append("\">")
                .append(esc(n)).append("</a> <span>").append(size(len)).append("</span></li>")
        }
        if (files.isEmpty()) sb.append("<li>").append(if (zh) "沒有檔案" else "No files").append("</li>")
        sb.append("</ul></body></html>")
        return sb.toString()
    }

    private fun head(title: String) =
        "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>${esc(title)}</title><style>body{font:16px system-ui,sans-serif;margin:16px;max-width:640px}" +
            "a{color:#0a7a6a}.big{font-weight:600;font-size:18px}li{margin:8px 0;word-break:break-all}span{color:#777;font-size:13px}" +
            ".e{color:#b00020}input{font-size:20px;width:6em}button{font-size:18px}</style></head><body>"

    companion object {
        const val MAX_FAILS = 10
        const val HTML = "text/html; charset=utf-8"

        /** 檔名白名單字元，擋掉路徑穿越（.. 與斜線）。 */
        fun isSafeName(n: String) = n.isNotEmpty() && n.length <= 80 && n != "." && n != ".." &&
            n.all { it.isLetterOrDigit() && it.code < 128 || it == '.' || it == '_' || it == '-' }

        fun typeOf(n: String) = when {
            n.endsWith(".json") -> "application/json; charset=utf-8"
            n.endsWith(".csv") -> "text/csv; charset=utf-8"
            n.endsWith(".html") -> HTML
            else -> "application/octet-stream"
        }

        fun parseQuery(q: String): Map<String, String> =
            q.split('&').filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }

        /** 只接受區網來源：本機、私有網段、連結本地。 */
        fun isLan(a: InetAddress) = a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress

        fun size(b: Long): String = when {
            b >= 1_048_576 -> String.format("%.1f MB", b / 1_048_576.0)
            b >= 1024 -> String.format("%.1f KB", b / 1024.0)
            else -> "$b B"
        }

        private fun text(code: Int, msg: String) = ShareResponse(code, "text/plain; charset=utf-8", msg.toByteArray(Charsets.UTF_8))

        fun reason(code: Int) = when (code) {
            200 -> "OK"; 403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; 429 -> "Too Many Requests"
            else -> "Error"
        }
    }
}

/** 分享失敗原因。 */
enum class ShareError { NO_WIFI, PORT }

/** 區網分享目前狀態；[url] 與 [pin] 只在 [running] 時有意義。 */
data class ShareState(
    val running: Boolean = false,
    val url: String = "",
    val pin: String = "",
    val error: ShareError? = null,
) {
    companion object {
        /** 開始後自動關閉的時間：10 分鐘。 */
        const val AUTO_STOP_MS = 10 * 60 * 1000L
    }
}
