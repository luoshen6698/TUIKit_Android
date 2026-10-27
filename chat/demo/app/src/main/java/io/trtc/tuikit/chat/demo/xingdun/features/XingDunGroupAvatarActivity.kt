package io.trtc.tuikit.chat.demo.xingdun.features

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiException
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunUploadFile
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Full-screen group-avatar editor aligned with the iOS avatar child page. */
class XingDunGroupAvatarActivity : BaseActivity() {

    override val requiresLogin: Boolean
        get() = !intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false)

    private val themeStore by lazy { ThemeStore.shared(this) }
    private var activityScope: CoroutineScope? = null
    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var leftContainer: LinearLayout
    private lateinit var back: ImageView
    private lateinit var leftAction: TextView
    private lateinit var more: ImageView
    private lateinit var saveAction: TextView
    private lateinit var divider: View
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var selectedPreview: ImageView
    private lateinit var presetRow: LinearLayout
    private var selectedBytes: ByteArray? = null
    private var selectedPreset = -1
    private var isSaving = false
    private var authorityAvailable = false
    @Volatile private var resumed = false
    private var pendingPhotoUri: Uri? = null
    private var authorityRevoked = false
    private var resultPending = false
    private var authorityRequest = 0L
    private var profileOwner: XingDunGroupProfileOperationOwner? = null
    private lateinit var choosePhoto: Button
    private lateinit var authorityNotice: TextView
    private var authorityMessage: String? = null

    private val groupID by lazy { intent.getStringExtra(EXTRA_GROUP_ID).orEmpty() }
    private val groupName by lazy { intent.getStringExtra(EXTRA_GROUP_NAME).orEmpty() }
    private val avatarURL by lazy { intent.getStringExtra(EXTRA_AVATAR_URL) }
    private val isDebugPreview by lazy { intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false) }

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        pendingPhotoUri = result.data?.data
        if (resumed) consumePendingPhoto()
    }

    private fun openAvatarPicker() {
        avatarPicker.launch(
            Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                type = "image/*"
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        setContentView(R.layout.xingdun_activity_profile_editor)
        bindViews()
        configureHeader()
        buildContent()
        applyTheme(colors())
        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        profileOwner = XingDunGroupProfileOperationOwner(getString(R.string.xingdun_session_expired))
        authorityAvailable = isDebugPreview
        updateAuthorityControls()
        activityScope?.launch {
            themeStore.themeState.collectLatest { applyTheme(it.currentTheme.tokens.color) }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (::content.isInitialized) {
            consumePendingPhoto()
            if (isDebugPreview) updateAuthorityControls() else if (!isSaving) refreshAuthority()
        }
    }

    override fun onPause() {
        resumed = false
        if (::content.isInitialized && !isDebugPreview) {
            authorityRequest++
            authorityAvailable = false
            updateAuthorityControls()
        }
        super.onPause()
    }

    override fun onDestroy() {
        activityScope?.cancel()
        activityScope = null
        super.onDestroy()
    }

    private fun bindViews() {
        root = findViewById(R.id.xingdun_profileEditorRoot)
        header = findViewById(R.id.demo_chatHeaderContainer)
        titleView = findViewById(R.id.demo_tvChatTitle)
        leftContainer = findViewById(R.id.demo_leftContainer)
        back = findViewById(R.id.demo_btnBack)
        leftAction = findViewById(R.id.demo_btnMultiSelectCancel)
        more = findViewById(R.id.demo_btnMore)
        divider = findViewById(R.id.demo_headerDivider)
        scroll = findViewById(R.id.xingdun_profileEditorScroll)
        content = findViewById(R.id.xingdun_profileEditorContent)
        findViewById<View>(R.id.xingdun_profileEditorBottomNavigation).visibility = View.GONE
        saveAction = TextView(this).apply {
            setText(R.string.xingdun_group_info_save)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(12.dp(), 0, 0, 0)
            isEnabled = false
            setOnClickListener { saveSelection() }
        }
        (more.parent as FrameLayout).addView(
            saveAction,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END),
        )
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = bars.top)
            scroll.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun configureHeader() {
        titleView.setText(R.string.xingdun_group_info_avatar)
        back.visibility = View.GONE
        leftAction.visibility = View.VISIBLE
        leftAction.setText(android.R.string.cancel)
        more.visibility = View.GONE
        leftContainer.setOnClickListener { if (!isSaving) finish() }
    }

    private fun buildContent() {
        val current = colors()
        val previewCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(16.dp(), 20.dp(), 16.dp(), 18.dp())
            background = rounded(current.bgColorOperate, 18f)
        }
        val preview = FrameLayout(this)
        preview.addView(Avatar(this).apply {
            setSize(Avatar.AvatarSize.L)
            setContent(Avatar.AvatarContent.Image(avatarURL, groupName))
        }, FrameLayout.LayoutParams(96.dp(), 96.dp(), Gravity.CENTER))
        selectedPreview = ImageView(this).apply {
            visibility = View.GONE
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.TRANSPARENT, 20f)
            clipToOutline = true
        }
        preview.addView(selectedPreview, FrameLayout.LayoutParams(96.dp(), 96.dp(), Gravity.CENTER))
        previewCard.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 112.dp()))
        choosePhoto = Button(this).apply {
            setText(R.string.xingdun_group_info_choose_photo)
            setOnClickListener { if (canEditSelection()) openAvatarPicker() }
        }
        previewCard.addView(choosePhoto)
        content.addView(previewCard, matchWrap())

        val presetsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 14.dp(), 16.dp(), 18.dp())
            background = rounded(current.bgColorOperate, 18f)
        }
        presetsCard.addView(TextView(this).apply {
            setText(R.string.xingdun_group_info_default_avatars)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(current.textColorSecondary)
            setPadding(0, 0, 0, 12.dp())
        })
        presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        presetsCard.addView(presetRow, matchWrap())
        content.addView(presetsCard, matchWrap().apply { topMargin = 12.dp() })
        authorityNotice = TextView(this).apply {
            visibility = View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(current.textColorSecondary)
            setPadding(4.dp(), 12.dp(), 4.dp(), 0)
        }
        content.addView(authorityNotice, matchWrap())
        renderPresets()
    }

    private fun ownerIsCurrent(): Boolean =
        resumed && !isFinishing && !isDestroyed && (isDebugPreview || profileOwner?.isCurrent() == true)

    private fun canEditSelection(): Boolean = ownerIsCurrent() && authorityAvailable &&
        !authorityRevoked && !resultPending && !isSaving

    private fun updateAuthorityControls() {
        if (!::saveAction.isInitialized) return
        val editable = canEditSelection()
        saveAction.isEnabled = editable && selectedBytes != null
        saveAction.setTextColor(if (saveAction.isEnabled) BRAND else colors().textColorTertiary)
        choosePhoto.isEnabled = editable
        leftAction.isEnabled = !isSaving
        for (index in 0 until presetRow.childCount) presetRow.getChildAt(index).isEnabled = editable
        authorityNotice.text = authorityMessage
        authorityNotice.visibility = if (authorityMessage.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun refreshAuthority() {
        val scope = activityScope ?: return
        val owner = profileOwner ?: return
        val ticket = ++authorityRequest
        authorityAvailable = false
        authorityMessage = getString(if (resultPending) R.string.xingdun_pending else R.string.xingdun_group_info_loading)
        updateAuthorityControls()
        scope.launch {
            try {
                val latest = owner.detail(groupID)
                if (ticket != authorityRequest || !ownerIsCurrent()) return@launch
                authorityAvailable = true
                if (!latest.canEditGroupInfo) authorityRevoked = true
                authorityMessage = when {
                    resultPending -> getString(R.string.xingdun_pending)
                    authorityRevoked -> getString(R.string.xingdun_group_permission_update_info_administrators_detail)
                    else -> null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (ticket != authorityRequest) return@launch
                authorityAvailable = false
                authorityMessage = getString(R.string.xingdun_group_info_load_failed_message)
            } finally {
                if (ticket == authorityRequest && !isFinishing && !isDestroyed) updateAuthorityControls()
            }
        }
    }

    private fun renderPresets() {
        presetRow.removeAllViews()
        PRESET_COLORS.forEachIndexed { index, color ->
            val bitmap = presetBitmap(color, PRESET_LABELS[index])
            val selected = selectedPreset == index
            presetRow.addView(FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(if (selected) 3.dp() else 0, if (selected) BRAND else Color.TRANSPARENT)
                }
                setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
                addView(ImageView(this@XingDunGroupAvatarActivity).apply {
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = getString(R.string.xingdun_group_info_default_avatar_number, index + 1)
                    background = rounded(Color.TRANSPARENT, 24f)
                    clipToOutline = true
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                setOnClickListener {
                    if (canEditSelection()) selectPreset(index, bitmap)
                }
            }, LinearLayout.LayoutParams(56.dp(), 56.dp()).apply {
                if (index > 0) marginStart = 8.dp()
            })
        }
    }

    private fun selectPreset(index: Int, bitmap: Bitmap) {
        if (!canEditSelection()) return
        selectedPreset = index
        selectedBytes = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output))
            output.toByteArray()
        }
        selectedPreview.setImageBitmap(bitmap)
        selectedPreview.visibility = View.VISIBLE
        renderPresets()
        updateAuthorityControls()
        applyTheme(colors())
    }

    private fun consumePendingPhoto() {
        if (!ownerIsCurrent() || isSaving) return
        val uri = pendingPhotoUri ?: return
        pendingPhotoUri = null
        prepareSelectedAvatar(uri)
    }

    private fun prepareSelectedAvatar(uri: Uri) {
        // Preparing a local draft is separate from authorizing its upload.
        if (!ownerIsCurrent() || isSaving) return
        val scope = activityScope ?: return
        scope.launch {
            runCatching { avatarBytes(uri) }
                .onSuccess { bytes ->
                    if (!ownerIsCurrent() || isSaving) return@onSuccess
                    selectedPreset = -1
                    selectedBytes = bytes
                    selectedPreview.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                    selectedPreview.visibility = View.VISIBLE
                    renderPresets()
                    updateAuthorityControls()
                    applyTheme(colors())
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (!ownerIsCurrent()) return@onFailure
                    Toast.makeText(
                        this@XingDunGroupAvatarActivity,
                        error.localizedMessage ?: getString(R.string.xingdun_avatar_invalid),
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    private suspend fun avatarBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { getString(R.string.xingdun_avatar_invalid) }
        var sample = 1
        while (bounds.outWidth / sample > 2_048 || bounds.outHeight / sample > 2_048) sample *= 2
        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error(getString(R.string.xingdun_avatar_invalid))
        val bytes = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output))
            output.toByteArray()
        }
        require(bytes.isNotEmpty() && bytes.size <= AVATAR_MAX_BYTES) { getString(R.string.xingdun_avatar_invalid) }
        bytes
    }

    private fun saveSelection() {
        val bytes = selectedBytes ?: return
        if (!canEditSelection()) return
        if (isDebugPreview) {
            setResult(Activity.RESULT_OK)
            finish()
            return
        }
        val owner = profileOwner ?: return
        isSaving = true
        authorityAvailable = false
        authorityRequest++
        updateAuthorityControls()
        activityScope?.launch {
            var writeAttempted = false
            try {
                val latest = owner.detail(groupID)
                if (!ownerIsCurrent()) return@launch
                authorityAvailable = true
                if (!latest.canEditGroupInfo) {
                    authorityRevoked = true
                    authorityMessage = getString(R.string.xingdun_group_permission_update_info_administrators_detail)
                    return@launch
                }
                writeAttempted = true
                owner.request { session ->
                    if (!resumed) throw CancellationException()
                    XingDunSessionManager.apiClient().postMultipartEmpty(session, "team/update",
                        mapOf("team_id" to groupID),
                        listOf(XingDunUploadFile("avatar", "xingdun-group-avatar.jpg", "image/jpeg", bytes)))
                }
                if (!ownerIsCurrent()) return@launch
                GroupStore.shared.loadJoinedGroups()
                Toast.makeText(this@XingDunGroupAvatarActivity,
                    R.string.xingdun_group_info_avatar_updated, Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            } catch (cancelled: CancellationException) {
                if (writeAttempted) {
                    resultPending = true
                    authorityAvailable = false
                    authorityMessage = getString(R.string.xingdun_pending)
                }
                throw cancelled
            } catch (error: Exception) {
                resultPending = writeAttempted && !isDefiniteGroupProfileRejection(error)
                if (isFinishing || isDestroyed) return@launch
                val actual = if (writeAttempted) try { owner.detail(groupID) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null } else null
                if (!ownerIsCurrent()) {
                    authorityAvailable = false
                    return@launch
                }
                authorityAvailable = actual != null
                if (actual != null && !actual.canEditGroupInfo) authorityRevoked = true
                // A different server avatar URL cannot identify this uncertain upload.
                resultPending = writeAttempted && !isDefiniteGroupProfileRejection(error)
                authorityMessage = when {
                    resultPending -> getString(R.string.xingdun_pending)
                    error is XingDunApiException -> error.localizedMessage
                        ?: getString(R.string.xingdun_action_failed)
                    else -> getString(R.string.xingdun_group_info_load_failed_message)
                }
            } finally {
                isSaving = false
                if (!isFinishing && !isDestroyed) updateAuthorityControls()
            }
        }
    }

    private fun applyTheme(colors: ColorTokens) {
        root.setBackgroundColor(colors.bgColorTopBar)
        header.setBackgroundColor(colors.bgColorOperate)
        titleView.setTextColor(colors.textColorPrimary)
        leftAction.setTextColor(BRAND)
        saveAction.setTextColor(if (saveAction.isEnabled) BRAND else colors.textColorTertiary)
        divider.setBackgroundColor(colors.strokeColorPrimary)
    }

    private fun presetBitmap(color: Int, label: String): Bitmap {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 108f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(label, 128f, 128f - (paint.descent() + paint.ascent()) / 2f, paint)
        return bitmap
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
        private const val EXTRA_GROUP_NAME = "group_name"
        private const val EXTRA_AVATAR_URL = "avatar_url"
        private const val EXTRA_DEBUG_PREVIEW = "xingdun_debug_group_avatar_preview"
        private const val AVATAR_MAX_BYTES = 5 * 1024 * 1024
        private const val BRAND = 0xFF23B39C.toInt()
        private val PRESET_COLORS = intArrayOf(
            0xFF2EBFA3.toInt(),
            0xFF407AF2.toInt(),
            0xFF8F4FDC.toInt(),
            0xFFEB4F8C.toInt(),
            0xFFF29B33.toInt(),
        )
        private val PRESET_LABELS = arrayOf("◆", "●", "••", "➤", "✦")

        fun intent(
            context: Context,
            groupID: String,
            groupName: String,
            avatarURL: String?,
            debugPreview: Boolean = false,
        ): Intent = Intent(context, XingDunGroupAvatarActivity::class.java)
            .putExtra(EXTRA_GROUP_ID, groupID)
            .putExtra(EXTRA_GROUP_NAME, groupName)
            .putExtra(EXTRA_AVATAR_URL, avatarURL)
            .putExtra(EXTRA_DEBUG_PREVIEW, debugPreview)
    }
}
