package com.tencent.qcloud.tuikit.tuicallkit

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.tencent.cloud.tuikit.engine.call.TUICallDefine
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine
import com.tencent.cloud.tuikit.engine.common.TUICommonDefine.Callback
import com.tencent.qcloud.tuicore.TUIConstants
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuikit.tuicallkit.common.data.Constants
import com.tencent.qcloud.tuikit.tuicallkit.common.data.Logger
import com.tencent.qcloud.tuikit.tuicallkit.common.metrics.KeyMetrics
import com.tencent.qcloud.tuikit.tuicallkit.common.utils.DeviceUtils
import com.tencent.qcloud.tuikit.tuicallkit.common.utils.PermissionRequest
import com.tencent.qcloud.tuikit.tuicallkit.manager.CallManager
import com.tencent.qcloud.tuikit.tuicallkit.manager.feature.NotificationFeature
import com.tencent.qcloud.tuikit.tuicallkit.state.GlobalState
import com.tencent.qcloud.tuikit.tuicallkit.view.CallAdapter
import com.tencent.qcloud.tuikit.tuicallkit.view.CallMainActivity
import com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner.IncomingNotificationBanner
import com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner.NativeCallIdentity
import com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner.NativeCallUiLifecyclePolicy
import com.trtc.tuikit.common.ui.floatwindow.FloatWindowManager
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast.Style
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.call.CallEndReason
import io.trtc.tuikit.atomicxcore.api.call.CallListener
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallParams
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantInfo
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class TUICallKitImpl private constructor(context: Context) : TUICallKit() {
    private val context = context.applicationContext ?: context
    private val mainHandler = Handler(Looper.getMainLooper())
    private var subscribeSelfStateJob: Job? = null
    private val callStatusObserver = object : CallListener() {
        override fun onCallStarted(callId: String, mediaType: CallMediaType) {
            if (selfIsCaller() && GlobalState.instance.enableAITranscriber) {
                CallManager.instance.startRealtimeTranscriber(callId)
            }
        }
        override fun onCallEnded(callId: String, mediaType: CallMediaType, reason: CallEndReason, userId: String) {
            val activeCall = CallStore.shared.observerState.activeCall.value
            val selfInfo = CallStore.shared.observerState.selfInfo.value
            if (activeCall.inviteeIds.size > 1 || activeCall.chatGroupId.isNotEmpty() || selfInfo.id == userId) {
                return
            }

            if (reason == CallEndReason.LineBusy) {
                mainHandler.post {
                    AtomicToast.show(context, context.getString(R.string.callkit_toast_other_party_busy), Style.INFO)
                }
            }
        }

        override fun onCallReceived(callId: String, mediaType: CallMediaType, userData: String) {
            KeyMetrics.countUV(KeyMetrics.EventId.RECEIVED, callId)
        }
    }

    init {
        IncomingNotificationBanner(context)
        registerObserver()
    }

    override fun setSelfInfo(nickname: String?, avatar: String?, completion: CompletionHandler?) {
        CallManager.instance.setSelfInfo(nickname, avatar, completion)
    }

    override fun calls(
        userIdList: List<String>, mediaType: CallMediaType, params: CallParams?, completion: CompletionHandler?
    ) {
        val selfStatus = CallStore.shared.observerState.selfInfo.value.status
        if (selfStatus != CallParticipantStatus.None) {
            completion?.onFailure(TUICallDefine.ERROR_PARAM_INVALID, "You are currently on a call.")
            AtomicToast.show(context, context.getString(R.string.callkit_toast_error_already_in_call), Style.ERROR)
            return
        }
        val list = userIdList.distinct().toMutableList()
        if (list.isEmpty()) {
            Logger.e(TAG, "calls failed, userIdList is empty")
            completion?.onFailure(TUICallDefine.ERROR_PARAM_INVALID, "calls failed, userIdList is empty")
            return
        }
        if (list.singleOrNull() == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
            AtomicToast.show(context, context.getString(R.string.callkit_toast_error_call_self), Style.ERROR)
            completion?.onFailure(TUICallDefine.ERROR_PARAM_INVALID, "calls failed, you cannot call yourself")
            return
        }
        PermissionRequest.requestPermissions(context, mediaType, object : PermissionCallback() {
            override fun onGranted() {
                CallManager.instance.calls(list, mediaType, params, object : CompletionHandler {
                    override fun onSuccess() {
                        startCallActivity()
                        completion?.onSuccess()
                    }

                    override fun onFailure(code: Int, desc: String) {
                        completion?.onFailure(code, desc)
                        CallManager.instance.reset()
                    }
                })
            }

            override fun onDenied() {
                Logger.w(TAG, "calls, request Permissions failed")
                completion?.onFailure(TUICallDefine.ERROR_PERMISSION_DENIED, "request Permissions failed")
            }
        })
    }

    override fun join(callId: String?, completion: CompletionHandler?) {
        val selfStatus = CallStore.shared.observerState.selfInfo.value.status
        if (selfStatus != CallParticipantStatus.None) {
            completion?.onFailure(TUICallDefine.ERROR_PARAM_INVALID, "You are currently on a call.")
            AtomicToast.show(context, context.getString(R.string.callkit_toast_error_already_in_call), Style.ERROR)
            return
        }
        PermissionRequest.requestPermissions(context, CallMediaType.Video, object : PermissionCallback() {
            override fun onGranted() {
                CallManager.instance.join(callId, object : Callback {
                    override fun onSuccess() {
                        IncomingNotificationBanner.onCallJoined(context, callId.orEmpty())
                        startCallActivity()
                        completion?.onSuccess()
                    }

                    override fun onError(errCode: Int, errMsg: String?) {
                        completion?.onFailure(errCode, errMsg ?: "")
                        CallManager.instance.reset()
                    }
                })
            }

            override fun onDenied() {
                Logger.w(TAG, "join, request Permissions failed")
                completion?.onFailure(TUICallDefine.ERROR_PERMISSION_DENIED, "request Permissions failed")
            }
        })
    }

    override fun callExperimentalAPI(jsonStr: String) {
        CallManager.instance.callExperimentalAPI(jsonStr)
    }

    override fun setCallingBell(filePath: String?) {
        CallManager.instance.setCallingBell(filePath)
    }

    override fun enableMuteMode(enable: Boolean) {
        CallManager.instance.enableMuteMode(enable)
    }

    override fun enableFloatWindow(enable: Boolean) {
        CallManager.instance.enableFloatWindow(enable)
    }

    override fun enableVirtualBackground(enable: Boolean) {
        CallManager.instance.enableVirtualBackground(enable)
    }

    override fun enableAITranscriber(enable: Boolean) {
        CallManager.instance.enableAITranscriber(enable)
    }

    override fun enableIncomingBanner(enable: Boolean) {
        CallManager.instance.enableIncomingBanner(enable)
    }

    override fun setScreenOrientation(orientation: Int) {
        CallManager.instance.setScreenOrientation(orientation)
    }

    override fun disableControlButton(button: Constants.ControlButton?) {
        CallManager.instance.disableControlButton(button)
    }

    override fun setAdapter(adapter: CallAdapter?) {
        Logger.i(TAG, "setAdapter, adapter: $adapter")
        GlobalState.instance.callAdapter = adapter
    }

    fun queryOfflineCall() {
        if (FloatWindowManager.sharedInstance().isShowing) {
            Logger.w(TAG, "queryOfflineCall, float window is showing")
            return
        }

        Logger.i(TAG, "queryOfflineCall start")
        val selfUser = CallStore.shared.observerState.selfInfo.value
        val mediaType = CallStore.shared.observerState.activeCall.value.mediaType
        if (CallParticipantStatus.Accept == selfUser.status) {
            return
        }

        if (null == mediaType) {
            Logger.w(TAG, "queryOfflineCall, current status is Unknown")
            return
        }

        // An authenticated foreground launch can display the SDK call UI. Background presentation
        // belongs to the system notification, regardless of an OEM background-launch permission.
        if (DeviceUtils.isAppRunningForeground(context)) startCallActivity()
    }

    private fun registerObserver() {
        CallStore.shared.addListener(callStatusObserver)
        subscribeSelfStateJob = CoroutineScope(Dispatchers.Main).launch {
            supervisorScope {
                launch { observeSelfInfo() }
            }
        }
    }

    private suspend fun observeSelfInfo() {
        CallStore.shared.observerState.selfInfo.collect { selfInfo ->
            Logger.i(TAG, "stage=native_call event=call_state status=${selfInfo.status}")
            notifyInternalEvent()
            if (selfInfo.status == CallParticipantStatus.None) {
                CallManager.instance.reset()
                return@collect
            }
            if (selfInfo.status != CallParticipantStatus.Waiting || selfIsCaller()) {
                return@collect
            }
            handleNewCall()
        }
    }

    private fun handleNewCall() {
        if (IncomingNotificationBanner.areNotificationsSuspended()) return
        if (NotificationFeature.canShowIncomingCall(context)) {
            IncomingNotificationBanner(context).showNotification(CallParticipantInfo()) {
                val self = CallStore.shared.observerState.selfInfo.value
                if (self.status == CallParticipantStatus.Waiting && !selfIsCaller() &&
                    DeviceUtils.isAppRunningForeground(context)) startFullScreenView()
            }
            return
        }
        // A disabled/blocked notification is not permission to pull an Activity from the background.
        if (DeviceUtils.isAppRunningForeground(context)) startFullScreenView()
        else Logger.i(TAG, "stage=native_call event=background_notification_unavailable")
    }

    private fun startFullScreenView() {
        Logger.i(TAG, "startFullScreenView")
        val state = CallStore.shared.observerState
        val call = state.activeCall.value
        val expected = NativeCallIdentity(call.callId, LoginStore.shared.sdkAppID, state.selfInfo.value.id)
        val mediaType = call.mediaType
        fun canPresent(): Boolean {
            val current = state.activeCall.value
            val self = state.selfInfo.value
            return !IncomingNotificationBanner.areNotificationsSuspended() &&
                NativeCallUiLifecyclePolicy.canPresentIncoming(expected,
                    NativeCallIdentity(current.callId, LoginStore.shared.sdkAppID, self.id),
                    mediaType == current.mediaType,
                    self.status == CallParticipantStatus.Waiting && !selfIsCaller(),
                    DeviceUtils.isAppRunningForeground(context))
        }
        if (!canPresent()) return
        PermissionRequest.requestPermissions(context, mediaType, object
            : PermissionCallback() {
            override fun onGranted() {
                if (canPresent()) {
                    Logger.i(TAG, "startFullScreenView requestPermissions onGranted")
                    startCallActivity()
                }
            }

            override fun onDenied() {
                if (canPresent()) {
                    CallStore.shared.reject(null)
                    CallManager.instance.reset()
                }
            }
        })
    }

    private fun startCallActivity() {
        val intent = Intent(context, CallMainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    private fun notifyInternalEvent() {
        val selfUser = CallStore.shared.observerState.selfInfo.value
        if (selfUser.status == CallParticipantStatus.None) {
            TUICore.notifyEvent(
                TUIConstants.Privacy.EVENT_ROOM_STATE_CHANGED, TUIConstants.Privacy.EVENT_SUB_KEY_ROOM_STATE_STOP, null
            )
            if (selfIsCaller()) {
                TUICore.notifyEvent(EVENT_KEY_TIME_LIMIT, EVENT_SUB_KEY_COUNTDOWN_END, null)
            }
            return
        }
        if (selfUser.status == CallParticipantStatus.Accept) {
            if (selfIsCaller()) {
                TUICore.notifyEvent(EVENT_KEY_TIME_LIMIT, EVENT_SUB_KEY_COUNTDOWN_START, null)
            }
            if (TUICore.getService(TUIConstants.Service.TUI_PRIVACY) != null) {
                val map = HashMap<String, Any?>()
                map[TUIConstants.Privacy.PARAM_DIALOG_CONTEXT] = context
                TUICore.callService(
                    TUIConstants.Service.TUI_PRIVACY, TUIConstants.Privacy.METHOD_ANTO_FRAUD_REMINDER, map, null
                )
            }
        }
    }

    private fun selfIsCaller(): Boolean {
        val selfId = CallStore.shared.observerState.selfInfo.value.id
        val callerId = CallStore.shared.observerState.activeCall.value.inviterId
        return selfId == callerId
    }

    companion object {
        private const val TAG = "IncomingView"
        private const val EVENT_KEY_TIME_LIMIT = "RTCRoomTimeLimitService"
        private const val EVENT_SUB_KEY_COUNTDOWN_START = "CountdownStart"
        private const val EVENT_SUB_KEY_COUNTDOWN_END = "CountdownEnd"
        private var instance: TUICallKitImpl? = null
        fun createInstance(context: Context): TUICallKitImpl {
            if (instance == null) {
                synchronized(TUICallKitImpl::class.java) {
                    if (instance == null) {
                        instance = TUICallKitImpl(context)
                    }
                }
            }
            return instance!!
        }
    }
}
