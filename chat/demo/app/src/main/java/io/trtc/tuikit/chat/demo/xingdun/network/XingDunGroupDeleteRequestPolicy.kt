package io.trtc.tuikit.chat.demo.xingdun.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Group deletion is reconciled by message ID. A transport must not silently retry or redirect its mutation. */
internal object XingDunGroupDeleteRequestPolicy {
    /** Gson's missing Int default must not turn a malformed envelope into a successful deletion. */
    fun responseData(payload: String): JsonObject {
        val root = JsonParser.parseString(payload)
        require(root.isJsonObject)
        val envelope = root.asJsonObject
        val code = envelope["code"] ?: error("Missing business code")
        require(code.isJsonPrimitive && code.asJsonPrimitive.isNumber && code.asString.matches(Regex("-?(0|[1-9][0-9]*)")))
        val businessCode = code.asString.toIntOrNull() ?: error("Invalid business code")
        if (businessCode != 0 && businessCode != 200) {
            fun optionalString(key: String, limit: Int): String? = envelope[key]?.takeIf {
                it.isJsonPrimitive && it.asJsonPrimitive.isString
            }?.asString?.takeIf { it.length <= limit }
            throw XingDunGroupEndpointBusinessException(businessCode, optionalString("message", 1000), optionalString("trace_id", 128))
        }
        return envelope["data"]?.takeIf { it.isJsonObject }?.asJsonObject ?: error("Missing deletion data")
    }

    fun appliesTo(request: Request): Boolean = request.method == "POST" && request.url.encodedPath.endsWith("/message/groupDelete")
    fun isRead(request: Request): Boolean = request.method == "POST" && listOf("/message/groupDeleted", "/message/groupDeletePermissions")
        .any { request.url.encodedPath.endsWith(it) }
    fun client(base: OkHttpClient): OkHttpClient = base.newBuilder()
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()

    fun protect(request: Request): Request {
        if (!appliesTo(request)) return request
        val body = request.body ?: return request
        return request.newBuilder().post(object : RequestBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
        }).build()
    }
}

internal class XingDunGroupEndpointBusinessException(val code: Int, val serverMessage: String?, val traceID: String?) :
    RuntimeException("Group endpoint returned a business error")
