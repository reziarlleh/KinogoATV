package com.kinogo.atv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Serializes durable checkpoint writes in callback order.
 *
 * The tail is sampled rather than held while joining: holding this monitor across suspension would
 * prevent a concurrent callback from enqueueing the next write.
 */
internal class PlaybackCheckpointWriteQueue {
    private var tail: Job? = null

    fun enqueue(
        scope: CoroutineScope,
        write: suspend () -> Unit,
    ) {
        val next = synchronized(this) {
            val previous = tail
            scope.launch(start = CoroutineStart.LAZY) {
                previous?.join()
                write()
            }.also { tail = it }
        }
        next.start()
    }

    suspend fun awaitIdle() {
        while (true) {
            val observed = synchronized(this) { tail } ?: return
            observed.join()
            if (synchronized(this) { tail === observed }) return
        }
    }
}
