package io.trtc.tuikit.chat.demo.common

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.login.LoginStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.demo.xingdun.launch.XingDunLaunchActivity
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantSessionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class BaseActivity : AppCompatActivity() {

    private val themeStore by lazy { ThemeStore.shared(this) }
    private var themeScope: CoroutineScope? = null

    protected open val requiresLogin: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (redirectToLoginIfNeeded()) {
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        updateWindowAppearance(themeStore.themeState.value.currentTheme.tokens.color)
    }

    private fun redirectToLoginIfNeeded(): Boolean {
        if (!requiresLogin) {
            return false
        }
        val session = XingDunSessionManager.currentSession()
        val loginStatus = LoginStore.shared.loginState.loginStatus.value
        val loginUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID
        val sdkMatches = session != null && LoginStore.shared.sdkAppID == session.sdkAppId
        val userMatches = session != null && loginUserID == session.timUserId
        if (loginStatus != LoginStatus.UNLOGIN && sdkMatches && userMatches) {
            return false
        }
        val openLogin = {
            startActivity(Intent(this, XingDunLaunchActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
        }
        if (loginStatus == LoginStatus.UNLOGIN) {
            openLogin()
        } else {
            XingDunTenantSessionCoordinator.logout(openLogin)
        }
        return true
    }

    override fun onStart() {
        super.onStart()
        themeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        themeScope?.launch {
            themeStore.themeState.collectLatest { state ->
                updateWindowAppearance(state.currentTheme.tokens.color)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        themeScope?.cancel()
        themeScope = null
    }

    protected open fun appearanceLightStatusBarsOverride(): Boolean? = null

    private fun updateWindowAppearance(colors: ColorTokens) {
        window.setBackgroundDrawable(ColorDrawable(colors.bgColorDefault))
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val isLight = isColorLight(colors.bgColorOperate)
        controller.isAppearanceLightStatusBars = appearanceLightStatusBarsOverride() ?: isLight
        controller.isAppearanceLightNavigationBars = isLight
    }

    protected fun isColorLight(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
        return luminance > 0.5
    }
}
