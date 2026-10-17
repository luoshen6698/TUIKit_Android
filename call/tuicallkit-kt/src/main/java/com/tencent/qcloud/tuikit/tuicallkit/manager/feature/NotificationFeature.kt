package com.tencent.qcloud.tuikit.tuicallkit.manager.feature

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.tencent.qcloud.tuikit.tuicallkit.R

class NotificationFeature(ctx: Context) {
    private val context: Context = ctx.applicationContext
    private val channelGroupId = "callKitChannelGroupId"

    // channelId only takes effect for the first time. If you want to change it,you need to uninstall the app and reinstall it.
    fun registerNotificationBannerChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelGroupName = context.getString(R.string.callkit_notification_channel_group_id)
            val channelGroup = NotificationChannelGroup(channelGroupId, channelGroupName)
            nm.createNotificationChannelGroup(channelGroup)

            val channelName = context.getString(R.string.callkit_notification_channel_id)
            val channel = NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH)
            channel.group = channelGroupId
            channel.enableLights(true)
            channel.enableVibration(true)
            channel.setShowBadge(true)
            channel.setSound(null, null)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(channel)

            val incoming = NotificationChannel(
                INCOMING_CHANNEL_ID,
                context.getString(R.string.callkit_incoming_call_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                group = channelGroupId
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
            }
            nm.createNotificationChannel(incoming)
            nm.createNotificationChannel(NotificationChannel(
                ONGOING_CHANNEL_ID,
                context.getString(R.string.callkit_ongoing_call_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                group = channelGroupId
                setSound(null, null)
                enableVibration(false)
            })
        }
    }

    companion object {
        const val CHANNEL_ID = "CallKitChannelId"
        const val INCOMING_CHANNEL_ID = "CallKitIncomingCallsV2"
        const val ONGOING_CHANNEL_ID = "CallKitOngoingCallsV2"

        fun allowsLocalIncomingSound(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(INCOMING_CHANNEL_ID) ?: return true
            return channel.importance != NotificationManager.IMPORTANCE_NONE && channel.sound != null
        }

        fun allowsLocalIncomingVibration(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(INCOMING_CHANNEL_ID) ?: return true
            return channel.importance != NotificationManager.IMPORTANCE_NONE && channel.shouldVibrate()
        }

        fun canShowIncomingCall(context: Context): Boolean {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(INCOMING_CHANNEL_ID) ?: return true
            if (channel.importance == NotificationManager.IMPORTANCE_NONE) return false
            if (Build.VERSION.SDK_INT >= 28 && channel.group != null &&
                manager.getNotificationChannelGroup(channel.group)?.isBlocked == true) return false
            return true
        }
    }
}
