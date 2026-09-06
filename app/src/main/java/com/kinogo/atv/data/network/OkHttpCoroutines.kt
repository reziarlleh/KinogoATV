package com.kinogo.atv.data.network

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** Executes an OkHttp call without pinning a coroutine to a blocking worker thread. */
internal suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                } else {
                    response.close()
                }
            }
        },
    )
}

/** Keeps cancellation wired to the call while its response body is being consumed. */
internal suspend fun <T> Call.useCancellableResponse(
    block: suspend (Response) -> T,
): T {
    val context = currentCoroutineContext()
    val cancellationHandle = context.job.invokeOnCompletion { cause ->
        if (cause is CancellationException) cancel()
    }
    return try {
        awaitResponse().use { response ->
            context.ensureActive()
            block(response)
        }
    } finally {
        // A cancelled coroutine has not completed yet while its finally blocks unwind. Keep the
        // handler installed in that case so completion closes the active call/body stream.
        if (context.isActive) cancellationHandle.dispose()
    }
}
