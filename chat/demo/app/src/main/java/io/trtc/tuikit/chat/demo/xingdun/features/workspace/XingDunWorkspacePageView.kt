package io.trtc.tuikit.chat.demo.xingdun.features.workspace

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunFeatureActivity
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.pages.PageHeaderView
import kotlinx.coroutines.launch

class XingDunWorkspacePageView(context: Context) : LinearLayout(context) {

    private val themeStore = ThemeStore.shared(context)
    private val body = LinearLayout(context).apply { orientation = VERTICAL }
    private val status = TextView(context)

    init {
        orientation = VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        addView(PageHeaderView(context).apply {
            setTitle(context.getString(R.string.xingdun_workspace_title))
        })

        addView(ScrollView(context).apply {
            isFillViewport = true
            body.setPadding(16.dp(), 14.dp(), 16.dp(), 28.dp())
            addView(body)
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        status.setPadding(16.dp(), 8.dp(), 16.dp(), 8.dp())
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        setBackgroundColor(themeStore.themeState.value.currentTheme.tokens.color.bgColorTopBar)
        load()
    }

    private fun load() {
        val owner = context as? LifecycleOwner ?: return showFallback()
        status.setText(R.string.xingdun_loading)
        owner.lifecycleScope.launch {
            runCatching {
                val session = XingDunSessionManager.currentSession() ?: error(context.getString(R.string.xingdun_session_expired))
                val api = XingDunSessionManager.apiClient()
                val types = XingDunWorkspaceContracts.parseTypes(
                    api.get<JsonArray>(session, "workspace/types", emptyMap(), JsonArray::class.java)
                )
                val mine = api.get<JsonObject>(session, "workspace/mine", mapOf("page" to "1", "page_size" to "1"), JsonObject::class.java)
                val pending = api.get<JsonObject>(session, "workspace/pending", mapOf("page" to "1", "page_size" to "1"), JsonObject::class.java)
                Triple(types, mine, pending)
            }.onSuccess { (types, mine, pending) ->
                status.text = ""
                render(types, mine, pending)
            }.onFailure {
                status.text = it.localizedMessage ?: context.getString(R.string.xingdun_action_failed)
                showFallback()
            }
        }
    }

    private fun render(
        types: List<XingDunWorkspaceType>,
        mine: com.google.gson.JsonObject,
        pending: com.google.gson.JsonObject
    ) {
        body.removeAllViews()
        addBanner()
        addSection(R.string.xingdun_workspace_quick)
        val quick = listOf("leave", "travel", "reimburse")
        quick.mapNotNull { key -> types.firstOrNull { it.type == key } }.forEach(::addTypeButton)

        listOf(
            "attendance" to R.string.xingdun_workspace_category_attendance,
            "finance" to R.string.xingdun_workspace_category_finance,
            "hr" to R.string.xingdun_workspace_category_hr
        ).forEach { (category, title) ->
            val values = types.filter { it.category == category }
            if (values.isNotEmpty()) {
                addSection(title)
                values.forEach(::addTypeButton)
            }
        }

        addSection(R.string.xingdun_workspace_progress)
        addNavigation(
            context.getString(R.string.xingdun_workspace_pending_with_count, pending.int("total")),
            XingDunFeatureActivity.MODE_WORKSPACE_PENDING
        )
        addNavigation(
            context.getString(R.string.xingdun_workspace_my_with_count, mine.int("total")),
            XingDunFeatureActivity.MODE_WORKSPACE_LIST
        )
        mine.getAsJsonArray("list")?.firstOrNull()?.asJsonObject?.let { recent ->
            addSection(R.string.xingdun_workspace_recent)
            addNavigation(
                recent.string("title") ?: context.getString(R.string.xingdun_workspace_untitled),
                XingDunFeatureActivity.MODE_WORKSPACE_DETAIL,
                recent.get("id")?.asInt ?: 0
            )
        }
        if (XingDunSessionManager.currentSession()?.features?.customerService == true) {
            addSection(R.string.xingdun_workspace_enterprise_service)
            addNavigation(context.getString(R.string.xingdun_customer_service), XingDunFeatureActivity.MODE_CUSTOMER_SERVICE)
        }
    }

    private fun showFallback() {
        body.removeAllViews()
        addBanner()
        addNavigation(context.getString(R.string.xingdun_workspace_create), XingDunFeatureActivity.MODE_WORKSPACE_CREATE)
        addNavigation(context.getString(R.string.xingdun_workspace_my), XingDunFeatureActivity.MODE_WORKSPACE_LIST)
        addNavigation(context.getString(R.string.xingdun_workspace_pending), XingDunFeatureActivity.MODE_WORKSPACE_PENDING)
        body.addView(Button(context).apply {
            setText(R.string.xingdun_retry)
            isAllCaps = false
            setOnClickListener { load() }
        })
    }

    private fun addBanner() {
        body.addView(TextView(context).apply {
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
    }

    private fun addSection(title: Int) {
        body.addView(TextView(context).apply {
            setText(title)
            textSize = 13f
            setPadding(4.dp(), 18.dp(), 4.dp(), 6.dp())
        })
    }

    private fun addTypeButton(type: XingDunWorkspaceType) {
        body.addView(Button(context).apply {
            text = buildString {
                append(type.name)
                type.approverName?.let { append(" · ").append(it) }
                if (!type.available && !type.unavailableReason.isNullOrBlank()) append("\n").append(type.unavailableReason)
            }
            isAllCaps = false
            isEnabled = type.available
            setOnClickListener { XingDunFeatureActivity.start(context, XingDunFeatureActivity.MODE_WORKSPACE_CREATE, type.type) }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = 6.dp() })
    }

    private fun addNavigation(label: String, mode: String, itemId: Int = 0) {
        body.addView(Button(context).apply {
            text = label
            isAllCaps = false
            setOnClickListener { XingDunFeatureActivity.start(context, mode, itemId) }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = 6.dp() })
    }

    private fun com.google.gson.JsonObject.int(name: String): Int =
        get(name)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asInt }.getOrDefault(0) } ?: 0

    private fun com.google.gson.JsonObject.string(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf(String::isNotEmpty)

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
