package io.trtc.tuikit.chat.demo.search

import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Test
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageVisibility
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchGroupHistoryAccess
import org.junit.Assert.*

class XingDunHistoryMediaGateTest {
    private val rows = listOf(MessageInfo(msgID = "selected"))

    private class ScopeFixture {
        val storage = io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionStorageRevision.forStore("history-${System.nanoTime()}")
        val generation = io.trtc.tuikit.chat.demo.xingdun.session.XingDunAuthenticationCommitGate()
        var account = "tenant-api-self"
        var self = "self"
        var missing = false
        var displayGenerationOffset = 0L
        var loads = 0
        var grants = 0
        var onLoad: () -> Unit = {}
        var onAuthorize: suspend () -> Boolean = { true }
        fun stamp() = storage.current()?.let { revision -> generation.currentReadGeneration()?.let {
            io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionReadStamp(revision, it)
        } }
        fun read(): io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupDeletionReadScope? {
            loads++; onLoad()
            if (missing) return null
            val current = stamp() ?: return null
            return io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupDeletionReadScope(
                io.trtc.tuikit.chat.demo.xingdun.features.XingDunMessageDisplayScope(account,
                    current.authenticationGeneration + displayGenerationOffset, self), current)
        }
        fun guard(io: CoroutineDispatcher = Dispatchers.IO): XingDunHistoryScopeGuard {
            val initial = requireNotNull(read()); loads = 0
            return XingDunHistoryScopeGuard(initial, ::stamp, ::read, { grants++; onAuthorize() }, io)
                .also { it.activate() }
        }
    }

    @Test fun `actual lifecycle guard rebinds same identity after foreground storage refresh`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard()
        assertEquals(XingDunHistoryScopeGuard.Result.READY, guard.refresh())
        f.storage.write { }
        assertFalse(guard.isCurrent())
        assertEquals(XingDunHistoryScopeGuard.Result.READY, guard.refresh())
        assertEquals(f.stamp(), guard.scope.stamp)
        assertTrue(guard.isCurrent())
    }
    @Test fun `foreground write completing after scope read retries fresh history before publication`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); var write = true
        f.onAuthorize = { if (write) { write = false; f.storage.write { } }; true }
        assertEquals(XingDunHistoryScopeGuard.Result.READY, guard.refresh())
        assertEquals(2, f.loads); assertEquals(2, f.grants)
        assertEquals(f.stamp(), guard.scope.stamp)
    }
    @Test fun `actual lifecycle guard rejects full identity replacement even at same auth generation`() = runBlocking {
        for (change in listOf<(ScopeFixture) -> Unit>({ it.account = "different-tenant-api-self" },
            { it.self = "different-user" }, { it.displayGenerationOffset = 1 })) {
            val f = ScopeFixture(); val guard = f.guard(); f.storage.write { change(f) }
            assertEquals(XingDunHistoryScopeGuard.Result.REPLACED, guard.refresh())
            assertFalse(guard.isCurrent()); assertEquals(0, f.grants)
        }
    }
    @Test fun `authentication in flight never publishes and replacement generation rejects after writer completes`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); val ticket = f.generation.begin()
        val entered = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
        val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val held = writer.submit { f.generation.commit(ticket) { entered.countDown(); release.await() } }
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            val task = async { guard.refresh() }
            delay(80); assertFalse(guard.isCurrent()); assertFalse(task.isCompleted); assertEquals(0, f.loads)
            release.countDown(); held.get(2, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(XingDunHistoryScopeGuard.Result.REPLACED, task.await())
            assertFalse(guard.isCurrent())
        } finally { release.countDown(); writer.shutdownNow() }
    }
    @Test fun `persistent missing bound session rejects instead of waiting or granting history`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); f.missing = true
        assertEquals(XingDunHistoryScopeGuard.Result.REPLACED, guard.refresh())
        assertEquals(1, f.loads); assertEquals(0, f.grants); assertFalse(guard.isCurrent())
    }
    @Test fun `fresh history denial cannot make rebound storage usable`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); f.storage.write { }; f.onAuthorize = { false }
        assertEquals(XingDunHistoryScopeGuard.Result.DENIED, guard.refresh())
        assertFalse(guard.isCurrent())
    }
    @Test fun `paused page discards a noncancellable late grant`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); val entered = CompletableDeferred<Unit>(); val grant = CompletableDeferred<Boolean>()
        f.onAuthorize = { entered.complete(Unit); withContext(NonCancellable) { grant.await() } }
        val task = async { guard.refresh() }; entered.await(); guard.pause(); grant.complete(true)
        assertEquals(XingDunHistoryScopeGuard.Result.STALE, task.await()); assertFalse(guard.isCurrent())
    }
    @Test fun `cancelled lifecycle rebind propagates cancellation without replacing source`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); val entered = CompletableDeferred<Unit>()
        f.onAuthorize = { entered.complete(Unit); awaitCancellation() }
        val original = guard.scope
        val task = async { guard.refresh() }; entered.await(); guard.pause(); task.cancelAndJoin()
        assertTrue(task.isCancelled); assertSame(original, guard.scope); assertFalse(guard.isCurrent())
    }
    @Test fun `late previous request cannot override newer fresh denial`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); val entered = CompletableDeferred<Unit>(); val old = CompletableDeferred<Boolean>()
        val calls = java.util.concurrent.atomic.AtomicInteger()
        f.onAuthorize = { if (calls.incrementAndGet() == 1) { entered.complete(Unit); old.await() } else false }
        val first = async { guard.refresh() }; entered.await()
        assertEquals(XingDunHistoryScopeGuard.Result.DENIED, guard.refresh()); old.complete(true)
        assertEquals(XingDunHistoryScopeGuard.Result.STALE, first.await()); assertFalse(guard.isCurrent())
    }
    @Test fun `caller remains responsive and repeated main guards never reload bound credentials`() = runBlocking {
        val main = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "HistoryMain") }.asCoroutineDispatcher()
        val io = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "HistoryIO") }.asCoroutineDispatcher()
        val owner = CoroutineScope(main + SupervisorJob()); val f = ScopeFixture(); val guard = f.guard(io)
        val entered = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
        val threads = java.util.concurrent.CopyOnWriteArrayList<String>()
        f.onLoad = { threads += Thread.currentThread().name; entered.countDown(); check(release.await(2, java.util.concurrent.TimeUnit.SECONDS)) }
        try {
            val task = owner.async { guard.refresh() }
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(withTimeout(300) { owner.async { repeat(100) { guard.isCurrent() }; true }.await() })
            assertEquals(1, f.loads); assertEquals(1, threads.size)
            assertTrue(threads.single().startsWith("HistoryIO"))
            assertFalse(threads.any { it.startsWith("HistoryMain") })
            release.countDown(); assertEquals(XingDunHistoryScopeGuard.Result.READY, task.await())
        } finally { release.countDown(); owner.cancel(); main.close(); io.close() }
    }
    @Test fun `writer waiting is bounded and never loads or publishes an identity in flight`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); val ticket = f.generation.begin()
        val entered = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
        val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val held = writer.submit { f.generation.commit(ticket) { entered.countDown(); release.await() } }
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(XingDunHistoryScopeGuard.Result.UNAVAILABLE, withTimeout(3_500) { guard.refresh() })
            assertEquals(0, f.loads); assertEquals(0, f.grants); assertFalse(guard.isCurrent())
            release.countDown(); held.get(2, java.util.concurrent.TimeUnit.SECONDS)
        } finally { release.countDown(); writer.shutdownNow() }
        Unit
    }
    @Test fun `repeated storage churn stops after three full reads without granting old rows`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); f.onAuthorize = { f.storage.write { }; true }
        assertEquals(XingDunHistoryScopeGuard.Result.UNAVAILABLE, guard.refresh())
        assertEquals(3, f.loads); assertEquals(3, f.grants); assertFalse(guard.isCurrent())
    }
    @Test fun `write after IO completion before main resumes discards queued publication and can rebind`() = runBlocking {
        val mainExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "HistoryPublishMain") }
        val ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "HistoryPublishIO") }
        val main = mainExecutor.asCoroutineDispatcher(); val io = ioExecutor.asCoroutineDispatcher()
        val owner = CoroutineScope(main + SupervisorJob()); val f = ScopeFixture(); val guard = f.guard(io)
        val blocked = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
        f.onAuthorize = {
            mainExecutor.submit { blocked.countDown(); release.await() }
            check(blocked.await(2, java.util.concurrent.TimeUnit.SECONDS)); true
        }
        try {
            val task = owner.async { guard.refresh() }
            assertTrue(blocked.await(2, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(ioExecutor.submit<Boolean> { true }.get(2, java.util.concurrent.TimeUnit.SECONDS))
            f.storage.write { }; release.countDown()
            assertEquals(XingDunHistoryScopeGuard.Result.RETRY, task.await()); assertFalse(guard.isCurrent())
            f.onAuthorize = { true }
            assertEquals(XingDunHistoryScopeGuard.Result.READY, owner.async { guard.refresh() }.await())
        } finally { release.countDown(); owner.cancel(); main.close(); io.close() }
    }

    @Test fun `actual history cycle waits for initial writer before one fresh authorization and consumption`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1); val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
        val results = mutableListOf<XingDunHistoryScopeGuard.Result>(); val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        f.onLoad = { events += "scope" }; f.onAuthorize = { events += "authorize"; f.stamp() != null }
        try {
            val held = writer.submit { f.storage.write { entered.countDown(); release.await() } }
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            val task = launch { guard.observe({}, { results += it; guard.pause() }) }
            delay(80); assertEquals(emptyList<XingDunHistoryScopeGuard.Result>(), results); assertEquals(0, f.grants); assertEquals(0, f.loads)
            release.countDown(); held.get(2, java.util.concurrent.TimeUnit.SECONDS); withTimeout(2_000) { task.join() }
            assertEquals(listOf(XingDunHistoryScopeGuard.Result.READY), results)
            assertEquals(listOf("scope", "authorize"), events)
        } finally { release.countDown(); writer.shutdownNow() }
        Unit
    }
    @Test fun `actual sole cycle waits for writer on later periodic reauthorization without premature denial`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); val first = CompletableDeferred<Unit>(); val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1); val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
        val results = mutableListOf<XingDunHistoryScopeGuard.Result>(); var invalidations = 0
        f.onAuthorize = { f.stamp() != null }
        val task = launch { guard.observe({ invalidations++ }, { results += it; if (results.size == 1) first.complete(Unit) else guard.pause() }, 30L) }
        try {
            first.await(); val held = writer.submit { f.storage.write { entered.countDown(); release.await() } }
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)); delay(100)
            assertEquals(listOf(XingDunHistoryScopeGuard.Result.READY), results); assertEquals(1, f.grants)
            assertTrue(invalidations > 0)
            release.countDown(); held.get(2, java.util.concurrent.TimeUnit.SECONDS); withTimeout(2_000) { task.join() }
            assertEquals(listOf(XingDunHistoryScopeGuard.Result.READY, XingDunHistoryScopeGuard.Result.READY), results)
            assertEquals(2, f.grants); assertEquals(2, f.loads)
        } finally { release.countDown(); task.cancelAndJoin(); writer.shutdownNow() }
        Unit
    }
    @Test fun `actual history consumer closes on stable fresh denial after scope proof with no duplicate authorization`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        f.onLoad = { events += "scope" }; f.onAuthorize = { events += "authorize"; false }
        guard.observe({}, { events += it.name })
        assertEquals(listOf("scope", "authorize", "DENIED"), events); assertFalse(guard.isCurrent()); assertEquals(1, f.grants)
    }
    @Test fun `actual history cycle invalidates old rows when writer finishes during authorization before ready reload`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); val events = mutableListOf<String>(); var write = true
        f.onAuthorize = { if (write) { write = false; f.storage.write { } }; true }
        guard.observe({ events += "hide" }, { events += it.name; guard.pause() })
        assertEquals("READY", events.last()); assertTrue("Old rows must be hidden before READY", events.dropLast(1).contains("hide")); assertEquals(2, f.grants)
    }
    @Test fun `actual history cycle serializes fast wake requests and periodic checks in one job`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var concurrent = 0; var maxConcurrent = 0; val results = mutableListOf<XingDunHistoryScopeGuard.Result>()
        f.onAuthorize = { concurrent++; maxConcurrent = maxOf(maxConcurrent, concurrent); if (f.grants == 1) { entered.complete(Unit); release.await() }; concurrent--; true }
        val task = launch { guard.observe({}, { results += it; if (results.size == 2) guard.pause() }) }
        entered.await(); repeat(100) { guard.requestRefresh() }; release.complete(Unit)
        withTimeout(2_000) { task.join() }
        assertEquals(2, f.grants); assertEquals(1, maxConcurrent); assertEquals(listOf(XingDunHistoryScopeGuard.Result.READY, XingDunHistoryScopeGuard.Result.READY), results)
    }
    @Test fun `actual history cycle pause and cancellation discard late authorization without later periodic calls`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); val entered = CompletableDeferred<Unit>(); val grant = CompletableDeferred<Boolean>()
        val results = mutableListOf<XingDunHistoryScopeGuard.Result>()
        f.onAuthorize = { entered.complete(Unit); withContext(NonCancellable) { grant.await() } }
        val task = launch { guard.observe({}, { results += it }, 10L) }; entered.await(); guard.pause(); task.cancel(); grant.complete(true); task.join()
        repeat(20) { guard.requestRefresh() }; delay(50)
        assertEquals(emptyList<XingDunHistoryScopeGuard.Result>(), results); assertEquals(1, f.grants); assertFalse(guard.isCurrent())
    }
    @Test fun `actual history cycle retries queued publication race at next period without external wake`() = runBlocking {
        val mainExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "HistoryCycleMain") }
        val ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "HistoryCycleIO") }
        val main = mainExecutor.asCoroutineDispatcher(); val io = ioExecutor.asCoroutineDispatcher()
        val owner = CoroutineScope(main + SupervisorJob()); val f = ScopeFixture(); val guard = f.guard(io)
        val blocked = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(mutableListOf<XingDunHistoryScopeGuard.Result>())
        f.onAuthorize = { mainExecutor.submit { blocked.countDown(); release.await() }; check(blocked.await(2, java.util.concurrent.TimeUnit.SECONDS)); true }
        try {
            val task = owner.launch { guard.observe({}, { results += it; if (it == XingDunHistoryScopeGuard.Result.RETRY) f.onAuthorize = { true } else guard.pause() }, 20L) }
            assertTrue(blocked.await(2, java.util.concurrent.TimeUnit.SECONDS)); assertTrue(ioExecutor.submit<Boolean> { true }.get(2, java.util.concurrent.TimeUnit.SECONDS))
            f.storage.write { }; release.countDown(); withTimeout(2_000) { task.join() }
            assertEquals(listOf(XingDunHistoryScopeGuard.Result.RETRY, XingDunHistoryScopeGuard.Result.READY), results)
            assertEquals(2, f.grants)
        } finally { release.countDown(); owner.cancel(); main.close(); io.close() }
    }
    @Test fun `paused retained C2C media deletion binding uses only same stamp without rendering authority`() {
        val f = ScopeFixture(); val guard = f.guard(); guard.pause()
        assertFalse(guard.isCurrent()); assertTrue(guard.isBoundToCurrentStamp()); assertEquals(0, f.loads); assertEquals(0, f.grants)
        f.storage.write { }; assertFalse(guard.isBoundToCurrentStamp()); assertEquals(0, f.loads)
    }
    @Test fun `initial page bound scope read waits for writer and rejects stable missing session`() = runBlocking {
        val f = ScopeFixture(); val entered = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
        val writer = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val held = writer.submit { f.storage.write { entered.countDown(); release.await() } }; assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            val task = async { XingDunHistoryScopeGuard.readInitialScope(f::stamp, f::read) }
            delay(80); assertFalse(task.isCompleted); assertEquals(0, f.loads)
            release.countDown(); held.get(2, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(f.stamp(), withTimeout(2_000) { task.await() }?.stamp)
            f.missing = true; assertNull(XingDunHistoryScopeGuard.readInitialScope(f::stamp, f::read))
        } finally { release.countDown(); writer.shutdownNow() }
        Unit
    }

    @Test fun `same stamp resume hides previous rows before fresh proof and READY reload consumption`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); assertEquals(XingDunHistoryScopeGuard.Result.READY, guard.refresh())
        val sameStamp = f.stamp(); guard.pause(); guard.activate()
        assertEquals(sameStamp, f.stamp()); assertFalse("Resume cannot make old rows immediately actionable", guard.isCurrent())
        var oldRowsVisible = true; val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        f.onAuthorize = { assertFalse("Old deleted rows must be hidden before fresh permission", oldRowsVisible); events += "fresh"; true }
        guard.observe({ oldRowsVisible = false; events += "hide" }, { events += it.name; guard.pause() })
        assertEquals(listOf("hide", "fresh", "READY"), events)
    }
    @Test fun `actual query acceptance rejects old C2C and group tickets after resume and query replacement`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); assertEquals(XingDunHistoryScopeGuard.Result.READY, guard.refresh())
        assertTrue(guard.acceptsRead(10L, 10L)); assertFalse("Superseded in-page result cannot replace current rows", guard.acceptsRead(9L, 10L))
        guard.pause(); guard.activate(); assertFalse(guard.acceptsRead(10L, 11L))
        assertEquals(XingDunHistoryScopeGuard.Result.READY, guard.refresh())
        assertFalse("Pre-pause pending result must not revive a deleted row after READY", guard.acceptsRead(10L, 11L))
        assertTrue(guard.acceptsRead(11L, 11L))
    }
    @Test fun `late old history consumer cannot publish into reactivated same identity page`() = runBlocking {
        val f = ScopeFixture(); val guard = f.guard(); val entered = CompletableDeferred<Unit>(); val oldGrant = CompletableDeferred<Boolean>()
        val oldResults = mutableListOf<XingDunHistoryScopeGuard.Result>(); val newResults = mutableListOf<XingDunHistoryScopeGuard.Result>()
        f.onAuthorize = { entered.complete(Unit); withContext(NonCancellable) { oldGrant.await() } }
        val old = launch { guard.observe({}, { oldResults += it }) }; entered.await(); guard.pause(); guard.activate(); f.onAuthorize = { true }
        guard.observe({}, { newResults += it; guard.pause() }); oldGrant.complete(true); withTimeout(2_000) { old.join() }
        assertEquals(emptyList<XingDunHistoryScopeGuard.Result>(), oldResults); assertEquals(listOf(XingDunHistoryScopeGuard.Result.READY), newResults)
    }

    @After fun restoreSourceProviders() {
        SearchMessageVisibility.setDisplayProvider(null)
        SearchMessageVisibility.setSessionProvider { null }
        SearchMessageVisibility.setProvider { _, value -> value }
        SearchMessageVisibility.setDeletedProvider { _, _ -> false }
        SearchMessageVisibility.setVerificationFailureProvider { false }
        SearchGroupHistoryAccess.setProvider(null)
    }
    private fun sourceSetup() {
        SearchMessageVisibility.setSessionProvider { "source-owner" }
        SearchMessageVisibility.setProvider { _, value -> value }
        SearchMessageVisibility.setDeletedProvider { _, _ -> false }
        SearchMessageVisibility.setVerificationFailureProvider { false }
        SearchGroupHistoryAccess.setProvider { true }
    }
    @Test fun `category fast owner callback never reads credential provider and rejects replaced source`() {
        var current = true
        var fastReads = 0
        SearchMessageVisibility.setSessionProvider { error("UI owner check must not load encrypted credentials") }
        val policy = XingDunHistoryMediaGate.sourcePolicy("group_target", "source-owner", rows, setOf("selected"),
            sourceIsCurrent = { fastReads++; current })
        repeat(100) { assertTrue(policy.ownerIsCurrent()) }
        current = false
        assertFalse(policy.ownerIsCurrent())
        assertEquals(101, fastReads)
        val missing = XingDunHistoryMediaGate.sourcePolicy("group_target", null, rows, setOf("selected"),
            sourceIsCurrent = { true })
        assertFalse(missing.ownerIsCurrent())
    }
    @Test fun `fast viewer owner callback cannot replace fresh session and history authorization`() = runBlocking {
        sourceSetup()
        val policy = XingDunHistoryMediaGate.sourcePolicy("group_target", "source-owner", rows, setOf("selected"),
            sourceIsCurrent = { true })
        SearchMessageVisibility.setSessionProvider { "replacement-owner" }
        assertTrue(policy.ownerIsCurrent())
        assertFalse(policy.revalidate())
        SearchMessageVisibility.setSessionProvider { "source-owner" }
        SearchGroupHistoryAccess.setProvider { false }
        assertFalse(policy.revalidate())
        SearchGroupHistoryAccess.setProvider { true }
        assertTrue(policy.revalidate())
    }
    @Test fun `actual detached viewer source checks batch deletion then fresh history without parent active state`() = runBlocking {
        sourceSetup(); val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        SearchMessageVisibility.setDisplayProvider { _, value, mapper -> events += "snapshot"; mapper(value) }
        SearchMessageVisibility.setProvider { _, value -> events += "delete"; value }
        SearchGroupHistoryAccess.setProvider { events += "history"; true }
        val policy = XingDunHistoryMediaGate.sourcePolicy("group_target", "source-owner", rows, setOf("selected"))
        assertTrue(policy.ownerIsCurrent())
        assertTrue(policy.revalidate())
        assertEquals(listOf("snapshot", "delete", "history"), events)
    }
    @Test fun `actual detached viewer known deleted or fresh denied source cannot survive revalidation`() = runBlocking {
        sourceSetup(); var historyCalls = 0
        SearchGroupHistoryAccess.setProvider { historyCalls++; false }
        val policy = XingDunHistoryMediaGate.sourcePolicy("group_target", "source-owner", rows, setOf("selected"))
        SearchMessageVisibility.setDeletedProvider { _, _ -> true }
        assertFalse(policy.revalidate()); assertEquals(0, historyCalls)
        SearchMessageVisibility.setDeletedProvider { _, _ -> false }
        assertFalse(policy.revalidate()); assertEquals(1, historyCalls)
        SearchGroupHistoryAccess.setProvider { true }
        SearchMessageVisibility.setProvider { _, _ -> emptyList() }
        assertFalse(policy.revalidate())
    }
    @Test fun `actual detached viewer owner replacement stops before source reads and late deletion cannot become a grant`() = runBlocking {
        sourceSetup(); var owner = "source-owner"; var reads = 0
        SearchMessageVisibility.setSessionProvider { owner }
        SearchMessageVisibility.setProvider { _, value -> reads++; owner = "new-generation"; value }
        val policy = XingDunHistoryMediaGate.sourcePolicy("group_target", "source-owner", rows, setOf("selected"))
        try { policy.revalidate(); fail("Changed source must be unavailable") } catch (_: IllegalStateException) { }
        assertFalse(policy.ownerIsCurrent())
        assertFalse(policy.revalidate())
        assertEquals(1, reads)
    }
    @Test fun `actual detached viewer fresh deletion failure and missing owner never open retained source`() = runBlocking {
        sourceSetup(); SearchMessageVisibility.setVerificationFailureProvider { true }
        val policy = XingDunHistoryMediaGate.sourcePolicy("group_target", "source-owner", rows, setOf("selected"))
        try { policy.revalidate(); fail("Failed deletion verification must not grant") } catch (_: IllegalStateException) { }
        val missing = XingDunHistoryMediaGate.sourcePolicy("group_target", null, rows, setOf("selected"))
        assertFalse(missing.ownerIsCurrent()); assertFalse(missing.revalidate())
    }

    @Test fun `group media requires fresh history after deletion and calls current rejection on deny`() = runBlocking {
        val events = mutableListOf<String>()
        var rejected = 0
        val value = XingDunHistoryMediaGate.checked("group_target", "selected", { true },
            { events += "delete"; rows }, { events += "history"; false }, { rejected++ })
        assertNull(value)
        assertEquals(listOf("delete", "history"), events)
        assertEquals(1, rejected)
    }
    @Test fun `unknown group authority rejects rather than opening the deletion checked snapshot`() = runBlocking {
        var rejected = 0
        val value = XingDunHistoryMediaGate.checked("group_target", "selected", { true },
            { rows }, { error("synthetic unknown") }, { rejected++ })
        assertNull(value)
        assertEquals(1, rejected)
    }
    @Test fun `allowed group source preserves selected snapshot after both checks`() = runBlocking {
        val events = mutableListOf<String>()
        val value = XingDunHistoryMediaGate.checked("group_target", "selected", { true },
            { events += "delete"; rows }, { events += "history"; true }, { fail("No deny on allow") })
        assertEquals(rows, value)
        assertEquals(listOf("delete", "history"), events)
    }
    @Test fun `direct media keeps deletion check without introducing group policy`() = runBlocking {
        var deleted = false
        val value = XingDunHistoryMediaGate.checked("c2c_friend", "selected", { true },
            { deleted = true; rows }, { fail("No group ACL for direct media"); false }, { fail("No group rejection") })
        assertTrue(deleted)
        assertEquals(rows, value)
    }
    @Test fun `deleted selected source never opens media or requests another grant`() = runBlocking {
        val value = XingDunHistoryMediaGate.checked("group_target", "selected", { true },
            { emptyList() }, { fail("Deleted selection is not actionable"); true }, { fail("No current policy denial") })
        assertNull(value)
    }
    @Test fun `late allow cannot open or reject after page media account or lifecycle replacement`() = runBlocking {
        for (replacement in listOf("new-page", "new-media", "new-account", "paused")) {
            var current = "original"
            val allow = CompletableDeferred<Boolean>()
            var rejected = 0
            val request = async(start = CoroutineStart.UNDISPATCHED) {
                XingDunHistoryMediaGate.checked("group_target", "selected", { current == "original" },
                    { rows }, { withContext(NonCancellable) { allow.await() } }, { rejected++ })
            }
            assertFalse("Fresh history must finish before media can open", request.isCompleted)
            current = replacement
            allow.complete(true)
            assertNull(request.await())
            assertEquals(0, rejected)
        }
    }
    @Test fun `already replaced source never reads or opens anything`() = runBlocking {
        val value = XingDunHistoryMediaGate.checked("group_target", "selected", { false },
            { fail("No stale source read"); rows }, { fail("No stale policy call"); true }, { fail("No new-page denial") })
        assertNull(value)
    }
    @Test fun `source change during deletion stops before history and never rejects a replacement`() = runBlocking {
        var current = true
        val deletion = CompletableDeferred<List<MessageInfo>>()
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            XingDunHistoryMediaGate.checked("group_target", "selected", { current },
                { withContext(NonCancellable) { deletion.await() } },
                { fail("No stale authorization"); true }, { fail("No new-page denial") })
        }
        current = false
        deletion.complete(rows)
        assertNull(request.await())
    }

    @Test fun `unavailable group deletion authority rejects the current source without opening cached media`() = runBlocking {
        var rejected = 0
        val value = XingDunHistoryMediaGate.checked("group_target", "selected", { true },
            { error("synthetic deletion verification unavailable") }, { fail("No grant after failed source read"); true }, { rejected++ })
        assertNull(value)
        assertEquals(1, rejected)
    }

    @Test fun `authority cancellation propagates without denying a replacement page`() = runBlocking {
        var rejected = 0
        try {
            XingDunHistoryMediaGate.checked("group_target", "selected", { true },
                { rows }, { throw CancellationException("synthetic cancellation") }, { rejected++ })
            fail("Cancellation must not return an openable snapshot")
        } catch (_: CancellationException) { }
        assertEquals(0, rejected)
    }
}
