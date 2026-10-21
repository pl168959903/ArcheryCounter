package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class V1184Test {
    private class Gate(val data: ShareData) : TrustGate {
        override val enabled = true
        override fun verify(token: String) = data.verify(token)
        override fun issue(ip: String, agent: String) = data.issue(ip, agent)
        override fun record(event: ShareEvent) { data.log(event) }
        override fun revoke(token: String) = data.revoke(token)
    }

    private var now = 1000L
    private val data = ShareData(FakePrefs())
    private val router = ShareRouter(
        pin = "1234", listFiles = { listOf("a.json") }, lookup = { ShareFile(2) { "{}".byteInputStream() } },
        buildReport = { "<html>rep</html>" }, gate = Gate(data), clock = { now },
    )
    private val app = AgentLabel.userAgent("1.18.4", "Pixel")
    private fun ShareResponse.text() = String(stream().readBytes(), Charsets.UTF_8)

    private fun trustedToken(): String {
        val r = router.handle("GET", "/info?pin=1234", ShareRequest("192.168.1.5", app, ""))
        val h = r.headers.first { it.startsWith("Set-Cookie") }
        return ShareRouter.cookieValue(h.substringAfter(": ").substringBefore(';'), ShareRouter.COOKIE)!!
    }

    @Test
    fun reportWithoutPinShowsFormNotForbidden() {
        val r = router.handle("GET", "/report.html", ShareRequest("192.168.1.9", "Mozilla Chrome", ""))
        assertEquals(200, r.status)
        assertTrue(r.text().contains("<form") && r.text().contains("action=\"/report.html\""))
        // 其他路徑仍然是 Forbidden
        assertEquals(403, router.handle("GET", "/f/a.json", ShareRequest("192.168.1.9", "Mozilla", "")).status)
    }

    @Test
    fun ticketOpensReportOnceAndExpires() {
        val tok = trustedToken()
        val t = router.handle("GET", "/ticket", ShareRequest("192.168.1.5", app, "trust=$tok")).text()
        assertTrue(t.length >= 16)
        val browser = ShareRequest("192.168.1.9", "Mozilla Chrome", "")
        val ok = router.handle("GET", "/report.html?ticket=$t", browser)
        assertEquals(200, ok.status)
        assertTrue(ok.text().contains("<html>rep</html>"))
        // 用過就作廢
        assertTrue(!router.handle("GET", "/report.html?ticket=$t", browser).text().contains("<html>rep</html>"))
        // 過期
        val t2 = router.handle("GET", "/ticket", ShareRequest("192.168.1.5", app, "trust=$tok")).text()
        now += ShareRouter.TICKET_MS + 1
        assertTrue(!router.handle("GET", "/report.html?ticket=$t2", browser).text().contains("<html>rep</html>"))
    }

    @Test
    fun ticketRequiresTrust() {
        assertEquals(403, router.handle("GET", "/ticket", ShareRequest("192.168.1.9", "Mozilla", "")).status)
    }

    @Test
    fun revokeRemovesDeviceAndTokenStopsWorking() {
        val tok = trustedToken()
        assertEquals(1, data.devices.value.size)
        assertEquals(200, router.handle("GET", "/revoke", ShareRequest("192.168.1.5", app, "trust=$tok")).status)
        assertEquals(0, data.devices.value.size)
        assertEquals(403, router.handle("GET", "/list", ShareRequest("192.168.1.5", app, "trust=$tok")).status)
        // 沒有憑證不能取消
        assertEquals(403, router.handle("GET", "/revoke", ShareRequest("192.168.1.9", "Mozilla", "")).status)
    }

    @Test
    fun staleTokenWithoutPinDoesNotCountAsWrongPin() {
        val r = ShareRequest("192.168.1.5", app, "trust=deadbeef")
        repeat(15) { assertEquals(403, router.handle("GET", "/info", r).status) }
        assertTrue(!router.locked)
    }

    @Test
    fun linkHelpers() {
        assertEquals("http://h:8080/report.html?ticket=ab12", ReportLink.url("http://h:8080", "1234", "ab12"))
        assertEquals(listOf("a", "b", "c"), ReportLink.tokenOrder("a", listOf("b", "a", "", "c")))
        assertEquals(listOf("b"), ReportLink.tokenOrder(null, listOf("b")))
    }
}
