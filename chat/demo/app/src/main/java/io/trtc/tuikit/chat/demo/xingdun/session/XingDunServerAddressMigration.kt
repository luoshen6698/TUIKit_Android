package io.trtc.tuikit.chat.demo.xingdun.session

import java.net.URI

/** Only the retired platform origin is redirected; independent tenant servers are preserved. */
internal object XingDunServerAddressMigration {
    fun api(value: String): String = migrate(value, "api.xingdunim.com", "api.xuyinfang365.bid", "/prod/im/v1")

    fun website(value: String): String = migrate(value, "www.xingdunim.com", "www.xuyinfang365.bid", null)

    private fun migrate(value: String, oldHost: String, newHost: String, apiPath: String?): String {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return value
        val scheme = uri.scheme?.lowercase()
        if (scheme !in setOf("https", "http") || !uri.host.equals(oldHost, ignoreCase = true) ||
            uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
            uri.port !in (if (scheme == "https") setOf(-1, 443) else setOf(-1, 80)) ||
            (apiPath != null && uri.path.trimEnd('/') != apiPath)) return value
        return URI("https", null, newHost, -1, if (apiPath != null) apiPath else uri.path, null, null).toASCIIString()
    }
}
