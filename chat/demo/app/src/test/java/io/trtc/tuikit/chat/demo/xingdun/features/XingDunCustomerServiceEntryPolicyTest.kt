package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunCustomerServiceEntryPolicyTest {
    @Test
    fun knownFriendOpensChatWithoutRemoteRelationship() {
        assertTrue(shouldOpenCustomerServiceChat(isKnownFriend = true, relationship = null))
    }

    @Test
    fun confirmedFriendOrBlockedRelationshipOpensChat() {
        assertTrue(shouldOpenCustomerServiceChat(isKnownFriend = false, relationship = "friend"))
        assertTrue(shouldOpenCustomerServiceChat(isKnownFriend = false, relationship = "blocked"))
    }

    @Test
    fun nonFriendRelationshipsKeepAddFriendFlow() {
        assertFalse(shouldOpenCustomerServiceChat(isKnownFriend = false, relationship = null))
        assertFalse(shouldOpenCustomerServiceChat(isKnownFriend = false, relationship = "none"))
        assertFalse(shouldOpenCustomerServiceChat(isKnownFriend = false, relationship = "outgoing"))
        assertFalse(shouldOpenCustomerServiceChat(isKnownFriend = false, relationship = "incoming"))
    }
}
