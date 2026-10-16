package io.trtc.tuikit.chat.demo.xingdun.session

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession

/** In-memory proof of completed server authentication; loading a stored session never creates it. */
internal class XingDunAuthenticatedWakeupSessionGate {
    private data class Proof(
        val session: XingDunStoredSession,
        val authenticatedAtElapsedMillis: Long,
    )

    private var proof: Proof? = null
    private var leavingSession = false

    /** Call only after server credentials and the active IM identity have both been verified. */
    @Synchronized
    fun recordAuthenticated(session: XingDunStoredSession, nowElapsedMillis: Long) {
        if (!leavingSession) proof = Proof(session, nowElapsedMillis)
    }

    @Synchronized
    fun clear() {
        proof = null
    }

    @Synchronized
    fun beginSessionLeave() {
        leavingSession = true
        proof = null
    }

    @Synchronized
    fun finishSessionLeave() {
        proof = null
        leavingSession = false
    }

    @Synchronized
    fun authenticatedSession(
        currentSession: XingDunStoredSession?,
        imSdkAppId: Int,
        imUserId: String?,
        imIsLoggedIn: Boolean,
        nowElapsedMillis: Long,
        nowEpochMillis: Long,
    ): XingDunStoredSession? {
        val existing = proof ?: return null
        val age = nowElapsedMillis - existing.authenticatedAtElapsedMillis
        val session = existing.session
        val usable = !leavingSession &&
            age in 0L until PROOF_TTL_MILLIS &&
            currentSession == session &&
            imIsLoggedIn && imSdkAppId == session.sdkAppId && imUserId == session.timUserId &&
            session.accessExpiresAtMillis - nowEpochMillis > ACCESS_EXPIRY_SAFETY_MILLIS &&
            session.userSigExpiresAtMillis - nowEpochMillis > USER_SIG_EXPIRY_SAFETY_MILLIS
        if (!usable) {
            proof = null
            return null
        }
        return session
    }

    companion object {
        const val PROOF_TTL_MILLIS = 60_000L
        const val ACCESS_EXPIRY_SAFETY_MILLIS = XingDunSessionRestorePolicy.EXPIRY_SAFETY_MILLIS
        const val USER_SIG_EXPIRY_SAFETY_MILLIS = XingDunCredentialRefreshPolicy.REFRESH_LEAD_TIME_MILLIS
    }
}
