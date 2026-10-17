package com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** Media protection started only after the visible call UI has granted permissions. */
class NativeCallForegroundService : Service() {
    private var protectedCall: Intent? = null

    override fun onCreate() {
        super.onCreate()
        activeService = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            return startMediaProtection(intent)
        } catch (error: RuntimeException) {
            Log.w(TAG, "stage=native_call event=media_service_failed stage=build_or_publish " +
                "type=${error.javaClass.simpleName} reason=${NativeCallNotificationPolicy.failureReason(error.message)}")
            detachAndStop()
            return START_NOT_STICKY
        }
    }

    private fun startMediaProtection(intent: Intent?): Int {
        val notification = if (intent != null && IncomingNotificationBanner.isCurrentNotificationIntent(intent))
            IncomingNotificationBanner.ongoingNotification(this, intent) else null
        if (notification == null || !hasMediaPermissions(this)) {
            // A delayed start for an older call must not stop this instance's newer accepted call.
            if (protectedCall?.let { IncomingNotificationBanner.isCurrentNotificationIntent(it) } != true ||
                !IncomingNotificationBanner.isAcceptedCallCurrent()) detachAndStop()
            return START_NOT_STICKY
        }
        try {
            val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    (if (IncomingNotificationBanner.acceptedCallIsVideo()) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0)
            } else 0
            ServiceCompat.startForeground(this, IncomingNotificationBanner.NOTIFICATION_ID, notification, types)
            protectedCall = intent?.let(::Intent)
            if (intent != null) IncomingNotificationBanner.foregroundNotificationPosted(this, intent)
            Log.i(TAG, "stage=native_call event=media_service_started")
        } catch (error: RuntimeException) {
            Log.w(TAG, "stage=native_call event=media_service_failed type=${error.javaClass.simpleName}")
            detachAndStop()
        }
        return START_NOT_STICKY
    }

    private fun detachAndStop() {
        // Detach before stopping so a late service removal cannot remove the next call's same-ID notification.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        protectedCall = null
        stopSelf()
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NativeCallNotification"
        @Volatile private var activeService: NativeCallForegroundService? = null

        internal fun isProtectingCurrentCall(): Boolean = activeService?.protectedCall?.let {
            IncomingNotificationBanner.isAcceptedCallCurrent() && IncomingNotificationBanner.isCurrentNotificationIntent(it)
        } == true

        internal fun updateNotification(notification: android.app.Notification): Boolean {
            val active = activeService ?: return false
            if (!isProtectingCurrentCall() || !hasMediaPermissions(active)) return false
            return try {
                val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        (if (IncomingNotificationBanner.acceptedCallIsVideo()) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0)
                } else 0
                ServiceCompat.startForeground(active, IncomingNotificationBanner.NOTIFICATION_ID, notification, types)
                true
            } catch (error: RuntimeException) {
                Log.w(TAG, "stage=native_call event=ongoing_update_failed type=${error.javaClass.simpleName}")
                false
            }
        }

        fun start(context: Context) {
            if (!IncomingNotificationBanner.isAcceptedCallCurrent() || !hasMediaPermissions(context)) return
            val intent = IncomingNotificationBanner.createActionIntent(context, "ongoing_media_service")
                ?.setClass(context, NativeCallForegroundService::class.java) ?: return
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (error: RuntimeException) {
                // A background-start/while-in-use restriction must not crash call recovery.
                Log.w(TAG, "stage=native_call event=media_service_start_failed type=${error.javaClass.simpleName}")
            }
        }

        fun stop(context: Context) {
            val active = activeService
            if (active != null) active.detachAndStop()
            else context.stopService(Intent(context, NativeCallForegroundService::class.java))
        }

        private fun hasMediaPermissions(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                (!IncomingNotificationBanner.acceptedCallIsVideo() ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
}
