package io.trtc.tuikit.chat.demo.xingdun.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class XingDunClientReleaseIdentityTest {
    @Test fun identityUsesBuildChannelAndKeepsExactVersionAndBuild() {
        val identity = XingDunClientReleaseIdentity.parameters("xuyinfang", "universal", "1.0.3", 8, "com.xuyinfang.app")
        assertEquals("android", identity["client_platform"])
        assertEquals("universal", identity["release_channel"])
        assertEquals("1.0.3", identity["app_version"])
        assertEquals("8", identity["app_build"])
        assertEquals("com.xuyinfang.app", identity["client_package"])
    }
    @Test fun malformedReleaseIdentityCannotReachServer() {
        for (channel in listOf("Huawei", "huawei*", "", "a b")) {
            assertThrows(IllegalArgumentException::class.java) { XingDunClientReleaseIdentity.parameters("xuyinfang", channel, "1.0.3", 8, "com.xuyinfang.app") }
        }
        assertThrows(IllegalArgumentException::class.java) { XingDunClientReleaseIdentity.parameters("xuyinfang", "huawei", "1.0.*", 8, "com.xuyinfang.app") }
        assertThrows(IllegalArgumentException::class.java) { XingDunClientReleaseIdentity.parameters("xuyinfang", "huawei", "1.0.3", 0, "com.xuyinfang.app") }
    }
}
