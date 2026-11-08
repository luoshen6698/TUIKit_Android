package io.trtc.tuikit.chat.demo.xingdun.launch

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Old internal/deep-link entries now converge on the inline enterprise selector in sign-in. */
class XingDunEnterpriseAccessActivity:AppCompatActivity() {
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        startActivity(Intent(this,XingDunLaunchActivity::class.java).apply {
            data=intent?.data
            putExtra(XingDunLaunchActivity.EXTRA_DISABLE_AUTO_RESTORE,true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        finish()
    }
}
