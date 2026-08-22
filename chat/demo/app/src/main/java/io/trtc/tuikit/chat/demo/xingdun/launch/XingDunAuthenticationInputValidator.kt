package io.trtc.tuikit.chat.demo.xingdun.launch

internal enum class XingDunAuthenticationInputError {
    USERNAME_REQUIRED,
    USERNAME_LENGTH,
    USERNAME_FORMAT,
    PHONE_REQUIRED,
    PHONE_FORMAT,
    EMAIL_REQUIRED,
    EMAIL_FORMAT,
    CODE_REQUIRED,
    CODE_FORMAT,
    NICKNAME_LENGTH,
    PASSWORD_REQUIRED,
    PASSWORD_LENGTH,
    PASSWORD_WHITESPACE,
    PASSWORD_CATEGORIES,
    PASSWORD_IDENTIFIER,
    PASSWORD_WEAK,
    CONFIRM_PASSWORD_REQUIRED,
    PASSWORD_MISMATCH
}

internal object XingDunAuthenticationInputValidator {
    private val usernamePattern = Regex("^[A-Za-z0-9_]+$")
    private val mobilePattern = Regex("^1[3-9][0-9]{9}$")
    private val codePattern = Regex("^[0-9]{6}$")
    private val weakFragments = listOf("123456", "password", "qwerty", "xingdun", "mataigo")

    fun usernameError(value: String): XingDunAuthenticationInputError? {
        val normalized = value.trim()
        return when {
            normalized.isEmpty() -> XingDunAuthenticationInputError.USERNAME_REQUIRED
            normalized.length !in 3..20 -> XingDunAuthenticationInputError.USERNAME_LENGTH
            !usernamePattern.matches(normalized) -> XingDunAuthenticationInputError.USERNAME_FORMAT
            else -> null
        }
    }

    fun loginUsernameError(value: String): XingDunAuthenticationInputError? {
        val normalized = value.trim()
        return when {
            normalized.isEmpty() -> XingDunAuthenticationInputError.USERNAME_REQUIRED
            normalized.length !in 3..64 -> XingDunAuthenticationInputError.USERNAME_LENGTH
            !usernamePattern.matches(normalized) -> XingDunAuthenticationInputError.USERNAME_FORMAT
            else -> null
        }
    }

    fun phoneError(value: String): XingDunAuthenticationInputError? {
        val normalized = value.trim()
        return when {
            normalized.isEmpty() -> XingDunAuthenticationInputError.PHONE_REQUIRED
            !mobilePattern.matches(normalized) -> XingDunAuthenticationInputError.PHONE_FORMAT
            else -> null
        }
    }

    fun emailError(value: String): XingDunAuthenticationInputError? {
        val normalized = value.trim()
        val at = normalized.indexOf('@')
        val domain = normalized.substringAfter('@', "")
        return when {
            normalized.isEmpty() -> XingDunAuthenticationInputError.EMAIL_REQUIRED
            normalized.length > 128 || normalized.any(Char::isWhitespace) ||
                at <= 0 || at != normalized.lastIndexOf('@') ||
                domain.isEmpty() || !domain.contains('.') || domain.startsWith('.') || domain.endsWith('.') ->
                XingDunAuthenticationInputError.EMAIL_FORMAT
            else -> null
        }
    }

    fun codeError(value: String): XingDunAuthenticationInputError? {
        val normalized = value.trim()
        return when {
            normalized.isEmpty() -> XingDunAuthenticationInputError.CODE_REQUIRED
            !codePattern.matches(normalized) -> XingDunAuthenticationInputError.CODE_FORMAT
            else -> null
        }
    }

    fun nicknameError(value: String): XingDunAuthenticationInputError? =
        XingDunAuthenticationInputError.NICKNAME_LENGTH.takeIf { value.trim().length > 64 }

    fun passwordError(
        password: String,
        identifiers: List<String> = emptyList()
    ): XingDunAuthenticationInputError? {
        if (password.isEmpty()) return XingDunAuthenticationInputError.PASSWORD_REQUIRED
        if (password.length !in 10..64) return XingDunAuthenticationInputError.PASSWORD_LENGTH
        if (password.any { it.isWhitespace() || it.isISOControl() }) {
            return XingDunAuthenticationInputError.PASSWORD_WHITESPACE
        }
        val categories = listOf(
            password.any(Char::isLowerCase),
            password.any(Char::isUpperCase),
            password.any(Char::isDigit),
            password.any { !it.isLetterOrDigit() }
        ).count { it }
        if (categories < 3) return XingDunAuthenticationInputError.PASSWORD_CATEGORIES
        val normalizedPassword = password.lowercase()
        if (identifiers.asSequence().map(String::trim).filter { it.length >= 4 }
                .map(String::lowercase).any(normalizedPassword::contains)
        ) {
            return XingDunAuthenticationInputError.PASSWORD_IDENTIFIER
        }
        if (weakFragments.any(normalizedPassword::contains)) {
            return XingDunAuthenticationInputError.PASSWORD_WEAK
        }
        return null
    }

    fun confirmationError(password: String, confirmation: String): XingDunAuthenticationInputError? = when {
        confirmation.isEmpty() -> XingDunAuthenticationInputError.CONFIRM_PASSWORD_REQUIRED
        confirmation != password -> XingDunAuthenticationInputError.PASSWORD_MISMATCH
        else -> null
    }
}
