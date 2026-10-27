package io.trtc.tuikit.chat.demo.xingdun.features

import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

class XingDunGroupMemberPreviewNavigatorTest {
    @Test fun `friend preview opens the group-source profile rather than independent C2C settings`() = runBlocking {
        val fixture = Fixture(friend = true)
        fixture.navigator.open("group-1", "target")
        assertEquals(listOf("group-1" to "target"), fixture.opened)
        assertEquals(1, fixture.groupReads)
        assertTrue(fixture.failures.isEmpty())
    }

    @Test fun `nonfriend preview preserves the same source instead of opening add-friend dialog`() = runBlocking {
        val fixture = Fixture(friend = false)
        fixture.navigator.open("group-1", "target")
        assertEquals(listOf("group-1" to "target"), fixture.opened)
        assertEquals(1, fixture.groupReads)
        assertTrue(fixture.failures.isEmpty())
    }

    @Test fun `fresh restricted card policy blocks friend and nonfriend before fetching profile`() = runBlocking {
        for (friend in listOf(true, false)) {
            val fixture = Fixture(friend)
            fixture.detail = fixture.detail.copy(viewMemberCardMode = 2)
            fixture.navigator.open("group-1", "target")
            assertTrue(fixture.opened.isEmpty())
            assertEquals(listOf(XingDunGroupMemberPreviewFailure.RESTRICTED), fixture.failures)
            assertEquals(0, fixture.contactReads)
        }
    }

    @Test fun `empty mismatched and failed responses show a safe failure and allow retry`() = runBlocking {
        for (failure in listOf("group-error", "group-mismatch", "contact-error", "contact-empty", "contact-mismatch")) {
            val fixture = Fixture()
            when (failure) {
                "group-error" -> fixture.groupError = IllegalStateException("fixture")
                "group-mismatch" -> fixture.detail = fixture.detail.copy(groupId = "other-group")
                "contact-error" -> fixture.contactError = IllegalStateException("fixture")
                "contact-empty" -> fixture.contact = null
                "contact-mismatch" -> fixture.contact = ContactInfo(userID = "other-user")
            }
            fixture.navigator.open("group-1", "target")
            assertTrue(failure, fixture.opened.isEmpty())
            assertEquals(failure, listOf(XingDunGroupMemberPreviewFailure.UNAVAILABLE), fixture.failures)
            fixture.groupError = null
            fixture.contactError = null
            fixture.detail = XingDunGroupDetail(groupId = "group-1", viewMemberCardMode = 1)
            fixture.contact = ContactInfo(userID = "target")
            fixture.navigator.open("group-1", "target")
            assertEquals(listOf("group-1" to "target"), fixture.opened)
        }
    }

    @Test fun `each click reloads card authority and does not cache earlier permission`() = runBlocking {
        val fixture = Fixture()
        fixture.navigator.open("group-1", "target")
        fixture.detail = fixture.detail.copy(viewMemberCardMode = 2)
        fixture.navigator.open("group-1", "target")
        assertEquals(2, fixture.groupReads)
        assertEquals(1, fixture.contactReads)
        assertEquals(listOf("group-1" to "target"), fixture.opened)
        assertEquals(listOf(XingDunGroupMemberPreviewFailure.RESTRICTED), fixture.failures)
    }

    @Test fun `scope replacement or leaving the page rejects late group and contact replies`() = runBlocking {
        for (stage in listOf("group", "contact")) {
            for (change in listOf("generation", "account", "page")) {
                val fixture = Fixture()
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val barrier: suspend () -> Unit = { entered.complete(Unit); release.await() }
                if (stage == "group") fixture.beforeGroup = barrier else fixture.beforeContact = barrier
                val request = launch(start = CoroutineStart.UNDISPATCHED) { fixture.navigator.open("group-1", "target") }
                entered.await()
                when (change) {
                    "generation" -> fixture.scope = fixture.scope!!.copy(generation = 2)
                    "account" -> fixture.scope = fixture.scope!!.copy(companyCode = "other", userID = "replacement")
                    "page" -> fixture.active = false
                }
                release.complete(Unit)
                request.join()
                assertTrue("$stage/$change", fixture.opened.isEmpty())
                assertTrue("$stage/$change", fixture.failures.isEmpty())
                if (stage == "group") assertEquals(0, fixture.contactReads)
            }
        }
    }

    @Test fun `duplicate clicks share no extra work and cancellation releases the guard`() = runBlocking {
        val fixture = Fixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.beforeContact = { entered.complete(Unit); release.await() }
        val request = launch(start = CoroutineStart.UNDISPATCHED) { fixture.navigator.open("group-1", "target") }
        entered.await()
        fixture.navigator.open("group-1", "target")
        assertEquals(1, fixture.groupReads)
        assertEquals(1, fixture.contactReads)
        request.cancelAndJoin()
        assertTrue(fixture.opened.isEmpty())
        assertTrue(fixture.failures.isEmpty())
        fixture.beforeContact = {}
        fixture.navigator.open("group-1", "target")
        assertEquals(listOf("group-1" to "target"), fixture.opened)
    }

    @Test fun `invalid input or missing owner never reads or navigates`() = runBlocking {
        val fixture = Fixture()
        fixture.navigator.open("", "target")
        fixture.navigator.open("group-1", " ")
        fixture.scope = null
        fixture.navigator.open("group-1", "target")
        assertEquals(0, fixture.groupReads)
        assertEquals(0, fixture.contactReads)
        assertTrue(fixture.opened.isEmpty())
        assertEquals(3, fixture.failures.size)
        assertTrue(fixture.failures.all { it == XingDunGroupMemberPreviewFailure.UNAVAILABLE })
    }

    private class Fixture(friend: Boolean = true) {
        var scope: XingDunGroupMemberPreviewScope? = XingDunGroupMemberPreviewScope("tenant", 123, "self", 1)
        var detail = XingDunGroupDetail(groupId = "group-1", viewMemberCardMode = 1)
        var contact: ContactInfo? = ContactInfo(userID = "target", nickname = "Original name", isFriend = friend)
        var active = true
        var groupReads = 0
        var contactReads = 0
        var groupError: Exception? = null
        var contactError: Exception? = null
        var beforeGroup: suspend () -> Unit = {}
        var beforeContact: suspend () -> Unit = {}
        val opened = mutableListOf<Pair<String, String>>()
        val failures = mutableListOf<XingDunGroupMemberPreviewFailure>()
        val navigator = XingDunGroupMemberPreviewNavigator(
            currentScope = { scope },
            loadGroup = { groupReads++; beforeGroup(); groupError?.let { throw it }; detail },
            loadContact = { contactReads++; beforeContact(); contactError?.let { throw it }; contact },
            openProfile = { group, info -> opened += group to info.userID },
            showFailure = { failures += it },
            isActive = { active },
        )
    }
}
