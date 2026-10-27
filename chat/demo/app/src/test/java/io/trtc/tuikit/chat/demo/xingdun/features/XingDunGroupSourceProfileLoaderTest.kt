package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonObject
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunContactDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import org.junit.Assert.*
import org.junit.Test

class XingDunGroupSourceProfileLoaderTest {
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

    private class Fixture(groupID: String = "group-1", target: String = "target", missingOwner: Boolean = false) {
        var scope: XingDunGroupMemberPreviewScope? = if (missingOwner) null else XingDunGroupMemberPreviewScope("tenant", 123, "self", 1)
        var active = true
        var group = XingDunGroupDetail(groupId = "group-1", viewMemberCardMode = 1)
        var profile = XingDunContactDetail(timUserId = target, signature = "Synthetic private field")
        var relationship = JsonObject().apply { addProperty("relationship_status", "friend"); addProperty("tim_user_id", target) }
        var groupReads = 0
        var profileReads = 0
        var groupError: Exception? = null
        var profileError: Exception? = null
        var relationshipError: Exception? = null
        var beforeGroup: suspend () -> Unit = {}
        var beforeProfile: suspend () -> Unit = {}
        var beforeRelationship: suspend () -> Unit = {}
        val states = mutableListOf<XingDunGroupSourceProfileState>()
        val recommended = mutableListOf<String>()
        var sentSignature: String? = null
        var recommendationFailures = 0
        val loader = XingDunGroupSourceProfileLoader(
            groupID = groupID, targetUserID = target, currentScope = { scope },
            loadGroup = { groupReads++; beforeGroup(); groupError?.let { throw it }; group },
            loadProfile = { profileReads++; beforeProfile(); profileError?.let { throw it }; profile },
            loadRelationship = { beforeRelationship(); relationshipError?.let { throw it }; relationship },
            publish = { states += it }, isActive = { active },
            recommend = { recommended += it; sentSignature = (states.last() as XingDunGroupSourceProfileState.Loaded).profile.signature },
            showRecommendationFailure = { recommendationFailures++ },
        )
    }
}
