package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import androidx.lifecycle.*
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi
import io.trtc.tuikit.chat.demo.xingdun.features.travel.safeTravelMedia
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.*

/** The same account identity opens My from each of the four home pages. */
class XingDunMineAvatarView(context: Context) : FrameLayout(context) {
    private val ui = TravelUi(context)
    private var observer: Job? = null
    init {
        background = ui.box(0xFF4C6FF4.toInt(), 20); clipToOutline = true
        contentDescription = context.getString(R.string.md_open); isFocusable = true
        setOnClickListener { (context as? MainActivity)?.openMineDrawer() }; render()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val owner = context as? LifecycleOwner ?: return
        observer = owner.lifecycleScope.launch { owner.repeatOnLifecycle(Lifecycle.State.STARTED) { LoginStore.shared.loginState.loginUserInfo.collect { render() } } }
    }
    override fun onDetachedFromWindow() { observer?.cancel(); super.onDetachedFromWindow() }
    private fun render() {
        removeAllViews()
        val session = XingDunSessionManager.currentSession(); val profile = LoginStore.shared.loginState.loginUserInfo.value?.takeIf { mineCanUseIMData(session?.timUserId, it.userID) }
        val name = profile?.nickname?.takeIf { it.isNotBlank() } ?: session?.nickname?.takeIf { it.isNotBlank() } ?: session?.username.orEmpty()
        addView(ui.text(name.takeLast(2).ifBlank { "·" }, 14f, -1).apply { gravity = Gravity.CENTER }, LayoutParams(-1, -1))
        profile?.avatarURL?.takeIf(::safeTravelMedia)?.let { addView(ui.image(it, 20), LayoutParams(-1, -1)) }
    }
}
