package com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.common.data.Constants
import com.tencent.qcloud.tuikit.tuicallkit.common.data.Logger
import com.tencent.qcloud.tuikit.tuicallkit.common.utils.DeviceUtils
import com.tencent.qcloud.tuikit.tuicallkit.manager.feature.CallingBellFeature
import com.tencent.qcloud.tuikit.tuicallkit.manager.feature.CallingVibratorFeature
import com.tencent.qcloud.tuikit.tuicallkit.manager.feature.NotificationFeature
import com.tencent.qcloud.tuikit.tuicallkit.state.GlobalState
import com.tencent.qcloud.tuikit.tuicallkit.view.CallMainActivity
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.call.CallEndReason
import io.trtc.tuikit.atomicxcore.api.call.CallListener
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantInfo
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One system notification and one action binding for the currently restored incoming call. */
class IncomingNotificationBanner(context: Context) {
    private val controller = controller(context)

    fun showNotification(participant: CallParticipantInfo, onFailure: (() -> Unit)? = null) {
        // The cached SDK participant is optional; never wait for a profile/avatar network request.
        controller.scope.launch { controller.showIncoming(onFailure) }
    }

    companion object {
        internal const val NOTIFICATION_ID = 9909
        private const val TAG = "NativeCallNotification"
        private const val EXTRA_CALL_ID = "tuicallkit.notification.call_id"
        private const val EXTRA_SDK_APP_ID = "tuicallkit.notification.sdk_app_id"
        private const val EXTRA_SELF_ID = "tuicallkit.notification.self_id"
        private const val EXTRA_BINDING = "tuicallkit.notification.binding"
        private const val OPEN_CALL_ACTION = "open_call_action"
        @Volatile private var notificationsSuspended = false
        @Volatile private var lifecycleEpoch = 0L
        @Volatile private var sharedController: Controller? = null
        private val terminationGate = NativeCallTerminationGate()
        private val incomingPresentationState = MutableStateFlow<NativeCallNotificationPolicy.IncomingPresentation?>(null)
        internal val incomingPresentation = incomingPresentationState.asStateFlow()

        internal fun needsLocalIncomingRinging(context: Context): Boolean {
            val call = currentCall() ?: return false
            return NativeCallNotificationPolicy.needsLocalRinging(call.identity, incomingPresentation.value,
                NotificationFeature.canShowIncomingCall(context), DeviceUtils.isAppRunningForeground(context) ||
                    sharedController?.persistentBanner?.isShowing == true,
                call.phase == NativeCallPhase.Waiting, notificationsSuspended,
                sharedController?.compatibleIncoming == true)
        }

        private fun controller(context: Context): Controller = sharedController ?: synchronized(this) {
            sharedController ?: Controller(context.applicationContext).also { sharedController = it }
        }

        fun resumeNotifications(context: Context) {
            val owner = controller(context)
            val epoch = synchronized(this) { ++lifecycleEpoch }
            owner.scope.launch {
                if (epoch != lifecycleEpoch) return@launch
                if (notificationsSuspended) owner.clear()
                notificationsSuspended = false
                owner.refresh()
            }
        }

        fun suspendNotifications(context: Context) {
            val epoch = synchronized(this) { notificationsSuspended = true; ++lifecycleEpoch }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
            CallingBellFeature.stopCalleeRinging()
            CallingVibratorFeature.stopCalleeVibration()
            NativeCallForegroundService.stop(context)
            sharedController?.let { owner -> owner.scope.launch {
                if (notificationsSuspended && epoch == lifecycleEpoch) owner.clear()
            } }
        }

        fun cancelAll(context: Context) = suspendNotifications(context)

        fun areNotificationsSuspended(): Boolean = notificationsSuspended

        internal fun onCallJoined(context: Context, callId: String) {
            val state = CallStore.shared.observerState
            if (callId.isBlank() || state.activeCall.value.callId != callId) return
            terminationGate.allowJoinedCall(NativeCallIdentity(callId, LoginStore.shared.sdkAppID,
                V2TIMManager.getInstance().loginUser.orEmpty()))
            controller(context).scope.launch { controller(context).refresh() }
        }

        fun isNotificationIntent(intent: Intent?): Boolean = intent?.hasExtra(EXTRA_BINDING) == true

        fun isCurrentNotificationIntent(intent: Intent?): Boolean =
            sharedController?.matches(intent) == true

        fun handleNotificationAction(context: Context, intent: Intent?, onFinished: () -> Unit = {}): Boolean =
            controller(context).execute(intent, onFinished)

        fun createActionIntent(context: Context, action: String): Intent? = controller(context).let { owner ->
            // The overlay is built inside refresh, after the binding is installed. Do not
            // recursively publish before it is attached (its visibility owns audio fallback).
            if (!owner.presentingPersistentBanner) owner.refresh()
            val binding = owner.binding ?: return@let null
            val intent = owner.actionIntent(binding, action, false)
            intent.takeIf { owner.matches(it) }
        }

        internal fun usesNativeNotification(context: Context): Boolean =
            !notificationsSuspended && NotificationFeature.canShowIncomingCall(context) && currentCall() != null

        internal fun isAcceptedCallCurrent(): Boolean =
            !notificationsSuspended && currentCall()?.phase == NativeCallPhase.Accepted

        internal fun ongoingNotification(context: Context, intent: Intent): Notification? = controller(context).let { owner ->
            owner.refresh()
            if (!owner.matches(intent)) return@let null
            val call = currentCall() ?: return@let null
            val binding = owner.binding ?: return@let null
            if (notificationsSuspended || call.phase != NativeCallPhase.Accepted || binding.identity != call.identity) null
            else owner.buildNotification(call, binding)
        }

        internal fun foregroundNotificationPosted(context: Context, intent: Intent) {
            controller(context).markForegroundPosted(intent)
        }

        internal fun acceptedCallIsVideo(): Boolean = currentCall()?.isVideo == true

        private data class Snapshot(
            val identity: NativeCallIdentity,
            val phase: NativeCallPhase,
            val callerName: String,
            val isVideo: Boolean,
        )

        private fun currentCall(): Snapshot? {
            val manager = V2TIMManager.getInstance()
            if (manager.loginStatus != V2TIMManager.V2TIM_STATUS_LOGINED) return null
            val state = CallStore.shared.observerState
            val self = state.selfInfo.value
            val call = state.activeCall.value
            val sdkAppId = LoginStore.shared.sdkAppID
            if (sdkAppId <= 0 || self.id.isBlank() || self.id != manager.loginUser ||
                call.callId.isBlank() || call.inviterId.isBlank() || call.inviterId == self.id || call.mediaType == null
            ) return null
            val phase = when (self.status) {
                CallParticipantStatus.Waiting -> NativeCallPhase.Waiting
                CallParticipantStatus.Accept -> NativeCallPhase.Accepted
                else -> return null
            }
            val identity = NativeCallIdentity(call.callId, sdkAppId, self.id)
            if (!terminationGate.canPresent(identity)) return null
            val caller = state.allParticipants.value.firstOrNull { it.id == call.inviterId }
            return Snapshot(identity, phase,
                caller?.name?.takeIf { it.isNotBlank() }.orEmpty(), call.mediaType == CallMediaType.Video)
        }

        private data class Binding(val identity: NativeCallIdentity, val nonce: String, val createdAt: Long)

        private class Controller(val context: Context) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            private val gate = NativeCallActionGate()
            var binding: Binding? = null
                private set
            private var lastRendered: Snapshot? = null
            private var lastRenderedLocalAlert: Boolean? = null
            private var expired = false
            private var expiryJob: Job? = null
            private val actionJobs = mutableMapOf<NativeCallActionGate.Attempt, Job>()
            private val actionReleases = mutableMapOf<NativeCallActionGate.Attempt, () -> Unit>()
            private var failureFallback: (() -> Unit)? = null
            private var fallbackShownFor: NativeCallIdentity? = null
            var persistentBanner: IncomingFloatBanner? = null
                private set
            var presentingPersistentBanner = false
                private set
            private var persistentBannerFailedFor: NativeCallIdentity? = null
            // Some OEM Android 13 systems strip FSI eligibility before validating CallStyle.
            // Retain the same channel, identity-bound actions and timeout without that style.
            var compatibleIncoming = false
                private set
            private val callEndListener = object : CallListener() {
                override fun onCallEnded(callId: String, mediaType: CallMediaType,
                                         reason: CallEndReason, userId: String) {
                    val identity = NativeCallIdentity(callId, LoginStore.shared.sdkAppID,
                        V2TIMManager.getInstance().loginUser.orEmpty())
                    // The terminal event can precede the SDK's Waiting -> None StateFlow update.
                    // Remember it before dispatching UI cleanup so no queued refresh can re-ring.
                    if (!terminationGate.recordEnded(identity)) return
                    scope.launch {
                        val matches = terminationGate.shouldClear(identity, binding?.identity)
                        Log.i(TAG, "stage=native_call event=terminal_received reason=${reason.name} current=$matches")
                        if (matches) clear()
                    }
                }
            }

            fun showIncoming(onFailure: (() -> Unit)?) {
                refresh()
                failureFallback = onFailure
                currentCall()?.let { dispatchForegroundFallback(it) }
            }

            init {
                NotificationFeature(context).registerNotificationBannerChannel()
                CallStore.shared.addListener(callEndListener)
                scope.launch {
                    val state = CallStore.shared.observerState
                    combine(state.selfInfo, state.activeCall, state.allParticipants,
                        LoginStore.shared.loginState.loginStatus) { _, _, _, _ -> Unit }.collect { refresh() }
                }
            }

            fun clear() {
                dismissPersistentBanner()
                persistentBannerFailedFor = null
                expiryJob?.cancel()
                expiryJob = null
                actionJobs.values.forEach { it.cancel() }
                actionJobs.clear()
                actionReleases.values.toList().forEach { it() }
                actionReleases.clear()
                gate.reset()
                if (binding != null) Log.i(TAG, "stage=native_call event=ended")
                binding = null
                lastRendered = null
                lastRenderedLocalAlert = null
                expired = false
                failureFallback = null
                fallbackShownFor = null
                incomingPresentationState.value = null
                CallingBellFeature.stopCalleeRinging()
                CallingVibratorFeature.stopCalleeVibration()
                NativeCallForegroundService.stop(context)
                manager.cancel(NOTIFICATION_ID)
            }

            fun refresh() {
                val call = currentCall()
                if (notificationsSuspended || call == null) {
                    clear()
                    return
                }
                if (binding?.identity != call.identity) {
                    clear()
                    val expected = Binding(call.identity, UUID.randomUUID().toString(), SystemClock.elapsedRealtime())
                    binding = expected
                    expiryJob = scope.launch {
                        val deadline = expected.createdAt + Constants.CALL_WAITING_MAX_TIME * 1_000L
                        while (binding == expected && SystemClock.elapsedRealtime() < deadline) {
                            delay(minOf(500L, (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)))
                            if (binding != expected) return@launch
                            if (currentCall()?.phase != NativeCallPhase.Waiting) {
                                refresh()
                                return@launch
                            }
                            // Keep the desktop surface in sync when the app is opened or backgrounded.
                            // Profile-only refreshes below do not republish the system notification.
                            if (SystemClock.elapsedRealtime() < deadline) refresh()
                        }
                        if (binding == expected && currentCall()?.phase == NativeCallPhase.Waiting) {
                            expired = true
                            dismissPersistentBanner()
                            incomingPresentationState.value = NativeCallNotificationPolicy.IncomingPresentation(
                                call.identity, NativeCallNotificationPolicy.IncomingDelivery.Expired)
                            CallingBellFeature.stopCalleeRinging()
                            CallingVibratorFeature.stopCalleeVibration()
                            manager.cancel(NOTIFICATION_ID)
                            Log.i(TAG, "stage=native_call event=incoming_timeout")
                        }
                    }
                }
                gate.update(call.identity, call.phase)
                if (!NotificationFeature.canShowIncomingCall(context) ||
                    (expired && call.phase == NativeCallPhase.Waiting)
                ) {
                    dismissPersistentBanner()
                    manager.cancel(NOTIFICATION_ID)
                    lastRendered = null
                    return
                }
                syncPersistentBanner(call)
                val localAlert = usesLocalAudio(call)
                if (call.phase == NativeCallPhase.Accepted) {
                    incomingPresentationState.value = null
                    CallingBellFeature.stopCalleeRinging()
                    CallingVibratorFeature.stopCalleeVibration()
                    expiryJob?.cancel()
                    expiryJob = null
                    if (lastRendered?.phase == NativeCallPhase.Waiting) {
                        manager.cancel(NOTIFICATION_ID)
                        lastRendered = null
                    }
                }
                // Profile-only updates must not interrupt an insistent incoming ringtone or
                // request another fullscreen presentation. The initial cached name is sufficient.
                if (call.phase == NativeCallPhase.Waiting && lastRendered?.let {
                        it.identity == call.identity && it.phase == call.phase && it.isVideo == call.isVideo &&
                            lastRenderedLocalAlert == localAlert
                    } == true) return
                if (lastRendered == call && lastRenderedLocalAlert == localAlert) return
                val currentBinding = binding ?: return
                if (call.phase == NativeCallPhase.Waiting && incomingPresentation.value?.let {
                        it.identity == call.identity && it.delivery == NativeCallNotificationPolicy.IncomingDelivery.Failed
                    } == true) {
                    dispatchForegroundFallback(call)
                    return
                }
                val plan = NativeCallNotificationPolicy.plan(call.phase,
                    NativeCallForegroundService.isProtectingCurrentCall())
                if (plan.delivery == NativeCallNotificationPolicy.Delivery.Defer) return
                if (call.phase == NativeCallPhase.Waiting) incomingPresentationState.value =
                    NativeCallNotificationPolicy.IncomingPresentation(call.identity,
                        NativeCallNotificationPolicy.IncomingDelivery.Pending)
                var stage = "build"
                var builtNotification: Notification? = null
                try {
                    var notification = buildNotification(call, currentBinding)
                    if (!terminationGate.canPresent(call.identity)) {
                        if (binding?.identity == call.identity) clear()
                        return
                    }
                    builtNotification = notification
                    stage = "publish"
                    when (plan.delivery) {
                        NativeCallNotificationPolicy.Delivery.IncomingNotification -> {
                            try {
                                manager.notify(NOTIFICATION_ID, notification)
                            } catch (error: RuntimeException) {
                                if (compatibleIncoming || !NativeCallNotificationPolicy.shouldUseCompatibleIncoming(
                                        call.phase, error is IllegalArgumentException,
                                        NativeCallNotificationPolicy.failureReason(error.message))) throw error
                                compatibleIncoming = true
                                notification = buildNotification(call, currentBinding)
                                builtNotification = notification
                                manager.notify(NOTIFICATION_ID, notification)
                                Log.i(TAG, "stage=native_call event=incoming_compatibility_posted")
                            }
                        }
                        NativeCallNotificationPolicy.Delivery.OngoingForegroundService ->
                            if (!NativeCallForegroundService.updateNotification(notification)) return
                        NativeCallNotificationPolicy.Delivery.Defer -> return
                    }
                    lastRendered = call
                    lastRenderedLocalAlert = usesLocalAudio(call)
                    if (call.phase == NativeCallPhase.Waiting) {
                        incomingPresentationState.value = NativeCallNotificationPolicy.IncomingPresentation(
                            call.identity, NativeCallNotificationPolicy.IncomingDelivery.Published,
                            localAudio = usesLocalAudio(call))
                        if (!needsLocalIncomingRinging(context)) {
                            CallingBellFeature.stopCalleeRinging()
                            CallingVibratorFeature.stopCalleeVibration()
                        }
                    }
                    Log.i(TAG, "stage=native_call event=${if (call.phase == NativeCallPhase.Waiting) "incoming_posted" else "ongoing_posted"}")
                    // OEMs may accept a compatible notification but suppress its heads-up/FSI.
                    // A foreground caller must still get an in-app answer surface (not a second ringtone).
                    if (call.phase == NativeCallPhase.Waiting && compatibleIncoming) dispatchForegroundFallback(call)
                } catch (error: RuntimeException) {
                    val origin = error.stackTrace.firstOrNull()?.let { "${it.className}.${it.methodName}" }.orEmpty()
                    Log.w(TAG, "stage=native_call event=notification_failed phase=${call.phase} stage=$stage " +
                        "type=${error.javaClass.simpleName} reason=${NativeCallNotificationPolicy.failureReason(error.message)} " +
                        "fsi=${builtNotification?.fullScreenIntent != null} icon=${builtNotification?.smallIcon != null} " +
                        "api=${Build.VERSION.SDK_INT} target=${context.applicationInfo.targetSdkVersion} origin=$origin")
                    if (call.phase == NativeCallPhase.Waiting && currentCall()?.identity == call.identity) {
                        incomingPresentationState.value = NativeCallNotificationPolicy.IncomingPresentation(
                            call.identity, NativeCallNotificationPolicy.IncomingDelivery.Failed)
                        dispatchForegroundFallback(call)
                    }
                }
            }

            private fun dispatchForegroundFallback(call: Snapshot) {
                val fallback = failureFallback ?: return
                val current = currentCall() ?: return
                if (notificationsSuspended || expired || call.phase != NativeCallPhase.Waiting ||
                    current.identity != call.identity || current.phase != NativeCallPhase.Waiting ||
                    !DeviceUtils.isAppRunningForeground(context) || fallbackShownFor == call.identity ||
                    incomingPresentation.value?.let { it.identity == call.identity &&
                        (it.delivery == NativeCallNotificationPolicy.IncomingDelivery.Failed ||
                            compatibleIncoming && it.delivery == NativeCallNotificationPolicy.IncomingDelivery.Published)
                    } != true) return
                fallbackShownFor = call.identity
                Log.i(TAG, "stage=native_call event=foreground_fallback")
                fallback()
            }

            private fun syncPersistentBanner(call: Snapshot) {
                if (presentingPersistentBanner) return
                val canOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
                if (!NativeCallNotificationPolicy.needsPersistentAnswerBanner(
                        call.phase == NativeCallPhase.Waiting, DeviceUtils.isAppRunningForeground(context),
                        DeviceUtils.isScreenLocked(context), canOverlay, NotificationFeature.canShowIncomingCall(context),
                        notificationsSuspended, expired)) {
                    dismissPersistentBanner()
                    return
                }
                if (persistentBanner?.isShowing == true || persistentBannerFailedFor == call.identity) return
                presentingPersistentBanner = true
                try {
                    val state = CallStore.shared.observerState
                    val caller = state.allParticipants.value.firstOrNull { it.id == state.activeCall.value.inviterId }
                        ?: CallParticipantInfo()
                    val banner = IncomingFloatBanner(context)
                    persistentBanner = banner
                    if (banner.showIncomingView(caller)) {
                        Log.i(TAG, "stage=native_call event=persistent_banner_shown")
                    } else {
                        persistentBannerFailedFor = call.identity
                        dismissPersistentBanner()
                    }
                } catch (error: RuntimeException) {
                    persistentBannerFailedFor = call.identity
                    dismissPersistentBanner()
                    Log.w(TAG, "stage=native_call event=persistent_banner_failed type=${error.javaClass.simpleName}")
                } finally {
                    presentingPersistentBanner = false
                }
            }

            private fun dismissPersistentBanner() {
                val banner = persistentBanner ?: return
                persistentBanner = null
                banner.dismiss()
                if (!needsLocalIncomingRinging(context)) {
                    CallingBellFeature.stopCalleeRinging()
                    CallingVibratorFeature.stopCalleeVibration()
                }
                Log.i(TAG, "stage=native_call event=persistent_banner_hidden")
            }

            fun markForegroundPosted(intent: Intent) {
                if (!matches(intent)) return
                val call = currentCall()?.takeIf { it.phase == NativeCallPhase.Accepted } ?: return
                lastRendered = call
                lastRenderedLocalAlert = false
                Log.i(TAG, "stage=native_call event=ongoing_posted")
            }

            fun matches(intent: Intent?): Boolean {
                if (notificationsSuspended || intent == null) return false
                val call = currentCall() ?: return false
                val current = binding ?: return false
                gate.update(call.identity, call.phase)
                return !(expired && call.phase == NativeCallPhase.Waiting) &&
                    current.identity == call.identity && intent.getStringExtra(EXTRA_BINDING) == current.nonce &&
                    intent.getStringExtra(EXTRA_CALL_ID) == call.identity.callId &&
                    intent.getIntExtra(EXTRA_SDK_APP_ID, 0) == call.identity.sdkAppId &&
                    intent.getStringExtra(EXTRA_SELF_ID) == call.identity.selfId && gate.canOpen(call.identity)
            }

            fun execute(intent: Intent?, onFinished: () -> Unit): Boolean {
                if (!matches(intent)) {
                    Log.i(TAG, "stage=native_call event=action_ignored")
                    return false
                }
                val action = when (intent?.action) {
                    Constants.ACCEPT_CALL_ACTION -> NativeCallAction.Accept
                    Constants.REJECT_CALL_ACTION -> NativeCallAction.Reject
                    Constants.HANGUP_CALL_ACTION -> NativeCallAction.Hangup
                    else -> return false
                }
                val call = currentCall() ?: return false
                val attempt = gate.begin(call.identity, action) ?: run {
                    Log.i(TAG, "stage=native_call event=action_ignored")
                    return false
                }
                Log.i(TAG, "stage=native_call event=${when (action) {
                    NativeCallAction.Accept -> "answer_requested"
                    NativeCallAction.Reject -> "reject_requested"
                    NativeCallAction.Hangup -> "hangup_requested"
                }}")
                val finished = AtomicBoolean(false)
                val release = { if (finished.compareAndSet(false, true)) onFinished() }
                actionReleases[attempt] = release
                val timeout = scope.launch {
                    delay(8_000)
                    if (gate.finish(attempt, false)) Log.w(TAG, "stage=native_call event=action_timeout")
                    actionJobs.remove(attempt)
                    actionReleases.remove(attempt)
                    release()
                }
                actionJobs[attempt] = timeout
                val completion = object : CompletionHandler {
                    override fun onSuccess() = finish(true, 0)
                    override fun onFailure(code: Int, desc: String) = finish(false, code)
                    private fun finish(success: Boolean, code: Int) {
                        scope.launch {
                            actionJobs.remove(attempt)?.cancel()
                            actionReleases.remove(attempt)
                            if (gate.finish(attempt, success) && !success) {
                                Log.w(TAG, "stage=native_call event=action_failed code=$code")
                            }
                            release()
                            refresh()
                        }
                    }
                }
                try {
                    when (action) {
                        NativeCallAction.Accept -> CallStore.shared.accept(completion)
                        NativeCallAction.Reject -> CallStore.shared.reject(completion)
                        NativeCallAction.Hangup -> CallStore.shared.hangup(completion)
                    }
                } catch (error: RuntimeException) {
                    actionJobs.remove(attempt)?.cancel()
                    actionReleases.remove(attempt)
                    gate.finish(attempt, false)
                    release()
                    Log.w(TAG, "stage=native_call event=action_unavailable type=${error.javaClass.simpleName}")
                }
                return true
            }

            fun buildNotification(call: Snapshot, binding: Binding): Notification {
                val waiting = call.phase == NativeCallPhase.Waiting
                val localAlert = usesLocalAudio(call)
                val plan = NativeCallNotificationPolicy.plan(call.phase,
                    NativeCallForegroundService.isProtectingCurrentCall())
                val open = pendingIntent(binding, OPEN_CALL_ACTION, false)
                val person = Person.Builder().setName(call.callerName.ifBlank {
                    context.getString(R.string.callkit_notification_caller_fallback)
                }).setImportant(true).build()
                val style = if (waiting) NotificationCompat.CallStyle.forIncomingCall(person,
                    pendingIntent(binding, Constants.REJECT_CALL_ACTION, true),
                    pendingIntent(binding, Constants.ACCEPT_CALL_ACTION, false))
                else NotificationCompat.CallStyle.forOngoingCall(person,
                    pendingIntent(binding, Constants.HANGUP_CALL_ACTION, true))
                style.setIsVideo(call.isVideo)
                val builder = NotificationCompat.Builder(context, if (waiting)
                    NotificationFeature.INCOMING_CHANNEL_ID else NotificationFeature.ONGOING_CHANNEL_ID)
                    .setSmallIcon(R.drawable.tuicallkit_ic_float)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setPriority(if (waiting) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_LOW)
                    .setContentIntent(open).setOngoing(true).setAutoCancel(false)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOnlyAlertOnce(plan.onlyAlertOnce)
                    .setContentTitle(person.name)
                    .setContentText(context.getString(if (!waiting) R.string.callkit_notification_ongoing
                        else if (call.isVideo) R.string.callkit_invite_video_call else R.string.callkit_invite_audio_call))
                if (waiting && compatibleIncoming) {
                    builder.addAction(R.drawable.tuicallkit_ic_hangup,
                        context.getString(R.string.callview_text_decline),
                        pendingIntent(binding, Constants.REJECT_CALL_ACTION, true))
                    builder.addAction(R.drawable.tuicallkit_ic_float,
                        context.getString(R.string.callview_text_dialing),
                        pendingIntent(binding, Constants.ACCEPT_CALL_ACTION, false))
                } else builder.setStyle(style)
                if (waiting) {
                    builder.setTimeoutAfter((Constants.CALL_WAITING_MAX_TIME * 1_000L -
                        (SystemClock.elapsedRealtime() - binding.createdAt)).coerceAtLeast(1L))
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), AudioManager.STREAM_RING)
                            .setDefaults(Notification.DEFAULT_VIBRATE)
                    }
                    // Every incoming CallStyle publication requires FSI (or its OS denial marker)
                    // until it belongs to a foreground service. Android applies the user's FSI access.
                    if (plan.fullScreenIntent) {
                        builder.setFullScreenIntent(open, true)
                    }
                    // Compatibility-mode foreground alerts are played locally because OEMs can
                    // suppress even an accepted system notification. Silence its system audio to avoid overlap.
                    if (GlobalState.instance.enableMuteMode || localAlert) builder.setSilent(true)
                } else builder.setSilent(true)
                return builder.build().apply {
                    if (waiting && !GlobalState.instance.enableMuteMode && !localAlert) flags = flags or Notification.FLAG_INSISTENT
                }
            }

            private fun usesLocalAudio(call: Snapshot): Boolean = NativeCallNotificationPolicy.usesLocalAudio(
                call.phase == NativeCallPhase.Waiting, compatibleIncoming,
                DeviceUtils.isAppRunningForeground(context), persistentBanner?.isShowing == true)

            private fun pendingIntent(binding: Binding, action: String, broadcast: Boolean): PendingIntent {
                val intent = actionIntent(binding, action, broadcast)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                if (broadcast) return PendingIntent.getBroadcast(context, 0, intent, flags)
                // SystemUI is the sender for notification/FSI actions; do not grant general creator BAL privileges.
                return PendingIntent.getActivity(context, 0, intent, flags)
            }

            fun actionIntent(binding: Binding, action: String, broadcast: Boolean): Intent {
                val target = if (broadcast) IncomingCallReceiver::class.java else CallMainActivity::class.java
                return Intent(context, target).apply {
                    this.action = action
                    data = Uri.Builder().scheme("tuicallkit").authority("call").appendPath(binding.nonce)
                        .appendPath(action).build()
                    putExtra(EXTRA_CALL_ID, binding.identity.callId)
                    putExtra(EXTRA_SDK_APP_ID, binding.identity.sdkAppId)
                    putExtra(EXTRA_SELF_ID, binding.identity.selfId)
                    putExtra(EXTRA_BINDING, binding.nonce)
                    if (!broadcast) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
    }
}
