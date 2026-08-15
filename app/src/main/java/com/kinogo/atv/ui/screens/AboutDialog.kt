package com.kinogo.atv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kinogo.atv.R
import com.kinogo.atv.ui.components.TvActionButton

@Composable
fun AboutDialog(
    versionName: String,
    onDonate: () -> Unit,
    onRepository: () -> Unit,
    onDismiss: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        LaunchedEffect(Unit) {
            repeat(5) {
                withFrameNanos { }
                if (runCatching { closeFocus.requestFocus() }.getOrDefault(false)) {
                    return@LaunchedEffect
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = 48.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(780.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF172A33),
                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shadowElevation = 30.dp,
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.donate_qr),
                            contentDescription = "QR-код поддержки автора",
                            modifier = Modifier.size(200.dp).padding(4.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "KinogoATV",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "Версия $versionName · Android TV 9+",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Неофициальное нативное приложение-каталог и Media3-плеер для телевизора и обычного D-pad-пульта.",
                            color = Color(0xFFD4E2E7),
                            fontSize = 13.sp,
                        )
                        Text(
                            text = "Приложение не связано с администрацией Kinogo и не хранит видеоматериалы. Если проект полезен, автора можно поддержать по QR-коду или ссылке Donate.Stream.",
                            color = Color(0xFFADC2CA),
                            fontSize = 12.sp,
                        )
                        Text(
                            text = "donate.stream/donate_6a60559cd9e35",
                            color = Color(0xFF8FEAE6),
                            fontSize = 11.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            TvActionButton(
                                text = "Закрыть",
                                onClick = onDismiss,
                                modifier = Modifier.focusRequester(closeFocus),
                            )
                            TvActionButton(
                                text = "Поддержать автора",
                                onClick = onDonate,
                                primary = true,
                                leadingMark = "♥",
                            )
                            TvActionButton(
                                text = "GitHub",
                                onClick = onRepository,
                                leadingMark = "↗",
                            )
                        }
                    }
                }
            }
        }
    }
}
