package io.trtc.tuikit.chat.demo.xingdun.session

import java.net.URI
import java.util.Locale

/** Only serialized through the existing Keystore-backed encrypted store. */
internal data class XingDunRememberedLogin(
    val scope: String?,
    val account: String?,
    val password: String?,
    val consentVersion: String?,
) {
    fun forScope(expected: String?): XingDunRememberedLogin? =
        takeIf { !expected.isNullOrBlank() && scope == expected && !account.isNullOrBlank() }

    fun canReuseConsent(version: String): Boolean =
        !password.isNullOrEmpty() && consentVersion == version

    fun withoutPassword() = copy(password = "", consentVersion = "")

    override fun toString() = "XingDunRememberedLogin([redacted])"
}

/** Include the actual HTTPS endpoint: a new server must not receive a previous server's password. */
internal fun rememberedLoginScope(tenantKey: String?, apiBaseUrl: String?): String? = runCatching {
    if (tenantKey.isNullOrBlank() || apiBaseUrl.isNullOrBlank()) return null
    val uri = URI(apiBaseUrl.trim())
    if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() ||
        uri.userInfo != null || uri.query != null || uri.fragment != null) return null
    val endpoint = URI("https", null, uri.host.lowercase(Locale.ROOT),
        uri.port.takeUnless { it == 443 } ?: -1, uri.path.orEmpty().trimEnd('/'), null, null)
    "$tenantKey|$endpoint"
}.getOrNull()
