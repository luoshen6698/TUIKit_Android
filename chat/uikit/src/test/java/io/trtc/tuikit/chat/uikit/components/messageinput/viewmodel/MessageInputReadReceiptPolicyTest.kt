package io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel

import io.trtc.tuikit.atomicxcore.api.group.GroupType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageInputReadReceiptPolicyTest {
    @Test
    fun enabledFeatureRequestsReceiptsForC2CAndSupportedGroups() {
        assertTrue(MessageInputReadReceiptPolicy.needReadReceipt(true, null))
        assertTrue(MessageInputReadReceiptPolicy.needReadReceipt(true, GroupType.WORK))
    }

    @Test
    fun disabledFeatureAndCommunityDoNotRequestReceipts() {
        assertFalse(MessageInputReadReceiptPolicy.needReadReceipt(false, null))
        assertFalse(MessageInputReadReceiptPolicy.needReadReceipt(true, GroupType.COMMUNITY))
    }
}
