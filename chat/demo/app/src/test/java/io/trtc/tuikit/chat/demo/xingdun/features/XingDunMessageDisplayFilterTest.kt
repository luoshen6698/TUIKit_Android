package io.trtc.tuikit.chat.demo.xingdun.features

import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageSenderInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.TextMessagePayload
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListDisplayMapper
import io.trtc.tuikit.chat.uikit.components.search.viewmodel.SearchMessageVisibility
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class XingDunMessageDisplayFilterTest {
    @After fun restore() { SearchMessageVisibility.setDisplayProvider(null) }

    private class Fixture {
        var scope: XingDunMessageDisplayScope? = XingDunMessageDisplayScope("tenant-api-self", 1, "self")
        var revision = 0L
        var identityReads = 0
        var durableReads = 0
        val state = XingDunGroupDeletionVisibilityState()
        val deleted = mutableMapOf<String, Set<String>>()
        val filter = XingDunMessageDisplayFilter(
            { identityReads++; scope },
            { conversation, owner -> durableReads++; deleted["${owner.account}:$conversation"].orEmpty() },
            state, { revision },
        )
        fun install() { SearchMessageVisibility.setDisplayProvider(filter::map) }
        fun checked(conversation: String, vararg ids: String) { state.checked(scope!!.groupKey(conversation), ids.toSet()) }
        fun render(conversation: String, messages: List<MessageInfo>, mapper: (List<MessageInfo>) -> List<MessageInfo> = { it }): List<MessageInfo> {
            install()
            return SearchMessageVisibility.mapForDisplay(conversation, messages, mapper)
        }
    }
    private fun row(id: String, self: Boolean = false, status: MessageStatus = MessageStatus.SEND_SUCCESS, sender: String = if (self) "self" else "peer") =
        MessageInfo(msgID = id, from = MessageSenderInfo(userID = sender), isSentBySelf = self, status = status)

    private fun constantReads(size: Int) {
        val f = Fixture(); val rows = (1..size).map { row("M$it") }
        f.checked("group_g", *rows.map { it.msgID }.toTypedArray())
        val actual = f.render("group_g", rows, MessageListDisplayMapper()::map)
        assertEquals(rows.map { it.msgID }.reversed(), actual.map { it.msgID })
        assertEquals("One initial scope and one publication check, independent of row count", 2, f.identityReads)
        assertEquals("One durable set per batch", 1, f.durableReads)
    }
    @Test fun `real display entry reads one scope and deletion set for twenty rows`() { constantReads(20) }
    @Test fun `real display entry keeps the same read bound for one hundred rows`() { constantReads(100) }

    @Test fun `group unknown pending and durable deleted rows never receive a sending exemption`() {
        val f = Fixture(); f.checked("group_g", "safe", "hinted")
        f.state.hold(f.scope!!.groupKey("group_g"), setOf("hinted"))
        f.deleted["tenant-api-self:group_g"] = setOf("deleted")
        val rows = listOf(row("safe"), row("unknown"), row("hinted", true, MessageStatus.SENDING), row("deleted", true, MessageStatus.SENDING))
        assertEquals(listOf("safe"), f.render("group_g", rows).map { it.msgID })
        assertFalse(f.state.canDisplay(f.scope!!.groupKey("group_g"), "deleted", false, false, false))
        assertTrue(f.render("group_other", listOf(row("safe"))).isEmpty())
    }

    @Test fun `own sending survives success and failure removes exemption without authorizing imported history`() {
        val f = Fixture()
        assertEquals(1, f.render("group_g", listOf(row("local", true, MessageStatus.SENDING))).size)
        assertEquals(1, f.render("group_g", listOf(row("local", true))).size)
        assertTrue(f.render("group_g", listOf(row("imported", true))).isEmpty())
        assertEquals(1, f.render("group_g", listOf(row("local", true, MessageStatus.SEND_FAIL))).size)
        assertTrue(f.render("group_g", listOf(row("local", true))).isEmpty())
        assertTrue(f.render("group_g", listOf(row("impostor", true, MessageStatus.SENDING, "other"))).isEmpty())
    }

    @Test fun `group quotes require checked authority while C2C unknown quotes remain and tombstones redact copies`() {
        val f = Fixture(); f.checked("group_g", "reply")
        val reply = row("reply").copy(quoteInfo = MessageQuoteInfo(msgID = "target", messagePayload = TextMessagePayload(text = "original quote")))
        val group = f.render("group_g", listOf(reply)).single()
        assertEquals(MessageStatus.DELETED, group.quoteInfo?.status); assertNull(group.quoteInfo?.messagePayload)
        assertEquals("original quote", (reply.quoteInfo?.messagePayload as TextMessagePayload).text)
        assertSame(reply, f.render("c2c_peer", listOf(reply)).single())
        f.deleted["tenant-api-self:c2c_peer"] = setOf("target", "deleted")
        val c2c = f.render("c2c_peer", listOf(reply, row("deleted"))).single()
        assertEquals("reply", c2c.msgID); assertNull(c2c.quoteInfo?.messagePayload)
        f.checked("group_g", "target")
        assertSame(reply, f.render("group_g", listOf(reply)).single())
    }

    @Test fun `account generation and deletion revision changes inside actual mapper discard whole old publication`() {
        for (change in listOf<(Fixture) -> Unit>(
            { it.scope = it.scope!!.copy(account = "other-tenant-self") },
            { it.scope = it.scope!!.copy(generation = 2) },
            { it.scope = null },
            { it.revision++ },
        )) {
            val f = Fixture(); f.checked("group_g", "safe")
            assertTrue(f.render("group_g", listOf(row("safe"))) { rows -> change(f); MessageListDisplayMapper().map(rows) }.isEmpty())
        }
        val sending = Fixture()
        assertTrue(sending.render("group_g", listOf(row("local", true, MessageStatus.SENDING))) { rows ->
            sending.scope = sending.scope!!.copy(generation = 2); rows
        }.isEmpty())
        assertTrue("Old generation Sending exemption cannot authorize its replacement", sending.render("group_g", listOf(row("local", true))).isEmpty())
        val f = Fixture(); f.scope = null
        assertTrue(f.render("c2c_peer", listOf(row("safe"))).isEmpty()); assertEquals(0, f.durableReads)
    }

    @Test fun `real mapper keeps custom exclusions order dedup and independent SDK copies`() {
        val f = Fixture(); val seen = mutableListOf<String>()
        val mapper = MessageListDisplayMapper(shouldDisplayMessage = { seen += it.msgID; it.msgID != "custom-hidden" })
        val first = row("first", true, MessageStatus.SENDING)
        val rows = listOf(first, row("custom-hidden"), row("last"), row("last"))
        val actual = f.render("c2c_peer", rows, mapper::map)
        assertEquals(listOf("last", "first"), actual.map { it.msgID })
        assertTrue("custom exclusion still runs", "custom-hidden" in seen)
        first.status = MessageStatus.SEND_SUCCESS
        assertEquals(MessageStatus.SENDING, actual.last().status)
    }

    @Test fun `fresh batches read new deletion sets and cannot reuse another scope tombstones`() {
        val f = Fixture(); val rows = listOf(row("one"), row("two"))
        assertEquals(2, f.render("c2c_peer", rows).size)
        f.deleted["tenant-api-self:c2c_peer"] = setOf("one"); f.revision++
        assertEquals(listOf("two"), f.render("c2c_peer", rows).map { it.msgID })
        f.scope = f.scope!!.copy(account = "other-tenant-self", generation = 2)
        assertEquals(2, f.render("c2c_peer", rows).size)
    }
}
