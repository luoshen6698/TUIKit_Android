package io.trtc.tuikit.chat.demo.xingdun.session

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coalesces concurrent 401 recovery and prevents an old request from borrowing another account. */
internal class XingDunAccessTokenRecoveryCoordinator(
    private val loadCurrentSession: () -> XingDunStoredSession?,
    private val refreshCurrentSession: suspend (XingDunStoredSession) -> XingDunStoredSession?,
    private val mutex: Mutex = Mutex(),
) {
    suspend fun recover(rejectedSession: XingDunStoredSession): XingDunStoredSession? = mutex.withLock {
        val current = loadCurrentSession() ?: return null
        if (!sameIdentity(current, rejectedSession)) return null
        if (current.accessToken != rejectedSession.accessToken) return current
        refreshCurrentSession(current)
    }

    companion object {
        fun sameIdentity(first: XingDunStoredSession, second: XingDunStoredSession): Boolean =
            first.sdkAppId == second.sdkAppId &&
                first.timUserId == second.timUserId &&
                first.companyCode.equals(second.companyCode, ignoreCase = true) &&
                first.companyId == second.companyId
    }
}
