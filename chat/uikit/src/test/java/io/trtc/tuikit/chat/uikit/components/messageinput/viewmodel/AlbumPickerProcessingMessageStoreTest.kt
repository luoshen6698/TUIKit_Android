package io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel

import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumPickerProcessingMessageStoreTest {
    @Test fun processingVideoDoesNotMoveBelowLaterEmojiMessages() {
        val messages = listOf(message("old", 10), message("emoji", 30))
        val processing = listOf(message("processing", 20))
        assertEquals(listOf("old", "processing", "emoji"),
            AlbumPickerProcessingMessageStore.mergeWithMessages(messages, processing).map { it.msgID })
    }

    @Test fun sdkOrderingAndSameSecondMessagesArePreserved() {
        val messages = listOf(message("first", 10), message("emoji", 20), message("last", 20))
        val processing = listOf(message("processing", 20))
        assertEquals(listOf("first", "processing", "emoji", "last"),
            AlbumPickerProcessingMessageStore.mergeWithMessages(messages, processing).map { it.msgID })
        assertEquals(messages, AlbumPickerProcessingMessageStore.mergeWithMessages(messages, emptyList()))
    }

    private fun message(id: String, time: Long) = MessageInfo(msgID = id, timestamp = time)
}
