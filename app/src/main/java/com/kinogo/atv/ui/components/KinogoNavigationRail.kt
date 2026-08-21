package com.kinogo.atv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kinogo.atv.R
import com.kinogo.atv.ui.model.TvDestination

@Composable
fun KinogoNavigationRail(
    selected: TvDestination,
    onSelected: (TvDestination) -> Unit,
    onAboutRequested: () -> Unit = {},
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = false,
) {
    val destinations = TvDestination.entries
    val itemFocusRequesters = remember(destinations.size) {
        List(destinations.size) { FocusRequester() }
    }
    val selectedFocusRequester =
        itemFocusRequesters[preferredRailFocusIndex(selected, destinations)]

    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            repeat(5) {
                withFrameNanos { }
                if (runCatching { selectedFocusRequester.requestFocus() }.getOrDefault(false)) {
                    return@LaunchedEffect
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .width(RailExpandedWidth)
            .fillMaxHeight(),
        shape = RectangleShape,
        color = Color(0xFF17262E),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .focusProperties {
                    onEnter = {
                        selectedFocusRequester.requestFocus()
                        cancelFocusChange()
                    }
                }
                .focusGroup()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            RailBrand(onClick = onAboutRequested)
            Spacer(Modifier.height(5.dp))
            destinations.forEachIndexed { index, destination ->
                NavigationRailItem(
                    destination = destination,
                    selected = selected == destination,
                    focusRequester = itemFocusRequesters[index],
                    onClick = { onSelected(destination) },
                )
            }
            Spacer(Modifier.weight(1f))
            RailStatus()
        }
    }
}

@Composable
private fun RailBrand(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = RAIL_ABOUT_CONTENT_DESCRIPTION },
        shape = RectangleShape,
        color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_kinogo_original),
                contentDescription = null,
                modifier = Modifier.size(38.dp),
            )
            Column {
                Text(
                    text = "KINOGO",
                    color = if (focused) Color(0xFF10272D) else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                Text(
                    text = "for Android TV",
                    color = if (focused) Color(0xFF10272D) else MaterialTheme.colorScheme.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

internal const val RAIL_ABOUT_CONTENT_DESCRIPTION = "О программе"

@Composable
private fun NavigationRailItem(
    destination: TvDestination,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
        else -> Color.Transparent
    }
    val foreground = when {
        focused -> Color(0xFF10272D)
        selected -> Color.White
        else -> Color.White
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .focusRequester(focusRequester)
            .fillMaxWidth()
            .height(44.dp)
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription = destination.title
                this.selected = selected
            },
        shape = RectangleShape,
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(start = 5.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (focused) {
                    Surface(
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                        color = Color.White,
                    ) {}
                }
            }
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = destination.mark,
                    color = foreground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = destination.title,
                color = foreground,
                fontSize = 13.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

internal fun preferredRailFocusIndex(
    selected: TvDestination,
    destinations: List<TvDestination> = TvDestination.entries,
): Int = destinations.indexOf(selected).takeIf { it >= 0 } ?: 0

@Composable
private fun RailStatus() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Surface(
            modifier = Modifier.size(7.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {}
        Text(
            text = "Источник доступен",
            color = Color(0xFFB7C8CF),
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}
