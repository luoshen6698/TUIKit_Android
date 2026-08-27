package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunGroupNicknamePolicyTest {

    @Test
    fun `counter uses trimmed UTF-8 bytes like iOS`() {
        assertEquals(3, XingDunGroupNicknamePolicy.utf8ByteCount(" 星 "))
        assertEquals(32, XingDunGroupNicknamePolicy.utf8ByteCount("12345678901234567890123456789012"))
    }

    @Test
    fun `empty nickname clears card and 32-byte limit is enforced`() {
        assertTrue(XingDunGroupNicknamePolicy.canSave("   "))
        assertTrue(XingDunGroupNicknamePolicy.canSave("星盾星盾星盾星盾星盾12"))
        assertFalse(XingDunGroupNicknamePolicy.canSave("星盾星盾星盾星盾星盾123"))
        assertEquals("项目负责人", XingDunGroupNicknamePolicy.normalized("  项目负责人  "))
    }
}
