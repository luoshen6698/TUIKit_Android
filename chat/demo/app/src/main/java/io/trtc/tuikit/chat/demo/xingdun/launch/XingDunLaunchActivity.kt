package io.trtc.tuikit.chat.demo.xingdun.launch

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantBoundary
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.login.BaseLoginActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunQRCodeParser
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunQRCodeRoute
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunAccountDeletionReceiptStore
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantSessionCoordinator
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunLaunchRecoveryPolicy
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import android.util.Log

open class XingDunLaunchActivity : BaseLoginActivity() {

    private lateinit var status: TextView
    private lateinit var deletionStatus: TextView
    private lateinit var enterpriseLogo: XingDunEnterpriseLogoView
    private lateinit var brandName: TextView
    private lateinit var loginSubtitle: TextView
    private lateinit var copyright: TextView
    private lateinit var selectedEnterprise: TextView
    private lateinit var switchEnterprise: Button
    private lateinit var companyCode: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var privacyConsent: CheckBox
    private lateinit var agreementText: TextView
    private lateinit var primaryAction: LinearLayout
    private lateinit var switchMode: Button
    private lateinit var forgotPassword: Button
    private var companySheet: XingDunCompanyCodeSheet? = null
    private var applyingRememberedLogin = false
    private var restoredLoginScope: String? = null
    private var didRestoreRememberedLogin = false
    private var autofilledAccount: String? = null
    private var isLoading = false
    private var recoveryRouted = false
    private var codeVisible = true
    private var companyGeneration = 0
    private var pendingInvitation: Uri? = null
    private var resolvedBootstrap: XingDunBootstrapConfiguration? = null
    private val initialNoticeResId: Int by lazy { intent.getIntExtra(EXTRA_NOTICE_RES_ID, 0) }
    private val autoRestoreAllowed: Boolean by lazy {
        !intent.getBooleanExtra(EXTRA_DISABLE_AUTO_RESTORE, false)
    }

    private val passwordReset = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            password.text?.clear()
            TravelAuthForm.message(this, getString(R.string.xingdun_password_reset_login_message))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.data == null && routeToRecoveryIfSessionExists(autoRestoreAllowed)) return
        setContentView(R.layout.xingdun_activity_launch)
        bindViews()
        if (!applySelectedEnterprise()) return
        applyThemeColors(themeStore.themeState.value.currentTheme.tokens.color)
        pendingInvitation = intent?.data
        primaryAction.setOnClickListener { authenticate() }
        switchMode.setOnClickListener { if (ensureCompany()) startActivity(Intent(this, XingDunRegistrationActivity::class.java)) }
        findViewById<Button>(R.id.xingdun_language).setOnClickListener { showLanguageSelector() }
        setupAgreementLinks()
        privacyConsent.setOnCheckedChangeListener { _, _ -> updatePrimaryActionEnabled() }
        username.doAfterTextChanged {
            if (!applyingRememberedLogin && autofilledAccount != null &&
                it.toString().trim() != autofilledAccount) {
                autofilledAccount = null
                password.text.clear()
                privacyConsent.isChecked = false
            }
        }
        forgotPassword.setOnClickListener {
            if (ensureCompany()) passwordReset.launch(Intent(this, XingDunPasswordResetActivity::class.java))
        }
        switchEnterprise.setOnClickListener { switchEnterprise() }
        companyCode.setOnClickListener { switchEnterprise() }
        findViewById<Button>(R.id.travel_auth_retry).setOnClickListener { loadLoginPolicy() }
        updatePrimaryActionEnabled()
        refreshDeletionStatus()
        checkVersionThenRestore()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openInvitationRegistration(intent.data)
    }

    override fun applyThemeColors(colors: ColorTokens) {
        if (::status.isInitialized) {
            status.setTextColor(Color.rgb(102, 125, 121))
        }
    }

    private fun bindViews() {
        status = findViewById(R.id.xingdun_launch_status)
        deletionStatus = findViewById(R.id.xingdun_deletion_status)
        enterpriseLogo = findViewById(R.id.xingdun_auth_logo)
        brandName = findViewById(R.id.xingdun_brand_name)
        loginSubtitle = findViewById(R.id.xingdun_login_subtitle)
        copyright = findViewById(R.id.xingdun_copyright)
        selectedEnterprise = findViewById(R.id.xingdun_selected_enterprise)
        switchEnterprise = findViewById(R.id.xingdun_switch_enterprise)
        companyCode = findViewById(R.id.xingdun_company_code)
        username = findViewById(R.id.xingdun_username)
        password = findViewById(R.id.xingdun_password)
        privacyConsent = findViewById(R.id.xingdun_privacy_consent)
        agreementText = findViewById(R.id.xingdun_agreement_text)
        primaryAction = findViewById(R.id.xingdun_primary_action)
        switchMode = findViewById(R.id.xingdun_switch_mode)
        forgotPassword = findViewById(R.id.xingdun_forgot_password)
    }

    private fun applySelectedEnterprise(): Boolean {
        val bootstrap = XingDunSessionManager.currentEnterprise()
        resolvedBootstrap = bootstrap
        val displayName = getString(R.string.demo_app_name)
        brandName.text = displayName
        loginSubtitle.text = getString(R.string.ta_login_subtitle,displayName)
        copyright.text = displayName
        enterpriseLogo.contentDescription = displayName
        enterpriseLogo.loadLogo(lifecycleScope,null)
        codeVisible = TravelAuthPolicy.showsCompanyCode(bootstrap?.showCompanyCode,bootstrap?.mode)
        renderCompany()
        return true
    }

    private fun renderCompany() {
        val bootstrap=resolvedBootstrap
        val selected=!codeVisible || TravelCompanySelection.matches(bootstrap)
        findViewById<View>(R.id.travel_company_group).visibility=if(codeVisible)View.VISIBLE else View.GONE
        companyCode.setText(if(selected)bootstrap?.companyCode.orEmpty() else "")
        selectedEnterprise.text=if(selected && bootstrap!=null)getString(R.string.ta_current_company,bootstrap.company?.name?.takeIf(String::isNotBlank)?:bootstrap.companyCode)else getString(R.string.ta_code_required)
        restoreRememberedLogin()
    }

    private fun restoreRememberedLogin() {
        val scope = XingDunSessionManager.rememberedScopeFor(resolvedBootstrap?.takeIf { it.configured })
        if (didRestoreRememberedLogin && restoredLoginScope == scope) return
        val changed = didRestoreRememberedLogin && restoredLoginScope != scope
        applyingRememberedLogin = true
        try {
            if (changed) {
                username.text.clear(); password.text.clear(); privacyConsent.isChecked = false
            }
            if (changed || (username.text.isBlank() && password.text.isEmpty())) {
                val remembered = XingDunSessionManager.rememberedLogin(scope)
                username.setText(remembered?.account.orEmpty())
                password.setText(remembered?.password.orEmpty())
                autofilledAccount = remembered?.account
                privacyConsent.isChecked = remembered?.canReuseConsent(
                    XingDunSessionManager.rememberedLoginConsentVersion
                ) == true
            }
            restoredLoginScope = scope
            didRestoreRememberedLogin = true
        } finally {
            applyingRememberedLogin = false
        }
    }

    private fun ensureCompany():Boolean {
        if(isLoading)return false
        if(resolvedBootstrap?.configured!=true){status.setText(if(codeVisible)R.string.ta_code_required else R.string.ta_config_unavailable);return false}
        if(codeVisible && !TravelCompanySelection.matches(resolvedBootstrap)){status.setText(R.string.ta_code_required);return false}
        return true
    }

    private fun loadLoginPolicy() {
        val generation=++companyGeneration
        setLoading(true,getString(R.string.xingdun_startup_loading_enterprise))
        lifecycleScope.launch {
            try {
                val policy=XingDunSessionManager.startupConfiguration()
                if(generation!=companyGeneration)return@launch
                codeVisible=TravelAuthPolicy.showsCompanyCode(policy.showCompanyCode,policy.mode)
                val complete:()->Unit = {
                    if(generation==companyGeneration && !isFinishing){
                        renderCompany();findViewById<View>(R.id.travel_auth_retry).visibility=if(!codeVisible && resolvedBootstrap?.configured!=true)View.VISIBLE else View.GONE
                        setLoading(false,if(!codeVisible && resolvedBootstrap?.configured!=true)getString(R.string.ta_config_unavailable)else initialNoticeResId.takeIf{it!=0}?.let(::getString).orEmpty())
                        pendingInvitation?.let { openInvitationRegistration(it) }
                    }
                }
                if(!codeVisible && policy.configured && XingDunSessionManager.currentSession()==null) {
                    commitCompany(policy,false,generation,complete)
                } else {
                    if(!codeVisible && !policy.configured)resolvedBootstrap=null
                    complete()
                }
            }catch(error:CancellationException){throw error}catch(_:Exception){
                if(generation==companyGeneration){setLoading(false,getString(R.string.ta_config_unavailable));findViewById<View>(R.id.travel_auth_retry).visibility=View.VISIBLE}
            }
        }
    }

    private fun commitCompany(next:XingDunBootstrapConfiguration,manual:Boolean,generation:Int,complete:()->Unit) {
        val identity=checkNotNull(XingDunTenantBoundary.identity(next))
        val current=XingDunTenantBoundary.identity(XingDunSessionManager.currentSession())
        val selected=XingDunTenantBoundary.identity(XingDunSessionManager.currentEnterprise())
        val loggedIn=com.tencent.qcloud.tuicore.TUILogin.getLoginUser().orEmpty().isNotBlank()
        val different=(current!=null && !current.matches(identity)) || (selected!=null && !selected.matches(identity))
        val commit:()->Unit = {
            if(generation==companyGeneration && !isFinishing && !isDestroyed){
                if(resolvedBootstrap?.let(TravelAuthPolicy::selectionKey)!=TravelAuthPolicy.selectionKey(next)){privacyConsent.isChecked=false;password.text.clear()}
                XingDunSessionManager.selectEnterprise(next)
                if(manual)TravelCompanySelection.record(next)
                resolvedBootstrap=next;complete()
            }
        }
        if(different && (loggedIn||current!=null))XingDunTenantSessionCoordinator.switchEnterprise(commit)else commit()
    }

    private fun restoreSession() {
        if (routeToRecoveryIfSessionExists()) return
        loadLoginPolicy()
    }

    private fun routeToRecoveryIfSessionExists(allowRecovery: Boolean = true): Boolean {
        if (recoveryRouted) return true
        val hasStoredSession = XingDunSessionManager.hasStoredSession()
        val hasBusinessSession = !hasStoredSession &&
            runCatching { XingDunSessionManager.currentSession() != null }.getOrDefault(false)
        if (!XingDunLaunchRecoveryPolicy.shouldRecover(allowRecovery, hasBusinessSession, hasStoredSession)) return false
        recoveryRouted = true
        startActivity(Intent(this, XingDunStartupActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        finish()
        return true
    }

    private fun checkVersionThenRestore() {
        setLoading(true, getString(R.string.xingdun_checking_version))
        lifecycleScope.launch {
            val result = runCatching { XingDunSessionManager.checkVersion() }.getOrElse {
                restoreSession()
                return@launch
            }
            val version = result.latestVersion
            if (!result.hasUpdate || version == null) {
                restoreSession()
                return@launch
            }
            val downloadUri = version.downloadUrl?.trim()?.let { runCatching { Uri.parse(it) }.getOrNull() }
                ?.takeIf { it.scheme == "https" && !it.host.isNullOrBlank() }
            if (downloadUri == null) {
                if (result.isForce) {
                    setLoading(true, getString(R.string.xingdun_force_update_url_invalid))
                } else {
                    restoreSession()
                }
                return@launch
            }
            setLoading(false)
            AlertDialog.Builder(this@XingDunLaunchActivity)
                .setTitle(if (result.isForce) R.string.xingdun_force_update else R.string.xingdun_update_available)
                .setMessage(
                    listOfNotNull(
                        version.versionName?.takeIf(String::isNotBlank)
                            ?: version.versionCode.takeIf(String::isNotBlank),
                        version.updateLog?.takeIf(String::isNotBlank)
                    ).joinToString("\n\n")
                )
                .setCancelable(!result.isForce)
                .setPositiveButton(R.string.xingdun_update_now) { _, _ ->
                    startActivity(Intent(Intent.ACTION_VIEW, downloadUri))
                    if (!result.isForce) restoreSession()
                }
                .apply {
                    if (!result.isForce) {
                        setNegativeButton(R.string.xingdun_update_later) { _, _ -> restoreSession() }
                    }
                }
                .show()
        }
    }

    private fun authenticate() {
        if (!ensureCompany()) return
        val company = companyCode.text.toString().trim()
        val account = username.text.toString().trim()
        val passwordValue = password.text.toString()
        val accountError = XingDunAuthenticationInputValidator.loginUsernameError(account)
        if (accountError != null) {
            status.setText(
                when (accountError) {
                    XingDunAuthenticationInputError.USERNAME_REQUIRED -> R.string.xingdun_username_required
                    XingDunAuthenticationInputError.USERNAME_LENGTH -> R.string.xingdun_login_username_length
                    else -> R.string.xingdun_username_format
                }
            )
            username.requestFocus()
            return
        }
        if (passwordValue.isEmpty()) {
            status.setText(R.string.xingdun_login_password_required)
            password.requestFocus()
            return
        }
        if (!privacyConsent.isChecked) {
            status.setText(R.string.xingdun_consent_required)
            return
        }
        setLoading(true, getString(R.string.xingdun_signing_in))
        val ticket = beginAuthenticationAttempt()
        lifecycleScope.launch {
            val result = runCatching { XingDunSessionManager.login(company, account, passwordValue) }
            result.onSuccess { session ->
                if (ownsAuthenticationAttempt(ticket)) loginToIM(session, ticket)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (!ownsAuthenticationAttempt(ticket)) return@onFailure
                failAuthenticationAttempt(ticket)
                Log.w("XingDunLogin", "stage=business failure=${error.javaClass.simpleName}")
                if (routeToRecoveryIfSessionExists()) return@onFailure
                setLoading(false, getString(XingDunAuthenticationErrorPresenter.login(error)))
            }
        }
    }

    private fun loginToIM(session: XingDunStoredSession, ticket: Long) {
        setLoading(true, getString(R.string.xingdun_connecting_im))
        performLogin(
            sdkAppId = session.sdkAppId,
            userId = session.timUserId,
            userSig = session.userSig,
            retryTransientFailureOnce = true,
            authenticationTicket = ticket,
            onFailure = { code, description ->
                Log.w("XingDunLogin", "stage=interactive_im code=$code")
                if (routeToRecoveryIfSessionExists()) return@performLogin
                setLoading(false, getString(XingDunAuthenticationErrorPresenter.im(code, description)))
            }
        )
    }

    private fun setupAgreementLinks() {
        val bootstrap = resolvedBootstrap ?: XingDunBootstrapConfiguration()
        XingDunAuthUiSupport.installLegalLinks(this, bootstrap, agreementText) {
            status.setText(R.string.xingdun_legal_document_unavailable)
        }
    }

    private fun switchEnterprise(prefill:String?=null) {
        if(isLoading || !codeVisible)return
        val generation=++companyGeneration
        val dialog = XingDunCompanyCodeSheet(this, prefill ?: companyCode.text.toString())
        companySheet = dialog
        dialog.onConfirm = confirm@{ value ->
            val code=TravelAuthPolicy.normalizedCode(value)
            if(code==null){dialog.showError(if(value.isBlank())R.string.ta_code_prompt else R.string.ta_code_format);return@confirm}
            dialog.setSaving(true);setLoading(true,getString(R.string.xingdun_enterprise_connecting))
            lifecycleScope.launch {
                try {
                    val next=XingDunSessionManager.discoverEnterprise(code,null)
                    if(generation!=companyGeneration)return@launch
                    commitCompany(next,true,generation){
                        codeVisible=TravelAuthPolicy.showsCompanyCode(next.showCompanyCode,next.mode);renderCompany();setupAgreementLinks();setLoading(false);dialog.dismiss()
                        pendingInvitation?.let { openInvitationRegistration(it) }
                    }
                }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){
                    if(generation==companyGeneration){dialog.setSaving(false);dialog.showError(R.string.ta_company_failed);setLoading(false)}
                }
            }
        }
        dialog.show()
    }

    private fun openInvitationRegistration(uri: Uri?): Boolean {
        if(uri==null)return false
        val invitation=runCatching{XingDunQRCodeParser.parse(uri.toString())}.getOrNull() as? XingDunQRCodeRoute.Invitation?:return false
        pendingInvitation=uri
        if(invitation.companyCode!=null && !invitation.companyCode.equals(resolvedBootstrap?.companyCode,true)) {
            if(codeVisible)switchEnterprise(invitation.companyCode)else status.setText(R.string.ta_company_failed)
            return true
        }
        if(!ensureCompany()){if(codeVisible)switchEnterprise(invitation.companyCode);return true}
        pendingInvitation=null
        startActivity(Intent(this,XingDunRegistrationActivity::class.java).apply{data=uri})
        return true
    }

    private fun refreshDeletionStatus() {
        val receipt = XingDunAccountDeletionReceiptStore.load(this) ?: return
        lifecycleScope.launch {
            runCatching { XingDunSessionManager.deletionStatus(receipt.receipt) }
                .onSuccess { result ->
                    deletionStatus.text = when (result.status.lowercase()) {
                        "completed" -> getString(R.string.xingdun_deletion_completed)
                        "cancelled" -> getString(R.string.xingdun_deletion_cancelled)
                        else -> getString(
                            R.string.xingdun_deletion_pending,
                            result.purgeAfter?.takeIf(String::isNotBlank) ?: receipt.purgeAfter.orEmpty()
                        )
                    }
                    deletionStatus.visibility = View.VISIBLE
                    if (result.status.equals("completed", true) || result.status.equals("cancelled", true)) {
                        XingDunAccountDeletionReceiptStore.clear(this@XingDunLaunchActivity)
                    }
                }
        }
    }

    private fun setLoading(loading: Boolean, message: String = "") {
        isLoading = loading
        updatePrimaryActionEnabled()
        switchMode.isEnabled = !loading
        forgotPassword.isEnabled = !loading
        switchEnterprise.isEnabled = !loading
        companyCode.isEnabled = !loading
        username.isEnabled = !loading;password.isEnabled = !loading;privacyConsent.isEnabled = !loading
        status.text = message
    }

    private fun updatePrimaryActionEnabled() {
        if (::primaryAction.isInitialized) {
            primaryAction.isEnabled = !isLoading
            primaryAction.alpha = if(isLoading).55f else 1f
        }
    }

    override fun onDestroy(){companyGeneration++;companySheet?.dismiss();companySheet=null;super.onDestroy()}

    companion object {
        const val EXTRA_NOTICE_RES_ID = "xingdun_authentication_notice_res_id"
        const val EXTRA_DISABLE_AUTO_RESTORE = "xingdun_disable_auto_restore"
    }
}
