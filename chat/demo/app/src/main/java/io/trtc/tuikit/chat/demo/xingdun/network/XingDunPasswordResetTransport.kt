package io.trtc.tuikit.chat.demo.xingdun.network

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

/** A lost response cannot safely replay a code delivery or a one-use password reset. */
internal class XingDunPasswordResetTransport(private val base: OkHttpClient) {
    private val singleAttempt = base.newBuilder().retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).build()

    fun newCall(request: Request): Call {
        if (request.method != "POST" || !listOf("/auth/sendResetCode", "/auth/resetPassword")
                .any { request.url.encodedPath.endsWith(it) }) return base.newCall(request)
        val body = request.body ?: return singleAttempt.newCall(request)
        val protected = request.newBuilder().post(object : RequestBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
        }).build()
        return singleAttempt.newCall(protected)
    }
}
