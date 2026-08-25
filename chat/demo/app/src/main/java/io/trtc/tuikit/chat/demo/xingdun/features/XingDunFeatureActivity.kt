package io.trtc.tuikit.chat.demo.xingdun.features

import android.Manifest
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.InputType
import android.text.format.Formatter
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.tencent.mmkv.MMKV
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.google.zxing.common.HybridBinarizer
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.contact.GetContactInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GetGroupInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.chat.app.BuildConfig
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.demo.xingdun.launch.XingDunAuthUiSupport
import io.trtc.tuikit.chat.demo.xingdun.launch.XingDunEnterpriseLogoView
import io.trtc.tuikit.chat.demo.xingdun.launch.XingDunLaunchActivity
import io.trtc.tuikit.chat.demo.xingdun.features.workspace.XingDunWorkspaceContracts
import io.trtc.tuikit.chat.demo.xingdun.features.workspace.XingDunWorkspaceSubmissionError
import io.trtc.tuikit.chat.demo.xingdun.features.workspace.XingDunWorkspaceSubmissionValidator
import io.trtc.tuikit.chat.demo.xingdun.features.workspace.XingDunWorkspaceType
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunQRCodeParser
import io.trtc.tuikit.chat.demo.xingdun.routing.XingDunQRCodeRoute
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunAccountDeletionReceiptStore
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantSessionCoordinator
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

/** Thin, product-owned screens for XingDun services that are not provided by TUIKit. */
open class XingDunFeatureActivity : BaseActivity() {

    private data class InviteInformation(
        val inviteCode: String,
        val shareUrl: String,
        val qrPayload: String,
    )

    private data class FeedbackSubmissionResult(
        val feedbackNo: String,
        val duplicate: Boolean,
    )

    override val requiresLogin: Boolean
        get() = !(BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_BYPASS_LOGIN, false))

    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var headerBar: FrameLayout
    private val mode: String by lazy { intent.getStringExtra(EXTRA_MODE).orEmpty() }
    private val itemId: Int by lazy { intent.getIntExtra(EXTRA_ITEM_ID, 0) }
    private val targetID: String by lazy { intent.getStringExtra(EXTRA_TARGET_ID).orEmpty() }
    private val targetType: String by lazy { intent.getStringExtra(EXTRA_TARGET_TYPE).orEmpty() }
    private var attachmentSelectionHandler: ((List<XingDunAttachment>) -> Unit)? = null
    private var pendingInvitePoster: Bitmap? = null
    private var pendingPersonalQRCode: XingDunPersonalQRCodeArtifact? = null
    private var legalWebView: WebView? = null
    private var reportTargetFilter: String? = null
    private var reportStatusFilter: Int? = null
    private var reportPage = 1
    private var reportTotal = 0
    private var reportLoading = false
    private var reportTouchStartY = 0f
    private val reportRecords = mutableListOf<JsonObject>()
    private var reportListContainer: LinearLayout? = null
    private val debugReportFixtureEnabled: Boolean
        get() = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_REPORT_FIXTURE, false)

    private val attachmentPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        lifecycleScope.launch {
            runCatching { XingDunAttachmentResolver.metadata(this@XingDunFeatureActivity, uris) }
                .onSuccess { attachmentSelectionHandler?.invoke(it) }
                .onFailure(::showAttachmentFailure)
        }
    }

    private val qrScanner = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let(::handleScannedPayload)
    }

    private val qrImagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            runCatching { decodeQRCode(uri) }
                .onSuccess(::handleScannedPayload)
                .onFailure { status.setText(R.string.xingdun_qr_unrecognized) }
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(
            this,
            if (granted) R.string.xingdun_permission_granted_feedback else R.string.xingdun_permission_denied_feedback,
            Toast.LENGTH_SHORT,
        ).show()
        content.removeAllViews()
        showNotificationSettings()
    }

    private val systemNotificationSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        content.removeAllViews()
        showNotificationSettings()
    }

    private val managedPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(
            this,
            getString(if (granted) R.string.xingdun_permission_granted_feedback else R.string.xingdun_permission_denied_feedback),
            Toast.LENGTH_SHORT,
        ).show()
        content.removeAllViews()
        showPermissionManagement()
    }

    private val managedPermissionSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        content.removeAllViews()
        showPermissionManagement()
    }

    private val accountChildResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && mode == MODE_ACCOUNT_SECURITY) {
            content.removeAllViews()
            showAccountSecurity()
        }
    }

    private val invitePosterStoragePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val poster = pendingInvitePoster
        pendingInvitePoster = null
        if (granted && poster != null) saveInvitePoster(poster)
        else showInvitePosterSettingsPrompt()
    }

    private val personalQRCodeStoragePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val artifact = pendingPersonalQRCode
        pendingPersonalQRCode = null
        if (granted && artifact != null) savePersonalQRCode(artifact)
        else showPersonalQRCodeSettingsPrompt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        buildShell()
        when (mode) {
            MODE_WORKSPACE_LIST -> showWorkspaceList()
            MODE_WORKSPACE_PENDING -> showWorkspacePending()
            MODE_WORKSPACE_DETAIL -> showWorkspaceDetail()
            MODE_WORKSPACE_CREATE -> showWorkspaceForm()
            MODE_CUSTOMER_SERVICE -> showCustomerService()
            MODE_CUSTOMER_SERVICE_GROUP -> showCustomerServiceGroup()
            MODE_INVITE -> showInvite()
            MODE_FEEDBACK -> showFeedbackForm()
            MODE_VERSION -> showVersion()
            MODE_REPORTS -> showReports()
            MODE_REPORT_DETAIL -> showReportDetail()
            MODE_REPORT_CREATE -> showReportForm()
            MODE_PERSONAL_QR -> showPersonalQRCode()
            MODE_QR_SCANNER -> showQRCodeScanner()
            MODE_ACCOUNT_SECURITY -> showAccountSecurity()
            MODE_BIND_PHONE -> showContactBinding("phone")
            MODE_BIND_EMAIL -> showContactBinding("email")
            MODE_CHANGE_PASSWORD -> showChangePassword()
            MODE_DEVICES -> showDevices()
            MODE_DEACTIVATE -> showDeactivation()
            MODE_DEACTIVATION_RULES -> showDeactivationRules()
            MODE_NOTIFICATIONS -> showNotificationSettings()
            MODE_STORAGE -> showStorageManagement()
            MODE_HELP -> showHelpCenter()
            MODE_PERMISSIONS -> showPermissionManagement()
            MODE_LANGUAGE -> showLanguageSettings()
            MODE_ABOUT -> showAbout()
            MODE_USER_AGREEMENT -> showLegalDocument(false)
            MODE_PRIVACY_POLICY -> showLegalDocument(true)
            MODE_FAVORITES -> showFavorites()
            MODE_REDPACKET_ACCOUNT -> showRedpacketAccount()
            MODE_REDPACKET_DETAIL -> showRedpacketDetail()
            else -> finish()
        }
    }

    override fun onDestroy() {
        legalWebView?.apply {
            stopLoading()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        legalWebView = null
        super.onDestroy()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        headerBar = FrameLayout(this).apply {
            addView(TextView(context).apply {
                text = "‹"
                textSize = 34f
                gravity = Gravity.CENTER
                contentDescription = getString(R.string.xingdun_back)
                setTextColor(Color.BLACK)
                isClickable = true
                isFocusable = true
                setOnClickListener { finish() }
            }, FrameLayout.LayoutParams(52.dp(), 52.dp(), Gravity.START or Gravity.CENTER_VERTICAL))
            addView(TextView(context).apply {
                text = titleForMode()
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                maxLines = 1
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp(), Gravity.CENTER).apply {
                marginStart = 58.dp()
                marginEnd = 58.dp()
            })
        }
        root.addView(headerBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()))
        scrollView = ScrollView(this).apply {
            isFillViewport = true
            content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18.dp(), 14.dp(), 18.dp(), 30.dp())
            }
            addView(content)
        }
        root.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        status = TextView(this).apply {
            setPadding(18.dp(), 10.dp(), 18.dp(), 10.dp())
            textSize = 14f
        }
        root.addView(status)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
        setContentView(root)
    }

    private fun showWorkspaceList() {
        showWorkspaceApplications("workspace/mine", R.string.xingdun_workspace_empty)
    }

    private fun showWorkspacePending() {
        showWorkspaceApplications("workspace/pending", R.string.xingdun_workspace_pending_empty)
    }

    private fun showWorkspaceApplications(path: String, emptyMessage: Int) {
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                val session = requireSession()
                XingDunSessionManager.apiClient().get<JsonObject>(
                    session,
                    path,
                    mapOf("page" to "1", "page_size" to "50"),
                    JsonObject::class.java
                )
            }.onSuccess { page ->
                setBusy(false)
                val list = page.array("list")
                if (list.isEmpty) addMessage(emptyMessage)
                list.forEach { element ->
                    val item = element.asJsonObject
                    addCard(
                        item.string("title").orEmpty().ifBlank { getString(R.string.xingdun_workspace_untitled) },
                        listOfNotNull(
                            item.string("type"),
                            item.string("status_text") ?: item.string("status"),
                            item.string("create_time")
                        ).joinToString(" · ")
                    ) {
                        item.int("id")?.takeIf { it > 0 }?.let {
                            start(this@XingDunFeatureActivity, MODE_WORKSPACE_DETAIL, it)
                        }
                    }
                }
            }.onFailure(::showFailure)
        }
    }

    private fun showWorkspaceDetail() {
        if (itemId <= 0) {
            showFailure(IllegalArgumentException(getString(R.string.xingdun_workspace_invalid_application)))
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    requireSession(), "workspace/detail", mapOf("id" to itemId.toString()), JsonObject::class.java
                )
            }.onSuccess { item ->
                setBusy(false)
                val applicationStatus = item.int("status") ?: 0
                val session = requireSession()
                val applicant = item.string("applicant_tim_user_id")
                val approver = item.string("approver_tim_user_id")
                addCard(
                    item.string("title").orEmpty().ifBlank { getString(R.string.xingdun_workspace_untitled) },
                    listOfNotNull(
                        item.string("application_no"),
                        item.string("type_name") ?: item.string("type"),
                        item.string("status_text"),
                        item.string("reason"),
                        item.string("start_time"),
                        item.string("end_time"),
                        item.string("amount"),
                        item.string("approval_comment"),
                        item.string("create_time")
                    ).joinToString("\n")
                )
                item.getAsJsonObject("applicant")?.let { person ->
                    addCard(
                        getString(R.string.xingdun_workspace_applicant),
                        listOfNotNull(person.string("name") ?: person.string("nickname"), person.string("tim_user_id")).joinToString(" · ")
                    )
                }
                item.getAsJsonObject("approver")?.let { person ->
                    addCard(
                        getString(R.string.xingdun_workspace_approver_title),
                        listOfNotNull(person.string("name") ?: person.string("nickname"), person.string("tim_user_id")).joinToString(" · ")
                    )
                }
                val logs = item.array("logs")
                if (!logs.isEmpty) {
                    addCard(getString(R.string.xingdun_workspace_timeline), "")
                    logs.forEach { element ->
                        val log = element.asJsonObject
                        val operator = log.getAsJsonObject("operator")
                        addCard(
                            log.string("action_text") ?: log.string("status_text") ?: log.string("action") ?: getString(R.string.xingdun_updated),
                            listOfNotNull(
                                operator?.string("name") ?: operator?.string("tim_user_id")
                                    ?: log.string("operator_name") ?: log.string("operator_tim_user_id"),
                                log.string("comment"),
                                log.string("create_time")
                            ).joinToString("\n")
                        )
                    }
                }
                if (applicant == session.timUserId && applicationStatus in 1..2) {
                    content.addView(actionButton(R.string.xingdun_workspace_withdraw) {
                        submitEmpty("workspace/withdraw", mapOf("id" to itemId), R.string.xingdun_workspace_withdrawn)
                    })
                }
                if (approver == session.timUserId && applicationStatus in 1..2) {
                    content.addView(actionButton(R.string.xingdun_workspace_approve) {
                        showWorkspaceDecision("approve", false)
                    })
                    content.addView(actionButton(R.string.xingdun_workspace_reject) {
                        showWorkspaceDecision("reject", true)
                    })
                }
                if (applicant == session.timUserId && applicationStatus in setOf(3, 4, 5)) {
                    content.addView(actionButton(R.string.xingdun_workspace_delete) { deleteWorkspaceApplication() })
                }
            }.onFailure(::showFailure)
        }
    }

    private fun showWorkspaceDecision(action: String, commentRequired: Boolean) {
        val comment = EditText(this).apply {
            setHint(R.string.xingdun_workspace_decision_comment)
            maxLines = 5
        }
        AlertDialog.Builder(this)
            .setTitle(if (action == "approve") R.string.xingdun_workspace_approve else R.string.xingdun_workspace_reject)
            .setView(comment)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_submit) { _, _ ->
                val value = comment.text.toString().trim()
                if (commentRequired && value.isEmpty()) {
                    status.setText(R.string.xingdun_workspace_reject_comment_required)
                } else {
                    submitEmpty(
                        "workspace/handle",
                        mapOf("id" to itemId, "action" to action, "comment" to value),
                        R.string.xingdun_workspace_decided
                    )
                }
            }
            .show()
    }

    private fun deleteWorkspaceApplication() {
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().deleteEmpty(requireSession(), "workspace/delete", mapOf("id" to itemId))
            }.onSuccess {
                setBusy(false)
                status.setText(R.string.xingdun_workspace_deleted)
            }.onFailure(::showFailure)
        }
    }

    private fun showWorkspaceForm() {
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                val values = XingDunSessionManager.apiClient().get<JsonArray>(
                    requireSession(), "workspace/types", emptyMap(), JsonArray::class.java
                )
                XingDunWorkspaceContracts.parseTypes(values).filter(XingDunWorkspaceType::available)
            }.onSuccess(::renderWorkspaceForm).onFailure(::showFailure)
        }
    }

    private fun renderWorkspaceForm(types: List<XingDunWorkspaceType>) {
        setBusy(false)
        if (types.isEmpty()) {
            addMessage(R.string.xingdun_workspace_no_available_types)
            return
        }
        val initialIndex = types.indexOfFirst { it.type == targetID }.takeIf { it >= 0 } ?: 0
        val type = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@XingDunFeatureActivity,
                android.R.layout.simple_spinner_dropdown_item,
                types.map(XingDunWorkspaceType::name)
            )
            setSelection(initialIndex)
        }
        val title = input(R.string.xingdun_workspace_form_title)
        val reason = input(R.string.xingdun_workspace_form_reason, multiline = true)
        val start = input(R.string.xingdun_workspace_form_start)
        val end = input(R.string.xingdun_workspace_form_end)
        val amount = input(R.string.xingdun_workspace_form_amount, decimal = true)
        content.addView(type)
        listOf(title, reason, start, end, amount).forEach(content::addView)
        val updateRequirements = {
            val selected = types[type.selectedItemPosition]
            start.visibility = if (selected.requiresTime) View.VISIBLE else View.GONE
            end.visibility = if (selected.requiresTime) View.VISIBLE else View.GONE
            amount.visibility = if (selected.requiresAmount) View.VISIBLE else View.GONE
            status.text = selected.approverName?.let { getString(R.string.xingdun_workspace_approver, it) }.orEmpty()
        }
        type.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = updateRequirements()
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        updateRequirements()
        content.addView(actionButton(R.string.xingdun_submit) {
            val selected = types[type.selectedItemPosition]
            val validation = XingDunWorkspaceSubmissionValidator.validate(
                selected,
                title.text.toString(),
                reason.text.toString(),
                start.text.toString(),
                end.text.toString(),
                amount.text.toString()
            )
            if (validation != null) {
                status.setText(when (validation) {
                    XingDunWorkspaceSubmissionError.TITLE -> R.string.xingdun_workspace_title_required
                    XingDunWorkspaceSubmissionError.REASON -> R.string.xingdun_workspace_reason_invalid
                    XingDunWorkspaceSubmissionError.TIME -> R.string.xingdun_workspace_time_invalid
                    XingDunWorkspaceSubmissionError.AMOUNT -> R.string.xingdun_workspace_amount_invalid
                })
                return@actionButton
            }
            val body = linkedMapOf<String, Any?>(
                "type" to selected.type,
                "title" to title.text.toString().trim(),
                "reason" to reason.text.toString().trim().takeIf(String::isNotEmpty),
                "start_time" to start.text.toString().trim().takeIf { selected.requiresTime && it.isNotEmpty() },
                "end_time" to end.text.toString().trim().takeIf { selected.requiresTime && it.isNotEmpty() },
                "amount" to amount.text.toString().trim().toBigDecimalOrNull().takeIf { selected.requiresAmount },
                "client_request_id" to UUID.randomUUID().toString()
            )
            submitEmpty("workspace/save", body, R.string.xingdun_workspace_submitted)
        })
    }

    private fun showCustomerService() {
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                val session = requireSession()
                val identity = XingDunSessionManager.apiClient().get<JsonObject>(
                    session, "cs/identity", emptyMap(), JsonObject::class.java
                )
                val users = if (identity.boolean("is_cs")) XingDunSessionManager.apiClient().get<JsonArray>(
                    session, "cs/myUsers", emptyMap(), JsonArray::class.java
                ) else JsonArray()
                val groups = if (identity.boolean("is_cs")) XingDunSessionManager.apiClient().get<JsonArray>(
                    session, "cs/myGroups", emptyMap(), JsonArray::class.java
                ) else JsonArray()
                Triple(identity, users, groups)
            }.onSuccess { (identity, users, groups) ->
                setBusy(false)
                addCard(
                    getString(R.string.xingdun_customer_service),
                    if (identity.boolean("is_cs")) getString(R.string.xingdun_customer_service_agent)
                    else getString(R.string.xingdun_customer_service_user)
                )
                if (identity.boolean("is_cs")) {
                    addCard(getString(R.string.xingdun_bound_users, users.size()), "")
                    if (users.isEmpty) addMessage(R.string.xingdun_customer_service_no_users)
                    users.forEach { element ->
                        val user = element.asJsonObject.getAsJsonObject("user") ?: JsonObject()
                        val timUserID = user.string("tim_user_id").orEmpty()
                        addCard(
                            user.string("nickname") ?: timUserID,
                            user.string("custom_id") ?: timUserID
                        ) {
                            if (timUserID.isNotBlank()) showCustomerServiceUserActions(timUserID)
                        }
                    }
                    addCard(getString(R.string.xingdun_customer_service_groups, groups.size()), "")
                    if (groups.isEmpty) addMessage(R.string.xingdun_customer_service_no_groups)
                    groups.forEach { element ->
                        val group = element.asJsonObject
                        val groupID = group.string("group_id").orEmpty()
                        addCard(
                            group.string("name") ?: groupID,
                            getString(
                                R.string.xingdun_customer_service_group_summary,
                                group.int("member_count") ?: 0,
                                if (group.boolean("mute_all")) getString(R.string.xingdun_muted) else getString(R.string.xingdun_not_muted)
                            )
                        ) {
                            if (groupID.isNotBlank()) start(this@XingDunFeatureActivity, MODE_CUSTOMER_SERVICE_GROUP, groupID)
                        }
                    }
                    return@onSuccess
                }
                val official = identity.string("official_cs_tim_user_id")
                val assigned = identity.array("customer_services").firstOrNull()?.asJsonObject?.string("tim_user_id")
                val target = official?.takeIf(String::isNotBlank) ?: assigned?.takeIf(String::isNotBlank)
                if (target != null) {
                    content.addView(actionButton(R.string.xingdun_open_customer_service) {
                        ChatActivity.start(this@XingDunFeatureActivity, "c2c_$target")
                    })
                } else {
                    addMessage(R.string.xingdun_customer_service_unavailable)
                }
            }.onFailure(::showFailure)
        }
    }

    private fun showCustomerServiceUserActions(timUserID: String) {
        AlertDialog.Builder(this)
            .setTitle(timUserID)
            .setItems(
                arrayOf(getString(R.string.xingdun_open_chat), getString(R.string.xingdun_customer_service_session_info))
            ) { _, which ->
                if (which == 0) ChatActivity.start(this, "c2c_$timUserID") else loadCustomerServiceSessionInfo(timUserID)
            }
            .show()
    }

    private fun loadCustomerServiceSessionInfo(timUserID: String) {
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    requireSession(),
                    "cs/userSessionInfo",
                    mapOf("tim_user_id" to timUserID),
                    JsonObject::class.java
                )
            }.onSuccess { info ->
                setBusy(false)
                AlertDialog.Builder(this@XingDunFeatureActivity)
                    .setTitle(R.string.xingdun_customer_service_session_info)
                    .setMessage(
                        listOfNotNull(
                            info.string("session_ip"),
                            info.string("session_location"),
                            info.string("client_type"),
                            info.string("msg_time"),
                            info.string("hint")
                        ).joinToString("\n").ifBlank { getString(R.string.xingdun_customer_service_session_empty) }
                    )
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }.onFailure(::showFailure)
        }
    }

    private fun showCustomerServiceGroup() {
        if (targetID.isBlank()) {
            showFailure(IllegalArgumentException(getString(R.string.xingdun_invalid_group)))
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                val session = requireSession()
                val groups = XingDunSessionManager.apiClient().get<JsonArray>(
                    session, "cs/myGroups", emptyMap(), JsonArray::class.java
                )
                val group = groups.map(JsonElement::getAsJsonObject)
                    .firstOrNull { it.string("group_id") == targetID }
                    ?: throw IllegalStateException(getString(R.string.xingdun_customer_service_group_unavailable))
                val members = XingDunSessionManager.apiClient().get<JsonObject>(
                    session,
                    "team/members",
                    mapOf("team_id" to targetID, "page" to "1", "pageSize" to "200"),
                    JsonObject::class.java
                )
                group to members.array("list")
            }.onSuccess { (group, members) ->
                setBusy(false)
                val announcement = input(R.string.xingdun_group_announcement, multiline = true).apply {
                    setText(group.string("announcement").orEmpty())
                }
                addCard(group.string("name") ?: targetID, getString(R.string.xingdun_group_members_count, members.size()))
                content.addView(actionButton(R.string.xingdun_open_group_chat) {
                    ChatActivity.start(this@XingDunFeatureActivity, "group_$targetID")
                })
                content.addView(announcement)
                content.addView(actionButton(R.string.xingdun_save_announcement) {
                    val value = announcement.text.toString().trim()
                    if (value.toByteArray().size > 300) status.setText(R.string.xingdun_announcement_too_long)
                    else submitCustomerServiceAction(
                        "cs/updateGroupAnnouncement",
                        mapOf("team_id" to targetID, "announcement" to value),
                        R.string.xingdun_saved
                    )
                })
                val muted = group.boolean("mute_all")
                content.addView(actionButton(if (muted) R.string.xingdun_disable_mute_all else R.string.xingdun_enable_mute_all) {
                    submitCustomerServiceAction(
                        "cs/setGroupMuteAll",
                        mapOf("team_id" to targetID, "mute" to !muted),
                        R.string.xingdun_saved,
                        refresh = true
                    )
                })
                addCard(getString(R.string.xingdun_member_management), "")
                members.forEach { element ->
                    val member = element.asJsonObject
                    val userID = member.string("user_id").orEmpty()
                    addCard(
                        member.string("nickname") ?: userID,
                        listOfNotNull(
                            userID,
                            member.string("role"),
                            if (member.boolean("is_muted")) getString(R.string.xingdun_muted) else null
                        ).joinToString(" · ")
                    ) {
                        if (userID.isNotBlank()) showCustomerServiceMemberActions(member)
                    }
                }
            }.onFailure(::showFailure)
        }
    }

    private fun showCustomerServiceMemberActions(member: JsonObject) {
        val userID = member.string("user_id").orEmpty()
        val role = member.string("role").orEmpty()
        if (userID.isBlank() || role == "owner") return
        val isMuted = member.boolean("is_muted")
        val isAdministrator = role == "administrator"
        val labels = arrayOf(
            getString(if (isMuted) R.string.xingdun_unmute_member else R.string.xingdun_mute_member),
            getString(if (isAdministrator) R.string.xingdun_remove_administrator else R.string.xingdun_set_administrator),
            getString(R.string.xingdun_remove_member)
        )
        AlertDialog.Builder(this)
            .setTitle(member.string("nickname") ?: userID)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> submitCustomerServiceAction(
                        "cs/muteGroupMember",
                        mapOf(
                            "team_id" to targetID,
                            "member_tim_user_id" to userID,
                            "mute" to !isMuted,
                            "duration_seconds" to if (isMuted) 0 else 31_536_000
                        ),
                        R.string.xingdun_saved,
                        refresh = true
                    )
                    1 -> submitCustomerServiceAction(
                        "cs/setGroupAdministrator",
                        mapOf(
                            "team_id" to targetID,
                            "member_tim_user_id" to userID,
                            "is_administrator" to !isAdministrator
                        ),
                        R.string.xingdun_saved,
                        refresh = true
                    )
                    2 -> AlertDialog.Builder(this)
                        .setMessage(R.string.xingdun_confirm_remove_member)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.xingdun_remove_member) { _, _ ->
                            submitCustomerServiceAction(
                                "cs/removeGroupMember",
                                mapOf("team_id" to targetID, "member_tim_user_id" to userID),
                                R.string.xingdun_saved,
                                refresh = true
                            )
                        }
                        .show()
                }
            }
            .show()
    }

    private fun submitCustomerServiceAction(path: String, body: Any, successMessage: Int, refresh: Boolean = false) {
        setBusy(true)
        lifecycleScope.launch {
            runCatching { XingDunSessionManager.apiClient().postEmpty(requireSession(), path, body) }
                .onSuccess {
                    setBusy(false)
                    status.setText(successMessage)
                    if (refresh) {
                        content.removeAllViews()
                        showCustomerServiceGroup()
                    }
                }
                .onFailure(::showFailure)
        }
    }

    private fun showInvite() {
        applyPersonalQRCodeChrome()
        showInvitePosterLoading()
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                val session = requireSession()
                val response = XingDunSessionManager.apiClient().get<JsonObject>(
                    session, "share/inviteInfo", emptyMap(), JsonObject::class.java
                )
                validateInviteInformation(response, session.companyCode)
            }.onSuccess { invitation ->
                setBusy(false)
                val qrBitmap = BarcodeEncoder().encodeBitmap(
                    invitation.qrPayload,
                    BarcodeFormat.QR_CODE,
                    720,
                    720,
                )
                val poster = createInvitePoster(qrBitmap, invitation.inviteCode, requireSession().nickname)
                renderInvitePoster(poster, invitation.shareUrl)
            }.onFailure {
                setBusy(false)
                showInvitePosterUnavailable()
            }
        }
    }

    private fun validateInviteInformation(response: JsonObject, currentCompanyCode: String): InviteInformation {
        val inviteCode = response.string("invite_code")?.lowercase().orEmpty()
        val shareUrl = response.string("share_url").orEmpty()
        val deepLink = response.string("deep_link").orEmpty()
        val qrPayload = response.string("qr_payload").orEmpty()
        val invitedCount = response.int("invited_count") ?: -1
        require(inviteCode.matches(Regex("^[23456789abcdefghjkmnpqrstuvwxyz]{6,20}$")))
        require(invitedCount >= 0)
        val shareUri = Uri.parse(shareUrl)
        require(shareUri.scheme.equals("https", ignoreCase = true) && shareUri.host != null)
        require(shareUri.path?.endsWith("/xingdun/share.html") == true)

        val payloadRoute = XingDunQRCodeParser.parse(qrPayload) as? XingDunQRCodeRoute.Invitation
            ?: error("Invalid invitation QR payload")
        val shareRoute = XingDunQRCodeParser.parse(shareUrl) as? XingDunQRCodeRoute.Invitation
            ?: error("Invalid invitation share URL")
        val deepLinkRoute = XingDunQRCodeParser.parse(deepLink) as? XingDunQRCodeRoute.Invitation
            ?: error("Invalid invitation deep link")
        val companyCode = requireNotNull(payloadRoute.companyCode)
        require(payloadRoute.code == inviteCode && shareRoute.code == inviteCode && deepLinkRoute.code == inviteCode)
        require(shareRoute.companyCode.equals(companyCode, ignoreCase = true))
        require(deepLinkRoute.companyCode.equals(companyCode, ignoreCase = true))
        require(companyCode.equals(currentCompanyCode, ignoreCase = true))
        response.string("company_code")?.let { require(it.equals(companyCode, ignoreCase = true)) }
        return InviteInformation(inviteCode, shareUrl, qrPayload)
    }

    private fun showInvitePosterLoading() {
        content.removeAllViews()
        content.gravity = Gravity.CENTER
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_invite_poster_preparing)
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, 180.dp(), 0, 0)
        })
    }

    private fun renderInvitePoster(poster: Bitmap, shareUrl: String) {
        content.removeAllViews()
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.addView(ImageView(this).apply {
            setImageBitmap(poster)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.xingdun_invite_poster_description)
            background = roundedDrawable(Color.WHITE, 16f)
            clipToOutline = true
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 4.dp()
            marginStart = 30.dp()
            marginEnd = 30.dp()
        })
        content.addView(invitePosterButton(R.string.xingdun_save_invite_poster, primary = true) {
            saveInvitePoster(poster)
        })
        content.addView(invitePosterButton(R.string.xingdun_copy_share_link, primary = false) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.xingdun_copy_share_link), shareUrl))
            showInvitePosterFeedback(R.string.xingdun_share_link_copied)
        })
    }

    private fun invitePosterButton(label: Int, primary: Boolean, action: () -> Unit): Button =
        actionButton(label, action).apply {
            setTextColor(if (primary) Color.WHITE else 0xFF28B7A2.toInt())
            background = roundedDrawable(if (primary) 0xFF28B7A2.toInt() else 0xFF063B36.toInt(), 10f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()).apply {
                topMargin = 12.dp()
                marginStart = 30.dp()
                marginEnd = 30.dp()
            }
        }

    private fun showInvitePosterUnavailable() {
        content.removeAllViews()
        content.gravity = Gravity.CENTER
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_invite_poster_unavailable)
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        })
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_invite_poster_unavailable_detail)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, 10.dp(), 0, 12.dp())
        })
        content.addView(invitePosterButton(R.string.xingdun_retry, primary = true) {
            showInvite()
        })
    }

    private fun createInvitePoster(qrBitmap: Bitmap, inviteCode: String, nickname: String): Bitmap {
        val poster = Bitmap.createBitmap(1_080, 1_440, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(poster)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        canvas.drawColor(Color.rgb(245, 247, 250))
        paint.color = Color.rgb(20, 46, 74)
        canvas.drawRect(0f, 0f, 1_080f, 430f, paint)
        paint.color = Color.rgb(31, 140, 89)
        canvas.drawRect(0f, 414f, 1_080f, 430f, paint)
        drawPosterText(canvas, paint, getString(R.string.xingdun_platform_brand_name), 120f, 62f, Color.WHITE, true)
        drawPosterText(canvas, paint, getString(R.string.xingdun_invite_poster_tagline), 210f, 32f, Color.WHITE)
        drawPosterText(
            canvas,
            paint,
            getString(R.string.xingdun_invite_poster_invitation, nickname.ifBlank { getString(R.string.xingdun_platform_brand_name) }),
            326f,
            32f,
            Color.rgb(220, 229, 238)
        )
        paint.color = Color.WHITE
        canvas.drawRoundRect(90f, 500f, 990f, 1_320f, 24f, 24f, paint)
        canvas.drawBitmap(qrBitmap, null, android.graphics.RectF(230f, 570f, 850f, 1_190f), paint)
        drawPosterText(canvas, paint, getString(R.string.xingdun_invite_poster_code, inviteCode), 1_255f, 34f, Color.rgb(20, 46, 74), true)
        drawPosterText(canvas, paint, getString(R.string.xingdun_invite_poster_scan_hint), 1_390f, 27f, Color.rgb(89, 99, 112))
        return poster
    }

    private fun drawPosterText(
        canvas: Canvas,
        paint: Paint,
        text: String,
        baseline: Float,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) {
        paint.textSize = size
        paint.color = color
        paint.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        while (paint.textSize > 18f && paint.measureText(text) > 900f) {
            paint.textSize -= 1f
        }
        canvas.drawText(text, 540f, baseline, paint)
    }

    private fun saveInvitePoster(poster: Bitmap) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingInvitePoster = poster
            invitePosterStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        lifecycleScope.launch {
            setBusy(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val name = "xingdun_invite_${System.currentTimeMillis()}.png"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, name)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/XingDun")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                            ?: error(getString(R.string.xingdun_invite_poster_save_failed))
                        runCatching {
                            contentResolver.openOutputStream(uri)?.use { output ->
                                check(poster.compress(Bitmap.CompressFormat.PNG, 100, output))
                            } ?: error(getString(R.string.xingdun_invite_poster_save_failed))
                            values.clear()
                            values.put(MediaStore.Images.Media.IS_PENDING, 0)
                            contentResolver.update(uri, values, null, null)
                        }.getOrElse { error ->
                            contentResolver.delete(uri, null, null)
                            throw error
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        val target = File(directory, "XingDun/$name")
                        val parent = requireNotNull(target.parentFile)
                        check(parent.exists() || parent.mkdirs())
                        FileOutputStream(target).use { output ->
                            check(poster.compress(Bitmap.CompressFormat.PNG, 100, output))
                        }
                        @Suppress("DEPRECATION")
                        sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(target)))
                    }
                }
            }.onSuccess {
                setBusy(false)
                showInvitePosterFeedback(R.string.xingdun_invite_poster_saved)
            }.onFailure {
                setBusy(false)
                showInvitePosterFeedback(R.string.xingdun_invite_poster_save_failed)
            }
        }
    }

    private fun showInvitePosterFeedback(message: Int) {
        status.setText(message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showInvitePosterSettingsPrompt() {
        AlertDialog.Builder(this)
            .setMessage(R.string.xingdun_invite_poster_permission_denied)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_open_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .show()
    }

    private fun showFeedbackForm() {
        applyFeedbackFormChrome()
        val requestID = UUID.randomUUID().toString().lowercase()
        val types = resources.getStringArray(R.array.xingdun_feedback_type_values)
        val labels = resources.getStringArray(R.array.xingdun_feedback_type_labels)
        val type = Spinner(this).apply {
            adapter = ArrayAdapter(this@XingDunFeatureActivity, android.R.layout.simple_spinner_dropdown_item, labels.toList())
        }
        val description = input(R.string.xingdun_feedback_content, multiline = true).apply {
            minHeight = 150.dp()
            gravity = Gravity.TOP or Gravity.START
        }
        val descriptionCount = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF8A8A8F.toInt())
            gravity = Gravity.END
        }
        val contact = input(R.string.xingdun_feedback_contact_hint)
        var attachments = emptyList<XingDunAttachment>()
        var result: FeedbackSubmissionResult? = null
        var submitting = false
        val attachmentTitle = TextView(this)
        val attachmentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val attachmentScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(attachmentRow)
        }
        lateinit var addImageButton: Button
        lateinit var submitButton: Button
        lateinit var updateState: () -> Unit
        lateinit var renderAttachments: () -> Unit
        renderAttachments = {
            attachmentTitle.text = getString(R.string.xingdun_feedback_images_count, attachments.size)
            attachmentRow.removeAllViews()
            attachments.forEach { attachment ->
                attachmentRow.addView(feedbackAttachmentPreview(attachment, result == null) {
                    if (result == null) {
                        attachments = attachments.filterNot { it.uri == attachment.uri }
                        renderAttachments()
                        updateState()
                    }
                })
            }
            attachmentScroll.visibility = if (attachments.isEmpty()) View.GONE else View.VISIBLE
        }

        addFeedbackSection(R.string.xingdun_feedback_type_title, type)
        addFeedbackSection(
            R.string.xingdun_feedback_description_title,
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(description)
                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(context).apply {
                        setText(R.string.xingdun_feedback_description_footer)
                        textSize = 12f
                        setTextColor(0xFF8A8A8F.toInt())
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(descriptionCount)
                })
            },
        )

        addFeedbackSectionHeader(attachmentTitle)
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(14.dp(), 10.dp(), 14.dp(), 12.dp())
            addView(attachmentScroll)
            addImageButton = actionButton(R.string.xingdun_feedback_add_image) {
                attachmentSelectionHandler = { selected ->
                    val combined = (attachments + selected).distinctBy(XingDunAttachment::uri)
                    if (combined.size > XingDunAttachmentResolver.MAX_COUNT) {
                        showAttachmentFailure(XingDunAttachmentException(XingDunAttachmentError.TOO_MANY))
                    } else {
                        attachments = combined
                        renderAttachments()
                        updateState()
                    }
                }
                attachmentPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
            }
            addView(addImageButton)
        }, feedbackSectionLayoutParams())

        addFeedbackSection(
            R.string.xingdun_feedback_contact,
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(contact)
                addView(TextView(context).apply {
                    setText(R.string.xingdun_feedback_contact_footer)
                    textSize = 12f
                    setTextColor(0xFF8A8A8F.toInt())
                    setPadding(0, 8.dp(), 0, 0)
                })
            },
        )

        submitButton = actionButton(R.string.xingdun_feedback_submit) {
            result?.let {
                finish()
                return@actionButton
            }
            val normalizedDescription = description.text.toString().trim()
            val normalizedContact = contact.text.toString().trim()
            if (normalizedDescription.length !in 10..2_000) {
                status.setText(R.string.xingdun_feedback_content_required)
                return@actionButton
            }
            if (normalizedContact.length > 128) {
                status.setText(R.string.xingdun_feedback_contact_too_long)
                return@actionButton
            }
            submitting = true
            updateState()
            setBusy(true)
            lifecycleScope.launch {
                runCatching {
                    val files = XingDunAttachmentResolver.uploadFiles(this@XingDunFeatureActivity, attachments)
                    XingDunSessionManager.apiClient().postMultipart<FeedbackSubmissionResult>(
                        session = requireSession(),
                        path = "feedback/save",
                        fields = mapOf(
                            "feedback_type" to types[type.selectedItemPosition],
                            "content" to normalizedDescription,
                            "contact" to normalizedContact,
                            "client_request_id" to requestID,
                            "platform" to "android",
                            "app_version" to BuildConfig.VERSION_NAME,
                            "app_build" to BuildConfig.VERSION_CODE,
                            "os_version" to Build.VERSION.RELEASE,
                            "device_model" to Build.MODEL,
                        ),
                        files = files,
                        responseType = FeedbackSubmissionResult::class.java,
                    )
                }.onSuccess { submission ->
                    result = submission
                    submitting = false
                    setBusy(false)
                    type.isEnabled = false
                    description.isEnabled = false
                    contact.isEnabled = false
                    val message = getString(
                        if (submission.duplicate) R.string.xingdun_feedback_duplicate_result
                        else R.string.xingdun_feedback_success_result,
                        submission.feedbackNo,
                    )
                    status.text = message
                    Toast.makeText(this@XingDunFeatureActivity, message, Toast.LENGTH_LONG).show()
                    submitButton.setText(R.string.xingdun_complete)
                    renderAttachments()
                    updateState()
                }.onFailure { error ->
                    submitting = false
                    updateState()
                    if (error is XingDunAttachmentException) showAttachmentFailure(error) else showFailure(error)
                }
            }
        }
        submitButton.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF18A987.toInt())
        content.addView(submitButton)

        updateState = {
            val count = description.text.toString().count()
            descriptionCount.text = getString(R.string.xingdun_feedback_character_count, count)
            descriptionCount.setTextColor(if (count > 2_000) 0xFFD93025.toInt() else 0xFF8A8A8F.toInt())
            addImageButton.isEnabled = result == null && attachments.size < XingDunAttachmentResolver.MAX_COUNT && !submitting
            submitButton.isEnabled = if (result != null) true else {
                count in 10..2_000 && contact.text.toString().count() <= 128 && !submitting
            }
        }
        description.doAfterTextChanged { updateState() }
        contact.doAfterTextChanged { updateState() }
        renderAttachments()
        updateState()
    }

    private fun applyFeedbackFormChrome() {
        val background = 0xFFF5F5F9.toInt()
        window.statusBarColor = background
        window.navigationBarColor = background
        headerBar.setBackgroundColor(background)
        scrollView.setBackgroundColor(background)
        content.setBackgroundColor(background)
        content.setPadding(20.dp(), 8.dp(), 20.dp(), 32.dp())
        status.setBackgroundColor(background)
        status.setTextColor(0xFF8A8A8F.toInt())
    }

    private fun addFeedbackSection(title: Int, child: View) {
        addFeedbackSectionHeader(TextView(this).apply { setText(title) })
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(14.dp(), 8.dp(), 14.dp(), 12.dp())
            addView(child)
        }, feedbackSectionLayoutParams())
    }

    private fun addFeedbackSectionHeader(header: TextView) {
        header.textSize = 14f
        header.setTextColor(0xFF8A8A8F.toInt())
        header.setPadding(14.dp(), 10.dp(), 8.dp(), 8.dp())
        content.addView(header)
    }

    private fun feedbackSectionLayoutParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = 12.dp() }

    private fun feedbackAttachmentPreview(
        attachment: XingDunAttachment,
        canRemove: Boolean,
        onRemove: () -> Unit,
    ): View =
        FrameLayout(this).apply {
            addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(attachment.uri)
                contentDescription = attachment.displayName
            }, FrameLayout.LayoutParams(88.dp(), 88.dp()).apply {
                marginEnd = 12.dp()
            })
            addView(Button(context).apply {
                text = "×"
                textSize = 18f
                minWidth = 0
                minHeight = 0
                setPadding(0, 0, 0, 2.dp())
                setTextColor(Color.WHITE)
                backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFD93025.toInt())
                setOnClickListener { onRemove() }
                isEnabled = canRemove
                visibility = if (canRemove) View.VISIBLE else View.GONE
                contentDescription = getString(R.string.xingdun_feedback_remove_image)
            }, FrameLayout.LayoutParams(30.dp(), 30.dp(), Gravity.TOP or Gravity.END).apply {
                marginEnd = 4.dp()
            })
        }

    private fun showReportForm() {
        if (targetType !in setOf("user", "team", "message") || targetID.isBlank()) {
            showFailure(IllegalArgumentException(getString(R.string.xingdun_report_invalid_target)))
            return
        }
        val reasonValues = resources.getStringArray(R.array.xingdun_report_reason_values)
        val reasonLabels = resources.getStringArray(R.array.xingdun_report_reason_labels)
        val reason = Spinner(this).apply {
            adapter = ArrayAdapter(this@XingDunFeatureActivity, android.R.layout.simple_spinner_dropdown_item, reasonLabels.toList())
        }
        val description = input(R.string.xingdun_report_description_required, multiline = true)
        var attachments = emptyList<XingDunAttachment>()
        val attachmentSummary = TextView(this).apply { setText(R.string.xingdun_no_attachments) }
        addCard(getString(R.string.xingdun_report_target), "$targetType · $targetID")
        content.addView(reason)
        content.addView(description)
        content.addView(attachmentSummary)
        content.addView(actionButton(R.string.xingdun_choose_images) {
            attachmentSelectionHandler = { selected ->
                attachments = selected
                attachmentSummary.text = attachmentSummary(selected)
            }
            attachmentPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
        })
        content.addView(actionButton(R.string.xingdun_clear_attachments) {
            attachments = emptyList()
            attachmentSummary.setText(R.string.xingdun_no_attachments)
        })
        content.addView(actionButton(R.string.xingdun_submit) {
            val detail = description.text.toString().trim()
            if (detail.isEmpty() || detail.length > 500) {
                status.setText(R.string.xingdun_report_description_required_error)
                return@actionButton
            }
            submitMultipart(
                path = "report/save",
                fields = mapOf(
                    "target_type" to targetType,
                    "target_id" to targetID,
                    "reason" to reasonValues[reason.selectedItemPosition],
                    "description" to detail
                ),
                attachments = attachments,
                successMessage = R.string.xingdun_report_submitted
            )
        })
    }

    private fun showVersion() {
        setBusy(true)
        lifecycleScope.launch {
            runCatching { XingDunSessionManager.checkVersion() }.onSuccess { result ->
                setBusy(false)
                addCard(
                    getString(R.string.xingdun_version_current, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    if (!result.hasUpdate) getString(R.string.xingdun_version_no_update)
                    else listOfNotNull(
                        result.latestVersion?.versionName ?: result.latestVersion?.versionCode,
                        result.latestVersion?.updateLog
                    ).joinToString("\n\n")
                )
            }.onFailure(::showFailure)
        }
    }

    private fun showReports() {
        buildReportFilters()
        reportListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(reportListContainer)
        scrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> reportTouchStartY = event.y
                MotionEvent.ACTION_UP -> {
                    if (scrollView.scrollY == 0 && event.y - reportTouchStartY > 120.dp()) {
                        loadReports(reset = true)
                    }
                }
            }
            false
        }
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_BYPASS_LOGIN, false)) {
            if (debugReportFixtureEnabled) reportRecords += debugReportFixture()
            renderReportList()
        } else {
            loadReports(reset = true)
        }
    }

    private fun buildReportFilters() {
        val targetValues = listOf<String?>(null, "user", "team", "message")
        val targetLabels = listOf(
            getString(R.string.xingdun_all),
            getString(R.string.xingdun_report_target_user),
            getString(R.string.xingdun_report_target_team),
            getString(R.string.xingdun_report_target_message),
        )
        val statusValues = listOf<Int?>(null, 1, 2, 3, 4, 5)
        val statusLabels = listOf(
            getString(R.string.xingdun_all),
            getString(R.string.xingdun_report_status_pending),
            getString(R.string.xingdun_report_status_processing),
            getString(R.string.xingdun_report_status_confirmed),
            getString(R.string.xingdun_report_status_clean),
            getString(R.string.xingdun_report_status_rejected),
        )
        var initializedSelections = 0
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
            background = roundedDrawable(Color.WHITE, 18f)
        }
        card.addView(TextView(this).apply {
            setText(R.string.xingdun_filter)
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(2.dp(), 0, 2.dp(), 8.dp())
        })
        card.addView(reportFilterRow(R.string.xingdun_report_target_type, targetLabels) { position ->
            reportTargetFilter = targetValues[position]
            initializedSelections += 1
            if (initializedSelections > 2) loadReports(reset = true)
        })
        card.addView(reportFilterRow(R.string.xingdun_report_processing_status, statusLabels) { position ->
            reportStatusFilter = statusValues[position]
            initializedSelections += 1
            if (initializedSelections > 2) loadReports(reset = true)
        })
        content.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 18.dp()
        })
    }

    private fun reportFilterRow(label: Int, values: List<String>, onSelected: (Int) -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 52.dp()
            addView(TextView(context).apply {
                setText(label)
                textSize = 16f
                setTextColor(Color.BLACK)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, values)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = onSelected(position)
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    private fun loadReports(reset: Boolean) {
        if (reportLoading) return
        reportLoading = true
        val requestedPage = if (reset) 1 else reportPage + 1
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    requireSession(),
                    "report/list",
                    mapOf(
                        "target_type" to reportTargetFilter,
                        "status" to reportStatusFilter?.toString(),
                        "page" to requestedPage.toString(),
                        "page_size" to REPORT_PAGE_SIZE.toString(),
                    ),
                    JsonObject::class.java,
                )
            }.onSuccess { page ->
                reportLoading = false
                setBusy(false)
                if (reset) reportRecords.clear()
                page.array("list").forEach { element ->
                    val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@forEach
                    val id = item.int("id") ?: return@forEach
                    if (reportRecords.none { it.int("id") == id }) reportRecords += item
                }
                reportPage = requestedPage
                reportTotal = page.int("total") ?: reportRecords.size
                renderReportList()
            }.onFailure { error ->
                reportLoading = false
                showFailure(error)
                if (reportRecords.isEmpty()) renderReportList(error.localizedMessage)
            }
        }
    }

    private fun renderReportList(errorMessage: String? = null) {
        val container = reportListContainer ?: return
        container.removeAllViews()
        if (reportRecords.isEmpty()) {
            container.addView(reportEmptyState(errorMessage))
            return
        }
        reportRecords.forEach { record -> container.addView(reportHistoryRow(record)) }
        if (reportRecords.size < reportTotal) {
            container.addView(actionButton(R.string.xingdun_load_more) { loadReports(reset = false) })
        }
    }

    private fun reportEmptyState(errorMessage: String?): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(20.dp(), 42.dp(), 20.dp(), 42.dp())
        addView(ImageView(context).apply {
            setImageResource(R.drawable.xingdun_ic_mine_report)
            imageTintList = android.content.res.ColorStateList.valueOf(0xFF23B39C.toInt())
        }, LinearLayout.LayoutParams(64.dp(), 64.dp()))
        addView(TextView(context).apply {
            text = errorMessage ?: getString(R.string.xingdun_reports_empty)
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, 14.dp(), 0, 4.dp())
        })
        addView(TextView(context).apply {
            setText(if (errorMessage == null) R.string.xingdun_reports_empty_detail else R.string.xingdun_pull_to_refresh)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
        })
    }

    private fun reportHistoryRow(record: JsonObject): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
        background = roundedDrawable(Color.WHITE, 16f)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            record.int("id")?.takeIf { it > 0 }?.let { reportID ->
                if (debugReportFixtureEnabled) {
                    startActivity(Intent(this@XingDunFeatureActivity, XingDunFeatureActivity::class.java).apply {
                        putExtra(EXTRA_MODE, MODE_REPORT_DETAIL)
                        putExtra(EXTRA_ITEM_ID, reportID)
                        putExtra(EXTRA_DEBUG_BYPASS_LOGIN, true)
                        putExtra(EXTRA_DEBUG_REPORT_FIXTURE, true)
                    })
                } else {
                    start(this@XingDunFeatureActivity, MODE_REPORT_DETAIL, reportID)
                }
            }
        }
        addView(ImageView(context).apply {
            setImageResource(reportTargetIcon(record.string("target_type")))
            imageTintList = android.content.res.ColorStateList.valueOf(0xFFF0A526.toInt())
        }, LinearLayout.LayoutParams(32.dp(), 32.dp()).apply { marginEnd = 12.dp() })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = record.string("target_name") ?: reportTargetText(record.string("target_type"))
                textSize = 16f
                setTextColor(Color.BLACK)
                maxLines = 1
            })
            addView(TextView(context).apply {
                text = listOfNotNull(
                    reportReasonText(record.string("reason"), record.string("reason_text")),
                    record.string("report_no"),
                ).joinToString(" · ")
                textSize = 13f
                setTextColor(Color.DKGRAY)
                maxLines = 1
            })
            record.string("create_time")?.let { value ->
                addView(TextView(context).apply { text = value; textSize = 12f; setTextColor(Color.GRAY) })
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(reportStatusBadge(record.int("status"), record.string("status_text")))
    }.also { row ->
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 10.dp()
        }
    }

    private fun showReportDetail() {
        if (itemId <= 0) {
            showFailure(IllegalArgumentException(getString(R.string.xingdun_report_invalid_target)))
            return
        }
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_BYPASS_LOGIN, false)) {
            renderReportDetail(debugReportFixture())
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    requireSession(), "report/read", mapOf("id" to itemId.toString()), JsonObject::class.java
                )
            }.onSuccess {
                setBusy(false)
                renderReportDetail(it)
            }.onFailure { error ->
                showFailure(error)
                content.addView(actionButton(R.string.xingdun_retry) { content.removeAllViews(); showReportDetail() })
            }
        }
    }

    private fun renderReportDetail(report: JsonObject) {
        content.removeAllViews()
        addDetailSection(null, listOf(
            getString(R.string.xingdun_report_processing_status) to reportStatusText(report.int("status"), report.string("status_text")),
            getString(R.string.xingdun_report_number) to report.string("report_no").orEmpty(),
            getString(R.string.xingdun_report_target) to (report.string("target_name") ?: reportTargetText(report.string("target_type"))),
            getString(R.string.xingdun_report_target_id) to report.string("target_id").orEmpty(),
            getString(R.string.xingdun_report_reason) to reportReasonText(report.string("reason"), report.string("reason_text")),
            getString(R.string.xingdun_report_submitted_at) to report.string("create_time").orEmpty(),
        ))
        report.string("description")?.let { addDetailTextSection(R.string.xingdun_report_description_section, it) }
        val screenshots = report.array("screenshot").mapNotNull { element ->
            element.takeUnless(JsonElement::isJsonNull)?.let { runCatching { it.asString }.getOrNull() }
        }
        if (screenshots.isNotEmpty()) {
            addSectionTitle(R.string.xingdun_report_screenshot_evidence)
            screenshots.forEach { url ->
                content.addView(ImageView(this).apply {
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    background = roundedDrawable(Color.WHITE, 14f)
                    ImageLoader.load(this@XingDunFeatureActivity, this, url, R.drawable.xingdun_ic_mine_report)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 10.dp()
                })
            }
        }
        val handleResult = report.string("handle_result")
        if ((report.int("status") ?: 0) >= 3 || handleResult != null) {
            addDetailSection(R.string.xingdun_report_processing_result, listOfNotNull(
                getString(R.string.xingdun_report_result) to (handleResult ?: reportStatusText(report.int("status"), report.string("status_text"))),
                report.string("resolution_action")?.takeUnless { it == "none" }?.let {
                    getString(R.string.xingdun_report_resolution_action) to reportResolutionText(it)
                },
                report.string("handle_time")?.let { getString(R.string.xingdun_report_handled_at) to it },
            ))
        }
    }

    private fun addSectionTitle(label: Int) {
        content.addView(TextView(this).apply {
            setText(label)
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(4.dp(), 12.dp(), 4.dp(), 8.dp())
        })
    }

    private fun addDetailSection(title: Int?, rows: List<Pair<String, String>>) {
        val visibleRows = rows.filter { it.second.isNotBlank() }
        if (visibleRows.isEmpty()) return
        title?.let(::addSectionTitle)
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 4.dp(), 14.dp(), 4.dp())
            background = roundedDrawable(Color.WHITE, 16f)
            visibleRows.forEachIndexed { index, (label, value) ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = 48.dp()
                    addView(TextView(context).apply {
                        text = label
                        textSize = 15f
                        setTextColor(Color.BLACK)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    if (label == getString(R.string.xingdun_report_processing_status)) {
                        addView(reportStatusBadge(null, value))
                    } else {
                        addView(TextView(context).apply {
                            text = value
                            textSize = 14f
                            gravity = Gravity.END
                            setTextColor(Color.DKGRAY)
                            setTextIsSelectable(true)
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f))
                    }
                    if (index < visibleRows.lastIndex) {
                        setBackgroundColor(Color.TRANSPARENT)
                    }
                })
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 12.dp()
        })
    }

    private fun addDetailTextSection(title: Int, value: String) {
        addSectionTitle(title)
        content.addView(TextView(this).apply {
            text = value
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setTextIsSelectable(true)
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            background = roundedDrawable(Color.WHITE, 16f)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 12.dp()
        })
    }

    private fun reportStatusBadge(statusValue: Int?, fallback: String?): TextView = TextView(this).apply {
        text = reportStatusText(statusValue, fallback)
        textSize = 12f
        gravity = Gravity.CENTER
        setPadding(9.dp(), 4.dp(), 9.dp(), 4.dp())
        val colors = when (statusValue) {
            1 -> 0xFFFFF0D6.toInt() to 0xFFB36B00.toInt()
            2 -> 0xFFE1F0FF.toInt() to 0xFF2374B8.toInt()
            3 -> 0xFFDDF6EB.toInt() to 0xFF18795F.toInt()
            else -> 0xFFECEFF2.toInt() to 0xFF5D646B.toInt()
        }
        background = roundedDrawable(colors.first, 20f)
        setTextColor(colors.second)
    }

    private fun reportStatusText(statusValue: Int?, fallback: String?): String = when (statusValue) {
        1 -> getString(R.string.xingdun_report_status_pending)
        2 -> getString(R.string.xingdun_report_status_processing)
        3 -> getString(R.string.xingdun_report_status_confirmed)
        4 -> getString(R.string.xingdun_report_status_clean)
        5 -> getString(R.string.xingdun_report_status_rejected)
        else -> fallback?.trim().takeUnless { it.isNullOrEmpty() } ?: getString(R.string.xingdun_unknown)
    }

    private fun reportTargetText(value: String?): String = when (value) {
        "user" -> getString(R.string.xingdun_report_target_user)
        "team" -> getString(R.string.xingdun_report_target_team)
        "message" -> getString(R.string.xingdun_report_target_message)
        else -> getString(R.string.xingdun_report_target)
    }

    private fun reportTargetIcon(value: String?): Int = when (value) {
        "user", "team", "message" -> R.drawable.xingdun_ic_mine_report
        else -> R.drawable.xingdun_ic_mine_report
    }

    private fun reportReasonText(value: String?, fallback: String?): String {
        val values = resources.getStringArray(R.array.xingdun_report_reason_values)
        val labels = resources.getStringArray(R.array.xingdun_report_reason_labels)
        val index = values.indexOf(value)
        return labels.getOrNull(index)
            ?: fallback?.trim().takeUnless { it.isNullOrEmpty() }
            ?: getString(R.string.xingdun_unknown)
    }

    private fun reportResolutionText(value: String): String = when (value) {
        "warn" -> getString(R.string.xingdun_report_resolution_warn)
        "content_removed" -> getString(R.string.xingdun_report_resolution_content_removed)
        "account_restricted" -> getString(R.string.xingdun_report_resolution_account_restricted)
        "group_restricted" -> getString(R.string.xingdun_report_resolution_group_restricted)
        else -> value
    }

    private fun roundedDrawable(color: Int, cornerRadiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = cornerRadiusDp * resources.displayMetrics.density
    }

    private fun debugReportFixture(): JsonObject = JsonObject().apply {
        addProperty("id", itemId.takeIf { it > 0 } ?: 1)
        addProperty("report_no", "XD202608230001")
        addProperty("target_type", "user")
        addProperty("target_id", "xd_demo_user")
        addProperty("target_name", "Demo User")
        addProperty("reason", "harassment")
        addProperty("reason_text", getString(R.string.xingdun_report_reason))
        addProperty("description", getString(R.string.xingdun_report_debug_description))
        addProperty("status", 2)
        addProperty("status_text", getString(R.string.xingdun_report_status_processing))
        addProperty("create_time", "2026-08-23 20:24")
        add("screenshot", JsonArray())
    }

    private fun showAccountSecurity() {
        applyNotificationSettingsChrome()
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    requireSession(), "user/profile", emptyMap(), JsonObject::class.java
                )
            }.onSuccess { profile ->
                setBusy(false)
                renderAccountSecurity(profile)
            }.onFailure { error ->
                setBusy(false)
                content.addView(LinearLayout(this@XingDunFeatureActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = roundedDrawable(Color.WHITE, 14f)
                    setPadding(20.dp(), 28.dp(), 20.dp(), 28.dp())
                    addView(TextView(context).apply {
                        setText(R.string.xingdun_account_security_load_failed)
                        textSize = 17f
                        gravity = Gravity.CENTER
                        setTextColor(Color.BLACK)
                    })
                    addView(TextView(context).apply {
                        text = error.localizedMessage ?: getString(R.string.xingdun_action_failed)
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setTextColor(0xFF8A8A8F.toInt())
                        setPadding(0, 8.dp(), 0, 8.dp())
                    })
                    addView(actionButton(R.string.xingdun_retry) {
                        content.removeAllViews()
                        showAccountSecurity()
                    })
                }, notificationSectionLayoutParams())
            }
        }
    }

    private fun renderAccountSecurity(profile: JsonObject) {
        val username = profile.string("username").orEmpty()
        val isDeviceAccount = username.isBlank() || username.startsWith("dev_")

        addNotificationSectionHeader(R.string.xingdun_account_login_section)
        content.addView(accountSecurityCard(
            accountSecurityRow(
                icon = "👤",
                title = R.string.xingdun_username,
                value = if (isDeviceAccount) getString(R.string.xingdun_not_bound) else username,
                action = if (isDeviceAccount) ::showUpgradeAccountDialog else null,
            )
        ), notificationSectionLayoutParams())
        if (isDeviceAccount) addNotificationFooter(R.string.xingdun_device_login_hint)

        addNotificationSectionHeader(R.string.xingdun_account_contact_section)
        content.addView(accountSecurityCard(
            accountSecurityRow(
                "☎",
                R.string.xingdun_phone,
                maskPhone(profile.string("phone")),
                action = { openAccountChild(MODE_BIND_PHONE) },
            ),
            notificationDivider(),
            accountSecurityRow(
                "✉",
                R.string.xingdun_email,
                maskEmail(profile.string("email")),
                action = { openAccountChild(MODE_BIND_EMAIL) },
            ),
        ), notificationSectionLayoutParams())

        addNotificationSectionHeader(R.string.xingdun_account_password_section)
        if (isDeviceAccount) {
            content.addView(accountSecurityMessage(R.string.xingdun_device_password_hint), notificationSectionLayoutParams())
        } else {
            content.addView(accountSecurityCard(
                accountSecurityRow(
                    "🔐",
                    R.string.xingdun_change_password,
                    null,
                    action = { openAccountChild(MODE_CHANGE_PASSWORD) },
                ),
            ), notificationSectionLayoutParams())
        }

        addNotificationSectionHeader(R.string.xingdun_account_management_section)
        if (isDeviceAccount) {
            content.addView(accountSecurityMessage(R.string.xingdun_device_deactivation_hint), notificationSectionLayoutParams())
        } else {
            content.addView(accountSecurityCard(
                accountSecurityRow(
                    "⚠",
                    R.string.xingdun_deactivate_account,
                    null,
                    action = { start(this@XingDunFeatureActivity, MODE_DEACTIVATE) },
                    danger = true,
                ),
            ), notificationSectionLayoutParams())
        }
    }

    private fun openAccountChild(childMode: String) {
        accountChildResult.launch(Intent(this, XingDunFeatureActivity::class.java).apply {
            putExtra(EXTRA_MODE, childMode)
        })
    }

    private fun showContactBinding(kind: String) {
        applyNotificationSettingsChrome()
        val isPhone = kind == "phone"
        val field = EditText(this).apply {
            setHint(if (isPhone) R.string.xingdun_phone_placeholder else R.string.xingdun_email_placeholder)
            inputType = if (isPhone) {
                InputType.TYPE_CLASS_PHONE
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            }
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            textSize = 16f
            setTextColor(Color.BLACK)
            setHintTextColor(0xFFC7C7CC.toInt())
            background = roundedDrawable(Color.WHITE, 22f)
            setPadding(16.dp(), 0, 16.dp(), 0)
        }
        val errorView = TextView(this).apply {
            visibility = View.GONE
            textSize = 13f
            setTextColor(0xFFD93025.toInt())
            setPadding(14.dp(), 10.dp(), 14.dp(), 0)
        }
        val confirm = Button(this).apply {
            setText(R.string.xingdun_confirm_binding)
            isAllCaps = false
            textSize = 16f
            setTextColor(0xFF20A88F.toInt())
            backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            background = roundedDrawable(Color.WHITE, 22f)
            stateListAnimator = null
        }
        field.doAfterTextChanged { errorView.visibility = View.GONE }
        confirm.setOnClickListener {
            val target = field.text.toString().trim().let { value ->
                if (isPhone) value else value.lowercase(Locale.ROOT)
            }
            val validation = if (isPhone) {
                XingDunAccountInputValidator.phone(target)
            } else {
                XingDunAccountInputValidator.email(target)
            }
            if (validation != null) {
                errorView.setText(if (isPhone) R.string.xingdun_phone_format_incorrect else R.string.xingdun_email_format_incorrect)
                errorView.visibility = View.VISIBLE
                return@setOnClickListener
            }
            confirm.isEnabled = false
            confirm.alpha = 0.55f
            field.isEnabled = false
            errorView.visibility = View.GONE
            lifecycleScope.launch {
                runCatching {
                    XingDunSessionManager.apiClient().postEmpty(
                        requireSession(),
                        if (isPhone) "auth/bindPhone" else "auth/bindEmail",
                        mapOf(kind to target),
                    )
                }.onSuccess {
                    setResult(RESULT_OK)
                    finish()
                }.onFailure { error ->
                    confirm.isEnabled = true
                    confirm.alpha = 1f
                    field.isEnabled = true
                    errorView.text = error.localizedMessage ?: getString(R.string.xingdun_action_failed)
                    errorView.visibility = View.VISIBLE
                }
            }
        }
        content.addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()).apply {
            topMargin = 10.dp()
        })
        content.addView(errorView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(confirm, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp()).apply {
            topMargin = 26.dp()
        })
    }

    private fun accountSecurityCard(vararg rows: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedDrawable(Color.WHITE, 14f)
        rows.forEach(::addView)
    }

    private fun accountSecurityMessage(message: Int): View = TextView(this).apply {
        setText(message)
        textSize = 14f
        setTextColor(0xFF8A8A8F.toInt())
        background = roundedDrawable(Color.WHITE, 14f)
        setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
    }

    private fun accountSecurityRow(
        icon: String,
        title: Int,
        value: String?,
        action: (() -> Unit)? = null,
        danger: Boolean = false,
    ): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(14.dp(), 0, 10.dp(), 0)
        addView(TextView(context).apply {
            text = icon
            textSize = 18f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(34.dp(), 56.dp()))
        addView(TextView(context).apply {
            setText(title)
            textSize = 16f
            setTextColor(if (danger) 0xFFD93025.toInt() else Color.BLACK)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, 56.dp(), 1f))
        if (!value.isNullOrBlank()) {
            addView(TextView(context).apply {
                text = value
                maxLines = 1
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setTextColor(0xFF8A8A8F.toInt())
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 56.dp()))
        }
        if (action != null) {
            addView(TextView(context).apply {
                text = "›"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(0xFF8A8A8F.toInt())
            }, LinearLayout.LayoutParams(28.dp(), 56.dp()))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    }

    private fun showChangePassword() {
        applyNotificationSettingsChrome()
        val oldPassword = accountPasswordField(R.string.xingdun_old_password)
        val newPassword = accountPasswordField(R.string.xingdun_new_password)
        val confirmation = accountPasswordField(R.string.xingdun_confirm_new_password)
        val errorView = TextView(this).apply {
            visibility = View.GONE
            textSize = 13f
            setTextColor(0xFFD93025.toInt())
            setPadding(14.dp(), 10.dp(), 14.dp(), 0)
        }
        val confirm = Button(this).apply {
            setText(R.string.xingdun_confirm_change)
            isAllCaps = false
            textSize = 16f
            setTextColor(0xFF20A88F.toInt())
            background = roundedDrawable(Color.WHITE, 22f)
            stateListAnimator = null
        }
        val fields = listOf(oldPassword, newPassword, confirmation)
        fields.forEach { it.doAfterTextChanged { errorView.visibility = View.GONE } }
        confirm.setOnClickListener {
            val oldValue = oldPassword.text.toString()
            val newValue = newPassword.text.toString()
            val confirmationValue = confirmation.text.toString()
            val message = when {
                oldValue.isEmpty() -> R.string.xingdun_original_password_incorrect
                newValue.isEmpty() -> R.string.xingdun_new_password_required
                else -> XingDunAccountInputValidator.password(newValue)?.let(::accountInputError)
                    ?: R.string.xingdun_password_mismatch.takeIf { newValue != confirmationValue }
                    ?: R.string.xingdun_password_unchanged.takeIf { newValue == oldValue }
            }
            if (message != null) {
                errorView.setText(message)
                errorView.visibility = View.VISIBLE
                return@setOnClickListener
            }
            fields.forEach { it.isEnabled = false }
            confirm.isEnabled = false
            confirm.alpha = 0.55f
            errorView.visibility = View.GONE
            lifecycleScope.launch {
                runCatching {
                    XingDunSessionManager.apiClient().postEmpty(
                        requireSession(),
                        "auth/changePassword",
                        mapOf(
                            "old_password" to oldValue,
                            "new_password" to newValue,
                            "confirm_password" to confirmationValue,
                        ),
                    )
                }.onSuccess {
                    completeSecurityLogout()
                }.onFailure { error ->
                    fields.forEach { it.isEnabled = true }
                    confirm.isEnabled = true
                    confirm.alpha = 1f
                    errorView.text = error.localizedMessage ?: getString(R.string.xingdun_action_failed)
                    errorView.visibility = View.VISIBLE
                }
            }
        }
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            addView(oldPassword, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()))
            addView(notificationDivider())
            addView(newPassword, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()))
            addView(notificationDivider())
            addView(confirmation, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()))
        }, notificationSectionLayoutParams())
        addNotificationFooter(R.string.xingdun_change_password_warning)
        content.addView(errorView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(confirm, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp()).apply {
            topMargin = 12.dp()
        })
    }

    private fun accountPasswordField(hint: Int): EditText = EditText(this).apply {
        setHint(hint)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setSingleLine(true)
        textSize = 16f
        setTextColor(Color.BLACK)
        setHintTextColor(0xFFC7C7CC.toInt())
        setPadding(14.dp(), 0, 14.dp(), 0)
        background = null
    }

    private fun showUpgradeAccountDialog() {
        val username = input(R.string.xingdun_username)
        val password = passwordInput(R.string.xingdun_new_password)
        val confirmation = passwordInput(R.string.xingdun_confirm_password)
        val form = verticalForm(username, password, confirmation)
        AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_upgrade_device_account)
            .setView(form)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_submit) { _, _ ->
                val name = username.text.toString().trim()
                val newPassword = password.text.toString()
                val validation = XingDunAccountInputValidator.username(name)
                    ?: XingDunAccountInputValidator.password(newPassword, listOf(name))
                    ?: XingDunAccountInputError.PASSWORD_MISMATCH.takeIf {
                        newPassword != confirmation.text.toString()
                    }
                if (validation != null) {
                    status.setText(accountInputError(validation))
                    return@setPositiveButton
                }
                submitAccountAction(
                    "auth/bindAccount",
                    mapOf(
                        "username" to name,
                        "password" to newPassword,
                        "confirm_password" to confirmation.text.toString()
                    ),
                    refresh = true
                )
            }
            .show()
    }

    private fun submitAccountAction(
        path: String,
        body: Any,
        refresh: Boolean = false,
        logoutAfterSuccess: Boolean = false
    ) {
        setBusy(true)
        lifecycleScope.launch {
            runCatching { XingDunSessionManager.apiClient().postEmpty(requireSession(), path, body) }
                .onSuccess {
                    setBusy(false)
                    if (logoutAfterSuccess) completeSecurityLogout()
                    else {
                        status.setText(R.string.xingdun_saved)
                        if (refresh) {
                            content.removeAllViews()
                            showAccountSecurity()
                        }
                    }
                }
                .onFailure(::showFailure)
        }
    }

    private fun showDevices() {
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonArray>(
                    requireSession(), "user/devices", emptyMap(), JsonArray::class.java
                )
            }.onSuccess { devices ->
                setBusy(false)
                if (devices.isEmpty) addMessage(R.string.xingdun_devices_empty)
                devices.forEach { element ->
                    val device = element.asJsonObject
                    addCard(
                        device.string("device_model") ?: device.string("device_type") ?: getString(R.string.xingdun_device),
                        listOfNotNull(
                            device.string("status_label"),
                            device.string("os_version"),
                            device.string("app_version"),
                            device.string("last_login_time")
                        ).joinToString(" · ")
                    ) {
                        val id = device.int("id") ?: return@addCard
                        if (device.int("status") == 1) confirmUnbindDevice(id)
                    }
                }
            }.onFailure(::showFailure)
        }
    }

    private fun confirmUnbindDevice(deviceBindingID: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_unbind_device)
            .setMessage(R.string.xingdun_unbind_device_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_unbind_device) { _, _ ->
                submitAccountAction(
                    "user/unbindDevice",
                    mapOf("device_binding_id" to deviceBindingID),
                    logoutAfterSuccess = true
                )
            }
            .show()
    }

    private fun showDeactivation() {
        applyNotificationSettingsChrome()

        addNotificationSectionHeader(R.string.xingdun_deactivation_explanation)
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = "⚠"
                    textSize = 18f
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(30.dp(), ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(TextView(context).apply {
                    setText(R.string.xingdun_deactivation_stops_immediately)
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(0xFFD93025.toInt())
                    setPadding(8.dp(), 4.dp(), 0, 8.dp())
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(notificationDivider())
            listOf(
                R.string.xingdun_deactivation_effect_login,
                R.string.xingdun_deactivation_effect_recycle,
                R.string.xingdun_deactivation_effect_retention,
                R.string.xingdun_deactivation_effect_recovery,
            ).forEach { addView(deactivationConsequenceRow(it)) }
        }, notificationSectionLayoutParams())

        addNotificationSectionHeader(R.string.xingdun_deactivation_identity_confirmation)
        val password = accountPasswordField(R.string.xingdun_current_password_placeholder)
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            addView(password, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 50.dp()))
            addView(notificationDivider())
            addView(TextView(context).apply {
                setText(R.string.xingdun_deactivation_password_hint)
                textSize = 13f
                setTextColor(0xFF8A8A8F.toInt())
                setPadding(14.dp(), 10.dp(), 14.dp(), 12.dp())
            })
        }, notificationSectionLayoutParams())

        addNotificationSectionHeader(R.string.xingdun_deactivation_reason_optional)
        val reason = EditText(this).apply {
            hint = ""
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.TOP or Gravity.START
            minHeight = 100.dp()
            textSize = 15f
            setTextColor(Color.BLACK)
            background = null
            setPadding(14.dp(), 12.dp(), 14.dp(), 8.dp())
        }
        val reasonCount = TextView(this).apply {
            text = getString(R.string.xingdun_deactivation_reason_count, 0)
            textSize = 12f
            gravity = Gravity.END
            setTextColor(0xFF8A8A8F.toInt())
            setPadding(14.dp(), 0, 14.dp(), 10.dp())
        }
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            addView(reason, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(reasonCount)
        }, notificationSectionLayoutParams())

        val acknowledged = Switch(this).apply {
            setText(R.string.xingdun_deactivation_acknowledgement)
            textSize = 15f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 0, 12.dp(), 0)
        }
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            addView(acknowledged, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 54.dp()))
            addView(notificationDivider())
            addView(notificationNavigationRow(R.string.xingdun_read_deactivation_rules) {
                openDeactivationRules()
            })
        }, notificationSectionLayoutParams())

        val errorView = TextView(this).apply {
            visibility = View.GONE
            textSize = 13f
            setTextColor(0xFFD93025.toInt())
            setPadding(14.dp(), 4.dp(), 14.dp(), 8.dp())
        }
        val requestButton = Button(this).apply {
            setText(R.string.xingdun_request_deactivation)
            isAllCaps = false
            textSize = 16f
            background = roundedDrawable(Color.WHITE, 14f)
            stateListAnimator = null
        }
        val fields = listOf<View>(password, reason, acknowledged)
        fun updateRequestState() {
            val allowed = password.text.isNotEmpty() && acknowledged.isChecked && reason.text.toString().trim().length <= 500
            requestButton.isEnabled = allowed
            requestButton.setTextColor(if (allowed) 0xFFD93025.toInt() else 0xFFC7C7CC.toInt())
            requestButton.alpha = if (allowed) 1f else 0.7f
        }
        password.doAfterTextChanged {
            errorView.visibility = View.GONE
            updateRequestState()
        }
        reason.doAfterTextChanged {
            val count = it?.length ?: 0
            reasonCount.text = getString(R.string.xingdun_deactivation_reason_count, count)
            reasonCount.setTextColor(if (count > 500) 0xFFD93025.toInt() else 0xFF8A8A8F.toInt())
            errorView.visibility = View.GONE
            updateRequestState()
        }
        acknowledged.setOnCheckedChangeListener { _, _ ->
            errorView.visibility = View.GONE
            updateRequestState()
        }
        requestButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.xingdun_confirm_current_account_deactivation)
                .setMessage(R.string.xingdun_deactivation_final_warning)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.xingdun_confirm_deactivation) { _, _ ->
                    fields.forEach { it.isEnabled = false }
                    requestButton.isEnabled = false
                    requestButton.setText(R.string.xingdun_loading)
                    errorView.visibility = View.GONE
                    submitDeactivation(
                        password.text.toString(),
                        reason.text.toString().trim(),
                    ) { error ->
                        fields.forEach { it.isEnabled = true }
                        requestButton.setText(R.string.xingdun_request_deactivation)
                        errorView.text = error.localizedMessage ?: getString(R.string.xingdun_action_failed)
                        errorView.visibility = View.VISIBLE
                        updateRequestState()
                    }
                }
                .show()
        }
        updateRequestState()
        content.addView(errorView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(requestButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 46.dp()).apply {
            topMargin = 4.dp()
        })
        addNotificationFooter(R.string.xingdun_deactivation_submit_hint)
    }

    private fun deactivationConsequenceRow(message: Int): View = LinearLayout(this).apply {
        gravity = Gravity.TOP
        addView(TextView(context).apply {
            text = "✓"
            textSize = 16f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(0xFF8A8A8F.toInt())
        }, LinearLayout.LayoutParams(28.dp(), ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(TextView(context).apply {
            setText(message)
            textSize = 13f
            setTextColor(0xFF6D6D72.toInt())
            setPadding(8.dp(), 2.dp(), 0, 8.dp())
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun openDeactivationRules() {
        startActivity(Intent(this, XingDunFeatureActivity::class.java).apply {
            putExtra(EXTRA_MODE, MODE_DEACTIVATION_RULES)
            if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_BYPASS_LOGIN, false)) {
                putExtra(EXTRA_DEBUG_BYPASS_LOGIN, true)
            }
        })
    }

    private fun showDeactivationRules() {
        applyNotificationSettingsChrome()
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4.dp(), 0, 12.dp())
            addView(TextView(context).apply {
                setText(R.string.xingdun_deactivation_rules_full_title)
                textSize = 24f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.BLACK)
            })
            addView(TextView(context).apply {
                setText(R.string.xingdun_deactivation_rules_version)
                textSize = 13f
                setTextColor(0xFF8A8A8F.toInt())
                setPadding(0, 8.dp(), 0, 12.dp())
            })
            addView(TextView(context).apply {
                setText(R.string.xingdun_deactivation_rules_summary)
                textSize = 15f
                setTextColor(Color.BLACK)
            })
        })
        listOf(
            R.string.xingdun_deactivation_rules_section_1_title to R.string.xingdun_deactivation_rules_section_1_body,
            R.string.xingdun_deactivation_rules_section_2_title to R.string.xingdun_deactivation_rules_section_2_body,
            R.string.xingdun_deactivation_rules_section_3_title to R.string.xingdun_deactivation_rules_section_3_body,
            R.string.xingdun_deactivation_rules_section_4_title to R.string.xingdun_deactivation_rules_section_4_body,
            R.string.xingdun_deactivation_rules_section_5_title to R.string.xingdun_deactivation_rules_section_5_body,
            R.string.xingdun_deactivation_rules_section_6_title to R.string.xingdun_deactivation_rules_section_6_body,
            R.string.xingdun_deactivation_rules_section_7_title to R.string.xingdun_deactivation_rules_section_7_body,
            R.string.xingdun_deactivation_rules_section_8_title to R.string.xingdun_deactivation_rules_section_8_body,
            R.string.xingdun_deactivation_rules_section_9_title to R.string.xingdun_deactivation_rules_section_9_body,
            R.string.xingdun_deactivation_rules_section_10_title to R.string.xingdun_deactivation_rules_section_10_body,
        ).forEach { (title, body) -> addDeactivationRuleSection(title, body) }
    }

    private fun addDeactivationRuleSection(title: Int, body: Int) {
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 14.dp(), 0, 0)
            addView(notificationDivider())
            addView(TextView(context).apply {
                setText(title)
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.BLACK)
                setPadding(0, 14.dp(), 0, 8.dp())
            })
            addView(TextView(context).apply {
                setText(body)
                textSize = 15f
                setTextColor(0xFF6D6D72.toInt())
                setLineSpacing(0f, 1.16f)
            })
        })
    }

    private fun submitDeactivation(password: String, reason: String, onFailure: (Throwable) -> Unit) {
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().post<JsonObject>(
                    requireSession(),
                    "user/deactivate",
                    mapOf("password" to password, "reason" to reason),
                    JsonObject::class.java
                )
            }.onSuccess { result ->
                XingDunAccountDeletionReceiptStore.save(
                    this@XingDunFeatureActivity,
                    result.string("deletion_receipt"),
                    result.string("purge_after")
                )
                completeSecurityLogout()
            }.onFailure { error ->
                setBusy(false)
                onFailure(error)
            }
        }
    }

    private fun completeSecurityLogout() {
        XingDunTenantSessionCoordinator.logout(::clearSessionAndOpenLogin)
    }

    private fun clearSessionAndOpenLogin() {
        startActivity(Intent(this, XingDunLaunchActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        finish()
    }

    private fun passwordInput(hint: Int): EditText = input(hint).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun accountInputError(error: XingDunAccountInputError): Int = when (error) {
        XingDunAccountInputError.PHONE -> R.string.xingdun_invalid_phone
        XingDunAccountInputError.EMAIL -> R.string.xingdun_invalid_email
        XingDunAccountInputError.USERNAME -> R.string.xingdun_invalid_username
        XingDunAccountInputError.PASSWORD_REQUIRED -> R.string.xingdun_password_required
        XingDunAccountInputError.PASSWORD_LENGTH -> R.string.xingdun_password_length
        XingDunAccountInputError.PASSWORD_WHITESPACE -> R.string.xingdun_password_whitespace
        XingDunAccountInputError.PASSWORD_COMPLEXITY -> R.string.xingdun_password_complexity
        XingDunAccountInputError.PASSWORD_IDENTIFIER -> R.string.xingdun_password_identifier
        XingDunAccountInputError.PASSWORD_COMMON -> R.string.xingdun_password_common
        XingDunAccountInputError.PASSWORD_MISMATCH -> R.string.xingdun_password_mismatch
        XingDunAccountInputError.PASSWORD_UNCHANGED -> R.string.xingdun_password_unchanged
    }

    private fun verticalForm(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20.dp(), 4.dp(), 20.dp(), 4.dp())
        views.forEach(::addView)
    }

    private fun maskPhone(value: String?): String {
        val normalized = value.orEmpty()
        return if (normalized.length < 7) getString(R.string.xingdun_not_bound)
        else "${normalized.take(3)}****${normalized.takeLast(4)}"
    }

    private fun maskEmail(value: String?): String {
        val normalized = value.orEmpty()
        val at = normalized.indexOf('@')
        return if (at <= 0) getString(R.string.xingdun_not_bound) else "${normalized.take(1)}***${normalized.substring(at)}"
    }

    private fun showNotificationSettings() {
        applyNotificationSettingsChrome()
        val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        addNotificationSectionHeader(R.string.xingdun_notification_system_section)
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.xingdun_ic_notification_bell)
                    imageTintList = ColorStateList.valueOf(0xFF20A88F.toInt())
                    setPadding(5.dp(), 5.dp(), 5.dp(), 5.dp())
                }, LinearLayout.LayoutParams(36.dp(), 52.dp()))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        setText(R.string.xingdun_notification_permission)
                        textSize = 16f
                        setTextColor(Color.BLACK)
                    })
                    addView(TextView(context).apply {
                        setText(R.string.xingdun_notification_permission_detail)
                        textSize = 13f
                        setTextColor(0xFF8A8A8F.toInt())
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(context).apply {
                    setText(if (enabled) R.string.xingdun_permission_enabled else R.string.xingdun_permission_disabled)
                    textSize = 13f
                    setTextColor(if (enabled) 0xFF168F83.toInt() else 0xFFD93025.toInt())
                    background = roundedDrawable(if (enabled) 0xFFDFF3EF.toInt() else 0xFFFFE7E5.toInt(), 12f)
                    setPadding(10.dp(), 5.dp(), 10.dp(), 5.dp())
                })
            })
            if (Build.VERSION.SDK_INT >= 33 && !enabled &&
                ContextCompat.checkSelfPermission(this@XingDunFeatureActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                addView(notificationDivider())
                addView(notificationNavigationRow(R.string.xingdun_request_notification_permission) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
            addView(notificationDivider())
                addView(notificationNavigationRow(
                    R.string.xingdun_open_system_notification_settings,
                    R.drawable.xingdun_ic_notification_settings,
                ) {
                    systemNotificationSettings.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    })
            })
        }, notificationSectionLayoutParams())

        addNotificationSectionHeader(R.string.xingdun_notification_in_app_section)
        val sound = Switch(this).apply {
            setText(R.string.xingdun_notification_sound)
            isChecked = XingDunForegroundNotificationManager.soundEnabled(this@XingDunFeatureActivity)
            setOnCheckedChangeListener { _, checked ->
                XingDunForegroundNotificationManager.setSoundEnabled(this@XingDunFeatureActivity, checked)
            }
        }
        val vibration = Switch(this).apply {
            setText(R.string.xingdun_notification_vibration)
            isChecked = XingDunForegroundNotificationManager.vibrationEnabled(this@XingDunFeatureActivity)
            setOnCheckedChangeListener { _, checked ->
                XingDunForegroundNotificationManager.setVibrationEnabled(this@XingDunFeatureActivity, checked)
            }
        }
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(16.dp(), 0, 16.dp(), 0)
            addView(sound, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 56.dp()))
            addView(notificationDivider())
            addView(vibration, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 56.dp()))
        }, notificationSectionLayoutParams())
        addNotificationFooter(R.string.xingdun_notification_foreground_hint)

        addNotificationSectionHeader(R.string.xingdun_notification_conversation_section)
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            addView(LinearLayout(context).apply {
                gravity = Gravity.TOP
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.xingdun_ic_notification_muted)
                    imageTintList = ColorStateList.valueOf(0xFF8A8A8F.toInt())
                    setPadding(2.dp(), 2.dp(), 8.dp(), 2.dp())
                }, LinearLayout.LayoutParams(32.dp(), 32.dp()))
                addView(TextView(context).apply {
                    setText(R.string.xingdun_notification_conversation_mute_hint)
                    textSize = 15f
                    setTextColor(Color.BLACK)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(TextView(context).apply {
                setText(R.string.xingdun_notification_conversation_priority_hint)
                textSize = 13f
                setTextColor(0xFF8A8A8F.toInt())
                setPadding(0, 8.dp(), 0, 0)
            })
        }, notificationSectionLayoutParams())
    }

    private fun showLanguageSettings() {
        applyNotificationSettingsChrome()
        val selectedTag = currentProductLanguageTag()
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            addView(languageRow(R.string.demo_settings_zh_hans, "zh-Hans", selectedTag == "zh-Hans"))
            addView(notificationDivider())
            addView(languageRow(R.string.demo_settings_en, "en", selectedTag == "en"))
        }, notificationSectionLayoutParams())
        addNotificationFooter(R.string.xingdun_language_footer)
    }

    private fun languageRow(label: Int, tag: String, selected: Boolean): View =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 0, 12.dp(), 0)
            addView(TextView(context).apply {
                setText(label)
                textSize = 16f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, 48.dp(), 1f))
            addView(TextView(context).apply {
                text = if (selected) "✓" else ""
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(0xFF20A88F.toInt())
            }, LinearLayout.LayoutParams(32.dp(), 48.dp()))
            isClickable = true
            isFocusable = true
            setOnClickListener { applyProductLanguage(tag) }
        }

    private fun currentProductLanguageTag(): String {
        val stored = MMKV.defaultMMKV().decodeString(AppConstants.KEY_APP_LANGUAGE, "").orEmpty()
        val current = stored.ifBlank { AppCompatDelegate.getApplicationLocales().toLanguageTags() }
        return if (current.startsWith("en", ignoreCase = true)) "en" else "zh-Hans"
    }

    private fun applyProductLanguage(tag: String) {
        val target = LocaleListCompat.forLanguageTags(tag)
        MMKV.defaultMMKV().encode(AppConstants.KEY_APP_LANGUAGE, tag)
        if (AppCompatDelegate.getApplicationLocales() == target) return
        AppCompatDelegate.setApplicationLocales(target)
    }

    private fun applyNotificationSettingsChrome() {
        val background = 0xFFF5F5F9.toInt()
        window.statusBarColor = background
        window.navigationBarColor = background
        headerBar.setBackgroundColor(background)
        scrollView.setBackgroundColor(background)
        content.setBackgroundColor(background)
        content.setPadding(20.dp(), 8.dp(), 20.dp(), 32.dp())
        status.setBackgroundColor(background)
        status.text = ""
    }

    private fun addNotificationSectionHeader(title: Int) {
        content.addView(TextView(this).apply {
            setText(title)
            textSize = 14f
            setTextColor(0xFF8A8A8F.toInt())
            setPadding(14.dp(), 12.dp(), 8.dp(), 8.dp())
        })
    }

    private fun addNotificationFooter(message: Int) {
        content.addView(TextView(this).apply {
            setText(message)
            textSize = 13f
            setTextColor(0xFF8A8A8F.toInt())
            setPadding(14.dp(), 0, 14.dp(), 10.dp())
        })
    }

    private fun notificationNavigationRow(title: Int, icon: Int? = null, action: () -> Unit): View =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 0, 10.dp(), 0)
            if (icon != null) {
                addView(ImageView(context).apply {
                    setImageResource(icon)
                    imageTintList = ColorStateList.valueOf(0xFF20A88F.toInt())
                    setPadding(0, 4.dp(), 10.dp(), 4.dp())
                }, LinearLayout.LayoutParams(34.dp(), 52.dp()))
            }
            addView(TextView(context).apply {
                setText(title)
                textSize = 15f
                setTextColor(0xFF168F83.toInt())
            }, LinearLayout.LayoutParams(0, 52.dp(), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
            addView(TextView(context).apply {
                text = "›"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(0xFF8A8A8F.toInt())
            }, LinearLayout.LayoutParams(28.dp(), 52.dp()))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun notificationDivider(): View = View(this).apply {
        setBackgroundColor(0xFFE7E7EA.toInt())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp()).apply {
            marginStart = 16.dp()
        }
    }

    private fun notificationSectionLayoutParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = 8.dp() }

    private fun showStorageManagement() {
        applyStorageManagementChrome()
        setBusy(true)
        lifecycleScope.launch {
            runCatching { XingDunStorageManager.usage(this@XingDunFeatureActivity) }
                .onSuccess { usage ->
                    setBusy(false)
                    renderStorageManagement(usage)
                }
                .onFailure { error ->
                    setBusy(false)
                    renderStorageLoadFailure(error)
                }
        }
    }

    private fun renderStorageLoadFailure(error: Throwable) {
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(20.dp(), 24.dp(), 20.dp(), 24.dp())
            addView(TextView(context).apply {
                setText(R.string.xingdun_storage_load_failed)
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(0xFFD93025.toInt())
            })
            addView(TextView(context).apply {
                text = error.localizedMessage ?: getString(R.string.xingdun_action_failed)
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(0xFF8A8A8F.toInt())
                setPadding(0, 8.dp(), 0, 8.dp())
            })
            addView(actionButton(R.string.xingdun_recalculate_storage) {
                content.removeAllViews()
                showStorageManagement()
            })
        }, storageSectionLayoutParams())
    }

    private fun renderStorageManagement(usage: XingDunCacheUsage) {
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            addView(TextView(context).apply {
                setText(R.string.xingdun_storage_used)
                textSize = 13f
                setTextColor(0xFF8A8A8F.toInt())
            })
            addView(TextView(context).apply {
                text = Formatter.formatFileSize(this@XingDunFeatureActivity, usage.totalBytes)
                textSize = 28f
                setTextColor(Color.BLACK)
                setPadding(0, 4.dp(), 0, 4.dp())
            })
            addView(TextView(context).apply {
                setText(R.string.xingdun_storage_scope_hint)
                textSize = 13f
                setTextColor(0xFF8A8A8F.toInt())
            })
        }, storageSectionLayoutParams())

        addStorageSectionHeader(R.string.xingdun_storage_select_section)
        val selected = XingDunCacheCategory.entries
            .filterTo(mutableSetOf()) { (usage.bytes[it] ?: 0L) > 0L }
        lateinit var clearButton: Button
        val selectionMarkers = mutableMapOf<XingDunCacheCategory, TextView>()
        val updateSelectionUI = {
            selectionMarkers.forEach { (category, marker) ->
                val isSelected = category in selected
                marker.text = if (isSelected) "✓" else ""
                marker.setTextColor(if (isSelected) Color.WHITE else Color.TRANSPARENT)
                marker.background = storageSelectionDrawable(isSelected)
            }
            val selectedBytes = selected.sumOf { usage.bytes[it] ?: 0L }
            clearButton.text = getString(
                R.string.xingdun_clear_selected_cache_size,
                Formatter.formatFileSize(this@XingDunFeatureActivity, selectedBytes)
            )
            clearButton.isEnabled = selected.isNotEmpty()
        }
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            XingDunCacheCategory.entries.forEachIndexed { index, category ->
                if (index > 0) addView(storageDivider())
                addView(storageCategoryRow(category, usage.bytes[category] ?: 0L) {
                    if (!selected.add(category)) selected.remove(category)
                    updateSelectionUI()
                }.also { row ->
                    selectionMarkers[category] = row.getChildAt(3) as TextView
                })
            }
        }, storageSectionLayoutParams())

        val errorPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = roundedDrawable(0xFFFFE7E5.toInt(), 12f)
            setPadding(14.dp(), 12.dp(), 14.dp(), 10.dp())
            addView(TextView(context).apply {
                setText(R.string.xingdun_storage_clear_failed)
                textSize = 13f
                setTextColor(0xFFD93025.toInt())
            })
            addView(TextView(context).apply {
                setText(R.string.xingdun_recalculate_storage)
                textSize = 14f
                setTextColor(0xFF168F83.toInt())
                setPadding(0, 8.dp(), 0, 2.dp())
                setOnClickListener {
                    content.removeAllViews()
                    showStorageManagement()
                }
            })
        }
        clearButton = Button(this).apply {
            isAllCaps = false
            textSize = 16f
            setTextColor(0xFFD93025.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnClickListener {
                val selectedBytes = selected.sumOf { usage.bytes[it] ?: 0L }
                AlertDialog.Builder(this@XingDunFeatureActivity)
                    .setTitle(R.string.xingdun_storage_clear_title)
                    .setMessage(getString(
                        R.string.xingdun_storage_clear_confirmation,
                        Formatter.formatFileSize(this@XingDunFeatureActivity, selectedBytes)
                    ))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.xingdun_clear_cache) { _, _ ->
                        clearButton.isEnabled = false
                        errorPanel.visibility = View.GONE
                        clearStorage(selected.toSet()) { error ->
                            clearButton.isEnabled = true
                            (errorPanel.getChildAt(0) as TextView).text =
                                error.localizedMessage ?: getString(R.string.xingdun_storage_clear_failed)
                            errorPanel.visibility = View.VISIBLE
                        }
                    }
                    .show()
            }
        }
        content.addView(errorPanel, storageSectionLayoutParams())
        content.addView(clearButton, storageSectionLayoutParams().apply { topMargin = 10.dp() })
        addStorageFooter(R.string.xingdun_storage_clear_warning)
        updateSelectionUI()
    }

    private fun applyStorageManagementChrome() {
        val background = 0xFFF5F5F9.toInt()
        window.statusBarColor = background
        window.navigationBarColor = background
        headerBar.setBackgroundColor(background)
        scrollView.setBackgroundColor(background)
        content.setBackgroundColor(background)
        content.setPadding(20.dp(), 12.dp(), 20.dp(), 32.dp())
        status.setBackgroundColor(background)
    }

    private fun addStorageSectionHeader(title: Int) = addNotificationSectionHeader(title)

    private fun storageCategoryRow(category: XingDunCacheCategory, bytes: Long, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
            addView(ImageView(context).apply {
                setImageResource(storageCategoryIcon(category))
                imageTintList = ColorStateList.valueOf(0xFF20A88F.toInt())
                setPadding(4.dp(), 4.dp(), 8.dp(), 4.dp())
            }, LinearLayout.LayoutParams(34.dp(), 52.dp()))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    setText(storageCategoryTitle(category))
                    textSize = 15f
                    setTextColor(Color.BLACK)
                })
                addView(TextView(context).apply {
                    setText(storageCategoryDetail(category))
                    textSize = 12f
                    setTextColor(0xFF8A8A8F.toInt())
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = Formatter.formatFileSize(this@XingDunFeatureActivity, bytes)
                textSize = 13f
                setTextColor(0xFF8A8A8F.toInt())
                setPadding(6.dp(), 0, 8.dp(), 0)
            })
            addView(TextView(context).apply {
                textSize = 15f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(24.dp(), 24.dp()).apply { gravity = Gravity.CENTER_VERTICAL })
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun storageSelectionDrawable(selected: Boolean): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (selected) 0xFF20A88F.toInt() else Color.TRANSPARENT)
        setStroke(2.dp(), if (selected) 0xFF20A88F.toInt() else 0xFFAEAEB2.toInt())
    }

    private fun storageCategoryIcon(category: XingDunCacheCategory): Int = when (category) {
        XingDunCacheCategory.IMAGE -> R.drawable.xingdun_ic_storage_image
        XingDunCacheCategory.AUDIO -> R.drawable.xingdun_ic_storage_audio
        XingDunCacheCategory.VIDEO -> R.drawable.xingdun_ic_storage_video
        XingDunCacheCategory.FILE -> R.drawable.xingdun_ic_storage_file
        XingDunCacheCategory.THUMBNAIL -> R.drawable.xingdun_ic_storage_thumbnail
        XingDunCacheCategory.TEMPORARY -> R.drawable.xingdun_ic_storage_temporary
    }

    private fun storageCategoryTitle(category: XingDunCacheCategory): Int = when (category) {
        XingDunCacheCategory.IMAGE -> R.string.xingdun_storage_images
        XingDunCacheCategory.AUDIO -> R.string.xingdun_storage_audio
        XingDunCacheCategory.VIDEO -> R.string.xingdun_storage_video
        XingDunCacheCategory.FILE -> R.string.xingdun_storage_files
        XingDunCacheCategory.THUMBNAIL -> R.string.xingdun_storage_thumbnails
        XingDunCacheCategory.TEMPORARY -> R.string.xingdun_storage_temporary
    }

    private fun storageCategoryDetail(category: XingDunCacheCategory): Int = when (category) {
        XingDunCacheCategory.IMAGE -> R.string.xingdun_storage_images_detail
        XingDunCacheCategory.AUDIO -> R.string.xingdun_storage_audio_detail
        XingDunCacheCategory.VIDEO -> R.string.xingdun_storage_video_detail
        XingDunCacheCategory.FILE -> R.string.xingdun_storage_files_detail
        XingDunCacheCategory.THUMBNAIL -> R.string.xingdun_storage_thumbnails_detail
        XingDunCacheCategory.TEMPORARY -> R.string.xingdun_storage_temporary_detail
    }

    private fun addStorageFooter(message: Int) {
        content.addView(TextView(this).apply {
            setText(message)
            textSize = 13f
            setTextColor(0xFF8A8A8F.toInt())
            setPadding(14.dp(), 0, 14.dp(), 8.dp())
        })
    }

    private fun storageDivider(): View = View(this).apply {
        setBackgroundColor(0xFFE7E7EA.toInt())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp()).apply {
            marginStart = 48.dp()
        }
    }

    private fun storageSectionLayoutParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = 8.dp() }

    private fun showHelpCenter() {
        applyHelpCenterChrome()
        addHelpFAQSection(
            R.string.xingdun_help_section_account,
            listOf(
                R.string.xingdun_help_login_question to R.string.xingdun_help_login_answer,
                R.string.xingdun_help_offline_question to R.string.xingdun_help_offline_answer,
                R.string.xingdun_help_contact_question to R.string.xingdun_help_contact_answer,
            ),
        )
        addHelpFAQSection(
            R.string.xingdun_help_section_messages,
            listOf(
                R.string.xingdun_help_notification_question to R.string.xingdun_help_notification_answer,
                R.string.xingdun_help_media_question to R.string.xingdun_help_media_answer,
                R.string.xingdun_help_storage_question to R.string.xingdun_help_storage_answer,
            ),
        )
        addHelpFAQSection(
            R.string.xingdun_help_section_contacts,
            listOf(
                R.string.xingdun_help_colleague_question to R.string.xingdun_help_colleague_answer,
                R.string.xingdun_help_group_question to R.string.xingdun_help_group_answer,
            ),
        )
        addHelpSupportSection()
    }

    private fun applyHelpCenterChrome() {
        val background = 0xFFF5F5F9.toInt()
        window.statusBarColor = background
        window.navigationBarColor = background
        headerBar.setBackgroundColor(background)
        scrollView.setBackgroundColor(background)
        content.setBackgroundColor(background)
        content.setPadding(20.dp(), 8.dp(), 20.dp(), 32.dp())
        status.setBackgroundColor(background)
        status.setTextColor(0xFF8A8A8F.toInt())
        (headerBar.getChildAt(0) as? Button)?.apply {
            setTextColor(Color.BLACK)
            backgroundTintList = android.content.res.ColorStateList.valueOf(background)
        }
        (headerBar.getChildAt(1) as? TextView)?.apply {
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }
    }

    private fun addHelpFAQSection(title: Int, entries: List<Pair<Int, Int>>) {
        addHelpSectionHeader(title)
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
        }
        entries.forEachIndexed { index, (question, answer) ->
            group.addView(helpDisclosureRow(question, answer))
            if (index != entries.lastIndex) group.addView(helpDivider())
        }
        content.addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 12.dp()
        })
    }

    private fun addHelpSectionHeader(title: Int) {
        content.addView(TextView(this).apply {
            setText(title)
            textSize = 14f
            setTextColor(0xFF8A8A8F.toInt())
            setPadding(14.dp(), 12.dp(), 8.dp(), 8.dp())
        })
    }

    private fun helpDisclosureRow(question: Int, answer: Int): View {
        val detail = TextView(this).apply {
            setText(answer)
            textSize = 14f
            setTextColor(0xFF6D6D72.toInt())
            setPadding(0, 0, 32.dp(), 13.dp())
            visibility = View.GONE
        }
        val arrow = TextView(this).apply {
            text = "›"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 0, 10.dp(), 0)
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    setText(question)
                    textSize = 16f
                    setTextColor(Color.BLACK)
                }, LinearLayout.LayoutParams(0, 56.dp(), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
                addView(arrow, LinearLayout.LayoutParams(28.dp(), 56.dp()))
            })
            addView(detail)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val expanding = detail.visibility != View.VISIBLE
                detail.visibility = if (expanding) View.VISIBLE else View.GONE
                arrow.text = if (expanding) "⌄" else "›"
            }
        }
    }

    private fun addHelpSupportSection() {
        addHelpSectionHeader(R.string.xingdun_help_section_support)
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
        }
        if (XingDunSessionManager.currentSession()?.features?.customerService == true) {
            val contactRow = LinearLayout(this)
            group.addView(contactRow)
            group.addView(helpDivider())
            loadHelpCustomerService(contactRow)
        }
        group.addView(helpNavigationRow(R.string.xingdun_feedback) { startChildMode(MODE_FEEDBACK) })
        group.addView(helpDivider())
        group.addView(helpNavigationRow(R.string.xingdun_report_violation) { startChildMode(MODE_REPORT_CREATE) })
        content.addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_help_support_footer)
            textSize = 13f
            setTextColor(0xFF8A8A8F.toInt())
            setPadding(14.dp(), 10.dp(), 14.dp(), 16.dp())
        })
    }

    private fun helpNavigationRow(title: Int, detail: Int? = null, action: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 0, 10.dp(), 0)
            addView(TextView(context).apply {
                setText(title)
                textSize = 16f
                setTextColor(Color.BLACK)
            }, LinearLayout.LayoutParams(0, 56.dp(), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
            detail?.let {
                addView(TextView(context).apply {
                    setText(it)
                    textSize = 13f
                    setTextColor(0xFF8A8A8F.toInt())
                    gravity = Gravity.CENTER_VERTICAL
                })
            }
            addView(TextView(context).apply {
                text = "›"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(0xFF8A8A8F.toInt())
            }, LinearLayout.LayoutParams(28.dp(), 56.dp()))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun startChildMode(childMode: String) {
        startActivity(Intent(this, XingDunFeatureActivity::class.java).apply {
            putExtra(EXTRA_MODE, childMode)
            if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_BYPASS_LOGIN, false)) {
                putExtra(EXTRA_DEBUG_BYPASS_LOGIN, true)
            }
        })
    }

    private fun helpDivider(): View = View(this).apply {
        setBackgroundColor(0xFFE7E7EA.toInt())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp()).apply {
            marginStart = 16.dp()
        }
    }

    private fun loadHelpCustomerService(row: LinearLayout) {
        configureHelpCustomerServiceRow(row, R.string.xingdun_contact_enterprise_support, R.string.xingdun_loading) {}
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    requireSession(), "cs/identity", emptyMap(), JsonObject::class.java
                )
            }.onSuccess { identity ->
                val official = identity.string("official_cs_tim_user_id")
                val assigned = identity.array("customer_services").firstOrNull()?.asJsonObject?.string("tim_user_id")
                val target = official?.takeIf(String::isNotBlank) ?: assigned?.takeIf(String::isNotBlank)
                if (target == null) {
                    configureHelpCustomerServiceRow(
                        row,
                        R.string.xingdun_contact_enterprise_support,
                        R.string.xingdun_customer_service_not_configured,
                    ) {}
                } else {
                    configureHelpCustomerServiceRow(row, R.string.xingdun_contact_enterprise_support, null) {
                        ChatActivity.start(this@XingDunFeatureActivity, "c2c_$target")
                    }
                }
            }.onFailure {
                configureHelpCustomerServiceRow(
                    row,
                    R.string.xingdun_contact_enterprise_support,
                    R.string.xingdun_customer_service_load_retry,
                ) { loadHelpCustomerService(row) }
            }
        }
    }

    private fun configureHelpCustomerServiceRow(row: LinearLayout, title: Int, detail: Int?, action: () -> Unit) {
        row.removeAllViews()
        val configured = helpNavigationRow(title, detail, action)
        while (configured.childCount > 0) row.addView(configured.getChildAt(0).also { configured.removeView(it) })
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(16.dp(), 0, 10.dp(), 0)
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener { action() }
    }

    private fun showPermissionManagement() {
        applyPermissionManagementChrome()
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                setText(R.string.xingdun_permission_enterprise_badge)
                textSize = 13f
                setTextColor(0xFF168F83.toInt())
                background = roundedDrawable(0xFFDFF3EF.toInt(), 12f)
                setPadding(10.dp(), 5.dp(), 10.dp(), 5.dp())
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(TextView(context).apply {
                setText(R.string.xingdun_permission_request_when_needed)
                textSize = 21f
                setTextColor(Color.BLACK)
                setPadding(0, 10.dp(), 0, 4.dp())
            })
            addView(TextView(context).apply {
                setText(R.string.xingdun_permission_description)
                textSize = 13f
                setTextColor(0xFF8A8A8F.toInt())
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 14.dp()
        })

        addRuntimePermissionCard(
            "♧", R.string.xingdun_notification_permission,
            R.string.xingdun_permission_notification_summary, R.string.xingdun_permission_notification_usage,
            Manifest.permission.POST_NOTIFICATIONS, notification = true,
        )
        addRuntimePermissionCard(
            "▣", R.string.xingdun_permission_camera,
            R.string.xingdun_permission_camera_summary, R.string.xingdun_permission_camera_usage,
            Manifest.permission.CAMERA,
        )
        addPickerScopedPermissionCard(
            "▧", R.string.xingdun_permission_photos,
            R.string.xingdun_permission_photos_summary, R.string.xingdun_permission_photos_usage,
        )
        addRuntimePermissionCard(
            "●", R.string.xingdun_permission_microphone,
            R.string.xingdun_permission_microphone_summary, R.string.xingdun_permission_microphone_usage,
            Manifest.permission.RECORD_AUDIO,
        )
        addPickerScopedPermissionCard(
            "▤", R.string.xingdun_permission_files,
            R.string.xingdun_permission_files_summary, R.string.xingdun_permission_files_usage,
        )
    }

    private fun addRuntimePermissionCard(
        icon: String,
        title: Int,
        summary: Int,
        usage: Int,
        permission: String,
        notification: Boolean = false,
    ) {
        val runtimePermissionRequired = !notification || Build.VERSION.SDK_INT >= 33
        val systemEnabled = if (notification) NotificationManagerCompat.from(this).areNotificationsEnabled()
        else ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        val granted = if (!runtimePermissionRequired) systemEnabled else {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED && systemEnabled
        }
        val requested = permissionWasRequested(permission)
        val shouldOpenSettings = !granted && (!runtimePermissionRequired ||
            (requested && !shouldShowRequestPermissionRationale(permission)))
        val status = when {
            granted -> R.string.xingdun_permission_enabled
            requested -> R.string.xingdun_permission_closed
            else -> R.string.xingdun_permission_not_requested
        }
        val action = when {
            granted -> R.string.xingdun_permission_enabled
            shouldOpenSettings -> R.string.xingdun_permission_go_to_settings
            else -> R.string.xingdun_permission_allow_access
        }
        addPermissionCard(icon, title, summary, usage, status, granted, action, !granted) {
            if (shouldOpenSettings) {
                managedPermissionSettings.launch(
                    if (notification) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            } else {
                markPermissionRequested(permission)
                managedPermissionRequest.launch(permission)
            }
        }
    }

    private fun addPickerScopedPermissionCard(icon: String, title: Int, summary: Int, usage: Int) {
        addPermissionCard(
            icon, title, summary, usage,
            R.string.xingdun_permission_picker_scoped, true,
            R.string.xingdun_permission_system_picker, false,
        ) {}
    }

    private fun addPermissionCard(
        icon: String,
        title: Int,
        summary: Int,
        usage: Int,
        statusLabel: Int,
        positiveStatus: Boolean,
        actionLabel: Int,
        actionEnabled: Boolean,
        action: () -> Unit,
    ) {
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            addView(LinearLayout(context).apply {
                gravity = Gravity.TOP
                addView(TextView(context).apply {
                    text = icon
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTextColor(0xFF168F83.toInt())
                    background = roundedDrawable(0xFFDFF3EF.toInt(), 10f)
                }, LinearLayout.LayoutParams(40.dp(), 40.dp()))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(12.dp(), 0, 0, 0)
                    addView(LinearLayout(context).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(TextView(context).apply {
                            setText(title)
                            textSize = 16f
                            setTextColor(Color.BLACK)
                        })
                        addView(TextView(context).apply {
                            setText(statusLabel)
                            textSize = 12f
                            setTextColor(if (positiveStatus) 0xFF168F83.toInt() else 0xFFD93025.toInt())
                            background = roundedDrawable(if (positiveStatus) 0xFFDFF3EF.toInt() else 0xFFFFE7E5.toInt(), 10f)
                            setPadding(8.dp(), 3.dp(), 8.dp(), 3.dp())
                        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = 8.dp()
                        })
                    })
                    addView(TextView(context).apply {
                        setText(summary)
                        textSize = 13f
                        setTextColor(0xFF66666B.toInt())
                        setPadding(0, 6.dp(), 0, 0)
                    })
                    addView(TextView(context).apply {
                        setText(usage)
                        textSize = 12f
                        setTextColor(0xFF9A9A9F.toInt())
                        setPadding(0, 5.dp(), 0, 0)
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(Button(context).apply {
                setText(actionLabel)
                isAllCaps = false
                isEnabled = actionEnabled
                setTextColor(if (actionEnabled) Color.WHITE else 0xFF8A8A8F.toInt())
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (actionEnabled) 0xFF168F83.toInt() else 0xFFE7E7EA.toInt()
                )
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp()).apply {
                topMargin = 12.dp()
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 12.dp()
        })
    }

    private fun applyPermissionManagementChrome() {
        val background = 0xFFF5F5F9.toInt()
        window.statusBarColor = background
        window.navigationBarColor = background
        headerBar.setBackgroundColor(background)
        scrollView.setBackgroundColor(background)
        content.setBackgroundColor(background)
        content.setPadding(20.dp(), 14.dp(), 20.dp(), 32.dp())
        status.setBackgroundColor(background)
        status.text = ""
    }

    private fun permissionWasRequested(permission: String): Boolean =
        getSharedPreferences(PERMISSION_PREFERENCES, MODE_PRIVATE).getBoolean(permission, false)

    private fun markPermissionRequested(permission: String) {
        getSharedPreferences(PERMISSION_PREFERENCES, MODE_PRIVATE).edit().putBoolean(permission, true).apply()
    }

    private fun showAbout() {
        val session = XingDunSessionManager.currentSession()
        val enterprise = XingDunSessionManager.currentEnterprise()
        val brandName = enterprise?.let { XingDunAuthUiSupport.displayName(this, it) }
            ?: session?.companyName?.takeIf(String::isNotBlank)
            ?: getString(R.string.demo_app_name)
        (headerBar.getChildAt(1) as? TextView)?.text = getString(R.string.xingdun_about_title_format, brandName)
        applyNotificationSettingsChrome()

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(16.dp(), 22.dp(), 16.dp(), 22.dp())
            addView(XingDunEnterpriseLogoView(context).apply {
                contentDescription = brandName
                loadLogo(lifecycleScope, enterprise?.let(XingDunAuthUiSupport::logoUrl))
            }, LinearLayout.LayoutParams(100.dp(), 100.dp()))
            addView(TextView(context).apply {
                text = brandName
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                setPadding(0, 10.dp(), 0, 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(TextView(context).apply {
                text = getString(R.string.xingdun_about_version, BuildConfig.VERSION_NAME)
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(0xFF8A8A8F.toInt())
                setPadding(0, 5.dp(), 0, 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, notificationSectionLayoutParams())

        addNotificationSectionHeader(R.string.xingdun_about_product_information)
        val aboutUri = publicWebUri(enterprise?.platform?.aboutUrl)
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            addView(aboutValueRow(
                R.string.xingdun_about_official_website,
                if (aboutUri == null) getString(R.string.xingdun_not_configured) else null,
            ) {
                aboutUri?.let { startActivity(Intent(Intent.ACTION_VIEW, it)) }
            })
            addView(notificationDivider())
            addView(aboutValueRow(R.string.xingdun_check_updates, null) { checkAboutUpdates() })
            enterprise?.platform?.siteRecordNumber?.trim()?.takeIf(String::isNotEmpty)?.let { record ->
                addView(notificationDivider())
                addView(aboutValueRow(R.string.xingdun_about_site_record_number, record, null))
            }
        }, notificationSectionLayoutParams())

        enterprise?.platform?.siteCopyright?.trim()?.takeIf(String::isNotEmpty)?.let { copyright ->
            content.addView(TextView(this).apply {
                text = copyright
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(0xFFAEAEB2.toInt())
                setPadding(14.dp(), 18.dp(), 14.dp(), 4.dp())
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun aboutValueRow(label: Int, value: String?, action: (() -> Unit)?): View =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 0, 10.dp(), 0)
            addView(TextView(context).apply {
                setText(label)
                textSize = 16f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, 56.dp(), 1f))
            if (!value.isNullOrBlank()) {
                addView(TextView(context).apply {
                    text = value
                    textSize = 14f
                    gravity = Gravity.CENTER_VERTICAL or Gravity.END
                    setTextColor(0xFF8A8A8F.toInt())
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 56.dp()))
            }
            if (action != null && value.isNullOrBlank()) {
                addView(TextView(context).apply {
                    text = "›"
                    textSize = 28f
                    gravity = Gravity.CENTER
                    setTextColor(0xFF8A8A8F.toInt())
                }, LinearLayout.LayoutParams(28.dp(), 56.dp()))
            }
            if (action != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }
        }

    private fun checkAboutUpdates() {
        status.setText(R.string.xingdun_checking_version)
        lifecycleScope.launch {
            runCatching { XingDunSessionManager.checkVersion() }
                .onSuccess { result ->
                    status.text = ""
                    if (!result.hasUpdate || result.latestVersion == null) {
                        Toast.makeText(this@XingDunFeatureActivity, R.string.xingdun_version_no_update, Toast.LENGTH_SHORT).show()
                    } else {
                        val version = result.latestVersion
                        val downloadUri = publicWebUri(version.downloadUrl)?.takeIf { it.scheme.equals("https", true) }
                        val message = listOfNotNull(
                            version.versionName?.takeIf(String::isNotBlank)
                                ?: version.versionCode.takeIf(String::isNotBlank),
                            version.updateLog?.takeIf(String::isNotBlank),
                        ).joinToString("\n\n")
                        val builder = AlertDialog.Builder(this@XingDunFeatureActivity)
                            .setTitle(if (result.isForce) R.string.xingdun_force_update else R.string.xingdun_update_available)
                            .setMessage(message)
                        if (!result.isForce) builder.setNegativeButton(R.string.xingdun_update_later, null)
                        if (downloadUri != null) {
                            builder.setPositiveButton(R.string.xingdun_update_now) { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, downloadUri))
                            }
                        } else {
                            builder.setPositiveButton(android.R.string.ok, null)
                        }
                        builder.create().apply {
                            setCancelable(!result.isForce)
                            setCanceledOnTouchOutside(!result.isForce)
                            show()
                        }
                    }
                }
                .onFailure {
                    status.text = ""
                    AlertDialog.Builder(this@XingDunFeatureActivity)
                        .setTitle(R.string.xingdun_check_updates)
                        .setMessage(R.string.xingdun_about_update_failed)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
        }
    }

    private fun publicWebUri(value: String?): Uri? {
        val uri = runCatching { Uri.parse(value?.trim().orEmpty()) }.getOrNull() ?: return null
        return uri.takeIf { it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrBlank() }
    }

    private fun showLegalDocument(privacy: Boolean) {
        val session = XingDunSessionManager.currentSession()
        val debugUrl = intent.getStringExtra(EXTRA_DEBUG_LEGAL_URL)
            ?.takeIf { BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_BYPASS_LOGIN, false) }
        if (session == null && debugUrl == null) {
            showLegalDocumentUnavailable()
            return
        }
        val url = debugUrl ?: if (privacy) session!!.privacy.privacyUrl else session!!.privacy.userAgreementUrl
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            showLegalDocumentUnavailable()
            return
        }
        val background = 0xFFF5F5F9.toInt()
        window.statusBarColor = background
        window.navigationBarColor = background
        headerBar.setBackgroundColor(background)
        scrollView.setBackgroundColor(background)
        content.setBackgroundColor(background)
        content.setPadding(0, 0, 0, 0)

        val frame = FrameLayout(this)
        val webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
        }
        legalWebView = webView
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        val errorPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(28.dp(), 28.dp(), 28.dp(), 28.dp())
            addView(TextView(context).apply {
                setText(R.string.xingdun_legal_load_failed)
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(0xFF6D6D72.toInt())
            })
            addView(actionButton(R.string.xingdun_retry) {
                visibility = View.GONE
                webView.visibility = View.VISIBLE
                progress.visibility = View.VISIBLE
                status.setText(R.string.xingdun_loading)
                webView.loadUrl(url)
            })
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val next = request?.url ?: return true
                return next.scheme?.lowercase() !in setOf("http", "https")
            }

            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                if (errorPanel.visibility != View.VISIBLE) {
                    progress.visibility = View.GONE
                    status.text = ""
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    progress.visibility = View.GONE
                    webView.visibility = View.GONE
                    errorPanel.visibility = View.VISIBLE
                    status.setText(R.string.xingdun_legal_load_failed)
                }
            }
        }
        frame.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(errorPanel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(progress, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 3.dp(), Gravity.TOP))
        val documentHeight = (resources.displayMetrics.heightPixels - 130.dp()).coerceAtLeast(480.dp())
        content.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, documentHeight))
        status.setText(R.string.xingdun_loading)
        webView.loadUrl(url)
    }

    private fun showLegalDocumentUnavailable() {
        content.removeAllViews()
        content.gravity = Gravity.CENTER
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_legal_unavailable)
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFF6D6D72.toInt())
            setPadding(28.dp(), 28.dp(), 28.dp(), 28.dp())
        })
    }

    private fun showFavorites() {
        if (XingDunSessionManager.currentSession()?.features?.messageFavorite != true) {
            addMessage(R.string.xingdun_feature_unavailable)
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    requireSession(), "message/favorites", mapOf("page" to "1", "page_size" to "50"), JsonObject::class.java
                )
            }.onSuccess { page ->
                setBusy(false)
                val list = page.array("items").takeIf { !it.isEmpty } ?: page.array("list")
                if (list.isEmpty) addMessage(R.string.xingdun_favorites_empty)
                list.forEach { element ->
                    val favorite = element.asJsonObject
                    val snapshot = favorite.getAsJsonObject("message") ?: favorite
                    val favoriteID = favorite.int("favorite_id") ?: favorite.int("id")
                    val summary = snapshot.string("text").orEmpty().ifBlank {
                        getString(R.string.xingdun_favorite_type_summary, snapshot.string("message_type").orEmpty())
                    }
                    addCard(
                        snapshot.string("sender_nickname") ?: snapshot.string("sender") ?: getString(R.string.xingdun_message),
                        listOfNotNull(summary, snapshot.string("conversation_name"), favorite.string("favorited_at")).joinToString("\n")
                    ) {
                        if (favoriteID != null) confirmRemoveFavorite(favoriteID)
                    }
                }
            }.onFailure(::showFailure)
        }
    }

    private fun confirmRemoveFavorite(favoriteID: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_remove_favorite)
            .setMessage(R.string.xingdun_remove_favorite_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_remove_favorite) { _, _ ->
                setBusy(true)
                lifecycleScope.launch {
                    runCatching {
                        XingDunSessionManager.apiClient().deleteEmpty(
                            requireSession(), "message/favorite", mapOf("favorite_id" to favoriteID)
                        )
                    }.onSuccess {
                        content.removeAllViews()
                        showFavorites()
                    }.onFailure(::showFailure)
                }
            }.show()
    }

    private fun showRedpacketAccount() {
        if (XingDunSessionManager.currentSession()?.features?.redpacket != true) {
            addMessage(R.string.xingdun_redpacket_closed_detail)
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                val api = XingDunSessionManager.apiClient()
                val session = requireSession()
                val balance = api.get<JsonObject>(session, "redpacket/myBalance", emptyMap(), JsonObject::class.java)
                val sent = api.get<JsonObject>(session, "redpacket/mySent", mapOf("page" to "1", "page_size" to "20"), JsonObject::class.java)
                val received = api.get<JsonObject>(session, "redpacket/myReceived", mapOf("page" to "1", "page_size" to "20"), JsonObject::class.java)
                Triple(balance, sent, received)
            }.onSuccess { (balance, sent, received) ->
                setBusy(false)
                addCard(
                    getString(R.string.xingdun_redpacket_balance),
                    centsText(balance.int("redpacket_balance") ?: 0)
                )
                addSectionList(getString(R.string.xingdun_redpacket_sent), sent.array("list"))
                addSectionList(getString(R.string.xingdun_redpacket_received), received.array("list"))
            }.onFailure(::showFailure)
        }
    }

    private fun addSectionList(title: String, items: JsonArray) {
        addCard(title, getString(R.string.xingdun_items_count, items.size()))
        items.forEach { element ->
            val wrapper = element.asJsonObject
            val item = wrapper.getAsJsonObject("packet") ?: wrapper
            val packetNo = item.string("packet_no").orEmpty()
            addCard(
                item.string("greeting") ?: getString(R.string.xingdun_redpacket_default_greeting),
                listOfNotNull(item.string("status_name"), centsText(item.int("total_amount") ?: 0), item.string("create_time")).joinToString(" · ")
            ) { if (packetNo.isNotBlank()) start(this, MODE_REDPACKET_DETAIL, packetNo) }
        }
    }

    private fun showRedpacketDetail() {
        if (XingDunSessionManager.currentSession()?.features?.redpacket != true) {
            addMessage(R.string.xingdun_redpacket_closed_detail)
            return
        }
        if (targetID.isBlank()) return addMessage(R.string.xingdun_redpacket_invalid)
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    requireSession(), "redpacket/detail", mapOf("packet_no" to targetID), JsonObject::class.java
                )
            }.onSuccess { detail ->
                setBusy(false)
                addCard(
                    detail.string("greeting") ?: getString(R.string.xingdun_redpacket_default_greeting),
                    listOfNotNull(
                        detail.string("status_name"),
                        centsText(detail.int("total_amount") ?: 0),
                        getString(R.string.xingdun_redpacket_claimed_progress, detail.int("claimed_count") ?: 0, detail.int("count") ?: 0),
                        detail.string("expire_time")
                    ).joinToString("\n")
                )
            }.onFailure(::showFailure)
        }
    }

    private fun centsText(value: Int): String = getString(R.string.xingdun_currency_amount, value / 100.0)

    private fun clearStorage(selected: Set<XingDunCacheCategory>, onFailure: (Throwable) -> Unit) {
        setBusy(true)
        lifecycleScope.launch {
            runCatching { XingDunStorageManager.clear(this@XingDunFeatureActivity, selected) }
                .onSuccess { removed ->
                    val resultMessage = if (removed > 0) getString(
                        R.string.xingdun_storage_cleared,
                        Formatter.formatFileSize(this@XingDunFeatureActivity, removed)
                    ) else getString(R.string.xingdun_storage_already_empty)
                    content.removeAllViews()
                    showStorageManagement()
                    Toast.makeText(this@XingDunFeatureActivity, resultMessage, Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    setBusy(false)
                    onFailure(error)
                }
        }
    }

    private fun showQRCodeScanner() {
        addMessage(R.string.xingdun_qr_scan_description)
        content.addView(actionButton(R.string.xingdun_scan_with_camera) {
            qrScanner.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt(getString(R.string.xingdun_qr_scan_prompt))
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
            )
        })
        content.addView(actionButton(R.string.xingdun_scan_from_gallery) { qrImagePicker.launch("image/*") })
        val manual = input(R.string.xingdun_qr_manual_payload, multiline = true)
        content.addView(manual)
        content.addView(actionButton(R.string.xingdun_continue) {
            runCatching { handleScannedPayload(manual.text.toString()) }
                .onFailure { status.setText(R.string.xingdun_qr_unrecognized) }
        })
    }

    private fun handleScannedPayload(payload: String) {
        val route = runCatching { XingDunQRCodeParser.parse(payload) }.getOrElse {
            status.setText(R.string.xingdun_qr_unrecognized)
            return
        }
        when (route) {
            is XingDunQRCodeRoute.User -> {
                setBusy(true)
                ContactStore.shared.getContactInfo(
                    listOf(route.userID),
                    object : GetContactInfoCompletionHandler {
                        override fun onSuccess(contactInfoList: List<ContactInfo>) {
                            setBusy(false)
                            val info = contactInfoList.firstOrNull()
                            if (info == null) status.setText(R.string.xingdun_qr_user_not_found)
                            else io.trtc.tuikit.chat.uikit.components.contactlist.ui.ContactFlowLauncher
                                .showAddFriendForContact(this@XingDunFeatureActivity, info)
                        }

                        override fun onFailure(code: Int, desc: String) {
                            setBusy(false)
                            status.setText(R.string.xingdun_qr_user_not_found)
                        }
                    }
                )
            }
            is XingDunQRCodeRoute.Group -> {
                setBusy(true)
                GroupStore.shared.getGroupInfo(
                    route.groupID,
                    object : GetGroupInfoCompletionHandler {
                        override fun onSuccess(groupInfo: GroupInfo) {
                            setBusy(false)
                            io.trtc.tuikit.chat.uikit.components.contactlist.ui.ContactFlowLauncher.showAddGroupForInfo(
                                this@XingDunFeatureActivity,
                                ContactInfo(
                                    userID = groupInfo.groupID,
                                    avatarURL = groupInfo.avatarURL,
                                    nickname = groupInfo.groupName
                                )
                            )
                        }

                        override fun onFailure(code: Int, desc: String) {
                            setBusy(false)
                            status.setText(R.string.xingdun_qr_group_not_found)
                        }
                    }
                )
            }
            is XingDunQRCodeRoute.Invitation -> showInvitationRoute(route)
        }
    }

    private fun showInvitationRoute(route: XingDunQRCodeRoute.Invitation) {
        content.removeAllViews()
        addCard(
            getString(R.string.xingdun_invitation_recognized),
            listOfNotNull(route.code, route.companyCode).joinToString("\n")
        )
        addMessage(R.string.xingdun_invitation_usage_hint)
        content.addView(actionButton(R.string.xingdun_copy_invitation_code) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.xingdun_invitation_code), route.code))
            status.setText(R.string.xingdun_invitation_copied)
        })
    }

    private suspend fun decodeQRCode(uri: Uri): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2_048 || bounds.outHeight / sample > 2_048) sample *= 2
        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("Unable to decode QR image")
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    private fun showPersonalQRCode() {
        applyPersonalQRCodeChrome()
        val session = runCatching { requireSession() }.getOrElse {
            showFailure(it)
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                val profile = runCatching {
                    XingDunSessionManager.apiClient().get<JsonObject>(
                        session, "user/profile", emptyMap(), JsonObject::class.java
                    )
                }.getOrDefault(JsonObject())
                val displayName = profile.string("nickname") ?: session.nickname.ifBlank { session.timUserId }
                val accountID = profile.string("custom_id") ?: session.username ?: session.timUserId
                val avatarURL = profile.string("avatar")
                withContext(Dispatchers.IO) {
                    XingDunPersonalQRCodeArtifactStore(this@XingDunFeatureActivity).artifact(
                        tenantKey = listOf(session.companyCode, session.companyId, session.sdkAppId).joinToString("|"),
                        userID = session.timUserId,
                        displayName = displayName,
                        accountID = accountID,
                        avatarURL = avatarURL,
                    )
                }
            }.onSuccess { artifact ->
                setBusy(false)
                renderPersonalQRCode(artifact)
            }.onFailure {
                setBusy(false)
                showPersonalQRCodeUnavailable()
            }
        }
    }

    private fun applyPersonalQRCodeChrome() {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        headerBar.setBackgroundColor(Color.BLACK)
        scrollView.setBackgroundColor(Color.BLACK)
        content.setBackgroundColor(Color.BLACK)
        status.setBackgroundColor(Color.BLACK)
        status.setTextColor(Color.WHITE)
        (headerBar.getChildAt(0) as? Button)?.apply {
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.BLACK)
        }
        (headerBar.getChildAt(1) as? TextView)?.setTextColor(Color.WHITE)
    }

    private fun renderPersonalQRCode(artifact: XingDunPersonalQRCodeArtifact) {
        content.removeAllViews()
        content.gravity = Gravity.CENTER_HORIZONTAL
        val card = ImageView(this).apply {
            setImageBitmap(artifact.image)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.xingdun_personal_qr_image_description)
            background = roundedDrawable(Color.WHITE, 16f)
            clipToOutline = true
            setOnLongClickListener {
                sharePersonalQRCode(artifact)
                true
            }
        }
        content.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 4.dp()
            marginStart = 30.dp()
            marginEnd = 30.dp()
        })
        content.addView(actionButton(R.string.xingdun_personal_qr_save_image) {
            savePersonalQRCode(artifact)
        }.apply {
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF28B7A2.toInt())
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()).apply {
            topMargin = 16.dp()
            marginStart = 30.dp()
            marginEnd = 30.dp()
        })
    }

    private fun showPersonalQRCodeUnavailable() {
        content.removeAllViews()
        content.gravity = Gravity.CENTER
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_personal_qr_unavailable)
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        })
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_personal_qr_unavailable_detail)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, 10.dp(), 0, 0)
        })
    }

    private fun savePersonalQRCode(artifact: XingDunPersonalQRCodeArtifact) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingPersonalQRCode = artifact
            personalQRCodeStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        lifecycleScope.launch {
            setBusy(true)
            runCatching {
                saveBitmapToPictures(artifact.image, "xingdun_personal_qr_${System.currentTimeMillis()}.png")
            }.onSuccess {
                setBusy(false)
                status.setText(R.string.xingdun_personal_qr_saved)
            }.onFailure {
                setBusy(false)
                status.setText(R.string.xingdun_personal_qr_save_failed)
            }
        }
    }

    private suspend fun saveBitmapToPictures(bitmap: Bitmap, name: String) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/XingDun")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error(getString(R.string.xingdun_personal_qr_save_failed))
            runCatching {
                contentResolver.openOutputStream(uri)?.use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                } ?: error(getString(R.string.xingdun_personal_qr_save_failed))
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }.getOrElse { error ->
                contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            @Suppress("DEPRECATION")
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val target = File(directory, "XingDun/$name")
            val parent = requireNotNull(target.parentFile)
            check(parent.exists() || parent.mkdirs())
            FileOutputStream(target).use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            @Suppress("DEPRECATION")
            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(target)))
        }
    }

    private fun sharePersonalQRCode(artifact: XingDunPersonalQRCodeArtifact) {
        runCatching {
            val directory = File(cacheDir, "xingdun-share").apply { mkdirs() }
            val file = File(directory, "personal-qr.png")
            FileOutputStream(file).use { output -> check(artifact.image.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.xingdun.files", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, artifact.shareText)
                clipData = ClipData.newUri(contentResolver, getString(R.string.xingdun_personal_qr), uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, getString(R.string.xingdun_share)))
        }.onFailure { status.setText(R.string.xingdun_personal_qr_share_failed) }
    }

    private fun showPersonalQRCodeSettingsPrompt() {
        AlertDialog.Builder(this)
            .setMessage(R.string.xingdun_personal_qr_permission_denied)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_open_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .show()
    }

    private fun submitMultipart(
        path: String,
        fields: Map<String, Any?>,
        attachments: List<XingDunAttachment>,
        successMessage: Int
    ) {
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                val files = XingDunAttachmentResolver.uploadFiles(this@XingDunFeatureActivity, attachments)
                XingDunSessionManager.apiClient().postMultipartEmpty(requireSession(), path, fields, files)
            }.onSuccess {
                setBusy(false)
                status.setText(successMessage)
            }.onFailure { error ->
                if (error is XingDunAttachmentException) showAttachmentFailure(error) else showFailure(error)
            }
        }
    }

    private fun attachmentSummary(attachments: List<XingDunAttachment>): String = attachments.joinToString("\n") { item ->
        val size = item.size.takeIf { it >= 0 }?.let { Formatter.formatFileSize(this, it) }
        listOfNotNull(item.displayName, size).joinToString(" · ")
    }.ifBlank { getString(R.string.xingdun_no_attachments) }

    private fun showAttachmentFailure(error: Throwable) {
        val message = when ((error as? XingDunAttachmentException)?.reason) {
            XingDunAttachmentError.TOO_MANY -> R.string.xingdun_attachment_too_many
            XingDunAttachmentError.INVALID_TYPE -> R.string.xingdun_attachment_invalid_type
            XingDunAttachmentError.TOO_LARGE -> R.string.xingdun_attachment_too_large
            XingDunAttachmentError.EMPTY -> R.string.xingdun_attachment_empty
            else -> R.string.xingdun_attachment_unreadable
        }
        setBusy(false)
        status.setText(message)
    }

    private fun submitEmpty(path: String, body: Any, successMessage: Int) {
        setBusy(true)
        lifecycleScope.launch {
            runCatching { XingDunSessionManager.apiClient().postEmpty(requireSession(), path, body) }
                .onSuccess { setBusy(false); status.setText(successMessage) }
                .onFailure(::showFailure)
        }
    }

    private fun input(hint: Int, multiline: Boolean = false, decimal: Boolean = false): EditText = EditText(this).apply {
        setHint(hint)
        inputType = when {
            decimal -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else -> InputType.TYPE_CLASS_TEXT
        }
        minLines = if (multiline) 3 else 1
        setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 8.dp()
        }
    }

    private fun actionButton(label: Int, action: () -> Unit): Button = Button(this).apply {
        setText(label)
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 12.dp()
        }
    }

    private fun addCard(title: String, detail: String, onClick: (() -> Unit)? = null) {
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            addView(TextView(context).apply { text = title; textSize = 16f })
            if (detail.isNotBlank()) addView(TextView(context).apply { text = detail; textSize = 13f; setPadding(0, 6.dp(), 0, 0) })
            if (onClick != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 8.dp()
        })
    }

    private fun addMessage(message: Int) = addCard(getString(message), "")

    private fun setBusy(busy: Boolean) {
        if (busy) status.setText(R.string.xingdun_loading) else status.text = ""
    }

    private fun showFailure(error: Throwable) {
        setBusy(false)
        status.text = error.localizedMessage ?: getString(R.string.xingdun_action_failed)
    }

    private fun requireSession() = XingDunSessionManager.currentSession()
        ?: throw IllegalStateException(getString(R.string.xingdun_session_expired))

    private fun titleForMode(): String = getString(when (mode) {
        MODE_WORKSPACE_LIST -> R.string.xingdun_workspace_my
        MODE_WORKSPACE_PENDING -> R.string.xingdun_workspace_pending
        MODE_WORKSPACE_DETAIL -> R.string.xingdun_workspace_detail
        MODE_WORKSPACE_CREATE -> R.string.xingdun_workspace_create
        MODE_CUSTOMER_SERVICE -> R.string.xingdun_customer_service
        MODE_CUSTOMER_SERVICE_GROUP -> R.string.xingdun_customer_service_group_management
        MODE_INVITE -> R.string.xingdun_share_poster
        MODE_FEEDBACK -> R.string.xingdun_feedback
        MODE_VERSION -> R.string.xingdun_version
        MODE_REPORTS -> R.string.xingdun_reports
        MODE_REPORT_DETAIL -> R.string.xingdun_report_detail
        MODE_REPORT_CREATE -> R.string.xingdun_report
        MODE_PERSONAL_QR -> R.string.xingdun_personal_qr
        MODE_QR_SCANNER -> R.string.xingdun_scan_qr
        MODE_ACCOUNT_SECURITY -> R.string.xingdun_account_security
        MODE_BIND_PHONE -> R.string.xingdun_bind_phone
        MODE_BIND_EMAIL -> R.string.xingdun_bind_email
        MODE_CHANGE_PASSWORD -> R.string.xingdun_change_password
        MODE_DEVICES -> R.string.xingdun_devices
        MODE_DEACTIVATE -> R.string.xingdun_deactivate_account
        MODE_DEACTIVATION_RULES -> R.string.xingdun_deactivation_rules_title
        MODE_NOTIFICATIONS -> R.string.xingdun_notification_settings
        MODE_STORAGE -> R.string.xingdun_storage_management
        MODE_HELP -> R.string.xingdun_help_feedback_title
        MODE_PERMISSIONS -> R.string.xingdun_permission_management
        MODE_LANGUAGE -> R.string.demo_settings_language
        MODE_ABOUT -> R.string.xingdun_about
        MODE_USER_AGREEMENT -> R.string.xingdun_user_agreement
        MODE_PRIVACY_POLICY -> R.string.xingdun_privacy_policy
        MODE_FAVORITES -> R.string.xingdun_message_favorites
        MODE_REDPACKET_ACCOUNT -> R.string.xingdun_redpacket_account
        MODE_REDPACKET_DETAIL -> R.string.xingdun_redpacket_detail
        else -> R.string.demo_app_name
    })

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.asString?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonObject.boolean(name: String): Boolean =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.asBoolean ?: false

    private fun JsonObject.int(name: String): Int? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.let { runCatching { it.asInt }.getOrNull() }

    private fun JsonObject.array(name: String): JsonArray =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: JsonArray()

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_DEBUG_BYPASS_LOGIN = "debug_bypass_login"
        private const val EXTRA_DEBUG_REPORT_FIXTURE = "debug_report_fixture"
        private const val EXTRA_DEBUG_LEGAL_URL = "debug_legal_url"
        private const val EXTRA_ITEM_ID = "item_id"
        private const val EXTRA_TARGET_ID = "target_id"
        private const val EXTRA_TARGET_TYPE = "target_type"
        const val MODE_WORKSPACE_LIST = "workspace_list"
        const val MODE_WORKSPACE_PENDING = "workspace_pending"
        const val MODE_WORKSPACE_DETAIL = "workspace_detail"
        const val MODE_WORKSPACE_CREATE = "workspace_create"
        const val MODE_CUSTOMER_SERVICE = "customer_service"
        const val MODE_CUSTOMER_SERVICE_GROUP = "customer_service_group"
        const val MODE_INVITE = "invite"
        const val MODE_FEEDBACK = "feedback"
        const val MODE_VERSION = "version"
        const val MODE_REPORTS = "reports"
        const val MODE_REPORT_DETAIL = "report_detail"
        const val MODE_REPORT_CREATE = "report_create"
        const val MODE_PERSONAL_QR = "personal_qr"
        const val MODE_QR_SCANNER = "qr_scanner"
        const val MODE_ACCOUNT_SECURITY = "account_security"
        const val MODE_BIND_PHONE = "bind_phone"
        const val MODE_BIND_EMAIL = "bind_email"
        const val MODE_CHANGE_PASSWORD = "change_password"
        const val MODE_DEVICES = "devices"
        const val MODE_DEACTIVATE = "deactivate"
        const val MODE_DEACTIVATION_RULES = "deactivation_rules"
        const val MODE_NOTIFICATIONS = "notifications"
        const val MODE_STORAGE = "storage"
        const val MODE_HELP = "help"
        const val MODE_PERMISSIONS = "permissions"
        const val MODE_LANGUAGE = "language"
        const val MODE_ABOUT = "about"
        const val MODE_USER_AGREEMENT = "user_agreement"
        const val MODE_PRIVACY_POLICY = "privacy_policy"
        const val MODE_FAVORITES = "favorites"
        const val MODE_REDPACKET_ACCOUNT = "redpacket_account"
        const val MODE_REDPACKET_DETAIL = "redpacket_detail"
        private const val PERMISSION_PREFERENCES = "xingdun_permission_ui"
        private const val REPORT_PAGE_SIZE = 20

        fun start(context: Context, mode: String, itemId: Int = 0) {
            context.startActivity(Intent(context, XingDunFeatureActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode)
                if (itemId > 0) putExtra(EXTRA_ITEM_ID, itemId)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        fun start(context: Context, mode: String, targetID: String) {
            context.startActivity(Intent(context, XingDunFeatureActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_TARGET_ID, targetID)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        fun startReport(context: Context, targetType: String, targetID: String) {
            context.startActivity(Intent(context, XingDunFeatureActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_REPORT_CREATE)
                putExtra(EXTRA_TARGET_TYPE, targetType)
                putExtra(EXTRA_TARGET_ID, targetID)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
