package com.tencent.qcloud.tuikit.tuicallkit.view.callview.public.controls

import com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner.NativeCallIdentity
import com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner.NativeCallPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallControlsRenderPolicyTest {
    private val waiting = CallControlsRenderKey(
        NativeCallIdentity("previous-call", 1_000, "self-a"), NativeCallPhase.Waiting, false, true,
    )

    @Test
    fun lateNonEmptyOutgoingIdRebuildsWaitingControlsAndInvalidatesOldAction() {
        val renderer = CallControlsRenderPolicy()
        val oldControls = CallEndActionPolicy().apply { attach(waiting.identity) }
        assertTrue(renderer.update(waiting))
        val oldAction = checkNotNull(oldControls.begin(waiting.identity, attached = true, actionAllowed = true))
        val current = waiting.copy(identity = waiting.identity.copy(callId = "assigned-call"))
        assertTrue(renderer.update(current))
        oldControls.reset() // Old controls are detached before the renderer adds their replacement.
        assertFalse(oldControls.release(oldAction, current.identity, attached = false, actionAllowed = true))
        val newControls = CallEndActionPolicy().apply { attach(current.identity) }
        assertTrue(newControls.begin(current.identity, attached = true, actionAllowed = true) != null)
        assertFalse(renderer.update(current.copy()))
    }

    @Test
    fun sameClassNextCallMustNotReuseThePreviousCallsControls() {
        val renderer = CallControlsRenderPolicy()
        val accepted = waiting.copy(phase = NativeCallPhase.Accepted, isVideo = true)
        assertTrue(renderer.update(accepted))
        assertTrue(renderer.update(accepted.copy(identity = accepted.identity.copy(callId = "next-call"))))
    }

    @Test
    fun roleMediaStatusAndAccountChangesRebuildControls() {
        listOf(
            waiting.copy(isCaller = false),
            waiting.copy(isVideo = true),
            waiting.copy(isVideo = null),
            waiting.copy(phase = NativeCallPhase.Accepted),
            waiting.copy(identity = waiting.identity.copy(selfId = "self-b")),
            waiting.copy(identity = waiting.identity.copy(sdkAppId = 2_000)),
        ).forEach { changed ->
            val renderer = CallControlsRenderPolicy()
            assertTrue(renderer.update(waiting))
            assertTrue(renderer.update(changed))
        }
    }

    @Test
    fun idleClearsOldControlsAndReattachmentRendersAgain() {
        val renderer = CallControlsRenderPolicy()
        assertTrue(renderer.update(waiting))
        val idle = waiting.copy(phase = NativeCallPhase.None)
        assertTrue(renderer.update(idle))
        assertFalse(renderer.update(idle))
        renderer.reset()
        assertTrue(renderer.update(idle))
    }
}
