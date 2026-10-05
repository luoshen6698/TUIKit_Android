package io.trtc.tuikit.chat.demo.xingdun.legal

import java.io.File

/** Use Context.noBackupFilesDir so a fresh installation cannot restore an old consent. */
class XingDunPrivacyConsentStore(directory: File) {
    private val receipt = File(directory, "xingdun-privacy-consent")

    fun hasConsent(): Boolean = runCatching { receipt.readText() == CURRENT_VERSION }.getOrDefault(false)

    fun accept() {
        receipt.parentFile?.mkdirs()
        receipt.writeText(CURRENT_VERSION)
        check(hasConsent()) { "Privacy consent could not be saved" }
    }

    companion object {
        const val CURRENT_VERSION = "2026.10.04"
    }
}
