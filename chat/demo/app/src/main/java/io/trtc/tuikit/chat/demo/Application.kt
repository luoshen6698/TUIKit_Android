package io.trtc.tuikit.chat.demo

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionRegistry
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingActionConfig
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingCustomAction
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingScene
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.atomicxcore.api.login.LoginListener
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.xingdun.launch.XingDunLaunchActivity
import io.trtc.tuikit.chat.demo.xingdun.launch.XingDunEnterpriseAccessActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunCustomMessagePresentation
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunForegroundNotificationManager
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunFeatureActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBusinessActionHandler
import io.trtc.tuikit.chat.demo.xingdun.push.XingDunPushManager
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunRouter
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantSessionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Application : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var enterpriseRefreshJob: Job? = null
    private var startedActivityCount = 0
    private var hasObservedFirstForeground = false

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
        XingDunTenantSessionCoordinator.initialize(this)
        XingDunRouter.initialize(this)
        XingDunPushManager.initialize(this)
        XingDunCustomMessagePresentation.registerGlobalSummaries()
        XingDunForegroundNotificationManager.initialize(this)
        BusinessActionRegistry.handler = XingDunBusinessActionHandler(this)
        ChatSettingActionConfig.setCustomActionProvider { actionContext ->
            val groupID = actionContext.groupID
            if (actionContext.scene == ChatSettingScene.GROUP && !groupID.isNullOrBlank()) {
                listOf(
                    ChatSettingCustomAction(
                        title = actionContext.context.getString(R.string.xingdun_report_group),
                        onClick = { context ->
                            XingDunFeatureActivity.startReport(
                                context = context,
                                targetType = "team",
                                targetID = groupID,
                                displayName = actionContext.displayName ?: groupID,
                                displayID = groupID
                            )
                        }
                    )
                )
            } else {
                emptyList()
            }
        }

        applyLanguageFromSettings()

        MMKV.defaultMMKV().decodeBool(AppConstants.KEY_ENABLE_READ_RECEIPT, false).also {
            AppBuilderConfig.enableReadReceipt = it
        }

        LoginStore.shared.addLoginListener(loginListener)
        registerEnterpriseForegroundRefresh()
    }

    private fun redirectToLogin(messageResId: Int, clearSession: Boolean) {
        if (clearSession) {
            XingDunSessionManager.clear()
        }
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, "")
        Toast.makeText(this, getString(messageResId), Toast.LENGTH_LONG).show()
        startActivity(Intent(this, XingDunLaunchActivity::class.java).apply {
            putExtra(XingDunLaunchActivity.EXTRA_NOTICE_RES_ID, messageResId)
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

    private fun registerEnterpriseForegroundRefresh() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                val enteringForeground = startedActivityCount == 0
                startedActivityCount += 1
                if (!enteringForeground) return
                if (!hasObservedFirstForeground) {
                    hasObservedFirstForeground = true
                    return
                }
                refreshEnterpriseAfterForeground()
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun refreshEnterpriseAfterForeground() {
        if (XingDunSessionManager.currentEnterprise() == null) return
        enterpriseRefreshJob?.cancel()
        enterpriseRefreshJob = applicationScope.launch {
            runCatching { XingDunSessionManager.refreshSelectedEnterprise() }
                .onFailure { error ->
                    if (!XingDunSessionManager.shouldRetainCachedEnterprise(error)) {
                        redirectToEnterpriseSelection()
                    }
                }
        }
    }

    private fun redirectToEnterpriseSelection() {
        XingDunTenantSessionCoordinator.switchEnterprise(::openEnterpriseSelection)
    }

    private fun openEnterpriseSelection() {
        startActivity(Intent(this, XingDunEnterpriseAccessActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
    }

}
