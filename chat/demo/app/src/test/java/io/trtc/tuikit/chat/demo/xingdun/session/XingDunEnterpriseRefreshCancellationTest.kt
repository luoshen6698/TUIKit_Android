package io.trtc.tuikit.chat.demo.xingdun.session

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunEnterpriseRefreshCancellationTest {
    @Test fun cancelledRefreshIsNotEvidenceOfAnInvalidEnterprise() {
        assertTrue(XingDunSessionManager.shouldRetainCachedEnterprise(CancellationException("superseded")))
    }
}
