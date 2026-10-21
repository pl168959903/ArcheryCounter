package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class V1181Test {
    private fun tmp(): File = Files.createTempDirectory("v1181").toFile()
    private fun sess(id: String) =
        Session(id, 0, 5000, 16f, 2, false, false, listOf(ShotEntry(1000, 20f, 16f, 1f, null)), emptyList(), emptyList(), false, emptyList(), null)

    private class Gate(val data: ShareData, override var enabled: Boolean = true) : TrustGate {
        val seen = mutableListOf<ShareEvent>()
        override fun verify(token: String) = data.verify(token)
        override fun issue(ip: String, agent: String) = data.issue(ip, agent)
        override fun record(event: ShareEvent) { seen.add(event); data.log(event) }
    }

    private fun router(gate: Gate) = ShareRouter(
        "1234", { listOf("a.json") }, { ShareFile(2) { "{}".byteInputStream() } }, { "<html/>" }, gate = gate, deviceName = "W",
    )

    private val appUa = AgentLabel.userAgent("1.18.1", "Pixel 8")

    @Test
    fun appUserAgentAndLabel() {
        assertEquals("ArcheryAssistant/1.18.1 (Pixel 8)", appUa)
        assertTrue(AgentLabel.isApp(appUa))
        assertFalse(AgentLabel.isApp("Mozilla/5.0"))
        assertEquals("App · Pixel 8", AgentLabel.of(appUa))
        assertEquals("App", AgentLabel.of("ArcheryAssistant/1.0"))
        assertEquals("ArcheryAssistant/1 (a b)", AgentLabel.userAgent("1", "a)b"))
    }

    @Test
    fun appPinVisitIsLoggedOncePerRun() {
        val d = ShareData(FakePrefs())
        val g = Gate(d)
        val r = router(g)
        val req = ShareRequest("192.168.1.8", appUa)
        r.handle("GET", "/list?pin=1234", req)
        r.handle("GET", "/info?pin=1234", req)
        r.handle("GET", "/f/a.json?pin=1234", req)
        assertEquals(listOf(ShareResult.OK_PIN), g.seen.map { it.result })
        assertEquals("App · Pixel 8", AgentLabel.of(g.seen[0].agent))
    }

    @Test
    fun appBecomesTrustedAutomatically() {
        val d = ShareData(FakePrefs())
        val r = router(Gate(d))
        val resp = r.handle("GET", "/list?pin=1234", ShareRequest("192.168.1.8", appUa))
        val set = resp.headers.single { it.startsWith("Set-Cookie: trust=") }
        val token = set.substringAfter("trust=").substringBefore(';')
        assertEquals(1, d.devices.value.size)
        assertEquals("App · Pixel 8", d.devices.value[0].name)
        // 之後只帶憑證、不給 PIN
        val again = r.handle("GET", "/list", ShareRequest("192.168.1.8", appUa, "trust=$token"))
        assertEquals(200, again.status)
    }

    @Test
    fun trustedAppVisitIsLoggedAsTrust() {
        val d = ShareData(FakePrefs())
        val g = Gate(d)
        val r = router(g)
        val token = d.issue("192.168.1.8", appUa)
        r.handle("GET", "/list", ShareRequest("192.168.1.8", appUa, "trust=$token"))
        assertEquals(listOf(ShareResult.OK_TRUST), g.seen.map { it.result })
    }

    @Test
    fun appWrongPinIsLoggedAndCounted() {
        val d = ShareData(FakePrefs())
        val g = Gate(d)
        val r = router(g)
        assertEquals(403, r.handle("GET", "/list?pin=0000", ShareRequest("192.168.1.8", appUa)).status)
        assertEquals(listOf(ShareResult.WRONG_PIN), g.seen.map { it.result })
    }

    @Test
    fun appNotTrustedWhenFeatureOff() {
        val d = ShareData(FakePrefs())
        val r = router(Gate(d, enabled = false))
        val resp = r.handle("GET", "/list?pin=1234", ShareRequest("192.168.1.8", appUa))
        assertTrue(resp.headers.isEmpty())
        assertTrue(d.devices.value.isEmpty())
    }

    @Test
    fun sameDeviceReTrustReplacesOldEntry() {
        val d = ShareData(FakePrefs())
        val t1 = d.issue("192.168.1.8", appUa)
        val t2 = d.issue("192.168.1.8", appUa)
        assertEquals(1, d.devices.value.size)
        assertNull(d.verify(t1))
        assertNotNull(d.verify(t2))
        d.issue("192.168.1.9", appUa)
        assertEquals(2, d.devices.value.size)
    }

    @Test
    fun httpSourceGetsTokenThenWorksWithoutPin() {
        val remote = SessionStore(tmp())
        remote.importJson("a.json", SessionJson.encode(sess("a")).toByteArray(), false)
        val data = ShareData(FakePrefs())
        val gate = Gate(data)
        val router = ShareRouter("4321", { remote.shareNames() }, { remote.shareFile(it) }, { "" }, gate = gate, deviceName = "W")
        val server = ShareServer(router, 18196)
        assertTrue(server.start())
        try {
            val first = HttpSource("http://127.0.0.1:18196", "4321", appUa)
            val list = first.list()
            assertEquals(listOf("a.json", "shots.csv", "summary.csv"), list.map { it.name })
            assertTrue(first.token.isNotEmpty())
            assertEquals(1, data.devices.value.size)
            // 下次只帶憑證，PIN 留空
            val second = HttpSource("http://127.0.0.1:18196", "", appUa, first.token)
            assertEquals("W", second.deviceName())
            val local = SessionStore(tmp())
            val r = SessionImporter(local, second).run(false, false)
            assertNull(r.error)
            assertEquals(1, r.imported)
            // 沒憑證也沒 PIN → 被拒絕
            val bad = SessionImporter(SessionStore(tmp()), HttpSource("http://127.0.0.1:18196", "", appUa)).run(false, false)
            assertEquals(ImportError.WRONG_PIN, bad.error)
            // 沒帶 PIN 不算輸錯（用過期憑證試探不會累積到鎖定）
            assertTrue(!router.locked)
        } finally {
            server.stop()
        }
    }

    @Test
    fun defaultsAfterChange() {
        val s = AppSettings(FakePrefs())
        assertTrue(s.shareRemember.value)
        assertTrue(s.importDiscover.value)
        assertTrue(s.reportView.value)
    }
}
