package kr.me.nulld.iduohome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal enum class CustomizationPage { OVERVIEW, WALLPAPER, ICONS, HOME, GESTURES, BACKUP, HELP }

@Composable
internal fun CustomizationSheet(state: LauncherState, initiallyWide: Boolean, model: LauncherModel,
    isDefaultHome: Boolean, page: CustomizationPage, onPage: (CustomizationPage) -> Unit,
    onMakeDefault: () -> Unit, onClose: () -> Unit, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit, onWallpaperPreview: () -> Unit,
    onExportLayout: () -> Unit, onImportLayout: () -> Unit,
    appearance: AppearanceState, onAppearanceMode: (AppearanceMode) -> Unit,
    onAppearanceManual: (String, Double, Double) -> Unit, onAppearanceDeviceLocation: () -> Unit,
    onAppearanceClear: () -> Unit, backgrounds: LauncherBackgroundController, homePage: Int = 0,
    onShadeSetup: () -> Unit = {},
    onDuneWallpaperPreview: () -> Unit = {},
) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    var wide by rememberSaveable { mutableStateOf(initiallyWide) }
    val title = when (page) {
        CustomizationPage.OVERVIEW -> settingsContext.getString(R.string.settings_title)
        CustomizationPage.WALLPAPER -> settingsContext.getString(R.string.settings_wallpaper_title)
        CustomizationPage.ICONS -> settingsContext.getString(R.string.settings_icons_title)
        CustomizationPage.HOME -> settingsContext.getString(R.string.settings_home_title)
        CustomizationPage.GESTURES -> settingsContext.getString(R.string.settings_gestures_title)
        CustomizationPage.BACKUP -> settingsContext.getString(R.string.settings_backup_title)
        CustomizationPage.HELP -> settingsContext.getString(R.string.settings_help_title)
    }
    val bodyScroll = rememberScrollState()
    LaunchedEffect(page) { bodyScroll.scrollTo(0) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (page != CustomizationPage.OVERVIEW) IconButton(onClick = { onPage(CustomizationPage.OVERVIEW) },
                Modifier.testTag("customization-back")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, settingsContext.getString(R.string.settings_back)) }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, settingsContext.getString(R.string.settings_close)) }
        }
        Column(Modifier.weight(1f).verticalScroll(bodyScroll).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (page) {
                CustomizationPage.OVERVIEW -> {
                    if (!isDefaultHome) Button(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("default-home-settings")) { Text(settingsContext.getString(R.string.settings_make_default)) }
                    if (state.canUndoEdit) OutlinedButton(onClick = { model.undoEdit(); onClose() },
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(settingsContext.getString(R.string.settings_undo)) }
                    MiniHomePreview(backgrounds.previewBitmap, state, 176.dp, initiallyWide)
                    CustomizationDestination(Icons.Rounded.Wallpaper, settingsContext.getString(R.string.settings_wallpaper_title),
                        if (backgrounds.previewPending) settingsContext.getString(R.string.settings_photo_ready) else settingsContext.getString(R.string.settings_wallpaper_summary),
                        "customization-wallpaper") { onPage(CustomizationPage.WALLPAPER) }
                    CustomizationDestination(Icons.Rounded.AutoAwesome, settingsContext.getString(R.string.settings_icons_title),
                        settingsContext.getString(R.string.settings_icons_summary), "customization-icons") { onPage(CustomizationPage.ICONS) }
                    CustomizationDestination(Icons.Rounded.GridView, settingsContext.getString(R.string.settings_home_title),
                        settingsContext.getString(R.string.settings_home_summary), "customization-home") { onPage(CustomizationPage.HOME) }
                    CustomizationDestination(Icons.Rounded.Search, settingsContext.getString(R.string.settings_gestures_title),
                        settingsContext.getString(R.string.settings_gestures_summary), "customization-gestures") { onPage(CustomizationPage.GESTURES) }
                    CustomizationDestination(Icons.Rounded.Save, settingsContext.getString(R.string.settings_backup_title),
                        settingsContext.getString(R.string.settings_backup_summary), "customization-backup") { onPage(CustomizationPage.BACKUP) }
                    CustomizationDestination(Icons.AutoMirrored.Rounded.HelpOutline, settingsContext.getString(R.string.settings_help_title),
                        settingsContext.getString(R.string.settings_help_summary), "customization-help") {
                        onPage(CustomizationPage.HELP)
                    }
                    if (isDefaultHome) TextButton(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("default-home-settings")) { Text(settingsContext.getString(R.string.settings_change_home)) }
                }
                CustomizationPage.WALLPAPER -> {
                    MiniHomePreview(backgrounds.previewBitmap, state, 228.dp, initiallyWide)
                    Text(settingsContext.getString(R.string.settings_android_wallpaper), style = MaterialTheme.typography.titleMedium)
                    Text(settingsContext.getString(R.string.settings_android_wallpaper_help),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onWallpaperPreview, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("wallpaper-preview")) { Icon(Icons.Rounded.Wallpaper, null); Spacer(Modifier.width(8.dp)); Text(settingsContext.getString(R.string.settings_choose_wallpaper)) }
                    OutlinedButton(onClick = onDuneWallpaperPreview, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("wallpaper-dunes-live")) { Icon(Icons.Rounded.Image, null); Spacer(Modifier.width(8.dp)); Text(settingsContext.getString(R.string.settings_preview_dunes)) }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(settingsContext.getString(R.string.settings_background_photo), style = MaterialTheme.typography.titleMedium)
                    Text(settingsContext.getString(R.string.settings_background_photo_help), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = backgrounds::choosePhoto, enabled = !backgrounds.loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("background-choose")) {
                        Text(if (backgrounds.previewPending) settingsContext.getString(R.string.settings_choose_other_photo) else settingsContext.getString(R.string.settings_choose_photo))
                    }
                    if (backgrounds.previewPending) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = backgrounds::cancelPreview, Modifier.weight(1f).heightIn(min = 48.dp)
                            .testTag("background-preview-cancel")) { Text(settingsContext.getString(R.string.settings_cancel)) }
                        Button(onClick = backgrounds::applyPreview, enabled = backgrounds.previewBitmap != null,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("background-preview-apply")) { Text(settingsContext.getString(R.string.settings_apply)) }
                    }
                    if (!backgrounds.previewPending) LauncherBackgroundButtons(backgrounds)
                    if (backgrounds.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("background-loading"))
                    (backgrounds.errorMessage ?: backgrounds.successMessage)?.let { message ->
                        TextButton(onClick = backgrounds::clearMessage, Modifier.fillMaxWidth().testTag("background-message")) { Text(message) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    AppearanceSettings(appearance, onAppearanceMode, onAppearanceManual, onAppearanceDeviceLocation, onAppearanceClear)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(stringResource(R.string.fold_effect_title), style = MaterialTheme.typography.titleMedium)
                    SettingsSwitch(stringResource(R.string.fold_effect_enable), state.foldEffectEnabled,
                        model::setFoldEffectEnabled, "fold-effect-switch")
                    Text(stringResource(R.string.fold_effect_description), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.foldEffectEnabled) {
                        SettingsSwitch(stringResource(R.string.fold_effect_follow_angle), state.foldEffectFollowAngle,
                            model::setFoldEffectFollowAngle, "fold-angle-switch")
                        Text(stringResource(R.string.fold_effect_angle_description), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        CustomizationSlider(stringResource(R.string.fold_effect_strength),
                            settingsContext.getString(R.string.settings_percent, (state.foldEffectStrength * 100).toInt()), state.foldEffectStrength, .25f..1f,
                            model::setFoldEffectStrength)
                    }
                }
                CustomizationPage.ICONS -> IconSettingsPage(model, state)
                CustomizationPage.HOME -> HomeLayoutSettings(state, wide, { wide = it }, model, homePage,
                    onEditPins, onWidget, onAddWidget, onRemoveWidget)
                CustomizationPage.GESTURES -> {
                    SettingsSwitch(settingsContext.getString(R.string.settings_discover_enable), state.discoverEnabled, model::setDiscoverEnabled, "discover-switch")
                    SettingsSwitch(settingsContext.getString(R.string.settings_labels_enable), state.labels, model::setLabels, "label-switch")
                    SettingsSwitch(settingsContext.getString(R.string.settings_status_enable), state.verticalStatus, model::setVerticalStatus, "status-switch")
                    SettingsSwitch(settingsContext.getString(R.string.settings_google_search), state.googleSearch, model::setGoogleSearch, "google-search-switch")
                    SettingsSwitch(settingsContext.getString(R.string.settings_double_tap_lock), state.doubleTapToLock, model::setDoubleTapToLock, "double-tap-to-lock-switch")
                    Text(settingsContext.getString(R.string.settings_local_search_help), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(settingsContext.getString(R.string.settings_gestures_help),
                        style = MaterialTheme.typography.bodyMedium)
                }
                CustomizationPage.BACKUP -> {
                    Text(settingsContext.getString(R.string.settings_backup_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onExportLayout, Modifier.weight(1f).heightIn(min = 48.dp).testTag("layout-export")) { Text(settingsContext.getString(R.string.settings_save)) }
                        Button(onClick = onImportLayout, Modifier.weight(1f).heightIn(min = 48.dp).testTag("layout-import")) { Text(settingsContext.getString(R.string.settings_restore)) }
                    }
                    Text(settingsContext.getString(R.string.settings_restore_review_help), style = MaterialTheme.typography.bodySmall)
                }
                CustomizationPage.HELP -> LauncherHelp(
                    isDefaultHome = isDefaultHome,
                    onHomeSettings = onMakeDefault,
                    onAddWidget = { onAddWidget(homePage) },
                    onShadeSetup = onShadeSetup,
                )
            }
        }
    }
}

@Composable
private fun LauncherHelp(
    isDefaultHome: Boolean,
    onHomeSettings: () -> Unit,
    onAddWidget: () -> Unit,
    onShadeSetup: () -> Unit,
) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    HelpSection(Icons.Rounded.Home, settingsContext.getString(R.string.settings_home_app),
        if (isDefaultHome) settingsContext.getString(R.string.settings_home_current_help)
        else settingsContext.getString(R.string.settings_home_choose_help))
    Button(onClick = onHomeSettings, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-home-settings")) {
        Text(if (isDefaultHome) settingsContext.getString(R.string.settings_change_home) else settingsContext.getString(R.string.settings_set_home))
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    HelpSection(Icons.Rounded.TouchApp, settingsContext.getString(R.string.settings_customize_page),
        settingsContext.getString(R.string.settings_customize_page_help))
    HelpSection(Icons.Rounded.Widgets, settingsContext.getString(R.string.settings_widgets),
        settingsContext.getString(R.string.settings_widgets_help))
    OutlinedButton(onClick = onAddWidget, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-add-widget")) {
        Text(settingsContext.getString(R.string.settings_add_widget_page))
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    HelpSection(Icons.Rounded.SwipeDown, settingsContext.getString(R.string.settings_shade_title),
        settingsContext.getString(R.string.settings_shade_help))
    TextButton(onClick = onShadeSetup, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-shade-setup")) {
        Text(settingsContext.getString(R.string.settings_shade_setup))
    }
    HelpSection(Icons.Rounded.Lock, settingsContext.getString(R.string.settings_lock_title),
        settingsContext.getString(R.string.settings_lock_help))
    HelpSection(Icons.Rounded.Explore, settingsContext.getString(R.string.settings_discover),
        settingsContext.getString(R.string.settings_discover_help))
}

@Composable
private fun HelpSection(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun CustomizationDestination(icon: ImageVector, title: String, detail: String, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
internal fun LauncherBackgroundButtons(backgrounds: LauncherBackgroundController) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    OutlinedButton(onClick = backgrounds::reset,
        enabled = backgrounds.photoSelected || backgrounds.systemWallpaperSelected,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("background-reset")) {
        Text(settingsContext.getString(R.string.settings_default_background))
    }
    OutlinedButton(onClick = backgrounds::useSystemWallpaper,
        enabled = backgrounds.photoSelected || !backgrounds.systemWallpaperSelected,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("background-system")) {
        Text(settingsContext.getString(R.string.settings_system_background))
    }
}

@Composable private fun MiniHomePreview(stagedBitmap: android.graphics.Bitmap?, state: LauncherState,
    previewHeight: androidx.compose.ui.unit.Dp, wide: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backgroundRevision = LauncherBackgroundCache.revision.intValue
    val committedBitmap = remember(backgroundRevision) { cachedLauncherBackground(context) }
    val bitmap = stagedBitmap ?: committedBitmap
    val apps = remember(state.apps) { state.apps.associateBy { it.id } }
    val homeIcons = state.homeSlots.mapNotNull { id -> id?.let(apps::get) }.take(8)
    val dockIcons = state.dock.mapNotNull { id -> id?.let(apps::get) }
    val scale = previewHeight.value * .632f / 250f
    fun unit(value: Float) = (value * scale).dp
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.height(previewHeight).width(previewHeight * .632f).clip(RoundedCornerShape(unit(24f)))
            .testTag("customization-home-preview")) {
            DuneWallpaper(wide = wide)
            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            Column(Modifier.fillMaxSize().padding(start = unit(16f), top = unit(18f), end = unit(54f)),
                verticalArrangement = Arrangement.spacedBy(unit(10f))) {
                Box(Modifier.fillMaxWidth().height(unit(42f)).background(MaterialTheme.colorScheme.surface.copy(alpha = .38f), RoundedCornerShape(unit(12f))))
                homeIcons.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { app -> Image(app.imageBitmap, null, Modifier.size(unit(24f)).clip(RoundedCornerShape(unit(7f)))) }
                } }
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end = unit(10f)).width(unit(36f))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .42f), RoundedCornerShape(unit(18f)))
                .padding(vertical = unit(8f)), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(unit(8f))) {
                dockIcons.forEach { app -> Image(app.imageBitmap, null, Modifier.size(unit(22f)).clip(RoundedCornerShape(unit(7f)))) }
            }
        }
    }
}

@Composable private fun HomeLayoutSettings(state: LauncherState, wide: Boolean, onWide: (Boolean) -> Unit,
    model: LauncherModel, homePage: Int, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    val p = if (wide) state.expanded else state.compact
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!wide, { onWide(false) }, label = { Text(settingsContext.getString(R.string.settings_cover)) })
        FilterChip(wide, { onWide(true) }, label = { Text(settingsContext.getString(R.string.settings_inner)) })
    }
    OutlinedButton(onClick = onEditPins, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(settingsContext.getString(R.string.settings_choose_home_apps)) }
    CustomizationSlider(settingsContext.getString(R.string.settings_icon_size), settingsContext.getString(R.string.settings_dimension_dp, p.iconSize.toInt()), p.iconSize, 40f..68f) { model.setPreset(wide, p.copy(iconSize = it)) }
    CustomizationSlider(settingsContext.getString(R.string.settings_row_spacing), settingsContext.getString(R.string.settings_dimension_dp, p.rowGap.toInt()), p.rowGap, 0f..28f) { model.setPreset(wide, p.copy(rowGap = it)) }
    CustomizationSlider(settingsContext.getString(R.string.settings_dock_width), settingsContext.getString(R.string.settings_dimension_dp, p.dockWidth.toInt()), p.dockWidth, 56f..84f) { model.setPreset(wide, p.copy(dockWidth = it)) }
    Text(settingsContext.getString(R.string.settings_pinned_count), style = MaterialTheme.typography.titleSmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (MIN_DOCK_SLOTS..MAX_DOCK_SLOTS).forEach { count ->
            FilterChip(
                selected = state.dock.size == count,
                onClick = { model.setDockCount(count) },
                label = { Text(count.toString()) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("dock-count-$count"),
            )
        }
    }
    SettingsSwitch(settingsContext.getString(R.string.settings_dock_align), p.dockAlignToGrid, { model.setPreset(wide, p.copy(dockAlignToGrid = it)) })
    if (!p.dockAlignToGrid) CustomizationSlider(settingsContext.getString(R.string.settings_dock_position), settingsContext.getString(R.string.settings_percent, (p.dockPosition * 100).toInt()), p.dockPosition, .25f.. .75f) { model.setPreset(wide, p.copy(dockPosition = it)) }
    TextButton(onClick = { model.setPreset(wide, LayoutPreset()) }, Modifier.fillMaxWidth()) { Text(settingsContext.getString(R.string.settings_reset_layout)) }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text(settingsContext.getString(R.string.settings_widget_page, homePage + 1), style = MaterialTheme.typography.titleMedium)
    state.widgetPlacements.filter { it.page == homePage || (wide && it.page == -1) }.forEach { placement ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (placement.page == -1) settingsContext.getString(R.string.settings_unfolded_page) else settingsContext.getString(R.string.settings_widget_placement, placement.spanX, placement.spanY, placement.row + 1), Modifier.weight(1f))
            IconButton(onClick = { onRemoveWidget(placement.slot) }, modifier = Modifier.semantics { contentDescription = if (placement.page == -1) settingsContext.getString(R.string.settings_remove_unfolded_widget) else settingsContext.getString(R.string.settings_remove_widget) }) { Icon(Icons.Rounded.DeleteOutline, null) }
            TextButton(onClick = { onWidget(placement.slot) }) { Text(settingsContext.getString(R.string.settings_replace)) }
        }
    }
    TextButton(onClick = { onAddWidget(homePage) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(settingsContext.getString(R.string.settings_add_widget_page)) }
}

@Composable private fun SettingsSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, tag: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChecked,
            Modifier.semantics { contentDescription = label }.then(if (tag != null) Modifier.testTag(tag) else Modifier))
    }
}

@Composable private fun CustomizationSlider(label: String, valueLabel: String, value: Float,
    range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column { Row { Text(label, Modifier.weight(1f)); Text(valueLabel, color = MaterialTheme.colorScheme.primary) }
        Slider(value, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label }) }
}

@Composable
private fun IconSettingsPage(
    model: LauncherModel,
    state: LauncherState,
) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    val context = LocalContext.current
    val iconPrefs by model.iconPreferences.state.collectAsStateWithLifecycle()
    val installedPacks = remember { IconPackManager.getInstalledIconPacks(context) }
    val currentPackPackage = iconPrefs.iconPackPackage
    val currentShape = iconPrefs.iconShape

    // Live preview card with glassmorphism
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("icon-settings-preview"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                settingsContext.getString(R.string.settings_live_preview),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.apps.take(4).forEach { app ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            bitmap = app.imageBitmap,
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(currentShape.composeShape())
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 64.dp)
                        )
                    }
                }
            }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 4.dp))

    // Icon Shape Selector
    Text(settingsContext.getString(R.string.settings_icon_shape), style = MaterialTheme.typography.titleMedium)
    Text(
        settingsContext.getString(R.string.settings_icon_shape_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(4.dp))

    val shapes = IconShape.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shapes.chunked(2).forEach { rowShapes ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowShapes.forEach { shape ->
                    val selected = shape == currentShape
                    Surface(
                        onClick = { model.setIconShape(shape) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 54.dp)
                            .testTag("shape-option-${shape.name.lowercase()}"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(shape.composeShape())
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                settingsContext.getString(shape.labelRes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = settingsContext.getString(R.string.settings_selected),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 4.dp))

    // Icon Pack Selector
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(settingsContext.getString(R.string.settings_icon_pack), style = MaterialTheme.typography.titleMedium)
            Text(
                settingsContext.getString(R.string.settings_icon_pack_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(Modifier.height(4.dp))

    // Default system pack option
    val isSystemDefault = currentPackPackage == null
    Surface(
        onClick = { model.setIconPack(null) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("icon-pack-default"),
        shape = RoundedCornerShape(16.dp),
        color = if (isSystemDefault) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            if (isSystemDefault) 2.dp else 1.dp,
            if (isSystemDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(36.dp).clip(currentShape.composeShape())
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Apps, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    settingsContext.getString(R.string.settings_system_default),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSystemDefault) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
                )
                Text(
                    settingsContext.getString(R.string.settings_adaptive_icons),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = isSystemDefault,
                onClick = { model.setIconPack(null) }
            )
        }
    }

    // Installed packs list
    installedPacks.forEach { pack ->
        val isSelected = currentPackPackage == pack.packageName
        Surface(
            onClick = { model.setIconPack(pack.packageName) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("icon-pack-${pack.packageName}"),
            shape = RoundedCornerShape(16.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(
                if (isSelected) 2.dp else 1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pack.icon != null) {
                    Image(
                        bitmap = pack.icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(currentShape.composeShape())
                    )
                } else {
                    Box(
                        Modifier.size(36.dp).clip(currentShape.composeShape())
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pack.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
                    )
                    Text(
                        pack.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                RadioButton(
                    selected = isSelected,
                    onClick = { model.setIconPack(pack.packageName) }
                )
            }
        }
    }

    // "Get more icon packs" button
    OutlinedButton(
        onClick = { IconPackManager.openPlayStoreForIconPacks(context) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("icon-packs-play-store")
    ) {
        Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(settingsContext.getString(R.string.settings_get_icon_packs))
    }
}
