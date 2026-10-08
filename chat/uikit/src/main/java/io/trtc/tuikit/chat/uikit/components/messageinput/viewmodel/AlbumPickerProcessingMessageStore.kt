package io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel
import io.trtc.tuikit.atomicx.albumpicker.AlbumMedia
import io.trtc.tuikit.atomicx.albumpicker.AlbumMediaType
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.message.ImageMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageSenderInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.VideoMessagePayload
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

internal object AlbumPickerProcessingMessageStore {
    private val _messagesByConversation =
        MutableStateFlow<Map<String, List<MessageInfo>>>(emptyMap())

    val messagesByConversation: StateFlow<Map<String, List<MessageInfo>>> =
        _messagesByConversation.asStateFlow()

    fun upsert(conversationID: String, media: AlbumMedia, progress: Int) {
        val message = media.toProcessingMessage(conversationID, progress)
        _messagesByConversation.update { current ->
            val previousMessages = current[conversationID].orEmpty()
            val previous = previousMessages.firstOrNull { it.msgID == message.msgID }
            val updatedMessages = if (previous == null) {
                previousMessages + message
            } else {
                previousMessages.map {
                    if (it.msgID == message.msgID) message.copy(timestamp = previous.timestamp) else it
                }
            }
            current + (conversationID to updatedMessages)
        }
    }

    fun mergeWithMessages(messages: List<MessageInfo>, processing: List<MessageInfo>): List<MessageInfo> {
        if (processing.isEmpty()) return messages
        // Preserve SDK order; insert temporary items at their original selection time.
        val merged = messages.toMutableList()
        processing.sortedBy { it.timestamp }.asReversed().forEach { message ->
            val index = merged.indexOfFirst { (it.timestamp ?: 0L) >= (message.timestamp ?: 0L) }
            if (index < 0) merged.add(message) else merged.add(index, message)
        }
        return merged
    }

    fun remove(conversationID: String, mediaId: ULong) {
        val messageId = processingMessageId(mediaId)
        _messagesByConversation.update { current ->
            val updatedMessages = current[conversationID]
                .orEmpty()
                .filterNot { it.msgID == messageId }
            if (updatedMessages.isEmpty()) {
                current - conversationID
            } else {
                current + (conversationID to updatedMessages)
            }
        }
    }

    private fun AlbumMedia.toProcessingMessage(
        conversationID: String,
        progress: Int
    ): MessageInfo {
        val isGroup = ConversationIDUtil.isGroup(conversationID)
        val targetId = if (isGroup) {
            ConversationIDUtil.groupId(conversationID)
        } else {
            conversationID.removePrefix(ConversationIDUtil.C2C_PREFIX)
        }
        val loginUserInfo = LoginStore.shared.loginState.loginUserInfo.value
        return MessageInfo(
            msgID = processingMessageId(id),
            status = MessageStatus.SENDING,
            timestamp = System.currentTimeMillis() / 1000,
            from = MessageSenderInfo(
                userID = loginUserInfo?.userID.orEmpty(),
                avatarURL = loginUserInfo?.avatarURL,
                nickname = loginUserInfo?.nickname
            ),
            to = targetId,
            isSentBySelf = true,
            conversationType = if (isGroup) ConversationType.GROUP else ConversationType.C2C,
            messageType = if (mediaType == AlbumMediaType.VIDEO) MessageType.VIDEO else MessageType.IMAGE,
            messagePayload = toProcessingPayload(),
            uploadMediaProgress = progress.coerceIn(0, 100)
        )
    }

    private fun AlbumMedia.toProcessingPayload() =
        if (mediaType == AlbumMediaType.VIDEO) {
            VideoMessagePayload(
                videoSnapshotPath = videoThumbnailPath ?: mediaPath ?: uri?.toString(),
                videoType = "mp4",
                videoDuration = (duration / 1000f).roundToInt(),
                videoPath = mediaPath ?: uri?.toString()
            )
        } else {
            ImageMessagePayload(
                originalImagePath = mediaPath ?: uri?.toString()
            )
        }

    private fun processingMessageId(mediaId: ULong): String {
        return "$PROCESSING_MESSAGE_ID_PREFIX$mediaId"
    }

    private const val PROCESSING_MESSAGE_ID_PREFIX = "album_picker_processing_"
}
