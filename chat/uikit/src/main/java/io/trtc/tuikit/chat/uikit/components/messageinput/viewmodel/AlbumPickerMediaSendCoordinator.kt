package io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel
import io.trtc.tuikit.atomicx.albumpicker.AlbumMedia
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerListener
import kotlin.math.roundToInt

internal class AlbumPickerMediaSendCoordinator(
    private val onProcessingStarted: (AlbumMedia) -> Unit,
    private val onProcessingProgress: (AlbumMedia, Int) -> Unit,
    private val onProcessingFinished: (AlbumMedia) -> Unit,
    private val onSendProcessedMedia: (AlbumMedia, String) -> Unit,
    private val onSendOriginalMedia: (AlbumMedia) -> Unit,
    private val onSendText: (String) -> Unit,
    private val shouldProcessMedia: (AlbumMedia) -> Boolean = { true },
    private val onMediaRejected: (AlbumMedia) -> Unit = {}
) : AlbumPickerListener {
    private val pendingMedia = linkedMapOf<ULong, AlbumMedia>()
    private var selectionConfirmed = false
    private var closed = false
    private var pendingText: String? = null

    @Synchronized
    override fun onPickConfirm(
        pickedAlbumMedias: List<AlbumMedia>,
        textMessage: String?
    ) {
        if (closed || selectionConfirmed) return
        selectionConfirmed = true
        pendingText = textMessage
        pickedAlbumMedias.distinctBy { it.id }.forEach { media ->
            if (shouldProcessMedia(media)) {
                pendingMedia[media.id] = media
                onProcessingStarted(media)
            } else {
                onMediaRejected(media)
            }
        }
    }

    @Synchronized
    override fun onMediaProcessing(
        albumMedia: AlbumMedia,
        progress: Float,
        error: Boolean
    ) {
        // Completed/cancelled items must not be reinserted by delayed progress callbacks.
        if (closed || albumMedia.id !in pendingMedia) return
        pendingMedia[albumMedia.id] = albumMedia
        if (error) {
            finishMedia(albumMedia, useOriginal = true)
            return
        }

        if (progress < COMPLETED_PROGRESS) {
            onProcessingProgress(albumMedia, progress.toPercent())
            return
        }

        finishMedia(albumMedia)
    }

    @Synchronized
    override fun onMediaProcessed() {
        if (closed) return
        closed = true
        // The batch is terminal even when an individual 100% callback was not delivered.
        pendingMedia.values.toList().forEach { finishMedia(it) }
        val text = pendingText
        pendingText = null
        if (!text.isNullOrEmpty()) {
            onSendText(text)
        }
    }

    @Synchronized
    override fun onCancel() {
        if (closed) return
        closed = true
        val cancelledMedia = pendingMedia.values.toList()
        pendingMedia.clear()
        pendingText = null
        cancelledMedia.forEach(onProcessingFinished)
    }

    private fun finishMedia(albumMedia: AlbumMedia, useOriginal: Boolean = false) {
        if (pendingMedia.remove(albumMedia.id) == null) return
        onProcessingFinished(albumMedia)
        val path = albumMedia.mediaPath
        if (useOriginal || path.isNullOrBlank()) {
            onSendOriginalMedia(albumMedia)
        } else {
            onSendProcessedMedia(albumMedia, path)
        }
    }

    private fun Float.toPercent(): Int {
        return (coerceIn(0f, COMPLETED_PROGRESS) * 100).roundToInt()
    }

    private companion object {
        const val COMPLETED_PROGRESS = 1.0f
    }
}
