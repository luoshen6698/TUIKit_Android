package io.trtc.tuikit.chat.uikit.components.search.viewmodel

import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload
import io.trtc.tuikit.atomicxcore.api.search.MessageSearchResultItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test

class SearchMessageVisibilityTest {
    @Before fun prepare() { restore(); SearchMessageVisibility.setSessionProvider { "test-session" } }
    @After fun restore() {
        SearchMessageVisibility.setDisplayProvider(null)
        SearchMessageVisibility.setProvider { _, messages -> messages }
        SearchMessageVisibility.setDeletedProvider { _, _ -> false }
        SearchMessageVisibility.setQuoteHiddenProvider(null)
        SearchMessageVisibility.setConfirmedDeletedProvider(null)
        SearchMessageVisibility.setVerificationFailureProvider { false }
        SearchMessageVisibility.setSessionProvider { null }
    }

    @Test fun `unloaded quote targets are included in authoritative page queries`() {
        val reply = MessageInfo(msgID = "reply", quoteInfo = MessageQuoteInfo(msgID = "older-target"))
        assertEquals(setOf("reply", "older-target"), SearchMessageVisibility.candidateIDs(listOf(reply)))
    }

    @Test fun `confirmed quote tombstones redact snapshot without changing source or reply text`() {
        val quote = MessageQuoteInfo(msgID = "older-target", status = MessageStatus.SEND_SUCCESS,
            messagePayload = TextMessagePayload(text = "private original"))
        val reply = MessageInfo(msgID = "reply", quoteInfo = quote, messagePayload = TextMessagePayload(text = "keep reply"))
        SearchMessageVisibility.setDeletedProvider { conversation, id -> conversation == "c2c_bob" && id == "older-target" }
        val visible = SearchMessageVisibility.redactQuotes("c2c_bob", listOf(reply)).single()
        assertEquals(MessageStatus.DELETED, visible.quoteInfo?.status)
        assertNull(visible.quoteInfo?.messagePayload)
        assertEquals("keep reply", (visible.messagePayload as TextMessagePayload).text)
        assertEquals("private original", (reply.quoteInfo?.messagePayload as TextMessagePayload).text)
        assertEquals(MessageStatus.SEND_SUCCESS, reply.quoteInfo?.status)
    }

    @Test fun `group quote tombstones now redact while unknown C2C targets still remain intact`() {
        val reply = MessageInfo(msgID = "reply", quoteInfo = MessageQuoteInfo(msgID = "target",
            messagePayload = TextMessagePayload(text = "original")))
        SearchMessageVisibility.setDeletedProvider { _, _ -> true }
        assertEquals(MessageStatus.DELETED, SearchMessageVisibility.redactQuotes("group_bob", listOf(reply)).single().quoteInfo?.status)
        SearchMessageVisibility.setDeletedProvider { _, _ -> false }
        assertSame(reply, SearchMessageVisibility.redactQuotes("c2c_bob", listOf(reply)).single())
    }

    @Test fun `group search hints immediately hold only the target and preserve unrelated rows without network`() {
        SearchMessageVisibility.setDeletedProvider { conversation, id -> conversation == "group_g" && id == "target" }
        val row = MessageSearchResultItem(conversationID = "group_g", messageCount = 2,
            messageList = listOf(MessageInfo(msgID = "target"), MessageInfo(msgID = "retained")))
        val otherGroup = row.copy(conversationID = "group_other")
        val filtered = SearchMessageVisibility.filterKnown(listOf(row, otherGroup))
        assertEquals(listOf("retained"), filtered.first().messageList.map { it.msgID })
        assertEquals(2, filtered.last().messageList.size)
        assertEquals(2, row.messageList.size)
    }

    @Test fun `unverified group quote redaction keeps reply text and source snapshot intact`() {
        SearchMessageVisibility.setQuoteHiddenProvider { conversation, id -> conversation == "group_g" && id == "old" }
        val reply = MessageInfo(msgID = "reply", messagePayload = TextMessagePayload(text = "keep reply"),
            quoteInfo = MessageQuoteInfo(msgID = "old", messagePayload = TextMessagePayload(text = "private snapshot")))
        val safe = SearchMessageVisibility.redactQuotes("group_g", listOf(reply)).single()
        assertNull(safe.quoteInfo?.messagePayload)
        assertEquals("keep reply", (safe.messagePayload as TextMessagePayload).text)
        assertEquals("private snapshot", (reply.quoteInfo?.messagePayload as TextMessagePayload).text)
        assertSame(reply, SearchMessageVisibility.redactQuotes("group_other", listOf(reply)).single())
    }

    @Test fun `group authority failure becomes retry state and clears after successful recheck`() = runBlocking {
        var failed = true
        SearchMessageVisibility.setVerificationFailureProvider { it == "group_g" && failed }
        SearchMessageVisibility.setProvider { _, messages -> if (failed) emptyList() else messages }
        val rows = listOf(MessageSearchResultItem(conversationID = "group_g", messageCount = 1, messageList = listOf(MessageInfo(msgID = "M1"))))
        assertTrue(SearchMessageVisibility.filter(rows).isEmpty())
        assertTrue(SearchMessageVisibility.searchVerificationFailed.value)
        failed = false
        assertEquals(1, SearchMessageVisibility.filter(rows).size)
        assertFalse(SearchMessageVisibility.searchVerificationFailed.value)
    }

    @Test fun `late group action requires fresh authority and rejects account or login replacement`() = runBlocking {
        var session = "alice-login1"
        SearchMessageVisibility.setSessionProvider { session }
        SearchMessageVisibility.setProvider { conversation, messages ->
            assertEquals("group_g", conversation)
            session = "alice-login2"
            messages
        }
        try { SearchMessageVisibility.messagesForAction("group_g", listOf(MessageInfo(msgID = "old"))); fail("No stale source publication") }
        catch (_: IllegalStateException) { }
    }

    @Test fun `multi group batch does not query or publish remaining old SDK snapshots after account changes`() = runBlocking {
        var session = "alice-login1"
        val queried = mutableListOf<String>()
        SearchMessageVisibility.setSessionProvider { session }
        SearchMessageVisibility.setProvider { conversation, messages ->
            queried += conversation
            session = "bob-login1"
            messages
        }
        val results = listOf("group_one", "group_two").map { conversation ->
            MessageSearchResultItem(conversationID = conversation, messageCount = 1, messageList = listOf(MessageInfo(msgID = "old")))
        }
        assertTrue(SearchMessageVisibility.filter(results, "alice-login1").isEmpty())
        assertEquals(listOf("group_one"), queried)
        queried.clear()
        assertTrue(SearchMessageVisibility.filter(results, "alice-login1").isEmpty())
        assertTrue(queried.isEmpty())
    }

    @Test fun `source action fails on unavailable authority even when old safe rows remain visible`() = runBlocking {
        SearchMessageVisibility.setSessionProvider { "alice-login1" }
        SearchMessageVisibility.setProvider { _, messages -> messages }
        SearchMessageVisibility.setVerificationFailureProvider { it == "group_g" }
        try { SearchMessageVisibility.messagesForAction("group_g", listOf(MessageInfo(msgID = "old"))); fail("No optimistic forward") }
        catch (_: IllegalStateException) { }
    }

    @Test fun `verified deleted source is not forwardable and quote omission does not change C2C semantics`() = runBlocking {
        SearchMessageVisibility.setSessionProvider { "alice-login1" }
        SearchMessageVisibility.setProvider { _, messages -> messages.filterNot { it.msgID == "deleted" } }
        SearchMessageVisibility.setDeletedProvider { conversation, id -> conversation == "group_g" && id == "deleted" }
        val source = MessageInfo(msgID = "deleted", messagePayload = TextMessagePayload(text = "private original"))
        assertTrue(SearchMessageVisibility.messagesForAction("group_g", listOf(source)).isEmpty())
        assertNull(SearchMessageVisibility.quoteForSend("group_g", source))
        assertSame(source, SearchMessageVisibility.quoteForSend("c2c_bob", source))
        assertEquals("private original", (source.messagePayload as TextMessagePayload).text)
    }

    @Test fun `recreated providers retain durable authority and cannot leak into another account`() {
        var account = "alice"
        val disk = mapOf("alice:c2c_bob" to setOf("target"))
        val reply = MessageInfo(msgID = "reply", quoteInfo = MessageQuoteInfo(msgID = "target"))
        repeat(2) {
            SearchMessageVisibility.setDeletedProvider { conversation, id -> id in disk["$account:$conversation"].orEmpty() }
            assertEquals(MessageStatus.DELETED, SearchMessageVisibility.redactQuotes("c2c_bob", listOf(reply)).single().quoteInfo?.status)
        }
        account = "carol"
        assertSame(reply, SearchMessageVisibility.redactQuotes("c2c_bob", listOf(reply)).single())
    }

    @Test fun `confirmed hidden rows are excluded without mutating SDK search results`() = runBlocking {
        val first = MessageInfo(msgID = "deleted")
        val second = MessageInfo(msgID = "visible")
        val original = MessageSearchResultItem(conversationID = "c2c_bob", messageCount = 2,
            messageList = listOf(first, second))
        SearchMessageVisibility.setProvider { conversationID, messages ->
            assertEquals("c2c_bob", conversationID)
            messages.filterNot { it.msgID == "deleted" }
        }
        val result = SearchMessageVisibility.filter(listOf(original)).single()
        assertEquals(listOf("visible"), result.messageList.map { it.msgID })
        assertEquals(1, result.messageCount)
        assertEquals(2, original.messageList.size)
        assertEquals(2, original.messageCount)
    }

    @Test fun `fully hidden conversations are omitted and group rows remain independent`() = runBlocking {
        SearchMessageVisibility.setProvider { conversationID, messages ->
            if (conversationID.startsWith("c2c_")) emptyList() else messages
        }
        val rows = listOf(
            MessageSearchResultItem(conversationID = "c2c_bob", messageCount = 1, messageList = listOf(MessageInfo(msgID = "M1"))),
            MessageSearchResultItem(conversationID = "group_test", messageCount = 1, messageList = listOf(MessageInfo(msgID = "M2"))),
        )
        assertEquals(listOf("group_test"), SearchMessageVisibility.filter(rows).map { it.conversationID })
    }

    @Test fun `new deletion invalidates displayed search results`() {
        val previous = SearchMessageVisibility.revision.value
        SearchMessageVisibility.invalidate()
        assertTrue(SearchMessageVisibility.revision.value > previous)
    }

    @Test fun `every independent media page goes through authority including older unloaded IDs`() = runBlocking {
        val queried = mutableListOf<List<String>>()
        SearchMessageVisibility.setProvider { conversation, messages ->
            assertEquals("c2c_bob", conversation)
            queried += messages.map { it.msgID }
            messages.filterNot { it.msgID == "deleted-old-video" }
        }
        assertEquals(listOf("image"), SearchMessageVisibility.authoritativeMessages("c2c_bob", listOf(MessageInfo(msgID = "image"))).map { it.msgID })
        assertEquals(listOf("older-image"), SearchMessageVisibility.authoritativeMessages("c2c_bob",
            listOf(MessageInfo(msgID = "deleted-old-video"), MessageInfo(msgID = "older-image"))).map { it.msgID })
        assertEquals(listOf(listOf("image"), listOf("deleted-old-video", "older-image")), queried)
    }

    @Test fun `confirmed offline media markers override a stale cached page`() = runBlocking {
        SearchMessageVisibility.setProvider { _, messages -> messages }
        SearchMessageVisibility.setDeletedProvider { conversation, id -> conversation == "c2c_bob" && id == "old" }
        val page = listOf(MessageInfo(msgID = "old"), MessageInfo(msgID = "retained"))
        assertEquals(listOf("retained"), SearchMessageVisibility.authoritativeMessages("c2c_bob", page).map { it.msgID })
        assertEquals(listOf("old", "retained"), SearchMessageVisibility.authoritativeMessages("group_bob", page).map { it.msgID })
    }
    @Test fun `display provider is process scoped and fallback exists only while absent`() {
        val rows = listOf(MessageInfo(msgID = "deleted"), MessageInfo(msgID = "safe"))
        SearchMessageVisibility.setDeletedProvider { _, id -> id == "deleted" }
        assertFalse(SearchMessageVisibility.hasDisplayProvider)
        assertEquals(listOf("safe"), SearchMessageVisibility.mapForDisplay("group_g", rows) { it }.map { it.msgID })
        var batches = 0
        SearchMessageVisibility.setDisplayProvider { conversation, source, mapper ->
            batches++; assertTrue(conversation in setOf("group_one", "group_two")); mapper(source.filterNot { it.msgID == "deleted" })
        }
        assertTrue(SearchMessageVisibility.hasDisplayProvider)
        assertEquals(1, SearchMessageVisibility.mapForDisplay("group_one", rows) { it }.size)
        assertEquals(1, SearchMessageVisibility.mapForDisplay("group_two", rows) { it }.size)
        assertEquals(2, batches)
        SearchMessageVisibility.setDisplayProvider { _, _, _ -> throw IllegalStateException("snapshot unavailable") }
        assertTrue(SearchMessageVisibility.mapForDisplay("group_g", rows) { fail("No failed provider fallback"); it }.isEmpty())
        assertTrue(SearchMessageVisibility.hasDisplayProvider)
        SearchMessageVisibility.setDisplayProvider(null)
        assertEquals(listOf("safe"), SearchMessageVisibility.mapForDisplay("group_g", rows) { it }.map { it.msgID })
    }
    @Test fun `batch display still executes configured custom exclusions through real mapper policy`() {
        val config = io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig()
        config.addMessageExclusion(io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageMatcher { it.msgID == "custom-hidden" })
        val policy = io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListVisibilityPolicy(config)
        val mapper = io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListDisplayMapper(shouldDisplayMessage = policy::shouldDisplay)
        SearchMessageVisibility.setDisplayProvider { _, rows, render -> render(rows.filterNot { it.msgID == "deleted" }) }
        val rows = listOf("first", "custom-hidden", "deleted", "last", "last").map { MessageInfo(msgID = it) }
        assertEquals(listOf("last", "first"), SearchMessageVisibility.mapForDisplay("group_g", rows, mapper::map).map { it.msgID })
        assertEquals(5, rows.size)
    }
}
