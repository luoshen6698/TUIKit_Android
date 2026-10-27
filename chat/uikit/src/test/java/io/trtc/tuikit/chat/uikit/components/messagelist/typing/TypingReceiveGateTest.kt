package io.trtc.tuikit.chat.uikit.components.messagelist.typing

import org.junit.Assert.*
import org.junit.Test

class TypingReceiveGateTest {
    private val identity = "123:owner"
    private fun newGate() = TypingReceiveGate("peer", identity)
    private fun raw(gate: TypingReceiveGate, status: Boolean, sender: String? = "peer",
                    group: String? = null, self: Boolean = false,
                    current: String = identity, loggedIn: Boolean = true): Boolean? =
        gate.rawStatus(TypingMessageProtocol.buildTypingMessageData(status), sender, group,
            self, current, loggedIn)

    @Test fun `legal raw start and end qualify without a Store event`() {
        val gate = newGate()
        assertEquals(true, raw(gate, true))
        assertEquals(false, raw(gate, false, group = ""))
    }

    @Test fun `other peer group self logout and account replacement controls are ignored`() {
        val gate = newGate()
        listOf(true, false).forEach { status ->
            assertNull(raw(gate, status, sender = "other"))
            assertNull(raw(gate, status, sender = null))
            assertNull(raw(gate, status, group = "group"))
            assertNull(raw(gate, status, self = true))
            assertNull(raw(gate, status, current = "123:next-owner"))
            assertNull(raw(gate, status, current = "456:owner"))
            assertNull(raw(gate, status, loggedIn = false))
        }
    }

    @Test fun `invalid raw controls do not qualify as peer activity`() {
        val gate = newGate()
        listOf("{}", "not json", """{"businessID":"other","typingStatus":1}""",
            """{"businessID":"user_typing_status","typingStatus":2}""").forEach { data ->
            assertNull(gate.rawStatus(data, "peer", null, false, identity, true))
        }
    }

    @Test fun `late Store start and end cannot qualify for state or activity`() {
        val gate = newGate()
        assertEquals(true, raw(gate, true))
        assertEquals(false, raw(gate, false))
        listOf(true, false, true).forEach { lateStatus ->
            val parsed = TypingMessageProtocol.parseTypingStatus(
                TypingMessageProtocol.buildTypingMessageData(lateStatus))
            // Both Store event and Store list use this gate. A late control is
            // ineligible even when its timestamp is newer than ordinary messages.
            assertFalse(gate.acceptsStoreActivity("peer", false, parsed, identity, true))
        }
        assertTrue(gate.acceptsStoreActivity("peer", false, null, identity, true))
    }

    @Test fun `ordinary Store activity still requires the same peer and account`() {
        val gate = newGate()
        assertFalse(gate.acceptsStoreActivity("other", false, null, identity, true))
        assertFalse(gate.acceptsStoreActivity("peer", true, null, identity, true))
        assertFalse(gate.acceptsStoreActivity("peer", false, null, "123:next-owner", true))
        assertFalse(gate.acceptsStoreActivity("peer", false, null, identity, false))
        assertTrue(gate.acceptsStoreActivity("peer", false, null, identity, true))
    }

    @Test fun `close is terminal for already accepted raw work and later Store activity`() {
        val gate = newGate()
        assertEquals(true, raw(gate, true))
        assertTrue(gate.isCurrent(identity, true))
        gate.close()
        // The Main dispatch checks isCurrent again before publishing accepted work.
        assertFalse(gate.isCurrent(identity, true))
        assertNull(raw(gate, true))
        assertNull(raw(gate, false))
        assertFalse(gate.acceptsStoreActivity("peer", false, null, identity, true))
        gate.close()
        assertFalse(gate.isCurrent(identity, true))
        assertEquals(true, raw(newGate(), true))
    }

    @Test fun `unknown owner or non C2C peer cannot receive controls`() {
        assertNull(raw(TypingReceiveGate("peer", "123:"), true, current = "123:"))
        assertNull(raw(TypingReceiveGate(null, identity), true))
        assertNull(raw(TypingReceiveGate("", identity), true))
    }
}
