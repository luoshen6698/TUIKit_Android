package io.trtc.tuikit.chat.demo.xingdun.features

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tencent.imsdk.v2.V2TIMMessage
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunLocalNotificationRoutes

/** Only handles SDK-delivered messages; it cannot replace a vendor push for a dead process. */
internal class XingDunMessageNotifications(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val tags = linkedMapOf<String, String>()
    private val delivered = linkedSetOf<String>()

    fun initializeChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        listOf(true to true, true to false, false to true, false to false).forEach { (sound, vibration) ->
            val label = when {
                sound && vibration -> R.string.xingdun_chat_notification_channel
                sound -> R.string.xingdun_chat_notification_channel_sound
                vibration -> R.string.xingdun_chat_notification_channel_vibration
                else -> R.string.xingdun_chat_notification_channel_silent
            }
            manager.createNotificationChannel(NotificationChannel(channelID(sound, vibration), context.getString(label),
                NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(if (sound) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else null,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                enableVibration(vibration)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
        }
    }

    fun show(message: V2TIMMessage, conversationID: String, title: String, accountKey: String,
             sound: Boolean, vibration: Boolean): Boolean {
        if (!XingDunLocalNotificationRoutes.isAuthenticatedAccount(accountKey) ||
            !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = channelID(sound, vibration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val key = accountKey + ":" + message.msgID.takeUnless { it.isNullOrBlank() }.orEmpty()
        if (!message.msgID.isNullOrBlank() && key in delivered) return false
        val tag = XingDunMessageNotificationIdentity.tag(accountKey, conversationID)
        val pending = XingDunLocalNotificationRoutes.contentIntent(context, tag, conversationID, accountKey) ?: return false
        val text = when (message.elemType) {
            V2TIMMessage.V2TIM_ELEM_TYPE_TEXT -> message.textElem?.text?.take(160)
            V2TIMMessage.V2TIM_ELEM_TYPE_IMAGE -> context.getString(R.string.xingdun_chat_notification_image)
            V2TIMMessage.V2TIM_ELEM_TYPE_VIDEO -> context.getString(R.string.xingdun_chat_notification_video)
            V2TIMMessage.V2TIM_ELEM_TYPE_SOUND -> context.getString(R.string.xingdun_chat_notification_audio)
            V2TIMMessage.V2TIM_ELEM_TYPE_FILE -> context.getString(R.string.xingdun_chat_notification_file)
            V2TIMMessage.V2TIM_ELEM_TYPE_FACE -> context.getString(R.string.xingdun_chat_notification_face)
            else -> null
        }?.takeIf { it.isNotBlank() } ?: context.getString(R.string.xingdun_chat_notification_new)
        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_xingdun_chat_notification)
            .setContentTitle(context.applicationInfo.loadLabel(context.packageManager))
            .setContentText(context.getString(R.string.xingdun_chat_notification_new)).build()
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_xingdun_chat_notification)
            .setContentTitle(title.take(80)).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
            .setContentIntent(pending).setAutoCancel(true).setOngoing(false)
            .apply {
                if (sound) setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                if (vibration) setVibrate(longArrayOf(0, 120))
            }.build()
        return try {
            manager.notify(tag, ID, notification)
            tags[conversationID] = tag
            while (tags.size > MAX_CONVERSATIONS) {
                val oldest = tags.keys.first()
                manager.cancel(tags.remove(oldest), ID)
            }
            if (!message.msgID.isNullOrBlank()) delivered += key
            while (delivered.size > 256) delivered.remove(delivered.first())
            Log.i("XingDunMessageNotification", "event=posted type=${message.elemType}")
            true
        } catch (error: RuntimeException) {
            Log.w("XingDunMessageNotification", "event=post_failed type=${error.javaClass.simpleName}")
            false
        }
    }

    fun clearConversation(conversationID: String) {
        val tag = tags.remove(conversationID) ?: XingDunLocalNotificationRoutes.accountKey()?.let {
            XingDunMessageNotificationIdentity.tag(it, conversationID)
        }
        tag?.let { manager.cancel(it, ID) }
    }

    fun clear() {
        tags.values.forEach { manager.cancel(it, ID) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.activeNotifications.filter { it.id == ID && it.tag?.startsWith("xd.chat.") == true }
                .forEach { manager.cancel(it.tag, ID) }
        }
        tags.clear()
        delivered.clear()
    }

    private fun channelID(sound: Boolean, vibration: Boolean) = "XingDunChatMessagesV1_${if (sound) 1 else 0}${if (vibration) 1 else 0}"
    private companion object { const val ID = 9911; const val MAX_CONVERSATIONS = 32 }
}
