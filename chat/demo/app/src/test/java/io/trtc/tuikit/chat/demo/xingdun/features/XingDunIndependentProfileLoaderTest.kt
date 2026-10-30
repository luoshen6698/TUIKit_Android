package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonObject
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunContactDetail
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class XingDunIndependentProfileLoaderTest {
    @Test fun `request binding ignores display metadata but rejects actual credential or service replacement`() = runBlocking {
        val session = syntheticSession()
        val binding = XingDunIndependentProfileCredentials.fromSession(session)
        assertNotNull(binding)
        assertEquals(binding, XingDunIndependentProfileCredentials.fromSession(session.copy(companyName = "New", nickname = "New")))
        for (changed in listOf(
            session.copy(accessToken = "replacement"), session.copy(tokenType = "replacement"),
            session.copy(userSig = "replacement"), session.copy(apiBaseUrl = "https://other.invalid/"),
        )) {
            assertNotEquals(binding, XingDunIndependentProfileCredentials.fromSession(changed))
            val f = Fixture()
            f.beforeProfile = { f.session = changed }
            f.loader.refresh()
            assertNull(f.states.last().profile)
            assertFalse(f.loader.ownsSnapshot())
            assertEquals(0, f.relationshipReads)
        }
        assertNull(XingDunIndependentProfileCredentials.fromSession(null))
        assertNull(XingDunIndependentProfileCredentials.fromSession(session.copy(accessToken = "")))
        assertEquals("XingDunIndependentProfileCredentials(redacted)", binding.toString())
    }
    @Test fun `same credential foreground enterprise refresh does not reject a successful profile`() = runBlocking {
        val f = Fixture(initialRelationship = "friend")
        // Application's foreground enterprise refresh saves metadata while preserving the
        // authenticated request credentials. The store revision alone is not their identity.
        f.beforeProfile = { f.session = f.session!!.copy(companyName = "Refreshed company", nickname = "Refreshed nickname") }
        f.loader.refresh()
        assertNotNull(f.states.last().profile)
        assertFalse(f.states.last().failed)
    }
    @Test fun `fresh explicit empty fields cannot restore older account and signature`() {
        val old = visibleProfile()
        val fresh = relationship().apply {
            addProperty("custom_id", ""); addProperty("signature", ""); add("profile_visibility", visiblePolicy())
        }
        val merged = mergeXingDunRelationshipProfile(old, fresh, "target").first
        assertNull(merged.customId)
        assertNull(merged.signature)
    }

    @Test fun `missing public policy cannot retain an older visibility grant`() {
        val merged = mergeXingDunRelationshipProfile(visibleProfile(), relationship(), "target").first
        assertTrue(XingDunProfileVisibility.fields.none { XingDunProfileVisibility.visible(merged.profileVisibility, it) })
    }

    @Test fun `verified own group profile is preserved while a public self flag grants nothing`() {
        val own = visibleProfile()
        val syntheticSelf = relationship().apply { addProperty("is_self", true) }
        val verified = mergeXingDunRelationshipProfile(own, syntheticSelf, "target", authoritativeOwnProfile = true)
        assertSame(own, verified.first)
        assertEquals("self", verified.second)
        val public = mergeXingDunRelationshipProfile(own, syntheticSelf, "target").first
        assertNull(public.customId)
        assertNull(public.signature)
        assertTrue(XingDunProfileVisibility.fields.none { XingDunProfileVisibility.visible(public.profileVisibility, it) })
    }

    @Test fun `refresh conceals old visible snapshot before current reads and on failure`() = runBlocking {
        val f = Fixture()
        f.loader.refresh()
        assertNotNull(f.states.last().profile)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.beforeProfile = { entered.complete(Unit); release.await(); throw IllegalStateException("Synthetic detail outage") }
        f.relationshipError = true
        val request = launch(start = CoroutineStart.UNDISPATCHED) { f.loader.refresh() }
        entered.await()
        assertNull("Old fields must not remain while authority is pending", f.states.last().profile)
        release.complete(Unit)
        request.join()
        assertNull(f.states.last().profile)
        assertTrue(f.states.last().failed)
        f.beforeProfile = {}
        f.relationshipError = false
        f.loader.refresh()
        assertNotNull("Explicit retry recovers", f.states.last().profile)
    }

    @Test fun `held reads cannot continue or publish under changed context or paused page`() = runBlocking {
        for (phase in listOf("detail", "discovery")) for (change in listOf(
            "tenant", "app", "account", "generation", "credential", "logout", "pause", "invalidate", "cancel",
        )) {
            val f = Fixture()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val held: suspend () -> Unit = {
                withContext(NonCancellable) { entered.complete(Unit); release.await() }
            }
            if (phase == "detail") f.beforeProfile = held else f.beforeRelationship = held
            val request = launch(start = CoroutineStart.UNDISPATCHED) { f.loader.refresh() }
            withTimeout(1_000) { entered.await() }
            when (change) {
                "tenant" -> f.scope = f.scope!!.copy(companyCode = "other")
                "app" -> f.scope = f.scope!!.copy(sdkAppID = 456)
                "account" -> f.scope = f.scope!!.copy(userID = "replacement")
                "generation" -> f.scope = f.scope!!.copy(generation = 2)
                "credential" -> f.session = f.session!!.copy(accessToken = "synthetic-new-token")
                "logout" -> f.scope = null
                "pause" -> f.active = false
                "invalidate" -> f.loader.invalidate()
                "cancel" -> request.cancel()
            }
            release.complete(Unit)
            request.join()
            assertFalse("$phase/$change", f.states.any { it.profile != null })
            assertEquals("$phase/$change follow-on discovery", if (phase == "detail") 0 else 1, f.relationshipReads)
        }
    }

    @Test fun `failed detail cannot start discovery after owner changes`() = runBlocking {
        val f = Fixture()
        f.beforeProfile = { f.scope = f.scope!!.copy(userID = "replacement"); error("Synthetic failure") }
        f.loader.refresh()
        assertEquals(0, f.relationshipReads)
        assertTrue(f.states.last().failed)
        assertNull(f.states.last().profile)
    }

    @Test fun `new request survives old callback cancellation or failure`() = runBlocking {
        for (fails in listOf(false, true)) {
            val f = Fixture()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var first = true
            f.beforeProfile = {
                if (first) {
                    first = false
                    withContext(NonCancellable) { entered.complete(Unit); release.await() }
                    if (fails) error("Synthetic stale failure")
                }
            }
            val old = launch(start = CoroutineStart.UNDISPATCHED) { f.loader.refresh() }
            withTimeout(1_000) { entered.await() }
            f.loader.refresh()
            val current = f.states.last()
            assertNotNull(current.profile)
            old.cancel()
            release.complete(Unit)
            old.join()
            assertSame("Old completion must not clear new success", current, f.states.last())
            assertEquals(1, f.relationshipReads)
        }
    }

    @Test fun `new explicit read can recover under refreshed credentials without changing actor`() = runBlocking {
        val f = Fixture()
        f.beforeProfile = { f.scope = f.scope!!.copy(generation = 2); f.session = f.session!!.copy(accessToken = "synthetic-new-token") }
        f.loader.refresh()
        assertTrue(f.states.last().failed)
        f.beforeProfile = {}
        f.loader.refresh()
        assertNotNull(f.states.last().profile)
        f.scope = f.scope!!.copy(userID = "other")
        val reads = f.profileReads
        f.loader.refresh()
        assertEquals("Page for the old actor must not start a new read", reads, f.profileReads)
        assertNull(f.states.last().profile)
    }

    @Test fun `snapshot cannot authorize an action after credentials page or request change`() = runBlocking {
        for (change in listOf("generation", "credential", "account", "pause", "invalidate", "refresh")) {
            val f = Fixture()
            assertFalse(f.loader.ownsSnapshot())
            f.loader.refresh()
            assertTrue(f.loader.ownsSnapshot())
            when (change) {
                "generation" -> f.scope = f.scope!!.copy(generation = 2)
                "credential" -> f.session = f.session!!.copy(accessToken = "synthetic-new-token")
                "account" -> f.scope = f.scope!!.copy(userID = "other")
                "pause" -> f.active = false
                "invalidate" -> f.loader.invalidate()
                "refresh" -> {
                    val entered = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    f.beforeProfile = { entered.complete(Unit); release.await() }
                    val request = launch(start = CoroutineStart.UNDISPATCHED) { f.loader.refresh() }
                    withTimeout(1_000) { entered.await() }
                    assertFalse(f.loader.ownsSnapshot())
                    request.cancel()
                    release.complete(Unit)
                    request.join()
                }
            }
            assertFalse(change, f.loader.ownsSnapshot())
        }
    }

    @Test fun `fresh discovery fallback clears fields absent from the public endpoint`() = runBlocking {
        val f = Fixture(initialRelationship = "friend", fallback = visibleProfile())
        f.beforeProfile = { error("Synthetic friendship detail denial") }
        f.loader.refresh()
        val profile = requireNotNull(f.states.last().profile)
        assertEquals("new-account", profile.customId)
        assertEquals("new-signature", profile.signature)
        assertNull(profile.phone)
        assertNull(profile.email)
        assertNull(profile.birthday)
        assertEquals("friend", f.states.last().relationship)
        assertEquals(1, f.relationshipReads)
    }

    @Test fun `wrong target and invalid owner never publish a profile`() = runBlocking {
        for (kind in listOf("detail-target", "discovery-target", "null-owner", "unreadable-storage")) {
            val f = Fixture()
            when (kind) {
                "detail-target" -> f.profile = visibleProfile().copy(timUserId = "wrong")
                "discovery-target" -> f.discoveryTarget = "wrong"
                "null-owner" -> f.scope = null
                else -> f.session = null
            }
            f.loader.refresh()
            assertFalse(kind, f.states.any { it.profile != null })
            assertTrue(kind, f.states.last().failed)
        }
    }

    @Test fun `empty discovery does not invent a nonfriend relationship or reuse fallback fields`() = runBlocking {
        val f = Fixture()
        f.emptyRelationship = true
        f.loader.refresh()
        assertNotNull(f.states.last().profile)
        assertEquals("unknown", f.states.last().relationship)
        f.beforeProfile = { error("Synthetic detail failure") }
        f.loader.refresh()
        assertNull(f.states.last().profile)
        assertTrue(f.states.last().failed)
    }

    @Test fun `malformed policies conceal all public fields and do not alias mutable JSON`() {
        for (kind in listOf("missing", "null", "scalar", "numeric", "string", "false", "true")) {
            val response = relationship().apply {
                addProperty("custom_id", "current"); addProperty("signature", "current")
                when (kind) {
                    "null" -> add("profile_visibility", com.google.gson.JsonNull.INSTANCE)
                    "scalar" -> addProperty("profile_visibility", true)
                    "numeric", "string", "false", "true" -> add("profile_visibility", JsonObject().apply {
                        XingDunProfileVisibility.fields.forEach {
                            when (kind) {
                                "numeric" -> addProperty(it, 1)
                                "string" -> addProperty(it, "true")
                                else -> addProperty(it, kind == "true")
                            }
                        }
                    })
                }
            }
            val merged = mergeXingDunRelationshipProfile(visibleProfile(), response, "target").first
            assertEquals(kind, kind == "true", XingDunProfileVisibility.visible(merged.profileVisibility, "account"))
            if (kind == "true") {
                response.getAsJsonObject("profile_visibility").addProperty("account", false)
                assertTrue(XingDunProfileVisibility.visible(merged.profileVisibility, "account"))
            } else {
                assertNull(merged.customId); assertNull(merged.signature); assertNull(merged.phone)
                assertNull(merged.email); assertNull(merged.birthday)
            }
        }
    }

    private class Fixture(initialRelationship: String = "unknown", fallback: XingDunContactDetail = XingDunContactDetail(timUserId = "target")) {
        var scope: XingDunGroupMemberPreviewScope? = XingDunGroupMemberPreviewScope("synthetic", 123, "viewer", 1)
        var beforeProfile: suspend () -> Unit = {}
        var beforeRelationship: suspend () -> Unit = {}
        var relationshipError = false
        var emptyRelationship = false
        var active = true
        var session: XingDunStoredSession? = syntheticSession()
        var profile = visibleProfile()
        var discoveryTarget = "target"
        var profileReads = 0
        var relationshipReads = 0
        val states = mutableListOf<XingDunIndependentProfileState>()
        val loader = XingDunIndependentProfileLoader(
            targetUserID = "target", fallback = fallback,
            initialRelationship = initialRelationship, currentScope = { scope },
            isActive = { active }, currentCredentials = { XingDunIndependentProfileCredentials.fromSession(session) },
            loadProfile = { profileReads++; beforeProfile(); profile },
            loadRelationship = { relationshipReads++; beforeRelationship(); if (relationshipError) error("Synthetic discovery outage"); if (emptyRelationship) null else relationship().apply {
                addProperty("tim_user_id", discoveryTarget)
                addProperty("custom_id", "new-account"); addProperty("signature", "new-signature")
                add("profile_visibility", visiblePolicy())
            } }, publish = { states += it },
        )
    }

    companion object {
        private fun syntheticSession() = XingDunStoredSession(
            accessToken = "synthetic-token", tokenType = "Bearer", accessExpiresAtMillis = 1000,
            companyCode = "synthetic", companyName = "Company", apiBaseUrl = "https://synthetic.invalid/",
            sdkAppId = 123, timUserId = "viewer", userSig = "synthetic-user-sig",
            userSigExpiresAtMillis = 1000, nickname = "Viewer",
        )
        private fun visiblePolicy() = JsonObject().apply { XingDunProfileVisibility.fields.forEach { addProperty(it, true) } }
        private fun visibleProfile() = XingDunContactDetail(
            timUserId = "target", customId = "old-account", signature = "old-signature",
            phone = "synthetic-phone", email = "synthetic-email", birthday = "2000-01-01",
            profileVisibility = visiblePolicy(),
        )
        private fun relationship() = JsonObject().apply {
            addProperty("tim_user_id", "target"); addProperty("relationship_status", "friend")
        }
    }
}
