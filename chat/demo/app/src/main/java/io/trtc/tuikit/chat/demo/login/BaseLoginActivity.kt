package io.trtc.tuikit.chat.demo.login

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.tencent.mmkv.MMKV
import com.tencent.qcloud.tuicore.TUILogin
import io.trtc.tuikit.atomicx.theme.Theme
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.demo.xingdun.call.XingDunCallSessionInitializer
import io.trtc.tuikit.chat.demo.xingdun.launch.XingDunInteractiveIMLoginRetryPolicy
import io.trtc.tuikit.chat.demo.xingdun.main.XingDunMessageFirstFramePreloader
import io.trtc.tuikit.chat.demo.xingdun.push.XingDunPushManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunAuthenticationCallbackPolicy
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunCredentialRecoveryCoordinator
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.widgets.ActionItem
import io.trtc.tuikit.chat.uikit.components.widgets.ActionSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class BaseLoginActivity : BaseActivity() {

    override val requiresLogin: Boolean = false

    protected val themeStore by lazy { ThemeStore.shared(this) }
    private var loginScope: CoroutineScope? = null
    private var activeAuthenticationTicket: Long? = null
    protected var contentReady = false

    private lateinit var themeSwitcher: LinearLayout
    private lateinit var tvThemeValue: TextView
    private lateinit var languageSwitcher: LinearLayout
    private lateinit var tvLanguageValue: TextView

    private val appLanguageOptions by lazy {
        listOf(
            ActionItem(text = getString(R.string.demo_settings_zh_hans), value = "zh"),
            ActionItem(text = getString(R.string.demo_settings_zh_hant), value = "zh-Hant"),
            ActionItem(text = getString(R.string.demo_settings_en), value = "en"),
            ActionItem(text = getString(R.string.demo_settings_ar), value = "ar")
        )
    }

    protected fun setupCommonLoginViews() {
        themeSwitcher = findViewById(R.id.demo_themeSwitcher)
        tvThemeValue = findViewById(R.id.demo_tvThemeValue)
        languageSwitcher = findViewById(R.id.demo_languageSwitcher)
        tvLanguageValue = findViewById(R.id.demo_tvLanguageValue)

        themeSwitcher.setOnClickListener { showThemeSelector() }
        languageSwitcher.setOnClickListener { showLanguageSelector() }
        updateThemeSwitcherLabel()
        updateLanguageSwitcherLabel()
        contentReady = true
    }

    override fun onStart() {
        super.onStart()
        if (!contentReady) {
            return
        }
        loginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        loginScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyThemeColors(state.currentTheme.tokens.color)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        loginScope?.cancel()
        loginScope = null
    }

    abstract fun applyThemeColors(colors: ColorTokens)

    protected fun beginAuthenticationAttempt(): Long =
        XingDunCredentialRecoveryCoordinator.onAuthenticationStarted().also { activeAuthenticationTicket = it }

    protected fun ownsAuthenticationAttempt(ticket: Long): Boolean = XingDunAuthenticationCallbackPolicy.canApply(
        expectedTicket = ticket,
        activityTicket = activeAuthenticationTicket,
        authenticationOwned = XingDunCredentialRecoveryCoordinator.ownsAuthentication(ticket),
        activityActive = !isFinishing && !isDestroyed,
    )

    protected fun failAuthenticationAttempt(ticket: Long) {
        if (activeAuthenticationTicket == ticket) activeAuthenticationTicket = null
        XingDunCredentialRecoveryCoordinator.onAuthenticationFailed(ticket)
    }

    override fun onDestroy() {
        activeAuthenticationTicket?.let(::failAuthenticationAttempt)
        super.onDestroy()
    }

    protected fun performLogin(
        sdkAppId: Int,
        userId: String,
        userSig: String,
        retryTransientFailureOnce: Boolean = false,
        onSuccess: (() -> Unit)? = null,
        onFailure: ((Int, String) -> Unit)? = null,
        authenticationTicket: Long? = null,
    ) {
        val ticket = authenticationTicket ?: beginAuthenticationAttempt()
        if (!ownsAuthenticationAttempt(ticket)) return
        if (!matchesBusinessIdentity(sdkAppId, userId)) {
            failAuthenticationAttempt(ticket)
            onFailure?.invoke(-1, getString(R.string.xingdun_error_company_mismatch))
            return
        }
        performLoginAttempt(
            ticket = ticket,
            sdkAppId = sdkAppId,
            userId = userId,
            userSig = userSig,
            retriesRemaining = if (retryTransientFailureOnce) 1 else 0,
            onSuccess = onSuccess,
            onFailure = onFailure,
        )
    }

    private fun performLoginAttempt(
        ticket: Long,
        sdkAppId: Int,
        userId: String,
        userSig: String,
        retriesRemaining: Int,
        onSuccess: (() -> Unit)?,
        onFailure: ((Int, String) -> Unit)?,
    ) {
        try {
            LoginStore.shared.login(
                this, sdkAppId, userId, userSig,
                object : CompletionHandler {
                    override fun onSuccess() {
                        runOnUiThread {
                            if (!ownsAuthenticationAttempt(ticket)) return@runOnUiThread
                            if (!matchesAuthenticatedIdentity(sdkAppId, userId)) {
                                completeLoginFailure(ticket, -1, getString(R.string.xingdun_error_company_mismatch), onFailure)
                                return@runOnUiThread
                            }
                            val session = runCatching { XingDunSessionManager.currentSession() }.getOrNull()
                            if (session == null) {
                                completeLoginFailure(ticket, -1, getString(R.string.xingdun_im_connection_failed), onFailure)
                                return@runOnUiThread
                            }
                            lifecycleScope.launch {
                                try {
                                    XingDunCallSessionInitializer.initialize(this@BaseLoginActivity, sdkAppId, userId, userSig)
                                    XingDunPushManager.syncDeviceRegistration()
                                    XingDunMessageFirstFramePreloader.preload(this@BaseLoginActivity, session)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    completeLoginFailure(ticket, -1, getString(R.string.xingdun_im_connection_failed), onFailure)
                                    return@launch
                                }
                                // A new login may take ownership while first-frame data is being loaded.
                                if (!ownsAuthenticationAttempt(ticket)) return@launch
                                if (!matchesAuthenticatedIdentity(sdkAppId, userId)) {
                                    completeLoginFailure(ticket, -1, getString(R.string.xingdun_error_company_mismatch), onFailure)
                                    return@launch
                                }
                                MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, userId)
                                XingDunCredentialRecoveryCoordinator.onAuthenticated()
                                activeAuthenticationTicket = null
                                onSuccess?.invoke()
                                startActivity(Intent(this@BaseLoginActivity, MainActivity::class.java).apply {
                                    putExtra(MainActivity.EXTRA_TARGET_TAB, MainActivity.TAB_HOME)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                })
                            }
                        }
                    }

                    override fun onFailure(code: Int, desc: String) {
                        runOnUiThread {
                            if (!ownsAuthenticationAttempt(ticket)) return@runOnUiThread
                            if (XingDunInteractiveIMLoginRetryPolicy.shouldRetry(retriesRemaining, code)) {
                                Log.w(TAG, "IM login failed with code $code; retrying once")
                                lifecycleScope.launch {
                                    delay(XingDunInteractiveIMLoginRetryPolicy.RETRY_DELAY_MILLIS)
                                    if (!ownsAuthenticationAttempt(ticket)) return@launch
                                    if (!matchesBusinessIdentity(sdkAppId, userId)) {
                                        completeLoginFailure(ticket, -1, getString(R.string.xingdun_error_company_mismatch), onFailure)
                                        return@launch
                                    }
                                    performLoginAttempt(
                                        ticket = ticket,
                                        sdkAppId = sdkAppId,
                                        userId = userId,
                                        userSig = userSig,
                                        retriesRemaining = retriesRemaining - 1,
                                        onSuccess = onSuccess,
                                        onFailure = onFailure,
                                    )
                                }
                                return@runOnUiThread
                            }
                            completeLoginFailure(ticket, code, desc, onFailure)
                        }
                    }
                }
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            runOnUiThread {
                completeLoginFailure(ticket, -1, getString(R.string.xingdun_im_connection_failed), onFailure)
            }
        }
    }

    private fun matchesBusinessIdentity(sdkAppId: Int, userId: String): Boolean =
        runCatching { XingDunSessionManager.matchesCurrentIMIdentity(sdkAppId, userId) }.getOrDefault(false)

    private fun matchesAuthenticatedIdentity(sdkAppId: Int, userId: String): Boolean =
        matchesBusinessIdentity(sdkAppId, userId) &&
            LoginStore.shared.sdkAppID == sdkAppId && TUILogin.getLoginUser() == userId

    private fun completeLoginFailure(ticket: Long, code: Int, description: String, onFailure: ((Int, String) -> Unit)?) {
        if (!ownsAuthenticationAttempt(ticket)) return
        failAuthenticationAttempt(ticket)
        if (onFailure != null) onFailure(code, description)
        else Toast.makeText(this, getString(R.string.demo_login_failed, description), Toast.LENGTH_SHORT).show()
    }

    protected fun createButtonBackground(colors: ColorTokens): StateListDrawable {
        return StateListDrawable().apply {
            addState(
                intArrayOf(-android.R.attr.state_enabled),
                createButtonShape(colors.buttonColorPrimaryDisabled)
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                createButtonShape(colors.buttonColorPrimaryActive)
            )
            addState(intArrayOf(), createButtonShape(colors.buttonColorPrimaryDefault))
        }
    }

    private fun createButtonShape(fillColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = dpToPx(14).toFloat()
        }
    }

    protected fun createButtonTextColors(colors: ColorTokens): ColorStateList {
        return ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(colors.textColorButtonDisabled, colors.textColorButton)
        )
    }

    protected fun updateBackgroundPreservingPadding(view: View, background: Drawable) {
        val paddingStart = view.paddingStart
        val paddingTop = view.paddingTop
        val paddingEnd = view.paddingEnd
        val paddingBottom = view.paddingBottom
        view.background = background
        view.setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom)
    }

    protected fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun showThemeSelector() {
        val options = listOf(
            ActionItem(text = getString(R.string.demo_settings_theme_system), value = AppConstants.THEME_MODE_SYSTEM),
            ActionItem(text = getString(R.string.demo_settings_theme_light), value = AppConstants.THEME_MODE_LIGHT),
            ActionItem(text = getString(R.string.demo_settings_theme_dark), value = AppConstants.THEME_MODE_DARK)
        )
        ActionSheet.show(this, options) { selected ->
            val mode = selected.value as Int
            when (mode) {
                AppConstants.THEME_MODE_SYSTEM -> themeStore.setTheme(Theme.systemTheme(this))
                AppConstants.THEME_MODE_LIGHT -> themeStore.setTheme(Theme.lightTheme(this))
                AppConstants.THEME_MODE_DARK -> themeStore.setTheme(Theme.darkTheme(this))
            }
            updateThemeSwitcherLabel()
        }
    }

    protected fun showLanguageSelector() {
        ActionSheet.show(this, appLanguageOptions) { selected ->
            val tag = selected.value as String
            val targetLocales = LocaleListCompat.forLanguageTags(tag)
            MMKV.defaultMMKV().encode(AppConstants.KEY_APP_LANGUAGE, tag)
            if (AppCompatDelegate.getApplicationLocales() == targetLocales) {
                return@show
            }
            AppCompatDelegate.setApplicationLocales(targetLocales)
        }
    }

    private fun updateThemeSwitcherLabel() {
        tvThemeValue.text = when (themeStore.themeState.value.currentTheme.id) {
            "dark" -> getString(R.string.demo_settings_theme_dark)
            Theme.SYSTEM_THEME_ID -> getString(R.string.demo_settings_theme_system)
            else -> getString(R.string.demo_settings_theme_light)
        }
    }

    private fun updateLanguageSwitcherLabel() {
        tvLanguageValue.text = getCurrentLanguageDisplayName()
    }

    private fun getCurrentLanguageDisplayName(): String {
        val persistedTag = MMKV.defaultMMKV()
            .decodeString(AppConstants.KEY_APP_LANGUAGE, "").orEmpty()
        val currentTag = if (persistedTag.isNotBlank()) {
            persistedTag
        } else {
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }
        return when {
            currentTag.isBlank() -> getString(R.string.demo_settings_current_language)
            isTraditionalChinese(currentTag) -> getString(R.string.demo_settings_zh_hant)
            currentTag.startsWith("zh", ignoreCase = true) -> getString(R.string.demo_settings_zh_hans)
            currentTag.startsWith("en", ignoreCase = true) -> getString(R.string.demo_settings_en)
            currentTag.startsWith("ar", ignoreCase = true) -> getString(R.string.demo_settings_ar)
            else -> getString(R.string.demo_settings_current_language)
        }
    }

    private fun isTraditionalChinese(languageTag: String): Boolean {
        val normalizedTag = languageTag.lowercase()
        return normalizedTag.contains("hant") ||
            normalizedTag.contains("zh-hk") ||
            normalizedTag.contains("zh-tw") ||
            normalizedTag.contains("zh-mo")
    }

    companion object {
        private const val TAG = "BaseLoginActivity"
    }
}
