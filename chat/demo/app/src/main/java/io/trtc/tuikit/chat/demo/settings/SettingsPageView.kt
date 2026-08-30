package io.trtc.tuikit.chat.demo.settings

import io.trtc.tuikit.chat.demo.common.AppConstants
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.gson.JsonObject
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.atomicx.theme.Theme
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicx.theme.utils.ThemePersistUtil
import io.trtc.tuikit.chat.uikit.components.widgets.ActionItem
import io.trtc.tuikit.chat.uikit.components.widgets.ActionSheet
import io.trtc.tuikit.chat.uikit.components.widgets.Switch
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.login.AllowType
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.demo.xingdun.launch.XingDunLaunchActivity
import io.trtc.tuikit.chat.demo.xingdun.launch.XingDunEnterpriseAccessActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunFeatureActivity
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantSessionCoordinator
import io.trtc.tuikit.chat.uikit.pages.PageHeaderView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val pageHeader: PageHeaderView
    private val tvUserName: TextView
    private val tvUserId: TextView
    private val tvUserStatus: TextView
    private val userAvatar: Avatar
    private val profileMetrics: LinearLayout
    private val metricFriends: TextView
    private val metricGroups: TextView
    private val metricFavorites: TextView

    private val itemTheme: View
    private val itemPrimaryColor: View
    private val itemLanguage: View
    private val itemAddRule: View
    private val itemTranslateLanguage: View
    private val itemVoiceMessage: View

    private val switchReadReceipt: Switch
    private val tvReadReceiptTitle: TextView
    private val tvReadReceiptDesc: TextView

    private val switchCallsTab: Switch
    private val tvCallsTabTitle: TextView

    private val btnLogout: TextView

    private val settingsGroup1: LinearLayout
    private val settingsGroup2: LinearLayout
    private val settingsGroupVoice: LinearLayout
    private val scrollContent: LinearLayout
    private val allDividers = mutableListOf<View>()
    private val allSpacers = mutableListOf<View>()
    private val productFeatureItems = mutableListOf<Pair<View, String>>()
    private val systemSettingsRows = mutableListOf<View>()
    private val systemSettingsDividers = mutableListOf<View>()
    private var backButton: ImageView? = null
    private var systemSettingsMode = false

    private var enableReadReceipt: Boolean = AppBuilderConfig.enableReadReceipt
    private var translateTargetLanguage: String = AppBuilderConfig.translateTargetLanguage

    private var coroutineScope: CoroutineScope? = null
    private var userInfoJob: Job? = null
    private var themeScope: CoroutineScope? = null
    private val themeStore = ThemeStore.shared(context)
    private val themePersistUtil = ThemePersistUtil(context)

    private val translateLanguageOptions = listOf(
        "zh" to "简体中文",
        "zh-TW" to "繁體中文",
        "en" to "English",
        "ja" to "日本語",
        "ko" to "한국어",
        "fr" to "Français",
        "es" to "Español",
        "it" to "Italiano",
        "de" to "Deutsch",
        "tr" to "Türkçe",
        "ru" to "Русский",
        "pt" to "Português",
        "vi" to "Tiếng Việt",
        "id" to "Bahasa Indonesia",
        "th" to "ภาษาไทย",
        "ms" to "Bahasa Melayu",
        "hi" to "हिन्दी"
    )

    init {
        LayoutInflater.from(context).inflate(R.layout.demo_page_settings, this, true)

        pageHeader = findViewById(R.id.demo_pageHeader)
        tvUserName = findViewById(R.id.demo_tvUserName)
        tvUserId = findViewById(R.id.demo_tvUserId)
        tvUserStatus = findViewById(R.id.demo_tvUserStatus)
        userAvatar = findViewById<Avatar>(R.id.demo_userAvatar).apply {
            setSize(Avatar.AvatarSize.L)
        }
        profileMetrics = findViewById(R.id.xingdun_profileMetrics)
        metricFriends = findViewById(R.id.xingdun_metricFriends)
        metricGroups = findViewById(R.id.xingdun_metricGroups)
        metricFavorites = findViewById(R.id.xingdun_metricFavorites)

        itemTheme = findViewById(R.id.demo_itemTheme)
        itemPrimaryColor = findViewById(R.id.demo_itemPrimaryColor)
        itemLanguage = findViewById(R.id.demo_itemLanguage)
        itemAddRule = findViewById(R.id.demo_itemAddRule)
        itemTranslateLanguage = findViewById(R.id.demo_itemTranslateLanguage)
        itemVoiceMessage = findViewById(R.id.demo_itemVoiceMessage)

        switchReadReceipt = findViewById(R.id.demo_switchReadReceipt)
        tvReadReceiptTitle = findViewById(R.id.demo_tvReadReceiptTitle)
        tvReadReceiptDesc = findViewById(R.id.demo_tvReadReceiptDesc)

        switchCallsTab = findViewById(R.id.demo_switchCallsTab)
        tvCallsTabTitle = findViewById(R.id.demo_tvCallsTabTitle)

        btnLogout = findViewById(R.id.demo_btnLogout)

        settingsGroup1 = findViewById(R.id.demo_settingsGroup1)
        settingsGroup2 = findViewById(R.id.demo_settingsGroup2)
        settingsGroupVoice = findViewById(R.id.demo_settingsGroupVoice)
        scrollContent = findViewById(R.id.demo_scrollContent)

        allDividers.addAll(listOf(
            findViewById(R.id.demo_dividerThemeColor),
            findViewById(R.id.demo_divider1),
            findViewById(R.id.demo_divider2),
            findViewById(R.id.demo_divider3),
            findViewById(R.id.demo_divider4)
        ))
        allSpacers.addAll(listOf(
            findViewById(R.id.demo_spacer1),
            findViewById(R.id.demo_spacer2),
            findViewById(R.id.demo_spacerVoice),
            findViewById(R.id.demo_spacer3),
            findViewById(R.id.demo_spacer4)
        ))

        setupSettingsItems()
        itemTranslateLanguage.visibility = View.GONE
        findViewById<View>(R.id.demo_divider4).visibility = View.GONE
        setupProductFeatureEntries()
        setupReadReceiptToggle()
        setupCallsTabToggle()
        setupLogout()
        setupUserProfileClick()
        setupMetricActions()
        observeUserInfo()
        applyThemeColors(themeStore.themeState.value.currentTheme.tokens.color)
    }

    private fun setupUserProfileClick() {
        val userProfileSection = findViewById<View>(R.id.demo_userProfileSection)
        val openSelfDetail: () -> Unit = {
            SelfDetailActivity.start(context)
        }
        userProfileSection?.setOnClickListener { openSelfDetail() }
        userAvatar.setOnAvatarClickListener { openSelfDetail() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        themeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        themeScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyThemeColors(state.currentTheme.tokens.color)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        themeScope?.cancel()
        themeScope = null
    }

    private fun applyThemeColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorTopBar)
        scrollContent.setBackgroundColor(colors.bgColorTopBar)

        findViewById<View>(R.id.demo_userProfileSection)?.setBackgroundColor(colors.bgColorOperate)
        profileMetrics.setBackgroundColor(colors.bgColorOperate)
        listOf(metricFriends, metricGroups, metricFavorites).forEach { it.setTextColor(colors.textColorPrimary) }
        findViewById<View>(R.id.xingdun_metricsSpacer)?.setBackgroundColor(colors.bgColorTopBar)
        tvUserName.setTextColor(colors.textColorPrimary)
        tvUserId.setTextColor(colors.textColorTertiary)
        tvUserStatus.setTextColor(colors.textColorTertiary)

        settingsGroup1.setBackgroundColor(colors.bgColorOperate)
        settingsGroup2.setBackgroundColor(colors.bgColorOperate)
        settingsGroupVoice.background = if (systemSettingsMode) {
            roundedBackground(colors.bgColorOperate, 18f)
        } else {
            GradientDrawable().apply { setColor(colors.bgColorOperate) }
        }

        for (divider in allDividers) {
            divider.setBackgroundColor(colors.strokeColorPrimary)
        }

        for (spacer in allSpacers) {
            spacer.setBackgroundColor(colors.bgColorTopBar)
        }

        val entryItems = listOf(
            itemTheme,
            itemPrimaryColor,
            itemLanguage,
            itemAddRule,
            itemTranslateLanguage,
            itemVoiceMessage,
        ) + productFeatureItems.map { it.first } + systemSettingsRows
        for (item in entryItems) {
            item.findViewById<TextView>(R.id.demo_tvSettingsTitle)?.setTextColor(colors.textColorSecondary)
            item.findViewById<TextView>(R.id.demo_tvSettingsValue)?.setTextColor(colors.textColorPrimary)
            item.findViewById<ImageView>(R.id.demo_ivArrow)?.setColorFilter(colors.textColorTertiary)
        }
        systemSettingsRows.forEach { row ->
            row.findViewById<TextView>(R.id.xingdun_mine_menu_title)?.setTextColor(colors.textColorPrimary)
            row.findViewById<ImageView>(R.id.xingdun_mine_menu_icon)?.setColorFilter(XINGDUN_GREEN)
            row.findViewById<ImageView>(R.id.xingdun_mine_menu_arrow)?.setColorFilter(colors.textColorTertiary)
            row.findViewWithTag<TextView>(SYSTEM_SETTINGS_VALUE_TAG)?.setTextColor(colors.textColorTertiary)
        }
        systemSettingsDividers.forEach { it.setBackgroundColor(colors.strokeColorPrimary) }
        updatePrimaryColorPreview(currentPrimaryColorHex(), colors.strokeColorPrimary)

        tvReadReceiptTitle.setTextColor(colors.textColorSecondary)
        tvReadReceiptDesc.setTextColor(colors.textColorTertiary)
        tvCallsTabTitle.setTextColor(colors.textColorSecondary)
        backButton?.setColorFilter(colors.textColorPrimary)

        btnLogout.setTextColor(colors.textColorError)
        btnLogout.compoundDrawableTintList = ColorStateList.valueOf(colors.textColorError)
        val logoutBg = GradientDrawable().apply {
            setColor(colors.bgColorInput)
            cornerRadius = 8f * resources.displayMetrics.density
        }
        btnLogout.background = logoutBg
    }

    fun setHeaderTitle(title: String) {
        pageHeader.setTitle(title)
    }

    /** Reuses the existing settings controls as the iOS-style My tab's child screen. */
    fun showAsSystemSettings(onBack: () -> Unit) {
        systemSettingsMode = true
        setHeaderTitle(context.getString(R.string.xingdun_system_settings))
        backButton = ImageView(context).apply {
            setImageResource(R.drawable.demo_ic_back)
            contentDescription = context.getString(R.string.xingdun_back)
            setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp())
            setOnClickListener { onBack() }
            layoutParams = FrameLayout.LayoutParams(44.dp(), 44.dp())
        }.also { button ->
            pageHeader.setLeftAction(button)
        }

        findViewById<View>(R.id.demo_userProfileSection).visibility = View.GONE
        findViewById<View>(R.id.demo_spacer1).visibility = View.GONE
        profileMetrics.visibility = View.GONE
        findViewById<View>(R.id.xingdun_metricsSpacer).visibility = View.GONE
        settingsGroup1.visibility = View.GONE
        settingsGroup2.visibility = View.GONE
        findViewById<View>(R.id.demo_spacer2).visibility = View.GONE
        findViewById<View>(R.id.demo_spacer4).visibility = View.GONE
        rebuildSystemSettingsMenu()
        btnLogout.setCompoundDrawablesWithIntrinsicBounds(R.drawable.xingdun_ic_settings_logout, 0, 0, 0)
        btnLogout.compoundDrawablePadding = 10.dp()
        btnLogout.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        btnLogout.setPadding(16.dp(), 10.dp(), 16.dp(), 10.dp())
        btnLogout.setOnClickListener { confirmSystemSettingsLogout() }
        applyThemeColors(themeStore.themeState.value.currentTheme.tokens.color)
    }

    private fun rebuildSystemSettingsMenu() {
        systemSettingsRows.clear()
        systemSettingsDividers.clear()
        settingsGroupVoice.removeAllViews()
        settingsGroupVoice.layoutParams = LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
        ).apply {
            marginStart = 16.dp()
            marginEnd = 16.dp()
        }

        val entries = listOf(
            Triple(R.string.xingdun_account_security, R.drawable.xingdun_ic_settings_account, XingDunFeatureActivity.MODE_ACCOUNT_SECURITY),
            Triple(R.string.xingdun_notification_settings, R.drawable.xingdun_ic_notification_bell, XingDunFeatureActivity.MODE_NOTIFICATIONS),
            Triple(R.string.demo_settings_language, R.drawable.demo_ic_login_language, XingDunFeatureActivity.MODE_LANGUAGE),
            Triple(R.string.xingdun_storage_management, R.drawable.xingdun_ic_storage_file, XingDunFeatureActivity.MODE_STORAGE),
            Triple(R.string.xingdun_permission_management, R.drawable.xingdun_ic_mine_privacy, XingDunFeatureActivity.MODE_PERMISSIONS),
        )
        entries.forEachIndexed { index, (title, icon, mode) ->
            val row = LayoutInflater.from(context).inflate(R.layout.xingdun_item_mine_menu, settingsGroupVoice, false)
            row.layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 48.dp())
            row.findViewById<ImageView>(R.id.xingdun_mine_menu_icon).apply {
                setImageResource(icon)
                layoutParams = layoutParams.apply {
                    width = 20.dp()
                    height = 20.dp()
                }
            }
            row.findViewById<TextView>(R.id.xingdun_mine_menu_title).apply {
                setText(title)
                textSize = 15f
                setTypeface(typeface, Typeface.NORMAL)
            }
            if (mode == XingDunFeatureActivity.MODE_LANGUAGE) {
                val arrow = row.findViewById<ImageView>(R.id.xingdun_mine_menu_arrow)
                val value = TextView(context).apply {
                    text = getCurrentLanguageDisplayName()
                    textSize = 14f
                    maxLines = 1
                    tag = SYSTEM_SETTINGS_VALUE_TAG
                }
                (row as LinearLayout).addView(
                    value,
                    row.indexOfChild(arrow),
                    LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT),
                )
            }
            row.setOnClickListener { XingDunFeatureActivity.start(context, mode) }
            settingsGroupVoice.addView(row)
            systemSettingsRows += row
            if (index < entries.lastIndex) {
                val divider = View(context)
                settingsGroupVoice.addView(
                    divider,
                    LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1.dp()).apply {
                        marginStart = 52.dp()
                        marginEnd = 18.dp()
                    },
                )
                systemSettingsDividers += divider
            }
        }
    }

    private fun confirmSystemSettingsLogout() {
        AlertDialog.Builder(context)
            .setTitle(R.string.xingdun_logout_confirm_title)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.demo_logout) { _, _ ->
                XingDunTenantSessionCoordinator.logout(::openLogin)
            }
            .show()
    }

    private fun setupSettingsItems() {
        setupEntryItem(
            itemTheme,
            context.getString(R.string.demo_settings_theme),
            currentThemeDisplayName()
        ) {
            showThemeSelector()
        }

        setupEntryItem(
            itemPrimaryColor,
            context.getString(R.string.demo_settings_primary_color),
            ""
        ) {
            showPrimaryColorPicker()
        }
        itemPrimaryColor.findViewById<TextView>(R.id.demo_tvSettingsValue)?.visibility = View.GONE
        itemPrimaryColor.findViewById<View>(R.id.demo_vSettingsColorPreview)?.visibility = View.VISIBLE
        updatePrimaryColorPreview(currentPrimaryColorHex())

        setupEntryItem(
            itemLanguage,
            context.getString(R.string.demo_settings_language),
            getCurrentLanguageDisplayName()
        ) {
            XingDunFeatureActivity.start(context, XingDunFeatureActivity.MODE_LANGUAGE)
        }

        setupEntryItem(
            itemAddRule,
            context.getString(R.string.demo_settings_add_rule),
            context.getString(R.string.demo_settings_allow_type_need_confirm)
        ) {
            showFriendAddRuleSelector()
        }

        setupEntryItem(
            itemTranslateLanguage,
            context.getString(R.string.demo_settings_translate_target_language),
            getTranslateLanguageDisplayName(translateTargetLanguage)
        ) {
            showTranslateLanguageSelector()
        }

        setupEntryItem(
            itemVoiceMessage,
            context.getString(R.string.demo_voice_message_settings),
            ""
        ) {
            VoiceMessageSettingActivity.start(context)
        }
    }

    private fun setupEntryItem(
        view: View,
        title: String,
        value: String,
        onClick: () -> Unit
    ) {
        view.findViewById<TextView>(R.id.demo_tvSettingsTitle).text = title
        view.findViewById<TextView>(R.id.demo_tvSettingsValue).text = value
        view.setOnClickListener { onClick() }
    }

    private fun setupProductFeatureEntries() {
        settingsGroupVoice.visibility = View.VISIBLE
        findViewById<View>(R.id.demo_spacerVoice).visibility = View.VISIBLE
        val session = XingDunSessionManager.currentSession()
        setupEntryItem(
            itemVoiceMessage,
            context.getString(R.string.xingdun_switch_enterprise),
            session?.let { "${it.companyName} (${it.companyCode})" }.orEmpty()
        ) {
            confirmSwitchEnterprise()
        }
        val entries = buildList {
            add(R.string.xingdun_reports to XingDunFeatureActivity.MODE_REPORTS)
            if (session?.features?.messageFavorite == true) {
                add(R.string.xingdun_message_favorites to XingDunFeatureActivity.MODE_FAVORITES)
            }
            if (session?.features?.redpacket == true) {
                add(R.string.xingdun_redpacket_account to XingDunFeatureActivity.MODE_REDPACKET_ACCOUNT)
            }
            add(R.string.xingdun_personal_qr to XingDunFeatureActivity.MODE_PERSONAL_QR)
            add(R.string.xingdun_scan_qr to XingDunFeatureActivity.MODE_QR_SCANNER)
            add(R.string.xingdun_invite_title to XingDunFeatureActivity.MODE_INVITE)
            add(R.string.xingdun_help_center to XingDunFeatureActivity.MODE_HELP)
            if (session?.features?.customerService == true) {
                add(R.string.xingdun_customer_service to XingDunFeatureActivity.MODE_CUSTOMER_SERVICE)
            }
            add(R.string.xingdun_feedback to XingDunFeatureActivity.MODE_FEEDBACK)
            add(R.string.xingdun_account_security to XingDunFeatureActivity.MODE_ACCOUNT_SECURITY)
            add(R.string.xingdun_notification_settings to XingDunFeatureActivity.MODE_NOTIFICATIONS)
            add(R.string.xingdun_storage_management to XingDunFeatureActivity.MODE_STORAGE)
            add(R.string.xingdun_permission_management to XingDunFeatureActivity.MODE_PERMISSIONS)
            add(R.string.xingdun_user_agreement to XingDunFeatureActivity.MODE_USER_AGREEMENT)
            add(R.string.xingdun_privacy_policy to XingDunFeatureActivity.MODE_PRIVACY_POLICY)
            add(R.string.xingdun_about to XingDunFeatureActivity.MODE_ABOUT)
        }
        entries.forEach { (title, mode) ->
            val entry = LayoutInflater.from(context).inflate(
                R.layout.demo_item_settings_entry,
                settingsGroupVoice,
                false
            ).also {
                settingsGroupVoice.addView(it)
                productFeatureItems.add(it to mode)
            }
            setupEntryItem(entry, context.getString(title), "") {
                XingDunFeatureActivity.start(context, mode)
            }
        }
    }

    private fun updateEntryValue(view: View, value: String) {
        view.findViewById<TextView>(R.id.demo_tvSettingsValue).text = value
    }

    private fun setupReadReceiptToggle() {
        tvReadReceiptTitle.text = context.getString(R.string.demo_settings_read_receipt)
        switchReadReceipt.setChecked(enableReadReceipt)
        updateReadReceiptDescription()

        switchReadReceipt.setOnCheckedChangeListener { isChecked ->
            enableReadReceipt = isChecked
            AppBuilderConfig.enableReadReceipt = isChecked
            MMKV.defaultMMKV().encode(KEY_ENABLE_READ_RECEIPT, isChecked)
            updateReadReceiptDescription()
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun roundedBackground(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun updateReadReceiptDescription() {
        tvReadReceiptDesc.text = if (enableReadReceipt) {
            context.getString(R.string.demo_settings_read_receipt_enabled_desc)
        } else {
            context.getString(R.string.demo_settings_read_receipt_disabled_desc)
        }
    }

    private fun setupCallsTabToggle() {
        tvCallsTabTitle.text = context.getString(R.string.xingdun_workspace_available)
        switchCallsTab.setChecked(true)
        switchCallsTab.isEnabled = false
    }

    private fun setupLogout() {
        btnLogout.setOnClickListener {
            XingDunTenantSessionCoordinator.logout(::openLogin)
        }
    }

    private fun confirmSwitchEnterprise() {
        AlertDialog.Builder(context)
            .setTitle(R.string.xingdun_switch_enterprise)
            .setMessage(R.string.xingdun_switch_enterprise_confirmation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.xingdun_switch_enterprise) { _, _ ->
                XingDunTenantSessionCoordinator.switchEnterprise(::openEnterpriseSelection)
            }
            .show()
    }

    private fun openLogin() {
        val activity = context
        if (activity is AppCompatActivity) {
            activity.startActivity(Intent(activity, XingDunLaunchActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
        }
    }

    private fun openEnterpriseSelection() {
        val activity = context
        if (activity is AppCompatActivity) {
            activity.startActivity(Intent(activity, XingDunEnterpriseAccessActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
        }
    }

    private fun observeUserInfo() {
        val activity = context
        if (activity is LifecycleOwner) {
            activity.lifecycle.addObserver(object : LifecycleEventObserver {
                override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                    when (event) {
                        Lifecycle.Event.ON_START -> startObserving()
                        Lifecycle.Event.ON_STOP -> stopObserving()
                        else -> {}
                    }
                }
            })
        } else {
            startObserving()
        }
    }

    private fun startObserving() {
        coroutineScope = CoroutineScope(Dispatchers.Main)
        userInfoJob = coroutineScope?.launch {
            LoginStore.shared.loginState.loginUserInfo.collectLatest { userInfo ->
                updateUserProfile(userInfo)
            }
        }
        coroutineScope?.launch { refreshMetrics() }
    }

    private fun stopObserving() {
        userInfoJob?.cancel()
        userInfoJob = null
        coroutineScope?.cancel()
        coroutineScope = null
    }

    private fun updateUserProfile(userInfo: UserProfile?) {
        val displayName = userInfo?.let {
            if (!it.nickname.isNullOrEmpty()) it.nickname else it.userID
        } ?: ""

        tvUserName.text = displayName
        tvUserId.text = "ID：${userInfo?.userID ?: ""}"
        tvUserStatus.text = "${context.getString(R.string.demo_settings_self_detail_status)}：${userInfo?.selfSignature ?: ""}"
        userAvatar.setContent(
            Avatar.AvatarContent.Image(
                url = userInfo?.avatarURL,
                fallbackName = displayName
            )
        )

        updateFriendAddRuleDisplay(userInfo?.allowType)
    }

    private fun setupMetricActions() {
        metricFriends.setOnClickListener { (context as? MainActivity)?.selectTabByName(MainActivity.TAB_CONTACTS) }
        metricGroups.setOnClickListener { (context as? MainActivity)?.selectTabByName(MainActivity.TAB_CONTACTS) }
        metricFavorites.setOnClickListener {
            if (XingDunSessionManager.currentSession()?.features?.messageFavorite == true) {
                XingDunFeatureActivity.start(context, XingDunFeatureActivity.MODE_FAVORITES)
            }
        }
        renderMetrics(null)
    }

    private suspend fun refreshMetrics() {
        val session = XingDunSessionManager.currentSession()
        val favoriteCount = if (session?.features?.messageFavorite == true) {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    session,
                    "message/favorites",
                    mapOf("page" to "1", "page_size" to "1"),
                    JsonObject::class.java
                ).get("total")?.asInt
            }.getOrNull()
        } else null
        renderMetrics(favoriteCount)
    }

    private fun renderMetrics(favoriteCount: Int?) {
        val friendCount = runCatching { ContactStore.shared.state.friendList.value.size }.getOrDefault(0)
        val groupCount = runCatching { GroupStore.shared.state.joinedGroupList.value.size }.getOrDefault(0)
        metricFriends.text = context.getString(R.string.xingdun_metric_friends, friendCount)
        metricGroups.text = context.getString(R.string.xingdun_metric_groups, groupCount)
        metricFavorites.text = context.getString(R.string.xingdun_metric_favorites, favoriteCount ?: 0)
        metricFavorites.alpha = if (XingDunSessionManager.currentSession()?.features?.messageFavorite == true) 1f else 0.55f
    }

    private fun updateFriendAddRuleDisplay(allowType: AllowType?) {
        val value = when (allowType) {
            AllowType.ALLOW_ANY -> context.getString(R.string.demo_settings_allow_type_allow_any)
            AllowType.DENY_ANY -> context.getString(R.string.demo_settings_allow_type_deny_any)
            AllowType.NEED_CONFIRM -> context.getString(R.string.demo_settings_allow_type_need_confirm)
            else -> context.getString(R.string.demo_settings_allow_type_need_confirm)
        }
        updateEntryValue(itemAddRule, value)
    }

    private fun currentThemeDisplayName(): String {
        return when (themeStore.themeState.value.currentTheme.id) {
            Theme.DARK_THEME_ID -> context.getString(R.string.demo_settings_theme_dark)
            Theme.SYSTEM_THEME_ID -> context.getString(R.string.demo_settings_theme_system)
            Theme.LIGHT_THEME_ID -> context.getString(R.string.demo_settings_theme_light)
            else -> context.getString(R.string.demo_settings_theme_system)
        }
    }

    private fun showThemeSelector() {
        val options = listOf(
            ActionItem(text = context.getString(R.string.demo_settings_theme_system), value = AppConstants.THEME_MODE_SYSTEM),
            ActionItem(text = context.getString(R.string.demo_settings_theme_light), value = AppConstants.THEME_MODE_LIGHT),
            ActionItem(text = context.getString(R.string.demo_settings_theme_dark), value = AppConstants.THEME_MODE_DARK)
        )
        ActionSheet.show(context, options) { selected ->
            val mode = selected.value as Int
            updateEntryValue(itemTheme, selected.text)
            when (mode) {
                AppConstants.THEME_MODE_SYSTEM -> themeStore.setTheme(Theme.systemTheme(context))
                AppConstants.THEME_MODE_LIGHT -> themeStore.setTheme(Theme.lightTheme(context))
                AppConstants.THEME_MODE_DARK -> themeStore.setTheme(Theme.darkTheme(context))
            }
        }
    }

    private fun showPrimaryColorPicker() {
        PrimaryColorPickerDialog.show(
            context = context,
            selectedHex = currentPrimaryColorHex()
        ) { hex ->
            themeStore.setPrimaryColor(hex)
            AppBuilderConfig.primaryColor = hex
            updatePrimaryColorPreview(hex)
        }
    }

    private fun updatePrimaryColorPreview(
        hex: String,
        strokeColor: Int = themeStore.themeState.value.currentTheme.tokens.color.strokeColorPrimary
    ) {
        val preview = itemPrimaryColor.findViewById<View>(R.id.demo_vSettingsColorPreview) ?: return
        val color = try {
            android.graphics.Color.parseColor(PrimaryColorPickerDialog.normalizeHex(hex))
        } catch (_: IllegalArgumentException) {
            android.graphics.Color.parseColor(DEFAULT_PRIMARY_COLOR)
        }
        val density = resources.displayMetrics.density
        preview.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke((1.5f * density).toInt(), strokeColor)
        }
    }

    private fun currentPrimaryColorHex(): String {
        val persisted = themePersistUtil.getCustomPrimaryColor()
        val fallback = AppBuilderConfig.primaryColor
        return PrimaryColorPickerDialog.normalizeHex(persisted ?: fallback)
    }

    private fun showFriendAddRuleSelector() {
        val options = listOf(
            ActionItem(text = context.getString(R.string.demo_settings_allow_type_allow_any), value = AllowType.ALLOW_ANY),
            ActionItem(text = context.getString(R.string.demo_settings_allow_type_deny_any), value = AllowType.DENY_ANY),
            ActionItem(text = context.getString(R.string.demo_settings_allow_type_need_confirm), value = AllowType.NEED_CONFIRM)
        )
        ActionSheet.show(context, options) { selected ->
            val allowType = selected.value as AllowType
            val userProfile = UserProfile().apply {
                this.allowType = allowType
            }
            LoginStore.shared.setSelfInfo(userProfile, object : CompletionHandler {
                override fun onSuccess() {
                    updateEntryValue(itemAddRule, selected.text)
                }

                override fun onFailure(code: Int, desc: String) {
                }
            })
        }
    }

    private fun showTranslateLanguageSelector() {
        val options = translateLanguageOptions.map { (code, name) ->
            ActionItem(text = name, value = code)
        }
        ActionSheet.show(context, options) { selected ->
            val code = selected.value as String
            translateTargetLanguage = code
            AppBuilderConfig.translateTargetLanguage = code
            updateEntryValue(itemTranslateLanguage, selected.text)
        }
    }

    private fun getTranslateLanguageDisplayName(code: String): String {
        return translateLanguageOptions.find { it.first == code }?.second ?: code
    }

    private fun getCurrentLanguageDisplayName(): String {
        val persistedTag = MMKV.defaultMMKV().decodeString(KEY_APP_LANGUAGE, "").orEmpty()
        val currentTag = if (persistedTag.isNotBlank()) {
            persistedTag
        } else {
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }
        return when {
            currentTag.isBlank() -> context.getString(R.string.demo_settings_current_language)
            isTraditionalChinese(currentTag) -> context.getString(R.string.demo_settings_zh_hant)
            currentTag.startsWith("zh", ignoreCase = true) -> context.getString(R.string.demo_settings_zh_hans)
            currentTag.startsWith("en", ignoreCase = true) -> context.getString(R.string.demo_settings_en)
            currentTag.startsWith("ar", ignoreCase = true) -> context.getString(R.string.demo_settings_ar)
            else -> context.getString(R.string.demo_settings_current_language)
        }
    }

    private fun isTraditionalChinese(languageTag: String): Boolean {
        val normalizedTag = languageTag.lowercase()
        return normalizedTag.contains("hant") ||
            normalizedTag.contains("zh-hk") ||
            normalizedTag.contains("zh-tw") ||
            normalizedTag.contains("zh-mo")
    }

    companion object {
        private const val KEY_ENABLE_READ_RECEIPT = AppConstants.KEY_ENABLE_READ_RECEIPT
        private const val KEY_APP_LANGUAGE = AppConstants.KEY_APP_LANGUAGE
        private const val DEFAULT_PRIMARY_COLOR = "#1C66E5"
        private const val SYSTEM_SETTINGS_VALUE_TAG = "xingdun_system_settings_value"
        private const val XINGDUN_GREEN = 0xFF23B39C.toInt()
    }
}
