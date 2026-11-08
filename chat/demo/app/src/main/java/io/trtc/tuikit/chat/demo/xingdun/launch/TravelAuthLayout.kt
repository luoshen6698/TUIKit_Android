package io.trtc.tuikit.chat.demo.xingdun.launch

import android.content.Context
import android.app.Activity
import androidx.core.view.WindowCompat
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.*
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.features.travel.TravelUi

/** Native counterpart of the reference MIT TravelAuthLayout. The photo never recrops for the IME. */
class TravelAuthLayout @JvmOverloads constructor(context:Context,attrs:AttributeSet?=null):FrameLayout(context,attrs) {
    private val ui=TravelUi(context)
    private val options=context.obtainStyledAttributes(attrs,R.styleable.TravelAuthLayout)
    private val scene=options.getInt(R.styleable.TravelAuthLayout_authScene,0)
    private val title=options.getResourceId(R.styleable.TravelAuthLayout_authTitle,R.string.xingdun_welcome_back)
    private val subtitle=options.getResourceId(R.styleable.TravelAuthLayout_authSubtitle,R.string.ta_login_subtitle)
    private val gap=View(context)
    private var systemTop=0
    private var keyboard=false
    init { options.recycle();configureTravelWindow(context,false);setBackgroundColor(0xFFF9F8F3.toInt()) }
    override fun onFinishInflate() {
        super.onFinishInflate();val form=getChildAt(0);removeAllViews()
        val backdrop=TravelLandscapeView(context,scene);addView(backdrop,LayoutParams(-1,-1))
        val column=ui.column().apply { gravity=Gravity.CENTER_HORIZONTAL;setPadding(ui.d(8),ui.d(12),ui.d(8),ui.d(20)) }
        val content=object:LinearLayout(context) {
            init { orientation=VERTICAL }
            override fun onMeasure(w:Int,h:Int) { super.onMeasure(MeasureSpec.makeMeasureSpec(minOf(MeasureSpec.getSize(w),ui.d(472)),MeasureSpec.EXACTLY),h) }
        }
        val header=ui.row().apply { setPadding(ui.d(if(scene==0)16 else 0),0,ui.d(16),0) }
        if(scene!=0)header.addView(ImageView(context).apply {
            id=if(scene==1)R.id.xingdun_registration_close else R.id.xingdun_password_reset_close
            setImageResource(R.drawable.travel_auth_back);setPadding(ui.d(12),ui.d(12),ui.d(12),ui.d(12));contentDescription=context.getString(R.string.xingdun_back);isFocusable=true
        },LinearLayout.LayoutParams(ui.d(48),ui.d(48)))
        header.addView(XingDunEnterpriseLogoView(context).apply {
            id=when(scene){1->R.id.xingdun_registration_logo;2->R.id.xingdun_password_reset_logo;else->R.id.xingdun_auth_logo};useTravelBrand()
        },LinearLayout.LayoutParams(ui.d(40),ui.d(40)))
        header.addView(ui.label(R.string.demo_app_name,20f,INK,true).apply { id=when(scene){1->R.id.xingdun_registration_brand_name;2->R.id.xingdun_password_reset_brand_name;else->R.id.xingdun_brand_name} },LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=ui.d(12) })
        if(scene==0)header.addView(Button(context).apply { id=R.id.xingdun_language;setText(R.string.demo_settings_language);textSize=12f;setTextColor(MUTED);setBackgroundColor(Color.TRANSPARENT);isAllCaps=false;minWidth=0;setPadding(0,0,0,0) },LinearLayout.LayoutParams(ui.d(56),ui.d(40)))
        content.addView(header);content.addView(gap,LinearLayout.LayoutParams(1,ui.d(100)))
        content.addView(ui.column().apply {
            setPadding(ui.d(16),0,ui.d(16),0)
            addView(ui.label(title,28f,INK,true).apply { if(scene==0)id=R.id.xingdun_welcome_title })
            addView(ui.space(8));addView(ui.text(context.getString(subtitle,context.getString(R.string.demo_app_name)),14f,MUTED).apply { if(scene==0)id=R.id.xingdun_login_subtitle })
            addView(ui.space(24));addView(form,LinearLayout.LayoutParams(-1,-2))
        })
        column.addView(content,LinearLayout.LayoutParams(-1,-2))
        val scroll=ScrollView(context).apply { isFillViewport=true;clipToPadding=false;addView(column,LayoutParams(-1,-2)) };addView(scroll,LayoutParams(-1,-1))
        ViewCompat.setOnApplyWindowInsetsListener(this) { _,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());val ime=insets.getInsets(WindowInsetsCompat.Type.ime())
            systemTop=bars.top;keyboard=ime.bottom>bars.bottom
            scroll.setPadding(0,bars.top,0,maxOf(bars.bottom,ime.bottom));updateGap();insets
        }
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);updateGap()}
    private fun updateGap(){
        val metrics=resources.displayMetrics;val screenH=metrics.heightPixels/metrics.density
        val compact=screenH<650 || metrics.widthPixels>metrics.heightPixels
        val fraction=when(scene){1->.18f;2->.26f;else->.30f}
        val titleTop=if(compact||keyboard)88f else (screenH*fraction).coerceIn(140f,280f)
        gap.layoutParams=LinearLayout.LayoutParams(1,ui.d((titleTop-systemTop/metrics.density-64).coerceIn(20f,200f).toInt()))
    }
    companion object { val INK=0xFF203B37.toInt();val MUTED=0xFF405E56.toInt() }
}

private class TravelLandscapeView(context:Context,scene:Int):FrameLayout(context) {
    private val ui=TravelUi(context)
    init {
        val fraction=when(scene){1->.18f;2->.26f;else->.30f}
        val compact=resources.configuration.screenHeightDp<650 || resources.configuration.screenWidthDp>resources.configuration.screenHeightDp
        val mist=if(compact).08f else fraction-.09f
        val drawable=context.getDrawable(R.drawable.travel_auth_landscape) as BitmapDrawable
        addView(Photo(context,drawable.bitmap),LayoutParams(-1,-1))
        addView(Photo(context,drawable.bitmap,mist).apply { if(Build.VERSION.SDK_INT>=31)setRenderEffect(RenderEffect.createBlurEffect(ui.d(4).toFloat(),ui.d(4).toFloat(),Shader.TileMode.CLAMP)) },LayoutParams(-1,-1))
        addView(object:View(context){
            private val paint=Paint()
            override fun onDraw(canvas:Canvas){
                val fullHeight=resources.displayMetrics.heightPixels.toFloat()
                paint.shader=LinearGradient(0f,0f,0f,fullHeight,intArrayOf(0x00EFF1E4,0x00EFF1E4,0xB8EFF1E4.toInt(),0xB8EFF1E4.toInt(),0x99EFF1E4.toInt()),floatArrayOf(0f,if(resources.configuration.screenHeightDp<650).08f else mist,(if(resources.configuration.screenHeightDp<650).08f else mist)+.22f,.78f,1f),Shader.TileMode.CLAMP)
                canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
            }
        },LayoutParams(-1,-1))
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    private class Photo(context:Context,private val bitmap:Bitmap,private val mist:Float?=null):View(context) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val mask=Paint().apply { xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        override fun onDraw(canvas:Canvas){
            val fullHeight=resources.displayMetrics.heightPixels.toFloat()
            val scale=maxOf(width.toFloat()/bitmap.width,fullHeight/bitmap.height)
            val checkpoint=if(mist!=null)canvas.saveLayer(0f,0f,width.toFloat(),height.toFloat(),null)else -1
            val matrix=Matrix().apply { setScale(scale,scale);postTranslate((width-bitmap.width*scale)/2,0f) };canvas.drawBitmap(bitmap,matrix,paint)
            if(mist!=null){mask.shader=LinearGradient(0f,0f,0f,fullHeight,intArrayOf(Color.TRANSPARENT,Color.TRANSPARENT,Color.BLACK,Color.BLACK),floatArrayOf(0f,if(resources.configuration.screenHeightDp<650).08f else mist,(if(resources.configuration.screenHeightDp<650).08f else mist)+.24f,1f),Shader.TileMode.CLAMP);canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),mask);canvas.restoreToCount(checkpoint)}
        }
    }
}

class TravelStartupView @JvmOverloads constructor(context:Context,attrs:AttributeSet?=null):FrameLayout(context,attrs) {
    private val ui=TravelUi(context)
    init {
        configureTravelWindow(context,true);setBackgroundColor(0xFFF9F8F3.toInt())
        addView(ImageView(context).apply { setImageResource(R.drawable.travel_startup_landscape);scaleType=ImageView.ScaleType.CENTER_CROP;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO },LayoutParams(-1,-1))
        val column=ui.column().apply { setPadding(ui.d(34),ui.d(12),ui.d(34),ui.d(30)) }
        column.addView(ui.row().apply {
            addView(ImageView(context).apply { setImageResource(R.drawable.xingdun_brand_logo);scaleType=ImageView.ScaleType.FIT_CENTER;background=ui.box(-1,10);clipToOutline=true;contentDescription=context.getString(R.string.demo_app_name) },LinearLayout.LayoutParams(ui.d(48),ui.d(48)))
            addView(ui.label(R.string.demo_app_name,20f,0xFF2D393A.toInt(),true),LinearLayout.LayoutParams(0,-2,1f).apply { marginStart=ui.d(12) })
        })
        column.addView(ui.space(30));column.addView(ui.label(R.string.ta_startup_headline,if(androidx.core.os.ConfigurationCompat.getLocales(resources.configuration)[0]?.language=="en")32f else 40f,0xFF293537.toInt(),true).apply { setLineSpacing(ui.d(6).toFloat(),1f) })
        column.addView(ui.space(16));column.addView(ui.label(R.string.ta_tagline,14f,0xFF637A75.toInt()))
        column.addView(View(context),LinearLayout.LayoutParams(1,0,1f))
        column.addView(ui.label(R.string.ta_ready,12f,0xFF637A75.toInt()).apply { id=R.id.xingdun_startup_status;gravity=Gravity.CENTER },LinearLayout.LayoutParams(-1,-2))
        column.addView(ui.space(12));column.addView(ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal).apply { id=R.id.xingdun_startup_progress;isIndeterminate=true;indeterminateTintList=android.content.res.ColorStateList.valueOf(0xFF365C54.toInt()) },LinearLayout.LayoutParams(ui.d(136),ui.d(3)).apply { gravity=Gravity.CENTER_HORIZONTAL })
        column.addView(Button(context).apply { id=R.id.xingdun_startup_retry;setText(R.string.xingdun_retry);visibility=GONE },LinearLayout.LayoutParams(-2,-2).apply { gravity=Gravity.CENTER_HORIZONTAL;topMargin=ui.d(12) })
        column.minimumHeight=ui.d(580)
        addView(ScrollView(context).apply { isFillViewport=true;addView(column,LayoutParams(-1,-2)) },LayoutParams(-1,-1))
        ViewCompat.setOnApplyWindowInsetsListener(column){v,insets->val b=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(ui.d(34),b.top+ui.d(12),ui.d(34),b.bottom+ui.d(30));insets}
    }
}

private fun configureTravelWindow(context:Context,startup:Boolean) {
    val activity=context as? Activity?:return
    WindowCompat.setDecorFitsSystemWindows(activity.window,false)
    activity.window.statusBarColor=Color.TRANSPARENT
    activity.window.navigationBarColor=if(startup)0xFFF9F8F3.toInt() else 0xFFE0E5D8.toInt()
    WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply { isAppearanceLightStatusBars=true;isAppearanceLightNavigationBars=true }
    activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
}

/** Empty transient status messages occupy no space; all direct validation writes remain visible. */
class TravelAuthStatusView @JvmOverloads constructor(context:Context,attrs:AttributeSet?=null):androidx.appcompat.widget.AppCompatTextView(context,attrs) {
    override fun setText(text:CharSequence?,type:BufferType?) {
        super.setText(text,type?:BufferType.NORMAL)
        visibility=if(text.isNullOrBlank())GONE else VISIBLE
        if(!text.isNullOrBlank())post { if(isAttachedToWindow && isShown && this.text.isNotBlank())requestRectangleOnScreen(Rect(0,0,width,height),true) }
    }
}
