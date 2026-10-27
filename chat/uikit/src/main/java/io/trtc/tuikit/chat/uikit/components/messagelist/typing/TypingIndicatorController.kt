package io.trtc.tuikit.chat.uikit.components.messagelist.typing

import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageEvent
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import io.trtc.tuikit.atomicxcore.api.message.MessageLoadOption
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMMessage
import com.tencent.imsdk.v2.V2TIMAdvancedMsgListener
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.gson.JsonObject
import com.google.gson.JsonParser

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

    fun isTypingMessage(message: MessageInfo): Boolean {
        return parseTypingStatus(message) != null
    }

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

    fun acceptsStoreActivity(sender: String?, self: Boolean, typingStatus: Boolean?,
                             identity: String, loggedIn: Boolean): Boolean =
        isCurrent(identity, loggedIn) && !self && sender == peerUserID && typingStatus == null

    fun close() { closed = true }
}

internal class TypingIndicatorController private constructor(
    private val conversationID: String
) {
    companion object {
        private const val TYPING_SEND_INTERVAL_MS = 4_000L
        private const val TYPING_DISPLAY_TIMEOUT_MS = 5_000L
        private const val PEER_ACTIVE_WINDOW_MS = 30_000L
        private const val RECENT_MESSAGE_LOAD_COUNT = 20

        private val controllers = HashMap<String, TypingIndicatorController>()

        @Synchronized
        fun obtain(conversationID: String): TypingIndicatorController {
            val controller = controllers.getOrPut(conversationID) {
                TypingIndicatorController(conversationID)
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var typingTimeoutJob: Job? = null

    @Volatile
    private var retainCount = 0

    @Volatile
    private var lastTypingSendTimeMs = 0L

    @Volatile
    private var hasSentTypingStart = false
    @Volatile
    private var destroyed = false

    @Volatile
    private var lastPeerMessageTimeMs = 0L

    private val loginIdentity = "${io.trtc.tuikit.atomicxcore.api.login.LoginStore.shared.sdkAppID}:${V2TIMManager.getInstance().loginUser.orEmpty()}"
    private val receiveGate = TypingReceiveGate(peerUserID, loginIdentity)
    private val listenStore = if (peerUserID != null) MessageListStore.create(conversationID) else null
    private val rawTypingListener = object : V2TIMAdvancedMsgListener() {
        override fun onRecvNewMessage(message: V2TIMMessage?) {
            val incoming = message ?: return
            if (incoming.elemType != V2TIMMessage.V2TIM_ELEM_TYPE_CUSTOM) return
            val data = incoming.customElem?.data ?: return
            if (destroyed) return
            val status = receiveGate.rawStatus(data.toString(Charsets.UTF_8), incoming.sender,
                incoming.groupID, incoming.isSelf, currentLoginIdentity(), isLoggedIn()) ?: return
            // Transient controls must not wait for AtomicX's history/list pipeline.
            scope.launch(Dispatchers.Main.immediate) {
                if (destroyed || !receiveGate.isCurrent(currentLoginIdentity(), isLoggedIn())) return@launch
                lastPeerMessageTimeMs = System.currentTimeMillis()
                updatePeerTyping(status)
            }
        }
    }

    private fun currentLoginIdentity(): String =
        "${io.trtc.tuikit.atomicxcore.api.login.LoginStore.shared.sdkAppID}:${V2TIMManager.getInstance().loginUser.orEmpty()}"

    private fun isLoggedIn(): Boolean =
        V2TIMManager.getInstance().loginStatus == V2TIMManager.V2TIM_STATUS_LOGINED

    init {
        if (peerUserID != null) V2TIMManager.getMessageManager().addAdvancedMsgListener(rawTypingListener)
        listenStore?.let { store ->
            store.loadMessages(MessageLoadOption(pageCount = RECENT_MESSAGE_LOAD_COUNT))
            scope.launch {
                store.messageEventFlow.collect { event ->
                    when (event) {
                        is MessageEvent.OnReceiveNewMessage -> handleNewMessage(event.message)
                    }
                }
            }
            scope.launch {
                store.state.messageList.collect { list ->
                    if (destroyed) return@collect
                    val identity = currentLoginIdentity()
                    val loggedIn = isLoggedIn()
                    val lastPeerMessageTimeSec = list.lastOrNull {
                        receiveGate.acceptsStoreActivity(it.from.userID, it.isSentBySelf,
                            TypingMessageProtocol.parseTypingStatus(it), identity, loggedIn)
                    }?.timestamp ?: return@collect
                    val timeMs = lastPeerMessageTimeSec * 1000L
                    if (timeMs > lastPeerMessageTimeMs) {
                        lastPeerMessageTimeMs = timeMs
                    }
                }
            }
        }
    }

    private fun handleNewMessage(message: MessageInfo) {
        if (destroyed || !receiveGate.acceptsStoreActivity(message.from.userID, message.isSentBySelf,
                TypingMessageProtocol.parseTypingStatus(message), currentLoginIdentity(), isLoggedIn())) return
        lastPeerMessageTimeMs = System.currentTimeMillis()
    }

    private fun updatePeerTyping(isTyping: Boolean) {
        typingTimeoutJob?.cancel()
        typingTimeoutJob = null
        if (isTyping) {
            _typingState.value = true
            typingTimeoutJob = scope.launch(Dispatchers.Main.immediate) {
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
        if (!isPeerActive()) return
        if (isTyping) {
            val now = System.currentTimeMillis()
            if (now - lastTypingSendTimeMs < TYPING_SEND_INTERVAL_MS) return
            lastTypingSendTimeMs = now
        } else if (!hasSentTypingStart) {
            return
        }
        hasSentTypingStart = isTyping
        dispatchTypingMessage(isTyping)
    }

    private fun dispatchTypingMessage(isTyping: Boolean) {
        val currentIdentity = "${io.trtc.tuikit.atomicxcore.api.login.LoginStore.shared.sdkAppID}:${V2TIMManager.getInstance().loginUser.orEmpty()}"
        if (currentIdentity != loginIdentity || V2TIMManager.getInstance().loginStatus != V2TIMManager.V2TIM_STATUS_LOGINED) return
        val peer = peerUserID ?: return
        val message = V2TIMManager.getMessageManager().createCustomMessage(
            TypingMessageProtocol.buildTypingMessageData(isTyping).toByteArray(Charsets.UTF_8))
        message.isExcludedFromUnreadCount = true
        message.isExcludedFromLastMessage = true
        // SDK transport only; never send through a business Outbox or generate offline push.
        V2TIMManager.getMessageManager().sendMessage(message, peer, null,
            V2TIMMessage.V2TIM_PRIORITY_NORMAL, true, null, null)
    }

    private fun isPeerActive(): Boolean {
        if (lastPeerMessageTimeMs == 0L) return false
        return System.currentTimeMillis() - lastPeerMessageTimeMs < PEER_ACTIVE_WINDOW_MS
    }

    private fun destroy() {
        destroyed = true
        receiveGate.close()
        if (peerUserID != null) V2TIMManager.getMessageManager().removeAdvancedMsgListener(rawTypingListener)
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
        sendTypingStatus(false)
        typingTimeoutJob?.cancel()
        typingTimeoutJob = null
        _typingState.value = false
    }
}
