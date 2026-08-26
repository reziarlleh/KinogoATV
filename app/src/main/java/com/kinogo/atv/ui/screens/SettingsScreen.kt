package com.kinogo.atv.ui.screens

import java.net.URI
import java.text.DateFormat
import java.util.Date
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kinogo.atv.domain.AccountConnectionPhase
import com.kinogo.atv.domain.AccountConnectionState
import com.kinogo.atv.ui.components.TvActionButton
import com.kinogo.atv.ui.components.TvChoiceChip
import com.kinogo.atv.ui.components.TvSectionTitle
import com.kinogo.atv.ui.model.KinogoFixtures
import com.kinogo.atv.ui.model.AppUpdateUiModel
import com.kinogo.atv.ui.model.AppUpdateUiPhase
import com.kinogo.atv.ui.model.MirrorStatusUi
import com.kinogo.atv.ui.model.MirrorUiModel
import com.kinogo.atv.ui.model.MirrorUiState
import com.kinogo.atv.ui.model.SettingSectionUiModel
import com.kinogo.atv.ui.model.SettingControlUi
import com.kinogo.atv.ui.model.SettingUiModel
import com.kinogo.atv.ui.model.RegistrationSubmissionUiInput
import com.kinogo.atv.ui.model.RegistrationUiModel

@Composable
fun SettingsScreen(
    sections: List<SettingSectionUiModel>,
    modifier: Modifier = Modifier,
    mirrorState: MirrorUiState = KinogoFixtures.mirrorState,
    onCheckMirrors: () -> Unit = {},
    onManualMirrorSubmitted: (String) -> Unit = {},
    onMirrorSelected: (String) -> Unit = {},
    onMirrorRetry: (String) -> Unit = {},
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
    onAboutOpen: () -> Unit = {},
    onSettingSelected: (String, String) -> Unit = { _, _ -> },
    reduceMotion: Boolean = false,
    requestInitialFocus: Boolean = true,
) {
    val initialFocus = remember { FocusRequester() }
    var showManualMirrorDialog by rememberSaveable { mutableStateOf(false) }
    var mirrorDetailsId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAccountDialog by rememberSaveable { mutableStateOf(false) }
    var showRemoveAccountDialog by rememberSaveable { mutableStateOf(false) }
    val regularSections = sections.filterNot { it.id == "sources" }

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) initialFocus.requestFocus()
    }
    BackHandler(enabled = showManualMirrorDialog) { showManualMirrorDialog = false }
    BackHandler(enabled = mirrorDetailsId != null) { mirrorDetailsId = null }
    BackHandler(enabled = showAccountDialog) { showAccountDialog = false }
    BackHandler(enabled = showRemoveAccountDialog) { showRemoveAccountDialog = false }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TvSectionTitle(text = "Настройки", trailing = "TV-профиль")
            LazyColumn(
                modifier = Modifier.testTag("settings-list"),
                contentPadding = PaddingValues(start = 2.dp, top = 2.dp, end = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "about-application") {
                    AboutSettingsButton(
                        onClick = onAboutOpen,
                        modifier = Modifier.focusRequester(initialFocus),
                    )
                }
                item(key = "account-heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Аккаунт Kinogo",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = if (accountState.credentialsSaved) {
                                "Логин и пароль сохранены на этом устройстве"
                            } else {
                                "После входа логин и пароль будут сохранены на этом устройстве"
                            },
                            color = Color(0xFF96A4B8),
                            fontSize = 11.sp,
                        )
                    }
                }
                item(key = "account-card") {
                    AccountCard(
                        state = accountState,
                        pendingSyncCount = pendingSyncCount,
                        syncMessage = syncMessage,
                        onLogin = { showAccountDialog = true },
                        onRegister = onRegistrationOpen,
                        onReconnect = onAccountReconnect,
                        onSyncNow = onSyncNow,
                        onRemove = { showRemoveAccountDialog = true },
                    )
                }
                item(key = "mirror-heading") {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "Источник и зеркала",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "Новые адреса проходят проверку перед активацией",
                            color = Color(0xFF96A4B8),
                            fontSize = 11.sp,
                        )
                    }
                }
                item(key = "mirror-actions") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvActionButton(
                            text = if (mirrorState.isChecking) "Проверяем…" else "Проверить зеркала",
                            onClick = { if (!mirrorState.isChecking) onCheckMirrors() },
                            primary = true,
                            leadingMark = if (mirrorState.isChecking) "…" else "↻",
                        )
                        TvActionButton(
                            text = "Добавить адрес",
                            onClick = { showManualMirrorDialog = true },
                            leadingMark = "+",
                        )
                        mirrorState.lastCheckedLabel?.let {
                            Text(
                                text = "Проверено: $it",
                                color = Color(0xFF8F9DB2),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
                mirrorState.mirrors.forEach { mirror ->
                    item(key = "mirror-${mirror.id}") {
                        MirrorRow(
                            mirror = mirror,
                            reduceMotion = reduceMotion,
                            onClick = {
                                when (mirror.status.rowAction()) {
                                    MirrorRowAction.Select -> onMirrorSelected(mirror.id)
                                    MirrorRowAction.ShowDetails -> mirrorDetailsId = mirror.id
                                }
                            },
                        )
                    }
                }

                regularSections.forEach { section ->
                    item(key = "section-${section.id}") {
                        Text(
                            text = section.title,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    section.items.forEach { item ->
                        item(key = item.id) {
                            SettingRow(
                                item = item,
                                onOptionSelected = { optionId ->
                                    onSettingSelected(item.id, optionId)
                                },
                            )
                        }
                    }
                }
                item(key = "application-update-heading") {
                    Text(
                        text = "Обновление приложения",
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                item(key = "application-update-card") {
                    AppUpdateCard(
                        state = appUpdate,
                        onAction = onUpdateAction,
                    )
                }
            }
        }

        if (showManualMirrorDialog) {
            ManualMirrorDialog(
                onDismiss = { showManualMirrorDialog = false },
                onSubmit = onManualMirrorSubmitted,
            )
        }
        mirrorDetailsId
            ?.let { selectedId -> mirrorState.mirrors.firstOrNull { it.id == selectedId } }
            ?.let { mirror ->
                MirrorDetailsDialog(
                    mirror = mirror,
                    isChecking = mirrorState.isChecking,
                    onRetry = { onMirrorRetry(mirror.id) },
                    onDismiss = { mirrorDetailsId = null },
                )
            }
        if (showAccountDialog) {
            AccountLoginDialog(
                initialLogin = accountState.login.orEmpty(),
                onDismiss = { showAccountDialog = false },
                onSubmit = { login, password ->
                    onAccountLogin(login, password)
                    showAccountDialog = false
                },
            )
        }
        if (showRemoveAccountDialog) {
            RemoveAccountDialog(
                onDismiss = { showRemoveAccountDialog = false },
                onConfirm = {
                    onAccountRemove()
                    showRemoveAccountDialog = false
                },
            )
        }
        registrationState?.let { state ->
            RegistrationDialog(
                state = state,
                onDismiss = onRegistrationDismiss,
                onRetry = onRegistrationRetry,
                onAcceptRules = onRegistrationAcceptRules,
                onRefreshCaptcha = onRegistrationRefreshCaptcha,
                onSubmit = onRegistrationSubmit,
            )
        }
    }
}

@Composable
private fun AboutSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.018f else 1f,
        label = "about-settings-focus-scale",
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .testTag("settings-about"),
        shape = RoundedCornerShape(10.dp),
        color = if (focused) Color(0xFF72FFF8) else Color(0xFF294752),
        border = BorderStroke(
            if (focused) 3.dp else 1.dp,
            if (focused) Color.White else Color(0xFF5A7A85),
        ),
        shadowElevation = if (focused) 12.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Text(
                text = "i",
                color = if (focused) Color(0xFF10272D) else MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "О программе",
                    color = if (focused) Color(0xFF10272D) else Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "Версия, информация о проекте и поддержка автора",
                    color = if (focused) Color(0xFF294752) else Color(0xFFB9CBD2),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun AccountCard(
    state: AccountConnectionState,
    pendingSyncCount: Int,
    syncMessage: String?,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onReconnect: () -> Unit,
    onSyncNow: () -> Unit,
    onRemove: () -> Unit,
) {
    val connecting = state.phase == AccountConnectionPhase.CONNECTING
    val statusColor = when (state.phase) {
        AccountConnectionPhase.NO_CREDENTIALS -> Color(0xFF9EABC0)
        AccountConnectionPhase.WAITING_FOR_MIRROR -> Color(0xFFFBBF24)
        AccountConnectionPhase.CONNECTING -> Color(0xFFFBBF24)
        AccountConnectionPhase.CONNECTED -> Color(0xFF4ADE80)
        AccountConnectionPhase.ERROR -> Color(0xFFFF7A7A)
    }
    val statusText = when (state.phase) {
        AccountConnectionPhase.NO_CREDENTIALS -> "Не настроен"
        AccountConnectionPhase.WAITING_FOR_MIRROR -> "Ожидает зеркало"
        AccountConnectionPhase.CONNECTING -> "Подключаемся…"
        AccountConnectionPhase.CONNECTED -> "Подключён"
        AccountConnectionPhase.ERROR -> "Требуется переподключение"
    }
    val identity = state.displayName?.takeIf { it.isNotBlank() }
        ?: state.login?.takeIf { it.isNotBlank() }
    val safePendingCount = pendingSyncCount.coerceAtLeast(0)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF151D2A),
        border = BorderStroke(1.dp, Color(0xFF303B4E)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.width(7.dp).height(28.dp),
                    shape = RoundedCornerShape(50),
                    color = statusColor,
                ) {}
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = identity ?: "Аккаунт сайта не подключён",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.message?.takeIf { it.isNotBlank() }
                            ?: if (state.credentialsSaved) {
                                "Сессия восстанавливается автоматически"
                            } else {
                                "Войдите, чтобы синхронизировать закладки и статусы"
                            },
                        color = Color(0xFF96A4B8),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.72f)),
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (safePendingCount > 0 || !syncMessage.isNullOrBlank()) {
                Text(
                    text = syncMessage?.takeIf { it.isNotBlank() }
                        ?: "Ожидают синхронизации: $safePendingCount",
                    color = if (safePendingCount > 0) Color(0xFFFBBF24) else Color(0xFF96A4B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvActionButton(
                    text = if (state.credentialsSaved) "Сменить данные" else "Войти",
                    onClick = { if (!connecting) onLogin() },
                    primary = !state.credentialsSaved,
                    leadingMark = if (state.credentialsSaved) "✎" else "→",
                )
                TvActionButton(
                    text = "Переподключиться",
                    onClick = { if (!connecting) onReconnect() },
                    leadingMark = "↻",
                    enabled = state.credentialsSaved,
                )
                TvActionButton(
                    text = if (safePendingCount > 0) {
                        "Синхронизировать ($safePendingCount)"
                    } else {
                        "Синхронизировать"
                    },
                    onClick = { if (!connecting) onSyncNow() },
                    leadingMark = "⇄",
                    enabled = state.credentialsSaved,
                )
                TvActionButton(
                    text = "Удалить данные",
                    onClick = onRemove,
                    leadingMark = "×",
                    enabled = state.credentialsSaved,
                )
                if (!state.isAuthenticated) {
                    TvActionButton(
                        text = "Регистрация",
                        onClick = { if (!connecting) onRegister() },
                        leadingMark = "+",
                    )
                }
            }
        }
    }
}

@Composable
private fun AppUpdateCard(
    state: AppUpdateUiModel,
    onAction: () -> Unit,
) {
    val action = state.stableActionPresentation()
    val statusColor = when (state.phase) {
        AppUpdateUiPhase.AVAILABLE,
        AppUpdateUiPhase.READY_TO_INSTALL,
        -> MaterialTheme.colorScheme.primary
        AppUpdateUiPhase.ERROR -> Color(0xFFFF8A80)
        AppUpdateUiPhase.CURRENT -> Color(0xFF74E0A3)
        else -> Color(0xFFBDD0D7)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF253C47),
        border = BorderStroke(1.dp, Color(0xFF597682)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "KinogoATV ${state.currentVersion}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = state.status,
                    color = statusColor,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TvActionButton(
                text = action.label,
                onClick = { if (action.actionable) onAction() },
                modifier = Modifier.testTag("settings-update-action"),
                primary = action.primary,
                leadingMark = action.leadingMark,
            )
        }
    }
}

internal data class AppUpdateActionPresentation(
    val label: String,
    val leadingMark: String,
    val primary: Boolean,
    val actionable: Boolean,
)

/** A running update operation keeps one focusable node instead of removing the focused button. */
internal fun AppUpdateUiModel.stableActionPresentation(): AppUpdateActionPresentation = when (phase) {
    AppUpdateUiPhase.CHECKING -> AppUpdateActionPresentation(
        label = "Проверяем…",
        leadingMark = "…",
        primary = false,
        actionable = false,
    )
    AppUpdateUiPhase.DOWNLOADING -> AppUpdateActionPresentation(
        label = "Загружаем…",
        leadingMark = "…",
        primary = false,
        actionable = false,
    )
    else -> AppUpdateActionPresentation(
        label = actionLabel ?: "Проверить",
        leadingMark = when (phase) {
            AppUpdateUiPhase.AVAILABLE -> "↓"
            AppUpdateUiPhase.READY_TO_INSTALL -> "✓"
            else -> "↻"
        },
        primary = phase == AppUpdateUiPhase.AVAILABLE ||
            phase == AppUpdateUiPhase.READY_TO_INSTALL,
        actionable = actionEnabled,
    )
}

@Composable
private fun MirrorRow(
    mirror: MirrorUiModel,
    reduceMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused && !reduceMotion) 1.014f else 1f,
        label = "mirror-row-scale",
    )
    val statusColor = mirror.status.statusColor()
    val statusLabel = mirror.status.statusLabel()

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription = "${mirror.url}, $statusLabel, ${mirror.statusDetail}"
            },
        shape = RoundedCornerShape(12.dp),
        color = if (focused) Color(0xFF283449) else Color(0xFF151D2A),
        border = BorderStroke(
            width = if (focused) 3.dp else if (mirror.status == MirrorStatusUi.Active) 2.dp else 1.dp,
            color = if (focused) MaterialTheme.colorScheme.primary else statusColor.copy(alpha = 0.72f),
        ),
        shadowElevation = if (focused) 10.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.width(7.dp).height(28.dp),
                shape = RoundedCornerShape(50),
                color = statusColor,
            ) {}
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = mirror.url,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (mirror.isManual) {
                        Text(
                            text = "РУЧНОЙ",
                            color = Color(0xFFB8C3D3),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
                Text(
                    text = mirror.statusDetail,
                    color = Color(0xFF96A4B8),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            mirror.latencyMs?.let {
                Text(text = "$it мс", color = Color(0xFF9EABC0), fontSize = 11.sp)
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = statusColor.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.72f)),
            ) {
                Text(
                    text = statusLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = if (mirror.status == MirrorStatusUi.Available) "Выбрать ›" else "Подробнее ›",
                color = if (focused) MaterialTheme.colorScheme.primary else Color(0xFFB4BFCE),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MirrorDetailsDialog(
    mirror: MirrorUiModel,
    isChecking: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val retryFocus = remember { FocusRequester() }
    val statusColor = mirror.status.statusColor()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        LaunchedEffect(Unit) { retryFocus.requestFocus() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 48.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(680.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF111927),
                border = BorderStroke(2.dp, statusColor.copy(alpha = 0.82f)),
                shadowElevation = 28.dp,
            ) {
                Column(
                    modifier = Modifier.padding(26.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Сведения о зеркале",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = mirror.url,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    MirrorDetailLine(label = "Состояние", value = mirror.status.statusLabel())
                    MirrorDetailLine(label = "Результат", value = mirror.statusDetail)
                    mirror.latencyMs?.let {
                        MirrorDetailLine(label = "Время ответа", value = "$it мс")
                    }
                    mirror.httpStatusCode?.let {
                        MirrorDetailLine(label = "HTTP", value = it.toString())
                    }
                    mirror.checkedAtEpochMs?.let {
                        MirrorDetailLine(
                            label = "Проверено",
                            value = DateFormat.getDateTimeInstance(
                                DateFormat.SHORT,
                                DateFormat.SHORT,
                            ).format(Date(it)),
                        )
                    }
                    mirror.redirectOrigin?.let {
                        MirrorDetailLine(label = "Переадресация", value = it)
                    }
                    mirror.diagnostic
                        ?.takeIf { it.isNotBlank() && it != mirror.statusDetail }
                        ?.let { MirrorDetailLine(label = "Диагностика", value = it) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvActionButton(
                            text = if (isChecking) "Проверяем…" else "Проверить снова",
                            onClick = { if (!isChecking) onRetry() },
                            modifier = Modifier.focusRequester(retryFocus),
                            primary = true,
                            leadingMark = if (isChecking) "…" else "↻",
                        )
                        Spacer(Modifier.width(12.dp))
                        TvActionButton(
                            text = "Закрыть",
                            onClick = onDismiss,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MirrorDetailLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(132.dp),
            color = Color(0xFF8491A5),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = Color(0xFFD7DFEA),
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SettingRow(
    item: SettingUiModel,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    var showOptions by rememberSaveable(item.id) { mutableStateOf(false) }
    var restoreFocusGeneration by remember { mutableIntStateOf(0) }
    val rowFocus = remember(item.id) { FocusRequester() }
    val selectedOption = item.selectedOptionId
    val checked = selectedOption == "true"
    val interactive = item.enabled && item.control != SettingControlUi.VALUE

    fun activate() {
        when (item.control) {
            SettingControlUi.SWITCH -> onOptionSelected((!checked).toString())
            SettingControlUi.DROPDOWN -> showOptions = true
            SettingControlUi.VALUE -> Unit
        }
    }

    fun closeOptions() {
        showOptions = false
        restoreFocusGeneration++
    }

    LaunchedEffect(restoreFocusGeneration) {
        if (restoreFocusGeneration > 0) {
            withFrameNanos { }
            runCatching { rowFocus.requestFocus() }
        }
    }

    Surface(
        onClick = ::activate,
        enabled = interactive,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(rowFocus)
            .testTag("setting-${item.id}")
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription = item.title
                stateDescription = item.value
                if (item.control == SettingControlUi.SWITCH) role = Role.Switch
            },
        shape = RoundedCornerShape(12.dp),
        color = if (focused) MaterialTheme.colorScheme.primary else Color(0xFF253C47),
        border = BorderStroke(
            if (focused) 3.dp else 1.dp,
            if (focused) Color.White else Color(0xFF597682),
        ),
        shadowElevation = if (focused) 12.dp else 0.dp,
    ) {
        val foreground = if (focused) Color(0xFF10272D) else Color.White
        val secondary = if (focused) Color(0xFF24454D) else Color(0xFFB9CCD3)
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.title,
                    color = foreground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = item.description,
                    color = secondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (item.control) {
                SettingControlUi.SWITCH -> Switch(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = item.enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF10272D),
                        checkedTrackColor = if (focused) Color.White else MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = if (focused) Color(0xFF10272D) else Color(0xFFD5E4E8),
                        uncheckedTrackColor = if (focused) Color.White.copy(alpha = 0.48f) else Color(0xFF526E7A),
                        uncheckedBorderColor = if (focused) Color(0xFF10272D) else Color(0xFF79939E),
                    ),
                )
                SettingControlUi.DROPDOWN -> Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.value,
                        color = foreground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = "▾",
                        color = foreground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                SettingControlUi.VALUE -> Text(
                    text = item.value,
                    color = secondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }

    if (showOptions) {
        SettingOptionsDialog(
            item = item,
            onDismiss = ::closeOptions,
            onSelected = { optionId ->
                onOptionSelected(optionId)
                closeOptions()
            },
        )
    }
}

@Composable
private fun SettingOptionsDialog(
    item: SettingUiModel,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    val requesters = remember(item.id, item.options) {
        List(item.options.size) { FocusRequester() }
    }
    val selectedIndex = item.options.indexOfFirst { it.id == item.selectedOptionId }
        .takeIf { it >= 0 }
        ?: 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        LaunchedEffect(item.id, item.selectedOptionId) {
            withFrameNanos { }
            runCatching { requesters.getOrNull(selectedIndex)?.requestFocus() }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 48.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(520.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF213842),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shadowElevation = 28.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 390.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        itemsIndexed(
                            items = item.options,
                            key = { _, option -> option.id },
                        ) { index, option ->
                            TvChoiceChip(
                                text = option.label,
                                selected = option.id == item.selectedOptionId,
                                onClick = { onSelected(option.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(requesters[index])
                                    .testTag("setting-option-${item.id}-${option.id}"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AccountLoginDialog(
    initialLogin: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var login by remember(initialLogin) { mutableStateOf(initialLogin) }
    var password by remember { mutableStateOf("") }
    var loginFocused by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val loginFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun dismiss() {
        keyboardController?.hide()
        onDismiss()
    }

    fun submit() {
        when {
            login.isBlank() -> {
                error = "Введите логин"
                loginFocus.requestFocus()
            }

            password.isEmpty() -> {
                error = "Введите пароль"
                passwordFocus.requestFocus()
            }

            else -> {
                keyboardController?.hide()
                // Password is intentionally not trimmed: submit exactly what the user entered.
                onSubmit(login.trim(), password)
            }
        }
    }

    Dialog(
        onDismissRequest = ::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = ::dismiss)
        LaunchedEffect(Unit) {
            loginFocus.requestFocus()
            keyboardController?.show()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 48.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(650.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF111927),
                border = BorderStroke(2.dp, Color(0xFF3A465A)),
                shadowElevation = 28.dp,
            ) {
                Column(
                    modifier = Modifier.padding(26.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    Text(
                        text = if (initialLogin.isBlank()) "Войти в аккаунт Kinogo" else "Сменить данные аккаунта",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "Приложение сохранит логин и пароль на этом устройстве и сможет автоматически войти снова после завершения сессии.",
                        color = Color(0xFFADB9C9),
                        fontSize = 13.sp,
                    )

                    CredentialField(
                        label = "Логин",
                        value = login,
                        onValueChange = {
                            login = it
                            error = null
                        },
                        focused = loginFocused,
                        onFocusChanged = { loginFocused = it },
                        focusRequester = loginFocus,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                        onImeAction = {
                            passwordFocus.requestFocus()
                            keyboardController?.show()
                        },
                    )
                    CredentialField(
                        label = "Пароль",
                        value = password,
                        onValueChange = {
                            password = it
                            error = null
                        },
                        focused = passwordFocused,
                        onFocusChanged = { passwordFocused = it },
                        focusRequester = passwordFocus,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        password = true,
                        onImeAction = ::submit,
                    )

                    error?.let {
                        Text(
                            text = it,
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = "Поля доступны с пульта: ↑/↓ меняют фокус, OK открывает экранную клавиатуру.",
                        color = Color(0xFF7F8CA0),
                        fontSize = 11.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvActionButton(text = "Отмена", onClick = ::dismiss)
                        Spacer(Modifier.width(12.dp))
                        TvActionButton(
                            text = "Сохранить и войти",
                            onClick = ::submit,
                            primary = true,
                            leadingMark = "→",
                            enabled = login.isNotBlank() && password.isNotEmpty(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    focused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    password: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            color = if (focused) MaterialTheme.colorScheme.primary else Color(0xFFADB9C9),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0B111B),
            border = BorderStroke(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color(0xFF3A465A),
            ),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .semantics { contentDescription = label }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = if (password) {
                    PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                ),
                keyboardActions = when (imeAction) {
                    ImeAction.Next -> KeyboardActions(onNext = { onImeAction() })
                    else -> KeyboardActions(onDone = { onImeAction() })
                },
            )
        }
    }
}

@Composable
private fun RemoveAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        LaunchedEffect(Unit) { cancelFocus.requestFocus() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 48.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(580.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF111927),
                border = BorderStroke(2.dp, Color(0xFF5B3C44)),
                shadowElevation = 28.dp,
            ) {
                Column(
                    modifier = Modifier.padding(26.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Удалить данные аккаунта?",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "С устройства будут удалены логин, пароль, текущая сессия и локальная копия серверных закладок. История просмотра останется на ТВ.",
                        color = Color(0xFFADB9C9),
                        fontSize = 13.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvActionButton(
                            text = "Отмена",
                            onClick = onDismiss,
                            modifier = Modifier.focusRequester(cancelFocus),
                            primary = true,
                        )
                        Spacer(Modifier.width(12.dp))
                        TvActionButton(
                            text = "Удалить данные",
                            onClick = onConfirm,
                            leadingMark = "×",
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ManualMirrorDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("https://") }
    var error by remember { mutableStateOf<String?>(null) }
    var fieldFocused by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun dismiss() {
        keyboardController?.hide()
        onDismiss()
    }

    fun submit() {
        val normalized = normalizeHttpsUrl(input)
        if (normalized == null) {
            error = "Введите корректный HTTPS-адрес без логина, query и fragment"
            return
        }
        keyboardController?.hide()
        onSubmit(normalized)
        onDismiss()
    }

    Dialog(
        onDismissRequest = ::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = ::dismiss)
        LaunchedEffect(Unit) {
            fieldFocus.requestFocus()
            keyboardController?.show()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(620.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF111927),
                border = BorderStroke(2.dp, Color(0xFF3A465A)),
                shadowElevation = 28.dp,
            ) {
                Column(
                    modifier = Modifier.padding(26.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Добавить зеркало вручную",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "Разрешены только HTTPS-адреса. После добавления зеркало попадёт в карантин до проверки.",
                        color = Color(0xFFADB9C9),
                        fontSize = 13.sp,
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0B111B),
                        border = BorderStroke(
                            width = if (fieldFocused) 3.dp else 1.dp,
                            color = if (fieldFocused) MaterialTheme.colorScheme.primary else Color(0xFF3A465A),
                        ),
                    ) {
                        BasicTextField(
                            value = input,
                            onValueChange = {
                                input = it
                                error = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .focusRequester(fieldFocus)
                                .onFocusChanged { fieldFocused = it.isFocused }
                                .semantics { contentDescription = "HTTPS-адрес зеркала" }
                                .padding(horizontal = 16.dp, vertical = 15.dp),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                        )
                    }
                    error?.let {
                        Text(
                            text = it,
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = "Фокус поля автоматически открывает экранную клавиатуру Android TV.",
                        color = Color(0xFF7F8CA0),
                        fontSize = 11.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvActionButton(text = "Отмена", onClick = ::dismiss)
                        Spacer(Modifier.width(12.dp))
                        TvActionButton(
                            text = "Добавить в карантин",
                            onClick = ::submit,
                            primary = true,
                            leadingMark = "+",
                            enabled = input.removePrefix("https://").isNotBlank(),
                        )
                    }
                }
            }
        }
    }
}

private fun normalizeHttpsUrl(value: String): String? {
    val raw = value.trim()
    if (raw.isBlank()) return null
    val candidate = when {
        raw.startsWith("https://", ignoreCase = true) -> "https://${raw.substring(8)}"
        "://" in raw -> return null
        else -> "https://$raw"
    }
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) return null
    return if (uri.path.isNullOrBlank()) "$candidate/" else candidate
}

private fun MirrorStatusUi.statusLabel(): String = when (this) {
    MirrorStatusUi.Active -> "Активно"
    MirrorStatusUi.Available -> "Доступно"
    MirrorStatusUi.Quarantined -> "Карантин"
    MirrorStatusUi.Error -> "Ошибка"
}

private fun MirrorStatusUi.statusColor(): Color = when (this) {
    MirrorStatusUi.Active -> Color(0xFF4ADE80)
    MirrorStatusUi.Available -> Color(0xFF67E8F9)
    MirrorStatusUi.Quarantined -> Color(0xFFFBBF24)
    MirrorStatusUi.Error -> Color(0xFFFF7A7A)
}
