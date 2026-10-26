package io.trtc.tuikit.chat.demo.xingdun.launch

import org.junit.Assert.*
import org.junit.Test

class XingDunResetCodeCooldownTest {
    @Test fun `cooldown is shared for the same normalized tenant and contact`() {
        val policy = XingDunResetCodeCooldown()
        val key = policy.key(" ACME ", "email", " User@Example.com ")
        policy.begin(key, 10_000)
        assertEquals(60_000L, policy.remaining(policy.key("acme", "email", "user@example.com"), 10_000))
        assertEquals(1L, policy.remaining(key, 69_999))
        assertEquals(0L, policy.remaining(key, 70_000))
    }
    @Test fun `another target or tenant cannot inherit a request cooldown`() {
        val policy = XingDunResetCodeCooldown()
        policy.begin(policy.key("a", "phone", "13800000001"), 5_000)
        assertEquals(0L, policy.remaining(policy.key("b", "phone", "13800000001"), 5_000))
        assertEquals(0L, policy.remaining(policy.key("a", "phone", "13800000002"), 5_000))
    }
    @Test fun `recreation restores deadline without extending the cooldown`() {
        val policy = XingDunResetCodeCooldown()
        val key = policy.key("a", "phone", "13800000001")
        policy.begin(key, 2_000)
        val restored = XingDunResetCodeCooldown(policy.snapshot(20_000).toMutableMap())
        assertEquals(42_000L, restored.remaining(key, 20_000))
        assertTrue(restored.snapshot(62_000).isEmpty())
    }
}
