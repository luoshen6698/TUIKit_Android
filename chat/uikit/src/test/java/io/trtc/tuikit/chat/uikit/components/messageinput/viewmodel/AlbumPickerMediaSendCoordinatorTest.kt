package io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel

import io.trtc.tuikit.atomicx.albumpicker.AlbumMedia
import io.trtc.tuikit.atomicx.albumpicker.AlbumMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumPickerMediaSendCoordinatorTest {
    private val media = AlbumMedia(id = 1uL, uri = null, mediaType = AlbumMediaType.VIDEO, mediaPath = "/video.mp4")
    private val events = mutableListOf<String>()
    private fun coordinator(accept: (AlbumMedia) -> Boolean = { true }) = AlbumPickerMediaSendCoordinator(
        onProcessingStarted = { events += "start:${it.id}" },
        onProcessingProgress = { item, _ -> events += "progress:${item.id}" },
        onProcessingFinished = { events += "finish:${it.id}" },
        onSendProcessedMedia = { item, _ -> events += "send:${item.id}" },
        onSendOriginalMedia = { events += "original:${it.id}" },
        onSendText = { events += "text:$it" },
        shouldProcessMedia = accept,
        onMediaRejected = { events += "reject:${it.id}" }
    )

    @Test fun completedMediaCannotRecreateProcessingBubbleWithLateProgress() {
        val sender = coordinator()
        sender.onPickConfirm(listOf(media), null)
        sender.onMediaProcessing(media, 1f, false)
        sender.onMediaProcessing(media, 0.9f, false)
        sender.onMediaProcessing(media, 1f, false)
        assertEquals(listOf("start:1", "finish:1", "send:1"), events)
    }

    @Test fun batchCompletionSendsPendingMediaAndClearsBubbleEvenWithoutFinalProgress() {
        val sender = coordinator()
        sender.onPickConfirm(listOf(media), "emoji")
        sender.onMediaProcessing(media, 0.9f, false)
        sender.onMediaProcessed()
        sender.onMediaProcessed()
        sender.onMediaProcessing(media, 0.8f, false)
        assertEquals(listOf("start:1", "progress:1", "finish:1", "send:1", "text:emoji"), events)
    }

    @Test fun cancelClearsPendingBubblesWithoutSendingAndIgnoresLateCallbacks() {
        val sender = coordinator()
        sender.onPickConfirm(listOf(media), "emoji")
        sender.onCancel()
        sender.onCancel()
        sender.onMediaProcessing(media, 1f, false)
        sender.onMediaProcessed()
        assertEquals(listOf("start:1", "finish:1"), events)
    }

    @Test fun cancelAfterOneCompletedItemOnlyClearsUnsentItems() {
        val sender = coordinator()
        val second = media.copy(id = 2uL)
        sender.onPickConfirm(listOf(media, second), null)
        sender.onMediaProcessing(media, 1f, false)
        sender.onCancel()
        assertEquals(listOf("start:1", "start:2", "finish:1", "send:1", "finish:2"), events)
    }

    @Test fun completionWithoutUsablePathFallsBackToOriginalExactlyOnce() {
        val sender = coordinator()
        val original = media.copy(mediaPath = null)
        sender.onPickConfirm(listOf(original), null)
        sender.onMediaProcessed()
        sender.onMediaProcessing(original, 1f, false)
        assertEquals(listOf("start:1", "finish:1", "original:1"), events)
    }

    @Test fun processingFailureFallsBackOnceAndCannotBeResurrected() {
        val sender = coordinator()
        sender.onPickConfirm(listOf(media), null)
        sender.onMediaProcessing(media, 0.5f, true)
        sender.onMediaProcessing(media, 0.8f, false)
        sender.onMediaProcessed()
        assertEquals(listOf("start:1", "finish:1", "original:1"), events)
    }

    @Test fun rejectedMediaIsNeverSentByBatchCompletion() {
        val sender = coordinator { false }
        sender.onPickConfirm(listOf(media), null)
        sender.onMediaProcessing(media, 1f, false)
        sender.onMediaProcessed()
        sender.onCancel()
        assertEquals(listOf("reject:1"), events)
    }

    @Test fun callbacksForUnselectedMediaDoNotCreateOrSendMessages() {
        val sender = coordinator()
        sender.onMediaProcessing(media, 0.1f, false)
        sender.onMediaProcessing(media, 1f, false)
        assertTrue(events.isEmpty())
    }

    @Test fun cancellationAfterSuccessfulBatchDoesNotUndoOrResendMessages() {
        val sender = coordinator()
        sender.onPickConfirm(listOf(media), "emoji")
        sender.onMediaProcessing(media, 1f, false)
        sender.onMediaProcessed()
        sender.onCancel()
        assertEquals(listOf("start:1", "finish:1", "send:1", "text:emoji"), events)
    }
}
