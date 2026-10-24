package io.trtc.tuikit.chat.uikit.components.search.viewmodel

import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload
import io.trtc.tuikit.atomicxcore.api.search.MessageSearchResultItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class SearchMessageVisibilityTest {
    @After fun restore() {
        SearchMessageVisibility.setProvider { _, messages -> messages }
        SearchMessageVisibility.setDeletedProvider { _, _ -> false }
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

    @Test fun `group quotes and unknown C2C targets are not redacted`() {
        val reply = MessageInfo(msgID = "reply", quoteInfo = MessageQuoteInfo(msgID = "target",
            messagePayload = TextMessagePayload(text = "original")))
        SearchMessageVisibility.setDeletedProvider { _, _ -> true }
        assertSame(reply, SearchMessageVisibility.redactQuotes("group_bob", listOf(reply)).single())
        SearchMessageVisibility.setDeletedProvider { _, _ -> false }
        assertSame(reply, SearchMessageVisibility.redactQuotes("c2c_bob", listOf(reply)).single())
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
}
