package com.kinogo.atv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kinogo.atv.ui.components.TvActionButton

@Composable
fun PlaybackPreparationScreen(
    title: String,
    errorMessage: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val primaryFocus = remember(errorMessage) { FocusRequester() }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) runCatching { primaryFocus.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF05080E))
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .width(620.dp)
                .background(Color(0xE6152130))
                .padding(horizontal = 38.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (errorMessage == null) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Подготавливаем просмотр",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = title,
                    color = Color(0xFFC5CFDD),
                    fontSize = 17.sp,
                )
                Text(
                    text = "Получаем свежие источники, переводы, серии и качество…",
                    color = Color(0xFF93A3B7),
                    fontSize = 14.sp,
                )
            } else {
                Text(
                    text = "Не удалось подготовить просмотр",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = errorMessage,
                    color = Color(0xFFFFD18A),
                    fontSize = 16.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionButton(
                        text = "Повторить",
                        onClick = onRetry,
                        modifier = Modifier.focusRequester(primaryFocus),
                        primary = true,
                        leadingMark = "↻",
                    )
                    TvActionButton(
                        text = "Назад",
                        onClick = onBack,
                        leadingMark = "‹",
                    )
                }
            }
        }
    }
}
