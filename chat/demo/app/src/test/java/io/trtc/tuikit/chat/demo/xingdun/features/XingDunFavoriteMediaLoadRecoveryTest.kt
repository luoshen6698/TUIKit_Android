package io.trtc.tuikit.chat.demo.xingdun.features

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class XingDunFavoriteMediaLoadRecoveryTest {
    @Test fun recoveredFilesUseTheExistingFileProviderRoot() {
        val paths = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/xml/xingdun_file_paths.xml"))
        val providerRoot = paths.getElementsByTagName("cache-path").item(0).attributes.getNamedItem("path").nodeValue
        val cacheRoot = File(System.getProperty("java.io.tmpdir"), "favorite-cache-path-test")
        assertEquals(File(cacheRoot, providerRoot).canonicalFile,
            XingDunFavoriteMediaLoader.cacheDirectory(cacheRoot).canonicalFile)
        assertNotEquals(cacheRoot.canonicalFile, XingDunFavoriteMediaLoader.cacheDirectory(cacheRoot).canonicalFile)
    }
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
