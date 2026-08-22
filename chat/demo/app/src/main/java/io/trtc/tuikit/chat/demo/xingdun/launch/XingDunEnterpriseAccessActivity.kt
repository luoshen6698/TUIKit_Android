package io.trtc.tuikit.chat.demo.xingdun.launch

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.launch

class XingDunEnterpriseAccessActivity : AppCompatActivity() {

    private lateinit var codeMode: Button
    private lateinit var domainMode: Button
    private lateinit var input: EditText
    private lateinit var inputLabel: TextView
    private lateinit var status: TextView
    private lateinit var connect: Button
    private lateinit var progress: ProgressBar
    private lateinit var enterpriseLogo: XingDunEnterpriseLogoView
    private var lookupMode = XingDunEnterpriseLookupMode.COMPANY_CODE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.xingdun_activity_enterprise_access)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.WHITE
        bindViews()
        bindActions()
        restoreMode(savedInstanceState)
        handleInitialRoute()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setLoading(false)
        applyInvitation(intent.data)
        handleInitialRoute()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_LOOKUP_MODE, lookupMode.name)
        super.onSaveInstanceState(outState)
    }

    private fun bindViews() {
        codeMode = findViewById(R.id.xingdun_enterprise_code_mode)
        domainMode = findViewById(R.id.xingdun_enterprise_domain_mode)
        input = findViewById(R.id.xingdun_enterprise_lookup_input)
        inputLabel = findViewById(R.id.xingdun_enterprise_input_label)
        status = findViewById(R.id.xingdun_enterprise_status)
        connect = findViewById(R.id.xingdun_enterprise_connect)
        progress = findViewById(R.id.xingdun_enterprise_progress)
        enterpriseLogo = findViewById(R.id.xingdun_enterprise_logo)
    }

    private fun bindActions() {
        codeMode.setOnClickListener { selectMode(XingDunEnterpriseLookupMode.COMPANY_CODE) }
        domainMode.setOnClickListener { selectMode(XingDunEnterpriseLookupMode.DOMAIN) }
        connect.setOnClickListener { connect() }
        input.doAfterTextChanged { status.text = "" }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                connect()
                true
            } else {
                false
            }
        }
    }

    private fun restoreMode(savedInstanceState: Bundle?) {
        lookupMode = savedInstanceState?.getString(STATE_LOOKUP_MODE)
            ?.let { runCatching { XingDunEnterpriseLookupMode.valueOf(it) }.getOrNull() }
            ?: XingDunEnterpriseLookupMode.COMPANY_CODE
        selectMode(lookupMode, clearsInput = false)
        applyInvitation(intent?.data)
    }

    private fun selectMode(mode: XingDunEnterpriseLookupMode, clearsInput: Boolean = true) {
        if (lookupMode != mode && clearsInput) input.text?.clear()
        lookupMode = mode
        status.text = ""
        val isCode = mode == XingDunEnterpriseLookupMode.COMPANY_CODE
        codeMode.setBackgroundResource(
            if (isCode) R.drawable.xingdun_bg_enterprise_segment_selected
            else R.drawable.xingdun_bg_enterprise_segment
        )
        domainMode.setBackgroundResource(
            if (isCode) R.drawable.xingdun_bg_enterprise_segment
            else R.drawable.xingdun_bg_enterprise_segment_selected
        )
        codeMode.isSelected = isCode
        domainMode.isSelected = !isCode
        inputLabel.setText(if (isCode) R.string.xingdun_company_code else R.string.xingdun_enterprise_domain)
        input.setHint(
            if (isCode) R.string.xingdun_enterprise_code_placeholder
            else R.string.xingdun_enterprise_domain_placeholder
        )
        input.inputType = if (isCode) {
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        } else {
            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
    }

    private fun handleInitialRoute() {
        val invitationCompany = invitationCompanyCode(intent?.data)
        val stored = XingDunSessionManager.currentEnterprise()
        if (invitationCompany != null && !stored?.companyCode.equals(invitationCompany, ignoreCase = true)) {
            selectMode(XingDunEnterpriseLookupMode.COMPANY_CODE)
            input.setText(invitationCompany)
            return
        }
        if (stored != null) {
            enterpriseLogo.loadLogo(lifecycleScope, stored.company?.logoUrl)
            revalidateStoredEnterprise(stored)
            return
        }
        setLoading(true, getString(R.string.xingdun_enterprise_preparing))
        lifecycleScope.launch {
            runCatching { XingDunSessionManager.attemptSimpleEnterprise() }
                .onSuccess { bootstrap ->
                    if (bootstrap == null) {
                        setLoading(false)
                    } else {
                        openAuthentication()
                    }
                }
                .onFailure { setLoading(false) }
        }
    }

    private fun revalidateStoredEnterprise(stored: XingDunBootstrapConfiguration) {
        setLoading(true, getString(R.string.xingdun_enterprise_revalidating))
        lifecycleScope.launch {
            runCatching { XingDunSessionManager.resolveEnterprise(stored.companyCode, null) }
                .onSuccess { bootstrap ->
                    enterpriseLogo.loadLogo(lifecycleScope, bootstrap.company?.logoUrl)
                    openAuthentication()
                }
                .onFailure { error ->
                    if (XingDunSessionManager.shouldRetainCachedEnterprise(error)) {
                        openAuthentication()
                    } else {
                        XingDunSessionManager.clearEnterpriseSelection()
                        setLoading(false, getString(R.string.xingdun_enterprise_expired))
                    }
                }
        }
    }

    private fun connect() {
        val lookup = XingDunEnterpriseInputValidator.resolve(
            lookupMode,
            if (lookupMode == XingDunEnterpriseLookupMode.COMPANY_CODE) input.text.toString() else "",
            if (lookupMode == XingDunEnterpriseLookupMode.DOMAIN) input.text.toString() else ""
        ).getOrElse { error ->
            status.setText(validationMessage((error as XingDunEnterpriseInputException).error))
            return
        }
        setLoading(true, getString(R.string.xingdun_enterprise_connecting))
        val previousCompanyCode = XingDunSessionManager.currentEnterprise()?.companyCode
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.resolveEnterprise(lookup.companyCode, lookup.domain)
            }.onSuccess { bootstrap ->
                enterpriseLogo.loadLogo(lifecycleScope, bootstrap.company?.logoUrl)
                if (previousCompanyCode != null &&
                    !previousCompanyCode.equals(bootstrap.companyCode, ignoreCase = true)
                ) {
                    XingDunSessionManager.clear()
                }
                openAuthentication()
            }.onFailure { error ->
                setLoading(
                    false,
                    error.localizedMessage?.takeIf(String::isNotBlank)
                        ?: getString(R.string.xingdun_enterprise_connection_failed)
                )
            }
        }
    }

    private fun openAuthentication() {
        startActivity(Intent(this, XingDunLaunchActivity::class.java).apply {
            data = intent?.data
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        finish()
    }

    private fun applyInvitation(uri: Uri?) {
        invitationCompanyCode(uri)?.let { company ->
            selectMode(XingDunEnterpriseLookupMode.COMPANY_CODE)
            input.setText(company)
        }
    }

    private fun invitationCompanyCode(uri: Uri?): String? {
        if (uri == null) return null
        val validRoute = (uri.scheme == "xingdun" && uri.host == "invite") ||
            (uri.scheme == "https" && uri.host == "api.xingdunim.com" && uri.path == "/prod/xingdun/share.html")
        if (!validRoute) return null
        return listOf("company_code", "companyCode")
            .firstNotNullOfOrNull(uri::getQueryParameter)
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.length in 4..20 && it.all(Char::isLetterOrDigit) }
    }

    private fun setLoading(loading: Boolean, message: String = "") {
        codeMode.isEnabled = !loading
        domainMode.isEnabled = !loading
        input.isEnabled = !loading
        connect.isEnabled = !loading
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        status.text = message
    }

    private fun validationMessage(error: XingDunEnterpriseInputError): Int = when (error) {
        XingDunEnterpriseInputError.COMPANY_CODE_REQUIRED -> R.string.xingdun_enterprise_code_required
        XingDunEnterpriseInputError.COMPANY_CODE_INVALID -> R.string.xingdun_error_company_code
        XingDunEnterpriseInputError.DOMAIN_REQUIRED -> R.string.xingdun_enterprise_domain_required
        XingDunEnterpriseInputError.DOMAIN_INVALID -> R.string.xingdun_enterprise_domain_invalid
    }

    companion object {
        private const val STATE_LOOKUP_MODE = "lookup_mode"
    }
}
