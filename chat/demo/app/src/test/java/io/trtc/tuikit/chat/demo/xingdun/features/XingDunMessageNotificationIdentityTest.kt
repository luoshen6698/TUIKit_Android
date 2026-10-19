package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class XingDunMessageNotificationIdentityTest {
    @Test fun collidingJavaHashesDoNotOverwriteOtherChats() {
        assertEquals("group_Aa".hashCode(), "group_BB".hashCode())
        assertNotEquals(XingDunMessageNotificationIdentity.tag("account", "group_Aa"),
            XingDunMessageNotificationIdentity.tag("account", "group_BB"))
    }

    @Test fun stableTagsKeepAccountsAndComponentBoundariesSeparate() {
        assertEquals(XingDunMessageNotificationIdentity.tag("account", "c2c_peer"),
            XingDunMessageNotificationIdentity.tag("account", "c2c_peer"))
        assertNotEquals(XingDunMessageNotificationIdentity.tag("account", "c2c_peer"),
            XingDunMessageNotificationIdentity.tag("other", "c2c_peer"))
        assertNotEquals(XingDunMessageNotificationIdentity.tag("ab", "c"),
            XingDunMessageNotificationIdentity.tag("a", "bc"))
    }
}
