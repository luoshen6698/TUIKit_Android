package io.trtc.tuikit.chat.uikit.components.presence

import android.view.View
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar

class PresenceAvatarBinding(private val avatar: Avatar) : View.OnAttachStateChangeListener {
    private var userID: String? = null
    init { avatar.addOnAttachStateChangeListener(this) }
    fun bind(user: String?) {
        UserPresenceStore.release(this)
        userID = user
        avatar.contentDescription = null
        avatar.setStatus(Avatar.AvatarStatus.None)
        if (avatar.isAttachedToWindow) observe()
    }
    private fun observe() {
        userID?.takeIf(String::isNotBlank)?.let { user -> UserPresenceStore.observe(this, user) { status ->
            avatar.contentDescription = avatar.context.getString(when (status) {
                UserPresence.ONLINE -> io.trtc.tuikit.chat.uikit.R.string.uikit_presence_online
                UserPresence.OFFLINE -> io.trtc.tuikit.chat.uikit.R.string.uikit_presence_offline
                UserPresence.UNKNOWN -> io.trtc.tuikit.chat.uikit.R.string.uikit_presence_unknown
            })
            avatar.setStatus(when (status) {
                UserPresence.ONLINE -> Avatar.AvatarStatus.Online
                UserPresence.OFFLINE -> Avatar.AvatarStatus.Offline
                UserPresence.UNKNOWN -> Avatar.AvatarStatus.None
            })
        } }
    }
    override fun onViewAttachedToWindow(view: View) = observe()
    override fun onViewDetachedFromWindow(view: View) { UserPresenceStore.release(this) }
}
