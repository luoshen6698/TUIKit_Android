package io.trtc.tuikit.chat.demo.xingdun.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XingDunAuthenticationInputValidatorTest {

    @Test
    fun accountPhoneEmailAndCodeMatchIosRules() {
        assertNull(XingDunAuthenticationInputValidator.usernameError("member_2026"))
        assertEquals(
            XingDunAuthenticationInputError.USERNAME_LENGTH,
            XingDunAuthenticationInputValidator.usernameError("ab")
        )
        assertNull(XingDunAuthenticationInputValidator.phoneError("13800138000"))
        assertEquals(
            XingDunAuthenticationInputError.PHONE_FORMAT,
            XingDunAuthenticationInputValidator.phoneError("12800138000")
        )
        assertNull(XingDunAuthenticationInputValidator.emailError("member@example.com"))
        assertEquals(
            XingDunAuthenticationInputError.EMAIL_FORMAT,
            XingDunAuthenticationInputValidator.emailError("member@example")
        )
        assertNull(XingDunAuthenticationInputValidator.codeError("123456"))
        assertEquals(
            XingDunAuthenticationInputError.CODE_FORMAT,
            XingDunAuthenticationInputValidator.codeError("12345")
        )
    }

    @Test
    fun passwordMatchesIosAndServerStrengthPolicy() {
        assertNull(XingDunAuthenticationInputValidator.passwordError("Safe#Entry9A"))
        assertEquals(
            XingDunAuthenticationInputError.PASSWORD_CATEGORIES,
            XingDunAuthenticationInputValidator.passwordError("onlyletters")
        )
        assertEquals(
            XingDunAuthenticationInputError.PASSWORD_IDENTIFIER,
            XingDunAuthenticationInputValidator.passwordError("Member_2026#A", listOf("member_2026"))
        )
        assertEquals(
            XingDunAuthenticationInputError.PASSWORD_WEAK,
            XingDunAuthenticationInputValidator.passwordError("Password#90")
        )
    }
}
