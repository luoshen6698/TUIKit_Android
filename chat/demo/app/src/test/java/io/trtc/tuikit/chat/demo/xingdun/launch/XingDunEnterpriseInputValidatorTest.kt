package io.trtc.tuikit.chat.demo.xingdun.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunEnterpriseInputValidatorTest {

    @Test
    fun companyCodeIsTrimmedLowercasedAndValidated() {
        val lookup = XingDunEnterpriseInputValidator.resolve(
            XingDunEnterpriseLookupMode.COMPANY_CODE,
            "  XC2026  ",
            ""
        ).getOrThrow()

        assertEquals("xc2026", lookup.companyCode)
        assertNull(lookup.domain)
    }

    @Test
    fun invalidCompanyCodeReturnsSpecificError() {
        val failure = XingDunEnterpriseInputValidator.resolve(
            XingDunEnterpriseLookupMode.COMPANY_CODE,
            "bad-code",
            ""
        ).exceptionOrNull() as XingDunEnterpriseInputException

        assertEquals(XingDunEnterpriseInputError.COMPANY_CODE_INVALID, failure.error)
    }

    @Test
    fun domainAcceptsUrlAndKeepsOnlyNormalizedHost() {
        val lookup = XingDunEnterpriseInputValidator.resolve(
            XingDunEnterpriseLookupMode.DOMAIN,
            "",
            " HTTPS://IM.XINGDUN.CN/path "
        ).getOrThrow()

        assertEquals("im.xingdun.cn", lookup.domain)
        assertNull(lookup.companyCode)
    }

    @Test
    fun domainRejectsSingleLabelAndWhitespace() {
        listOf("localhost", "im .xingdun.cn").forEach { value ->
            val result = XingDunEnterpriseInputValidator.resolve(
                XingDunEnterpriseLookupMode.DOMAIN,
                "",
                value
            )
            assertTrue(result.isFailure)
            assertEquals(
                XingDunEnterpriseInputError.DOMAIN_INVALID,
                (result.exceptionOrNull() as XingDunEnterpriseInputException).error
            )
        }
    }
}
