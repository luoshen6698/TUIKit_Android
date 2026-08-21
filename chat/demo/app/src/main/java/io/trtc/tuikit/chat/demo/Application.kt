package io.trtc.tuikit.chat.demo

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionRegistry
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.atomicxcore.api.login.LoginListener
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.xingdun.launch.XingDunLaunchActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBusinessActionHandler
import io.trtc.tuikit.chat.demo.xingdun.push.XingDunPushManager
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunRouter
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager

class Application : Application() {

    private val loginListener = object : LoginListener() {
        override fun onKickedOffline() {
            redirectToLogin(R.string.demo_force_offline, clearSession = true)
        }

        override fun onLoginExpired() {
            redirectToLogin(R.string.demo_login_expired, clearSession = false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
        XingDunSessionManager.initialize(this)
        XingDunRouter.initialize(this)
        XingDunPushManager.initialize(this)
        BusinessActionRegistry.handler = XingDunBusinessActionHandler(this)

        applyLanguageFromSettings()

        MMKV.defaultMMKV().decodeBool(AppConstants.KEY_ENABLE_READ_RECEIPT, false).also {
            AppBuilderConfig.enableReadReceipt = it
        }

        LoginStore.shared.addLoginListener(loginListener)
    }

    private fun redirectToLogin(messageResId: Int, clearSession: Boolean) {
        if (clearSession) {
            XingDunSessionManager.clear()
        }
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, "")
        Toast.makeText(this, getString(messageResId), Toast.LENGTH_LONG).show()
        startActivity(Intent(this, XingDunLaunchActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
    }

    private fun applyLanguageFromSettings() {
        val languageTag = MMKV.defaultMMKV().decodeString(AppConstants.KEY_APP_LANGUAGE, "").orEmpty()
        val targetLocales = if (languageTag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        if (AppCompatDelegate.getApplicationLocales() != targetLocales) {
            AppCompatDelegate.setApplicationLocales(targetLocales)
        }
    }

}
