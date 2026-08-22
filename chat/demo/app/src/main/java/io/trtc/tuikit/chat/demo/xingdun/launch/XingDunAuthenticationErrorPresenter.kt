package io.trtc.tuikit.chat.demo.xingdun.launch

import androidx.annotation.StringRes
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiException
import java.io.IOException

internal enum class XingDunRegistrationField {
    USERNAME,
    PHONE,
    CODE,
    NICKNAME,
    PASSWORD,
    CONFIRM_PASSWORD,
    INVITE_CODE
}

internal data class XingDunAuthenticationErrorPresentation(
    @StringRes val message: Int,
    val registrationField: XingDunRegistrationField? = null
)

internal object XingDunAuthenticationErrorPresenter {
    @StringRes
    fun login(error: Throwable): Int {
        if (error is IOException) return R.string.xingdun_network_error
        val apiError = error as? XingDunApiException ?: return R.string.xingdun_authentication_failed
        val message = apiError.message.trim()
        return when {
            apiError.httpStatus == 429 || message.contains("频繁") || message.contains("尝试过多") ->
                R.string.xingdun_auth_rate_limited
            apiError.businessCode == 40010 -> R.string.xingdun_enterprise_lookup_required
            apiError.businessCode == 40911 -> R.string.xingdun_error_company_mismatch
            message.contains("未注册") -> R.string.xingdun_login_account_not_registered
            message.contains("禁用") || message.contains("停用") -> R.string.xingdun_login_account_disabled
            message.contains("密码错误") || message.contains("账号或者密码错误") || message.contains("用户名或密码错误") ->
                R.string.xingdun_login_invalid_credentials
            else -> R.string.xingdun_authentication_failed
        }
    }

    fun registration(error: Throwable): XingDunAuthenticationErrorPresentation {
        if (error is IOException) return XingDunAuthenticationErrorPresentation(R.string.xingdun_network_error)
        val apiError = error as? XingDunApiException
            ?: return XingDunAuthenticationErrorPresentation(R.string.xingdun_registration_failed_safe)
        val message = apiError.message.trim()
        if (apiError.httpStatus == 429 || message.contains("频繁") || message.contains("尝试过多")) {
            return XingDunAuthenticationErrorPresentation(R.string.xingdun_auth_rate_limited)
        }
        if (apiError.businessCode == 40911) {
            return XingDunAuthenticationErrorPresentation(R.string.xingdun_error_company_mismatch)
        }
        return when {
            message.contains("用户名") && message.contains("存在") -> presentation(
                R.string.xingdun_registration_username_exists,
                XingDunRegistrationField.USERNAME
            )
            message.contains("用户名") -> presentation(
                R.string.xingdun_username_format,
                XingDunRegistrationField.USERNAME
            )
            message.contains("手机号") && message.contains("注册") -> presentation(
                R.string.xingdun_registration_phone_exists,
                XingDunRegistrationField.PHONE
            )
            message.contains("手机号") -> presentation(R.string.xingdun_phone_invalid, XingDunRegistrationField.PHONE)
            message.contains("验证码") -> presentation(R.string.xingdun_code_invalid, XingDunRegistrationField.CODE)
            message.contains("昵称") -> presentation(R.string.xingdun_nickname_too_long, XingDunRegistrationField.NICKNAME)
            message.contains("确认密码") || message.contains("两次密码") -> presentation(
                R.string.xingdun_password_mismatch,
                XingDunRegistrationField.CONFIRM_PASSWORD
            )
            message.contains("密码") -> presentation(R.string.xingdun_registration_password_invalid, XingDunRegistrationField.PASSWORD)
            message.contains("邀请") -> presentation(R.string.xingdun_invitation_invalid, XingDunRegistrationField.INVITE_CODE)
            else -> XingDunAuthenticationErrorPresentation(R.string.xingdun_registration_failed_safe)
        }
    }

    @StringRes
    fun reset(error: Throwable): Int = when (error) {
        is IOException -> R.string.xingdun_network_error
        is XingDunApiException -> if (
            error.httpStatus == 429 || error.message.contains("频繁") || error.message.contains("尝试过多")
        ) R.string.xingdun_auth_rate_limited else R.string.xingdun_password_reset_failed_safe
        else -> R.string.xingdun_password_reset_failed_safe
    }

    @StringRes
    fun im(code: Int, description: String): Int = when {
        code == 10108 -> R.string.xingdun_enterprise_service_expired
        code == -1 && description.contains("企业") -> R.string.xingdun_error_company_mismatch
        else -> R.string.xingdun_im_connection_failed
    }

    private fun presentation(
        @StringRes message: Int,
        field: XingDunRegistrationField
    ) = XingDunAuthenticationErrorPresentation(message, field)
}
