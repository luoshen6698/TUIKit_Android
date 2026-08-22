package io.trtc.tuikit.chat.demo.xingdun.network

import android.content.Context
import android.os.Build
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import io.trtc.tuikit.chat.app.BuildConfig
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.session.XingDunSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.reflect.Type
import java.util.UUID
import java.util.concurrent.TimeUnit

class XingDunApiClient(
    context: Context,
    private val sessionStore: XingDunSessionStore
) {

    private val appContext = context.applicationContext
    private val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun resolveEnterprise(
        companyCode: String?,
        domain: String?
    ): XingDunBootstrapConfiguration {
        val urlBuilder = centralBaseUrl().toHttpUrl().newBuilder()
            .addPathSegments("config/bootstrap")
        companyCode?.takeIf(String::isNotBlank)?.let {
            urlBuilder.addQueryParameter("company_code", it)
        }
        domain?.takeIf(String::isNotBlank)?.let {
            urlBuilder.addQueryParameter("domain", it)
        }
        val request = requestBuilder(urlBuilder.build().toString(), null)
            .header("Cache-Control", "no-cache")
            .get()
            .build()
        return execute(request, XingDunBootstrapConfiguration::class.java)
    }

    suspend fun login(
        baseUrl: String,
        requestBody: XingDunLoginRequest
    ): XingDunAuthResponse = post(baseUrl, "auth/login", requestBody, null, XingDunAuthResponse::class.java)

    suspend fun register(
        baseUrl: String,
        requestBody: XingDunRegisterRequest
    ): XingDunAuthResponse = post(baseUrl, "auth/register", requestBody, null, XingDunAuthResponse::class.java)

    suspend fun refresh(
        session: XingDunStoredSession
    ): XingDunAuthResponse = post(
        session.apiBaseUrl,
        "auth/refreshToken",
        XingDunRefreshRequest(session.refreshToken, session.companyCode),
        null,
        XingDunAuthResponse::class.java
    )

    suspend fun refreshIMCredential(session: XingDunStoredSession): XingDunIMCredential {
        val responseType = XingDunIMCredentialResponse::class.java
        val response: XingDunIMCredentialResponse = post(
            session.apiBaseUrl,
            "auth/imCredential",
            emptyMap<String, String>(),
            session,
            responseType
        )
        return response.resolved()
    }

    suspend fun <T> get(
        session: XingDunStoredSession,
        path: String,
        query: Map<String, String?>,
        responseType: Type
    ): T {
        val urlBuilder = endpointUrl(session.apiBaseUrl, path).toHttpUrl().newBuilder()
        query.forEach { (key, value) -> if (value != null) urlBuilder.addQueryParameter(key, value) }
        val request = requestBuilder(urlBuilder.build().toString(), session).get().build()
        return execute(request, responseType)
    }

    suspend fun <T> publicGet(path: String, query: Map<String, String?>, responseType: Type): T {
        val urlBuilder = endpointUrl(centralBaseUrl(), path).toHttpUrl().newBuilder()
        query.forEach { (key, value) -> if (value != null) urlBuilder.addQueryParameter(key, value) }
        val request = requestBuilder(urlBuilder.build().toString(), null).get().build()
        return execute(request, responseType)
    }

    suspend fun <T> post(
        session: XingDunStoredSession,
        path: String,
        body: Any,
        responseType: Type
    ): T = post(session.apiBaseUrl, path, body, session, responseType)

    suspend fun postEmpty(session: XingDunStoredSession, path: String, body: Any) {
        val requestBody = gson.toJson(body).toRequestBody(JSON_MEDIA_TYPE)
        val request = requestBuilder(endpointUrl(session.apiBaseUrl, path), session).post(requestBody).build()
        executeAllowEmpty(request)
    }

    suspend fun postMultipartEmpty(
        session: XingDunStoredSession,
        path: String,
        fields: Map<String, Any?>,
        files: List<XingDunUploadFile>
    ) {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                fields.forEach { (name, value) ->
                    if (value != null) addFormDataPart(name, value.toString())
                }
                files.forEach { file ->
                    addFormDataPart(
                        file.fieldName,
                        file.fileName,
                        file.bytes.toRequestBody(file.mimeType.toMediaType())
                    )
                }
            }
            .build()
        val request = requestBuilder(endpointUrl(session.apiBaseUrl, path), session)
            .post(multipart)
            .build()
        executeAllowEmpty(request)
    }

    suspend fun deleteEmpty(session: XingDunStoredSession, path: String, body: Any) {
        val requestBody = gson.toJson(body).toRequestBody(JSON_MEDIA_TYPE)
        val request = requestBuilder(endpointUrl(session.apiBaseUrl, path), session).delete(requestBody).build()
        executeAllowEmpty(request)
    }

    private suspend fun <T> post(
        baseUrl: String,
        path: String,
        body: Any,
        session: XingDunStoredSession?,
        responseType: Type
    ): T {
        val requestBody = gson.toJson(body).toRequestBody(JSON_MEDIA_TYPE)
        val request = requestBuilder(endpointUrl(baseUrl, path), session).post(requestBody).build()
        return execute(request, responseType)
    }

    private fun requestBuilder(url: String, session: XingDunStoredSession?): Request.Builder {
        if (session != null && !sessionStore.isBoundToCurrentEnterprise(session)) {
            throw IllegalStateException(appContext.getString(R.string.xingdun_error_company_mismatch))
        }
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent())
            .header("X-XingDun-Device-ID", sessionStore.deviceId())
            .header("X-XingDun-Trace-ID", UUID.randomUUID().toString())
        if (session != null) {
            builder.header("Authorization", "${session.tokenType} ${session.accessToken}")
        }
        return builder
    }

    private suspend fun <T> execute(request: Request, responseType: Type): T = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw apiException(payload, response.code)
            }
            val envelopeType = TypeToken.getParameterized(XingDunEnvelope::class.java, responseType).type
            val envelope: XingDunEnvelope<T> = try {
                gson.fromJson(payload, envelopeType)
            } catch (_: Exception) {
                throw XingDunApiException(null, response.code, appContext.getString(R.string.xingdun_error_response_format))
            }
            if (envelope.code != 0 && envelope.code != 200) {
                throw XingDunApiException(envelope.code, response.code, envelope.message, envelope.traceId)
            }
            envelope.data ?: throw XingDunApiException(
                envelope.code,
                response.code,
                appContext.getString(R.string.xingdun_error_response_data),
                envelope.traceId
            )
        }
    }

    private suspend fun executeAllowEmpty(request: Request): Unit = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw apiException(payload, response.code)
            val envelope = runCatching { gson.fromJson(payload, JsonObject::class.java) }.getOrNull()
                ?: throw XingDunApiException(null, response.code, appContext.getString(R.string.xingdun_error_response_format))
            val code = envelope.get("code")?.takeUnless { it.isJsonNull }?.asInt ?: -1
            if (code != 0 && code != 200) {
                throw XingDunApiException(
                    code,
                    response.code,
                    envelope.get("message")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
                    envelope.get("trace_id")?.takeUnless { it.isJsonNull }?.asString
                )
            }
        }
    }

    private fun apiException(payload: String, status: Int): XingDunApiException {
        val json = runCatching { gson.fromJson(payload, JsonObject::class.java) }.getOrNull()
        val message = json?.get("message")?.takeUnless { it.isJsonNull }?.asString
            ?: when (status) {
                401 -> appContext.getString(R.string.xingdun_session_expired)
                429 -> appContext.getString(R.string.xingdun_error_rate_limited)
                in 500..599 -> appContext.getString(R.string.xingdun_error_service_unavailable)
                else -> appContext.getString(R.string.xingdun_error_request_failed, status)
            }
        return XingDunApiException(
            businessCode = json?.get("code")?.takeUnless { it.isJsonNull }?.asInt,
            httpStatus = status,
            message = message,
            traceId = json?.get("trace_id")?.takeUnless { it.isJsonNull }?.asString
        )
    }

    private fun endpointUrl(baseUrl: String, path: String): String =
        "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"

    private fun centralBaseUrl(): String = BuildConfig.XINGDUN_API_BASE_URL.trimEnd('/')

    private fun userAgent(): String =
        "XingDunAndroid/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"

    data class XingDunIMCredentialResponse(
        val imCredential: XingDunIMCredential? = null,
        val provider: String? = null,
        val imProvider: String? = null,
        val sdkAppId: Int? = null,
        val userId: String? = null,
        val userSig: String? = null,
        val expire: Long? = null,
        val expireAt: String? = null
    ) {
        fun resolved(): XingDunIMCredential = imCredential ?: XingDunIMCredential(
            provider = provider,
            imProvider = imProvider,
            sdkAppId = sdkAppId ?: 0,
            userId = userId.orEmpty(),
            userSig = userSig.orEmpty(),
            expire = expire,
            expireAt = expireAt
        )
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

data class XingDunUploadFile(
    val fieldName: String,
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)
