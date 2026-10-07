package com.kinogo.atv.data.history

import com.kinogo.atv.domain.CatalogItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/** Fresh Kinogo card reads only; never writes or changes playback checkpoints. */
class HistoryMetadataRefresher(
    private val load: suspend (CatalogItem) -> CatalogItem,
    private val concurrency: Int = 2,
    private val timeoutMs: Long = 20_000L,
) {
    init {
        require(concurrency > 0)
        require(timeoutMs > 0)
    }

    /** A null result marks a failed read, not an empty or confirmed-up-to-date series. */
    suspend fun refresh(
        items: List<CatalogItem>,
        onResult: (contentId: String, fresh: CatalogItem?) -> Unit,
    ) = coroutineScope {
        val permits = Semaphore(concurrency)
        items.distinctBy(CatalogItem::id).forEach { item ->
            launch {
                permits.withPermit {
                    val fresh = withTimeoutOrNull(timeoutMs) {
                        try {
                            load(item).takeIf { it.id == item.id }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                    }
                    coroutineContext.ensureActive()
                    onResult(item.id, fresh)
                }
            }
        }
    }
}
