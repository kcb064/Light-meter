package com.kevinboutwell.lightmeter.ui.meter

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * A horizontal drag ruler over a third-stop table: fixed label on the left, a
 * swipeable value strip on the right with the selected value snapped to
 * center. Drag or fling to change value (haptic detents per third stop,
 * stronger at full stops), or tap a neighbor to jump to it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DragRuler(
    label: String,
    values: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isFullStop: (Int) -> Boolean = { it % 3 == 0 },
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // Keyed on the table so an APERTURE <-> SHUTTER swap starts fresh at the
    // right position instead of keeping the old scroll offset.
    val listState = remember(values) { LazyListState(firstVisibleItemIndex = selectedIndex) }
    // The long-lived collectors below must see the latest selection/callback,
    // not the ones captured when the effect first launched.
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)

    // Item whose center is nearest the strip's center — the "live" value while
    // dragging, and the committed value once the strip settles.
    val centeredIndex by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
                ?: selectedIndex
        }
    }

    LaunchedEffect(listState) {
        launch {
            // Detent ticks while dragging; firmer at full stops.
            snapshotFlow { centeredIndex }.drop(1).collect { index ->
                if (listState.isScrollInProgress) {
                    haptics.performHapticFeedback(
                        if (isFullStop(index)) HapticFeedbackType.LongPress
                        else HapticFeedbackType.TextHandleMove,
                    )
                }
            }
        }
        launch {
            // Commit once the snap fling settles.
            snapshotFlow { listState.isScrollInProgress }.drop(1).collect { scrolling ->
                if (!scrolling && centeredIndex != currentSelected) currentOnSelect(centeredIndex)
            }
        }
    }
    // Follow external changes (restored dial state, clamps).
    LaunchedEffect(selectedIndex, listState) {
        if (!listState.isScrollInProgress && centeredIndex != selectedIndex) {
            listState.scrollToItem(selectedIndex)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier
                .padding(start = 16.dp)
                .width(78.dp),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            // With uniform items and this padding, scrollToItem(i) centers
            // item i exactly, matching the snap fling's center position.
            val sidePadding = (maxWidth - ItemWidth) / 2
            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState),
                contentPadding = PaddingValues(horizontal = sidePadding),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .fadedEdges(),
            ) {
                itemsIndexed(values) { index, text ->
                    val distance = abs(index - centeredIndex)
                    Box(
                        modifier = Modifier
                            .width(ItemWidth)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { scope.launch { listState.animateScrollToItem(index) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text,
                            style = when (distance) {
                                0 -> MaterialTheme.typography.headlineMedium
                                else -> NeighborStyle.copy(fontSize = if (distance == 1) 15.sp else 14.sp)
                            },
                            color = when (distance) {
                                0 -> MaterialTheme.colorScheme.primary
                                1 -> NearGray
                                else -> FarGray
                            },
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/** Horizontal alpha mask — the fade at both ends is the swipe affordance. */
private fun Modifier.fadedEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.18f to Color.Black,
                0.82f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

private val ItemWidth = 72.dp
private val NearGray = Color(0xFF84817A)
private val FarGray = Color(0xFF4A4A50)
private val NeighborStyle = TextStyle(fontFeatureSettings = "tnum")
