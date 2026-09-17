package kr.me.nulld.iduohome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal data class WidgetResizeAnchor(val placement: WidgetPlacement, val bounds: Rect)

internal fun widgetResizeBounds(anchor: WidgetResizeAnchor, placement: WidgetPlacement,
    grid: WidgetGridSizing, density: Float): Rect {
    fun rowTop(row: Int) = minOf(row, 2) * grid.topRowHeightDp + maxOf(0, row - 2) * grid.appRowHeightDp
    val left = anchor.bounds.left + (placement.column - anchor.placement.column) * grid.cellWidthDp * density
    val top = anchor.bounds.top + (rowTop(placement.row) - rowTop(anchor.placement.row)) * density
    val size = grid.contentSize(placement.column, placement.row, placement.spanX, placement.spanY)
    return Rect(left, top, left + size.widthDp * density, top + size.heightDp * density)
}

@Composable
internal fun WidgetResizeFrame(
    bounds: Rect, placement: WidgetPlacement, grid: WidgetGridSizing, limits: WidgetSpanConstraints?, valid: Boolean,
    onStart: () -> Unit, onPreview: (WidgetPlacement) -> Unit, onCommit: (WidgetPlacement) -> Unit, onCancel: () -> Unit,
) {
    val density = LocalDensity.current
    val color = if (valid) Color.White else Color(0xFFFF6B6B)
    val latestPlacement by rememberUpdatedState(placement)
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    val cancel by rememberUpdatedState(onCancel)
    val start by rememberUpdatedState(onStart)
    val canResize = placement.row in 0 until GRID_ROWS && (placement.id < 0 || limits != null)
    val horizontal = canResize && limits?.canResizeHorizontally != false
    val vertical = canResize && limits?.canResizeVertically != false
    val description = stringResource(R.string.home_drag_resize)
    val grow = stringResource(R.string.home_grow)
    val shrink = stringResource(R.string.home_shrink)
    Box(Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
        .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
        .border(2.dp, color, RoundedCornerShape(24.dp)).testTag("widget-resize-preview-${placement.slot}")) {
        val handles = buildList {
            if (horizontal) { add(-1 to 0); add(1 to 0) }
            if (vertical) { add(0 to -1); add(0 to 1) }
            if (horizontal && vertical) add(1 to 1)
        }
        handles.forEach { (x, y) ->
            val alignment = when { x < 0 -> Alignment.CenterStart; y < 0 -> Alignment.TopCenter
                x == 0 -> Alignment.BottomCenter; y == 0 -> Alignment.CenterEnd; else -> Alignment.BottomEnd }
            val tag = if (x == 1 && y == 1) "widget-resize-handle-${placement.slot}" else "widget-resize-handle-${placement.slot}-$x-$y"
            Box(Modifier.align(alignment).offset((x * 20).dp, (y * 20).dp).size(48.dp).testTag(tag)
                .semantics {
                    contentDescription = description
                    customActions = listOf(1 to grow, -1 to shrink).map { (direction, label) ->
                        CustomAccessibilityAction(label) {
                            commit(snappedWidgetResize(latestPlacement, x, y, x * direction * grid.cellWidthDp,
                                y * direction * grid.maximumCellHeightDp, grid, limits)); true
                        }
                    }
                }
                .pointerInput(placement.slot, x, y, grid, limits) {
                    var delta = Offset.Zero
                    var original = latestPlacement
                    var candidate = original
                    detectDragGestures(onDragStart = {
                        original = latestPlacement; candidate = original; delta = Offset.Zero; start()
                    }, onDrag = { change, amount ->
                        change.consume(); delta += amount
                        candidate = snappedWidgetResize(original, x, y, delta.x / density.density,
                            delta.y / density.density, grid, limits)
                        preview(candidate)
                    }, onDragEnd = { commit(candidate) }, onDragCancel = { cancel() })
                }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(if (x != 0 && y != 0) 16.dp else 12.dp).background(color, CircleShape)
                    .border(1.dp, Color.Black.copy(alpha = .25f), CircleShape))
            }
        }
        if (!valid) Text(stringResource(R.string.home_no_room), color = Color.White,
            modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .75f), RoundedCornerShape(8.dp)).padding(8.dp))
    }
}
