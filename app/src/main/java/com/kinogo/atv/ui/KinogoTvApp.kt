package com.kinogo.atv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kinogo.atv.ui.components.KinogoNavigationRail
import com.kinogo.atv.ui.components.TvActionButton
import com.kinogo.atv.ui.model.KinogoFixtures
import com.kinogo.atv.ui.model.BookmarkUiModel
import com.kinogo.atv.ui.model.AppUpdateUiModel
import com.kinogo.atv.ui.model.HistoryUiModel
import com.kinogo.atv.ui.model.MirrorUiState
import com.kinogo.atv.ui.model.PlaybackSelectionUiModel
import com.kinogo.atv.ui.model.PosterUiModel
import com.kinogo.atv.ui.model.RegistrationSubmissionUiInput
import com.kinogo.atv.ui.model.RegistrationUiModel
import com.kinogo.atv.ui.model.SettingSectionUiModel
import com.kinogo.atv.ui.model.TvDestination
import com.kinogo.atv.ui.screens.CatalogScreen
import com.kinogo.atv.ui.screens.AboutDialog
import com.kinogo.atv.ui.screens.DetailsScreen
import com.kinogo.atv.ui.screens.BookmarksScreen
import com.kinogo.atv.ui.screens.HistoryScreen
import com.kinogo.atv.ui.screens.HomeScreen
import com.kinogo.atv.ui.screens.SearchScreen
import com.kinogo.atv.ui.screens.SettingsScreen
import com.kinogo.atv.domain.CatalogBrowseFilters
import com.kinogo.atv.domain.CatalogCategory
import com.kinogo.atv.domain.CatalogControls
import com.kinogo.atv.domain.AccountConnectionState
import com.kinogo.atv.domain.LibraryFilter
import com.kinogo.atv.domain.VideoQualityPreference
import com.kinogo.atv.domain.WatchStatus

private val KinogoTvColors = darkColorScheme(
    primary = Color(0xFF43D7D2),
    onPrimary = Color(0xFF10272D),
    secondary = Color(0xFF62BFE9),
    onSecondary = Color(0xFF10242D),
    background = Color(0xFF304955),
    onBackground = Color(0xFFF5F7FA),
    surface = Color(0xFF385562),
    onSurface = Color(0xFFF5F7FA),
    surfaceVariant = Color(0xFF466773),
    onSurfaceVariant = Color(0xFFD1E0E6),
    outline = Color(0xFF5D7B87),
)

private val KinogoTvHighContrastColors = KinogoTvColors.copy(
    primary = Color(0xFF72FFF8),
    background = Color(0xFF101B20),
    surface = Color(0xFF1B3039),
    surfaceVariant = Color(0xFF284650),
    outline = Color.White,
)

@Composable
fun KinogoTvTheme(
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (highContrast) KinogoTvHighContrastColors else KinogoTvColors,
        content = content,
    )
}

/**
 * TV-only application shell. Domain models and playback implementation stay outside the UI layer.
 */
@Composable
fun KinogoTvApp(
    modifier: Modifier = Modifier,
    initialDestination: TvDestination = TvDestination.Home,
    initialDetailsId: String? = null,
    homeCatalog: List<PosterUiModel> = KinogoFixtures.catalog,
    history: List<HistoryUiModel> = KinogoFixtures.history,
    mirrorState: MirrorUiState = KinogoFixtures.mirrorState,
    catalog: List<com.kinogo.atv.ui.model.PosterUiModel> = KinogoFixtures.catalog,
    favorites: List<com.kinogo.atv.ui.model.PosterUiModel> = catalog.filter { it.isFavorite },
    bookmarks: List<BookmarkUiModel> = favorites.map { BookmarkUiModel(it, favorite = true) },
    favoriteIds: Set<String> = favorites.mapTo(linkedSetOf()) { it.id },
    watchStatusById: Map<String, WatchStatus> = emptyMap(),
    detailsById: Map<String, com.kinogo.atv.ui.model.DetailsUiModel> =
        KinogoFixtures.details.associateBy { it.id },
    catalogHasMore: Boolean = false,
    catalogLoading: Boolean = false,
    catalogError: String? = null,
    catalogControls: CatalogControls = CatalogControls(),
    catalogCategory: CatalogCategory = CatalogCategory.NEW_RELEASES,
    catalogFilters: CatalogBrowseFilters = CatalogBrowseFilters(),
    homeHasMore: Boolean = false,
    homeLoading: Boolean = false,
    homeError: String? = null,
    homeControls: CatalogControls = CatalogControls(),
    homeFilters: CatalogBrowseFilters = CatalogBrowseFilters(),
    catalogStatusLabel: String? = null,
    searchResults: List<com.kinogo.atv.ui.model.PosterUiModel> = catalog,
    searchLoading: Boolean = false,
    searchError: String? = null,
    searchHasMore: Boolean = false,
    searchQuery: String = "",
    recentSearchQueries: List<String> = emptyList(),
    searchFocusedItemId: String? = null,
    searchResultsQuery: String? = null,
    homeFocusedItemId: String? = null,
    catalogFocusedItemId: String? = null,
    bookmarksFocusedItemId: String? = null,
    historyFocusedItemId: String? = null,
    bookmarksFilter: LibraryFilter = LibraryFilter.ALL,
    useRemoteCatalog: Boolean = false,
    onPlayRequested: (PlaybackSelectionUiModel) -> Unit = {},
    onCatalogLoadMore: () -> Unit = {},
    onCatalogRetry: () -> Unit = {},
    onHomeRetry: () -> Unit = {},
    onHomeLoadMore: () -> Unit = {},
    onHomeFiltersChanged: (CatalogBrowseFilters) -> Unit = {},
    onDestinationChanged: (TvDestination) -> Unit = {},
    onDetailsRequested: (String) -> Unit = {},
    onCatalogCategorySelected: (CatalogCategory) -> Unit = {},
    onCatalogFiltersChanged: (CatalogBrowseFilters) -> Unit = {},
    onSearchInputChanged: (String) -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onSearchCommitted: (String) -> Unit = {},
    onSearchFocusedItemChanged: (String) -> Unit = {},
    onHomeFocusedItemChanged: (String) -> Unit = {},
    onCatalogFocusedItemChanged: (String) -> Unit = {},
    onBookmarksFocusedItemChanged: (String) -> Unit = {},
    onHistoryFocusedItemChanged: (String) -> Unit = {},
    onBookmarksFilterSelected: (LibraryFilter) -> Unit = {},
    onSearchLoadMore: () -> Unit = {},
    onFavoriteToggle: (String) -> Unit = {},
    onWatchStatusChange: (String, WatchStatus?) -> Unit = { _, _ -> },
    onCheckMirrors: () -> Unit = {},
    onManualMirrorSubmitted: (String) -> Unit = {},
    onMirrorSelected: (String) -> Unit = {},
    onMirrorRetry: (String) -> Unit = {},
    onHistoryResume: ((String) -> Unit)? = null,
    accountState: AccountConnectionState = AccountConnectionState(),
    pendingSyncCount: Int = 0,
    syncMessage: String? = null,
    onAccountLogin: (String, String) -> Unit = { _, _ -> },
    onAccountReconnect: () -> Unit = {},
    onAccountRemove: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    registrationState: RegistrationUiModel? = null,
    onRegistrationOpen: () -> Unit = {},
    onRegistrationDismiss: () -> Unit = {},
    onRegistrationRetry: () -> Unit = {},
    onRegistrationAcceptRules: () -> Unit = {},
    onRegistrationRefreshCaptcha: () -> Unit = {},
    onRegistrationSubmit: (RegistrationSubmissionUiInput) -> Unit = {},
    appUpdate: AppUpdateUiModel = AppUpdateUiModel(currentVersion = "—"),
    onUpdateAction: () -> Unit = {},
    appVersionName: String = "—",
    onDonateOpen: () -> Unit = {},
    onRepositoryOpen: () -> Unit = {},
    settingsSections: List<SettingSectionUiModel> = KinogoFixtures.settings,
    highContrast: Boolean = false,
    reduceMotion: Boolean = false,
    onSettingSelected: (String, String) -> Unit = { _, _ -> },
    defaultQuality: VideoQualityPreference = VideoQualityPreference.AUTO,
    onExitConfirmed: () -> Unit = {},
) {
    var destinationName by rememberSaveable { mutableStateOf(initialDestination.name) }
    var selectedDetailsId by rememberSaveable { mutableStateOf(initialDetailsId) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var suppressInitialContentFocus by remember {
        mutableStateOf(initialDetailsId == null)
    }
    val destination = remember(destinationName, initialDestination) {
        restoredTvDestination(destinationName, initialDestination)
    }
    LaunchedEffect(destination) {
        onDestinationChanged(destination)
    }
    val allCatalogPosters = remember(homeCatalog, catalog) {
        (homeCatalog + catalog).distinctBy(PosterUiModel::id)
    }
    val selectedDetails = selectedDetailsId?.let { id ->
        detailsById[id]
            ?: pendingDetailsPoster(
                id = id,
                catalog = allCatalogPosters,
                searchResults = searchResults,
                favorites = favorites,
                history = history,
            )
                ?.toPendingDetails()
    }

    fun openDetails(id: String) {
        suppressInitialContentFocus = false
        selectedDetailsId = id
        onDetailsRequested(id)
    }

    BackHandler {
        when (
            rootBackAction(
                hasOpenDetails = selectedDetails != null,
                exitConfirmationVisible = showExitConfirmation,
            )
        ) {
            RootBackAction.CLOSE_DETAILS -> selectedDetailsId = null
            RootBackAction.SHOW_EXIT_CONFIRMATION -> showExitConfirmation = true
            RootBackAction.DISMISS_EXIT_CONFIRMATION -> showExitConfirmation = false
        }
    }

    KinogoTvTheme(highContrast = highContrast) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF17262E)),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                KinogoNavigationRail(
                    selected = destination,
                    onAboutRequested = { showAboutDialog = true },
                    onSelected = {
                        suppressInitialContentFocus = false
                        selectedDetailsId = null
                        destinationName = it.name
                    },
                    modifier = Modifier.fillMaxHeight(),
                    requestInitialFocus = suppressInitialContentFocus,
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF304955))
                        .padding(start = 14.dp, top = 10.dp, end = 10.dp, bottom = 6.dp),
                ) {
                    if (selectedDetails != null) {
                        DetailsScreen(
                            details = selectedDetails,
                            onBack = { selectedDetailsId = null },
                            onPlay = onPlayRequested,
                            isFavorite = selectedDetails.id in favoriteIds,
                            onFavoriteToggle = { onFavoriteToggle(selectedDetails.id) },
                            watchStatus = watchStatusById[selectedDetails.id],
                            onWatchStatusChange = { status ->
                                onWatchStatusChange(selectedDetails.id, status)
                            },
                            defaultQuality = defaultQuality,
                        )
                    } else {
                        when (destination) {
                            TvDestination.Home -> HomeScreen(
                                items = homeCatalog,
                                controls = homeControls,
                                filters = homeFilters,
                                hasMore = homeHasMore,
                                onLoadMore = onHomeLoadMore,
                                onFiltersChanged = onHomeFiltersChanged,
                                onOpenDetails = ::openDetails,
                                isLoading = homeLoading,
                                errorMessage = homeError,
                                onRetry = onHomeRetry,
                                requestInitialFocus = !suppressInitialContentFocus,
                                lastFocusedItemId = homeFocusedItemId,
                                onFocusedItemChanged = onHomeFocusedItemChanged,
                            )

                            TvDestination.Catalog -> CatalogScreen(
                                items = catalog,
                                hasMore = catalogHasMore,
                                onLoadMore = onCatalogLoadMore,
                                onOpenDetails = ::openDetails,
                                isLoading = catalogLoading,
                                errorMessage = catalogError,
                                statusLabel = catalogStatusLabel,
                                onRetry = onCatalogRetry,
                                controls = catalogControls,
                                selectedCategory = catalogCategory,
                                filters = catalogFilters,
                                onCategorySelected = onCatalogCategorySelected,
                                onFiltersChanged = onCatalogFiltersChanged,
                                requestInitialFocus = !suppressInitialContentFocus,
                                lastFocusedItemId = catalogFocusedItemId,
                                onFocusedItemChanged = onCatalogFocusedItemChanged,
                            )

                            TvDestination.Search -> SearchScreen(
                                catalog = searchResults,
                                query = searchQuery,
                                recentQueries = recentSearchQueries,
                                lastFocusedResultId = searchFocusedItemId,
                                resultsQuery = searchResultsQuery,
                                onOpenDetails = ::openDetails,
                                useRemoteResults = useRemoteCatalog,
                                isLoading = searchLoading,
                                errorMessage = searchError,
                                onInputChanged = onSearchInputChanged,
                                onQueryChanged = onSearchQueryChanged,
                                onQueryCommitted = onSearchCommitted,
                                onFocusedResultChanged = onSearchFocusedItemChanged,
                                hasMore = searchHasMore,
                                onLoadMore = onSearchLoadMore,
                                requestInitialFocus = !suppressInitialContentFocus,
                            )

                            TvDestination.Favorites -> BookmarksScreen(
                                bookmarks = bookmarks,
                                onOpenDetails = ::openDetails,
                                selectedFilter = bookmarksFilter,
                                onFilterSelected = onBookmarksFilterSelected,
                                requestInitialFocus = !suppressInitialContentFocus,
                                lastFocusedItemId = bookmarksFocusedItemId,
                                onFocusedItemChanged = onBookmarksFocusedItemChanged,
                            )

                            TvDestination.History -> HistoryScreen(
                                history = history,
                                onResume = { contentId ->
                                    onHistoryResume?.invoke(contentId)
                                        ?: run { selectedDetailsId = contentId }
                                },
                                requestInitialFocus = !suppressInitialContentFocus,
                                lastFocusedItemId = historyFocusedItemId,
                                onFocusedItemChanged = onHistoryFocusedItemChanged,
                            )

                            TvDestination.Settings -> SettingsScreen(
                                sections = settingsSections,
                                mirrorState = mirrorState,
                                onCheckMirrors = onCheckMirrors,
                                onManualMirrorSubmitted = onManualMirrorSubmitted,
                                onMirrorSelected = onMirrorSelected,
                                onMirrorRetry = onMirrorRetry,
                                accountState = accountState,
                                pendingSyncCount = pendingSyncCount,
                                syncMessage = syncMessage,
                                onAccountLogin = onAccountLogin,
                                onAccountReconnect = onAccountReconnect,
                                onAccountRemove = onAccountRemove,
                                onSyncNow = onSyncNow,
                                registrationState = registrationState,
                                onRegistrationOpen = onRegistrationOpen,
                                onRegistrationDismiss = onRegistrationDismiss,
                                onRegistrationRetry = onRegistrationRetry,
                                onRegistrationAcceptRules = onRegistrationAcceptRules,
                                onRegistrationRefreshCaptcha = onRegistrationRefreshCaptcha,
                                onRegistrationSubmit = onRegistrationSubmit,
                                appUpdate = appUpdate,
                                onUpdateAction = onUpdateAction,
                                onAboutOpen = { showAboutDialog = true },
                                onSettingSelected = onSettingSelected,
                                reduceMotion = reduceMotion,
                                requestInitialFocus = !suppressInitialContentFocus,
                            )
                        }
                    }
                }
            }

            if (showExitConfirmation) {
                ExitConfirmationDialog(
                    onStay = { showExitConfirmation = false },
                    onExit = onExitConfirmed,
                )
            }
            if (showAboutDialog) {
                AboutDialog(
                    versionName = appVersionName,
                    onDonate = onDonateOpen,
                    onRepository = onRepositoryOpen,
                    onDismiss = { showAboutDialog = false },
                )
            }
        }
    }
}

internal enum class RootBackAction {
    CLOSE_DETAILS,
    SHOW_EXIT_CONFIRMATION,
    DISMISS_EXIT_CONFIRMATION,
}

internal fun rootBackAction(
    hasOpenDetails: Boolean,
    exitConfirmationVisible: Boolean,
): RootBackAction = when {
    exitConfirmationVisible -> RootBackAction.DISMISS_EXIT_CONFIRMATION
    hasOpenDetails -> RootBackAction.CLOSE_DETAILS
    else -> RootBackAction.SHOW_EXIT_CONFIRMATION
}

@Composable
private fun ExitConfirmationDialog(
    onStay: () -> Unit,
    onExit: () -> Unit,
) {
    val stayFocus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onStay,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onStay)
        LaunchedEffect(Unit) { stayFocus.requestFocus() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.74f))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(560.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF213842),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shadowElevation = 28.dp,
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Выйти из KinogoATV?",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "Случайное нажатие «Назад» больше не закроет приложение.",
                        color = Color(0xFFD0DEE4),
                        fontSize = 15.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvActionButton(
                            text = "Остаться",
                            onClick = onStay,
                            modifier = Modifier.focusRequester(stayFocus),
                            primary = true,
                        )
                        Spacer(Modifier.width(12.dp))
                        TvActionButton(
                            text = "Выйти",
                            onClick = onExit,
                        )
                    }
                }
            }
        }
    }
}

internal fun restoredTvDestination(
    savedName: String,
    fallback: TvDestination = TvDestination.Home,
): TvDestination = TvDestination.entries.firstOrNull { it.name == savedName } ?: fallback

internal fun pendingDetailsPoster(
    id: String,
    catalog: List<PosterUiModel>,
    searchResults: List<PosterUiModel>,
    favorites: List<PosterUiModel>,
    history: List<HistoryUiModel>,
): PosterUiModel? =
    catalog.firstOrNull { it.id == id }
        ?: searchResults.firstOrNull { it.id == id }
        ?: favorites.firstOrNull { it.id == id }
        ?: history.firstOrNull { it.poster.id == id }?.poster

private fun PosterUiModel.toPendingDetails() =
    com.kinogo.atv.ui.model.DetailsUiModel(
        id = id,
        title = title,
        originalTitle = "",
        summary = "Карточка загружается с выбранного зеркала.",
        metadata = subtitle,
        rating = "",
        accentArgb = accentArgb,
        voiceovers = emptyList(),
        qualities = emptyList(),
        resumeLabel = "Источник не готов",
        playbackAvailable = false,
        statusMessage = "Подождите завершения загрузки данных",
    )
