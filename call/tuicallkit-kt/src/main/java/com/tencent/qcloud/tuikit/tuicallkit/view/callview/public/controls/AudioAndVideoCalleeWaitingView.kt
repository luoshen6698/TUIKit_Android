package com.tencent.qcloud.tuikit.tuicallkit.view.callview.public.controls

import android.content.Context
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.constraintlayout.utils.widget.ImageFilterView
import com.tencent.qcloud.tuikit.tuicallkit.R
import io.trtc.tuikit.atomicxcore.api.call.CallStore

class AudioAndVideoCalleeWaitingView(context: Context) : RelativeLayout(context) {
    private val endCallAction = CallEndActionController(this)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        this.layoutParams?.width = LayoutParams.MATCH_PARENT
        this.layoutParams?.height = LayoutParams.MATCH_PARENT
        endCallAction.attach()
        initView()
    }

    override fun onDetachedFromWindow() {
        endCallAction.detach()
        super.onDetachedFromWindow()
    }

    private fun initView() {
        LayoutInflater.from(context).inflate(R.layout.callview_function_view_invited_waiting, this)
        val layoutReject: LinearLayout = findViewById(R.id.ll_reject)
        val layoutDialing: LinearLayout = findViewById(R.id.ll_answer)
        val imageViewReject: ImageFilterView = findViewById(R.id.img_reject)
        layoutDialing.isEnabled = true

        layoutReject.setOnClickListener {
            endCallAction.run(true, layoutReject, imageViewReject, layoutDialing)
        }
        layoutDialing.setOnClickListener {
            if (!layoutDialing.isEnabled) {
                return@setOnClickListener
            }
            CallStore.shared.accept(null)
        }
    }
}
