package com.kinogo.atv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.preferencesDataStore
import com.kinogo.atv.data.catalog.CatalogChallengeException
import com.kinogo.atv.data.catalog.CatalogException
import com.kinogo.atv.data.catalog.CatalogFingerprintException
import com.kinogo.atv.data.catalog.CatalogHttpStatusException
import com.kinogo.atv.data.catalog.CatalogNetworkException
import com.kinogo.atv.data.catalog.CatalogRedirectException
import com.kinogo.atv.data.catalog.CatalogRepository
import com.kinogo.atv.data.catalog.HtmlCatalogRepository
import com.kinogo.atv.data.catalog.KinogoHtmlParser
import com.kinogo.atv.data.catalog.KinogoSessionHttpClient
import com.kinogo.atv.data.catalog.ParsedContentPage
import com.kinogo.atv.data.catalog.PlayerEmbedCandidate
import com.kinogo.atv.data.favorites.FavoriteStore
import com.kinogo.atv.data.auth.KinogoAuthApi
import com.kinogo.atv.data.auth.KinogoRegistrationApi
import com.kinogo.atv.data.auth.KinogoSessionManager
import com.kinogo.atv.data.auth.RegistrationCaptchaKind
import com.kinogo.atv.data.auth.RegistrationInput
import com.kinogo.atv.data.auth.RegistrationLoadResult
import com.kinogo.atv.data.auth.RegistrationPage
import com.kinogo.atv.data.auth.RegistrationRulesPage
import com.kinogo.atv.data.auth.RegistrationSubmitResult
import com.kinogo.atv.data.auth.createCredentialStore
import com.kinogo.atv.data.history.PlaybackProgressStore
import com.kinogo.atv.data.history.PlaybackProgressCollection
import com.kinogo.atv.data.history.LegacyHistoryDetailsResolver
import com.kinogo.atv.data.library.KinogoLibraryApi
import com.kinogo.atv.data.library.KinogoLibraryRepository
import com.kinogo.atv.data.library.LibraryStateStore
import com.kinogo.atv.data.mirror.MirrorEntry
import com.kinogo.atv.data.mirror.MirrorBootstrapClient
import com.kinogo.atv.data.mirror.MirrorHealthChecker
import com.kinogo.atv.data.mirror.MirrorHealthStatus
import com.kinogo.atv.data.mirror.MirrorPreferencesStore
import com.kinogo.atv.data.mirror.MirrorRefreshCoordinator
import com.kinogo.atv.data.mirror.MirrorRefreshResult
import com.kinogo.atv.data.mirror.MirrorRegistry
import com.kinogo.atv.data.mirror.MirrorSource
import com.kinogo.atv.data.mirror.MirrorTrustState
import com.kinogo.atv.data.playback.DirectMediaResolver
import com.kinogo.atv.data.playback.KinogoPlaybackPreparationService
import com.kinogo.atv.data.playback.NativePlaybackPlanMapper
import com.kinogo.atv.data.playback.PlaybackPreparationRequest
import com.kinogo.atv.data.playback.PlaybackPreparationResult
import com.kinogo.atv.data.playback.PlaybackSourceRequest
import com.kinogo.atv.data.playback.PlaybackSourceResolution
import com.kinogo.atv.data.playback.ResolvedPlaybackEmbed
import com.kinogo.atv.data.search.SearchHistoryCollection
import com.kinogo.atv.data.search.SearchHistoryStore
import com.kinogo.atv.data.settings.TvPreferencesStore
import com.kinogo.atv.data.update.AppUpdateCheckResult
import com.kinogo.atv.data.update.AppUpdateCheckOrigin
import com.kinogo.atv.data.update.AppUpdateInstallResult
import com.kinogo.atv.data.update.AppUpdateManager
import com.kinogo.atv.data.update.AppUpdateRelease
import com.kinogo.atv.data.update.AutomaticUpdateCheckPolicy
import com.kinogo.atv.data.update.VerifiedAppUpdate
import com.kinogo.atv.domain.PlaybackSelection
import com.kinogo.atv.domain.CatalogItem
import com.kinogo.atv.domain.CatalogBrowseFilters
import com.kinogo.atv.domain.CatalogCategory
import com.kinogo.atv.domain.CatalogControls
import com.kinogo.atv.domain.CatalogQuery
import com.kinogo.atv.domain.ContentDetails
import com.kinogo.atv.domain.LibraryFilter
import com.kinogo.atv.domain.PlaybackMediaPlan
import com.kinogo.atv.domain.PlaybackMediaVariant
import com.kinogo.atv.domain.WatchProgress
import com.kinogo.atv.domain.LibraryRecord
import com.kinogo.atv.domain.StoredCredentials
import com.kinogo.atv.domain.WatchStatus
import com.kinogo.atv.domain.TvPreferences
import com.kinogo.atv.player.ui.TvPlayerScreen
import com.kinogo.atv.player.ui.PlaybackCheckpoint
import com.kinogo.atv.player.ui.PlaybackSourceRefreshRequest
import com.kinogo.atv.player.ui.PlaybackSourceRefreshUnitKey
import com.kinogo.atv.player.web.ProviderEmbedPlayerScreen
import com.kinogo.atv.ui.KinogoTvApp
import com.kinogo.atv.ui.components.PosterGridColumnCount
import com.kinogo.atv.ui.mapper.toDetailsUiModel
import com.kinogo.atv.ui.mapper.toPosterUiModel
import com.kinogo.atv.ui.model.BookmarkUiModel
import com.kinogo.atv.ui.model.DetailsUiModel
import com.kinogo.atv.ui.model.AppUpdateUiModel
import com.kinogo.atv.ui.model.AppUpdateUiPhase
import com.kinogo.atv.ui.model.HistoryUiModel
import com.kinogo.atv.ui.model.KinogoFixtures
import com.kinogo.atv.ui.model.MirrorStatusUi
import com.kinogo.atv.ui.model.MirrorUiModel
import com.kinogo.atv.ui.model.MirrorUiState
import com.kinogo.atv.ui.model.PlaybackSelectionUiModel
import com.kinogo.atv.ui.model.PosterUiModel
import com.kinogo.atv.ui.model.RegistrationSubmissionUiInput
import com.kinogo.atv.ui.model.RegistrationUiModel
import com.kinogo.atv.ui.model.RegistrationUiPhase
import com.kinogo.atv.ui.model.TvDestination
import com.kinogo.atv.ui.model.withPreferences
import com.kinogo.atv.ui.screens.PlaybackPreparationScreen
import com.kinogo.atv.ui.screens.PlaybackSourceSelectionModel
import com.kinogo.atv.ui.screens.PlaybackSourceSelectionScreen
import com.kinogo.atv.ui.screens.PlaybackWebFallbackUiModel
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.kinogoDataStore by preferencesDataStore(name = "kinogo_tv_state")

private const val DEVELOPMENT_FIXTURE_VIDEO_URL =
    "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
private const val DEVELOPMENT_FIXTURE_VOICE = "Демонстрационная дорожка"
private const val DEVELOPMENT_FIXTURE_QUALITY = "320p"
private const val APP_ROOT_LOG_TAG = "KinogoAppRoot"
private const val PLAYBACK_DETAIL_RETRY_DELAY_MS = 350L
private const val HOME_INITIAL_PRELOAD_ROWS = 3
private const val DONATE_URL = "https://donate.stream/donate_6a60559cd9e35"
private const val REPOSITORY_URL = "https://github.com/reziarlleh/KinogoATV"

/**
 * Home starts with the focused first row, so three rows keep two complete rows ready below it.
 * A strictly advancing page guard prevents a malformed pager from creating a request loop.
 */
internal fun shouldContinueHomeInitialPreload(
    itemCount: Int,
    loadedPage: Int,
    nextPage: Int?,
    columns: Int = PosterGridColumnCount,
): Boolean = columns > 0 &&
    itemCount < columns * HOME_INITIAL_PRELOAD_ROWS &&
    nextPage != null &&
    nextPage > loadedPage

internal fun isSamePlaybackUnit(
    requested: PlaybackSelectionUiModel,
    normalized: PlaybackSelectionUiModel,
): Boolean = requested.contentId == normalized.contentId &&
    requested.season == normalized.season &&
    requested.episode == normalized.episode

internal data class PlaybackLaunchSafety(
    val automaticSourceRefreshAttempts: Set<PlaybackSourceRefreshUnitKey>,
    val discardActivePlaybackOnExit: Boolean,
)

internal enum class PlaybackRecoveryEarlyFailure {
    CONTENT_UNAVAILABLE,
    MIRROR_UNAVAILABLE,
}

internal fun playbackLaunchSafety(
    currentAttempts: Set<PlaybackSourceRefreshUnitKey>,
    recovery: PlaybackSourceRefreshRequest?,
): PlaybackLaunchSafety = PlaybackLaunchSafety(
    automaticSourceRefreshAttempts = if (recovery == null) {
        currentAttempts
    } else {
        currentAttempts + recovery.attemptedUnits
    },
    discardActivePlaybackOnExit = recovery != null,
)

internal fun PlaybackLaunchSafety.recoveryErrorFor(
    failure: PlaybackRecoveryEarlyFailure,
): String? {
    if (!discardActivePlaybackOnExit) return null
    return when (failure) {
        PlaybackRecoveryEarlyFailure.CONTENT_UNAVAILABLE ->
            "Не удалось обновить источник: карточка больше недоступна"
        PlaybackRecoveryEarlyFailure.MIRROR_UNAVAILABLE ->
            "Не удалось обновить источник: нет активного проверенного зеркала"
    }
}

private data class ActivePlaybackSession(
    val generation: Long,
    val selection: PlaybackSelectionUiModel,
    val mediaPlan: PlaybackMediaPlan,
    val webFallbacks: List<ResolvedPlaybackEmbed> = emptyList(),
    val automaticSourceRefreshAttempts: Set<PlaybackSourceRefreshUnitKey> = emptySet(),
)

/**
 * Checkpoint writes must keep callback order. In particular, the close checkpoint must become
 * visible before an immediate Continue action reads DataStore again.
 */
internal class PlaybackCheckpointWriteQueue {
    private var tail: Job? = null

    fun enqueue(
        scope: CoroutineScope,
        write: suspend () -> Unit,
    ) {
        val next = synchronized(this) {
            val previous = tail
            scope.launch(start = CoroutineStart.LAZY) {
                previous?.join()
                write()
            }.also { tail = it }
        }
        next.start()
    }

    suspend fun awaitIdle() {
        while (true) {
            val observed = synchronized(this) { tail } ?: return
            observed.join()
            if (synchronized(this) { tail === observed }) return
        }
    }
}

internal fun acceptsPlaybackCheckpoint(
    activeGeneration: Long?,
    callbackGeneration: Long,
): Boolean = activeGeneration == callbackGeneration

internal fun monotonicPlaybackCheckpointTimestamp(
    nowMs: Long,
    previousTimestampMs: Long,
    entries: Collection<WatchProgress>,
): Long {
    val newestKnown = maxOf(
        previousTimestampMs,
        entries.maxOfOrNull(WatchProgress::updatedAtEpochMs) ?: 0L,
    )
    val nextKnown = if (newestKnown == Long.MAX_VALUE) Long.MAX_VALUE else newestKnown + 1L
    return maxOf(nowMs.coerceAtLeast(0L), nextKnown)
}

/** The fresh page used for source discovery also keeps the return Details action immediately live. */
internal fun ParsedContentPage.toPlaybackDetailsUiModel(): DetailsUiModel {
    val details = ContentDetails(
        catalogItem = catalogItem,
        description = description,
        countries = countries,
        genres = genres,
        directors = directors,
        cast = cast,
        durationMinutes = durationMinutes,
    )
    val voiceovers = metadata.findValue("Перевод")
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        .orEmpty()
    val qualities = listOfNotNull(metadata.findValue("Качество"))
    val canDiscoverAtLaunch =
        playerEmbeds.isNotEmpty() || (catalogItem.serverPostId != null && catalogItem.year != null)
    val sourceStatus = when {
        playerEmbeds.isNotEmpty() ->
            "Нативные источники, переводы и серии будут обновлены перед запуском"
        canDiscoverAtLaunch ->
            "Источник будет найден и проверен непосредственно перед запуском"
        else -> playerNotice ?: "На странице не найден источник воспроизведения"
    }
    return details.toDetailsUiModel(
        playbackAvailable = canDiscoverAtLaunch,
        statusMessage = sourceStatus,
    ).copy(
        voiceovers = voiceovers,
        qualities = qualities,
        providerPlayback = false,
    )
}

/**
 * Source discovery can succeed even when the conservative HTML card did not expose enough
 * metadata to advertise playback beforehand. Keep the returned Details action enabled after a
 * successful preparation instead of forcing the user to leave and reopen the card.
 */
internal fun DetailsUiModel.withPreparedPlaybackAvailability(
    nativePlanReady: Boolean,
    webFallbackReady: Boolean,
): DetailsUiModel {
    if (!nativePlanReady && !webFallbackReady) return this
    return copy(
        playbackAvailable = true,
        statusMessage = if (nativePlanReady) {
            "Нативный источник готов к воспроизведению"
        } else {
            "Доступен оригинальный web-плеер"
        },
    )
}

/** A transient refresh must not revoke a playback action that was already proven to work. */
internal fun DetailsUiModel.preserveConfirmedPlaybackAvailability(
    previous: DetailsUiModel?,
): DetailsUiModel = if (previous?.playbackAvailable == true && !playbackAvailable) {
    copy(
        playbackAvailable = true,
        statusMessage = previous.statusMessage,
    )
} else {
    this
}

internal fun DetailsUiModel.withPlaybackPreparationFailure(): DetailsUiModel =
    if (playbackAvailable) {
        copy(
            statusMessage =
                "Источник временно недоступен. Нажмите «Смотреть» для повторного поиска",
        )
    } else {
        this
    }

private data class ActiveEmbeddedPlaybackSession(
    val selection: PlaybackSelectionUiModel,
    val source: ResolvedPlaybackEmbed,
)

private data class PlaybackLaunchUiState(
    val request: PlaybackSelectionUiModel,
    val title: String,
    val errorMessage: String? = null,
    val discardActivePlaybackOnExit: Boolean = false,
)

/** Short-lived prepared sources. This state is memory-only and all nested URLs redact themselves. */
private data class PendingPlaybackSelectionSession(
    val title: String,
    val selection: PlaybackSelectionUiModel,
    val mediaPlan: PlaybackMediaPlan?,
    val webFallbacks: List<ResolvedPlaybackEmbed>,
    val initialPositionMs: Long,
) {
    init {
        require(mediaPlan != null || webFallbacks.isNotEmpty())
        require(initialPositionMs >= 0L)
    }

    override fun toString(): String =
        "PendingPlaybackSelectionSession(" +
            "title=$title, selection=$selection, mediaPlan=$mediaPlan, " +
            "webFallbacks=<redacted>, initialPositionMs=$initialPositionMs)"
}

private enum class CatalogFeedKind {
    HOME,
    CATALOG,
    SEARCH,
}

private data class CatalogFeedState(
    val query: CatalogQuery? = null,
    val items: List<CatalogItem> = emptyList(),
    val controls: CatalogControls = CatalogControls(),
    val nextPage: Int? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val origin: String? = null,
    val generation: Long = 0L,
)

private val CatalogControls.hasParsedBrowseControls: Boolean
    get() = sortOptions.isNotEmpty() ||
        collectionOptions.isNotEmpty() ||
        yearOptions.isNotEmpty() ||
        countryOptions.isNotEmpty()

@Composable
fun KinogoAppRoot() {
    val localContext = LocalContext.current
    val activity = localContext as? Activity
    val context = localContext.applicationContext
    val scope = rememberCoroutineScope()
    val progressStore = remember(context) { PlaybackProgressStore(context.kinogoDataStore) }
    val favoriteStore = remember(context) { FavoriteStore(context.kinogoDataStore) }
    val libraryStore = remember(context) { LibraryStateStore(context.kinogoDataStore) }
    val mirrorPreferences = remember(context) { MirrorPreferencesStore(context.kinogoDataStore) }
    val tvPreferencesStore = remember(context) { TvPreferencesStore(context.kinogoDataStore) }
    val searchHistoryStore = remember(context) { SearchHistoryStore(context.kinogoDataStore) }
    val tvPreferencesSnapshot by tvPreferencesStore.preferences.collectAsState(initial = null)
    val tvPreferences = tvPreferencesSnapshot ?: TvPreferences()
    val mirrorRegistry = remember { MirrorRegistry() }
    val mirrorBootstrapClient = remember { MirrorBootstrapClient() }
    val mirrorCoordinator = remember(mirrorRegistry) {
        MirrorRefreshCoordinator(mirrorRegistry, MirrorHealthChecker())
    }
    val sessionHttpClient = remember { KinogoSessionHttpClient() }
    val registrationApi = remember(sessionHttpClient) { KinogoRegistrationApi(sessionHttpClient) }
    val appUpdateManager = remember(context) { AppUpdateManager(context) }
    val htmlParser = remember { KinogoHtmlParser() }
    val credentialStore = remember(context) { context.createCredentialStore() }
    val sessionManager = remember(credentialStore, sessionHttpClient) {
        KinogoSessionManager(
            credentialStore = credentialStore,
            authApi = KinogoAuthApi(sessionHttpClient),
            client = sessionHttpClient,
        )
    }
    val libraryRepository = remember(libraryStore, sessionManager, sessionHttpClient, htmlParser) {
        KinogoLibraryRepository(
            store = libraryStore,
            sessionManager = sessionManager,
            remote = KinogoLibraryApi(sessionHttpClient, htmlParser),
        )
    }
    val catalogRepository = remember(sessionHttpClient, htmlParser) {
        HtmlCatalogRepository(sessionHttpClient, htmlParser)
    }
    val legacyHistoryDetailsResolver = remember(sessionHttpClient, catalogRepository) {
        LegacyHistoryDetailsResolver(sessionHttpClient, catalogRepository)
    }
    val directMediaResolver = remember { DirectMediaResolver() }
    val playbackPreparationService = remember { KinogoPlaybackPreparationService() }

    var history by remember { mutableStateOf(emptyList<WatchProgress>()) }
    var libraryRecords by remember { mutableStateOf(emptyList<LibraryRecord>()) }
    var librarySyncMessage by remember { mutableStateOf<String?>(null) }
    var librarySyncPendingCount by remember { mutableIntStateOf(0) }
    var librarySyncInProgress by remember { mutableStateOf(false) }
    var librarySyncQueued by remember { mutableStateOf(false) }
    val accountState by sessionManager.state.collectAsState()
    var mirrorEntries by remember { mutableStateOf(mirrorRegistry.all()) }
    var activeMirrorOrigin by remember { mutableStateOf<String?>(null) }
    var mirrorCheckInProgress by remember { mutableStateOf(false) }
    var lastMirrorCheckLabel by remember { mutableStateOf<String?>(null) }
    var registrationPage by remember { mutableStateOf<RegistrationPage?>(null) }
    var registrationRulesPage by remember { mutableStateOf<RegistrationRulesPage?>(null) }
    var registrationUi by remember { mutableStateOf<RegistrationUiModel?>(null) }
    var registrationGeneration by remember { mutableLongStateOf(0L) }
    var registrationJob by remember { mutableStateOf<Job?>(null) }
    var availableAppUpdate by remember { mutableStateOf<AppUpdateRelease?>(null) }
    var verifiedAppUpdate by remember { mutableStateOf<VerifiedAppUpdate?>(null) }
    var appUpdateUi by remember {
        mutableStateOf(AppUpdateUiModel(currentVersion = BuildConfig.VERSION_NAME))
    }
    var appUpdateJob by remember { mutableStateOf<Job?>(null) }
    var automaticUpdateCheckStarted by remember { mutableStateOf(false) }
    var showAutomaticUpdatePrompt by remember { mutableStateOf(false) }
    var activePlayback by remember { mutableStateOf<ActivePlaybackSession?>(null) }
    var playbackSessionGeneration by remember { mutableLongStateOf(0L) }
    var lastCheckpointTimestampMs by remember { mutableLongStateOf(0L) }
    val checkpointWriteQueue = remember { PlaybackCheckpointWriteQueue() }
    var activeEmbeddedPlayback by remember {
        mutableStateOf<ActiveEmbeddedPlaybackSession?>(null)
    }
    var playbackInitialPositionMs by remember { mutableLongStateOf(0L) }
    var playbackLaunchGeneration by remember { mutableLongStateOf(0L) }
    var playbackLaunchUi by remember { mutableStateOf<PlaybackLaunchUiState?>(null) }
    var playbackLaunchJob by remember { mutableStateOf<Job?>(null) }
    var pendingPlaybackSelection by remember {
        mutableStateOf<PendingPlaybackSelectionSession?>(null)
    }
    var playbackReturnDetailsId by remember { mutableStateOf<String?>(null) }
    var startupError by remember { mutableStateOf<String?>(null) }
    var currentDestination by remember { mutableStateOf(TvDestination.Home) }
    var searchInputQuery by remember { mutableStateOf("") }
    var recentSearchQueries by remember { mutableStateOf(emptyList<String>()) }
    var searchFocusedItemId by remember { mutableStateOf<String?>(null) }
    var homeFocusedItemId by remember { mutableStateOf<String?>(null) }
    var catalogFocusedItemId by remember { mutableStateOf<String?>(null) }
    var bookmarksFocusedItemId by remember { mutableStateOf<String?>(null) }
    var historyFocusedItemId by remember { mutableStateOf<String?>(null) }
    var bookmarksFilter by remember { mutableStateOf(LibraryFilter.ALL) }
    var homeFeed by remember {
        mutableStateOf(CatalogFeedState(query = CatalogQuery()))
    }
    var catalogFeed by remember {
        mutableStateOf(
            CatalogFeedState(query = CatalogQuery(category = CatalogCategory.NEW_RELEASES)),
        )
    }
    var searchFeed by remember { mutableStateOf(CatalogFeedState()) }
    val feedJobs = remember { mutableMapOf<CatalogFeedKind, Job>() }
    var contentGeneration by remember { mutableLongStateOf(0L) }
    var liveDetailsById by remember {
        mutableStateOf(emptyMap<String, com.kinogo.atv.ui.model.DetailsUiModel>())
    }
    var loadingDetailIds by remember { mutableStateOf(emptySet<String>()) }
    var enrichingHistoryIds by remember { mutableStateOf(emptySet<String>()) }

    fun nextCheckpointTimestampMs(): Long {
        val timestamp = monotonicPlaybackCheckpointTimestamp(
            nowMs = System.currentTimeMillis(),
            previousTimestampMs = lastCheckpointTimestampMs,
            entries = history,
        )
        lastCheckpointTimestampMs = timestamp
        return timestamp
    }

    fun knownCatalogItems(): List<CatalogItem> =
        (
            homeFeed.items +
                catalogFeed.items +
                searchFeed.items +
                libraryRecords.map { it.item } +
                history.mapNotNull(WatchProgress::historyCatalogItem)
            ).distinctBy(CatalogItem::id)

    suspend fun persistHistorySnapshot(item: CatalogItem) {
        progressStore.attachContentSnapshot(item)
        history = PlaybackProgressCollection.normalize(history + progressStore.list())
    }

    suspend fun loadCatalogDetails(origin: String, item: CatalogItem) =
        loadCatalogDetailsWithLegacyFallback(
            origin = origin,
            item = item,
            repository = catalogRepository,
            legacyResolver = legacyHistoryDetailsResolver,
        )

    fun requestLibrarySync(origin: String, login: String) {
        if (librarySyncInProgress) {
            librarySyncQueued = true
            return
        }
        librarySyncInProgress = true
        scope.launch {
            try {
                val result = libraryRepository.sync(origin, login)
                if (activeMirrorOrigin == origin) {
                    libraryRecords = result.records
                    librarySyncPendingCount = result.pendingCount
                    librarySyncMessage = result.message
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(APP_ROOT_LOG_TAG, "Library synchronization failed", error)
                if (activeMirrorOrigin == origin) {
                    librarySyncMessage =
                        "Не удалось синхронизировать закладки. Локальные данные сохранены."
                }
            } finally {
                librarySyncInProgress = false
                val runAgain = librarySyncQueued
                librarySyncQueued = false
                if (runAgain) {
                    val currentOrigin = activeMirrorOrigin
                    val currentLogin = sessionManager.state.value.login
                    if (currentOrigin != null && currentLogin != null) {
                        requestLibrarySync(currentOrigin, currentLogin)
                    }
                }
            }
        }
    }

    fun currentFeed(kind: CatalogFeedKind): CatalogFeedState = when (kind) {
        CatalogFeedKind.HOME -> homeFeed
        CatalogFeedKind.CATALOG -> catalogFeed
        CatalogFeedKind.SEARCH -> searchFeed
    }

    fun setFeed(kind: CatalogFeedKind, value: CatalogFeedState) {
        when (kind) {
            CatalogFeedKind.HOME -> homeFeed = value
            CatalogFeedKind.CATALOG -> catalogFeed = value
            CatalogFeedKind.SEARCH -> searchFeed = value
        }
    }

    fun requestFeedPage(
        kind: CatalogFeedKind,
        origin: String,
        query: CatalogQuery,
        page: Int,
        reset: Boolean,
    ) {
        val identity = query.identity
        val previous = currentFeed(kind)
        if (!reset && (previous.loading || previous.query?.identity != identity)) return
        if (reset) feedJobs.remove(kind)?.cancel()
        val generation = if (reset) previous.generation + 1L else previous.generation
        val started = if (reset) {
            val retainVisibleSnapshot = kind != CatalogFeedKind.SEARCH &&
                previous.origin == origin
            previous.copy(
                query = identity,
                items = if (retainVisibleSnapshot) previous.items else emptyList(),
                controls = if (retainVisibleSnapshot) previous.controls else CatalogControls(),
                nextPage = null,
                loading = true,
                error = null,
                origin = origin,
                generation = generation,
            )
        } else {
            previous.copy(loading = true, error = null)
        }
        setFeed(kind, started)

        val job = scope.launch {
            try {
                val result = catalogRepository.loadPage(
                    origin = origin,
                    query = identity.copy(page = page),
                )
                val latest = currentFeed(kind)
                if (
                    latest.generation != generation ||
                    latest.origin != origin ||
                    latest.query?.identity != identity ||
                    activeMirrorOrigin != origin
                ) {
                    return@launch
                }
                val parsedBrowseControls = result.controls.hasParsedBrowseControls
                val controls = when {
                    parsedBrowseControls -> result.controls.copy(
                        categories = result.controls.categories.ifEmpty {
                            latest.controls.categories
                        },
                    )
                    result.controls.categories.isNotEmpty() -> latest.controls.copy(
                        categories = result.controls.categories,
                    )
                    else -> latest.controls
                }
                val effectiveQuery = if (
                    identity.searchTerm == null && parsedBrowseControls
                ) {
                    identity.copy(filters = result.controls.activeFilters)
                } else {
                    identity
                }
                val updated = latest.copy(
                    query = effectiveQuery,
                    items = if (reset) {
                        result.items
                    } else {
                        (latest.items + result.items).distinctBy(CatalogItem::id)
                    },
                    controls = controls,
                    nextPage = result.nextPage,
                    loading = false,
                    error = null,
                )
                setFeed(kind, updated)
                if (
                    kind == CatalogFeedKind.HOME &&
                    shouldContinueHomeInitialPreload(
                        itemCount = updated.items.size,
                        loadedPage = page,
                        nextPage = updated.nextPage,
                    )
                ) {
                    requestFeedPage(
                        kind = kind,
                        origin = origin,
                        query = effectiveQuery,
                        page = requireNotNull(updated.nextPage),
                        reset = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(
                    APP_ROOT_LOG_TAG,
                    "Catalog feed $kind page $page failed",
                    error,
                )
                val latest = currentFeed(kind)
                if (
                    latest.generation == generation &&
                    latest.origin == origin &&
                    activeMirrorOrigin == origin
                ) {
                    setFeed(
                        kind,
                        latest.copy(loading = false, error = catalogErrorLabel(error)),
                    )
                }
            } finally {
                if (feedJobs[kind] === coroutineContext[Job]) {
                    feedJobs.remove(kind)
                }
            }
        }
        feedJobs[kind] = job
    }

    fun requestSearch(rawQuery: String) {
        val value = SearchHistoryCollection.normalize(rawQuery).orEmpty()
        if (value.isEmpty()) {
            searchFeed = CatalogFeedState(generation = searchFeed.generation + 1L)
            return
        }
        val query = CatalogQuery.search(value)
        if (searchFeed.query?.identity != query.identity) {
            searchFocusedItemId = null
        }
        if (searchFeed.query?.identity == query.identity) {
            if (searchFeed.loading) return
            if (searchFeed.error == null && searchFeed.items.isNotEmpty()) return
        }
        val origin = activeMirrorOrigin
        if (origin == null) {
            searchFeed = CatalogFeedState(
                query = query,
                error = "Нет доступного зеркала для поиска",
                generation = searchFeed.generation + 1L,
            )
            return
        }
        val retryAppend = searchFeed.query?.identity == query.identity &&
            searchFeed.items.isNotEmpty() &&
            searchFeed.nextPage != null
        requestFeedPage(
            kind = CatalogFeedKind.SEARCH,
            origin = origin,
            query = query,
            page = if (retryAppend) requireNotNull(searchFeed.nextPage) else 1,
            reset = !retryAppend,
        )
    }

    fun recordCommittedSearch(rawQuery: String) {
        val value = SearchHistoryCollection.normalize(rawQuery) ?: return
        recentSearchQueries = SearchHistoryCollection.record(recentSearchQueries, value)
        scope.launch {
            try {
                recentSearchQueries = searchHistoryStore.record(value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(APP_ROOT_LOG_TAG, "Recent search query write failed", error)
            }
        }
    }

    fun requestDetails(contentId: String) {
        val item = knownCatalogItems().firstOrNull { it.id == contentId } ?: return
        val origin = activeMirrorOrigin ?: return
        if (contentId in liveDetailsById || contentId in loadingDetailIds) return
        val generation = contentGeneration
        loadingDetailIds = loadingDetailIds + contentId
        scope.launch {
            try {
                val parsed = loadCatalogDetails(origin, item)
                if (generation != contentGeneration || activeMirrorOrigin != origin) return@launch
                persistHistorySnapshot(parsed.catalogItem)
                liveDetailsById = liveDetailsById + (contentId to parsed.toPlaybackDetailsUiModel())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == contentGeneration && activeMirrorOrigin == origin) {
                    val unavailable = ContentDetails(
                        catalogItem = item,
                        description = "Не удалось загрузить подробную карточку.",
                    ).toDetailsUiModel(
                        playbackAvailable = false,
                        statusMessage = catalogErrorLabel(error),
                    )
                    liveDetailsById = liveDetailsById + (contentId to unavailable)
                }
            } finally {
                if (generation == contentGeneration) {
                    loadingDetailIds = loadingDetailIds - contentId
                }
            }
        }
    }

    fun applyMirrorRefresh(result: MirrorRefreshResult, preferredOrigin: String?) {
        mirrorEntries = result.entries
        val preferred = result.entries.firstOrNull { entry ->
            entry.origin == preferredOrigin && mirrorRegistry.isEligible(entry.origin)
        }
        val selectedOrigin = (preferred ?: result.active)?.origin
        val mirrorChanged = activeMirrorOrigin != selectedOrigin
        activeMirrorOrigin = selectedOrigin
        if (mirrorChanged) {
            contentGeneration++
            registrationPage = null
            registrationUi = null
            liveDetailsById = emptyMap()
            loadingDetailIds = emptySet()
            searchFeed = CatalogFeedState(generation = searchFeed.generation + 1L)
            catalogFeed = CatalogFeedState(
                query = CatalogQuery(
                    category = catalogFeed.query?.category ?: CatalogCategory.NEW_RELEASES,
                ),
                controls = CatalogControls(categories = catalogFeed.controls.categories),
                generation = catalogFeed.generation + 1L,
            )
        }
        if (selectedOrigin == null) {
            val message = "Нет доступного проверенного зеркала"
            homeFeed = CatalogFeedState(
                query = CatalogQuery(),
                error = message,
                generation = homeFeed.generation + 1L,
            )
            catalogFeed = CatalogFeedState(
                query = CatalogQuery(category = CatalogCategory.NEW_RELEASES),
                error = message,
                generation = catalogFeed.generation + 1L,
            )
        } else {
            val homeQuery = if (mirrorChanged) {
                CatalogQuery()
            } else {
                homeFeed.query ?: CatalogQuery()
            }
            if (mirrorChanged || homeFeed.origin != selectedOrigin || homeFeed.items.isEmpty()) {
                requestFeedPage(
                    CatalogFeedKind.HOME,
                    selectedOrigin,
                    homeQuery,
                    page = 1,
                    reset = true,
                )
            }
            if (currentDestination == TvDestination.Catalog) {
                requestFeedPage(
                    kind = CatalogFeedKind.CATALOG,
                    origin = selectedOrigin,
                    query = catalogFeed.query
                        ?: CatalogQuery(category = CatalogCategory.NEW_RELEASES),
                    page = 1,
                    reset = true,
                )
            }
        }
        if (selectedOrigin != null) {
            scope.launch {
                try {
                    sessionManager.restore(selectedOrigin)?.let { session ->
                        requestLibrarySync(selectedOrigin, session.login)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(APP_ROOT_LOG_TAG, "Saved account restore failed", error)
                    librarySyncMessage =
                        "Не удалось восстановить сессию. Войдите повторно в настройках."
                }
            }
        }
    }

    fun requestMirrorRefresh(preferredOrigin: String? = activeMirrorOrigin) {
        if (mirrorCheckInProgress) return
        mirrorCheckInProgress = true
        startupError = null
        if (activeMirrorOrigin == null && homeFeed.items.isEmpty()) {
            homeFeed = homeFeed.copy(error = null)
            catalogFeed = catalogFeed.copy(error = null)
        }
        mirrorEntries = mirrorRegistry.all()
        scope.launch {
            try {
                val persistedPreference = preferredOrigin ?: try {
                    mirrorPreferences.selectedOrigin()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(APP_ROOT_LOG_TAG, "Selected mirror preference read failed", error)
                    null
                }
                // Remote candidates never become active here. They enter the registry quarantined
                // and still have to pass the existing DNS/HTTPS/service-fingerprint probe below.
                mirrorBootstrapClient.refresh(mirrorRegistry)
                mirrorEntries = mirrorRegistry.all()
                val result = mirrorCoordinator.refresh()
                startupError = null
                applyMirrorRefresh(result, persistedPreference)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(APP_ROOT_LOG_TAG, "Mirror refresh failed", error)
                mirrorEntries = mirrorRegistry.all()
                val message = mirrorRefreshErrorLabel(error)
                startupError = message
                if (activeMirrorOrigin == null && homeFeed.items.isEmpty()) {
                    homeFeed = homeFeed.copy(loading = false, error = message)
                    catalogFeed = catalogFeed.copy(loading = false, error = message)
                }
            } finally {
                mirrorCheckInProgress = false
                lastMirrorCheckLabel =
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
            }
        }
    }

    fun applyRegistrationLoadResult(result: RegistrationLoadResult) {
        when (result) {
            is RegistrationLoadResult.Ready -> {
                registrationRulesPage = null
                registrationPage = result.page
                registrationUi = result.page.toRegistrationUiModel()
            }
            is RegistrationLoadResult.ConsentRequired -> {
                registrationPage = null
                registrationRulesPage = result.page
                registrationUi = RegistrationUiModel(
                    phase = RegistrationUiPhase.RULES,
                    rulesText = result.page.rulesText,
                )
            }
            is RegistrationLoadResult.Unavailable -> {
                registrationPage = null
                registrationRulesPage = null
                registrationUi = RegistrationUiModel(
                    phase = RegistrationUiPhase.UNAVAILABLE,
                    message = result.message,
                )
            }
            is RegistrationLoadResult.Failed -> {
                registrationPage = null
                registrationRulesPage = null
                registrationUi = RegistrationUiModel(
                    phase = RegistrationUiPhase.ERROR,
                    message = result.message,
                )
            }
        }
    }

    fun requestRegistration() {
        registrationJob?.cancel()
        val generation = ++registrationGeneration
        val origin = activeMirrorOrigin
        registrationPage = null
        registrationRulesPage = null
        if (origin == null) {
            registrationUi = RegistrationUiModel(
                phase = RegistrationUiPhase.UNAVAILABLE,
                message = "Сначала выберите доступное проверенное зеркало",
            )
            return
        }
        registrationUi = RegistrationUiModel(phase = RegistrationUiPhase.LOADING)
        registrationJob = scope.launch {
            try {
                val result = registrationApi.load(origin)
                if (generation != registrationGeneration || activeMirrorOrigin != origin) return@launch
                applyRegistrationLoadResult(result)
            } finally {
                if (generation == registrationGeneration) registrationJob = null
            }
        }
    }

    fun acceptRegistrationRules() {
        val page = registrationRulesPage ?: return
        val origin = activeMirrorOrigin ?: return
        registrationJob?.cancel()
        val generation = ++registrationGeneration
        registrationUi = RegistrationUiModel(
            phase = RegistrationUiPhase.LOADING,
            message = "Загружаем регистрационную форму…",
        )
        registrationJob = scope.launch {
            try {
                val result = registrationApi.acceptRules(page)
                if (generation != registrationGeneration || activeMirrorOrigin != origin) return@launch
                applyRegistrationLoadResult(result)
            } finally {
                if (generation == registrationGeneration) registrationJob = null
            }
        }
    }

    fun submitRegistration(input: RegistrationSubmissionUiInput) {
        val page = registrationPage ?: return
        val origin = activeMirrorOrigin ?: return
        registrationJob?.cancel()
        val generation = ++registrationGeneration
        registrationUi = page.toRegistrationUiModel(
            phase = RegistrationUiPhase.SUBMITTING,
            message = null,
        )
        registrationJob = scope.launch {
            try {
                val submission = RegistrationInput(
                    login = input.login,
                    email = input.email,
                    password = input.password,
                    passwordConfirmation = input.passwordConfirmation,
                    captchaText = input.captchaText,
                    acceptedTerms = input.acceptedTerms,
                )
                val result = registrationApi.submit(page, submission)
                if (generation != registrationGeneration || activeMirrorOrigin != origin) return@launch
                when (result) {
                    is RegistrationSubmitResult.Completed -> {
                        registrationUi = RegistrationUiModel(
                            phase = RegistrationUiPhase.COMPLETED,
                            message = "Аккаунт создан. Выполняем вход…",
                        )
                        try {
                            val login = input.login.trim()
                            if (accountState.login != null && accountState.login != login) {
                                libraryStore.clearAccountData()
                                if (generation != registrationGeneration || activeMirrorOrigin != origin) {
                                    return@launch
                                }
                                libraryRecords = emptyList()
                                librarySyncPendingCount = 0
                            }
                            val session = sessionManager.saveAndLogin(
                                origin,
                                StoredCredentials(login, input.password),
                            )
                            if (generation != registrationGeneration || activeMirrorOrigin != origin) {
                                return@launch
                            }
                            registrationUi = RegistrationUiModel(
                                phase = RegistrationUiPhase.COMPLETED,
                                message = if (session != null) {
                                    requestLibrarySync(origin, session.login)
                                    "Аккаунт создан, вход выполнен"
                                } else {
                                    "Аккаунт создан. Войдите с новыми данными"
                                },
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            if (generation == registrationGeneration && activeMirrorOrigin == origin) {
                                registrationUi = RegistrationUiModel(
                                    phase = RegistrationUiPhase.COMPLETED,
                                    message = "Аккаунт создан. Автоматический вход не удался — выполните вход вручную",
                                )
                            }
                        }
                    }
                    is RegistrationSubmitResult.Rejected -> {
                        val refreshedPage = result.refreshedPage ?: page
                        registrationPage = refreshedPage
                        registrationUi = refreshedPage.toRegistrationUiModel(message = result.message)
                    }
                    is RegistrationSubmitResult.Failed -> registrationUi = page.toRegistrationUiModel(
                        phase = RegistrationUiPhase.ERROR,
                        message = result.message,
                    )
                }
            } finally {
                if (generation == registrationGeneration) registrationJob = null
            }
        }
    }

    fun requestAppUpdateCheck(origin: AppUpdateCheckOrigin = AppUpdateCheckOrigin.MANUAL) {
        if (appUpdateJob?.isActive == true) return
        if (origin == AppUpdateCheckOrigin.MANUAL) showAutomaticUpdatePrompt = false
        verifiedAppUpdate?.apkFile?.delete()
        verifiedAppUpdate = null
        availableAppUpdate = null
        appUpdateUi = AppUpdateUiModel(
            currentVersion = BuildConfig.VERSION_NAME,
            phase = AppUpdateUiPhase.CHECKING,
            status = "Проверяем наличие обновлений…",
            actionLabel = null,
            actionEnabled = false,
        )
        appUpdateJob = scope.launch {
            var attempt = 1
            try {
                while (true) {
                    try {
                        val result = appUpdateManager.check(BuildConfig.VERSION_CODE.toLong())
                        when (result) {
                            is AppUpdateCheckResult.UpToDate -> {
                                availableAppUpdate = null
                                verifiedAppUpdate = null
                                appUpdateUi = AppUpdateUiModel(
                                    currentVersion = BuildConfig.VERSION_NAME,
                                    phase = AppUpdateUiPhase.CURRENT,
                                    status = if (result.latestVersionName == null) {
                                        "Опубликованных обновлений пока нет"
                                    } else {
                                        "Установлена актуальная версия"
                                    },
                                    actionLabel = "Проверить снова",
                                )
                            }
                            is AppUpdateCheckResult.Available -> {
                                availableAppUpdate = result.release
                                verifiedAppUpdate = null
                                appUpdateUi = AppUpdateUiModel(
                                    currentVersion = BuildConfig.VERSION_NAME,
                                    phase = AppUpdateUiPhase.AVAILABLE,
                                    status = "Доступна версия ${result.release.versionName}",
                                    availableVersion = result.release.versionName,
                                    actionLabel = "Загрузить",
                                )
                            }
                        }
                        if (AutomaticUpdateCheckPolicy.shouldPrompt(origin, result)) {
                            showAutomaticUpdatePrompt = true
                        }
                        break
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (AutomaticUpdateCheckPolicy.shouldRetry(origin, attempt)) {
                            Log.w(
                                APP_ROOT_LOG_TAG,
                                "Automatic app update check failed; retrying once",
                            )
                            attempt++
                            delay(AutomaticUpdateCheckPolicy.RETRY_DELAY_MS)
                        } else {
                            Log.w(APP_ROOT_LOG_TAG, "App update check failed")
                            appUpdateUi = AppUpdateUiModel(
                                currentVersion = BuildConfig.VERSION_NAME,
                                phase = AppUpdateUiPhase.ERROR,
                                status = "Не удалось проверить обновления",
                                actionLabel = "Повторить",
                            )
                            break
                        }
                    }
                }
            } finally {
                appUpdateJob = null
            }
        }
    }

    fun requestAppUpdateDownload(release: AppUpdateRelease) {
        if (appUpdateJob?.isActive == true) return
        appUpdateUi = AppUpdateUiModel(
            currentVersion = BuildConfig.VERSION_NAME,
            phase = AppUpdateUiPhase.DOWNLOADING,
            status = "Загружаем и проверяем подпись APK…",
            availableVersion = release.versionName,
            actionLabel = null,
            actionEnabled = false,
        )
        appUpdateJob = scope.launch {
            try {
                val verified = appUpdateManager.downloadAndVerify(release)
                verifiedAppUpdate = verified
                appUpdateUi = AppUpdateUiModel(
                    currentVersion = BuildConfig.VERSION_NAME,
                    phase = AppUpdateUiPhase.READY_TO_INSTALL,
                    status = "APK проверен. Android попросит подтвердить установку",
                    availableVersion = release.versionName,
                    actionLabel = "Установить",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                verifiedAppUpdate = null
                appUpdateUi = AppUpdateUiModel(
                    currentVersion = BuildConfig.VERSION_NAME,
                    phase = AppUpdateUiPhase.ERROR,
                    status = "Не удалось загрузить или проверить обновление",
                    availableVersion = release.versionName,
                    actionLabel = "Повторить",
                )
            } finally {
                appUpdateJob = null
            }
        }
    }

    fun handleAppUpdateAction() {
        when (appUpdateUi.phase) {
            AppUpdateUiPhase.AVAILABLE -> availableAppUpdate?.let(::requestAppUpdateDownload)
            AppUpdateUiPhase.DOWNLOADING,
            AppUpdateUiPhase.CHECKING,
            -> Unit
            AppUpdateUiPhase.READY_TO_INSTALL -> {
                val host = activity
                val update = verifiedAppUpdate
                if (host == null || update == null) {
                    availableAppUpdate = null
                    verifiedAppUpdate = null
                    appUpdateUi = appUpdateUi.copy(
                        phase = AppUpdateUiPhase.ERROR,
                        status = "Системный установщик недоступен",
                        actionLabel = "Проверить снова",
                    )
                } else {
                    try {
                        appUpdateUi = when (appUpdateManager.requestInstall(host, update)) {
                            AppUpdateInstallResult.InstallerOpened -> appUpdateUi.copy(
                                status = "Подтвердите обновление в системном окне Android",
                                actionLabel = "Установить снова",
                            )
                            AppUpdateInstallResult.UnknownSourcesPermissionOpened -> appUpdateUi.copy(
                                status = "Разрешите установку для KinogoATV, вернитесь и нажмите «Установить»",
                                actionLabel = "Установить",
                            )
                        }
                    } catch (_: Exception) {
                        appUpdateUi = appUpdateUi.copy(
                            phase = AppUpdateUiPhase.READY_TO_INSTALL,
                            status = "Не удалось открыть системный установщик",
                            actionLabel = "Повторить установку",
                        )
                    }
                }
            }
            else -> {
                val available = availableAppUpdate
                if (appUpdateUi.phase == AppUpdateUiPhase.ERROR && available != null) {
                    requestAppUpdateDownload(available)
                } else {
                    requestAppUpdateCheck()
                }
            }
        }
    }

    fun startPlayback(
        requested: PlaybackSelectionUiModel,
        recovery: PlaybackSourceRefreshRequest? = null,
    ) {
        playbackReturnDetailsId = requested.contentId
        playbackLaunchJob?.cancel()
        playbackLaunchJob = null
        val launchSafety = playbackLaunchSafety(
            currentAttempts = activePlayback
                ?.automaticSourceRefreshAttempts
                .orEmpty(),
            recovery = recovery,
        )
        if (recovery != null) {
            // Persist the consumed refresh budget before the launch screen disposes Media3.
            // Otherwise Back could revive the failed session with an empty immutable set.
            activePlayback = activePlayback?.copy(
                automaticSourceRefreshAttempts =
                    launchSafety.automaticSourceRefreshAttempts,
            )
        }
        fun stopRecoveryBeforePreparation(title: String, errorMessage: String) {
            activePlayback = null
            activeEmbeddedPlayback = null
            pendingPlaybackSelection = null
            playbackLaunchGeneration++
            playbackLaunchUi = PlaybackLaunchUiState(
                request = requested,
                title = title,
                errorMessage = errorMessage,
                discardActivePlaybackOnExit = true,
            )
        }
        val allItems = knownCatalogItems()
        val item = allItems.firstOrNull { it.id == requested.contentId }
        if (item == null) {
            val fixturePlan = fixturePlaybackPlan(requested)
            if (fixturePlan == null) {
                launchSafety.recoveryErrorFor(
                    PlaybackRecoveryEarlyFailure.CONTENT_UNAVAILABLE,
                )?.let { errorMessage ->
                    stopRecoveryBeforePreparation(requested.contentId, errorMessage)
                }
                return
            }
            pendingPlaybackSelection = PendingPlaybackSelectionSession(
                title = KinogoFixtures.catalog
                    .firstOrNull { it.id == requested.contentId }
                    ?.title
                    ?: requested.contentId,
                selection = requested.normalizedFor(fixturePlan),
                mediaPlan = fixturePlan,
                webFallbacks = emptyList(),
                initialPositionMs = 0L,
            )
            activePlayback = null
            activeEmbeddedPlayback = null
            return
        }
        val origin = activeMirrorOrigin
        if (origin == null) {
            launchSafety.recoveryErrorFor(
                PlaybackRecoveryEarlyFailure.MIRROR_UNAVAILABLE,
            )?.let { errorMessage ->
                stopRecoveryBeforePreparation(item.title, errorMessage)
            }
            return
        }
        pendingPlaybackSelection = null
        playbackLaunchGeneration++
        val launchGeneration = playbackLaunchGeneration
        playbackLaunchUi = PlaybackLaunchUiState(
            request = requested,
            title = item.title,
            discardActivePlaybackOnExit = launchSafety.discardActivePlaybackOnExit,
        )
        playbackLaunchJob = scope.launch {
            try {
                val fresh = try {
                    loadCatalogDetails(origin, item)
                } catch (firstError: CatalogNetworkException) {
                    Log.w(
                        APP_ROOT_LOG_TAG,
                        "Playback detail refresh failed; retrying once",
                        firstError,
                    )
                    delay(PLAYBACK_DETAIL_RETRY_DELAY_MS)
                    if (
                        launchGeneration != playbackLaunchGeneration ||
                        activeMirrorOrigin != origin
                    ) {
                        return@launch
                    }
                    loadCatalogDetails(origin, item)
                }
                if (
                    launchGeneration != playbackLaunchGeneration ||
                    activeMirrorOrigin != origin
                ) {
                    return@launch
                }
                checkpointWriteQueue.awaitIdle()
                if (
                    launchGeneration != playbackLaunchGeneration ||
                    activeMirrorOrigin != origin
                ) {
                    return@launch
                }
                val previousPlaybackDetails = liveDetailsById[requested.contentId]
                liveDetailsById = liveDetailsById + (
                    requested.contentId to fresh
                        .toPlaybackDetailsUiModel()
                        .preserveConfirmedPlaybackAvailability(previousPlaybackDetails)
                )
                persistHistorySnapshot(fresh.catalogItem)
                val documentUrl = resolvedPlaybackDocumentUrl(origin, fresh)
                val directPlan = resolveFreshDirectPlan(
                    resolver = directMediaResolver,
                    contentId = item.id,
                    documentOrigin = origin,
                    documentUrl = documentUrl,
                    candidates = fresh.playerEmbeds,
                    voiceover = fresh.metadata.findValue("Перевод") ?: "По умолчанию",
                    quality = fresh.metadata.findValue("Качество") ?: "Авто",
                )
                val preparationRequest = PlaybackPreparationRequest(
                    contentId = item.id,
                    title = fresh.catalogItem.title,
                    year = fresh.catalogItem.year ?: item.year,
                    originalTitle = fresh.catalogItem.originalTitle ?: item.originalTitle,
                    documentOrigin = origin,
                    documentUrl = documentUrl,
                    freshPageCandidates = fresh.playerEmbeds,
                    useOfficialDiscoveryFallback = directPlan == null,
                )
                val prepared = withContext(Dispatchers.Default) {
                    playbackPreparationService.prepare(preparationRequest)
                }
                if (
                    launchGeneration != playbackLaunchGeneration ||
                    activeMirrorOrigin != origin
                ) {
                    return@launch
                }
                val preparedSession =
                    (prepared as? PlaybackPreparationResult.Ready)?.session
                val nativePlans = listOfNotNull(
                    preparedSession?.nativePlan,
                    directPlan,
                )
                val plan = nativePlans
                    .takeIf(List<PlaybackMediaPlan>::isNotEmpty)
                    ?.let(NativePlaybackPlanMapper::merge)
                val webFallbacks = preparedSession?.webFallbacks.orEmpty()
                liveDetailsById[requested.contentId]?.let { cachedDetails ->
                    liveDetailsById = liveDetailsById + (
                        requested.contentId to cachedDetails.withPreparedPlaybackAvailability(
                            nativePlanReady = plan != null,
                            webFallbackReady = webFallbacks.isNotEmpty(),
                        )
                    )
                }

                if (plan == null && webFallbacks.isEmpty()) {
                    val message = fresh.playerNotice
                        ?.takeIf(String::isNotBlank)
                        ?: (prepared as? PlaybackPreparationResult.Unavailable)
                        ?.userMessage
                        ?: preparedSession?.notices?.firstOrNull()
                        ?: "Не найден совместимый источник воспроизведения"
                    if (launchSafety.discardActivePlaybackOnExit) activePlayback = null
                    liveDetailsById[requested.contentId]?.let { cachedDetails ->
                        liveDetailsById = liveDetailsById + (
                            requested.contentId to
                                cachedDetails.withPlaybackPreparationFailure()
                        )
                    }
                    playbackLaunchUi = PlaybackLaunchUiState(
                        request = requested,
                        title = item.title,
                        errorMessage = message,
                        discardActivePlaybackOnExit =
                            launchSafety.discardActivePlaybackOnExit,
                    )
                    return@launch
                }

                var effectiveSelection = requested
                var initialPosition = recovery?.positionMs ?: 0L
                if (recovery == null && requested.resume) {
                    try {
                        checkpointWriteQueue.awaitIdle()
                        val allProgress = PlaybackProgressCollection.normalize(
                            progressStore.list() + history,
                        )
                        history = allProgress
                        val saved = preferredResumeProgress(
                            entries = allProgress,
                            contentId = requested.contentId,
                        )
                        if (saved != null) {
                            effectiveSelection = saved.selection.toUiSelection(resume = true)
                            initialPosition = saved.resumePositionMs() ?: 0L
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        // Damaged/stale local history must not make an otherwise playable title fail.
                        Log.w(APP_ROOT_LOG_TAG, "Playback resume state could not be restored", error)
                    }
                }
                if (
                    recovery != null &&
                    plan != null
                ) {
                    val normalizedSelection = effectiveSelection.normalizedFor(plan)
                    if (isSamePlaybackUnit(requested, normalizedSelection)) {
                        val recoveredSelection = normalizedSelection.copy(resume = true)
                        val recoveredSourceId = recoveredSelection.sourceId ?: plan.defaultSourceId
                        playbackInitialPositionMs = recovery.positionMs
                        activePlayback = ActivePlaybackSession(
                            generation = ++playbackSessionGeneration,
                            selection = recoveredSelection,
                            mediaPlan = PlaybackSourceSelectionModel.preferSource(
                                plan,
                                recoveredSourceId,
                            ),
                            webFallbacks = webFallbacks,
                            automaticSourceRefreshAttempts =
                                launchSafety.automaticSourceRefreshAttempts,
                        )
                        pendingPlaybackSelection = null
                        activeEmbeddedPlayback = null
                        playbackLaunchUi = null
                        return@launch
                    }
                }
                if (recovery != null) initialPosition = 0L
                pendingPlaybackSelection = PendingPlaybackSelectionSession(
                    title = fresh.catalogItem.title,
                    // Preserve the saved/requested unit as the resume reference. The selector
                    // normalizes it against the fresh plan and enables the timestamp only when
                    // the effective movie/season/episode still matches this reference.
                    selection = effectiveSelection,
                    mediaPlan = plan,
                    webFallbacks = webFallbacks,
                    initialPositionMs = initialPosition,
                )
                activePlayback = null
                activeEmbeddedPlayback = null
                playbackLaunchUi = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (launchGeneration == playbackLaunchGeneration) {
                    Log.e(APP_ROOT_LOG_TAG, "Playback preparation failed", error)
                    if (launchSafety.discardActivePlaybackOnExit) activePlayback = null
                    playbackLaunchUi = PlaybackLaunchUiState(
                        request = requested,
                        title = item.title,
                        errorMessage = playbackPreparationErrorLabel(error),
                        discardActivePlaybackOnExit =
                            launchSafety.discardActivePlaybackOnExit,
                    )
                }
            }
        }
    }

    fun toggleFavorite(contentId: String) {
        val item = knownCatalogItems().firstOrNull { it.id == contentId } ?: return
        scope.launch {
            val enabled = libraryRecords.firstOrNull { it.item.id == contentId }?.favorite != true
            libraryRecords = libraryRepository.setFavorite(item, enabled)
            librarySyncPendingCount = libraryStore.pending().size
            val origin = activeMirrorOrigin
            val login = accountState.login
            if (origin != null && login != null) requestLibrarySync(origin, login)
        }
    }

    fun changeWatchStatus(contentId: String, status: WatchStatus?) {
        val item = knownCatalogItems().firstOrNull { it.id == contentId } ?: return
        scope.launch {
            libraryRecords = libraryRepository.setStatus(item, status)
            librarySyncPendingCount = libraryStore.pending().size
            val origin = activeMirrorOrigin
            val login = accountState.login
            if (origin != null && login != null) requestLibrarySync(origin, login)
        }
    }

    LaunchedEffect(Unit) {
        try {
            mirrorPreferences.manualOrigins().forEach(mirrorRegistry::addManual)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(APP_ROOT_LOG_TAG, "Manual mirror preferences read failed", error)
        }
        mirrorEntries = mirrorRegistry.all()
        try {
            history = PlaybackProgressCollection.normalize(history + progressStore.list())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(APP_ROOT_LOG_TAG, "Playback history read failed", error)
        }
        try {
            recentSearchQueries = searchHistoryStore.list()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(APP_ROOT_LOG_TAG, "Recent search history read failed", error)
        }
        try {
            libraryRecords = libraryStore.importLegacyFavorites(favoriteStore.list())
            favoriteStore.clear()
            librarySyncPendingCount = libraryStore.pending().size
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(APP_ROOT_LOG_TAG, "Local library migration failed", error)
        }
        val preferredOrigin = try {
            mirrorPreferences.selectedOrigin()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(APP_ROOT_LOG_TAG, "Selected mirror preference read failed at startup", error)
            null
        }
        requestMirrorRefresh(preferredOrigin)
    }

    LaunchedEffect(tvPreferencesSnapshot?.autoCheckUpdates) {
        if (AutomaticUpdateCheckPolicy.shouldStart(
                autoCheckPreference = tvPreferencesSnapshot?.autoCheckUpdates,
                alreadyStarted = automaticUpdateCheckStarted,
            )
        ) {
            automaticUpdateCheckStarted = true
            requestAppUpdateCheck(AppUpdateCheckOrigin.AUTOMATIC)
        }
    }

    val legacyHistoryIds = remember(history) {
        history
            .filter { it.contentSnapshot == null }
            .map { it.selection.contentId }
            .distinct()
    }
    LaunchedEffect(activeMirrorOrigin, legacyHistoryIds) {
        val origin = activeMirrorOrigin ?: return@LaunchedEffect
        legacyHistoryIds
            .filterNot(enrichingHistoryIds::contains)
            .forEach { contentId ->
            val candidate = legacyHistoryLookupItem(contentId) ?: return@forEach
            enrichingHistoryIds = enrichingHistoryIds + contentId
            try {
                val parsed = loadCatalogDetails(origin, candidate)
                if (activeMirrorOrigin == origin) persistHistorySnapshot(parsed.catalogItem)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(
                    APP_ROOT_LOG_TAG,
                    "Legacy history card $contentId could not be enriched",
                    error,
                )
            } finally {
                enrichingHistoryIds = enrichingHistoryIds - contentId
            }
        }
    }

    val historyCatalogItems = remember(
        homeFeed.items,
        catalogFeed.items,
        searchFeed.items,
        libraryRecords,
        history,
    ) {
        (
            homeFeed.items +
                catalogFeed.items +
                searchFeed.items +
                libraryRecords.map { it.item } +
                history.mapNotNull(WatchProgress::historyCatalogItem)
            )
            .distinctBy(CatalogItem::id)
            .associateBy(CatalogItem::id)
    }
    val historyUi = remember(history, historyCatalogItems) {
        history
            .distinctBy { it.selection.contentId }
            .map { progress ->
                toHistoryUiModel(progress, historyCatalogItems[progress.selection.contentId])
            }
    }
    val favoriteIds = remember(libraryRecords) {
        libraryRecords.filter(LibraryRecord::favorite).mapTo(linkedSetOf()) { it.item.id }
    }
    val homePosters = remember(homeFeed.items, favoriteIds) {
        homeFeed.items.map { item ->
            item.toPosterUiModel().copy(isFavorite = item.id in favoriteIds)
        }
    }
    val catalogPosters = remember(catalogFeed.items, favoriteIds) {
        catalogFeed.items.map { item ->
            item.toPosterUiModel().copy(isFavorite = item.id in favoriteIds)
        }
    }
    val searchPosters = remember(searchFeed.items, favoriteIds) {
        searchFeed.items.map { item ->
            item.toPosterUiModel().copy(isFavorite = item.id in favoriteIds)
        }
    }
    val favoritePosters = remember(libraryRecords) {
        libraryRecords.filter(LibraryRecord::favorite).map {
            it.item.toPosterUiModel().copy(isFavorite = true)
        }
    }
    val bookmarkUi = remember(libraryRecords) {
        libraryRecords.map { record ->
            BookmarkUiModel(
                poster = record.item.toPosterUiModel().copy(isFavorite = record.favorite),
                watchStatus = record.status,
                favorite = record.favorite,
            )
        }
    }
    val mirrorUiState = MirrorUiState(
        mirrors = mirrorEntries.map { it.toUiModel(activeMirrorOrigin) },
        isChecking = mirrorCheckInProgress,
        lastCheckedLabel = lastMirrorCheckLabel,
    )
    val detailsUiById = remember(liveDetailsById, history) {
        liveDetailsById.mapValues { (_, details) -> details.withLocalResume(history) }
    }

    val launchUi = playbackLaunchUi
    val selectorSession = pendingPlaybackSelection
    val embeddedPlaybackSession = activeEmbeddedPlayback
    val playbackSession = activePlayback
    if (launchUi != null) {
        PlaybackPreparationScreen(
            title = launchUi.title,
            errorMessage = launchUi.errorMessage,
            highContrast = tvPreferences.highContrast,
            onRetry = { startPlayback(launchUi.request) },
            onBack = {
                playbackLaunchJob?.cancel()
                playbackLaunchJob = null
                playbackLaunchGeneration++
                if (launchUi.discardActivePlaybackOnExit) activePlayback = null
                playbackLaunchUi = null
            },
        )
    } else if (selectorSession != null) {
        PlaybackSourceSelectionScreen(
            title = selectorSession.title,
            requestedSelection = selectorSession.selection,
            mediaPlan = selectorSession.mediaPlan,
            highContrast = tvPreferences.highContrast,
            webFallbacks = selectorSession.webFallbacks.map { fallback ->
                PlaybackWebFallbackUiModel(
                    id = fallback.id,
                    label = fallback.label,
                    providerLabel = fallback.providerId,
                )
            },
            resumePositionMs = selectorSession.initialPositionMs,
            onNativeSelected = nativeSelected@{ sourceId, selected ->
                val plan = selectorSession.mediaPlan ?: return@nativeSelected
                playbackInitialPositionMs = if (selected.resume) {
                    selectorSession.initialPositionMs
                } else {
                    0L
                }
                activePlayback = ActivePlaybackSession(
                    generation = ++playbackSessionGeneration,
                    selection = selected,
                    mediaPlan = PlaybackSourceSelectionModel.preferSource(plan, sourceId),
                    webFallbacks = selectorSession.webFallbacks,
                )
                activeEmbeddedPlayback = null
                pendingPlaybackSelection = null
            },
            onWebSelected = webSelected@{ fallbackId, selected ->
                val fallback = selectorSession.webFallbacks
                    .firstOrNull { it.id == fallbackId }
                    ?: return@webSelected
                activeEmbeddedPlayback = ActiveEmbeddedPlaybackSession(
                    selection = selected,
                    source = fallback,
                )
                activePlayback = null
                pendingPlaybackSelection = null
            },
            onBack = {
                pendingPlaybackSelection = null
                activePlayback = null
                activeEmbeddedPlayback = null
            },
        )
    } else if (embeddedPlaybackSession != null) {
        val contentId = embeddedPlaybackSession.selection.contentId
        val title = knownCatalogItems()
            .firstOrNull { it.id == contentId }
            ?.title
            ?: contentId
        ProviderEmbedPlayerScreen(
            source = embeddedPlaybackSession.source,
            title = title,
            seekStepSeconds = tvPreferences.seekStepSeconds,
            onRefreshSourceRequested = {
                startPlayback(embeddedPlaybackSession.selection.copy(resume = true))
            },
            onExit = { activeEmbeddedPlayback = null },
        )
    } else if (playbackSession != null) {
        val playback = playbackSession.selection
        val title = knownCatalogItems()
            .firstOrNull { it.id == playback.contentId }
            ?.title
            ?: KinogoFixtures.catalog.firstOrNull { it.id == playback.contentId }?.title
            ?: playback.contentId
        TvPlayerScreen(
            selection = playback,
            mediaPlan = playbackSession.mediaPlan,
            title = title,
            initialPositionMs = playbackInitialPositionMs,
            playbackSessionGeneration = playbackSession.generation,
            preferences = tvPreferences,
            onCheckpoint = checkpoint@{ checkpoint: PlaybackCheckpoint ->
                if (
                    !acceptsPlaybackCheckpoint(
                        activeGeneration = activePlayback?.generation,
                        callbackGeneration = playbackSession.generation,
                    )
                ) {
                    return@checkpoint
                }
                if (
                    checkpoint.positionMs > 0L ||
                    (
                        checkpoint.selection.season != null &&
                            checkpoint.selection.episode != null
                        )
                ) {
                    val progress = WatchProgress(
                        selection = checkpoint.selection.toDomainSelection(),
                        positionMs = checkpoint.positionMs,
                        durationMs = checkpoint.durationMs.takeIf { it > 0L },
                        updatedAtEpochMs = nextCheckpointTimestampMs(),
                        playbackEnded = checkpoint.playbackEnded,
                        contentSnapshot = knownCatalogItems()
                            .firstOrNull { it.id == checkpoint.selection.contentId },
                    )
                    // The Details screen is restored synchronously after exit; publish the exact
                    // checkpoint before Media3 is removed, then serialize the durable writes.
                    history = PlaybackProgressCollection.upsert(history, progress)
                    checkpointWriteQueue.enqueue(scope) {
                        try {
                            progressStore.upsert(progress)
                            history = PlaybackProgressCollection.normalize(
                                history + progressStore.list(),
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Log.e(APP_ROOT_LOG_TAG, "Playback checkpoint persistence failed", error)
                        }
                    }
                }
            },
            automaticSourceRefreshAttempts =
                playbackSession.automaticSourceRefreshAttempts,
            onAutomaticSourceRefreshRequested = { request ->
                startPlayback(request.selection, recovery = request)
            },
            onExit = exit@{
                if (
                    !acceptsPlaybackCheckpoint(
                        activeGeneration = activePlayback?.generation,
                        callbackGeneration = playbackSession.generation,
                    )
                ) {
                    return@exit
                }
                activePlayback = null
            },
        )
    } else {
        KinogoTvApp(
            initialDestination = currentDestination,
            initialDetailsId = playbackReturnDetailsId,
            homeCatalog = homePosters,
            history = historyUi,
            mirrorState = mirrorUiState,
            catalog = catalogPosters,
            favorites = favoritePosters,
            bookmarks = bookmarkUi,
            favoriteIds = favoriteIds,
            watchStatusById = libraryRecords.mapNotNull { record ->
                record.status?.let { record.item.id to it }
            }.toMap(),
            detailsById = detailsUiById,
            catalogHasMore = catalogFeed.nextPage != null,
            catalogLoading = catalogFeed.loading ||
                (activeMirrorOrigin == null && mirrorCheckInProgress),
            catalogError = catalogFeed.error,
            catalogControls = catalogFeed.controls,
            catalogCategory = catalogFeed.query?.category ?: CatalogCategory.NEW_RELEASES,
            catalogFilters = catalogFeed.query?.filters ?: CatalogBrowseFilters(),
            homeHasMore = homeFeed.nextPage != null,
            homeLoading = homeFeed.loading ||
                (activeMirrorOrigin == null && mirrorCheckInProgress),
            homeError = if (mirrorCheckInProgress || homeFeed.loading) {
                null
            } else {
                startupError ?: homeFeed.error
            },
            homeControls = homeFeed.controls,
            homeFilters = homeFeed.query?.filters ?: CatalogBrowseFilters(),
            catalogStatusLabel = null,
            searchResults = searchPosters,
            searchLoading = searchFeed.loading,
            searchError = searchFeed.error,
            searchHasMore = searchFeed.nextPage != null,
            searchQuery = searchInputQuery,
            recentSearchQueries = recentSearchQueries,
            searchFocusedItemId = searchFocusedItemId,
            searchResultsQuery = searchFeed.query?.normalizedSearchTerm,
            homeFocusedItemId = homeFocusedItemId,
            catalogFocusedItemId = catalogFocusedItemId,
            bookmarksFocusedItemId = bookmarksFocusedItemId,
            historyFocusedItemId = historyFocusedItemId,
            bookmarksFilter = bookmarksFilter,
            useRemoteCatalog = true,
            onPlayRequested = ::startPlayback,
            onCatalogLoadMore = {
                val origin = activeMirrorOrigin
                val query = catalogFeed.query
                val page = catalogFeed.nextPage
                if (origin != null && query != null && page != null) {
                    requestFeedPage(
                        kind = CatalogFeedKind.CATALOG,
                        origin = origin,
                        query = query,
                        page = page,
                        reset = false,
                    )
                }
            },
            onCatalogRetry = {
                activeMirrorOrigin?.let { origin ->
                    val query = catalogFeed.query
                        ?: CatalogQuery(category = CatalogCategory.NEW_RELEASES)
                    val reset = catalogFeed.nextPage == null
                    requestFeedPage(
                        kind = CatalogFeedKind.CATALOG,
                        origin = origin,
                        query = query,
                        page = if (reset) 1 else catalogFeed.nextPage ?: 1,
                        reset = reset,
                    )
                }
            },
            onHomeRetry = {
                val origin = activeMirrorOrigin
                if (origin == null) {
                    requestMirrorRefresh()
                } else {
                    val query = homeFeed.query ?: CatalogQuery()
                    val reset = homeFeed.nextPage == null
                    requestFeedPage(
                        kind = CatalogFeedKind.HOME,
                        origin = origin,
                        query = query,
                        page = if (reset) 1 else homeFeed.nextPage ?: 1,
                        reset = reset,
                    )
                }
            },
            onHomeLoadMore = {
                val origin = activeMirrorOrigin
                val query = homeFeed.query
                val page = homeFeed.nextPage
                if (origin != null && query != null && page != null) {
                    requestFeedPage(
                        kind = CatalogFeedKind.HOME,
                        origin = origin,
                        query = query,
                        page = page,
                        reset = false,
                    )
                }
            },
            onHomeFiltersChanged = { filters ->
                val current = homeFeed.query ?: CatalogQuery()
                if (filters != current.filters) {
                    homeFocusedItemId = null
                    activeMirrorOrigin?.let { origin ->
                        requestFeedPage(
                            kind = CatalogFeedKind.HOME,
                            origin = origin,
                            query = current.copy(filters = filters, page = 1),
                            page = 1,
                            reset = true,
                        )
                    }
                }
            },
            onDestinationChanged = { destination ->
                currentDestination = destination
                if (destination == TvDestination.Catalog) {
                    val origin = activeMirrorOrigin
                    if (
                        origin != null &&
                        !catalogFeed.loading &&
                        (catalogFeed.origin != origin || catalogFeed.items.isEmpty())
                    ) {
                        requestFeedPage(
                            kind = CatalogFeedKind.CATALOG,
                            origin = origin,
                            query = catalogFeed.query
                                ?: CatalogQuery(category = CatalogCategory.NEW_RELEASES),
                            page = 1,
                            reset = true,
                        )
                    }
                }
            },
            onDetailsRequested = ::requestDetails,
            onCatalogCategorySelected = { category ->
                val current = catalogFeed.query
                    ?: CatalogQuery(category = CatalogCategory.NEW_RELEASES)
                if (category != current.category) {
                    catalogFocusedItemId = null
                    activeMirrorOrigin?.let { origin ->
                        requestFeedPage(
                            kind = CatalogFeedKind.CATALOG,
                            origin = origin,
                            query = current.copy(
                                category = category,
                                filters = CatalogBrowseFilters(),
                                page = 1,
                            ),
                            page = 1,
                            reset = true,
                        )
                    }
                }
            },
            onCatalogFiltersChanged = { filters ->
                val current = catalogFeed.query
                    ?: CatalogQuery(category = CatalogCategory.NEW_RELEASES)
                if (filters != current.filters) {
                    catalogFocusedItemId = null
                    activeMirrorOrigin?.let { origin ->
                        requestFeedPage(
                            kind = CatalogFeedKind.CATALOG,
                            origin = origin,
                            query = current.copy(filters = filters, page = 1),
                            page = 1,
                            reset = true,
                        )
                    }
                }
            },
            onSearchInputChanged = { value -> searchInputQuery = value },
            onSearchQueryChanged = ::requestSearch,
            onSearchCommitted = ::recordCommittedSearch,
            onSearchFocusedItemChanged = { contentId ->
                searchFocusedItemId = contentId
            },
            onHomeFocusedItemChanged = { contentId ->
                homeFocusedItemId = contentId
            },
            onCatalogFocusedItemChanged = { contentId ->
                catalogFocusedItemId = contentId
            },
            onBookmarksFocusedItemChanged = { contentId ->
                bookmarksFocusedItemId = contentId
            },
            onHistoryFocusedItemChanged = { contentId ->
                historyFocusedItemId = contentId
            },
            onBookmarksFilterSelected = { filter ->
                if (bookmarksFilter != filter) {
                    bookmarksFilter = filter
                    bookmarksFocusedItemId = null
                }
            },
            onSearchLoadMore = {
                val origin = activeMirrorOrigin
                val query = searchFeed.query
                val page = searchFeed.nextPage
                if (origin != null && query != null && page != null) {
                    requestFeedPage(
                        kind = CatalogFeedKind.SEARCH,
                        origin = origin,
                        query = query,
                        page = page,
                        reset = false,
                    )
                }
            },
            onFavoriteToggle = ::toggleFavorite,
            onWatchStatusChange = ::changeWatchStatus,
            onCheckMirrors = { requestMirrorRefresh() },
            onManualMirrorSubmitted = { rawOrigin ->
                scope.launch {
                    val origin = mirrorPreferences.addManual(rawOrigin)
                    mirrorRegistry.addManual(origin)
                    mirrorEntries = mirrorRegistry.all()
                    requestMirrorRefresh(origin)
                }
            },
            onMirrorSelected = { origin ->
                val entry = mirrorRegistry.get(origin)
                if (entry != null && mirrorRegistry.isEligible(entry.origin)) {
                    val mirrorChanged = activeMirrorOrigin != entry.origin
                    activeMirrorOrigin = entry.origin
                    if (mirrorChanged) {
                        contentGeneration++
                        registrationJob?.cancel()
                        registrationJob = null
                        registrationGeneration++
                        registrationPage = null
                        registrationRulesPage = null
                        registrationUi = null
                        liveDetailsById = emptyMap()
                        loadingDetailIds = emptySet()
                        searchFeed = CatalogFeedState(
                            generation = searchFeed.generation + 1L,
                        )
                        catalogFeed = CatalogFeedState(
                            query = CatalogQuery(
                                category = catalogFeed.query?.category
                                    ?: CatalogCategory.NEW_RELEASES,
                            ),
                            controls = CatalogControls(
                                categories = catalogFeed.controls.categories,
                            ),
                            generation = catalogFeed.generation + 1L,
                        )
                    }
                    if (mirrorChanged || homeFeed.origin != entry.origin) {
                        requestFeedPage(
                            kind = CatalogFeedKind.HOME,
                            origin = entry.origin,
                            query = if (mirrorChanged) CatalogQuery() else {
                                homeFeed.query ?: CatalogQuery()
                            },
                            page = 1,
                            reset = true,
                        )
                    }
                    if (
                        currentDestination == TvDestination.Catalog &&
                        (mirrorChanged || catalogFeed.origin != entry.origin)
                    ) {
                        requestFeedPage(
                            kind = CatalogFeedKind.CATALOG,
                            origin = entry.origin,
                            query = if (mirrorChanged) {
                                CatalogQuery(
                                    category = catalogFeed.query?.category
                                        ?: CatalogCategory.NEW_RELEASES,
                                )
                            } else {
                                catalogFeed.query
                                    ?: CatalogQuery(category = CatalogCategory.NEW_RELEASES)
                            },
                            page = 1,
                            reset = true,
                        )
                    }
                    scope.launch {
                        mirrorPreferences.setSelectedOrigin(entry.origin)
                        sessionManager.restore(entry.origin)?.let { session ->
                            requestLibrarySync(entry.origin, session.login)
                        }
                    }
                }
            },
            onMirrorRetry = { origin -> requestMirrorRefresh(origin) },
            onHistoryDelete = { contentId ->
                if (historyFocusedItemId == contentId) historyFocusedItemId = null
                checkpointWriteQueue.enqueue(scope) {
                    try {
                        progressStore.deleteContent(contentId)
                        history = progressStore.list()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.e(APP_ROOT_LOG_TAG, "History item deletion failed", error)
                    }
                }
            },
            onHistoryClear = {
                historyFocusedItemId = null
                checkpointWriteQueue.enqueue(scope) {
                    try {
                        progressStore.clear()
                        history = emptyList()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.e(APP_ROOT_LOG_TAG, "History clear failed", error)
                    }
                }
            },
            accountState = accountState,
            pendingSyncCount = librarySyncPendingCount,
            syncMessage = librarySyncMessage
                ?: "С сайтом синхронизируются только закладки; история и позиции просмотра " +
                    "хранятся локально на этом ТВ",
            onAccountLogin = { rawLogin, password ->
                val origin = activeMirrorOrigin
                val login = rawLogin.trim()
                if (login.isNotEmpty() && password.isNotEmpty()) {
                    scope.launch {
                        if (accountState.login != null && accountState.login != login) {
                            libraryStore.clearAccountData()
                            libraryRecords = emptyList()
                            librarySyncPendingCount = 0
                        }
                        val credentials = StoredCredentials(login, password)
                        if (origin == null) {
                            sessionManager.saveForLater(credentials)
                            librarySyncMessage =
                                "Данные входа сохранены; ожидаем проверенное зеркало"
                        } else {
                            val session = sessionManager.saveAndLogin(origin, credentials)
                            if (session != null) requestLibrarySync(origin, session.login)
                        }
                    }
                }
            },
            onAccountReconnect = {
                val origin = activeMirrorOrigin
                if (origin != null) {
                    scope.launch {
                        sessionManager.reauthenticate(origin)?.let { session ->
                            requestLibrarySync(origin, session.login)
                        }
                    }
                }
            },
            onAccountRemove = {
                scope.launch {
                    sessionManager.removeSavedAccount()
                    libraryStore.clearAccountData()
                    libraryRecords = emptyList()
                    librarySyncPendingCount = 0
                    librarySyncMessage = "Данные аккаунта удалены с устройства"
                }
            },
            onSyncNow = {
                val origin = activeMirrorOrigin
                val login = accountState.login
                if (origin != null && login != null) requestLibrarySync(origin, login)
            },
            registrationState = registrationUi,
            onRegistrationOpen = ::requestRegistration,
            onRegistrationDismiss = {
                registrationJob?.cancel()
                registrationJob = null
                registrationGeneration++
                registrationPage = null
                registrationRulesPage = null
                registrationUi = null
            },
            onRegistrationRetry = ::requestRegistration,
            onRegistrationAcceptRules = ::acceptRegistrationRules,
            onRegistrationRefreshCaptcha = ::requestRegistration,
            onRegistrationSubmit = ::submitRegistration,
            appUpdate = appUpdateUi,
            onUpdateAction = ::handleAppUpdateAction,
            showAppUpdatePrompt = showAutomaticUpdatePrompt,
            onUpdatePromptDismiss = { showAutomaticUpdatePrompt = false },
            appVersionName = BuildConfig.VERSION_NAME,
            onDonateOpen = {
                activity?.openTrustedExternalUrl(DONATE_URL)
            },
            onRepositoryOpen = {
                activity?.openTrustedExternalUrl(REPOSITORY_URL)
            },
            settingsSections = KinogoFixtures.settings.withPreferences(tvPreferences),
            highContrast = tvPreferences.highContrast,
            reduceMotion = tvPreferences.reduceMotion,
            defaultQuality = tvPreferences.defaultQuality,
            onSettingSelected = { settingId, optionId ->
                scope.launch { tvPreferencesStore.set(settingId, optionId) }
            },
            onExitConfirmed = { activity?.finish() },
        )
    }
}

private fun fixturePlaybackPlan(selection: PlaybackSelectionUiModel): PlaybackMediaPlan? {
    if (KinogoFixtures.catalog.none { it.id == selection.contentId }) return null
    val isEpisodic = selection.season != null && selection.episode != null
    val variants = if (isEpisodic) {
        (1..maxOf(12, requireNotNull(selection.episode))).map { episode ->
            PlaybackMediaVariant(
                id = "fixture:${selection.contentId}:e$episode",
                episodeNumber = episode,
                voiceover = DEVELOPMENT_FIXTURE_VOICE,
                quality = DEVELOPMENT_FIXTURE_QUALITY,
                mediaUrl = DEVELOPMENT_FIXTURE_VIDEO_URL,
                mimeType = "video/mp4",
            )
        }
    } else {
        listOf(
            PlaybackMediaVariant(
                id = "fixture:${selection.contentId}:film",
                episodeNumber = null,
                voiceover = DEVELOPMENT_FIXTURE_VOICE,
                quality = DEVELOPMENT_FIXTURE_QUALITY,
                mediaUrl = DEVELOPMENT_FIXTURE_VIDEO_URL,
                mimeType = "video/mp4",
            ),
        )
    }
    return PlaybackMediaPlan(variants)
}

private fun RegistrationPage.toRegistrationUiModel(
    phase: RegistrationUiPhase = RegistrationUiPhase.READY,
    message: String? = this.message,
): RegistrationUiModel = RegistrationUiModel(
    phase = phase,
    message = message,
    captchaBytes = captchaImage?.bytes,
    captchaMimeType = captchaImage?.mimeType,
    requiresCaptcha = form.captcha?.kind == RegistrationCaptchaKind.IMAGE,
    requiresConsent = form.consent != null,
    consentLabel = form.consent?.label ?: "Я принимаю правила сайта",
)

private fun Activity.openTrustedExternalUrl(rawUrl: String) {
    if (rawUrl !in setOf(DONATE_URL, REPOSITORY_URL)) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl))
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Android TV builds without a browser keep the About dialog open instead of crashing.
    }
}

internal suspend fun resolveFreshDirectPlan(
    resolver: DirectMediaResolver,
    contentId: String,
    documentOrigin: String,
    documentUrl: String,
    candidates: List<PlayerEmbedCandidate>,
    voiceover: String,
    quality: String,
): PlaybackMediaPlan? {
    candidates.forEach { candidate ->
        if (!resolver.supports(candidate)) return@forEach
        when (
            val result = resolver.resolve(
                PlaybackSourceRequest(
                    contentId = contentId,
                    documentOrigin = documentOrigin,
                    documentUrl = documentUrl,
                    candidate = candidate,
                ),
            )
        ) {
            is PlaybackSourceResolution.Resolved -> {
                val source = result.source
                return PlaybackMediaPlan(
                    listOf(
                        PlaybackMediaVariant(
                            id = source.id,
                            // providerId is display metadata parsed from untrusted HTML and may
                            // contain a mirror hostname or arbitrary text. Only the internal
                            // resolver id is stable and safe to persist in playback checkpoints.
                            sourceId = resolver.id,
                            sourceLabel = source.label,
                            episodeNumber = null,
                            voiceover = voiceover,
                            quality = quality,
                            mediaUrl = source.mediaUrl,
                            mimeType = source.mimeType,
                        ),
                    ),
                )
            }
            is PlaybackSourceResolution.Embedded,
            is PlaybackSourceResolution.Rejected,
            is PlaybackSourceResolution.Unsupported,
            -> Unit
        }
    }
    return null
}

private fun PlaybackSelectionUiModel.normalizedFor(
    plan: PlaybackMediaPlan,
): PlaybackSelectionUiModel =
    PlaybackSourceSelectionModel
        .initial(
            plan = plan,
            requested = this,
        )
        .toPlaybackSelection(this)

internal fun PlaybackSelectionUiModel.toDomainSelection(): PlaybackSelection {
    val hasEpisode = season != null && episode != null
    return PlaybackSelection(
        contentId = contentId,
        seasonId = if (hasEpisode) "season-$season" else null,
        episodeId = if (hasEpisode) "episode-$episode" else null,
        voiceId = voiceover,
        qualityId = quality,
        sourceId = sourceId,
    )
}

internal fun PlaybackSelection.toUiSelection(resume: Boolean): PlaybackSelectionUiModel =
    PlaybackSelectionUiModel(
        contentId = contentId,
        season = seasonId?.trailingNumber(),
        episode = episodeId?.trailingNumber(),
        voiceover = voiceId,
        quality = qualityId,
        resume = resume,
        sourceId = sourceId,
    )

internal fun WatchProgress.historyCatalogItem(): CatalogItem? =
    contentSnapshot ?: legacyHistoryLookupItem(selection.contentId)

/**
 * One resume policy is shared by History, Catalog and Search entry points. The newest unfinished
 * playback unit wins; a completed checkpoint for a default first episode must never hide a later
 * unfinished episode.
 */
internal fun preferredResumeProgress(
    entries: Collection<WatchProgress>,
    contentId: String,
): WatchProgress? {
    val latestActiveUnit = entries
        .asSequence()
        .filter { it.selection.contentId == contentId }
        .filter { progress ->
            progress.resumePositionMs() != null ||
                progress.isCompleted() ||
                (progress.selection.isEpisode && progress.positionMs == 0L)
        }
        .maxByOrNull(WatchProgress::updatedAtEpochMs)
    return latestActiveUnit?.takeUnless { it.isCompleted() }
}

/** Every Details entry point receives the same local checkpoint-derived Continue action. */
internal fun com.kinogo.atv.ui.model.DetailsUiModel.withLocalResume(
    entries: List<WatchProgress>,
): com.kinogo.atv.ui.model.DetailsUiModel =
    preferredResumeProgress(entries, id)
        ?.let { progress -> copy(resumeLabel = resumeActionLabel(progress)) }
        ?: this

internal fun resumeActionLabel(progress: WatchProgress): String {
    val position = formatClock(progress.boundedPositionMs)
    val season = progress.selection.seasonId?.trailingNumber()
    val episode = progress.selection.episodeId?.trailingNumber()
    return if (season != null && episode != null) {
        if (progress.boundedPositionMs == 0L) {
            "Продолжить S%02dE%02d".format(season, episode)
        } else {
            "Продолжить S%02dE%02d с %s".format(season, episode, position)
        }
    } else {
        "Продолжить с $position"
    }
}

/**
 * A constrained probe lets the legacy resolver recover a canonical card path after the old search
 * result has disappeared. Only a positive numeric id is accepted, so persisted text can never
 * become an arbitrary request path.
 */
internal fun legacyHistoryLookupItem(contentId: String): CatalogItem? =
    LegacyHistoryDetailsResolver.probeItem(contentId)

/**
 * A persisted history snapshot may contain a formerly canonical slug that no longer exists on the
 * active mirror. Only a 404/410 for a strict numeric post id may fall back to the constrained
 * legacy resolver; unrelated HTTP failures and non-history-style ids keep their original error.
 */
internal suspend fun loadCatalogDetailsWithLegacyFallback(
    origin: String,
    item: CatalogItem,
    repository: CatalogRepository,
    legacyResolver: LegacyHistoryDetailsResolver,
): ParsedContentPage {
    if (LegacyHistoryDetailsResolver.isProbeItem(item)) {
        return legacyResolver.resolve(origin, item.id)
    }
    return try {
        repository.loadDetails(origin, item)
    } catch (error: CatalogHttpStatusException) {
        val canRecover =
            error.statusCode in LEGACY_HISTORY_RECOVERABLE_HTTP_STATUSES &&
                LegacyHistoryDetailsResolver.probeItem(item.id) != null
        if (!canRecover) throw error
        legacyResolver.resolve(origin, item.id)
    }
}

/**
 * Playback must use the canonical path returned by the freshly parsed details page. A legacy
 * history probe can resolve through a 404/410 suggestion, so its requested placeholder path is
 * not necessarily a valid document URL or Referer for source discovery.
 */
internal fun resolvedPlaybackDocumentUrl(
    origin: String,
    freshDetails: ParsedContentPage,
): String = "$origin${freshDetails.catalogItem.relativePath}"

private val LEGACY_HISTORY_RECOVERABLE_HTTP_STATUSES = setOf(404, 410)

private fun String.trailingNumber(): Int? =
    takeLastWhile(Char::isDigit).toIntOrNull()

private fun formatClock(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun toHistoryUiModel(
    progress: WatchProgress,
    catalogItem: CatalogItem?,
): HistoryUiModel {
    val fixturePoster = KinogoFixtures.catalog.firstOrNull { it.id == progress.selection.contentId }
    val fraction = (progress.progressFraction ?: 0.0).toFloat().coerceIn(0f, 1f)
    val poster = (catalogItem?.toPosterUiModel() ?: fixturePoster ?: PosterUiModel(
        id = progress.selection.contentId,
        title = progress.selection.contentId,
        subtitle = "История просмотра",
    )).copy(progress = fraction)
    val season = progress.selection.seasonId?.trailingNumber()
    val episode = progress.selection.episodeId?.trailingNumber()
    val episodeLabel = if (season != null && episode != null) {
        "Сезон $season, серия $episode"
    } else {
        "Фильм"
    }

    return HistoryUiModel(
        id = "history-${progress.selection.contentId}-${progress.selection.playbackUnitId}",
        poster = poster,
        episodeLabel = episodeLabel,
        positionLabel = "${formatMinutes(progress.boundedPositionMs)} из " +
            formatMinutes(progress.durationMs ?: 0L),
        lastWatchedLabel = relativeDateLabel(progress.updatedAtEpochMs),
        progress = fraction,
    )
}

private fun MirrorEntry.toUiModel(activeOrigin: String?): MirrorUiModel {
    val health = lastHealth
    val status = when {
        origin == activeOrigin -> MirrorStatusUi.Active
        isTrusted && health?.isUsable == true -> MirrorStatusUi.Available
        trustState == MirrorTrustState.QUARANTINED || health == null -> MirrorStatusUi.Quarantined
        else -> MirrorStatusUi.Error
    }
    val detail = when (health?.status) {
        MirrorHealthStatus.HEALTHY -> "Отпечаток сервиса подтверждён"
        MirrorHealthStatus.DEGRADED -> "Доступно, но отвечает медленно"
        MirrorHealthStatus.REDIRECTED -> health.redirectOrigin
            ?.let { "Перенаправляет на $it" }
            ?: "Обнаружен другой конечный адрес"
        MirrorHealthStatus.CHALLENGE_REQUIRED -> "Требуется проверка в браузере"
        MirrorHealthStatus.UNREACHABLE -> health.diagnostic
            ?: "Нет ответа или адрес не прошёл безопасную проверку"
        MirrorHealthStatus.INVALID_CONTENT -> "Отпечаток сервиса не совпал"
        null -> "Ожидает безопасной проверки"
    }
    return MirrorUiModel(
        id = origin,
        url = origin,
        status = status,
        statusDetail = detail,
        latencyMs = health?.latencyMs?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
        isManual = source == MirrorSource.MANUAL,
        httpStatusCode = health?.httpStatusCode,
        checkedAtEpochMs = health?.checkedAtEpochMs,
        redirectOrigin = health?.redirectOrigin,
        diagnostic = health?.diagnostic,
    )
}

private fun formatMinutes(milliseconds: Long): String {
    val minutes = (milliseconds.coerceAtLeast(0L) / 60_000L)
    return "$minutes мин"
}

private fun relativeDateLabel(timestampMs: Long): String {
    val ageMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val days = ageMs / (24L * 60L * 60L * 1_000L)
    return when (days) {
        0L -> "Сегодня"
        1L -> "Вчера"
        else -> "$days дн. назад"
    }
}

private fun Map<String, String>.findValue(label: String): String? =
    entries.firstOrNull { (key, _) ->
        key.trim().trimEnd(':').equals(label, ignoreCase = true)
    }?.value?.takeIf(String::isNotBlank)

private fun catalogErrorLabel(error: Throwable): String = when (error) {
    is CatalogChallengeException -> "Зеркало требует проверку в браузере"
    is CatalogRedirectException -> "Зеркало перенаправляет на непроверенный адрес"
    is CatalogFingerprintException -> "Структура страницы не прошла проверку"
    is CatalogException -> error.message ?: "Ошибка загрузки каталога"
    else -> "Не удалось загрузить каталог"
}

private fun mirrorRefreshErrorLabel(error: Throwable): String = when (error) {
    is CatalogChallengeException -> "Зеркало требует проверку в браузере"
    is CatalogRedirectException -> "Зеркало перенаправляет на непроверенный адрес"
    is CatalogFingerprintException -> "Структура зеркала не прошла проверку"
    else -> "Не удалось проверить зеркала. Проверьте сеть и повторите попытку."
}

private fun playbackPreparationErrorLabel(error: Throwable): String = when (error) {
    is CatalogChallengeException -> "Зеркало требует проверку в браузере"
    is CatalogRedirectException -> "Зеркало сменило адрес во время подготовки плеера"
    is CatalogFingerprintException -> "Страница фильма не прошла проверку"
    is CatalogException -> error.message ?: "Не удалось обновить страницу фильма"
    else -> "Не удалось получить источники просмотра. Повторите попытку."
}
