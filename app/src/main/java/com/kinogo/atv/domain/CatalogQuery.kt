package com.kinogo.atv.domain

/** Visual grouping used by the combined TV category dropdown. */
enum class CatalogCategoryGroup {
    MOVIES,
    SERIES,
}

/**
 * Exact category routes observed in the server-rendered Kinogo navigation.
 *
 * Routes are deliberately allowlisted. A mirror-provided arbitrary href must never become a
 * catalog request merely because it was present in HTML.
 */
enum class CatalogCategory(
    val title: String,
    val relativePath: String,
    val group: CatalogCategoryGroup,
) {
    ALL_MOVIES("Все фильмы", "/filmy/", CatalogCategoryGroup.MOVIES),
    CARTOONS("Мультфильмы", "/multfilmy/", CatalogCategoryGroup.MOVIES),
    NEW_RELEASES("Новинки", "/novinki/", CatalogCategoryGroup.MOVIES),
    SCIENCE_FICTION("Фантастика", "/fantastika/", CatalogCategoryGroup.MOVIES),
    FANTASY("Фэнтези", "/fjentezi/", CatalogCategoryGroup.MOVIES),
    NOIR("Нуар", "/nuar/", CatalogCategoryGroup.MOVIES),
    HORROR("Ужасы", "/uzhasy/", CatalogCategoryGroup.MOVIES),
    THRILLER("Триллер", "/triller/", CatalogCategoryGroup.MOVIES),
    SPORT("Спорт", "/sport/", CatalogCategoryGroup.MOVIES),
    ADVENTURE("Приключения", "/prikljuchenija/", CatalogCategoryGroup.MOVIES),
    HISTORICAL("Исторические", "/istoricheskie/", CatalogCategoryGroup.MOVIES),
    MUSICAL("Мюзикл", "/mjuzikl/", CatalogCategoryGroup.MOVIES),
    MELODRAMA("Мелодрама", "/melodrama/", CatalogCategoryGroup.MOVIES),
    SHORT_FILM("Короткометражка", "/korotkometrazhka/", CatalogCategoryGroup.MOVIES),
    CRIME("Криминал", "/kriminal/", CatalogCategoryGroup.MOVIES),
    DRAMA("Драма", "/drama/", CatalogCategoryGroup.MOVIES),
    COMEDY("Комедия", "/komedija/", CatalogCategoryGroup.MOVIES),
    DOCUMENTARY("Документальные", "/dokumentalnye/", CatalogCategoryGroup.MOVIES),
    DETECTIVE("Детектив", "/detektiv/", CatalogCategoryGroup.MOVIES),
    CHILDREN("Детский", "/detskij/", CatalogCategoryGroup.MOVIES),
    WAR("Военный", "/voennyj/", CatalogCategoryGroup.MOVIES),
    WESTERN("Вестерн", "/vestern/", CatalogCategoryGroup.MOVIES),

    ALL_SERIES("Все сериалы", "/serialy/", CatalogCategoryGroup.SERIES),
    FOREIGN_SERIES("Зарубежные", "/zarubezhnye-serialy/", CatalogCategoryGroup.SERIES),
    RUSSIAN_SERIES("Русские", "/russkie-serialy/", CatalogCategoryGroup.SERIES),
    ANIMATED_SERIES("Мультсериалы", "/multserialy/", CatalogCategoryGroup.SERIES),
    ANIME_SERIES("Аниме-сериалы", "/anime-serialy/", CatalogCategoryGroup.SERIES),
    ANIME("Аниме", "/anime/", CatalogCategoryGroup.SERIES),
    ;

    companion object {
        private val BY_PATH = entries.associateBy(CatalogCategory::relativePath)

        fun fromRelativePath(relativePath: String): CatalogCategory? = BY_PATH[relativePath]
    }
}

/** Values currently exposed by the HTML xSort `defaultsort` control. */
enum class CatalogDefaultSort(
    val wireValue: String,
    val fallbackTitle: String,
) {
    DATE("date", "по дате"),
    RATING("rating", "по рейтингу"),
    TOP_3_DAYS("views_top", "топ за 3 дня"),
    VIEWS("views", "по просмотрам"),
    COMMENTS("comm", "по комментариям"),
    YEAR("year", "по году"),
    KINOPOISK("kp", "по Кинопоиск`у"),
    ;

    companion object {
        private val BY_WIRE_VALUE = entries.associateBy(CatalogDefaultSort::wireValue)

        fun fromWireValue(value: String): CatalogDefaultSort? = BY_WIRE_VALUE[value]
    }
}

enum class CatalogSortDirection {
    DESC,
    ASC,
}

/** One server-provided xSort choice after strict parser validation. */
data class CatalogFilterOption(
    val value: String,
    val title: String,
) {
    init {
        require(value.isNotBlank()) { "Catalog filter value must not be blank" }
        require(value.length <= MAX_FILTER_VALUE_LENGTH) { "Catalog filter value is too long" }
        require(value.none(Char::isISOControl)) {
            "Catalog filter value must not contain control characters"
        }
        require(title.isNotBlank()) { "Catalog filter title must not be blank" }
        require(title.length <= MAX_FILTER_TITLE_LENGTH) { "Catalog filter title is too long" }
        require(title.none(Char::isISOControl)) {
            "Catalog filter title must not contain control characters"
        }
    }

    companion object {
        const val MAX_FILTER_VALUE_LENGTH = 96
        const val MAX_FILTER_TITLE_LENGTH = 96
    }
}

/** A sort option keeps the page-specific label; null means the site's default ordering. */
data class CatalogSortOption(
    val value: CatalogDefaultSort?,
    val title: String,
) {
    init {
        require(title.isNotBlank()) { "Catalog sort title must not be blank" }
        require(title.length <= CatalogFilterOption.MAX_FILTER_TITLE_LENGTH) {
            "Catalog sort title is too long"
        }
        require(title.none(Char::isISOControl)) {
            "Catalog sort title must not contain control characters"
        }
    }
}

/** All independently selectable controls that form one reproducible browse query. */
data class CatalogBrowseFilters(
    val defaultSort: CatalogDefaultSort? = null,
    val sortDirection: CatalogSortDirection = CatalogSortDirection.DESC,
    val collection: CatalogFilterOption? = null,
    val year: Int? = null,
    val country: CatalogFilterOption? = null,
) {
    init {
        require(year == null || year in MIN_CATALOG_YEAR..MAX_CATALOG_YEAR) {
            "Catalog year is outside the supported range"
        }
        require(defaultSort != null || sortDirection == CatalogSortDirection.DESC) {
            "Ascending direction requires an explicit server sort"
        }
    }

    val isEmpty: Boolean
        get() = defaultSort == null && collection == null && year == null && country == null

    companion object {
        const val MIN_CATALOG_YEAR = 1900
        const val MAX_CATALOG_YEAR = 2100
    }
}

/** Controls parsed from the concrete page plus the selection active in that server session. */
data class CatalogControls(
    val sortOptions: List<CatalogSortOption> = emptyList(),
    val collectionOptions: List<CatalogFilterOption> = emptyList(),
    val yearOptions: List<CatalogFilterOption> = emptyList(),
    val countryOptions: List<CatalogFilterOption> = emptyList(),
    val categories: List<CatalogCategory> = emptyList(),
    val activeFilters: CatalogBrowseFilters = CatalogBrowseFilters(),
)

/**
 * A single page of either a browse feed or text search.
 *
 * [category] is null for the site home route. Browse filters are an intersection maintained by
 * the server's xSort cookie session. Text search is a separate deterministic mode.
 */
data class CatalogQuery(
    val category: CatalogCategory? = null,
    val filters: CatalogBrowseFilters = CatalogBrowseFilters(),
    val searchTerm: String? = null,
    val page: Int = 1,
) {
    init {
        require(page > 0) { "Catalog page must be positive" }
        require(searchTerm == null || searchTerm.trim().isNotEmpty()) {
            "Search term must not be blank"
        }
        require(searchTerm == null || searchTerm.none(Char::isISOControl)) {
            "Search term must not contain control characters"
        }
        require(searchTerm == null || (category == null && filters.isEmpty)) {
            "Text search cannot be combined with browse category or filters"
        }
    }

    val normalizedSearchTerm: String?
        get() = searchTerm?.trim()?.replace(WHITESPACE, " ")

    /** Stable identity used by a paging generation; page itself is deliberately excluded. */
    val identity: CatalogQuery
        get() = copy(searchTerm = normalizedSearchTerm, page = 1)

    companion object {
        private val WHITESPACE = Regex("\\s+")

        fun search(term: String, page: Int = 1): CatalogQuery =
            CatalogQuery(searchTerm = term, page = page)
    }
}
