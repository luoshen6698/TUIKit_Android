package io.trtc.tuikit.chat.demo.xingdun.network

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.load.model.stream.HttpGlideUrlLoader
import java.io.InputStream

/** Route managed legacy URLs through Glide's normal HTTP fetcher and source cache. */
internal class XingDunAvatarGlideLoader(
    private val httpLoader: ModelLoader<GlideUrl, InputStream>,
) : ModelLoader<GlideUrl, InputStream> {

    override fun handles(model: GlideUrl): Boolean = httpLoader.handles(model)

    override fun buildLoadData(
        model: GlideUrl, width: Int, height: Int, options: Options,
    ): ModelLoader.LoadData<InputStream>? {
        val original = model.cacheKey
        val resolved = XingDunAvatarUrl.resolve(original)
        // Managed avatars are public. Do not forward headers across origins.
        val request = if (resolved == original) model else GlideUrl(resolved)
        return httpLoader.buildLoadData(request, width, height, options)
    }

    object Factory : ModelLoaderFactory<GlideUrl, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<GlideUrl, InputStream> =
            XingDunAvatarGlideLoader(HttpGlideUrlLoader.Factory().build(multiFactory))

        override fun teardown() = Unit
    }
}
