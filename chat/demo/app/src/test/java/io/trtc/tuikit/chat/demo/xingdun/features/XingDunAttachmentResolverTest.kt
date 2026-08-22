package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XingDunAttachmentResolverTest {
    @Test
    fun recognizesOnlyApprovedImageSignatures() {
        assertEquals(
            "image/jpeg",
            XingDunAttachmentResolver.imageFormat(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))?.mimeType
        )
        assertEquals(
            "image/png",
            XingDunAttachmentResolver.imageFormat(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))?.mimeType
        )
        assertEquals(
            "image/webp",
            XingDunAttachmentResolver.imageFormat("RIFF0000WEBP".toByteArray())?.mimeType
        )
        assertNull(XingDunAttachmentResolver.imageFormat("GIF89a".toByteArray()))
    }
}
