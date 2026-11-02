package io.trtc.tuikit.chat.demo.xingdun.network

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class XingDunPasswordResetTransportTest {
    private val paths = listOf("auth/sendResetCode", "auth/resetPassword")
    private fun request(url: String) = Request.Builder().url(url)
        .header("X-Test-Context", "synthetic")
        .post("{\"company_code\":\"fixture\",\"target\":\"19900000001\"}".toRequestBody("application/json".toMediaType()))
        .build()
    private fun base() = OkHttpClient.Builder().connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS).callTimeout(2, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true).build()

    @Test fun `reset bodies are one shot and preserve payload type length and context`() {
        val base = base()
        try {
            val transport = XingDunPasswordResetTransport(base)
            for (path in paths) {
                val original = request("http://127.0.0.1/api/$path")
                val protected = transport.newCall(original).request()
                assertTrue(protected.body!!.isOneShot())
                assertEquals(original.url, protected.url)
                assertEquals(original.headers, protected.headers)
                assertEquals(original.body!!.contentType(), protected.body!!.contentType())
                assertEquals(original.body!!.contentLength(), protected.body!!.contentLength())
                assertEquals(Buffer().also { original.body!!.writeTo(it) }.readUtf8(),
                    Buffer().also { protected.body!!.writeTo(it) }.readUtf8())
            }
        } finally { base.connectionPool.evictAll() }
    }

    @Test fun `other endpoints methods and similar names keep the original request`() {
        val base = base()
        try {
            val transport = XingDunPasswordResetTransport(base)
            for (path in listOf("auth/login", "auth/register", "auth/sendResetCodeExtra", "auth/resetPasswordExtra")) {
                val original = request("http://127.0.0.1/api/$path")
                assertSame(original, transport.newCall(original).request())
            }
            for (path in paths) {
                val original = Request.Builder().url("http://127.0.0.1/api/$path").get().build()
                assertSame(original, transport.newCall(original).request())
            }
            assertTrue(base.retryOnConnectionFailure)
            assertTrue(base.followRedirects)
            assertTrue(base.followSslRedirects)
        } finally { base.connectionPool.evictAll() }
    }

    @Test fun `503 Retry After zero never repeats a reset POST`() = single(503, "Retry-After: 0\r\n")
    @Test fun `408 Retry After zero never repeats a reset POST`() = single(408, "Retry-After: 0\r\n")
    @Test fun `307 never redirects a reset POST`() = single(307, "Location: /redirected\r\n")
    @Test fun `308 never redirects a reset POST`() = single(308, "Location: /redirected\r\n")
    @Test fun `301 302 and 303 never change reset POST into a redirected request`() {
        for (status in listOf(301, 302, 303)) single(status, "Location: /redirected\r\n")
    }

    @Test fun `lost response propagates IOException after exactly one reset request`() {
        for (path in paths) LocalServer(null).use { server ->
            val base = base()
            try {
                assertThrows(IOException::class.java) {
                    XingDunPasswordResetTransport(base).newCall(request(server.url(path))).execute().close()
                }
                assertEquals(path, 1, server.requests.get())
            } finally { base.connectionPool.evictAll() }
        }
    }

    @Test fun `successful reset response remains readable without another request`() = single(200, "")

    @Test fun `unrelated endpoint retains existing 503 recovery`() {
        LocalServer(response(503, "Retry-After: 0\r\n")).use { server ->
            val base = base()
            try {
                XingDunPasswordResetTransport(base).newCall(request(server.url("auth/login"))).execute().use {
                    assertEquals(200, it.code)
                }
                assertEquals(2, server.requests.get())
            } finally { base.connectionPool.evictAll() }
        }
    }

    private fun single(status: Int, headers: String) {
        for (path in paths) LocalServer(response(status, headers)).use { server ->
            val base = base()
            try {
                XingDunPasswordResetTransport(base).newCall(request(server.url(path))).execute().use {
                    assertEquals(path, status, it.code)
                    if (status == 200) assertEquals("{}", it.body!!.string())
                }
                assertEquals(path, 1, server.requests.get())
            } finally { base.connectionPool.evictAll() }
        }
    }

    private fun response(status: Int, headers: String) =
        "HTTP/1.1 $status Test\r\n${headers}Content-Length: 2\r\nConnection: close\r\n\r\n{}"

    /** Real loopback HTTP and OkHttp; no SMS, credentials, app account or external connection. */
    private class LocalServer(private val firstResponse: String?) : Closeable {
        private val listener = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val requests = AtomicInteger()
        fun url(path: String) = "http://127.0.0.1:${listener.localPort}/api/$path"
        private val worker = thread(name = "reset-transport-test", isDaemon = true) {
            while (!listener.isClosed) {
                val socket = try { listener.accept() } catch (_: SocketException) { break }
                socket.use {
                    socket.soTimeout = 2_000
                    val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                    input.readLine() ?: return@use
                    var length = 0
                    while (true) {
                        val line = input.readLine() ?: return@use
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                    }
                    while (length > 0) {
                        val count = input.read(CharArray(length))
                        if (count <= 0) return@use
                        length -= count
                    }
                    val count = requests.incrementAndGet()
                    val response = if (count == 1) firstResponse else
                        "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}"
                    response?.let { socket.getOutputStream().write(it.toByteArray(Charsets.UTF_8)); socket.getOutputStream().flush() }
                }
            }
        }
        override fun close() { listener.close(); worker.join(3_000); assertFalse("Fixture worker must stop", worker.isAlive) }
    }
}
