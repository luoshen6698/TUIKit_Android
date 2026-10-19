package io.trtc.tuikit.chat.demo.xingdun.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.trtc.tuikit.atomicxcore.api.call.CallEndReason
import io.trtc.tuikit.atomicxcore.api.call.CallListener
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunLocalNotificationRoutes
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** A silent, dismissible history entry, separate from the live ringing/media notification. */
internal object XingDunCallHistoryNotificationManager {
    private const val TAG = "XingDunCallHistory"
    private const val CHANNEL = "XingDunCallHistoryV1"
    private const val NOTIFICATION_TAG_PREFIX = "xingdun.call.history."
    private const val NOTIFICATION_ID = 9910
    private const val MAX_NOTIFICATIONS = 32
    private const val PREFERENCES = "xingdun_call_history_notifications"
    private const val KEY_ACCOUNT_DIGEST = "account_scope_digest"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val generation = AtomicLong()
    private val policy = XingDunCallHistoryNotificationPolicy()
    private var appContext: Context? = null
    private var initialized = false
    private var account: String? = null
    @Volatile private var enabled = false

    private val listener = object : CallListener() {
        override fun onCallReceived(callId: String, mediaType: CallMediaType, userData: String) {
            observeCurrentCall(callId)
        }

        override fun onCallStarted(callId: String, mediaType: CallMediaType) {
            observeCurrentCall(callId, accepted = true)
        }

        override fun onCallEnded(callId: String, mediaType: CallMediaType, reason: CallEndReason, userId: String) {
            // The SDK may already have reset activeCall by this callback. Use the captured call,
            // never infer the caller or account from a later/new call's mutable SDK state.
            if (!enabled) return
            val epoch = generation.get()
            val eventAccount = XingDunLocalNotificationRoutes.accountKey()
            scope.launch {
                if (!enabled || epoch != generation.get()) return@launch
                syncAccount()
                if (eventAccount == null || eventAccount != account) return@launch
                val record = policy.finish(eventAccount, callId, endReason(reason)) ?: return@launch
                publish(record)
            }
        }
    }

    fun initialize(context: Context) {
        val epoch = generation.get()
        scope.launch {
            if (epoch != generation.get()) return@launch
            appContext = context.applicationContext
            enabled = true
            syncAccount()
            if (initialized) return@launch
            initialized = true
            CallStore.shared.addListener(listener)
            val state = CallStore.shared.observerState
            combine(state.selfInfo, state.activeCall, state.allParticipants) { _, _, _ -> Unit }
                .collect { observeCurrentCall() }
        }
    }

    /** Invalidate queued callbacks immediately and remove only this feature's retained records. */
    fun clear() {
        enabled = false
        val epoch = generation.incrementAndGet()
        scope.launch {
            if (epoch != generation.get()) return@launch
            policy.clear()
            account = null
            cancelHistoryNotifications()
            appContext?.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)?.edit()
                ?.remove(KEY_ACCOUNT_DIGEST)?.apply()
        }
    }

    private fun observeCurrentCall(expectedCallId: String? = null, accepted: Boolean = false) {
        if (!enabled) return
        val epoch = generation.get()
        val eventAccount = XingDunLocalNotificationRoutes.accountKey()
        val state = CallStore.shared.observerState
        val call = state.activeCall.value
        val self = state.selfInfo.value
        val waitingOrAccepted = accepted || self.status == CallParticipantStatus.Waiting ||
            self.status == CallParticipantStatus.Accept
        val selfId = self.id
        val hasMedia = call.mediaType != null
        val callerName = state.allParticipants.value.firstOrNull { it.id == call.inviterId }?.name.orEmpty()
        val snapshot = eventAccount?.let {
            XingDunCallHistoryNotificationPolicy.Call(it, call.callId, call.inviterId, callerName,
                call.mediaType == CallMediaType.Video, accepted || self.status == CallParticipantStatus.Accept)
        }
        val oneToOne = call.chatGroupId.isBlank() && call.inviteeIds.size == 1 &&
            call.inviteeIds.single() == self.id
        val matchesEvent = expectedCallId == null || call.callId == expectedCallId
        scope.launch {
            if (!enabled || epoch != generation.get()) return@launch
            syncAccount()
            if (snapshot == null || snapshot.accountKey != account || !waitingOrAccepted ||
                !matchesEvent || !hasMedia) return@launch
            policy.observe(snapshot, selfId, oneToOne)
        }
    }

    private fun syncAccount() {
        val current = XingDunLocalNotificationRoutes.accountKey()
        val context = appContext ?: return
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val currentDigest = current?.let(::digest)
        val savedDigest = preferences.getString(KEY_ACCOUNT_DIGEST, null)
        if (account == current && savedDigest == currentDigest) return
        policy.clear()
        // A same-account process restart must not dismiss already posted history. Only the
        // scope's one-way digest is stored; no caller data, credentials or call payload is saved.
        if (savedDigest != currentDigest) cancelHistoryNotifications()
        preferences.edit().putString(KEY_ACCOUNT_DIGEST, currentDigest).apply()
        account = current
    }

    private fun publish(record: XingDunCallHistoryNotificationPolicy.Record) {
        val context = appContext ?: return
        if (!enabled || !XingDunLocalNotificationRoutes.isAuthenticatedAccount(record.call.accountKey) ||
            !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL,
                    context.getString(R.string.xingdun_call_history_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                })
            }
            val tag = NOTIFICATION_TAG_PREFIX + digest(record.call.accountKey + "\u0000" + record.call.callId)
            val open = XingDunLocalNotificationRoutes.contentIntent(context, tag,
                "c2c_${record.call.callerId}", record.call.accountKey) ?: return
            val media = context.getString(if (record.call.video) R.string.xingdun_call_history_video
                else R.string.xingdun_call_history_audio)
            val result = context.getString(when (record.result) {
                XingDunCallHistoryNotificationPolicy.Result.ENDED -> R.string.xingdun_call_history_ended
                XingDunCallHistoryNotificationPolicy.Result.REJECTED -> R.string.xingdun_call_history_rejected
                XingDunCallHistoryNotificationPolicy.Result.MISSED -> R.string.xingdun_call_history_missed
                XingDunCallHistoryNotificationPolicy.Result.CANCELED -> R.string.xingdun_call_history_canceled
            })
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(com.tencent.qcloud.tuikit.tuicallkit.R.drawable.tuicallkit_ic_float)
                .setContentTitle(record.call.callerName.ifBlank { context.getString(R.string.xingdun_call_history_caller) })
                .setContentText(context.getString(R.string.xingdun_call_history_summary, media, result))
                .setCategory(if (record.result == XingDunCallHistoryNotificationPolicy.Result.MISSED)
                    NotificationCompat.CATEGORY_MISSED_CALL else NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(open)
                .setOngoing(false)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build()
            // Do not reuse the ringing notification's ID, CallStyle, FSI, actions or timeout.
            manager.notify(tag, NOTIFICATION_ID, notification)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.activeNotifications.filter { it.tag?.startsWith(NOTIFICATION_TAG_PREFIX) == true }
                    .sortedByDescending { it.postTime }.drop(MAX_NOTIFICATIONS)
                    .forEach { manager.cancel(it.tag, it.id) }
            }
            Log.i(TAG, "stage=call_history event=posted result=${record.result}")
        } catch (error: RuntimeException) {
            Log.w(TAG, "stage=call_history event=unavailable type=${error.javaClass.simpleName}")
        }
    }

    private fun cancelHistoryNotifications() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching {
            manager.activeNotifications.filter { it.tag?.startsWith(NOTIFICATION_TAG_PREFIX) == true }
                .forEach { manager.cancel(it.tag, it.id) }
        }.onFailure { Log.w(TAG, "stage=call_history event=clear_unavailable type=${it.javaClass.simpleName}") }
    }

    private fun endReason(reason: CallEndReason): XingDunCallHistoryNotificationPolicy.EndReason = when (reason) {
        CallEndReason.Hangup -> XingDunCallHistoryNotificationPolicy.EndReason.HANGUP
        CallEndReason.Reject -> XingDunCallHistoryNotificationPolicy.EndReason.REJECT
        CallEndReason.NoResponse -> XingDunCallHistoryNotificationPolicy.EndReason.NO_RESPONSE
        CallEndReason.Canceled -> XingDunCallHistoryNotificationPolicy.EndReason.CANCELED
        CallEndReason.LineBusy -> XingDunCallHistoryNotificationPolicy.EndReason.LINE_BUSY
        else -> XingDunCallHistoryNotificationPolicy.EndReason.OTHER
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
