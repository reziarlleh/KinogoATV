package com.kinogo.atv.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kinogo.atv.ui.components.PosterArtwork
import com.kinogo.atv.ui.components.TvProgressBar
import com.kinogo.atv.ui.components.TvSectionTitle
import com.kinogo.atv.ui.model.HistoryUiModel

@Composable
fun HistoryScreen(
    history: List<HistoryUiModel>,
    onResume: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(history.isNotEmpty()) {
        if (history.isNotEmpty()) initialFocus.requestFocus()
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvSectionTitle(text = "История", trailing = "${history.size} записей")
        Text(
            text = "Позиция, сезон и серия сохраняются автоматически",
            color = Color(0xFF8F9DB2),
            fontSize = 13.sp,
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 4.dp, top = 7.dp, end = 18.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(items = history, key = { _, item -> item.id }) { index, item ->
                HistoryRow(
                    item = item,
                    onClick = { onResume(item.poster.id) },
                    modifier = if (index == 0) Modifier.focusRequester(initialFocus) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: HistoryUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.018f else 1f,
        label = "history-row-scale",
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(126.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription = "${item.poster.title}, ${item.episodeLabel}, ${item.positionLabel}, продолжить"
            },
        shape = RoundedCornerShape(14.dp),
        color = if (focused) Color(0xFF273247) else Color(0xFF151D2A),
        border = BorderStroke(
            if (focused) 3.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.primary else Color(0xFF303B4E),
        ),
        shadowElevation = if (focused) 12.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterArtwork(
                title = item.poster.title,
                accentArgb = item.poster.accentArgb,
                posterUrl = item.poster.posterUrl,
                badge = item.poster.badge,
                modifier = Modifier.width(68.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = item.poster.title,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = item.episodeLabel,
                    color = Color(0xFFD0D8E6),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                TvProgressBar(progress = item.progress)
                Text(text = item.positionLabel, color = Color(0xFF96A4B8), fontSize = 12.sp)
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = item.lastWatchedLabel, color = Color(0xFF8F9DB2), fontSize = 12.sp)
                Text(
                    text = "▶ Продолжить",
                    color = if (focused) MaterialTheme.colorScheme.primary else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
