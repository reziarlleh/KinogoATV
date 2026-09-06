package com.kinogo.atv.data.network

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpCoroutinesTest {
    @Test
    fun cancellingAwaitCancelsTheUnderlyingHttpCall() = runTest {
        val call = PendingCall()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { call.awaitResponse() }

        job.cancel()
        job.join()

        assertTrue(call.cancelled.get())
    }

    @Test
    fun cancellingResponseProcessingStillCancelsTheUnderlyingHttpCall() = runTest {
        val call = ImmediateResponseCall()
        val processingStarted = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            call.useCancellableResponse {
                processingStarted.complete(Unit)
                awaitCancellation()
            }
        }

        processingStarted.await()
        job.cancel()
        job.join()

        assertTrue(call.cancelled.get())
    }

    private class PendingCall : Call {
        val cancelled = AtomicBoolean(false)

        override fun request(): Request = Request.Builder().url("https://example.com/").build()
        override fun execute(): Response = error("Synchronous execution is not expected")
        override fun enqueue(responseCallback: Callback) = Unit
        override fun cancel() {
            cancelled.set(true)
        }
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = cancelled.get()
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = PendingCall()
    }

    private class ImmediateResponseCall : Call {
        val cancelled = AtomicBoolean(false)
        private val request = Request.Builder().url("https://example.com/").build()

        override fun request(): Request = request
        override fun execute(): Response = error("Synchronous execution is not expected")
        override fun enqueue(responseCallback: Callback) {
            responseCallback.onResponse(
                this,
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ByteArray(0).toResponseBody())
                    .build(),
            )
        }
        override fun cancel() {
            cancelled.set(true)
        }
        override fun isExecuted(): Boolean = true
        override fun isCanceled(): Boolean = cancelled.get()
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = ImmediateResponseCall()
    }
}
