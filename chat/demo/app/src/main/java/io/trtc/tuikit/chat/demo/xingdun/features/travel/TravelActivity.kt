package io.trtc.tuikit.chat.demo.xingdun.features.travel

import android.app.DatePickerDialog
import androidx.activity.result.contract.ActivityResultContracts
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunContactForwardPickerActivity
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.SendMessageOption
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.InputFilter
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.trtc.tuikit.chat.demo.common.BaseActivity
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Native, authenticated child pages sharing the exact catalog and action receipts with Home. */
class TravelActivity : BaseActivity() {
    private lateinit var ui: TravelUi
    private lateinit var root: LinearLayout
    private lateinit var body: FrameLayout
    private lateinit var footer: LinearLayout
    private lateinit var header: LinearLayout
    private val store = TravelStore.repository
    private val mode get() = intent.getStringExtra("page").orEmpty()
    private val itemId get() = intent.getStringExtra("id").orEmpty()
    private var pendingShare: String? = null
    private var shareScope: String? = null
    private fun accountScope() = io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager.currentUiSession()?.scopeKey
    private val sharePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text=pendingShare
        if(result.resultCode==RESULT_OK && text!=null && shareScope==accountScope()) {
            val conversation=result.data?.getStringExtra(XingDunContactForwardPickerActivity.EXTRA_RESULT_CONVERSATION_ID).orEmpty()
            if(conversation.startsWith("c2c_") || conversation.startsWith("group_"))
                AlertDialog.Builder(this).setTitle(R.string.plan_share_confirm).setMessage(result.data?.getStringExtra(XingDunContactForwardPickerActivity.EXTRA_RESULT_DISPLAY_NAME).orEmpty()+"\n\n"+text)
                    .setNegativeButton(R.string.tx_cancel,null).setPositiveButton(R.string.plan_send){_,_->sendPlan(conversation,text)}.show()
        }
    }
    private var busy = false
    private var renderedRevision: String? = null
    private var draft: JsonObject? = null
    private var dirty = false
    private var templateId: String? = null
    private var matchIndex = 0
    private var replyRoot = ""
    private var replyTarget = ""
    private var replyAuthor = ""
    private var message = ""
    private var articleAuthors = emptyMap<String,String>()
    private var pageScroll = 0
    private var statusInset = 0
    private var editorFields = mutableMapOf<String,EditText>()
    private var activeSheet: BottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); if(isFinishing)return
        ui=TravelUi(this)
        pendingShare=savedInstanceState?.getString("share");shareScope=savedInstanceState?.getString("share_scope")
        draft=savedInstanceState?.getString("draft")?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
        dirty=savedInstanceState?.getBoolean("dirty") ?: false;templateId=savedInstanceState?.getString("template");matchIndex=savedInstanceState?.getInt("match") ?: 0
        message=savedInstanceState?.getString("message").orEmpty();replyRoot=savedInstanceState?.getString("reply_root").orEmpty();replyTarget=savedInstanceState?.getString("reply_target").orEmpty();replyAuthor=savedInstanceState?.getString("reply_author").orEmpty()
        root=ui.column().apply{setBackgroundColor(BG)};header=ui.column();body=FrameLayout(this);footer=ui.column()
        root.addView(header);root.addView(body,LinearLayout.LayoutParams(-1,0,1f));root.addView(footer)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root){v,insets-> val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());val ime=insets.getInsets(WindowInsetsCompat.Type.ime());val changed=statusInset!=bars.top;statusInset=bars.top;v.updatePadding(top=if(mode=="route")0 else bars.top,bottom=maxOf(bars.bottom,ime.bottom));if(changed && mode=="route" && renderedRevision!=null)root.post{render()};insets }
        onBackPressedDispatcher.addCallback(this,object: OnBackPressedCallback(true){override fun handleOnBackPressed(){leave()}})
        lifecycleScope.launch { store.snapshots.collect { catalog -> if(catalog!=null && store.current!=null && (renderedRevision==null || mode!="intent")) render() } }
        load()
    }
    override fun appearanceLightStatusBarsOverride(): Boolean = mode != "route"
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState);outState.putString("share",pendingShare);outState.putString("share_scope",shareScope);outState.putString("draft",draft?.toString());outState.putBoolean("dirty",dirty);outState.putString("template",templateId);outState.putInt("match",matchIndex)
        outState.putString("message",message);outState.putString("reply_root",replyRoot);outState.putString("reply_target",replyTarget);outState.putString("reply_author",replyAuthor)
    }
    private fun leave() {
        if(busy){toast(T.busy);return}
        if(mode=="intent" && dirty) AlertDialog.Builder(this).setTitle(ui.s(T.discard)).setMessage(ui.s(T.discard_body)).setNegativeButton(ui.s(T.continue_edit),null).setPositiveButton(ui.s(T.discard_yes)){_,_->finish()}.show()
        else finish()
    }
    private fun load(force: Boolean=false) {
        if(store.current==null) status(ui.s(T.loading))
        lifecycleScope.launch {
            try{store.load(force);render()}catch(e:CancellationException){throw e}catch(e:Exception){if(store.current==null)status(ui.error(e),true)else toast(ui.error(e))}
        }
    }
    private fun status(text:String,retry:Boolean=false){setHeader(ui.s(T.home));body.removeAllViews();body.addView(ui.column(24).apply{gravity=Gravity.CENTER;addView(ui.text(text,14f,SECONDARY));if(retry){addView(ui.space(16));addView(ui.button(T.retry){load(true)})}},FrameLayout.LayoutParams(-1,-1))}
    private fun setHeader(title:String,subtitle:String="",report:(()->Unit)?=null) {
        header.removeAllViews();header.addView(ui.row().apply{
            addView(ui.icon(TravelIcons.arrow_back_ios_new_rounded,22,INK,ui.s(T.back)).apply{setOnClickListener{leave()}},LinearLayout.LayoutParams(ui.d(48),-1))
            addView(ui.column().apply{gravity=Gravity.CENTER;addView(ui.text(title,17f,INK,true).apply{gravity=Gravity.CENTER;maxLines=2});if(subtitle.isNotEmpty()){addView(ui.space(4));addView(ui.text(subtitle,13f,SECONDARY).apply{gravity=Gravity.CENTER;maxLines=2})}},LinearLayout.LayoutParams(0,-2,1f))
            addView(if(report!=null)ui.icon(TravelIcons.flag_outlined,23,SECONDARY,ui.s(T.report)).apply{setOnClickListener{report()}} else View(this@TravelActivity),LinearLayout.LayoutParams(ui.d(48),-1))
        },LinearLayout.LayoutParams(-1,ui.d(if(mode=="article")80 else 56)))
    }
    private fun scroll(content:View,pad:Int=16) {
        body.removeAllViews();body.addView(NestedScrollView(this).apply{isFillViewport=false;clipToPadding=false;setPadding(ui.d(pad),ui.d(5),ui.d(pad),ui.d(24));addView(content);post{scrollTo(0,pageScroll)};setOnScrollChangeListener{_:NestedScrollView, _:Int,y:Int,_:Int,_:Int->pageScroll=y}},FrameLayout.LayoutParams(-1,-1))
    }
    private fun footer(view:View?) {
        footer.removeAllViews();footer.visibility=if(view==null)View.GONE else View.VISIBLE
        if(view!=null){footer.setBackgroundColor(WHITE);footer.addView(ui.line());footer.addView(ui.column(16).apply{setPadding(ui.d(16),ui.d(11),ui.d(16),ui.d(10));addView(view)})}
    }
    private fun render() {
        val c=store.current?:return;renderedRevision=c.revision
        when(mode){"intent"->editor(c);"route"->route(c);"match"->matches(c);"article"->article(c);else->status(ui.s(T.unavailable))}
    }
    private fun action(payload:JsonObject,onSuccess:()->Unit={render()}) {
        if(busy)return;busy=true;enable(root,false);activeSheet?.let{enable(it.window?.decorView,false);it.setCancelable(false)}
        lifecycleScope.launch {
            try{store.action(payload);onSuccess()}catch(e:CancellationException){throw e}catch(e:Exception){toast(ui.error(e))}
            finally{busy=false;if(!isFinishing){enable(root,true);activeSheet?.let{enable(it.window?.decorView,true);it.setCancelable(true)}}}
        }
    }
    private fun enable(view:View?,enabled:Boolean){view?:return;view.isEnabled=enabled;if(view is ViewGroup)for(i in 0 until view.childCount)enable(view.getChildAt(i),enabled)}
    private fun toast(id:Int)=toast(ui.s(id))
    private fun toast(text:String){Toast.makeText(this,text,Toast.LENGTH_LONG).show()}
    private fun field(parent:LinearLayout,label:Int,value:String,max:Int=80,change:(String)->Unit={}):EditText {
        parent.addView(ui.space(11));parent.addView(ui.label(label,13f,SECONDARY));parent.addView(ui.space(6))
        return EditText(this).apply {
            setText(value);textSize=TravelTypography.size(15f);setTextColor(INK);setHintTextColor(MUTED);val multiline=label==T.route_description || label==T.report_details;isSingleLine=!multiline;minHeight=ui.d(48)
            background=ui.box(FIELD,12,true);setPadding(ui.d(16),ui.d(12),ui.d(16),ui.d(12));filters=arrayOf(InputFilter.LengthFilter(max));inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or if(multiline)android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
            if(label==T.report_details){minLines=3;maxLines=3;gravity=Gravity.TOP}
            addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){change(s.toString())};override fun afterTextChanged(s:Editable?){} })
            parent.addView(this,LinearLayout.LayoutParams(-1,-2))
        }
    }
    private fun editor(c:TravelCatalog) {
        if(draft==null) draft=if(itemId.isNotEmpty()) c.route(itemId)?.let(TravelPlans::editable) else TravelPlans.newPlan(intent.getStringExtra("route_source")?.let { runCatching{JsonParser.parseString(it).asJsonObject}.getOrNull() },c.styles + c.intent?.strings("styles").orEmpty())
        val d=draft?:run{status(ui.s(T.unavailable));footer(null);return}
        editorFields.clear();setHeader(getString(if(d.opt("id").isBlank())R.string.plan_new else R.string.plan_edit))
        val content=ui.column()
        content.addView(ui.text(getString(R.string.plan_hint),13f,SECONDARY))
        fun input(key:String,label:Int,max:Int=80):EditText = field(content,label,d.opt(key),max){d.addProperty(key,it);dirty=true}.also { editorFields[key]=it;it.contentDescription=getString(label) }
        input("title",T.route_title);input("city",T.where,40)
        fun dayPicker(label:Int,key:String) {
            content.addView(ui.space(12));content.addView(ui.button(label,true){
                val parts=d.str(key).split('-').map(String::toInt)
                DatePickerDialog(this,{_,year,month,day->
                    val next=d.deepCopy().apply { addProperty(key,String.format(java.util.Locale.ROOT,"%04d-%02d-%02d",year,month+1,day)); if(key=="start_date" && str("start_date")>str("end_date")) addProperty("end_date",TravelPlans.date(str("start_date"),(TravelPlans.duration(d.str("start_date"),d.str("end_date"))-1).coerceAtLeast(0))) }
                    val length=TravelPlans.duration(next.str("start_date"),next.str("end_date"))
                    if(length==0){toast(R.string.plan_dates_error);return@DatePickerDialog}
                    fun applyDate(){next.add("days",TravelPlans.days(d,length));draft=next;dirty=true;editor(c)}
                    if(length<d.rows("days").size && d.rows("days").drop(length).any { it.opt("title").isNotBlank()||it.opt("notes").isNotBlank() })
                        AlertDialog.Builder(this).setMessage(R.string.plan_shorter).setNegativeButton(R.string.tx_cancel,null).setPositiveButton(R.string.tx_use){_,_->applyDate()}.show()
                    else applyDate()
                },parts[0],parts[1]-1,parts[2]).show()
            }.apply { text="${getString(label)} · ${d.str(key)}";contentDescription=getString(label) })
        }
        dayPicker(R.string.plan_start,"start_date");dayPicker(R.string.plan_end,"end_date")
        input("budget",T.budget,20).apply{inputType=android.text.InputType.TYPE_CLASS_TEXT;hint="800-1500"}
        content.addView(ui.text(getString(R.string.plan_budget_hint),12f,MUTED))
        input("description",T.route_description,200)
        content.addView(ui.space(14));content.addView(ui.label(R.string.plan_status,13f,SECONDARY))
        val statuses=listOf("计划中","已完成","已取消")
        val statusLabels=listOf(R.string.plan_planning,R.string.plan_completed,R.string.plan_cancelled).map(::getString)
        content.addView(ui.chips(statusLabels,setOf(statusLabels[statuses.indexOf(d.opt("status")).coerceAtLeast(0)]),true){label->d.addProperty("status",statuses[statusLabels.indexOf(label)]);dirty=true;editor(c)})
        content.addView(ui.space(20));content.addView(ui.label(R.string.plan_days,18f,INK,true))
        val duration=TravelPlans.duration(d.str("start_date"),d.str("end_date"))
        if(duration>0 && d.rows("days").size!=duration) d.add("days",TravelPlans.days(d,duration))
        d.rows("days").forEachIndexed { i,day ->
            content.addView(ui.space(14));content.addView(ui.card().apply {
                addView(ui.text("${getString(R.string.plan_day,i+1)} · ${TravelPlans.date(d.str("start_date"),i)}",15f,ACCENT,true))
                field(this,R.string.plan_day_title,day.opt("title"),80){day.addProperty("title",it);dirty=true}.contentDescription="${getString(R.string.plan_day,i+1)} ${getString(R.string.plan_day_title)}"
                field(this,R.string.plan_day_notes,day.opt("notes"),1000){day.addProperty("notes",it);dirty=true}.apply {isSingleLine=false;minLines=3;maxLines=8;gravity=Gravity.TOP;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;hint=getString(R.string.plan_day_hint);contentDescription="${getString(R.string.plan_day,i+1)} ${getString(R.string.plan_day_notes)}"}
            })
        }
        if(d.opt("id").isNotBlank()) {
            content.addView(ui.space(16));content.addView(ui.button(R.string.plan_save_copy,true){draft=d.deepCopy().apply{addProperty("id","");addProperty("version",0)};dirty=true;editor(c)})
            content.addView(ui.space(10));content.addView(ui.button(R.string.plan_stale,true){AlertDialog.Builder(this).setMessage(R.string.plan_reload_warning).setNegativeButton(R.string.tx_cancel,null).setPositiveButton(R.string.tx_retry){_,_->
                if(busy)return@setPositiveButton
                busy=true;lifecycleScope.launch {try {store.load(true);val current=store.current?:return@launch;val latest=current.route(d.str("id"));if(latest==null)toast(T.unavailable)else{draft=TravelPlans.editable(latest);dirty=false;editor(current)}}catch(e:Exception){toast(ui.error(e))}finally{busy=false}}
            }.show()})
        }
        scroll(content);footer(ui.button(T.save){
            val missing=listOf("title","city").firstOrNull{d.opt(it).isBlank()}
            if(missing!=null){editorFields[missing]?.error=ui.s(T.required,getString(if(missing=="title")T.route_title else T.where));return@button}
            if(!TravelPlans.validBudget(d.opt("budget"))){editorFields["budget"]?.error=getString(R.string.plan_budget_error);return@button}
            if(duration==0){toast(R.string.plan_dates_error);return@button}
            action(travelBody("action" to "save_plan","plan" to d.deepCopy())){dirty=false;setResult(RESULT_OK);toast(T.saved);finish()}
        })
    }
    private fun sharePlan(r:JsonObject) {
        val text=TravelPlans.summary(r.deepCopy().apply{addProperty("status",ui.planStatus(r.str("status")))},getString(R.string.plan_snapshot)){getString(R.string.plan_day,it)}
        if(text.toByteArray(Charsets.UTF_8).size>7000){toast(R.string.plan_share_long);return}
        AlertDialog.Builder(this).setTitle(R.string.plan_share).setMessage(R.string.plan_share_hint)
            .setNegativeButton(R.string.tx_cancel,null).setPositiveButton(R.string.plan_share){_,_->pendingShare=text;shareScope=accountScope();sharePicker.launch(XingDunContactForwardPickerActivity.intent(this,""))}.show()
    }
    private fun sendPlan(conversation:String,text:String) {
        if(busy || shareScope!=accountScope())return
        busy=true;enable(root,false)
        MessageInputStore.create(conversation).sendMessage(SendMessagePayload.TextSendMessagePayload(text),SendMessageOption(),object:CompletionHandler {
            override fun onSuccess()=runOnUiThread {busy=false;enable(root,true);pendingShare=null;toast(R.string.plan_sent);ChatActivity.start(this@TravelActivity,conversation)}
            override fun onFailure(code:Int,desc:String)=runOnUiThread {busy=false;enable(root,true);toast(R.string.plan_send_failed)}
        })
    }
    private fun route(c:TravelCatalog) {
        val r=c.route(itemId)?:run{status(ui.s(T.unavailable));footer(null);return}
        header.removeAllViews();val content=ui.column()
        content.addView(ui.photoHero(r.str("photo"),416,r.str("title"),titleSize=22f,eyebrow="${r.str("city")} · ${r.str("province")} · ${r.str("dates")}",radius=0,tags=r.strings("tags")).apply {
            addView(ui.icon(TravelIcons.arrow_back_ios_new_rounded,22,WHITE,ui.s(T.back)).apply{background=ui.box(0x40FFFFFF,24);setOnClickListener{leave()}},FrameLayout.LayoutParams(ui.d(40),ui.d(40),Gravity.TOP or Gravity.START).apply{setMargins(ui.d(8),statusInset+ui.d(4),0,0)})
        })
        content.addView(ui.column(16).apply{
            addView(ui.space(0))
            addView(ui.row().apply{
                fun stat(icon:Int,label:Int,value:String){addView(ui.card(8).apply{gravity=Gravity.CENTER;addView(ui.icon(icon,18,SECONDARY));addView(ui.space(6));addView(ui.label(label,12f,MUTED));addView(ui.space(3));addView(ui.text(value,12f,INK,true).apply{gravity=Gravity.CENTER})},LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=ui.d(6)})}
                stat(TravelIcons.calendar_today_outlined,if(r.guide())T.duration else T.travel_dates,r.str("dates"));stat(TravelIcons.account_balance_wallet_outlined,if(r.guide())T.budget_ref else T.route_budget,r.str("budget"));stat(TravelIcons.verified_user_outlined,if(r.guide() || r.flag("mine"))T.type else T.seats,if(r.guide())ui.s(T.platform_route)else if(r.flag("mine"))ui.s(T.plan)else if(r.count("remaining")==0)ui.s(T.full)else ui.s(T.remaining,r.count("remaining")))
            });addView(ui.space(24));addView(ui.text(r.str("description"),15f,INK).apply{setLineSpacing(ui.d(5).toFloat(),1.2f)});addView(ui.space(14));addView(ui.text(if(r.flag("mine"))ui.s(T.my_route_note)else r.str("note"),14f,SECONDARY));addView(ui.space(20))
            if(r.flag("mine")) {
                addView(ui.label(R.string.plan_private,12f,MUTED));addView(ui.space(16))
                addView(ui.label(R.string.plan_days,18f,INK,true));addView(ui.space(10))
                r.rows("days").forEach { day ->addView(ui.card().apply {addView(ui.text(getString(R.string.plan_day,day.count("day")),14f,ACCENT,true));addView(ui.space(6));addView(ui.text(day.opt("title").ifBlank{getString(R.string.plan_day_empty)},16f,INK,true));if(day.opt("notes").isNotBlank()){addView(ui.space(8));addView(ui.text(day.opt("notes"),14f,SECONDARY))}});addView(ui.space(10)) }
                addView(ui.button(R.string.plan_share){sharePlan(r)});addView(ui.space(20))
            } else if(r.guide()) {addView(ui.button(R.string.plan_from_route,true){startActivity(intent(this@TravelActivity,"intent").putExtra("route_source",r.toString()))});addView(ui.space(16))}
            addView(ui.card().apply{addView(ui.row().apply{addView(ui.image(r.str("avatar"),24),LinearLayout.LayoutParams(ui.d(48),ui.d(48)));addView(ui.column().apply{addView(ui.text(r.creator("owner"),17f,INK,true));addView(ui.space(6));addView(ui.text(if(r.flag("mine"))ui.s(T.organizer_self)else r.str("owner_bio"),14f,SECONDARY))},LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=ui.d(12)})});if(r.flag("profile"))setOnClickListener{toast(T.profile_soon)}})
        });scroll(content,0)
        if(r.flag("mine"))footer(ui.row().apply{
            addView(ui.button(T.delete,true,0xFFB85E55.toInt()){AlertDialog.Builder(this@TravelActivity).setTitle(ui.s(T.delete_route)).setMessage(ui.s(T.delete_warning)).setNegativeButton(ui.s(T.cancel),null).setPositiveButton(ui.s(T.delete)){_,_->action(travelBody("action" to "delete_plan","id" to r.str("id"),"version" to (r.get("version")?.asInt ?: 1))){finish()}}.show()},LinearLayout.LayoutParams(ui.d(88),ui.d(48)))
            addView(ui.button(T.edit_route){open(this@TravelActivity,"intent",r.str("id"))},LinearLayout.LayoutParams(0,ui.d(48),1f).apply{marginStart=ui.d(10)})
        })else{
            val joined=c.selected("joins",r.str("id"));val ended=r.str("status")=="已结束"
            footer(ui.button(if(ended)T.ended else if(joined)if(r.guide())T.collected else T.joined else if(r.guide())T.collect else T.join){if(!joined&&!ended)action(travelBody("action" to "join","id" to r.str("id"))){toast(T.saved);render()}})
        }
    }
    private fun sheet(title:Int):Pair<BottomSheetDialog,LinearLayout> {
        val dialog=BottomSheetDialog(this);val content=ui.column(20).apply{background=ui.box(BG,24);addView(ui.label(title,18f,INK,true));addView(ui.space(8))}
        dialog.setContentView(NestedScrollView(this).apply{addView(content)})
        dialog.setOnDismissListener{if(activeSheet===dialog)activeSheet=null};activeSheet=dialog;return dialog to content
    }
    private fun matches(c:TravelCatalog) {
        val rows=c.matches;setHeader(ui.s(T.swipe_routes),ui.s(T.swipe_sub,rows.size))
        body.removeAllViews();val layout=ui.column(16).apply{setPadding(ui.d(16),ui.d(6),ui.d(16),0)};body.addView(layout,FrameLayout.LayoutParams(-1,-1))
        val progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=maxOf(1,rows.size);progress=matchIndex;progressTintList=android.content.res.ColorStateList.valueOf(ACCENT)}
        layout.addView(progress,LinearLayout.LayoutParams(-1,ui.d(4)));layout.addView(ui.space(12))
        if(matchIndex>=rows.size){
            progress.progress=progress.max
            layout.addView(ui.column(24).apply{
                gravity=Gravity.CENTER;addView(ui.icon(TravelIcons.check_circle_outline,48,ACCENT));addView(ui.space(20));addView(ui.label(T.swipe_done,22f,INK,true));addView(ui.space(12));addView(ui.label(T.swipe_done_sub,13f,MUTED).apply{gravity=Gravity.CENTER});addView(ui.space(24))
                addView(ui.button(T.another_round){matchIndex=0;render()},LinearLayout.LayoutParams(-2,ui.d(48)))
            },LinearLayout.LayoutParams(-1,0,1f));footer(null);return
        }
        val r=rows[matchIndex];val card=ui.card(0,20)
        card.addView(ui.photoHero(r.str("photo"),196,r.str("city"),r.str("dates"),titleSize=28f,radius=0,inset=16).apply {
            addView(ui.text("${r.str("city")} · ${r.str("dates")}",12f,WHITE,true).apply{background=ui.box(0x66000000,20);setPadding(ui.d(10),ui.d(6),ui.d(10),ui.d(6))},FrameLayout.LayoutParams(-1,-2,Gravity.TOP).apply{setMargins(ui.d(16),ui.d(16),ui.d(16),0)})
        })
        card.addView(NestedScrollView(this).apply{addView(ui.column(16).apply{
            addView(ui.row().apply{addView(ui.image(r.str("avatar"),14),LinearLayout.LayoutParams(ui.d(44),ui.d(44)));addView(ui.column().apply{addView(ui.text(r.creator("author"),17f,INK,true));addView(ui.space(5));addView(ui.text(if(r.guide())r.str("home")else ui.s(T.match_percent,r.str("home"),r.count("score")),13f,MUTED))},LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=ui.d(12)})});addView(ui.space(12));addView(ui.text(r.str("reason"),13f,SECONDARY).apply{background=ui.box(FIELD,12);setPadding(ui.d(12),ui.d(12),ui.d(12),ui.d(12))});addView(ui.space(10));addView(ui.chips(r.strings("tags")))
        })},LinearLayout.LayoutParams(-1,0,1f))
        fun next(selected:Boolean){if(busy)return;action(travelBody("action" to "match","id" to r.str("id"),"selected" to selected)){matchIndex++;if(selected)toast(if(r.guide())T.matched else T.together_saved);render()}}
        var downX=0f;var downY=0f
        card.setOnTouchListener{v,event->when(event.actionMasked){MotionEvent.ACTION_DOWN->{downX=event.rawX;downY=event.rawY;true};MotionEvent.ACTION_MOVE->{val dx=event.rawX-downX;if(kotlin.math.abs(dx)>ui.d(20)&&kotlin.math.abs(dx)>kotlin.math.abs(event.rawY-downY)){v.translationX=dx*.45f;v.rotation=dx/ui.d(40);true}else false};MotionEvent.ACTION_UP->{val dx=event.rawX-downX;v.translationX=0f;v.rotation=0f;if(kotlin.math.abs(dx)>ui.d(80))next(dx>0);v.performClick();true};MotionEvent.ACTION_CANCEL->{v.translationX=0f;v.rotation=0f;true};else->false}}
        layout.addView(card,LinearLayout.LayoutParams(-1,0,1f))
        footer(ui.row().apply{
            setPadding(ui.d(40),0,ui.d(40),0)
            fun control(label:Int,icon:Int,primary:Boolean=false,click:()->Unit){addView(ui.column().apply{gravity=Gravity.CENTER;addView(ui.icon(icon,if(primary)30 else 24,if(primary)WHITE else MUTED,ui.s(label)).apply{background=ui.box(if(primary)ACCENT else WHITE,40,!primary);setOnClickListener{click()}},LinearLayout.LayoutParams(ui.d(if(primary)64 else 48),ui.d(if(primary)64 else 48)));addView(ui.space(7));addView(ui.label(label,12f,if(primary)ACCENT else MUTED,true));setOnClickListener{click()}},LinearLayout.LayoutParams(0,-2,1f))}
            control(T.skip,TravelIcons.close){next(false)};control(if(r.guide())T.like else T.together,TravelIcons.favorite,true){next(true)};control(T.reset,TravelIcons.refresh){matchIndex=0;render()}
        })
        footer.setBackgroundColor(BG);footer.getChildAt(0)?.visibility=View.GONE
        (footer.getChildAt(1) as? LinearLayout)?.setPadding(ui.d(16),ui.d(20),ui.d(16),ui.d(20))
    }
    private fun article(c:TravelCatalog) {
        val a=c.article(itemId)?:run{status(ui.s(T.unavailable));footer(null);return}
        setHeader(a.str("title"),"${a.str("destination")} · ${a.creator("author")}"){report("")}
        val content=ui.column();content.addView(ui.image(a.str("image"),16),LinearLayout.LayoutParams(-1,ui.d(180)));content.addView(ui.space(14))
        content.addView(ui.card().apply{
            addView(ui.row().apply{background=ui.box(FIELD,12);setPadding(ui.d(12),ui.d(12),ui.d(12),ui.d(12));addView(ui.image(a.str("avatar"),20),LinearLayout.LayoutParams(ui.d(40),ui.d(40)));addView(ui.column().apply{addView(ui.text(a.creator("author"),15f,INK,true));addView(ui.space(4));addView(ui.text("${a.str("city")} · ${a.str("time")}",12f,MUTED))},LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=ui.d(12)})})
            addView(ui.space(18));a.strings("paragraphs").forEach{addView(ui.text(it,14f,SECONDARY).apply{setLineSpacing(0f,1.6f)});addView(ui.space(14))};addView(ui.chips(a.strings("tags")));addView(ui.space(20));addView(ui.line());addView(ui.space(12))
            addView(ui.row().apply{
                val liked=c.selected("article_likes",itemId)
                fun countButton(code:Int,count:Int,selected:Boolean=false,click:()->Unit){
                    addView(ui.row().apply{gravity=Gravity.CENTER;background=ui.box(WHITE,12,true);minimumHeight=ui.d(40);addView(ui.icon(code,17,if(selected)0xFFE75C67.toInt()else SECONDARY));addView(ui.text("  $count",13f,SECONDARY));setOnClickListener{click()}},LinearLayout.LayoutParams(0,ui.d(40),1f).apply{marginEnd=ui.d(5)})
                }
                countButton(if(liked)TravelIcons.favorite else TravelIcons.favorite_border,c.likeCount(a),liked){action(travelBody("action" to "article_like","article_id" to itemId,"selected" to !liked))}
                countButton(TravelIcons.chat_bubble_outline,c.commentCount(a)){(body.getChildAt(0) as? NestedScrollView)?.let{it.smoothScrollTo(0,body.findViewWithTag<View>("comments_title")?.top ?: 0)}}
            })
        });content.addView(ui.space(22));content.addView(ui.label(T.comments,18f,INK,true).apply{tag="comments_title"});content.addView(ui.space(12))
        val allComments=a.rows("messages")+c.state.obj("comments").rows(itemId)
        articleAuthors=allComments.flatMap{listOf(it)+it.rows("replies")}.associate{it.str("id") to it.str("author")}
        val comments=c.articleComments(itemId)
        if(comments.isEmpty())content.addView(ui.label(T.no_comments,13f,MUTED))
        comments.forEach{comment->content.addView(comment(c,comment,comment.str("id")));content.addView(ui.space(12))}
        scroll(content);articleFooter()
    }
    private fun comment(c:TravelCatalog,row:JsonObject,rootId:String,reply:Boolean=false,replyingTo:String?=null):View=ui.card(if(reply)10 else 12).apply{
        val id=row.str("id");val liked=c.selected("comment_likes",id)
        addView(ui.row().apply{
            addView(ui.image(row.str("avatar"),if(reply)14 else 18),LinearLayout.LayoutParams(ui.d(if(reply)28 else 36),ui.d(if(reply)28 else 36)))
            addView(ui.column().apply{addView(ui.text(row.str("author"),13f,INK,true));if(replyingTo!=null){addView(ui.space(3));addView(ui.text(ui.s(T.reply_to,replyingTo),11f,MUTED))};addView(ui.space(3));addView(ui.text(commentTime(row),11f,MUTED))},LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=ui.d(8)})
            addView(ui.icon(TravelIcons.more_horiz,20,MUTED,ui.s(T.more)).apply{setOnClickListener{commentMenu(row)}},LinearLayout.LayoutParams(ui.d(36),ui.d(36)))
        });addView(ui.space(10));addView(ui.text(row.str("text"),13f,SECONDARY).apply{setLineSpacing(ui.d(4).toFloat(),1.15f)})
        addView(ui.row().apply{
            addView(ui.row().apply{minimumHeight=ui.d(36);addView(ui.icon(if(liked)TravelIcons.favorite else TravelIcons.favorite_border,15,if(liked)0xFFE75C67.toInt()else MUTED));addView(ui.text(" ${row.count("likes")+if(liked)1 else 0}",11f,MUTED));setOnClickListener{action(travelBody("action" to "comment_like","article_id" to itemId,"id" to id,"selected" to !liked))}},LinearLayout.LayoutParams(0,-2,1f))
            addView(ui.label(T.reply,11f,ACCENT).apply{setPadding(ui.d(8),ui.d(12),ui.d(8),ui.d(12));setOnClickListener{replyRoot=rootId;replyTarget=id;replyAuthor=row.str("author");articleFooter(true)}})
        })
        if(!reply)row.rows("replies").filterNot{c.selected("hidden_comments",it.str("id"))}.forEach{addView(ui.space(10));addView(comment(c,it,rootId,true,articleAuthors[it.opt("reply_to_id")]?:row.str("author")))}
    }
    private fun commentMenu(row:JsonObject) {
        val(dialog,content)=sheet(T.comment_actions);content.background=ui.box(WHITE,24);content.addView(ui.text(row.str("author"),13f,MUTED));content.addView(ui.space(16))
        content.addView(ui.card(0).apply {
            fun option(code:Int,title:Int,subtitle:Int,color:Int=SECONDARY,click:()->Unit){addView(ui.row().apply{setPadding(ui.d(16),ui.d(14),ui.d(16),ui.d(14));addView(ui.icon(code,24,color),LinearLayout.LayoutParams(ui.d(40),ui.d(40)));addView(ui.column().apply{addView(ui.label(title,16f,INK));addView(ui.space(4));addView(ui.label(subtitle,14f,MUTED))},LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=ui.d(12)});setOnClickListener{dialog.dismiss();click()}})}
            option(TravelIcons.thumb_down_outlined,T.hide,T.hide_hint){action(travelBody("action" to "comment_hide","article_id" to itemId,"id" to row.str("id")))}
            addView(ui.line());option(TravelIcons.flag_outlined,T.report,T.report_hint,0xFFE75C67.toInt()){report(row.str("id"))}
        });dialog.show()
    }
    private fun commentTime(row:JsonObject):String {
        val instant=TravelDates.utcTimestamp(row.opt("created_at"))?:return row.opt("time")
        val minutes=(System.currentTimeMillis()-instant.time)/60_000;return when{minutes<1->ui.s(T.just_now);minutes<60->ui.s(T.minutes_ago,minutes.toInt());minutes<1440->ui.s(T.hours_ago,(minutes/60).toInt());else->TravelDates.monthDay(instant)}
    }
    private fun articleFooter(focus:Boolean=false) {
        val col=ui.column()
        if(replyTarget.isNotEmpty())col.addView(ui.row().apply{addView(ui.text(ui.s(T.reply_to,replyAuthor),12f,ACCENT),LinearLayout.LayoutParams(0,-2,1f));addView(ui.icon(TravelIcons.close,20,MUTED,ui.s(T.cancel)).apply{setOnClickListener{replyRoot="";replyTarget="";replyAuthor="";articleFooter()}},LinearLayout.LayoutParams(ui.d(40),ui.d(36)))})
        val input=EditText(this).apply{setText(message);hint=ui.s(T.write_comment);textSize=TravelTypography.size(15f);setTextColor(INK);setHintTextColor(MUTED);background=ui.box(FIELD,14,true);setPadding(ui.d(18),ui.d(12),ui.d(18),ui.d(12));maxLines=4;filters=arrayOf(InputFilter.LengthFilter(500));addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){message=s.toString()};override fun afterTextChanged(s:Editable?){} })}
        col.addView(ui.row().apply{addView(input,LinearLayout.LayoutParams(0,-2,1f));addView(ui.button(T.send){val value=message.trim();if(value.isBlank()){toast(T.comment_empty);return@button};action(travelBody("action" to "comment","article_id" to itemId,"text" to value,"parent_id" to replyRoot,"target_id" to replyTarget)){message="";replyRoot="";replyTarget="";replyAuthor="";(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0);render()}},LinearLayout.LayoutParams(ui.d(58),ui.d(48)).apply{marginStart=ui.d(8)})});footer(col)
        if(focus){input.requestFocus();input.post{(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT)}}
    }
    private fun report(commentId:String) {
        val c=store.current?:return;if(c.reportReasons.isEmpty()){toast(T.unavailable);return}
        val(dialog,content)=sheet(if(commentId.isEmpty())T.report_article else T.report_comment);content.background=ui.box(WHITE,24);var reason=c.reportReasons.first();val options=FrameLayout(this);content.addView(options)
        fun reasons(){options.removeAllViews();options.addView(ui.chips(c.reportReasons,setOf(reason),true){reason=it;reasons()})};reasons()
        val details=field(content,T.report_details,"",1000);content.addView(ui.space(20));content.addView(ui.button(T.report_submit){action(travelBody("action" to "report","article_id" to itemId,"id" to commentId,"reason" to reason,"details" to details.text.toString().trim())){dialog.dismiss();toast(T.reported)}});dialog.show()
    }
    companion object {
        fun intent(context:Context,page:String,id:String="") = Intent(context,TravelActivity::class.java).putExtra("page",page).putExtra("id",id)
        fun open(context:Context,page:String,id:String=""){context.startActivity(intent(context,page,id))}
    }
}
