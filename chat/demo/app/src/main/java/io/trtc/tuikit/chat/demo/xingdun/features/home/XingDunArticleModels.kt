package io.trtc.tuikit.chat.demo.xingdun.features.home

import io.trtc.tuikit.chat.app.BuildConfig
import java.net.URI

data class XingDunArticleCategory(val id: Int = 0, val categoryName: String = "")

data class XingDunArticle(
    val id: Int = 0,
    val categoryId: Int = 0,
    val categoryName: String = "",
    val title: String = "",
    val author: String = "",
    val image: String = "",
    val summary: String = "",
    val updateTime: String? = null,
    val content: String = "",
    val linkUrl: String = "",
)

data class XingDunArticlePage(
    val list: List<XingDunArticle> = emptyList(),
    val page: Int = 1,
    val total: Int = 0,
    val hasMore: Boolean = false,
)

data class XingDunArticleHome(
    val categories: List<XingDunArticleCategory> = emptyList(),
    val featured: List<XingDunArticle> = emptyList(),
    val headlines: List<XingDunArticle> = emptyList(),
    val articles: XingDunArticlePage = XingDunArticlePage(),
)

object XingDunArticleContent {
    fun safeUrl(value: String, base: String = BuildConfig.XINGDUN_API_BASE_URL): String? {
        if (value.isBlank() || value.any { it <= ' ' || it == '\\' } || value.startsWith("//")) return null
        return runCatching {
            val uri = if (value.startsWith('/')) URI(base).resolve(value) else URI(value)
            uri.takeIf { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null }?.toASCIIString()
        }.getOrNull()
    }

    fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    fun append(current: List<XingDunArticle>, next: List<XingDunArticle>): List<XingDunArticle> =
        (current + next).distinctBy { it.id }
}
