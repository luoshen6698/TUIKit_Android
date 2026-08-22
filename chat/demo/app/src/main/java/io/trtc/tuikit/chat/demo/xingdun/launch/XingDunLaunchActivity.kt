package io.trtc.tuikit.chat.demo.xingdun.launch

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.launch

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
    private var isLoading = false
    private var resolvedBootstrap: XingDunBootstrapConfiguration? = null
    private val initialNoticeResId: Int by lazy { intent.getIntExtra(EXTRA_NOTICE_RES_ID, 0) }

    private val passwordReset = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            password.text?.clear()
            status.setText(R.string.xingdun_password_reset_login_message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.xingdun_activity_launch)
        bindViews()
        if (!applySelectedEnterprise()) return
        applyThemeColors(themeStore.themeState.value.currentTheme.tokens.color)
        if (openInvitationRegistration(intent?.data)) return
        primaryAction.setOnClickListener { authenticate() }
        switchMode.setOnClickListener { startActivity(Intent(this, XingDunRegistrationActivity::class.java)) }
        findViewById<Button>(R.id.xingdun_language).setOnClickListener { showLanguageSelector() }
        setupAgreementLinks()
        privacyConsent.setOnCheckedChangeListener { _, _ -> updatePrimaryActionEnabled() }
        forgotPassword.setOnClickListener {
            passwordReset.launch(Intent(this, XingDunPasswordResetActivity::class.java))
        }
        switchEnterprise.setOnClickListener { switchEnterprise() }
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
        if (bootstrap == null) {
            startActivity(Intent(this, XingDunEnterpriseAccessActivity::class.java).apply {
                data = intent?.data
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
            return false
        }
        resolvedBootstrap = bootstrap
        val displayName = XingDunAuthUiSupport.displayName(this, bootstrap)
        brandName.text = displayName
        loginSubtitle.text = getString(R.string.xingdun_login_platform_account, displayName)
        copyright.text = bootstrap.platform?.siteCopyright?.trim()?.takeIf(String::isNotEmpty) ?: displayName
        enterpriseLogo.contentDescription = displayName
        enterpriseLogo.loadLogo(lifecycleScope, XingDunAuthUiSupport.logoUrl(bootstrap))
        companyCode.setText(bootstrap.companyCode)
        selectedEnterprise.text = getString(
            R.string.xingdun_selected_enterprise,
            bootstrap.company?.name?.takeIf(String::isNotBlank) ?: bootstrap.companyCode,
            bootstrap.companyCode
        )
        switchEnterprise.visibility = if (bootstrap.mode.equals("simple", ignoreCase = true)) View.GONE else View.VISIBLE
        XingDunSessionManager.currentSession()?.takeIf {
            !it.companyCode.equals(bootstrap.companyCode, ignoreCase = true)
        }?.let { XingDunSessionManager.clear() }
        return true
    }

    private fun restoreSession() {
        if (XingDunSessionManager.currentSession() == null) {
            setLoading(false, initialNoticeResId.takeIf { it != 0 }?.let(::getString).orEmpty())
            return
        }
        setLoading(true, getString(R.string.xingdun_restoring_session))
        lifecycleScope.launch {
            runCatching { XingDunSessionManager.restore() }
                .onSuccess { session ->
                    if (session == null) {
                        setLoading(false, getString(R.string.xingdun_session_expired))
                    } else {
                        loginToIM(session)
                    }
                }
                .onFailure { error ->
                    setLoading(false, getString(XingDunAuthenticationErrorPresenter.login(error)))
                }
        }
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
        lifecycleScope.launch {
            val result = runCatching { XingDunSessionManager.login(company, account, passwordValue) }
            result.onSuccess(::loginToIM).onFailure { error ->
                setLoading(false, getString(XingDunAuthenticationErrorPresenter.login(error)))
            }
        }
    }

    private fun loginToIM(session: XingDunStoredSession) {
        setLoading(true, getString(R.string.xingdun_connecting_im))
        performLogin(
            sdkAppId = session.sdkAppId,
            userId = session.timUserId,
            userSig = session.userSig,
            onFailure = { code, description ->
                setLoading(false, getString(XingDunAuthenticationErrorPresenter.im(code, description)))
            }
        )
    }

    private fun setupAgreementLinks() {
        val bootstrap = resolvedBootstrap ?: run {
            status.setText(R.string.xingdun_enterprise_lookup_required)
            return
        }
        XingDunAuthUiSupport.installLegalLinks(this, bootstrap, agreementText) {
            status.setText(R.string.xingdun_legal_document_unavailable)
        }
    }

    private fun switchEnterprise() {
        setLoading(true, getString(R.string.xingdun_switching_enterprise))
        XingDunTenantSessionCoordinator.switchEnterprise {
            startActivity(Intent(this, XingDunEnterpriseAccessActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
        }
    }

    private fun openInvitationRegistration(uri: Uri?): Boolean {
        if (uri == null) return false
        val invitation = runCatching { XingDunQRCodeParser.parse(uri.toString()) }.getOrNull()
            as? XingDunQRCodeRoute.Invitation ?: return false
        val currentCompany = resolvedBootstrap?.companyCode
        if (invitation.companyCode != null && !invitation.companyCode.equals(currentCompany, ignoreCase = true)) {
            setLoading(true, getString(R.string.xingdun_switching_enterprise))
            XingDunTenantSessionCoordinator.switchEnterprise {
                startActivity(Intent(this, XingDunEnterpriseAccessActivity::class.java).apply {
                    data = uri
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
                finish()
            }
            return true
        }
        startActivity(Intent(this, XingDunRegistrationActivity::class.java).apply { data = uri })
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
        status.text = message
    }

    private fun updatePrimaryActionEnabled() {
        if (::primaryAction.isInitialized) {
            primaryAction.isEnabled = !isLoading && privacyConsent.isChecked
        }
    }

    companion object {
        const val EXTRA_NOTICE_RES_ID = "xingdun_authentication_notice_res_id"
    }
}
