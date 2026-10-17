package com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCallUiLifecyclePolicyTest {
    private val first = NativeCallIdentity("first", 123, "self")
    private val second = NativeCallIdentity("second", 123, "self")

    @Test fun aLatePermissionResultCannotOpenOrRejectAnotherCall() {
        assertTrue(NativeCallUiLifecyclePolicy.canPresentIncoming(first, first, true, true, true))
        assertFalse(NativeCallUiLifecyclePolicy.canPresentIncoming(first, second, true, true, true))
        assertFalse(NativeCallUiLifecyclePolicy.canPresentIncoming(first, first, false, true, true))
        assertFalse(NativeCallUiLifecyclePolicy.canPresentIncoming(first, first, true, false, true))
        assertFalse(NativeCallUiLifecyclePolicy.canPresentIncoming(first, first, true, true, false))
    }

    @Test fun anIdleCallClosesWithoutDependingOnAnEndEvent() {
        assertTrue(NativeCallUiLifecyclePolicy.shouldFinishIdleCall(first, first, true))
        assertTrue(NativeCallUiLifecyclePolicy.shouldFinishIdleCall(null, null, true))
    }

    @Test fun aDelayedIdleCloseCannotCloseTheNextCall() {
        assertFalse(NativeCallUiLifecyclePolicy.shouldFinishIdleCall(first, second, true))
        assertFalse(NativeCallUiLifecyclePolicy.shouldFinishIdleCall(first, first, false))
    }

    @Test fun clearedSdkIdentityIsNotMistakenForANewCall() {
        assertFalse(NativeCallUiLifecyclePolicy.hasDifferentActiveCall(first,
            NativeCallIdentity("first", 0, ""), true))
        assertFalse(NativeCallUiLifecyclePolicy.hasDifferentActiveCall(first,
            NativeCallIdentity("", 123, "self"), false))
    }

    @Test fun onlyARealActiveReplacementProtectsTheNewUi() {
        assertTrue(NativeCallUiLifecyclePolicy.hasDifferentActiveCall(first, second, false))
        assertFalse(NativeCallUiLifecyclePolicy.hasDifferentActiveCall(first, second, true))
        assertFalse(NativeCallUiLifecyclePolicy.hasDifferentActiveCall(first, first, false))
    }
}
