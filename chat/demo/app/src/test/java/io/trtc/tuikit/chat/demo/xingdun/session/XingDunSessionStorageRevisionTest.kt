package io.trtc.tuikit.chat.demo.xingdun.session

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class XingDunSessionStorageRevisionTest {
    private fun revision() = XingDunSessionStorageRevision.forStore("test-${System.nanoTime()}")

    @Test fun `same persistent namespace shares revision across store instances`() {
        val key = "shared-${System.nanoTime()}"
        val first = XingDunSessionStorageRevision.forStore(key)
        val second = XingDunSessionStorageRevision.forStore(key)
        assertSame(first, second)
        val before = first.current()
        second.write { assertNull(first.current()) }
        assertNotEquals(before, first.current())
        assertNotEquals(first.current(), XingDunSessionStorageRevision.forStore("other-$key").current())
    }

    @Test fun `exception and overlapping writers stay invalid until every writer exits`() {
        val value = revision(); val before = value.current()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor()
        try {
            val task = worker.submit { value.write { entered.countDown(); assertTrue(release.await(2, TimeUnit.SECONDS)) } }
            assertTrue(entered.await(2, TimeUnit.SECONDS)); assertNull(value.current())
            try { value.write { throw IllegalStateException("synthetic write failure") }; fail() } catch (_: IllegalStateException) { }
            assertNull("One writer still in flight", value.current())
            release.countDown(); task.get(2, TimeUnit.SECONDS)
            assertNotNull(value.current()); assertNotEquals(before, value.current())
        } finally { release.countDown(); worker.shutdownNow() }
    }

    @Test fun `revision getter is nonblocking while storage action is blocked`() {
        val value = revision(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val writer = Executors.newSingleThreadExecutor(); val reader = Executors.newSingleThreadExecutor()
        try {
            val task = writer.submit { value.write { entered.countDown(); release.await() } }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertNull(reader.submit<Long?> { value.current() }.get(200, TimeUnit.MILLISECONDS))
            release.countDown(); task.get(2, TimeUnit.SECONDS)
        } finally { release.countDown(); writer.shutdownNow(); reader.shutdownNow() }
    }

    @Test fun `before and after stamp reject writes during actual bound read`() {
        val value = revision()
        fun stamp() = value.current()?.let { XingDunSessionReadStamp(it, 4) }
        assertNull(readConsistentSessionSnapshot(::stamp) { value.write { }; "old bound account" })
        assertEquals("bound account", readConsistentSessionSnapshot(::stamp) { "bound account" }?.second)
    }

    @Test fun `authentication begin before persistence invalidates the captured scope`() {
        val value = revision(); val gate = XingDunAuthenticationCommitGate()
        fun stamp() = value.current()?.let { XingDunSessionReadStamp(it, gate.currentGeneration()) }
        val original = stamp()
        assertNull(readConsistentSessionSnapshot(::stamp) { gate.begin(); "same-account old session" })
        assertNotEquals(original, stamp())
    }

    @Test fun `legacy generation getter retains serialization while new display getter is nonblocking`() {
        val gate = XingDunAuthenticationCommitGate(); val ticket = gate.begin()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val writer = Executors.newSingleThreadExecutor(); val reader = Executors.newSingleThreadExecutor()
        try {
            val task = writer.submit { gate.commit(ticket) { entered.countDown(); release.await() } }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val legacy = reader.submit<Long> { gate.currentGeneration() }
            try { legacy.get(100, TimeUnit.MILLISECONDS); fail("Legacy getter must keep its original writer monitor") }
            catch (_: java.util.concurrent.TimeoutException) { }
            assertNull("New display getter does not wait for that monitor", gate.currentReadGeneration())
            release.countDown(); task.get(2, TimeUnit.SECONDS); assertEquals(ticket, legacy.get(2, TimeUnit.SECONDS))
            gate.invalidateAndRun { }; assertNotEquals(ticket, gate.currentGeneration())
        } finally { release.countDown(); writer.shutdownNow(); reader.shutdownNow() }
    }

    @Test fun `session clear followed by enterprise clear never exposes a bound midpoint`() {
        val value = revision(); var session: String? = "self"; var enterprise: String? = "tenant"
        fun stamp() = value.current()?.let { XingDunSessionReadStamp(it, 1) }
        fun bound() = if (session != null && enterprise != null) "$enterprise:$session" else null
        assertNotNull(readConsistentSessionSnapshot(::stamp, ::bound))
        value.write { session = null }
        assertNull(readConsistentSessionSnapshot(::stamp, ::bound))
        value.write { enterprise = null }
        assertNull(readConsistentSessionSnapshot(::stamp, ::bound))
    }

    @Test fun `gate action before first storage write makes display stamp unavailable without blocking`() {
        val gate = XingDunAuthenticationCommitGate(); val ticket = gate.begin()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val writer = Executors.newSingleThreadExecutor(); val reader = Executors.newSingleThreadExecutor()
        try {
            val task = writer.submit { gate.commit(ticket) { entered.countDown(); release.await() } }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertNull(reader.submit<Long?> { gate.currentReadGeneration() }.get(200, TimeUnit.MILLISECONDS))
            release.countDown(); task.get(2, TimeUnit.SECONDS)
            assertEquals(ticket, gate.currentReadGeneration())
            try { gate.commit(ticket) { throw IllegalStateException("synthetic failed transaction") } } catch (_: IllegalStateException) { }
            assertEquals(ticket, gate.currentReadGeneration())
        } finally { release.countDown(); writer.shutdownNow(); reader.shutdownNow() }
    }

    private fun enterprise(id: Int = 1) = io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration(
        configured = true, imProvider = "tencent", companyCode = "fixture", sdkAppId = 1000,
        company = io.trtc.tuikit.chat.demo.xingdun.network.XingDunCompany(id = id, code = "fixture", name = "Synthetic"),
    )
    private fun session(id: Int = 1, user: String = "synthetic-user") = io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession(
        accessToken = "synthetic-access", tokenType = "Bearer", accessExpiresAtMillis = Long.MAX_VALUE,
        refreshToken = "synthetic-refresh", refreshExpiresAtMillis = Long.MAX_VALUE,
        companyCode = "fixture", companyId = id, companyName = "Synthetic", apiBaseUrl = "https://example.invalid/prod/im/v1",
        sdkAppId = 1000, timUserId = user, userSig = "synthetic-not-valid-signature", userSigExpiresAtMillis = Long.MAX_VALUE,
        username = "synthetic", nickname = "Synthetic",
    )

    @Test fun `display binding stable mismatch fails closed without clearing stored credentials`() {
        val storage = revision(); val before = storage.current(); val retained = session()
        assertNull(XingDunSessionBindingPolicy.resolveForDisplay(retained, enterprise(2)))
        assertEquals(before, storage.current())
        assertEquals("synthetic-user", retained.timUserId)
    }

    @Test fun `mixed snapshot crossing account or tenant write cannot clear the new record`() {
        for (crossTenant in listOf(false, true)) {
            val storage = revision(); var current = session(); var selected = enterprise()
            fun stamp() = storage.current()?.let { XingDunSessionReadStamp(it, 1) }
            val result = readConsistentSessionSnapshot(::stamp) {
                val oldRead = current
                storage.write { current = session(if (crossTenant) 2 else 1, "new-synthetic-user"); selected = enterprise(if (crossTenant) 2 else 1) }
                // The actual display binding policy never invokes the destructive authentication cleanup.
                XingDunSessionBindingPolicy.resolveForDisplay(oldRead, selected)
            }
            assertNull(result)
            assertEquals("new-synthetic-user", current.timUserId)
            assertNotNull(XingDunSessionBindingPolicy.resolveForDisplay(current, selected))
        }
    }

}
