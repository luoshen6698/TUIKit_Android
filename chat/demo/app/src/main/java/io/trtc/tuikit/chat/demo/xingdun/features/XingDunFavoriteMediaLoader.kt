package io.trtc.tuikit.chat.demo.xingdun.features

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiException

internal class XingDunFavoriteMediaUnavailable(val irrecoverable: Boolean = false) : IOException("Favorite media unavailable")

/** Every explicit reload tries the saved file first, even if the source message has expired. */
internal object XingDunFavoriteMediaLoadRecovery {
    suspend fun <T> load(
        savedURL: String?,
        download: suspend (String) -> T,
        recover: suspend (Set<String>) -> T,
    ): T {
        if (savedURL != null) {
            try { return download(savedURL) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (loadError: Exception) {
                try { return recover(setOf(savedURL)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (recoveryError: Exception) {
                    val savedFileIsGone = (loadError as? XingDunFavoriteMediaUnavailable)?.irrecoverable == true
                    val sourceIsGone = (recoveryError as? XingDunFavoriteMediaUnavailable)?.irrecoverable == true ||
                        (recoveryError as? XingDunApiException)?.businessCode == 410
                    // Deleting the source cannot prove a saved URL expired during a temporary outage.
                    if (!savedFileIsGone && sourceIsGone) throw XingDunFavoriteMediaUnavailable()
                    throw recoveryError
                }
            }
        }
        return recover(emptySet())
    }
}

/** A disposable, account-scoped playback cache, never a permanent favorite backup. */
internal object XingDunFavoriteMediaLoader {
    const val MAX_BYTES = 100L * 1024 * 1024

    fun normalizedURL(raw: String?): String? = runCatching {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = if (value.startsWith("//")) "https:$value" else value
        val uri = URI(normalized)
        normalized.takeIf { it.length <= 4096 && uri.scheme == "https" &&
            !uri.host.isNullOrBlank() && uri.userInfo == null && uri.port in listOf(-1, 443) }
    }.getOrNull()

    suspend fun download(context: Context, accountContext: String, type: String, rawURL: String): File = withContext(Dispatchers.IO) {
        val url = normalizedURL(rawURL) ?: throw XingDunFavoriteMediaUnavailable()
        val digest = MessageDigest.getInstance("SHA-256").digest("$accountContext\u0000$url".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val suffix = when (type) { "PICTURE" -> ".jpg"; "VIDEO" -> ".mp4"; "AUDIO" -> ".m4a"; else -> ".bin" }
        val target = File(context.cacheDir, "xingdun-favorite-$digest$suffix")
        if (target.isFile && target.length() in 1..MAX_BYTES && validFile(target, type)) return@withContext target
        val temporary = File.createTempFile("xingdun-favorite-", ".part", context.cacheDir)
        var connection: HttpURLConnection? = null
        try {
            var currentURL = url
            for (redirect in 0..3) {
                coroutineContext.ensureActive()
                connection = URL(currentURL).openConnection() as HttpURLConnection
                connection.connectTimeout = 12_000
                connection.readTimeout = 20_000
                connection.instanceFollowRedirects = false
                val code = connection.responseCode
                if (code in 300..399) {
                    val next = connection.getHeaderField("Location") ?: throw XingDunFavoriteMediaUnavailable()
                    val resolved = URL(URL(currentURL), next).toString()
                    currentURL = normalizedURL(resolved) ?: throw XingDunFavoriteMediaUnavailable()
                    connection.disconnect()
                    if (redirect == 3) throw XingDunFavoriteMediaUnavailable()
                    continue
                }
                if (code !in 200..299) throw XingDunFavoriteMediaUnavailable(code == 404 || code == 410)
                val expected = connection.contentLengthLong
                if (expected > MAX_BYTES || context.cacheDir.usableSpace < maxOf(expected, 0) + 8 * 1024 * 1024) {
                    throw XingDunFavoriteMediaUnavailable()
                }
                val contentType = connection.contentType.orEmpty().lowercase()
                if (type != "FILE" && (contentType.contains("text/html") || contentType.contains("application/json"))) {
                    throw XingDunFavoriteMediaUnavailable()
                }
                var size = 0L
                val deadline = System.nanoTime() + 45_000_000_000L
                connection.inputStream.use { input -> temporary.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        if (System.nanoTime() > deadline) throw XingDunFavoriteMediaUnavailable()
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > MAX_BYTES) throw XingDunFavoriteMediaUnavailable()
                        output.write(buffer, 0, count)
                    }
                } }
                if (size == 0L || (expected >= 0 && size != expected) || !validFile(temporary, type)) throw XingDunFavoriteMediaUnavailable()
                if (!temporary.renameTo(target)) throw XingDunFavoriteMediaUnavailable()
                return@withContext target
            }
            throw XingDunFavoriteMediaUnavailable()
        } finally {
            connection?.disconnect()
            temporary.delete()
        }
    }

    private fun validFile(file: File, type: String): Boolean = when (type) {
        "PICTURE" -> runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) false else {
                val options = BitmapFactory.Options().apply { inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1024) }
                BitmapFactory.decodeFile(file.absolutePath, options)?.let { it.recycle(); true } == true
            }
        }.getOrDefault(false)
        "VIDEO", "AUDIO" -> runCatching {
            val reader = MediaMetadataRetriever()
            try {
                reader.setDataSource(file.absolutePath)
                val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                duration > 0 && (type != "VIDEO" || reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?.let { it > 0 } == true)
            } finally { reader.release() }
        }.getOrDefault(false)
        else -> file.length() > 0
    }
}
