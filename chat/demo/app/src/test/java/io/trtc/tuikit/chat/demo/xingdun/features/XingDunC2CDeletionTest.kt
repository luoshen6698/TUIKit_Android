package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunAuthenticationCommitGate

class XingDunC2CDeletionTest {
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject

    @Test fun `only own stable successful C2C messages offer delete for both`() {
        fun allowed(conv: String = "c2c_bob", id: String = "M1", self: Boolean = true,
                    sent: Boolean = true, control: Boolean = false) =
            XingDunC2CDeletionPolicy.canDelete(conv, "alice", id, self, sent, control)
        assertTrue(allowed())
        assertFalse(allowed("group_bob"))
        assertFalse(allowed("c2c_alice"))
        assertFalse(allowed(id = ""))
        assertFalse(allowed(id = "x".repeat(129)))
        assertFalse(allowed(self = false))
        assertFalse(allowed(sent = false))
        assertFalse(allowed(control = true))
        assertFalse(XingDunC2CDeletionPolicy.canDelete("c2c_bob", "alice", "RP1", true, true, false, "redpacket"))
    }

    @Test fun `authoritative query accepts only matching peer and requested subset`() {
        assertEquals(setOf("M1"), XingDunC2CDeletionPolicy.deletedIDs("bob", setOf("M1", "M2"),
            json("""{"peer_user_id":"bob","message_ids":["M1"]}""")))
        for (body in listOf(
            """{"peer_user_id":"mallory","message_ids":["M1"]}""",
            """{"peer_user_id":"bob","message_ids":["M3"]}""",
            """{"peer_user_id":"bob","message_ids":["M1","M1"]}""",
            """{"peer_user_id":"bob","message_ids":[3]}""",
            """{"peer_user_id":"bob","message_ids":["M1"],"extra":true}""",
        )) assertThrows(IllegalArgumentException::class.java) {
            XingDunC2CDeletionPolicy.deletedIDs("bob", setOf("M1"), json(body))
        }
    }

    @Test fun `mutation echo and boolean must match exactly`() {
        XingDunC2CDeletionPolicy.validateDeleted("bob", "M1", json(
            """{"peer_user_id":"bob","message_id":"M1","already_deleted":false}"""))
        for (body in listOf(
            """{"peer_user_id":"bob","message_id":"M2","already_deleted":false}""",
            """{"peer_user_id":"bob","message_id":"M1","already_deleted":"false"}""",
            """{"peer_user_id":"bob","message_id":"M1"}""",
        )) assertThrows(IllegalArgumentException::class.java) {
            XingDunC2CDeletionPolicy.validateDeleted("bob", "M1", json(body))
        }
    }

    @Test fun `forged control hint never directly deletes a target`() = runBlocking {
        val remembered = mutableSetOf<String>()
        val requests = XingDunC2CDeletionRequests({ "account" }, { peer, ids ->
            assertEquals("bob", peer); assertEquals(setOf("forged-M1"), ids); emptySet()
        }, { _, _ -> fail("A receive hint must never mutate the server") }, { _, _, ids -> remembered += ids })
        assertTrue(requests.check("bob", setOf("forged-M1")).isEmpty())
        assertTrue(remembered.isEmpty())
    }

    @Test fun `lost response is reconciled using the same ID and never retriggers mutation`() = runBlocking {
        var removed = false
        var mutations = 0
        val remembered = mutableSetOf<String>()
        val requests = XingDunC2CDeletionRequests({ "account" }, { _, _ -> if (removed) setOf("M1") else emptySet() },
            { peer, id -> assertEquals("bob", peer); assertEquals("M1", id); removed = true; mutations++; throw IOException("lost response") },
            { _, _, ids -> remembered += ids })
        requests.delete("bob", "M1")
        requests.delete("bob", "M1")
        assertEquals(1, mutations)
        assertEquals(setOf("M1"), remembered)
    }

    @Test fun `unconfirmed mutation fails without optimistic deletion and explicit retry can recover`() = runBlocking {
        var mutations = 0
        var removed = false
        val remembered = mutableSetOf<String>()
        val requests = XingDunC2CDeletionRequests({ "account" }, { _, _ -> if (removed) setOf("M1") else emptySet() },
            { _, _ -> mutations++; if (mutations == 1) throw IOException("offline") else removed = true },
            { _, _, ids -> remembered += ids })
        try { requests.delete("bob", "M1"); fail("Offline delete must not succeed") } catch (_: IOException) { }
        assertTrue(remembered.isEmpty())
        requests.delete("bob", "M1")
        assertEquals(2, mutations)
        assertEquals(setOf("M1"), remembered)
    }

    @Test fun `lookup unavailable refuses mutation and keeps local history intact`() = runBlocking {
        val requests = XingDunC2CDeletionRequests({ "account" }, { _, _ -> throw IOException("not deployed") },
            { _, _ -> fail("No write without authoritative lookup") }, { _, _, _ -> fail("No optimistic hide") })
        try { requests.delete("bob", "M1"); fail("Should fail closed") } catch (_: IOException) { }
    }

    @Test fun `account switch rejects in flight result and never persists into the new account`() = runBlocking {
        var account = "alice-account"
        val requests = XingDunC2CDeletionRequests({ account }, { _, _ -> account = "carol-account"; setOf("M1") },
            { _, _ -> fail("Account switched") }, { _, _, _ -> fail("Must not persist cross-account data") })
        try { requests.check("bob", setOf("M1")); fail("Should reject stale response") } catch (_: IllegalStateException) { }
    }

    @Test fun `simultaneous clicks issue one mutation`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var mutations = 0
        val requests = XingDunC2CDeletionRequests({ "account" }, { _, _ -> emptySet() },
            { _, _ -> mutations++; started.complete(Unit); release.await() }, { _, _, _ -> })
        val first = async(start = CoroutineStart.UNDISPATCHED) { requests.delete("bob", "M1") }
        started.await()
        try { requests.delete("bob", "M1"); fail("Duplicate should be locked") } catch (_: IllegalStateException) { }
        release.complete(Unit); first.await()
        assertEquals(1, mutations)
    }

    @Test fun `persisted authoritative records survive coordinator restart and isolate accounts`() = runBlocking {
        val disk = mutableMapOf<String, Set<String>>()
        var account = "tenant-sdk-alice"
        var mutations = 0
        fun coordinator() = XingDunC2CDeletionRequests({ account }, { _, _ -> setOf("M1") },
            { _, _ -> mutations++ }, { key, peer, ids -> disk["$key:$peer"] = disk["$key:$peer"].orEmpty() + ids })
        coordinator().check("bob", setOf("M1"))
        assertEquals(setOf("M1"), disk["tenant-sdk-alice:bob"])
        coordinator().delete("bob", "M1")
        assertEquals(0, mutations)
        account = "another-tenant-sdk-alice"
        assertNull(disk["$account:bob"])
    }

    @Test fun `batch bounds reject malformed candidate IDs before request`() = runBlocking {
        val requests = XingDunC2CDeletionRequests({ "account" }, { _, _ -> fail("Not called"); emptySet() },
            { _, _ -> fail("Not called") }, { _, _, _ -> fail("Not called") })
        for (ids in listOf(emptySet(), (1..101).map { "M$it" }.toSet(), setOf("bad id"))) {
            try { requests.check("bob", ids); fail("Invalid batch") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun `same account relogin rejects a stale response while preserving stable marker namespace`() = runBlocking {
        val gate = XingDunAuthenticationCommitGate()
        gate.begin()
        var writes = 0
        val requests = XingDunC2CDeletionRequests({ "stable-account" }, { _, _ ->
            gate.invalidateAndRun { }; gate.begin(); setOf("M1")
        }, { _, _ -> fail("Stale request must not mutate") }, { _, _, _ -> writes++ },
            session = gate::currentGeneration, commit = { expected, action -> gate.commit(expected as Long, action) })
        try { requests.check("bob", setOf("M1")); fail("Old login instance must be rejected") } catch (_: IllegalStateException) { }
        assertEquals(0, writes)
    }

    @Test fun `logout between response and persistence is rejected by the shared authentication commit gate`() = runBlocking {
        val gate = XingDunAuthenticationCommitGate()
        gate.begin()
        var writes = 0
        val requests = XingDunC2CDeletionRequests({ "stable-account" }, { _, _ -> setOf("M1") },
            { _, _ -> fail("No mutation needed") }, { _, _, _ -> writes++ }, session = gate::currentGeneration,
            commit = { expected, action -> gate.invalidateAndRun { }; gate.commit(expected as Long, action) })
        try { requests.check("bob", setOf("M1")); fail("Logout must invalidate commit") }
        catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals(0, writes)
    }

    @Test fun `same account relogin while deleting never rebinds the query or persistence to the new login`() = runBlocking {
        val gate = XingDunAuthenticationCommitGate()
        gate.begin()
        var writes = 0
        var mutations = 0
        val requests = XingDunC2CDeletionRequests({ "stable-account" }, { _, _ ->
            gate.invalidateAndRun { }; gate.begin(); setOf("M1")
        }, { _, _ -> mutations++ }, { _, _, _ -> writes++ }, session = gate::currentGeneration,
            commit = { expected, action -> gate.commit(expected as Long, action) })
        try { requests.delete("bob", "M1"); fail("Old login instance must not succeed") }
        catch (_: IllegalStateException) { }
        assertEquals(0, mutations)
        assertEquals(0, writes)
    }
}
