package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonParser
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class XingDunGroupDeletionTest {
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject

    @Test fun `group scope and message limits do not accept C2C or whitespace aliases`() {
        assertEquals("@TGS#abc", XingDunGroupDeletionPolicy.group("group_@TGS#abc"))
        listOf("c2c_bob", "group_", "group_ abc", "group_a/b").forEach { assertNull(XingDunGroupDeletionPolicy.group(it)) }
        assertTrue(XingDunGroupDeletionPolicy.validMessage("M".repeat(64)))
        assertFalse(XingDunGroupDeletionPolicy.validMessage("M".repeat(65)))
    }

    @Test fun `active menu requires server capability and another sender successful real group message`() {
        fun allowed(conv: String = "group_g", sender: String = "member", self: Boolean = false,
                    sent: Boolean = true, control: Boolean = false, capability: Boolean = true) =
            XingDunGroupDeletionPolicy.canOffer(conv, "operator", "M1", sender, self, sent, control, capability)
        assertTrue(allowed())
        assertFalse(allowed(capability = false)) // Neither an owner nor a client CS label substitutes for a server capability.
        assertFalse(allowed(conv = "c2c_member"))
        assertFalse(allowed(sender = "operator"))
        assertFalse(allowed(self = true)); assertFalse(allowed(sent = false)); assertFalse(allowed(control = true))
    }

    @Test fun `strict permission response accepts only queried senders in exact group`() {
        assertEquals(setOf("member"), XingDunGroupDeletionPolicy.permissions("g", setOf("member", "owner"), json(
            """{"group_id":"g","allowed_sender_ids":["member"]}""")))
        for (body in listOf(
            """{"group_id":"other","allowed_sender_ids":["member"]}""",
            """{"group_id":"g","allowed_sender_ids":["foreign"]}""",
            """{"group_id":"g","allowed_sender_ids":["member","member"]}""",
            """{"group_id":"g","allowed_sender_ids":[5]}""",
            """{"group_id":"g","allowed_sender_ids":[],"owner":true}""",
        )) assertThrows(IllegalArgumentException::class.java) { XingDunGroupDeletionPolicy.permissions("g", setOf("member"), json(body)) }
    }

    @Test fun `authoritative tombstones and mutation echoes are validated before persistence`() {
        assertEquals(setOf("M1"), XingDunGroupDeletionPolicy.deleted("g", setOf("M1"), json("""{"group_id":"g","message_ids":["M1"]}""")))
        for (body in listOf(
            """{"group_id":"other","message_ids":["M1"]}""",
            """{"group_id":"g","message_ids":["M2"]}""",
            """{"group_id":"g","message_ids":["M1","M1"]}""",
        )) assertThrows(IllegalArgumentException::class.java) { XingDunGroupDeletionPolicy.deleted("g", setOf("M1"), json(body)) }
        XingDunGroupDeletionPolicy.validateDeleted("M1", json("""{"message_id":"M1","already_deleted":false}"""))
        assertThrows(IllegalArgumentException::class.java) { XingDunGroupDeletionPolicy.validateDeleted("M1", json("""{"message_id":"M2","already_deleted":true}""")) }
        assertThrows(IllegalArgumentException::class.java) { XingDunGroupDeletionPolicy.validateDeleted("M1", json("""{"message_id":"M1","already_deleted":"false"}""")) }
    }

    @Test fun `hint IDs require exact group and bounded array and cannot grant deletion`() {
        fun hint(conv: String = "group_g", raw: String = "[\"M1\"]", version: String = "1") =
            """{"xd_type":"remote_delete","xd_version":$version,"payload":{"conversation_id":"$conv","message_ids":$raw}}"""
        assertEquals(setOf("M1"), XingDunGroupDeletionPolicy.hintIDs("group_g", hint()))
        assertTrue(XingDunGroupDeletionPolicy.hintIDs("group_g", hint("group_other")).isEmpty())
        assertTrue(XingDunGroupDeletionPolicy.hintIDs("c2c_g", hint()).isEmpty())
        listOf("2", "null", "\"1\"", "true").forEach { assertTrue(XingDunGroupDeletionPolicy.hintIDs("group_g", hint(version = it)).isEmpty()) }
        assertTrue(XingDunGroupDeletionPolicy.hintIDs("group_g", """{"xd_type":"remote_delete","payload":{"conversation_id":"group_g","message_ids":["M1"]}}""").isEmpty())
        listOf("[]", "[1]", "[\"bad id\"]", "not json", "[" + (1..51).joinToString { "\"M$it\"" } + "]")
            .forEach { assertTrue(XingDunGroupDeletionPolicy.hintIDs("group_g", hint(raw = it)).isEmpty()) }
    }

    private class Fixture {
        var account = "tenant-sdk-alice"
        var generation = 1
        var permission: Set<String> = setOf("member")
        var disk = mutableMapOf<String, Set<String>>()
        var mutations = 0
        var deleted = false
        var loseResponse = false
        var offline = false
        var onQuery: () -> Unit = {}
        fun requests() = XingDunGroupDeletionRequests(
            { account }, { generation },
            { _, ids -> onQuery(); if (offline) throw IOException("offline"); if (deleted) ids else emptySet() },
            { _, senders -> permission.intersect(senders) },
            { _, _ -> mutations++; deleted = true; if (loseResponse) throw IOException("lost") },
            { key, group, ids -> disk["$key:$group"] = disk["$key:$group"].orEmpty() + ids },
            { expected, action -> check(expected == generation); action() },
        )
    }

    @Test fun `ordinary denied or revoked capability never mutates or persists`() = runBlocking {
        val f = Fixture(); f.permission = emptySet()
        try { f.requests().delete("g", "M1", "member"); fail("Must deny") } catch (_: IllegalStateException) { }
        assertEquals(0, f.mutations); assertTrue(f.disk.isEmpty())
    }

    @Test fun `offline query cannot optimistically hide or attempt a write`() = runBlocking {
        val f = Fixture(); f.offline = true
        try { f.requests().delete("g", "M1", "member"); fail("Must fail") } catch (_: IOException) { }
        assertEquals(0, f.mutations); assertTrue(f.disk.isEmpty())
    }

    @Test fun `successful idempotent deletion and lost reply persist exactly the requested group ID`() = runBlocking {
        val f = Fixture(); f.loseResponse = true
        f.requests().delete("g", "M1", "member")
        f.requests().delete("g", "M1", "member")
        assertEquals(1, f.mutations)
        assertEquals(setOf("M1"), f.disk["tenant-sdk-alice:g"])
        assertNull(f.disk["tenant-sdk-alice:another-group"])
        f.account = "another-tenant-alice"; assertNull(f.disk["${f.account}:g"])
    }

    @Test fun `account switch and same account relogin discard late authority answers`() = runBlocking {
        for (relogin in listOf(false, true)) {
            val f = Fixture(); f.deleted = true
            f.onQuery = { if (relogin) f.generation++ else f.account = "other-account" }
            try { f.requests().check("g", setOf("M1")); fail("Late result must fail") } catch (_: IllegalStateException) { }
            assertTrue(f.disk.isEmpty()); assertEquals(0, f.mutations)
        }
    }

    @Test fun `duplicate taps share one mutation while first response is pending`() = runBlocking {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var writes = 0
        val requests = XingDunGroupDeletionRequests({ "account" }, { 1 }, { _, _ -> emptySet() }, { _, ids -> ids },
            { _, _ -> writes++; started.complete(Unit); release.await() }, { _, _, _ -> }, { _, action -> action() })
        val first = async(start = CoroutineStart.UNDISPATCHED) { requests.delete("g", "M1", "member") }
        started.await()
        try { requests.delete("g", "M1", "member"); fail("Duplicate must be rejected") } catch (_: IllegalStateException) { }
        release.complete(Unit); first.await(); assertEquals(1, writes)
    }

    @Test fun `query and capability batches reject malformed overlarge inputs before transport`() = runBlocking {
        val f = Fixture()
        for (ids in listOf(emptySet(), setOf("bad id"), (1..101).map { "M$it" }.toSet())) {
            try { f.requests().check("g", ids); fail("Invalid batch") } catch (_: IllegalArgumentException) { }
            try { f.requests().allowed("g", ids); fail("Invalid capability batch") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, f.mutations)
    }

    @Test fun `unknown new page holds while checked safe rows survive temporary network failure`() {
        val state = XingDunGroupDeletionVisibilityState()
        assertFalse(state.canDisplay("account:g", "new", false, false, false))
        state.checked("account:g", setOf("retained"))
        assertTrue(state.canDisplay("account:g", "retained", false, false, false))
        assertFalse(state.canDisplay("account:other", "retained", false, false, false))
        assertFalse(state.canDisplay("relogin:g", "retained", false, false, false))
        assertFalse(XingDunGroupDeletionVisibilityState().canDisplay("account:g", "retained", false, false, false))
    }

    @Test fun `hint targets wait for successful post window authority and later hints never release earlier targets`() {
        var now = 0L
        val state = XingDunGroupDeletionVisibilityState { now }
        state.checked("a:g", setOf("one", "two", "retained")); state.hold("a:g", setOf("one"))
        now = 5_000; state.hold("a:g", setOf("two"))
        assertTrue(state.canDisplay("a:g", "retained", false, false, false))
        assertFalse(state.canDisplay("a:g", "one", false, false, false))
        now = 11_001
        assertFalse(state.canDisplay("a:g", "one", false, false, false))
        state.checked("a:g", setOf("one"), requestStartedAt = 10_999)
        assertFalse(state.canDisplay("a:g", "one", false, false, false))
        state.checked("a:g", setOf("one"))
        assertTrue(state.canDisplay("a:g", "one", false, false, false))
        assertFalse(state.canDisplay("a:g", "two", false, false, false))
        now = 16_001
        assertFalse(state.canDisplay("a:g", "two", false, false, false))
        state.checked("a:g", setOf("two"))
        assertTrue(state.canDisplay("a:g", "two", false, false, false))
        assertFalse(state.canDisplay("a:g", "one", true, false, false))
    }

    @Test fun `real local Sending stays immediate on Success but imported history and known tombstone never get exemption`() {
        val state = XingDunGroupDeletionVisibilityState()
        assertTrue(state.canDisplay("a:g", "local", false, true, false))
        assertTrue(state.canDisplay("a:g", "local", false, false, false))
        assertFalse(state.canDisplay("a:g", "imported", false, false, false))
        assertFalse(state.canDisplay("a:g", "local", true, true, false))
        state.clear(); assertFalse(state.canDisplay("a:g", "local", false, false, false))
    }

    @Test fun `preview guard captures scope before SDK callback and rejects account relogin or deletion invalidation at publish`() {
        var account: String? = "alice"
        var generation: Long? = 1
        var revision = 1L
        fun guard() = XingDunDeletionPublicationGuard({ account }, { generation }, { revision })
        val atSDK = guard(); assertTrue(atSDK.isCurrent())
        account = "bob"; assertFalse(atSDK.isCurrent())
        account = "alice"; generation = 2; assertFalse(atSDK.isCurrent())
        val atFilter = guard(); assertTrue(atFilter.isCurrent())
        revision++; assertFalse(atFilter.isCurrent())
        val atMainPost = guard(); generation = null; assertFalse(atMainPost.isCurrent())
        generation = 3; assertFalse(atMainPost.isCurrent())
        assertTrue(guard().isCurrent())
    }

    @Test fun `reconnect scan is queued in background and resumed only by foreground owner`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val gate = XingDunForegroundDeletionScan(scope)
            var reads = 0
            gate.request { reads++ }
            assertEquals(0, reads)
            gate.setForeground(true)
            assertEquals(1, reads)
            gate.setForeground(false)
            gate.request { reads++ }
            assertEquals(1, reads)
            gate.setForeground(true)
            assertEquals(2, reads)
        } finally { scope.cancel() }
    }

    @Test fun `background cancels current traversal and canceled completion cannot clear replacement scan`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val gate = XingDunForegroundDeletionScan(scope)
            val firstPage = CompletableDeferred<Unit>()
            val replacementPage = CompletableDeferred<Unit>()
            val reads = mutableListOf<String>()
            gate.setForeground(true)
            gate.request { reads += "old-first"; firstPage.await(); reads += "old-second" }
            assertEquals(listOf("old-first"), reads)
            gate.setForeground(false)
            gate.request { reads += "replacement-first"; replacementPage.await(); reads += "replacement-second" }
            firstPage.complete(Unit)
            yield()
            assertEquals(listOf("old-first"), reads)
            gate.setForeground(true)
            assertEquals(listOf("old-first", "replacement-first"), reads)
            // A second queued recovery waits for the current owner, rather than starting a parallel scan.
            gate.request { reads += "queued" }
            assertEquals(listOf("old-first", "replacement-first"), reads)
            replacementPage.complete(Unit)
            yield()
            assertEquals(listOf("old-first", "replacement-first", "replacement-second", "queued"), reads)
        } finally { scope.cancel() }
    }
}
