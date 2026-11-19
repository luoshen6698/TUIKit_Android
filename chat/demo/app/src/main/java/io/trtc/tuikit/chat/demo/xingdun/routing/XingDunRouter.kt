package io.trtc.tuikit.chat.demo.xingdun.routing

import android.content.Context
import android.content.Intent
import com.tencent.mmkv.MMKV
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.qcloud.tuicore.TUILogin
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.demo.xingdun.launch.XingDunStartupActivity
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunCredentialRecoveryCoordinator
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunCredentialRecoveryState
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantBoundary
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantSessionCoordinator

object XingDunRouter {

    private lateinit var appContext: Context
    private var navigationReadyScope: String? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /** Startup may replace the task after authentication; no chat may be opened before Main is ready. */
    @Synchronized
    fun markStartupPending() {
        navigationReadyScope = null
    }

    @Synchronized
    fun markMainReady() {
        navigationReadyScope = currentScope()?.key
    }

    @Synchronized
    fun routeNotification(extension: String?) {
        if (!stageNotification(extension)) return
        if (navigationReadyScope != currentScope()?.key) {
            // The vendor click activity can finish without opening our launcher on a cold start.
            // Keep the account-scoped destination and run the normal authenticated startup first.
            appContext.startActivity(Intent(appContext, XingDunStartupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        } else {
            consumePendingRoute()
        }
    }

    /** Launcher intents stage only; authentication/Main owns the subsequent safe consumption. */
    @Synchronized
    fun stageNotification(extension: String?): Boolean {
        val destination = XingDunNotificationPayloadParser.parse(extension) ?: return false
        val scope = currentScope()
        if (scope == null || XingDunTenantSessionCoordinator.isLeaving()) {
            clearPendingRoute()
            return false
        }
        if (!scope.allows(destination)) return false
        val store = MMKV.defaultMMKV()
        if (!store.encode(KEY_PENDING_PUSH_ROUTE, XingDunNotificationPayloadParser.encode(destination)) ||
            !store.encode(KEY_PENDING_PUSH_SCOPE, scope.key)) {
            clearPendingRoute()
            return false
        }
        return true
    }

    @Synchronized
    fun consumePendingRoute() {
        if (!::appContext.isInitialized) return
        val destination = readyPendingRoute(requireExistingNavigation = true) ?: return
        clearPendingRoute()
        when (destination) {
            is XingDunNotificationDestination.Conversation -> ChatActivity.start(appContext, destination.conversationID)
            XingDunNotificationDestination.Tab.MESSAGES -> launchMain(MainActivity.TAB_MESSAGES)
            XingDunNotificationDestination.Tab.CONTACTS -> launchMain(MainActivity.TAB_CONTACTS)
            XingDunNotificationDestination.Tab.PROFILE -> launchMain(MainActivity.TAB_PROFILE)
        }
    }

    @Synchronized
    internal fun takeConversationIntent(context: Context, requireExistingNavigation: Boolean): Intent? {
        val destination = readyPendingRoute(requireExistingNavigation) as? XingDunNotificationDestination.Conversation
            ?: return null
        clearPendingRoute()
        return ChatActivity.conversationIntent(context, destination.conversationID)
    }

    @Synchronized
    internal fun hasReadyStartupConversation(): Boolean =
        readyPendingRoute(requireExistingNavigation = false) is XingDunNotificationDestination.Conversation

    private fun readyPendingRoute(requireExistingNavigation: Boolean): XingDunNotificationDestination? {
        val raw = MMKV.defaultMMKV().decodeString(KEY_PENDING_PUSH_ROUTE, "").orEmpty()
        if (raw.isEmpty()) return null
        val scope = currentScope()
        val recovery = XingDunCredentialRecoveryCoordinator.state.value
        if (XingDunTenantSessionCoordinator.isLeaving() || recovery == XingDunCredentialRecoveryState.REDIRECTING) {
            clearPendingRoute()
            return null
        }
        when (XingDunNotificationConsumptionPolicy.decide(
            MMKV.defaultMMKV().decodeString(KEY_PENDING_PUSH_SCOPE, "").orEmpty(),
            scope, LoginStore.shared.sdkAppID, TUILogin.getLoginUser(),
            authenticationReady = recovery == XingDunCredentialRecoveryState.NORMAL &&
                V2TIMManager.getInstance().loginStatus == V2TIMManager.V2TIM_STATUS_LOGINED,
            navigationReady = scope != null && (!requireExistingNavigation || navigationReadyScope == scope.key),
        )) {
            XingDunNotificationConsumption.WAIT -> return null
            XingDunNotificationConsumption.DISCARD -> { clearPendingRoute(); return null }
            XingDunNotificationConsumption.OPEN -> Unit
        }
        val destination = XingDunNotificationPayloadParser.parse(raw)
        if (destination == null || scope?.allows(destination) != true) {
            clearPendingRoute()
            return null
        }
        return destination
    }

    internal fun currentAccountKey(): String? = currentScope()?.key

    private fun currentScope(): XingDunNotificationScope? {
        val session = XingDunSessionManager.currentSession() ?: return null
        val tenant = XingDunTenantBoundary.identity(session) ?: return null
        if (session.apiBaseUrl.isBlank() || session.timUserId.isBlank()) return null
        return XingDunNotificationScope(tenant.companyId, tenant.companyCode, tenant.sdkAppId,
            session.apiBaseUrl, session.timUserId)
    }

    private fun launchMain(tab: String) {
        appContext.startActivity(Intent(appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_TARGET_TAB, tab)
        })
    }

    @Synchronized
    fun clearPendingRoute() {
        MMKV.defaultMMKV().removeValueForKey(KEY_PENDING_PUSH_ROUTE)
        MMKV.defaultMMKV().removeValueForKey(KEY_PENDING_PUSH_SCOPE)
        // Drop the older tenant-only binding; it cannot safely authorize another account.
        MMKV.defaultMMKV().removeValueForKey(KEY_PENDING_PUSH_TENANT)
    }

    private const val KEY_PENDING_PUSH_ROUTE = "xingdun.pending.push.route"
    private const val KEY_PENDING_PUSH_TENANT = "xingdun.pending.push.tenant"
    private const val KEY_PENDING_PUSH_SCOPE = "xingdun.pending.push.scope.v2"
}
