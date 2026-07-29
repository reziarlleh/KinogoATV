package com.kinogo.atv.domain

/**
 * Converts a TV grid's row-based UX rule into Paging's item-based prefetch distance.
 *
 * With the default of two rows, a 5-column grid requests more data as focus enters the
 * penultimate loaded row. Duplicate requests are suppressed while append is active or after EOF.
 */
data class PrefetchPolicy(
    val rowsBeforeEnd: Int = 2,
) {
    init {
        require(rowsBeforeEnd > 0)
    }

    fun pagingPrefetchDistance(columns: Int): Int {
        require(columns > 0)
        return rowsBeforeEnd * columns
    }

    fun shouldRequestNextPage(
        anchorItemIndex: Int,
        loadedItemCount: Int,
        columns: Int,
        appendInProgress: Boolean = false,
        endReached: Boolean = false,
    ): Boolean {
        require(columns > 0)

        if (appendInProgress || endReached || loadedItemCount <= 0) return false
        if (anchorItemIndex !in 0 until loadedItemCount) return false

        val anchorRow = anchorItemIndex / columns
        val lastLoadedRow = (loadedItemCount - 1) / columns
        val rowsAfterAnchor = lastLoadedRow - anchorRow
        return rowsAfterAnchor < rowsBeforeEnd
    }
}
