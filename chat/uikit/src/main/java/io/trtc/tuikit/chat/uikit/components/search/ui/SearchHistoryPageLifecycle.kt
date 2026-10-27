package io.trtc.tuikit.chat.uikit.components.search.ui

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** Hidden, detached and paused search pages cannot retain publication authority. */
internal class SearchHistoryPageLifecycle(private val view: View, private val active: (Boolean) -> Unit) : LifecycleEventObserver {
    private var lifecycle: Lifecycle? = null
    fun attach() {
        var context: Context? = view.context
        while (context != null) {
            if (context is LifecycleOwner) { lifecycle = context.lifecycle; break }
            val next = (context as? ContextWrapper)?.baseContext
            if (next === context) break
            context = next
        }
        lifecycle?.addObserver(this)
        update()
    }
    fun update() { active(view.isAttachedToWindow && view.isShown && lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) }
    fun detach() { active(false); lifecycle?.removeObserver(this); lifecycle = null }
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) = update()
}
