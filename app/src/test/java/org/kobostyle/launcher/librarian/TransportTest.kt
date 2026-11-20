package org.kobostyle.launcher.librarian

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

class TransportTest {
    private class Connection(url: URL, val code: Int = 200, val stream: InputStream = ByteArrayInputStream("{}".toByteArray()), val disconnected: () -> Unit = {}) : HttpsURLConnection(url) {
        val sent = ByteArrayOutputStream()
        var closed = false
        var reads = 0
        override fun getOutputStream() = sent
        override fun getInputStream(): InputStream { reads++; return stream }
        override fun getResponseCode() = code
        override fun disconnect() { closed = true; disconnected() }
        override fun usingProxy() = false
        override fun connect() = Unit
        override fun getCipherSuite() = "mock"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getHeaderField(name: String?) = if (name == "Retry-After") "1000" else null
    }
    @Test fun fixedHttpsPostNoRedirectAndTimeoutsAreSet() = runBlocking {
        lateinit var wire: Connection
        val transport = OpenAiTransport { url ->
            assertEquals("https://api.openai.com/v1/responses", url.toString())
            Connection(url).also { wire = it }
        }
        assertEquals(200, transport.post("test-key", "{}").code)
        assertEquals("POST", wire.requestMethod); assertFalse(wire.instanceFollowRedirects)
        assertEquals(10_000, wire.connectTimeout); assertEquals(20_000, wire.readTimeout)
        assertEquals("Bearer test-key", wire.getRequestProperty("Authorization"))
        assertEquals("{}", wire.sent.toString("UTF-8")); assertTrue(wire.closed)
    }
    @Test fun httpErrorDoesNotReadErrorBodyOrFollowRedirect() = runBlocking {
        val connection = Connection(URL("https://api.openai.com/v1/responses"), 302)
        val response = OpenAiTransport { connection }.post("test-key", "{}")
        assertEquals(302, response.code); assertEquals("", response.body); assertEquals(0, connection.reads)
        assertEquals(3, response.retryAfterSeconds); assertTrue(connection.closed)
    }
    @Test fun bodyLimitClosesConnection() = runBlocking {
        val connection = Connection(URL("https://api.openai.com/v1/responses"), stream = ByteArrayInputStream(ByteArray(262_145)))
        try { OpenAiTransport { connection }.post("test-key", "{}"); fail("Oversized body accepted") }
        catch (e: CuratorFailure) { assertEquals("invalid_response", e.kind) }
        assertTrue(connection.closed)
    }
    @Test fun cancellationDisconnectsBlockingRead() = runBlocking {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val stream = object : InputStream() {
            override fun read(): Int { started.countDown(); check(release.await(5, TimeUnit.SECONDS)); return -1 }
        }
        val connection = Connection(URL("https://api.openai.com/v1/responses"), stream = stream, disconnected = { release.countDown() })
        val job = launch(Dispatchers.Default) { OpenAiTransport { connection }.post("test-key", "{}") }
        assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
        withTimeout(2000) { job.cancelAndJoin() }
        assertTrue(connection.closed)
    }
}
