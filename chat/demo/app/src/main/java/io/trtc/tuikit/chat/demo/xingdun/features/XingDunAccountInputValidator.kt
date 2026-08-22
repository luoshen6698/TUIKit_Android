package io.trtc.tuikit.chat.demo.xingdun.features

internal enum class XingDunAccountInputError {
    PHONE,
    EMAIL,
    USERNAME,
    PASSWORD_REQUIRED,
    PASSWORD_LENGTH,
    PASSWORD_WHITESPACE,
    PASSWORD_COMPLEXITY,
    PASSWORD_IDENTIFIER,
    PASSWORD_COMMON,
    PASSWORD_MISMATCH,
    PASSWORD_UNCHANGED,
}

internal object XingDunAccountInputValidator {
    fun phone(value: String): XingDunAccountInputError? =
        if (value.matches(Regex("^1[3-9][0-9]{9}$"))) null else XingDunAccountInputError.PHONE

    fun email(value: String): XingDunAccountInputError? {
        val normalized = value.trim()
        val parts = normalized.split('@')
        val domain = parts.getOrNull(1).orEmpty()
        return if (
            normalized.length <= 128 && parts.size == 2 && parts[0].isNotEmpty() &&
            domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.') &&
            normalized.none(Char::isWhitespace)
        ) null else XingDunAccountInputError.EMAIL
    }

    fun username(value: String): XingDunAccountInputError? =
        if (value.matches(Regex("^[A-Za-z0-9_]{3,20}$")) && !value.startsWith("dev_")) null
        else XingDunAccountInputError.USERNAME

    fun password(value: String, identifiers: List<String> = emptyList()): XingDunAccountInputError? {
        if (value.isEmpty()) return XingDunAccountInputError.PASSWORD_REQUIRED
        if (value.length !in 10..64) return XingDunAccountInputError.PASSWORD_LENGTH
        if (value.any { it.isWhitespace() || it.isISOControl() }) return XingDunAccountInputError.PASSWORD_WHITESPACE
        val categories = listOf(
            value.any(Char::isLowerCase),
            value.any(Char::isUpperCase),
            value.any(Char::isDigit),
            value.any { !it.isLetterOrDigit() }
        ).count { it }
        if (categories < 3) return XingDunAccountInputError.PASSWORD_COMPLEXITY
        val normalized = value.lowercase()
        if (identifiers.map(String::trim).filter { it.length >= 4 }.any { normalized.contains(it.lowercase()) }) {
            return XingDunAccountInputError.PASSWORD_IDENTIFIER
        }
        if (listOf("123456", "password", "qwerty", "xingdun").any(normalized::contains)) {
            return XingDunAccountInputError.PASSWORD_COMMON
        }
        return null
    }
}
