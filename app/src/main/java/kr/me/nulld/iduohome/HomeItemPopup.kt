package kr.me.nulld.iduohome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal fun homePopupOffset(anchor: Rect, popup: IntSize, safe: Rect, gap: Float): IntOffset {
    val x = (anchor.center.x - popup.width / 2f).coerceIn(safe.left, maxOf(safe.left, safe.right - popup.width))
    val below = anchor.bottom + gap
    val above = anchor.top - gap - popup.height
    val y = when {
        below + popup.height <= safe.bottom -> below
        above >= safe.top -> above
        else -> below.coerceIn(safe.top, maxOf(safe.top, safe.bottom - popup.height))
    }
    return IntOffset(x.roundToInt(), y.roundToInt())
}

/** Drawn in Home's own window so resizing remains directly reachable beside the menu. */
@Composable
internal fun HomeItemPopup(
    anchor: Rect, title: String, tag: String, placed: Boolean,
    onSelect: (() -> Unit)?, onRemoveOrAdd: () -> Unit, onSettings: (() -> Unit)?,
    onWidgets: (() -> Unit)? = null, onFolder: (() -> Unit)? = null,
    onUninstall: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        var popupSize by remember { mutableStateOf(IntSize.Zero) }
        // The parent already applies safeDrawing insets.
        val safe = with(density) { Rect(8.dp.toPx(), 8.dp.toPx(), maxWidth.toPx() - 8.dp.toPx(), maxHeight.toPx() - 8.dp.toPx()) }
        val position = homePopupOffset(anchor, popupSize, safe, with(density) { 12.dp.toPx() })
        Surface(Modifier.offset { position }.width(minOf(320.dp, maxWidth - 16.dp))
            .onSizeChanged { popupSize = it }.testTag(tag).semantics { paneTitle = title },
            shape = RoundedCornerShape(22.dp), tonalElevation = 6.dp, shadowElevation = 10.dp) {
            Column(Modifier.padding(8.dp)) {
                Text(title, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth()) {
                    if (onUninstall != null) {
                        PopupAction(Icons.Rounded.AddHome, stringResource(R.string.home_add),
                            onRemoveOrAdd, Modifier.weight(1f).testTag("library-home-add"))
                        PopupAction(Icons.Rounded.DeleteOutline, stringResource(R.string.library_uninstall),
                            onUninstall, Modifier.weight(1f).testTag("library-uninstall"))
                    } else {
                        PopupAction(Icons.Rounded.CheckCircleOutline, stringResource(R.string.home_select),
                            onSelect, Modifier.weight(1f).testTag("home-item-select"))
                        PopupAction(if (placed) Icons.Rounded.DeleteOutline else Icons.Rounded.AddHome,
                            stringResource(if (placed) R.string.home_remove else R.string.home_add),
                            onRemoveOrAdd, Modifier.weight(1.35f).testTag("home-item-remove"))
                        PopupAction(Icons.Rounded.Settings, stringResource(R.string.home_settings),
                            onSettings, Modifier.weight(1f).testTag("home-item-settings"))
                    }
                }
                if (onUninstall == null && (onWidgets != null || onFolder != null)) {
                    HorizontalDivider(Modifier.padding(horizontal = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        onWidgets?.let { TextButton(onClick = it) { Text(stringResource(R.string.home_widgets)) } }
                        onFolder?.let { TextButton(onClick = it) { Text(stringResource(R.string.home_create_folder)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupAction(icon: ImageVector, label: String, onClick: (() -> Unit)?, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (onClick == null) .38f else 1f)
    Surface(onClick = { onClick?.invoke() }, enabled = onClick != null, modifier = modifier.heightIn(min = 76.dp),
        color = Color.Transparent, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = color)
            Spacer(Modifier.height(6.dp))
            Text(label, textAlign = TextAlign.Center, color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun HomeSelectionOverlay(
    regions: List<DragRegion>, selected: Set<DropTarget>, origin: Offset,
    labelFor: (DragRegion) -> String,
    onToggle: (DropTarget) -> Unit, onRemove: () -> Unit, onClose: () -> Unit,
) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = onClose)
        .testTag("home-selection")) {
        regions.forEach { region ->
            val checked = region.target in selected
            val bounds = region.bounds.translate(-origin)
            val label = stringResource(R.string.home_select_item) + ": " + labelFor(region)
            Box(Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                .toggleable(checked, role = Role.Checkbox, onValueChange = { onToggle(region.target) })
                .semantics { contentDescription = label }
                .border(if (checked) 2.dp else 1.dp, Color.White.copy(alpha = if (checked) 1f else .5f), RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = if (checked) .15f else 0f), RoundedCornerShape(18.dp))) {
                Icon(if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp), tint = Color.White)
            }
        }
        Surface(Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            shape = RoundedCornerShape(20.dp), shadowElevation = 8.dp) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pluralStringResource(R.plurals.home_selected_count, selected.size, selected.size))
                TextButton(onClick = onRemove, enabled = selected.isNotEmpty(), modifier = Modifier.testTag("home-selection-remove")) {
                    Text(stringResource(R.string.home_remove))
                }
                TextButton(onClick = onClose) { Text(stringResource(R.string.home_done)) }
            }
        }
    }
}
