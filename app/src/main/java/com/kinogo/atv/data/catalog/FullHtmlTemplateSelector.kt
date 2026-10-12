package com.kinogo.atv.data.catalog

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Negotiate the DLE template once per origin/cookie clear, without rewriting content routes. */
internal class FullHtmlTemplateSelector {
    private val mutex = Mutex()
    private val selectedGenerations = mutableMapOf<String, Long>()

    suspend fun ensure(origin: String, clearGeneration: () -> Long, select: suspend () -> Unit) {
        mutex.withLock {
            val generation = clearGeneration()
            if (selectedGenerations[origin] == generation) return
            select()
            if (clearGeneration() != generation) throw CatalogSessionChangedException()
            selectedGenerations[origin] = generation
        }
    }
}
