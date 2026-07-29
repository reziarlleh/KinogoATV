package com.kinogo.atv.player.web

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.kinogo.atv.data.playback.ResolvedPlaybackEmbed
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.delay

private const val HUD_TIMEOUT_MS = 5_000L

/**
 * Full-screen provider-only fallback. The catalog, details and navigation stay native; only the
 * already validated, freshly resolved provider document is rendered by WebView.
 *
 * The entry URL can use a rotating provider domain. Navigation is nevertheless pinned to the
 * exact HTTPS origin of this one fresh offer; the provider label never grants wildcard access.
 */
@Composable
fun ProviderEmbedPlayerScreen(
    source: ResolvedPlaybackEmbed,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onRefreshSourceRequested: (() -> Unit)? = null,
    title: String = source.label,
    seekStepSeconds: Int = 10,
) {
    val allowedOrigin = requireNotNull(exactHttpsOrigin(source.embedUrl)) {
        "Provider embed must use a safe HTTPS origin"
    }
    require(exactHttpsOrigin(source.refererUrl) != null)
    require(seekStepSeconds in 1..600)

    val composeView = LocalView.current
    val lifecycleOwner = remember(composeView) { composeView.findViewTreeLifecycleOwner() }
    val rootFocus = remember { FocusRequester() }
    val primaryFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }
    var webPlayer by remember(source.id) { mutableStateOf<CinemarWebPlayerView?>(null) }
    var loading by remember(source.id) { mutableStateOf(true) }
    var errorMessage by remember(source.id) { mutableStateOf<String?>(null) }
    var notice by remember(source.id) { mutableStateOf<String?>(null) }
    var hudVisible by remember(source.id) { mutableStateOf(true) }
    var cursorMode by remember(source.id) { mutableStateOf(false) }
    var cursorX by remember(source.id) { mutableStateOf(0.5f) }
    var cursorY by remember(source.id) { mutableStateOf(0.5f) }
    var interactionGeneration by remember(source.id) { mutableIntStateOf(0) }
    var recoveryState by remember(source.id) { mutableStateOf(CinemarWebViewRecoveryState()) }

    fun showHud(message: String? = null) {
        notice = message
        hudVisible = true
        interactionGeneration++
    }

    fun runCommand(command: PlayerJsCommand) {
        val player = webPlayer
        if (player == null || loading) {
            showHud("Плеер ещё загружается")
            return
        }
        player.execute(command) { accepted ->
            showHud(if (accepted) null else "Команда пока недоступна")
        }
    }

    fun handleBack() {
        if (cursorMode) {
            cursorMode = false
            showHud("Режим курсора выключен")
            return
        }
        if (webPlayer?.hideProviderFullscreen() == true) return
        if (hudVisible && errorMessage == null) {
            hudVisible = false
        } else {
            webPlayer?.execute(PlayerJsCommand.Stop)
            onExit()
        }
    }

    BackHandler(onBack = ::handleBack)

    val observedPlayer = webPlayer
    DisposableEffect(observedPlayer, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> observedPlayer?.onHostResume()
                Lifecycle.Event.ON_PAUSE -> observedPlayer?.onHostPause()
                else -> Unit
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            observedPlayer?.destroyPlayer()
        }
    }

    LaunchedEffect(Unit) {
        rootFocus.requestFocus()
    }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) runCatching { errorFocus.requestFocus() }
    }
    LaunchedEffect(hudVisible, interactionGeneration) {
        if (!hudVisible || errorMessage != null) return@LaunchedEffect
        runCatching { primaryFocus.requestFocus() }
        delay(HUD_TIMEOUT_MS)
        hudVisible = false
        notice = null
        runCatching { rootFocus.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                handleTvKey(
                    event = event.nativeKeyEvent,
                    hudVisible = hudVisible,
                    showHud = ::showHud,
                    hideHud = { hudVisible = false },
                    runCommand = ::runCommand,
                    seekStepSeconds = seekStepSeconds,
                    cursorMode = cursorMode,
                    exitCursorMode = {
                        cursorMode = false
                        showHud("Режим курсора выключен")
                    },
                    moveCursor = { dx, dy ->
                        cursorX = (cursorX + dx).coerceIn(0.02f, 0.98f)
                        cursorY = (cursorY + dy).coerceIn(0.02f, 0.98f)
                    },
                    clickCursor = { webPlayer?.clickAt(cursorX, cursorY) },
                    onBack = ::handleBack,
                )
            }
            .focusable(),
    ) {
        key(recoveryState.instanceGeneration, allowedOrigin) {
            val instanceGeneration = recoveryState.instanceGeneration
            val callbacks = CinemarPlayerCallbacks(
                onLoading = {
                    if (recoveryState.instanceGeneration == instanceGeneration) loading = it
                },
                onFatalError = {
                    if (recoveryState.instanceGeneration == instanceGeneration) {
                        loading = false
                        errorMessage = it
                        showHud()
                    }
                },
                onRendererGone = {
                    if (recoveryState.instanceGeneration == instanceGeneration) {
                        recoveryState = recoveryState.onRendererGone()
                        loading = false
                        errorMessage = "Процесс встроенного плеера завершился"
                        showHud()
                    }
                },
                onBlockedAction = {
                    if (recoveryState.instanceGeneration == instanceGeneration) showHud(it)
                },
            )
            AndroidView(
                factory = { context ->
                    CinemarWebPlayerView(
                        context = context,
                        source = source,
                        allowedOrigin = allowedOrigin,
                        callbacks = callbacks,
                    ).also { webPlayer = it }
                },
                update = { view ->
                    view.updateCallbacks(callbacks)
                    view.updateSource(source)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (loading && errorMessage == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xD90A0F18))
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text("Загрузка плеера…", color = Color.White, fontSize = 18.sp)
            }
        }

        errorMessage?.let { message ->
            ProviderErrorPanel(
                message = message,
                onRetry = {
                    if (onRefreshSourceRequested != null) {
                        onRefreshSourceRequested()
                        return@ProviderErrorPanel
                    }
                    val retry = recoveryState.retry(
                        currentInstanceCanReload = webPlayer?.canReloadSource == true,
                    )
                    errorMessage = null
                    loading = true
                    recoveryState = retry.nextState
                    if (retry.action == CinemarWebViewRecoveryAction.RELOAD) {
                        webPlayer?.reloadSource()
                    }
                },
                onExit = onExit,
                retryFocus = errorFocus,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (cursorMode && errorMessage == null) {
            VirtualCursorOverlay(cursorX = cursorX, cursorY = cursorY)
        }

        if (hudVisible && !cursorMode && errorMessage == null) {
            ProviderHud(
                title = title,
                providerLabel = source.label,
                notice = notice,
                seekStepSeconds = seekStepSeconds,
                primaryFocus = primaryFocus,
                runCommand = ::runCommand,
                onCursorMode = {
                    cursorMode = true
                    hudVisible = false
                    notice = null
                    runCatching { rootFocus.requestFocus() }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Backward-compatible name for callers built before the generic provider fallback was added. */
@Composable
fun CinemarEmbedPlayerScreen(
    source: ResolvedPlaybackEmbed,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onRefreshSourceRequested: (() -> Unit)? = null,
    title: String = source.label,
    seekStepSeconds: Int = 10,
) = ProviderEmbedPlayerScreen(
    source = source,
    onExit = onExit,
    onRefreshSourceRequested = onRefreshSourceRequested,
    modifier = modifier,
    title = title,
    seekStepSeconds = seekStepSeconds,
)

@Composable
private fun ProviderHud(
    title: String,
    providerLabel: String,
    notice: String?,
    seekStepSeconds: Int,
    primaryFocus: FocusRequester,
    runCommand: (PlayerJsCommand) -> Unit,
    onCursorMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0xE6000000)),
                ),
            )
            .padding(horizontal = 34.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(
            text = notice ?: "Web-плеер $providerLabel · резервный режим",
            color = if (notice == null) Color(0xFFC8D1DF) else MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderButton("Пред.") { runCommand(PlayerJsCommand.Previous) }
            ProviderButton("−$seekStepSeconds") {
                runCommand(PlayerJsCommand.SeekRelative(-seekStepSeconds))
            }
            ProviderButton(
                text = "Пуск / пауза",
                primary = true,
                modifier = Modifier.focusRequester(primaryFocus),
            ) { runCommand(PlayerJsCommand.Toggle) }
            ProviderButton("+$seekStepSeconds") {
                runCommand(PlayerJsCommand.SeekRelative(seekStepSeconds))
            }
            ProviderButton("След.") { runCommand(PlayerJsCommand.Next) }
            ProviderButton("Курсор", onClick = onCursorMode)
            ProviderButton("Стоп") { runCommand(PlayerJsCommand.Stop) }
        }
    }
}

@Composable
private fun ProviderButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) MaterialTheme.colorScheme.primary else Color(0xD9324055),
            contentColor = if (primary) Color(0xFF07131E) else Color.White,
        ),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProviderErrorPanel(
    message: String,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    retryFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(520.dp)
            .background(Color(0xF20A0F18))
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Не удалось открыть плеер", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(message, color = Color(0xFFC8D1DF), fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProviderButton(
                "Повторить",
                modifier = Modifier.focusRequester(retryFocus),
                primary = true,
                onClick = onRetry,
            )
            ProviderButton("Назад", onClick = onExit)
        }
    }
}

@Composable
private fun VirtualCursorOverlay(
    cursorX: Float,
    cursorY: Float,
) {
    Box(Modifier.fillMaxSize()) {
        Text(
            text = "Курсор · стрелки — движение · OK — нажать · Back — выйти",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(Color(0xD9000000))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Canvas(Modifier.fillMaxSize()) {
            val center = androidx.compose.ui.geometry.Offset(
                x = size.width * cursorX,
                y = size.height * cursorY,
            )
            drawCircle(Color.Black.copy(alpha = 0.72f), radius = 13.dp.toPx(), center = center)
            drawCircle(Color.White, radius = 10.dp.toPx(), center = center)
            drawCircle(
                color = Color(0xFF00D6FF),
                radius = 7.dp.toPx(),
                center = center,
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

private fun handleTvKey(
    event: KeyEvent,
    hudVisible: Boolean,
    showHud: (String?) -> Unit,
    hideHud: () -> Unit,
    runCommand: (PlayerJsCommand) -> Unit,
    seekStepSeconds: Int,
    cursorMode: Boolean,
    exitCursorMode: () -> Unit,
    moveCursor: (dx: Float, dy: Float) -> Unit,
    clickCursor: () -> Unit,
    onBack: () -> Unit,
): Boolean {
    val handled = isHandledProviderKey(event.keyCode, hudVisible, cursorMode)
    if (!handled || event.action != KeyEvent.ACTION_DOWN) return handled
    if (event.repeatCount > 0 && event.keyCode !in REPEATABLE_SEEK_KEYS) return true

    if (cursorMode) {
        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> exitCursorMode()
            KeyEvent.KEYCODE_DPAD_LEFT -> moveCursor(-CURSOR_STEP, 0f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveCursor(CURSOR_STEP, 0f)
            KeyEvent.KEYCODE_DPAD_UP -> moveCursor(0f, -CURSOR_STEP)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveCursor(0f, CURSOR_STEP)
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> clickCursor()
            KeyEvent.KEYCODE_MEDIA_PLAY -> runCommand(PlayerJsCommand.Play)
            KeyEvent.KEYCODE_MEDIA_PAUSE -> runCommand(PlayerJsCommand.Pause)
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            -> runCommand(PlayerJsCommand.Toggle)
            KeyEvent.KEYCODE_MEDIA_STOP -> runCommand(PlayerJsCommand.Stop)
            KeyEvent.KEYCODE_MEDIA_NEXT -> runCommand(PlayerJsCommand.Next)
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> runCommand(PlayerJsCommand.Previous)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
            -> runCommand(PlayerJsCommand.SeekRelative(seekStepSeconds))
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
            -> runCommand(PlayerJsCommand.SeekRelative(-seekStepSeconds))
        }
        return true
    }

    when (event.keyCode) {
        KeyEvent.KEYCODE_BACK -> onBack()
        KeyEvent.KEYCODE_MEDIA_PLAY -> runCommand(PlayerJsCommand.Play)
        KeyEvent.KEYCODE_MEDIA_PAUSE -> runCommand(PlayerJsCommand.Pause)
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK,
        -> runCommand(PlayerJsCommand.Toggle)
        KeyEvent.KEYCODE_MEDIA_STOP -> runCommand(PlayerJsCommand.Stop)
        KeyEvent.KEYCODE_MEDIA_NEXT -> runCommand(PlayerJsCommand.Next)
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> runCommand(PlayerJsCommand.Previous)
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
        -> runCommand(PlayerJsCommand.SeekRelative(seekStepSeconds))
        KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
        -> runCommand(PlayerJsCommand.SeekRelative(-seekStepSeconds))
        KeyEvent.KEYCODE_DPAD_LEFT -> runCommand(PlayerJsCommand.SeekRelative(-seekStepSeconds))
        KeyEvent.KEYCODE_DPAD_RIGHT -> runCommand(PlayerJsCommand.SeekRelative(seekStepSeconds))
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> runCommand(PlayerJsCommand.Toggle)
        KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        -> if (hudVisible) hideHud() else showHud(null)
    }
    return true
}

private fun isHandledProviderKey(
    keyCode: Int,
    hudVisible: Boolean,
    cursorMode: Boolean,
): Boolean = when (keyCode) {
    KeyEvent.KEYCODE_BACK,
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
    KeyEvent.KEYCODE_MENU,
    -> true
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER,
    -> cursorMode || !hudVisible
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    -> true
    else -> false
}

private val REPEATABLE_SEEK_KEYS = setOf(
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_MEDIA_REWIND,
    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
    KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
    KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
)

private const val CURSOR_STEP = 0.035f

private data class CinemarPlayerCallbacks(
    val onLoading: (Boolean) -> Unit,
    val onFatalError: (String) -> Unit,
    val onRendererGone: () -> Unit,
    val onBlockedAction: (String) -> Unit,
)

internal enum class CinemarWebViewRecoveryAction {
    RELOAD,
    RECREATE,
}

internal data class CinemarWebViewRecoveryResult(
    val nextState: CinemarWebViewRecoveryState,
    val action: CinemarWebViewRecoveryAction,
)

internal data class CinemarWebViewRecoveryState(
    val instanceGeneration: Int = 0,
    val rendererGone: Boolean = false,
) {
    fun onRendererGone(): CinemarWebViewRecoveryState = copy(rendererGone = true)

    fun retry(currentInstanceCanReload: Boolean): CinemarWebViewRecoveryResult {
        val mustRecreate = rendererGone || !currentInstanceCanReload
        return if (mustRecreate) {
            CinemarWebViewRecoveryResult(
                nextState = copy(
                    instanceGeneration = instanceGeneration + 1,
                    rendererGone = false,
                ),
                action = CinemarWebViewRecoveryAction.RECREATE,
            )
        } else {
            CinemarWebViewRecoveryResult(
                nextState = this,
                action = CinemarWebViewRecoveryAction.RELOAD,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private class CinemarWebPlayerView(
    context: android.content.Context,
    source: ResolvedPlaybackEmbed,
    allowedOrigin: String,
    callbacks: CinemarPlayerCallbacks,
) : FrameLayout(context) {
    private var callbacks = callbacks
    private var source = source
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var destroyed = false
    private val webView = WebView(context)

    val canReloadSource: Boolean
        get() = !destroyed

    init {
        setBackgroundColor(AndroidColor.BLACK)
        isFocusable = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        WebView.startSafeBrowsing(context.applicationContext, null)

        webView.apply {
            setBackgroundColor(AndroidColor.BLACK)
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            settings.applySecureProviderSettings()
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = SecureCinemarWebViewClient(
                allowedOrigin = allowedOrigin,
                onLoading = { this@CinemarWebPlayerView.callbacks.onLoading(it) },
                onFatalError = { this@CinemarWebPlayerView.callbacks.onFatalError(it) },
                onRendererGone = {
                    discardAfterRendererGone()
                    this@CinemarWebPlayerView.callbacks.onRendererGone()
                },
                onBlockedAction = {
                    this@CinemarWebPlayerView.callbacks.onBlockedAction(it)
                },
            )
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?,
                ): Boolean {
                    this@CinemarWebPlayerView.callbacks.onBlockedAction(
                        "Всплывающее окно заблокировано",
                    )
                    return false
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?,
                ): Boolean {
                    filePathCallback?.onReceiveValue(null)
                    this@CinemarWebPlayerView.callbacks.onBlockedAction(
                        "Выбор файлов для встроенного плеера запрещён",
                    )
                    return true
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.deny()
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?,
                ) {
                    callback?.invoke(origin, false, false)
                }

                override fun onShowCustomView(
                    view: View?,
                    callback: CustomViewCallback?,
                ) {
                    if (view == null || customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    customView = view
                    customViewCallback = callback
                    webView.visibility = View.INVISIBLE
                    addView(
                        view,
                        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
                    )
                }

                override fun onHideCustomView() {
                    hideProviderFullscreen()
                }
            }
            setDownloadListener { _, _, _, _, _ ->
                this@CinemarWebPlayerView.callbacks.onBlockedAction("Загрузка файла заблокирована")
            }
        }
        addView(
            webView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        loadSource()
    }

    fun updateCallbacks(value: CinemarPlayerCallbacks) {
        callbacks = value
    }

    fun updateSource(value: ResolvedPlaybackEmbed) {
        if (value.embedUrl == source.embedUrl && value.refererUrl == source.refererUrl) return
        source = value
        loadSource()
    }

    fun reloadSource() {
        loadSource()
    }

    fun execute(command: PlayerJsCommand, result: (Boolean) -> Unit = {}) {
        if (destroyed) {
            result(false)
            return
        }
        webView.evaluateJavascript(PlayerJsCommandBuilder.javascript(command)) { rawResult ->
            result(rawResult == "true")
        }
    }

    fun clickAt(normalizedX: Float, normalizedY: Float) {
        if (destroyed) return
        val target = customView ?: webView
        if (target.width <= 0 || target.height <= 0) return
        val x = target.width * normalizedX.coerceIn(0f, 1f)
        val y = target.height * normalizedY.coerceIn(0f, 1f)
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0,
        )
        val up = MotionEvent.obtain(
            downTime,
            downTime + 32L,
            MotionEvent.ACTION_UP,
            x,
            y,
            0,
        )
        try {
            target.dispatchTouchEvent(down)
            target.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    fun onHostResume() {
        if (!destroyed) webView.onResume()
    }

    fun onHostPause() {
        if (!destroyed) {
            execute(PlayerJsCommand.Pause)
            webView.onPause()
        }
    }

    fun hideProviderFullscreen(): Boolean {
        val view = customView ?: return false
        customView = null
        removeView(view)
        webView.visibility = View.VISIBLE
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        return true
    }

    fun destroyPlayer() {
        if (destroyed) return
        destroyed = true
        hideProviderFullscreen()
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        removeView(webView)
        webView.destroy()
    }

    private fun loadSource() {
        if (destroyed) return
        callbacks.onLoading(true)
        webView.loadUrl(source.embedUrl, mapOf("Referer" to source.refererUrl))
    }

    /**
     * A WebView whose renderer has gone must never receive load/evaluate/pause calls again. Detach
     * and destroy only that dead instance; Compose creates a fresh wrapper after the user retries.
     */
    private fun discardAfterRendererGone() {
        if (destroyed) return
        destroyed = true
        customView?.let(::removeView)
        customView = null
        customViewCallback = null
        removeView(webView)
        webView.destroy()
    }
}

@Suppress("DEPRECATION")
private fun WebSettings.applySecureProviderSettings() {
    javaScriptEnabled = true
    domStorageEnabled = true
    allowFileAccess = false
    allowContentAccess = false
    allowFileAccessFromFileURLs = false
    allowUniversalAccessFromFileURLs = false
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    javaScriptCanOpenWindowsAutomatically = false
    setSupportMultipleWindows(false)
    setGeolocationEnabled(false)
    mediaPlaybackRequiresUserGesture = true
    safeBrowsingEnabled = true
    builtInZoomControls = false
    displayZoomControls = false
    saveFormData = false
}

private class SecureCinemarWebViewClient(
    private val allowedOrigin: String,
    private val onLoading: (Boolean) -> Unit,
    private val onFatalError: (String) -> Unit,
    private val onRendererGone: () -> Unit,
    private val onBlockedAction: (String) -> Unit,
) : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        onLoading(true)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        onLoading(false)
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        if (request == null || !request.isForMainFrame) return false
        return blockIfExternal(request.url.toString())
    }

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
        url == null || blockIfExternal(url)

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        if (request?.isForMainFrame != true) return
        onFatalError("Ошибка сети при загрузке встроенного плеера")
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
    ) {
        if (request?.isForMainFrame != true || (errorResponse?.statusCode ?: 0) < 400) return
        onFatalError("Сервер плеера ответил с ошибкой ${errorResponse?.statusCode}")
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?,
    ) {
        handler?.cancel()
        onFatalError("Не удалось проверить защищённое соединение с плеером")
    }

    override fun onSafeBrowsingHit(
        view: WebView?,
        request: WebResourceRequest?,
        threatType: Int,
        callback: SafeBrowsingResponse?,
    ) {
        callback?.backToSafety(true)
        onFatalError("Опасная страница встроенного плеера заблокирована")
    }

    override fun onRenderProcessGone(
        view: WebView?,
        detail: RenderProcessGoneDetail?,
    ): Boolean {
        onRendererGone()
        return true
    }

    private fun blockIfExternal(url: String): Boolean {
        if (exactHttpsOrigin(url) == allowedOrigin) return false
        onBlockedAction("Внешний переход из плеера заблокирован")
        return true
    }
}

private fun exactHttpsOrigin(rawUrl: String): String? {
    if (
        rawUrl.isBlank() ||
        rawUrl != rawUrl.trim() ||
        rawUrl.any(Char::isISOControl) ||
        '\\' in rawUrl
    ) {
        return null
    }
    val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
    if (
        !uri.scheme.equals("https", ignoreCase = true) ||
        uri.isOpaque ||
        uri.host.isNullOrBlank() ||
        uri.rawUserInfo != null ||
        uri.rawFragment != null ||
        (uri.port != -1 && uri.port != 443)
    ) {
        return null
    }
    return "https://${requireNotNull(uri.host).lowercase(Locale.ROOT)}"
}
