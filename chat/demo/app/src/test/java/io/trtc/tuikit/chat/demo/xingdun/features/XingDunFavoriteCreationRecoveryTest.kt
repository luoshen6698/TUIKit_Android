package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class XingDunFavoriteCreationRecoveryTest {
    private val saved = JsonParser.parseString("""{"favorite_id":7,"conversation_id":"c2c_peer","message":{"message_id":"m1","message_type":"VIDEO","sender":"sender","text":"snapshot","sent_at":1700000000000}}""").asJsonObject
    private val request = XingDunFavoriteMessageRequest("m1", "c2c_peer", 1, "sender", "VIDEO", "snapshot", emptyList<Any>(), 1700000000000)

    @Test fun lostResponseFoundDoesNotRepost() = runBlocking {
        var posts = 0
        val result = XingDunFavoriteCreationRecovery.save(
            { posts++; throw IOException("timeout") }, { XingDunFavoriteConfirmation.Found(saved) }, { true })
        assertEquals(1, posts)
        assertEquals(saved, result)
    }

    @Test fun failedReadIsUnknownNotAbsent() = runBlocking {
        var posts = 0
        try {
            XingDunFavoriteCreationRecovery.save(
                { posts++; throw IOException() }, { throw IOException() }, { true })
            fail("Should remain unconfirmed")
        } catch (_: XingDunFavoriteResultPending) { assertEquals(1, posts) }
    }

    @Test fun confirmedAbsenceAllowsExactlyOneRetry() = runBlocking {
        var posts = 0
        val result = XingDunFavoriteCreationRecovery.save(
            { if (++posts == 1) throw IOException() else saved }, { XingDunFavoriteConfirmation.Absent }, { true })
        assertEquals(saved, result)
        assertEquals(2, posts)
    }

    @Test fun repeatedFailureNeverRetriesForever() = runBlocking {
        var posts = 0
        try {
            XingDunFavoriteCreationRecovery.save(
                { posts++; throw IOException() }, { XingDunFavoriteConfirmation.Absent }, { true })
            fail("Expected failure")
        } catch (_: IOException) { assertEquals(2, posts) }
    }

    @Test fun rejectedRequestIsNotRetriedOrQueried() = runBlocking {
        var reads = 0
        try {
            XingDunFavoriteCreationRecovery.save(
                { throw XingDunApiException(403, 200, "Denied") },
                { reads++; XingDunFavoriteConfirmation.Absent }, { true })
            fail("Expected permission failure")
        } catch (_: XingDunApiException) { assertEquals(0, reads) }
    }

    @Test fun changedSessionCannotAcceptLateSuccess() = runBlocking {
        var current = true
        try {
            XingDunFavoriteCreationRecovery.save({ current = false; saved },
                { XingDunFavoriteConfirmation.Found(saved) }, { current })
            fail("Expected stale session rejection")
        } catch (_: IllegalStateException) { assertFalse(current) }
    }

    @Test fun responseMustMatchMessageConversationSenderAndType() {
        assertTrue(XingDunMessageFavoritePolicy.matches(saved, request))
        listOf("message_id" to "other", "sender" to "other", "message_type" to "AUDIO").forEach { (key, value) ->
            val mismatched = saved.deepCopy()
            mismatched.getAsJsonObject("message").addProperty(key, value)
            assertFalse(XingDunMessageFavoritePolicy.matches(mismatched, request))
        }
        val wrongConversation = saved.deepCopy().apply { addProperty("conversation_id", "group_other") }
        assertFalse(XingDunMessageFavoritePolicy.matches(wrongConversation, request))
        XingDunMessageFavoritePolicy.validateResponse(saved, request)
    }

    @Test fun missingOrZeroFavoriteIdIsNotSuccess() {
        for (id in listOf(null, 0)) {
            val invalid = saved.deepCopy()
            if (id == null) invalid.remove("favorite_id") else invalid.addProperty("favorite_id", id)
            try { XingDunMessageFavoritePolicy.validateResponse(invalid, request); fail("Missing identity") }
            catch (_: IllegalStateException) { }
        }
    }

    @Test fun remoteURLRejectsLocalFilesCleartextAndCredentials() {
        assertEquals("https://cdn.example/media.mp4", XingDunFavoriteMediaLoader.normalizedURL("//cdn.example/media.mp4"))
        for (url in listOf("file:///tmp/private", "/tmp/private", "http://cdn.example/m", "https://user:pass@cdn.example/m", "https://cdn.example:80/m", "javascript:alert(1)", "")) {
            assertNull(XingDunFavoriteMediaLoader.normalizedURL(url))
        }
    }
}
