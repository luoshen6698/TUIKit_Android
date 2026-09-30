package io.trtc.tuikit.chat.demo.xingdun.features.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class XingDunHomePageView(context: Context) : LinearLayout(context) {
    private val colors get() = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    private val refresh = SwipeRefreshLayout(context)
    private val list = RecyclerView(context)
    private val adapter = FeedAdapter()
    private var home = XingDunArticleHome()
    private var articles = emptyList<XingDunArticle>()
    private var categoryId = 0
    private var page = 1
    private var hasMore = false
    private var loading = false
    private var failed = false
    private var failedRefresh = false
    private lateinit var titleText: TextView
    private var job: Job? = null
    private var requestId = 0
    private var active = false
    private var heroIndex = 0
    private var headlineIndex = 0
    private var heroPager: ViewPager2? = null
    private var tickerText: TextView? = null
    private val rotate = object : Runnable {
        override fun run() {
            if (!active || !isAttachedToWindow) return
            if (isShown && windowVisibility == VISIBLE) {
                if (home.featured.size > 1) heroPager?.setCurrentItem((heroIndex + 1) % home.featured.size, true)
                if (home.headlines.isNotEmpty()) {
                    headlineIndex = (headlineIndex + 1) % home.headlines.size
                    tickerText?.text = home.headlines[headlineIndex].title
                }
            }
            postDelayed(this, 6000)
        }
    }

    init {
        orientation = VERTICAL
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dp(), 8.dp(), 20.dp(), 8.dp())
            titleText = label(context.getString(R.string.xingdun_home_title), 27f, true)
            addView(titleText, LayoutParams(0, 44.dp(), 1f))
            addView(label(context.getString(R.string.xingdun_home_local_life), 13f).apply { setTextColor(BRAND) })
        }, LayoutParams(LayoutParams.MATCH_PARENT, 60.dp()))
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.itemAnimator = null
        list.clipToPadding = false
        list.setPadding(16.dp(), 0, 16.dp(), 12.dp())
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val last = (list.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                if (dy > 0 && last >= adapter.itemCount - 3 && hasMore && !loading && !failed) loadMore()
            }
        })
        refresh.setColorSchemeColors(BRAND)
        refresh.addView(list)
        refresh.setOnRefreshListener { loadHome() }
        addView(refresh, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        updateColors()
        loadHome()
    }

    fun updateColors() {
        setBackgroundColor(colors.bgColorDefault)
        titleText.setTextColor(colors.textColorPrimary)
        adapter.notifyDataSetChanged()
    }

    fun setActive(value: Boolean) {
        active = value
        removeCallbacks(rotate)
        if (active && isAttachedToWindow) postDelayed(rotate, 6000)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); setActive(active) }
    override fun onDetachedFromWindow() { removeCallbacks(rotate); super.onDetachedFromWindow() }

    private fun loadHome() {
        val owner = context as? LifecycleOwner ?: return
        job?.cancel()
        val current = ++requestId
        loading = true
        failed = false
        adapter.notifyDataSetChanged()
        job = owner.lifecycleScope.launch {
            try {
                val api = XingDunSessionManager.apiClient()
                val result: XingDunArticleHome = api.publicGet("articles/home", emptyMap(), XingDunArticleHome::class.java)
                val selected = categoryId.takeIf { id -> result.categories.any { it.id == id } } ?: 0
                val resultPage = if (selected == 0) result.articles else fetchPage(selected, 1)
                if (current != requestId) return@launch
                home = result
                categoryId = selected
                articles = resultPage.list
                page = resultPage.page
                hasMore = resultPage.hasMore
                heroIndex = 0
                headlineIndex = 0
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (current == requestId) { failed = true; failedRefresh = true } }
            finally {
                if (current == requestId) {
                    loading = false
                    refresh.isRefreshing = false
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun selectCategory(id: Int) {
        if (id == categoryId) return
        categoryId = id
        articles = emptyList()
        page = 0
        hasMore = true
        list.scrollToPosition(0)
        loadMore(replace = true)
    }

    private fun loadMore(replace: Boolean = false) {
        if (loading && !replace) return
        val owner = context as? LifecycleOwner ?: return
        job?.cancel()
        val current = ++requestId
        loading = true
        failed = false
        refresh.isRefreshing = false
        adapter.notifyDataSetChanged()
        job = owner.lifecycleScope.launch {
            try {
                val result = fetchPage(categoryId, if (replace) 1 else page + 1)
                if (current != requestId) return@launch
                articles = if (replace) result.list else XingDunArticleContent.append(articles, result.list)
                page = result.page
                hasMore = result.hasMore
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (current == requestId) { failed = true; failedRefresh = false } }
            finally { if (current == requestId) { loading = false; adapter.notifyDataSetChanged() } }
        }
    }

    private suspend fun fetchPage(category: Int, targetPage: Int): XingDunArticlePage =
        XingDunSessionManager.apiClient().publicGet("articles/index", mapOf(
            "category_id" to category.toString(), "page" to targetPage.toString(), "page_size" to "10",
        ), XingDunArticlePage::class.java)

    private fun open(article: XingDunArticle) { XingDunArticleActivity.start(context, article.id) }

    private inner class FeedAdapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = articles.size + 2
        override fun getItemViewType(position: Int) = when (position) { 0 -> 0; itemCount - 1 -> 2; else -> 1 }
        override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(FrameLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        })
        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.container.removeAllViews()
            holder.container.addView(when (getItemViewType(position)) {
                0 -> header()
                1 -> articleRow(articles[position - 1])
                else -> footer()
            })
        }
    }

    private class Holder(val container: FrameLayout) : RecyclerView.ViewHolder(container)

    private fun header(): View = LinearLayout(context).apply {
        orientation = VERTICAL
        heroPager = null
        tickerText = null
        if (home.featured.isNotEmpty()) {
            val indicator = label("", 11f).apply {
                setTextColor(Color.WHITE)
                background = rounded(0x66000000, 12f)
                setPadding(9.dp(), 3.dp(), 9.dp(), 3.dp())
            }
            val pager = ViewPager2(context).apply {
                adapter = HeroAdapter(home.featured)
                setCurrentItem(heroIndex.coerceIn(home.featured.indices), false)
                registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        heroIndex = position
                        indicator.text = context.getString(R.string.xingdun_home_page_count, position + 1, home.featured.size)
                    }
                })
            }
            heroPager = pager
            indicator.text = context.getString(R.string.xingdun_home_page_count, heroIndex + 1, home.featured.size)
            addView(FrameLayout(context).apply {
                background = rounded(colors.bgColorInput, 16f)
                clipToOutline = true
                addView(pager, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                addView(indicator, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                    topMargin = 12.dp(); marginEnd = 12.dp()
                })
            }, LayoutParams(LayoutParams.MATCH_PARENT, 200.dp()).apply { topMargin = 4.dp(); bottomMargin = 12.dp() })
        }
        if (home.headlines.isNotEmpty()) {
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(colors.bgColorInput, 12f)
                setPadding(12.dp(), 0, 12.dp(), 0)
                addView(label(context.getString(R.string.xingdun_home_headlines), 13f, true).apply { setTextColor(BRAND) }, LayoutParams(LayoutParams.WRAP_CONTENT, 46.dp()))
                val headline = label(home.headlines[headlineIndex.coerceIn(home.headlines.indices)].title, 13f).apply {
                    setSingleLine(true)
                    ellipsize = TextUtils.TruncateAt.MARQUEE
                    marqueeRepeatLimit = -1
                    isSelected = true
                    setPadding(12.dp(), 0, 0, 0)
                }
                tickerText = headline
                addView(headline, LayoutParams(0, 46.dp(), 1f))
                isClickable = true; isFocusable = true
                setOnClickListener { home.headlines.getOrNull(headlineIndex)?.let(::open) }
            }, LayoutParams(LayoutParams.MATCH_PARENT, 46.dp()))
        }
        addView(label(context.getString(R.string.xingdun_home_feed), 20f, true).apply {
            setPadding(2.dp(), 18.dp(), 0, 10.dp())
        })
        val chips = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        (listOf(XingDunArticleCategory(0, context.getString(R.string.xingdun_home_all))) + home.categories).forEach { category ->
            chips.addView(label(category.categoryName, 13f, category.id == categoryId).apply {
                gravity = Gravity.CENTER
                setPadding(15.dp(), 0, 15.dp(), 0)
                setTextColor(if (category.id == categoryId) Color.WHITE else colors.textColorSecondary)
                background = rounded(if (category.id == categoryId) BRAND else colors.bgColorInput, 18f)
                isSelected = category.id == categoryId
                setOnClickListener { selectCategory(category.id) }
            }, LayoutParams(LayoutParams.WRAP_CONTENT, 36.dp()).apply { marginEnd = 8.dp() })
        }
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }, LayoutParams(LayoutParams.MATCH_PARENT, 48.dp()))
    }

    private inner class HeroAdapter(private val items: List<XingDunArticle>) : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        })
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val article = items[position]
            holder.container.removeAllViews()
            holder.container.addView(articleImage(article), FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            holder.container.addView(View(context).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x11000000, 0xDD0A2520.toInt()))
            }, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            holder.container.addView(LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(16.dp(), 12.dp(), 16.dp(), 16.dp())
                addView(label(article.categoryName, 11f).apply { setTextColor(0xFFD2FFF2.toInt()) })
                addView(label(article.title, 20f, true).apply {
                    setTextColor(Color.WHITE); maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, 5.dp(), 0, 0)
                })
            }, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            holder.container.contentDescription = article.title
            holder.container.isFocusable = true
            holder.container.setOnClickListener { open(article) }
        }
    }

    private fun articleRow(article: XingDunArticle): View = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
        background = rounded(colors.bgColorOperate, 14f)
        layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10.dp() }
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(label(article.title, 16f, true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
            if (article.summary.isNotBlank()) addView(label(article.summary, 12f).apply {
                setTextColor(colors.textColorSecondary); maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 6.dp(), 0, 6.dp())
            })
            addView(label(listOf(article.categoryName, article.author).filter(String::isNotBlank).joinToString(" · "), 10f).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setTextColor(colors.textColorTertiary)
            })
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        if (XingDunArticleContent.safeUrl(article.image) != null) {
            addView(articleImage(article).apply { background = rounded(colors.bgColorInput, 9f); clipToOutline = true },
                LayoutParams(98.dp(), 82.dp()).apply { marginStart = 12.dp() })
        }
        isFocusable = true
        setOnClickListener { open(article) }
    }

    private fun footer(): View = label(context.getString(when {
        loading -> R.string.xingdun_home_loading
        failed -> R.string.xingdun_home_retry
        articles.isEmpty() -> R.string.xingdun_home_empty
        hasMore -> R.string.xingdun_home_more
        else -> R.string.xingdun_home_end
    }), 13f).apply {
        gravity = Gravity.CENTER
        setTextColor(colors.textColorSecondary)
        setPadding(16.dp(), 22.dp(), 16.dp(), 26.dp())
        minHeight = if (articles.isEmpty()) 120.dp() else 64.dp()
        if (!loading && (failed || hasMore)) setOnClickListener {
            if (failedRefresh || home.categories.isEmpty()) loadHome() else loadMore(replace = articles.isEmpty())
        }
    }

    private fun articleImage(article: XingDunArticle) = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(colors.bgColorInput)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        Glide.with(this).load(XingDunArticleContent.safeUrl(article.image)).into(this)
    }

    private fun label(value: String, size: Float, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(colors.textColorPrimary); gravity = Gravity.CENTER_VERTICAL
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius * resources.displayMetrics.density
    }
    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private companion object { const val BRAND = 0xFF168F83.toInt() }
}
