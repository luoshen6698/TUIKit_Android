package io.trtc.tuikit.chat.demo.xingdun.launch

import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiException
import org.junit.Assert.assertEquals
import org.junit.Test

class XingDunAuthenticationErrorPresenterTest {
    @Test
    fun loginMapsStableBusinessErrorsWithoutExposingUnknownServerText() {
        assertEquals(
            R.string.xingdun_auth_rate_limited,
            XingDunAuthenticationErrorPresenter.login(
                XingDunApiException(422, 200, "认证尝试过于频繁，请15分钟后再试")
            )
        )
        assertEquals(
            R.string.xingdun_login_invalid_credentials,
            XingDunAuthenticationErrorPresenter.login(
                XingDunApiException(422, 200, "账号或者密码错误")
            )
        )
        assertEquals(
            R.string.xingdun_authentication_failed,
            XingDunAuthenticationErrorPresenter.login(
                XingDunApiException(50001, 200, "SQLSTATE internal table name")
            )
        )
    }

    @Test
    fun registrationMapsFieldAndKeepsUnknownMessagePrivate() {
        assertEquals(
            XingDunAuthenticationErrorPresentation(
                R.string.xingdun_registration_username_exists,
                XingDunRegistrationField.USERNAME
            ),
            XingDunAuthenticationErrorPresenter.registration(
                XingDunApiException(40901, 200, "用户名已存在")
            )
        )
        assertEquals(
            R.string.xingdun_registration_failed_safe,
            XingDunAuthenticationErrorPresenter.registration(
                XingDunApiException(50001, 200, "private exception details")
            ).message
        )
    }

    @Test
    fun imErrorsSeparateTenantExpiryAndGenericConnectionFailure() {
        assertEquals(
            R.string.xingdun_enterprise_service_expired,
            XingDunAuthenticationErrorPresenter.im(10108, "expired")
        )
        assertEquals(
            R.string.xingdun_im_connection_failed,
            XingDunAuthenticationErrorPresenter.im(6012, "raw sdk description")
        )
    }
}
