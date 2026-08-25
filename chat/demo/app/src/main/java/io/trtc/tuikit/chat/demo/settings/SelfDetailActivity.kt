package io.trtc.tuikit.chat.demo.settings

import io.trtc.tuikit.chat.demo.common.BaseActivity

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import com.google.gson.JsonObject
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.atomicxcore.api.login.Gender
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunFeatureActivity
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunUploadFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

open class SelfDetailActivity : BaseActivity() {

    private val themeStore by lazy { ThemeStore.shared(this) }
    private var activityScope: CoroutineScope? = null

    private lateinit var rootContainer: LinearLayout
    private lateinit var headerContainer: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnMore: ImageView
    private lateinit var headerDivider: View
    private lateinit var badgeContainer: FrameLayout
    private lateinit var leftContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var contentColumn: LinearLayout

    private lateinit var avatar: Avatar
    private lateinit var avatarRow: LinearLayout
    private lateinit var avatarArrow: ImageView
    private lateinit var avatarDivider: View
    private lateinit var identityContainer: LinearLayout
    private lateinit var detailContainer: LinearLayout
    private lateinit var detailsTitle: TextView

    private lateinit var accountItem: SettingsEntry
    private lateinit var nicknameItem: SettingsEntry
    private lateinit var statusItem: SettingsEntry
    private lateinit var genderItem: SettingsEntry
    private lateinit var birthdayItem: SettingsEntry
    private lateinit var phoneItem: SettingsEntry
    private lateinit var emailItem: SettingsEntry

    private var cachedUserID: String = ""
    private var cachedNickname: String = ""
    private var cachedSignature: String = ""
    private var cachedGender: Gender = Gender.UNKNOWN
    private var cachedBirthday: Long? = null
    private var cachedAvatarUrl: String? = null
    private var cachedCustomID: String = ""
    private val isDebugPreview: Boolean
        get() = !requiresLogin && intent.getBooleanExtra(EXTRA_DEBUG_PREVIEW, false)

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        showAvatarPreview(uri)
    }

    private val profileEditor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val mode = result.data?.getStringExtra(XingDunProfileEditorActivity.EXTRA_MODE).orEmpty()
        val value = result.data?.getStringExtra(XingDunProfileEditorActivity.EXTRA_VALUE).orEmpty()
        when (mode) {
            XingDunProfileEditorActivity.MODE_ACCOUNT -> saveCustomID(value)
            XingDunProfileEditorActivity.MODE_NICKNAME ->
                updateProfileOnServer(mapOf("nickname" to value))
            XingDunProfileEditorActivity.MODE_SIGNATURE ->
                updateProfileOnServer(mapOf("signature" to value))
            XingDunProfileEditorActivity.MODE_GENDER -> {
                updateProfileOnServer(mapOf("gender" to value.toIntOrNull().orZero()))
            }
            XingDunProfileEditorActivity.MODE_BIRTHDAY -> {
                updateProfileOnServer(mapOf("birthday" to value))
            }
        }
    }

    companion object {
        const val EXTRA_DEBUG_PREVIEW = "xingdun_debug_profile_preview"

        fun start(context: Context) {
            context.startActivity(Intent(context, SelfDetailActivity::class.java))
        }

        private const val BRAND = 0xFF23B39C.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }
        setContentView(R.layout.demo_activity_self_detail)

        rootContainer = findViewById(R.id.demo_selfDetailRootContainer)
        headerContainer = findViewById(R.id.demo_chatHeaderContainer)
        tvTitle = findViewById(R.id.demo_tvChatTitle)
        btnBack = findViewById(R.id.demo_btnBack)
        btnMore = findViewById(R.id.demo_btnMore)
        headerDivider = findViewById(R.id.demo_headerDivider)
        badgeContainer = findViewById(R.id.demo_badgeContainer)
        leftContainer = findViewById(R.id.demo_leftContainer)
        scrollView = findViewById(R.id.demo_selfDetailScrollView)
        contentColumn = findViewById(R.id.demo_selfDetailContent)

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            headerContainer.updatePadding(top = systemBars.top)
            scrollView.updatePadding(bottom = systemBars.bottom)
            insets
        }

        leftContainer.contentDescription = btnBack.contentDescription
        leftContainer.setOnClickListener { finish() }
        btnMore.visibility = View.GONE
        badgeContainer.visibility = View.GONE
        tvTitle.setText(R.string.xingdun_profile_title)

        buildBody()
        if (isDebugPreview) {
            applyDebugPreviewState()
        }
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)

        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        activityScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyColors(state.currentTheme.tokens.color)
            }
        }
        if (!isDebugPreview) {
            activityScope?.launch {
                LoginStore.shared.loginState.loginUserInfo.collectLatest { profile ->
                    updateUserProfile(profile)
                }
            }
            activityScope?.launch { loadServerProfile() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isDebugPreview && ::phoneItem.isInitialized) activityScope?.launch { loadServerProfile() }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope?.cancel()
        activityScope = null
    }

    private fun buildBody() {
        contentColumn.setPadding(16.dp(), 16.dp(), 16.dp(), 24.dp())
        identityContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        contentColumn.addView(
            identityContainer,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        buildAvatarRow()
        nicknameItem = SettingsEntry(
            context = this,
            title = getString(R.string.demo_settings_self_detail_nickname),
            showArrow = true,
            showDivider = true,
            onClick = { openEditor(XingDunProfileEditorActivity.MODE_NICKNAME, cachedNickname) },
        )
        accountItem = SettingsEntry(
            context = this,
            title = getString(R.string.demo_settings_self_detail_account),
            showArrow = true,
            showDivider = true,
            onClick = { openEditor(XingDunProfileEditorActivity.MODE_ACCOUNT, cachedCustomID) },
        )
        statusItem = SettingsEntry(
            context = this,
            title = getString(R.string.demo_settings_self_detail_status),
            showArrow = true,
            showDivider = false,
            onClick = { openEditor(XingDunProfileEditorActivity.MODE_SIGNATURE, cachedSignature) },
        )

        identityContainer.addView(nicknameItem.view, entryLayoutParams())
        identityContainer.addView(accountItem.view, entryLayoutParams())
        identityContainer.addView(statusItem.view, entryLayoutParams())

        detailsTitle = TextView(this).apply {
            setText(R.string.xingdun_profile_details_section)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(12.dp(), 22.dp(), 12.dp(), 10.dp())
        }
        contentColumn.addView(detailsTitle)
        detailContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        contentColumn.addView(
            detailContainer,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        genderItem = SettingsEntry(
            context = this,
            title = getString(R.string.demo_settings_self_detail_gender),
            showArrow = true,
            showDivider = true,
            onClick = { openEditor(XingDunProfileEditorActivity.MODE_GENDER, genderServerValue()) },
        )
        birthdayItem = SettingsEntry(
            context = this,
            title = getString(R.string.demo_settings_self_detail_birthday),
            showArrow = true,
            showDivider = true,
            onClick = { openEditor(XingDunProfileEditorActivity.MODE_BIRTHDAY, birthdayDisplayText(cachedBirthday).takeUnless { it == getString(R.string.xingdun_not_set) }.orEmpty()) },
        )
        phoneItem = SettingsEntry(
            context = this,
            title = getString(R.string.xingdun_phone),
            showArrow = true,
            showDivider = true,
            onClick = { XingDunFeatureActivity.start(this, XingDunFeatureActivity.MODE_BIND_PHONE) },
        )
        emailItem = SettingsEntry(
            context = this,
            title = getString(R.string.xingdun_email),
            showArrow = true,
            showDivider = false,
            onClick = { XingDunFeatureActivity.start(this, XingDunFeatureActivity.MODE_BIND_EMAIL) },
        )

        detailContainer.addView(genderItem.view, entryLayoutParams())
        detailContainer.addView(birthdayItem.view, entryLayoutParams())
        detailContainer.addView(phoneItem.view, entryLayoutParams())
        detailContainer.addView(emailItem.view, entryLayoutParams())
    }

    private fun buildAvatarRow() {
        avatarRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 10.dp(), 16.dp(), 10.dp())
            minimumHeight = 76.dp()
            isClickable = true
            isFocusable = true
            setOnClickListener { showAvatarActions() }
        }
        avatarRow.addView(TextView(this).apply {
            setText(R.string.xingdun_profile_avatar)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        avatar = Avatar(this).apply {
            setSize(Avatar.AvatarSize.L)
            setOnAvatarClickListener { showAvatarActions() }
        }
        avatarRow.addView(avatar)
        avatarArrow = ImageView(this).apply { setImageResource(R.drawable.demo_ic_arrow_right) }
        avatarRow.addView(avatarArrow, LinearLayout.LayoutParams(7.dp(), 12.dp()).apply { marginStart = 10.dp() })
        identityContainer.addView(avatarRow, entryLayoutParams())
        avatarDivider = View(this)
        identityContainer.addView(avatarDivider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp()).apply {
            marginStart = 16.dp()
        })
    }

    private fun entryLayoutParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

    private fun applyColors(colors: ColorTokens) {
        rootContainer.setBackgroundColor(colors.bgColorTopBar)
        headerContainer.setBackgroundColor(colors.bgColorOperate)
        tvTitle.setTextColor(colors.textColorPrimary)
        btnBack.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        headerDivider.setBackgroundColor(colors.strokeColorPrimary)

        identityContainer.background = roundedBackground(colors.bgColorOperate, 18f)
        detailContainer.background = roundedBackground(colors.bgColorOperate, 18f)
        detailsTitle.setTextColor(colors.textColorSecondary)
        (avatarRow.getChildAt(0) as? TextView)?.setTextColor(colors.textColorPrimary)
        avatarArrow.setColorFilter(colors.textColorTertiary)
        avatarDivider.setBackgroundColor(colors.strokeColorPrimary)

        accountItem.applyColors(colors)
        nicknameItem.applyColors(colors)
        statusItem.applyColors(colors)
        genderItem.applyColors(colors)
        birthdayItem.applyColors(colors)
        phoneItem.applyColors(colors)
        emailItem.applyColors(colors)
    }

    private fun updateUserProfile(profile: UserProfile?) {
        cachedUserID = profile?.userID.orEmpty()
        cachedNickname = profile?.nickname.orEmpty()
        cachedSignature = profile?.selfSignature.orEmpty()
        cachedGender = profile?.gender ?: Gender.UNKNOWN
        cachedBirthday = profile?.birthday
        cachedAvatarUrl = profile?.avatarURL

        val displayName = if (cachedNickname.isNotEmpty()) cachedNickname else cachedUserID
        avatar.setContent(
            Avatar.AvatarContent.Image(url = cachedAvatarUrl, fallbackName = displayName)
        )
        accountItem.setValue(cachedCustomID.ifBlank { getString(R.string.xingdun_not_set) })
        nicknameItem.setValue(cachedNickname)
        statusItem.setValue(cachedSignature.ifBlank { getString(R.string.xingdun_not_set) })
        genderItem.setValue(genderDisplayText(cachedGender))
        birthdayItem.setValue(birthdayDisplayText(cachedBirthday))
    }

    private suspend fun loadServerProfile() {
        val session = XingDunSessionManager.currentSession() ?: return
        runCatching {
            XingDunSessionManager.apiClient().get<JsonObject>(
                session, "user/profile", emptyMap(), JsonObject::class.java
            )
        }.onSuccess { profile ->
            cachedCustomID = profile.string("custom_id").orEmpty()
            cachedNickname = profile.string("nickname") ?: cachedNickname
            cachedSignature = profile.string("signature").orEmpty()
            cachedGender = when (profile.get("gender")?.asInt) {
                1 -> Gender.MALE
                2 -> Gender.FEMALE
                else -> Gender.UNKNOWN
            }
            cachedBirthday = profile.string("birthday")?.replace("-", "")?.toLongOrNull()
            cachedAvatarUrl = profile.get("avatar")?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf(String::isNotEmpty)
            phoneItem.setValue(profile.string("phone")?.let(::maskPhone) ?: getString(R.string.xingdun_not_bound))
            emailItem.setValue(profile.string("email")?.let(::maskEmail) ?: getString(R.string.xingdun_not_bound))
            renderCachedProfile()
        }
    }

    private fun renderCachedProfile() {
        val displayName = cachedNickname.ifBlank { cachedUserID }
        avatar.setContent(Avatar.AvatarContent.Image(url = cachedAvatarUrl, fallbackName = displayName))
        nicknameItem.setValue(displayName)
        accountItem.setValue(cachedCustomID.ifBlank { getString(R.string.xingdun_not_set) })
        statusItem.setValue(cachedSignature.ifBlank { getString(R.string.xingdun_not_set) })
        genderItem.setValue(genderDisplayText(cachedGender))
        birthdayItem.setValue(birthdayDisplayText(cachedBirthday))
    }

    /** Debug preview data is reachable only from a debug-only subclass that disables the login guard. */
    private fun applyDebugPreviewState() {
        cachedUserID = "preview_user_b"
        cachedNickname = "b002"
        cachedCustomID = "xd_xc2026_preview"
        cachedSignature = ""
        cachedGender = Gender.UNKNOWN
        cachedBirthday = null
        cachedAvatarUrl = null
        phoneItem.setValue(getString(R.string.xingdun_not_bound))
        emailItem.setValue(getString(R.string.xingdun_not_bound))
        renderCachedProfile()
    }

    private fun openEditor(mode: String, value: String) {
        profileEditor.launch(
            XingDunProfileEditorActivity.intent(this, mode, value).apply {
                if (isDebugPreview) {
                    putExtra(XingDunProfileEditorActivity.EXTRA_DEBUG_PREVIEW, true)
                }
            },
        )
    }

    private fun saveCustomID(value: String) {
        activityScope?.launch {
            runCatching {
                XingDunSessionManager.apiClient().postEmpty(
                    XingDunSessionManager.currentSession() ?: error(getString(R.string.xingdun_session_expired)),
                    "user/updateCustomId",
                    mapOf("custom_id" to value),
                )
            }.onSuccess {
                cachedCustomID = value
                accountItem.setValue(value)
                Toast.makeText(this@SelfDetailActivity, R.string.xingdun_profile_updated, Toast.LENGTH_SHORT).show()
            }.onFailure(::showProfileError)
        }
    }

    private fun showAvatarActions() {
        val actions = mutableListOf(getString(R.string.xingdun_profile_choose_avatar))
        if (!cachedAvatarUrl.isNullOrBlank()) actions += getString(R.string.xingdun_profile_remove_avatar)
        AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_profile_avatar)
            .setItems(actions.toTypedArray()) { _, which ->
                if (which == 0) avatarPicker.launch("image/*") else confirmRemoveAvatar()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAvatarPreview(uri: Uri) {
        val preview = ImageView(this).apply {
            setImageURI(uri)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(20.dp(), 12.dp(), 20.dp(), 12.dp())
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.xingdun_profile_avatar_preview)
            .setView(preview)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                activityScope?.launch { uploadAvatar(uri) }
            }
            .show()
    }

    private fun confirmRemoveAvatar() {
        AlertDialog.Builder(this)
            .setMessage(R.string.xingdun_profile_remove_avatar_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_profile_remove_avatar) { _, _ ->
                activityScope?.launch { removeAvatar() }
            }
            .show()
    }

    private suspend fun removeAvatar() {
        val session = XingDunSessionManager.currentSession()
            ?: return showProfileError(IllegalStateException(getString(R.string.xingdun_session_expired)))
        runCatching {
            XingDunSessionManager.apiClient().postMultipartEmpty(
                session,
                "user/updateProfile",
                mapOf("remove_avatar" to "1"),
                emptyList(),
            )
        }.onSuccess {
            cachedAvatarUrl = null
            renderCachedProfile()
            Toast.makeText(this, R.string.xingdun_profile_updated, Toast.LENGTH_SHORT).show()
        }.onFailure(::showProfileError)
    }

    private suspend fun uploadAvatar(uri: android.net.Uri) {
        val bytes = runCatching {
            withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                check(bounds.outWidth > 0 && bounds.outHeight > 0)
                var sample = 1
                while (bounds.outWidth / sample > 2_048 || bounds.outHeight / sample > 2_048) sample *= 2
                val bitmap = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: error(getString(R.string.xingdun_avatar_invalid))
                ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output))
                    output.toByteArray()
                }
            }
        }.getOrElse {
            showProfileError(it)
            return
        }
        if (bytes.isEmpty() || bytes.size > 5 * 1024 * 1024) {
            Toast.makeText(this, R.string.xingdun_avatar_invalid, Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            XingDunSessionManager.apiClient().postMultipartEmpty(
                XingDunSessionManager.currentSession() ?: error(getString(R.string.xingdun_session_expired)),
                "user/updateProfile",
                emptyMap(),
                listOf(XingDunUploadFile("avatar", "xingdun-avatar.jpg", "image/jpeg", bytes))
            )
        }.onSuccess {
            loadServerProfile()
            Toast.makeText(this, R.string.xingdun_profile_updated, Toast.LENGTH_SHORT).show()
        }.onFailure(::showProfileError)
    }

    private fun showProfileError(error: Throwable) {
        Toast.makeText(this, error.localizedMessage ?: getString(R.string.xingdun_action_failed), Toast.LENGTH_LONG).show()
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf(String::isNotEmpty)

    private fun maskPhone(value: String): String = if (value.length < 7) value else "${value.take(3)}****${value.takeLast(4)}"
    private fun maskEmail(value: String): String {
        val at = value.indexOf('@')
        return if (at <= 0) value else "${value.take(1)}***${value.substring(at)}"
    }

    private fun genderDisplayText(gender: Gender?): String = when (gender) {
        Gender.MALE -> getString(R.string.demo_settings_self_detail_gender_male)
        Gender.FEMALE -> getString(R.string.demo_settings_self_detail_gender_female)
        else -> getString(R.string.xingdun_not_set)
    }

    private fun birthdayDisplayText(birthday: Long?): String {
        if (birthday == null || birthday <= 0L) {
            return getString(R.string.xingdun_not_set)
        }
        val raw = birthday.toString()
        return try {
            "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}"
        } catch (_: Exception) {
            getString(R.string.xingdun_not_set)
        }
    }

    private fun updateProfileOnServer(fields: Map<String, Any>) {
        val session = XingDunSessionManager.currentSession()
        if (session == null) {
            Toast.makeText(this, R.string.xingdun_session_expired, Toast.LENGTH_LONG).show()
            return
        }
        activityScope?.launch {
            runCatching {
                XingDunSessionManager.apiClient().postEmpty(session, "user/updateProfile", fields)
            }.onSuccess {
                loadServerProfile()
                Toast.makeText(this@SelfDetailActivity, R.string.xingdun_profile_updated, Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(
                    this@SelfDetailActivity,
                    error.localizedMessage ?: getString(R.string.xingdun_action_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun genderServerValue(): String = when (cachedGender) {
        Gender.MALE -> "1"
        Gender.FEMALE -> "2"
        else -> "0"
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun roundedBackground(color: Int, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private class SettingsEntry(
        context: Context,
        title: String,
        private val showArrow: Boolean,
        private val showDivider: Boolean,
        private val onClick: (() -> Unit)? = null
    ) {
        val view: LinearLayout
        private val tvTitle: TextView
        private val tvValue: TextView
        private val arrowView: ImageView?
        private val divider: View?
        private val density = context.resources.displayMetrics.density

        init {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LOCALE
                gravity = Gravity.CENTER_VERTICAL
                val padH = (16f * density).toInt()
                val padV = (12f * density).toInt()
                setPadding(padH, padV, padH, padV)
                minimumHeight = (48f * density).toInt()
                if (onClick != null) {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onClick.invoke() }
                }
            }

            tvTitle = TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                maxLines = 1
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
            row.addView(
                tvTitle,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            tvValue = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                val padStart = (16f * density).toInt()
                val padEnd = (8f * density).toInt()
                setPaddingRelative(padStart, 0, padEnd, 0)
            }
            row.addView(
                tvValue,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )

            arrowView = if (showArrow) {
                ImageView(context).apply {
                    setImageResource(R.drawable.demo_ic_arrow_right)
                    layoutParams = LinearLayout.LayoutParams(
                        (7f * density).toInt(),
                        (12f * density).toInt()
                    )
                }.also { row.addView(it) }
            } else {
                null
            }

            container.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            divider = if (showDivider) {
                View(context).also { view ->
                    container.addView(
                        view,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Math.max(1, (0.5f * density).toInt())
                        )
                    )
                }
            } else {
                null
            }

            view = container
        }

        fun setValue(value: String) {
            tvValue.text = value
        }

        fun applyColors(colors: ColorTokens) {
            tvTitle.setTextColor(colors.textColorPrimary)
            tvValue.setTextColor(colors.textColorSecondary)
            arrowView?.setColorFilter(colors.textColorTertiary)
            divider?.setBackgroundColor(colors.strokeColorPrimary)
            view.setBackgroundColor(Color.TRANSPARENT)
        }
    }
}
