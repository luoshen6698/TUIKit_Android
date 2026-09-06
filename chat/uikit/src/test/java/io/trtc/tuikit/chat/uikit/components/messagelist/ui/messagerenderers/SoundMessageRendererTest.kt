package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundMessageRendererTest {
    @Test
    fun `ltr sent message puts duration before icon`() {
        assertTrue(SoundMessageRenderer.shouldShowDurationBeforeIcon(isSelf = true, isRtl = false))
    }

    @Test
    fun `ltr received message keeps icon before duration`() {
        assertFalse(SoundMessageRenderer.shouldShowDurationBeforeIcon(isSelf = false, isRtl = false))
    }

    @Test
    fun `rtl mirrors sent message order`() {
        assertFalse(SoundMessageRenderer.shouldShowDurationBeforeIcon(isSelf = true, isRtl = true))
    }

    @Test
    fun `rtl mirrors received message order`() {
        assertTrue(SoundMessageRenderer.shouldShowDurationBeforeIcon(isSelf = false, isRtl = true))
    }
}
