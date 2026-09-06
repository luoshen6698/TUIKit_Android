package io.trtc.tuikit.chat.demo.xingdun.network

/** Runs one authenticated request, allowing exactly one credential recovery and replay after 401. */
internal class XingDunAuthenticatedRequestExecutor {
    private var recoverSession: (suspend (XingDunStoredSession) -> XingDunStoredSession?)? = null
    private var rejectSession: ((XingDunStoredSession) -> Unit)? = null

    fun configure(
        recoverSession: suspend (XingDunStoredSession) -> XingDunStoredSession?,
        rejectSession: (XingDunStoredSession) -> Unit,
    ) {
        this.recoverSession = recoverSession
        this.rejectSession = rejectSession
    }

    suspend fun <T> execute(
        session: XingDunStoredSession?,
        initialRequest: suspend () -> T,
        replayRequest: suspend (XingDunStoredSession) -> T,
    ): T {
        try {
            return initialRequest()
        } catch (error: Throwable) {
            if (session == null || !error.isUnauthorized() || recoverSession == null) throw error

            val recovered = recoverSession?.invoke(session)
            if (recovered == null) {
                rejectSession?.invoke(session)
                throw error
            }
            try {
                return replayRequest(recovered)
            } catch (replayError: Throwable) {
                if (replayError.isUnauthorized()) rejectSession?.invoke(recovered)
                throw replayError
            }
        }
    }

    private fun Throwable.isUnauthorized(): Boolean =
        this is XingDunApiException && isUnauthorized
}
