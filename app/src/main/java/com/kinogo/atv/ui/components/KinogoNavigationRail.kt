package com.kinogo.atv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kinogo.atv.ui.model.TvDestination

@Composable
fun KinogoNavigationRail(
    selected: TvDestination,
    onSelected: (TvDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinations = TvDestination.entries
    val itemFocusRequesters = remember(destinations.size) {
        List(destinations.size) { FocusRequester() }
    }
    val selectedFocusRequester =
        itemFocusRequesters[preferredRailFocusIndex(selected, destinations)]

    Surface(
        modifier = modifier
            .width(RailExpandedWidth)
            .fillMaxHeight(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF20D131F),
        border = BorderStroke(1.dp, Color(0xFF2B3547)),
        shadowElevation = 8.dp,
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
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            RailBrand()
            Spacer(Modifier.height(8.dp))
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
private fun RailBrand() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "K",
                    color = Color(0xFF10131A),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Column {
            Text(
                text = "KinoGo TV",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(text = "Каталог и плеер", color = Color(0xFF8F9DB2), fontSize = 11.sp)
        }
    }
}

@Composable
private fun NavigationRailItem(
    destination: TvDestination,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        label = "rail-item-scale",
    )
    val background = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val foreground = when {
        focused -> Color(0xFF10131A)
        selected -> MaterialTheme.colorScheme.primary
        else -> Color.White
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .focusRequester(focusRequester)
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription = destination.title
                this.selected = selected
            },
        shape = RoundedCornerShape(11.dp),
        color = background,
        border = BorderStroke(
            width = if (focused) 3.dp else if (selected) 2.dp else 0.dp,
            color = if (focused) Color.White else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = destination.mark,
                    color = foreground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = destination.title,
                color = foreground,
                fontSize = 15.sp,
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
            .height(38.dp)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Surface(
            modifier = Modifier.size(9.dp),
            shape = RoundedCornerShape(50),
            color = Color(0xFF4ADE80),
        ) {}
        Text(
            text = "Источник доступен",
            color = Color(0xFFAAB5C6),
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}
