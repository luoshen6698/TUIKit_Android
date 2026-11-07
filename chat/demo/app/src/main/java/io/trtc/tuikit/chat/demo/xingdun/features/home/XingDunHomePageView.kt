package io.trtc.tuikit.chat.demo.xingdun.features.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunGroupListActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunVerificationMessagesActivity
import io.trtc.tuikit.chat.demo.xingdun.features.travel.*
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.BG
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.INK
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.MUTED
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.ACCENT
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.BRONZE
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.BORDER
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.SECONDARY
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.PALE
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.FIELD
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi.Companion.WHITE
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Native rendering of the tenant-backed Tuxing feed. Other tabs keep their existing destinations. */
class XingDunHomePageView(context: Context, private val verificationUnread: StateFlow<Int>? = null) : LinearLayout(context) {
    private val ui = TravelUi(context)
    private val store = TravelStore.repository
    private val refresh = SwipeRefreshLayout(context)
    private val list = RecyclerView(context)
    private val feed = FeedAdapter()
    private val identityHeader = ui.row()
    private var observer: Job? = null
    private var request: Job? = null
    private var error: Throwable? = null
    private var active = false
    private var lastDay = TravelDates.todayKey()
    init {
        orientation = VERTICAL; setBackgroundColor(BG)
        addView(identityHeader, LayoutParams(-1, ui.d(58)))
        list.layoutManager = LinearLayoutManager(context); list.adapter = feed; list.itemAnimator = null
        list.setPadding(ui.d(16), 0, ui.d(16), ui.d(24)); list.clipToPadding = false
        refresh.addView(list); refresh.setColorSchemeColors(ACCENT); refresh.setOnRefreshListener { load(true) }
        addView(refresh, LayoutParams(-1, 0, 1f)); header()
    }
    fun updateColors() { setBackgroundColor(BG); header(); feed.notifyDataSetChanged() }
    fun setActive(value: Boolean) { active = value; if (value && isAttachedToWindow) { header(); val day=TravelDates.todayKey(); load(day!=lastDay); lastDay=day } }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val owner = context as? LifecycleOwner ?: return
        observer = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { store.snapshots.collect { feed.notifyDataSetChanged() } }
                launch { verificationUnread?.collect { feed.notifyItemChanged(0) } }
                launch { LoginStore.shared.loginState.loginUserInfo.collect { header() } }
                launch { if (active) { header(); val day=TravelDates.todayKey(); load(day!=lastDay);lastDay=day } }
            }
        }
        if (active) load()
    }
    override fun onDetachedFromWindow() { observer?.cancel(); request?.cancel(); super.onDetachedFromWindow() }
    private fun load(force: Boolean = false) {
        if (request?.isActive == true) return
        val owner = context as? LifecycleOwner ?: return
        error = null
        request = owner.lifecycleScope.launch {
            try { store.load(force) } catch (e: CancellationException) { throw e } catch (e: Exception) { error=e; if (store.current!=null) Toast.makeText(context, ui.error(e), Toast.LENGTH_LONG).show() }
            finally { refresh.isRefreshing=false; feed.notifyDataSetChanged() }
        }
    }
    private fun header() {
        identityHeader.removeAllViews(); identityHeader.setPadding(ui.d(16),ui.d(7),ui.d(16),ui.d(7))
        val session=XingDunSessionManager.currentSession(); val profile=LoginStore.shared.loginState.loginUserInfo.value
        val name=profile?.nickname?.takeIf { it.isNotBlank() } ?: session?.nickname?.takeIf { it.isNotBlank() } ?: session?.username.orEmpty()
        val hour=TravelDates.hour()
        val greeting=ui.s(when { hour<6->T.late;hour<11->T.morning;hour<14->T.noon;hour<18->T.afternoon;else->T.evening })
        identityHeader.addView(ui.column().apply { addView(ui.label(T.home,18f,INK,true));addView(ui.space(3));addView(ui.text(if(name.isBlank())greeting else ui.s(T.greeting,greeting,name),11f,MUTED)) },LayoutParams(0,-2,1f))
        val avatar=FrameLayout(context).apply {
            background=ui.box(0xFF4C6FF4.toInt(),20);clipToOutline=true;contentDescription=ui.s(T.mine)
            addView(ui.text(name.takeLast(2).ifEmpty { "·" },14f,WHITE).apply{gravity=Gravity.CENTER},FrameLayout.LayoutParams(-1,-1))
            profile?.avatarURL?.takeIf { safeTravelMedia(it) }?.let{addView(ui.image(it,20),FrameLayout.LayoutParams(-1,-1))}
            setOnClickListener { (context as? Activity)?.findViewById<View>(R.id.demo_tab_me)?.performClick() }
        }
        identityHeader.addView(avatar,LayoutParams(ui.d(36),ui.d(36)))
    }
    private inner class FeedAdapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount()=1+(store.current?.articles?.size ?: 0)
        override fun onCreateViewHolder(parent: ViewGroup,type: Int)=Holder(FrameLayout(context).apply { layoutParams=RecyclerView.LayoutParams(-1,-2) })
        override fun onBindViewHolder(holder: Holder,position: Int) {
            holder.frame.removeAllViews()
            val catalog=store.current
            val view=if(position==0) top(catalog) else ui.articleCard(catalog!!,catalog.articles[position-1]) { TravelActivity.open(context,"article",catalog.articles[position-1].str("id")) }
            holder.frame.addView(view,FrameLayout.LayoutParams(-1,-2).apply { bottomMargin=ui.d(14) })
        }
    }
    private class Holder(val frame: FrameLayout):RecyclerView.ViewHolder(frame)
    private fun top(catalog: TravelCatalog?): View=ui.column().apply {
        if(catalog==null) {
            addView(ui.text(error?.let(ui::error) ?: ui.s(T.loading),14f,SECONDARY).apply { gravity=Gravity.CENTER;setPadding(ui.d(20),ui.d(100),ui.d(20),ui.d(24)) })
            if(error!=null) addView(ui.button(T.retry){load(true)});return@apply
        }
        addView(ui.column().apply {
            background=ui.box(WHITE,22);clipToOutline=true;elevation=ui.d(1).toFloat()
            addView(ui.photoHero(catalog.home.str("hero_image"),148,catalog.home.str("hero_title"),catalog.home.str("hero_subtitle"),ui.s(T.home),radius=0))
            addView(ui.row().apply {
                setPadding(ui.d(16),ui.d(17),ui.d(16),ui.d(17))
                addView(ui.column().apply { gravity=Gravity.CENTER;addView(ui.text(catalog.calendar.str("day"),30f,0xFF111827.toInt(),true));addView(ui.space(4));addView(ui.text(catalog.calendar.str("month"),12f,0xFF677C98.toInt(),true)) },LayoutParams(ui.d(48),-2))
                addView(View(context).apply{setBackgroundColor(0xFFEEEEEE.toInt())},LayoutParams(ui.d(1),ui.d(44)).apply{marginStart=ui.d(14);marginEnd=ui.d(12)})
                addView(ui.column().apply{addView(ui.text(catalog.calendar.str("weekday"),13f,0xFF111827.toInt(),true));addView(ui.space(6));addView(ui.text(catalog.calendar.str("detail"),12f,0xFF677C98.toInt()))},LayoutParams(0,-2,1f))
            })
        });addView(ui.space(16))
        addView(ui.card().apply {
            addView(ui.row().apply {
                addView(ui.image(catalog.home.str("intent_image"),12),LayoutParams(ui.d(36),ui.d(36)))
                val intent=catalog.intent
                addView(ui.column().apply {
                    addView(ui.label(T.intent_label,12f,MUTED));addView(ui.space(3))
                    addView(ui.text(if(intent==null)ui.s(T.next_trip) else "${intent.str("destination")} · ${intent.str("dates")}",14f,INK,true));addView(ui.space(3))
                    addView(ui.text(if(intent==null)ui.s(T.publish_hint) else ui.s(T.view_route,ui.s(T.plan)),12f,ACCENT))
                },LayoutParams(0,-2,1f).apply{marginStart=ui.d(12)})
                addView(ui.icon(TravelIcons.chevron_right,20,MUTED))
            });setOnClickListener { catalog.ownRoute?.let { TravelActivity.open(context,"route",it.str("id")) } ?: TravelActivity.open(context,"intent") }
        });addView(ui.space(24))
        addView(ui.row().apply {
            fun action(label:Int,icon:Int,badge:Int=0,click:()->Unit) {
                addView(ui.column().apply {
                    gravity=Gravity.CENTER;setPadding(0,ui.d(6),0,ui.d(6))
                    addView(FrameLayout(context).apply {
                        addView(ui.icon(icon,22,ACCENT,ui.s(label)).apply { background=ui.box(WHITE,16,true) },FrameLayout.LayoutParams(ui.d(44),ui.d(44),Gravity.CENTER))
                        if(badge>0)addView(ui.text(if(badge>99)"99+" else "$badge",10f,WHITE,true).apply { gravity=Gravity.CENTER;minWidth=ui.d(20);setPadding(ui.d(4),ui.d(3),ui.d(4),ui.d(3));background=ui.box(BRONZE,12) },FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.END))
                    },LayoutParams(ui.d(52),ui.d(48)))
                    addView(ui.space(8));addView(ui.label(label,12f,if(badge>0)BRONZE else SECONDARY,true));setOnClickListener{click()}
                },LayoutParams(0,-2,1f))
            }
            action(T.publish,TravelIcons.edit_outlined){TravelActivity.open(context,"intent")}
            action(T.find_routes,TravelIcons.auto_awesome_outlined,catalog.matches.size){TravelActivity.open(context,"match")}
            action(T.verification,TravelIcons.route_outlined,verificationUnread?.value ?: 0){context.startActivity(Intent(context,XingDunVerificationMessagesActivity::class.java))}
            action(T.teams,TravelIcons.groups_outlined){context.startActivity(Intent(context,XingDunGroupListActivity::class.java))}
        });addView(ui.space(24));addView(ui.label(T.community,14f,INK,true));addView(ui.space(6));addView(ui.label(T.community_sub,12f,MUTED))
    }
}
