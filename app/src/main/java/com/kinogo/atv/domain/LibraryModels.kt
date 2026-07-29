package com.kinogo.atv.domain

enum class WatchStatus(
    val folder: String,
    val title: String,
) {
    WATCHING("watch", "Смотрю"),
    WATCHED("done", "Смотрел"),
    PLANNED("todo", "Буду"),
    DROPPED("drop", "Бросил"),
    ;

    companion object {
        fun fromFolder(value: String?): WatchStatus? = entries.firstOrNull { it.folder == value }
    }
}

data class LibraryRecord(
    val item: CatalogItem,
    val status: WatchStatus? = null,
    val favorite: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
)

enum class LibraryFilter(val title: String) {
    ALL("Все"),
    WATCHING("Смотрю"),
    WATCHED("Смотрел"),
    PLANNED("Буду"),
    DROPPED("Бросил"),
    FAVORITES("Избранное"),
    ;

    fun accepts(record: LibraryRecord): Boolean = when (this) {
        ALL -> record.favorite || record.status != null
        WATCHING -> record.status == WatchStatus.WATCHING
        WATCHED -> record.status == WatchStatus.WATCHED
        PLANNED -> record.status == WatchStatus.PLANNED
        DROPPED -> record.status == WatchStatus.DROPPED
        FAVORITES -> record.favorite
    }
}
