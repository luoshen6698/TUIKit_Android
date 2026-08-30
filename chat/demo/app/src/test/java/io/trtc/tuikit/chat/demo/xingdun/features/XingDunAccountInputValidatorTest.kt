package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XingDunAccountInputValidatorTest {
    @Test
    fun validatesContactAndUsernameContracts() {
        assertNull(XingDunAccountInputValidator.phone("13800138000"))
        assertEquals(XingDunAccountInputError.PHONE, XingDunAccountInputValidator.phone("123"))
        assertNull(XingDunAccountInputValidator.email("user@example.com"))
        assertNull(XingDunAccountInputValidator.email("${"a".repeat(128)}@example.com"))
        assertEquals(XingDunAccountInputError.EMAIL, XingDunAccountInputValidator.email("bad@"))
        assertNull(XingDunAccountInputValidator.username("xingdun_01"))
        assertEquals(XingDunAccountInputError.USERNAME, XingDunAccountInputValidator.username("dev_local"))
    }

    @Test
    fun enforcesIOSPasswordPolicy() {
        assertNull(XingDunAccountInputValidator.password("SafePass!20"))
        assertEquals(
            XingDunAccountInputError.PASSWORD_LENGTH,
            XingDunAccountInputValidator.password("Short1!")
        )
        assertEquals(
            XingDunAccountInputError.PASSWORD_IDENTIFIER,
            XingDunAccountInputValidator.password("Account_2026!", listOf("account"))
        )
        assertEquals(
            XingDunAccountInputError.PASSWORD_COMMON,
            XingDunAccountInputValidator.password("Password!2026")
        )
    }
}
