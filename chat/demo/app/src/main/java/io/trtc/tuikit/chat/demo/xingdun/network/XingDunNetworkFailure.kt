package io.trtc.tuikit.chat.demo.xingdun.network

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import java.io.InterruptedIOException

/** Safe categories only: never expose URLs, request bodies or exception messages. */
internal enum class XingDunNetworkFailure {
    DNS, TIMEOUT, TLS, CONNECTION, PERMISSION, OTHER;

    companion object {
        fun from(error: Throwable): XingDunNetworkFailure {
            val causes = generateSequence(error) { it.cause }.take(8).toList()
            return when {
                causes.any { it.message?.contains("EACCES") == true || it.message?.contains("EPERM") == true } -> PERMISSION
                causes.any { it is UnknownHostException } -> DNS
                causes.any { it is SocketTimeoutException || it is InterruptedIOException } -> TIMEOUT
                causes.any { it is SSLException } -> TLS
                causes.any { it is ConnectException } -> CONNECTION
                else -> OTHER
            }
        }
    }
}
