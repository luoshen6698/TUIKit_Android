package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XingDunStorageManagerTest {
    @Test
    fun classifiesOnlySupportedMediaCacheFiles() {
        assertEquals(XingDunCacheCategory.IMAGE, XingDunStorageManager.category("avatar.WEBP"))
        assertEquals(XingDunCacheCategory.AUDIO, XingDunStorageManager.category("voice.m4a"))
        assertEquals(XingDunCacheCategory.VIDEO, XingDunStorageManager.category("clip.mp4"))
        assertEquals(XingDunCacheCategory.FILE, XingDunStorageManager.category("document.pdf"))
        assertEquals(XingDunCacheCategory.THUMBNAIL, XingDunStorageManager.category("message_thumbnail.webp"))
        assertEquals(XingDunCacheCategory.TEMPORARY, XingDunStorageManager.category("upload.part"))
        assertNull(XingDunStorageManager.category("session.json"))
        assertNull(XingDunStorageManager.category("token"))
    }
}
