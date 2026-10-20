package com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner

import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import com.tencent.qcloud.tuicore.util.ScreenUtil
import com.tencent.qcloud.tuicore.util.TUIBuild
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.common.data.Constants
import com.tencent.qcloud.tuikit.tuicallkit.common.data.Logger
import com.tencent.qcloud.tuikit.tuicallkit.common.metrics.KeyMetrics
import com.tencent.qcloud.tuikit.tuicallkit.manager.CallManager
import com.tencent.qcloud.tuikit.tuicallkit.state.ViewState
import com.trtc.tuikit.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantInfo

/** A single-use answer surface whose call lifecycle belongs to the notification controller. */
class IncomingFloatBanner(context: Context) : RelativeLayout(context) {
    private val appContext: Context = context.applicationContext
    private val windowManager: WindowManager = appContext.getSystemService(WINDOW_SERVICE) as WindowManager
    private val padding = 40
    private var dismissed = false
    private var windowAddAttempted = false
    var isShowing = false
        private set

    fun showIncomingView(user: CallParticipantInfo): Boolean {
        if (dismissed) return false
        if (isShowing) return true
        return try {
            if (!initView(user) || dismissed) {
                dismiss()
                return false
            }
            // Track WindowManager ownership even before its first attachment traversal.
            windowAddAttempted = true
            windowManager.addView(this, viewParams)
            isShowing = true
            CallManager.instance.viewState.router.set(ViewState.ViewRouter.Banner)
            KeyMetrics.countUV(KeyMetrics.EventId.WAKEUP,
                CallStore.shared.observerState.activeCall.value.callId)
            true
        } catch (error: RuntimeException) {
            Logger.w(TAG, "stage=native_call event=float_banner_show_failed")
            // addView can fail after WindowManager has started registering the view.
            removeWindow()
            dismissed = true
            removeAllViews()
            false
        }
    }

    fun dismiss() {
        if (dismissed) return
        dismissed = true
        removeWindow()
        removeAllViews()
    }

    private fun removeWindow() {
        if (!windowAddAttempted) return
        try {
            windowManager.removeViewImmediate(this)
        } catch (error: RuntimeException) {
            Logger.w(TAG, "stage=native_call event=float_banner_remove_unavailable")
        } finally {
            windowAddAttempted = false
            isShowing = false
        }
    }

    private fun initView(caller: CallParticipantInfo): Boolean {
        LayoutInflater.from(context).inflate(R.layout.tuicallkit_incoming_float_view, this, true)
        val imageFloatAvatar = findViewById<ImageView>(R.id.img_float_avatar)
        val textFloatTitle = findViewById<TextView>(R.id.tv_float_title)
        val textFloatDescription = findViewById<TextView>(R.id.tv_float_desc)
        val imageReject = findViewById<ImageView>(R.id.btn_float_decline)
        val imageAccept = findViewById<ImageView>(R.id.btn_float_accept)

        imageFloatAvatar.setImageResource(R.drawable.tuicallkit_ic_avatar)
        if (caller.avatarURL.isNotBlank()) {
            try {
                ImageLoader.load(appContext, imageFloatAvatar, caller.avatarURL, R.drawable.tuicallkit_ic_avatar)
            } catch (error: RuntimeException) {
                Logger.w(TAG, "stage=native_call event=float_banner_avatar_unavailable")
            }
        }
        textFloatTitle.text = caller.name.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.callkit_notification_caller_fallback)

        val mediaType = CallStore.shared.observerState.activeCall.value.mediaType
        textFloatDescription.text = getTextDescription()
        val rejectIntent = IncomingNotificationBanner.createActionIntent(context, Constants.REJECT_CALL_ACTION)
        val acceptIntent = IncomingNotificationBanner.createActionIntent(context, Constants.ACCEPT_CALL_ACTION)
        val openIntent = IncomingNotificationBanner.createActionIntent(context, "open_call_action")
        if (!IncomingNotificationBanner.isCurrentNotificationIntent(rejectIntent) ||
            !IncomingNotificationBanner.isCurrentNotificationIntent(acceptIntent) ||
            !IncomingNotificationBanner.isCurrentNotificationIntent(openIntent)) return false

        imageReject.setOnClickListener {
            try {
                IncomingNotificationBanner.handleNotificationAction(context, rejectIntent)
            } catch (error: RuntimeException) {
                Logger.w(TAG, "stage=native_call event=float_banner_reject_failed")
            }
        }

        setOnClickListener { openCall(openIntent) }

        when (mediaType) {
            CallMediaType.Video -> imageAccept.setBackgroundResource(R.drawable.tuicallkit_ic_dialing_video)
            else -> imageAccept.setBackgroundResource(R.drawable.tuicallkit_bg_dialing)
        }
        imageAccept.setOnClickListener { openCall(acceptIntent) }
        return true
    }

    private fun openCall(intent: Intent?) {
        if (dismissed || !isShowing || !IncomingNotificationBanner.isCurrentNotificationIntent(intent)) return
        try {
            context.startActivity(intent)
        } catch (error: RuntimeException) {
            Logger.w(TAG, "stage=native_call event=float_banner_open_failed")
        }
    }

    private val viewParams: WindowManager.LayoutParams
        get() {
            val windowLayoutParams = WindowManager.LayoutParams()
            if (TUIBuild.getVersionInt() >= Build.VERSION_CODES.O) {
                windowLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                windowLayoutParams.type = WindowManager.LayoutParams.TYPE_PHONE
            }
            windowLayoutParams.flags = (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            windowLayoutParams.gravity = Gravity.START or Gravity.TOP
            windowLayoutParams.x = padding
            windowLayoutParams.y = 0
            windowLayoutParams.width = ScreenUtil.getScreenWidth(appContext) - padding * 2
            windowLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            windowLayoutParams.format = PixelFormat.TRANSPARENT
            return windowLayoutParams
        }

    private fun getTextDescription(): String {
        val activeCall = CallStore.shared.observerState.activeCall.value
        return when {
            activeCall.inviteeIds.size > 1 || activeCall.chatGroupId.isNotEmpty() ->
                appContext.getString(R.string.callkit_invite_multi_call)
            activeCall.mediaType == CallMediaType.Video ->
                appContext.getString(R.string.callkit_invite_video_call)
            else ->
                appContext.getString(R.string.callkit_invite_audio_call)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        if (!isShowing || !isAttachedToWindow) return
        try {
            windowManager.updateViewLayout(this, viewParams)
        } catch (error: RuntimeException) {
            Logger.w(TAG, "stage=native_call event=float_banner_update_failed")
            dismiss()
        }
    }

    companion object {
        private const val TAG = "IncomingViewFloat"
    }
}
