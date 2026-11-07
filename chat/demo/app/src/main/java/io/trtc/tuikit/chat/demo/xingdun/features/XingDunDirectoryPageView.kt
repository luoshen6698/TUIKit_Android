package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.*
import androidx.recyclerview.widget.*
import com.google.gson.JsonObject
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.presence.PresenceAvatarBinding
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.Collator
import java.util.Locale

internal fun directorySignature(profile: JsonObject): String? {
    val policy=profile.get("profile_visibility")?.takeIf { it.isJsonObject }?.asJsonObject
    if(!XingDunProfileVisibility.visible(policy,"signature"))return null
    return profile.get("signature")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()?.takeIf(String::isNotEmpty)
}

/** The directory reads the same native IM stores as chat; rows never create copied contacts. */
internal class XingDunDirectoryPageView(context: Context, initialCategory:Int=0) : LinearLayout(context) {
    private val ui=TravelUi(context)
    private val header=JourneyPageHeader(context,R.string.demo_page_contacts_title,R.string.jd_directory_subtitle)
    private val filters=JourneyFilters(context,listOf("",""),initialCategory.coerceIn(0,1),34) { category=it;render() }
    var category=initialCategory.coerceIn(0,1)
        private set
    private val body=FrameLayout(context)
    private val list=RecyclerView(context).apply { id=R.id.journey_directory_list;layoutManager=LinearLayoutManager(context);setPadding(ui.d(16),0,ui.d(16),ui.d(24));clipToPadding=false }
    private val state=ui.column().apply { gravity=Gravity.CENTER }
    private var observer:Job?=null
    private var profiles:Job?=null
    private var scope:CoroutineScope?=null
    private var owner=""
    private var loading=true
    private var failed=false
    private var revision=0
    private val signatures=HashMap<String,String?>()
    private val requested=HashSet<String>()
    private val permits=Semaphore(4)
    private data class Row(val id:String,val name:String,val subtitle:String,val avatar:String?,val team:Boolean,val footer:Boolean=false)
    private var rows=listOf<Row>()
    private val adapter=DirectoryAdapter()
    init {
        orientation=VERTICAL;setBackgroundColor(TravelUi.BG);addView(header);addView(filters)
        list.adapter=adapter;body.addView(list,FrameLayout.LayoutParams(-1,-1));body.addView(state,FrameLayout.LayoutParams(-1,-1));addView(body,LayoutParams(-1,0,1f));render()
    }
    private fun key():String {
        val s=XingDunSessionManager.currentSession()?:return ""
        return "${s.apiBaseUrl}|${s.companyId}|${s.timUserId}|${XingDunSessionManager.currentAuthenticationGeneration()}"
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow();val lifecycle=context as? LifecycleOwner?:return
        profiles=SupervisorJob();scope=CoroutineScope(Dispatchers.Main.immediate+checkNotNull(profiles))
        owner=key()
        observer=lifecycle.lifecycleScope.launch { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            combine(ContactStore.shared.state.friendList,GroupStore.shared.state.joinedGroupList,ContactStore.shared.state.blackList,LoginStore.shared.loginState.loginUserInfo) { _,_,_,_->Unit }.collect {
                if(owner!=key()){owner=key();signatures.clear();requested.clear();refresh()};render()
            }
        } }
        refresh()
    }
    override fun onDetachedFromWindow() { observer?.cancel();profiles?.cancel();scope=null;requested.clear();super.onDetachedFromWindow() }
    fun setConnectionNotice(message:CharSequence?,warning:Boolean=false){header.setConnectionNotice(message)}
    fun refresh(done:()->Unit={}) {
        val token=++revision;val account=key();loading=true;failed=false;requested.clear();signatures.clear();render()
        var remaining=3
        fun complete(success:Boolean){post { if(token!=revision || account!=key() || !isAttachedToWindow)return@post;failed=failed||!success;remaining--;if(remaining==0){loading=false;render();adapter.notifyDataSetChanged();done()} }}
        fun callback()=object:CompletionHandler{override fun onSuccess(){complete(true)};override fun onFailure(code:Int,desc:String){complete(false)}}
        ContactStore.shared.loadFriends(callback());GroupStore.shared.loadJoinedGroups(callback());ContactStore.shared.loadBlackList(callback())
        ContactStore.shared.loadFriendApplications();GroupStore.shared.loadApplications()
    }
    private fun render() {
        val session=XingDunSessionManager.currentSession()
        val ready=mineCanUseIMData(session?.timUserId,LoginStore.shared.loginState.loginUserInfo.value?.userID)
        val blocked=ContactStore.shared.state.blackList.value.map { it.userID }.toSet()
        val collator=Collator.getInstance(Locale.CHINA)
        val friends=if(ready)ContactStore.shared.state.friendList.value.filter { it.userID !in blocked }.distinctBy { it.userID }.sortedWith { a,b->collator.compare(a.displayName,b.displayName) }.map {
            Row(it.userID,it.displayName,signatures[it.userID]?:context.getString(R.string.jd_friend_subtitle),it.avatarURL,false)
        }else emptyList()
        val teams=if(ready)GroupStore.shared.state.joinedGroupList.value.distinctBy { it.groupID }.map {
            Row(it.groupID,it.groupName?.takeIf(String::isNotBlank)?:it.groupID,it.memberCount?.let { count->context.getString(R.string.jd_members,count) }?:context.getString(R.string.jd_members_pending),it.avatarURL,true)
        }else emptyList()
        filters.labels(listOf(context.getString(R.string.jd_friend_count,friends.size),context.getString(R.string.jd_team_count,teams.size)),listOf(context.getString(R.string.jd_friends),context.getString(R.string.jd_teams)))
        rows=if(category==0)friends else teams
        val total=context.getString(if(category==0)R.string.jd_friend_total else R.string.jd_team_total,rows.size)
        adapter.submitList(if(rows.isEmpty())emptyList()else rows+Row("directory.footer",total,"",null,category==1,true))
        list.visibility=if(rows.isNotEmpty())VISIBLE else GONE
        state.visibility=if(rows.isEmpty())VISIBLE else GONE;state.removeAllViews()
        if(rows.isEmpty()) {
            val pending=loading||!ready
            state.addView(ui.icon(if(category==0)0xf006c else 0xf7c8,36).apply { background=ui.box(TravelUi.PALE,24) },LayoutParams(ui.d(72),ui.d(72)))
            state.addView(ui.space(16));state.addView(ui.label(if(pending)R.string.jd_loading else if(failed)R.string.jd_retry else if(category==0)R.string.jd_no_friends else R.string.jd_no_teams,16f,TravelUi.INK,true))
            state.setOnClickListener { if(failed)refresh() }
        }
    }
    private fun requestSignature(row:Row) {
        if(row.team||!requested.add(row.id))return
        val session=XingDunSessionManager.currentSession()?:return;val account=key();val token=revision
        scope?.launch {
            try {
                val profile=permits.withPermit { XingDunSessionManager.apiClient().get<JsonObject>(session,"user/detail",mapOf("tim_user_id" to row.id),JsonObject::class.java) }
                if(account==key() && token==revision){signatures[row.id]=directorySignature(profile);render()}
            }catch(e:CancellationException){throw e}catch(_:Exception){ /* Private or unavailable profiles keep the safe generic subtitle. */ }
        }
    }
    private inner class DirectoryAdapter:androidx.recyclerview.widget.ListAdapter<Row,Holder>(object:DiffUtil.ItemCallback<Row>(){override fun areItemsTheSame(a:Row,b:Row)=a.id==b.id&&a.team==b.team;override fun areContentsTheSame(a:Row,b:Row)=a==b}) {
        override fun getItemViewType(position:Int)=if(getItem(position).footer)1 else 0
        override fun onCreateViewHolder(parent:ViewGroup,type:Int)=Holder(type)
        override fun onBindViewHolder(holder:Holder,position:Int){
            if(getItem(position).footer){holder.footer.text=getItem(position).name;return}
            val row=getItem(position);holder.name.text=row.name;holder.subtitle.text=row.subtitle
            holder.avatar.setContent(Avatar.AvatarContent.Image(row.avatar,row.name));holder.presence.bind(if(row.team)null else row.id)
            fun chat(){ChatActivity.start(context,(if(row.team)"group_" else "c2c_")+row.id)}
            holder.itemView.setOnClickListener { if(row.team)chat() else XingDunContactDetailActivity.start(context,row.id,row.name,row.avatar) }
            holder.chat.contentDescription=context.getString(R.string.jd_chat_with,row.name);holder.chat.setOnClickListener { chat() };requestSignature(row)
        }
    }
    private inner class Holder(type:Int):RecyclerView.ViewHolder(if(type==1)ui.text("",12f,TravelUi.MUTED).apply { gravity=Gravity.CENTER;setPadding(ui.d(24),ui.d(24),ui.d(24),ui.d(24)) }else ui.row().apply { clipChildren=false;clipToPadding=false;background=ui.box(-1,18,true);setPadding(ui.d(12),ui.d(10),ui.d(12),ui.d(10)) }) {
        val footer=if(type==1)itemView as TextView else TextView(context)
        val avatar=Avatar(context).apply { setSize(Avatar.AvatarSize.L);setShape(Avatar.AvatarShape.Round) }
        val presence=PresenceAvatarBinding(avatar)
        val name=ui.text("",15f,TravelUi.INK,true).apply { maxLines=1;ellipsize=TextUtils.TruncateAt.END }
        val subtitle=ui.text("",12f,TravelUi.SECONDARY).apply { maxLines=1;ellipsize=TextUtils.TruncateAt.END }
        val chat=ImageView(context).apply { setImageResource(io.trtc.tuikit.chat.uikit.R.drawable.journey_ic_chat);setPadding(ui.d(11),ui.d(11),ui.d(11),ui.d(11));background=ui.box(-1,20,true);isFocusable=true }
        init {
            itemView.layoutParams=RecyclerView.LayoutParams(-1,-2).apply { bottomMargin=ui.d(8) }
            if(type==0)(itemView as LinearLayout).apply {
                addView(avatar,LayoutParams(ui.d(48),ui.d(48)))
                addView(ui.column().apply { addView(name);addView(ui.space(3));addView(subtitle) },LayoutParams(0,-2,1f).apply { marginStart=ui.d(12);marginEnd=ui.d(8) })
                addView(chat,LayoutParams(ui.d(40),ui.d(40)));isFocusable=true
            }
        }
    }
}
