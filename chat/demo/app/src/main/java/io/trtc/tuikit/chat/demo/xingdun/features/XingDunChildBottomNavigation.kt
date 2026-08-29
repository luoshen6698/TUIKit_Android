package io.trtc.tuikit.chat.demo.xingdun.features

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.main.MainActivity

/** Keeps product-owned child screens inside the same four-tab navigation model as iOS. */
class XingDunChildBottomNavigation @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private data class Tab(
        val route: String,
        val title: Int,
        val icon: Int,
        val cutout: Int = 0,
    )

    private val tabs = listOf(
        Tab(
            MainActivity.TAB_MESSAGES,
            R.string.demo_tab_messages,
            R.drawable.demo_ic_tab_messages,
            R.drawable.demo_ic_tab_messages_lines,
        ),
        Tab(MainActivity.TAB_CONTACTS, R.string.demo_tab_contacts, R.drawable.demo_ic_tab_contacts),
        Tab(MainActivity.TAB_WORKSPACE, R.string.demo_tab_calls, R.drawable.demo_ic_tab_calls),
        Tab(MainActivity.TAB_PROFILE, R.string.demo_tab_me, R.drawable.demo_ic_tab_me),
    )
    private val nav = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = 62.dp()
        setPadding(3.dp(), 3.dp(), 3.dp(), 3.dp())
    }
    private val tabViews = linkedMapOf<String, LinearLayout>()
    private val tabIcons = linkedMapOf<String, ImageView>()
    private val tabLabels = linkedMapOf<String, TextView>()
    private val tabCutouts = linkedMapOf<String, ImageView>()
    private var selectedRoute: String = MainActivity.TAB_PROFILE

    init {
        clipChildren = false
        clipToPadding = false
        addView(nav, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = 12.dp()
            marginEnd = 12.dp()
            topMargin = 4.dp()
            bottomMargin = 8.dp()
        })
        tabs.forEach { tab ->
            val icon = ImageView(context).apply {
                setImageResource(tab.icon)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val iconContainer = FrameLayout(context).apply {
                addView(icon, LayoutParams(24.dp(), 24.dp()))
                if (tab.cutout != 0) {
                    val cutout = ImageView(context).apply {
                        setImageResource(tab.cutout)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                    addView(cutout, LayoutParams(24.dp(), 24.dp()))
                    tabCutouts[tab.route] = cutout
                }
            }
            val label = TextView(context).apply {
                setText(tab.title)
                gravity = Gravity.CENTER
                includeFontPadding = false
                textSize = 10f
            }
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 8.dp(), 0, 9.dp())
                isClickable = true
                isFocusable = true
                contentDescription = context.getString(tab.title)
                addView(iconContainer, LinearLayout.LayoutParams(24.dp(), 24.dp()))
                addView(label, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 1.dp()
                })
            }
            nav.addView(item, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            tabViews[tab.route] = item
            tabIcons[tab.route] = icon
            tabLabels[tab.route] = label
        }
        refreshColors()
    }

    fun bind(activity: Activity, selectedRoute: String = MainActivity.TAB_PROFILE) {
        this.selectedRoute = selectedRoute
        tabs.forEach { tab ->
            tabViews[tab.route]?.setOnClickListener {
                activity.startActivity(Intent(activity, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_TARGET_TAB, tab.route)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
            }
        }
        refreshColors()
    }

    private fun refreshColors() {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        setBackgroundColor(colors.bgColorTopBar)
        nav.background = rounded(colors.bgColorBottomBar, 32f)
        tabs.forEach { tab ->
            val selected = tab.route == selectedRoute
            tabViews[tab.route]?.background = if (selected) rounded(colors.bgColorInput, 28f) else null
            tabIcons[tab.route]?.let { icon ->
                icon.imageTintList = ColorStateList.valueOf(
                    if (selected) BRAND else colors.textColorTertiary
                )
            }
            tabCutouts[tab.route]?.imageTintList = ColorStateList.valueOf(
                if (selected) colors.bgColorInput else colors.bgColorBottomBar
            )
            tabLabels[tab.route]?.setTextColor(
                if (selected) BRAND else colors.textColorTertiary
            )
        }
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private companion object {
        const val BRAND = 0xFF23B39C.toInt()
    }
}
