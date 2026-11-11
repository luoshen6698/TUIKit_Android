package io.trtc.tuikit.chat.demo.xingdun.launch

import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiException
import org.junit.Assert.assertEquals
import org.junit.Test

class XingDunRegistrationFailureTest {
    @Test
    fun stableConsentErrorsDoNotBlameAccountFields() {
        for ((code, message) in listOf(
            42208 to R.string.xingdun_registration_legal_version_changed,
            42209 to R.string.xingdun_registration_consent_invalid,
            50020 to R.string.xingdun_registration_service_unavailable
        )) {
            assertEquals(
                XingDunAuthenticationErrorPresentation(message),
                XingDunAuthenticationErrorPresenter.registration(
                    XingDunApiException(code, 200, "private server details")
                )
            )
        }
    }

    @Test
    fun rejectedNicknameDoesNotClaimTheServiceOrLengthFailed() {
        for (message in listOf("昵称内容不符合规范，请修改后重试", "private provider details")) {
            assertEquals(
                XingDunAuthenticationErrorPresentation(
                    R.string.xingdun_nickname_rejected,
                    XingDunRegistrationField.NICKNAME
                ),
                XingDunAuthenticationErrorPresenter.registration(XingDunApiException(42204, 200, message))
            )
        }
        assertEquals(
            R.string.xingdun_nickname_too_long,
            XingDunAuthenticationErrorPresenter.registration(
                XingDunApiException(42204, 200, "昵称最多64个字符")
            ).message
        )
    }

    @Test
    fun duplicateAndUnknownErrorsStillRemainSafe() {
        assertEquals(
            XingDunRegistrationField.USERNAME,
            XingDunAuthenticationErrorPresenter.registration(
                XingDunApiException(40901, 200, "用户名已存在，请更换后重试")
            ).registrationField
        )
        assertEquals(
            R.string.xingdun_registration_failed_safe,
            XingDunAuthenticationErrorPresenter.registration(
                XingDunApiException(50001, 200, "private server details")
            ).message
        )
    }
}
