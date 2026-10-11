package io.trtc.tuikit.chat.demo.xingdun.features

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class XingDunFavoriteMediaLoadRecoveryTest {
    @Test fun savedMediaWorksWithoutReadingDeletedSource() = runBlocking {
        var repairs = 0
        val value = XingDunFavoriteMediaLoadRecovery.load("saved", { "playable" }, { repairs++; throw IOException() })
        assertEquals("playable", value)
        assertEquals(0, repairs)
    }
    @Test fun actualFailureTriggersOnlyOneRecovery() = runBlocking {
        var repairs = 0
        try {
            XingDunFavoriteMediaLoadRecovery.load("old", { throw IOException() }, {
                assertEquals(setOf("old"), it); repairs++; throw IOException()
            })
            fail("Expected bounded failure")
        } catch (_: IOException) { assertEquals(1, repairs) }
    }
    @Test fun explicitReloadTriesSavedURLAgainAfterTemporaryNetworkFailure() = runBlocking {
        var online = false
        var repairs = 0
        val download: suspend (String) -> String = { if (!online) throw IOException() else "playable" }
        try {
            XingDunFavoriteMediaLoadRecovery.load("saved", download, { repairs++; throw XingDunFavoriteMediaUnavailable(true) })
        } catch (error: XingDunFavoriteMediaUnavailable) { assertFalse(error.irrecoverable) }
        online = true
        assertEquals("playable", XingDunFavoriteMediaLoadRecovery.load("saved", download, { repairs++; throw IOException() }))
        assertEquals(1, repairs)
    }
    @Test fun missingURLCanUseAuthorizedRecovery() = runBlocking {
        assertEquals("recovered", XingDunFavoriteMediaLoadRecovery.load(null, { fail("No URL"); "" }, {
            assertTrue(it.isEmpty()); "recovered"
        }))
    }
    @Test fun expiredSavedFileAndDeletedSourceAreIrrecoverable() = runBlocking {
        try {
            XingDunFavoriteMediaLoadRecovery.load("gone", { throw XingDunFavoriteMediaUnavailable(true) }, {
                throw XingDunFavoriteMediaUnavailable(true)
            })
            fail("Expected expired media")
        } catch (error: XingDunFavoriteMediaUnavailable) { assertTrue(error.irrecoverable) }
    }
    @Test fun cancelledLoadDoesNotStartRepair() = runBlocking {
        var repairs = 0
        try {
            XingDunFavoriteMediaLoadRecovery.load("saved", { throw CancellationException() }, { repairs++; "bad" })
            fail("Expected cancellation")
        } catch (_: CancellationException) { assertEquals(0, repairs) }
    }
}
