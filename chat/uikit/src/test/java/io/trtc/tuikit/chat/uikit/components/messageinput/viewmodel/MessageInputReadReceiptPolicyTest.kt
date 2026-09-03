package io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel

import io.trtc.tuikit.atomicxcore.api.group.GroupType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageInputReadReceiptPolicyTest {
    @Test
    fun enabledFeatureRequestsReceiptsOnlyForSupportedGroups() {
        assertFalse(MessageInputReadReceiptPolicy.needReadReceipt(true, false, null))
        assertTrue(MessageInputReadReceiptPolicy.needReadReceipt(true, true, GroupType.WORK))
    }

    @Test
    fun disabledFeatureAndCommunityDoNotRequestReceipts() {
        assertFalse(MessageInputReadReceiptPolicy.needReadReceipt(false, true, GroupType.WORK))
        assertFalse(MessageInputReadReceiptPolicy.needReadReceipt(true, true, GroupType.COMMUNITY))
    }
}
