package io.trtc.tuikit.chat.demo.xingdun.features

internal object XingDunGroupNicknamePolicy {
    const val MAX_UTF8_BYTES = 32

    fun normalized(value: String): String = value.trim()

    fun utf8ByteCount(value: String): Int = normalized(value).toByteArray(Charsets.UTF_8).size

    fun canSave(value: String): Boolean = utf8ByteCount(value) <= MAX_UTF8_BYTES
}
