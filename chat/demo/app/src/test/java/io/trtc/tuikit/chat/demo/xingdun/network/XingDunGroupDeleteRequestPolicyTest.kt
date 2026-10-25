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

class XingDunGroupDeleteRequestPolicyTest {
    private fun request(url: String) = Request.Builder().url(url)
        .post("{\"group_id\":\"fixture\",\"message_server_id\":\"M1\"}".toRequestBody("application/json".toMediaType())).build()

    @Test fun `only explicit numeric success envelope with object data can acknowledge deletion`() {
        for (code in listOf("0", "200")) assertEquals("M1", XingDunGroupDeleteRequestPolicy.responseData(
            """{"code":$code,"data":{"message_id":"M1","already_deleted":false}}""")["message_id"].asString)
        for (body in listOf(
            """{"data":{"message_id":"M1","already_deleted":false}}""",
            """{"code":"0","data":{}}""", """{"code":false,"data":{}}""",
            """{"code":0.0,"data":{}}""", """{"code":2e2,"data":{}}""",
            """{"code":403,"data":{}}""", """{"code":0,"data":null}""",
            """{"code":0}""", """{"code":0,"data":[]}""", "[]", "null",
        )) assertThrows(RuntimeException::class.java) { XingDunGroupDeleteRequestPolicy.responseData(body) }
    }

    @Test fun `valid HTTP 200 business unauthorized keeps its numeric classification for read recovery`() {
        val unauthorized = assertThrows(XingDunGroupEndpointBusinessException::class.java) {
            XingDunGroupDeleteRequestPolicy.responseData("""{"code":401,"message":"Login required","trace_id":"fixture","data":null}""")
        }
        assertEquals(401, unauthorized.code)
        assertEquals("Login required", unauthorized.serverMessage)
        assertEquals("fixture", unauthorized.traceID)
        val forbidden = assertThrows(XingDunGroupEndpointBusinessException::class.java) {
            XingDunGroupDeleteRequestPolicy.responseData("""{"code":403}""")
        }
        assertEquals(403, forbidden.code)
        assertThrows(IllegalArgumentException::class.java) { XingDunGroupDeleteRequestPolicy.responseData("""{"code":"401","data":null}""") }
    }

    @Test fun `single attempt transport is limited to the exact groupDelete POST`() {
        val target = request("http://127.0.0.1/api/message/groupDelete")
        assertTrue(XingDunGroupDeleteRequestPolicy.appliesTo(target))
        assertFalse(XingDunGroupDeleteRequestPolicy.isRead(target))
        assertTrue(XingDunGroupDeleteRequestPolicy.isRead(request("http://127.0.0.1/api/message/groupDeleted")))
        assertTrue(XingDunGroupDeleteRequestPolicy.isRead(request("http://127.0.0.1/api/message/groupDeletePermissions")))
        assertFalse(XingDunGroupDeleteRequestPolicy.isRead(request("http://127.0.0.1/api/message/groupDeletePermissionsExtra")))
        assertFalse(XingDunGroupDeleteRequestPolicy.isRead(request("http://127.0.0.1/api/message/groupDeleted").newBuilder().get().build()))
        for (ordinary in listOf(target.newBuilder().get().build(),
            request("http://127.0.0.1/api/message/groupDeleted"), request("http://127.0.0.1/api/message/groupDeletePermissions"),
            request("http://127.0.0.1/api/message/c2cDelete"), request("http://127.0.0.1/api/other/groupDelete"))) {
            assertFalse(XingDunGroupDeleteRequestPolicy.appliesTo(ordinary))
            assertSame(ordinary, XingDunGroupDeleteRequestPolicy.protect(ordinary))
        }
        val base = OkHttpClient()
        val protected = XingDunGroupDeleteRequestPolicy.client(base)
        assertFalse(protected.retryOnConnectionFailure)
        assertFalse(protected.followRedirects)
        assertFalse(protected.followSslRedirects)
        assertTrue(base.retryOnConnectionFailure); assertTrue(base.followRedirects)
    }

    @Test fun `one shot body preserves exact payload media type and length`() {
        val original = request("http://127.0.0.1/api/message/groupDelete")
        val protected = XingDunGroupDeleteRequestPolicy.protect(original)
        assertTrue(protected.body!!.isOneShot())
        assertEquals(original.body!!.contentType(), protected.body!!.contentType())
        assertEquals(original.body!!.contentLength(), protected.body!!.contentLength())
        assertEquals(Buffer().also { original.body!!.writeTo(it) }.readUtf8(), Buffer().also { protected.body!!.writeTo(it) }.readUtf8())
    }

    @Test fun `503 Retry After zero is one POST`() = single(503, "Retry-After: 0\r\n")
    @Test fun `408 Retry After zero is one POST`() = single(408, "Retry-After: 0\r\n")
    @Test fun `307 is never followed`() = single(307, "Location: /redirected\r\n")
    @Test fun `308 is never followed`() = single(308, "Location: /redirected\r\n")
    @Test fun `lost response is unknown outcome and never automatically posts again`() {
        LocalServer(null).use { server ->
            val client = client()
            try { client.newCall(XingDunGroupDeleteRequestPolicy.protect(request(server.url))).execute().close(); fail("Must not report success") }
            catch (_: IOException) { }
            assertEquals(1, server.requests.get()); client.connectionPool.evictAll()
        }
    }
    private fun single(status: Int, headers: String) {
        LocalServer("HTTP/1.1 $status Test\r\n${headers}Content-Length: 0\r\nConnection: close\r\n\r\n").use { server ->
            val client = client()
            client.newCall(XingDunGroupDeleteRequestPolicy.protect(request(server.url))).execute().use { assertEquals(status, it.code) }
            assertEquals(1, server.requests.get()); client.connectionPool.evictAll()
        }
    }
    private fun client() = XingDunGroupDeleteRequestPolicy.client(OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).callTimeout(3, TimeUnit.SECONDS).build())

    /** Local fake HTTP only. No credentials, real IM messages or external network. */
    private class LocalServer(private val firstResponse: String?) : Closeable {
        private val listener = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val requests = AtomicInteger()
        val url = "http://127.0.0.1:${listener.localPort}/api/message/groupDelete"
        private val worker = thread(name = "group-delete-transport-test", isDaemon = true) {
            while (!listener.isClosed) {
                val socket = try { listener.accept() } catch (_: SocketException) { break }
                socket.use {
                    socket.soTimeout = 2_000
                    val input = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                    input.readLine() ?: return@use
                    var count = 0
                    while (true) {
                        val line = input.readLine() ?: return@use
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", true)) count = line.substringAfter(':').trim().toInt()
                    }
                    while (count > 0) {
                        val read = input.read(CharArray(count))
                        if (read <= 0) return@use
                        count -= read
                    }
                    val number = requests.incrementAndGet()
                    val response = if (number == 1) firstResponse else "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    response?.let { socket.getOutputStream().write(it.toByteArray(Charsets.UTF_8)); socket.getOutputStream().flush() }
                }
            }
        }
        override fun close() { listener.close(); worker.join(3_000); assertFalse("Fixture listener must stop", worker.isAlive) }
    }
}
