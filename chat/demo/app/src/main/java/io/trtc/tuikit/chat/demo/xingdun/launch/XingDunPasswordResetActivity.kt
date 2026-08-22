package io.trtc.tuikit.chat.demo.xingdun.launch

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.login.BaseLoginActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.launch

class XingDunPasswordResetActivity : BaseLoginActivity() {
    private lateinit var bootstrap: XingDunBootstrapConfiguration
    private lateinit var status: TextView
    private lateinit var phoneTab: Button
    private lateinit var emailTab: Button
    private lateinit var targetLabel: TextView
    private lateinit var target: EditText
    private lateinit var code: EditText
    private lateinit var newPassword: EditText
    private lateinit var confirmation: EditText
    private lateinit var getCode: Button
    private lateinit var primaryAction: LinearLayout
    private lateinit var form: View
    private lateinit var completion: View
    private var phoneMode = true
    private var loading = false
    private var cooldown: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selected = XingDunSessionManager.currentEnterprise()
        if (selected == null) {
            finish()
            return
        }
        bootstrap = selected
        setContentView(R.layout.xingdun_activity_password_reset)
        bindViews()
        bindBrand()
        bindActions()
        updateMode(true)
    }

    override fun onDestroy() {
        cooldown?.cancel()
        super.onDestroy()
    }

    override fun applyThemeColors(colors: ColorTokens) = Unit

    private fun bindViews() {
        status = findViewById(R.id.xingdun_password_reset_status)
        phoneTab = findViewById(R.id.xingdun_password_reset_phone_tab)
        emailTab = findViewById(R.id.xingdun_password_reset_email_tab)
        targetLabel = findViewById(R.id.xingdun_password_reset_target_label)
        target = findViewById(R.id.xingdun_password_reset_target)
        code = findViewById(R.id.xingdun_password_reset_code)
        newPassword = findViewById(R.id.xingdun_password_reset_new_password)
        confirmation = findViewById(R.id.xingdun_password_reset_confirm_password)
        getCode = findViewById(R.id.xingdun_password_reset_get_code)
        primaryAction = findViewById(R.id.xingdun_password_reset_primary_action)
        form = findViewById(R.id.xingdun_password_reset_form)
        completion = findViewById(R.id.xingdun_password_reset_completion)
    }

    private fun bindBrand() {
        val displayName = XingDunAuthUiSupport.displayName(this, bootstrap)
        findViewById<TextView>(R.id.xingdun_password_reset_brand_name).text = displayName
        findViewById<XingDunEnterpriseLogoView>(R.id.xingdun_password_reset_logo).apply {
            contentDescription = displayName
            loadLogo(lifecycleScope, XingDunAuthUiSupport.logoUrl(bootstrap))
        }
    }

    private fun bindActions() {
        findViewById<View>(R.id.xingdun_password_reset_close).setOnClickListener { finish() }
        phoneTab.setOnClickListener { updateMode(true) }
        emailTab.setOnClickListener { updateMode(false) }
        getCode.setOnClickListener { requestCode() }
        primaryAction.setOnClickListener { resetPassword() }
        findViewById<Button>(R.id.xingdun_password_reset_back_login).setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun updateMode(usePhone: Boolean) {
        phoneMode = usePhone
        phoneTab.background = AppCompatResources.getDrawable(this, if (phoneMode) R.drawable.xingdun_bg_enterprise_segment_selected else R.drawable.xingdun_bg_enterprise_segment)
        emailTab.background = AppCompatResources.getDrawable(this, if (phoneMode) R.drawable.xingdun_bg_enterprise_segment else R.drawable.xingdun_bg_enterprise_segment_selected)
        phoneTab.setTextColor(if (phoneMode) SELECTED_TEXT_COLOR else UNSELECTED_TEXT_COLOR)
        emailTab.setTextColor(if (phoneMode) UNSELECTED_TEXT_COLOR else SELECTED_TEXT_COLOR)
        targetLabel.setText(if (phoneMode) R.string.xingdun_phone else R.string.xingdun_email)
        target.setHint(if (phoneMode) R.string.xingdun_bound_phone_placeholder else R.string.xingdun_bound_email_placeholder)
        target.inputType = if (phoneMode) InputType.TYPE_CLASS_PHONE else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        target.setAutofillHints(if (phoneMode) View.AUTOFILL_HINT_PHONE else View.AUTOFILL_HINT_EMAIL_ADDRESS)
        code.text.clear()
        status.text = ""
        cooldown?.cancel()
        getCode.isEnabled = true
        getCode.setText(R.string.xingdun_get_verification_code)
    }

    private fun requestCode() {
        val targetValue = target.text.toString().trim()
        val targetError = if (phoneMode) {
            XingDunAuthenticationInputValidator.phoneError(targetValue)
        } else {
            XingDunAuthenticationInputValidator.emailError(targetValue)
        }
        if (targetError != null) {
            status.setText(targetError.messageResource())
            return
        }
        if (phoneMode) {
            status.setText(R.string.xingdun_phone_reset_code_placeholder)
            return
        }
        setLoading(true, getString(R.string.xingdun_sending_verification_code))
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.sendResetCode(bootstrap.companyCode, "email", targetValue)
            }.onSuccess { response ->
                setLoading(false, getString(R.string.xingdun_verification_code_sent, ((response.expiresIn ?: 300L) + 59L) / 60L))
                startCooldown()
            }.onFailure { error ->
                setLoading(false, getString(XingDunAuthenticationErrorPresenter.reset(error)))
            }
        }
    }

    private fun startCooldown() {
        cooldown?.cancel()
        cooldown = object : CountDownTimer(60_000L, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                getCode.isEnabled = false
                getCode.text = getString(R.string.xingdun_resend_seconds, (millisUntilFinished + 999L) / 1_000L)
            }

            override fun onFinish() {
                getCode.isEnabled = true
                getCode.setText(R.string.xingdun_get_verification_code)
            }
        }.start()
    }

    private fun resetPassword() {
        val targetValue = target.text.toString().trim()
        val validationError = (if (phoneMode) {
            XingDunAuthenticationInputValidator.phoneError(targetValue)
        } else {
            XingDunAuthenticationInputValidator.emailError(targetValue)
        }) ?: XingDunAuthenticationInputValidator.codeError(code.text.toString())
            ?: XingDunAuthenticationInputValidator.passwordError(newPassword.text.toString(), listOf(targetValue))
            ?: XingDunAuthenticationInputValidator.confirmationError(newPassword.text.toString(), confirmation.text.toString())
        if (validationError != null) {
            status.setText(validationError.messageResource())
            return
        }
        setLoading(true, getString(R.string.xingdun_resetting_password))
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.resetPassword(
                    bootstrap.companyCode,
                    if (phoneMode) "phone" else "email",
                    targetValue,
                    code.text.toString(),
                    newPassword.text.toString(),
                    confirmation.text.toString()
                )
            }.onSuccess {
                loading = false
                status.text = ""
                form.visibility = View.GONE
                completion.visibility = View.VISIBLE
            }.onFailure { error ->
                setLoading(false, getString(XingDunAuthenticationErrorPresenter.reset(error)))
            }
        }
    }

    private fun setLoading(value: Boolean, message: String) {
        loading = value
        status.text = message
        primaryAction.isEnabled = !value
        phoneTab.isEnabled = !value
        emailTab.isEnabled = !value
        getCode.isEnabled = !value
    }

    private fun XingDunAuthenticationInputError.messageResource(): Int = when (this) {
        XingDunAuthenticationInputError.PHONE_REQUIRED -> R.string.xingdun_phone_required
        XingDunAuthenticationInputError.PHONE_FORMAT -> R.string.xingdun_phone_invalid
        XingDunAuthenticationInputError.EMAIL_REQUIRED -> R.string.xingdun_email_required
        XingDunAuthenticationInputError.EMAIL_FORMAT -> R.string.xingdun_email_invalid
        XingDunAuthenticationInputError.CODE_REQUIRED -> R.string.xingdun_code_required
        XingDunAuthenticationInputError.CODE_FORMAT -> R.string.xingdun_code_invalid
        XingDunAuthenticationInputError.PASSWORD_REQUIRED -> R.string.xingdun_new_password_required
        XingDunAuthenticationInputError.PASSWORD_LENGTH -> R.string.xingdun_password_length
        XingDunAuthenticationInputError.PASSWORD_WHITESPACE -> R.string.xingdun_password_whitespace
        XingDunAuthenticationInputError.PASSWORD_CATEGORIES -> R.string.xingdun_password_categories
        XingDunAuthenticationInputError.PASSWORD_IDENTIFIER -> R.string.xingdun_password_identifier
        XingDunAuthenticationInputError.PASSWORD_WEAK -> R.string.xingdun_password_weak
        XingDunAuthenticationInputError.CONFIRM_PASSWORD_REQUIRED -> R.string.xingdun_confirm_new_password_required
        XingDunAuthenticationInputError.PASSWORD_MISMATCH -> R.string.xingdun_password_mismatch
        else -> R.string.xingdun_authentication_failed
    }

    companion object {
        private val SELECTED_TEXT_COLOR = Color.rgb(18, 63, 58)
        private val UNSELECTED_TEXT_COLOR = Color.rgb(102, 125, 121)
    }
}
