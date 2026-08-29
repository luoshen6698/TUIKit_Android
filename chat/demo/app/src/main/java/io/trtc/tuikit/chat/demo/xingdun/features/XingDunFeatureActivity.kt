package io.trtc.tuikit.chat.demo.xingdun.features

import android.Manifest
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
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
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import android.widget.PopupMenu
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
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.tencent.mmkv.MMKV
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
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
import io.trtc.tuikit.chat.uikit.components.audioplayer.AudioPlayer
import io.trtc.tuikit.chat.uikit.components.audioplayer.AudioPlayerListener
import io.trtc.tuikit.chat.uikit.components.imageviewer.EventHandler
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageElement
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewer
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Calendar
import java.util.Date
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

    private data class ReportSubmissionResult(
        val reportNo: String,
        val duplicate: Boolean? = null,
    )

    override val requiresLogin: Boolean
        get() = !(BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_BYPASS_LOGIN, false))

    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var fixedActionContainer: LinearLayout
    private lateinit var headerBar: FrameLayout
    private lateinit var headerTitle: TextView
    private val mode: String by lazy { intent.getStringExtra(EXTRA_MODE).orEmpty() }
    private val itemId: Int by lazy { intent.getIntExtra(EXTRA_ITEM_ID, 0) }
    private val targetID: String by lazy { intent.getStringExtra(EXTRA_TARGET_ID).orEmpty() }
    private val targetType: String by lazy { intent.getStringExtra(EXTRA_TARGET_TYPE).orEmpty() }
    private val targetDisplayName: String by lazy { intent.getStringExtra(EXTRA_TARGET_DISPLAY_NAME).orEmpty() }
    private val targetDisplayID: String by lazy { intent.getStringExtra(EXTRA_TARGET_DISPLAY_ID).orEmpty() }
    private val initialReport: JsonObject? by lazy {
        intent.getStringExtra(EXTRA_INITIAL_REPORT_JSON)
            ?.let { value -> runCatching { JsonParser.parseString(value).asJsonObject }.getOrNull() }
    }
    private var attachmentSelectionHandler: ((List<XingDunAttachment>) -> Unit)? = null
    private var attachmentFailureHandler: ((Throwable) -> Unit)? = null
    private var pendingInvitePoster: Bitmap? = null
    private var invitePosterSaving = false
    private var invitePosterSaveButton: Button? = null
    private var invitePosterCopyButton: Button? = null
    private var pendingPersonalQRCode: XingDunPersonalQRCodeArtifact? = null
    private var personalQRCodeSaving = false
    private var personalQRCodeSaveButton: Button? = null
    private var legalWebView: WebView? = null
    private var reportTargetFilter: String? = null
    private var reportStatusFilter: Int? = null
    private var reportPage = 1
    private var reportTotal = 0
    private var reportLoading = false
    private var reportReloadPending = false
    private var reportTouchStartY = 0f
    private var reportDetailRecord: JsonObject? = null
    private val favoriteRecords = mutableListOf<JsonObject>()
    private var favoritePage = 1
    private var favoriteTotal = 0
    private var favoriteLoading = false
    private var favoriteTouchStartY = 0f
    private var accountSecurityTouchStartY = 0f
    private var accountSecurityLoading = false
    private var storageTouchStartY = 0f
    private var storageLoading = false
    private var helpCustomerServiceTouchStartY = 0f
    private var helpCustomerServiceLoading = false
    private var helpCustomerServiceRow: LinearLayout? = null
    private var customerServiceTouchStartY = 0f
    private var customerServiceLoading = false
    private val workspaceApplicationRecords = mutableListOf<JsonObject>()
    private var workspaceApplicationPage = 1
    private var workspaceApplicationTotal = 0
    private var workspaceApplicationLoading = false
    private var workspaceApplicationTouchStartY = 0f
    private var workspaceApplicationCategory: String? = null
    private var workspaceDetailLoading = false
    private var workspaceDetailTouchStartY = 0f
    private var workspaceFormLoading = false
    private var favoriteListContainer: LinearLayout? = null
    private val favoriteAudioPlayer: AudioPlayer by lazy { AudioPlayer.create() }
    private var favoriteAudioURL: String? = null
    private var favoriteAudioView: FavoriteAudioVisual? = null
    private var aboutUpdateProgress: ProgressBar? = null
    private var aboutUpdateRow: View? = null
    private val reportRecords = mutableListOf<JsonObject>()
    private var reportListContainer: LinearLayout? = null
    private var managedPermissionFeedbackTitle: Int? = null
    private val debugReportFixtureEnabled: Boolean
        get() = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_REPORT_FIXTURE, false)
    private val debugPersonalQRCodeFixtureEnabled: Boolean
        get() = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_PERSONAL_QR_FIXTURE, false)
    private val debugInvitePosterFixtureEnabled: Boolean
        get() = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_INVITE_POSTER_FIXTURE, false)
    private val debugFavoritesFixtureEnabled: Boolean
        get() = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_FAVORITES_FIXTURE, false)
    private val debugAccountSecurityFixtureEnabled: Boolean
        get() = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_ACCOUNT_SECURITY_FIXTURE, false)
    private val debugCustomerServiceFixtureEnabled: Boolean
        get() = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_CUSTOMER_SERVICE_FIXTURE, false)

    private data class FavoriteAudioVisual(
        val icon: TextView,
        val duration: TextView,
        val seconds: Int,
    )

    private val attachmentPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        lifecycleScope.launch {
            runCatching { XingDunAttachmentResolver.metadata(this@XingDunFeatureActivity, uris) }
                .onSuccess { attachmentSelectionHandler?.invoke(it) }
                .onFailure { error ->
                    attachmentFailureHandler?.invoke(error) ?: showAttachmentFailure(error)
                }
        }
    }

    private val qrScanner = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val payload = result.data?.getStringExtra(XingDunQRCodeScannerActivity.EXTRA_PAYLOAD)
        if (result.resultCode == RESULT_OK && !payload.isNullOrBlank()) handleScannedPayload(payload)
        else finish()
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
        val permissionTitle = managedPermissionFeedbackTitle
        managedPermissionFeedbackTitle = null
        Toast.makeText(
            this,
            permissionTitle?.let {
                getString(
                    if (granted) R.string.xingdun_managed_permission_granted_feedback
                    else R.string.xingdun_managed_permission_denied_feedback,
                    getString(it),
                )
            } ?: getString(
                if (granted) R.string.xingdun_permission_granted_feedback
                else R.string.xingdun_permission_denied_feedback
            ),
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
        if (granted && poster != null) {
            performInvitePosterSave(poster)
        } else {
            setInvitePosterSaving(false)
            showInvitePosterSettingsPrompt()
        }
    }

    private val personalQRCodeStoragePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val artifact = pendingPersonalQRCode
        pendingPersonalQRCode = null
        if (granted && artifact != null) {
            performPersonalQRCodeSave(artifact)
        } else {
            setPersonalQRCodeSaving(false)
            showPersonalQRCodeSettingsPrompt()
        }
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
            MODE_FRIEND_SEARCH -> showFriendSearch()
            MODE_INVITE -> showInvite()
            MODE_FEEDBACK -> showFeedbackForm()
            MODE_VERSION -> showVersion()
            MODE_REPORTS -> showReports()
            MODE_REPORT_DETAIL -> showReportDetail()
            MODE_REPORT_CREATE -> showReportForm()
            MODE_PERSONAL_QR -> showPersonalQRCode()
            MODE_QR_SCANNER -> showQRCodeScanner()
            MODE_ACCOUNT_SECURITY -> showAccountSecurity()
            MODE_UPGRADE_ACCOUNT -> showUpgradeAccount()
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
        if (mode == MODE_FAVORITES) favoriteAudioPlayer.stop()
        legalWebView?.apply {
            stopLoading()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
        legalWebView = null
        attachmentSelectionHandler = null
        attachmentFailureHandler = null
        super.onDestroy()
    }

    override fun onRestart() {
        super.onRestart()
        when (mode) {
            MODE_WORKSPACE_LIST -> reloadWorkspaceApplications("workspace/mine", R.string.xingdun_workspace_empty)
            MODE_WORKSPACE_PENDING -> reloadWorkspaceApplications("workspace/pending", R.string.xingdun_workspace_pending_empty)
            MODE_WORKSPACE_DETAIL -> loadWorkspaceDetail()
        }
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
            headerTitle = TextView(context).apply {
                text = titleForMode()
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                maxLines = 1
            }
            addView(headerTitle, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp(), Gravity.CENTER).apply {
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
        fixedActionContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 8.dp(), 18.dp(), 10.dp())
            setBackgroundColor(Color.WHITE)
            visibility = View.GONE
        }
        root.addView(
            fixedActionContainer,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        status = TextView(this).apply {
            setPadding(18.dp(), 10.dp(), 18.dp(), 10.dp())
            textSize = 14f
        }
        root.addView(status)
        if (mode != MODE_PERSONAL_QR && mode != MODE_INVITE) {
            root.addView(
                XingDunChildBottomNavigation(this).apply {
                    bind(
                        this@XingDunFeatureActivity,
                        childSelectedTab(),
                    )
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
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
        applyWorkspaceListChrome()
        workspaceApplicationRecords.clear()
        workspaceApplicationPage = 1
        workspaceApplicationTotal = 0
        scrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> workspaceApplicationTouchStartY = event.y
                MotionEvent.ACTION_UP -> {
                    if (!workspaceApplicationLoading && scrollView.scrollY == 0 &&
                        event.y - workspaceApplicationTouchStartY > 120.dp()
                    ) {
                        reloadWorkspaceApplications(path, emptyMessage)
                    }
                }
            }
            false
        }
        renderWorkspaceApplicationList(path, emptyMessage)
        loadWorkspaceApplications(path, emptyMessage, reset = true)
    }

    private fun reloadWorkspaceApplications(path: String, emptyMessage: Int) {
        if (workspaceApplicationLoading) return
        workspaceApplicationPage = 1
        loadWorkspaceApplications(path, emptyMessage, reset = true)
    }

    private fun loadWorkspaceApplications(path: String, emptyMessage: Int, reset: Boolean) {
        if (workspaceApplicationLoading) return
        workspaceApplicationLoading = true
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                val session = requireSession()
                val query = linkedMapOf(
                    "page" to workspaceApplicationPage.toString(),
                    "page_size" to "20",
                ).apply {
                    if (path == "workspace/mine") workspaceApplicationCategory?.let { put("category", it) }
                }
                XingDunSessionManager.apiClient().get<JsonObject>(
                    session,
                    path,
                    query,
                    JsonObject::class.java
                )
            }.onSuccess { page ->
                workspaceApplicationLoading = false
                setBusy(false)
                val list = page.array("list")
                if (reset) workspaceApplicationRecords.clear()
                list.filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).forEach { item ->
                    val id = item.int("id") ?: return@forEach
                    if (workspaceApplicationRecords.none { it.int("id") == id }) workspaceApplicationRecords += item
                }
                workspaceApplicationTotal = page.int("total") ?: workspaceApplicationRecords.size
                if (workspaceApplicationRecords.size < workspaceApplicationTotal) workspaceApplicationPage += 1
                renderWorkspaceApplicationList(path, emptyMessage)
            }.onFailure {
                workspaceApplicationLoading = false
                setBusy(false)
                renderWorkspaceApplicationList(path, emptyMessage, failed = true)
            }
        }
    }

    private fun renderWorkspaceApplicationList(path: String, emptyMessage: Int, failed: Boolean = false) {
        content.removeAllViews()
        if (path == "workspace/mine") addWorkspaceApplicationFilters(path, emptyMessage)
        if (failed) {
            content.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = roundedDrawable(Color.WHITE, 14f)
                setPadding(20.dp(), 22.dp(), 20.dp(), 22.dp())
                addView(TextView(context).apply {
                    setText(R.string.xingdun_workspace_list_load_failed)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(0xFF8A8A8F.toInt())
                })
                addView(actionButton(R.string.xingdun_retry) {
                    loadWorkspaceApplications(path, emptyMessage, reset = workspaceApplicationRecords.isEmpty())
                })
            }, workspaceListSectionLayoutParams())
        }
        if (workspaceApplicationRecords.isEmpty()) {
            if (!workspaceApplicationLoading && !failed) addWorkspaceEmptyState(emptyMessage)
        } else {
            workspaceApplicationRecords.forEach { item -> addWorkspaceApplicationCard(item, path, emptyMessage) }
        }
        if (!workspaceApplicationLoading && workspaceApplicationRecords.size < workspaceApplicationTotal) {
            content.addView(actionButton(R.string.xingdun_load_more) {
                loadWorkspaceApplications(path, emptyMessage, reset = false)
            })
        }
    }

    private fun addWorkspaceApplicationFilters(path: String, emptyMessage: Int) {
        val filters = listOf(
            null to R.string.xingdun_workspace_filter_all,
            "attendance" to R.string.xingdun_workspace_filter_attendance,
            "finance" to R.string.xingdun_workspace_filter_finance,
            "hr" to R.string.xingdun_workspace_filter_hr,
        )
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedDrawable(0xFFE7E7EA.toInt(), 12f)
            setPadding(3.dp(), 3.dp(), 3.dp(), 3.dp())
            filters.forEach { (category, label) ->
                addView(TextView(context).apply {
                    setText(label)
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(0xFF1C1C1E.toInt())
                    background = if (workspaceApplicationCategory == category) roundedDrawable(Color.WHITE, 10f) else null
                    setPadding(4.dp(), 10.dp(), 4.dp(), 10.dp())
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (workspaceApplicationCategory != category) {
                            workspaceApplicationCategory = category
                            reloadWorkspaceApplications(path, emptyMessage)
                        }
                    }
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }, workspaceListSectionLayoutParams())
    }

    private fun addWorkspaceEmptyState(message: Int) {
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(20.dp(), 38.dp(), 20.dp(), 38.dp())
            addView(TextView(context).apply {
                text = "□"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(0xFF8A8A8F.toInt())
            })
            addView(TextView(context).apply {
                setText(R.string.xingdun_workspace_no_records_short)
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(0xFF1C1C1E.toInt())
                setPadding(0, 10.dp(), 0, 5.dp())
            })
            addView(TextView(context).apply {
                setText(message)
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(0xFF8A8A8F.toInt())
            })
        }, workspaceListSectionLayoutParams())
    }

    private fun addWorkspaceApplicationCard(item: JsonObject, path: String, emptyMessage: Int) {
        val id = item.int("id") ?: return
        val statusValue = item.int("status") ?: 0
        val type = item.string("type").orEmpty()
        val typeColor = workspaceTypeColor(type)
        val statusLabel = workspaceStatusLabel(statusValue, item.string("status_text") ?: item.string("status").orEmpty())
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
        }
        val header = LinearLayout(this).apply { gravity = Gravity.TOP }
        header.addView(TextView(this).apply {
            text = workspaceTypeIcon(type)
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(typeColor)
            background = roundedDrawable(Color.argb(0x1F, Color.red(typeColor), Color.green(typeColor), Color.blue(typeColor)), 10f)
        }, LinearLayout.LayoutParams(42.dp(), 42.dp()).apply { marginEnd = 12.dp() })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = item.string("title") ?: getString(R.string.xingdun_workspace_untitled)
                textSize = 16f
                setTextColor(0xFF1C1C1E.toInt())
                maxLines = 2
            })
            addView(TextView(context).apply {
                text = workspaceTypeLabel(type, item.string("type_name"))
                textSize = 13f
                setTextColor(0xFF8A8A8F.toInt())
                setPadding(0, 4.dp(), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = statusLabel
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(workspaceStatusColor(statusValue))
            background = roundedDrawable(workspaceStatusColor(statusValue, 0x22), 10f)
            setPadding(9.dp(), 4.dp(), 9.dp(), 4.dp())
        })
        card.addView(header)
        item.string("reason")?.let { reason ->
            card.addView(TextView(this).apply {
                text = reason
                textSize = 13f
                setTextColor(0xFF6D6D72.toInt())
                maxLines = 2
                setPadding(54.dp(), 8.dp(), 0, 0)
            })
        }
        val businessDetail = when {
            item.string("start_time") != null -> listOfNotNull(
                workspaceDisplayDate(item.string("start_time")),
                workspaceDisplayDate(item.string("end_time")),
            ).filter(String::isNotBlank).joinToString(" — ")
            item.string("amount") != null -> getString(R.string.xingdun_workspace_amount_display, item.string("amount"))
            else -> null
        }
        card.addView(TextView(this).apply {
            text = listOfNotNull(businessDetail, workspaceDisplayDate(item.string("create_time")).takeIf(String::isNotBlank)).joinToString("  ·  ")
            textSize = 12f
            setTextColor(0xFF8A8A8F.toInt())
            setPadding(54.dp(), 9.dp(), 0, 0)
        })
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { start(this@XingDunFeatureActivity, MODE_WORKSPACE_DETAIL, id) }
        if (path == "workspace/mine") {
            card.setOnLongClickListener {
                showWorkspaceMineListActions(item, path, emptyMessage)
                true
            }
        } else if (statusValue in 1..2) {
            card.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(workspaceInlineAction(R.string.xingdun_workspace_reject, false) {
                    showWorkspaceListRejection(item, path, emptyMessage)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 6.dp() })
                addView(workspaceInlineAction(R.string.xingdun_workspace_approve, true) {
                    AlertDialog.Builder(this@XingDunFeatureActivity)
                        .setMessage(R.string.xingdun_workspace_confirm_approve)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.xingdun_workspace_confirm_approve_action) { _, _ ->
                            submitWorkspaceListDecision(item, "approve", "", path, emptyMessage)
                        }
                        .show()
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 6.dp() })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12.dp()
            })
        }
        content.addView(card, workspaceListSectionLayoutParams())
    }

    private fun workspaceInlineAction(label: Int, primary: Boolean, action: () -> Unit) = Button(this).apply {
        setText(label)
        textSize = 13f
        isAllCaps = false
        setTextColor(if (primary) Color.WHITE else 0xFF168F83.toInt())
        backgroundTintList = ColorStateList.valueOf(if (primary) 0xFF20A88F.toInt() else 0xFFE3F5F0.toInt())
        setOnClickListener { action() }
    }

    private fun showWorkspaceMineListActions(item: JsonObject, path: String, emptyMessage: Int) {
        val statusValue = item.int("status") ?: return
        val id = item.int("id") ?: return
        val action = when (statusValue) {
            1, 2 -> "withdraw"
            3, 4, 5 -> "delete"
            else -> return
        }
        AlertDialog.Builder(this)
            .setTitle(item.string("title") ?: getString(R.string.xingdun_workspace_untitled))
            .setItems(arrayOf(getString(if (action == "withdraw") R.string.xingdun_workspace_withdraw else R.string.xingdun_workspace_delete))) { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage(if (action == "withdraw") R.string.xingdun_workspace_confirm_withdraw else R.string.xingdun_workspace_confirm_delete)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(if (action == "withdraw") R.string.xingdun_workspace_confirm_withdraw_action else R.string.xingdun_workspace_confirm_delete_action) { _, _ ->
                        submitWorkspaceMineListAction(id, action, path, emptyMessage)
                    }
                    .show()
            }
            .show()
    }

    private fun showWorkspaceListRejection(item: JsonObject, path: String, emptyMessage: Int) {
        val id = item.int("id") ?: return
        val dialog = AlertDialog.Builder(this).create()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 18f)
            setPadding(18.dp(), 10.dp(), 18.dp(), 18.dp())
        }
        val cancel = TextView(this).apply {
            setText(R.string.xingdun_cancel)
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(0xFF168F83.toInt())
            setPadding(4.dp(), 0, 8.dp(), 0)
        }
        val confirm = TextView(this).apply {
            setText(R.string.xingdun_workspace_confirm_reject)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(8.dp(), 0, 4.dp(), 0)
        }
        panel.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(cancel, LinearLayout.LayoutParams(0, 52.dp(), 1f))
            addView(TextView(context).apply {
                setText(R.string.xingdun_workspace_reject_title)
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(0xFF1C1C1E.toInt())
            }, LinearLayout.LayoutParams(0, 52.dp(), 2f))
            addView(confirm, LinearLayout.LayoutParams(0, 52.dp(), 1f))
        })
        panel.addView(TextView(this).apply {
            setText(R.string.xingdun_workspace_rejection_reason)
            textSize = 14f
            setTextColor(0xFF8A8A8F.toInt())
            setPadding(12.dp(), 8.dp(), 8.dp(), 7.dp())
        })
        val reason = EditText(this).apply {
            setHint(R.string.xingdun_workspace_reject_reason_hint)
            textSize = 16f
            setTextColor(0xFF1C1C1E.toInt())
            setHintTextColor(0xFFAEAEB2.toInt())
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 5
            maxLines = 8
            background = roundedDrawable(0xFFF7F7F9.toInt(), 12f)
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
        }
        panel.addView(reason, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 140.dp()))
        val counter = TextView(this).apply {
            text = getString(R.string.xingdun_workspace_reject_count, 0)
            textSize = 12f
            gravity = Gravity.END
            setTextColor(0xFF8A8A8F.toInt())
            setPadding(8.dp(), 6.dp(), 8.dp(), 2.dp())
        }
        panel.addView(counter)
        val error = TextView(this).apply {
            setText(R.string.xingdun_workspace_reject_failed)
            textSize = 13f
            setTextColor(0xFFD93025.toInt())
            setPadding(8.dp(), 6.dp(), 8.dp(), 0)
            visibility = View.GONE
        }
        panel.addView(error)
        dialog.setView(panel)

        var submitting = false
        fun updateActions() {
            val valid = reason.text.toString().trim().isNotEmpty() && reason.text.length <= 500
            confirm.isEnabled = valid && !submitting
            confirm.setTextColor(if (confirm.isEnabled) 0xFF168F83.toInt() else 0xFFAEAEB2.toInt())
            confirm.text = getString(
                if (submitting) R.string.xingdun_workspace_processing else R.string.xingdun_workspace_confirm_reject
            )
            cancel.isEnabled = !submitting
            cancel.alpha = if (submitting) 0.48f else 1f
        }
        reason.doAfterTextChanged {
            val count = it?.length ?: 0
            counter.text = getString(R.string.xingdun_workspace_reject_count, count)
            counter.setTextColor(if (count > 500) 0xFFD93025.toInt() else 0xFF8A8A8F.toInt())
            error.visibility = View.GONE
            updateActions()
        }
        cancel.setOnClickListener { if (!submitting) dialog.dismiss() }
        confirm.setOnClickListener {
            if (!confirm.isEnabled || submitting) return@setOnClickListener
            val comment = reason.text.toString().trim()
            submitting = true
            updateActions()
            lifecycleScope.launch {
                runCatching {
                    XingDunSessionManager.apiClient().postEmpty(
                        requireSession(),
                        "workspace/handle",
                        mapOf("id" to id, "action" to "reject", "comment" to comment),
                    )
                }.onSuccess {
                    dialog.dismiss()
                    reloadWorkspaceApplications(path, emptyMessage)
                }.onFailure {
                    submitting = false
                    error.visibility = View.VISIBLE
                    updateActions()
                }
            }
        }
        updateActions()
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.92f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        dialog.show()
    }

    private fun submitWorkspaceListDecision(item: JsonObject, action: String, comment: String, path: String, emptyMessage: Int) {
        val id = item.int("id") ?: return
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().postEmpty(
                    requireSession(), "workspace/handle", mapOf("id" to id, "action" to action, "comment" to comment)
                )
            }.onSuccess {
                setBusy(false)
                reloadWorkspaceApplications(path, emptyMessage)
            }.onFailure {
                setBusy(false)
                renderWorkspaceApplicationList(path, emptyMessage, failed = true)
            }
        }
    }

    private fun submitWorkspaceMineListAction(id: Int, action: String, path: String, emptyMessage: Int) {
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                if (action == "withdraw") XingDunSessionManager.apiClient().postEmpty(
                    requireSession(), "workspace/withdraw", mapOf("id" to id)
                ) else XingDunSessionManager.apiClient().deleteEmpty(
                    requireSession(), "workspace/delete", mapOf("id" to id)
                )
            }.onSuccess {
                setBusy(false)
                reloadWorkspaceApplications(path, emptyMessage)
            }.onFailure {
                setBusy(false)
                renderWorkspaceApplicationList(path, emptyMessage, failed = true)
            }
        }
    }

    private fun workspaceTypeLabel(type: String, fallback: String? = null): String {
        val resource = when (type) {
            "leave" -> R.string.xingdun_workspace_leave
            "travel" -> R.string.xingdun_workspace_travel
            "out" -> R.string.xingdun_workspace_out
            "overtime" -> R.string.xingdun_workspace_overtime
            "reimburse" -> R.string.xingdun_workspace_reimburse
            "purchase" -> R.string.xingdun_workspace_purchase
            "hr_need" -> R.string.xingdun_workspace_hr_need
            "confirmation" -> R.string.xingdun_workspace_confirmation
            "resign" -> R.string.xingdun_workspace_resign
            else -> return fallback?.takeIf(String::isNotBlank) ?: type
        }
        return getString(resource)
    }

    private fun workspaceTypeIcon(type: String): String = when (type) {
        "travel" -> "✈"
        "out" -> "↗"
        "overtime" -> "◴"
        "reimburse" -> "¥"
        "purchase" -> "▣"
        "hr_need" -> "+"
        "confirmation" -> "✓"
        "resign" -> "↪"
        else -> "◷"
    }

    private fun workspaceTypeColor(type: String): Int = when (type) {
        "leave" -> 0xFFE05252.toInt()
        "travel" -> 0xFF3478F6.toInt()
        "out" -> 0xFF34A853.toInt()
        "overtime" -> 0xFFE6A117.toInt()
        "reimburse" -> 0xFF8E5CC7.toInt()
        "purchase" -> 0xFF00A6B2.toInt()
        "hr_need" -> 0xFFD84B8A.toInt()
        "confirmation" -> 0xFF20A88F.toInt()
        else -> 0xFF8A8A8F.toInt()
    }

    private fun workspaceStatusLabel(statusValue: Int, fallback: String): String {
        val resource = when (statusValue) {
            1, 3 -> R.string.xingdun_workspace_status_submitted
            2 -> R.string.xingdun_workspace_status_in_review
            4 -> R.string.xingdun_workspace_status_rejected
            5 -> R.string.xingdun_workspace_status_withdrawn
            else -> return fallback
        }
        return getString(resource)
    }

    private fun workspaceStatusColor(statusValue: Int, alpha: Int = 0xFF): Int {
        val rgb = when (statusValue) {
            1, 3 -> 0xFF3478F6.toInt()
            2 -> 0xFFE6A117.toInt()
            4 -> 0xFFD93025.toInt()
            else -> 0xFF8A8A8F.toInt()
        }
        return Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
    }

    private fun applyWorkspaceListChrome() {
        applyNotificationSettingsChrome()
        status.setTextColor(0xFF8A8A8F.toInt())
    }

    private fun workspaceListSectionLayoutParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = 10.dp() }

    private fun showWorkspaceDetail() {
        applyWorkspaceListChrome()
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_WORKSPACE_DETAIL_FIXTURE, false)) {
            renderWorkspaceDetail(debugWorkspaceDetailFixture())
            return
        }
        if (itemId <= 0) {
            status.setText(R.string.xingdun_workspace_invalid_application)
            return
        }
        scrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> workspaceDetailTouchStartY = event.y
                MotionEvent.ACTION_UP -> {
                    if (!workspaceDetailLoading && scrollView.scrollY == 0 &&
                        event.y - workspaceDetailTouchStartY > 120.dp()
                    ) {
                        loadWorkspaceDetail()
                    }
                }
            }
            false
        }
        loadWorkspaceDetail()
    }

    private fun loadWorkspaceDetail() {
        if (workspaceDetailLoading || itemId <= 0) return
        workspaceDetailLoading = true
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    requireSession(), "workspace/detail", mapOf("id" to itemId.toString()), JsonObject::class.java
                )
            }.onSuccess { item ->
                workspaceDetailLoading = false
                setBusy(false)
                renderWorkspaceDetail(item)
            }.onFailure {
                workspaceDetailLoading = false
                setBusy(false)
                renderWorkspaceDetailFailure()
            }
        }
    }

    private fun renderWorkspaceDetail(item: JsonObject) {
        content.removeAllViews()
        val applicationStatus = item.int("status") ?: 0
        val type = item.string("type").orEmpty()
        val typeColor = workspaceTypeColor(type)
        val applicant = item.string("applicant_tim_user_id")
        val isApplicant = applicant != null && applicant == XingDunSessionManager.currentSession()?.timUserId
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 16f)
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
        }
        card.addView(LinearLayout(this).apply {
            gravity = Gravity.TOP
            addView(TextView(context).apply {
                text = workspaceTypeIcon(type)
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(typeColor)
                background = roundedDrawable(
                    Color.argb(0x1F, Color.red(typeColor), Color.green(typeColor), Color.blue(typeColor)),
                    12f,
                )
            }, LinearLayout.LayoutParams(48.dp(), 48.dp()).apply { marginEnd = 12.dp() })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = item.string("title").orEmpty().ifBlank { getString(R.string.xingdun_workspace_untitled) }
                    textSize = 19f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(0xFF1C1C1E.toInt())
                    maxLines = 3
                })
                addView(TextView(context).apply {
                    text = workspaceTypeLabel(type, item.string("type_name"))
                    textSize = 14f
                    setTextColor(0xFF8A8A8F.toInt())
                    setPadding(0, 3.dp(), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = workspaceStatusLabel(
                    applicationStatus,
                    item.string("status_text") ?: item.string("status").orEmpty(),
                )
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(workspaceStatusColor(applicationStatus))
                background = roundedDrawable(workspaceStatusColor(applicationStatus, 0x22), 10f)
                setPadding(9.dp(), 4.dp(), 9.dp(), 4.dp())
            })
        })
        item.string("reason")?.takeIf(String::isNotBlank)?.let {
            addWorkspaceDetailRow(card, R.string.xingdun_workspace_detail_reason, it)
        }
        item.string("start_time")?.takeIf(String::isNotBlank)?.let {
            addWorkspaceDetailRow(card, R.string.xingdun_workspace_detail_start_time, workspaceDetailDisplayDate(it))
        }
        item.string("end_time")?.takeIf(String::isNotBlank)?.let {
            addWorkspaceDetailRow(card, R.string.xingdun_workspace_detail_end_time, workspaceDetailDisplayDate(it))
        }
        item.string("amount")?.takeIf(String::isNotBlank)?.let {
            addWorkspaceDetailRow(
                card,
                R.string.xingdun_workspace_detail_amount,
                workspaceDisplayAmount(it),
                0xFF20A88F.toInt(),
            )
        }
        item.string("create_time")?.takeIf(String::isNotBlank)?.let {
            addWorkspaceDetailRow(card, R.string.xingdun_workspace_detail_create_time, workspaceDetailDisplayDate(it))
        }
        item.string("update_time")?.takeIf(String::isNotBlank)?.let {
            addWorkspaceDetailRow(card, R.string.xingdun_workspace_detail_update_time, workspaceDetailDisplayDate(it))
        }
        card.addView(View(this).apply { setBackgroundColor(0xFFE5E5EA.toInt()) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            1.dp(),
        ).apply { topMargin = 4.dp(); bottomMargin = 12.dp() })
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            if (isApplicant && applicationStatus in 3..5) {
                addView(workspaceDetailActionButton(
                    R.string.xingdun_workspace_delete,
                    0xFFD93025.toInt(),
                    0xFFFFE9E7.toInt(),
                ) { confirmWorkspaceDetailAction("delete") }, LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply { marginEnd = 6.dp() })
            } else if (isApplicant && applicationStatus in 1..2) {
                addView(workspaceDetailActionButton(
                    R.string.xingdun_workspace_withdraw,
                    0xFF168F83.toInt(),
                    0xFFE3F5F0.toInt(),
                ) { confirmWorkspaceDetailAction("withdraw") }, LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply { marginEnd = 6.dp() })
            }
            addView(workspaceDetailActionButton(
                R.string.xingdun_workspace_close_detail,
                Color.WHITE,
                0xFF20A88F.toInt(),
            ) { finish() }, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply { marginStart = if (childCount > 0) 6.dp() else 0 })
        })
        content.addView(card, workspaceListSectionLayoutParams())
    }

    private fun addWorkspaceDetailRow(container: LinearLayout, label: Int, value: String, valueColor: Int = 0xFF1C1C1E.toInt()) {
        container.addView(LinearLayout(this).apply {
            gravity = Gravity.TOP
            setPadding(0, 14.dp(), 0, 0)
            addView(TextView(context).apply {
                text = when (label) {
                    R.string.xingdun_workspace_detail_reason -> "▤"
                    R.string.xingdun_workspace_detail_start_time -> "▷"
                    R.string.xingdun_workspace_detail_end_time -> "◉"
                    R.string.xingdun_workspace_detail_amount -> "¥"
                    R.string.xingdun_workspace_detail_update_time -> "↻"
                    else -> "◷"
                }
                textSize = 13f
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setTextColor(0xFF8A8A8F.toInt())
            }, LinearLayout.LayoutParams(24.dp(), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = 6.dp() })
            addView(TextView(context).apply {
                setText(label)
                textSize = 13f
                setTextColor(0xFF8A8A8F.toInt())
            }, LinearLayout.LayoutParams(72.dp(), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(TextView(context).apply {
                text = value
                textSize = 15f
                setTextColor(valueColor)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
    }

    private fun debugWorkspaceDetailFixture() = JsonObject().apply {
        addProperty("id", 900001)
        addProperty("type", "travel")
        addProperty("type_name", getString(R.string.xingdun_workspace_travel))
        addProperty("title", getString(R.string.xingdun_workspace_detail_preview_title))
        addProperty("reason", getString(R.string.xingdun_workspace_detail_preview_reason))
        addProperty("status", 3)
        addProperty("status_text", getString(R.string.xingdun_workspace_status_submitted))
        addProperty("applicant_tim_user_id", XingDunSessionManager.currentSession()?.timUserId.orEmpty())
        addProperty("start_time", "2026-08-31 09:00:00")
        addProperty("end_time", "2026-09-02 18:00:00")
        addProperty("create_time", "2026-08-28 16:20:00")
        addProperty("update_time", "2026-08-28 16:20:00")
    }

    private fun workspaceDetailActionButton(label: Int, foreground: Int, backgroundColor: Int, action: () -> Unit) = Button(this).apply {
        setText(label)
        textSize = 13f
        isAllCaps = false
        setTextColor(foreground)
        backgroundTintList = ColorStateList.valueOf(backgroundColor)
        setOnClickListener { action() }
    }

    private fun confirmWorkspaceDetailAction(action: String) {
        val delete = action == "delete"
        AlertDialog.Builder(this)
            .setMessage(if (delete) R.string.xingdun_workspace_confirm_delete else R.string.xingdun_workspace_confirm_withdraw)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                if (delete) R.string.xingdun_workspace_confirm_delete_action else R.string.xingdun_workspace_confirm_withdraw_action,
            ) { _, _ -> performWorkspaceDetailAction(action) }
            .show()
    }

    private fun performWorkspaceDetailAction(action: String) {
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                if (action == "delete") {
                    XingDunSessionManager.apiClient().deleteEmpty(requireSession(), "workspace/delete", mapOf("id" to itemId))
                } else {
                    XingDunSessionManager.apiClient().postEmpty(requireSession(), "workspace/withdraw", mapOf("id" to itemId))
                }
            }.onSuccess {
                setBusy(false)
                if (action == "delete") {
                    setResult(RESULT_OK)
                    finish()
                } else {
                    loadWorkspaceDetail()
                }
            }.onFailure {
                setBusy(false)
                status.setText(
                    if (action == "delete") R.string.xingdun_workspace_delete_failed
                    else R.string.xingdun_workspace_withdraw_failed,
                )
            }
        }
    }

    private fun renderWorkspaceDetailFailure() {
        content.removeAllViews()
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedDrawable(Color.WHITE, 16f)
            setPadding(20.dp(), 32.dp(), 20.dp(), 32.dp())
            addView(TextView(context).apply {
                setText(R.string.xingdun_workspace_detail_load_failed)
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(0xFF8A8A8F.toInt())
            })
            addView(actionButton(R.string.xingdun_retry) { loadWorkspaceDetail() })
        }, workspaceListSectionLayoutParams())
    }

    private fun workspaceDetailDisplayDate(raw: String): String {
        val value = raw.trim()
        val date = parseDisplayDate(value) ?: return value
        val locale = resources.configuration.locales[0] ?: Locale.getDefault()
        val pattern = if (locale.language == Locale.CHINESE.language) "yyyy年M月d日 HH:mm" else "MMM d, yyyy, HH:mm"
        return SimpleDateFormat(pattern, locale).format(date)
    }

    private fun workspaceDisplayAmount(raw: String): String {
        val amount = raw.toBigDecimalOrNull() ?: return raw
        val locale = resources.configuration.locales[0] ?: Locale.getDefault()
        return NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("CNY") }.format(amount)
    }

    private fun showWorkspaceForm() {
        applyWorkspaceListChrome()
        if (workspaceFormLoading) return
        workspaceFormLoading = true
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                val values = XingDunSessionManager.apiClient().get<JsonArray>(
                    requireSession(), "workspace/types", emptyMap(), JsonArray::class.java
                )
                XingDunWorkspaceContracts.parseTypes(values)
            }.onSuccess {
                workspaceFormLoading = false
                renderWorkspaceForm(it)
            }.onFailure {
                workspaceFormLoading = false
                setBusy(false)
                renderWorkspaceFormLoadFailure()
            }
        }
    }

    private fun renderWorkspaceForm(types: List<XingDunWorkspaceType>) {
        setBusy(false)
        content.removeAllViews()
        fixedActionContainer.removeAllViews()
        fixedActionContainer.visibility = View.GONE
        status.visibility = View.GONE
        val availableTypes = types.filter(XingDunWorkspaceType::available)
        val selected = if (targetID.isNotBlank()) {
            availableTypes.firstOrNull { it.type == targetID }
        } else {
            availableTypes.firstOrNull()
        }
        if (selected == null) {
            headerTitle.setText(R.string.xingdun_workspace_create)
            content.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = roundedDrawable(Color.WHITE, 14f)
                setPadding(20.dp(), 30.dp(), 20.dp(), 30.dp())
                addView(TextView(context).apply {
                    setText(R.string.xingdun_workspace_no_available_types)
                    textSize = 15f
                    gravity = Gravity.CENTER
                    setTextColor(0xFF8A8A8F.toInt())
                })
            }, workspaceListSectionLayoutParams())
            return
        }

        val typeTitle = workspaceTypeLabel(selected.type, selected.name)
        headerTitle.text = typeTitle
        val requestID = UUID.randomUUID().toString().lowercase(Locale.ROOT)
        val startCalendar = Calendar.getInstance().apply { set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val endCalendar = (startCalendar.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 1) }

        fun sectionHeader(label: Int) {
            content.addView(TextView(this).apply {
                setText(label)
                textSize = 14f
                setTextColor(0xFF8A8A8F.toInt())
                setPadding(14.dp(), 10.dp(), 8.dp(), 7.dp())
            })
        }

        fun formCard(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(16.dp(), 4.dp(), 16.dp(), 4.dp())
        }

        fun divider(): View = View(this).apply { setBackgroundColor(0xFFE5E5EA.toInt()) }

        fun footer(text: String): TextView = TextView(this).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.END
            setTextColor(0xFF8A8A8F.toInt())
            setPadding(12.dp(), 5.dp(), 12.dp(), 5.dp())
        }

        sectionHeader(R.string.xingdun_workspace_form_basic_information)
        val basicCard = formCard()
        val title = EditText(this).apply {
            setHint(R.string.xingdun_workspace_form_title)
            setText(getString(R.string.xingdun_workspace_default_title, typeTitle))
            textSize = 16f
            setTextColor(0xFF1C1C1E.toInt())
            setHintTextColor(0xFFAEAEB2.toInt())
            background = null
            setSingleLine(true)
            setPadding(0, 10.dp(), 0, 10.dp())
        }
        basicCard.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()))
        selected.approverName?.takeIf(String::isNotBlank)?.let { approver ->
            basicCard.addView(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp()))
            basicCard.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    setText(R.string.xingdun_workspace_form_approver)
                    textSize = 16f
                    setTextColor(0xFF1C1C1E.toInt())
                }, LinearLayout.LayoutParams(0, 52.dp(), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
                addView(TextView(context).apply {
                    text = approver
                    textSize = 15f
                    setTextColor(0xFF8A8A8F.toInt())
                })
            })
        }
        content.addView(basicCard, workspaceListSectionLayoutParams())
        val titleCounter = footer(getString(R.string.xingdun_workspace_title_count, title.text.length))
        content.addView(titleCounter)

        sectionHeader(R.string.xingdun_workspace_form_reason_title)
        val reason = EditText(this).apply {
            setHint(R.string.xingdun_workspace_form_reason)
            textSize = 16f
            setTextColor(0xFF1C1C1E.toInt())
            setHintTextColor(0xFFAEAEB2.toInt())
            background = null
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 5
            setPadding(0, 12.dp(), 0, 12.dp())
        }
        content.addView(formCard().apply { addView(reason) }, workspaceListSectionLayoutParams())
        val reasonCounter = footer(getString(R.string.xingdun_workspace_reason_count, 0))
        content.addView(reasonCounter)

        var startValue: TextView? = null
        var endValue: TextView? = null
        var timeFooter: TextView? = null
        fun dateRow(label: Int, calendar: Calendar, onClick: () -> Unit): LinearLayout = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                setText(label)
                textSize = 16f
                setTextColor(0xFF1C1C1E.toInt())
            }, LinearLayout.LayoutParams(0, 54.dp(), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
            val value = TextView(context).apply {
                text = workspaceFormDisplayDate(calendar)
                textSize = 15f
                setTextColor(0xFF3A3A3C.toInt())
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
            }
            addView(value)
            addView(TextView(context).apply {
                text = "›"
                textSize = 24f
                setTextColor(0xFF8A8A8F.toInt())
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(24.dp(), 54.dp()))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            if (label == R.string.xingdun_workspace_form_start_label) startValue = value else endValue = value
        }
        fun updateTimeState() {
            startValue?.text = workspaceFormDisplayDate(startCalendar)
            endValue?.text = workspaceFormDisplayDate(endCalendar)
            timeFooter?.visibility = if (startCalendar.before(endCalendar)) View.GONE else View.VISIBLE
        }
        if (selected.requiresTime) {
            sectionHeader(R.string.xingdun_workspace_form_time_title)
            val timeCard = formCard()
            timeCard.addView(dateRow(R.string.xingdun_workspace_form_start_label, startCalendar) {
                showWorkspaceDateTimePicker(startCalendar) { updateTimeState() }
            })
            timeCard.addView(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp()))
            timeCard.addView(dateRow(R.string.xingdun_workspace_form_end_label, endCalendar) {
                showWorkspaceDateTimePicker(endCalendar) { updateTimeState() }
            })
            content.addView(timeCard, workspaceListSectionLayoutParams())
            timeFooter = TextView(this).apply {
                setText(R.string.xingdun_workspace_time_invalid)
                textSize = 12f
                setTextColor(0xFFD93025.toInt())
                setPadding(12.dp(), 0, 12.dp(), 5.dp())
                visibility = View.GONE
            }
            content.addView(timeFooter)
        }

        var amount: EditText? = null
        var amountFooter: TextView? = null
        if (selected.requiresAmount) {
            sectionHeader(R.string.xingdun_workspace_form_amount_title)
            amount = EditText(this).apply {
                hint = "0.00"
                textSize = 16f
                setTextColor(0xFF1C1C1E.toInt())
                setHintTextColor(0xFFAEAEB2.toInt())
                background = null
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setSingleLine(true)
                setPadding(0, 10.dp(), 0, 10.dp())
            }
            content.addView(formCard().apply { addView(amount) }, workspaceListSectionLayoutParams())
            amountFooter = TextView(this).apply {
                setText(R.string.xingdun_workspace_amount_invalid)
                textSize = 12f
                setPadding(12.dp(), 0, 12.dp(), 5.dp())
            }
            content.addView(amountFooter)
        }

        val errorBanner = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF8A3A00.toInt())
            background = roundedDrawable(0xFFFFF3E0.toInt(), 12f)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            visibility = View.GONE
        }
        content.addView(errorBanner, workspaceListSectionLayoutParams())

        fun showValidationError(error: XingDunWorkspaceSubmissionError) {
            errorBanner.setText(when (error) {
                XingDunWorkspaceSubmissionError.TITLE -> R.string.xingdun_workspace_title_invalid
                XingDunWorkspaceSubmissionError.REASON -> R.string.xingdun_workspace_reason_invalid
                XingDunWorkspaceSubmissionError.TIME -> R.string.xingdun_workspace_time_invalid
                XingDunWorkspaceSubmissionError.AMOUNT -> R.string.xingdun_workspace_amount_invalid
            })
            errorBanner.visibility = View.VISIBLE
        }

        title.doAfterTextChanged {
            val count = it?.length ?: 0
            titleCounter.text = getString(R.string.xingdun_workspace_title_count, count)
            titleCounter.setTextColor(if (count > 128) 0xFFD93025.toInt() else 0xFF8A8A8F.toInt())
        }
        reason.doAfterTextChanged {
            val count = it?.length ?: 0
            reasonCounter.text = getString(R.string.xingdun_workspace_reason_count, count)
            reasonCounter.setTextColor(if (count > 5_000) 0xFFD93025.toInt() else 0xFF8A8A8F.toInt())
        }
        amount?.doAfterTextChanged {
            val value = it?.toString()?.trim()?.toBigDecimalOrNull()
            val invalid = value == null || value <= java.math.BigDecimal.ZERO || value > java.math.BigDecimal("99999999.99")
            amountFooter?.setTextColor(if (invalid) 0xFFD93025.toInt() else 0xFF8A8A8F.toInt())
        }
        if (selected.requiresAmount) amountFooter?.setTextColor(0xFFD93025.toInt())

        var submitting = false
        val submitButton = Button(this).apply {
            setText(R.string.xingdun_workspace_submit_application)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(0xFF20A88F.toInt())
            minHeight = 50.dp()
        }
        submitButton.setOnClickListener {
            if (submitting) return@setOnClickListener
            val start = if (selected.requiresTime) workspaceFormPayloadDate(startCalendar) else ""
            val end = if (selected.requiresTime) workspaceFormPayloadDate(endCalendar) else ""
            val amountText = amount?.text?.toString().orEmpty()
            val validation = XingDunWorkspaceSubmissionValidator.validate(
                selected,
                title.text.toString(),
                reason.text.toString(),
                start,
                end,
                amountText,
            )
            if (validation != null) {
                showValidationError(validation)
                return@setOnClickListener
            }
            errorBanner.visibility = View.GONE
            val body = linkedMapOf<String, Any?>(
                "type" to selected.type,
                "title" to title.text.toString().trim(),
                "reason" to reason.text.toString().trim(),
                "start_time" to start.takeIf { selected.requiresTime },
                "end_time" to end.takeIf { selected.requiresTime },
                "amount" to amountText.trim().toBigDecimalOrNull().takeIf { selected.requiresAmount },
                "client_request_id" to requestID,
            )
            submitting = true
            submitButton.isEnabled = false
            submitButton.alpha = 0.58f
            submitButton.setText(R.string.xingdun_loading)
            lifecycleScope.launch {
                runCatching {
                    XingDunSessionManager.apiClient().postEmpty(requireSession(), "workspace/save", body)
                }.onSuccess {
                    Toast.makeText(this@XingDunFeatureActivity, R.string.xingdun_workspace_submitted, Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }.onFailure {
                    submitting = false
                    submitButton.isEnabled = true
                    submitButton.alpha = 1f
                    submitButton.setText(R.string.xingdun_workspace_submit_application)
                    errorBanner.setText(R.string.xingdun_workspace_submit_failed)
                    errorBanner.visibility = View.VISIBLE
                }
            }
        }
        fixedActionContainer.addView(
            submitButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()),
        )
        fixedActionContainer.visibility = View.VISIBLE
    }

    private fun showWorkspaceDateTimePicker(calendar: Calendar, onSelected: () -> Unit) {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        calendar.set(year, month, day, hour, minute, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        onSelected()
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(this),
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun workspaceFormDisplayDate(calendar: Calendar): String {
        val locale = resources.configuration.locales[0] ?: Locale.getDefault()
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(calendar.time)
    }

    private fun workspaceFormPayloadDate(calendar: Calendar): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(calendar.time)

    private fun renderWorkspaceFormLoadFailure() {
        fixedActionContainer.removeAllViews()
        fixedActionContainer.visibility = View.GONE
        status.visibility = View.GONE
        content.removeAllViews()
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(20.dp(), 30.dp(), 20.dp(), 30.dp())
            addView(TextView(context).apply {
                setText(R.string.xingdun_workspace_form_load_failed)
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(0xFF8A8A8F.toInt())
            })
            addView(actionButton(R.string.xingdun_retry) { showWorkspaceForm() })
        }, workspaceListSectionLayoutParams())
    }

    private fun showCustomerService() {
        applyCustomerServiceChrome()
        if (debugCustomerServiceFixtureEnabled) {
            val (identity, users, groups) = debugCustomerServiceDashboardFixture()
            renderCustomerServiceDashboard(identity, users, groups)
            return
        }
        scrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> customerServiceTouchStartY = event.y
                MotionEvent.ACTION_UP -> {
                    if (!customerServiceLoading && scrollView.scrollY == 0 &&
                        event.y - customerServiceTouchStartY > 120.dp()
                    ) {
                        content.removeAllViews()
                        showCustomerService()
                    }
                }
            }
            false
        }
        if (customerServiceLoading) return
        customerServiceLoading = true
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
                customerServiceLoading = false
                setBusy(false)
                renderCustomerServiceDashboard(identity, users, groups)
            }.onFailure {
                customerServiceLoading = false
                renderCustomerServiceFailure(R.string.xingdun_customer_service_dashboard_load_failed) {
                    content.removeAllViews()
                    showCustomerService()
                }
            }
        }
    }

    private fun renderCustomerServiceDashboard(identity: JsonObject, users: JsonArray, groups: JsonArray) {
        content.removeAllViews()
        addCustomerServiceSectionHeader(R.string.xingdun_customer_service_identity)
        if (identity.boolean("is_cs")) {
            val displayName = identity.getAsJsonObject("cs_info")?.string("realname")
                ?: getString(R.string.xingdun_enterprise_customer_service)
            addCustomerServiceIdentityRow(displayName, true)

            addCustomerServiceSectionHeader(getString(R.string.xingdun_bound_users, users.size()))
            val userRows = users.map { element ->
                val user = element.asJsonObject.getAsJsonObject("user") ?: JsonObject()
                val timUserID = user.string("tim_user_id").orEmpty()
                customerServiceRow(
                    user.string("nickname") ?: timUserID,
                    user.string("custom_id") ?: timUserID,
                    user.string("avatar"),
                    false,
                    onClick = {
                        if (timUserID.isNotBlank()) ChatActivity.start(this, "c2c_$timUserID")
                    },
                )
            }
            addCustomerServiceGroupedRows(userRows, R.string.xingdun_customer_service_no_users)

            addCustomerServiceSectionHeader(getString(R.string.xingdun_customer_service_groups, groups.size()))
            val groupRows = groups.map { element ->
                val group = element.asJsonObject
                val groupID = group.string("group_id").orEmpty()
                customerServiceRow(
                    group.string("name") ?: groupID,
                    getString(R.string.xingdun_group_members_count, group.int("member_count") ?: 0),
                    group.string("avatar"),
                    true,
                    group.boolean("mute_all"),
                    onClick = {
                        if (groupID.isNotBlank()) {
                            if (debugCustomerServiceFixtureEnabled) {
                                startActivity(Intent(this, XingDunFeatureActivity::class.java).apply {
                                    putExtra(EXTRA_MODE, MODE_CUSTOMER_SERVICE_GROUP)
                                    putExtra(EXTRA_TARGET_ID, groupID)
                                    putExtra(EXTRA_DEBUG_BYPASS_LOGIN, true)
                                    putExtra(EXTRA_DEBUG_CUSTOMER_SERVICE_FIXTURE, true)
                                })
                            } else {
                                start(this, MODE_CUSTOMER_SERVICE_GROUP, groupID)
                            }
                        }
                    },
                )
            }
            addCustomerServiceGroupedRows(groupRows, R.string.xingdun_customer_service_no_groups)
            return
        }

        addCustomerServiceIdentityRow(getString(R.string.xingdun_customer_service_user), false)
        val official = identity.string("official_cs_tim_user_id")
        val assigned = identity.array("customer_services").firstOrNull()?.asJsonObject?.string("tim_user_id")
        val target = official?.takeIf(String::isNotBlank) ?: assigned?.takeIf(String::isNotBlank)
        if (target != null) {
            content.addView(actionButton(R.string.xingdun_open_customer_service) {
                ChatActivity.start(this, "c2c_$target")
            })
        } else {
            addMessage(R.string.xingdun_customer_service_unavailable)
        }
    }

    private fun debugCustomerServiceDashboardFixture(): Triple<JsonObject, JsonArray, JsonArray> {
        val identity = JsonObject().apply {
            addProperty("is_cs", true)
            add("cs_info", JsonObject().apply {
                addProperty("realname", getString(R.string.xingdun_customer_service_preview_agent))
            })
        }
        val users = JsonArray().apply {
            add(JsonObject().apply { add("user", debugCustomerServiceUser("xd_preview_user_01", 1)) })
            add(JsonObject().apply { add("user", debugCustomerServiceUser("xd_preview_user_02", 2)) })
        }
        val groups = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("group_id", "xd_preview_group_01")
                addProperty("name", getString(R.string.xingdun_customer_service_preview_group_primary))
                addProperty("member_count", 18)
                addProperty("mute_all", true)
            })
            add(JsonObject().apply {
                addProperty("group_id", "xd_preview_group_02")
                addProperty("name", getString(R.string.xingdun_customer_service_preview_group_secondary))
                addProperty("member_count", 9)
                addProperty("mute_all", false)
            })
        }
        return Triple(identity, users, groups)
    }

    private fun debugCustomerServiceUser(userID: String, index: Int) = JsonObject().apply {
        addProperty("tim_user_id", userID)
        addProperty("custom_id", "CS-DEMO-0$index")
        addProperty("nickname", getString(R.string.xingdun_customer_service_preview_user, index))
    }

    private fun showFriendSearch() {
        applyCustomerServiceChrome()
        val query = input(R.string.xingdun_friend_search_hint).apply {
            setText(targetID)
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
        }
        val resultContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val searchButton = actionButton(R.string.xingdun_search_user) {
            searchFriend(query.text.toString(), resultContainer)
        }
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(14.dp(), 8.dp(), 14.dp(), 14.dp())
            addView(query)
            addView(searchButton)
        }, customerServiceSectionLayoutParams())
        content.addView(resultContainer)
        if (targetID.isNotBlank()) searchFriend(targetID, resultContainer)
    }

    private fun searchFriend(rawQuery: String, resultContainer: LinearLayout) {
        val keyword = rawQuery.trim()
        if (keyword.isEmpty() || keyword.toByteArray(Charsets.UTF_8).size > 128) {
            status.setText(R.string.xingdun_friend_search_invalid)
            return
        }
        resultContainer.removeAllViews()
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().getNullable<JsonObject>(
                    requireSession(),
                    "user/searchForFriend",
                    mapOf("keyword" to keyword),
                    JsonObject::class.java,
                )
            }.onSuccess { profile ->
                setBusy(false)
                if (profile == null) {
                    resultContainer.addView(friendSearchEmptyState())
                } else {
                    renderFriendSearchProfile(resultContainer, profile)
                }
            }.onFailure { error ->
                setBusy(false)
                status.text = error.localizedMessage ?: getString(R.string.xingdun_friend_search_failed)
            }
        }
    }

    private fun renderFriendSearchProfile(container: LinearLayout, profile: JsonObject) {
        val localUserID = profile.int("id") ?: 0
        val timUserID = profile.string("tim_user_id").orEmpty()
        val nickname = profile.string("nickname") ?: timUserID
        val relationship = if (profile.boolean("is_self")) "self" else profile.string("relationship_status").orEmpty()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val avatarFrame = FrameLayout(this)
        avatarFrame.addView(TextView(this).apply {
            text = nickname.take(1).uppercase(Locale.getDefault())
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(0xFF168F83.toInt())
            background = roundedDrawable(0xFFE3F5F0.toInt(), 26f)
        }, FrameLayout.LayoutParams(52.dp(), 52.dp()))
        profile.string("avatar")?.let { avatarURL ->
            avatarFrame.addView(ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = roundedDrawable(0xFFE3F5F0.toInt(), 26f)
                clipToOutline = true
                Glide.with(this@XingDunFeatureActivity).load(avatarURL).into(this)
            }, FrameLayout.LayoutParams(52.dp(), 52.dp()))
        }
        header.addView(avatarFrame, LinearLayout.LayoutParams(52.dp(), 52.dp()).apply { marginEnd = 12.dp() })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = nickname
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(0xFF1C1C1E.toInt())
                maxLines = 1
            })
            profile.string("signature")?.let { signature ->
                addView(TextView(context).apply {
                    text = signature
                    textSize = 13f
                    setTextColor(0xFF8A8A8F.toInt())
                    maxLines = 1
                    setPadding(0, 3.dp(), 0, 0)
                })
            }
            if (relationship.isNotBlank() && relationship != "none") {
                addView(TextView(context).apply {
                    text = friendRelationshipText(relationship)
                    textSize = 13f
                    setTextColor(if (relationship == "blocked") 0xFFD93025.toInt() else 0xFF168F83.toInt())
                    setPadding(0, 3.dp(), 0, 0)
                })
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(header)
        profile.string("custom_id")?.let { customID ->
            card.addView(TextView(this).apply {
                text = getString(R.string.xingdun_friend_account_value, customID)
                textSize = 14f
                setTextColor(0xFF6D6D72.toInt())
                setPadding(0, 14.dp(), 0, 0)
            })
        }
        if (relationship.isBlank() || relationship == "none") {
            card.addView(actionButton(R.string.xingdun_add_friend) {
                showFriendApplicationComposer(card, localUserID)
            })
        }
        container.addView(TextView(this).apply {
            setText(R.string.xingdun_user_profile)
            textSize = 14f
            setTextColor(0xFF8A8A8F.toInt())
            setPadding(14.dp(), 12.dp(), 8.dp(), 8.dp())
        })
        container.addView(card, customerServiceSectionLayoutParams())
    }

    private fun showFriendApplicationComposer(card: LinearLayout, localUserID: Int) {
        if (localUserID <= 0 || card.findViewWithTag<View>(FRIEND_APPLICATION_TAG) != null) return
        val composer = LinearLayout(this).apply {
            tag = FRIEND_APPLICATION_TAG
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10.dp(), 0, 0)
        }
        val message = input(R.string.xingdun_friend_application_message).apply {
            maxLines = 2
        }
        composer.addView(message)
        composer.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(actionButton(R.string.xingdun_cancel) { card.removeView(composer) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(actionButton(R.string.xingdun_send_application) {
                val normalized = message.text.toString().trim()
                if (normalized.toByteArray(Charsets.UTF_8).size > 256) {
                    status.setText(R.string.xingdun_friend_application_too_long)
                    return@actionButton
                }
                setBusy(true)
                lifecycleScope.launch {
                    runCatching {
                        XingDunSessionManager.apiClient().post<JsonObject>(
                            requireSession(),
                            "friend/apply",
                            mapOf("to_user_id" to localUserID, "message" to normalized),
                            JsonObject::class.java,
                        )
                    }.onSuccess { response ->
                        setBusy(false)
                        status.setText(
                            if (response.string("relationship_status") == "friend") R.string.xingdun_friend_added
                            else R.string.xingdun_friend_application_sent
                        )
                        card.removeView(composer)
                    }.onFailure(::showFailure)
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 8.dp() })
        })
        card.addView(composer)
    }

    private fun friendSearchEmptyState(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = roundedDrawable(Color.WHITE, 14f)
        setPadding(20.dp(), 56.dp(), 20.dp(), 56.dp())
        addView(TextView(context).apply {
            setText(R.string.xingdun_friend_not_found)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(0xFF1C1C1E.toInt())
        })
        addView(TextView(context).apply {
            setText(R.string.xingdun_friend_not_found_detail)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF8A8A8F.toInt())
            setPadding(0, 8.dp(), 0, 0)
        })
    }

    private fun friendRelationshipText(value: String): String = getString(when (value) {
        "self" -> R.string.xingdun_friend_relationship_self
        "friend" -> R.string.xingdun_friend_relationship_friend
        "outgoing_pending" -> R.string.xingdun_friend_relationship_outgoing
        "incoming_pending" -> R.string.xingdun_friend_relationship_incoming
        "blocked" -> R.string.xingdun_friend_relationship_blocked
        else -> R.string.xingdun_friend_relationship_none
    })

    private fun showCustomerServiceGroup() {
        applyCustomerServiceChrome()
        if (debugCustomerServiceFixtureEnabled) {
            val (group, members) = debugCustomerServiceGroupFixture()
            renderCustomerServiceGroup(group, members)
            return
        }
        if (targetID.isBlank()) {
            renderCustomerServiceFailure(R.string.xingdun_invalid_group)
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
                renderCustomerServiceGroup(group, members)
            }.onFailure {
                renderCustomerServiceFailure(R.string.xingdun_customer_service_group_load_failed) {
                    content.removeAllViews()
                    showCustomerServiceGroup()
                }
            }
        }
    }

    private fun renderCustomerServiceGroup(group: JsonObject, members: JsonArray) {
        content.removeAllViews()
        addCustomerServiceSectionHeader(R.string.xingdun_group_announcement)
        val announcement = input(R.string.xingdun_group_announcement, multiline = true).apply {
            setText(group.string("announcement").orEmpty())
            gravity = Gravity.TOP or Gravity.START
            minHeight = 96.dp()
            background = null
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            addView(announcement)
            addView(View(context).apply { setBackgroundColor(0xFFE5E5EA.toInt()) }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.dp(),
            ).apply { marginStart = 14.dp() })
            addView(customerServiceGroupActionRow(R.string.xingdun_save_announcement) {
                val value = announcement.text.toString().trim()
                if (value.codePointCount(0, value.length) > 100) {
                    status.setText(R.string.xingdun_announcement_too_long)
                } else if (debugCustomerServiceFixtureEnabled) {
                    group.addProperty("announcement", value)
                    status.setText(R.string.xingdun_saved)
                } else {
                    submitCustomerServiceAction(
                        "cs/updateGroupAnnouncement",
                        mapOf("team_id" to targetID, "announcement" to value),
                        R.string.xingdun_saved,
                    )
                }
            })
        }, customerServiceSectionLayoutParams())

        addCustomerServiceSectionHeader(R.string.xingdun_customer_service_group_control)
        val muted = group.boolean("mute_all")
        content.addView(customerServiceGroupToggleRow(muted) {
            if (debugCustomerServiceFixtureEnabled) {
                group.addProperty("mute_all", !muted)
                renderCustomerServiceGroup(group, members)
            } else {
                submitCustomerServiceAction(
                    "cs/setGroupMuteAll",
                    mapOf("team_id" to targetID, "mute" to !muted),
                    R.string.xingdun_saved,
                    refresh = true,
                )
            }
        }, customerServiceSectionLayoutParams())

        addCustomerServiceSectionHeader(R.string.xingdun_member_management)
        val currentUserID = XingDunSessionManager.currentSession()?.timUserId.orEmpty()
        val rows = members.map { element ->
            val member = element.asJsonObject
            val userID = member.string("user_id").orEmpty()
            val role = member.string("role").orEmpty()
            val canMute = role == "member" && userID.isNotBlank() && userID != currentUserID
            customerServiceGroupMemberRow(member, canMute) {
                val isMuted = member.boolean("is_muted")
                if (debugCustomerServiceFixtureEnabled) {
                    member.addProperty("is_muted", !isMuted)
                    renderCustomerServiceGroup(group, members)
                } else {
                    submitCustomerServiceAction(
                        "cs/muteGroupMember",
                        mapOf(
                            "team_id" to targetID,
                            "member_tim_user_id" to userID,
                            "mute" to !isMuted,
                            "duration_seconds" to if (isMuted) 0 else 31_536_000,
                        ),
                        R.string.xingdun_saved,
                        refresh = true,
                    )
                }
            }
        }
        addCustomerServiceGroupedRows(rows, R.string.xingdun_customer_service_no_group_members)
    }

    private fun customerServiceGroupActionRow(label: Int, onClick: () -> Unit): View = TextView(this).apply {
        setText(label)
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setTextColor(0xFF168F83.toInt())
        setPadding(16.dp(), 13.dp(), 16.dp(), 13.dp())
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun customerServiceGroupToggleRow(checked: Boolean, onClick: () -> Unit): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = roundedDrawable(Color.WHITE, 14f)
        setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
        addView(TextView(context).apply {
            setText(R.string.xingdun_customer_service_mute_all)
            textSize = 16f
            setTextColor(0xFF1C1C1E.toInt())
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = if (checked) "✓" else ""
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundedDrawable(if (checked) 0xFF168F83.toInt() else 0xFFD1D1D6.toInt(), 12f)
            contentDescription = getString(if (checked) R.string.xingdun_enabled else R.string.xingdun_disabled)
        }, LinearLayout.LayoutParams(24.dp(), 24.dp()))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun customerServiceGroupMemberRow(member: JsonObject, canMute: Boolean, onClick: () -> Unit): View {
        val userID = member.string("user_id").orEmpty()
        val name = member.string("nickname")?.takeIf(String::isNotBlank) ?: userID
        val avatarURL = member.string("avatar")
        val role = member.string("role").orEmpty()
        val action = when {
            canMute && member.boolean("is_muted") -> R.string.xingdun_unmute_member
            canMute -> R.string.xingdun_mute_member
            role == "owner" -> R.string.xingdun_customer_service_group_owner
            role == "administrator" -> R.string.xingdun_customer_service_group_administrator
            else -> R.string.xingdun_customer_service_badge
        }
        val avatarFrame = FrameLayout(this)
        avatarFrame.addView(TextView(this).apply {
            text = name.trim().take(1).uppercase(Locale.getDefault())
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFF168F83.toInt())
            background = roundedDrawable(0xFFE3F5F0.toInt(), 20f)
        }, FrameLayout.LayoutParams(40.dp(), 40.dp()))
        if (!avatarURL.isNullOrBlank()) {
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = roundedDrawable(0xFFE3F5F0.toInt(), 20f)
                clipToOutline = true
            }
            avatarFrame.addView(image, FrameLayout.LayoutParams(40.dp(), 40.dp()))
            Glide.with(this).load(avatarURL).into(image)
        }
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp(), 11.dp(), 16.dp(), 11.dp())
            addView(avatarFrame, LinearLayout.LayoutParams(40.dp(), 40.dp()).apply { marginEnd = 12.dp() })
            addView(TextView(context).apply {
                text = name
                textSize = 16f
                maxLines = 1
                setTextColor(0xFF1C1C1E.toInt())
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                setText(action)
                textSize = 14f
                setTextColor(if (canMute) 0xFF168F83.toInt() else 0xFF8A8A8F.toInt())
            })
            isClickable = canMute
            isFocusable = canMute
            if (canMute) setOnClickListener { onClick() }
        }
    }

    private fun debugCustomerServiceGroupFixture(): Pair<JsonObject, JsonArray> {
        val group = JsonObject().apply {
            addProperty("group_id", targetID.ifBlank { "xd_preview_group_01" })
            addProperty("name", getString(R.string.xingdun_customer_service_preview_group_primary))
            addProperty("announcement", getString(R.string.xingdun_customer_service_preview_announcement))
            addProperty("mute_all", true)
        }
        val members = JsonArray().apply {
            add(debugCustomerServiceGroupMember("xd_preview_owner", R.string.xingdun_customer_service_preview_owner, "owner"))
            add(debugCustomerServiceGroupMember("xd_preview_admin", R.string.xingdun_customer_service_preview_admin, "administrator"))
            add(debugCustomerServiceGroupMember("xd_preview_agent_01", R.string.xingdun_customer_service_preview_member_primary, "member"))
            add(debugCustomerServiceGroupMember("xd_preview_agent_02", R.string.xingdun_customer_service_preview_member_secondary, "member", true))
        }
        return group to members
    }

    private fun debugCustomerServiceGroupMember(
        userID: String,
        name: Int,
        role: String,
        muted: Boolean = false,
    ) = JsonObject().apply {
        addProperty("user_id", userID)
        addProperty("nickname", getString(name))
        addProperty("role", role)
        addProperty("is_muted", muted)
    }

    private fun applyCustomerServiceChrome() {
        applyNotificationSettingsChrome()
        status.setTextColor(0xFF8A8A8F.toInt())
    }

    private fun addCustomerServiceSectionHeader(title: Int) =
        addCustomerServiceSectionHeader(getString(title))

    private fun addCustomerServiceSectionHeader(title: String) {
        content.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(0xFF8A8A8F.toInt())
            setPadding(14.dp(), 12.dp(), 8.dp(), 8.dp())
        })
    }

    private fun addCustomerServiceIdentityRow(displayName: String, isAgent: Boolean) {
        content.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            addView(TextView(context).apply {
                text = displayName
                textSize = 16f
                setTextColor(0xFF1C1C1E.toInt())
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                setText(if (isAgent) R.string.xingdun_customer_service_badge else R.string.xingdun_customer_service_user)
                textSize = 13f
                setTextColor(if (isAgent) 0xFF168F83.toInt() else 0xFF8A8A8F.toInt())
                background = roundedDrawable(if (isAgent) 0xFFE3F5F0.toInt() else 0xFFF0F0F2.toInt(), 10f)
                setPadding(10.dp(), 4.dp(), 10.dp(), 4.dp())
            })
        }, customerServiceSectionLayoutParams())
    }

    private fun customerServiceRow(
        title: String,
        detail: String,
        avatarURL: String?,
        group: Boolean,
        muted: Boolean = false,
        onClick: () -> Unit,
    ): View {
        val avatarFrame = FrameLayout(this)
        val fallback = TextView(this).apply {
            text = title.trim().take(1).uppercase(Locale.getDefault())
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(0xFF168F83.toInt())
            background = roundedDrawable(0xFFE3F5F0.toInt(), if (group) 12f else 22f)
        }
        avatarFrame.addView(fallback, FrameLayout.LayoutParams(44.dp(), 44.dp()))
        if (!avatarURL.isNullOrBlank()) {
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = roundedDrawable(0xFFE3F5F0.toInt(), if (group) 12f else 22f)
                clipToOutline = true
            }
            avatarFrame.addView(image, FrameLayout.LayoutParams(44.dp(), 44.dp()))
            Glide.with(this).load(avatarURL).into(image)
        }
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp(), 12.dp(), 10.dp(), 12.dp())
            addView(avatarFrame, LinearLayout.LayoutParams(44.dp(), 44.dp()).apply { marginEnd = 12.dp() })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = title
                    textSize = 16f
                    setTextColor(0xFF1C1C1E.toInt())
                    maxLines = 1
                })
                addView(TextView(context).apply {
                    text = detail
                    textSize = 13f
                    setTextColor(0xFF8A8A8F.toInt())
                    setPadding(0, 4.dp(), 0, 0)
                    maxLines = 1
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (muted) addView(TextView(context).apply {
                text = "🔇"
                textSize = 16f
                contentDescription = getString(R.string.xingdun_muted)
            })
            if (!group) addView(ImageView(context).apply {
                setImageResource(android.R.drawable.sym_action_chat)
                imageTintList = ColorStateList.valueOf(0xFF168F83.toInt())
                contentDescription = getString(R.string.xingdun_open_customer_service)
                setPadding(4.dp(), 8.dp(), 4.dp(), 8.dp())
            }, LinearLayout.LayoutParams(30.dp(), 44.dp()).apply { marginStart = 4.dp() })
            addView(TextView(context).apply {
                text = "›"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(0xFF8A8A8F.toInt())
            }, LinearLayout.LayoutParams(28.dp(), 44.dp()))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun addCustomerServiceGroupedRows(rows: List<View>, emptyMessage: Int) {
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            if (rows.isEmpty()) {
                addView(TextView(context).apply {
                    setText(emptyMessage)
                    textSize = 15f
                    setTextColor(0xFF8A8A8F.toInt())
                    setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
                })
            } else {
                rows.forEachIndexed { index, row ->
                    addView(row)
                    if (index < rows.lastIndex) addView(View(context).apply {
                        setBackgroundColor(0xFFE5E5EA.toInt())
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp()).apply {
                        marginStart = 70.dp()
                    })
                }
            }
        }, customerServiceSectionLayoutParams())
    }

    private fun customerServiceSectionLayoutParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = 8.dp() }

    private fun renderCustomerServiceFailure(message: Int, retry: (() -> Unit)? = null) {
        setBusy(false)
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(20.dp(), 24.dp(), 20.dp(), 24.dp())
            addView(TextView(context).apply {
                setText(message)
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(0xFF8A8A8F.toInt())
            })
            if (retry != null) addView(actionButton(R.string.xingdun_retry, retry))
        }, customerServiceSectionLayoutParams())
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
        if (debugInvitePosterFixtureEnabled) {
            renderDebugInvitePoster()
            return
        }
        showInvitePosterLoading()
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                val session = requireSession()
                val response = XingDunSessionManager.apiClient().get<JsonObject>(
                    session, "share/inviteInfo", emptyMap(), JsonObject::class.java
                )
                val invitation = validateInviteInformation(response, session.companyCode)
                val profileResult = runCatching {
                    XingDunSessionManager.apiClient().get<JsonObject>(
                        session, "user/profile", emptyMap(), JsonObject::class.java
                    )
                }
                val profile = profileResult.getOrDefault(JsonObject())
                if (profileResult.isSuccess) {
                    profile.string("tim_user_id")?.let { returnedUserID ->
                        check(returnedUserID == session.timUserId)
                    }
                    profile.string("company_code")?.let { returnedCompanyCode ->
                        check(returnedCompanyCode.equals(session.companyCode, ignoreCase = true))
                    }
                }
                val nickname = profile.string("nickname") ?: session.nickname
                Triple(invitation, session, nickname)
            }.onSuccess { (invitation, _, nickname) ->
                setBusy(false)
                val qrBitmap = BarcodeEncoder().encodeBitmap(
                    invitation.qrPayload,
                    BarcodeFormat.QR_CODE,
                    720,
                    720,
                )
                val poster = createInvitePoster(
                    qrBitmap = qrBitmap,
                    inviteCode = invitation.inviteCode,
                    nickname = nickname,
                    brandName = getString(R.string.xingdun_platform_brand_name),
                )
                renderInvitePoster(poster, invitation.shareUrl)
            }.onFailure {
                setBusy(false)
                showInvitePosterUnavailable()
            }
        }
    }

    private fun renderDebugInvitePoster() {
        val inviteCode = "xc2345"
        val payload = "{\"version\":1,\"type\":\"xingdun_invite\",\"code\":\"$inviteCode\",\"company_code\":\"xc2026\"}"
        val qrBitmap = BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, 720, 720)
        renderInvitePoster(
            poster = createInvitePoster(qrBitmap, inviteCode, "d001", getString(R.string.xingdun_platform_brand_name)),
            shareUrl = "https://api.xingdunim.com/prod/xingdun/share.html?code=$inviteCode&company_code=xc2026",
        )
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
        setInvitePosterSaving(false)
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
        val saveButton = invitePosterButton(R.string.xingdun_save_invite_poster, primary = true) {
            saveInvitePoster(poster)
        }
        val copyButton = invitePosterButton(R.string.xingdun_copy_share_link, primary = false) {
            if (invitePosterSaving) return@invitePosterButton
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.xingdun_copy_share_link), shareUrl))
            showInvitePosterFeedback(R.string.xingdun_share_link_copied)
        }
        invitePosterSaveButton = saveButton
        invitePosterCopyButton = copyButton
        content.addView(saveButton)
        content.addView(copyButton)
    }

    private fun invitePosterButton(label: Int, primary: Boolean, action: () -> Unit): Button =
        actionButton(label, action).apply {
            val foreground = if (primary) Color.WHITE else 0xFF28B7A2.toInt()
            setTextColor(foreground)
            background = roundedDrawable(if (primary) 0xFF28B7A2.toInt() else 0xFF063B36.toInt(), 10f)
            compoundDrawablePadding = 8.dp()
            setCompoundDrawablesWithIntrinsicBounds(
                if (primary) R.drawable.xingdun_ic_save_image else R.drawable.xingdun_ic_link,
                0,
                0,
                0,
            )
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(foreground)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()).apply {
                topMargin = 12.dp()
                marginStart = 30.dp()
                marginEnd = 30.dp()
            }
        }

    private fun showInvitePosterUnavailable() {
        invitePosterSaveButton = null
        invitePosterCopyButton = null
        invitePosterSaving = false
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

    private fun createInvitePoster(
        qrBitmap: Bitmap,
        inviteCode: String,
        nickname: String,
        brandName: String,
    ): Bitmap {
        val poster = Bitmap.createBitmap(1_080, 1_440, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(poster)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        canvas.drawColor(Color.rgb(245, 247, 250))
        paint.color = Color.rgb(20, 46, 74)
        canvas.drawRect(0f, 0f, 1_080f, 430f, paint)
        paint.color = Color.rgb(31, 140, 89)
        canvas.drawRect(0f, 414f, 1_080f, 430f, paint)
        drawPosterText(canvas, paint, brandName, 120f, 62f, Color.WHITE, true)
        drawPosterText(canvas, paint, getString(R.string.xingdun_invite_poster_tagline), 210f, 32f, Color.WHITE)
        drawPosterText(
            canvas,
            paint,
            getString(R.string.xingdun_invite_poster_invitation, nickname.ifBlank { brandName }),
            326f,
            32f,
            Color.rgb(220, 229, 238)
        )
        paint.color = Color.WHITE
        canvas.drawRoundRect(90f, 500f, 990f, 1_320f, 24f, 24f, paint)
        canvas.drawBitmap(qrBitmap, null, android.graphics.RectF(230f, 570f, 850f, 1_190f), paint)
        drawPosterText(
            canvas,
            paint,
            getString(R.string.xingdun_invite_poster_code, inviteCode.uppercase(Locale.ROOT)),
            1_255f,
            34f,
            Color.rgb(20, 46, 74),
            true,
        )
        drawPosterText(canvas, paint, getString(R.string.xingdun_invite_poster_scan_hint, brandName), 1_390f, 27f, Color.rgb(89, 99, 112))
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
        if (invitePosterSaving) return
        setInvitePosterSaving(true)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingInvitePoster = poster
            invitePosterStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        performInvitePosterSave(poster)
    }

    private fun performInvitePosterSave(poster: Bitmap) {
        lifecycleScope.launch {
            setBusy(true)
            runCatching {
                XingDunImageDelivery.saveToPictures(
                    this@XingDunFeatureActivity,
                    poster,
                    "xingdun_invite_${System.currentTimeMillis()}.png",
                )
            }.onSuccess {
                setInvitePosterSaving(false)
                setBusy(false)
                showInvitePosterFeedback(R.string.xingdun_invite_poster_saved)
            }.onFailure {
                setInvitePosterSaving(false)
                setBusy(false)
                showInvitePosterFeedback(R.string.xingdun_invite_poster_save_failed)
            }
        }
    }

    private fun setInvitePosterSaving(saving: Boolean) {
        invitePosterSaving = saving
        invitePosterSaveButton?.apply {
            isEnabled = !saving
            setText(if (saving) R.string.xingdun_invite_poster_saving else R.string.xingdun_save_invite_poster)
            alpha = if (saving) 0.48f else 1f
        }
        invitePosterCopyButton?.apply {
            isEnabled = !saving
            alpha = if (saving) 0.48f else 1f
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
        val feedbackError = TextView(this).apply {
            visibility = View.GONE
            textSize = 13f
            setTextColor(0xFF9A3412.toInt())
            background = roundedDrawable(0xFFFFF4E5.toInt(), 12f)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
        }
        val setFeedbackError: (CharSequence?) -> Unit = { message ->
            feedbackError.text = message?.toString().orEmpty()
            feedbackError.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        attachmentFailureHandler = { error ->
            setBusy(false)
            setFeedbackError(getString(attachmentFailureMessage(error)))
        }
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
                        attachmentFailureHandler?.invoke(XingDunAttachmentException(XingDunAttachmentError.TOO_MANY))
                    } else {
                        attachments = combined
                        setFeedbackError(null)
                        renderAttachments()
                        updateState()
                    }
                }
                attachmentPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
            }.apply {
                setTextColor(0xFF28B7A2.toInt())
                background = null
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                compoundDrawablePadding = 8.dp()
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.xingdun_ic_feedback_add_image, 0, 0, 0)
                elevation = 0f
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
        content.addView(feedbackError, feedbackSectionLayoutParams())

        submitButton = actionButton(R.string.xingdun_feedback_submit) {
            result?.let {
                finish()
                return@actionButton
            }
            val normalizedDescription = description.text.toString().trim()
            val normalizedContact = contact.text.toString().trim()
            if (normalizedDescription.length !in 10..2_000) {
                setFeedbackError(getString(R.string.xingdun_feedback_content_required))
                return@actionButton
            }
            if (normalizedContact.length > 128) {
                setFeedbackError(getString(R.string.xingdun_feedback_contact_too_long))
                return@actionButton
            }
            submitting = true
            setFeedbackError(null)
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
                    setFeedbackError(null)
                    Toast.makeText(this@XingDunFeatureActivity, message, Toast.LENGTH_LONG).show()
                    submitButton.setText(R.string.xingdun_complete)
                    renderAttachments()
                    updateState()
                }.onFailure { error ->
                    submitting = false
                    setBusy(false)
                    updateState()
                    val message = if (error is XingDunAttachmentException) {
                        getString(attachmentFailureMessage(error))
                    } else {
                        error.localizedMessage ?: getString(R.string.xingdun_action_failed)
                    }
                    setFeedbackError(message)
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
        val targetValues = listOf("user", "team", "message")
        val hasFixedTarget = targetType in targetValues && targetID.isNotBlank()
        if ((targetType.isNotBlank() || targetID.isNotBlank()) && !hasFixedTarget) {
            showFailure(IllegalArgumentException(getString(R.string.xingdun_report_invalid_target)))
            return
        }
        applyFeedbackFormChrome()
        var selectedTargetType = if (hasFixedTarget) targetType else targetValues.first()
        val targetLabels = listOf(
            getString(R.string.xingdun_report_target_user),
            getString(R.string.xingdun_report_target_team),
            getString(R.string.xingdun_report_target_message_short),
        )
        val targetTypeSegments = mutableListOf<TextView>()
        lateinit var selectTargetType: (Int) -> Unit
        val targetTypeSelector = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedDrawable(0xFFEDEEF0.toInt(), 11f)
            setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
            targetLabels.forEachIndexed { index, label ->
                addView(TextView(context).apply {
                    text = label
                    textSize = 14f
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { selectTargetType(index) }
                    targetTypeSegments += this
                }, LinearLayout.LayoutParams(0, 42.dp(), 1f))
            }
        }
        val targetIdentifier = if (hasFixedTarget) null else input(
            R.string.xingdun_report_target_user_id_hint,
        ).apply {
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val reasonValues = resources.getStringArray(R.array.xingdun_report_reason_values)
        val reasonLabels = resources.getStringArray(R.array.xingdun_report_reason_labels)
        val reason = Spinner(this).apply {
            adapter = ArrayAdapter(this@XingDunFeatureActivity, android.R.layout.simple_spinner_dropdown_item, reasonLabels.toList())
        }
        val description = input(R.string.xingdun_report_description_hint, multiline = true).apply {
            minHeight = 140.dp()
            gravity = Gravity.TOP or Gravity.START
        }
        val descriptionCount = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF8A8A8F.toInt())
            gravity = Gravity.END
        }
        var attachments = emptyList<XingDunAttachment>()
        var result: ReportSubmissionResult? = null
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
        lateinit var historyButton: Button
        lateinit var updateState: () -> Unit
        lateinit var renderAttachments: () -> Unit
        var formReady = false
        renderAttachments = {
            attachmentTitle.text = getString(R.string.xingdun_report_images_count, attachments.size)
            attachmentRow.removeAllViews()
            attachments.forEach { attachment ->
                attachmentRow.addView(feedbackAttachmentPreview(attachment, result == null && !submitting) {
                    if (result == null && !submitting) {
                        attachments = attachments.filterNot { it.uri == attachment.uri }
                        renderAttachments()
                        updateState()
                    }
                })
            }
            attachmentScroll.visibility = if (attachments.isEmpty()) View.GONE else View.VISIBLE
        }

        addFeedbackSection(
            R.string.xingdun_report_target,
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                if (hasFixedTarget) {
                    addView(reportTargetRow(R.string.xingdun_report_target_type, reportFormTargetText(targetType)))
                    addView(reportDivider())
                    addView(reportTargetRow(
                        R.string.xingdun_report_target_object,
                        targetDisplayName.trim().ifEmpty { reportTargetText(targetType) },
                    ))
                    addView(reportDivider())
                    addView(reportTargetRow(
                        R.string.xingdun_report_target_identifier,
                        targetDisplayID.trim().ifEmpty { targetID },
                    ))
                } else {
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(TextView(context).apply {
                            setText(R.string.xingdun_report_target_type)
                            textSize = 14f
                            setTextColor(0xFF1C1C1E.toInt())
                            setPadding(0, 4.dp(), 0, 8.dp())
                        })
                        addView(targetTypeSelector, LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            50.dp(),
                        ))
                    })
                    addView(reportDivider())
                    addView(targetIdentifier)
                }
            },
        )
        addFeedbackSection(R.string.xingdun_report_reason, reason)
        addFeedbackSection(
            R.string.xingdun_report_description_section,
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(description)
                addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(context).apply {
                        setText(R.string.xingdun_report_description_footer)
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
            addImageButton = actionButton(R.string.xingdun_report_add_image) {
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
            }.apply {
                setTextColor(0xFF28B7A2.toInt())
                background = null
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                compoundDrawablePadding = 8.dp()
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.xingdun_ic_feedback_add_image, 0, 0, 0)
                elevation = 0f
            }
            addView(addImageButton)
        }, feedbackSectionLayoutParams())

        historyButton = actionButton(R.string.xingdun_report_view_history) {
            start(this@XingDunFeatureActivity, MODE_REPORTS)
        }.apply { visibility = View.GONE }
        content.addView(historyButton)
        submitButton = actionButton(R.string.xingdun_report_submit) {
            result?.let {
                finish()
                return@actionButton
            }
            val detail = description.text.toString().trim()
            val submissionTargetID = if (hasFixedTarget) targetID else targetIdentifier?.text?.toString()?.trim().orEmpty()
            if (submissionTargetID.isEmpty()) {
                status.setText(R.string.xingdun_report_invalid_target)
                return@actionButton
            }
            if (detail.isEmpty() || detail.length > 500) {
                status.setText(R.string.xingdun_report_description_required_error)
                return@actionButton
            }
            submitting = true
            updateState()
            setBusy(true)
            lifecycleScope.launch {
                runCatching {
                    val files = XingDunAttachmentResolver.uploadFiles(this@XingDunFeatureActivity, attachments)
                    XingDunSessionManager.apiClient().postMultipart<ReportSubmissionResult>(
                        session = requireSession(),
                        path = "report/save",
                        fields = mapOf(
                            "target_type" to selectedTargetType,
                            "target_id" to submissionTargetID,
                            "reason" to reasonValues[reason.selectedItemPosition],
                            "description" to detail,
                        ),
                        files = files,
                        responseType = ReportSubmissionResult::class.java,
                    )
                }.onSuccess { submission ->
                    result = submission
                    submitting = false
                    setBusy(false)
                    reason.isEnabled = false
                    targetTypeSegments.forEach { it.isEnabled = false }
                    targetIdentifier?.isEnabled = false
                    description.isEnabled = false
                    val message = if (submission.duplicate == true) {
                        getString(R.string.xingdun_report_duplicate_result)
                    } else {
                        getString(R.string.xingdun_report_success_result, submission.reportNo)
                    }
                    status.text = message
                    Toast.makeText(this@XingDunFeatureActivity, message, Toast.LENGTH_LONG).show()
                    historyButton.visibility = View.VISIBLE
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
            descriptionCount.text = getString(R.string.xingdun_report_character_count, count)
            descriptionCount.setTextColor(if (count > 500) 0xFFD93025.toInt() else 0xFF8A8A8F.toInt())
            addImageButton.isEnabled = result == null && attachments.size < XingDunAttachmentResolver.MAX_COUNT && !submitting
            val hasTargetIdentifier = hasFixedTarget || !targetIdentifier?.text?.toString()?.trim().isNullOrEmpty()
            val targetSelectionEnabled = !hasFixedTarget && result == null && !submitting
            targetTypeSegments.forEach {
                it.isEnabled = targetSelectionEnabled
                it.alpha = if (targetSelectionEnabled) 1f else 0.55f
            }
            targetIdentifier?.isEnabled = result == null && !submitting
            submitButton.isEnabled = result != null || (hasTargetIdentifier && count in 1..500 && !submitting)
            submitButton.setText(if (submitting) R.string.xingdun_workspace_processing else if (result != null) R.string.xingdun_complete else R.string.xingdun_report_submit)
        }
        selectTargetType = { requestedPosition ->
            val position = requestedPosition.coerceIn(targetValues.indices)
            selectedTargetType = targetValues[position]
            targetTypeSegments.forEachIndexed { index, segment ->
                val selected = index == position
                segment.isSelected = selected
                segment.setTextColor(if (selected) 0xFF0B6B60.toInt() else 0xFF5C6966.toInt())
                segment.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                segment.background = if (selected) roundedDrawable(Color.WHITE, 9f) else null
                ViewCompat.setElevation(segment, if (selected) 2.dp().toFloat() else 0f)
            }
            targetIdentifier?.setHint(when (selectedTargetType) {
                "team" -> R.string.xingdun_report_target_team_id_hint
                "message" -> R.string.xingdun_report_target_message_id_hint
                else -> R.string.xingdun_report_target_user_id_hint
            })
            if (formReady) updateState()
        }
        targetIdentifier?.doAfterTextChanged { if (formReady) updateState() }
        description.doAfterTextChanged { updateState() }
        formReady = true
        selectTargetType(0)
        renderAttachments()
        updateState()
    }

    private fun reportTargetRow(label: Int, value: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = 48.dp()
        addView(TextView(context).apply {
            setText(label)
            textSize = 16f
            setTextColor(0xFF1C1C1E.toInt())
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = value
            textSize = 15f
            gravity = Gravity.END
            setTextColor(0xFF8A8A8F.toInt())
            setTextIsSelectable(true)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f))
    }

    private fun reportDivider(): View = View(this).apply {
        setBackgroundColor(0xFFE5E5EA.toInt())
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp())
    }

    private fun reportFormTargetText(value: String): String = when (value) {
        "user" -> getString(R.string.xingdun_report_target_user)
        "team" -> getString(R.string.xingdun_report_target_team)
        "message" -> getString(R.string.xingdun_report_target_message_short)
        else -> getString(R.string.xingdun_report_target)
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
            setPadding(14.dp(), 4.dp(), 14.dp(), 4.dp())
            background = roundedDrawable(Color.WHITE, 18f)
        }
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_filter)
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(4.dp(), 0, 4.dp(), 8.dp())
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
            minimumHeight = 44.dp()
            addView(TextView(context).apply {
                setText(label)
                textSize = 15f
                setTextColor(Color.BLACK)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, values).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF23B39C.toInt())
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        (view as? TextView)?.setTextColor(0xFF23B39C.toInt())
                        onSelected(position)
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    private fun loadReports(reset: Boolean) {
        if (reportLoading) {
            if (reset) reportReloadPending = true
            return
        }
        reportLoading = true
        val requestedPage = if (reset) 1 else reportPage + 1
        val requestedTargetFilter = reportTargetFilter
        val requestedStatusFilter = reportStatusFilter
        if (reset) {
            reportRecords.clear()
            reportPage = 1
            reportTotal = 0
            reportListContainer?.removeAllViews()
        }
        setBusy(true)
        lifecycleScope.launch {
            val result = runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    requireSession(),
                    "report/list",
                    mapOf(
                        "target_type" to requestedTargetFilter,
                        "status" to requestedStatusFilter?.toString(),
                        "page" to requestedPage.toString(),
                        "page_size" to REPORT_PAGE_SIZE.toString(),
                    ),
                    JsonObject::class.java,
                )
            }
            val filtersStillCurrent = requestedTargetFilter == reportTargetFilter &&
                requestedStatusFilter == reportStatusFilter
            reportLoading = false
            setBusy(false)
            result.onSuccess { page ->
                if (filtersStillCurrent) {
                    page.array("list").forEach { element ->
                        val item = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@forEach
                        val id = item.int("id") ?: return@forEach
                        if (reportRecords.none { it.int("id") == id }) reportRecords += item
                    }
                    reportPage = requestedPage
                    reportTotal = page.int("total") ?: reportRecords.size
                    renderReportList()
                }
            }.onFailure { error ->
                if (filtersStillCurrent) {
                    val message = getString(R.string.xingdun_reports_load_failed)
                    showFailure(IllegalStateException(message, error))
                    renderReportList(message)
                }
            }
            if (reportReloadPending || !filtersStillCurrent) {
                reportReloadPending = false
                loadReports(reset = true)
            }
        }
    }

    private fun renderReportList(errorMessage: String? = null) {
        val container = reportListContainer ?: return
        container.removeAllViews()
        errorMessage?.let { message ->
            container.addView(TextView(this).apply {
                text = message
                textSize = 14f
                setTextColor(0xFF8A6D1D.toInt())
                setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
                background = roundedDrawable(0xFFFFF4D8.toInt(), 14f)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10.dp()
            })
        }
        if (reportRecords.isEmpty()) {
            container.addView(reportEmptyState(null))
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
                startActivity(Intent(this@XingDunFeatureActivity, XingDunFeatureActivity::class.java).apply {
                    putExtra(EXTRA_MODE, MODE_REPORT_DETAIL)
                    putExtra(EXTRA_ITEM_ID, reportID)
                    putExtra(EXTRA_INITIAL_REPORT_JSON, record.toString())
                    if (debugReportFixtureEnabled) {
                        putExtra(EXTRA_DEBUG_BYPASS_LOGIN, true)
                        putExtra(EXTRA_DEBUG_REPORT_FIXTURE, true)
                    }
                })
            }
        }
        addView(ImageView(context).apply {
            setImageResource(reportTargetIcon(record.string("target_type")))
            imageTintList = android.content.res.ColorStateList.valueOf(0xFFF0A526.toInt())
        }, LinearLayout.LayoutParams(32.dp(), 32.dp()).apply { marginEnd = 12.dp() })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = record.string("target_name")?.takeIf(String::isNotBlank)
                    ?: reportTargetText(record.string("target_type"))
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
        addView(ImageView(context).apply {
            setImageResource(R.drawable.demo_ic_arrow_right)
            imageTintList = android.content.res.ColorStateList.valueOf(0xFF8A8A8F.toInt())
        }, LinearLayout.LayoutParams(7.dp(), 12.dp()).apply { marginStart = 10.dp() })
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
        scrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> reportTouchStartY = event.y
                MotionEvent.ACTION_UP -> {
                    if (scrollView.scrollY == 0 && event.y - reportTouchStartY > 120.dp()) {
                        loadReportDetail()
                    }
                }
            }
            false
        }
        initialReport?.let {
            reportDetailRecord = it
            renderReportDetail(it)
        }
        if (debugReportFixtureEnabled) {
            val fixture = debugReportFixture()
            reportDetailRecord = fixture
            renderReportDetail(fixture)
            return
        }
        loadReportDetail()
    }

    private fun loadReportDetail() {
        if (reportLoading) return
        reportLoading = true
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    requireSession(), "report/read", mapOf("id" to itemId.toString()), JsonObject::class.java
                )
            }.onSuccess {
                reportLoading = false
                setBusy(false)
                reportDetailRecord = it
                renderReportDetail(it)
            }.onFailure { error ->
                reportLoading = false
                showFailure(IllegalStateException(getString(R.string.xingdun_report_detail_load_failed), error))
                if (reportDetailRecord == null) {
                    content.addView(actionButton(R.string.xingdun_retry) { content.removeAllViews(); loadReportDetail() })
                }
            }
        }
    }

    private fun renderReportDetail(report: JsonObject) {
        content.removeAllViews()
        addDetailSection(null, listOf(
            getString(R.string.xingdun_report_processing_status) to reportStatusText(report.int("status"), report.string("status_text")),
            getString(R.string.xingdun_report_number) to report.string("report_no").orEmpty(),
            getString(R.string.xingdun_report_target) to (
                report.string("target_name")?.takeIf(String::isNotBlank)
                    ?: reportTargetText(report.string("target_type"))
            ),
            getString(R.string.xingdun_report_target_id) to report.string("target_id").orEmpty(),
            getString(R.string.xingdun_report_reason) to reportReasonText(report.string("reason"), report.string("reason_text")),
            getString(R.string.xingdun_report_submitted_at) to report.string("create_time").orEmpty(),
        ), reportStatusValue = report.int("status"))
        report.string("description")?.let { addDetailTextSection(R.string.xingdun_report_description_section, it) }
        val screenshots = report.array("screenshot").mapNotNull { element ->
            element.takeUnless(JsonElement::isJsonNull)?.let { runCatching { it.asString }.getOrNull() }
        }
        if (screenshots.isNotEmpty()) {
            addSectionTitle(R.string.xingdun_report_screenshot_evidence)
            screenshots.forEach { url ->
                content.addView(reportScreenshotView(url), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
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

    private fun reportScreenshotView(url: String): View = FrameLayout(this).apply {
        minimumHeight = 120.dp()
        background = roundedDrawable(Color.WHITE, 14f)
        val image = ImageView(context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        val failure = TextView(context).apply {
            setText(R.string.xingdun_report_screenshot_load_failed)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF8A6D1D.toInt())
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            visibility = View.GONE
        }
        addView(image, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(failure, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        Glide.with(applicationContext)
            .load(url)
            .placeholder(R.drawable.xingdun_ic_mine_report)
            .error(R.drawable.xingdun_ic_mine_report)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean,
                ): Boolean {
                    failure.visibility = View.VISIBLE
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean,
                ): Boolean {
                    failure.visibility = View.GONE
                    return false
                }
            })
            .into(image)
    }

    private fun addSectionTitle(label: Int) {
        content.addView(TextView(this).apply {
            setText(label)
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(4.dp(), 12.dp(), 4.dp(), 8.dp())
        })
    }

    private fun addDetailSection(
        title: Int?,
        rows: List<Pair<String, String>>,
        reportStatusValue: Int? = null,
    ) {
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
                        addView(reportStatusBadge(reportStatusValue, value))
                    } else {
                        addView(TextView(context).apply {
                            text = value
                            textSize = 14f
                            gravity = Gravity.END
                            setTextColor(Color.DKGRAY)
                            setTextIsSelectable(true)
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f))
                    }
                })
                if (index < visibleRows.lastIndex) {
                    addView(View(context).apply { setBackgroundColor(0xFFE5E5EA.toInt()) }, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1.dp(),
                    ))
                }
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
        "post" -> getString(R.string.xingdun_report_target_post)
        else -> getString(R.string.xingdun_report_target)
    }

    private fun reportTargetIcon(value: String?): Int = when (value) {
        "user" -> R.drawable.xingdun_ic_user
        "team" -> R.drawable.demo_ic_tab_contacts
        "message", "post" -> R.drawable.demo_ic_tab_messages
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
        if (accountSecurityLoading) return
        applyNotificationSettingsChrome()
        scrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> accountSecurityTouchStartY = event.y
                MotionEvent.ACTION_UP -> {
                    if (scrollView.scrollY == 0 && event.y - accountSecurityTouchStartY > 120.dp()) {
                        content.removeAllViews()
                        showAccountSecurity()
                    }
                }
            }
            false
        }
        if (debugAccountSecurityFixtureEnabled) {
            renderAccountSecurity(JsonObject().apply {
                addProperty("username", "xingdun_user")
                addProperty("phone", "13800138000")
                addProperty("email", "user@xingdunim.com")
            })
            return
        }
        val session = XingDunSessionManager.currentSession() ?: run {
            showFailure(IllegalStateException(getString(R.string.xingdun_session_expired)))
            return
        }
        accountSecurityLoading = true
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    session, "user/profile", emptyMap(), JsonObject::class.java
                ).also { profile ->
                    profile.string("company_code")?.let { returnedCompanyCode ->
                        check(returnedCompanyCode.equals(session.companyCode, ignoreCase = true))
                    }
                    profile.string("tim_user_id")?.let { returnedTimUserID ->
                        check(returnedTimUserID == session.timUserId)
                    }
                }
            }.onSuccess { profile ->
                accountSecurityLoading = false
                setBusy(false)
                renderAccountSecurity(profile)
            }.onFailure {
                accountSecurityLoading = false
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
                        setText(R.string.xingdun_account_security_retry_hint)
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
                icon = R.drawable.xingdun_ic_settings_account,
                title = R.string.xingdun_username,
                value = if (isDeviceAccount) getString(R.string.xingdun_not_bound) else username,
                action = if (isDeviceAccount) { { openAccountChild(MODE_UPGRADE_ACCOUNT) } } else null,
            )
        ), notificationSectionLayoutParams())
        if (isDeviceAccount) addNotificationFooter(R.string.xingdun_device_login_hint)

        addNotificationSectionHeader(R.string.xingdun_account_contact_section)
        content.addView(accountSecurityCard(
            accountSecurityRow(
                R.drawable.xingdun_ic_account_phone,
                R.string.xingdun_phone,
                maskPhone(profile.string("phone")),
                action = { openAccountChild(MODE_BIND_PHONE) },
            ),
            notificationDivider(),
            accountSecurityRow(
                R.drawable.xingdun_ic_account_email,
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
                    R.drawable.xingdun_ic_password,
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
                    R.drawable.xingdun_ic_account_deactivate,
                    R.string.xingdun_deactivate_account,
                    null,
                    action = { openAccountChild(MODE_DEACTIVATE) },
                    danger = true,
                ),
            ), notificationSectionLayoutParams())
        }
    }

    private fun openAccountChild(childMode: String) {
        accountChildResult.launch(Intent(this, XingDunFeatureActivity::class.java).apply {
            putExtra(EXTRA_MODE, childMode)
            if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_DEBUG_BYPASS_LOGIN, false)) {
                putExtra(EXTRA_DEBUG_BYPASS_LOGIN, true)
            }
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
        icon: Int,
        title: Int,
        value: String?,
        action: (() -> Unit)? = null,
        danger: Boolean = false,
    ): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(14.dp(), 0, 10.dp(), 0)
        addView(ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(if (danger) 0xFFD93025.toInt() else 0xFF23B39C.toInt())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, LinearLayout.LayoutParams(24.dp(), 24.dp()).apply {
            marginEnd = 12.dp()
        })
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

    private fun showUpgradeAccount() {
        applyNotificationSettingsChrome()
        val username = EditText(this).apply {
            setHint(R.string.xingdun_username_placeholder)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            textSize = 16f
            setTextColor(Color.BLACK)
            setHintTextColor(0xFFC7C7CC.toInt())
            setPadding(14.dp(), 0, 14.dp(), 0)
            background = null
        }
        val password = accountPasswordField(R.string.xingdun_new_password)
        val confirmation = accountPasswordField(R.string.xingdun_confirm_password)
        val errorView = TextView(this).apply {
            visibility = View.GONE
            textSize = 13f
            setTextColor(0xFFD93025.toInt())
            setPadding(14.dp(), 10.dp(), 14.dp(), 0)
        }
        val fields = listOf(username, password, confirmation)
        fields.forEach { it.doAfterTextChanged { errorView.visibility = View.GONE } }
        val confirm = Button(this).apply {
            setText(R.string.xingdun_confirm_account_upgrade)
            isAllCaps = false
            textSize = 16f
            setTextColor(0xFF20A88F.toInt())
            background = roundedDrawable(Color.WHITE, 22f)
            stateListAnimator = null
        }
        confirm.setOnClickListener {
            val name = username.text.toString().trim()
            val newPassword = password.text.toString()
            val confirmationValue = confirmation.text.toString()
            val validation = XingDunAccountInputValidator.username(name)
                ?: XingDunAccountInputValidator.password(newPassword, listOf(name))
                ?: XingDunAccountInputError.PASSWORD_MISMATCH.takeIf { newPassword != confirmationValue }
            if (validation != null) {
                errorView.setText(accountInputError(validation))
                errorView.visibility = View.VISIBLE
                return@setOnClickListener
            }
            fields.forEach { it.isEnabled = false }
            confirm.isEnabled = false
            confirm.alpha = 0.55f
            lifecycleScope.launch {
                runCatching {
                    XingDunSessionManager.apiClient().postEmpty(
                        requireSession(),
                        "auth/bindAccount",
                        mapOf(
                            "username" to name,
                            "password" to newPassword,
                            "confirm_password" to confirmationValue,
                        ),
                    )
                }.onSuccess {
                    setResult(RESULT_OK)
                    finish()
                }.onFailure {
                    fields.forEach { field -> field.isEnabled = true }
                    confirm.isEnabled = true
                    confirm.alpha = 1f
                    errorView.setText(R.string.xingdun_account_upgrade_failed)
                    errorView.visibility = View.VISIBLE
                }
            }
        }
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            addView(username, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()))
            addView(notificationDivider())
            addView(password, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()))
            addView(notificationDivider())
            addView(confirmation, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()))
        }, notificationSectionLayoutParams())
        addNotificationFooter(R.string.xingdun_device_account_upgrade_hint)
        content.addView(errorView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(confirm, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp()).apply {
            topMargin = 12.dp()
        })
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
        if (storageLoading) return
        applyStorageManagementChrome()
        scrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> storageTouchStartY = event.y
                MotionEvent.ACTION_UP -> {
                    if (scrollView.scrollY == 0 && event.y - storageTouchStartY > 120.dp()) {
                        content.removeAllViews()
                        showStorageManagement()
                    }
                }
            }
            false
        }
        storageLoading = true
        setBusy(true)
        lifecycleScope.launch {
            runCatching { XingDunStorageManager.usage(this@XingDunFeatureActivity) }
                .onSuccess { usage ->
                    storageLoading = false
                    setBusy(false)
                    renderStorageManagement(usage)
                }
                .onFailure { error ->
                    storageLoading = false
                    setBusy(false)
                    renderStorageLoadFailure(error)
                }
        }
    }

    private fun renderStorageLoadFailure(@Suppress("UNUSED_PARAMETER") error: Throwable) {
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
                setText(R.string.xingdun_storage_retry_hint)
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
        helpCustomerServiceLoading = false
        helpCustomerServiceRow = null
        scrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> helpCustomerServiceTouchStartY = event.y
                MotionEvent.ACTION_UP -> {
                    if (scrollView.scrollY == 0 && event.y - helpCustomerServiceTouchStartY > 72.dp()) {
                        helpCustomerServiceRow?.let(::loadHelpCustomerService)
                    }
                }
            }
            false
        }
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
            helpCustomerServiceRow = contactRow
            group.addView(contactRow)
            group.addView(helpDivider())
            loadHelpCustomerService(contactRow)
        }
        group.addView(helpNavigationRow(R.string.xingdun_feedback, icon = R.drawable.xingdun_ic_mine_feedback) {
            startChildMode(MODE_FEEDBACK)
        })
        group.addView(helpDivider())
        group.addView(helpNavigationRow(R.string.xingdun_report_violation, icon = R.drawable.xingdun_ic_mine_report) {
            startChildMode(MODE_REPORT_CREATE)
        })
        content.addView(group, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_help_support_footer)
            textSize = 13f
            setTextColor(0xFF8A8A8F.toInt())
            setPadding(14.dp(), 10.dp(), 14.dp(), 16.dp())
        })
    }

    private fun helpNavigationRow(
        title: Int,
        detail: Int? = null,
        icon: Int? = null,
        action: () -> Unit,
    ): LinearLayout =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 0, 10.dp(), 0)
            icon?.let {
                addView(ImageView(context).apply {
                    setImageResource(it)
                    imageTintList = android.content.res.ColorStateList.valueOf(0xFF28B7A2.toInt())
                }, LinearLayout.LayoutParams(22.dp(), 22.dp()).apply {
                    marginEnd = 12.dp()
                })
            }
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
        if (helpCustomerServiceLoading) return
        val session = XingDunSessionManager.currentSession() ?: run {
            configureHelpCustomerServiceRow(
                row,
                R.string.xingdun_contact_enterprise_support,
                R.string.xingdun_session_expired,
            ) {}
            return
        }
        helpCustomerServiceLoading = true
        configureHelpCustomerServiceRow(row, R.string.xingdun_contact_enterprise_support, R.string.xingdun_loading) {}
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    session, "cs/identity", emptyMap(), JsonObject::class.java
                ).also { identity ->
                    identity.string("company_code")?.let { returnedCompanyCode ->
                        check(returnedCompanyCode.equals(session.companyCode, ignoreCase = true))
                    }
                }
            }.onSuccess { identity ->
                helpCustomerServiceLoading = false
                val target = identity.string("official_cs_tim_user_id")?.takeIf {
                    identity.boolean("customer_service_enabled") && identity.boolean("ordinary_entry_enabled")
                }
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
                helpCustomerServiceLoading = false
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
        val configured = helpNavigationRow(
            title = title,
            detail = detail,
            icon = R.drawable.xingdun_ic_mine_customer_service,
            action = action,
        )
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
            R.drawable.xingdun_ic_permission_notification, R.string.xingdun_permission_notification,
            R.string.xingdun_permission_notification_summary, R.string.xingdun_permission_notification_usage,
            Manifest.permission.POST_NOTIFICATIONS, notification = true,
        )
        addRuntimePermissionCard(
            R.drawable.xingdun_ic_permission_camera, R.string.xingdun_permission_camera,
            R.string.xingdun_permission_camera_summary, R.string.xingdun_permission_camera_usage,
            Manifest.permission.CAMERA,
        )
        addPickerScopedPermissionCard(
            R.drawable.xingdun_ic_permission_photos, R.string.xingdun_permission_photos,
            R.string.xingdun_permission_photos_summary, R.string.xingdun_permission_photos_usage,
        )
        addRuntimePermissionCard(
            R.drawable.xingdun_ic_permission_microphone, R.string.xingdun_permission_microphone,
            R.string.xingdun_permission_microphone_summary, R.string.xingdun_permission_microphone_usage,
            Manifest.permission.RECORD_AUDIO,
        )
        addPickerScopedPermissionCard(
            R.drawable.xingdun_ic_permission_files, R.string.xingdun_permission_files,
            R.string.xingdun_permission_files_summary, R.string.xingdun_permission_files_usage,
        )
    }

    private fun addRuntimePermissionCard(
        icon: Int,
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
            !runtimePermissionRequired -> R.string.xingdun_permission_closed
            requested -> R.string.xingdun_permission_closed
            else -> R.string.xingdun_permission_not_requested
        }
        val statusTextColor: Int
        val statusBackgroundColor: Int
        when {
            granted -> {
                statusTextColor = 0xFF168F83.toInt()
                statusBackgroundColor = 0xFFDFF3EF.toInt()
            }
            !runtimePermissionRequired || requested -> {
                statusTextColor = 0xFFD93025.toInt()
                statusBackgroundColor = 0xFFFFE7E5.toInt()
            }
            else -> {
                statusTextColor = 0xFF168F83.toInt()
                statusBackgroundColor = 0xFFDFF3EF.toInt()
            }
        }
        val action = when {
            granted -> R.string.xingdun_permission_enabled
            shouldOpenSettings -> R.string.xingdun_permission_go_to_settings
            else -> R.string.xingdun_permission_allow_access
        }
        addPermissionCard(
            icon, title, summary, usage, status,
            statusTextColor, statusBackgroundColor, action, !granted,
        ) {
            if (shouldOpenSettings) {
                managedPermissionSettings.launch(
                    if (notification) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            } else {
                markPermissionRequested(permission)
                managedPermissionFeedbackTitle = title
                managedPermissionRequest.launch(permission)
            }
        }
    }

    private fun addPickerScopedPermissionCard(icon: Int, title: Int, summary: Int, usage: Int) {
        addPermissionCard(
            icon, title, summary, usage,
            R.string.xingdun_permission_picker_scoped,
            0xFF168F83.toInt(), 0xFFDFF3EF.toInt(),
            R.string.xingdun_permission_system_picker, false,
        ) {}
    }

    private fun addPermissionCard(
        icon: Int,
        title: Int,
        summary: Int,
        usage: Int,
        statusLabel: Int,
        statusTextColor: Int,
        statusBackgroundColor: Int,
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
                addView(ImageView(context).apply {
                    setImageResource(icon)
                    imageTintList = ColorStateList.valueOf(0xFF168F83.toInt())
                    background = roundedDrawable(0xFFDFF3EF.toInt(), 10f)
                    setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp())
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
                            setTextColor(statusTextColor)
                            background = roundedDrawable(statusBackgroundColor, 10f)
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
            }, LinearLayout.LayoutParams(84.dp(), 84.dp()))
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
                R.drawable.xingdun_ic_about_website,
                if (aboutUri == null) getString(R.string.xingdun_not_configured) else null,
                externalAction = aboutUri != null,
            ) {
                aboutUri?.let { startActivity(Intent(Intent.ACTION_VIEW, it)) }
            })
            addView(notificationDivider())
            addView(aboutValueRow(
                R.string.xingdun_check_updates,
                R.drawable.xingdun_ic_about_refresh,
                value = null,
                showsProgress = true,
            ) { checkAboutUpdates() }.also { aboutUpdateRow = it })
            enterprise?.platform?.siteRecordNumber?.trim()?.takeIf(String::isNotEmpty)?.let { record ->
                addView(notificationDivider())
                addView(aboutValueRow(R.string.xingdun_about_site_record_number, null, record))
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

    private fun aboutValueRow(
        label: Int,
        icon: Int?,
        value: String?,
        externalAction: Boolean = false,
        showsProgress: Boolean = false,
        action: (() -> Unit)? = null,
    ): View =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 0, 10.dp(), 0)
            icon?.let { iconResource ->
                addView(ImageView(context).apply {
                    setImageResource(iconResource)
                    imageTintList = ColorStateList.valueOf(0xFF168F83.toInt())
                }, LinearLayout.LayoutParams(21.dp(), 21.dp()).apply {
                    marginEnd = 12.dp()
                })
            }
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
            if (showsProgress) {
                addView(ProgressBar(context).apply {
                    visibility = View.GONE
                    aboutUpdateProgress = this
                }, LinearLayout.LayoutParams(20.dp(), 20.dp()).apply {
                    marginEnd = 8.dp()
                })
            }
            if (action != null && value.isNullOrBlank()) {
                addView(TextView(context).apply {
                    text = if (externalAction) "↗" else "›"
                    textSize = if (externalAction) 19f else 28f
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
        if (aboutUpdateProgress?.visibility == View.VISIBLE) return
        aboutUpdateProgress?.visibility = View.VISIBLE
        aboutUpdateRow?.isEnabled = false
        status.setText(R.string.xingdun_checking_version)
        lifecycleScope.launch {
            runCatching { XingDunSessionManager.checkVersion() }
                .onSuccess { result ->
                    aboutUpdateProgress?.visibility = View.GONE
                    aboutUpdateRow?.isEnabled = true
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
                    aboutUpdateProgress?.visibility = View.GONE
                    aboutUpdateRow?.isEnabled = true
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
        val url = debugUrl ?: session?.let { if (privacy) it.privacy.privacyUrl else it.privacy.userAgreementUrl }
        val uri = runCatching { Uri.parse(url.orEmpty()) }.getOrNull()
        if (uri == null || uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            showBundledLegalDocument(privacy)
            return
        }
        val remoteUrl = uri.toString()
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
                webView.loadUrl(remoteUrl)
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
        webView.loadUrl(remoteUrl)
    }

    private fun showBundledLegalDocument(privacy: Boolean) {
        val background = 0xFFF5F5F9.toInt()
        window.statusBarColor = background
        window.navigationBarColor = background
        headerBar.setBackgroundColor(background)
        scrollView.setBackgroundColor(background)
        content.setBackgroundColor(background)
        content.setPadding(20.dp(), 18.dp(), 20.dp(), 32.dp())

        val fullTitle = if (privacy) {
            R.string.xingdun_privacy_policy_full_title
        } else {
            R.string.xingdun_user_agreement_full_title
        }
        val summary = if (privacy) {
            R.string.xingdun_privacy_policy_summary
        } else {
            R.string.xingdun_user_agreement_summary
        }
        val sections = resources.getStringArray(
            if (privacy) R.array.xingdun_privacy_policy_sections else R.array.xingdun_user_agreement_sections
        )

        content.addView(TextView(this).apply {
            setText(fullTitle)
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.BLACK)
        })
        content.addView(TextView(this).apply {
            text = getString(
                R.string.xingdun_legal_version_effective_date,
                getString(R.string.xingdun_legal_current_version),
                getString(R.string.xingdun_legal_effective_date),
            )
            textSize = 13f
            setTextColor(0xFF8A8A8F.toInt())
            setPadding(0, 7.dp(), 0, 0)
        })
        content.addView(TextView(this).apply {
            setText(summary)
            textSize = 16f
            setTextColor(Color.BLACK)
            setLineSpacing(4.dp().toFloat(), 1f)
            setPadding(0, 16.dp(), 0, 10.dp())
        })

        sections.toList().chunked(2).forEach { section ->
            if (section.size != 2) return@forEach
            content.addView(View(this).apply {
                setBackgroundColor(0xFFE1E1E6.toInt())
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp()).apply {
                topMargin = 18.dp()
                bottomMargin = 16.dp()
            })
            content.addView(TextView(this).apply {
                text = section[0]
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.BLACK)
            })
            content.addView(TextView(this).apply {
                text = section[1]
                textSize = 15f
                setTextColor(0xFF66666B.toInt())
                setLineSpacing(5.dp().toFloat(), 1f)
                setPadding(0, 10.dp(), 0, 0)
            })
        }
        status.text = ""
    }

    private fun showFavorites() {
        if (!debugFavoritesFixtureEnabled && XingDunSessionManager.currentSession()?.features?.messageFavorite != true) {
            addMessage(R.string.xingdun_feature_unavailable)
            return
        }
        scrollView.setBackgroundColor(0xFFF5F6FA.toInt())
        favoriteListContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(favoriteListContainer)
        scrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> favoriteTouchStartY = event.y
                MotionEvent.ACTION_UP -> {
                    if (scrollView.scrollY == 0 && event.y - favoriteTouchStartY > 120.dp()) {
                        loadFavorites(reset = true)
                    }
                }
            }
            false
        }
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY <= oldScrollY || favoriteLoading || favoriteRecords.size >= favoriteTotal) {
                return@setOnScrollChangeListener
            }
            val child = scrollView.getChildAt(0) ?: return@setOnScrollChangeListener
            if (child.height - (scrollView.height + scrollY) <= 240.dp()) loadFavorites(reset = false)
        }
        loadFavorites(reset = true)
    }

    private fun loadFavorites(reset: Boolean) {
        if (favoriteLoading) return
        if (debugFavoritesFixtureEnabled) {
            loadFavoriteDebugFixture(reset)
            return
        }
        favoriteLoading = true
        val requestedPage = if (reset) 1 else favoritePage + 1
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    requireSession(),
                    "message/favorites",
                    mapOf("page" to requestedPage.toString(), "page_size" to FAVORITE_PAGE_SIZE.toString()),
                    JsonObject::class.java,
                )
            }.onSuccess { page ->
                favoriteLoading = false
                setBusy(false)
                val list = page.array("items").takeIf { !it.isEmpty } ?: page.array("list")
                if (reset) favoriteRecords.clear()
                list.forEach { element ->
                    val favorite = element.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@forEach
                    val id = favorite.int("favorite_id") ?: favorite.int("id") ?: return@forEach
                    if (favoriteRecords.none { (it.int("favorite_id") ?: it.int("id")) == id }) favoriteRecords += favorite
                }
                favoritePage = requestedPage
                favoriteTotal = page.int("total") ?: favoriteRecords.size
                renderFavorites()
            }.onFailure { error ->
                favoriteLoading = false
                setBusy(false)
                if (favoriteRecords.isEmpty()) renderFavorites(error.localizedMessage)
                else status.setText(R.string.xingdun_favorites_load_more_failed)
            }
        }
    }

    private fun loadFavoriteDebugFixture(reset: Boolean) {
        if (!reset && favoriteRecords.isNotEmpty()) return
        favoriteRecords.clear()
        val fixture = JsonParser.parseString(
            """
            [
              {
                "favorite_id": 9101,
                "conversation_id": "c2c_xd_demo",
                "favorited_at": "2026-08-28 14:20:00",
                "message": {
                  "message_id": "fav-text-1",
                  "sender": "x001",
                  "sender_nickname": "${getString(R.string.xingdun_favorite_preview_sender_primary)}",
                  "conversation_name": "${getString(R.string.xingdun_favorite_preview_conversation_project)}",
                  "message_type": "TEXT",
                  "text": "${getString(R.string.xingdun_favorite_preview_text)}"
                }
              },
              {
                "favorite_id": 9102,
                "conversation_id": "group_xd_demo",
                "favorited_at": "2026-08-27 09:32:00",
                "message": {
                  "message_id": "fav-audio-1",
                  "sender": "d001",
                  "sender_nickname": "${getString(R.string.xingdun_favorite_preview_sender_secondary)}",
                  "conversation_name": "${getString(R.string.xingdun_favorite_preview_conversation_team)}",
                  "message_type": "AUDIO",
                  "text": "",
                  "attachment": [{"MsgType":"TIMSoundElem","MsgContent":{"Second":8}}]
                }
              },
              {
                "favorite_id": 9103,
                "conversation_id": "group_xd_demo",
                "favorited_at": "2026-08-26 18:05:00",
                "message": {
                  "message_id": "fav-file-1",
                  "sender": "x001",
                  "sender_nickname": "${getString(R.string.xingdun_favorite_preview_sender_primary)}",
                  "conversation_name": "${getString(R.string.xingdun_favorite_preview_conversation_team)}",
                  "message_type": "FILE",
                  "text": "${getString(R.string.xingdun_favorite_preview_file)}"
                }
              }
            ]
            """.trimIndent(),
        ).asJsonArray
        fixture.forEach { favoriteRecords += it.asJsonObject }
        favoritePage = 1
        favoriteTotal = favoriteRecords.size
        favoriteLoading = false
        setBusy(false)
        renderFavorites()
    }

    private fun renderFavorites(errorMessage: String? = null) {
        val container = favoriteListContainer ?: return
        container.removeAllViews()
        when {
            errorMessage != null && favoriteRecords.isEmpty() -> {
                container.addView(favoriteEmptyState(
                    R.string.xingdun_favorites_load_failed,
                    R.string.xingdun_favorites_retry_hint,
                    R.string.xingdun_retry,
                ) { loadFavorites(reset = true) })
            }
            favoriteRecords.isEmpty() -> {
                container.addView(favoriteEmptyState(
                    R.string.xingdun_favorites_empty_title,
                    R.string.xingdun_favorites_empty,
                ))
            }
            else -> {
                favoriteRecords.forEach { favorite -> container.addView(favoriteCard(favorite)) }
            }
        }
    }

    private fun favoriteEmptyState(
        titleRes: Int,
        messageRes: Int,
        actionRes: Int? = null,
        action: (() -> Unit)? = null,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(24.dp(), 72.dp(), 24.dp(), 36.dp())
        addView(ImageView(context).apply {
            setImageResource(R.drawable.xingdun_ic_mine_favorite)
            imageTintList = ColorStateList.valueOf(0xFF23B39C.toInt())
        }, LinearLayout.LayoutParams(48.dp(), 48.dp()))
        addView(TextView(context).apply {
            setText(titleRes)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(0, 18.dp(), 0, 0)
        })
        addView(TextView(context).apply {
            setText(messageRes)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF7A7F87.toInt())
            setPadding(0, 8.dp(), 0, 0)
        })
        if (actionRes != null && action != null) addView(actionButton(actionRes, action))
    }

    private fun favoriteCard(favorite: JsonObject): View {
        val snapshot = favorite.getAsJsonObject("message") ?: favorite
        val favoriteID = favorite.int("favorite_id") ?: favorite.int("id")
        val senderID = snapshot.string("sender").orEmpty()
        val senderName = snapshot.string("sender_nickname") ?: senderID.ifBlank { getString(R.string.xingdun_message) }
        val conversationName = snapshot.string("conversation_name")
            ?: favorite.string("conversation_id")
            ?: getString(R.string.xingdun_favorite_unknown_conversation)
        val messageType = snapshot.string("message_type").orEmpty().uppercase(Locale.ROOT)
        val previewURL = favoriteMediaURL(snapshot, messageType, preview = true)
        val playbackURL = favoriteMediaURL(snapshot, messageType, preview = false)
        val audioDuration = if (messageType == "AUDIO") favoriteAudioDuration(snapshot) else null

        return FrameLayout(this).apply {
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(14.dp(), 14.dp(), 10.dp(), 14.dp())
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            val avatar = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFFE2F4F0.toInt())
                }
                clipToOutline = true
                setImageResource(R.drawable.xingdun_ic_user)
            }
            row.addView(avatar, LinearLayout.LayoutParams(42.dp(), 42.dp()).apply { marginEnd = 12.dp() })

            val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = senderName
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.BLACK)
                    maxLines = 1
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(context).apply {
                    text = localizedDisplayDate(favorite.string("favorited_at") ?: snapshot.string("sent_at"))
                    textSize = 11f
                    setTextColor(0xFF9A9EA5.toInt())
                    maxLines = 1
                    setPadding(6.dp(), 0, 34.dp(), 0)
                })
            }
            body.addView(header)
            body.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 5.dp(), 0, 0)
                addView(ImageView(context).apply {
                    setImageResource(favoriteTypeIcon(messageType))
                    imageTintList = ColorStateList.valueOf(0xFF23B39C.toInt())
                }, LinearLayout.LayoutParams(14.dp(), 14.dp()).apply { marginEnd = 6.dp() })
                addView(TextView(context).apply {
                    text = conversationName
                    textSize = 12f
                    setTextColor(0xFF66716F.toInt())
                    maxLines = 1
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })

            if ((messageType == "PICTURE" || messageType == "VIDEO") && previewURL != null) {
                val thumbnail = FrameLayout(context).apply {
                    background = roundedDrawable(0xFFF0F2F5.toInt(), 10f)
                    clipToOutline = true
                    val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                    addView(image, FrameLayout.LayoutParams(72.dp(), 72.dp()))
                    ImageLoader.load(
                        this@XingDunFeatureActivity,
                        image,
                        previewURL,
                        if (messageType == "VIDEO") R.drawable.xingdun_ic_storage_video else R.drawable.xingdun_ic_storage_image,
                    )
                    if (messageType == "VIDEO") addView(TextView(context).apply {
                        text = "▶"
                        textSize = 22f
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                    }, FrameLayout.LayoutParams(72.dp(), 72.dp()))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { openFavoriteMedia(messageType, playbackURL ?: previewURL) }
                }
                body.addView(thumbnail, LinearLayout.LayoutParams(72.dp(), 72.dp()).apply { topMargin = 8.dp() })
            } else if (messageType == "AUDIO") {
                val duration = audioDuration ?: 1
                val icon = TextView(context).apply {
                    text = "▶"
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setTextColor(0xFF159A86.toInt())
                }
                val durationView = TextView(context).apply {
                    text = "$duration″"
                    textSize = 14f
                    setTextColor(0xFF159A86.toInt())
                }
                val audioBar = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = roundedDrawable(0x1F23B39C, 10f)
                    setPadding(10.dp(), 0, 10.dp(), 0)
                    addView(icon, LinearLayout.LayoutParams(20.dp(), 40.dp()).apply { marginEnd = 6.dp() })
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        repeat(minOf(18, duration + 5)) { index ->
                            addView(View(context).apply {
                                background = roundedDrawable(0xFF159A86.toInt(), 1f)
                            }, LinearLayout.LayoutParams(2.dp(), if (index % 3 == 0) 13.dp() else 7.dp()).apply {
                                marginEnd = 2.dp()
                            })
                        }
                    }, LinearLayout.LayoutParams(0, 40.dp(), 1f))
                    addView(durationView)
                    isClickable = playbackURL != null
                    isFocusable = playbackURL != null
                    contentDescription = getString(R.string.xingdun_favorite_audio_play_hint)
                }
                val visual = FavoriteAudioVisual(icon, durationView, duration)
                playbackURL?.let { url -> audioBar.setOnClickListener { toggleFavoriteAudio(url, visual) } }
                body.addView(audioBar, LinearLayout.LayoutParams(minOf(220.dp(), (92 + maxOf(0, duration - 2) * 7).dp()), 40.dp()).apply {
                    topMargin = 8.dp()
                })
            } else {
                body.addView(TextView(context).apply {
                    text = favoriteSummary(snapshot, messageType)
                    textSize = 14f
                    setTextColor(Color.BLACK)
                    maxLines = 3
                    setPadding(0, 8.dp(), 0, 0)
                })
            }
            row.addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(TextView(context).apply {
                text = "⋯"
                textSize = 22f
                gravity = Gravity.CENTER
                contentDescription = getString(R.string.xingdun_favorite_more_actions)
                isClickable = favoriteID != null
                isFocusable = favoriteID != null
                setTextColor(0xFF69716F.toInt())
                setOnClickListener { anchor -> favoriteID?.let { showFavoriteActions(anchor, it, snapshot.string("message_id")) } }
            }, FrameLayout.LayoutParams(38.dp(), 38.dp(), Gravity.TOP or Gravity.END))
            if (senderID.isNotBlank()) loadFavoriteAvatar(senderID, avatar)
        }.also { card ->
            card.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12.dp()
            }
        }
    }

    private fun loadFavoriteAvatar(senderID: String, avatar: ImageView) {
        ContactStore.shared.getContactInfo(
            listOf(senderID),
            object : GetContactInfoCompletionHandler {
                override fun onSuccess(contactInfoList: List<ContactInfo>) {
                    val url = contactInfoList.firstOrNull()?.avatarURL?.takeIf(String::isNotBlank) ?: return
                    avatar.post { ImageLoader.load(this@XingDunFeatureActivity, avatar, url, R.drawable.xingdun_ic_user) }
                }

                override fun onFailure(code: Int, desc: String) = Unit
            },
        )
    }

    private fun favoriteSummary(snapshot: JsonObject, messageType: String): String =
        snapshot.string("text").orEmpty().ifBlank {
            getString(
                when (messageType) {
                    "PICTURE" -> R.string.xingdun_favorite_picture
                    "AUDIO" -> R.string.xingdun_favorite_audio
                    "VIDEO" -> R.string.xingdun_favorite_video
                    "FILE" -> R.string.xingdun_favorite_file
                    "LOCATION" -> R.string.xingdun_favorite_location
                    "CUSTOM" -> R.string.xingdun_favorite_unsupported
                    else -> R.string.xingdun_favorite_message
                },
            )
        }

    private fun localizedDisplayDate(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return ""
        val date = parseDisplayDate(value)
        return date?.let {
            val locale = resources.configuration.locales[0] ?: Locale.getDefault()
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(it)
        } ?: value
    }

    private fun workspaceDisplayDate(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return ""
        val date = parseDisplayDate(value) ?: return value
        val locale = resources.configuration.locales[0] ?: Locale.getDefault()
        val pattern = if (locale.language == Locale.CHINESE.language) "M/d HH:mm" else "MMM d, HH:mm"
        return SimpleDateFormat(pattern, locale).format(date)
    }

    private fun parseDisplayDate(value: String): Date? = value.toLongOrNull()?.let { number ->
            Date(if (number > 10_000_000_000L) number else number * 1_000L)
        } ?: listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
        ).firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).parse(value) }.getOrNull()
        }

    private fun favoriteAudioDuration(snapshot: JsonObject): Int? {
        val attachment = snapshot.get("attachment") ?: return null
        return listOf("Second", "Duration", "duration")
            .firstNotNullOfOrNull { key -> attachment.firstDouble(key) }
            ?.toInt()
            ?.coerceAtLeast(1)
    }

    private fun favoriteTypeIcon(messageType: String): Int = when (messageType) {
        "PICTURE" -> R.drawable.xingdun_ic_storage_image
        "AUDIO" -> R.drawable.xingdun_ic_storage_audio
        "VIDEO" -> R.drawable.xingdun_ic_storage_video
        "FILE" -> R.drawable.xingdun_ic_storage_file
        else -> R.drawable.xingdun_ic_mine_document
    }

    private fun favoriteMediaURL(snapshot: JsonObject, messageType: String, preview: Boolean): String? {
        val attachment = snapshot.get("attachment") ?: return null
        val keys = when (messageType) {
            "PICTURE" -> listOf("ThumbUrl", "ThumbURL", "thumbUrl", "URL", "Url", "url")
            "VIDEO" -> if (preview) {
                listOf("ThumbUrl", "ThumbURL", "thumbUrl", "CoverUrl", "coverUrl", "VideoUrl", "VideoURL")
            } else {
                listOf("VideoUrl", "VideoURL", "videoUrl", "Url", "URL")
            }
            "AUDIO" -> listOf("Url", "URL", "url", "SoundUrl", "SoundURL", "DownloadUrl", "DownloadURL")
            else -> emptyList()
        }
        return keys.firstNotNullOfOrNull { key -> attachment.firstString(key) }
            ?.trim()
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }

    private fun JsonElement.firstString(key: String): String? = when {
        isJsonObject -> {
            val objectValue = asJsonObject
            objectValue.get(key)?.takeUnless(JsonElement::isJsonNull)?.let { direct ->
                runCatching { direct.asString }.getOrNull()
            } ?: objectValue.entrySet().firstNotNullOfOrNull { (_, nested) -> nested.firstString(key) }
        }
        isJsonArray -> asJsonArray.firstNotNullOfOrNull { it.firstString(key) }
        else -> null
    }

    private fun JsonElement.firstDouble(key: String): Double? = when {
        isJsonObject -> {
            val objectValue = asJsonObject
            objectValue.get(key)?.takeUnless(JsonElement::isJsonNull)?.let { direct ->
                runCatching { direct.asDouble }.getOrNull()
            } ?: objectValue.entrySet().firstNotNullOfOrNull { (_, nested) -> nested.firstDouble(key) }
        }
        isJsonArray -> asJsonArray.firstNotNullOfOrNull { it.firstDouble(key) }
        else -> null
    }

    private fun showFavoriteActions(anchor: View, favoriteID: Int, messageID: String?) {
        PopupMenu(this, anchor).apply {
            menu.add(R.string.xingdun_remove_favorite)
            setOnMenuItemClickListener {
                removeFavorite(favoriteID, messageID)
                true
            }
            show()
        }
    }

    private fun openFavoriteMedia(messageType: String, url: String) {
        if (messageType == "AUDIO") return
        val mediaType = if (messageType == "VIDEO") 1 else 0
        val element = ImageElement(
            data = url,
            type = mediaType,
            videoData = if (mediaType == 1) url else null,
            stableId = "favorite-${url.hashCode()}",
        )
        val session = ImageViewer.view(
            listOf(element),
            onEventTriggered = object : EventHandler {
                override fun onEvent(eventData: Map<String, Any>, callback: (Any?) -> Unit) {
                    when (eventData.keys.firstOrNull()) {
                        ImageViewer.EVENT_SAVE_MEDIA, ImageViewer.EVENT_DOWNLOAD_VIDEO -> {
                            lifecycleScope.launch(Dispatchers.IO) {
                                callback(downloadFavoriteMedia(url, mediaType))
                            }
                        }
                        else -> callback(null)
                    }
                }
            },
        )
        if (session == null) status.setText(R.string.xingdun_favorite_media_open_failed)
    }

    private fun toggleFavoriteAudio(url: String, view: FavoriteAudioVisual) {
        if (favoriteAudioURL == url && favoriteAudioPlayer.isPlaying()) {
            favoriteAudioPlayer.pause()
            updateFavoriteAudioVisual(view, false)
            return
        }
        if (favoriteAudioURL == url && favoriteAudioPlayer.isPaused()) {
            favoriteAudioPlayer.resume()
            updateFavoriteAudioVisual(view, true)
            return
        }
        favoriteAudioView?.let { updateFavoriteAudioVisual(it, false) }
        favoriteAudioURL = url
        favoriteAudioView = view
        favoriteAudioPlayer.setListener(object : AudioPlayerListener {
            override fun onPlay() = updateFavoriteAudioView(view, url, true)
            override fun onResume() = updateFavoriteAudioView(view, url, true)
            override fun onPause() = updateFavoriteAudioView(view, url, false)
            override fun onCompletion() = updateFavoriteAudioView(view, url, false)
            override fun onError(errorMessage: String) {
                updateFavoriteAudioView(view, url, false)
                status.setText(R.string.xingdun_favorite_media_open_failed)
            }
        })
        favoriteAudioPlayer.play(url)
    }

    private fun updateFavoriteAudioView(view: FavoriteAudioVisual, url: String, playing: Boolean) {
        view.icon.post {
            if (favoriteAudioURL != url) return@post
            updateFavoriteAudioVisual(view, playing)
            if (!playing) favoriteAudioURL = null
        }
    }

    private fun updateFavoriteAudioVisual(view: FavoriteAudioVisual, playing: Boolean) {
        view.icon.text = if (playing) "Ⅱ" else "▶"
        view.duration.text = "${view.seconds}″"
    }

    private fun downloadFavoriteMedia(url: String, mediaType: Int): String? = runCatching {
        val extension = if (mediaType == 1) ".mp4" else ".jpg"
        val target = File(cacheDir, "xingdun-favorite-${url.hashCode()}$extension")
        if (target.exists() && target.length() > 0L) return@runCatching target.absolutePath
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.inputStream.use { input -> FileOutputStream(target).use(input::copyTo) }
        connection.disconnect()
        target.absolutePath
    }.getOrNull()

    private fun removeFavorite(favoriteID: Int, messageID: String?) {
        if (debugFavoritesFixtureEnabled) {
            favoriteRecords.removeAll { (it.int("favorite_id") ?: it.int("id")) == favoriteID }
            favoriteTotal = maxOf(0, favoriteTotal - 1)
            favoritePage = XingDunMessageFavoritePolicy.pageAfterRemoval(favoritePage)
            renderFavorites()
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().deleteEmpty(
                    requireSession(), "message/favorite", mapOf("favorite_id" to favoriteID)
                )
            }.onSuccess {
                favoriteRecords.removeAll { (it.int("favorite_id") ?: it.int("id")) == favoriteID }
                favoriteTotal = maxOf(0, favoriteTotal - 1)
                favoritePage = XingDunMessageFavoritePolicy.pageAfterRemoval(favoritePage)
                messageID?.takeIf(String::isNotBlank)?.let(XingDunMessageFavoriteRepository::noteRemoved)
                setBusy(false)
                renderFavorites()
            }.onFailure(::showFailure)
        }
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
        qrScanner.launch(Intent(this, XingDunQRCodeScannerActivity::class.java))
    }

    private fun handleScannedPayload(payload: String) {
        val route = runCatching { XingDunQRCodeParser.parse(payload) }.getOrElse {
            status.setText(R.string.xingdun_qr_unrecognized)
            return
        }
        when (route) {
            is XingDunQRCodeRoute.User -> {
                // Match iOS: resolve a scanned TIM user ID through the tenant-scoped
                // business search before offering any friend action. This is required
                // when multiple enterprises share one Tencent IM application.
                start(this, MODE_FRIEND_SEARCH, route.userID)
                finish()
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

    private fun showPersonalQRCode() {
        applyPersonalQRCodeChrome()
        if (debugPersonalQRCodeFixtureEnabled) {
            renderDebugPersonalQRCode()
            return
        }
        val session = runCatching { requireSession() }.getOrElse {
            showFailure(it)
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                val profileResult = runCatching {
                    XingDunSessionManager.apiClient().get<JsonObject>(
                        session, "user/profile", emptyMap(), JsonObject::class.java
                    )
                }
                val profile = profileResult.getOrDefault(JsonObject())
                if (profileResult.isSuccess) {
                    profile.string("tim_user_id")?.let { returnedUserID ->
                        check(returnedUserID == session.timUserId)
                    }
                    profile.string("company_code")?.let { returnedCompanyCode ->
                        check(returnedCompanyCode.equals(session.companyCode, ignoreCase = true))
                    }
                }
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

    private fun renderDebugPersonalQRCode() {
        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    XingDunPersonalQRCodeArtifactStore(this@XingDunFeatureActivity).artifact(
                        tenantKey = "debug-company|debug-company-id|debug-sdk-app-id",
                        userID = "xd_debug_user",
                        displayName = "d001",
                        accountID = "d001",
                        avatarURL = null,
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
        (headerBar.getChildAt(0) as? TextView)?.apply {
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.BLACK)
        }
        (headerBar.getChildAt(1) as? TextView)?.setTextColor(Color.WHITE)
    }

    private fun renderPersonalQRCode(artifact: XingDunPersonalQRCodeArtifact) {
        content.removeAllViews()
        content.gravity = Gravity.CENTER_HORIZONTAL
        setPersonalQRCodeSaving(false)
        val card = ImageView(this).apply {
            setImageBitmap(artifact.image)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.xingdun_personal_qr_image_description)
            background = roundedDrawable(Color.WHITE, 16f)
            clipToOutline = true
            setOnLongClickListener {
                showPersonalQRCodeActions(this, artifact)
                true
            }
        }
        content.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 4.dp()
            marginStart = 30.dp()
            marginEnd = 30.dp()
        })
        val saveButton = actionButton(R.string.xingdun_personal_qr_save_image) {
            savePersonalQRCode(artifact)
        }.apply {
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF28B7A2.toInt())
            compoundDrawablePadding = 8.dp()
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.xingdun_ic_save_image, 0, 0, 0)
        }
        personalQRCodeSaveButton = saveButton
        content.addView(saveButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp()).apply {
            topMargin = 16.dp()
            marginStart = 30.dp()
            marginEnd = 30.dp()
        })
    }

    private fun showPersonalQRCodeUnavailable() {
        personalQRCodeSaveButton = null
        personalQRCodeSaving = false
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
        if (personalQRCodeSaving) return
        setPersonalQRCodeSaving(true)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingPersonalQRCode = artifact
            personalQRCodeStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        performPersonalQRCodeSave(artifact)
    }

    private fun performPersonalQRCodeSave(artifact: XingDunPersonalQRCodeArtifact) {
        lifecycleScope.launch {
            setBusy(true)
            runCatching {
                saveBitmapToPictures(artifact.image, "xingdun_personal_qr_${System.currentTimeMillis()}.png")
            }.onSuccess {
                setPersonalQRCodeSaving(false)
                setBusy(false)
                status.setText(R.string.xingdun_personal_qr_saved)
            }.onFailure {
                setPersonalQRCodeSaving(false)
                setBusy(false)
                status.setText(R.string.xingdun_personal_qr_save_failed)
            }
        }
    }

    private fun setPersonalQRCodeSaving(saving: Boolean) {
        personalQRCodeSaving = saving
        personalQRCodeSaveButton?.apply {
            isEnabled = !saving
            setText(if (saving) R.string.xingdun_personal_qr_saving else R.string.xingdun_personal_qr_save_image)
            alpha = if (saving) 0.48f else 1f
        }
    }

    private fun showPersonalQRCodeActions(anchor: View, artifact: XingDunPersonalQRCodeArtifact) {
        PopupMenu(this, anchor).apply {
            menu.add(getString(R.string.xingdun_personal_qr_save_image)).setOnMenuItemClickListener {
                savePersonalQRCode(artifact)
                true
            }
            menu.add(getString(R.string.xingdun_share)).setOnMenuItemClickListener {
                sharePersonalQRCode(artifact)
                true
            }
            show()
        }
    }

    private suspend fun saveBitmapToPictures(bitmap: Bitmap, name: String) = withContext(Dispatchers.IO) {
        XingDunImageDelivery.saveToPictures(this@XingDunFeatureActivity, bitmap, name)
    }

    private fun sharePersonalQRCode(artifact: XingDunPersonalQRCodeArtifact) {
        if (personalQRCodeSaving) return
        runCatching {
            val uri = XingDunImageDelivery.shareUri(this, artifact.image, "personal-qr.png")
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
        setBusy(false)
        status.setText(attachmentFailureMessage(error))
    }

    private fun attachmentFailureMessage(error: Throwable): Int =
        when ((error as? XingDunAttachmentException)?.reason) {
            XingDunAttachmentError.TOO_MANY -> R.string.xingdun_attachment_too_many
            XingDunAttachmentError.INVALID_TYPE -> R.string.xingdun_attachment_invalid_type
            XingDunAttachmentError.TOO_LARGE -> R.string.xingdun_attachment_too_large
            XingDunAttachmentError.EMPTY -> R.string.xingdun_attachment_empty
            else -> R.string.xingdun_attachment_unreadable
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
        MODE_CUSTOMER_SERVICE -> R.string.xingdun_customer_service_dashboard
        MODE_CUSTOMER_SERVICE_GROUP -> R.string.xingdun_customer_service_group_management
        MODE_FRIEND_SEARCH -> R.string.xingdun_add_friend_title
        MODE_INVITE -> R.string.xingdun_share_poster
        MODE_FEEDBACK -> R.string.xingdun_feedback
        MODE_VERSION -> R.string.xingdun_version
        MODE_REPORTS -> R.string.xingdun_reports
        MODE_REPORT_DETAIL -> R.string.xingdun_report_detail
        MODE_REPORT_CREATE -> R.string.xingdun_report
        MODE_PERSONAL_QR -> R.string.xingdun_personal_qr
        MODE_QR_SCANNER -> R.string.xingdun_scan_qr
        MODE_ACCOUNT_SECURITY -> R.string.xingdun_account_security
        MODE_UPGRADE_ACCOUNT -> R.string.xingdun_upgrade_device_account
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

    private fun childSelectedTab(): String = when (mode) {
        MODE_WORKSPACE_LIST,
        MODE_WORKSPACE_PENDING,
        MODE_WORKSPACE_DETAIL,
        MODE_WORKSPACE_CREATE,
        MODE_CUSTOMER_SERVICE,
        MODE_CUSTOMER_SERVICE_GROUP -> MainActivity.TAB_WORKSPACE
        else -> MainActivity.TAB_PROFILE
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_DEBUG_BYPASS_LOGIN = "debug_bypass_login"
        private const val EXTRA_DEBUG_REPORT_FIXTURE = "debug_report_fixture"
        private const val EXTRA_DEBUG_PERSONAL_QR_FIXTURE = "debug_personal_qr_fixture"
        private const val EXTRA_DEBUG_INVITE_POSTER_FIXTURE = "debug_invite_poster_fixture"
        private const val EXTRA_DEBUG_FAVORITES_FIXTURE = "debug_favorites_fixture"
        private const val EXTRA_DEBUG_ACCOUNT_SECURITY_FIXTURE = "debug_account_security_fixture"
        private const val EXTRA_DEBUG_WORKSPACE_DETAIL_FIXTURE = "debug_workspace_detail_fixture"
        private const val EXTRA_DEBUG_CUSTOMER_SERVICE_FIXTURE = "debug_customer_service_fixture"
        private const val EXTRA_DEBUG_LEGAL_URL = "debug_legal_url"
        private const val EXTRA_INITIAL_REPORT_JSON = "initial_report_json"
        private const val EXTRA_ITEM_ID = "item_id"
        private const val EXTRA_TARGET_ID = "target_id"
        private const val EXTRA_TARGET_TYPE = "target_type"
        private const val EXTRA_TARGET_DISPLAY_NAME = "target_display_name"
        private const val EXTRA_TARGET_DISPLAY_ID = "target_display_id"
        const val MODE_WORKSPACE_LIST = "workspace_list"
        const val MODE_WORKSPACE_PENDING = "workspace_pending"
        const val MODE_WORKSPACE_DETAIL = "workspace_detail"
        const val MODE_WORKSPACE_CREATE = "workspace_create"
        const val MODE_CUSTOMER_SERVICE = "customer_service"
        const val MODE_CUSTOMER_SERVICE_GROUP = "customer_service_group"
        const val MODE_FRIEND_SEARCH = "friend_search"
        const val MODE_INVITE = "invite"
        const val MODE_FEEDBACK = "feedback"
        const val MODE_VERSION = "version"
        const val MODE_REPORTS = "reports"
        const val MODE_REPORT_DETAIL = "report_detail"
        const val MODE_REPORT_CREATE = "report_create"
        const val MODE_PERSONAL_QR = "personal_qr"
        const val MODE_QR_SCANNER = "qr_scanner"
        const val MODE_ACCOUNT_SECURITY = "account_security"
        const val MODE_UPGRADE_ACCOUNT = "upgrade_account"
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
        private const val FAVORITE_PAGE_SIZE = 20
        private const val FRIEND_APPLICATION_TAG = "xingdun_friend_application"

        fun start(context: Context, mode: String, itemId: Int = 0) {
            context.startActivity(Intent(context, XingDunFeatureActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode)
                if (itemId > 0) putExtra(EXTRA_ITEM_ID, itemId)
                if (BuildConfig.DEBUG && context is XingDunFeatureActivity &&
                    context.intent.getBooleanExtra(EXTRA_DEBUG_BYPASS_LOGIN, false)
                ) putExtra(EXTRA_DEBUG_BYPASS_LOGIN, true)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        fun start(context: Context, mode: String, targetID: String) {
            context.startActivity(Intent(context, XingDunFeatureActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_TARGET_ID, targetID)
                if (BuildConfig.DEBUG && context is XingDunFeatureActivity &&
                    context.intent.getBooleanExtra(EXTRA_DEBUG_BYPASS_LOGIN, false)
                ) putExtra(EXTRA_DEBUG_BYPASS_LOGIN, true)
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        fun startReport(
            context: Context,
            targetType: String,
            targetID: String,
            displayName: String? = null,
            displayID: String? = null,
        ) {
            context.startActivity(Intent(context, XingDunFeatureActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_REPORT_CREATE)
                putExtra(EXTRA_TARGET_TYPE, targetType)
                putExtra(EXTRA_TARGET_ID, targetID)
                displayName?.takeIf(String::isNotBlank)?.let { putExtra(EXTRA_TARGET_DISPLAY_NAME, it) }
                displayID?.takeIf(String::isNotBlank)?.let { putExtra(EXTRA_TARGET_DISPLAY_ID, it) }
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
