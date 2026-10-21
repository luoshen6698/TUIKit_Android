package io.trtc.tuikit.chat.demo.xingdun.launch

import org.junit.Assert.assertEquals
import org.junit.Test

class XingDunStartupRecoveryPolicyTest {
    @Test fun firstTransientFailureRetriesPromptly() {
        assertEquals(2_000L, XingDunStartupRecoveryPolicy.retryDelayMillis(0))
    }

    @Test fun repeatedFailuresBackOffWithoutASignInDeadline() {
        assertEquals(5_000L, XingDunStartupRecoveryPolicy.retryDelayMillis(1))
        assertEquals(10_000L, XingDunStartupRecoveryPolicy.retryDelayMillis(2))
        assertEquals(30_000L, XingDunStartupRecoveryPolicy.retryDelayMillis(3))
        assertEquals(30_000L, XingDunStartupRecoveryPolicy.retryDelayMillis(Int.MAX_VALUE))
    }
}
