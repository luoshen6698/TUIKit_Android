package io.trtc.tuikit.chat.demo.xingdun.network

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class XingDunAuthenticatedRequestExecutorTest {

    @Test
    fun unauthorizedRequestRefreshesAndReplaysExactlyOnce() = runBlocking {
        val old = session("old-access")
        val renewed = session("renewed-access")
        var recoveries = 0
        var replays = 0
        val executor = XingDunAuthenticatedRequestExecutor().apply {
            configure(
                recoverSession = {
                    recoveries += 1
                    renewed
                },
                rejectSession = { fail("renewed request must not be rejected") },
            )
        }

        val result = executor.execute(
            session = old,
            initialRequest = { throw unauthorized() },
            replayRequest = {
                assertEquals("renewed-access", it.accessToken)
                replays += 1
                "success"
            },
        )

        assertEquals("success", result)
        assertEquals(1, recoveries)
        assertEquals(1, replays)
    }

    @Test
    fun secondUnauthorizedRejectsSessionWithoutThirdAttempt() = runBlocking {
        val old = session("old-access")
        val renewed = session("renewed-access")
        val rejected = mutableListOf<XingDunStoredSession>()
        var replays = 0
        val executor = XingDunAuthenticatedRequestExecutor().apply {
            configure(recoverSession = { renewed }, rejectSession = rejected::add)
        }

        try {
            executor.execute<Unit>(
                session = old,
                initialRequest = { throw unauthorized() },
                replayRequest = {
                    replays += 1
                    throw unauthorized()
                },
            )
            fail("Expected replayed 401")
        } catch (_: XingDunApiException) {
            // Expected.
        }

        assertEquals(1, replays)
        assertEquals(listOf(renewed), rejected)
    }

    @Test
    fun rejectedRefreshEndsSessionWithoutReplay() = runBlocking {
        val old = session("old-access")
        val rejected = mutableListOf<XingDunStoredSession>()
        var replays = 0
        val executor = XingDunAuthenticatedRequestExecutor().apply {
            configure(recoverSession = { null }, rejectSession = rejected::add)
        }

        try {
            executor.execute(
                session = old,
                initialRequest = { throw unauthorized() },
                replayRequest = {
                    replays += 1
                    Unit
                },
            )
            fail("Expected original 401")
        } catch (_: XingDunApiException) {
            // Expected.
        }

        assertEquals(0, replays)
        assertEquals(listOf(old), rejected)
    }

    @Test
    fun offlineFailureKeepsSessionAndDoesNotAttemptRecovery() = runBlocking {
        val old = session("old-access")
        var recoveries = 0
        var rejections = 0
        val offline = IOException("offline")
        val executor = XingDunAuthenticatedRequestExecutor().apply {
            configure(
                recoverSession = {
                    recoveries += 1
                    null
                },
                rejectSession = { rejections += 1 },
            )
        }

        try {
            executor.execute(
                session = old,
                initialRequest = { throw offline },
                replayRequest = { Unit },
            )
            fail("Expected offline failure")
        } catch (error: IOException) {
            assertSame(offline, error)
        }

        assertEquals(0, recoveries)
        assertEquals(0, rejections)
    }

    @Test
    fun offlineDuringRefreshKeepsSessionAndDoesNotReplay() = runBlocking {
        val old = session("old-access")
        var replays = 0
        var rejections = 0
        val offline = IOException("offline during refresh")
        val executor = XingDunAuthenticatedRequestExecutor().apply {
            configure(
                recoverSession = { throw offline },
                rejectSession = { rejections += 1 },
            )
        }

        try {
            executor.execute(
                session = old,
                initialRequest = { throw unauthorized() },
                replayRequest = {
                    replays += 1
                    Unit
                },
            )
            fail("Expected offline refresh failure")
        } catch (error: IOException) {
            assertSame(offline, error)
        }

        assertEquals(0, replays)
        assertEquals(0, rejections)
    }

    private fun unauthorized() = XingDunApiException(40100, 401, "unauthorized")

    private fun session(accessToken: String) = XingDunStoredSession(
        accessToken = accessToken,
        tokenType = "Bearer",
        accessExpiresAtMillis = Long.MAX_VALUE,
        refreshToken = "refresh",
        refreshExpiresAtMillis = Long.MAX_VALUE,
        companyCode = "xc2026",
        companyId = 1,
        companyName = "XingDun",
        apiBaseUrl = "https://example.com/prod/im/v1",
        sdkAppId = 1_000,
        timUserId = "xd_xc2026_1",
        userSig = "user-sig",
        userSigExpiresAtMillis = Long.MAX_VALUE,
        nickname = "User",
    )
}
