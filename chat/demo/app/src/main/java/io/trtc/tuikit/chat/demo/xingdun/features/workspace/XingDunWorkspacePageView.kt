package io.trtc.tuikit.chat.demo.xingdun.features.workspace

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.features.XingDunFeatureActivity
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.pages.PageHeaderView
import kotlinx.coroutines.launch

class XingDunWorkspacePageView(context: Context) : LinearLayout(context) {

    private data class WorkspaceData(
        val types: List<XingDunWorkspaceType>,
        val mine: JsonObject,
        val pending: JsonObject,
        val customerServiceIdentity: JsonObject?,
    )

    private val body = LinearLayout(context).apply { orientation = VERTICAL }
    private val status = TextView(context)
    private lateinit var scrollView: ScrollView
    private var touchStartY = 0f
    private var loading = false

    init {
        orientation = VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        addView(PageHeaderView(context).apply {
            setTitle(context.getString(R.string.xingdun_workspace_title))
            setEditContent(TextView(context).apply {
                text = "▤"
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(0xFF168F83.toInt())
                contentDescription = context.getString(R.string.xingdun_workspace_my)
                setPadding(12.dp(), 4.dp(), 4.dp(), 4.dp())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    XingDunFeatureActivity.start(context, XingDunFeatureActivity.MODE_WORKSPACE_LIST)
                }
            })
        })

        scrollView = ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(0xFFF5F5F9.toInt())
            body.setPadding(20.dp(), 8.dp(), 20.dp(), 32.dp())
            addView(body)
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> touchStartY = event.y
                    MotionEvent.ACTION_UP -> if (!loading && scrollY == 0 && event.y - touchStartY > 120.dp()) load()
                }
                false
            }
        }
        addView(scrollView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        status.setPadding(16.dp(), 8.dp(), 16.dp(), 8.dp())
        status.setTextColor(0xFF8A8A8F.toInt())
        status.setBackgroundColor(0xFFF5F5F9.toInt())
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        setBackgroundColor(0xFFF5F5F9.toInt())
        load()
    }

    private fun load() {
        if (loading) return
        val owner = context as? LifecycleOwner ?: return showFallback()
        loading = true
        status.setText(R.string.xingdun_loading)
        owner.lifecycleScope.launch {
            runCatching {
                val session = XingDunSessionManager.currentSession() ?: error(context.getString(R.string.xingdun_session_expired))
                val api = XingDunSessionManager.apiClient()
                val types = XingDunWorkspaceContracts.parseTypes(
                    api.get<JsonArray>(session, "workspace/types", emptyMap(), JsonArray::class.java)
                )
                val mine = api.get<JsonObject>(session, "workspace/mine", mapOf("page" to "1", "page_size" to "5"), JsonObject::class.java)
                val pending = api.get<JsonObject>(session, "workspace/pending", mapOf("page" to "1", "page_size" to "5"), JsonObject::class.java)
                val identity = if (session.features.customerService) runCatching {
                    api.get<JsonObject>(session, "cs/identity", emptyMap(), JsonObject::class.java)
                }.getOrNull() else null
                WorkspaceData(types, mine, pending, identity)
            }.onSuccess { data ->
                loading = false
                status.text = ""
                render(data)
            }.onFailure {
                loading = false
                status.setText(R.string.xingdun_workspace_load_failed)
                showFallback()
            }
        }
    }

    private fun render(data: WorkspaceData) {
        body.removeAllViews()
        addGuide(data.types)
        addRecent(data.mine)
        addApplicationCategories(data.types)
        addSectionHeader(R.string.xingdun_workspace_management)
        addNavigationRow(
            context.getString(R.string.xingdun_workspace_pending),
            data.pending.int("total"),
            XingDunFeatureActivity.MODE_WORKSPACE_PENDING,
        )
        if (data.customerServiceIdentity?.boolean("is_cs") == true) {
            addSectionHeader(R.string.xingdun_workspace_customer_service)
            addNavigationRow(
                context.getString(R.string.xingdun_customer_service_dashboard),
                null,
                XingDunFeatureActivity.MODE_CUSTOMER_SERVICE,
            )
        }
    }

    private fun addGuide(types: List<XingDunWorkspaceType>) {
        body.addView(LinearLayout(context).apply {
            orientation = VERTICAL
            background = roundedDrawable(0xFFE1F3EE.toInt(), 16f)
            setPadding(16.dp(), 16.dp(), 16.dp(), 14.dp())
            addView(TextView(context).apply {
                setText(R.string.xingdun_workspace_guide_title)
                textSize = 17f
                setTextColor(0xFF1C1C1E.toInt())
            })
            addView(TextView(context).apply {
                setText(R.string.xingdun_workspace_guide_message)
                textSize = 13f
                setTextColor(0xFF6D6D72.toInt())
                setPadding(0, 6.dp(), 0, 12.dp())
            })
            val quickRow = LinearLayout(context).apply { orientation = HORIZONTAL }
            val quick = listOf("leave", "travel", "reimburse")
            quick.mapNotNull { key -> types.firstOrNull { it.type == key } }.forEachIndexed { index, type ->
                quickRow.addView(quickFlowCard(type), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = 8.dp()
                })
            }
            if (quickRow.childCount > 0) addView(quickRow)
            addView(TextView(context).apply {
                setText(R.string.xingdun_workspace_my)
                textSize = 15f
                setTextColor(0xFF168F83.toInt())
                setPadding(4.dp(), 14.dp(), 4.dp(), 4.dp())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    XingDunFeatureActivity.start(context, XingDunFeatureActivity.MODE_WORKSPACE_LIST)
                }
            })
        }, sectionLayoutParams())
    }

    private fun quickFlowCard(type: XingDunWorkspaceType): View = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        background = roundedDrawable(Color.WHITE, 12f)
        minimumHeight = 94.dp()
        setPadding(6.dp(), 12.dp(), 6.dp(), 10.dp())
        addView(TextView(context).apply {
            text = when (type.type) {
                "leave" -> "◷"
                "travel" -> "✈"
                "reimburse" -> "¥"
                else -> "□"
            }
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(if (type.available) 0xFF168F83.toInt() else 0xFF8A8A8F.toInt())
            background = roundedDrawable(0xFFE1F3EE.toInt(), 10f)
        }, LayoutParams(42.dp(), 42.dp()).apply { bottomMargin = 4.dp() })
        addView(TextView(context).apply {
            text = typeDisplayName(type)
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF1C1C1E.toInt())
            maxLines = 2
        })
        addView(TextView(context).apply {
            text = type.unavailableReason?.takeIf { !type.available && it.isNotBlank() }
                ?: categoryShortLabel(type.category)
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(if (type.available) 0xFF8A8A8F.toInt() else 0xFFD93025.toInt())
            maxLines = 1
            setPadding(2.dp(), 3.dp(), 2.dp(), 0)
        })
        isEnabled = type.available
        alpha = if (type.available) 1f else 0.58f
        isClickable = type.available
        isFocusable = type.available
        setOnClickListener {
            XingDunFeatureActivity.start(context, XingDunFeatureActivity.MODE_WORKSPACE_CREATE, type.type)
        }
    }

    private fun addRecent(mine: JsonObject) {
        addSectionHeader(R.string.xingdun_workspace_recent, R.string.xingdun_view_all) {
            XingDunFeatureActivity.start(context, XingDunFeatureActivity.MODE_WORKSPACE_LIST)
        }
        val recent = mine.array("list").firstOrNull { it.isJsonObject }?.asJsonObject
        if (recent == null) {
            addInformationRow(context.getString(R.string.xingdun_workspace_no_records))
            return
        }
        val id = recent.int("id")
        val statusText = if (recent.int("status") == 3) {
            context.getString(R.string.xingdun_workspace_status_submitted)
        } else {
            recent.string("status_text")
        }
        val detail = listOfNotNull(recent.string("type_name"), statusText).joinToString(" · ")
        addInformationRow(recent.string("title") ?: context.getString(R.string.xingdun_workspace_untitled), detail) {
            if (id > 0) XingDunFeatureActivity.start(context, XingDunFeatureActivity.MODE_WORKSPACE_DETAIL, id)
        }
    }

    private fun addApplicationCategories(types: List<XingDunWorkspaceType>) {
        listOf(
            Triple("attendance", R.string.xingdun_workspace_category_attendance, "◷"),
            Triple("finance", R.string.xingdun_workspace_category_finance, "¥"),
            Triple("hr", R.string.xingdun_workspace_category_hr, "♟")
        ).forEach { (category, title, icon) ->
            val values = types.filter { it.category == category }
            if (values.isNotEmpty()) {
                body.addView(LinearLayout(context).apply {
                    orientation = VERTICAL
                    background = roundedDrawable(Color.WHITE, 14f)
                    setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
                    addView(LinearLayout(context).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(TextView(context).apply {
                            text = icon
                            textSize = 24f
                            gravity = Gravity.CENTER
                            setTextColor(categoryColor(category))
                        }, LayoutParams(34.dp(), 34.dp()).apply { marginEnd = 8.dp() })
                        addView(TextView(context).apply {
                            setText(title)
                            textSize = 15f
                            setTextColor(0xFF1C1C1E.toInt())
                        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                        addView(TextView(context).apply {
                            text = context.getString(R.string.xingdun_workspace_flow_count, values.size)
                            textSize = 11f
                            gravity = Gravity.CENTER
                            setTextColor(0xFF168F83.toInt())
                            background = roundedDrawable(0xFFE1F3EE.toInt(), 12f)
                            setPadding(9.dp(), 4.dp(), 9.dp(), 4.dp())
                        })
                    })
                    addView(TextView(context).apply {
                        text = values.joinToString(" · ") { typeDisplayName(it) }
                        textSize = 11f
                        setTextColor(0xFF8A8A8F.toInt())
                        maxLines = 2
                        setPadding(2.dp(), 5.dp(), 2.dp(), 8.dp())
                    })
                    values.chunked(4).forEach { rowTypes ->
                        addView(LinearLayout(context).apply {
                            orientation = HORIZONTAL
                            setPadding(0, 2.dp(), 0, 2.dp())
                            rowTypes.forEach { type ->
                                addView(typeGridItem(type), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                            }
                            repeat(4 - rowTypes.size) { addView(View(context), LayoutParams(0, 1, 1f)) }
                        })
                    }
                }, sectionLayoutParams())
            }
        }
    }

    private fun showFallback() {
        body.removeAllViews()
        addGuide(emptyList())
        addSectionHeader(R.string.xingdun_workspace_recent, R.string.xingdun_view_all) {
            XingDunFeatureActivity.start(context, XingDunFeatureActivity.MODE_WORKSPACE_LIST)
        }
        addInformationRow(context.getString(R.string.xingdun_workspace_no_records))
        addSectionHeader(R.string.xingdun_workspace_management)
        addNavigationRow(context.getString(R.string.xingdun_workspace_pending), null, XingDunFeatureActivity.MODE_WORKSPACE_PENDING)
        body.addView(Button(context).apply {
            setText(R.string.xingdun_retry)
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF20A88F.toInt())
            setOnClickListener { load() }
        }, sectionLayoutParams())
    }

    private fun addSectionHeader(title: Int, trailing: Int? = null, action: (() -> Unit)? = null) =
        addSectionHeader(context.getString(title), trailing?.let(context::getString), action)

    private fun addSectionHeader(title: String, trailing: String? = null, action: (() -> Unit)? = null) {
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4.dp(), 16.dp(), 4.dp(), 7.dp())
        }
        header.addView(TextView(context).apply {
            text = title
            textSize = 14f
            setTextColor(0xFF8A8A8F.toInt())
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        if (trailing != null) header.addView(TextView(context).apply {
            text = trailing
            textSize = 13f
            setTextColor(0xFF168F83.toInt())
            if (action != null) {
                isClickable = true
                setOnClickListener { action() }
            }
        })
        body.addView(header)
    }

    private fun typeGridItem(type: XingDunWorkspaceType): View = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(4.dp(), 8.dp(), 4.dp(), 8.dp())
        addView(TextView(context).apply {
            text = when (type.category) { "finance" -> "¥"; "hr" -> "♟"; else -> "◷" }
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(if (type.available) 0xFF20A88F.toInt() else 0xFF8A8A8F.toInt())
            background = roundedDrawable(0xFFE1F3EE.toInt(), 10f)
        }, LayoutParams(40.dp(), 40.dp()).apply { bottomMargin = 4.dp() })
        addView(TextView(context).apply {
            text = typeDisplayName(type)
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF1C1C1E.toInt())
            maxLines = 2
        })
        isEnabled = type.available
        alpha = if (type.available) 1f else 0.58f
        isClickable = type.available
        setOnClickListener {
            XingDunFeatureActivity.start(context, XingDunFeatureActivity.MODE_WORKSPACE_CREATE, type.type)
        }
    }

    private fun addNavigationRow(label: String, count: Int?, mode: String) {
        body.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(16.dp(), 2.dp(), 10.dp(), 2.dp())
            addView(TextView(context).apply {
                text = label
                textSize = 15f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(0xFF1C1C1E.toInt())
            }, LayoutParams(0, 52.dp(), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
            if (count != null && count > 0) addView(TextView(context).apply {
                text = if (count > 99) "99+" else count.toString()
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = roundedDrawable(0xFFE64A4A.toInt(), 12f)
                setPadding(8.dp(), 2.dp(), 8.dp(), 2.dp())
            })
            addView(TextView(context).apply {
                text = "›"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(0xFF8A8A8F.toInt())
            }, LayoutParams(28.dp(), 52.dp()))
            isClickable = true
            isFocusable = true
            setOnClickListener { XingDunFeatureActivity.start(context, mode) }
        }, sectionLayoutParams())
    }

    private fun addInformationRow(title: String, detail: String = "", action: (() -> Unit)? = null) {
        body.addView(TextView(context).apply {
            text = if (detail.isBlank()) title else "$title\n$detail"
            textSize = 15f
            setTextColor(0xFF1C1C1E.toInt())
            background = roundedDrawable(Color.WHITE, 14f)
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            if (action != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }
        }, sectionLayoutParams())
    }

    private fun sectionLayoutParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = 8.dp()
    }

    private fun typeDisplayName(type: XingDunWorkspaceType): String {
        val resource = when (type.type) {
            "leave" -> R.string.xingdun_workspace_leave
            "travel" -> R.string.xingdun_workspace_travel
            "out" -> R.string.xingdun_workspace_out
            "overtime" -> R.string.xingdun_workspace_overtime
            "reimburse" -> R.string.xingdun_workspace_reimburse
            "purchase" -> R.string.xingdun_workspace_purchase
            "hr_need" -> R.string.xingdun_workspace_hr_need
            "confirmation" -> R.string.xingdun_workspace_confirmation
            "resign" -> R.string.xingdun_workspace_resign
            else -> return type.name
        }
        return context.getString(resource)
    }

    private fun categoryShortLabel(category: String): String = context.getString(when (category) {
        "finance" -> R.string.xingdun_workspace_filter_finance
        "hr" -> R.string.xingdun_workspace_filter_hr
        else -> R.string.xingdun_workspace_filter_attendance
    })

    private fun categoryColor(category: String): Int = when (category) {
        "finance" -> 0xFF20A88F.toInt()
        "hr" -> 0xFFE6A117.toInt()
        else -> 0xFF3478F6.toInt()
    }

    private fun roundedDrawable(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.dp()
    }

    private fun com.google.gson.JsonObject.int(name: String): Int =
        get(name)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asInt }.getOrDefault(0) } ?: 0

    private fun com.google.gson.JsonObject.string(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonObject.boolean(name: String): Boolean =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.let { runCatching { it.asBoolean }.getOrDefault(false) } ?: false

    private fun JsonObject.array(name: String): JsonArray =
        get(name)?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: JsonArray()

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun Float.dp(): Float = this * resources.displayMetrics.density
}
