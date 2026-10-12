package io.trtc.tuikit.chat.demo.xingdun.network

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** Parses and closes the response before delivery, while cancellation aborts the owning call. */
internal suspend fun <T> Call.executeCancellable(processResponse: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        if (!continuation.isActive) return@suspendCancellableCoroutine
        try {
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!continuation.isActive) return
                            processResponse(it)
                        }
                    }
                    continuation.resumeWith(result)
                }
            })
        } catch (error: Throwable) {
            continuation.resumeWith(Result.failure(error))
        }
    }
