package io.trtc.tuikit.chat.demo.xingdun.features

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.imsdk.v2.V2TIMAdvancedMsgListener
import com.tencent.imsdk.v2.V2TIMConversation
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMMessage
import com.tencent.imsdk.v2.V2TIMValueCallback
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.call.XingDunCallHistoryNotificationManager
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunLocalNotificationRoutes

internal data class XingDunForegroundNotificationDecision(
    val showNotification: Boolean,
    val playSound: Boolean,
    val playVibration: Boolean,
)

internal object XingDunForegroundNotificationPolicy {
    fun decide(
        appInForeground: Boolean,
        isActiveConversation: Boolean,
        isMuted: Boolean,
        isSupportedMessage: Boolean,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
    ): XingDunForegroundNotificationDecision {
        val shouldPresent = !(appInForeground && isActiveConversation) && !isMuted && isSupportedMessage
        return XingDunForegroundNotificationDecision(
            showNotification = shouldPresent,
            playSound = shouldPresent && soundEnabled,
            playVibration = shouldPresent && vibrationEnabled,
        )
    }
}

/** Posts notifications for SDK-delivered messages in foreground or a still-running background process. */
internal object XingDunForegroundNotificationManager {
    private const val PREFERENCES = "xingdun_notification_preferences"
    private const val KEY_SOUND = "sound_enabled"
    private const val KEY_VIBRATION = "vibration_enabled"
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var appContext: Context
    private lateinit var notifications: XingDunMessageNotifications
    private var startedActivityCount = 0

    @Volatile
    private var activeConversationID: String? = null

    private val messageListener = object : V2TIMAdvancedMsgListener() {
        override fun onRecvNewMessage(message: V2TIMMessage?) {
            message ?: return
            val conversationID = conversationID(message) ?: return
            if (message.elemType == V2TIMMessage.V2TIM_ELEM_TYPE_CUSTOM) {
                val data = message.customElem?.data?.toString(Charsets.UTF_8)
                XingDunCustomMessageParser.parse(data)?.let {
                    when (it.type) {
                        "auto_delete_config" -> XingDunConversationPreviewCleaner.onAutoDeleteConfigurationReceived(
                            conversationID,
                            it.values,
                        )
                        "remote_delete" -> XingDunConversationPreviewCleaner.onRemoteDeleteReceived(
                            appContext,
                            conversationID,
                            it.values,
                            message,
                        )
                        "pin_message" -> XingDunPinnedMessageRepository.applyRemote(conversationID, it.values)
                    }
                }
            }
            val supported = isSupportedMessage(message)
            if (message.isSelf) return
            val account = XingDunLocalNotificationRoutes.accountKey() ?: return
            val active = activeConversationID == conversationID
            val foreground = synchronized(this@XingDunForegroundNotificationManager) { startedActivityCount > 0 }
            if ((foreground && active) || !supported) return
            V2TIMManager.getConversationManager().getConversation(
                conversationID,
                object : V2TIMValueCallback<V2TIMConversation> {
                    override fun onSuccess(conversation: V2TIMConversation?) {
                        mainHandler.post {
                            if (account != XingDunLocalNotificationRoutes.accountKey()) return@post
                            presentIfNeeded(message, conversationID, account,
                                conversation?.showName?.takeIf { it.isNotBlank() }
                                    ?: appContext.getString(R.string.xingdun_chat_notification_new),
                                muted = conversation == null || conversation.recvOpt != V2TIMMessage.V2TIM_RECEIVE_MESSAGE,
                                supported = supported)
                        }
                    }

                    override fun onError(code: Int, desc: String?) {
                        // Do not bypass a possibly-muted conversation when its preference cannot be checked.
                        Log.w("XingDunMessageNotification", "event=conversation_lookup_failed code=$code")
                    }
                }
            )
        }
    }

    fun initialize(application: Application) {
        if (::appContext.isInitialized) return
        appContext = application.applicationContext
        notifications = XingDunMessageNotifications(appContext).also { it.initializeChannels() }
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                synchronized(this@XingDunForegroundNotificationManager) { startedActivityCount += 1 }
            }

            override fun onActivityStopped(activity: Activity) {
                synchronized(this@XingDunForegroundNotificationManager) {
                    startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        V2TIMManager.getMessageManager().addAdvancedMsgListener(messageListener)
    }

    fun enterConversation(conversationID: String) {
        activeConversationID = conversationID
        if (::notifications.isInitialized) notifications.clearConversation(conversationID)
    }

    fun leaveConversation(conversationID: String) {
        if (activeConversationID == conversationID) activeConversationID = null
    }

    fun resetTenantState() {
        activeConversationID = null
        if (::notifications.isInitialized) notifications.clear()
        XingDunCallHistoryNotificationManager.clear()
        XingDunConversationPreviewCleaner.resetAccountState()
        XingDunConversationPreviewResolver.clear()
    }

    fun soundEnabled(context: Context): Boolean = preferences(context).getBoolean(KEY_SOUND, true)

    fun vibrationEnabled(context: Context): Boolean = preferences(context).getBoolean(KEY_VIBRATION, true)

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_VIBRATION, enabled).apply()
    }

    private fun presentIfNeeded(
        message: V2TIMMessage,
        conversationID: String,
        account: String,
        title: String,
        muted: Boolean,
        supported: Boolean,
    ) {
        val decision = XingDunForegroundNotificationPolicy.decide(
            appInForeground = synchronized(this) { startedActivityCount > 0 },
            isActiveConversation = activeConversationID == conversationID,
            isMuted = muted,
            isSupportedMessage = supported,
            soundEnabled = soundEnabled(appContext),
            vibrationEnabled = vibrationEnabled(appContext),
        )
        if (decision.showNotification) notifications.show(message, conversationID, title, account,
            decision.playSound, decision.playVibration)
    }

    private fun conversationID(message: V2TIMMessage): String? = when {
        !message.groupID.isNullOrBlank() -> "group_${message.groupID}"
        !message.userID.isNullOrBlank() -> "c2c_${message.userID}"
        !message.sender.isNullOrBlank() -> "c2c_${message.sender}"
        else -> null
    }

    private fun isSupportedMessage(message: V2TIMMessage): Boolean = when (message.elemType) {
        V2TIMMessage.V2TIM_ELEM_TYPE_TEXT,
        V2TIMMessage.V2TIM_ELEM_TYPE_FACE,
        V2TIMMessage.V2TIM_ELEM_TYPE_IMAGE,
        V2TIMMessage.V2TIM_ELEM_TYPE_SOUND,
        V2TIMMessage.V2TIM_ELEM_TYPE_VIDEO,
        V2TIMMessage.V2TIM_ELEM_TYPE_FILE -> true
        V2TIMMessage.V2TIM_ELEM_TYPE_CUSTOM -> {
            val data = message.customElem?.data?.toString(Charsets.UTF_8)
            XingDunCustomMessageParser.parse(data)?.let { !it.isControl && !it.isLegacyCallSummary() } == true
        }
        else -> false
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
