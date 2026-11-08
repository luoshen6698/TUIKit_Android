package io.trtc.tuikit.chat.demo.xingdun.launch

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.widget.FrameLayout
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.view.inputmethod.EditorInfo
import android.text.InputFilter
import kotlinx.coroutines.CancellationException
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.core.widget.doAfterTextChanged
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
    private lateinit var primaryAction: View
    private lateinit var formUi: TravelAuthForm
    private var sendingCode = false
    private var phoneMode = true
    private var loading = false
    private var cooldown: CountDownTimer? = null
    private lateinit var codeCooldown: XingDunResetCodeCooldown
    private var requestGeneration = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selected = XingDunSessionManager.currentEnterprise()
        if (selected == null) {
            finish()
            return
        }
        bootstrap = selected
        val deadlines = savedInstanceState?.getBundle("reset_code_deadlines")
        val now = SystemClock.elapsedRealtime()
        codeCooldown = XingDunResetCodeCooldown(deadlines?.keySet()?.associateWith { deadlines.getLong(it) }
            ?.filterValues { it > now && it <= now + 60_000L }?.toMutableMap() ?: mutableMapOf())
        setContentView(R.layout.xingdun_activity_password_reset)
        bindViews()
        formUi = TravelAuthForm(this)
        bindBrand()
        bindActions()
        updateMode(savedInstanceState?.getBoolean("reset_phone_mode", true) ?: true)
        target.doAfterTextChanged {
            requestGeneration++
            setLoading(false, "")
            refreshCooldown()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("reset_phone_mode", phoneMode)
        outState.putBundle("reset_code_deadlines", Bundle().apply {
            codeCooldown.snapshot(SystemClock.elapsedRealtime()).forEach { (key, deadline) -> putLong(key, deadline) }
        })
    }

    override fun onResume() {
        super.onResume()
        if (::codeCooldown.isInitialized && ::getCode.isInitialized) refreshCooldown()
    }

    override fun onDestroy() {
        requestGeneration++
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
        code.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val slot = getCode.parent as FrameLayout
            val row = slot.parent as FrameLayout
            val bounds = Rect(0, 0, code.width, code.height)
            row.offsetDescendantRectToMyCoords(code, bounds)
            val params = slot.layoutParams as FrameLayout.LayoutParams
            if (params.topMargin != bounds.top || params.height != code.height) {
                params.topMargin = bounds.top; params.height = code.height; slot.layoutParams = params
            }
        }
    }

    private fun bindBrand() {
        val displayName = XingDunAuthUiSupport.displayName(this, bootstrap)
        findViewById<TextView>(R.id.xingdun_password_reset_brand_name).text = displayName
        findViewById<XingDunEnterpriseLogoView>(R.id.xingdun_password_reset_logo).apply {
            contentDescription = displayName
            loadLogo(lifecycleScope, null)
        }
    }

    private fun bindActions() {
        findViewById<View>(R.id.xingdun_password_reset_close).setOnClickListener { finish() }
        phoneTab.setOnClickListener { updateMode(true) }
        emailTab.setOnClickListener { updateMode(false) }
        getCode.setOnClickListener { requestCode() }
        primaryAction.setOnClickListener { resetPassword() }
        confirmation.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { resetPassword(); true } else false
        }
    }

    private fun updateMode(usePhone: Boolean) {
        if (loading || (phoneMode == usePhone && ::formUi.isInitialized && target.text.isNotEmpty())) return
        requestGeneration++
        formUi.clearErrors()
        if (phoneMode != usePhone) target.text.clear()
        phoneMode = usePhone
        val ui = io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi(this)
        listOf(phoneTab to phoneMode, emailTab to !phoneMode).forEach { (tab, selected) ->
            tab.isSelected = selected
            tab.backgroundTintList = null
            tab.background = ui.box(if (selected) 0xFF2F6055.toInt() else 0x80FFFFFF.toInt(), 12).apply {
                setStroke(ui.d(1), 0xBFFFFFFF.toInt())
            }
            tab.setTextColor(if (selected) Color.WHITE else 0xFF2F6055.toInt())
        }
        targetLabel.setText(if (phoneMode) R.string.xingdun_phone else R.string.xingdun_email)
        findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.travel_reset_target_container).hint = getString(if (phoneMode) R.string.ta_reset_phone_target else R.string.ta_reset_email_target)
        findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.travel_reset_target_container)
            .setStartIconDrawable(if (phoneMode) R.drawable.travel_auth_phone else R.drawable.travel_auth_email)
        target.inputType = if (phoneMode) InputType.TYPE_CLASS_PHONE else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        target.filters = arrayOf(InputFilter.LengthFilter(if (phoneMode) 11 else 128))
        target.setAutofillHints(if (phoneMode) View.AUTOFILL_HINT_PHONE else View.AUTOFILL_HINT_EMAIL_ADDRESS)
        code.text.clear()
        status.text = ""
        refreshCooldown()
    }

    private fun requestCode() {
        if (loading) return
        formUi.hideKeyboard()
        val targetValue = target.text.toString().trim()
        val targetError = if (phoneMode) {
            XingDunAuthenticationInputValidator.phoneError(targetValue)
        } else {
            XingDunAuthenticationInputValidator.emailError(targetValue)
        }
        if (targetError != null) {
            formUi.error(target, targetError.messageResource()); formUi.reveal(target)
            return
        }
        val channel = if (phoneMode) "phone" else "email"
        val requestKey = codeCooldown.key(bootstrap.companyCode, channel, targetValue)
        if (codeCooldown.remaining(requestKey, SystemClock.elapsedRealtime()) > 0) return
        val generation = ++requestGeneration
        codeCooldown.begin(requestKey, SystemClock.elapsedRealtime())
        sendingCode = true
        setLoading(true, getString(R.string.xingdun_sending_verification_code))
        refreshCooldown()
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.sendResetCode(bootstrap.companyCode, channel, targetValue)
            }.onSuccess { response ->
                if (!ownsRequest(generation, requestKey)) return@onSuccess
                setLoading(false, "")
                formUi.message(getString(R.string.xingdun_reset_code_requested, ((response.expiresIn ?: 300L) + 59L) / 60L))
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (!ownsRequest(generation, requestKey)) return@onFailure
                setLoading(false, "")
                formUi.message(getString(XingDunAuthenticationErrorPresenter.reset(error)), true)
            }
        }
    }

    private fun ownsRequest(generation: Long, key: String): Boolean =
        generation == requestGeneration && key == currentCooldownKey() &&
            XingDunSessionManager.currentEnterprise()?.companyCode == bootstrap.companyCode

    private fun currentCooldownKey(): String = codeCooldown.key(
        bootstrap.companyCode, if (phoneMode) "phone" else "email", target.text.toString()
    )

    private fun refreshCooldown() {
        cooldown?.cancel()
        val remaining = codeCooldown.remaining(currentCooldownKey(), SystemClock.elapsedRealtime())
        getCode.isEnabled = !loading && remaining == 0L
        getCode.text = if (remaining > 0) getString(R.string.xingdun_resend_seconds, (remaining + 999L) / 1_000L)
            else getString(R.string.xingdun_get_verification_code)
        if (remaining == 0L) return
        cooldown = object : CountDownTimer(remaining, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                getCode.isEnabled = false
                getCode.text = getString(R.string.xingdun_resend_seconds, (millisUntilFinished + 999L) / 1_000L)
            }

            override fun onFinish() {
                refreshCooldown()
            }
        }.start()
    }

    private fun resetPassword() {
        if (loading) return
        val targetValue = target.text.toString().trim()
        formUi.hideKeyboard()
        formUi.clearErrors()
        val checks = listOf(
            target to (if (phoneMode) XingDunAuthenticationInputValidator.phoneError(targetValue) else XingDunAuthenticationInputValidator.emailError(targetValue)),
            code to XingDunAuthenticationInputValidator.codeError(code.text.toString()),
            newPassword to when {
                newPassword.text.isEmpty() -> XingDunAuthenticationInputError.PASSWORD_REQUIRED
                newPassword.text.length !in 6..32 -> XingDunAuthenticationInputError.PASSWORD_LENGTH
                else -> null
            },
            confirmation to if (confirmation.text.isNotEmpty() && confirmation.text.length !in 6..32)
                XingDunAuthenticationInputError.PASSWORD_LENGTH
            else XingDunAuthenticationInputValidator.confirmationError(newPassword.text.toString(), confirmation.text.toString())
        )
        checks.forEach { (field, error) -> if (error != null) formUi.error(field, error.messageResource()) }
        checks.firstOrNull { it.second != null }?.let { formUi.reveal(it.first); return }
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
                newPassword.text.clear(); confirmation.text.clear(); code.text.clear()
                setResult(Activity.RESULT_OK)
                finish()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                setLoading(false, "")
                formUi.message(getString(XingDunAuthenticationErrorPresenter.reset(error)), true)
            }
        }
    }

    private fun setLoading(value: Boolean, message: String) {
        loading = value
        status.text = ""
        if (!value) sendingCode = false
        findViewById<TextView>(R.id.xingdun_password_reset_primary_action_label).visibility = if (value && !sendingCode) View.INVISIBLE else View.VISIBLE
        findViewById<ProgressBar>(R.id.travel_reset_progress).apply { visibility = if (value && !sendingCode) View.VISIBLE else View.GONE; contentDescription = message }
        findViewById<ProgressBar>(R.id.travel_reset_code_progress).apply { visibility = if (sendingCode) View.VISIBLE else View.GONE; contentDescription = message }
        getCode.visibility = if (sendingCode) View.INVISIBLE else View.VISIBLE
        primaryAction.alpha = if (value) .55f else 1f
        listOf(code, newPassword, confirmation).forEach { it.isEnabled = !value }
        primaryAction.isEnabled = !value
        phoneTab.isEnabled = !value
        emailTab.isEnabled = !value
        target.isEnabled = !value
        refreshCooldown()
    }

    private fun XingDunAuthenticationInputError.messageResource(): Int = when (this) {
        XingDunAuthenticationInputError.PHONE_REQUIRED -> R.string.xingdun_phone_required
        XingDunAuthenticationInputError.PHONE_FORMAT -> R.string.xingdun_phone_invalid
        XingDunAuthenticationInputError.EMAIL_REQUIRED -> R.string.xingdun_email_required
        XingDunAuthenticationInputError.EMAIL_FORMAT -> R.string.xingdun_email_invalid
        XingDunAuthenticationInputError.CODE_REQUIRED -> R.string.xingdun_code_required
        XingDunAuthenticationInputError.CODE_FORMAT -> R.string.xingdun_code_invalid
        XingDunAuthenticationInputError.PASSWORD_REQUIRED -> R.string.xingdun_new_password_required
        XingDunAuthenticationInputError.PASSWORD_LENGTH -> R.string.ta_reset_password_length
        XingDunAuthenticationInputError.PASSWORD_WHITESPACE -> R.string.xingdun_password_whitespace
        XingDunAuthenticationInputError.PASSWORD_CATEGORIES -> R.string.xingdun_password_categories
        XingDunAuthenticationInputError.PASSWORD_IDENTIFIER -> R.string.xingdun_password_identifier
        XingDunAuthenticationInputError.PASSWORD_WEAK -> R.string.xingdun_password_weak
        XingDunAuthenticationInputError.CONFIRM_PASSWORD_REQUIRED -> R.string.xingdun_confirm_new_password_required
        XingDunAuthenticationInputError.PASSWORD_MISMATCH -> R.string.xingdun_password_mismatch
        else -> R.string.xingdun_authentication_failed
    }

}
