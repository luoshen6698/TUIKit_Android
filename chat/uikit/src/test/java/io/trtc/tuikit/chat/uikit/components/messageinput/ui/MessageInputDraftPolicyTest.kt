package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import org.junit.Assert.*
import org.junit.Test

class MessageInputDraftPolicyTest {
    @Test fun `initial empty untouched composer accepts an asynchronously loaded draft`() {
        val policy = MessageInputDraftPolicy()
        assertFalse(policy.shouldApplyIncomingDraft("", null, false))
        assertTrue(policy.shouldApplyIncomingDraft("", "saved draft", false))
    }

    @Test fun `manual clear rejects stored draft replay on conversation updates`() {
        val policy = MessageInputDraftPolicy()
        assertTrue(policy.shouldApplyIncomingDraft("", "saved draft", false))
        repeat(3) { assertFalse(policy.shouldApplyIncomingDraft("", "saved draft", true)) }
        assertTrue(policy.shouldSaveDraft("", true))
    }

    @Test fun `empty stored draft acknowledgement does not undo a local clear`() {
        val policy = MessageInputDraftPolicy()
        assertFalse(policy.shouldApplyIncomingDraft("", null, true))
        assertFalse(policy.shouldApplyIncomingDraft("", "stale draft", true))
    }

    @Test fun `incoming stored draft never replaces nonempty local text`() {
        val policy = MessageInputDraftPolicy()
        assertFalse(policy.shouldApplyIncomingDraft("local draft", "stored draft", false))
        assertFalse(policy.shouldApplyIncomingDraft("local edit", "stored draft", true))
    }

    @Test fun `submitted draft stays suppressed until clear acknowledgement and new setup resets policy`() {
        val policy = MessageInputDraftPolicy()
        policy.markDraftClearPending()
        assertFalse(policy.shouldApplyIncomingDraft("", "old submitted draft", false))
        assertFalse(policy.shouldApplyIncomingDraft("", null, false))
        assertTrue(policy.shouldApplyIncomingDraft("", "new stored draft", false))
        policy.markDraftClearPending()
        policy.reset()
        assertTrue(policy.shouldApplyIncomingDraft("", "other conversation draft", false))
        assertFalse(policy.shouldSaveDraft("", false))
        assertTrue(policy.shouldSaveDraft("nonempty draft", false))
    }
}
