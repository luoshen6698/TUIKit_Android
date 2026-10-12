package io.trtc.tuikit.chat.demo.xingdun.network

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunCancellableCallTest {
    @Test fun cancellingCoroutineImmediatelyCancelsPendingCall() = runBlocking {
        val call = FakeCall()
        var processed = false
        val pending = launch(start = CoroutineStart.UNDISPATCHED) {
            call.executeCancellable {
                processed = true
                it.body!!.string()
            }
        }
        call.started.await()
        try {
            pending.cancel()
            assertTrue("The cancelled coroutine must cancel its HTTP call", call.isCanceled())
            assertFalse(processed)
        } finally {
            call.releaseExecute.countDown()
            pending.join()
        }
    }

    @Test fun lateResponseAfterCancellationIsClosedWithoutBeingParsed() = runBlocking {
        val call = FakeCall()
        val body = TrackedBody()
        var processed = false
        val pending = launch(start = CoroutineStart.UNDISPATCHED) {
            call.executeCancellable { processed = true }
        }
        call.started.await()
        pending.cancel()
        pending.join()

        call.respond(body)

        assertTrue(body.closed)
        assertFalse(processed)
    }

    @Test fun successfulResponseIsParsedAndClosedBeforeDelivery() = runBlocking {
        val call = FakeCall()
        val body = TrackedBody()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            call.executeCancellable {
                assertFalse(body.closed)
                it.body!!.source().readUtf8()
            }
        }
        call.started.await()
        call.respond(body)

        assertEquals("payload", pending.await())
        assertTrue(body.closed)
        assertFalse(call.isCanceled())
    }

    @Test fun transportFailurePreservesOriginalIOException() = runBlocking {
        val call = FakeCall()
        val expected = IOException("Fake transport failure")
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { call.executeCancellable { Unit } }
        }
        call.started.await()
        call.fail(expected)

        assertPreservedFailure(expected, pending.await().exceptionOrNull())
    }

    @Test fun processingFailureClosesResponseAndPreservesException() = runBlocking {
        val call = FakeCall()
        val body = TrackedBody()
        val expected = IllegalArgumentException("Fake parsing failure")
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { call.executeCancellable<Unit> { throw expected } }
        }
        call.started.await()
        call.respond(body)

        assertPreservedFailure(expected, pending.await().exceptionOrNull())
        assertTrue(body.closed)
    }

    @Test fun cancellationDuringParsingClosesResponseAndDoesNotDeliverResult() = runBlocking {
        val call = FakeCall()
        val body = TrackedBody()
        lateinit var pending: Deferred<String>
        pending = async(start = CoroutineStart.UNDISPATCHED) {
            call.executeCancellable {
                pending.cancel()
                assertTrue(call.isCanceled())
                "discarded"
            }
        }
        call.started.await()
        call.respond(body)
        pending.join()

        assertTrue(pending.isCancelled)
        assertTrue(body.closed)
    }

    @Test fun cancellationAbortsRealCallWaitingForLocalServer() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val peer = AtomicReference<Socket?>()
        val accepted = CompletableDeferred<Unit>()
        val acceptThread = thread(isDaemon = true, name = "cancellable-call-test") {
            runCatching { server.accept() }
                .onSuccess {
                    peer.set(it)
                    accepted.complete(Unit)
                }
                .onFailure { accepted.completeExceptionally(it) }
        }
        val client = OkHttpClient()
        val call = client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/").build())
        val pending = launch(start = CoroutineStart.UNDISPATCHED) {
            call.executeCancellable { Unit }
        }
        try {
            withTimeout(5_000) { accepted.await() }
            pending.cancel()
            withTimeout(2_000) { pending.join() }
            assertTrue(call.isCanceled())
        } finally {
            pending.cancel()
            server.close()
            acceptThread.join(1_000)
            peer.get()?.close()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }

    private fun assertPreservedFailure(expected: Throwable, actual: Throwable?) {
        assertEquals(expected.javaClass, actual?.javaClass)
        assertEquals(expected.message, actual?.message)
        // Coroutine stacktrace recovery may copy the exception and retain the original as a cause.
        assertTrue(generateSequence(actual) { it.cause }.take(8).any { it === expected })
    }

    private class TrackedBody : ResponseBody() {
        var closed = false
        private val content = object : ForwardingSource(Buffer().writeUtf8("payload")) {
            override fun close() {
                closed = true
                super.close()
            }
        }.buffer()

        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = 7L
        override fun source(): BufferedSource = content
    }

    private class FakeCall : Call {
        val started = CompletableDeferred<Unit>()
        val releaseExecute = CountDownLatch(1)
        var callback: Callback? = null
        private val fakeRequest = Request.Builder().url("https://example.invalid/").build()
        @Volatile private var cancelled = false
        @Volatile private var executed = false

        override fun request(): Request = fakeRequest
        override fun execute(): Response {
            executed = true
            started.complete(Unit)
            check(releaseExecute.await(5, TimeUnit.SECONDS)) { "Fake call was not released" }
            if (cancelled) throw IOException("Canceled")
            return response(TrackedBody())
        }
        override fun enqueue(responseCallback: Callback) {
            executed = true
            callback = responseCallback
            started.complete(Unit)
        }
        override fun cancel() {
            cancelled = true
            releaseExecute.countDown()
        }
        override fun isExecuted(): Boolean = executed
        override fun isCanceled(): Boolean = cancelled
        override fun timeout(): Timeout = Timeout()
        override fun clone(): Call = FakeCall()

        fun respond(body: ResponseBody) {
            checkNotNull(callback).onResponse(this, response(body))
        }

        fun fail(error: IOException) {
            checkNotNull(callback).onFailure(this, error)
        }

        fun response(body: ResponseBody): Response = Response.Builder()
            .request(fakeRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }
}
