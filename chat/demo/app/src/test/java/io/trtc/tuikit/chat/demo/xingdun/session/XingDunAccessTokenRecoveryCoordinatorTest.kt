package io.trtc.tuikit.chat.demo.xingdun.session

import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XingDunAccessTokenRecoveryCoordinatorTest {

    @Test
    fun concurrentUnauthorizedRequestsShareOneRefresh() = runBlocking {
        val rejected = session("old-access")
        val renewed = session("renewed-access")
        var current = rejected
        var refreshCalls = 0
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val coordinator = XingDunAccessTokenRecoveryCoordinator(
            loadCurrentSession = { current },
            refreshCurrentSession = {
                refreshCalls += 1
                refreshStarted.complete(Unit)
                releaseRefresh.await()
                current = renewed
                renewed
            },
        )

        val first = async { coordinator.recover(rejected) }
        refreshStarted.await()
        val second = async { coordinator.recover(rejected) }
        releaseRefresh.complete(Unit)

        assertEquals(renewed, first.await())
        assertEquals(renewed, second.await())
        assertEquals(1, refreshCalls)
    }

    @Test
    fun staleRequestCannotRecoverWithAnotherAccountSession() = runBlocking {
        val rejected = session("old-access", userId = "user-a")
        val current = session("new-access", userId = "user-b")
        var refreshCalls = 0
        val coordinator = XingDunAccessTokenRecoveryCoordinator(
            loadCurrentSession = { current },
            refreshCurrentSession = {
                refreshCalls += 1
                it
            },
        )

        assertNull(coordinator.recover(rejected))
        assertEquals(0, refreshCalls)
    }

    private fun session(accessToken: String, userId: String = "xd_xc2026_1") = XingDunStoredSession(
        accessToken = accessToken,
        tokenType = "Bearer",
        accessExpiresAtMillis = Long.MAX_VALUE,
        refreshToken = "refresh-$userId",
        refreshExpiresAtMillis = Long.MAX_VALUE,
        companyCode = "xc2026",
        companyId = 1,
        companyName = "XingDun",
        apiBaseUrl = "https://example.com/prod/im/v1",
        sdkAppId = 1_000,
        timUserId = userId,
        userSig = "user-sig",
        userSigExpiresAtMillis = Long.MAX_VALUE,
        nickname = "User",
    )
}
