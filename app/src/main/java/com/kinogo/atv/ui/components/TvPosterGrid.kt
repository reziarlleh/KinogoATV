package com.kinogo.atv.ui.components

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.kinogo.atv.ui.model.PosterUiModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Deterministic result of one D-pad direction inside a poster grid. */
internal sealed interface PosterGridNavigationDecision {
    data class Move(val targetIndex: Int) : PosterGridNavigationDecision

    data object Exit : PosterGridNavigationDecision

    data object Stay : PosterGridNavigationDecision
}

internal data class PosterLongPressDecision(
    val consume: Boolean,
    val invokeLongClick: Boolean,
    val longPressHandled: Boolean,
)

enum class PosterLongClickOrigin {
    POINTER_OR_SEMANTICS,
    REMOTE_KEY_REPEAT,
}

/** Handles repeat-based long OK/Enter consistently on TV remotes. */
internal fun posterLongPressDecision(
    key: Key,
    type: KeyEventType,
    repeatCount: Int,
    alreadyHandled: Boolean,
): PosterLongPressDecision {
    if (!key.isPosterActivationKey()) {
        return PosterLongPressDecision(false, false, alreadyHandled)
    }
    return when {
        type == KeyEventType.KeyDown && repeatCount > 0 && !alreadyHandled ->
            PosterLongPressDecision(true, true, true)
        type == KeyEventType.KeyDown && alreadyHandled ->
            PosterLongPressDecision(true, false, true)
        type == KeyEventType.KeyUp && alreadyHandled ->
            PosterLongPressDecision(true, false, false)
        else -> PosterLongPressDecision(false, false, alreadyHandled)
    }
}

/**
 * Resolves poster navigation without relying on Compose's geometric focus search.
 *
 * [Exit] deliberately leaves the key event unconsumed so focus can move to controls above the
 * first row or to the navigation rail from the first column. [Stay] consumes a directional event
 * and prevents wrapping to another row or escaping from an incomplete final row.
 */
internal fun posterGridNavigationDecision(
    index: Int,
    itemCount: Int,
    columns: Int,
    key: Key,
): PosterGridNavigationDecision {
    if (columns <= 0 || index !in 0 until itemCount) {
        return PosterGridNavigationDecision.Stay
    }

    return when (key) {
        Key.DirectionLeft -> if (index % columns == 0) {
            PosterGridNavigationDecision.Exit
        } else {
            PosterGridNavigationDecision.Move(index - 1)
        }

        Key.DirectionRight -> if (
            index % columns == columns - 1 ||
            index + 1 >= itemCount
        ) {
            PosterGridNavigationDecision.Stay
        } else {
            PosterGridNavigationDecision.Move(index + 1)
        }

        Key.DirectionUp -> if (index < columns) {
            PosterGridNavigationDecision.Exit
        } else {
            PosterGridNavigationDecision.Move(index - columns)
        }

        Key.DirectionDown -> if (index + columns < itemCount) {
            PosterGridNavigationDecision.Move(index + columns)
        } else {
            PosterGridNavigationDecision.Stay
        }

        else -> PosterGridNavigationDecision.Stay
    }
}

/** True when fewer than [preloadRows] fully loaded rows remain below the focused row. */
internal fun shouldPreloadPosterGrid(
    focusedIndex: Int,
    itemCount: Int,
    columns: Int,
    preloadRows: Int = 2,
): Boolean {
    if (
        columns <= 0 ||
        preloadRows <= 0 ||
        focusedIndex !in 0 until itemCount
    ) {
        return false
    }
    val focusedRow = focusedIndex / columns
    val lastLoadedRow = (itemCount - 1) / columns
    return lastLoadedRow - focusedRow < preloadRows
}

/** Keeps the same visual row when a one-row D-pad move crosses the viewport boundary. */
internal fun posterGridScrollAnchor(
    firstVisibleItemIndex: Int,
    currentIndex: Int,
    targetIndex: Int,
    itemCount: Int,
    columns: Int,
): Int {
    if (
        columns <= 0 ||
        itemCount <= 0 ||
        currentIndex !in 0 until itemCount ||
        targetIndex !in 0 until itemCount
    ) {
        return 0
    }
    val firstVisibleRowStart =
        (firstVisibleItemIndex.coerceIn(0, itemCount - 1) / columns) * columns
    val adjacentRowStart = if (targetIndex > currentIndex) {
        firstVisibleRowStart + columns
    } else {
        firstVisibleRowStart - columns
    }
    val finalRowStart = ((itemCount - 1) / columns) * columns
    return adjacentRowStart.coerceIn(0, finalRowStart)
}

/** An append must not cancel an in-flight D-pad move to a card that is still present. */
internal fun shouldCancelPosterGridFocusMove(
    previousPagingKey: Any?,
    pagingKey: Any?,
    targetId: String?,
    itemIds: List<String>,
): Boolean = previousPagingKey != pagingKey || (targetId?.let { it !in itemIds } == true)

internal fun ownsPosterGridFocusMove(
    activeGeneration: Long,
    moveGeneration: Long,
): Boolean = activeGeneration == moveGeneration

/** Restores focus by stable content identity rather than by a stale numeric grid position. */
internal fun restoredPosterGridFocusIndex(
    preferredItemId: String?,
    itemIds: List<String>,
    requested: Boolean,
): Int? = if (requested && preferredItemId != null) {
    itemIds.indexOf(preferredItemId).takeIf { it >= 0 }
} else {
    null
}

/** Lets a recreated origin screen fall back to its first poster only when no stable target exists. */
internal fun shouldRequestFirstPosterFocus(
    requestInitialFocus: Boolean,
    preferredItemId: String?,
    itemIds: List<String>,
): Boolean = requestInitialFocus && restoredPosterGridFocusIndex(
    preferredItemId = preferredItemId,
    itemIds = itemIds,
    requested = true,
) == null

/**
 * Shared six-column TV poster grid with stable identity, deterministic D-pad movement and early
 * page preloading. The component only attaches [firstFocus]; it never requests initial focus on
 * its own, so appending cards cannot steal focus from the current poster or surrounding controls.
 */
@Composable
fun TvPosterGrid(
    items: List<PosterUiModel>,
    onOpenDetails: (String) -> Unit,
    emptyTitle: String,
    emptyDescription: String,
    hasMore: Boolean,
    onNearEnd: () -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
    columns: Int = PosterGridColumnCount,
    preloadRows: Int = 2,
    pagingKey: Any? = null,
    preferredFocusItemId: String? = null,
    requestPreferredFocus: Boolean = false,
    onFocusedItemChanged: (String) -> Unit = {},
    onItemLongClick: ((String, PosterLongClickOrigin) -> Unit)? = null,
    itemLongClickLabel: String = "Дополнительные действия",
) {
    require(columns > 0) { "Poster grid column count must be positive" }
    require(preloadRows > 0) { "Poster grid preload row count must be positive" }

    val gridState = key(pagingKey) { rememberLazyGridState() }
    val scope = rememberCoroutineScope()
    val focusRequestersById = remember { mutableMapOf<String, FocusRequester>() }
    val itemIds = remember(items) { items.map(PosterUiModel::id) }
    var focusMoveJob by remember { mutableStateOf<Job?>(null) }
    var focusMoveTargetId by remember { mutableStateOf<String?>(null) }
    var focusMoveGeneration by remember { mutableLongStateOf(0L) }
    var previousPagingKey by remember { mutableStateOf(pagingKey) }
    LaunchedEffect(itemIds, pagingKey) {
        if (
            shouldCancelPosterGridFocusMove(
                previousPagingKey = previousPagingKey,
                pagingKey = pagingKey,
                targetId = focusMoveTargetId,
                itemIds = itemIds,
            )
        ) {
            focusMoveGeneration++
            focusMoveJob?.cancel()
            focusMoveJob = null
            focusMoveTargetId = null
        }
        previousPagingKey = pagingKey
    }

    var requestedPreloadBoundary by remember { mutableStateOf<PosterGridPreloadBoundary?>(null) }
    val preloadBoundary = remember(itemIds, pagingKey) {
        PosterGridPreloadBoundary(
            pagingKey = pagingKey,
            firstItemId = itemIds.firstOrNull(),
            lastItemId = itemIds.lastOrNull(),
            itemCount = itemIds.size,
        )
    }
    LaunchedEffect(
        requestPreferredFocus,
        preferredFocusItemId,
        itemIds,
        pagingKey,
    ) {
        val targetId = preferredFocusItemId
        val targetIndex = restoredPosterGridFocusIndex(
            preferredItemId = targetId,
            itemIds = itemIds,
            requested = requestPreferredFocus,
        )
        if (targetId == null || targetIndex == null) {
            return@LaunchedEffect
        }
        val targetVisible = gridState.layoutInfo.visibleItemsInfo.any { it.key == targetId }
        if (!targetVisible) {
            gridState.scrollToItem((targetIndex / columns) * columns)
        }
        repeat(8) {
            withFrameNanos { }
            val requester = focusRequestersById[targetId]
            if (requester != null && runCatching { requester.requestFocus() }.getOrDefault(false)) {
                return@LaunchedEffect
            }
        }
    }

    if (items.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(title = emptyTitle, description = emptyDescription)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 2.dp, top = 3.dp, end = 5.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(
            items = items,
            key = { _, item -> item.id },
        ) { index, item ->
            val itemRequester = remember(item.id) { FocusRequester() }
            var remoteLongPressHandled by remember(item.id) { mutableStateOf(false) }
            var lastLongClickUptimeMs by remember(item.id) {
                mutableLongStateOf(-LONG_CLICK_DEDUPLICATION_MS)
            }
            fun dispatchLongClick(origin: PosterLongClickOrigin) {
                val callback = onItemLongClick ?: return
                val now = SystemClock.uptimeMillis()
                if (now - lastLongClickUptimeMs >= LONG_CLICK_DEDUPLICATION_MS) {
                    lastLongClickUptimeMs = now
                    callback(item.id, origin)
                }
            }
            val effectiveRequester = if (index == 0) firstFocus ?: itemRequester else itemRequester
            DisposableEffect(item.id, effectiveRequester) {
                focusRequestersById[item.id] = effectiveRequester
                onDispose {
                    if (focusRequestersById[item.id] === effectiveRequester) {
                        focusRequestersById.remove(item.id)
                    }
                }
            }

            PosterCard(
                item = item,
                onClick = { onOpenDetails(item.id) },
                onLongClick = onItemLongClick?.let {
                    { dispatchLongClick(PosterLongClickOrigin.POINTER_OR_SEMANTICS) }
                },
                onLongClickLabel = itemLongClickLabel,
                focusRequester = effectiveRequester,
                onFocused = {
                    onFocusedItemChanged(item.id)
                    if (
                        hasMore &&
                        requestedPreloadBoundary != preloadBoundary &&
                        shouldPreloadPosterGrid(
                            focusedIndex = index,
                            itemCount = items.size,
                            columns = columns,
                            preloadRows = preloadRows,
                        )
                    ) {
                        requestedPreloadBoundary = preloadBoundary
                        onNearEnd()
                    }
                },
                modifier = Modifier
                    .onFocusChanged {
                        if (!it.isFocused) remoteLongPressHandled = false
                    }
                    .onPreviewKeyEvent { event ->
                        if (onItemLongClick != null && event.key.isPosterActivationKey()) {
                            val longPress = posterLongPressDecision(
                                key = event.key,
                                type = event.type,
                                repeatCount = event.nativeKeyEvent.repeatCount,
                                alreadyHandled = remoteLongPressHandled,
                            )
                            remoteLongPressHandled = longPress.longPressHandled
                            if (longPress.invokeLongClick) {
                                dispatchLongClick(PosterLongClickOrigin.REMOTE_KEY_REPEAT)
                            }
                            if (longPress.consume) return@onPreviewKeyEvent true
                        }
                        if (event.type != KeyEventType.KeyDown || !event.key.isGridDirection()) {
                            return@onPreviewKeyEvent false
                        }
                        when (
                        val decision = posterGridNavigationDecision(
                            index = index,
                            itemCount = items.size,
                            columns = columns,
                            key = event.key,
                        )
                    ) {
                        PosterGridNavigationDecision.Exit -> false
                        PosterGridNavigationDecision.Stay -> true
                        is PosterGridNavigationDecision.Move -> {
                            val target = items.getOrNull(decision.targetIndex)
                                ?: return@onPreviewKeyEvent true
                            focusMoveJob?.cancel()
                            focusMoveGeneration++
                            val moveGeneration = focusMoveGeneration
                            focusMoveTargetId = target.id
                            focusMoveJob = scope.launch {
                                try {
                                    val targetIsVisible = gridState.layoutInfo.visibleItemsInfo
                                        .any { it.key == target.id }
                                    if (!targetIsVisible) {
                                        val nextRowStart = posterGridScrollAnchor(
                                            firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                                            currentIndex = index,
                                            targetIndex = decision.targetIndex,
                                            itemCount = items.size,
                                            columns = columns,
                                        )
                                        gridState.scrollToItem(nextRowStart)
                                        withTimeoutOrNull(1_000L) {
                                            snapshotFlow {
                                                gridState.layoutInfo.visibleItemsInfo.any {
                                                    it.key == target.id
                                                }
                                            }.first { it }
                                        }
                                        withFrameNanos { }
                                    }
                                    if (
                                        gridState.layoutInfo.visibleItemsInfo.any {
                                            it.key == target.id
                                        }
                                    ) {
                                        val targetRequester = if (
                                            decision.targetIndex == 0 && firstFocus != null
                                        ) {
                                            firstFocus
                                        } else {
                                            focusRequestersById[target.id]
                                        }
                                        runCatching { targetRequester?.requestFocus() }
                                    }
                                } finally {
                                    if (
                                        ownsPosterGridFocusMove(
                                            activeGeneration = focusMoveGeneration,
                                            moveGeneration = moveGeneration,
                                        )
                                    ) {
                                        focusMoveTargetId = null
                                        focusMoveJob = null
                                    }
                                }
                            }
                            true
                        }
                    }
                },
            )
        }
    }
}

private data class PosterGridPreloadBoundary(
    val pagingKey: Any?,
    val firstItemId: String?,
    val lastItemId: String?,
    val itemCount: Int,
)

private fun Key.isGridDirection(): Boolean =
    this == Key.DirectionLeft ||
        this == Key.DirectionRight ||
        this == Key.DirectionUp ||
        this == Key.DirectionDown

private fun Key.isPosterActivationKey(): Boolean =
    this == Key.DirectionCenter ||
        this == Key.Enter ||
        this == Key.NumPadEnter

private const val LONG_CLICK_DEDUPLICATION_MS = 400L
