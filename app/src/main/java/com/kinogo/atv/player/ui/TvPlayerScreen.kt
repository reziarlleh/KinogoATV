@file:androidx.annotation.OptIn(
    markerClass = [androidx.media3.common.util.UnstableApi::class],
)

package com.kinogo.atv.player.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.accessibility.CaptioningManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kinogo.atv.domain.PlaybackMediaPlan
import com.kinogo.atv.domain.PlaybackMediaVariant
import com.kinogo.atv.domain.PlaybackEpisodeCoordinate
import com.kinogo.atv.domain.TvPreferences
import com.kinogo.atv.player.EpisodeDirection
import com.kinogo.atv.player.EpisodeNumberInput
import com.kinogo.atv.player.Media3PlayerController
import com.kinogo.atv.player.Media3PlayerHost
import com.kinogo.atv.player.PlaybackCompletionDecision
import com.kinogo.atv.player.PlaybackBufferConfiguration
import com.kinogo.atv.player.PlaybackBufferPolicy
import com.kinogo.atv.player.PlaybackErrorRecoveryDecision
import com.kinogo.atv.player.PlaybackItemTransitionCompletion
import com.kinogo.atv.player.PlaybackPauseCompletion
import com.kinogo.atv.player.PlaybackQualityCandidate
import com.kinogo.atv.player.PlaybackQualityPolicy
import com.kinogo.atv.player.PlayerDrawer
import com.kinogo.atv.player.PlayerHudFocusTarget
import com.kinogo.atv.player.PlayerHudState
import com.kinogo.atv.player.PlayerIntent
import com.kinogo.atv.player.PlayerKeyReleaseGuard
import com.kinogo.atv.player.PlayerPlaybackState
import com.kinogo.atv.player.PlayerReducerConfig
import com.kinogo.atv.player.PlayerTimeoutKind
import com.kinogo.atv.player.RemoteKeySource
import com.kinogo.atv.player.SafePlaybackDataSources
import com.kinogo.atv.player.SeekDirection
import com.kinogo.atv.player.TvPlayerKeyMapper
import com.kinogo.atv.player.TvPlayerReducer
import com.kinogo.atv.player.TvPlayerState
import com.kinogo.atv.player.VisibleHudKeyKind
import com.kinogo.atv.player.completedPlaybackCheckpoint
import com.kinogo.atv.player.playbackItemTransitionCompletion
import com.kinogo.atv.player.playbackPauseCompletion
import com.kinogo.atv.player.preferredForQuality
import com.kinogo.atv.player.shouldDispatchVisibleHudKeyAtRoot
import com.kinogo.atv.player.playbackCompletionDecision
import com.kinogo.atv.player.playbackErrorRecoveryDecision
import com.kinogo.atv.player.toPlayerReducerConfig
import com.kinogo.atv.player.withSubtitlePreference
import com.kinogo.atv.player.withVideoQualityIntent
import com.kinogo.atv.ui.model.PlaybackSelectionUiModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

private const val CHECKPOINT_INTERVAL_MS = 10_000L
private const val TIMELINE_REFRESH_MS = 500L

/**
 * Full-screen TV-only player. It consumes an already resolved [PlaybackMediaPlan]; provider HTML
 * never enters this layer. Media buttons enter through [MediaSession] to avoid duplicate commands.
 */
@Composable
fun TvPlayerScreen(
    selection: PlaybackSelectionUiModel,
    mediaPlan: PlaybackMediaPlan,
    initialPositionMs: Long,
    onCheckpoint: (PlaybackCheckpoint) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    playbackSessionGeneration: Long = 0L,
    automaticSourceRefreshAttempts: Set<PlaybackSourceRefreshUnitKey> = emptySet(),
    onAutomaticSourceRefreshRequested: ((PlaybackSourceRefreshRequest) -> Unit)? = null,
    title: String = selection.contentId,
    preferences: TvPreferences = TvPreferences(),
) {
    val appContext = LocalContext.current.applicationContext
    val composeView = LocalView.current
    val lifecycleOwner = remember(composeView) { composeView.findViewTreeLifecycleOwner() }
    val scope = rememberCoroutineScope()
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val timelineFocus = remember { FocusRequester() }
    val seasonFocus = remember { FocusRequester() }
    val voiceoverFocus = remember { FocusRequester() }
    val qualityFocus = remember { FocusRequester() }
    val subtitlesFocus = remember { FocusRequester() }
    val sourceFocus = remember { FocusRequester() }
    val drawerFocus = remember { FocusRequester() }
    var rootFocused by remember { mutableStateOf(false) }
    val systemCaptionsEnabled = remember(appContext) {
        (appContext.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager)
            ?.isEnabled == true
    }
    val textTracksEnabled = preferences.subtitles.textTrackEnabled(systemCaptionsEnabled)
    val reducerConfig = remember(preferences.seekStepMs) {
        preferences.toPlayerReducerConfig()
    }
    val bufferConfiguration = remember(preferences.playbackBufferSeconds) {
        PlaybackBufferPolicy.forSeconds(preferences.playbackBufferSeconds)
    }
    val loadControl = remember(bufferConfiguration) {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferConfiguration.targetBufferMs,
                bufferConfiguration.targetBufferMs,
                bufferConfiguration.playbackStartBufferMs,
                bufferConfiguration.rebufferStartBufferMs,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }
    val mediaSourceFactory = remember(mediaPlan.mediaUrlResolver) {
        DefaultMediaSourceFactory(
            SafePlaybackDataSources.createFactory(mediaPlan.mediaUrlResolver),
        )
    }

    // Do not key by initialPositionMs: checkpoint persistence may update that value while this
    // screen is composed and must never recreate the player mid-playback.
    val player = remember(
        playbackSessionGeneration,
        selection.contentId,
        selection.season,
        selection.episode,
        mediaPlan,
        preferences.seekStepMs,
        preferences.playbackBufferSeconds,
        preferences.autoNextEpisode,
        preferences.subtitles,
        systemCaptionsEnabled,
    ) {
        ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(preferences.seekStepMs)
            .setSeekForwardIncrementMs(preferences.seekStepMs)
            .build()
            .apply {
                pauseAtEndOfMediaItems = !preferences.autoNextEpisode
                // A deferred provider grant must not be opened near the beginning of a long
                // episode. TvPlayerRuntime arms one-item preloading only after the current end is
                // demonstrably inside Media3's buffer.
                setPreloadConfiguration(ExoPlayer.PreloadConfiguration.DEFAULT)
                trackSelectionParameters = trackSelectionParameters
                    .withSubtitlePreference(
                        preference = preferences.subtitles,
                        systemCaptionsEnabled = systemCaptionsEnabled,
                    )
                    .withVideoQualityIntent(selection.quality)
            }
    }
    val runtime = remember(player) {
        TvPlayerRuntime(
            player = player,
            scope = scope,
            selection = selection,
            mediaPlan = mediaPlan,
            title = title,
            initialPositionMs = initialPositionMs,
            checkpointCallback = onCheckpoint,
            exitCallback = onExit,
            initialAutomaticSourceRefreshAttempts = automaticSourceRefreshAttempts,
            automaticSourceRefreshCallback = onAutomaticSourceRefreshRequested,
            reducerConfig = reducerConfig,
            bufferConfiguration = bufferConfiguration,
            initialPlaylistGeneration = playbackSessionGeneration,
            initialSubtitlesEnabled = textTracksEnabled,
            autoNextEpisode = preferences.autoNextEpisode,
        )
    }
    val mediaSession = remember(player, runtime) {
        MediaSession.Builder(appContext, player)
            .setCallback(TvMediaSessionCallback(runtime))
            .build()
    }

    SideEffect {
        runtime.checkpointCallback = onCheckpoint
        runtime.exitCallback = onExit
        runtime.automaticSourceRefreshCallback = onAutomaticSourceRefreshRequested
    }

    DisposableEffect(runtime, mediaSession, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) runtime.pauseForLifecycle()
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)

        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            mediaSession.release()
            runtime.close()
            player.release()
        }
    }

    LaunchedEffect(runtime) {
        while (isActive) {
            runtime.refreshTimeline()
            delay(TIMELINE_REFRESH_MS)
        }
    }
    LaunchedEffect(runtime) {
        while (isActive) {
            delay(CHECKPOINT_INTERVAL_MS)
            runtime.checkpoint()
        }
    }
    LaunchedEffect(
        runtime.state.hud,
        runtime.state.drawer,
        runtime.state.hudFocusTarget,
    ) {
        val requestedFocus = when {
            runtime.state.drawer != null -> drawerFocus
            runtime.state.hud == PlayerHudState.VISIBLE -> {
                when (runtime.state.hudFocusTarget) {
                    PlayerHudFocusTarget.PLAY_PAUSE -> playFocus
                    PlayerHudFocusTarget.TIMELINE -> timelineFocus
                    PlayerHudFocusTarget.SEASON -> seasonFocus
                    PlayerHudFocusTarget.VOICEOVER -> voiceoverFocus
                    PlayerHudFocusTarget.QUALITY -> qualityFocus
                    PlayerHudFocusTarget.SUBTITLES -> subtitlesFocus
                    PlayerHudFocusTarget.SOURCE -> sourceFocus
                }
            }
            else -> rootFocus
        }
        requestHudFocusWithRetry(
            requestFocus = { requestedFocus.requestFocus() },
            awaitNextAttempt = { withFrameNanos { } },
        )
    }

    BackHandler {
        runtime.dispatch(PlayerIntent.Back)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onFocusChanged { rootFocused = it.isFocused }
            .onPreviewKeyEvent { composeKeyEvent ->
                handleComposeKeyEvent(
                    keyEvent = composeKeyEvent.nativeKeyEvent,
                    runtime = runtime,
                    rootFocused = rootFocused,
                )
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    subtitleView?.apply {
                        setUserDefaultStyle()
                        setUserDefaultTextSize()
                    }
                    this.player = player
                }
            },
            update = { view ->
                view.player = player
                view.keepScreenOn = runtime.state.playback == PlayerPlaybackState.PLAYING
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (runtime.state.hud == PlayerHudState.VISIBLE) {
            PlayerHud(
                runtime = runtime,
                playFocus = playFocus,
                timelineFocus = timelineFocus,
                seasonFocus = seasonFocus,
                voiceoverFocus = voiceoverFocus,
                qualityFocus = qualityFocus,
                subtitlesFocus = subtitlesFocus,
            )
        }

        runtime.state.drawer?.let { drawer ->
            PlayerDrawerPanel(
                drawer = drawer,
                runtime = runtime,
                firstItemFocus = drawerFocus,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        if (runtime.isBuffering) {
            PlayerBufferingIndicator(
                reduceMotion = preferences.reduceMotion,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun PlayerHud(
    runtime: TvPlayerRuntime,
    playFocus: FocusRequester,
    timelineFocus: FocusRequester,
    seasonFocus: FocusRequester,
    voiceoverFocus: FocusRequester,
    qualityFocus: FocusRequester,
    subtitlesFocus: FocusRequester,
) {
    val state = runtime.state
    val duration = runtime.durationMs
    val progress = if (duration > 0L) {
        (runtime.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xB8000000),
                        Color.Transparent,
                        Color(0x16000000),
                        Color(0xDC000000),
                    ),
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PlayerControlButton(
                text = "‹ К карточке",
                onClick = { runtime.dispatch(PlayerIntent.Stop) },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = runtime.selectionTitle,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(runtime.selectedSourceLabel)
                        append("  •  ")
                        if (runtime.isEpisode) {
                            append("S${runtime.season}  •  E${runtime.selectedEpisode}  •  ")
                        }
                        append(runtime.selectedVoiceover)
                        append("  •  ${runtime.displayQuality}")
                        if (runtime.subtitlesEnabled) append("  •  CC")
                    },
                    color = Color(0xFFC8D1DF),
                    fontSize = 13.sp,
                )
                runtime.errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = Color(0xFFFFB4AB),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            PlayerStatusBadge(state.playback)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Color(0xD80A0F18))
                .padding(start = 28.dp, top = 13.dp, end = 28.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTime(runtime.positionMs),
                    color = Color.White,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.weight(1f))
                val feedbackText = state.episodeNumberInput
                    .takeIf(EpisodeNumberInput::isActive)
                    ?.let { "Серия ${it.digits}" }
                    ?: state.seekFeedback?.let { feedback ->
                        val seconds = feedback.accumulatedDeltaMs / 1_000L
                        if (seconds > 0L) "+$seconds сек" else "$seconds сек"
                    }
                feedbackText?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    text = formatTime(duration),
                    color = Color(0xFFC8D1DF),
                    fontSize = 13.sp,
                )
            }
            PlayerTimeline(
                runtime = runtime,
                progress = progress,
                modifier = Modifier.focusRequester(timelineFocus),
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (runtime.isEpisode) {
                    item(key = "previous") {
                        PlayerTransportButton(
                            icon = TransportIcon.PREVIOUS,
                            description = "Предыдущая серия",
                            onClick = { runtime.dispatch(PlayerIntent.PreviousEpisode) },
                        )
                    }
                }
                item(key = "play-pause") {
                    PlayerTransportButton(
                        icon = if (state.playback == PlayerPlaybackState.PLAYING) {
                            TransportIcon.PAUSE
                        } else {
                            TransportIcon.PLAY
                        },
                        description = if (state.playback == PlayerPlaybackState.PLAYING) {
                            "Пауза"
                        } else {
                            "Воспроизвести"
                        },
                        onClick = {
                            runtime.dispatch(
                                PlayerIntent.TogglePlayback(SystemClock.uptimeMillis()),
                            )
                        },
                        modifier = Modifier.focusRequester(playFocus),
                        primary = true,
                    )
                }
                if (runtime.isEpisode) {
                    item(key = "next") {
                        PlayerTransportButton(
                            icon = TransportIcon.NEXT,
                            description = "Следующая серия",
                            onClick = { runtime.dispatch(PlayerIntent.NextEpisode) },
                        )
                    }
                    item(key = "season") {
                        PlayerControlButton(
                            text = "Сезон ${runtime.season}",
                            onClick = {
                                runtime.dispatch(PlayerIntent.OpenDrawer(PlayerDrawer.SEASONS))
                            },
                            modifier = Modifier.focusRequester(seasonFocus),
                        )
                    }
                }
                item(key = "voiceover") {
                    PlayerControlButton(
                        text = "Озвучка: ${compactHudValue(runtime.selectedVoiceover)}",
                        onClick = {
                            runtime.dispatch(PlayerIntent.OpenDrawer(PlayerDrawer.VOICEOVER))
                        },
                        modifier = Modifier.focusRequester(voiceoverFocus),
                    )
                }
                item(key = "quality") {
                    PlayerControlButton(
                        text = runtime.displayQuality,
                        onClick = {
                            runtime.dispatch(PlayerIntent.OpenDrawer(PlayerDrawer.QUALITY))
                        },
                        modifier = Modifier.focusRequester(qualityFocus),
                    )
                }
                item(key = "subtitles") {
                    PlayerControlButton(
                        text = if (runtime.subtitlesEnabled) "CC: вкл." else "CC: выкл.",
                        onClick = {
                            runtime.dispatch(PlayerIntent.OpenDrawer(PlayerDrawer.SUBTITLES))
                        },
                        modifier = Modifier.focusRequester(subtitlesFocus),
                    )
                }
            }
            if (runtime.isEpisode) {
                EpisodeQuickRow(runtime)
            }
        }
    }
}

@Composable
private fun PlayerStatusBadge(playback: PlayerPlaybackState) {
    val (label, background) = when (playback) {
        PlayerPlaybackState.PLAYING -> "Воспроизведение" to Color(0xC72E6B54)
        PlayerPlaybackState.PAUSED -> "Пауза" to Color(0xC77A5B22)
        PlayerPlaybackState.STOPPED -> "Остановлено" to Color(0xC7623340)
    }
    Text(
        text = label,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun PlayerTimeline(
    runtime: TvPlayerRuntime,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val direction = when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> SeekDirection.BACKWARD
                    KeyEvent.KEYCODE_DPAD_RIGHT -> SeekDirection.FORWARD
                    else -> return@onPreviewKeyEvent false
                }
                runtime.dispatch(
                    PlayerIntent.Seek(
                        direction = direction,
                        source = RemoteKeySource.DPAD,
                        eventTimeMs = native.eventTime,
                    ),
                )
                true
            }
            .clickable {
                runtime.dispatch(PlayerIntent.TogglePlayback(SystemClock.uptimeMillis()))
            }
            .padding(horizontal = 9.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color(0xFF465061),
        )
        if (focused) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp),
            ) {
                val radius = 6.dp.toPx()
                drawCircle(
                    color = Color.White,
                    radius = radius,
                    center = Offset(
                        x = timelineMarkerCenterX(
                            widthPx = size.width,
                            radiusPx = radius,
                            progress = progress,
                        ),
                        y = size.height / 2f,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PlayerBufferingIndicator(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(Color(0xB8000000))
            .semantics { contentDescription = "Буферизация" },
        contentAlignment = Alignment.Center,
    ) {
        if (reduceMotion) {
            Canvas(modifier = Modifier.size(52.dp)) {
                drawCircle(
                    color = Color.White,
                    radius = size.minDimension / 2f - 3.dp.toPx(),
                    style = Stroke(width = 5.dp.toPx()),
                )
            }
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(52.dp),
                color = Color.White,
                strokeWidth = 5.dp,
            )
        }
    }
}

internal fun timelineMarkerCenterX(
    widthPx: Float,
    radiusPx: Float,
    progress: Float,
): Float {
    val width = widthPx.coerceAtLeast(0f)
    val radius = radiusPx.coerceIn(0f, width / 2f)
    return radius + (width - radius * 2f) * progress.coerceIn(0f, 1f)
}

internal fun isPlayerBuffering(playbackState: Int): Boolean =
    playbackState == Player.STATE_BUFFERING

@Composable
private fun EpisodeQuickRow(runtime: TvPlayerRuntime) {
    val episodes = runtime.episodeNumbers
    val currentIndex = episodes.indexOf(runtime.selectedEpisode).coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(
        runtime.selectedSourceId,
        runtime.season,
        runtime.selectedVoiceover,
        episodes,
        currentIndex,
    ) {
        if (episodes.isNotEmpty()) {
            listState.scrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Серии · сезон ${runtime.season}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${if (episodes.isEmpty()) 0 else currentIndex + 1} из ${episodes.size}",
                color = Color(0xFF9EABC0),
                fontSize = 12.sp,
            )
        }
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(
                items = episodes,
                key = { _, episode -> "quick-episode-$episode" },
            ) { _, episode ->
                EpisodeQuickButton(
                    episode = episode,
                    selected = episode == runtime.selectedEpisode,
                    onClick = {
                        if (episode != runtime.selectedEpisode) {
                            runtime.requestEpisode(episode)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun EpisodeQuickButton(
    episode: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Color(0xFFF2F5FA)
        selected -> MaterialTheme.colorScheme.primary
        else -> Color(0xFF202B3C)
    }
    val foreground = if (focused || selected) Color(0xFF101522) else Color.White
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .heightIn(min = 42.dp)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (selected) "▶  $episode" else episode.toString(),
            color = foreground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayerDrawerPanel(
    drawer: PlayerDrawer,
    runtime: TvPlayerRuntime,
    firstItemFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val title = when (drawer) {
        PlayerDrawer.SOURCE -> "Источник"
        PlayerDrawer.SEASONS -> "Выбор сезона"
        PlayerDrawer.EPISODES -> "Выбор серии"
        PlayerDrawer.VOICEOVER -> "Озвучка"
        PlayerDrawer.QUALITY -> "Качество"
        PlayerDrawer.SUBTITLES -> "Субтитры"
        PlayerDrawer.SETTINGS -> "Настройки"
    }
    val options = when (drawer) {
        PlayerDrawer.SOURCE -> runtime.sourceOptions.map { source ->
            DrawerPanelOption(
                id = source.id,
                label = source.label,
                selected = source.id == runtime.selectedSourceId,
                select = { runtime.selectSource(source.id) },
            )
        }
        PlayerDrawer.SEASONS -> runtime.seasonOptions.map { season ->
            DrawerPanelOption(
                id = season.toString(),
                label = "Сезон $season",
                selected = season == runtime.season,
                select = { runtime.selectSeason(season) },
            )
        }
        PlayerDrawer.EPISODES -> runtime.episodeNumbers.map { episode ->
            DrawerPanelOption(
                id = episode.toString(),
                label = "Серия $episode",
                selected = episode == runtime.selectedEpisode,
                select = { runtime.requestEpisode(episode) },
            )
        }
        PlayerDrawer.VOICEOVER -> runtime.voiceoverOptions.map { voiceover ->
            DrawerPanelOption(
                id = voiceover,
                label = voiceover,
                selected = voiceover == runtime.selectedVoiceover,
                select = { runtime.selectVoiceover(voiceover) },
            )
        }
        PlayerDrawer.QUALITY -> runtime.qualityOptions.map { quality ->
            DrawerPanelOption(
                id = quality,
                label = quality,
                selected = quality == runtime.displayQuality,
                select = { runtime.selectQuality(quality) },
            )
        }
        PlayerDrawer.SUBTITLES -> buildList {
            add(
                DrawerPanelOption(
                    id = "system",
                    label = "Авто / системные",
                    selected = runtime.subtitlesEnabled &&
                        runtime.selectedSubtitleOptionId == null,
                    select = { runtime.selectSubtitleOption(null) },
                ),
            )
            runtime.subtitleOptions.forEach { subtitle ->
                add(
                    DrawerPanelOption(
                        id = subtitle.id,
                        label = subtitle.label,
                        selected = runtime.subtitlesEnabled &&
                            runtime.selectedSubtitleOptionId == subtitle.id,
                        select = { runtime.selectSubtitleOption(subtitle.id) },
                    ),
                )
            }
            add(
                DrawerPanelOption(
                    id = "off",
                    label = "Выкл.",
                    selected = !runtime.subtitlesEnabled,
                    select = { runtime.selectSubtitlesEnabled(false) },
                ),
            )
        }
        PlayerDrawer.SETTINGS -> listOf(
            DrawerPanelOption(
                id = "defaults",
                label = "По умолчанию",
                selected = false,
                select = {},
            ),
        )
    }
    val focusIndex = options.indexOfFirst(DrawerPanelOption::selected)
        .takeIf { it >= 0 }
        ?: 0
    val listState = rememberLazyListState()
    LaunchedEffect(drawer, focusIndex, options.size) {
        if (options.isNotEmpty()) {
            listState.scrollToItem(focusIndex)
            runCatching { firstItemFocus.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(390.dp)
            .background(Color(0xD90A0F18))
            .padding(horizontal = 24.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
        )
        if (drawer == PlayerDrawer.SOURCE) {
            Text(
                text = "Нативные потоки. Оригинальный web-плеер запускается отдельно.",
                color = Color(0xFF9EABC0),
                fontSize = 13.sp,
            )
        }
        if (drawer == PlayerDrawer.SEASONS || drawer == PlayerDrawer.EPISODES) {
            Text(
                text = if (drawer == PlayerDrawer.EPISODES) {
                    "Сезон ${runtime.season}. Цифровые кнопки позволяют сразу ввести номер."
                } else {
                    "После выбора сезона откроется список доступных серий."
                },
                color = Color(0xFF9EABC0),
                fontSize = 13.sp,
            )
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(options, key = { _, option -> "$drawer-${option.id}" }) { index, option ->
                DrawerOption(
                    text = option.label,
                    selected = option.selected,
                    onClick = {
                        option.select()
                        if (drawer == PlayerDrawer.SEASONS) {
                            runtime.dispatch(PlayerIntent.OpenDrawer(PlayerDrawer.EPISODES))
                        } else {
                            runtime.dispatch(
                                PlayerIntent.CloseDrawer(SystemClock.uptimeMillis()),
                            )
                        }
                    },
                    modifier = if (index == focusIndex) {
                        Modifier.focusRequester(firstItemFocus)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

private data class DrawerPanelOption(
    val id: String,
    val label: String,
    val selected: Boolean,
    val select: () -> Unit,
)

private enum class TransportIcon {
    PREVIOUS,
    PLAY,
    PAUSE,
    NEXT,
}

@Composable
private fun PlayerTransportButton(
    icon: TransportIcon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Color(0xFFF2F5FA)
        primary -> Color(0xFFB6C8FF)
        else -> Color(0xB8202838)
    }
    val foreground = if (focused || primary) Color(0xFF101522) else Color.White
    Box(
        modifier = modifier
            .width(52.dp)
            .heightIn(min = 48.dp)
            .semantics { contentDescription = description }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width
            val h = size.height
            fun drawTriangle(point: Offset, top: Offset, bottom: Offset) {
                val path = Path().apply {
                    moveTo(point.x, point.y)
                    lineTo(top.x, top.y)
                    lineTo(bottom.x, bottom.y)
                    close()
                }
                drawPath(path, foreground)
            }

            when (icon) {
                TransportIcon.PREVIOUS -> {
                    drawRect(
                        color = foreground,
                        topLeft = Offset(w * 0.16f, h * 0.19f),
                        size = Size(w * 0.12f, h * 0.62f),
                    )
                    drawTriangle(
                        point = Offset(w * 0.31f, h * 0.5f),
                        top = Offset(w * 0.79f, h * 0.18f),
                        bottom = Offset(w * 0.79f, h * 0.82f),
                    )
                }
                TransportIcon.PLAY -> drawTriangle(
                    point = Offset(w * 0.25f, h * 0.15f),
                    top = Offset(w * 0.82f, h * 0.5f),
                    bottom = Offset(w * 0.25f, h * 0.85f),
                )
                TransportIcon.PAUSE -> {
                    drawRect(
                        color = foreground,
                        topLeft = Offset(w * 0.24f, h * 0.17f),
                        size = Size(w * 0.18f, h * 0.66f),
                    )
                    drawRect(
                        color = foreground,
                        topLeft = Offset(w * 0.58f, h * 0.17f),
                        size = Size(w * 0.18f, h * 0.66f),
                    )
                }
                TransportIcon.NEXT -> {
                    drawTriangle(
                        point = Offset(w * 0.69f, h * 0.5f),
                        top = Offset(w * 0.21f, h * 0.18f),
                        bottom = Offset(w * 0.21f, h * 0.82f),
                    )
                    drawRect(
                        color = foreground,
                        topLeft = Offset(w * 0.72f, h * 0.19f),
                        size = Size(w * 0.12f, h * 0.62f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    fontSizeSp: Int = 13,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Color(0xFFF2F5FA)
        primary -> Color(0xFFB6C8FF)
        else -> Color(0xB8202838)
    }
    val foreground = if (focused || primary) Color(0xFF101522) else Color.White
    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun DrawerOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Color(0xFFF2F5FA)
        selected -> Color(0xFF31466E)
        else -> Color(0xFF17202E)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(11.dp),
            )
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text(
            text = if (selected) "✓  $text" else text,
            color = if (focused) Color(0xFF101522) else Color.White,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

private class TvPlayerRuntime(
    val player: ExoPlayer,
    private val scope: CoroutineScope,
    selection: PlaybackSelectionUiModel,
    private val mediaPlan: PlaybackMediaPlan,
    title: String,
    initialPositionMs: Long,
    checkpointCallback: (PlaybackCheckpoint) -> Unit,
    exitCallback: () -> Unit,
    initialAutomaticSourceRefreshAttempts: Set<PlaybackSourceRefreshUnitKey>,
    automaticSourceRefreshCallback: ((PlaybackSourceRefreshRequest) -> Unit)?,
    reducerConfig: PlayerReducerConfig,
    bufferConfiguration: PlaybackBufferConfiguration,
    initialPlaylistGeneration: Long,
    initialSubtitlesEnabled: Boolean,
    private val autoNextEpisode: Boolean,
) : Media3PlayerHost {
    private val reducer = TvPlayerReducer(reducerConfig)
    private val keyMapper = TvPlayerKeyMapper()
    private val controller = Media3PlayerController(player, this)
    private val timeoutJobs = mutableMapOf<PlayerTimeoutKind, Job>()
    private var exitDelivered = false
    private var lastAppliedAudioVariantId: String? = null
    private var lastAppliedVideoSelectionKey: String? = null
    private var lastAppliedSubtitleSelectionKey: String? = null
    private var completionHandled = false
    private val automaticSourceRefreshAttempts =
        initialAutomaticSourceRefreshAttempts.toMutableSet()
    private val stallWatchdog = PlaybackStallWatchdog(
        initialBufferingTimeoutMs = bufferConfiguration.initialBufferingRecoveryMs,
        rebufferingTimeoutMs = bufferConfiguration.rebufferingRecoveryMs,
        readyNoProgressTimeoutMs = bufferConfiguration.readyNoProgressRecoveryMs,
    )
    private var automaticSourceRefreshInFlight = false
    private var skipCloseCheckpoint = false
    private val hudRevealKeyReleaseGuard = PlayerKeyReleaseGuard()
    private val nextEpisodePreloadConfiguration = ExoPlayer.PreloadConfiguration(
        bufferConfiguration.nextEpisodePreloadMs * 1_000L,
    )
    private val preloadHorizonMs = bufferConfiguration.targetBufferMs.toLong()
    private var preloadArmedForMediaItemIndex: Int? = null
    private var preloadRearmPositionMs = 0L
    private var pendingPreloadFailure: PlaybackPreloadFailure? = null
    private var playlistGeneration = initialPlaylistGeneration
    private var qualityGeneration = 0L

    private val baseSelection: PlaybackSelectionUiModel = selection
    private val initialSourceId: String = selection.sourceId
        ?.takeIf { requested -> mediaPlan.sourceOptions.any { it.id == requested } }
        ?: mediaPlan.defaultSourceId
    private val initialVoiceover: String = selection.voiceover
        .takeIf { requested -> requested in mediaPlan.voiceoversFor(initialSourceId) }
        ?: mediaPlan.voiceoversFor(initialSourceId).first()
    private val initialSeasonNumber: Int? = if (mediaPlan.isEpisodic) {
        selection.season
            ?.takeIf { it in mediaPlan.seasonNumbersFor(initialSourceId, initialVoiceover) }
            ?: mediaPlan.defaultSeasonNumber(initialSourceId, initialVoiceover)
    } else {
        null
    }
    private val initialEpisodeNumbers: List<Int> = mediaPlan.episodeNumbersFor(
        sourceId = initialSourceId,
        seasonNumber = initialSeasonNumber,
        voiceover = initialVoiceover,
    )
    private val initialEpisodeNumber: Int? = if (mediaPlan.isEpisodic) {
        selection.episode?.takeIf { it in initialEpisodeNumbers }
            ?: initialEpisodeNumbers.first()
    } else {
        null
    }
    private val initialVariant: PlaybackMediaVariant = mediaPlan.preferredForQuality(
        sourceId = initialSourceId,
        seasonNumber = initialSeasonNumber,
        episodeNumber = initialEpisodeNumber,
        voiceover = initialVoiceover,
        quality = selection.quality,
    )
    val selectionTitle: String = title
    val isEpisode: Boolean = mediaPlan.isEpisodic
    val sourceOptions
        get() = mediaPlan.sourceOptions
    var selectedSourceId by mutableStateOf(initialSourceId)
        private set
    val selectedSourceLabel: String
        get() = mediaPlan.sourceLabel(selectedSourceId) ?: selectedSourceId
    var season by mutableIntStateOf(initialSeasonNumber ?: 1)
        private set
    val seasonOptions: List<Int>
        get() = if (isEpisode) {
            mediaPlan.seasonNumbersFor(selectedSourceId, selectedVoiceover)
        } else {
            emptyList()
        }
    val episodeNumbers: List<Int>
        get() = if (isEpisode) {
            mediaPlan.episodeNumbersFor(selectedSourceId, season, selectedVoiceover)
        } else {
            emptyList()
        }
    private val playlistCoordinates: List<PlaybackEpisodeCoordinate>
        get() = if (isEpisode) {
            mediaPlan.episodeCoordinatesFor(selectedSourceId, selectedVoiceover)
        } else {
            emptyList()
        }
    var selectedEpisode by mutableIntStateOf(initialEpisodeNumber ?: 1)
        private set
    var selectedVoiceover by mutableStateOf(initialVariant.voiceover)
        private set
    /** User intent persisted to history and re-applied independently for every episode. */
    private var desiredQuality by mutableStateOf(selection.quality)
    /** Concrete plan variant currently loaded; it must never overwrite [desiredQuality]. */
    private var activeVariantId = initialVariant.id
    private var pendingQualitySwitchRequest: PlaybackQualitySwitchRequest? = null
    private var adaptiveVideoTracks by mutableStateOf(emptyList<AdaptiveVideoTrack>())
    val displayQuality: String
        get() = desiredQuality
    var subtitlesEnabled by mutableStateOf(initialSubtitlesEnabled)
        private set
    var selectedSubtitleOptionId by mutableStateOf<String?>(null)
        private set
    private var selectableTextTracks by mutableStateOf(emptyList<SelectableTextTrack>())
    val subtitleOptions: List<PlayerTextTrackOption>
        get() = selectableTextTracks.map { PlayerTextTrackOption(it.id, it.label) }
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var state by mutableStateOf(TvPlayerState())
        private set

    var checkpointCallback: (PlaybackCheckpoint) -> Unit = checkpointCallback
    var exitCallback: () -> Unit = exitCallback
    var automaticSourceRefreshCallback: ((PlaybackSourceRefreshRequest) -> Unit)? =
        automaticSourceRefreshCallback

    val voiceoverOptions: List<String>
        get() = mediaPlan.voiceoversFor(selectedSourceId)
    val qualityOptions: List<String>
        get() {
            val planOptions = mediaPlan.qualitiesFor(
                sourceId = selectedSourceId,
                seasonNumber = currentSeasonNumber(),
                episodeNumber = currentEpisodeNumber(),
                voiceover = selectedVoiceover,
            )
            val available = (planOptions + adaptiveVideoTracks.map(AdaptiveVideoTrack::label))
                .distinct()
            return if (
                desiredQuality !in available &&
                (
                    isAutoQuality(desiredQuality) ||
                        PlaybackQualityPolicy.height(desiredQuality) != null
                    )
            ) {
                listOf(desiredQuality) + available
            } else available
        }
    val seekStepSeconds: Long = reducerConfig.seekStepMs / 1_000L

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) disarmNextEpisodePreload()
            when (
                playbackPauseCompletion(
                    playWhenReady = playWhenReady,
                    mediaItemEnded =
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM,
                    autoNextEpisode = autoNextEpisode,
                )
            ) {
                PlaybackPauseCompletion.IGNORE -> Unit
                PlaybackPauseCompletion.CHECKPOINT -> checkpoint()
                PlaybackPauseCompletion.CHECKPOINT_AND_EXIT -> {
                    if (!completionHandled) {
                        completionHandled = true
                        checkpointCompletedSelection()
                        skipCloseCheckpoint = true
                        onExitRequested()
                    }
                }
            }
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                disarmNextEpisodePreload()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (
                reason == Player.DISCONTINUITY_REASON_SEEK &&
                oldPosition.mediaItemIndex == newPosition.mediaItemIndex &&
                newPosition.positionMs < oldPosition.positionMs
            ) {
                preloadRearmPositionMs = maxOf(
                    preloadRearmPositionMs,
                    oldPosition.positionMs,
                )
                disarmNextEpisodePreload()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // The previous one-item preload has either become current or is no longer relevant.
            // Disable it before arming the following episode from its own near-end observation.
            disarmNextEpisodePreload()
            preloadRearmPositionMs = 0L
            when (
                playbackItemTransitionCompletion(
                    automaticTransition =
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                    autoNextEpisode = autoNextEpisode,
                )
            ) {
                PlaybackItemTransitionCompletion.IGNORE -> Unit
                PlaybackItemTransitionCompletion.CHECKPOINT_AND_ADVANCE -> {
                    checkpointCompletedSelection()
                }
                PlaybackItemTransitionCompletion.CHECKPOINT_AND_EXIT -> {
                    if (!completionHandled) {
                        completionHandled = true
                        checkpointCompletedSelection()
                        skipCloseCheckpoint = true
                        onExitRequested()
                    }
                    return
                }
            }
            val previousSeason = currentSeasonNumber()
            val previousEpisode = currentEpisodeNumber()
            val variantId = mediaItem?.playbackTag()?.variantId
            mediaPlan.findById(variantId.orEmpty())?.let { variant ->
                selectedSourceId = variant.sourceId
                variant.effectiveSeasonNumber?.let { season = it }
                variant.episodeNumber?.let { selectedEpisode = it }
                selectedVoiceover = variant.voiceover
                activeVariantId = variant.id
            }
            if (
                isEpisode &&
                (previousSeason != currentSeasonNumber() ||
                    previousEpisode != currentEpisodeNumber())
            ) {
                checkpointUnitActivation()
            }
            val preloadFailure = pendingPreloadFailure
            val recoverFailedPreload = preloadFailure?.terminal == true &&
                PlaybackPreloadFailurePolicy.matches(
                    failure = preloadFailure,
                    playlistGeneration = playlistGeneration,
                    windowIndex = player.currentMediaItemIndex,
                    variantId = variantId,
                )
            if (
                preloadFailure != null &&
                PlaybackPreloadFailurePolicy.matches(
                    failure = preloadFailure,
                    playlistGeneration = playlistGeneration,
                    windowIndex = player.currentMediaItemIndex,
                    variantId = variantId,
                )
            ) {
                pendingPreloadFailure = null
            }
            lastAppliedAudioVariantId = null
            stallWatchdog.reset()
            refreshTimeline()
            if (recoverFailedPreload) {
                requestAutomaticSourceRefresh(
                    fallbackMessage =
                        "Предзагруженный источник недоступен. Обновите данные из карточки.",
                )
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateAdaptiveVideoTracks(tracks)
            updateSelectableTextTracks(tracks)
            applyPreferredAudioTrack(tracks)
            applySelectedVideoTrack()
            applySubtitleSelection()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            isBuffering = isPlayerBuffering(playbackState)
            refreshTimeline()
            if (playbackState == Player.STATE_READY) {
                completionHandled = false
                errorMessage = null
            }
            if (playbackState == Player.STATE_ENDED && !completionHandled) {
                completionHandled = true
                checkpointCompletedSelection()
                handlePlaybackCompleted()
            }
        }

    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: IOException,
            wasCanceled: Boolean,
        ) {
            if (wasCanceled) return
            val eventTag = mediaTagAt(eventTime) ?: return
            val identity = PlaybackPreloadFailurePolicy.futureWindowOrNull(
                playlistGeneration = playlistGeneration,
                armedForWindowIndex = preloadArmedForMediaItemIndex,
                currentWindowIndex = eventTime.currentWindowIndex,
                eventWindowIndex = eventTime.windowIndex,
                eventVariantId = eventTag.variantId,
            ) ?: return
            val existing = pendingPreloadFailure
            if (existing == null || !existing.terminal || existing.window != identity) {
                pendingPreloadFailure = PlaybackPreloadFailure(identity, terminal = false)
            }
            // onLoadError is non-fatal. Never consume the current episode's recovery budget and
            // never issue a second provider transaction here; only stop future-item preloading.
            disarmNextEpisodePreload()
        }

        override fun onLoadCompleted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
        ) {
            val pending = pendingPreloadFailure ?: return
            val eventTag = mediaTagAt(eventTime) ?: return
            if (
                !pending.terminal &&
                PlaybackPreloadFailurePolicy.matches(
                    failure = pending,
                    playlistGeneration = playlistGeneration,
                    windowIndex = eventTime.windowIndex,
                    variantId = eventTag.variantId,
                )
            ) {
                pendingPreloadFailure = null
            }
        }

        override fun onPlayerError(
            eventTime: AnalyticsListener.EventTime,
            error: PlaybackException,
        ) {
            val eventIndex = eventTime.windowIndex
            val eventTag = mediaTagAt(eventTime) ?: return
            val eventVariantId = eventTag.variantId
            val currentIndex = eventTime.currentWindowIndex
            if (eventIndex == currentIndex && currentIndex == player.currentMediaItemIndex) {
                if (eventTag != player.currentMediaItem?.playbackTag()) return
                pendingPreloadFailure = null
                isBuffering = false
                requestAutomaticSourceRefresh(userSafePlaybackError(error))
                return
            }

            if (eventIndex != currentIndex + 1) return
            val identity = PlaybackWindowIdentity(
                playlistGeneration = playlistGeneration,
                windowIndex = eventIndex,
                variantId = eventVariantId,
            )
            val alreadyObserved = pendingPreloadFailure?.window == identity
            if (preloadArmedForMediaItemIndex != currentIndex && !alreadyObserved) return
            pendingPreloadFailure = PlaybackPreloadFailure(identity, terminal = true)
            disarmNextEpisodePreload()
        }
    }

    init {
        require(isEpisode == (selection.season != null && selection.episode != null)) {
            "Selection and playback plan must agree on episodic playback"
        }
        player.addListener(playerListener)
        player.addAnalyticsListener(analyticsListener)
        controller.attach()
        val startPosition = if (selection.resume) initialPositionMs.coerceAtLeast(0L) else 0L
        val startIndex = if (isEpisode) {
            playlistCoordinates.indexOf(currentEpisodeCoordinate())
        } else {
            0
        }
        player.setMediaItems(mediaItems(), startIndex, startPosition)
        player.prepare()
        player.play()
        refreshTimeline()
    }

    fun dispatch(intent: PlayerIntent) {
        val reduction = reducer.reduce(state, intent)
        state = reduction.state
        controller.execute(reduction)
    }

    fun mapAndDispatch(keyEvent: KeyEvent): Boolean {
        if (
            keyEvent.action == KeyEvent.ACTION_UP &&
            hudRevealKeyReleaseGuard.consumeRelease(keyEvent.keyCode)
        ) {
            return true
        }
        if (isDigitKeyCode(keyEvent.keyCode)) {
            if (!isEpisode) return false
            // Numeric episode entry is global while watching, but must not alter a selection
            // underneath another open panel.
            if (state.drawer != null && state.drawer != PlayerDrawer.EPISODES) return true
        }
        val intent = keyMapper.map(keyEvent) ?: return false
        if (intent is PlayerIntent.PrimaryAction) {
            hudRevealKeyReleaseGuard.arm(keyEvent.keyCode)
        }
        dispatch(intent)
        return true
    }

    fun handleMediaButton(keyEvent: KeyEvent): Boolean {
        if (!isMediaKeyCode(keyEvent.keyCode)) return false
        if (
            !isEpisode &&
            (keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        ) {
            return true
        }
        keyMapper.map(keyEvent)?.let { intent ->
            scope.launch { dispatch(intent) }
        }
        // Consume UP and ignored repeats as well, otherwise Media3 would perform a second action.
        return true
    }

    fun requestEpisode(episodeNumber: Int) {
        if (!isEpisode) return
        val baseTime = SystemClock.uptimeMillis()
        episodeNumber.toString().forEachIndexed { index, character ->
            dispatch(PlayerIntent.EpisodeDigit(character.digitToInt(), baseTime + index))
        }
        dispatch(PlayerIntent.PrimaryAction(baseTime + episodeNumber.toString().length))
    }

    fun selectSource(sourceId: String) {
        if (sourceId == selectedSourceId || sourceOptions.none { it.id == sourceId }) return
        val targetVoiceovers = mediaPlan.voiceoversFor(sourceId)
        val targetVoiceover = selectedVoiceover.takeIf { it in targetVoiceovers }
            ?: targetVoiceovers.first()
        val targetSeason = if (isEpisode) {
            season.takeIf {
                it in mediaPlan.seasonNumbersFor(sourceId, targetVoiceover)
            } ?: mediaPlan.defaultSeasonNumber(sourceId, targetVoiceover)
        } else {
            null
        }
        val targetEpisodes = mediaPlan.episodeNumbersFor(
            sourceId = sourceId,
            seasonNumber = targetSeason,
            voiceover = targetVoiceover,
        )
        val targetEpisode = if (isEpisode) {
            selectedEpisode.takeIf { it in targetEpisodes } ?: targetEpisodes.first()
        } else {
            null
        }
        val variant = mediaPlan.preferredForQuality(
            sourceId = sourceId,
            seasonNumber = targetSeason,
            episodeNumber = targetEpisode,
            voiceover = targetVoiceover,
            quality = desiredQuality,
        )
        replacePlaybackContext(
            sourceId = sourceId,
            seasonNumber = targetSeason,
            episodeNumber = targetEpisode,
            variant = variant,
            retainPosition = !isEpisode ||
                (targetSeason == season && targetEpisode == selectedEpisode),
        )
    }

    fun selectSeason(seasonNumber: Int) {
        if (!isEpisode || seasonNumber == season || seasonNumber !in seasonOptions) return
        val targetEpisodes = mediaPlan.episodeNumbersFor(
            sourceId = selectedSourceId,
            seasonNumber = seasonNumber,
            voiceover = selectedVoiceover,
        )
        val targetEpisode = selectedEpisode.takeIf { it in targetEpisodes }
            ?: targetEpisodes.first()
        val variant = mediaPlan.preferredForQuality(
            sourceId = selectedSourceId,
            seasonNumber = seasonNumber,
            episodeNumber = targetEpisode,
            voiceover = selectedVoiceover,
            quality = desiredQuality,
        )
        replacePlaybackContext(
            sourceId = selectedSourceId,
            seasonNumber = seasonNumber,
            episodeNumber = targetEpisode,
            variant = variant,
            retainPosition = false,
        )
    }

    fun selectVoiceover(voiceover: String) {
        if (voiceover == selectedVoiceover || voiceover !in voiceoverOptions) return
        val targetSeason = if (isEpisode) {
            season.takeIf {
                it in mediaPlan.seasonNumbersFor(selectedSourceId, voiceover)
            } ?: mediaPlan.defaultSeasonNumber(selectedSourceId, voiceover)
        } else {
            null
        }
        val targetEpisodes = mediaPlan.episodeNumbersFor(
            sourceId = selectedSourceId,
            seasonNumber = targetSeason,
            voiceover = voiceover,
        )
        val targetEpisode = if (isEpisode) {
            selectedEpisode.takeIf { it in targetEpisodes } ?: targetEpisodes.first()
        } else {
            null
        }
        val variant = mediaPlan.preferredForQuality(
            sourceId = selectedSourceId,
            seasonNumber = targetSeason,
            episodeNumber = targetEpisode,
            voiceover = voiceover,
            quality = desiredQuality,
        )
        replacePlaybackContext(
            sourceId = selectedSourceId,
            seasonNumber = targetSeason,
            episodeNumber = targetEpisode,
            variant = variant,
            retainPosition = !isEpisode ||
                (targetSeason == season && targetEpisode == selectedEpisode),
        )
    }

    fun selectQuality(quality: String) {
        if (
            quality !in qualityOptions &&
            !isAutoQuality(quality) &&
            PlaybackQualityPolicy.height(quality) == null
        ) return
        val qualityChanged = desiredQuality != quality
        if (qualityChanged) advanceQualityGeneration()
        desiredQuality = quality
        applyDesiredVideoConstraints()
        val variant = mediaPlan.preferredForQuality(
            sourceId = selectedSourceId,
            seasonNumber = currentSeasonNumber(),
            episodeNumber = currentEpisodeNumber(),
            voiceover = selectedVoiceover,
            quality = desiredQuality,
        )
        lastAppliedVideoSelectionKey = null
        if (
            variant.sourceId == selectedSourceId &&
            variant.effectiveSeasonNumber == currentSeasonNumber() &&
            variant.episodeNumber == currentEpisodeNumber() &&
            variant.voiceover == selectedVoiceover &&
            variant.id == activeVariantId
        ) {
            if (qualityChanged && isEpisode) {
                // Future fixed MediaItems were chosen when the playlist was first built. Rebuild
                // them before preload/transition even when this episode already uses the right
                // concrete variant. The exact current opaque reference, index and position stay.
                switchVariant(variant, rebuildEvenIfActive = true)
            } else {
                applySelectedVideoTrack()
                dispatch(PlayerIntent.ShowHud(SystemClock.uptimeMillis()))
            }
        } else {
            switchVariant(variant)
        }
    }

    fun selectSubtitlesEnabled(enabled: Boolean) {
        subtitlesEnabled = enabled
        if (!enabled) selectedSubtitleOptionId = null
        lastAppliedSubtitleSelectionKey = null
        applySubtitleSelection()
    }

    fun selectSubtitleOption(optionId: String?) {
        subtitlesEnabled = true
        selectedSubtitleOptionId = optionId
        lastAppliedSubtitleSelectionKey = null
        applySubtitleSelection()
    }

    fun refreshTimeline(observeStall: Boolean = true) {
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        // Deliberate checkpoint/replacement paths only sample the timeline. They must not arm a
        // now-stale future item in the small window before that playlist is invalidated.
        if (observeStall) updateNextEpisodePreload()
        if (
            observeStall &&
            stallWatchdog.observe(
                PlaybackStallObservation(
                    nowMs = SystemClock.uptimeMillis(),
                    playbackState = player.playbackStallState(),
                    playWhenReady = player.playWhenReady,
                    playbackSuppressed = player.playbackSuppressionReason !=
                        Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                    positionMs = positionMs,
                    durationMs = durationMs,
                ),
            ) == PlaybackStallDecision.REFRESH_SOURCES
        ) {
            requestAutomaticSourceRefresh(
                fallbackMessage =
                    "Автоматическое обновление не помогло. Вернитесь в карточку и нажмите «Смотреть».",
            )
        }
    }

    fun checkpoint() {
        // Saving progress during Back, lifecycle pause, or source replacement must never start a
        // new recovery flow after the user has deliberately left the player. Stall recovery is
        // owned by the periodic timeline observer and explicit Media3 errors only.
        refreshTimeline(observeStall = false)
        checkpointCallback(
            PlaybackCheckpoint(
                selection = currentSelection(),
                positionMs = positionMs,
                durationMs = durationMs,
                playbackEnded = false,
            ),
        )
    }

    fun selectionForHandoff(): PlaybackSelectionUiModel = currentSelection()

    fun pauseForLifecycle() {
        checkpoint()
        player.pause()
    }

    fun close() {
        if (!skipCloseCheckpoint) checkpoint()
        disarmNextEpisodePreload()
        advancePlaylistGeneration()
        timeoutJobs.values.forEach { it.cancel() }
        timeoutJobs.clear()
        controller.detach()
        player.removeAnalyticsListener(analyticsListener)
        player.removeListener(playerListener)
    }

    override fun onPlayerIntent(intent: PlayerIntent) {
        dispatch(intent)
    }

    override fun onSaveProgressRequested() {
        checkpoint()
    }

    override fun onEpisodeNumberRequested(episodeNumber: Int) {
        val targetCoordinate = PlaybackEpisodeCoordinate(season, episodeNumber)
        val targetIndex = playlistCoordinates.indexOf(targetCoordinate)
        if (!isEpisode || targetIndex < 0) {
            errorMessage = "Серия $episodeNumber недоступна"
            dispatch(PlayerIntent.EpisodeTransitionFinished)
            dispatch(PlayerIntent.ShowHud(SystemClock.uptimeMillis()))
            return
        }
        // Keep the old unit explicit even when this method is called outside the reducer path.
        // The reducer normally emits SaveProgress first, but changing selectedEpisode before a
        // defensive checkpoint would store the old position under the new episode key.
        checkpoint()
        val sameItem = player.currentMediaItemIndex == targetIndex
        stallWatchdog.reset()
        preloadRearmPositionMs = 0L
        selectedEpisode = episodeNumber
        checkpointUnitActivation()
        player.seekTo(targetIndex, 0L)
        dispatch(PlayerIntent.Play(SystemClock.uptimeMillis()))
        if (sameItem) dispatch(PlayerIntent.EpisodeTransitionFinished)
    }

    override fun onEpisodeBoundary(direction: EpisodeDirection) {
        if (!isEpisode) {
            dispatch(PlayerIntent.EpisodeTransitionFinished)
            return
        }
        val target = when (direction) {
            EpisodeDirection.NEXT -> mediaPlan.nextEpisodeCoordinate(
                sourceId = selectedSourceId,
                seasonNumber = season,
                episodeNumber = selectedEpisode,
                voiceover = selectedVoiceover,
            )
            EpisodeDirection.PREVIOUS -> mediaPlan.previousEpisodeCoordinate(
                sourceId = selectedSourceId,
                seasonNumber = season,
                episodeNumber = selectedEpisode,
                voiceover = selectedVoiceover,
            )
        }
        if (target != null) {
            val coordinates = playlistCoordinates
            val preparedTargetIndex = preparedEpisodePlaylistIndex(
                coordinates = coordinates,
                preparedCoordinates = preparedPlaylistCoordinates(),
                target = target,
            )
            if (preparedTargetIndex != null) {
                // The reducer normally saves first; keep this path correct for direct host calls.
                checkpoint()
                val shouldPlay = player.playWhenReady
                stallWatchdog.reset()
                preloadRearmPositionMs = 0L
                season = target.seasonNumber
                selectedEpisode = target.episodeNumber
                checkpointUnitActivation()
                player.seekTo(preparedTargetIndex, 0L)
                player.playWhenReady = shouldPlay
                if (shouldPlay) player.play()
                errorMessage = null
                dispatch(PlayerIntent.ShowHud(SystemClock.uptimeMillis()))
                return
            }
            val variant = mediaPlan.preferredForQuality(
                sourceId = selectedSourceId,
                seasonNumber = target.seasonNumber,
                episodeNumber = target.episodeNumber,
                voiceover = selectedVoiceover,
                quality = desiredQuality,
            )
            replacePlaybackContext(
                sourceId = selectedSourceId,
                seasonNumber = target.seasonNumber,
                episodeNumber = target.episodeNumber,
                variant = variant,
                retainPosition = false,
            )
            return
        }
        when (direction) {
            EpisodeDirection.NEXT -> {
                errorMessage = "Это последняя серия"
                dispatch(PlayerIntent.EpisodeTransitionFinished)
                dispatch(PlayerIntent.ShowHud(SystemClock.uptimeMillis()))
            }
            EpisodeDirection.PREVIOUS -> {
                errorMessage = "Это первая серия"
                dispatch(PlayerIntent.EpisodeTransitionFinished)
                dispatch(PlayerIntent.ShowHud(SystemClock.uptimeMillis()))
            }
        }
    }

    private fun handlePlaybackCompleted() {
        when (
            val decision = playbackCompletionDecision(
                mediaPlan = mediaPlan,
                sourceId = selectedSourceId,
                seasonNumber = currentSeasonNumber(),
                episodeNumber = currentEpisodeNumber(),
                voiceover = selectedVoiceover,
                autoNextEpisode = autoNextEpisode,
            )
        ) {
            is PlaybackCompletionDecision.Advance -> {
                val target = decision.coordinate
                val variant = mediaPlan.preferredForQuality(
                    sourceId = selectedSourceId,
                    seasonNumber = target.seasonNumber,
                    episodeNumber = target.episodeNumber,
                    voiceover = selectedVoiceover,
                    quality = desiredQuality,
                )
                replacePlaybackContext(
                    sourceId = selectedSourceId,
                    seasonNumber = target.seasonNumber,
                    episodeNumber = target.episodeNumber,
                    variant = variant,
                    retainPosition = false,
                    revealHud = false,
                    checkpointBeforeReplace = false,
                    forcePlay = true,
                )
            }
            PlaybackCompletionDecision.Exit -> {
                skipCloseCheckpoint = true
                onExitRequested()
            }
        }
    }

    override fun onExitRequested() {
        if (exitDelivered) return
        exitDelivered = true
        exitCallback()
    }

    override fun scheduleTimeout(kind: PlayerTimeoutKind, deadlineMs: Long) {
        timeoutJobs.remove(kind)?.cancel()
        timeoutJobs[kind] = scope.launch {
            delay((deadlineMs - SystemClock.uptimeMillis()).coerceAtLeast(0L))
            dispatch(PlayerIntent.Timeout(kind, deadlineMs))
        }
    }

    private fun switchVariant(
        variant: PlaybackMediaVariant,
        rebuildEvenIfActive: Boolean = false,
    ) {
        if (
            variant.sourceId != selectedSourceId ||
            variant.effectiveSeasonNumber != currentSeasonNumber() ||
            variant.episodeNumber != currentEpisodeNumber() ||
            variant.voiceover != selectedVoiceover
        ) {
            return
        }
        if (variant.id == activeVariantId && !rebuildEvenIfActive) return
        disarmNextEpisodePreload()
        checkpoint()
        val currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val shouldPlay = player.playWhenReady
        advancePlaylistGeneration()
        selectedVoiceover = variant.voiceover
        activeVariantId = variant.id
        stallWatchdog.reset()
        lastAppliedAudioVariantId = null
        lastAppliedVideoSelectionKey = null
        lastAppliedSubtitleSelectionKey = null
        player.setMediaItems(
            mediaItems(currentVariantOverride = variant),
            currentIndex,
            currentPosition,
        )
        player.prepare()
        player.playWhenReady = shouldPlay
    }

    private fun replacePlaybackContext(
        sourceId: String,
        seasonNumber: Int?,
        episodeNumber: Int?,
        variant: PlaybackMediaVariant,
        retainPosition: Boolean,
        revealHud: Boolean = true,
        checkpointBeforeReplace: Boolean = true,
        forcePlay: Boolean = false,
    ) {
        disarmNextEpisodePreload()
        if (checkpointBeforeReplace) checkpoint()
        val currentPosition = if (retainPosition) {
            player.currentPosition.coerceAtLeast(0L)
        } else {
            0L
        }
        val shouldPlay = forcePlay || player.playWhenReady
        advancePlaylistGeneration()
        selectedSourceId = sourceId
        if (seasonNumber != null) season = seasonNumber
        if (episodeNumber != null) selectedEpisode = episodeNumber
        selectedVoiceover = variant.voiceover
        activeVariantId = variant.id
        if (isEpisode && !retainPosition) checkpointUnitActivation()
        stallWatchdog.reset()
        lastAppliedAudioVariantId = null
        lastAppliedVideoSelectionKey = null
        lastAppliedSubtitleSelectionKey = null
        val targetIndex = if (isEpisode) {
            playlistCoordinates.indexOf(currentEpisodeCoordinate())
        } else {
            0
        }
        player.setMediaItems(
            mediaItems(currentVariantOverride = variant),
            targetIndex.coerceAtLeast(0),
            currentPosition,
        )
        player.prepare()
        player.playWhenReady = shouldPlay
        if (forcePlay) player.play()
        errorMessage = null
        if (revealHud) dispatch(PlayerIntent.ShowHud(SystemClock.uptimeMillis()))
    }

    private fun updateNextEpisodePreload() {
        val currentIndex = player.currentMediaItemIndex
        val pendingFailure = pendingPreloadFailure
        if (
            pendingFailure != null &&
            PlaybackPreloadFailurePolicy.matches(
                failure = pendingFailure,
                playlistGeneration = playlistGeneration,
                windowIndex = currentIndex + 1,
                variantId = mediaVariantIdAt(currentIndex + 1),
            )
        ) {
            return
        }
        val shouldArm = PlaybackBufferPolicy.shouldArmNextEpisodePreload(
            isEpisodic = isEpisode,
            autoNextEpisode = autoNextEpisode,
            currentMediaItemIndex = currentIndex,
            mediaItemCount = player.mediaItemCount,
            durationMs = durationMs,
            currentPositionMs = positionMs,
            bufferedPositionMs = player.bufferedPosition,
            preloadHorizonMs = preloadHorizonMs,
            playWhenReady = player.playWhenReady,
            playbackSuppressed =
                player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            minimumRearmPositionMs = preloadRearmPositionMs,
        )
        if (shouldArm && preloadArmedForMediaItemIndex != currentIndex) {
            player.setPreloadConfiguration(nextEpisodePreloadConfiguration)
            preloadArmedForMediaItemIndex = currentIndex
        }
    }

    private fun disarmNextEpisodePreload() {
        if (preloadArmedForMediaItemIndex == null) return
        preloadArmedForMediaItemIndex = null
        player.setPreloadConfiguration(ExoPlayer.PreloadConfiguration.DEFAULT)
    }

    private fun checkpointCompletedSelection() {
        val completed = completedPlaybackCheckpoint(
            lastPositionMs = positionMs,
            lastDurationMs = durationMs,
        )
        checkpointCallback(
            PlaybackCheckpoint(
                selection = currentSelection(),
                positionMs = completed.positionMs,
                durationMs = completed.durationMs,
                playbackEnded = true,
            ),
        )
    }

    /** Records the chosen episode even before Media3 advances beyond position zero. */
    private fun checkpointUnitActivation() {
        if (!isEpisode) return
        checkpointCallback(
            PlaybackCheckpoint(
                selection = currentSelection(),
                positionMs = 0L,
                durationMs = 0L,
                playbackEnded = false,
            ),
        )
    }

    private fun requestAutomaticSourceRefresh(fallbackMessage: String) {
        if (automaticSourceRefreshInFlight) return
        checkpoint()
        // Keep this defensive guard even though checkpointing itself does not observe stalls.
        if (automaticSourceRefreshInFlight) return
        dispatch(PlayerIntent.ShowHud(SystemClock.uptimeMillis()))
        val refreshSelection = currentSelection().copy(resume = true)
        val refreshUnit = refreshSelection.sourceRefreshUnitKey()
        val callback = automaticSourceRefreshCallback
        when (
            playbackErrorRecoveryDecision(
                refreshCallbackAvailable = callback != null,
                refreshAlreadyRequested = refreshUnit in automaticSourceRefreshAttempts,
            )
        ) {
            PlaybackErrorRecoveryDecision.REFRESH_SOURCES -> {
                automaticSourceRefreshAttempts += refreshUnit
                automaticSourceRefreshInFlight = true
                errorMessage = "Источник недоступен. Обновляем данные…"
                val request = PlaybackSourceRefreshRequest(
                    selection = refreshSelection,
                    positionMs = positionMs,
                    attemptedUnits = automaticSourceRefreshAttempts.toSet(),
                )
                // Stop consuming the stale stream while the root performs fresh details/provider
                // preparation. The checkpoint above remains the exact restart position.
                player.pause()
                scope.launch {
                    callback?.invoke(request)
                }
            }
            PlaybackErrorRecoveryDecision.SHOW_ERROR -> {
                errorMessage = fallbackMessage
            }
        }
    }

    private fun mediaItems(
        currentVariantOverride: PlaybackMediaVariant? = null,
    ): List<MediaItem> = if (isEpisode) {
        val coordinates = playlistCoordinates
        val variants = playlistVariantsForQuality(
            mediaPlan = mediaPlan,
            coordinates = coordinates,
            sourceId = selectedSourceId,
            voiceover = selectedVoiceover,
            desiredQuality = desiredQuality,
            currentVariantOverride = currentVariantOverride,
        )
        coordinates.zip(variants).map { (coordinate, variant) ->
            mediaItemForEpisode(
                seasonNumber = coordinate.seasonNumber,
                episode = coordinate.episodeNumber,
                variantOverride = variant,
            )
        }
    } else {
        listOf(mediaItemForEpisode(null, null, currentVariantOverride))
    }

    private fun mediaItemForEpisode(
        seasonNumber: Int?,
        episode: Int?,
        variantOverride: PlaybackMediaVariant? = null,
    ): MediaItem {
        val variant = variantOverride ?: mediaPlan.preferredForQuality(
            sourceId = selectedSourceId,
            seasonNumber = seasonNumber,
            episodeNumber = episode,
            voiceover = selectedVoiceover,
            quality = desiredQuality,
        )
        val subtitle = if (episode != null) {
            "Сезон $seasonNumber, серия $episode"
        } else {
            "${variant.voiceover} • ${variant.quality}"
        }
        val builder = MediaItem.Builder()
            .setMediaId(
                if (episode != null) {
                    "$selectionTitle|$selectedSourceId|s$seasonNumber|e$episode"
                } else {
                    "$selectionTitle|$selectedSourceId"
                },
            )
            .setUri(variant.mediaUrl)
            .setTag(PlaybackMediaItemTag(variant.id, playlistGeneration))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(selectionTitle)
                    .setSubtitle(subtitle)
                    .build(),
            )
        variant.mimeType?.let(builder::setMimeType)
        if (variant.subtitleTracks.isNotEmpty()) {
            builder.setSubtitleConfigurations(
                variant.subtitleTracks.map { track ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.mediaUrl))
                        .setId(track.id)
                        .setLabel(track.label)
                        .setMimeType(track.mimeType)
                        .apply { track.languageTag?.let(::setLanguage) }
                        .build()
                },
            )
        }
        return builder.build()
    }

    /**
     * Collaps and similar adaptive manifests identify translation tracks by a stable zero-based
     * index. Apply that index only after Media3 has exposed the manifest's audio groups.
     */
    private fun applyPreferredAudioTrack(tracks: Tracks) {
        val variantId = player.currentMediaItem?.playbackTag()?.variantId ?: return
        if (lastAppliedAudioVariantId == variantId) return
        val variant = mediaPlan.findById(variantId) ?: return
        val requestedIndex = variant.preferredAudioTrackIndex
        val audioTracks = tracks.groups
            .asSequence()
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { group ->
                (0 until group.length).asSequence().map { trackIndex ->
                    AudioTrackTarget(group, trackIndex)
                }
            }
            .toList()
        if (requestedIndex != null && requestedIndex !in audioTracks.indices) return

        val parameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .apply {
                requestedIndex?.let { index ->
                    val target = audioTracks[index]
                    addOverride(
                        TrackSelectionOverride(
                            target.group.mediaTrackGroup,
                            listOf(target.trackIndex),
                        ),
                    )
                }
            }
            .build()
        lastAppliedAudioVariantId = variantId
        if (parameters != player.trackSelectionParameters) {
            player.trackSelectionParameters = parameters
        }
    }

    private fun updateAdaptiveVideoTracks(tracks: Tracks) {
        adaptiveVideoTracks = tracks.groups
            .asSequence()
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                (0 until group.length).asSequence().mapNotNull { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    val height = format.height.takeIf { it > 0 } ?: return@mapNotNull null
                    AdaptiveVideoTrack(
                        label = "${height}p",
                        height = height,
                        bitrate = format.bitrate.takeIf { it > 0 } ?: 0,
                        group = group,
                        trackIndex = trackIndex,
                    )
                }
            }
            .groupBy(AdaptiveVideoTrack::height)
            .values
            .map { sameHeight -> sameHeight.maxBy(AdaptiveVideoTrack::bitrate) }
            .sortedByDescending(AdaptiveVideoTrack::height)
    }

    private fun updateSelectableTextTracks(tracks: Tracks) {
        val labels = linkedMapOf<String, Int>()
        selectableTextTracks = tracks.groups
            .mapIndexedNotNull { groupIndex, group ->
                if (group.type != C.TRACK_TYPE_TEXT) return@mapIndexedNotNull null
                (0 until group.length).map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    val rawLabel = format.label
                        ?.takeIf(String::isNotBlank)
                        ?: format.language?.takeIf(String::isNotBlank)
                        ?: "Субтитры ${labels.size + 1}"
                    val count = (labels[rawLabel] ?: 0) + 1
                    labels[rawLabel] = count
                    SelectableTextTrack(
                        id = "text:$groupIndex:$trackIndex",
                        label = if (count == 1) rawLabel else "$rawLabel · $count",
                        group = group,
                        trackIndex = trackIndex,
                    )
                }
            }
            .flatten()
        if (selectedSubtitleOptionId != null &&
            selectableTextTracks.none { it.id == selectedSubtitleOptionId }
        ) {
            selectedSubtitleOptionId = null
        }
    }

    private fun applySelectedVideoTrack() {
        val variantId = player.currentMediaItem?.playbackTag()?.variantId ?: return
        val resolvedTarget = if (isAutoQuality(desiredQuality)) {
            null
        } else {
            val fixedVariants = mediaPlan
                .variantsFor(
                    sourceId = selectedSourceId,
                    seasonNumber = currentSeasonNumber(),
                    episodeNumber = currentEpisodeNumber(),
                )
                .filter {
                    it.voiceover == selectedVoiceover &&
                        !isAutoQuality(it.quality)
                }
            val candidates = buildList<PlaybackQualityCandidate<ResolvedVideoQualityTarget>> {
                adaptiveVideoTracks.forEach { track ->
                    add(
                        PlaybackQualityCandidate(
                            value = ResolvedVideoQualityTarget.AdaptiveTrack(track),
                            label = track.label,
                        ),
                    )
                }
                fixedVariants.forEach { variant ->
                    add(
                        PlaybackQualityCandidate(
                            value = ResolvedVideoQualityTarget.PlanVariant(variant),
                            label = variant.quality,
                        ),
                    )
                }
            }
            PlaybackQualityPolicy.select(
                desiredQuality = desiredQuality,
                candidates = candidates,
                preferAutomaticForMissingFixed = false,
            )?.selected
        }
        if (!isAutoQuality(desiredQuality) && resolvedTarget == null) return
        val targetTrack = when (resolvedTarget) {
            is ResolvedVideoQualityTarget.AdaptiveTrack -> resolvedTarget.track
            is ResolvedVideoQualityTarget.PlanVariant -> {
                if (resolvedTarget.variant.id != activeVariantId) {
                    requestQualityVariantSwitch(resolvedTarget.variant)
                    return
                }
                null
            }
            null -> null
        }
        val targetKey = when (resolvedTarget) {
            is ResolvedVideoQualityTarget.AdaptiveTrack -> "track:${resolvedTarget.track.height}"
            is ResolvedVideoQualityTarget.PlanVariant -> "variant:${resolvedTarget.variant.id}"
            null -> "auto"
        }
        val key = "$variantId:$desiredQuality:$targetKey"
        if (lastAppliedVideoSelectionKey == key) return

        val parameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .apply {
                targetTrack?.let {
                    addOverride(
                        TrackSelectionOverride(
                            it.group.mediaTrackGroup,
                            listOf(it.trackIndex),
                        ),
                    )
                }
            }
            .build()
        lastAppliedVideoSelectionKey = key
        if (parameters != player.trackSelectionParameters) {
            player.trackSelectionParameters = parameters
        }
    }

    private fun applyDesiredVideoConstraints() {
        val parameters = player.trackSelectionParameters.withVideoQualityIntent(desiredQuality)
        lastAppliedVideoSelectionKey = null
        if (parameters != player.trackSelectionParameters) {
            player.trackSelectionParameters = parameters
        }
    }

    private fun requestQualityVariantSwitch(variant: PlaybackMediaVariant) {
        val request = PlaybackQualitySwitchRequest(
            targetVariantId = variant.id,
            context = currentQualitySwitchContext(),
        )
        if (pendingQualitySwitchRequest == request) return
        pendingQualitySwitchRequest = request
        scope.launch {
            if (request.isApplicableTo(currentQualitySwitchContext())) {
                switchVariant(variant)
            }
            if (pendingQualitySwitchRequest == request) pendingQualitySwitchRequest = null
        }
    }

    private fun currentQualitySwitchContext(): PlaybackQualitySwitchContext =
        PlaybackQualitySwitchContext(
            sourceId = selectedSourceId,
            voiceover = selectedVoiceover,
            seasonNumber = currentSeasonNumber(),
            episodeNumber = currentEpisodeNumber(),
            currentMediaItemVariantId =
                player.currentMediaItem?.playbackTag()?.variantId,
            desiredQuality = desiredQuality,
            playlistGeneration = playlistGeneration,
            qualityGeneration = qualityGeneration,
        )

    private fun advancePlaylistGeneration() {
        playlistGeneration++
        preloadRearmPositionMs = 0L
        pendingPreloadFailure = null
        pendingQualitySwitchRequest = null
    }

    private fun advanceQualityGeneration() {
        qualityGeneration++
        pendingQualitySwitchRequest = null
    }

    private fun applySubtitleSelection() {
        val variantId = player.currentMediaItem?.playbackTag()?.variantId ?: return
        val selected = selectedSubtitleOptionId?.let { id ->
            selectableTextTracks.firstOrNull { it.id == id }
        }
        if (selectedSubtitleOptionId != null && selected == null) return
        val key = "$variantId:${if (!subtitlesEnabled) "off" else selected?.id ?: "system"}"
        if (lastAppliedSubtitleSelectionKey == key) return

        val parameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
            .setSelectTextByDefault(subtitlesEnabled)
            .setSelectUndeterminedTextLanguage(subtitlesEnabled)
            .apply {
                selected?.let {
                    addOverride(
                        TrackSelectionOverride(
                            it.group.mediaTrackGroup,
                            listOf(it.trackIndex),
                        ),
                    )
                }
            }
            .build()
        lastAppliedSubtitleSelectionKey = key
        if (parameters != player.trackSelectionParameters) {
            player.trackSelectionParameters = parameters
        }
    }

    private fun currentSeasonNumber(): Int? = if (isEpisode) season else null

    private fun currentEpisodeNumber(): Int? = if (isEpisode) selectedEpisode else null

    private fun mediaVariantIdAt(windowIndex: Int): String? =
        if (windowIndex in 0 until player.mediaItemCount) {
            player.getMediaItemAt(windowIndex).playbackTag()
                ?.takeIf { it.playlistGeneration == playlistGeneration }
                ?.variantId
        } else {
            null
        }

    private fun preparedPlaylistCoordinates(): List<PlaybackEpisodeCoordinate?> =
        List(player.mediaItemCount) { windowIndex ->
            val variantId = mediaVariantIdAt(windowIndex) ?: return@List null
            val variant = mediaPlan.findById(variantId) ?: return@List null
            if (
                variant.sourceId != selectedSourceId ||
                variant.voiceover != selectedVoiceover
            ) return@List null
            val seasonNumber = variant.effectiveSeasonNumber ?: return@List null
            val episodeNumber = variant.episodeNumber ?: return@List null
            PlaybackEpisodeCoordinate(seasonNumber, episodeNumber)
        }

    private fun mediaTagAt(eventTime: AnalyticsListener.EventTime): PlaybackMediaItemTag? {
        val windowIndex = eventTime.windowIndex
        if (windowIndex !in 0 until eventTime.timeline.windowCount) return null
        return eventTime.timeline
            .getWindow(windowIndex, Timeline.Window())
            .mediaItem
            .playbackTag()
            ?.takeIf { it.playlistGeneration == playlistGeneration }
    }

    private fun currentEpisodeCoordinate(): PlaybackEpisodeCoordinate =
        PlaybackEpisodeCoordinate(seasonNumber = season, episodeNumber = selectedEpisode)

    private fun currentSelection(): PlaybackSelectionUiModel = baseSelection.copy(
        season = if (isEpisode) season else null,
        episode = if (isEpisode) selectedEpisode else null,
        voiceover = selectedVoiceover,
        quality = desiredQuality,
        resume = true,
        sourceId = selectedSourceId,
    )
}

private data class PlaybackMediaItemTag(
    val variantId: String,
    val playlistGeneration: Long,
)

private fun MediaItem.playbackTag(): PlaybackMediaItemTag? =
    localConfiguration?.tag as? PlaybackMediaItemTag

private fun Player.playbackStallState(): PlaybackStallState = when (playbackState) {
    Player.STATE_BUFFERING -> PlaybackStallState.BUFFERING
    Player.STATE_READY -> PlaybackStallState.READY
    Player.STATE_ENDED -> PlaybackStallState.ENDED
    else -> PlaybackStallState.IDLE
}

private data class AudioTrackTarget(
    val group: Tracks.Group,
    val trackIndex: Int,
)

private data class AdaptiveVideoTrack(
    val label: String,
    val height: Int,
    val bitrate: Int,
    val group: Tracks.Group,
    val trackIndex: Int,
)

private sealed interface ResolvedVideoQualityTarget {
    data class AdaptiveTrack(val track: AdaptiveVideoTrack) : ResolvedVideoQualityTarget
    data class PlanVariant(val variant: PlaybackMediaVariant) : ResolvedVideoQualityTarget
}

private data class SelectableTextTrack(
    val id: String,
    val label: String,
    val group: Tracks.Group,
    val trackIndex: Int,
)

private data class PlayerTextTrackOption(
    val id: String,
    val label: String,
)

internal fun isAutoQuality(label: String): Boolean =
    PlaybackQualityPolicy.isAutomatic(label)

private fun userSafePlaybackError(error: PlaybackException): String {
    val responseCode = generateSequence<Throwable>(error) { it.cause }
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()
        ?.responseCode
    return when {
        responseCode in setOf(401, 403, 404, 410) ->
            "Ссылка источника устарела. Вернитесь в карточку и нажмите «Смотреть»."
        responseCode != null ->
            "Сервер источника ответил ошибкой $responseCode"
        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Не удалось подключиться к серверу видео"
        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "Устройство не смогло декодировать этот формат видео"
        else -> "Не удалось воспроизвести выбранный источник"
    }
}

private class TvMediaSessionCallback(
    private val runtime: TvPlayerRuntime,
) : MediaSession.Callback {
    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent,
    ): Boolean {
        val keyEvent = intent.mediaKeyEvent() ?: return false
        return runtime.handleMediaButton(keyEvent)
    }
}

private fun handleComposeKeyEvent(
    keyEvent: KeyEvent,
    runtime: TvPlayerRuntime,
    rootFocused: Boolean,
): Boolean {
    // MediaSession is the single owner of transport keys.
    if (isMediaKeyCode(keyEvent.keyCode)) return false

    if (
        runtime.state.hud == PlayerHudState.VISIBLE &&
        keyEvent.action == KeyEvent.ACTION_DOWN &&
        isInteractiveDpadKey(keyEvent.keyCode)
    ) {
        // Keep the HUD alive. Compose normally owns navigation, except during the brief interval
        // between revealing the HUD and applying its requested focus.
        runtime.dispatch(PlayerIntent.ShowHud(keyEvent.eventTime))
        val keyKind = when {
            isPrimaryDpadKey(keyEvent.keyCode) -> VisibleHudKeyKind.PRIMARY
            isDpadSeekKey(keyEvent.keyCode) -> VisibleHudKeyKind.SEEK
            else -> VisibleHudKeyKind.OTHER
        }
        if (
            shouldDispatchVisibleHudKeyAtRoot(
                rootFocused = rootFocused,
                focusTarget = runtime.state.hudFocusTarget,
                episodeNumberInputActive = runtime.state.episodeNumberInput.isActive,
                keyKind = keyKind,
            )
        ) {
            // On slower TVs this also preserves a fast second OK/Left/Right. Once the requested
            // control owns focus, the helper returns false and the event cannot be applied twice.
            return runtime.mapAndDispatch(keyEvent)
        }
        return false
    }
    return runtime.mapAndDispatch(keyEvent)
}

private fun isPrimaryDpadKey(keyCode: Int): Boolean = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER,
    KeyEvent.KEYCODE_BUTTON_A,
    KeyEvent.KEYCODE_BUTTON_SELECT,
    -> true
    else -> false
}

private fun isDpadSeekKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT

private fun isInteractiveDpadKey(keyCode: Int): Boolean = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER,
    KeyEvent.KEYCODE_BUTTON_A,
    KeyEvent.KEYCODE_BUTTON_SELECT,
    -> true
    else -> false
}

private fun isMediaKeyCode(keyCode: Int): Boolean = when (keyCode) {
    KeyEvent.KEYCODE_MEDIA_PLAY,
    KeyEvent.KEYCODE_MEDIA_PAUSE,
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    KeyEvent.KEYCODE_MEDIA_STOP,
    KeyEvent.KEYCODE_MEDIA_NEXT,
    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
    KeyEvent.KEYCODE_MEDIA_REWIND,
    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
    KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
    KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
    KeyEvent.KEYCODE_HEADSETHOOK,
    -> true
    else -> false
}

private fun isDigitKeyCode(keyCode: Int): Boolean =
    keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
        keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9

@Suppress("DEPRECATION")
private fun Intent.mediaKeyEvent(): KeyEvent? =
    getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent

private fun compactHudValue(value: String, maxLength: Int = 22): String {
    val normalized = value.trim().replace(Regex("""\s+"""), " ")
    if (normalized.length <= maxLength) return normalized
    return normalized.take((maxLength - 1).coerceAtLeast(1)).trimEnd() + "…"
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
