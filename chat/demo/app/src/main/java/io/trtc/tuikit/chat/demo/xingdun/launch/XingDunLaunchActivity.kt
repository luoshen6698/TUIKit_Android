package io.trtc.tuikit.chat.demo.xingdun.launch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.login.BaseLoginActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.launch

class XingDunLaunchActivity : BaseLoginActivity() {

    private lateinit var status: TextView
    private lateinit var enterpriseLogo: XingDunEnterpriseLogoView
    private lateinit var selectedEnterprise: TextView
    private lateinit var switchEnterprise: Button
    private lateinit var companyCode: EditText
    private lateinit var username: EditText
    private lateinit var nickname: EditText
    private lateinit var password: EditText
    private lateinit var confirmPassword: EditText
    private lateinit var inviteCode: EditText
    private lateinit var adultDeclaration: CheckBox
    private lateinit var privacyConsent: CheckBox
    private lateinit var legalLinks: View
    private lateinit var primaryAction: Button
    private lateinit var switchMode: Button
    private var registrationMode = false
    private var resolvedBootstrap: XingDunBootstrapConfiguration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.xingdun_activity_launch)
        bindViews()
        if (!applySelectedEnterprise()) return
        applyThemeColors(themeStore.themeState.value.currentTheme.tokens.color)
        applyInvitation(intent?.data)
        primaryAction.setOnClickListener { authenticate() }
        switchMode.setOnClickListener { setRegistrationMode(!registrationMode) }
        findViewById<Button>(R.id.xingdun_language).setOnClickListener { showLanguageSelector() }
        findViewById<Button>(R.id.xingdun_user_agreement).setOnClickListener { openLegalDocument(false) }
        findViewById<Button>(R.id.xingdun_privacy_policy).setOnClickListener { openLegalDocument(true) }
        switchEnterprise.setOnClickListener { switchEnterprise() }
        checkVersionThenRestore()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyInvitation(intent.data)
    }

    override fun applyThemeColors(colors: ColorTokens) {
        if (::status.isInitialized) {
            findViewById<View>(R.id.xingdun_launch_root).setBackgroundColor(colors.bgColorDefault)
            status.setTextColor(colors.textColorSecondary)
        }
    }

    private fun bindViews() {
        status = findViewById(R.id.xingdun_launch_status)
        enterpriseLogo = findViewById(R.id.xingdun_auth_logo)
        selectedEnterprise = findViewById(R.id.xingdun_selected_enterprise)
        switchEnterprise = findViewById(R.id.xingdun_switch_enterprise)
        companyCode = findViewById(R.id.xingdun_company_code)
        username = findViewById(R.id.xingdun_username)
        nickname = findViewById(R.id.xingdun_nickname)
        password = findViewById(R.id.xingdun_password)
        confirmPassword = findViewById(R.id.xingdun_confirm_password)
        inviteCode = findViewById(R.id.xingdun_invite_code)
        adultDeclaration = findViewById(R.id.xingdun_adult_declaration)
        privacyConsent = findViewById(R.id.xingdun_privacy_consent)
        legalLinks = findViewById(R.id.xingdun_legal_links)
        primaryAction = findViewById(R.id.xingdun_primary_action)
        switchMode = findViewById(R.id.xingdun_switch_mode)
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
        enterpriseLogo.loadLogo(lifecycleScope, bootstrap.company?.logoUrl)
        companyCode.setText(bootstrap.companyCode)
        selectedEnterprise.text = getString(
            R.string.xingdun_selected_enterprise,
            bootstrap.company?.name?.takeIf(String::isNotBlank) ?: bootstrap.companyCode,
            bootstrap.companyCode
        )
        XingDunSessionManager.currentSession()?.takeIf {
            !it.companyCode.equals(bootstrap.companyCode, ignoreCase = true)
        }?.let { XingDunSessionManager.clear() }
        return true
    }

    private fun restoreSession() {
        if (XingDunSessionManager.currentSession() == null) {
            setLoading(false)
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
                    setLoading(false, error.localizedMessage ?: getString(R.string.xingdun_network_error))
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
        if (account.isEmpty() || passwordValue.isEmpty()) {
            status.setText(R.string.xingdun_required_fields)
            return
        }
        if (!privacyConsent.isChecked) {
            status.setText(R.string.xingdun_consent_required)
            return
        }
        if (registrationMode) {
            if (nickname.text.toString().trim().isEmpty() || confirmPassword.text.toString() != passwordValue) {
                status.setText(R.string.xingdun_registration_fields_invalid)
                return
            }
            if (!adultDeclaration.isChecked) {
                status.setText(R.string.xingdun_consent_required)
                return
            }
        }

        setLoading(true, getString(if (registrationMode) R.string.xingdun_registering else R.string.xingdun_signing_in))
        lifecycleScope.launch {
            val result = runCatching {
                if (registrationMode) {
                    XingDunSessionManager.register(
                        companyCode = company,
                        username = account,
                        password = passwordValue,
                        confirmPassword = confirmPassword.text.toString(),
                        nickname = nickname.text.toString(),
                        inviteCode = inviteCode.text.toString()
                    )
                } else {
                    XingDunSessionManager.login(company, account, passwordValue)
                }
            }
            result.onSuccess(::loginToIM).onFailure { error ->
                setLoading(false, error.localizedMessage ?: getString(R.string.xingdun_authentication_failed))
            }
        }
    }

    private fun loginToIM(session: XingDunStoredSession) {
        setLoading(true, getString(R.string.xingdun_connecting_im))
        performLogin(
            sdkAppId = session.sdkAppId,
            userId = session.timUserId,
            userSig = session.userSig,
            onFailure = { _, description ->
                setLoading(false, getString(R.string.demo_login_failed, description))
            }
        )
    }

    private fun setRegistrationMode(enabled: Boolean) {
        registrationMode = enabled
        listOf(nickname, confirmPassword, inviteCode, adultDeclaration).forEach {
            it.visibility = if (enabled) View.VISIBLE else View.GONE
        }
        privacyConsent.visibility = View.VISIBLE
        legalLinks.visibility = View.VISIBLE
        primaryAction.setText(if (enabled) R.string.xingdun_register else R.string.xingdun_login)
        switchMode.setText(if (enabled) R.string.xingdun_back_to_login else R.string.xingdun_create_account)
        status.text = ""
    }

    private fun openLegalDocument(privacy: Boolean) {
        val bootstrap = resolvedBootstrap ?: run {
            status.setText(R.string.xingdun_enterprise_lookup_required)
            return
        }
        lifecycleScope.launch {
            val value = if (privacy) bootstrap.privacy.privacyUrl else bootstrap.privacy.userAgreementUrl
            val uri = runCatching { Uri.parse(value) }.getOrNull()
            if (uri?.scheme != "https" || uri.host.isNullOrBlank()) {
                status.setText(R.string.xingdun_legal_document_unavailable)
                return@launch
            }
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    private fun switchEnterprise() {
        XingDunSessionManager.clearEnterpriseSelection()
        startActivity(Intent(this, XingDunEnterpriseAccessActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        finish()
    }

    private fun applyInvitation(uri: Uri?) {
        if (uri == null) return
        val validRoute = (uri.scheme == "xingdun" && uri.host == "invite") ||
            (uri.scheme == "https" && uri.host == "api.xingdunim.com" && uri.path == "/prod/xingdun/share.html")
        if (!validRoute) return
        val code = listOf("code", "invite_code", "inviteCode")
            .firstNotNullOfOrNull { uri.getQueryParameter(it) }
            ?.trim()?.lowercase()
        val company = listOf("company_code", "companyCode")
            .firstNotNullOfOrNull { uri.getQueryParameter(it) }
            ?.trim()?.lowercase()
        if (code != null && code.length in 6..20 && code.all { it in INVITE_CHARACTERS }) {
            setRegistrationMode(true)
            inviteCode.setText(code)
        }
        if (company != null && company.length in 4..20 && company.all(Char::isLetterOrDigit)) {
            companyCode.setText(company)
        }
    }

    private fun setLoading(loading: Boolean, message: String = "") {
        primaryAction.isEnabled = !loading
        switchMode.isEnabled = !loading
        switchEnterprise.isEnabled = !loading
        status.text = message
    }

    companion object {
        private const val INVITE_CHARACTERS = "23456789abcdefghjkmnpqrstuvwxyz"
    }
}
