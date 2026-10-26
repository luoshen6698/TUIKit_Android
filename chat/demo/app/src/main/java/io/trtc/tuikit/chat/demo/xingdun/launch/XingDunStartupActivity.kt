package io.trtc.tuikit.chat.demo.xingdun.launch

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Button
import android.widget.Toast
import android.widget.ScrollView
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tencent.mmkv.MMKV
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKitImpl
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import io.trtc.tuikit.chat.app.BuildConfig
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.xingdun.legal.XingDunPrivacyConsentStore
import io.trtc.tuikit.chat.demo.xingdun.legal.XingDunLegalActivity
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.demo.xingdun.call.XingDunCallSessionInitializer
import io.trtc.tuikit.chat.demo.xingdun.main.XingDunMessageFirstFramePreloader
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunVersionCheckResult
import io.trtc.tuikit.chat.demo.xingdun.push.XingDunPushManager
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunNotificationIntentReader
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunRouter
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunCredentialRecoveryCoordinator
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunCredentialRecoveryState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.resume

/**
 * Hands off from the system splash to a lightweight status view while the existing enterprise,
 * version, session and IM startup work runs. Enterprise and authentication remain fallbacks.
 */
class XingDunStartupActivity : AppCompatActivity() {

    private var hasRouted = false
    private var timeoutJob: Job? = null
    private lateinit var startupStatusView: TextView
    private lateinit var retryButton: Button
    private val retrySignals = Channel<Unit>(Channel.CONFLATED)
    private var waitingToRetry = false
    private var networkCallbackRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread { if (waitingToRetry) retrySignals.trySend(Unit) }
        }
    }
    private var incomingCallLaunch = false
    private var authenticationTicket: Long? = null
    private val startupStartedAt = SystemClock.elapsedRealtime()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        XingDunRouter.markStartupPending()
        setContentView(R.layout.xingdun_activity_startup_loading)
        startupStatusView = findViewById(R.id.xingdun_startup_status)
        retryButton = findViewById<Button>(R.id.xingdun_startup_retry).apply {
            setOnClickListener { retrySignals.trySend(Unit) }
        }
        splashScreen.setKeepOnScreenCondition { false }

        if (!XingDunPrivacyConsentStore(noBackupFilesDir).hasConsent()) {
            showFirstLaunchPrivacyDialog()
            return
        }
        continueStartup()
    }

    private fun showFirstLaunchPrivacyDialog() {
        startupStatusView.setText(R.string.xingdun_first_privacy_title)
        findViewById<View>(R.id.xingdun_startup_progress).visibility = View.GONE
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(TextView(context).apply {
                setText(R.string.xingdun_first_privacy_body)
                textSize = 16f
            })
            addView(Button(context).apply {
                setText(R.string.xingdun_user_agreement_link)
                setOnClickListener { XingDunLegalActivity.open(this@XingDunStartupActivity, false) }
            })
            addView(Button(context).apply {
                setText(R.string.xingdun_privacy_policy_link)
                setOnClickListener { XingDunLegalActivity.open(this@XingDunStartupActivity, true) }
            })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_first_privacy_title)
            .setView(ScrollView(this).apply { addView(content) })
            .setCancelable(false)
            .setNegativeButton(R.string.xingdun_first_privacy_decline) { _, _ -> finishAndRemoveTask() }
            .setPositiveButton(R.string.xingdun_first_privacy_accept, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    XingDunPrivacyConsentStore(noBackupFilesDir).accept()
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.xingdun_first_privacy_save_failed, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                (application as io.trtc.tuikit.chat.demo.Application).initializeAfterPrivacyConsent()
                dialog.dismiss()
                findViewById<View>(R.id.xingdun_startup_progress).visibility = View.VISIBLE
                startupStatusView.setText(R.string.xingdun_startup_checking_account)
                continueStartup()
            }
        }
        dialog.show()
    }

    private fun continueStartup() {
        if (intent?.data != null) {
            routeToEnterpriseAccess()
            return
        }

        // Keep the destination account-scoped while the existing authenticated startup runs.
        XingDunNotificationIntentReader.readAndStage(intent, "startup")

        incomingCallLaunch = XingDunIncomingCallLaunchPolicy.hasCallHint(intent?.getStringExtra("ext")) ||
            XingDunSessionManager.currentSession()?.let(::hasIncomingCall) == true
        updateStartupStatus(R.string.xingdun_startup_checking_account)
        logStartup("entry")

        lifecycleScope.launch {
            try {
                timeoutJob = launch {
                    delay(STARTUP_TIMEOUT_MILLIS)
                    // Slow network is not expired authentication. Let the bounded request finish.
                    updateStartupStatus(R.string.xingdun_startup_waiting_network)
                }
                var failures = 0
                while (isStartupActive()) {
                    awaitForeground(Lifecycle.State.STARTED)
                    waitingToRetry = false
                    retryButton.visibility = View.GONE
                    while (retrySignals.tryReceive().isSuccess) { /* Discard a prior retry signal. */ }
                    try {
                        prepareAuthenticatedStartup()
                        break
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        if (error is io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiException &&
                            error.reason == "login_ip_restricted") {
                            XingDunCredentialRecoveryCoordinator.redirectToLogin(R.string.xingdun_login_ip_restricted)
                            break
                        }
                        failStartupAuthentication()
                        if (!isStartupActive() ||
                            XingDunCredentialRecoveryCoordinator.state.value == XingDunCredentialRecoveryState.REDIRECTING
                        ) break
                        // Fail closed while retaining the account. Only a confirmed rejection
                        // may route to sign-in; a failed connection is not a logout event.
                        Log.w("XingDunStartup", "stage=session_recovery outcome=retry failure=${error.javaClass.simpleName}")
                        updateStartupStatus(
                            (error as? StartupRetryException)?.noticeResId
                                ?: R.string.xingdun_startup_recovering_session,
                        )
                        timeoutJob?.cancel()
                        waitingToRetry = true
                        retryButton.visibility = View.VISIBLE
                        withTimeoutOrNull(XingDunStartupRecoveryPolicy.retryDelayMillis(failures++)) {
                            retrySignals.receive()
                        }
                    }
                }
            } finally {
                failStartupAuthentication()
            }
        }
    }

    private suspend fun prepareAuthenticatedStartup() = coroutineScope {
        var enterprise = XingDunSessionManager.currentEnterprise()
        if (enterprise == null) {
            if (XingDunSessionManager.hasStoredSession()) throw StartupRetryException()
            updateStartupStatus(R.string.xingdun_startup_loading_enterprise)
            enterprise = runCatching { XingDunSessionManager.attemptSimpleEnterprise() }.getOrNull()
        }
        if (enterprise == null) {
            if (!isStartupActive()) return@coroutineScope
            routeToEnterpriseAccess()
            return@coroutineScope
        }

        if (XingDunSessionManager.currentSession() == null) {
            if (XingDunSessionManager.hasStoredSession()) throw StartupRetryException()
            routeToAuthentication()
            return@coroutineScope
        }

        // The push wakeup owner may already be restoring this exact account. Join its verified
        // result instead of rotating the session and logging into IM a second time.
        val wakeupSession = XingDunCredentialRecoveryCoordinator.awaitAuthenticatedWakeupSession()
        if (!isStartupActive() ||
            XingDunCredentialRecoveryCoordinator.state.value == XingDunCredentialRecoveryState.REDIRECTING
        ) return@coroutineScope
        if (wakeupSession == null) authenticationTicket = XingDunCredentialRecoveryCoordinator.onAuthenticationStarted()
        logStartup(if (wakeupSession != null) "reuse_verified_wakeup" else "restore_begin")

        updateStartupStatus(R.string.xingdun_startup_checking_account)
        val validationIsFresh = startupValidationIsFresh(enterprise.companyCode)
        // These checks still gate normal app access, but must not cover a valid incoming call.
        val enterpriseRefresh = async {
            if (validationIsFresh) Result.success(enterprise)
            else startupResult("enterprise") { XingDunSessionManager.refreshSelectedEnterprise() }
        }
        val versionCheck = async {
            if (validationIsFresh) null
            else startupResult("version") { XingDunSessionManager.checkVersion() }
        }
        val restoredSession = (if (wakeupSession != null) Result.success(wakeupSession)
            else startupResult("restore") { XingDunSessionManager.restore(preferCachedCredentials = true) })
            .getOrThrow()
        if (!isStartupActive()) return@coroutineScope
        if (restoredSession == null) {
            if (XingDunSessionManager.hasStoredSession()) throw StartupRetryException()
            failStartupAuthentication()
            routeToAuthentication(R.string.xingdun_session_expired)
            return@coroutineScope
        }
        if (authenticationTicket?.let { !XingDunCredentialRecoveryCoordinator.ownsAuthentication(it) } == true) {
            return@coroutineScope
        }

        updateStartupStatus(R.string.xingdun_connecting_im)
        val imLoginFailure = if (wakeupSession != null) 0 else {
            withTimeoutOrNull(IM_LOGIN_TIMEOUT_MILLIS) { loginToIM(restoredSession) }
                ?: R.string.xingdun_im_connection_failed
        }
        logStartup("im_ready")
        if (!isStartupActive()) {
            return@coroutineScope
        }
        if (authenticationTicket?.let { !XingDunCredentialRecoveryCoordinator.ownsAuthentication(it) } == true) {
            return@coroutineScope
        }
        if (imLoginFailure != 0) {
            throw StartupRetryException(R.string.xingdun_startup_recovering_session)
        }
        if (!XingDunSessionManager.matchesCurrentIMIdentity(restoredSession.sdkAppId, restoredSession.timUserId) ||
            LoginStore.shared.sdkAppID != restoredSession.sdkAppId ||
            TUILogin.getLoginUser() != restoredSession.timUserId
        ) {
            throw StartupRetryException(R.string.xingdun_error_company_mismatch)
        }

        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, restoredSession.timUserId)
        if (wakeupSession == null) {
            XingDunCredentialRecoveryCoordinator.onAuthenticated()
            authenticationTicket = null
        }
        val callReady = if (incomingCallLaunch || hasIncomingCall(restoredSession)) {
            withTimeoutOrNull(5_000) {
                XingDunCallSessionInitializer.awaitReady(
                    this@XingDunStartupActivity,
                    restoredSession.sdkAppId,
                    restoredSession.timUserId,
                    restoredSession.userSig,
                )
            } == true
        } else {
            XingDunCallSessionInitializer.initialize(
                this@XingDunStartupActivity,
                restoredSession.sdkAppId,
                restoredSession.timUserId,
                restoredSession.userSig,
            )
            false
        }
        logStartup("call_ready")
        XingDunPushManager.syncDeviceRegistration()
        if (callReady && incomingCallLaunch && !hasIncomingCall(restoredSession)) {
            withTimeoutOrNull(2_000) {
                combine(CallStore.shared.observerState.selfInfo, CallStore.shared.observerState.activeCall) { _, _ ->
                    hasIncomingCall(restoredSession)
                }.first { it }
            }
        }
        awaitIncomingCallIfPresent(restoredSession)
        if (!isStartupActive()) return@coroutineScope

        val enterpriseResult = enterpriseRefresh.await()
        val versionResult = versionCheck.await()
        awaitIncomingCallIfPresent(restoredSession)
        if (!isStartupActive()) return@coroutineScope
        val enterpriseRefreshError = enterpriseResult.exceptionOrNull()
        if (enterpriseRefreshError != null &&
            !XingDunSessionManager.shouldRetainCachedEnterprise(enterpriseRefreshError)
        ) {
            // A changed/unreadable configuration must be revalidated, not erase the account.
            throw enterpriseRefreshError
        }
        versionResult?.getOrNull()?.takeIf { it.hasUpdate }?.let {
            if (!awaitUpdateDecision(it)) {
                throw StartupRetryException(R.string.xingdun_force_update)
            }
        }
        if (!validationIsFresh && enterpriseResult.isSuccess && versionResult?.isSuccess == true) {
            markStartupValidationFresh(enterprise.companyCode)
        }
        if (!isStartupActive()) return@coroutineScope
        incomingCallLaunch = false
        updateStartupStatus(R.string.xingdun_startup_loading_messages)
        XingDunMessageFirstFramePreloader.preload(this@XingDunStartupActivity, restoredSession)
        awaitIncomingCallIfPresent(restoredSession)
        // Do not pull an app back over the user's foreground app after a slow recovery.
        awaitForeground(Lifecycle.State.RESUMED)
        if (!isStartupActive() || !XingDunSessionManager.matchesCurrentIMIdentity(
                restoredSession.sdkAppId, restoredSession.timUserId,
            ) || LoginStore.shared.sdkAppID != restoredSession.sdkAppId ||
            TUILogin.getLoginUser() != restoredSession.timUserId
        ) {
            if (isStartupActive()) throw StartupRetryException()
            return@coroutineScope
        }
        routeToHome()
    }

    private suspend fun awaitUpdateDecision(result: XingDunVersionCheckResult): Boolean {
        val version = result.latestVersion ?: return !result.isForce
        val uri = version.downloadUrl?.trim()?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.takeIf { it.scheme == "https" && !it.host.isNullOrBlank() }
        if (uri == null) {
            if (result.isForce) throw StartupRetryException(R.string.xingdun_force_update_url_invalid)
            return true
        }
        awaitForeground(Lifecycle.State.RESUMED)
        return suspendCancellableCoroutine { continuation ->
            fun complete(proceed: Boolean) {
                if (continuation.isActive) continuation.resume(proceed)
            }
            val dialog = AlertDialog.Builder(this)
                .setTitle(if (result.isForce) R.string.xingdun_force_update else R.string.xingdun_update_available)
                .setMessage(listOfNotNull(version.versionName ?: version.versionCode, version.updateLog)
                    .joinToString("\n\n"))
                .setCancelable(!result.isForce)
                .setPositiveButton(R.string.xingdun_update_now) { _, _ ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    complete(!result.isForce)
                }
                .apply {
                    if (!result.isForce) setNegativeButton(R.string.xingdun_update_later) { _, _ -> complete(true) }
                }
                .setOnCancelListener { complete(!result.isForce) }
                .create()
            continuation.invokeOnCancellation { runOnUiThread { dialog.dismiss() } }
            dialog.show()
        }
    }

    override fun onStart() {
        super.onStart()
        val manager = getSystemService(ConnectivityManager::class.java)
        networkCallbackRegistered = runCatching {
            manager.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                networkCallback,
            )
            true
        }.getOrDefault(false)
        if (waitingToRetry) retrySignals.trySend(Unit)
    }

    override fun onStop() {
        if (networkCallbackRegistered) {
            runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
        super.onStop()
    }

    private class StartupRetryException(
        @StringRes val noticeResId: Int = R.string.xingdun_startup_recovering_session,
    ) : Exception()

    private suspend fun awaitForeground(minimum: Lifecycle.State) {
        if (lifecycle.currentState.isAtLeast(minimum)) return
        suspendCancellableCoroutine<Unit> { continuation ->
            val observer = object : LifecycleEventObserver {
                override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: Lifecycle.Event) {
                    if (source.lifecycle.currentState.isAtLeast(minimum)) {
                        source.lifecycle.removeObserver(this)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
            continuation.invokeOnCancellation { runOnUiThread { lifecycle.removeObserver(observer) } }
            lifecycle.addObserver(observer)
        }
    }

    private fun hasIncomingCall(session: XingDunStoredSession): Boolean {
        val self = CallStore.shared.observerState.selfInfo.value
        val call = CallStore.shared.observerState.activeCall.value
        return XingDunIncomingCallLaunchPolicy.canHandOff(
            identityMatches = XingDunSessionManager.matchesCurrentIMIdentity(session.sdkAppId, session.timUserId) &&
                LoginStore.shared.sdkAppID == session.sdkAppId && TUILogin.getLoginUser() == session.timUserId &&
                self.id == session.timUserId,
            callId = call.callId,
            selfIsCallee = call.inviterId != session.timUserId,
            isWaitingOrAccepted = self.status == CallParticipantStatus.Waiting || self.status == CallParticipantStatus.Accept,
            hasMediaType = call.mediaType != null,
        )
    }

    private suspend fun awaitIncomingCallIfPresent(session: XingDunStoredSession) {
        val hasCall = hasIncomingCall(session)
        if (!hasCall && !incomingCallLaunch) return
        incomingCallLaunch = true
        timeoutJob?.cancel()
        updateStartupStatus(R.string.xingdun_startup_connecting_call)
        logStartup(if (hasCall) "call_handoff" else "call_return")
        // Use the SDK's restored call and UI; never infer an acceptance from notification data.
        if (hasCall) TUICallKitImpl.createInstance(this).queryOfflineCall()
        val activityState = MutableStateFlow(lifecycle.currentState)
        val observer = LifecycleEventObserver { _, _ -> activityState.value = lifecycle.currentState }
        lifecycle.addObserver(observer)
        try {
            val incomingCall = combine(
                CallStore.shared.observerState.selfInfo,
                CallStore.shared.observerState.activeCall,
            ) { _, _ -> hasIncomingCall(session) }
            XingDunIncomingCallLaunchPolicy.awaitNormalStartup(
                incomingCall,
                activityState.map { it.isAtLeast(Lifecycle.State.RESUMED) },
            )
        } finally {
            lifecycle.removeObserver(observer)
        }
        logStartup("call_finished")
    }

    private fun failStartupAuthentication() {
        authenticationTicket?.let { XingDunCredentialRecoveryCoordinator.onAuthenticationFailed(it) }
        authenticationTicket = null
    }

    private suspend fun <T> startupResult(phase: String, block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            logStartup(phase)
        }
    }

    private fun logStartup(phase: String) {
        Log.i("XingDunStartup", "stage=call_startup phase=$phase elapsed_ms=${SystemClock.elapsedRealtime() - startupStartedAt} call_hint=$incomingCallLaunch")
    }

    private suspend fun loginToIM(session: XingDunStoredSession): Int =
        suspendCancellableCoroutine { continuation ->
            LoginStore.shared.login(
                this,
                session.sdkAppId,
                session.timUserId,
                session.userSig,
                object : CompletionHandler {
                    override fun onSuccess() {
                        if (continuation.isActive) continuation.resume(0)
                    }

                    override fun onFailure(code: Int, desc: String) {
                        android.util.Log.w("XingDunLogin", "stage=startup_im code=$code")
                        if (continuation.isActive) {
                            continuation.resume(XingDunAuthenticationErrorPresenter.im(code, desc))
                        }
                    }
                },
            )
        }

    private fun routeToHome() {
        routeOnce(
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_TARGET_TAB, MainActivity.TAB_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
    }

    private fun routeToAuthentication(noticeResId: Int = 0) {
        routeOnce(
            Intent(this, XingDunLaunchActivity::class.java).apply {
                if (noticeResId != 0) putExtra(XingDunLaunchActivity.EXTRA_NOTICE_RES_ID, noticeResId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
    }

    private fun routeToEnterpriseAccess() {
        routeOnce(
            Intent(this, XingDunEnterpriseAccessActivity::class.java).apply {
                data = intent?.data
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
    }

    private fun routeOnce(target: Intent) {
        if (hasRouted || isFinishing || isDestroyed) return
        hasRouted = true
        timeoutJob?.cancel()
        timeoutJob = null
        waitingToRetry = false
        startActivity(target)
    }

    private fun isStartupActive(): Boolean = !hasRouted && !isFinishing && !isDestroyed &&
        XingDunCredentialRecoveryCoordinator.state.value != XingDunCredentialRecoveryState.REDIRECTING

    private fun updateStartupStatus(@StringRes messageResId: Int) {
        if (::startupStatusView.isInitialized) startupStatusView.setText(
            if (incomingCallLaunch) R.string.xingdun_startup_connecting_call else messageResId,
        )
    }

    private fun startupValidationIsFresh(companyCode: String): Boolean {
        val preferences = MMKV.defaultMMKV()
        if (preferences.decodeString(KEY_STARTUP_VALIDATION_SCOPE).orEmpty() != validationScope(companyCode)) {
            return false
        }
        val validatedAt = preferences.decodeLong(KEY_STARTUP_VALIDATED_AT, 0L)
        val age = System.currentTimeMillis() - validatedAt
        return age in 0..STARTUP_VALIDATION_TTL_MILLIS
    }

    private fun markStartupValidationFresh(companyCode: String) {
        MMKV.defaultMMKV().apply {
            encode(KEY_STARTUP_VALIDATION_SCOPE, validationScope(companyCode))
            encode(KEY_STARTUP_VALIDATED_AT, System.currentTimeMillis())
        }
    }

    private fun clearStartupValidation() {
        MMKV.defaultMMKV().apply {
            removeValueForKey(KEY_STARTUP_VALIDATION_SCOPE)
            removeValueForKey(KEY_STARTUP_VALIDATED_AT)
        }
    }

    private fun validationScope(companyCode: String): String = buildString {
        append(companyCode.trim().lowercase())
        append('|')
        append(BuildConfig.XINGDUN_ENVIRONMENT)
        append('|')
        append(BuildConfig.VERSION_CODE)
        append('|')
        append(BuildConfig.VERSION_NAME)
    }

    companion object {
        private const val STARTUP_TIMEOUT_MILLIS = 15_000L
        private const val IM_LOGIN_TIMEOUT_MILLIS = 30_000L
        private const val STARTUP_VALIDATION_TTL_MILLIS = 5 * 60 * 1000L
        private const val KEY_STARTUP_VALIDATION_SCOPE = "xingdun.startup.validation.scope"
        private const val KEY_STARTUP_VALIDATED_AT = "xingdun.startup.validation.validated_at"
    }
}
