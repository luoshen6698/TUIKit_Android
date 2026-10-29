package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonObject
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunContactDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import io.trtc.tuikit.chat.demo.xingdun.network.executeCancellable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class XingDunGroupSourceProfileLoaderTest {
    @Test fun `self target loads own profile without requiring a friendship or discovery`() = runBlocking {
        val f = Fixture(target = "self")
        f.profileError = IllegalStateException("Friend detail requires a friendship")
        f.relationshipError = IllegalStateException("Self is not a friend discovery")
        assertTrue(f.loader.refresh())
        val loaded = f.states.last() as XingDunGroupSourceProfileState.Loaded
        assertEquals("self", loaded.profile.timUserId)
        assertEquals("Own profile", loaded.profile.nickname)
        assertTrue(loaded.relationship!!.get("is_self").asBoolean)
        assertEquals("self", loaded.relationship.get("relationship_status").asString)
        assertEquals(1, f.selfProfileReads)
        assertEquals(0, f.profileReads)
        assertEquals(0, f.relationshipReads)
    }

    @Test fun `own profile must return the exact authenticated owner`() = runBlocking {
        for (returnedID in listOf("", "other-user")) {
            val f = Fixture(target = "self")
            f.selfProfile = f.selfProfile.copy(timUserId = returnedID)
            assertFalse(f.loader.refresh())
            assertSame(XingDunGroupSourceProfileState.Unavailable, f.states.last())
            assertEquals(0, f.profileReads)
            assertEquals(0, f.relationshipReads)
        }
    }

    @Test fun `own profile failure conceals fields and retries without public detail fallback`() = runBlocking {
        val f = Fixture(target = "self")
        f.selfProfileError = IllegalStateException("Synthetic own profile outage")
        assertFalse(f.loader.refresh())
        assertSame(XingDunGroupSourceProfileState.Unavailable, f.states.last())
        f.selfProfileError = null
        assertTrue(f.loader.refresh())
        assertTrue(f.states.last() is XingDunGroupSourceProfileState.Loaded)
        assertEquals(2, f.selfProfileReads)
        assertEquals(0, f.profileReads)
        assertEquals(0, f.relationshipReads)
    }

    @Test fun `changed authentication scope cannot publish a pending own profile`() = runBlocking {
        for (change in listOf("tenant", "app", "account", "generation", "page")) {
            val f = Fixture(target = "self")
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            f.beforeSelfProfile = { entered.complete(Unit); release.await() }
            val request = launch(start = CoroutineStart.UNDISPATCHED) { f.loader.refresh() }
            withTimeout(1_000) { entered.await() }
            when (change) {
                "tenant" -> f.scope = f.scope!!.copy(companyCode = "other")
                "app" -> f.scope = f.scope!!.copy(sdkAppID = 456)
                "account" -> f.scope = f.scope!!.copy(userID = "replacement")
                "generation" -> f.scope = f.scope!!.copy(generation = 2)
                else -> f.active = false
            }
            release.complete(Unit)
            request.join()
            assertFalse(change, f.states.any { it is XingDunGroupSourceProfileState.Loaded })
            assertEquals(0, f.relationshipReads)
        }
    }

    @Test fun `own profile cannot authorize a queued friend recommendation`() = runBlocking {
        val f = Fixture(target = "self")
        f.loader.queueRecommendation("c2c-recipient")
        assertTrue(f.loader.refresh())
        assertTrue(f.recommended.isEmpty())
        assertEquals(1, f.recommendationFailures)
    }

    @Test fun `nonself detail denial never falls back to own private fields`() = runBlocking {
        val f = Fixture()
        f.profileError = IllegalStateException("Synthetic friend detail denial")
        assertFalse(f.loader.refresh())
        assertSame(XingDunGroupSourceProfileState.Unavailable, f.states.last())
        assertEquals(0, f.selfProfileReads)
    }

    @Test fun `refresh conceals previously visible profile before awaiting current authority`() = runBlocking {
        val f = Fixture()
        f.loader.refresh()
        assertTrue(f.states.last() is XingDunGroupSourceProfileState.Loaded)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.beforeProfile = { entered.complete(Unit); release.await() }
        val refresh = launch(start = CoroutineStart.UNDISPATCHED) { f.loader.refresh() }
        entered.await()
        assertSame(XingDunGroupSourceProfileState.Loading, f.states.last())
        release.complete(Unit)
        refresh.join()
    }

    @Test fun `fresh revoked card permission removes the old profile and does not reload private fields`() = runBlocking {
        val f = Fixture()
        f.loader.refresh()
        f.group = f.group.copy(viewMemberCardMode = 2)
        f.loader.refresh()
        assertSame(XingDunGroupSourceProfileState.Restricted, f.states.last())
        assertEquals(1, f.profileReads)
        assertEquals(2, f.groupReads)
    }

    @Test fun `old relationship reply cannot publish after authentication owner changes`() = runBlocking {
        val f = Fixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.beforeRelationship = { entered.complete(Unit); release.await() }
        val request = launch(start = CoroutineStart.UNDISPATCHED) { f.loader.refresh() }
        entered.await()
        f.scope = f.scope!!.copy(companyCode = "other", userID = "replacement", generation = 2)
        release.complete(Unit)
        request.join()
        assertFalse(f.states.any { it is XingDunGroupSourceProfileState.Loaded })
    }

    @Test fun `existing card exceptions remain distinct from private chat permission`() = runBlocking {
        for (exception in listOf("self", "owner", "administrator", "assigned-cs")) {
            val f = Fixture(target = if (exception == "self") "self" else "target")
            f.group = f.group.copy(
                viewMemberCardMode = 2, memberPrivateChat = 2,
                currentUserRole = if (exception in listOf("owner", "administrator")) exception else "member",
                currentUserIsAssignedCs = exception == "assigned-cs",
            )
            f.loader.refresh()
            assertTrue(exception, f.states.last() is XingDunGroupSourceProfileState.Loaded)
        }
    }

    @Test fun `role or assigned-customer-service removal revokes an already loaded card`() = runBlocking {
        for (previous in listOf("administrator", "assigned-cs")) {
            val f = Fixture()
            f.group = f.group.copy(viewMemberCardMode = 2,
                currentUserRole = if (previous == "administrator") previous else "member",
                currentUserIsAssignedCs = previous == "assigned-cs")
            f.loader.refresh()
            assertTrue(f.states.last() is XingDunGroupSourceProfileState.Loaded)
            f.group = f.group.copy(currentUserRole = "member", currentUserIsAssignedCs = false)
            f.loader.refresh()
            assertSame(previous, XingDunGroupSourceProfileState.Restricted, f.states.last())
            assertEquals(1, f.profileReads)
        }
    }

    @Test fun `unknown or restricted mode never opens ordinary member fields`() = runBlocking {
        for (mode in listOf(0, 2, 99)) {
            val f = Fixture()
            f.group = f.group.copy(viewMemberCardMode = mode)
            f.loader.refresh()
            assertSame(XingDunGroupSourceProfileState.Restricted, f.states.last())
            assertEquals(0, f.profileReads)
        }
    }

    @Test fun `failed and mismatched reads conceal old fields and allow a fresh retry`() = runBlocking {
        for (failure in listOf("group", "group-id", "profile", "profile-id", "relationship", "relationship-id")) {
            val f = Fixture()
            f.loader.refresh()
            when (failure) {
                "group" -> f.groupError = IllegalStateException("synthetic")
                "group-id" -> f.group = f.group.copy(groupId = "other-group")
                "profile" -> f.profileError = IllegalStateException("synthetic")
                "profile-id" -> f.profile = f.profile.copy(timUserId = "other-user")
                "relationship" -> f.relationshipError = IllegalStateException("synthetic")
                "relationship-id" -> f.relationship.addProperty("tim_user_id", "other-user")
            }
            f.loader.refresh()
            assertSame(failure, XingDunGroupSourceProfileState.Unavailable, f.states.last())
            f.groupError = null; f.profileError = null; f.relationshipError = null
            f.group = f.group.copy(groupId = "group-1")
            f.profile = f.profile.copy(timUserId = "target")
            f.relationship.addProperty("tim_user_id", "target")
            f.loader.refresh()
            assertTrue(failure, f.states.last() is XingDunGroupSourceProfileState.Loaded)
        }
    }

    @Test fun `every await rejects changed tenant app account generation or paused page`() = runBlocking {
        for (stage in listOf("group", "profile", "relationship")) {
            for (change in listOf("tenant", "app", "account", "generation", "page")) {
                val f = Fixture()
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val barrier: suspend () -> Unit = { entered.complete(Unit); release.await() }
                when (stage) {
                    "group" -> f.beforeGroup = barrier
                    "profile" -> f.beforeProfile = barrier
                    else -> f.beforeRelationship = barrier
                }
                val request = launch(start = CoroutineStart.UNDISPATCHED) { f.loader.refresh() }
                entered.await()
                when (change) {
                    "tenant" -> f.scope = f.scope!!.copy(companyCode = "other")
                    "app" -> f.scope = f.scope!!.copy(sdkAppID = 456)
                    "account" -> f.scope = f.scope!!.copy(userID = "replacement")
                    "generation" -> f.scope = f.scope!!.copy(generation = 2)
                    else -> f.active = false
                }
                val publications = f.states.size
                release.complete(Unit)
                request.join()
                assertEquals("$stage/$change", publications, f.states.size)
                assertFalse("$stage/$change", f.states.any { it is XingDunGroupSourceProfileState.Loaded })
            }
        }
    }

    @Test fun `invalidating or replacing a pending request rejects its late publication`() = runBlocking {
        for (stage in listOf("group", "profile", "relationship")) {
            val f = Fixture()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var first = true
            val barrier: suspend () -> Unit = { if (first) { first = false; entered.complete(Unit); release.await() } }
            when (stage) {
                "group" -> f.beforeGroup = barrier
                "profile" -> f.beforeProfile = barrier
                else -> f.beforeRelationship = barrier
            }
            val old = launch(start = CoroutineStart.UNDISPATCHED) { f.loader.refresh() }
            entered.await()
            f.loader.invalidate()
            f.loader.refresh()
            assertTrue(f.states.last() is XingDunGroupSourceProfileState.Loaded)
            val publications = f.states.size
            release.complete(Unit)
            old.join()
            assertEquals(stage, publications, f.states.size)
        }
    }

    @Test fun `coroutine cancellation stays cancelled and permits a later foreground refresh`() = runBlocking {
        val f = Fixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.beforeRelationship = { entered.complete(Unit); release.await() }
        val request = launch(start = CoroutineStart.UNDISPATCHED) { f.loader.refresh() }
        entered.await()
        request.cancelAndJoin()
        assertTrue(request.isCancelled)
        assertSame(XingDunGroupSourceProfileState.Loading, f.states.last())
        f.beforeRelationship = {}
        f.loader.refresh()
        assertTrue(f.states.last() is XingDunGroupSourceProfileState.Loaded)
    }

    @Test fun `missing owner invalid input or changed page owner does not read or publish private fields`() = runBlocking {
        for (invalid in listOf("group", "target", "owner", "owner-after-creation")) {
            val f = Fixture(groupID = if (invalid == "group") "" else "group-1",
                target = if (invalid == "target") "" else "target", missingOwner = invalid == "owner")
            if (invalid == "owner-after-creation") f.scope = f.scope!!.copy(generation = 2)
            f.loader.refresh()
            assertSame(invalid, XingDunGroupSourceProfileState.Unavailable, f.states.last())
            assertEquals(0, f.groupReads)
            assertEquals(0, f.profileReads)
        }
    }

    @Test fun `recommendation returned before resume waits for a fresh authorized profile then sends once`() = runBlocking {
        val f = Fixture()
        f.loader.refresh()
        f.active = false
        f.loader.invalidate()
        f.loader.queueRecommendation("c2c_recipient")
        assertTrue(f.recommended.isEmpty())
        f.profile = f.profile.copy(signature = "Fresh synthetic field")
        f.active = true
        f.loader.refresh()
        assertEquals(listOf("c2c_recipient"), f.recommended)
        assertEquals("Fresh synthetic field", f.sentSignature)
        f.loader.refresh()
        assertEquals(1, f.recommended.size)
    }

    @Test fun `recommendation denied or unavailable after returning is reported once and never replayed`() = runBlocking {
        for (failure in listOf("restricted", "network", "not-friend")) {
            val f = Fixture()
            f.loader.refresh()
            f.loader.invalidate()
            f.loader.queueRecommendation("c2c_recipient")
            when (failure) {
                "restricted" -> f.group = f.group.copy(viewMemberCardMode = 2)
                "network" -> f.groupError = IllegalStateException("synthetic")
                else -> f.relationship.addProperty("relationship_status", "none")
            }
            f.loader.refresh()
            assertTrue(failure, f.recommended.isEmpty())
            assertEquals(failure, 1, f.recommendationFailures)
            f.group = f.group.copy(viewMemberCardMode = 1)
            f.groupError = null
            f.relationship.addProperty("relationship_status", "friend")
            f.loader.refresh()
            assertTrue(failure, f.recommended.isEmpty())
            assertEquals(failure, 1, f.recommendationFailures)
        }
    }

    @Test fun `recommendation does not survive a later pause owner change or replaced authentication`() = runBlocking {
        for (change in listOf("pause", "account", "generation")) {
            val f = Fixture()
            f.loader.queueRecommendation("c2c_recipient")
            when (change) {
                "pause" -> f.loader.invalidate()
                "account" -> f.scope = f.scope!!.copy(userID = "replacement")
                else -> f.scope = f.scope!!.copy(generation = 2)
            }
            f.loader.refresh()
            assertTrue(change, f.recommended.isEmpty())
            assertEquals(change, 0, f.recommendationFailures)
        }
    }

    @Test fun `periodic unchanged refresh does not mask the confirmed foreground profile`() = runBlocking {
        val f = Fixture()
        f.loader.refresh()
        val count = f.states.size
        f.loader.refresh(periodic = true)
        assertFalse(f.states.drop(count).any { it === XingDunGroupSourceProfileState.Loading })
        assertTrue(f.states.last() is XingDunGroupSourceProfileState.Loaded)
    }

    @Test fun `equal actual rendered inputs do not request another view rebuild`() {
        val renderer = XingDunGroupSourceProfileRenderState()
        val first = XingDunContactDetail(timUserId = "target", nickname = "Synthetic",
            profileVisibility = JsonObject().apply { addProperty("signature", true) }, signature = "Synthetic value")
        assertTrue(renderer.shouldRender(first, "friend", true, true, false, null, "target"))
        val same = first.copy(profileVisibility = first.profileVisibility!!.deepCopy())
        assertFalse(renderer.shouldRender(same, "friend", true, true, false, null, "target"))
    }

    @Test fun `periodic success does not dispatch a queued recommendation instead of hard reauthorization`() = runBlocking {
        val f = Fixture()
        f.loader.refresh()
        f.loader.queueRecommendation("c2c_recipient")
        f.loader.refresh(periodic = true)
        assertTrue(f.recommended.isEmpty())
        f.loader.refresh()
        assertEquals(listOf("c2c_recipient"), f.recommended)
    }

    @Test fun `periodic without a confirmed profile still starts concealed`() = runBlocking {
        val f = Fixture()
        f.loader.refresh(periodic = true)
        assertSame(XingDunGroupSourceProfileState.Loading, f.states.first())
        f.groupError = IllegalStateException("synthetic")
        f.loader.refresh(periodic = true)
        assertSame(XingDunGroupSourceProfileState.Unavailable, f.states.last())
        val count = f.states.size
        f.groupError = null
        f.loader.refresh(periodic = true)
        assertSame(XingDunGroupSourceProfileState.Loading, f.states[count])
    }

    @Test fun `periodic denial failure or mismatched context closes the retained profile`() = runBlocking {
        for (failure in listOf("deny", "group", "profile", "relationship", "mismatch")) {
            val f = Fixture()
            f.loader.refresh()
            when (failure) {
                "deny" -> f.group = f.group.copy(viewMemberCardMode = 2)
                "group" -> f.groupError = IllegalStateException("synthetic")
                "profile" -> f.profileError = IllegalStateException("synthetic")
                "relationship" -> f.relationshipError = IllegalStateException("synthetic")
                else -> f.group = f.group.copy(groupId = "other")
            }
            f.loader.refresh(periodic = true)
            assertSame(failure, if (failure == "deny") XingDunGroupSourceProfileState.Restricted else XingDunGroupSourceProfileState.Unavailable, f.states.last())
        }
    }

    @Test fun `periodic owner or generation loss at every await conceals instead of leaving the old profile`() = runBlocking {
        for (stage in listOf("group", "profile", "relationship")) {
            for (change in listOf("tenant", "app", "account", "generation", "missing")) {
                val f = Fixture()
                f.loader.refresh()
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val barrier: suspend () -> Unit = { entered.complete(Unit); release.await() }
                when (stage) { "group" -> f.beforeGroup = barrier; "profile" -> f.beforeProfile = barrier; else -> f.beforeRelationship = barrier }
                val pending = launch(start = CoroutineStart.UNDISPATCHED) { f.loader.refresh(periodic = true) }
                entered.await()
                when (change) {
                    "tenant" -> f.scope = f.scope!!.copy(companyCode = "other")
                    "app" -> f.scope = f.scope!!.copy(sdkAppID = 456)
                    "account" -> f.scope = f.scope!!.copy(userID = "replacement")
                    "generation" -> f.scope = f.scope!!.copy(generation = 2)
                    else -> f.scope = null
                }
                release.complete(Unit)
                pending.join()
                assertSame("$stage/$change", XingDunGroupSourceProfileState.Loading, f.states.last())
            }
        }
    }

    @Test fun `periodic overall deadline cancels any awaited stage and closes the retained profile`() = runBlocking {
        for (stage in listOf("group", "profile", "relationship")) {
            val f = Fixture(periodicTimeoutMillis = 50)
            f.loader.refresh()
            var cancelled = false
            val never = CompletableDeferred<Unit>()
            val barrier: suspend () -> Unit = { try { never.await() } finally { cancelled = true } }
            when (stage) { "group" -> f.beforeGroup = barrier; "profile" -> f.beforeProfile = barrier; else -> f.beforeRelationship = barrier }
            val completed = withTimeout(2_000) { f.loader.refresh(periodic = true) }
            assertFalse(stage, completed)
            assertTrue(stage, cancelled)
            assertSame(stage, XingDunGroupSourceProfileState.Unavailable, f.states.last())
        }
    }

    @Test fun `obsolete periodic timeout cannot conceal a newer hard request success`() = runBlocking {
        val f = Fixture(periodicTimeoutMillis = 50)
        f.loader.refresh()
        val entered = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        var first = true
        f.beforeGroup = { if (first) { first = false; entered.complete(Unit); never.await() } }
        val old = async(start = CoroutineStart.UNDISPATCHED) { f.loader.refresh(periodic = true) }
        entered.await()
        f.loader.refresh()
        val count = f.states.size
        val newest = f.states.last()
        assertFalse(withTimeout(2_000) { old.await() })
        assertEquals(count, f.states.size)
        assertSame(newest, f.states.last())
    }

    @Test fun `cancelled old periodic request does not clear the current request or its queued intent`() = runBlocking {
        val f = Fixture()
        f.loader.refresh()
        val entered = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        var first = true
        f.beforeGroup = { if (first) { first = false; entered.complete(Unit); never.await() } }
        val old = launch(start = CoroutineStart.UNDISPATCHED) { f.loader.refresh(periodic = true) }
        entered.await()
        f.loader.queueRecommendation("c2c_recipient")
        f.loader.refresh()
        val count = f.states.size
        old.cancelAndJoin()
        assertEquals(count, f.states.size)
        assertEquals(listOf("c2c_recipient"), f.recommended)
    }

    @Test fun `a hard request crossing unknown owner cannot later replay its recommendation when the owner returns`() = runBlocking {
        val f = Fixture()
        val owner = f.scope
        f.loader.queueRecommendation("c2c_recipient")
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.beforeRelationship = { entered.complete(Unit); release.await() }
        val request = launch(start = CoroutineStart.UNDISPATCHED) { f.loader.refresh() }
        entered.await()
        f.scope = null
        release.complete(Unit)
        request.join()
        f.scope = owner
        f.beforeRelationship = {}
        f.loader.refresh()
        assertTrue(f.recommended.isEmpty())
        assertEquals(0, f.recommendationFailures)
    }

    @Test fun `render projection detects all displayed data and action factors without retaining mutable JSON`() {
        fun policy() = JsonObject().apply { listOf("account", "birthday", "phone", "email", "signature").forEach { addProperty(it, true) } }
        val base = XingDunContactDetail(timUserId = "target", customId = "synthetic-id", nickname = "Synthetic",
            avatar = "https://example.invalid/a.png", alias = "Synthetic alias", birthday = "2000-01-01", phone = "synthetic-phone",
            email = "synthetic@example.invalid", signature = "Synthetic signature", profileVisibility = policy())
        val variants = listOf(base.copy(nickname = "Other"), base.copy(avatar = "other"), base.copy(customId = "other"),
            base.copy(accountType = "internal"), base.copy(alias = "Other"), base.copy(birthday = "2001-01-01"),
            base.copy(phone = "other"), base.copy(email = "other"), base.copy(signature = "other"), base.copy(isBlacklist = true))
        for (changed in variants) {
            val state = XingDunGroupSourceProfileRenderState()
            state.shouldRender(base, "friend", true, true, false, null, "target")
            assertTrue(state.shouldRender(changed, "friend", true, true, false, null, "target"))
        }
        for (field in listOf("account", "birthday", "phone", "email", "signature")) {
            val profile = base.copy(profileVisibility = policy())
            val state = XingDunGroupSourceProfileRenderState()
            state.shouldRender(profile, "friend", true, true, false, null, "target")
            profile.profileVisibility!!.addProperty(field, false)
            assertTrue(field, state.shouldRender(profile, "friend", true, true, false, null, "target"))
        }
        for (change in listOf("relationship", "audio", "video", "busy", "error")) {
            val state = XingDunGroupSourceProfileRenderState()
            state.shouldRender(base, "friend", true, true, false, null, "target")
            assertTrue(change, state.shouldRender(base, if (change == "relationship") "none" else "friend",
                change != "audio", change != "video", change == "busy", if (change == "error") "Synthetic error" else null, "target"))
        }
    }

    @Test fun `hidden values and unrelated DTO metadata do not rebuild identical UI but reset restores hard rendering`() {
        val profile = XingDunContactDetail(timUserId = "target", nickname = " Synthetic ", phone = "old hidden value", signature = "hidden")
        val state = XingDunGroupSourceProfileRenderState()
        assertTrue(state.shouldRender(profile, "friend", true, true, false, null, "target"))
        assertFalse(state.shouldRender(profile.copy(nickname = "Synthetic", phone = "new hidden value", signature = "different hidden",
            departmentPath = listOf("ignored"), isBlockedByPeer = true), "friend", true, true, false, null, "target"))
        state.reset()
        assertTrue(state.shouldRender(profile, "friend", true, true, false, null, "target"))
    }

    @Test fun `periodic deadline aborts the actual HTTP call through the production cancellation bridge`() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val peer = AtomicReference<Socket?>()
        val accepted = CompletableDeferred<Unit>()
        val acceptThread = thread(isDaemon = true, name = "group-profile-periodic-timeout-test") {
            runCatching { server.accept() }.onSuccess { peer.set(it); accepted.complete(Unit) }
                .onFailure { accepted.completeExceptionally(it) }
        }
        val client = OkHttpClient()
        val call = client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/").build())
        val f = Fixture(periodicTimeoutMillis = 1_000)
        f.loader.refresh()
        f.beforeGroup = { call.executeCancellable { Unit } }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { f.loader.refresh(periodic = true) }
        try {
            withTimeout(5_000) { accepted.await() }
            assertFalse(withTimeout(3_000) { pending.await() })
            assertTrue(call.isCanceled())
            assertSame(XingDunGroupSourceProfileState.Unavailable, f.states.last())
            assertTrue(f.recommended.isEmpty())
        } finally {
            pending.cancelAndJoin()
            server.close()
            acceptThread.join(1_000)
            peer.get()?.close()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }

    private class Fixture(groupID: String = "group-1", target: String = "target", missingOwner: Boolean = false, periodicTimeoutMillis: Long = 10_000L) {
        var scope: XingDunGroupMemberPreviewScope? = if (missingOwner) null else XingDunGroupMemberPreviewScope("tenant", 123, "self", 1)
        var active = true
        var group = XingDunGroupDetail(groupId = "group-1", viewMemberCardMode = 1)
        var profile = XingDunContactDetail(timUserId = target, signature = "Synthetic private field")
        var selfProfile = XingDunContactDetail(timUserId = "self", nickname = "Own profile")
        var relationship = JsonObject().apply { addProperty("relationship_status", "friend"); addProperty("tim_user_id", target) }
        var groupReads = 0
        var profileReads = 0
        var selfProfileReads = 0
        var relationshipReads = 0
        var groupError: Exception? = null
        var profileError: Exception? = null
        var selfProfileError: Exception? = null
        var relationshipError: Exception? = null
        var beforeGroup: suspend () -> Unit = {}
        var beforeProfile: suspend () -> Unit = {}
        var beforeSelfProfile: suspend () -> Unit = {}
        var beforeRelationship: suspend () -> Unit = {}
        val states = mutableListOf<XingDunGroupSourceProfileState>()
        val recommended = mutableListOf<String>()
        var sentSignature: String? = null
        var recommendationFailures = 0
        val loader = XingDunGroupSourceProfileLoader(
            groupID = groupID, targetUserID = target, currentScope = { scope },
            loadGroup = { groupReads++; beforeGroup(); groupError?.let { throw it }; group },
            loadProfile = { profileReads++; beforeProfile(); profileError?.let { throw it }; profile },
            loadSelfProfile = { selfProfileReads++; beforeSelfProfile(); selfProfileError?.let { throw it }; selfProfile },
            loadRelationship = { relationshipReads++; beforeRelationship(); relationshipError?.let { throw it }; relationship },
            publish = { states += it }, isActive = { active },
            recommend = { recommended += it; sentSignature = (states.last() as XingDunGroupSourceProfileState.Loaded).profile.signature },
            showRecommendationFailure = { recommendationFailures++ },
            periodicTimeoutMillis = periodicTimeoutMillis,
        )
    }
}
