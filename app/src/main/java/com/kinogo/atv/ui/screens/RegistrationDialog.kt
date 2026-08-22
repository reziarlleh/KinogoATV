package com.kinogo.atv.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kinogo.atv.ui.components.TvActionButton
import com.kinogo.atv.ui.model.RegistrationSubmissionUiInput
import com.kinogo.atv.ui.model.RegistrationUiModel
import com.kinogo.atv.ui.model.RegistrationUiPhase
import kotlinx.coroutines.launch

@Composable
fun RegistrationDialog(
    state: RegistrationUiModel,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onAcceptRules: () -> Unit,
    onRefreshCaptcha: () -> Unit,
    onSubmit: (RegistrationSubmissionUiInput) -> Unit,
) {
    var login by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirmation by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }
    var acceptedTerms by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val loginFocus = remember { FocusRequester() }
    val safeActionFocus = remember { FocusRequester() }

    LaunchedEffect(state.captchaBytes?.contentHashCode()) {
        captcha = ""
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        LaunchedEffect(state.phase) {
            val requester = when (state.phase) {
                RegistrationUiPhase.READY -> loginFocus
                RegistrationUiPhase.RULES,
                RegistrationUiPhase.COMPLETED,
                RegistrationUiPhase.UNAVAILABLE,
                RegistrationUiPhase.ERROR,
                -> safeActionFocus
                else -> null
            }
            if (requester != null) {
                repeat(5) {
                    withFrameNanos { }
                    if (runCatching { requester.requestFocus() }.getOrDefault(false)) {
                        return@LaunchedEffect
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = 36.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(840.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF172A33),
                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shadowElevation = 30.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Регистрация аккаунта Kinogo",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "Форма и CAPTCHA загружаются с выбранного проверенного зеркала. CAPTCHA решает пользователь — приложение её не обходит.",
                        color = Color(0xFFBDD0D7),
                        fontSize = 12.sp,
                    )
                    when (state.phase) {
                        RegistrationUiPhase.LOADING,
                        RegistrationUiPhase.SUBMITTING,
                        -> RegistrationProgress(
                            if (state.phase == RegistrationUiPhase.LOADING) {
                                "Загружаем форму…"
                            } else {
                                "Создаём аккаунт…"
                            },
                        )

                        RegistrationUiPhase.RULES -> RegistrationRules(
                            rulesText = state.rulesText.orEmpty(),
                            declineFocus = safeActionFocus,
                            onDecline = onDismiss,
                            onAccept = onAcceptRules,
                        )

                        RegistrationUiPhase.READY -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RegistrationField(
                                    label = "Логин",
                                    value = login,
                                    onValueChange = { login = it; localError = null },
                                    modifier = Modifier.weight(1f),
                                    focusRequester = loginFocus,
                                )
                                RegistrationField(
                                    label = "E-mail",
                                    value = email,
                                    onValueChange = { email = it; localError = null },
                                    keyboardType = KeyboardType.Email,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RegistrationField(
                                    label = "Пароль",
                                    value = password,
                                    onValueChange = { password = it; localError = null },
                                    password = true,
                                    modifier = Modifier.weight(1f),
                                )
                                RegistrationField(
                                    label = "Повторите пароль",
                                    value = passwordConfirmation,
                                    onValueChange = { passwordConfirmation = it; localError = null },
                                    password = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (state.requiresCaptcha) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CaptchaImage(state.captchaBytes)
                                    RegistrationField(
                                        label = "Код с изображения",
                                        value = captcha,
                                        onValueChange = { captcha = it; localError = null },
                                        modifier = Modifier.weight(1f),
                                    )
                                    TvActionButton(
                                        text = "Обновить CAPTCHA",
                                        onClick = {
                                            captcha = ""
                                            onRefreshCaptcha()
                                        },
                                        leadingMark = "↻",
                                    )
                                }
                            }
                            if (state.requiresConsent) {
                                ConsentRow(
                                    checked = acceptedTerms,
                                    label = state.consentLabel,
                                    onToggle = { acceptedTerms = !acceptedTerms; localError = null },
                                )
                            }
                            (localError ?: state.message)?.takeIf(String::isNotBlank)?.let { message ->
                                Text(
                                    text = message,
                                    color = Color(0xFFFFB1A9),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TvActionButton(text = "Отмена", onClick = onDismiss)
                                Box(Modifier.width(10.dp))
                                TvActionButton(
                                    text = "Зарегистрироваться",
                                    primary = true,
                                    leadingMark = "+",
                                    onClick = {
                                        localError = registrationInputError(
                                            login = login,
                                            email = email,
                                            password = password,
                                            confirmation = passwordConfirmation,
                                            captcha = captcha,
                                            requiresCaptcha = state.requiresCaptcha,
                                            acceptedTerms = acceptedTerms,
                                            requiresConsent = state.requiresConsent,
                                        )
                                        if (localError == null) {
                                            onSubmit(
                                                RegistrationSubmissionUiInput(
                                                    login = login.trim(),
                                                    email = email.trim(),
                                                    password = password,
                                                    passwordConfirmation = passwordConfirmation,
                                                    captchaText = captcha.trim(),
                                                    acceptedTerms = acceptedTerms,
                                                ),
                                            )
                                        }
                                    },
                                )
                            }
                        }

                        RegistrationUiPhase.COMPLETED -> RegistrationResult(
                            message = state.message ?: "Аккаунт создан, вход выполнен.",
                            action = "Готово",
                            onAction = onDismiss,
                            actionFocus = safeActionFocus,
                        )
                        RegistrationUiPhase.UNAVAILABLE,
                        RegistrationUiPhase.ERROR,
                        -> RegistrationResult(
                            message = state.message ?: "Не удалось загрузить регистрацию",
                            action = "Повторить",
                            onAction = onRetry,
                            onDismiss = onDismiss,
                            dismissFocus = safeActionFocus,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegistrationRules(
    rulesText: String,
    declineFocus: FocusRequester,
    onDecline: () -> Unit,
    onAccept: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Сайт требует принять правила до открытия регистрационной формы.",
            color = Color.White,
            fontSize = 15.sp,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .testTag("registration_rules_scroll")
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    val target = when (event.key) {
                        Key.DirectionDown -> {
                            if (scrollState.value >= scrollState.maxValue) {
                                // Focus traversal through a vertically scrollable child is not
                                // deterministic on all TV builds. Return to the safe action
                                // explicitly so the rules panel can never trap the D-pad.
                                declineFocus.requestFocus()
                                return@onPreviewKeyEvent true
                            }
                            scrollState.value + 150
                        }
                        Key.DirectionUp -> {
                            if (scrollState.value <= 0) {
                                return@onPreviewKeyEvent false
                            }
                            scrollState.value - 150
                        }
                        else -> return@onPreviewKeyEvent false
                    }
                    scope.launch { scrollState.animateScrollTo(target.coerceIn(0, scrollState.maxValue)) }
                    true
                }
                .focusable(),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0E1A20),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF617E89)),
        ) {
            Text(
                text = rulesText.ifBlank { "Правила сайта не содержат текстового описания." },
                modifier = Modifier.verticalScroll(scrollState).padding(12.dp),
                color = Color(0xFFD7E4E9),
                fontSize = 12.sp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvActionButton(
                text = "Не принимаю",
                onClick = onDecline,
                modifier = Modifier.focusRequester(declineFocus),
            )
            TvActionButton(
                text = "Принимаю и продолжить",
                onClick = onAccept,
                primary = true,
            )
        }
    }
}

@Composable
private fun RegistrationProgress(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun RegistrationResult(
    message: String,
    action: String,
    onAction: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    actionFocus: FocusRequester? = null,
    dismissFocus: FocusRequester? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = message, color = Color.White, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvActionButton(
                text = action,
                onClick = onAction,
                primary = true,
                modifier = actionFocus?.let { Modifier.focusRequester(it) } ?: Modifier,
            )
            onDismiss?.let {
                TvActionButton(
                    text = "Закрыть",
                    onClick = it,
                    modifier = dismissFocus?.let { focus -> Modifier.focusRequester(focus) } ?: Modifier,
                )
            }
        }
    }
}

@Composable
private fun RegistrationField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, color = Color(0xFFBDD0D7), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= 254) onValueChange(it) },
            modifier = (focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .fillMaxWidth()
                .height(46.dp)
                .background(Color(0xFF0E1A20), RoundedCornerShape(9.dp))
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) Color.White else Color(0xFF617E89),
                    shape = RoundedCornerShape(9.dp),
                )
                .onFocusChanged { focused = it.isFocused }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { keyboard?.hide() }),
        )
    }
}

@Composable
private fun CaptchaImage(bytes: ByteArray?) {
    val bitmap = remember(bytes?.contentHashCode()) {
        bytes?.let(::decodeBoundedCaptcha)
    }
    Surface(
        modifier = Modifier.width(210.dp).height(64.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "CAPTCHA",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text("CAPTCHA недоступна", color = Color(0xFF5B2020), fontSize = 11.sp)
            }
        }
    }
}

private fun decodeBoundedCaptcha(bytes: ByteArray): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width !in 1..MAX_CAPTCHA_DIMENSION || height !in 1..MAX_CAPTCHA_DIMENSION) return null
    if (width.toLong() * height.toLong() > MAX_CAPTCHA_PIXELS) return null
    var sampleSize = 1
    while (width / sampleSize > CAPTCHA_DECODE_WIDTH || height / sampleSize > CAPTCHA_DECODE_HEIGHT) {
        sampleSize *= 2
    }
    return runCatching {
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            },
        )
    }.getOrNull()
}

private const val MAX_CAPTCHA_DIMENSION = 4_096
private const val MAX_CAPTCHA_PIXELS = 8_000_000L
private const val CAPTCHA_DECODE_WIDTH = 840
private const val CAPTCHA_DECODE_HEIGHT = 256

@Composable
private fun ConsentRow(
    checked: Boolean,
    label: String,
    onToggle: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(10.dp),
        color = if (focused) MaterialTheme.colorScheme.primary else Color(0xFF253C47),
        border = androidx.compose.foundation.BorderStroke(
            if (focused) 3.dp else 1.dp,
            if (focused) Color.White else Color(0xFF617E89),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = Color.White),
            )
            Text(
                text = label,
                color = if (focused) Color(0xFF10272D) else Color.White,
                fontSize = 12.sp,
                maxLines = 2,
            )
        }
    }
}

private fun registrationInputError(
    login: String,
    email: String,
    password: String,
    confirmation: String,
    captcha: String,
    requiresCaptcha: Boolean,
    acceptedTerms: Boolean,
    requiresConsent: Boolean,
): String? = when {
    login.trim().length !in 3..40 -> "Логин должен содержать от 3 до 40 символов"
    email.trim().length !in 3..254 || '@' !in email -> "Введите корректный e-mail"
    password.length !in 6..128 -> "Пароль должен содержать от 6 до 128 символов"
    password != confirmation -> "Пароли не совпадают"
    requiresCaptcha && captcha.isBlank() -> "Введите код с изображения"
    requiresConsent && !acceptedTerms -> "Подтвердите согласие с правилами сайта"
    else -> null
}
