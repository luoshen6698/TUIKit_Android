package io.trtc.tuikit.chat.demo.xingdun.session

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunFeatures
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession

/** Display metadata only: never retain access/refresh tokens, UserSig or encrypted records. */
internal data class XingDunUiSession(
    val apiBaseUrl: String,
    val companyId: Int?,
    val companyCode: String,
    val sdkAppId: Int,
    val timUserId: String,
    val username: String?,
    val nickname: String,
    val features: XingDunFeatures,
    val authenticationGeneration: Long,
    val tenantKey: String,
) {
    val scopeKey: String get() = "$apiBaseUrl|$companyId|$timUserId|$authenticationGeneration"
    // Preserve the durable message namespace exactly; this is not an authentication credential.
    val accountKey: String = com.google.gson.JsonArray().apply {
        add(apiBaseUrl.trimEnd('/')); add(tenantKey); add(timUserId)
    }.toString()
}

/** Secure-store writes and authentication changes invalidate display data without a Keystore read. */
internal class XingDunUiSessionCache(
    private val stamp: () -> XingDunSessionReadStamp?,
    private val read: () -> XingDunStoredSession?,
) {
    @Volatile private var cached: Pair<XingDunSessionReadStamp, XingDunUiSession>? = null

    fun current(): XingDunUiSession? {
        val before = stamp() ?: run { cached = null; return null }
        cached?.takeIf { it.first == before }?.let { value ->
            return value.second.takeIf { stamp() == before }
        }
        val snapshot = readConsistentSessionSnapshot(stamp, read) ?: run { cached = null; return null }
        val session = snapshot.second
        val tenant = XingDunTenantBoundary.identity(session) ?: run { cached = null; return null }
        val display = XingDunUiSession(session.apiBaseUrl, session.companyId, session.companyCode,
            session.sdkAppId, session.timUserId, session.username, session.nickname, session.features,
            snapshot.first.authenticationGeneration, tenant.key)
        cached = snapshot.first to display
        return display.takeIf { stamp() == snapshot.first }
    }
}
