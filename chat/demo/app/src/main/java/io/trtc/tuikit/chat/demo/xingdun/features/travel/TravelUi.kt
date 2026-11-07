package io.trtc.tuikit.chat.demo.xingdun.features.travel

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.NestedScrollView
import com.bumptech.glide.Glide
import com.google.gson.JsonObject
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiException

internal class TravelUi(val context: Context) {
    fun d(v: Int) = (v * context.resources.displayMetrics.density + .5f).toInt()
    fun s(id: Int, vararg args: Any) = context.getString(id, *args)
    fun box(color: Int = WHITE, radius: Int = 16, border: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = d(radius).toFloat(); if (border) setStroke(d(1), BORDER)
    }
    fun column(pad: Int = 0) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(d(pad), d(pad), d(pad), d(pad)) }
    fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    fun text(value: String, size: Float = 14f, color: Int = INK, bold: Boolean = false) = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(-2, -2)
        text = value; textSize = TravelTypography.size(size); setTextColor(color); includeFontPadding = false
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setLineSpacing(d(2).toFloat(), 1f)
    }
    fun label(id: Int, size: Float = 14f, color: Int = INK, bold: Boolean = false) = text(s(id), size, color, bold)
    fun icon(code: Int, size: Int = 22, color: Int = ACCENT, description: String? = null) = text(String(Character.toChars(code)), size.toFloat(), color).apply {
        textSize = size.toFloat(); typeface = Typeface.createFromAsset(context.assets, "fonts/travel-material-icons.otf"); gravity = Gravity.CENTER; contentDescription = description
    }
    fun image(path: String, radius: Int = 0) = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(PALE)
        if (radius > 0) { background = box(PALE, radius); clipToOutline = true }
        if (safeTravelMedia(path)) Glide.with(this).load(if (path.startsWith("assets/")) "file:///android_asset/${path.removePrefix("assets/")}" else path).into(this)
    }
    fun space(height: Int) = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, d(height)) }
    fun line() = View(context).apply { setBackgroundColor(BORDER); layoutParams = LinearLayout.LayoutParams(-1, d(1)) }
    fun card(padding: Int = 16, radius: Int = 16) = column(padding).apply { background = box(WHITE, radius, true); clipToOutline = true }
    fun button(id: Int, outlined: Boolean = false, color: Int = ACCENT, onClick: () -> Unit): TextView = text(s(id), 15f, if (outlined) color else WHITE, true).apply {
        gravity = Gravity.CENTER; setPadding(d(12), 0, d(12), 0); minHeight = d(48); background = box(if (outlined) WHITE else color, 16, outlined)
        setOnClickListener { onClick() }; isFocusable = true; layoutParams = LinearLayout.LayoutParams(-1, d(48))
    }
    fun chips(values: Collection<String>, selected: Set<String> = emptySet(), choice: Boolean = false, onClick: ((String) -> Unit)? = null): View = TravelFlow(context, d(8)).apply {
        values.forEach { value -> addView(text(value, if (choice) 13f else 12f, if (value in selected) WHITE else if (choice) SECONDARY else ACCENT, choice).apply {
            setPadding(d(if (choice) 14 else 10), d(if (choice) 9 else 5), d(if (choice) 14 else 10), d(if (choice) 9 else 5))
            background = box(if (value in selected) ACCENT else if (choice) FIELD else PALE, 18, choice && value !in selected)
            if (onClick != null) { setOnClickListener { onClick(value) }; isFocusable = true }
        }) }
    }
    fun photoHero(path: String, height: Int, title: String, subtitle: String = "", eyebrow: String = "", titleSize: Float = 26f, radius: Int = 16, tags: List<String> = emptyList(), inset: Int = 20): FrameLayout = FrameLayout(context).apply {
        background = box(WHITE, radius); clipToOutline = true
        addView(image(path), FrameLayout.LayoutParams(-1, -1))
        addView(View(context).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x19000000, 0xB3000000.toInt())) }, FrameLayout.LayoutParams(-1, -1))
        addView(column().apply {
            if (eyebrow.isNotEmpty()) { addView(text(eyebrow, 12f, 0xFFD8D8D8.toInt(), true)); addView(space(4)) }
            addView(text(title, titleSize, WHITE, true)); if(tags.isNotEmpty()){addView(space(12));addView(TravelFlow(context,d(6)).apply{tags.forEach{tag->addView(text(tag,12f,WHITE).apply{background=box(0x40FFFFFF,18);setPadding(d(10),d(6),d(10),d(6))})}})}; if (subtitle.isNotEmpty()) { addView(space(4)); addView(text(subtitle, 14f, 0xFFDDDDDD.toInt())) }
        }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(d(inset), d(16), d(16), d(inset)) })
        layoutParams = LinearLayout.LayoutParams(-1, d(height))
    }
    fun articleCard(catalog: TravelCatalog, article: JsonObject, open: () -> Unit): View = card(0).apply {
        addView(photoHero(article.str("image"), 140, article.str("title"), titleSize = 16f, radius = 0).apply {
            addView(text(article.str("destination"), 12f, ACCENT, true).apply { setPadding(d(8), d(5), d(8), d(5)); background = box(0xE6FFFFFF.toInt(), 10) }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(d(12), d(12), 0, 0) })
        })
        addView(column(14).apply {
            addView(text(article.str("description"), 13f, SECONDARY).apply { setLineSpacing(d(3).toFloat(), 1.15f) }); addView(space(10)); addView(chips(article.strings("tags"))); addView(space(12)); addView(line()); addView(space(12))
            addView(row().apply {
                addView(image(article.str("avatar"), 10), LinearLayout.LayoutParams(d(28), d(28)))
                addView(column().apply { addView(text(article.creator("author"), 13f, INK, true)); addView(space(3)); addView(text(article.str("time"), 11f, MUTED)) }, LinearLayout.LayoutParams(0,-2,1f).apply { marginStart = d(8) })
                addView(icon(if (catalog.selected("article_likes", article.str("id"))) TravelIcons.favorite else TravelIcons.favorite_border, 17, MUTED)); addView(text(" ${catalog.likeCount(article)}  ", 11f, MUTED)); addView(icon(TravelIcons.chat_bubble_outline, 17, MUTED)); addView(text(" ${catalog.commentCount(article)}", 11f, MUTED))
            })
        }); setOnClickListener { open() }
    }
    fun error(error: Throwable): String = when {
        error is XingDunApiException && error.httpStatus == 404 -> s(T.api_missing)
        error is XingDunApiException -> error.message ?: s(T.load_failed)
        else -> s(T.load_failed)
    }
    companion object {
        val BG = Color.rgb(247,249,250); val INK = Color.rgb(48,56,61); val MUTED = Color.rgb(154,167,179); val ACCENT = Color.rgb(75,105,117)
        val BRONZE = Color.rgb(146,119,87); val BORDER = Color.rgb(231,229,226); val SECONDARY = Color.rgb(99,113,123); val PALE = Color.rgb(237,242,244); val FIELD = Color.rgb(244,243,241); const val WHITE = Color.WHITE
    }
}
/** A small wrapping layout keeps server-supplied tag lengths and font scaling usable. */
internal class TravelFlow(context: Context, private val gap: Int) : ViewGroup(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val max = MeasureSpec.getSize(widthMeasureSpec); var x=0; var y=0; var line=0
        for (i in 0 until childCount) { val c=getChildAt(i); measureChild(c,widthMeasureSpec,heightMeasureSpec); if (x>0 && x+c.measuredWidth>max) { x=0; y+=line+gap; line=0 }; x+=c.measuredWidth+gap; line=maxOf(line,c.measuredHeight) }
        setMeasuredDimension(max, resolveSize(y+line,heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) { var x=0;var y=0;var line=0; for(i in 0 until childCount) { val c=getChildAt(i);if(x>0 && x+c.measuredWidth>width){x=0;y+=line+gap;line=0};c.layout(x,y,x+c.measuredWidth,y+c.measuredHeight);x+=c.measuredWidth+gap;line=maxOf(line,c.measuredHeight) } }
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT)
}
