package io.trtc.tuikit.chat.demo.xingdun.features

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunGroupDetail
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cross-platform group QR page aligned with XingDunGroupQRCodeView on iOS. */
open class XingDunGroupQRCodeActivity : BaseActivity() {

    override val requiresLogin: Boolean
        get() = !intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false)

    private val themeStore by lazy { ThemeStore.shared(this) }
    private val groupID by lazy { intent.getStringExtra(EXTRA_GROUP_ID).orEmpty() }
    private val isDebugPreview by lazy { intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false) }
    private var activityScope: CoroutineScope? = null

    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var back: ImageView
    private lateinit var more: ImageView
    private lateinit var badge: FrameLayout
    private lateinit var divider: View
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private var detail: XingDunGroupDetail? = null
    private var qrBitmap: Bitmap? = null
    private var isSaving = false
    private var pendingSave: Bitmap? = null

    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val bitmap = pendingSave
        pendingSave = null
        if (granted && bitmap != null) saveBitmap(bitmap) else showPermissionSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        if (groupID.isBlank()) {
            Toast.makeText(this, R.string.xingdun_invalid_group, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContentView(R.layout.xingdun_activity_group_info)
        bindViews()
        configureHeader()
        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        activityScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyHeaderTheme(state.currentTheme.tokens.color)
                val currentDetail = detail
                val currentBitmap = qrBitmap
                if (currentDetail != null && currentBitmap != null) render(currentDetail, currentBitmap)
            }
        }
        if (isDebugPreview) {
            refresh.isEnabled = false
            showFixture()
        } else {
            showLoading()
            load()
        }
    }

    override fun onDestroy() {
        activityScope?.cancel()
        activityScope = null
        super.onDestroy()
    }

    private fun bindViews() {
        root = findViewById(R.id.xingdun_groupInfoRoot)
        header = findViewById(R.id.demo_chatHeaderContainer)
        titleView = findViewById(R.id.demo_tvChatTitle)
        back = findViewById(R.id.demo_btnBack)
        more = findViewById(R.id.demo_btnMore)
        badge = findViewById(R.id.demo_badgeContainer)
        divider = findViewById(R.id.demo_headerDivider)
        refresh = findViewById(R.id.xingdun_groupInfoRefresh)
        scroll = findViewById(R.id.xingdun_groupInfoScroll)
        content = findViewById(R.id.xingdun_groupInfoContent)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = bars.top)
            scroll.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun configureHeader() {
        titleView.setText(R.string.xingdun_group_qr_title)
        findViewById<LinearLayout>(R.id.demo_leftContainer).setOnClickListener { finish() }
        more.visibility = View.GONE
        badge.visibility = View.GONE
        refresh.setColorSchemeColors(BRAND)
        refresh.setOnRefreshListener { load() }
        applyHeaderTheme(colors())
    }

    private fun showFixture() {
        val fixture = XingDunGroupDetail(
            groupId = groupID,
            displayGroupId = "100284",
            name = getString(R.string.xingdun_group_info_preview_name),
            avatar = null,
            intro = getString(R.string.xingdun_group_info_preview_intro),
        )
        createAndRender(fixture)
    }

    private fun load() {
        val scope = activityScope ?: return
        scope.launch {
            val result = runCatching {
                val session = XingDunSessionManager.currentSession()
                    ?: error(getString(R.string.xingdun_session_expired))
                XingDunSessionManager.apiClient().get<XingDunGroupDetail>(
                    session,
                    "team/detail",
                    mapOf("team_id" to groupID),
                    XingDunGroupDetail::class.java,
                )
            }
            refresh.isRefreshing = false
            result.onSuccess(::createAndRender).onFailure(::showLoadError)
        }
    }

    private fun createAndRender(value: XingDunGroupDetail) {
        val scope = activityScope ?: return
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    val payload = XingDunGroupQRCodePayload.make(groupID)
                    require(payload.isNotBlank())
                    BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE)
                }
            }
            result.onSuccess { bitmap ->
                detail = value
                qrBitmap = bitmap
                render(value, bitmap)
            }.onFailure(::showLoadError)
        }
    }

    private fun showLoading() {
        content.removeAllViews()
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.addView(ProgressBar(this), LinearLayout.LayoutParams(48.dp(), 48.dp()).apply { topMargin = 80.dp() })
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_group_qr_loading)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(colors().textColorSecondary)
            gravity = Gravity.CENTER
            setPadding(0, 16.dp(), 0, 0)
        })
    }

    private fun showLoadError(error: Throwable) {
        val current = colors()
        content.removeAllViews()
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.addView(TextView(this).apply {
            setText(R.string.xingdun_group_qr_load_failed)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(current.textColorPrimary)
            gravity = Gravity.CENTER
        }, matchWrap().apply { topMargin = 72.dp() })
        content.addView(TextView(this).apply {
            text = error.localizedMessage?.takeIf(String::isNotBlank)
                ?: getString(R.string.xingdun_group_qr_load_failed_message)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(current.textColorSecondary)
            gravity = Gravity.CENTER
            setPadding(12.dp(), 12.dp(), 12.dp(), 18.dp())
        })
        content.addView(Button(this).apply {
            setText(R.string.xingdun_group_info_retry)
            setOnClickListener {
                showLoading()
                load()
            }
        })
    }

    private fun render(value: XingDunGroupDetail, bitmap: Bitmap) {
        val current = colors()
        content.removeAllViews()
        content.gravity = Gravity.NO_GRAVITY
        content.setBackgroundColor(current.bgColorTopBar)

        val qrCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(20.dp(), 24.dp(), 20.dp(), 24.dp())
            background = rounded(current.bgColorOperate, 18f)
        }
        qrCard.addView(Avatar(this).apply {
            setSize(Avatar.AvatarSize.L)
            setContent(Avatar.AvatarContent.Image(value.avatar, value.name))
        }, LinearLayout.LayoutParams(72.dp(), 72.dp()))
        qrCard.addView(TextView(this).apply {
            text = value.name.ifBlank { getString(R.string.xingdun_not_set) }
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(current.textColorPrimary)
            setPadding(0, 12.dp(), 0, 16.dp())
        }, matchWrap())
        qrCard.addView(ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
            contentDescription = getString(R.string.xingdun_group_qr_image_description)
        }, LinearLayout.LayoutParams(260.dp(), 260.dp()))
        qrCard.addView(TextView(this).apply {
            text = getString(
                R.string.xingdun_group_qr_number,
                value.publicGroupId ?: getString(R.string.xingdun_group_info_id_unavailable),
            )
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(current.textColorSecondary)
            setPadding(0, 16.dp(), 0, 0)
            setTextIsSelectable(true)
        }, matchWrap())
        content.addView(qrCard, matchWrap())

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(current.bgColorOperate, 18f)
        }
        actions.addView(actionRow(R.string.xingdun_group_qr_save, current) { saveQRCode(bitmap) })
        actions.addView(View(this).apply { setBackgroundColor(current.strokeColorPrimary) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { marginStart = 16.dp() })
        actions.addView(actionRow(R.string.xingdun_group_qr_share, current) { shareQRCode(value, bitmap) })
        content.addView(actions, matchWrap().apply { topMargin = 12.dp() })
    }

    private fun actionRow(label: Int, colors: ColorTokens, action: () -> Unit) = TextView(this).apply {
        setText(label)
        gravity = Gravity.CENTER_VERTICAL
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(BRAND)
        setPadding(18.dp(), 0, 18.dp(), 0)
        minHeight = 56.dp()
        isClickable = true
        isFocusable = true
        foreground = selectableItemBackground()
        setOnClickListener { if (!isSaving) action() }
        backgroundTintList = ColorStateList.valueOf(colors.bgColorOperate)
    }

    private fun saveQRCode(bitmap: Bitmap) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = bitmap
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        saveBitmap(bitmap)
    }

    private fun saveBitmap(bitmap: Bitmap) {
        if (isSaving) return
        isSaving = true
        val scope = activityScope ?: return
        scope.launch {
            runCatching {
                XingDunImageDelivery.saveToPictures(
                    this@XingDunGroupQRCodeActivity,
                    bitmap,
                    "xingdun_group_qr_${System.currentTimeMillis()}.png",
                )
            }.onSuccess {
                Toast.makeText(this@XingDunGroupQRCodeActivity, R.string.xingdun_group_qr_saved, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@XingDunGroupQRCodeActivity, R.string.xingdun_group_qr_save_failed, Toast.LENGTH_LONG).show()
            }
            isSaving = false
        }
    }

    private fun shareQRCode(value: XingDunGroupDetail, bitmap: Bitmap) {
        runCatching {
            val uri = XingDunImageDelivery.shareUri(this, bitmap, "group-qr.png")
            val shareText = getString(
                R.string.xingdun_group_qr_share_text,
                getString(R.string.demo_app_name),
                value.name,
                value.publicGroupId ?: getString(R.string.xingdun_group_info_id_unavailable),
            )
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                clipData = ClipData.newUri(contentResolver, getString(R.string.xingdun_group_qr_title), uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, getString(R.string.xingdun_group_qr_share)))
        }.onFailure {
            Toast.makeText(this, R.string.xingdun_group_qr_share_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showPermissionSettings() {
        AlertDialog.Builder(this)
            .setMessage(R.string.xingdun_group_qr_permission_denied)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_open_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            .show()
    }

    private fun selectableItemBackground(): android.graphics.drawable.Drawable? {
        val attributes = intArrayOf(android.R.attr.selectableItemBackground)
        return obtainStyledAttributes(attributes).use { it.getDrawable(0) }
    }

    private fun applyHeaderTheme(colors: ColorTokens) {
        root.setBackgroundColor(colors.bgColorTopBar)
        header.setBackgroundColor(colors.bgColorOperate)
        titleView.setTextColor(colors.textColorPrimary)
        back.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        divider.setBackgroundColor(colors.strokeColorPrimary)
    }

    private fun colors(): ColorTokens = themeStore.themeState.value.currentTheme.tokens.color

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_GROUP_ID = "group_id"
        const val EXTRA_DEBUG_PREVIEW = "xingdun_debug_group_qr_preview"
        private const val BRAND = 0xFF23B39C.toInt()
        private const val QR_SIZE = 900

        fun start(context: Context, groupID: String) {
            context.startActivity(
                Intent(context, XingDunGroupQRCodeActivity::class.java)
                    .putExtra(EXTRA_GROUP_ID, groupID),
            )
        }
    }
}
