package io.trtc.tuikit.chat.demo.xingdun.features.travel

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.BG
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.INK
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.MUTED
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.ACCENT
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.BRONZE
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.BORDER
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.SECONDARY
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.PALE
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.WHITE
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.*

/** Account-private route plans and the tenant's editorial routes share the reference catalog. */
internal object LushuRoutes {
    fun inCity(catalog: TravelCatalog, cityId: String) = catalog.routes.filter { it.opt("city_id") == cityId }
    fun visible(catalog: TravelCatalog, mine: Boolean, status: String?): List<JsonObject> =
        (if (mine) listOfNotNull(catalog.ownRoute) else catalog.routes).filter { status == null || it.str("status") == status }
    fun filters(catalog: TravelCatalog, mine: Boolean): List<String> {
        val present = visible(catalog, mine, null).map { it.str("status") }.toSet()
        return catalog.statuses.filter { it != "全部" && it in present }
    }
}

/** Native counterpart of LushuPage: fixed city overview, timeline, own plan and shared route details. */
class LushuPageView(context: Context, private val publish: () -> Unit) : LinearLayout(context) {
    private val ui = TravelUi(context)
    private val store = TravelStore.repository
    private val header = ui.row()
    private val switches = ui.row()
    private val body = FrameLayout(context)
    private var timeline = false
    private var mine = false
    private var filter: String? = null
    private var cityId: String? = null
    private var observer: Job? = null
    private var request: Job? = null
    private var active = false
    private var error: Throwable? = null
    private var account = scope()
    private var timelineList: RecyclerView? = null
    private var savedScroll: android.os.Parcelable? = null
    private fun scope() = XingDunSessionManager.currentSession()?.let { "${it.apiBaseUrl}|${it.companyId}|${it.timUserId}|${XingDunSessionManager.currentAuthenticationGeneration()}" }

    init {
        orientation = VERTICAL; setBackgroundColor(BG)
        addView(header, LayoutParams(-1, ui.d(58)))
        switches.setPadding(ui.d(16), 0, ui.d(16), 0)
        addView(switches, LayoutParams(-1, -2)); addView(body, LayoutParams(-1, 0, 1f))
        renderHeader(); render()
    }
    fun saveState() = Bundle().apply {
        putBoolean("timeline", timeline); putBoolean("mine", mine); putString("filter", filter); putString("city", cityId)
        putString("scope", account); putParcelable("scroll", timelineList?.layoutManager?.onSaveInstanceState() ?: savedScroll)
    }
    fun restoreState(state: Bundle?) {
        if (state == null || state.getString("scope") != account) return
        timeline = state.getBoolean("timeline"); mine = state.getBoolean("mine"); filter = state.getString("filter"); cityId = state.getString("city")
        @Suppress("DEPRECATION")
        val scroll = state.getParcelable<android.os.Parcelable>("scroll")
        savedScroll = scroll; render()
    }
    fun setActive(value: Boolean) {
        active = value
        if (value && isAttachedToWindow) { checkAccount(); renderHeader(); load() }
    }
    fun updateColors() { setBackgroundColor(BG); renderHeader() }
    fun published() { mine = true; timeline = true; cityId = null; filter = null; savedScroll = null; render(); Toast.makeText(context, ui.s(T.route_published), Toast.LENGTH_LONG).show() }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val owner = context as? LifecycleOwner ?: return
        observer = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { store.snapshots.collect { checkAccount(); render(keepScroll = true) } }
                launch { LoginStore.shared.loginState.loginUserInfo.collect { checkAccount(); renderHeader(); if (active) load() } }
                if (active) load()
            }
        }
    }
    override fun onDetachedFromWindow() {
        savedScroll = timelineList?.layoutManager?.onSaveInstanceState() ?: savedScroll
        observer?.cancel(); request?.cancel(); super.onDetachedFromWindow()
    }
    private fun checkAccount() {
        val now = scope()
        if (now != account) { account = now; timeline = false; mine = false; filter = null; cityId = null; savedScroll = null; render() }
    }
    private fun load(force: Boolean = false) {
        if (request?.isActive == true) return
        val owner = context as? LifecycleOwner ?: return
        request = owner.lifecycleScope.launch {
            error = null
            try { store.load(force) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                error = e
                if (store.current != null) Toast.makeText(context, ui.error(e), Toast.LENGTH_LONG).show()
            } finally { render(keepScroll = true) }
        }
    }
    private fun renderHeader() {
        header.removeAllViews(); header.setPadding(ui.d(16), ui.d(7), ui.d(16), ui.d(7))
        header.addView(ui.column().apply {
            addView(ui.label(T.lushu, 18f, INK, true)); addView(ui.space(3)); addView(ui.label(T.lushu_subtitle, 11f, MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        }, LayoutParams(0, -2, 1f))
        val session = XingDunSessionManager.currentSession(); val profile = LoginStore.shared.loginState.loginUserInfo.value
        val name = profile?.nickname?.takeIf { it.isNotBlank() } ?: session?.nickname?.takeIf { it.isNotBlank() } ?: session?.username.orEmpty()
        header.addView(FrameLayout(context).apply {
            background = ui.box(0xFF4C6FF4.toInt(), 20); clipToOutline = true; contentDescription = context.getString(R.string.md_open)
            addView(ui.text(name.takeLast(2).ifEmpty { "·" }, 14f, WHITE).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
            profile?.avatarURL?.takeIf(::safeTravelMedia)?.let { addView(ui.image(it, 20), FrameLayout.LayoutParams(-1, -1)) }
            setOnClickListener { (context as? io.trtc.tuikit.chat.demo.main.MainActivity)?.openMineDrawer() }
        }, LayoutParams(ui.d(36), ui.d(36)))
    }
    private fun render(keepScroll: Boolean = false) {
        if (keepScroll) savedScroll = timelineList?.layoutManager?.onSaveInstanceState() ?: savedScroll
        timelineList = null; switches.removeAllViews(); body.removeAllViews()
        val catalog = store.current
        if (catalog == null) {
            body.addView(ui.column(24).apply {
                gravity = Gravity.CENTER; addView(ui.text(error?.let(ui::error) ?: ui.s(T.loading), 14f, SECONDARY).apply { gravity = Gravity.CENTER })
                if (error != null) { addView(ui.space(16)); addView(ui.button(T.retry) { load(true) }) }
            }, FrameLayout.LayoutParams(-1, -1)); return
        }
        if (cityId != null && catalog.cities.none { it.str("id") == cityId }) cityId = null
        if (filter !in LushuRoutes.filters(catalog, mine)) filter = null
        val segmented = ui.row().apply { background = ui.box(WHITE, 28, true); setPadding(ui.d(6), ui.d(2), ui.d(6), ui.d(2)) }
        fun segment(label: Int, icon: Int, selected: Boolean, click: () -> Unit) {
            segmented.addView(ui.row().apply {
                gravity = Gravity.CENTER; background = ui.box(if (selected) PALE else WHITE, 28)
                addView(ui.icon(icon, 14, if (selected) ACCENT else MUTED)); addView(ui.text("  " + ui.s(label), 12f, if (selected) ACCENT else MUTED, true))
                contentDescription = ui.s(label); isSelected = selected; isFocusable = true; setOnClickListener { savedScroll = null; click(); render() }
            }, LayoutParams(0, ui.d(28), 1f))
        }
        segment(T.map, 0xe3c8, !timeline) { timeline = false; mine = false; filter = null }
        segment(T.timeline, 0xf06bb, timeline) { timeline = true; cityId = null }
        switches.addView(segmented, LayoutParams(0, -2, 1f))
        switches.addView(ui.row().apply {
            if (mine) addView(ui.icon(0xe15e, 18, ACCENT))
            addView(ui.label(if (mine) T.discover_routes else T.my_routes, 12f, ACCENT, true))
            minimumHeight = ui.d(34); gravity = Gravity.CENTER; contentDescription = ui.s(if (mine) T.discover_routes else T.my_routes)
            isFocusable = true; setOnClickListener { mine = !mine; timeline = mine; cityId = null; filter = null; savedScroll = null; render() }
        }, LayoutParams(-2, -2).apply { marginStart = ui.d(10) })
        body.addView(if (timeline) buildTimeline(catalog) else CityMap(catalog), FrameLayout.LayoutParams(-1, -1))
    }
    private fun single(view: TextView) = view.apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private fun availability(r: JsonObject): String = when {
        r.guide() -> ui.s(if (r.opt("creator_kind") == "virtual_guide") T.guide_route else T.platform_book)
        r.flag("mine") -> ui.s(T.plan)
        r.count("remaining") == 0 -> ui.s(T.full)
        else -> ui.s(T.remaining, r.count("remaining"))
    }
    private fun badge(r: JsonObject) = if (r.guide() || r.str("status") == "招募") availability(r) else r.str("status")
    private fun openRoute(r: JsonObject) = TravelActivity.open(context, "route", r.str("id"))
    private fun buildTimeline(catalog: TravelCatalog): View = ui.column().apply {
        val chips = ui.row().apply { setPadding(ui.d(16), ui.d(9), ui.d(16), ui.d(8)) }
        (listOf<String?>(null) + LushuRoutes.filters(catalog, mine)).forEach { value ->
            chips.addView(ui.text(value ?: ui.s(T.all), 12f, if (value == filter) WHITE else SECONDARY, true).apply {
                minHeight = ui.d(31); gravity = Gravity.CENTER; setPadding(ui.d(15), ui.d(7), ui.d(15), ui.d(7)); background = ui.box(if (value == filter) ACCENT else WHITE, 24, value != filter)
                isSelected = value == filter; isFocusable = true; setOnClickListener { filter = value; savedScroll = null; render() }
            }, LayoutParams(-2, -2).apply { marginEnd = ui.d(8) })
        }
        addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(chips) })
        val rows = LushuRoutes.visible(catalog, mine, filter)
        val entries = mutableListOf<Any>()
        var last = ""
        rows.forEach { row ->
            val date = row.str("start_date"); val key = date.take(7)
            if (key != last) { entries += ui.s(T.route_month, date.take(4), date.substring(5, 7).toInt().toString()); last = key }
            entries += row
        }
        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context); itemAnimator = null
            setPadding(ui.d(16), ui.d(5), ui.d(16), ui.d(20)); clipToPadding = false
            adapter = object : RecyclerView.Adapter<RowHolder>() {
                override fun getItemCount() = maxOf(1, entries.size)
                override fun onCreateViewHolder(parent: ViewGroup, type: Int) = RowHolder(FrameLayout(context).apply { layoutParams = RecyclerView.LayoutParams(-1, -2) })
                override fun onBindViewHolder(holder: RowHolder, position: Int) {
                    holder.frame.removeAllViews()
                    val entry = entries.getOrNull(position)
                    val view: View = when (entry) {
                        is JsonObject -> timelineRow(entry)
                        is String -> ui.text(entry, 13f, MUTED, true).apply { setPadding(0, 0, 0, ui.d(14)) }
                        else -> ui.card().apply {
                            addView(ui.column().apply {
                                gravity = Gravity.CENTER; addView(ui.label(T.no_routes, 15f, SECONDARY, true)); addView(ui.space(6)); addView(ui.label(T.publish_route_hint, 14f, MUTED)); addView(ui.space(6))
                                addView(ui.label(T.publish_intent, 15f, ACCENT, true).apply { setPadding(ui.d(16), ui.d(12), ui.d(16), ui.d(12)); isFocusable = true; setOnClickListener { publish() } })
                            }, LayoutParams(-1, ui.d(164)))
                        }
                    }
                    holder.frame.addView(view, FrameLayout.LayoutParams(-1, -2))
                }
            }
        }
        timelineList = list; savedScroll?.let { list.layoutManager?.onRestoreInstanceState(it) }; savedScroll = null
        addView(list, LayoutParams(-1, 0, 1f))
    }
    private class RowHolder(val frame: FrameLayout) : RecyclerView.ViewHolder(frame)
    private fun timelineRow(r: JsonObject): View = ui.row().apply {
        gravity = Gravity.TOP; setPadding(0, 0, 0, ui.d(18))
        addView(ui.column().apply {
            gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, ui.d(5), 0, 0)
            addView(ui.text(r.str("start_date").takeLast(2), 15f, ACCENT, true)); addView(ui.space(2)); addView(ui.label(T.day_unit, 12f, MUTED)); addView(ui.space(8)); addView(View(context).apply { setBackgroundColor(BORDER) }, LayoutParams(ui.d(1), 0, 1f))
        }, LayoutParams(ui.d(40), ui.d(93)))
        addView(ui.card(12).apply {
            addView(ui.row().apply {
                addView(ui.image(r.str("photo"), 9), LayoutParams(ui.d(40), ui.d(40)))
                addView(ui.column().apply {
                    addView(ui.row().apply {
                        addView(View(context).apply { background = ui.box(if (r.count("remaining") == 0) BRONZE else ACCENT, 3) }, LayoutParams(ui.d(6), ui.d(6)).apply { marginEnd = ui.d(6) })
                        addView(single(ui.text(r.str("title"), 14f, INK, true)), LayoutParams(0, -2, 1f))
                    }); addView(ui.space(4)); addView(single(ui.text("${r.str("dates")} · ${r.str("city")} · ${r.str("province")}", 12f, MUTED)))
                }, LayoutParams(0, -2, 1f).apply { marginStart = ui.d(8) })
            }); addView(ui.space(8)); addView(ui.row().apply {
                addView(ui.image(r.str("avatar"), 10), LayoutParams(ui.d(20), ui.d(20)))
                addView(single(ui.text(r.creator("owner"), 13f, SECONDARY)), LayoutParams(0, -2, 1f).apply { marginStart = ui.d(6) })
                addView(ui.text(badge(r), 12f, MUTED).apply { background = ui.box(0xFFF4F4F3.toInt(), 16); setPadding(ui.d(9), ui.d(3), ui.d(9), ui.d(3)) })
            })
        }, LayoutParams(0, -2, 1f))
        isFocusable = true; setOnClickListener { openRoute(r) }
    }
    private inner class CityMap(private val catalog: TravelCatalog) : FrameLayout(context) {
        init { clipChildren = true; addView(MapPainting(context), FrameLayout.LayoutParams(-1, -1)) }
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); if (w > 0 && h > 0) post { if (isAttachedToWindow) arrange() } }
        private fun arrange() {
            while (childCount > 1) removeViewAt(1)
            val factor = width / 360f
            catalog.cities.forEach { city ->
                val id = city.str("id"); val name = city.str("name"); val selected = cityId == id; val point = city.getAsJsonArray("map_point")
                val pill = ui.column().apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(ui.text("$name ${LushuRoutes.inCity(catalog, id).size}", 11f, if (selected) WHITE else INK, true).apply {
                        minHeight = ui.d(26); gravity = Gravity.CENTER; setPadding(ui.d(9), ui.d(4), ui.d(9), ui.d(4)); background = ui.box(if (selected) ACCENT else WHITE, 18, true); elevation = ui.d(3).toFloat()
                    }); addView(ui.space(3)); addView(View(context).apply { background = ui.box(if (selected) ACCENT else BRONZE, 4) }, LayoutParams(ui.d(8), ui.d(8)))
                    isFocusable = true; contentDescription = "$name ${LushuRoutes.inCity(catalog, id).size}"; setOnClickListener { cityId = id; render() }
                }
                addView(pill, FrameLayout.LayoutParams(-2, -2).apply {
                    leftMargin = (point[0].asFloat * factor - (name.length * 5.5f + 12.5f) * factor).toInt(); topMargin = (point[1].asFloat * factor).toInt()
                })
            }
            val panelHeight = minOf(ui.d(300), (height * .57f).toInt())
            val strip = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; background = ui.box(0xF5FFFFFF.toInt(), 14); setPadding(0, ui.d(12), 0, ui.d(12)); clipToPadding = false }
            strip.addView(ui.row().apply {
                setPadding(ui.d(8), 0, ui.d(8), 0)
                (listOf<JsonObject?>(null) + catalog.cities).forEach { city ->
                    val selected = city?.str("id") == cityId
                    addView(ui.text(if (city == null) ui.s(T.nationwide) else "${city.str("name")} ${LushuRoutes.inCity(catalog, city.str("id")).size}", 12f, if (selected) ACCENT else SECONDARY, true).apply {
                        setPadding(ui.d(16), ui.d(6), ui.d(16), ui.d(6)); background = ui.box(if (selected) PALE else 0xFFFFFAF6.toInt(), 9, true).apply { if (selected) setStroke(ui.d(1), ACCENT) }
                        isSelected = selected; isFocusable = true; setOnClickListener { cityId = city?.str("id"); render() }
                    }, LayoutParams(-2, -2).apply { marginEnd = ui.d(6) })
                }
            })
            addView(strip, FrameLayout.LayoutParams(-1, ui.d(54), Gravity.BOTTOM).apply {
                setMargins(ui.d(16), 0, ui.d(16), if (cityId == null) maxOf(ui.d(16), (this@CityMap.height - 519 * factor).toInt()) else panelHeight + ui.d(9))
            })
            cityId?.let { id ->
                val city = catalog.cities.firstOrNull { it.str("id") == id } ?: return@let
                val routes = LushuRoutes.inCity(catalog, id)
                addView(ui.column().apply {
                    background = ui.box(WHITE, 22); clipToOutline = true
                    addView(FrameLayout(context).apply {
                        addView(View(context).apply { background = ui.box(BORDER, 2) }, FrameLayout.LayoutParams(ui.d(36), ui.d(4), Gravity.CENTER))
                        contentDescription = ui.s(T.close_city); isFocusable = true; setOnClickListener { cityId = null; render() }
                    }, LayoutParams(-1, ui.d(22)))
                    addView(ui.row().apply {
                        setPadding(ui.d(16), 0, ui.d(16), ui.d(10))
                        addView(ui.text(ui.s(T.city_routes, city.str("name"), routes.size), 15f, INK, true), LayoutParams(0, -2, 1f))
                        addView(ui.text(ui.s(T.route_count, routes.size), 13f, MUTED))
                    })
                    addView(ScrollView(context).apply {
                        addView(ui.column().apply {
                            setPadding(ui.d(16), 0, ui.d(16), ui.d(16))
                            routes.forEach { r ->
                                addView(ui.card(12).apply {
                                    addView(ui.row().apply {
                                        addView(ui.image(r.str("photo"), 9), LayoutParams(ui.d(48), ui.d(48)))
                                        addView(ui.column().apply {
                                            addView(single(ui.text(r.str("title"), 14f, INK, true))); addView(ui.space(4)); addView(ui.text("${r.str("dates")} · ${availability(r)}", 12f, MUTED))
                                        }, LayoutParams(0, -2, 1f).apply { marginStart = ui.d(12) })
                                    }); isFocusable = true; setOnClickListener { openRoute(r) }
                                }, LayoutParams(-1, -2).apply { bottomMargin = ui.d(8) })
                            }
                        })
                    }, LayoutParams(-1, 0, 1f))
                }, FrameLayout.LayoutParams(-1, panelHeight, Gravity.BOTTOM))
            }
        }
    }
    private class MapPainting(context: Context) : View(context) {
        override fun onDraw(canvas: Canvas) {
            val s = width / 360f; canvas.drawColor(0xFFE7EDE9.toInt())
            val oval = RectF(47 * s, 25 * s, 314 * s, 705 * s)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(oval.left, oval.top, oval.right, oval.bottom, 0xFFD3DDD7.toInt(), 0xFFC5D1C9.toInt(), Shader.TileMode.CLAMP) }
            canvas.drawOval(oval, paint); paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = .8f; paint.color = 0xFFB6C6BC.toInt(); canvas.drawOval(oval, paint)
            paint.color = 0xFFD5DFD9.toInt(); listOf(156, 276, 396).forEach { canvas.drawLine(36 * s, it * s, 324 * s, it * s, paint) }
        }
    }
}
