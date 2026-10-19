package io.trtc.tuikit.chat.demo.xingdun.features

import java.security.MessageDigest

internal object XingDunMessageNotificationIdentity {
    fun tag(account: String, conversationID: String): String = "xd.chat." + MessageDigest.getInstance("SHA-256")
        .digest((account + "\u0000" + conversationID).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
