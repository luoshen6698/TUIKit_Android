package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel

import org.junit.Assert.*
import org.junit.Test

class MessageMediaDeletionGateTest {
    @Test fun `deleted pending anchor rejects a late initial page after closing`() {
        val gate = MessageMediaDeletionGate()
        val pending = gate.begin("old-image")
        assertTrue(gate.shouldClose { it == "old-image" })
        gate.invalidate()
        assertFalse(gate.isCurrent(pending))
    }

    @Test fun `an open viewer closes for a deleted cached image or video even outside current timeline`() {
        val gate = MessageMediaDeletionGate()
        val token = gate.begin("image")
        gate.bind(token, setOf("image", "old-video"))
        assertFalse(gate.shouldClose { it == "unrelated" })
        assertTrue(gate.shouldClose { it == "old-video" })
        gate.invalidate()
        assertFalse(gate.isCurrent(token))
    }

    @Test fun `old boundary pages cannot bind into a newly opened viewer`() {
        val gate = MessageMediaDeletionGate()
        val old = gate.begin("first")
        val current = gate.begin("second")
        gate.bind(old, setOf("stale"))
        assertFalse(gate.shouldClose { it == "stale" })
        gate.bind(current, setOf("second"))
        assertTrue(gate.shouldClose { it == "second" })
        assertFalse(gate.isCurrent(old))
    }

    @Test fun `media SDK page and download tokens retain source login across account and relogin changes`() {
        var session: String? = "alice-login1"
        val gate = MessageMediaDeletionGate { session }
        val initialPage = gate.begin("image")
        gate.bind(initialPage, setOf("image", "video"))
        val download = gate.token()
        assertTrue(gate.isCurrent(download))
        session = "bob-login1"
        assertFalse(gate.isCurrent(initialPage))
        assertFalse(gate.isCurrent(download))
        session = "alice-login2"
        assertFalse(gate.isCurrent(initialPage))
        gate.bind(initialPage, setOf("stale"))
        assertFalse(gate.shouldClose { it == "stale" })
        val newPage = gate.begin("new-image")
        assertTrue(gate.isCurrent(newPage))
        assertFalse(gate.isCurrent(initialPage))
        session = null
        assertFalse(gate.isCurrent(newPage))
    }

    @Test fun `group preview without authenticated source cannot adopt a later login`() {
        var session: String? = null
        val gate = MessageMediaDeletionGate { session }
        val old = gate.begin("old")
        assertFalse(gate.isCurrent(old))
        session = "alice-login1"
        assertFalse(gate.isCurrent(old))
        assertTrue(gate.isCurrent(gate.begin("new")))
    }
}
