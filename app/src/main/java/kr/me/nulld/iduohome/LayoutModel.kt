package kr.me.nulld.iduohome

/** Jake's reference measures 76px dock artwork against 106px home artwork. */
fun dockIconSize(homeIconSize: Float) = homeIconSize * (76f / 106f)

data class LayoutPreset(
    val iconSize: Float = 66f,
    val rowGap: Float = 8f,
    val dockWidth: Float = 68f,
    val dockPosition: Float = 0.56f,
    val dockAlignToGrid: Boolean = true,
) {
    fun sanitized() = copy(
        iconSize = iconSize.coerceIn(40f, 68f),
        rowGap = rowGap.coerceIn(0f, 28f),
        dockWidth = dockWidth.coerceIn(56f, 84f),
        dockPosition = dockPosition.coerceIn(0.25f, 0.75f),
    )
}

data class HomeGeometry(
    val expanded: Boolean,
    val homeWidth: Float,
    val gridWidth: Float,
    val iconSize: Float,
    val rowHeight: Float,
    val widgetHeight: Float,
    val contentTop: Float,
    val dockTop: Float,
    val dockHeight: Float,
    val dockRowHeight: Float,
)

/** Advance old defaults without changing individually tuned values. */
fun upgradePreset(preset: LayoutPreset, schema: Int, expanded: Boolean): LayoutPreset = when {
    schema < 2 -> preset.copy(
        iconSize = if (preset.iconSize == if (expanded) 58f else 54f) 66f else preset.iconSize,
        rowGap = if (preset.rowGap == 12f) 8f else preset.rowGap,
        dockWidth = if (preset.dockWidth == 64f) 68f else preset.dockWidth,
    )
    schema == 2 && preset.iconSize == 60f -> preset.copy(iconSize = 66f)
    else -> preset
}

fun homeGeometry(width: Float, height: Float, preset: LayoutPreset, labels: Boolean, statusHeight: Float = 0f,
    labelHeight: Float = 20f, inLibrary: Boolean = false, homeBottomSpace: Float = 44f,
    dockSlots: Int = MIN_DOCK_SLOTS, referenceGridWidth: Float? = null): HomeGeometry {
    val p = preset.sanitized()
    val dockCount = dockSlots.coerceIn(MIN_DOCK_SLOTS, MAX_DOCK_SLOTS)
    val expanded = width >= 650f
    val desiredGrid = if (expanded) referenceGridWidth?.takeIf { it.isFinite() && it >= 192f }
        ?: (width / 2f - p.dockWidth - 44f) else width - p.dockWidth - 44f
    // Use the measured cover grid on both inner panes. Limit only when the window
    // cannot fit that grid beside the fixed dock (for example, in multi-window).
    val gridWidth = if (expanded) desiredGrid.coerceIn(192f, (width / 2f - p.dockWidth - 28f).coerceAtLeast(192f))
        else desiredGrid.coerceAtLeast(192f)
    val homeWidth = if (expanded) gridWidth + p.dockWidth + 44f else width
    val widget = minOf(176f, gridWidth / 2f - 5f).coerceAtLeast(88f)
    val labelSpace = if (labels) maxOf(20f, labelHeight) else 20f
    val iconRows = GRID_ROWS - 2
    val fittingRow = (height - widget - 18f - homeBottomSpace - 32f) / iconRows
    val fittingIcon = (fittingRow - 20f - p.rowGap).coerceAtLeast(32f)
    // The shorter inner display scrolls the grid instead of tightening its rows.
    val icon = minOf(p.iconSize, (gridWidth / 4f - 10f).coerceAtLeast(32f), if (expanded) p.iconSize else fittingIcon)
    // Keep the same icon rhythm when labels are hidden; allow larger system text to fit.
    val row = maxOf(48f, icon + labelSpace) + p.rowGap
    val contentTop = ((height - widget - 18f - iconRows * row - homeBottomSpace) / 2f).coerceIn(16f, 72f)
    // Search reclaims the redundant bottom controls' space for the fixed dock apps.
    // Extremely short windows still scroll rather than reduce touch targets below 48dp.
    val topLimit = maxOf(8f, statusHeight)
    val bottomReserve = if (inLibrary) 12f else 124f
    // Four slots span three Home icon rows. Five or six use the available app area.
    val minimumHeight = dockCount * 48f + 16f
    val desiredHeight = if (p.dockAlignToGrid) {
        maxOf(minOf(dockCount - 2, 3) * row + icon, minimumHeight)
    } else maxOf(256f, minimumHeight)
    val dockHeight = minOf(desiredHeight, (height - topLimit - bottomReserve).coerceAtLeast(76f))
    val dockRowHeight = ((dockHeight - 16f) / dockCount).coerceAtLeast(48f)
    // Use the home position as the anchor, so removing library buttons does not
    // move a low-positioned dock on ordinary page swipes. Move up only to fit.
    val homeDockHeight = minOf(desiredHeight, (height - topLimit - 124f).coerceAtLeast(76f))
    val homeDockTop = (if (p.dockAlignToGrid) contentTop + widget + 18f else height * p.dockPosition - homeDockHeight / 2f)
        .coerceIn(topLimit, maxOf(topLimit, height - homeDockHeight - 124f))
    val dockTop = homeDockTop.coerceIn(topLimit, maxOf(topLimit, height - dockHeight - bottomReserve))
    return HomeGeometry(expanded, homeWidth, gridWidth, icon, row, widget, contentTop, dockTop, dockHeight, dockRowHeight)
}

/** Keep stored order stable across installs, removals and configuration changes. */
fun reconcileOrder(saved: List<String>, installed: List<String>): List<String> {
    val present = installed.toSet()
    return (saved.filter { it in present } + installed).distinct()
}

/** Installing an app must never create a home-screen pin. */
fun reconcilePins(saved: List<String>, installed: List<String>): List<String> {
    val available = installed.toSet()
    return saved.filter { it in available }.distinct()
}

fun migrateHomePins(legacy: List<String>, installed: List<String>, suggested: List<String>): List<String> {
    val surviving = reconcilePins(legacy, installed)
    val oldSet = surviving.toSet()
    val wasReordered = surviving.isNotEmpty() && surviving != installed.filter { it in oldSet }
    return if (wasReordered) surviving.take(16) else reconcilePins(suggested, installed).take(16)
}

fun homePageCount(cellCount: Int) = maxOf(1, (cellCount + HOME_CELLS - 1) / HOME_CELLS)

fun moveApp(order: List<String>, id: String, offset: Int): List<String> {
    val from = order.indexOf(id)
    if (from < 0) return order
    val to = (from + offset).coerceIn(0, order.lastIndex)
    return order.toMutableList().apply { add(to, removeAt(from)) }
}
