package br.com.wdc.shopping.view.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    width: Dp = 4.dp,
    color: Color = Color.Gray.copy(alpha = 0.5f),
): Modifier {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            visible = true
        } else {
            delay(1000)
            visible = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300)
    )

    return this.drawWithContent {
        drawContent()
        val maxScroll = scrollState.maxValue.toFloat()
        if (maxScroll > 0f && alpha > 0f) {
            val viewportHeight = size.height
            val contentHeight = viewportHeight + maxScroll
            val scrollbarHeight = (viewportHeight / contentHeight * viewportHeight).coerceAtLeast(24.dp.toPx())
            val scrollRange = viewportHeight - scrollbarHeight
            val scrollbarOffset = (scrollState.value.toFloat() / maxScroll) * scrollRange

            drawRoundRect(
                color = color.copy(alpha = color.alpha * alpha),
                topLeft = Offset(size.width - width.toPx() - 2.dp.toPx(), scrollbarOffset),
                size = Size(width.toPx(), scrollbarHeight),
                cornerRadius = CornerRadius(width.toPx() / 2f)
            )
        }
    }
}

@Composable
fun Modifier.verticalScrollbar(
    state: LazyGridState,
    width: Dp = 4.dp,
    color: Color = Color.Gray.copy(alpha = 0.5f),
): Modifier {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) {
            visible = true
        } else {
            delay(1000)
            visible = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300)
    )

    return this.drawWithContent {
        drawContent()
        val layoutInfo = state.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        val visibleItems = layoutInfo.visibleItemsInfo

        if (totalItems > 0 && visibleItems.isNotEmpty() && alpha > 0f) {
            val viewportHeight = size.height
            val firstVisible = visibleItems.first()
            val lastVisible = visibleItems.last()

            val estimatedContentHeight = (lastVisible.offset.y + lastVisible.size.height - firstVisible.offset.y).toFloat() /
                visibleItems.size * totalItems
            if (estimatedContentHeight > viewportHeight) {
                val scrollbarHeight = (viewportHeight / estimatedContentHeight * viewportHeight).coerceAtLeast(24.dp.toPx())
                val scrollFraction = firstVisible.index.toFloat() / totalItems
                val scrollRange = viewportHeight - scrollbarHeight
                val scrollbarOffset = scrollFraction * scrollRange

                drawRoundRect(
                    color = color.copy(alpha = color.alpha * alpha),
                    topLeft = Offset(size.width - width.toPx() - 2.dp.toPx(), scrollbarOffset),
                    size = Size(width.toPx(), scrollbarHeight),
                    cornerRadius = CornerRadius(width.toPx() / 2f)
                )
            }
        }
    }
}
