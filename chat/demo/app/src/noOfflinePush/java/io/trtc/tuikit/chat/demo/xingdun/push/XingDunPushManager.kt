package io.trtc.tuikit.chat.demo.xingdun.push

import android.content.Context

/** Build without offline push SDKs; session teardown must still complete. */
object XingDunPushManager {
    fun initialize(@Suppress("UNUSED_PARAMETER") context: Context) = Unit

    fun syncDeviceRegistration() = Unit

    fun unregisterDevice(onComplete: () -> Unit) {
        onComplete()
    }
}
