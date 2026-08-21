package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import io.trtc.tuikit.chat.app.BuildConfig
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.launch
import java.util.UUID

/** Thin, product-owned screens for XingDun services that are not provided by TUIKit. */
class XingDunFeatureActivity : BaseActivity() {

    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private val mode: String by lazy { intent.getStringExtra(EXTRA_MODE).orEmpty() }
    private val itemId: Int by lazy { intent.getIntExtra(EXTRA_ITEM_ID, 0) }

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
            MODE_INVITE -> showInvite()
            MODE_FEEDBACK -> showFeedbackForm()
            MODE_VERSION -> showVersion()
            MODE_REPORTS -> showReports()
            MODE_PERSONAL_QR -> showPersonalQRCode()
            else -> finish()
        }
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(), 10.dp(), 16.dp(), 10.dp())
            addView(Button(context).apply {
                text = getString(R.string.xingdun_back)
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(TextView(context).apply {
                text = titleForMode()
                textSize = 20f
                setPadding(12.dp(), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18.dp(), 14.dp(), 18.dp(), 30.dp())
            }
            addView(content)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        status = TextView(this).apply {
            setPadding(18.dp(), 10.dp(), 18.dp(), 10.dp())
            textSize = 14f
        }
        root.addView(status)
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
        val typeValues = resources.getStringArray(R.array.xingdun_workspace_type_values)
        val typeLabels = resources.getStringArray(R.array.xingdun_workspace_type_labels)
        val type = Spinner(this).apply {
            adapter = ArrayAdapter(this@XingDunFeatureActivity, android.R.layout.simple_spinner_dropdown_item, typeLabels.toList())
        }
        val title = input(R.string.xingdun_workspace_form_title)
        val reason = input(R.string.xingdun_workspace_form_reason, multiline = true)
        val start = input(R.string.xingdun_workspace_form_start)
        val end = input(R.string.xingdun_workspace_form_end)
        val amount = input(R.string.xingdun_workspace_form_amount, decimal = true)
        content.addView(type)
        listOf(title, reason, start, end, amount).forEach(content::addView)
        content.addView(actionButton(R.string.xingdun_submit) {
            if (title.text.toString().isBlank()) {
                status.setText(R.string.xingdun_workspace_title_required)
                return@actionButton
            }
            val body = linkedMapOf<String, Any?>(
                "type" to typeValues[type.selectedItemPosition],
                "title" to title.text.toString().trim(),
                "reason" to reason.text.toString().trim().takeIf(String::isNotEmpty),
                "start_time" to start.text.toString().trim().takeIf(String::isNotEmpty),
                "end_time" to end.text.toString().trim().takeIf(String::isNotEmpty),
                "amount" to amount.text.toString().trim().takeIf(String::isNotEmpty),
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
                XingDunSessionManager.apiClient().get<JsonObject>(session, "cs/identity", emptyMap(), JsonObject::class.java)
            }.onSuccess { identity ->
                setBusy(false)
                addCard(
                    getString(R.string.xingdun_customer_service),
                    if (identity.boolean("is_cs")) getString(R.string.xingdun_customer_service_agent)
                    else getString(R.string.xingdun_customer_service_user)
                )
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

    private fun showInvite() {
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    requireSession(), "share/inviteInfo", emptyMap(), JsonObject::class.java
                )
            }.onSuccess { invitation ->
                setBusy(false)
                val shareUrl = invitation.string("share_url").orEmpty()
                addCard(
                    getString(R.string.xingdun_invite_title),
                    listOfNotNull(invitation.string("invite_code"), shareUrl.takeIf(String::isNotBlank)).joinToString("\n")
                )
                if (shareUrl.isNotBlank()) {
                    content.addView(actionButton(R.string.xingdun_share) {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                        }, getString(R.string.xingdun_share)))
                    })
                }
            }.onFailure(::showFailure)
        }
    }

    private fun showFeedbackForm() {
        val types = resources.getStringArray(R.array.xingdun_feedback_type_values)
        val labels = resources.getStringArray(R.array.xingdun_feedback_type_labels)
        val type = Spinner(this).apply {
            adapter = ArrayAdapter(this@XingDunFeatureActivity, android.R.layout.simple_spinner_dropdown_item, labels.toList())
        }
        val body = input(R.string.xingdun_feedback_content, multiline = true)
        val contact = input(R.string.xingdun_feedback_contact)
        content.addView(type)
        content.addView(body)
        content.addView(contact)
        content.addView(actionButton(R.string.xingdun_submit) {
            if (body.text.toString().trim().length < 10) {
                status.setText(R.string.xingdun_feedback_content_required)
                return@actionButton
            }
            submitEmpty(
                "feedback/save",
                mapOf(
                    "feedback_type" to types[type.selectedItemPosition],
                    "content" to body.text.toString().trim(),
                    "contact" to contact.text.toString().trim(),
                    "client_request_id" to UUID.randomUUID().toString(),
                    "platform" to "android",
                    "app_version" to BuildConfig.VERSION_NAME,
                    "app_build" to BuildConfig.VERSION_CODE,
                    "os_version" to Build.VERSION.RELEASE,
                    "device_model" to Build.MODEL
                ),
                R.string.xingdun_feedback_submitted
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
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    requireSession(), "report/list", mapOf("page" to "1", "page_size" to "50"), JsonObject::class.java
                )
            }.onSuccess { page ->
                setBusy(false)
                val list = page.array("list")
                if (list.isEmpty) addMessage(R.string.xingdun_reports_empty)
                list.forEach { element ->
                    val item = element.asJsonObject
                    addCard(
                        item.string("reason_text") ?: item.string("reason") ?: getString(R.string.xingdun_report),
                        listOfNotNull(item.string("target_type"), item.string("status_text"), item.string("create_time"))
                            .joinToString(" · ")
                    )
                }
            }.onFailure(::showFailure)
        }
    }

    private fun showPersonalQRCode() {
        val session = runCatching { requireSession() }.getOrElse {
            showFailure(it)
            return
        }
        val payload = JsonObject().apply {
            addProperty("app", "XingDun")
            addProperty("type", "user")
            addProperty("user_id", session.timUserId)
            addProperty("version", 1)
        }.toString()
        val bitmap = runCatching {
            BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, 720, 720)
        }.getOrElse {
            showFailure(it)
            return
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.xingdun_personal_qr_description, session.nickname, session.timUserId)
            textSize = 16f
            gravity = Gravity.CENTER
        })
        content.addView(ImageView(this).apply {
            setImageBitmap(bitmap)
            contentDescription = getString(R.string.xingdun_personal_qr)
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 14.dp()
        })
        content.addView(actionButton(R.string.xingdun_share) {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, payload)
            }, getString(R.string.xingdun_share)))
        })
        status.setText(R.string.xingdun_personal_qr_validity)
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
        status.setText(if (busy) R.string.xingdun_loading else 0)
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
        MODE_INVITE -> R.string.xingdun_invite_title
        MODE_FEEDBACK -> R.string.xingdun_feedback
        MODE_VERSION -> R.string.xingdun_version
        MODE_REPORTS -> R.string.xingdun_reports
        MODE_PERSONAL_QR -> R.string.xingdun_personal_qr
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
        private const val EXTRA_ITEM_ID = "item_id"
        const val MODE_WORKSPACE_LIST = "workspace_list"
        const val MODE_WORKSPACE_PENDING = "workspace_pending"
        const val MODE_WORKSPACE_DETAIL = "workspace_detail"
        const val MODE_WORKSPACE_CREATE = "workspace_create"
        const val MODE_CUSTOMER_SERVICE = "customer_service"
        const val MODE_INVITE = "invite"
        const val MODE_FEEDBACK = "feedback"
        const val MODE_VERSION = "version"
        const val MODE_REPORTS = "reports"
        const val MODE_PERSONAL_QR = "personal_qr"

        fun start(context: Context, mode: String, itemId: Int = 0) {
            context.startActivity(Intent(context, XingDunFeatureActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode)
                if (itemId > 0) putExtra(EXTRA_ITEM_ID, itemId)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
