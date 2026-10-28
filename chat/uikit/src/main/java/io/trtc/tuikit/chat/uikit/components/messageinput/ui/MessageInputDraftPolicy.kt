package io.trtc.tuikit.chat.uikit.components.messageinput.ui

internal class MessageInputDraftPolicy {
    private var isDraftClearPending = false

    fun reset() {
        isDraftClearPending = false
    }

    fun markDraftClearPending() {
        isDraftClearPending = true
    }

    fun shouldApplyIncomingDraft(currentInput: String, draft: String?, hasUserEditedText: Boolean): Boolean {
        if (draft.isNullOrEmpty()) {
            isDraftClearPending = false
            return false
        }
        if (isDraftClearPending) {
            return false
        }
        // An empty field can be an intentional local edit, not a request to restore.
        return currentInput.isEmpty() && !hasUserEditedText
    }

    fun shouldSaveDraft(currentInput: String, hasUserEditedText: Boolean): Boolean {
        return currentInput.isNotEmpty() || hasUserEditedText
    }
}
