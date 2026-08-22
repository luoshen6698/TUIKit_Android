package io.trtc.tuikit.chat.demo.xingdun.launch

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.login.BaseLoginActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantSessionCoordinator
import kotlinx.coroutines.launch

open class XingDunLaunchActivity : BaseLoginActivity() {

    private lateinit var status: TextView
    private lateinit var enterpriseLogo: XingDunEnterpriseLogoView
    private lateinit var brandName: TextView
    private lateinit var loginSubtitle: TextView
    private lateinit var copyright: TextView
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
    private lateinit var agreementText: TextView
    private lateinit var primaryAction: LinearLayout
    private lateinit var primaryActionLabel: TextView
    private lateinit var switchMode: Button
    private lateinit var forgotPassword: Button
    private var registrationMode = false
    private var isLoading = false
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
        setupAgreementLinks()
        privacyConsent.setOnCheckedChangeListener { _, _ -> updatePrimaryActionEnabled() }
        forgotPassword.setOnClickListener { showForgotPasswordSupport() }
        switchEnterprise.setOnClickListener { switchEnterprise() }
        updatePrimaryActionEnabled()
        checkVersionThenRestore()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyInvitation(intent.data)
    }

    override fun applyThemeColors(colors: ColorTokens) {
        if (::status.isInitialized) {
            status.setTextColor(Color.rgb(102, 125, 121))
        }
    }

    private fun bindViews() {
        status = findViewById(R.id.xingdun_launch_status)
        enterpriseLogo = findViewById(R.id.xingdun_auth_logo)
        brandName = findViewById(R.id.xingdun_brand_name)
        loginSubtitle = findViewById(R.id.xingdun_login_subtitle)
        copyright = findViewById(R.id.xingdun_copyright)
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
        agreementText = findViewById(R.id.xingdun_agreement_text)
        primaryAction = findViewById(R.id.xingdun_primary_action)
        primaryActionLabel = findViewById(R.id.xingdun_primary_action_label)
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
        val displayName = bootstrap.platform?.platformName?.trim()?.takeIf(String::isNotEmpty)
            ?: bootstrap.company?.name?.trim()?.takeIf(String::isNotEmpty)
            ?: getString(R.string.demo_app_name)
        brandName.text = displayName
        loginSubtitle.text = getString(R.string.xingdun_login_platform_account, displayName)
        copyright.text = bootstrap.platform?.siteCopyright?.trim()?.takeIf(String::isNotEmpty) ?: displayName
        enterpriseLogo.contentDescription = displayName
        enterpriseLogo.loadLogo(lifecycleScope, resolveBrandLogoUrl(bootstrap))
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
        val primaryLabel = if (enabled) R.string.xingdun_register else R.string.xingdun_login
        primaryActionLabel.setText(primaryLabel)
        primaryAction.contentDescription = getString(primaryLabel)
        switchMode.setText(if (enabled) R.string.xingdun_back_to_login else R.string.xingdun_register_now)
        status.text = ""
        updatePrimaryActionEnabled()
    }

    private fun setupAgreementLinks() {
        val prefix = getString(R.string.xingdun_agreement_prefix)
        val agreement = getString(R.string.xingdun_user_agreement_link)
        val connector = getString(R.string.xingdun_agreement_connector)
        val privacy = getString(R.string.xingdun_privacy_policy_link)
        agreementText.text = SpannableStringBuilder().apply {
            append(prefix)
            val agreementStart = length
            append(agreement)
            setSpan(legalLinkSpan(false), agreementStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(connector)
            val privacyStart = length
            append(privacy)
            setSpan(legalLinkSpan(true), privacyStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        agreementText.movementMethod = LinkMovementMethod.getInstance()
        agreementText.highlightColor = Color.TRANSPARENT
    }

    private fun legalLinkSpan(privacy: Boolean): ClickableSpan = object : ClickableSpan() {
        override fun onClick(widget: View) = openLegalDocument(privacy)

        override fun updateDrawState(ds: TextPaint) {
            ds.color = Color.rgb(23, 154, 132)
            ds.isUnderlineText = false
            ds.isFakeBoldText = true
        }
    }

    private fun showForgotPasswordSupport() {
        AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_forgot_password)
            .setMessage(R.string.xingdun_forgot_password_support)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun resolveBrandLogoUrl(bootstrap: XingDunBootstrapConfiguration): String? {
        val raw = bootstrap.platform?.platformLogo?.trim()?.takeIf(String::isNotEmpty)
            ?: bootstrap.company?.logoUrl?.trim()?.takeIf(String::isNotEmpty)
            ?: return null
        val candidate = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        if (!candidate.scheme.isNullOrBlank()) return raw
        val baseValue = bootstrap.apiBaseUrl?.trim()?.takeIf(String::isNotEmpty)
            ?: bootstrap.company?.apiBaseUrl?.trim()?.takeIf(String::isNotEmpty)
            ?: return null
        val base = runCatching { Uri.parse(baseValue) }.getOrNull() ?: return null
        if (!base.scheme.equals("https", ignoreCase = true) || base.host.isNullOrBlank()) return null
        return Uri.Builder()
            .scheme("https")
            .encodedAuthority(base.encodedAuthority)
            .encodedPath(if (raw.startsWith('/')) raw else "/$raw")
            .build()
            .toString()
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
        setLoading(true, getString(R.string.xingdun_switching_enterprise))
        XingDunTenantSessionCoordinator.switchEnterprise {
            startActivity(Intent(this, XingDunEnterpriseAccessActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
        }
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
        private const val INVITE_CHARACTERS = "23456789abcdefghjkmnpqrstuvwxyz"
    }
}
