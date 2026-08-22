package io.trtc.tuikit.chat.demo.xingdun.launch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunRemoteLogoPolicyTest {

    @Test
    fun acceptsPublicHttpsImageLocation() {
        assertTrue(XingDunRemoteLogoPolicy.isAllowed("https://cdn.example.com/enterprise/logo.png"))
    }

    @Test
    fun rejectsMissingInsecureOrCredentialedLocations() {
        assertFalse(XingDunRemoteLogoPolicy.isAllowed(null))
        assertFalse(XingDunRemoteLogoPolicy.isAllowed(""))
        assertFalse(XingDunRemoteLogoPolicy.isAllowed("http://cdn.example.com/logo.png"))
        assertFalse(XingDunRemoteLogoPolicy.isAllowed("https://user:secret@cdn.example.com/logo.png"))
        assertFalse(XingDunRemoteLogoPolicy.isAllowed("https://cdn.example.com/logo.png#fragment"))
    }
}
