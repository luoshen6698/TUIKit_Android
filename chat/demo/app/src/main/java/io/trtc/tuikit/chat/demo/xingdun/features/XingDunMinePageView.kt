package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.*
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.main.MainActivity
import io.trtc.tuikit.chat.demo.settings.SelfDetailActivity
import io.trtc.tuikit.chat.demo.settings.XingDunSystemSettingsActivity
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi
import io.trtc.tuikit.chat.demo.xingdun.features.travel.safeTravelMedia
import io.trtc.tuikit.chat.demo.xingdun.features.travel.opt
import io.trtc.tuikit.chat.demo.xingdun.launch.XingDunLaunchActivity
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunTenantSessionCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import java.util.Locale

internal fun shouldOpenCustomerServiceChat(isKnownFriend: Boolean, relationship: String?): Boolean =
    isKnownFriend || relationship?.trim() in setOf("friend", "blocked")

internal fun mineCanUseIMData(currentId: String?, profileId: String?) = !currentId.isNullOrBlank() && currentId == profileId

internal fun mineMetric(count: Int): String = when {
    count >= 10000 -> "9999+"
    count >= 1000 -> String.format(Locale.US, "%.1fk", count / 1000.0)
    else -> count.coerceAtLeast(0).toString()
}

/** Reference Android My drawer. Shared settings and IM operations keep their native implementation. */
class XingDunMinePageView(context: Context, private val close: () -> Unit, private val markRead: () -> Unit, private val createTeam: () -> Unit) : LinearLayout(context) {
    private val ui = TravelUi(context)
    private val refresh = SwipeRefreshLayout(context)
    private val scroll = ScrollView(context)
    private val content = ui.column()
    private val profileCard = ui.column(20)
    private val stats = ui.row()
    private var favoriteCount: Int? = null
    private var cacheBytes: Long? = null
    private var backendProfile: JsonObject? = null
    private var active = false
    private var ownerKey = key()
    private var observer: Job? = null
    private var request: Job? = null
    private var customerServiceRequest: Job? = null
    private var storageValue: TextView? = null
    private var storageProgress: ProgressBar? = null
    private fun key() = XingDunSessionManager.currentUiSession()?.scopeKey
    private fun d(v: Int) = ui.d(v)
    private fun s(id: Int) = context.getString(id)
    private fun text(value: String, color: Int = INK) = ui.text(value, 12f, color).apply { textSize = 12f; typeface = Typeface.DEFAULT; minHeight = d(18); gravity = Gravity.CENTER_VERTICAL }
    private fun label(id: Int, color: Int = INK) = text(s(id), color)
    private fun icon(code: Int, size: Int = 20, color: Int = BLUE) = ui.icon(code, size, color).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    private fun box(color: Int = WHITE, radius: Int = 12) = ui.box(color, radius)
    private fun tint(color: Int) = (color and 0x00FFFFFF) or 0x1A000000
    private fun navigate(hide: Boolean = false, action: () -> Unit) { if (hide) close(); action() }
    private fun feature(mode: String) = XingDunFeatureActivity.start(context, mode)

    init {
        orientation = VERTICAL; setBackgroundColor(BG)
        addView(FrameLayout(context).apply {
            addView(icon(0xe16a, 24, INK).apply { contentDescription = s(R.string.md_close); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES; isFocusable = true; setOnClickListener { close() } }, FrameLayout.LayoutParams(d(48), d(48), Gravity.LEFT))
        }, LayoutParams(-1, d(48)))
        scroll.id = R.id.md_drawer_scroll
        scroll.isFillViewport = false; scroll.isVerticalScrollBarEnabled = false
        scroll.addView(content, FrameLayout.LayoutParams(-1, -2)); refresh.addView(scroll)
        refresh.setColorSchemeColors(BLUE); refresh.setOnRefreshListener { refreshData() }
        addView(refresh, LayoutParams(-1, 0, 1f))
        buildContent()
    }
    fun setActive(value: Boolean) {
        active = value
        if (value) { checkOwner(); renderProfile(); renderStats(); refreshData() }
        else { request?.cancel(); customerServiceRequest?.cancel(); refresh.isRefreshing = false }
    }
    private fun checkOwner() {
        if (ownerKey != key()) { ownerKey = key(); favoriteCount = null; cacheBytes = null; backendProfile = null; content.removeAllViews(); buildContent(); scroll.scrollTo(0, 0); renderStorage() }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val owner = context as? LifecycleOwner ?: return
        observer = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { LoginStore.shared.loginState.loginUserInfo.collect { checkOwner(); renderProfile(); renderStats() } }
                launch { combine(ContactStore.shared.state.friendList, GroupStore.shared.state.joinedGroupList) { f, g -> f.size to g.size }.collect { renderStats() } }
            }
        }
    }
    override fun onDetachedFromWindow() { observer?.cancel(); request?.cancel(); customerServiceRequest?.cancel(); super.onDetachedFromWindow() }
    private fun buildContent() {
        content.setPadding(d(16), d(12), d(16), d(32))
        profileCard.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0x1F4C6EF5, 0x0A4C6EF5)).apply { cornerRadius = d(16).toFloat(); setStroke(d(1), 0x1A4C6EF5) }
        profileCard.setOnClickListener { navigate { SelfDetailActivity.start(context) } }
        content.addView(profileCard, LayoutParams(-1, -2)); gap(12)
        stats.background = box(WHITE, 16); stats.setPadding(0, d(16), 0, d(16))
        content.addView(stats, LayoutParams(-1, -2)); gap(12)
        content.addView(ui.row().apply {
            background = box(); setPadding(d(6), d(14), d(6), d(14)); gravity = Gravity.TOP
            val actions = listOf(Triple(R.string.md_read_all, 0xf6d2, { navigate(true, markRead) }), Triple(R.string.md_add_friend, 0xf278, { navigate(true) { feature(XingDunFeatureActivity.MODE_FRIEND_SEARCH) } }), Triple(R.string.md_create_team, 0xf0db, { navigate(true, createTeam) }), Triple(R.string.md_scan, 0xf00cc, { navigate(true) { feature(XingDunFeatureActivity.MODE_QR_SCANNER) } }))
            actions.forEach { (label, code, click) -> addView(ui.column().apply {
                gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, d(4), 0, d(4))
                val actionIcon: View = if (label == R.string.md_add_friend || label == R.string.md_create_team) ImageView(context).apply {
                    setImageResource(if (label == R.string.md_add_friend) R.drawable.mine_drawer_add_friend else R.drawable.mine_drawer_create_team)
                    setPadding(d(10), d(10), d(10), d(10)); background = box(0xFFEEF2FF.toInt(), 14)
                } else icon(code, 24).apply { background = box(0xFFEEF2FF.toInt(), 14) }
                addView(actionIcon, LayoutParams(d(44), d(44))); addView(ui.space(8)); addView(this@XingDunMinePageView.label(label).apply { gravity = Gravity.CENTER; maxLines = 2 })
                contentDescription = s(label); isFocusable = true; setOnClickListener { click() }
            }, LayoutParams(0, -2, 1f)) }
        }, LayoutParams(-1, -2)); gap(12)
        group(listOfNotNull(
            Row(R.string.md_qr, 0xf00cb, PURPLE) { navigate { feature(XingDunFeatureActivity.MODE_PERSONAL_QR) } },
            Row(R.string.md_poster, 0xef27, 0xFF0EA5E9.toInt()) { navigate { feature(XingDunFeatureActivity.MODE_INVITE) } },
            Row(R.string.md_blacklist, 0xf279, 0xFF64748B.toInt()) { navigate(true) { XingDunBlacklistActivity.start(context) } },
            if (XingDunSessionManager.currentUiSession()?.features?.redpacket == true) Row(R.string.md_redpacket, 0xef2d, 0xFFE11D48.toInt()) { navigate { feature(XingDunFeatureActivity.MODE_REDPACKET_ACCOUNT) } } else null
        ))
        group(listOf(
            Row(R.string.md_password, 0xf888, RED) { navigate { feature(XingDunFeatureActivity.MODE_CHANGE_PASSWORD) } },
            Row(R.string.md_privacy, 0xf379, GREEN) { navigate { context.startActivity(Intent(context, XingDunProfilePrivacyActivity::class.java)) } },
            Row(R.string.md_binding, 0xf85a, PURPLE) { navigate { context.startActivity(Intent(context, XingDunAccountBindingActivity::class.java)) } }
        ), R.string.md_security)
        content.addView(ui.row().apply {
            background = box(); setPadding(d(20), d(16), d(20), d(16))
            addView(icon(0xf01de, 20, GREEN).apply { background = box(tint(GREEN), 10) }, LayoutParams(d(36), d(36)))
            addView(ui.column().apply {
                addView(label(R.string.md_storage)); addView(ui.space(6))
                storageProgress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply { max = 50000; progressTintList = android.content.res.ColorStateList.valueOf(GREEN); progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFFF1F3F9.toInt()) }
                addView(storageProgress, LayoutParams(-1, d(6)))
            }, LayoutParams(0, -2, 1f).apply { marginStart = d(12); marginEnd = d(12) })
            storageValue = text("—", MUTED); addView(storageValue); isFocusable = true; setOnClickListener { clearCache() }
        }, LayoutParams(-1, -2)); gap(12)
        group(buildList {
            add(Row(R.string.md_help, 0xf7e4, 0xFF3B82F6.toInt()) { navigate { feature(XingDunFeatureActivity.MODE_HELP) } })
            add(Row(R.string.md_feedback, 0xf04f, GOLD) { navigate { feature(XingDunFeatureActivity.MODE_FEEDBACK) } })
            add(Row(R.string.md_appeal, 0xf0c8, 0xFFDC2626.toInt()) { navigate { context.startActivity(Intent(context, XingDunAccountAppealActivity::class.java)) } })
            add(Row(R.string.xingdun_reports, 0xf07b, MUTED) { navigate { feature(XingDunFeatureActivity.MODE_REPORTS) } })
            if (XingDunSessionManager.currentUiSession()?.features?.customerService == true) add(Row(R.string.xingdun_contact_enterprise_support, 0xe155, BLUE) { navigate { openEnterpriseCustomerService() } })
            add(Row(R.string.md_agreement, 0xefae, MUTED) { navigate { feature(XingDunFeatureActivity.MODE_USER_AGREEMENT) } })
            add(Row(R.string.md_policy, 0xf2d0, GREEN) { navigate { feature(XingDunFeatureActivity.MODE_PRIVACY_POLICY) } })
        }, R.string.md_help)
        group(listOf(
            Row(R.string.md_settings, 0xf36e, MUTED) { navigate { XingDunSystemSettingsActivity.start(context) } },
            Row(R.string.xingdun_about_platform, 0xf815, BLUE, titleText = context.getString(R.string.md_about, context.getString(R.string.demo_app_name))) { navigate { feature(XingDunFeatureActivity.MODE_ABOUT) } }
        ))
        group(listOf(Row(R.string.md_logout, 0xf88b, RED, arrow = false) { logout() }))
        content.addView(label(R.string.md_delete, 0xFF888888.toInt()).apply {
            gravity = Gravity.CENTER; paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG; minHeight = d(48); isFocusable = true
            setOnClickListener { navigate { feature(XingDunFeatureActivity.MODE_DEACTIVATE) } }
        }, LayoutParams(-1, -2).apply { topMargin = d(4) })
        renderProfile(); renderStats()
    }
    private data class Row(val title: Int, val code: Int, val color: Int, val arrow: Boolean = true, val titleText: String? = null, val click: () -> Unit)
    private fun group(rows: List<Row>, title: Int? = null) {
        if (title != null) content.addView(label(title, MUTED).apply { setPadding(d(4), 0, 0, d(8)) })
        content.addView(ui.column().apply {
            background = box(); clipToOutline = true
            rows.forEachIndexed { index, item ->
                if (index > 0) addView(View(context).apply { setBackgroundColor(DIVIDER) }, LayoutParams(-1, maxOf(1, d(1)/2)).apply { marginStart = d(20) })
                addView(ui.row().apply {
                    setPadding(d(20), d(14), d(20), d(14))
                    addView(icon(item.code, 20, item.color).apply { background = box(tint(item.color), 10) }, LayoutParams(d(36), d(36)))
                    addView(text(item.titleText ?: s(item.title), if (item.title == R.string.md_logout) RED else INK).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }, LayoutParams(0, -2, 1f).apply { marginStart = d(12); marginEnd = d(12) })
                    if (item.arrow) addView(icon(0xe15f, 20, HINT))
                    contentDescription = item.titleText ?: s(item.title); isFocusable = true; setOnClickListener { item.click() }
                }, LayoutParams(-1, -2))
            }
        }, LayoutParams(-1, -2)); gap(12)
    }
    private fun gap(size: Int) { content.addView(ui.space(size)) }
    private fun renderProfile() {
        profileCard.removeAllViews()
        val session = XingDunSessionManager.currentUiSession(); val profile = LoginStore.shared.loginState.loginUserInfo.value?.takeIf { mineCanUseIMData(session?.timUserId, it.userID) }
        val name = backendProfile?.opt("nickname")?.takeIf { it.isNotBlank() } ?: profile?.nickname?.takeIf { it.isNotBlank() } ?: session?.nickname?.takeIf { it.isNotBlank() } ?: session?.username.orEmpty()
        val account = backendProfile?.opt("custom_id")?.takeIf { it.isNotBlank() } ?: session?.username?.takeIf { it.isNotBlank() } ?: session?.timUserId.orEmpty()
        val signature = backendProfile?.opt("signature") ?: profile?.selfSignature.orEmpty()
        profileCard.addView(ui.row().apply {
            addView(FrameLayout(context).apply {
                addView(FrameLayout(context).apply {
                    background = box(0xFF5ACAA5.toInt(), 16); clipToOutline = true
                    addView(text(name.takeLast(2), WHITE).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
                    val url = backendProfile?.opt("avatar")?.takeIf { it.isNotBlank() } ?: profile?.avatarURL
                    url?.takeIf(::safeTravelMedia)?.let { addView(ui.image(it, 16), FrameLayout.LayoutParams(-1, -1)) }
                }, FrameLayout.LayoutParams(-1, -1))
                addView(View(context).apply { contentDescription = s(R.string.md_online); background = box(GREEN, 7).apply { setStroke(d(2), WHITE) } }, FrameLayout.LayoutParams(d(14), d(14), Gravity.BOTTOM or Gravity.RIGHT))
            }, LayoutParams(d(72), d(72)))
            addView(ui.column().apply {
                addView(text(name).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }); addView(ui.space(6))
                addView(text(account, BLUE).apply { background = box(0x144C6EF5, 10); setPadding(d(8), d(3), d(8), d(3)); maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            }, LayoutParams(0, -2, 1f).apply { marginStart = d(16) })
            addView(icon(0xe15f, 24, HINT))
        })
        profileCard.addView(ui.space(14)); profileCard.addView(ui.row().apply {
            background = box(if (signature.isBlank()) 0x66FFFFFF else 0x99FFFFFF.toInt(), 10); setPadding(d(12), d(10), d(12), d(10))
            addView(icon(if (signature.isBlank()) 0xf030f else 0xe504, 18, HINT))
            addView(text(signature.ifBlank { s(R.string.md_signature) }, if (signature.isBlank()) HINT else MUTED).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }, LayoutParams(0, -2, 1f).apply { marginStart = d(8) })
        }, LayoutParams(-1, -2))
        profileCard.contentDescription = listOf(name, account, signature.ifBlank { s(R.string.md_signature) }).joinToString("\n")
    }
    private fun renderStats() {
        stats.removeAllViews()
        val ready = mineCanUseIMData(XingDunSessionManager.currentUiSession()?.timUserId, LoginStore.shared.loginState.loginUserInfo.value?.userID)
        val counts = listOf(if (ready) ContactStore.shared.state.friendList.value.size else null, if (ready) GroupStore.shared.state.joinedGroupList.value.size else null, favoriteCount)
        val labels = listOf(R.string.md_friends, R.string.md_teams, R.string.md_favorites)
        val colors = listOf(BLUE, PURPLE, GOLD); val icons = listOf(0xf005d, 0xf0db, 0xf5cf)
        counts.forEachIndexed { index, value ->
            stats.addView(ui.column().apply {
                gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, d(8), 0, d(8))
                addView(icon(icons[index], 22, colors[index]).apply { background = box(tint(colors[index]), 12) }, LayoutParams(d(40), d(40)))
                addView(ui.space(8)); addView(text(value?.let(::mineMetric) ?: "—")); addView(ui.space(2)); addView(label(labels[index], MUTED))
                contentDescription = "${value?.let(::mineMetric) ?: "—"}\n${s(labels[index])}"; isFocusable = true
                if (index == 2 && XingDunSessionManager.currentUiSession()?.features?.messageFavorite != true) { isEnabled = false; alpha = .55f }
                setOnClickListener { when(index) {
                    0 -> navigate(true) { (context as? MainActivity)?.selectTabByName(MainActivity.TAB_CONTACTS) }
                    1 -> navigate { XingDunGroupListActivity.start(context) }
                    else -> navigate { feature(XingDunFeatureActivity.MODE_FAVORITES) }
                } }
            }, LayoutParams(0, -2, 1f))
        }
    }
    private fun renderStorage() {
        val bytes = cacheBytes; val mb = (bytes ?: 0L) / 1048576.0
        storageValue?.text = when { bytes == null -> "—"; mb < 1 -> String.format(Locale.getDefault(), "%.0f KB", mb*1024); mb >= 1024 -> String.format(Locale.getDefault(), "%.1f GB", mb/1024); else -> String.format(Locale.getDefault(), "%.1f MB", mb) }
        storageProgress?.progress = minOf(50000, (mb*100).toInt())
        storageProgress?.progressTintList = android.content.res.ColorStateList.valueOf(when { mb < 300 -> GREEN; mb < 425 -> GOLD; else -> RED })
    }
    private fun refreshData() {
        if (!active || request?.isActive == true) return
        val owner = context as? LifecycleOwner ?: return
        val session = XingDunSessionManager.currentSession() ?: return
        val stamp = key()
        request = owner.lifecycleScope.launch {
            try { supervisorScope {
                val profile = async { runCatching { XingDunSessionManager.apiClient().get<JsonElement>(session, "user/profile", emptyMap(), JsonElement::class.java).asJsonObject } }
                val favorites = async { runCatching { if (session.features.messageFavorite) XingDunSessionManager.apiClient().get<JsonElement>(session, "message/favorites", mapOf("page" to "1", "page_size" to "1"), JsonElement::class.java).asJsonObject.get("total").asInt else 0 } }
                val usage = async { runCatching { XingDunStorageManager.usage(context).totalBytes } }
                val p = profile.await(); val f = favorites.await(); val u = usage.await()
                if (key() != stamp) { checkOwner(); return@supervisorScope }
                p.getOrNull()?.let { result ->
                    check(result.opt("tim_user_id").ifBlank { session.timUserId } == session.timUserId)
                    backendProfile = result
                }
                f.getOrNull()?.let { favoriteCount = it }; u.getOrNull()?.let { cacheBytes = it }
                if (p.isFailure || f.isFailure || u.isFailure) Toast.makeText(context, R.string.md_refresh_failed, Toast.LENGTH_SHORT).show()
                renderProfile(); renderStats(); renderStorage()
            } } catch (e: CancellationException) { throw e } catch (_: Exception) { Toast.makeText(context, R.string.md_refresh_failed, Toast.LENGTH_SHORT).show() }
            finally { refresh.isRefreshing = false }
        }
    }
    private fun clearCache() {
        AlertDialog.Builder(context).setTitle(R.string.md_clear_title).setMessage(R.string.md_clear_body).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.md_clear) { _, _ ->
                val owner = context as? LifecycleOwner ?: return@setPositiveButton
                owner.lifecycleScope.launch {
                    try { XingDunStorageManager.clear(context, XingDunCacheCategory.entries.toSet()); cacheBytes = XingDunStorageManager.usage(context).totalBytes; renderStorage(); Toast.makeText(context, R.string.md_cleared, Toast.LENGTH_SHORT).show() }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { Toast.makeText(context, R.string.md_storage_error, Toast.LENGTH_SHORT).show() }
                }
            }.show()
    }
    private fun openEnterpriseCustomerService() {
        if (customerServiceRequest?.isActive == true) return
        val session = XingDunSessionManager.currentSession() ?: run {
            Toast.makeText(context, R.string.xingdun_session_expired, Toast.LENGTH_SHORT).show()
            return
        }
        val requestKey = key()
        val activeScope = (context as? LifecycleOwner)?.lifecycleScope ?: return
        Toast.makeText(context, R.string.xingdun_loading, Toast.LENGTH_SHORT).show()
        customerServiceRequest = activeScope.launch {
            runCatching {
                XingDunSessionManager.apiClient().get<JsonObject>(
                    session,
                    "cs/identity",
                    emptyMap(),
                    JsonObject::class.java,
                )
            }.onSuccess { identity ->
                if (key() != requestKey || !active) return@onSuccess
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
                    val isKnownFriend = ContactStore.shared.state.friendList.value.any { it.userID == official }
                    val relationship = if (isKnownFriend) {
                        "friend"
                    } else {
                        runCatching {
                            XingDunSessionManager.apiClient().getNullable<JsonObject>(
                                session,
                                "user/searchForFriend",
                                mapOf("keyword" to official),
                                JsonObject::class.java,
                            )
                        }.getOrNull()
                            ?.get("relationship_status")
                            ?.takeUnless { it.isJsonNull }
                            ?.let { runCatching { it.asString }.getOrNull() }
                    }
                    if (shouldOpenCustomerServiceChat(isKnownFriend, relationship)) {
                        ChatActivity.start(context, "c2c_$official")
                    } else {
                        XingDunFeatureActivity.start(context, XingDunFeatureActivity.MODE_FRIEND_SEARCH, official)
                    }
                } else {
                    Toast.makeText(context, R.string.xingdun_customer_service_not_configured, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                if (it is CancellationException) throw it
                if (key() != requestKey || !active) return@onFailure
                Toast.makeText(context, R.string.xingdun_customer_service_load_retry, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logout() {
        AlertDialog.Builder(context).setTitle(R.string.md_logout).setMessage(R.string.md_logout_body).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.md_logout) { _, _ ->
                close(); XingDunTenantSessionCoordinator.logout { context.startActivity(Intent(context, XingDunLaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) }
            }.show()
    }
    private companion object {
        const val BG = 0xFFF8F9FC.toInt(); const val WHITE = Color.WHITE; const val INK = 0xFF1A1A2E.toInt(); const val MUTED = 0xFF6B7280.toInt(); const val HINT = 0xFF9CA3AF.toInt(); const val DIVIDER = 0xFFE5E7EB.toInt()
        const val BLUE = 0xFF4C6EF5.toInt(); const val PURPLE = 0xFF8B5CF6.toInt(); const val GOLD = 0xFFF59E0B.toInt(); const val GREEN = 0xFF22C55E.toInt(); const val RED = 0xFFEF4444.toInt()
    }
}
