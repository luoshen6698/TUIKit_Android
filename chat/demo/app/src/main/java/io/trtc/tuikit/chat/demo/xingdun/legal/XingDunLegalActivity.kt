package io.trtc.tuikit.chat.demo.xingdun.legal

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.trtc.tuikit.chat.app.R

/** Android reads the public Chinese website, including before sign-in. */
class XingDunLegalActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var source: TextView
    private lateinit var progress: ProgressBar
    private lateinit var errorPanel: LinearLayout
    private var privacy = true
    private var failed = false
    private val timeout = Runnable { showLoadError() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        privacy = savedInstanceState?.getBoolean(EXTRA_PRIVACY)
            ?: intent.getBooleanExtra(EXTRA_PRIVACY, true)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bar.addView(Button(this).apply {
            setText(R.string.xingdun_back)
            setOnClickListener { finish() }
        })
        source = TextView(this).apply {
            text = "星盾xd · Android · 简体中文"
            textSize = 14f
            setTextColor(Color.DKGRAY)
        }
        bar.addView(source, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(bar)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6))
        errorPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            visibility = View.GONE
            addView(TextView(this@XingDunLegalActivity).apply {
                setText(R.string.xingdun_legal_load_failed)
                textSize = 16f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
            })
            addView(Button(this@XingDunLegalActivity).apply {
                setText(R.string.xingdun_retry)
                setOnClickListener { loadRemote() }
            })
        }
        root.addView(errorPanel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        webView = WebView(this).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val nextPrivacy = XingDunLegalDocuments.documentType(uri.toString())
                if (nextPrivacy != null) {
                    if (nextPrivacy != privacy) {
                        privacy = nextPrivacy
                        loadRemote()
                        return true
                    }
                    return false
                }
                if (request.hasGesture() && (uri.scheme == "https" || uri.scheme == "mailto")) {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                } else if (request.isForMainFrame) {
                    showLoadError()
                }
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showLoadError()
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) showLoadError()
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                showLoadError()
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!failed && XingDunLegalDocuments.documentType(url) == privacy) {
                    webView.removeCallbacks(timeout)
                    progress.visibility = View.GONE
                }
            }
        }
        loadRemote()
    }

    private fun loadRemote() {
        webView.stopLoading()
        failed = false
        source.text = "www.xingdunim.com · Android · 简体中文"
        errorPanel.visibility = View.GONE
        webView.visibility = View.VISIBLE
        progress.visibility = View.VISIBLE
        webView.removeCallbacks(timeout)
        webView.postDelayed(timeout, 10_000)
        webView.loadUrl(XingDunLegalDocuments.url(privacy))
    }

    private fun showLoadError() {
        if (isFinishing || isDestroyed) return
        failed = true
        webView.removeCallbacks(timeout)
        webView.stopLoading()
        progress.visibility = View.GONE
        webView.visibility = View.GONE
        errorPanel.visibility = View.VISIBLE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(EXTRA_PRIVACY, privacy)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        webView.removeCallbacks(timeout)
        webView.stopLoading()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PRIVACY = "privacy"
        fun open(context: Context, privacy: Boolean) {
            context.startActivity(Intent(context, XingDunLegalActivity::class.java).apply {
                putExtra(EXTRA_PRIVACY, privacy)
            })
        }
    }
}
