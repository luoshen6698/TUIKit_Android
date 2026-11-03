package io.trtc.tuikit.chat.uikit.components.messagelist.typing

import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMMessage
import com.tencent.imsdk.v2.V2TIMAdvancedMsgListener
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.atomic.AtomicLong

object TypingMessageProtocol {
    const val BUSINESS_ID = "user_typing_status"

    private const val USER_ACTION_TYPING = 14
    private const val ACTION_PARAM_TYPING_START = "EIMAMSG_InputStatus_Ing"
    private const val ACTION_PARAM_TYPING_END = "EIMAMSG_InputStatus_End"

    fun buildTypingMessageData(isTyping: Boolean): String {
        val json = JsonObject()
        json.addProperty("businessID", BUSINESS_ID)
        json.addProperty("typingStatus", if (isTyping) 1 else 0)
        json.addProperty("version", 0)
        json.addProperty("userAction", if (isTyping) USER_ACTION_TYPING else 0)
        json.addProperty("actionParam", if (isTyping) ACTION_PARAM_TYPING_START else ACTION_PARAM_TYPING_END)
        return json.toString()
    }

    fun isTypingMessage(message: MessageInfo): Boolean =
        message.messageType == MessageType.CUSTOM &&
            isTypingControlData((message.messagePayload as? CustomMessagePayload)?.customData)

    // Content filtering recognizes the envelope even when its state is invalid or newer.
    // Only parseTypingStatus may turn a control into a live typing update.
    fun isTypingControlData(data: String?): Boolean = runCatching {
        val root = JsonParser.parseString(data).asJsonObject
        fun matches(json: JsonObject): Boolean = json.get("businessID")?.let {
            it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString == BUSINESS_ID
        } == true
        if (matches(root)) true else root.get("data")?.let { nested ->
            when {
                nested.isJsonObject -> matches(nested.asJsonObject)
                nested.isJsonPrimitive && nested.asJsonPrimitive.isString ->
                    matches(JsonParser.parseString(nested.asString).asJsonObject)
                else -> false
            }
        } ?: false
    }.getOrDefault(false)

    fun parseTypingStatus(message: MessageInfo): Boolean? {
        if (message.messageType != MessageType.CUSTOM) return null
        val data = (message.messagePayload as? CustomMessagePayload)?.customData ?: return null
        return parseTypingStatus(data)
    }

    fun parseTypingStatus(data: String): Boolean? {
        return try {
            val json = JsonParser.parseString(data).asJsonObject
            val businessID = json.get("businessID")
            if (businessID?.isJsonPrimitive != true || !businessID.asJsonPrimitive.isString || businessID.asString != BUSINESS_ID) return null
            if (json.has("version")) {
                val version = json.get("version")
                if (!version.isJsonPrimitive || !version.asJsonPrimitive.isNumber || version.asDouble != 0.0) return null
            }
            if (json.has("typingStatus")) {
                val value = json.get("typingStatus")
                if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
                when (value.asDouble) { 1.0 -> true; 0.0 -> false; else -> null }
            } else {
                when (json.get("actionParam")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString) {
                    ACTION_PARAM_TYPING_START -> true
                    ACTION_PARAM_TYPING_END -> false
                    else -> null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

// Kept independent of SDK callbacks so the same source/lifetime checks guard
// receipt and the later Main dispatch. A closed controller cannot accept work again.
internal class TypingReceiveGate(private val peerUserID: String?, private val loginIdentity: String) {
    @Volatile private var closed = false

    fun isCurrent(identity: String, loggedIn: Boolean): Boolean =
        !closed && !peerUserID.isNullOrBlank() && loginIdentity.substringAfter(':', "").isNotBlank() &&
            loggedIn && identity == loginIdentity

    fun rawStatus(data: String, sender: String?, groupID: String?, self: Boolean,
                  identity: String, loggedIn: Boolean): Boolean? {
        if (!isCurrent(identity, loggedIn) || self || sender != peerUserID || !groupID.isNullOrEmpty()) return null
        return TypingMessageProtocol.parseTypingStatus(data)
    }

    fun close() { closed = true }
}

internal class TypingIndicatorController private constructor(
    private val conversationID: String,
    private val runtime: Runtime,
    private val mainDispatcher: MainCoroutineDispatcher,
    private val nowMillis: () -> Long
) {
    // External SDK I/O only. Timing, parsing, state and ownership remain in this controller.
    internal interface Runtime {
        fun currentIdentity(): String
        fun isLoggedIn(): Boolean
        fun bind(
            onRaw: (String, String?, String?, Boolean) -> Unit
        ): () -> Unit
        fun send(peerUserID: String, data: String)
    }

    private class SdkRuntime : Runtime {
        override fun currentIdentity(): String =
            "${io.trtc.tuikit.atomicxcore.api.login.LoginStore.shared.sdkAppID}:${V2TIMManager.getInstance().loginUser.orEmpty()}"

        override fun isLoggedIn(): Boolean =
            V2TIMManager.getInstance().loginStatus == V2TIMManager.V2TIM_STATUS_LOGINED

        override fun bind(
            onRaw: (String, String?, String?, Boolean) -> Unit
        ): () -> Unit {
            val rawTypingListener = object : V2TIMAdvancedMsgListener() {
                override fun onRecvNewMessage(message: V2TIMMessage?) {
                    val incoming = message ?: return
                    if (incoming.elemType != V2TIMMessage.V2TIM_ELEM_TYPE_CUSTOM) return
                    val data = incoming.customElem?.data ?: return
                    onRaw(data.toString(Charsets.UTF_8), incoming.sender, incoming.groupID, incoming.isSelf)
                }
            }
            V2TIMManager.getMessageManager().addAdvancedMsgListener(rawTypingListener)
            return { V2TIMManager.getMessageManager().removeAdvancedMsgListener(rawTypingListener) }
        }

        override fun send(peerUserID: String, data: String) {
            val message = V2TIMManager.getMessageManager().createCustomMessage(data.toByteArray(Charsets.UTF_8))
            message.isExcludedFromUnreadCount = true
            message.isExcludedFromLastMessage = true
            // SDK transport only; never send through a business Outbox or generate offline push.
            V2TIMManager.getMessageManager().sendMessage(message, peerUserID, null,
                V2TIMMessage.V2TIM_PRIORITY_NORMAL, true, null, null)
        }
    }

    companion object {
        private const val TYPING_SEND_INTERVAL_MS = 4_000L
        private const val TYPING_DISPLAY_TIMEOUT_MS = 5_000L

        private val controllers = HashMap<String, TypingIndicatorController>()

        @Synchronized
        fun obtain(conversationID: String): TypingIndicatorController =
            obtain(conversationID, SdkRuntime(), Dispatchers.Main, System::currentTimeMillis)

        @Synchronized
        internal fun obtain(
            conversationID: String,
            runtime: Runtime,
            mainDispatcher: MainCoroutineDispatcher,
            nowMillis: () -> Long
        ): TypingIndicatorController {
            val controller = controllers.getOrPut(conversationID) {
                TypingIndicatorController(conversationID, runtime, mainDispatcher, nowMillis)
            }
            controller.retainCount++
            return controller
        }

        @Synchronized
        fun release(controller: TypingIndicatorController) {
            controller.retainCount--
            if (controller.retainCount <= 0) {
                if (controllers[controller.conversationID] === controller) controllers.remove(controller.conversationID)
                controller.destroy()
            }
        }

        @Synchronized
        fun resetAll() {
            controllers.values.toList().forEach { it.destroy() }
            controllers.clear()
        }
    }

    private val peerUserID: String? = ConversationIDUtil.userIdOrNull(conversationID)

    private val _typingState = MutableStateFlow(false)
    val typingState: StateFlow<Boolean> = _typingState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private var typingTimeoutJob: Job? = null

    @Volatile
    private var retainCount = 0

    @Volatile
    private var lastTypingSendTimeMs = 0L

    @Volatile
    private var hasSentTypingStart = false
    @Volatile
    private var destroyed = false

    private val displayWorkRevision = AtomicLong()
    private val loginIdentity = runtime.currentIdentity()
    private val receiveGate = TypingReceiveGate(peerUserID, loginIdentity)
    private val unbind: () -> Unit = if (peerUserID != null) {
        runtime.bind(::handleRawMessage)
    } else {
        {}
    }

    private fun currentLoginIdentity(): String = runtime.currentIdentity()

    private fun isLoggedIn(): Boolean = runtime.isLoggedIn()

    private fun handleRawMessage(data: String, sender: String?, groupID: String?, self: Boolean) {
        val workRevision = displayWorkRevision.get()
        if (destroyed) return
        val status = receiveGate.rawStatus(data, sender, groupID, self,
            currentLoginIdentity(), isLoggedIn()) ?: return
        // Transient controls must not wait for AtomicX's history/list pipeline.
        scope.launch(mainDispatcher.immediate) {
            if (destroyed || workRevision != displayWorkRevision.get() ||
                !receiveGate.isCurrent(currentLoginIdentity(), isLoggedIn())) return@launch
            updatePeerTyping(status)
        }
    }

    private fun updatePeerTyping(isTyping: Boolean) {
        typingTimeoutJob?.cancel()
        typingTimeoutJob = null
        if (isTyping) {
            _typingState.value = true
            typingTimeoutJob = scope.launch(mainDispatcher.immediate) {
                delay(TYPING_DISPLAY_TIMEOUT_MS)
                _typingState.value = false
            }
        } else {
            _typingState.value = false
        }
    }

    fun sendTypingStatus(isTyping: Boolean) {
        if (destroyed) return
        if (peerUserID == null) return
        if (!receiveGate.isCurrent(currentLoginIdentity(), isLoggedIn())) return
        if (isTyping) {
            val now = nowMillis()
            if (now - lastTypingSendTimeMs < TYPING_SEND_INTERVAL_MS) return
            lastTypingSendTimeMs = now
        } else if (!hasSentTypingStart) {
            return
        }
        hasSentTypingStart = isTyping
        dispatchTypingMessage(isTyping)
    }

    private fun dispatchTypingMessage(isTyping: Boolean) {
        val currentIdentity = runtime.currentIdentity()
        if (currentIdentity != loginIdentity || !runtime.isLoggedIn()) return
        val peer = peerUserID ?: return
        runtime.send(peer, TypingMessageProtocol.buildTypingMessageData(isTyping))
    }

    private fun destroy() {
        destroyed = true
        receiveGate.close()
        unbind()
        typingTimeoutJob?.cancel()
        typingTimeoutJob = null
        _typingState.value = false
        if (hasSentTypingStart) {
            hasSentTypingStart = false
            dispatchTypingMessage(false)
        }
        scope.cancel()
    }

    fun stop() {
        displayWorkRevision.incrementAndGet()
        sendTypingStatus(false)
        typingTimeoutJob?.cancel()
        typingTimeoutJob = null
        _typingState.value = false
    }
}
