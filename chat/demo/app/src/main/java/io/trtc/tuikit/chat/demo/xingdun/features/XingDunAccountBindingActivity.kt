package io.trtc.tuikit.chat.demo.xingdun.features

import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonElement
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi
import io.trtc.tuikit.chat.demo.xingdun.features.travel.opt
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Account ID, phone and email use the same native profile/binding APIs as security settings. */
class XingDunAccountBindingActivity : BaseActivity() {
    private lateinit var ui: TravelUi
    private lateinit var content: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); if(isFinishing)return
        ui=TravelUi(this)
        val root=ui.column().apply { setBackgroundColor(0xFFF8F9FC.toInt()) }
        root.addView(ui.row().apply {
            addView(ui.icon(0xe092,24).apply { contentDescription=getString(R.string.tx_back); setOnClickListener{finish()} },LinearLayout.LayoutParams(ui.d(48),ui.d(56)))
            addView(ui.label(R.string.md_binding,18f,0xFF1A1A2E.toInt(),true))
        })
        content=ui.column(16);root.addView(ScrollView(this).apply{addView(content)},LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root){v,i->val b=i.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(0,b.top,0,b.bottom);i}
    }
    override fun onResume(){super.onResume();if(::content.isInitialized)load()}
    private fun load(){
        content.removeAllViews();content.addView(ui.label(R.string.tx_loading));val session=XingDunSessionManager.currentSession()?:return
        val generation=XingDunSessionManager.currentAuthenticationGeneration()
        lifecycleScope.launch{
            try{
                val profile=XingDunSessionManager.apiClient().get<JsonElement>(session,"user/profile",emptyMap(),JsonElement::class.java).asJsonObject
                if(XingDunSessionManager.currentSession()?.timUserId!=session.timUserId || XingDunSessionManager.currentAuthenticationGeneration()!=generation)return@launch
                check(profile.opt("tim_user_id").ifBlank{session.timUserId}==session.timUserId)
                content.removeAllViews();content.addView(ui.label(R.string.md_account,13f,TravelUi.MUTED));content.addView(ui.space(8))
                content.addView(ui.card().apply{addView(ui.text(profile.opt("custom_id").ifBlank{session.username?:session.timUserId},15f))});content.addView(ui.space(16));content.addView(ui.label(R.string.md_binding_info,13f,TravelUi.MUTED));content.addView(ui.space(8))
                fun row(title:Int,key:String,mode:String){content.addView(ui.card().apply{
                    addView(ui.row().apply{addView(ui.label(title),LinearLayout.LayoutParams(0,-2,1f));addView(ui.text(profile.opt(key).ifBlank{getString(R.string.xingdun_not_set)},14f,TravelUi.MUTED));addView(ui.icon(0xe15f,20,TravelUi.MUTED))})
                    setOnClickListener{XingDunFeatureActivity.start(this@XingDunAccountBindingActivity,mode)}
                });content.addView(ui.space(8))}
                row(R.string.xingdun_phone,"phone",XingDunFeatureActivity.MODE_BIND_PHONE);row(R.string.xingdun_email,"email",XingDunFeatureActivity.MODE_BIND_EMAIL)
            }catch(e:CancellationException){throw e}catch(_:Exception){content.removeAllViews();content.addView(ui.label(R.string.md_refresh_failed));content.addView(ui.button(R.string.tx_retry){load()})}
        }
    }
}
