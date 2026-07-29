package com.kinogo.atv.player.ui

/**
 * Focus nodes inside a newly composed LazyRow may not be attached during the first effect pass.
 * Retry on the next frame instead of silently leaving focus on the player root.
 */
internal suspend fun requestHudFocusWithRetry(
    maxAttempts: Int = 3,
    requestFocus: () -> Boolean,
    awaitNextAttempt: suspend () -> Unit,
): Boolean {
    require(maxAttempts > 0)
    repeat(maxAttempts) { attempt ->
        if (runCatching(requestFocus).getOrDefault(false)) return true
        if (attempt < maxAttempts - 1) awaitNextAttempt()
    }
    return false
}
