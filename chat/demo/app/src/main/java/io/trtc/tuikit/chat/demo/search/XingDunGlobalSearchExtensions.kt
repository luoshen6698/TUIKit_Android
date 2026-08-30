package io.trtc.tuikit.chat.demo.search

import android.content.Context
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import io.trtc.tuikit.chat.uikit.components.search.ui.GlobalSearchExtensionResult
import io.trtc.tuikit.chat.uikit.components.search.ui.GlobalSearchExtensionSection

internal object XingDunGlobalSearchExtensions {
    private const val FAVORITE_LIMIT = 50

    suspend fun search(
        context: Context,
        query: String,
    ): List<GlobalSearchExtensionSection> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return emptyList()

        val favoriteResults = runCatching { favoriteResults(context, keyword) }.getOrDefault(emptyList())
        val workspaceResults = workspaceResults(context, keyword)
        return buildList {
            if (favoriteResults.isNotEmpty()) add(
                GlobalSearchExtensionSection(
                    id = "favorites",
                    title = context.getString(R.string.xingdun_search_category_favorites),
                    results = favoriteResults,
                )
            )
            if (workspaceResults.isNotEmpty()) add(
                GlobalSearchExtensionSection(
                    id = "workspace",
                    title = context.getString(R.string.xingdun_search_category_workspace),
                    results = workspaceResults,
                )
            )
        }
    }

    private suspend fun favoriteResults(
        context: Context,
        keyword: String,
    ): List<GlobalSearchExtensionResult> {
        val session = XingDunSessionManager.currentSession() ?: return emptyList()
        val page = XingDunSessionManager.apiClient().get<JsonObject>(
            session,
            "message/favorites",
            mapOf("page" to "1", "page_size" to FAVORITE_LIMIT.toString()),
            JsonObject::class.java,
        )
        return page.items().mapNotNull { favorite ->
            val snapshot = favorite.getAsJsonObject("message") ?: favorite
            val title = snapshot.string("conversation_name")
                ?: favorite.string("conversation_name")
                ?: snapshot.string("sender_nickname")
                ?: context.getString(R.string.xingdun_message_favorites)
            val summary = snapshot.string("text")
                ?: favoriteSummary(context, snapshot.string("message_type"))
            if (!title.matches(keyword) && !summary.matches(keyword)) return@mapNotNull null

            val messageID = snapshot.string("message_id").orEmpty()
            val favoriteID = favorite.string("favorite_id") ?: favorite.string("id") ?: messageID
            GlobalSearchExtensionResult(
                id = "favorite:$favoriteID",
                title = title,
                subtitle = summary,
                metadata = mapOf(
                    "kind" to "favorite",
                    "conversation_id" to (
                        snapshot.string("conversation_id")
                            ?: favorite.string("conversation_id")
                            ?: ""
                    ),
                    "message_id" to messageID,
                ),
            )
        }.take(20)
    }

    private fun workspaceResults(
        context: Context,
        keyword: String,
    ): List<GlobalSearchExtensionResult> = listOf(
        workspaceResult(
            id = "workspace",
            title = context.getString(R.string.xingdun_workspace_title),
            subtitle = context.getString(R.string.xingdun_search_workspace_entry),
            keywords = listOf("workspace", "应用", "办公"),
            metadata = mapOf("kind" to "workspace"),
        ),
        workspaceResult(
            id = "leave",
            title = context.getString(R.string.xingdun_workspace_leave),
            subtitle = context.getString(R.string.xingdun_search_workspace_entry),
            keywords = listOf("leave", "休假", "审批"),
            metadata = mapOf("kind" to "workspace_create", "type" to "leave"),
        ),
        workspaceResult(
            id = "reimburse",
            title = context.getString(R.string.xingdun_workspace_reimburse),
            subtitle = context.getString(R.string.xingdun_search_workspace_entry),
            keywords = listOf("reimburse", "费用", "审批"),
            metadata = mapOf("kind" to "workspace_create", "type" to "reimburse"),
        ),
        workspaceResult(
            id = "pending",
            title = context.getString(R.string.xingdun_workspace_pending),
            subtitle = context.getString(R.string.xingdun_search_workspace_entry),
            keywords = listOf("pending", "审批", "审核", "申请"),
            metadata = mapOf("kind" to "workspace_pending"),
        ),
    ).filter { result ->
        result.title.matches(keyword) || result.subtitle.matches(keyword) ||
            result.metadata["keywords"].orEmpty().matches(keyword)
    }.map { result -> result.copy(metadata = result.metadata - "keywords") }

    private fun workspaceResult(
        id: String,
        title: String,
        subtitle: String,
        keywords: List<String>,
        metadata: Map<String, String>,
    ) = GlobalSearchExtensionResult(
        id = "workspace:$id",
        title = title,
        subtitle = subtitle,
        metadata = metadata + ("keywords" to keywords.joinToString(" ")),
    )

    private fun favoriteSummary(context: Context, type: String?): String = when (type?.uppercase()) {
        "PICTURE", "IMAGE" -> context.getString(R.string.xingdun_favorite_picture)
        "AUDIO", "SOUND" -> context.getString(R.string.xingdun_favorite_audio)
        "VIDEO" -> context.getString(R.string.xingdun_favorite_video)
        "FILE" -> context.getString(R.string.xingdun_favorite_file)
        else -> context.getString(R.string.xingdun_favorite_message)
    }

    private fun JsonObject.items(): List<JsonObject> = sequenceOf("items", "list")
        .mapNotNull { key -> get(key)?.takeIf(JsonElement::isJsonArray)?.asJsonArray }
        .firstOrNull()
        ?.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
        ?: emptyList()

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeUnless(JsonElement::isJsonNull)?.asString?.trim()?.takeIf(String::isNotEmpty)

    private fun String.matches(keyword: String): Boolean = contains(keyword, ignoreCase = true)
}
