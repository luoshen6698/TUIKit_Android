package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunMessageFavoritePolicyTest {
    @Test fun `recent favorite response maps message ids without crossing snapshots`() {
        val page = JsonParser.parseString(
            """{"items":[{"favorite_id":7,"message":{"message_id":"m1"}},{"id":8,"message_id":"m2"}]}""",
        ).asJsonObject

        assertEquals(mapOf("m1" to 7, "m2" to 8), XingDunMessageFavoritePolicy.favoriteIDs(page))
    }

    @Test fun `image type follows iOS server contract`() {
        assertEquals("PICTURE", XingDunMessageFavoritePolicy.serverMessageType("IMAGE"))
        assertEquals("CUSTOM", XingDunMessageFavoritePolicy.serverMessageType("TIPS"))
    }

    @Test fun `removing a favorite rewinds pagination cursor`() {
        assertEquals(2, XingDunMessageFavoritePolicy.pageAfterRemoval(3))
        assertEquals(0, XingDunMessageFavoritePolicy.pageAfterRemoval(0))
    }

    @Test fun `image favorite keeps original and thumbnail cloud urls`() {
        val attachment = XingDunMessageFavoritePolicy.imageAttachment(
            originalURL = "https://cdn.example/original.jpg",
            thumbnailURL = "https://cdn.example/thumb.jpg",
            largeURL = null,
        ) as List<*>

        assertEquals(1, attachment.size)
        assertTrue(XingDunMessageFavoritePolicy.hasCompleteMedia("PICTURE", "https://cdn.example/thumb.jpg", null))
        assertFalse(XingDunMessageFavoritePolicy.hasCompleteMedia("PICTURE", null, null))
    }

    @Test fun `video favorite requires both cover and playback cloud urls`() {
        val attachment = XingDunMessageFavoritePolicy.videoAttachment(
            snapshotURL = "https://cdn.example/cover.jpg",
            videoURL = "https://cdn.example/video.mp4",
        ) as List<*>

        assertEquals(1, attachment.size)
        assertTrue(XingDunMessageFavoritePolicy.hasCompleteMedia(
            "VIDEO",
            "https://cdn.example/cover.jpg",
            "https://cdn.example/video.mp4",
        ))
        assertFalse(XingDunMessageFavoritePolicy.hasCompleteMedia(
            "VIDEO",
            "https://cdn.example/cover.jpg",
            null,
        ))
    }
}
