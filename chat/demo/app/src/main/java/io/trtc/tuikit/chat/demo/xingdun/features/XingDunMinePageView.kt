package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.gson.JsonObject
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.demo.settings.SelfDetailActivity
import io.trtc.tuikit.chat.demo.settings.XingDunSystemSettingsActivity
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.pages.PageHeaderView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** iOS-aligned root for the My tab. Detailed settings remain in child screens. */
class XingDunMinePageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private data class MenuItem(
        val titleRes: Int,
        val iconRes: Int,
        val action: () -> Unit,
    )

    private val header: PageHeaderView
    private val content: LinearLayout
    private val profileCard: View
    private val avatarRing: View
    private val avatar: Avatar
    private val name: TextView
    private val account: TextView
    private val profileArrow: ImageView
    private val statsCard: View
    private val friends: TextView
    private val groups: TextView
    private val favorites: TextView
    private val statDividers: List<View>
    private val menuGroups: List<LinearLayout>
    private val menuRows = mutableListOf<View>()
    private val menuDividers = mutableListOf<View>()
    private val themeStore = ThemeStore.shared(context)

    private var scope: CoroutineScope? = null
    private var userJob: Job? = null
    private var favoriteCount: Int? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.xingdun_page_mine, this, true)

        header = findViewById(R.id.xingdun_mine_header)
        content = findViewById(R.id.xingdun_mine_content)
        profileCard = findViewById(R.id.xingdun_mine_profile_card)
        avatarRing = findViewById(R.id.xingdun_mine_avatar_ring)
        avatar = findViewById<Avatar>(R.id.xingdun_mine_avatar).apply {
            setSize(Avatar.AvatarSize.XL)
            setShape(Avatar.AvatarShape.RoundRectangle)
        }
        name = findViewById(R.id.xingdun_mine_name)
        account = findViewById(R.id.xingdun_mine_account)
        profileArrow = findViewById(R.id.xingdun_mine_profile_arrow)
        statsCard = findViewById(R.id.xingdun_mine_stats_card)
        friends = findViewById(R.id.xingdun_mine_friends)
        groups = findViewById(R.id.xingdun_mine_groups)
        favorites = findViewById(R.id.xingdun_mine_favorites)
        statDividers = listOf(
            findViewById(R.id.xingdun_mine_stats_divider_1),
            findViewById(R.id.xingdun_mine_stats_divider_2),
        )
        menuGroups = listOf(
            findViewById(R.id.xingdun_mine_quick_group),
            findViewById(R.id.xingdun_mine_help_group),
            findViewById(R.id.xingdun_mine_settings_group),
        )

        header.setTitle(context.getString(R.string.demo_page_me_title))
        setupProfile()
        setupStats()
        setupMenus()
        observeLifecycle()
        applyTheme(themeStore.themeState.value.currentTheme.tokens.color)
    }

    private fun setupProfile() {
        val openProfile = { SelfDetailActivity.start(context) }
        profileCard.setOnClickListener { openProfile() }
        avatar.setOnAvatarClickListener { openProfile() }
    }

    private fun setupStats() {
        friends.setOnClickListener { openMainTab(MainActivity.TAB_CONTACTS) }
        groups.setOnClickListener { XingDunGroupListActivity.start(context) }
        favorites.setOnClickListener {
            if (XingDunSessionManager.currentSession()?.features?.messageFavorite == true) {
                XingDunFeatureActivity.start(context, XingDunFeatureActivity.MODE_FAVORITES)
            }
        }
        renderStats(null)
    }

    private fun setupMenus() {
        populateGroup(
            menuGroups[0],
            buildList {
                if (XingDunSessionManager.currentSession()?.features?.redpacket == true) {
                    add(menu(R.string.xingdun_redpacket_account, R.drawable.xingdun_ic_gift_white, XingDunFeatureActivity.MODE_REDPACKET_ACCOUNT))
                }
                add(menu(R.string.xingdun_reports, R.drawable.xingdun_ic_mine_report, XingDunFeatureActivity.MODE_REPORTS))
                add(menu(R.string.xingdun_personal_qr, R.drawable.xingdun_ic_mine_qr, XingDunFeatureActivity.MODE_PERSONAL_QR))
                add(menu(R.string.xingdun_share_poster, R.drawable.xingdun_ic_mine_poster, XingDunFeatureActivity.MODE_INVITE))
            },
        )
        populateGroup(
            menuGroups[1],
            buildList {
                add(menu(R.string.xingdun_help_center_title, R.drawable.xingdun_ic_mine_help, XingDunFeatureActivity.MODE_HELP))
                add(menu(R.string.xingdun_feedback, R.drawable.xingdun_ic_mine_feedback, XingDunFeatureActivity.MODE_FEEDBACK))
                if (XingDunSessionManager.currentSession()?.features?.customerService == true) {
                    add(MenuItem(R.string.xingdun_contact_enterprise_support, R.drawable.xingdun_ic_mine_customer_service) {
                        openEnterpriseCustomerService()
                    })
                }
                add(menu(R.string.xingdun_user_agreement, R.drawable.xingdun_ic_mine_document, XingDunFeatureActivity.MODE_USER_AGREEMENT))
                add(menu(R.string.xingdun_privacy_policy, R.drawable.xingdun_ic_mine_privacy, XingDunFeatureActivity.MODE_PRIVACY_POLICY))
            },
        )
        populateGroup(
            menuGroups[2],
            listOf(
                MenuItem(R.string.xingdun_system_settings, R.drawable.xingdun_ic_mine_settings) {
                    XingDunSystemSettingsActivity.start(context)
                },
                menu(R.string.xingdun_about_platform, R.drawable.xingdun_ic_mine_info, XingDunFeatureActivity.MODE_ABOUT),
            ),
        )
    }

    private fun menu(titleRes: Int, iconRes: Int, mode: String) = MenuItem(titleRes, iconRes) {
        XingDunFeatureActivity.start(context, mode)
    }

    private fun populateGroup(container: LinearLayout, items: List<MenuItem>) {
        items.forEachIndexed { index, item ->
            val row = LayoutInflater.from(context).inflate(R.layout.xingdun_item_mine_menu, container, false)
            row.findViewById<ImageView>(R.id.xingdun_mine_menu_icon).setImageResource(item.iconRes)
            row.findViewById<TextView>(R.id.xingdun_mine_menu_title).setText(item.titleRes)
            row.contentDescription = context.getString(item.titleRes)
            row.setOnClickListener { item.action() }
            container.addView(row)
            menuRows += row

            if (index < items.lastIndex) {
                val divider = View(context)
                container.addView(
                    divider,
                    LayoutParams(LayoutParams.MATCH_PARENT, 1.dp()).apply {
                        marginStart = 52.dp()
                        marginEnd = 18.dp()
                    },
                )
                menuDividers += divider
            }
        }
    }

    private fun observeLifecycle() {
        (context as? LifecycleOwner)?.lifecycle?.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_START -> startObserving()
                    Lifecycle.Event.ON_STOP -> stopObserving()
                    else -> Unit
                }
            }
        }) ?: startObserving()
    }

    private fun startObserving() {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        userJob = scope?.launch {
            LoginStore.shared.loginState.loginUserInfo.collectLatest(::renderProfile)
        }
        scope?.launch {
            themeStore.themeState.collectLatest { applyTheme(it.currentTheme.tokens.color) }
        }
        scope?.launch {
            combine(
                ContactStore.shared.state.friendList,
                GroupStore.shared.state.joinedGroupList,
            ) { friends, groups -> friends.size to groups.size }
                .collectLatest { renderStats(favoriteCount) }
        }
        scope?.launch { refreshStats() }
    }

    private fun stopObserving() {
        userJob?.cancel()
        userJob = null
        scope?.cancel()
        scope = null
    }

    private fun renderProfile(profile: UserProfile?) {
        val session = XingDunSessionManager.currentSession()
        val displayName = profile?.nickname?.takeIf(String::isNotBlank)
            ?: session?.nickname?.takeIf(String::isNotBlank)
            ?: profile?.userID.orEmpty()
        val loginName = session?.username?.takeIf(String::isNotBlank) ?: displayName
        name.text = displayName
        account.text = context.getString(R.string.xingdun_login_username_format, loginName)
        avatar.setContent(
            Avatar.AvatarContent.Image(
                url = profile?.avatarURL,
                fallbackName = displayName,
            ),
        )
    }

    private suspend fun refreshStats() {
        val session = XingDunSessionManager.currentSession()
        val favoriteCount = if (session?.features?.messageFavorite == true) {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    session,
                    "message/favorites",
                    mapOf("page" to "1", "page_size" to "1"),
                    JsonObject::class.java,
                ).get("total")?.asInt
            }.getOrNull()
        } else {
            null
        }
        this.favoriteCount = favoriteCount
        renderStats(favoriteCount)
    }

    private fun openEnterpriseCustomerService() {
        val session = XingDunSessionManager.currentSession() ?: run {
            Toast.makeText(context, R.string.xingdun_session_expired, Toast.LENGTH_SHORT).show()
            return
        }
        val activeScope = scope ?: return
        Toast.makeText(context, R.string.xingdun_loading, Toast.LENGTH_SHORT).show()
        activeScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    session,
                    "cs/identity",
                    emptyMap(),
                    JsonObject::class.java,
                )
            }.onSuccess { identity ->
                val declaredEnabled = identity.get("customer_service_enabled")
                    ?.takeUnless { it.isJsonNull }
                    ?.let { runCatching { it.asBoolean }.getOrNull() }
                val official = identity.get("official_cs_tim_user_id")
                    ?.takeUnless { it.isJsonNull }
                    ?.asString
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                val ordinaryEntryEnabled = identity.get("ordinary_entry_enabled")
                    ?.takeUnless { it.isJsonNull }
                    ?.let { runCatching { it.asBoolean }.getOrNull() }
                    ?: false
                val enabled = declaredEnabled
                    ?: identity.get("is_cs")?.takeUnless { it.isJsonNull }
                        ?.let { runCatching { it.asBoolean }.getOrNull() }
                    ?: (official != null)
                if (enabled && ordinaryEntryEnabled && official != null) {
                    XingDunFeatureActivity.start(context, XingDunFeatureActivity.MODE_FRIEND_SEARCH, official)
                } else {
                    Toast.makeText(context, R.string.xingdun_customer_service_not_configured, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(context, R.string.xingdun_customer_service_load_retry, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderStats(favoriteCount: Int?) {
        val friendCount = runCatching { ContactStore.shared.state.friendList.value.size }.getOrDefault(0)
        val groupCount = runCatching { GroupStore.shared.state.joinedGroupList.value.size }.getOrDefault(0)
        friends.text = context.getString(R.string.xingdun_metric_friends, friendCount)
        groups.text = context.getString(R.string.xingdun_metric_groups, groupCount)
        favorites.text = context.getString(R.string.xingdun_metric_favorites, favoriteCount ?: 0)
        favorites.alpha = if (XingDunSessionManager.currentSession()?.features?.messageFavorite == true) 1f else 0.55f
    }

    private fun applyTheme(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorTopBar)
        content.setBackgroundColor(colors.bgColorTopBar)
        val cards = listOf(profileCard, statsCard) + menuGroups
        cards.forEach { it.background = roundedBackground(colors.bgColorOperate, 18f) }
        avatarRing.background = roundedBackground(Color.TRANSPARENT, 16f, XINGDUN_GREEN, 2f)
        name.setTextColor(colors.textColorPrimary)
        account.setTextColor(colors.textColorTertiary)
        profileArrow.setColorFilter(colors.textColorTertiary)
        listOf(friends, groups, favorites).forEach { it.setTextColor(XINGDUN_GREEN) }
        statDividers.forEach { it.setBackgroundColor(colors.strokeColorPrimary) }
        menuRows.forEach { row ->
            row.findViewById<TextView>(R.id.xingdun_mine_menu_title).setTextColor(colors.textColorPrimary)
            row.findViewById<ImageView>(R.id.xingdun_mine_menu_icon).setColorFilter(XINGDUN_GREEN)
            row.findViewById<ImageView>(R.id.xingdun_mine_menu_arrow).setColorFilter(colors.textColorTertiary)
        }
        menuDividers.forEach { it.setBackgroundColor(colors.strokeColorPrimary) }
    }

    private fun roundedBackground(color: Int, radiusDp: Float, strokeColor: Int? = null, strokeDp: Float = 0f) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
            if (strokeColor != null && strokeDp > 0f) {
                setStroke((strokeDp * resources.displayMetrics.density).toInt(), strokeColor)
            }
        }

    private fun openMainTab(tab: String) {
        (context as? MainActivity)?.selectTabByName(tab)
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private companion object {
        val XINGDUN_GREEN: Int = Color.rgb(35, 179, 156)
    }
}
