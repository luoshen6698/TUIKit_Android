package io.trtc.tuikit.chat.demo.xingdun.legal

/** Android uses its own public Chinese documents, independently of tenant and iOS URLs. */
object XingDunLegalDocuments {
    const val VERSION = "2026.10.01"
    const val PRIVACY_FILE = "xingdun-privacy-policy-android.html"
    const val AGREEMENT_FILE = "xingdun-user-agreement-android.html"
    const val BASE_URL = "https://www.xingdunim.com/"

    fun fileName(privacy: Boolean) = if (privacy) PRIVACY_FILE else AGREEMENT_FILE
    fun url(privacy: Boolean) = BASE_URL + fileName(privacy)

    fun documentType(value: String): Boolean? = when (value.substringBefore('#')) {
        url(true) -> true
        url(false) -> false
        else -> null
    }
}
