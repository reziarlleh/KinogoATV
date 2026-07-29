package com.kinogo.atv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kinogo.atv.ui.image.PosterUrlPolicy
import com.kinogo.atv.ui.image.SafePosterImageLoader
import com.kinogo.atv.ui.model.PosterUiModel

val RailExpandedWidth = 224.dp
val RailContentOffset = 240.dp
val PosterGridMinimumWidth = 136.dp

@Composable
fun PosterCard(
    item: PosterUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.055f else 1f,
        label = "poster-focus-scale",
    )
    val requesterModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .then(requesterModifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .semantics {
                contentDescription = buildString {
                    append(item.title)
                    append(", ")
                    append(item.subtitle)
                    item.progress?.let { append(", просмотрено ${(it * 100).toInt()} процентов") }
                }
            },
        shape = RoundedCornerShape(12.dp),
        color = if (focused) Color(0xFF263247) else Color(0xFF151C29),
        border = BorderStroke(
            width = if (focused) 3.dp else 1.dp,
            color = if (focused) MaterialTheme.colorScheme.primary else Color(0xFF2A3446),
        ),
        shadowElevation = if (focused) 14.dp else 2.dp,
    ) {
        Column {
            PosterArtwork(
                title = item.title,
                accentArgb = item.accentArgb,
                posterUrl = item.posterUrl,
                badge = item.badge,
                progress = item.progress,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = item.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFFB5C0D3),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
fun PosterArtwork(
    title: String,
    accentArgb: Long,
    modifier: Modifier = Modifier,
    posterUrl: String? = null,
    badge: String? = null,
    progress: Float? = null,
) {
    val accent = Color(accentArgb)
    val context = LocalContext.current
    val safePosterUrl = remember(posterUrl) { PosterUrlPolicy.normalizeOrNull(posterUrl) }
    val imageLoader = remember(context) { SafePosterImageLoader.get(context) }
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.98f), Color(0xFF0D1420)),
                ),
            ),
    ) {
        Text(
            text = title.take(1).uppercase(),
            modifier = Modifier.align(Alignment.Center),
            color = Color.White.copy(alpha = 0.18f),
            fontSize = 62.sp,
            fontWeight = FontWeight.Black,
        )
        safePosterUrl?.let { url ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        badge?.let {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(5.dp),
                color = Color.Black.copy(alpha = 0.72f),
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        progress?.let {
            TvProgressBar(
                progress = it,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
fun TvActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    leadingMark: String? = null,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        label = "action-focus-scale",
    )
    val background = when {
        !enabled -> Color(0xFF171D27)
        focused -> MaterialTheme.colorScheme.primary
        primary -> MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
        else -> Color(0xFF222D40)
    }
    val foreground = when {
        !enabled -> Color(0xFF667185)
        focused || primary -> Color(0xFF10131A)
        else -> Color.White
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(10.dp),
        color = background,
        border = BorderStroke(
            if (focused) 3.dp else 1.dp,
            when {
                !enabled -> Color(0xFF272F3D)
                focused -> Color.White
                else -> Color(0xFF38445A)
            },
        ),
        shadowElevation = if (focused) 12.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingMark?.let {
                Text(text = it, color = foreground, fontWeight = FontWeight.Black)
            }
            Text(
                text = text,
                color = foreground,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun TvChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(50),
        color = when {
            focused -> MaterialTheme.colorScheme.primary
            selected -> Color(0xFF37445A)
            else -> Color(0xFF1A2230)
        },
        border = BorderStroke(
            if (focused) 3.dp else 1.dp,
            when {
                focused -> Color.White
                selected -> MaterialTheme.colorScheme.primary
                else -> Color(0xFF344055)
            },
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (focused) Color(0xFF10131A) else Color.White,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun TvProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.28f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(5.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
fun TvSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        trailing?.let {
            Text(text = it, color = Color(0xFF9EABC0), fontSize = 13.sp)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.size(62.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF202A3B),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = "·", color = MaterialTheme.colorScheme.primary, fontSize = 38.sp)
            }
        }
        Text(text = title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text = description, color = Color(0xFF9EABC0), fontSize = 14.sp)
    }
}
