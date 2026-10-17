package com.tencent.qcloud.tuikit.tuicallkit.manager.feature

import android.content.Context
import android.app.NotificationManager
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.tencent.qcloud.tuicore.util.TUIBuild
import com.tencent.qcloud.tuikit.tuicallkit.common.utils.DeviceUtils
import com.tencent.qcloud.tuikit.tuicallkit.state.GlobalState
import com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner.IncomingNotificationBanner
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

class CallingVibratorFeature(context: Context) {
    private val scope = MainScope()
    private val context: Context = context.applicationContext
    private val vibrator: Vibrator
    private var isVibrating = false

    init {
        if (TUIBuild.getVersionInt() >= Build.VERSION_CODES.S) {
            val vibratorManager = this.context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator = vibratorManager.defaultVibrator
        } else {
            vibrator = this.context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val ownsObserver = synchronized(CallingVibratorFeature::class.java) {
            if (observerOwner == null) { observerOwner = this; true } else false
        }
        if (ownsObserver) registerObserver() else scope.cancel()
    }

    private fun registerObserver() {
        scope.launch {
            val state = CallStore.shared.observerState
            combine(state.selfInfo, state.activeCall, IncomingNotificationBanner.incomingPresentation) {
                    self, _, _ -> self
            }.collect { selfInfo ->
                if (isCaller(selfInfo.id)) {
                    return@collect
                }
                when (selfInfo.status) {
                    CallParticipantStatus.Waiting -> {
                        if (canVibrateLocally()) startVibrating() else stopVibrating()
                    }
                    else -> stopVibrating()
                }
            }
        }
    }

    private fun startVibrating() {
        if (isVibrating) return
        if (vibrator.hasVibrator()) {
            val pattern = longArrayOf(0, 500, 1500)
            isVibrating = true
            if (TUIBuild.getVersionInt() >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createWaveform(pattern, 1)
                vibrator.vibrate(vibrationEffect)
            } else {
                vibrator.vibrate(pattern, 1)
            }
        }
    }

    private fun stopVibrating() {
        if (isVibrating) {
            vibrator.cancel()
        }
        isVibrating = false
    }

    private fun isCaller(userId: String): Boolean {
        val callerId = CallStore.shared.observerState.activeCall.value.inviterId
        return callerId == userId
    }

    private fun canVibrateLocally(): Boolean {
        if (GlobalState.instance.enableMuteMode || !IncomingNotificationBanner.needsLocalIncomingRinging(context)) return false
        if (!NotificationFeature.allowsLocalIncomingVibration(context)) return false
        if ((context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode == AudioManager.RINGER_MODE_SILENT) return false
        return Build.VERSION.SDK_INT < 23 || (context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager).currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
    }

    companion object {
        private var observerOwner: CallingVibratorFeature? = null
        fun stopCalleeVibration() { observerOwner?.stopVibrating() }
    }
}
