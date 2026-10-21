package com.zgrcc.archeryassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class ShareRouterTest {
    private val files = mapOf("a.json" to "{\"x\":1}", "b.csv" to "t,a\n1,2\n")

    private fun router(zh: Boolean = true) = ShareRouter(
        pin = "1234",
        listFiles = { files.keys.toList() },
        lookup = { n -> files[n]?.let { c -> ShareFile(c.length.toLong()) { c.byteInputStream() } } },
        buildReport = { "<html>report</html>" },
        zh = zh,
    )

    private fun ShareResponse.text() = String(stream().readBytes(), Charsets.UTF_8)

    @Test
    fun rootWithoutPinShowsForm() {
        val r = router().handle("GET", "/")
        assertEquals(200, r.status)
        assertTrue(r.text().contains("<form"))
        assertFalse(r.text().contains("a.json"))
    }

    @Test
    fun correctPinShowsIndexWithLinks() {
        val t = router().handle("GET", "/?pin=1234").text()
        assertTrue(t.contains("/f/a.json?pin=1234"))
        assertTrue(t.contains("/f/b.csv?pin=1234"))
        assertTrue(t.contains("/report.html?pin=1234"))
    }

    @Test
    fun wrongPinIsForbidden() {
        val r = router().handle("GET", "/f/a.json?pin=0000")
        assertEquals(403, r.status)
        assertFalse(r.text().contains("x"))
    }

    @Test
    fun filesNeedPin() {
        assertEquals(403, router().handle("GET", "/f/a.json").status)
    }

    @Test
    fun downloadsFileWithRightType() {
        val j = router().handle("GET", "/f/a.json?pin=1234")
        assertEquals(200, j.status)
        assertTrue(j.contentType.startsWith("application/json"))
        assertEquals("{\"x\":1}", j.text())
        assertEquals(7L, j.length)
        assertTrue(router().handle("GET", "/f/b.csv?pin=1234").contentType.startsWith("text/csv"))
    }

    @Test
    fun reportIsBuiltOnDemand() {
        val r = router().handle("GET", "/report.html?pin=1234")
        assertEquals(200, r.status)
        assertEquals("<html>report</html>", r.text())
    }

    @Test
    fun unknownAndTraversalNamesAreRejected() {
        val r = router()
        assertEquals(404, r.handle("GET", "/f/nope.json?pin=1234").status)
        assertEquals(404, r.handle("GET", "/f/..?pin=1234").status)
        assertEquals(404, r.handle("GET", "/f/..%2Fsecret?pin=1234").status)
        assertEquals(404, r.handle("GET", "/f/a/b?pin=1234").status)
        assertEquals(404, r.handle("GET", "/other?pin=1234").status)
    }

    @Test
    fun onlyReadMethods() {
        assertEquals(405, router().handle("POST", "/?pin=1234").status)
        assertEquals(405, router().handle("DELETE", "/f/a.json?pin=1234").status)
        assertEquals(200, router().handle("HEAD", "/?pin=1234").status)
    }

    @Test
    fun locksAfterTooManyWrongPins() {
        val r = router()
        repeat(ShareRouter.MAX_FAILS) { r.handle("GET", "/?pin=0000") }
        assertTrue(r.locked)
        assertEquals(429, r.handle("GET", "/?pin=1234").status)
    }

    @Test
    fun correctPinResetsFailCount() {
        val r = router()
        repeat(ShareRouter.MAX_FAILS - 1) { r.handle("GET", "/?pin=0000") }
        assertEquals(200, r.handle("GET", "/?pin=1234").status)
        repeat(ShareRouter.MAX_FAILS - 1) { r.handle("GET", "/?pin=0000") }
        assertFalse(r.locked)
    }

    @Test
    fun safeNames() {
        assertTrue(ShareRouter.isSafeName("20261020_193721.json"))
        assertTrue(ShareRouter.isSafeName("summary.csv"))
        assertFalse(ShareRouter.isSafeName(""))
        assertFalse(ShareRouter.isSafeName(".."))
        assertFalse(ShareRouter.isSafeName("a/b"))
        assertFalse(ShareRouter.isSafeName("a\\b"))
        assertFalse(ShareRouter.isSafeName("檔案.json"))
    }

    @Test
    fun onlyLanSourcesAccepted() {
        assertTrue(ShareRouter.isLan(InetAddress.getByName("192.168.1.5")))
        assertTrue(ShareRouter.isLan(InetAddress.getByName("10.0.0.2")))
        assertTrue(ShareRouter.isLan(InetAddress.getByName("127.0.0.1")))
        assertFalse(ShareRouter.isLan(InetAddress.getByName("8.8.8.8")))
    }

    @Test
    fun englishPages() {
        assertTrue(router(zh = false).handle("GET", "/").text().contains("Enter PIN"))
    }

    @Test
    fun emptyFileListMessage() {
        val r = ShareRouter("1234", { emptyList() }, { null }, { "" })
        assertTrue(r.handle("GET", "/?pin=1234").text().contains("沒有檔案"))
    }

    @Test
    fun sizeFormat() {
        assertEquals("12 B", ShareRouter.size(12))
        assertEquals("1.5 KB", ShareRouter.size(1536))
        assertEquals("2.0 MB", ShareRouter.size(2L * 1_048_576))
    }

}
