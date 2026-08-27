package io.trtc.tuikit.chat.demo.xingdun.features

internal object XingDunGroupQRCodePayload {
    fun make(groupID: String): String {
        val normalized = groupID.trim()
        if (normalized.isEmpty()) return ""
        return "{\"app\":\"XingDun\",\"group_id\":\"${jsonEscape(normalized)}\",\"type\":\"group\",\"version\":1}"
    }

    private fun jsonEscape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }
}
