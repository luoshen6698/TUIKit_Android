package io.trtc.tuikit.chat.demo.xingdun.features

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunPinnedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunPinnedMessagePolicyTest {
    @Test fun `pin permissions match iOS and server policy`() {
        assertTrue(XingDunPinnedMessagePolicy.canManage("member", false, 1))
        assertTrue(XingDunPinnedMessagePolicy.canManage("owner", false, 2))
        assertTrue(XingDunPinnedMessagePolicy.canManage("administrator", false, 2))
        assertTrue(XingDunPinnedMessagePolicy.canManage("member", true, 2))
        assertFalse(XingDunPinnedMessagePolicy.canManage("member", false, 2))
    }

    @Test fun `only active valid pins are displayed`() {
        val active = XingDunPinnedMessage(messageId = "m1", isPinned = true)
        val inactive = XingDunPinnedMessage(messageId = "m2", isPinned = false)
        assertEquals(listOf(active), XingDunPinnedMessagePolicy.visiblePins(listOf(active, inactive)))
    }

    @Test fun `older control message cannot replace newer pin state`() {
        assertTrue(XingDunPinnedMessagePolicy.shouldApply(null, 1))
        assertTrue(XingDunPinnedMessagePolicy.shouldApply(3, 3))
        assertFalse(XingDunPinnedMessagePolicy.shouldApply(4, 3))
    }

    @Test fun `read token is version aware so repinned server message becomes visible`() {
        assertEquals(
            "m1#4",
            XingDunPinnedMessagePolicy.readToken(XingDunPinnedMessage(messageId = "m1", version = 4)),
        )
        assertEquals(
            "m1#5",
            XingDunPinnedMessagePolicy.readToken(XingDunPinnedMessage(messageId = "m1", version = 5)),
        )
    }
}
