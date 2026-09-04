package io.trtc.tuikit.chat.demo.xingdun.session

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import io.trtc.tuikit.chat.app.BuildConfig
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunAccountDeletionStatus
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiClient
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunApiException
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunAuthResponse
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunIMCredential
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunLoginRequest
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunPhoneRegisterRequest
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunRegisterRequest
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunResetCodeResponse
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunResetPasswordRequest
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunSendResetCodeRequest
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunStoredSession
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunVersionCheckResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
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

    fun currentSession(): XingDunStoredSession? = if (::store.isInitialized) store.loadBoundSession() else null

    fun currentEnterprise(): XingDunBootstrapConfiguration? {
        if (!::store.isInitialized) return null
        val enterprise = store.loadEnterprise() ?: return null
        val hasPushSnapshot = runCatching { enterprise.push }.getOrNull() != null
        if (XingDunTenantBoundary.identity(enterprise) == null || !hasPushSnapshot) {
            store.clearEnterprise()
            store.clearSession()
            XingDunReadReceiptFeatureSynchronizer.reset()
            return null
        }
        return enterprise
    }

    fun deviceId(): String = store.deviceId()

    internal fun apiClient(): XingDunApiClient = client

    suspend fun resolveEnterprise(
        companyCode: String?,
        domain: String?
    ): XingDunBootstrapConfiguration {
        val bootstrap = discoverEnterprise(companyCode, domain)
        selectEnterprise(bootstrap)
        return bootstrap
    }

    suspend fun discoverEnterprise(
        companyCode: String?,
        domain: String?
    ): XingDunBootstrapConfiguration {
        val normalizedCode = companyCode?.let(::normalizedCompanyCode)
        val normalizedDomain = domain?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
        require(normalizedCode != null || normalizedDomain != null || (companyCode == null && domain == null)) {
            message(R.string.xingdun_enterprise_lookup_required)
        }
        val bootstrap = client.resolveEnterprise(normalizedCode, normalizedDomain)
        validateBootstrap(bootstrap, normalizedCode)
        return bootstrap
    }

    fun selectEnterprise(bootstrap: XingDunBootstrapConfiguration) {
        validateBootstrap(bootstrap, bootstrap.companyCode)
        storeEnterprise(bootstrap)
    }

    suspend fun attemptSimpleEnterprise(): XingDunBootstrapConfiguration? {
        val bootstrap = discoverEnterprise(null, null)
        if (!bootstrap.mode.equals("simple", ignoreCase = true)) return null
        selectEnterprise(bootstrap)
        return bootstrap
    }

    suspend fun refreshSelectedEnterprise(): XingDunBootstrapConfiguration? {
        val selected = store.loadEnterprise() ?: return null
        val refreshed = discoverEnterprise(selected.companyCode, null)
        val selectedIdentity = XingDunTenantBoundary.identity(selected)
        val refreshedIdentity = XingDunTenantBoundary.identity(refreshed)
        require(selectedIdentity != null && refreshedIdentity != null && selectedIdentity.matches(refreshedIdentity)) {
            message(R.string.xingdun_error_company_mismatch)
        }
        selectEnterprise(refreshed)
        return refreshed
    }

    fun shouldRetainCachedEnterprise(error: Throwable): Boolean = when (error) {
        is IOException -> true
        is XingDunApiException -> error.httpStatus == 429 || (error.httpStatus ?: 500) >= 500
        else -> false
    }

    suspend fun bootstrap(companyCode: String): XingDunBootstrapConfiguration {
        val code = normalizedCompanyCode(companyCode)
        store.loadEnterprise()?.takeIf { it.companyCode.equals(code, ignoreCase = true) }?.let {
            validateBootstrap(it, code)
            return it
        }
        return resolveEnterprise(code, null)
    }

    private fun validateBootstrap(bootstrap: XingDunBootstrapConfiguration, requestedCode: String?) {
        require(bootstrap.configured) { message(R.string.xingdun_error_im_not_configured) }
        require(bootstrap.imProvider.equals("tencent", ignoreCase = true)) { message(R.string.xingdun_error_im_provider) }
        require(bootstrap.companyCode.isNotBlank()) { message(R.string.xingdun_error_company_mismatch) }
        require(bootstrap.company?.code?.equals(bootstrap.companyCode, ignoreCase = true) == true) {
            message(R.string.xingdun_error_company_mismatch)
        }
        require((bootstrap.company?.id ?: 0) > 0) { message(R.string.xingdun_error_company_mismatch) }
        if (requestedCode != null) {
            require(bootstrap.companyCode.equals(requestedCode, ignoreCase = true)) {
                message(R.string.xingdun_error_company_mismatch)
            }
        }
        require(bootstrap.sdkAppId > 0) { message(R.string.xingdun_error_sdk_app_id) }
        validateBaseUrl(resolveApiBaseUrl(bootstrap))
    }

    private fun storeEnterprise(bootstrap: XingDunBootstrapConfiguration) {
        store.saveEnterprise(bootstrap)
        XingDunReadReceiptFeatureSynchronizer.apply(bootstrap)
        val session = store.load() ?: return
        val sessionIdentity = XingDunTenantBoundary.identity(session)
        val enterpriseIdentity = XingDunTenantBoundary.identity(bootstrap)
        if (sessionIdentity == null || enterpriseIdentity == null || !sessionIdentity.matches(enterpriseIdentity)) {
            store.clearSession()
            return
        }
        store.save(
            session.copy(
                companyName = bootstrap.company?.name?.takeIf(String::isNotBlank) ?: session.companyName,
                apiBaseUrl = resolveApiBaseUrl(bootstrap),
                companyId = bootstrap.company?.id,
                push = bootstrap.push,
                features = bootstrap.features,
                privacy = bootstrap.privacy
            )
        )
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
        val response = client.register(
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
        return persist(bootstrap, response)
    }

    suspend fun registerByPhone(
        companyCode: String,
        phone: String,
        code: String,
        password: String,
        confirmPassword: String,
        nickname: String,
        inviteCode: String?
    ): XingDunStoredSession {
        val bootstrap = bootstrap(companyCode)
        val response = client.registerByPhone(
            resolveApiBaseUrl(bootstrap),
            XingDunPhoneRegisterRequest(
                phone = phone.trim(),
                code = code.trim(),
                password = password,
                confirmPassword = confirmPassword,
                nickname = nickname.trim().takeIf(String::isNotEmpty),
                inviteCode = inviteCode?.trim()?.takeIf(String::isNotEmpty),
                companyCode = normalizedCompanyCode(companyCode),
                adultDeclaration = true,
                consent = true,
                userAgreementVersion = PRIVACY_VERSION,
                privacyPolicyVersion = PRIVACY_VERSION,
                consentEvidenceId = "android:${store.deviceId()}:${UUID.randomUUID()}".take(64)
            )
        )
        return persist(bootstrap, response)
    }

    suspend fun deletionStatus(deletionReceipt: String): XingDunAccountDeletionStatus {
        val bootstrap = currentEnterprise() ?: throw IllegalStateException(message(R.string.xingdun_enterprise_lookup_required))
        return client.deletionStatus(resolveApiBaseUrl(bootstrap), deletionReceipt)
    }

    suspend fun sendResetCode(
        companyCode: String,
        verifyType: String,
        target: String
    ): XingDunResetCodeResponse {
        val bootstrap = bootstrap(companyCode)
        return client.sendResetCode(
            resolveApiBaseUrl(bootstrap),
            XingDunSendResetCodeRequest(
                verifyType = verifyType,
                target = target.trim(),
                companyCode = normalizedCompanyCode(companyCode)
            )
        )
    }

    suspend fun resetPassword(
        companyCode: String,
        verifyType: String,
        target: String,
        code: String,
        newPassword: String,
        confirmPassword: String
    ) {
        val bootstrap = bootstrap(companyCode)
        client.resetPassword(
            resolveApiBaseUrl(bootstrap),
            XingDunResetPasswordRequest(
                verifyType = verifyType,
                target = target.trim(),
                code = code.trim(),
                newPassword = newPassword,
                confirmPassword = confirmPassword,
                companyCode = normalizedCompanyCode(companyCode)
            )
        )
    }

    suspend fun restore(preferCachedCredentials: Boolean = false): XingDunStoredSession? = refreshMutex.withLock {
        val existing = store.loadBoundSession() ?: return null
        val now = System.currentTimeMillis()
        val accessAndIMAreUsable = existing.accessExpiresAtMillis > now + EXPIRY_SAFETY_MILLIS &&
            existing.userSigExpiresAtMillis > now + EXPIRY_SAFETY_MILLIS
        if (preferCachedCredentials && accessAndIMAreUsable) return existing
        val canRefresh = !existing.refreshToken.isNullOrBlank() &&
            (existing.refreshExpiresAtMillis ?: 0L) > now
        if (!canRefresh) {
            if (accessAndIMAreUsable) return existing
            store.clearSession()
            return null
        }
        return try {
            val response = client.refresh(existing)
            persist(existing, response)
        } catch (error: XingDunApiException) {
            if (error.isUnauthorized) {
                store.clear()
                null
            } else if (accessAndIMAreUsable) {
                existing
            } else {
                throw error
            }
        }
    }

    suspend fun refreshIMCredential(): XingDunStoredSession = refreshMutex.withLock {
        val existing = store.loadBoundSession()
            ?: throw XingDunApiException(401, 401, message(R.string.xingdun_session_expired))
        val credential = client.refreshIMCredential(existing)
        validateIMCredential(credential, existing.sdkAppId)
        require(credential.userId == existing.timUserId) { message(R.string.xingdun_error_company_mismatch) }
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
        if (::store.isInitialized) store.clearSession()
    }

    fun clearEnterpriseSelection() {
        if (!::store.isInitialized) return
        store.clearSession()
        store.clearEnterprise()
        XingDunReadReceiptFeatureSynchronizer.reset()
    }

    private fun persist(
        bootstrap: XingDunBootstrapConfiguration,
        response: XingDunAuthResponse
    ): XingDunStoredSession {
        val companyCode = normalizedCompanyCode(bootstrap.companyCode)
        val tenantIdentity = requireNotNull(XingDunTenantBoundary.identity(bootstrap)) {
            message(R.string.xingdun_error_company_mismatch)
        }
        require(XingDunTenantBoundary.responseMatches(response, tenantIdentity, bootstrap.company?.id)) {
            message(R.string.xingdun_error_company_mismatch)
        }
        val credential = requireNotNull(response.imCredential) { message(R.string.xingdun_error_im_credential_missing) }
        validateIMCredential(credential, bootstrap.sdkAppId)
        val now = System.currentTimeMillis()
        val accessExpiresIn = requirePositive(response.expiresIn, message(R.string.xingdun_error_credential_expiry))
        val refreshToken = response.refreshToken?.trim()?.takeIf(String::isNotEmpty)
        val refreshExpiresAtMillis = if (refreshToken == null) {
            null
        } else {
            now + requirePositive(response.refreshExpiresIn, message(R.string.xingdun_error_credential_expiry)) * 1000L
        }
        require(response.accessToken.isNotBlank()) { message(R.string.xingdun_error_credential_missing) }
        return XingDunStoredSession(
            accessToken = response.accessToken,
            tokenType = response.tokenType?.takeIf(String::isNotBlank) ?: "Bearer",
            accessExpiresAtMillis = now + accessExpiresIn * 1000L,
            refreshToken = refreshToken,
            refreshExpiresAtMillis = refreshExpiresAtMillis,
            companyCode = companyCode,
            companyId = bootstrap.company?.id,
            companyName = response.company?.name?.takeIf(String::isNotBlank)
                ?: bootstrap.company?.name?.takeIf(String::isNotBlank)
                ?: companyCode,
            apiBaseUrl = resolveApiBaseUrl(bootstrap),
            sdkAppId = bootstrap.sdkAppId,
            timUserId = credential.userId,
            userSig = credential.userSig,
            userSigExpiresAtMillis = credentialExpiryMillis(credential),
            username = response.user?.username?.takeIf(String::isNotBlank)
                ?: response.username?.takeIf(String::isNotBlank)
                ?: response.user?.nickname?.takeIf(String::isNotBlank)
                ?: credential.userId,
            nickname = response.user?.nickname?.takeIf(String::isNotBlank)
                ?: response.nickname?.takeIf(String::isNotBlank)
                ?: response.username?.takeIf(String::isNotBlank)
                ?: credential.userId,
            push = bootstrap.push,
            features = bootstrap.features,
            privacy = bootstrap.privacy
        ).also {
            storeEnterprise(bootstrap)
            store.save(it)
        }
    }

    private fun persist(existing: XingDunStoredSession, response: XingDunAuthResponse): XingDunStoredSession {
        val tenantIdentity = requireNotNull(XingDunTenantBoundary.identity(existing)) {
            message(R.string.xingdun_error_company_mismatch)
        }
        require(XingDunTenantBoundary.responseMatches(response, tenantIdentity, existing.companyId)) {
            message(R.string.xingdun_error_company_mismatch)
        }
        val credential = requireNotNull(response.imCredential) { message(R.string.xingdun_error_im_credential_missing) }
        validateIMCredential(credential, existing.sdkAppId)
        require(credential.userId == existing.timUserId) { message(R.string.xingdun_error_company_mismatch) }
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

    fun matchesCurrentIMIdentity(sdkAppId: Int, userId: String): Boolean {
        val session = currentSession() ?: return false
        return session.sdkAppId == sdkAppId && session.timUserId == userId
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
