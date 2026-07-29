package com.kinogo.atv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kinogo.atv.ui.components.KinogoNavigationRail
import com.kinogo.atv.ui.components.RailContentOffset
import com.kinogo.atv.ui.components.TvActionButton
import com.kinogo.atv.ui.model.KinogoFixtures
import com.kinogo.atv.ui.model.BookmarkUiModel
import com.kinogo.atv.ui.model.HistoryUiModel
import com.kinogo.atv.ui.model.HomeSectionUiModel
import com.kinogo.atv.ui.model.MirrorUiState
import com.kinogo.atv.ui.model.PlaybackSelectionUiModel
import com.kinogo.atv.ui.model.PosterUiModel
import com.kinogo.atv.ui.model.SettingSectionUiModel
import com.kinogo.atv.ui.model.TvDestination
import com.kinogo.atv.ui.screens.CatalogScreen
import com.kinogo.atv.ui.screens.DetailsScreen
import com.kinogo.atv.ui.screens.BookmarksScreen
import com.kinogo.atv.ui.screens.HistoryScreen
import com.kinogo.atv.ui.screens.HomeScreen
import com.kinogo.atv.ui.screens.SearchScreen
import com.kinogo.atv.ui.screens.SettingsScreen
import com.kinogo.atv.domain.CatalogSection
import com.kinogo.atv.domain.AccountConnectionState
import com.kinogo.atv.domain.SettingCycleDirection
import com.kinogo.atv.domain.VideoQualityPreference
import com.kinogo.atv.domain.WatchStatus

private val KinogoTvColors = darkColorScheme(
    primary = Color(0xFFF4C542),
    onPrimary = Color(0xFF111318),
    background = Color(0xFF080C13),
    onBackground = Color(0xFFF5F7FA),
    surface = Color(0xFF151D2A),
    onSurface = Color(0xFFF5F7FA),
    surfaceVariant = Color(0xFF222D40),
    onSurfaceVariant = Color(0xFFB9C4D4),
    outline = Color(0xFF3A465A),
)

private val KinogoTvHighContrastColors = KinogoTvColors.copy(
    primary = Color(0xFFFFE05D),
    background = Color.Black,
    surface = Color(0xFF0C111A),
    surfaceVariant = Color(0xFF1A2536),
    outline = Color.White,
)

/**
 * TV-only application shell. Domain models and playback implementation stay outside the UI layer.
 */
@Composable
fun KinogoTvApp(
    modifier: Modifier = Modifier,
    initialDestination: TvDestination = TvDestination.Home,
    initialDetailsId: String? = null,
    homeSections: List<HomeSectionUiModel> = KinogoFixtures.homeSections,
    history: List<HistoryUiModel> = KinogoFixtures.history,
    mirrorState: MirrorUiState = KinogoFixtures.mirrorState,
    catalog: List<com.kinogo.atv.ui.model.PosterUiModel> = KinogoFixtures.catalog,
    favorites: List<com.kinogo.atv.ui.model.PosterUiModel> = catalog.filter { it.isFavorite },
    bookmarks: List<BookmarkUiModel> = favorites.map { BookmarkUiModel(it, favorite = true) },
    favoriteIds: Set<String> = favorites.mapTo(linkedSetOf()) { it.id },
    watchStatusById: Map<String, WatchStatus> = emptyMap(),
    featured: com.kinogo.atv.ui.model.DetailsUiModel? =
        KinogoFixtures.detailsFor("title-2"),
    detailsById: Map<String, com.kinogo.atv.ui.model.DetailsUiModel> =
        KinogoFixtures.details.associateBy { it.id },
    catalogHasMore: Boolean = false,
    catalogLoading: Boolean = false,
    catalogError: String? = null,
    homeError: String? = null,
    catalogStatusLabel: String? = null,
    catalogSection: CatalogSection = CatalogSection.ROOT,
    searchResults: List<com.kinogo.atv.ui.model.PosterUiModel> = catalog,
    searchLoading: Boolean = false,
    searchError: String? = null,
    useRemoteCatalog: Boolean = false,
    onPlayRequested: (PlaybackSelectionUiModel) -> Unit = {},
    onCatalogLoadMore: () -> Unit = {},
    onCatalogRetry: () -> Unit = {},
    onHomeRetry: () -> Unit = {},
    onDetailsRequested: (String) -> Unit = {},
    onCatalogSectionSelected: (CatalogSection) -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
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
    settingsSections: List<SettingSectionUiModel> = KinogoFixtures.settings,
    highContrast: Boolean = false,
    reduceMotion: Boolean = false,
    onSettingChanged: (String, SettingCycleDirection) -> Unit = { _, _ -> },
    defaultQuality: VideoQualityPreference = VideoQualityPreference.AUTO,
    onExitConfirmed: () -> Unit = {},
) {
    var destinationName by rememberSaveable { mutableStateOf(initialDestination.name) }
    var selectedDetailsId by rememberSaveable { mutableStateOf(initialDetailsId) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    val destination = remember(destinationName, initialDestination) {
        restoredTvDestination(destinationName, initialDestination)
    }
    val selectedDetails = selectedDetailsId?.let { id ->
        detailsById[id]
            ?: pendingDetailsPoster(
                id = id,
                catalog = catalog,
                searchResults = searchResults,
                favorites = favorites,
                history = history,
            )
                ?.toPendingDetails()
    }

    fun openDetails(id: String) {
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

    MaterialTheme(colorScheme = if (highContrast) KinogoTvHighContrastColors else KinogoTvColors) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0E1521), Color(0xFF080C13), Color(0xFF05080D)),
                    ),
                ),
        ) {
            // UI controls stay inside a 5% TV safe area; decorative background remains full bleed.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = RailContentOffset),
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
                                featured = featured,
                                sections = homeSections,
                                onOpenDetails = ::openDetails,
                                errorMessage = homeError,
                                onRetry = onHomeRetry,
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
                                useRemoteSections = useRemoteCatalog,
                                selectedSection = catalogSection,
                                onSectionSelected = onCatalogSectionSelected,
                            )

                            TvDestination.Search -> SearchScreen(
                                catalog = searchResults,
                                onOpenDetails = ::openDetails,
                                useRemoteResults = useRemoteCatalog,
                                isLoading = searchLoading,
                                errorMessage = searchError,
                                onQueryChanged = onSearchQueryChanged,
                            )

                            TvDestination.Favorites -> BookmarksScreen(
                                bookmarks = bookmarks,
                                onOpenDetails = ::openDetails,
                            )

                            TvDestination.History -> HistoryScreen(
                                history = history,
                                onResume = { contentId ->
                                    onHistoryResume?.invoke(contentId)
                                        ?: run { selectedDetailsId = contentId }
                                },
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
                                onSettingChanged = onSettingChanged,
                                reduceMotion = reduceMotion,
                            )
                        }
                    }
                }

                KinogoNavigationRail(
                    selected = destination,
                    onSelected = {
                        selectedDetailsId = null
                        destinationName = it.name
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .zIndex(5f),
                )
            }

            if (showExitConfirmation) {
                ExitConfirmationDialog(
                    onStay = { showExitConfirmation = false },
                    onExit = onExitConfirmed,
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
                .padding(horizontal = 48.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(560.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF111927),
                border = BorderStroke(2.dp, Color(0xFF53647C)),
                shadowElevation = 28.dp,
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Выйти из Kinogo?",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "Случайное нажатие «Назад» больше не закроет приложение.",
                        color = Color(0xFFADB9C9),
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
