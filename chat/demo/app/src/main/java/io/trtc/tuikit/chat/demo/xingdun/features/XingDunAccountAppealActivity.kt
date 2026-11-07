package io.trtc.tuikit.chat.demo.xingdun.features

import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonElement
import io.trtc.tuikit.chat.app.BuildConfig
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

internal data class MineAppeal(val account:String,val contact:String,val reason:String,val type:Int) {
    fun valid()=account.trim().isNotEmpty() && account.length<=80 && contact.trim().isNotEmpty() && contact.length<=128 && reason.trim().length in 10..600 && type in 0..3
    fun content(typeName:String)="[Account appeal / 账号申诉]\n$typeName\nAccount: ${account.trim()}\nContact: ${contact.trim()}\n\n${reason.trim()}"
}

/** Real server acknowledgements use Xuyinfang's existing account-feedback channel. */
class XingDunAccountAppealActivity : BaseActivity() {
    private lateinit var ui:TravelUi
    private lateinit var account:EditText
    private lateinit var contact:EditText
    private lateinit var reason:EditText
    private lateinit var type:Spinner
    private lateinit var send:TextView
    private lateinit var status:TextView
    private var busy=false
    private var pending=""
    private var requestId=""
    private val types=intArrayOf(R.string.md_appeal_banned,R.string.md_appeal_login,R.string.md_appeal_deleted,R.string.md_appeal_other)
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);if(isFinishing)return
        ui=TravelUi(this);pending=savedInstanceState?.getString("pending").orEmpty();requestId=savedInstanceState?.getString("request").orEmpty()
        val root=ui.column().apply{setBackgroundColor(0xFFF8F9FC.toInt())};root.addView(ui.row().apply{
            addView(ui.icon(0xe092,24).apply{contentDescription=getString(R.string.tx_back);setOnClickListener{leave()}},LinearLayout.LayoutParams(ui.d(48),ui.d(56)));addView(ui.label(R.string.md_appeal,18f,0xFF1A1A2E.toInt(),true))
        })
        val form=ui.column(16);form.addView(ui.label(R.string.md_appeal_channel,13f,TravelUi.SECONDARY));form.addView(ui.space(16));form.addView(ui.label(R.string.md_appeal_type,13f))
        type=Spinner(this).apply{adapter=ArrayAdapter(this@XingDunAccountAppealActivity,android.R.layout.simple_spinner_dropdown_item,types.map{getString(it)});setSelection(savedInstanceState?.getInt("type")?:0)};form.addView(type)
        fun field(label:Int,key:String,max:Int,multi:Boolean=false)=EditText(this).apply{
            hint=getString(label);setText(savedInstanceState?.getString(key).orEmpty());minHeight=ui.d(if(multi)150 else 52);textSize=15f;setTextColor(TravelUi.INK);setPadding(ui.d(16),ui.d(12),ui.d(16),ui.d(12));background=ui.box(-1,12,true);isSingleLine=!multi;filters=arrayOf(InputFilter.LengthFilter(max));if(multi){gravity=Gravity.TOP;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE}
            form.addView(ui.space(12));form.addView(this,LinearLayout.LayoutParams(-1,-2))
        }
        account=field(R.string.md_appeal_account,"account",80);contact=field(R.string.md_appeal_contact,"contact",128);reason=field(R.string.md_appeal_reason,"reason",600,true)
        status=ui.text("",13f,TravelUi.SECONDARY);form.addView(ui.space(16));form.addView(status);send=ui.button(R.string.md_submit,color=0xFF4C6EF5.toInt()){submit()};form.addView(send);root.addView(ScrollView(this).apply{addView(form)},LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root){v,i->val b=i.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(0,b.top,0,b.bottom);i}
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){leave()}})
    }
    override fun onSaveInstanceState(b:Bundle){b.putString("pending",pending);b.putString("request",requestId);b.putString("account",account.text.toString());b.putString("contact",contact.text.toString());b.putString("reason",reason.text.toString());b.putInt("type",type.selectedItemPosition);super.onSaveInstanceState(b)}
    private fun leave(){
        if(busy)return
        if(listOf(account,contact,reason).none{it.text.isNotEmpty()}){finish();return}
        AlertDialog.Builder(this).setTitle(R.string.tx_discard).setMessage(R.string.tx_discard_body).setNegativeButton(R.string.tx_continue_edit,null).setPositiveButton(R.string.tx_discard_yes){_,_->finish()}.show()
    }
    private fun submit(){
        if(busy)return
        val value=MineAppeal(account.text.toString().trim(),contact.text.toString().trim(),reason.text.toString().trim(),type.selectedItemPosition)
        if(!value.valid()){status.setText(R.string.md_appeal_invalid);return}
        val session=XingDunSessionManager.currentSession()?:return;val gen=XingDunSessionManager.currentAuthenticationGeneration()
        val payload="${session.apiBaseUrl}|${session.companyId}|${session.timUserId}|$gen|"+com.google.gson.Gson().toJson(value)+value.content(getString(types[value.type]))
        if(payload!=pending){pending=payload;requestId=UUID.randomUUID().toString()}
        busy=true;listOf(account,contact,reason,type,send).forEach{it.isEnabled=false};status.setText(R.string.tx_saving)
        lifecycleScope.launch{
            try{
                val receipt=XingDunSessionManager.apiClient().postMultipart<JsonElement>(session,"feedback/save",mapOf("feedback_type" to "account","content" to value.content(getString(types[value.type])),"contact" to value.contact,"client_request_id" to requestId,"platform" to "android","app_version" to BuildConfig.VERSION_NAME,"app_build" to BuildConfig.VERSION_CODE,"os_version" to Build.VERSION.RELEASE,"device_model" to Build.MODEL),emptyList(),JsonElement::class.java).asJsonObject
                check(XingDunSessionManager.currentSession()?.timUserId==session.timUserId && XingDunSessionManager.currentAuthenticationGeneration()==gen)
                check(receipt.get("id")?.asInt?.let{it>0}==true);val number=receipt.get("feedback_no")?.asString.orEmpty();check(number.isNotBlank())
                pending="";requestId="";reason.text.clear();status.text=getString(R.string.md_appeal_sent,number)
            }catch(e:CancellationException){throw e}catch(_:Exception){status.setText(R.string.md_submit_failed)}finally{busy=false;listOf(account,contact,reason,type,send).forEach{it.isEnabled=true}}
        }
    }
}
