package com.kinogo.atv.ui.mapper

import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.ContentType

/** Never show a persisted season/episode label as a fresh answer about new episodes. */
internal fun historyEpisodeBadge(
    cached: CatalogItem?,
    fresh: CatalogItem?,
    checked: Boolean,
    episodicProgress: Boolean,
): String? {
    if (fresh != null) return fresh.episodeBadge?.trim()?.takeIf(String::isNotEmpty)
    val series = episodicProgress || cached?.type == ContentType.SERIES ||
        !cached?.episodeBadge.isNullOrBlank()
    if (!series) return null
    return if (checked) "Серии не проверены" else "Проверяем серии…"
}
