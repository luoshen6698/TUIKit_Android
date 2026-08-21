package io.trtc.tuikit.chat.demo.xingdun.session

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import io.trtc.tuikit.chat.app.BuildConfig
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiClient
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiException
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunAuthResponse
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunIMCredential
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunLoginRequest
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunRegisterRequest
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunVersionCheckResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

object XingDunSessionManager {

    const val PRIVACY_VERSION = "2026.08.13"

    private lateinit var store: XingDunSessionStore
    private lateinit var client: XingDunApiClient
    private lateinit var appContext: Context
    private val refreshMutex = Mutex()

    fun initialize(context: Context) {
        if (::store.isInitialized) return
        appContext = context.applicationContext
        store = XingDunSessionStore(context.applicationContext)
        client = XingDunApiClient(context.applicationContext, store)
    }

    fun currentSession(): XingDunStoredSession? = if (::store.isInitialized) store.load() else null

    fun deviceId(): String = store.deviceId()

    internal fun apiClient(): XingDunApiClient = client

    suspend fun bootstrap(companyCode: String): XingDunBootstrapConfiguration {
        val code = normalizedCompanyCode(companyCode)
        val bootstrap = client.bootstrap(code)
        require(bootstrap.configured) { message(R.string.xingdun_error_im_not_configured) }
        require(bootstrap.imProvider.equals("tencent", ignoreCase = true)) { message(R.string.xingdun_error_im_provider) }
        require(bootstrap.companyCode.equals(code, ignoreCase = true)) { message(R.string.xingdun_error_company_mismatch) }
        require(bootstrap.sdkAppId > 0) { message(R.string.xingdun_error_sdk_app_id) }
        validateBaseUrl(resolveApiBaseUrl(bootstrap))
        return bootstrap
    }

    suspend fun login(companyCode: String, username: String, password: String): XingDunStoredSession {
        val bootstrap = bootstrap(companyCode)
        val response = client.login(
            resolveApiBaseUrl(bootstrap),
            XingDunLoginRequest(username.trim(), password, normalizedCompanyCode(companyCode))
        )
        return persist(bootstrap, response)
    }

    suspend fun register(
        companyCode: String,
        username: String,
        password: String,
        confirmPassword: String,
        nickname: String,
        inviteCode: String?
    ): XingDunStoredSession {
        val bootstrap = bootstrap(companyCode)
        client.register(
            resolveApiBaseUrl(bootstrap),
            XingDunRegisterRequest(
                username = username.trim(),
                password = password,
                confirmPassword = confirmPassword,
                nickname = nickname.trim(),
                inviteCode = inviteCode?.trim()?.takeIf(String::isNotEmpty),
                companyCode = normalizedCompanyCode(companyCode),
                adultDeclaration = true,
                consent = true,
                userAgreementVersion = PRIVACY_VERSION,
                privacyPolicyVersion = PRIVACY_VERSION,
                consentEvidenceId = "android:${store.deviceId()}:${UUID.randomUUID()}".take(64)
            )
        )
        // The registration contract deliberately returns no long-lived refresh token. Reuse the
        // normal password-login contract immediately so a newly registered account has the same
        // restorable session shape as an existing account.
        val response = client.login(
            resolveApiBaseUrl(bootstrap),
            XingDunLoginRequest(username.trim(), password, normalizedCompanyCode(companyCode))
        )
        return persist(bootstrap, response)
    }

    suspend fun restore(): XingDunStoredSession? = refreshMutex.withLock {
        val existing = store.load() ?: return null
        val now = System.currentTimeMillis()
        if (existing.refreshExpiresAtMillis <= now) {
            store.clear()
            return null
        }
        return try {
            val response = client.refresh(existing)
            persist(existing, response)
        } catch (error: XingDunApiException) {
            if (error.isUnauthorized) {
                store.clear()
                null
            } else if (existing.accessExpiresAtMillis > now + EXPIRY_SAFETY_MILLIS &&
                existing.userSigExpiresAtMillis > now + EXPIRY_SAFETY_MILLIS
            ) {
                existing
            } else {
                throw error
            }
        }
    }

    suspend fun refreshIMCredential(): XingDunStoredSession = refreshMutex.withLock {
        val existing = store.load() ?: throw XingDunApiException(401, 401, message(R.string.xingdun_session_expired))
        val credential = client.refreshIMCredential(existing)
        validateIMCredential(credential, existing.sdkAppId)
        existing.copy(
            timUserId = credential.userId,
            userSig = credential.userSig,
            userSigExpiresAtMillis = credentialExpiryMillis(credential)
        ).also(store::save)
    }

    suspend fun checkVersion(): XingDunVersionCheckResult = client.publicGet(
        "version/check",
        mapOf(
            "platform" to "android",
            "environment" to BuildConfig.XINGDUN_ENVIRONMENT,
            "current_version" to BuildConfig.VERSION_NAME,
            "current_build" to BuildConfig.VERSION_CODE.toString()
        ),
        XingDunVersionCheckResult::class.java
    )

    fun clear() {
        if (::store.isInitialized) store.clear()
    }

    private fun persist(
        bootstrap: XingDunBootstrapConfiguration,
        response: XingDunAuthResponse
    ): XingDunStoredSession {
        val companyCode = normalizedCompanyCode(bootstrap.companyCode)
        val responseCompanyCode = response.company?.code ?: response.companyCode ?: companyCode
        require(responseCompanyCode.equals(companyCode, ignoreCase = true)) { message(R.string.xingdun_error_company_mismatch) }
        val credential = requireNotNull(response.imCredential) { message(R.string.xingdun_error_im_credential_missing) }
        validateIMCredential(credential, bootstrap.sdkAppId)
        val now = System.currentTimeMillis()
        val accessExpiresIn = requirePositive(response.expiresIn, message(R.string.xingdun_error_credential_expiry))
        val refreshToken = response.refreshToken?.trim().orEmpty()
        val refreshExpiresIn = requirePositive(response.refreshExpiresIn, message(R.string.xingdun_error_credential_expiry))
        require(response.accessToken.isNotBlank() && refreshToken.isNotBlank()) { message(R.string.xingdun_error_credential_missing) }
        return XingDunStoredSession(
            accessToken = response.accessToken,
            tokenType = response.tokenType?.takeIf(String::isNotBlank) ?: "Bearer",
            accessExpiresAtMillis = now + accessExpiresIn * 1000L,
            refreshToken = refreshToken,
            refreshExpiresAtMillis = now + refreshExpiresIn * 1000L,
            companyCode = companyCode,
            companyName = response.company?.name?.takeIf(String::isNotBlank)
                ?: bootstrap.company?.name?.takeIf(String::isNotBlank)
                ?: companyCode,
            apiBaseUrl = resolveApiBaseUrl(bootstrap),
            sdkAppId = bootstrap.sdkAppId,
            timUserId = credential.userId,
            userSig = credential.userSig,
            userSigExpiresAtMillis = credentialExpiryMillis(credential),
            nickname = response.user?.nickname?.takeIf(String::isNotBlank)
                ?: response.nickname?.takeIf(String::isNotBlank)
                ?: response.username?.takeIf(String::isNotBlank)
                ?: credential.userId,
            features = bootstrap.features.copy(redpacket = false, groupCall = false),
            privacy = bootstrap.privacy
        ).also(store::save)
    }

    private fun persist(existing: XingDunStoredSession, response: XingDunAuthResponse): XingDunStoredSession {
        val credential = requireNotNull(response.imCredential) { message(R.string.xingdun_error_im_credential_missing) }
        validateIMCredential(credential, existing.sdkAppId)
        val now = System.currentTimeMillis()
        return existing.copy(
            accessToken = response.accessToken.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException(message(R.string.xingdun_error_credential_missing)),
            tokenType = response.tokenType?.takeIf(String::isNotBlank) ?: existing.tokenType,
            accessExpiresAtMillis = now + requirePositive(response.expiresIn, message(R.string.xingdun_error_credential_expiry)) * 1000L,
            refreshToken = response.refreshToken?.takeIf(String::isNotBlank) ?: existing.refreshToken,
            refreshExpiresAtMillis = response.refreshExpiresIn?.takeIf { it > 0 }
                ?.let { now + it * 1000L } ?: existing.refreshExpiresAtMillis,
            timUserId = credential.userId,
            userSig = credential.userSig,
            userSigExpiresAtMillis = credentialExpiryMillis(credential),
            nickname = response.user?.nickname?.takeIf(String::isNotBlank) ?: existing.nickname
        ).also(store::save)
    }

    private fun validateIMCredential(credential: XingDunIMCredential, expectedSDKAppId: Int) {
        require(credential.sdkAppId == expectedSDKAppId) { message(R.string.xingdun_error_company_mismatch) }
        require(credential.userId.isNotBlank() && credential.userSig.isNotBlank()) { message(R.string.xingdun_error_im_credential_missing) }
        require(credentialExpiryMillis(credential) > System.currentTimeMillis()) { message(R.string.xingdun_error_im_credential_expired) }
    }

    private fun credentialExpiryMillis(credential: XingDunIMCredential): Long {
        credential.expireAt?.let { value ->
            runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()?.let { return it }
        }
        val seconds = requirePositive(credential.expire, message(R.string.xingdun_error_credential_expiry))
        return System.currentTimeMillis() + seconds * 1000L
    }

    private fun resolveApiBaseUrl(bootstrap: XingDunBootstrapConfiguration): String =
        bootstrap.apiBaseUrl?.trim()?.takeIf(String::isNotEmpty)
            ?: bootstrap.company?.apiBaseUrl?.trim()?.takeIf(String::isNotEmpty)
            ?: BuildConfig.XINGDUN_API_BASE_URL

    private fun validateBaseUrl(value: String) {
        val uri = Uri.parse(value)
        val allowedScheme = uri.scheme.equals("https", ignoreCase = true) ||
            (BuildConfig.DEBUG && uri.scheme.equals("http", ignoreCase = true))
        require(allowedScheme && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) {
            message(R.string.xingdun_error_service_url)
        }
    }

    private fun normalizedCompanyCode(value: String): String {
        val normalized = value.trim().lowercase()
        require(normalized.length in 4..20 && normalized.all(Char::isLetterOrDigit)) { message(R.string.xingdun_error_company_code) }
        return normalized
    }

    private fun requirePositive(value: Long?, message: String): Long {
        require(value != null && value > 0) { message }
        return value
    }

    private fun message(@StringRes id: Int): String = appContext.getString(id)

    private const val EXPIRY_SAFETY_MILLIS = 60_000L
}
