package io.trtc.tuikit.chat.demo.xingdun.features.home

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.app.BuildConfig
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiException
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.util.Locale

class XingDunArticleActivity : BaseActivity() {
    private lateinit var web: WebView
    private lateinit var status: TextView
    private var loading = false
    private val colors get() = ThemeStore.shared(this).themeState.value.currentTheme.tokens.color

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(colors.bgColorDefault) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "‹"; textSize = 34f; gravity = Gravity.CENTER; setTextColor(colors.textColorPrimary)
            contentDescription = getString(R.string.xingdun_home_back)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(56.dp(), 52.dp()))
        header.addView(TextView(this).apply {
            setText(R.string.xingdun_home_detail); textSize = 17f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(colors.textColorPrimary)
        })
        root.addView(header)
        status = TextView(this).apply {
            gravity = Gravity.CENTER; textSize = 15f; setTextColor(colors.textColorSecondary)
            setPadding(24.dp(), 36.dp(), 24.dp(), 36.dp())
            setOnClickListener { if (!loading) loadArticle() }
        }
        root.addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        web = WebView(this).apply {
            setBackgroundColor(colors.bgColorDefault)
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = XingDunArticleContent.safeUrl(request.url.toString()) ?: return true
                    if (request.isForMainFrame && request.hasGesture()) openLink(url)
                    return true
                }
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (request.url.scheme == "https") return null
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
            }
        }
        root.addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        loadArticle()
    }

    private fun loadArticle() {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        if (id <= 0) { status.setText(R.string.xingdun_home_unavailable); return }
        loading = true
        status.visibility = View.VISIBLE
        status.setText(R.string.xingdun_home_loading)
        web.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val article: XingDunArticle = XingDunSessionManager.apiClient().publicGet(
                    "articles/read", mapOf("id" to id.toString()), XingDunArticle::class.java,
                )
                web.loadDataWithBaseURL(BuildConfig.XINGDUN_API_BASE_URL.trimEnd('/') + "/", document(article), "text/html", "UTF-8", null)
                web.visibility = View.VISIBLE
                status.visibility = View.GONE
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                status.setText(if (error is XingDunApiException && error.httpStatus == 404)
                    R.string.xingdun_home_unavailable else R.string.xingdun_home_retry)
            } finally { loading = false }
        }
    }

    private fun document(article: XingDunArticle): String {
        val escape = XingDunArticleContent::escape
        val metadata = listOf(article.categoryName, article.author).filter(String::isNotBlank).joinToString(" · ")
        val time = article.updateTime?.takeIf(String::isNotBlank)?.let { getString(R.string.xingdun_home_updated, it) }.orEmpty()
        val cover = XingDunArticleContent.safeUrl(article.image)?.let { "<img class=\"cover\" src=\"${escape(it)}\" alt=\"\">" }.orEmpty()
        val original = XingDunArticleContent.safeUrl(article.linkUrl)?.let {
            "<p><a class=\"original\" href=\"${escape(it)}\">${escape(getString(R.string.xingdun_home_original))} ↗</a></p>"
        }.orEmpty()
        return """<!doctype html><html lang="${escape(resources.configuration.locales[0].language)}"><head>
            <meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'">
            <style>body{margin:0;padding:20px 20px 40px;background:${hex(colors.bgColorDefault)};color:${hex(colors.textColorPrimary)};font-family:sans-serif;line-height:1.85;word-wrap:break-word}h1{font-size:26px;line-height:1.45;margin:0 0 14px}.meta{font-size:12px;color:${hex(colors.textColorSecondary)};margin:0 0 6px}.summary{padding:14px 16px;background:${hex(colors.bgColorInput)};border-radius:10px;font-size:14px;margin:20px 0}article{font-size:17px}img{max-width:100%;height:auto;border-radius:8px}.cover{width:100%;margin:16px 0}a{color:#168f83}table{display:block;max-width:100%;overflow-x:auto}blockquote{margin:12px 0;padding-left:14px;border-left:3px solid #168f83}pre{white-space:pre-wrap}p{margin:0 0 16px}.original{display:inline-block;padding:10px 0}</style>
            </head><body><h1>${escape(article.title)}</h1><p class="meta">${escape(metadata)}</p><p class="meta">${escape(time)}</p>
            $cover${if (article.summary.isBlank()) "" else "<div class=\"summary\">${escape(article.summary)}</div>"}
            <article>${article.content}</article>$original</body></html>""".trimIndent()
    }

    private fun openLink(url: String) {
        runCatching { CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url)) }
            .onFailure { Toast.makeText(this, R.string.xingdun_home_link_failed, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        if (::web.isInitialized) { web.stopLoading(); web.destroy() }
        super.onDestroy()
    }
    private fun hex(color: Int) = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)
    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()
    companion object {
        private const val EXTRA_ID = "article_id"
        fun start(context: Context, id: Int) { context.startActivity(Intent(context, XingDunArticleActivity::class.java).putExtra(EXTRA_ID, id)) }
    }
}
