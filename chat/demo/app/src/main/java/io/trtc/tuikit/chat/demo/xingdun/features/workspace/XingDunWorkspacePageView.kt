package io.trtc.tuikit.chat.demo.xingdun.features.workspace

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.uikit.pages.PageHeaderView
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunFeatureActivity

class XingDunWorkspacePageView(context: Context) : LinearLayout(context) {

    private val themeStore = ThemeStore.shared(context)

    init {
        orientation = VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        addView(PageHeaderView(context).apply {
            setTitle(context.getString(R.string.xingdun_workspace_title))
        })

        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(16.dp(), 20.dp(), 16.dp(), 20.dp())
            addView(TextView(context).apply {
                text = context.getString(R.string.xingdun_workspace_description)
                textSize = 15f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16.dp(), 18.dp(), 16.dp(), 18.dp())
                background = GradientDrawable().apply {
                    cornerRadius = 12.dp().toFloat()
                    setColor(themeStore.themeState.value.currentTheme.tokens.color.bgColorOperate)
                }
                setTextColor(themeStore.themeState.value.currentTheme.tokens.color.textColorPrimary)
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            val entries = listOf(
                R.string.xingdun_workspace_create to XingDunFeatureActivity.MODE_WORKSPACE_CREATE,
                R.string.xingdun_workspace_my to XingDunFeatureActivity.MODE_WORKSPACE_LIST,
                R.string.xingdun_workspace_pending to XingDunFeatureActivity.MODE_WORKSPACE_PENDING,
                R.string.xingdun_customer_service to XingDunFeatureActivity.MODE_CUSTOMER_SERVICE,
                R.string.xingdun_invite_title to XingDunFeatureActivity.MODE_INVITE,
                R.string.xingdun_feedback to XingDunFeatureActivity.MODE_FEEDBACK,
                R.string.xingdun_reports to XingDunFeatureActivity.MODE_REPORTS,
                R.string.xingdun_version to XingDunFeatureActivity.MODE_VERSION,
            )
            entries.forEach { (label, mode) ->
                addView(Button(context).apply {
                    setText(label)
                    isAllCaps = false
                    setOnClickListener { XingDunFeatureActivity.start(context, mode) }
                }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 10.dp()
                })
            }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        setBackgroundColor(themeStore.themeState.value.currentTheme.tokens.color.bgColorTopBar)
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
