package io.trtc.tuikit.chat.demo.xingdun.launch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

internal object XingDunRemoteLogoPolicy {
    fun isAllowed(rawUrl: String?): Boolean {
        val value = rawUrl?.trim().orEmpty()
        if (value.isEmpty()) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null &&
            uri.fragment == null
    }
}

internal object XingDunRemoteLogoLoader {
    private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    private const val MAX_SOURCE_DIMENSION = 4_096
    private const val TARGET_DIMENSION = 1_024
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!XingDunRemoteLogoPolicy.isAllowed(url)) return@withContext null
        val request = Request.Builder().url(url).get().build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful || response.request.url.scheme != "https") return@use null
                val body = response.body ?: return@use null
                if (body.contentLength() > MAX_RESPONSE_BYTES) return@use null
                val contentType = body.contentType()?.toString().orEmpty()
                if (contentType.isNotEmpty() && !contentType.startsWith("image/", ignoreCase = true)) {
                    return@use null
                }
                val bytes = body.byteStream().use { readLimited(it) } ?: return@use null
                decodeBounded(bytes)
            }
        }.getOrNull()
    }

    private suspend fun readLimited(stream: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            coroutineContext.ensureActive()
            val count = stream.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_RESPONSE_BYTES) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun decodeBounded(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..MAX_SOURCE_DIMENSION || bounds.outHeight !in 1..MAX_SOURCE_DIMENSION) {
            return null
        }
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > TARGET_DIMENSION ||
            bounds.outHeight / sampleSize > TARGET_DIMENSION
        ) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        )
    }
}
