package io.trtc.tuikit.chat.demo.xingdun.network

import java.net.URI

internal object XingDunAvatarUrl {
    private const val LEGACY_HOST = "api.xingdunim.com"
    private const val CURRENT_ORIGIN = "https://api.xuyinfang365.bid"
    private val managedPath = Regex("/upload/im/(avatar|group)/[A-Za-z0-9_./-]+")

    /** IM profiles and old messages can still carry the former URL of a managed image. */
    fun resolve(value: String): String {
        val candidate = value.trim()
        if (!candidate.startsWith("https://$LEGACY_HOST", ignoreCase = true)) return value
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return value
        val path = uri.rawPath ?: return value
        if (!LEGACY_HOST.equals(uri.host, ignoreCase = true) ||
            uri.rawUserInfo != null || uri.port !in setOf(-1, 443) ||
            !managedPath.matches(path) || ".." in path
        ) return value

        return buildString {
            append(CURRENT_ORIGIN)
            append(path)
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
    }
}
