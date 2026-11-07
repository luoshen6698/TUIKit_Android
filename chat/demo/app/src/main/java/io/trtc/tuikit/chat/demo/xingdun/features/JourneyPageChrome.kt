package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.search.SearchActivity
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi

/** Native equivalent of the reference TuxingHomeHeader and directory segmented controls. */
internal class JourneyPageHeader(context: Context, title: Int, subtitle: Int) : LinearLayout(context) {
    private val ui = TravelUi(context)
    private val status = ui.text("",11f,TravelUi.MUTED)
    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(TravelUi.BG); setPadding(ui.d(16),ui.d(12),ui.d(16),ui.d(7)); minimumHeight=ui.d(58)
        addView(ui.column().apply {
            addView(ui.row().apply { addView(ui.label(title,18f,TravelUi.INK,true)); addView(status,LayoutParams(-2,-2).apply { marginStart=ui.d(6) }) })
            addView(ui.label(subtitle,11f,TravelUi.MUTED).apply { maxLines=1; ellipsize=TextUtils.TruncateAt.END })
        },LayoutParams(0,-2,1f))
        addView(XingDunMineAvatarView(context),LayoutParams(ui.d(36),ui.d(36)))
    }
    fun setConnectionNotice(value: CharSequence?) { status.text=value; status.visibility=if(value.isNullOrBlank()) View.GONE else View.VISIBLE }
}

internal class JourneyFilters(context: Context, labels: List<String>, selected: Int, private val height: Int, private val changed: (Int)->Unit) : LinearLayout(context) {
    private val ui=TravelUi(context)
    private val segments=ui.row()
    private val buttons=ArrayList<TextView>()
    var selectedIndex=selected
        private set
    init {
        orientation=VERTICAL;setPadding(ui.d(16),0,ui.d(16),ui.d(16));setBackgroundColor(TravelUi.BG)
        addView(ui.row().apply {
            setPadding(ui.d(14),0,ui.d(14),0);background=ui.box(-1,12,true)
            addView(ImageView(context).apply { setImageResource(io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_search);setColorFilter(TravelUi.MUTED) },LayoutParams(ui.d(18),ui.d(18)))
            addView(ui.label(R.string.jd_search,14f,TravelUi.MUTED).apply { maxLines=1;ellipsize=TextUtils.TruncateAt.END },LayoutParams(0,-2,1f).apply { marginStart=ui.d(16) })
            contentDescription=context.getString(R.string.jd_search);isFocusable=true;setOnClickListener { SearchActivity.start(context) }
        },LayoutParams(-1,ui.d(48)))
        addView(ui.space(12));segments.setPadding(ui.d(3),ui.d(3),ui.d(3),ui.d(3));segments.background=ui.box(-1,20,true);segments.elevation=ui.d(1).toFloat()
        labels.forEachIndexed { index,label ->
            val button=ui.text(label,12f,TravelUi.MUTED,true).apply {
                gravity=Gravity.CENTER;maxLines=1;ellipsize=TextUtils.TruncateAt.END;isFocusable=true
                setOnClickListener { select(index);changed(index) }
            }
            buttons.add(button);segments.addView(button,LayoutParams(0,-1,1f))
        }
        addView(segments,LayoutParams(-1,ui.d(height)));select(selected)
    }
    fun labels(values:List<String>, semantics:List<String> = values) { buttons.forEachIndexed { i,b -> b.text=values[i];b.contentDescription=semantics[i] } }
    fun select(index:Int) {
        selectedIndex=index
        buttons.forEachIndexed { i,b -> b.isSelected=i==index;b.background=ui.box(if(i==index)TravelUi.PALE else android.graphics.Color.TRANSPARENT,18);b.setTextColor(if(i==index)TravelUi.ACCENT else TravelUi.MUTED) }
    }
}
