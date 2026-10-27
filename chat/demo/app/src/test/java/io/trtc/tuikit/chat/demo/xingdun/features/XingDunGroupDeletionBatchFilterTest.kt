package io.trtc.tuikit.chat.demo.xingdun.features

import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageSenderInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload
import io.trtc.tuikit.chat.demo.xingdun.session.*
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class XingDunGroupDeletionBatchFilterTest {
    private fun row(id: String, self: Boolean = false, status: MessageStatus = MessageStatus.SEND_SUCCESS) =
        MessageInfo(msgID = id, from = MessageSenderInfo(userID = if (self) "self" else "peer"), isSentBySelf = self, status = status)

    private class Fixture {
        val storage = XingDunSessionStorageRevision.forStore("batch-${System.nanoTime()}")
        val gate = XingDunAuthenticationCommitGate()
        // Synthetic fixture only. Exercise actual JCE decrypt on the production batch's caller/IO path;
        // this does not emulate Android Keystore Binder latency or use stored app credentials.
        private val key = SecretKeySpec(ByteArray(16) { it.toByte() }, "AES")
        private val iv = ByteArray(12) { (it + 1).toByte() }
        private fun encrypt(value: String): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, this@Fixture.iv)); doFinal(value.toByteArray())
        }
        private var encryptedAccount: ByteArray? = encrypt("tenant-api-self")
        var account: String? = "tenant-api-self"
            set(value) { field = value; encryptedAccount = value?.let(::encrypt) }
        val decryptThreads = CopyOnWriteArrayList<String>()
        var loads = 0; var deletedReads = 0; var failures = 0; var queryCount = 0
        var revision = 0L
        var deleted = emptySet<String>()
        var loadAction: () -> Unit = {}
        var verifyAction: suspend () -> Unit = {}
        var deletedAction: () -> Unit = {}
        val loadThreads = CopyOnWriteArrayList<String>()
        val state = XingDunGroupDeletionVisibilityState()
        fun stamp() = storage.current()?.let { stored -> gate.currentReadGeneration()?.let { XingDunSessionReadStamp(stored, it) } }
        fun load(): XingDunGroupDeletionReadScope? = readConsistentSessionSnapshot(::stamp) {
            loads++; loadThreads += Thread.currentThread().name
            val data = encryptedAccount ?: return@readConsistentSessionSnapshot null
            val decoded = Cipher.getInstance("AES/GCM/NoPadding").run {
                decryptThreads += Thread.currentThread().name
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, this@Fixture.iv)); String(doFinal(data))
            }
            loadAction()
            XingDunMessageDisplayScope(decoded, gate.currentGeneration(), "self")
        }?.let { XingDunGroupDeletionReadScope(it.second, it.first) }
        fun filter(io: CoroutineDispatcher = Dispatchers.IO) = XingDunGroupDeletionBatchFilter(
            ::load, ::stamp,
            { _, _ -> deletedReads++; deletedAction(); deleted },
            { conversation, source, ids ->
                queryCount++; verifyAction()
                if (state.checked(source.display.groupKey(conversation), ids)) revision++
            },
            { _, _ -> failures++ }, state, { revision }, io,
        )
        fun checked(vararg ids: String) { state.checked("$account:${gate.currentGeneration()}:group_g", ids.toSet()) }
    }

    @Test fun `actual provider batch reads bound identity once and tombstones once for hundred rows and quotes`() = runBlocking {
        val f = Fixture(); f.deleted = setOf("M2", "Q")
        val rows = (1..100).map { row("M$it").copy(quoteInfo = MessageQuoteInfo(msgID = "Q", messagePayload = TextMessagePayload(text = "quote"))) }
        val actual = f.filter().filter("group_g", rows)
        assertEquals(99, actual.size); assertFalse(actual.any { it.msgID == "M2" })
        assertTrue(actual.all { it.quoteInfo?.status == MessageStatus.DELETED && it.quoteInfo?.messagePayload == null })
        assertEquals("One bound-session read independent of message and quote count", 1, f.loads)
        assertEquals("One durable tombstone snapshot", 1, f.deletedReads)
        assertEquals("100 queried IDs per request preserved", 2, f.queryCount)
        assertNotNull(rows.first().quoteInfo?.messagePayload)
    }

    @Test fun `main caller never executes bound load and remains responsive during blocked decrypt double`() = runBlocking {
        val main = Executors.newSingleThreadExecutor { r -> Thread(r, "RegressionMain") }.asCoroutineDispatcher()
        val io = Executors.newSingleThreadExecutor { r -> Thread(r, "RegressionIO") }.asCoroutineDispatcher()
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val owner = CoroutineScope(main + SupervisorJob()); val f = Fixture()
        f.loadAction = { started.countDown(); check(release.await(2, TimeUnit.SECONDS)) }
        val task = owner.async { f.filter(io).filter("group_g", (1..100).map { row("M$it") }) }
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val pulse = owner.async { "responsive" }
            val responsive = try { withTimeout(200) { pulse.await() == "responsive" } } catch (_: TimeoutCancellationException) { false }
            release.countDown(); assertEquals(100, task.await().size)
            assertTrue("Caller executor must remain responsive while injected bound/decrypt work waits", responsive)
            assertTrue("Bound loader actually decrypted a synthetic JCE fixture", f.decryptThreads.isNotEmpty())
            assertTrue("No bound/decrypt callback may execute on the caller Main executor", f.loadThreads.none { it == "RegressionMain" })
            assertTrue("Actual Cipher init and doFinal never execute on the caller Main executor", f.decryptThreads.none { it == "RegressionMain" })
        } finally { release.countDown(); owner.cancel(); main.close(); io.close() }
    }

    @Test fun `write during bound scope read rejects before verification`() = runBlocking {
        val f = Fixture(); f.loadAction = { f.storage.write { } }
        assertTrue(f.filter().filter("group_g", listOf(row("M1"))).isEmpty()); assertEquals(0, f.queryCount)
    }

    @Test fun `missing or unreadable bound session remains empty without granting a query`() = runBlocking {
        val f = Fixture(); f.account = null
        assertTrue(f.filter().filter("group_g", listOf(row("M1"))).isEmpty()); assertEquals(0, f.queryCount)
    }

    @Test fun `same account authentication begin before store write discards late reply`() = runBlocking {
        val f = Fixture(); f.verifyAction = { f.gate.begin() }
        assertTrue(f.filter().filter("group_g", listOf(row("M1"))).isEmpty())
    }

    @Test fun `session clear enterprise and credential rewrite invalidate an in flight batch`() = runBlocking {
        for (kind in listOf("session-clear", "enterprise-change", "refresh-write", "password-replacement")) {
            val f = Fixture(); f.verifyAction = { f.storage.write { if (kind == "session-clear") f.account = null } }
            assertTrue(kind, f.filter().filter("group_g", listOf(row("M1"))).isEmpty())
        }
    }

    @Test fun `another tenant cannot publish old reply or reuse its checked authority`() = runBlocking {
        val f = Fixture(); f.verifyAction = { f.storage.write { f.account = "other-tenant-self" } }
        assertTrue(f.filter().filter("group_g", listOf(row("M1"))).isEmpty())
        f.verifyAction = { throw IOException("synthetic unavailable") }
        assertTrue(f.filter().filter("group_g", listOf(row("M1"))).isEmpty())
    }

    @Test fun `network failure retains only checked rows with failure status and redacts unknown quotes`() = runBlocking {
        val f = Fixture(); f.checked("safe"); f.verifyAction = { throw IOException("synthetic unavailable") }
        val safe = row("safe").copy(quoteInfo = MessageQuoteInfo(msgID = "unknown", messagePayload = TextMessagePayload(text = "not authorized")))
        val actual = f.filter().filter("group_g", listOf(safe, row("unknown")))
        assertEquals(listOf("safe"), actual.map { it.msgID }); assertNull(actual.single().quoteInfo?.messagePayload)
        assertEquals(1, f.failures)
    }

    @Test fun `cancellation propagates without recording transport failure`() = runBlocking {
        val f = Fixture(); f.verifyAction = { throw CancellationException("synthetic cancelled") }
        try { f.filter().filter("group_g", listOf(row("M1"))); fail() } catch (_: CancellationException) { }
        assertEquals(0, f.failures)
    }

    @Test fun `own Sending and Failed keep prior display rules while imported success stays hidden`() = runBlocking {
        val f = Fixture(); f.verifyAction = { throw IOException("synthetic unavailable") }
        val actual = f.filter().filter("group_g", listOf(row("local", true, MessageStatus.SENDING), row("failed", true, MessageStatus.SEND_FAIL), row("imported", true)))
        assertEquals(listOf("local", "failed"), actual.map { it.msgID })
        f.state.hold("${f.account}:${f.gate.currentGeneration()}:group_g", setOf("local"))
        assertTrue(f.filter().filter("group_g", listOf(row("local", true, MessageStatus.SENDING))).isEmpty())
    }

    @Test fun `visibility revision change during actual batch mapping discards publication`() = runBlocking {
        val f = Fixture(); f.deletedAction = { f.revision++ }
        assertTrue(f.filter().filter("group_g", listOf(row("M1"))).isEmpty())
    }

    @Test fun `non group input preserves exact rows and does not load session or group policy`() = runBlocking {
        val f = Fixture(); f.account = null; val rows = listOf(row("C1"))
        assertSame(rows, f.filter().filter("c2c_peer", rows)); assertEquals(0, f.loads); assertEquals(0, f.queryCount)
    }

    @Test fun `write or deletion revision after IO before Main resumes discards queued publication`() = runBlocking {
        for (change in listOf("credential-write", "visibility-change", "authentication-begin")) {
            val mainExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "PublicationMain") }
            val ioExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "PublicationIO") }
            val main = mainExecutor.asCoroutineDispatcher(); val io = ioExecutor.asCoroutineDispatcher()
            val owner = CoroutineScope(main + SupervisorJob()); val mainBlocked = CountDownLatch(1); val releaseMain = CountDownLatch(1)
            val f = Fixture()
            f.deletedAction = {
                mainExecutor.submit { mainBlocked.countDown(); releaseMain.await() }
                check(mainBlocked.await(2, TimeUnit.SECONDS))
            }
            val task = owner.async { f.filter(io).filter("group_g", listOf(row("M1"))) }
            try {
                assertTrue(mainBlocked.await(2, TimeUnit.SECONDS))
                assertTrue(ioExecutor.submit<Boolean> { true }.get(2, TimeUnit.SECONDS))
                if (change == "credential-write") f.storage.write { } else if (change == "authentication-begin") f.gate.begin() else f.revision++
                releaseMain.countDown(); assertTrue(change, task.await().isEmpty())
            } finally { releaseMain.countDown(); owner.cancel(); main.close(); io.close() }
        }
    }

    @Test fun `IO uses independent SDK status and quote snapshots captured before dispatch`() = runBlocking {
        val f = Fixture(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        f.verifyAction = { entered.complete(Unit); release.await(); throw IOException("synthetic unavailable") }
        val original = row("local", true, MessageStatus.SENDING)
        val task = async { f.filter().filter("group_g", listOf(original)) }
        entered.await(); original.status = MessageStatus.SEND_SUCCESS; release.complete(Unit)
        val displayed = task.await().single()
        assertEquals(MessageStatus.SENDING, displayed.status); assertNotSame(original, displayed)
    }

    @Test fun `successful checked callback invalidation is captured after verification and still publishes`() = runBlocking {
        val f = Fixture()
        val result = f.filter().filter("group_g", listOf(row("fresh")))
        assertEquals("The real checked state reports change and emits revision before display mapping", 1L, f.revision)
        assertEquals(listOf("fresh"), result.map { it.msgID })
        assertEquals(1, f.loads); assertEquals(1, f.deletedReads)
    }

    @Test fun `held authentication action before persistence cannot decrypt or publish the bound batch`() = runBlocking {
        val f = Fixture(); val ticket = f.gate.begin(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val writer = Executors.newSingleThreadExecutor()
        try {
            val transaction = writer.submit { f.gate.commit(ticket) { entered.countDown(); release.await() } }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertTrue(withTimeout(500) { f.filter().filter("group_g", listOf(row("M1"))).isEmpty() })
            assertEquals(0, f.loads); assertTrue(f.decryptThreads.isEmpty()); assertEquals(0, f.queryCount)
            release.countDown(); transaction.get(2, TimeUnit.SECONDS)
        } finally { release.countDown(); writer.shutdownNow() }
        Unit
    }

}
