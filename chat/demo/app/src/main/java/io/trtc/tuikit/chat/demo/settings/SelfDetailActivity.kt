package io.trtc.tuikit.chat.demo.settings

import io.trtc.tuikit.chat.demo.common.BaseActivity

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.TextInputDialog
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.ActionItem
import io.trtc.tuikit.chat.uikit.components.widgets.ActionSheet
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.Gender
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.chat.app.R
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SelfDetailActivity : BaseActivity() {

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
    private lateinit var tvDisplayName: TextView
    private lateinit var entryContainer: LinearLayout

    private lateinit var accountItem: SettingsEntry
    private lateinit var customIDItem: SettingsEntry
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

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        activityScope?.launch { uploadAvatar(uri) }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, SelfDetailActivity::class.java))
        }

        private const val DEFAULT_BIRTHDAY_TEXT = "1970-01-01"
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
        tvTitle.text = getString(R.string.demo_settings_self_detail_title)

        buildBody()
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)

        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        activityScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyColors(state.currentTheme.tokens.color)
            }
        }
        activityScope?.launch {
            LoginStore.shared.loginState.loginUserInfo.collectLatest { profile ->
                updateUserProfile(profile)
            }
        }
        activityScope?.launch { loadServerProfile() }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope?.cancel()
        activityScope = null
    }

    private fun buildBody() {
        val density = resources.displayMetrics.density
        val dp16 = (16f * density).toInt()
        val dp36 = (36f * density).toInt()

        avatar = Avatar(this).apply {
            setSize(Avatar.AvatarSize.XXL)
            setOnAvatarClickListener { avatarPicker.launch("image/*") }
        }
        contentColumn.addView(
            avatar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp16
            }
        )

        tvDisplayName = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            maxLines = 3
            setPadding(dp16, dp16, dp16, 0)
        }
        contentColumn.addView(
            tvDisplayName,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        entryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        contentColumn.addView(
            entryContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp36
            }
        )

        accountItem = SettingsEntry(
            context = this,
            title = getString(R.string.demo_settings_self_detail_account),
            showArrow = false,
            showDivider = true
        )
        customIDItem = SettingsEntry(
            context = this,
            title = getString(R.string.xingdun_custom_id),
            showArrow = true,
            showDivider = true,
            onClick = { showCustomIDEditor() }
        )
        nicknameItem = SettingsEntry(
            context = this,
            title = getString(R.string.demo_settings_self_detail_nickname),
            showArrow = true,
            showDivider = true,
            onClick = { showNicknameEditor() }
        )
        statusItem = SettingsEntry(
            context = this,
            title = getString(R.string.demo_settings_self_detail_status),
            showArrow = true,
            showDivider = true,
            onClick = { showStatusEditor() }
        )
        genderItem = SettingsEntry(
            context = this,
            title = getString(R.string.demo_settings_self_detail_gender),
            showArrow = true,
            showDivider = true,
            onClick = { showGenderSelector() }
        )
        birthdayItem = SettingsEntry(
            context = this,
            title = getString(R.string.demo_settings_self_detail_birthday),
            showArrow = true,
            showDivider = true,
            onClick = { showBirthdayPicker() }
        )
        phoneItem = SettingsEntry(
            context = this,
            title = getString(R.string.xingdun_phone),
            showArrow = false,
            showDivider = true
        )
        emailItem = SettingsEntry(
            context = this,
            title = getString(R.string.xingdun_email),
            showArrow = false,
            showDivider = false
        )

        entryContainer.addView(accountItem.view, entryLayoutParams())
        entryContainer.addView(customIDItem.view, entryLayoutParams())
        entryContainer.addView(nicknameItem.view, entryLayoutParams())
        entryContainer.addView(statusItem.view, entryLayoutParams())
        entryContainer.addView(genderItem.view, entryLayoutParams())
        entryContainer.addView(birthdayItem.view, entryLayoutParams())
        entryContainer.addView(phoneItem.view, entryLayoutParams())
        entryContainer.addView(emailItem.view, entryLayoutParams())
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

        entryContainer.setBackgroundColor(colors.bgColorOperate)
        tvDisplayName.setTextColor(colors.textColorPrimary)

        accountItem.applyColors(colors)
        customIDItem.applyColors(colors)
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
        tvDisplayName.text = displayName
        avatar.setContent(
            Avatar.AvatarContent.Image(url = cachedAvatarUrl, fallbackName = displayName)
        )
        accountItem.setValue(cachedUserID)
        customIDItem.setValue(cachedCustomID)
        nicknameItem.setValue(cachedNickname)
        statusItem.setValue(cachedSignature)
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
            customIDItem.setValue(cachedCustomID.ifBlank { getString(R.string.xingdun_not_set) })
            phoneItem.setValue(profile.string("phone")?.let(::maskPhone) ?: getString(R.string.xingdun_not_bound))
            emailItem.setValue(profile.string("email")?.let(::maskEmail) ?: getString(R.string.xingdun_not_bound))
            profile.string("avatar")?.let { url ->
                cachedAvatarUrl = url
                avatar.setContent(Avatar.AvatarContent.Image(url = url, fallbackName = cachedNickname.ifBlank { cachedUserID }))
            }
        }
    }

    private fun showCustomIDEditor() {
        TextInputDialog(
            context = this,
            title = getString(R.string.xingdun_edit_custom_id),
            initialText = cachedCustomID,
            onConfirm = { value ->
                val normalized = value.trim()
                if (!normalized.matches(Regex("^[A-Za-z0-9_]{3,32}$"))) {
                    Toast.makeText(this, R.string.xingdun_custom_id_invalid, Toast.LENGTH_LONG).show()
                    return@TextInputDialog
                }
                activityScope?.launch {
                    runCatching {
                        XingDunSessionManager.apiClient().postEmpty(
                            XingDunSessionManager.currentSession() ?: error(getString(R.string.xingdun_session_expired)),
                            "user/updateCustomId",
                            mapOf("custom_id" to normalized)
                        )
                    }.onSuccess {
                        cachedCustomID = normalized
                        customIDItem.setValue(normalized)
                        Toast.makeText(this@SelfDetailActivity, R.string.xingdun_profile_updated, Toast.LENGTH_SHORT).show()
                    }.onFailure { showProfileError(it) }
                }
            }
        ).show()
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
        else -> getString(R.string.demo_settings_self_detail_gender_secret)
    }

    private fun birthdayDisplayText(birthday: Long?): String {
        if (birthday == null || birthday <= 0L) {
            return DEFAULT_BIRTHDAY_TEXT
        }
        val raw = birthday.toString()
        return try {
            "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}"
        } catch (_: Exception) {
            DEFAULT_BIRTHDAY_TEXT
        }
    }

    private fun showNicknameEditor() {
        TextInputDialog(
            context = this,
            title = getString(R.string.demo_settings_self_detail_edit_nickname_title),
            initialText = cachedNickname,
            onConfirm = { text ->
                if (text.isNotBlank()) {
                    val profile = UserProfile(nickname = text)
                    updateProfileOnServer(mapOf("nickname" to text), profile)
                }
            }
        ).show()
    }

    private fun showStatusEditor() {
        TextInputDialog(
            context = this,
            title = getString(R.string.demo_settings_self_detail_edit_status_title),
            initialText = cachedSignature,
            onConfirm = { text ->
                val profile = UserProfile(selfSignature = text)
                updateProfileOnServer(mapOf("signature" to text), profile)
            }
        ).show()
    }

    private fun showGenderSelector() {
        val options = listOf(
            ActionItem(
                text = getString(R.string.demo_settings_self_detail_gender_male),
                value = Gender.MALE
            ),
            ActionItem(
                text = getString(R.string.demo_settings_self_detail_gender_female),
                value = Gender.FEMALE
            ),
            ActionItem(
                text = getString(R.string.demo_settings_self_detail_gender_secret),
                value = Gender.UNKNOWN
            )
        )
        ActionSheet.show(this, options) { selected ->
            val gender = selected.value as? Gender ?: Gender.UNKNOWN
            val profile = UserProfile(gender = gender)
            val serverGender = when (gender) {
                Gender.MALE -> 1
                Gender.FEMALE -> 2
                else -> 0
            }
            updateProfileOnServer(mapOf("gender" to serverGender), profile)
        }
    }

    private fun showBirthdayPicker() {
        val calendar = Calendar.getInstance()
        val existing = cachedBirthday
        if (existing != null && existing > 0L) {
            val raw = existing.toString()
            try {
                val year = raw.substring(0, 4).toInt()
                val month = raw.substring(4, 6).toInt() - 1
                val day = raw.substring(6, 8).toInt()
                calendar.set(year, month, day)
            } catch (_: Exception) {
            }
        }

        val picker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance()
                cal.set(year, month, dayOfMonth)
                val birthdayStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                    .format(Date(cal.timeInMillis))
                val profile = UserProfile(birthday = birthdayStr.toLong())
                val serverBirthday = "%04d-%02d-%02d".format(year, month + 1, dayOfMonth)
                updateProfileOnServer(mapOf("birthday" to serverBirthday), profile)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        picker.show()
    }

    private fun updateProfileOnServer(fields: Map<String, Any>, profile: UserProfile) {
        val session = XingDunSessionManager.currentSession()
        if (session == null) {
            Toast.makeText(this, R.string.xingdun_session_expired, Toast.LENGTH_LONG).show()
            return
        }
        activityScope?.launch {
            runCatching {
                XingDunSessionManager.apiClient().postEmpty(session, "user/updateProfile", fields)
            }.onSuccess {
                // Server REST already synchronizes Tencent IM. This call refreshes the local Store;
                // it is intentionally issued only after the authoritative business write succeeds.
                LoginStore.shared.setSelfInfo(profile, noopCompletion())
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

    private fun noopCompletion(): CompletionHandler = object : CompletionHandler {
        override fun onSuccess() {}
        override fun onFailure(code: Int, desc: String) {}
    }

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
            tvTitle.setTextColor(colors.textColorSecondary)
            tvValue.setTextColor(colors.textColorPrimary)
            arrowView?.setColorFilter(colors.textColorTertiary)
            divider?.setBackgroundColor(colors.strokeColorPrimary)
            view.setBackgroundColor(colors.bgColorOperate)
        }
    }
}
