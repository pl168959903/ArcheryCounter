package com.zgrcc.archeryassistant

import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * 區網分享的極簡 HTTP 伺服器（v1.16.1）。只回應 GET / HEAD，只接受區網來源，
 * 每個連線在自己的執行緒處理，逾時 8 秒。路由與驗證在 [ShareRouter]。
 */
class ShareServer(private val router: ShareRouter, private val port: Int = PORT) {
    @Volatile private var server: ServerSocket? = null

    val running: Boolean get() = server?.isClosed == false

    /** 啟動；連線埠被占用等失敗回傳 false。 */
    fun start(): Boolean {
        if (running) return true
        return try {
            val s = ServerSocket(port)
            server = s
            Thread({ acceptLoop(s) }, "share-accept").apply { isDaemon = true }.start()
            true
        } catch (e: IOException) {
            false
        }
    }

    fun stop() {
        try {
            server?.close()
        } catch (_: IOException) {
        }
        server = null
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val c = try {
                s.accept()
            } catch (e: IOException) {
                return
            }
            Thread({ serve(c) }, "share-conn").apply { isDaemon = true }.start()
        }
    }

    private fun serve(c: Socket) {
        try {
            c.use {
                it.soTimeout = 8000
                val ip = it.inetAddress.hostAddress ?: ""
                if (!ShareRouter.isLan(it.inetAddress)) {
                    router.noteRefused(ip)
                    return
                }
                val input = it.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                val line = input.readLine()?.take(2048) ?: return
                // 讀掉其餘標頭（最多 100 行），不使用
                var agent = ""
                var cookie = ""
                var n = 0
                while (n++ < 100) {
                    val h = input.readLine() ?: break
                    if (h.isEmpty()) break
                    when {
                        h.startsWith("User-Agent:", ignoreCase = true) -> agent = h.substringAfter(':').trim().take(300)
                        h.startsWith("Cookie:", ignoreCase = true) -> cookie = h.substringAfter(':').trim().take(600)
                    }
                }
                val parts = line.split(' ')
                val r = if (parts.size >= 2) router.handle(parts[0], parts[1], ShareRequest(ip, agent, cookie))
                else ShareResponse(400, "text/plain; charset=utf-8", "Bad Request".toByteArray())
                val head = "HTTP/1.1 ${r.status} ${ShareRouter.reason(r.status)}\r\n" +
                    "Content-Type: ${r.contentType}\r\nContent-Length: ${r.length}\r\n" +
                    r.headers.joinToString("") { h -> h + "\r\n" } +
                    "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
                val out = it.getOutputStream()
                out.write(head.toByteArray(Charsets.ISO_8859_1))
                if (parts[0] != "HEAD") r.stream().use { s -> s.copyTo(out) }
                out.flush()
            }
        } catch (_: SocketTimeoutException) {
        } catch (_: IOException) {
        }
    }

    companion object {
        const val PORT = 8080

        /** 手錶的區網 IPv4（優先 wlan）；沒有連上網路回傳 null。 */
        fun localIp(): String? = try {
            val all = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            val ordered = all.sortedByDescending { it.name.startsWith("wlan") }
            ordered.flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
