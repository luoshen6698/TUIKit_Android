package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunImageSaveDestinationTest {
    @Test fun supportedLegacyAndroidSavesOnlyToAUserSelectedDocument() {
        assertTrue(XingDunImageDelivery.needsUserSelectedDestination(23))
        assertTrue(XingDunImageDelivery.needsUserSelectedDestination(28))
    }

    @Test fun scopedStorageAndroidSavesOwnedMediaWithoutAPermissionRequest() {
        assertFalse(XingDunImageDelivery.needsUserSelectedDestination(29))
        assertFalse(XingDunImageDelivery.needsUserSelectedDestination(36))
    }
}
