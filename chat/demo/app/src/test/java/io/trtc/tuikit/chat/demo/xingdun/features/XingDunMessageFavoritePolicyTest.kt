package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunMessageFavoritePolicyTest {
    @Test fun `late recent GET preserves a newly created favorite`() {
        val started = mapOf("old" to 1)
        val afterCreate = started + ("new" to 2)
        assertEquals(afterCreate, XingDunMessageFavoritePolicy.recentIDsAfterRead(started, mapOf("new" to 2)))
    }

    @Test fun `late recent GET does not restore a removed favorite`() {
        val started = mapOf("old" to 1)
        val afterRemove = emptyMap<String, Int?>()
        assertEquals(afterRemove, XingDunMessageFavoritePolicy.recentIDsAfterRead(started, mapOf("old" to null)))
    }

    @Test fun `normal recent GET accepts the fetched snapshot`() {
        val fetched = mapOf("new" to 2)
        assertEquals(fetched, XingDunMessageFavoritePolicy.recentIDsAfterRead(fetched, emptyMap()))
    }

    @Test fun `first recent GET keeps preexisting favorites when a new POST finishes earlier`() {
        assertEquals(mapOf("preexisting" to 1, "new" to 2), XingDunMessageFavoritePolicy.recentIDsAfterRead(
            mapOf("preexisting" to 1), mapOf("new" to 2)))
    }

    @Test fun `late GET cannot restore a removed favorite absent from the initial cache`() {
        assertEquals(mapOf("keep" to 2), XingDunMessageFavoritePolicy.recentIDsAfterRead(
            mapOf("removed" to 1, "keep" to 2), mapOf("removed" to null)))
    }

    @Test fun `page read started before removal must be refreshed not displayed`() {
        assertFalse(XingDunMessageFavoritePolicy.canApplyPage(0, 1))
        assertTrue(XingDunMessageFavoritePolicy.canApplyPage(1, 1))
    }

    @Test fun `native image types map to the REST contract`() {
        assertEquals(1, XingDunMessageFavoritePolicy.canonicalImageType(0))
        assertEquals(3, XingDunMessageFavoritePolicy.canonicalImageType(1))
        assertEquals(2, XingDunMessageFavoritePolicy.canonicalImageType(2))
        assertEquals(null, XingDunMessageFavoritePolicy.canonicalImageType(99))
    }
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

    @Test fun `video favorite allows a missing cover when playback url exists`() {
        val attachment = XingDunMessageFavoritePolicy.videoAttachment(
            snapshotURL = "https://cdn.example/cover.jpg",
            videoURL = "https://cdn.example/video.mp4",
        ) as List<*>

        assertEquals(1, attachment.size)
        assertTrue(XingDunMessageFavoritePolicy.hasCompleteMedia("VIDEO", null, "https://cdn.example/video.mp4"))
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
