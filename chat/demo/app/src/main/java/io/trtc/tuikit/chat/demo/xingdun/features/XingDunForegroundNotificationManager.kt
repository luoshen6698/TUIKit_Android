package io.trtc.tuikit.chat.demo.xingdun.features

import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.tencent.imsdk.v2.V2TIMAdvancedMsgListener
import com.tencent.imsdk.v2.V2TIMConversation
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMMessage
import com.tencent.imsdk.v2.V2TIMValueCallback

internal data class XingDunForegroundNotificationDecision(
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
        val shouldPresent = appInForeground && !isActiveConversation && !isMuted && isSupportedMessage
        return XingDunForegroundNotificationDecision(
            playSound = shouldPresent && soundEnabled,
            playVibration = shouldPresent && vibrationEnabled,
        )
    }
}

/** Applies the iOS-equivalent sound/vibration preferences to foreground IM messages. */
internal object XingDunForegroundNotificationManager {
    private const val PREFERENCES = "xingdun_notification_preferences"
    private const val KEY_SOUND = "sound_enabled"
    private const val KEY_VIBRATION = "vibration_enabled"
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var appContext: Context
    private var startedActivityCount = 0

    @Volatile
    private var activeConversationID: String? = null

    private val messageListener = object : V2TIMAdvancedMsgListener() {
        override fun onRecvNewMessage(message: V2TIMMessage?) {
            message ?: return
            if (message.isSelf) return
            val conversationID = conversationID(message) ?: return
            val supported = isSupportedMessage(message)
            val active = activeConversationID == conversationID
            val foreground = synchronized(this@XingDunForegroundNotificationManager) { startedActivityCount > 0 }
            if (!foreground || active || !supported) return
            V2TIMManager.getConversationManager().getConversation(
                conversationID,
                object : V2TIMValueCallback<V2TIMConversation> {
                    override fun onSuccess(conversation: V2TIMConversation?) {
                        presentIfNeeded(
                            appInForeground = synchronized(this@XingDunForegroundNotificationManager) {
                                startedActivityCount > 0
                            },
                            active = activeConversationID == conversationID,
                            muted = conversation?.recvOpt != null &&
                                conversation.recvOpt != V2TIMMessage.V2TIM_RECEIVE_MESSAGE,
                            supported = supported,
                        )
                    }

                    override fun onError(code: Int, desc: String?) {
                        presentIfNeeded(
                            appInForeground = synchronized(this@XingDunForegroundNotificationManager) {
                                startedActivityCount > 0
                            },
                            active = activeConversationID == conversationID,
                            muted = false,
                            supported = supported,
                        )
                    }
                }
            )
        }
    }

    fun initialize(application: Application) {
        if (::appContext.isInitialized) return
        appContext = application.applicationContext
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
    }

    fun leaveConversation(conversationID: String) {
        if (activeConversationID == conversationID) activeConversationID = null
    }

    fun resetTenantState() {
        activeConversationID = null
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
        appInForeground: Boolean,
        active: Boolean,
        muted: Boolean,
        supported: Boolean,
    ) {
        val decision = XingDunForegroundNotificationPolicy.decide(
            appInForeground = appInForeground,
            isActiveConversation = active,
            isMuted = muted,
            isSupportedMessage = supported,
            soundEnabled = soundEnabled(appContext),
            vibrationEnabled = vibrationEnabled(appContext),
        )
        if (!decision.playSound && !decision.playVibration) return
        mainHandler.post {
            if (decision.playSound) {
                runCatching {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    RingtoneManager.getRingtone(appContext, uri)?.play()
                }
            }
            if (decision.playVibration) vibrate()
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(120)
        }
    }

    private fun conversationID(message: V2TIMMessage): String? = when {
        !message.groupID.isNullOrBlank() -> "group_${message.groupID}"
        !message.userID.isNullOrBlank() -> "c2c_${message.userID}"
        !message.sender.isNullOrBlank() -> "c2c_${message.sender}"
        else -> null
    }

    private fun isSupportedMessage(message: V2TIMMessage): Boolean = when (message.elemType) {
        V2TIMMessage.V2TIM_ELEM_TYPE_TEXT,
        V2TIMMessage.V2TIM_ELEM_TYPE_IMAGE,
        V2TIMMessage.V2TIM_ELEM_TYPE_SOUND,
        V2TIMMessage.V2TIM_ELEM_TYPE_VIDEO,
        V2TIMMessage.V2TIM_ELEM_TYPE_FILE -> true
        V2TIMMessage.V2TIM_ELEM_TYPE_CUSTOM -> {
            val data = message.customElem?.data?.toString(Charsets.UTF_8)
            XingDunCustomMessageParser.parse(data)?.isControl == false
        }
        else -> false
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
