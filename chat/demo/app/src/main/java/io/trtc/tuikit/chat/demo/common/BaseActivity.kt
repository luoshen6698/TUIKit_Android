package io.trtc.tuikit.chat.demo.common

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.imsdk.v2.V2TIMManager
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunCredentialRecoveryCoordinator
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunCredentialRecoveryState
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunProtectedSessionDestination
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunProtectedSessionPolicy
import io.trtc.tuikit.chat.demo.xingdun.launch.XingDunStartupActivity
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
        val hasStoredSession = XingDunSessionManager.hasStoredSession()
        val session = runCatching { XingDunSessionManager.currentSession() }.getOrNull()
        val loginUserID = TUILogin.getLoginUser().orEmpty().takeIf(String::isNotBlank)
            ?: LoginStore.shared.loginState.loginUserInfo.value?.userID
        val sdkMatches = session != null && LoginStore.shared.sdkAppID == session.sdkAppId
        val userMatches = session != null && loginUserID == session.timUserId
        val recoveryState = XingDunCredentialRecoveryCoordinator.state.value
        return when (XingDunProtectedSessionPolicy.destination(
            hasBusinessSession = session != null,
            hasStoredSession = hasStoredSession,
            recoveryState = recoveryState,
            sdkIdentityMatches = sdkMatches && userMatches,
            imIsLoggedIn = V2TIMManager.getInstance().loginStatus == V2TIMManager.V2TIM_STATUS_LOGINED,
        )) {
            XingDunProtectedSessionDestination.CONTENT -> false
            XingDunProtectedSessionDestination.RECOVERY -> {
                startActivity(Intent(this, XingDunStartupActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
                // Subclasses check isFinishing immediately after super.onCreate().
                finish()
                true
            }
            XingDunProtectedSessionDestination.LOGIN -> {
                finish()
                if (recoveryState != XingDunCredentialRecoveryState.REDIRECTING) {
                    XingDunCredentialRecoveryCoordinator.redirectToLogin(R.string.demo_login_expired)
                }
                true
            }
        }
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
