package io.trtc.tuikit.chat.demo.xingdun.legal

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class XingDunPrivacyConsentStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun freshInstallationRequiresAnExplicitDecision() {
        assertFalse(XingDunPrivacyConsentStore(temporary.root).hasConsent())
    }

    @Test fun acceptanceSurvivesStoreRecreation() {
        XingDunPrivacyConsentStore(temporary.root).accept()
        assertTrue(XingDunPrivacyConsentStore(temporary.root).hasConsent())
    }

    @Test fun outdatedOrDamagedReceiptRequiresConsentAgain() {
        val receipt = File(temporary.root, "xingdun-privacy-consent")
        for (value in listOf("", "2026.10.01", "2026.10.0", "true")) {
            receipt.writeText(value)
            assertFalse(XingDunPrivacyConsentStore(temporary.root).hasConsent())
        }
    }

    @Test fun aNewInstallationDirectoryDoesNotInheritConsent() {
        XingDunPrivacyConsentStore(temporary.newFolder("old-install")).accept()
        assertFalse(XingDunPrivacyConsentStore(temporary.newFolder("fresh-install")).hasConsent())
    }
}
