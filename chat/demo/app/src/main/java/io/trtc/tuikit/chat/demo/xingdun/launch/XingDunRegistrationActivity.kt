package io.trtc.tuikit.chat.demo.xingdun.launch

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
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.login.BaseLoginActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunQRCodeParser
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunQRCodeRoute
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantSessionCoordinator
import kotlinx.coroutines.launch

class XingDunRegistrationActivity : BaseLoginActivity() {
    private lateinit var bootstrap: XingDunBootstrapConfiguration
    private lateinit var status: TextView
    private lateinit var accountTab: Button
    private lateinit var phoneTab: Button
    private lateinit var accountGroup: LinearLayout
    private lateinit var phoneGroup: LinearLayout
    private lateinit var username: EditText
    private lateinit var phone: EditText
    private lateinit var code: EditText
    private lateinit var nickname: EditText
    private lateinit var password: EditText
    private lateinit var confirmation: EditText
    private lateinit var inviteCode: EditText
    private lateinit var agreementConsent: CheckBox
    private lateinit var primaryAction: LinearLayout
    private lateinit var primaryActionLabel: TextView
    private var phoneMode = false
    private var loading = false
    private var pendingSession: XingDunStoredSession? = null

    private val qrScanner = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let(::applyScannedInvitation)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selected = XingDunSessionManager.currentEnterprise()
        if (selected == null) {
            finish()
            return
        }
        bootstrap = selected
        setContentView(R.layout.xingdun_activity_registration)
        bindViews()
        bindBrand()
        bindActions()
        if (savedInstanceState?.getBoolean(STATE_PENDING_SESSION) == true) {
            pendingSession = XingDunSessionManager.currentSession()
            updatePendingState()
        }
        intent?.dataString?.let(::applyScannedInvitation)
        updateMode(false)
        updateEnabled()
    }

    override fun applyThemeColors(colors: ColorTokens) = Unit

    private fun bindViews() {
        status = findViewById(R.id.xingdun_registration_status)
        accountTab = findViewById(R.id.xingdun_registration_account_tab)
        phoneTab = findViewById(R.id.xingdun_registration_phone_tab)
        accountGroup = findViewById(R.id.xingdun_registration_account_group)
        phoneGroup = findViewById(R.id.xingdun_registration_phone_group)
        username = findViewById(R.id.xingdun_registration_username)
        phone = findViewById(R.id.xingdun_registration_phone)
        code = findViewById(R.id.xingdun_registration_code)
        nickname = findViewById(R.id.xingdun_registration_nickname)
        password = findViewById(R.id.xingdun_registration_password)
        confirmation = findViewById(R.id.xingdun_registration_confirm_password)
        inviteCode = findViewById(R.id.xingdun_registration_invite_code)
        agreementConsent = findViewById(R.id.xingdun_registration_consent)
        primaryAction = findViewById(R.id.xingdun_registration_primary_action)
        primaryActionLabel = findViewById(R.id.xingdun_registration_primary_action_label)
    }

    private fun bindBrand() {
        val displayName = XingDunAuthUiSupport.displayName(this, bootstrap)
        findViewById<TextView>(R.id.xingdun_registration_brand_name).text = displayName
        findViewById<XingDunEnterpriseLogoView>(R.id.xingdun_registration_logo).apply {
            contentDescription = displayName
            loadLogo(lifecycleScope, XingDunAuthUiSupport.logoUrl(bootstrap))
        }
        XingDunAuthUiSupport.installLegalLinks(
            this,
            bootstrap,
            findViewById(R.id.xingdun_registration_agreement_text)
        ) { status.setText(R.string.xingdun_legal_document_unavailable) }
    }

    private fun bindActions() {
        findViewById<View>(R.id.xingdun_registration_close).setOnClickListener { finish() }
        accountTab.setOnClickListener { updateMode(false) }
        phoneTab.setOnClickListener { updateMode(true) }
        findViewById<Button>(R.id.xingdun_registration_get_code).setOnClickListener {
            val error = XingDunAuthenticationInputValidator.phoneError(phone.text.toString())
            status.setText(if (error == null) R.string.xingdun_phone_code_placeholder else error.messageResource())
        }
        findViewById<Button>(R.id.xingdun_registration_scan_invite).setOnClickListener {
            qrScanner.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt(getString(R.string.xingdun_scan_invitation_prompt))
                    .setBeepEnabled(false)
            )
        }
        agreementConsent.setOnCheckedChangeListener { _, _ -> updateEnabled() }
        primaryAction.setOnClickListener { submit() }
        findViewById<Button>(R.id.xingdun_registration_back_login).setOnClickListener { finish() }
    }

    private fun updateMode(phoneRegistration: Boolean) {
        if (pendingSession != null) return
        phoneMode = phoneRegistration
        accountGroup.visibility = if (phoneMode) View.GONE else View.VISIBLE
        phoneGroup.visibility = if (phoneMode) View.VISIBLE else View.GONE
        accountTab.background = AppCompatResources.getDrawable(this, if (phoneMode) R.drawable.xingdun_bg_enterprise_segment else R.drawable.xingdun_bg_enterprise_segment_selected)
        phoneTab.background = AppCompatResources.getDrawable(this, if (phoneMode) R.drawable.xingdun_bg_enterprise_segment_selected else R.drawable.xingdun_bg_enterprise_segment)
        accountTab.setTextColor(if (phoneMode) UNSELECTED_TEXT_COLOR else SELECTED_TEXT_COLOR)
        phoneTab.setTextColor(if (phoneMode) SELECTED_TEXT_COLOR else UNSELECTED_TEXT_COLOR)
        status.text = ""
    }

    private fun submit() {
        pendingSession?.let {
            loginToIM(it)
            return
        }
        val identifier = if (phoneMode) phone.text.toString().trim() else username.text.toString().trim()
        val error = firstValidationError(identifier)
        if (error != null) {
            status.setText(error.messageResource())
            return
        }
        if (!agreementConsent.isChecked) {
            status.setText(R.string.xingdun_consent_required)
            return
        }
        setLoading(true, getString(R.string.xingdun_registering))
        lifecycleScope.launch {
            runCatching {
                if (phoneMode) {
                    XingDunSessionManager.registerByPhone(
                        bootstrap.companyCode,
                        identifier,
                        code.text.toString(),
                        password.text.toString(),
                        confirmation.text.toString(),
                        nickname.text.toString(),
                        inviteCode.text.toString()
                    )
                } else {
                    XingDunSessionManager.register(
                        bootstrap.companyCode,
                        identifier,
                        password.text.toString(),
                        confirmation.text.toString(),
                        nickname.text.toString(),
                        inviteCode.text.toString()
                    )
                }
            }.onSuccess { session ->
                pendingSession = session
                clearSensitiveInputs()
                updatePendingState()
                loginToIM(session)
            }.onFailure { errorValue ->
                val presentation = XingDunAuthenticationErrorPresenter.registration(errorValue)
                setLoading(false, getString(presentation.message))
                focusRegistrationField(presentation.registrationField)
            }
        }
    }

    private fun firstValidationError(identifier: String): XingDunAuthenticationInputError? {
        return if (phoneMode) {
            XingDunAuthenticationInputValidator.phoneError(identifier)
                ?: XingDunAuthenticationInputValidator.codeError(code.text.toString())
        } else {
            XingDunAuthenticationInputValidator.usernameError(identifier)
        } ?: XingDunAuthenticationInputValidator.nicknameError(nickname.text.toString())
            ?: XingDunAuthenticationInputError.PASSWORD_REQUIRED.takeIf { password.text.isEmpty() }
            ?: XingDunAuthenticationInputValidator.confirmationError(password.text.toString(), confirmation.text.toString())
    }

    private fun loginToIM(session: XingDunStoredSession) {
        setLoading(true, getString(R.string.xingdun_connecting_im))
        performLogin(
            session.sdkAppId,
            session.timUserId,
            session.userSig,
            onFailure = { code, description ->
                setLoading(false, getString(XingDunAuthenticationErrorPresenter.im(code, description)))
            }
        )
    }

    private fun applyScannedInvitation(payload: String) {
        val route = runCatching { XingDunQRCodeParser.parse(payload) }.getOrNull()
        if (route !is XingDunQRCodeRoute.Invitation) {
            status.setText(R.string.xingdun_qr_unrecognized)
            return
        }
        if (route.companyCode != null && !route.companyCode.equals(bootstrap.companyCode, ignoreCase = true)) {
            switchToInvitationEnterprise(route)
            return
        }
        inviteCode.setText(route.code)
        status.setText(R.string.xingdun_invitation_applied)
    }

    private fun setLoading(value: Boolean, message: String) {
        loading = value
        status.text = message
        updateEnabled()
    }

    private fun updateEnabled() {
        primaryAction.isEnabled = !loading && agreementConsent.isChecked
        listOf(accountTab, phoneTab).forEach { it.isEnabled = !loading && pendingSession == null }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PENDING_SESSION, pendingSession != null)
        super.onSaveInstanceState(outState)
    }

    private fun clearSensitiveInputs() {
        password.text?.clear()
        confirmation.text?.clear()
        code.text?.clear()
    }

    private fun updatePendingState() {
        primaryActionLabel.setText(if (pendingSession == null) R.string.xingdun_create_account else R.string.xingdun_continue_login)
        primaryAction.contentDescription = primaryActionLabel.text
        updateEnabled()
    }

    private fun focusRegistrationField(field: XingDunRegistrationField?) {
        when (field) {
            XingDunRegistrationField.USERNAME -> username
            XingDunRegistrationField.PHONE -> phone
            XingDunRegistrationField.CODE -> code
            XingDunRegistrationField.NICKNAME -> nickname
            XingDunRegistrationField.PASSWORD -> password
            XingDunRegistrationField.CONFIRM_PASSWORD -> confirmation
            XingDunRegistrationField.INVITE_CODE -> inviteCode
            null -> null
        }?.requestFocus()
    }

    private fun switchToInvitationEnterprise(invitation: XingDunQRCodeRoute.Invitation) {
        val uri = Uri.Builder()
            .scheme("xingdun")
            .authority("invite")
            .appendQueryParameter("code", invitation.code)
            .appendQueryParameter("company_code", invitation.companyCode)
            .build()
        setLoading(true, getString(R.string.xingdun_switching_enterprise))
        XingDunTenantSessionCoordinator.switchEnterprise {
            startActivity(Intent(this, XingDunEnterpriseAccessActivity::class.java).apply {
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
        }
    }

    private fun XingDunAuthenticationInputError.messageResource(): Int = when (this) {
        XingDunAuthenticationInputError.USERNAME_REQUIRED -> R.string.xingdun_username_required
        XingDunAuthenticationInputError.USERNAME_LENGTH -> R.string.xingdun_username_length
        XingDunAuthenticationInputError.USERNAME_FORMAT -> R.string.xingdun_username_format
        XingDunAuthenticationInputError.PHONE_REQUIRED -> R.string.xingdun_phone_required
        XingDunAuthenticationInputError.PHONE_FORMAT -> R.string.xingdun_phone_invalid
        XingDunAuthenticationInputError.CODE_REQUIRED -> R.string.xingdun_code_required
        XingDunAuthenticationInputError.CODE_FORMAT -> R.string.xingdun_code_invalid
        XingDunAuthenticationInputError.NICKNAME_LENGTH -> R.string.xingdun_nickname_too_long
        XingDunAuthenticationInputError.CONFIRM_PASSWORD_REQUIRED -> R.string.xingdun_confirm_password_required
        XingDunAuthenticationInputError.PASSWORD_MISMATCH -> R.string.xingdun_password_mismatch
        else -> R.string.xingdun_registration_fields_invalid
    }

    companion object {
        private const val STATE_PENDING_SESSION = "pending_registration_session"
        private val SELECTED_TEXT_COLOR = Color.rgb(18, 63, 58)
        private val UNSELECTED_TEXT_COLOR = Color.rgb(102, 125, 121)
    }
}
