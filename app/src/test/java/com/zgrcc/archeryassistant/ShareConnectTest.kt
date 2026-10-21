package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareConnectTest {
    private val day = 24L * 60 * 60 * 1000

    private class Gate(val data: ShareData, override var enabled: Boolean = true) : TrustGate {
        val seen = mutableListOf<ShareEvent>()
        override fun verify(token: String) = data.verify(token)
        override fun issue(ip: String, agent: String) = data.issue(ip, agent)
        override fun record(event: ShareEvent) { seen.add(event); data.log(event) }
    }

    private fun router(gate: Gate?, now: () -> Long = { 1000L }) = ShareRouter(
        pin = "1234", listFiles = { listOf("a.json") },
        lookup = { ShareFile(2) { "{}".byteInputStream() } }, buildReport = { "<html/>" },
        gate = gate, clock = now,
    )

    private fun ShareResponse.text() = String(stream().readBytes(), Charsets.UTF_8)
    private fun req(ip: String = "192.168.1.9", cookie: String = "") = ShareRequest(ip, "Mozilla/5.0 (Linux; Android 14) Chrome/120 Safari/537", cookie)

    @Test
    fun agentLabels() {
        assertEquals("Android · Chrome", AgentLabel.of("Mozilla/5.0 (Linux; Android 14) AppleWebKit Chrome/120 Safari/537"))
        assertEquals("iOS · Safari", AgentLabel.of("Mozilla/5.0 (iPhone; CPU iPhone OS 17) AppleWebKit Version/17 Safari/604"))
        assertEquals("Windows · Edge", AgentLabel.of("Mozilla/5.0 (Windows NT 10.0) Chrome/120 Safari/537 Edg/120"))
        assertEquals("?", AgentLabel.of(""))
    }

    @Test
    fun cookieParsing() {
        assertEquals("abc", ShareRouter.cookieValue("a=1; trust=abc; b=2", "trust"))
        assertNull(ShareRouter.cookieValue("a=1", "trust"))
        assertNull(ShareRouter.cookieValue("trust=", "trust"))
    }

    @Test
    fun rememberCheckboxOnlyWhenEnabled() {
        val d = ShareData(FakePrefs())
        assertTrue(router(Gate(d, true)).handle("GET", "/", req()).text().contains("name=\"remember\""))
        assertFalse(router(Gate(d, false)).handle("GET", "/", req()).text().contains("name=\"remember\""))
        assertFalse(router(null).handle("GET", "/", req()).text().contains("name=\"remember\""))
    }

    @Test
    fun rememberIssuesCookieAndNextVisitNeedsNoPin() {
        val d = ShareData(FakePrefs())
        val r = router(Gate(d))
        val first = r.handle("GET", "/?pin=1234&remember=1", req())
        assertEquals(200, first.status)
        val set = first.headers.single { it.startsWith("Set-Cookie: trust=") }
        assertTrue(set.contains("HttpOnly") && set.contains("SameSite=Strict"))
        val token = set.substringAfter("trust=").substringBefore(';')
        assertEquals(1, d.devices.value.size)
        assertFalse(d.devices.value[0].hash == token) // 只存雜湊
        val again = r.handle("GET", "/", req(cookie = "trust=$token"))
        assertEquals(200, again.status)
        assertTrue(again.text().contains("/f/a.json\""))      // 連結不帶 PIN
        assertFalse(again.text().contains("pin="))
        assertEquals(200, r.handle("GET", "/f/a.json", req(cookie = "trust=$token")).status)
    }

    @Test
    fun noRememberNoCookie() {
        val d = ShareData(FakePrefs())
        val r = router(Gate(d)).handle("GET", "/?pin=1234", req())
        assertTrue(r.headers.isEmpty())
        assertTrue(d.devices.value.isEmpty())
    }

    @Test
    fun cookieIgnoredWhenFeatureOff() {
        val d = ShareData(FakePrefs())
        val token = d.issue("1.1.1.1", "ua")
        val r = router(Gate(d, false)).handle("GET", "/f/a.json", req(cookie = "trust=$token"))
        assertEquals(403, r.status)
    }

    @Test
    fun badCookieIsNotTrusted() {
        val d = ShareData(FakePrefs())
        assertEquals(403, router(Gate(d)).handle("GET", "/f/a.json", req(cookie = "trust=deadbeef")).status)
    }

    @Test
    fun trustedPassesEvenWhenPinLocked() {
        val d = ShareData(FakePrefs())
        val token = d.issue("1.1.1.1", "ua")
        val r = router(Gate(d))
        repeat(ShareRouter.MAX_FAILS) { r.handle("GET", "/?pin=0000", req()) }
        assertTrue(r.locked)
        assertEquals(429, r.handle("GET", "/?pin=1234", req()).status)
        assertEquals(200, r.handle("GET", "/", req(cookie = "trust=$token")).status)
    }

    @Test
    fun eventsAreRecorded() {
        val d = ShareData(FakePrefs())
        val g = Gate(d)
        val r = router(g)
        r.handle("GET", "/?pin=0000", req())
        r.handle("GET", "/?pin=1234", req())
        r.handle("GET", "/f/a.json?pin=1234", req())   // 下載不記錄
        r.noteRefused("8.8.8.8")
        assertEquals(listOf(ShareResult.WRONG_PIN, ShareResult.OK_PIN, ShareResult.REFUSED), g.seen.map { it.result })
        assertEquals("192.168.1.9", g.seen[0].ip)
    }

    @Test
    fun maxDevicesKeepsNewest() {
        var t = 1000L
        val d = ShareData(FakePrefs(), { t })
        val tokens = (1..6).map { t += 10; d.issue("ip$it", "ua") }
        assertEquals(ShareData.MAX_DEVICES, d.devices.value.size)
        assertNull(d.verify(tokens[0]))
        assertNotNull(d.verify(tokens[5]))
    }

    @Test
    fun tokenExpiresAfterAYearOfNoUse() {
        var t = 1000L
        val d = ShareData(FakePrefs(), { t })
        val token = d.issue("ip", "ua")
        t += ShareData.VALID_MS - 1
        assertNotNull(d.verify(token))        // 使用後延長
        t += ShareData.VALID_MS + 1
        assertNull(d.verify(token))
    }

    @Test
    fun renameRemoveClear() {
        val d = ShareData(FakePrefs())
        d.issue("a", "ua"); d.issue("b", "ua")
        val id = d.devices.value[0].id
        d.rename(id, "  我的手機  ")
        assertEquals("我的手機", d.devices.value.first { it.id == id }.name)
        d.rename(id, "   ")
        assertEquals("我的手機", d.devices.value.first { it.id == id }.name)
        d.remove(id)
        assertEquals(1, d.devices.value.size)
        d.clearDevices()
        assertTrue(d.devices.value.isEmpty())
    }

    @Test
    fun removedDeviceTokenStopsWorking() {
        val d = ShareData(FakePrefs())
        val token = d.issue("a", "ua")
        d.remove(d.devices.value[0].id)
        assertNull(d.verify(token))
    }

    @Test
    fun persistenceRoundTripWithSpecialChars() {
        val p = FakePrefs()
        val d = ShareData(p)
        val token = d.issue("1.2.3.4", "ua\twith\ttabs\nand\\slash")
        d.rename(d.devices.value[0].id, "a\tb")
        val d2 = ShareData(p)
        assertEquals(1, d2.devices.value.size)
        assertEquals("1.2.3.4", d2.devices.value[0].ip)
        assertEquals("ua\twith\ttabs\nand\\slash", d2.devices.value[0].agent)
        assertNotNull(d2.verify(token))
    }

    @Test
    fun onlineWindow() {
        var t = 1000L
        val d = ShareData(FakePrefs(), { t })
        d.issue("a", "ua")
        assertEquals(1, d.online().size)
        t += ShareData.ONLINE_MS + 1
        assertEquals(0, d.online().size)
    }

    @Test
    fun logOnlyKeepsTodayAndCaps() {
        var t = 3 * day + 12 * 3600_000L
        val d = ShareData(FakePrefs(), { t })
        d.log(ShareEvent(t - 2 * day, "old", "", ShareResult.OK_PIN))
        d.log(ShareEvent(t, "new", "", ShareResult.OK_PIN))
        assertEquals(listOf("new"), d.events.value.map { it.ip })
        repeat(150) { d.log(ShareEvent(t, "x$it", "", ShareResult.WRONG_PIN)) }
        assertEquals(ShareData.MAX_EVENTS, d.events.value.size)
        d.clearLog()
        assertTrue(d.events.value.isEmpty())
    }

    @Test
    fun summaryText() {
        val e = listOf(
            ShareEvent(1, "a", "u1", ShareResult.OK_PIN),
            ShareEvent(2, "a", "u1", ShareResult.WRONG_PIN),
            ShareEvent(3, "b", "u2", ShareResult.OK_TRUST),
        )
        assertEquals("今天 2 支裝置、3 次連線、1 次 PIN 錯誤", ShareText.summary(e))
    }

}
