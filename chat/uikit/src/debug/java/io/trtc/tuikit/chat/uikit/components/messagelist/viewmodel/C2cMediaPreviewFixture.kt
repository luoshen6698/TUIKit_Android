package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.ImageMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageLoadDirection
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import kotlinx.coroutines.CoroutineScope
import java.io.File

/** Debug-only local media source; the controller and authoritative filter are production code. */
object C2cMediaPreviewFixture {
    fun open(context: Context, scope: CoroutineScope, conversationID: String): () -> Unit {
        val messages = (1..2).map { index ->
            val file = File(context.cacheDir, "c2c-deletion-fixture-$index.png")
            val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(if (index == 1) Color.BLUE else Color.GREEN)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            MessageInfo(msgID = "fixture-msg-$index", status = MessageStatus.SEND_SUCCESS,
                messageType = MessageType.IMAGE,
                messagePayload = ImageMessagePayload(originalImagePath = file.absolutePath,
                    originalImageWidth = 320, originalImageHeight = 320))
        }
        val source = object : MessageMediaPreviewMessageSource {
            override fun loadInitialMediaMessages(anchorMessage: MessageInfo, completion: (List<MessageInfo>) -> Unit) = completion(messages)
            override fun loadMoreMediaMessages(direction: MessageLoadDirection, completion: (List<MessageInfo>) -> Unit) = completion(messages)
            override fun currentMediaMessages() = messages
        }
        val controller = MessageMediaPreviewController(scope, source,
            downloadResource = { _, _, completion: CompletionHandler -> completion.onFailure(-1, "Fixture has no remote media") },
            reverseMessageOrderForPreview = false, conversationID = conversationID)
        controller.showImage(context, messages.last())
        return controller::clear
    }
}
