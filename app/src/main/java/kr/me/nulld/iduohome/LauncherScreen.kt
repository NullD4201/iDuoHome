@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package kr.me.nulld.iduohome

import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.os.UserManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal val Ink: Color
    @Composable get() = LocalDuoPalette.current.ink
internal val Glass: Color
    @Composable get() = LocalDuoPalette.current.glass

private fun findFreeWidgetIndex(layout: HomeLayout, page: Int, spanX: Int, spanY: Int): Int? {
    val blocked = layout.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
    for (row in 0..GRID_ROWS - spanY) for (column in 0..GRID_COLUMNS - spanX) {
        val cells = buildList {
            repeat(spanY) { y -> repeat(spanX) { x -> add(homeCellIndex(page, (row + y) * GRID_COLUMNS + column + x)) } }
        }
        if (cells.none { it in blocked || layout.slotAt(it) != null }) return cells.first()
    }
    return null
}

@Composable
fun DuoTheme(dark: Boolean = false, backgroundDark: Boolean = dark, content: @Composable () -> Unit) {
    val palette = (if (dark) DarkDuoPalette else LightDuoPalette).copy(backgroundDark = backgroundDark)
    CompositionLocalProvider(LocalDuoPalette provides palette) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF9BC5D7), onPrimary = Color(0xFF12303D),
            surface = Color(0xFF17272E), onSurface = palette.ink, secondary = Color(0xFFD1BE98),
            secondaryContainer = Color(0xFF314852), onSecondaryContainer = palette.ink)
        else lightColorScheme(primary = Color(0xFF30596D), onPrimary = Color.White,
            surface = Color(0xFFF4F7F8), onSurface = palette.ink, secondary = Color(0xFF84775F),
            secondaryContainer = Color(0xFFDCE8ED), onSecondaryContainer = palette.ink), content = content)
    }
}


@Composable
fun LauncherScreen(
    state: LauncherState, model: LauncherModel, widgets: WidgetController, homeRequests: Int,
    onLaunch: (AppEntry) -> Unit, onMakeDefault: () -> Unit, onAppInfo: (AppEntry) -> Unit,
    isDefaultHome: Boolean, deviceStatus: DeviceStatus, onWallpaperPreview: () -> Unit,
    onDuneWallpaperPreview: () -> Unit = onWallpaperPreview,
    onDiscover: () -> Unit = {}, searchRequests: Int = 0,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit = { app, _ -> onLaunch(app) },
    onGoogleSearch: (android.graphics.Rect?) -> Boolean = { false },
    appearance: AppearanceState = AppearanceState(),
    onAppearanceMode: (AppearanceMode) -> Unit = {},
    onAppearanceManual: (String, Double, Double) -> Unit = { _, _, _ -> },
    onAppearanceDeviceLocation: () -> Unit = {},
    onAppearanceClear: () -> Unit = {},
    showFirstRun: Boolean = false,
    onFinishFirstRun: () -> Unit = {},
    onShadeSetup: () -> Unit = {},
    onLockScreen: (() -> Unit)? = null,
    onUninstall: (AppEntry) -> Unit = onAppInfo,
) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    var sheet by rememberSaveable { mutableStateOf("") }
    var dockSlot by rememberSaveable { mutableIntStateOf(0) }
    var widgetSlot by rememberSaveable { mutableIntStateOf(0) }
    var widgetTargetIndex by rememberSaveable { mutableIntStateOf(Int.MIN_VALUE) }
    var widgetExactTarget by rememberSaveable { mutableStateOf(false) }
    var widgetPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var widgetProfileSerial by rememberSaveable { mutableStateOf<Long?>(null) }
    var widgetSession by remember { mutableStateOf<WidgetPickerSession?>(null) }
    var widgetPlacementMessage by remember { mutableStateOf<String?>(null) }
    var emptyCellIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var resizeAnchor by remember { mutableStateOf<WidgetResizeAnchor?>(null) }
    var resizeDraft by remember { mutableStateOf<WidgetPlacement?>(null) }
    val resizeSlot = resizeAnchor?.placement?.slot
    var resizeGrid by remember { mutableStateOf(WidgetGridSizing(GRID_COLUMNS, GRID_ROWS, 1f, 1f)) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var optionSource by remember { mutableStateOf<DragRegion?>(null) }
    var selection by remember { mutableStateOf<Set<DropTarget>>(emptySet()) }
    var customizationPage by rememberSaveable { mutableStateOf(CustomizationPage.OVERVIEW) }
    LaunchedEffect(sheet) { if (sheet.isEmpty()) customizationPage = CustomizationPage.OVERVIEW }
    var openFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var createFolderFirstId by rememberSaveable { mutableStateOf<String?>(null) }
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    var lastHomePage by rememberSaveable { mutableIntStateOf(0) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var pinQuery by rememberSaveable { mutableStateOf("") }
    val launcherActivity = androidx.activity.compose.LocalActivity.current as MainActivity
    val launcherRootView = LocalView.current.rootView
    DisposableEffect(sheet == "widgets") {
        val active = sheet == "widgets"
        if (active) LiveDiscover.setExternalResultPending(launcherActivity, "main", "widget-picker", true)
        onDispose { if (active) LiveDiscover.setExternalResultPending(launcherActivity, "main", "widget-picker", false) }
    }
    val appsById = remember(state.apps) { state.apps.associateBy { it.id } }
    val drag = remember { HomeDragState() }
    val folderOwnsInput = openFolderId != null || drag.source?.folderId != null
    DisposableEffect(folderOwnsInput) {
        if (folderOwnsInput) LiveDiscover.setExternalResultPending(launcherActivity, "main", "folder-panel", true)
        onDispose { if (folderOwnsInput) LiveDiscover.setExternalResultPending(launcherActivity, "main", "folder-panel", false) }
    }
    val haptic = LocalHapticFeedback.current
    val homePages = state.homePages
    val pendingNewPage = widgets.pendingPlacement?.page == homePages
    val homeDragActive = drag.active &&
        ((drag.source?.target !is DropTarget.Library && drag.originPage != homePages) || drag.moved)
    val visibleHomePages = homePages + if (homeDragActive || widgetSession != null || pendingNewPage) 1 else 0
    var expandedWorkspace by remember { mutableStateOf(false) }
    val firstHome = if (state.discoverEnabled && DiscoverBounds.available) 1 else 0
    val pageCount = visibleHomePages + 1
    val nativePager = key(firstHome) {
        rememberPagerState(initialPage = savedPage.coerceIn(-firstHome, pageCount - 1) + firstHome, pageCount = { pageCount + firstHome })
    }
    val pager = remember(nativePager, firstHome) { LauncherPager(nativePager, firstHome) }
    fun leaveTemporaryWidgetPage() {
        val persistedPages = model.state.value.homePages
        if (pager.currentPage >= persistedPages)
            pager.requestScrollToPage((persistedPages - 1).coerceAtLeast(0))
    }
    var priorPendingPlacement by remember { mutableStateOf<WidgetPlacement?>(null) }
    LaunchedEffect(widgets.pendingPlacement, state.layout) {
        val pending = widgets.pendingPlacement
        if (pending != null) priorPendingPlacement = pending
        else priorPendingPlacement?.let { prior ->
            if (model.placement(prior.slot) == null && prior.page >= homePages) leaveTemporaryWidgetPage()
            priorPendingPlacement = null
        }
    }
    val pageGestures = remember(nativePager) { PageGestureLimits(nativePager) }
    SideEffect { pageGestures.editing = drag.active || widgetSession != null || resizeSlot != null || selectedId != null || selection.isNotEmpty(); LiveDiscover.allowNativeOpen = state.discoverEnabled && pager.currentPage == 0 && !drag.active && widgetSession == null && resizeSlot == null && selectedId == null && selection.isEmpty() && sheet.isEmpty() }
    val pageFling = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(nativePager, pagerSnapDistance = pageGestures)
    var nativeMotion by remember(nativePager) { mutableStateOf(false) }
    DisposableEffect(nativePager) {
        val callback: (Float) -> Unit = callback@{ progress ->
            if (firstHome == 0) return@callback
            val scrolling = nativePager.isScrollInProgress
            if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_received",
                "progress=$progress scrolling=$scrolling nativeMotion=$nativeMotion current=${nativePager.currentPage} offset=${nativePager.currentPageOffsetFraction}")
            if (!scrolling || nativeMotion) {
                val priorNativeMotion = nativeMotion
                nativeMotion = progress > 0f && progress < 1f
                val position = 1f - progress
                val page = position.roundToInt()
                if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_accepted",
                    "progress=$progress nativeMotion=$priorNativeMotion->$nativeMotion requestPage=$page requestOffset=${position - page}")
                nativePager.requestScrollToPage(page, position - page)
            } else if (DuoMotionTrace.enabled) DuoMotionTrace.event("native_callback_rejected",
                "progress=$progress reason=compose_scrolling nativeMotion=$nativeMotion")
        }
        LiveDiscover.onNativeProgress = callback
        onDispose { if (LiveDiscover.onNativeProgress === callback) LiveDiscover.onNativeProgress = null }
    }
    LaunchedEffect(nativePager) {
        snapshotFlow { Triple((1f - nativePager.currentPage - nativePager.currentPageOffsetFraction).coerceIn(0f, 1f), nativePager.isScrollInProgress, nativeMotion) to (nativePager.targetPage < firstHome) }
            .collect { (motion, towardFeed) ->
                val (progress, scrolling, native) = motion
                if (firstHome > 0) {
                    if (DuoMotionTrace.enabled) DuoMotionTrace.event("pager_observer",
                        "progress=$progress scrolling=$scrolling nativeMotion=$native towardFeed=$towardFeed")
                    if (scrolling) {
                        if (nativeMotion && DuoMotionTrace.enabled) DuoMotionTrace.event("native_owner_cleared",
                            "reason=compose_scrolling progress=$progress")
                        nativeMotion = false
                        LiveDiscover.page(progress, true, towardFeed)
                    } else if (!native) LiveDiscover.page(progress, false)
                }
            }
    }
    val scope = rememberCoroutineScope()
    DisposableEffect(pager) {
        val callback = { scope.launch { pager.animateScrollToPage(0) }; Unit }
        LiveDiscover.onHomeRequest = callback
        onDispose { if (LiveDiscover.onHomeRequest === callback) LiveDiscover.onHomeRequest = null }
    }
    val view = androidx.compose.ui.platform.LocalView.current
    val wallpaperManager = remember(view) { runCatching { android.app.WallpaperManager.getInstance(view.context) }.getOrNull() }
    LaunchedEffect(nativePager, homePages) {
        val totalPages = (homePages - 1).coerceAtLeast(1)
        snapshotFlow {
            val offset = (nativePager.currentPage + nativePager.currentPageOffsetFraction).coerceIn(0f, totalPages.toFloat())
            offset / totalPages
        }.distinctUntilChanged().collect { offset ->
            val token = view.windowToken
            if (token != null && wallpaperManager?.isWallpaperSupported == true) {
                runCatching {
                    wallpaperManager.setWallpaperOffsets(token, offset, 0f)
                    wallpaperManager.setWallpaperOffsetSteps(1f / totalPages, 1f)
                }
            }
        }
    }
    var previousHomePages by remember { mutableIntStateOf(homePages) }
    var previousEditRevision by remember { mutableIntStateOf(state.editRevision) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(pager, homePages) {
        snapshotFlow { pager.settledPage to drag.active }.distinctUntilChanged().collect { (page, moving) ->
            if (!moving) { savedPage = page; if (page in 0 until homePages) lastHomePage = page }
        }
    }
    LaunchedEffect(homePages, state.editRevision) {
        if (homePages != previousHomePages && !drag.active) {
            // Pin edits in the library keep the library selected; a completed drop stays on home.
            if (state.editRevision == previousEditRevision) {
                if (pager.currentPage == previousHomePages) pager.scrollToPage(homePages)
                else if (pager.currentPage >= pageCount) pager.scrollToPage(homePages - 1)
            } else if (pager.currentPage >= homePages) pager.scrollToPage(homePages - 1)
        }
        previousHomePages = homePages
        previousEditRevision = state.editRevision
    }
    LaunchedEffect(pager.settledPage) { if (pager.settledPage != homePages) focus.clearFocus() }
    LaunchedEffect(homeRequests) { if (homeRequests > 0) {
        // An app can pause Home after the destination is visible but before its settle completes.
        val page = pager.currentPage.takeIf { it in 0 until homePages }
            ?: lastHomePage.coerceIn(0, homePages - 1)
        drag.clear(); widgetSession = null; resizeAnchor = null; resizeDraft = null; sheet = ""; widgetPackage = null
        widgetExactTarget = false; widgetPlacementMessage = null; selectedId = null; selection = emptySet()
        openFolderId = null; createFolderFirstId = null; emptyCellIndex = null
        focus.clearFocus(); keyboard?.hide()
        pager.animateScrollToPage(page)
    } }
    LaunchedEffect(searchRequests) { if (searchRequests > 0) { drag.clear(); widgetSession = null; resizeAnchor = null; resizeDraft = null; sheet = ""; widgetPackage = null; widgetExactTarget = false; selectedId = null
        if (!state.googleSearch || !onGoogleSearch(null)) pager.animateScrollToPage(homePages)
    } }
    val widgetPickerBack = {
        if (widgetSession != null) {
            leaveTemporaryWidgetPage(); widgetSession = null; widgetPlacementMessage = null
        } else {
            sheet = ""; widgetPackage = null; widgetExactTarget = false; widgetPlacementMessage = null
        }
    }
    BackHandler(enabled = sheet == "widgets") { widgetPickerBack() }
    BackHandler(enabled = sheet.isEmpty()) { if (selection.isNotEmpty()) selection = emptySet()
        else if (resizeSlot != null || selectedId != null) { resizeAnchor = null; resizeDraft = null; selectedId = null }
        else if (drag.active) {
        val destination = if (drag.source?.target is DropTarget.Library) homePages else drag.originPage.coerceAtMost(homePages - 1)
        drag.clear(); scope.launch { pager.scrollToPage(destination) }
    } else { focus.clearFocus(); scope.launch { pager.animateScrollToPage(0) } } }
    val openDiscover = { if (state.discoverEnabled) { if (firstHome > 0) scope.launch { pager.animateScrollToPage(-1) } else onDiscover() }; Unit }
    val openLibrary = { scope.launch { pager.animateScrollToPage(homePages) }; Unit }
    val iconPrefs by model.iconPreferences.state.collectAsStateWithLifecycle()
    val iconShape = iconPrefs.iconShape
    val composeShape = remember(iconShape) { iconShape.composeShape() }

    val dragWindowPage = if (expandedWorkspace && (drag.active || widgetSession != null)) pager.settledPage else pager.currentPage
    val eligibleDragPages = remember(expandedWorkspace, dragWindowPage, visibleHomePages) {
        if (expandedWorkspace && dragWindowPage in 0..visibleHomePages) {
            setOfNotNull((dragWindowPage - 1).takeIf { it >= -1 }, dragWindowPage)
        } else setOf(dragWindowPage)
    }
    fun openAppOptions(app: AppEntry) {
        optionSource = drag.regions.values.firstOrNull {
            it.appId == app.id && it.target !is DropTarget.Library &&
                (it.page == null || it.page in eligibleDragPages)
        }
        selectedId = app.id; sheet = ""; resizeAnchor = null; resizeDraft = null
    }
    fun openLibraryOptions(app: AppEntry) {
        openAppOptions(app)
        optionSource = drag.regions[DropTarget.Library(app.id)]
    }
    fun openWidgetOptions(slot: Int) {
        optionSource = drag.regions[DropTarget.Widget(slot)]
        widgetSlot = slot; selectedId = null; sheet = "widgetActions"
    }
    fun closeItemOptions() {
        selectedId = null; resizeAnchor = null; resizeDraft = null
        if (sheet == "widgetActions") sheet = ""
    }
    BackHandler(enabled = sheet == "widgetActions") { closeItemOptions() }
    val rawTarget = if (drag.active) drag.destination(drag.pointer, eligibleDragPages)?.target else null
    val target = if (rawTarget is DropTarget.Home && drag.source?.target is DropTarget.Widget) {
        val slot = (drag.source!!.target as DropTarget.Widget).index
        model.placement(slot)?.let {
            DropTarget.Home(adjustedWidgetDropIndex(rawTarget.index, it, drag.source!!.bounds, drag.origin))
        } ?: rawTarget
    } else rawTarget
    val blockedDock = drag.moved && target is DropTarget.Dock &&
        if (drag.source?.folderId != null) state.dock.none { it == null }
        else drag.source?.appId?.let { !canPlaceInDock(state.layout, it) } == true
    val insertionTarget = target.takeIf { drag.moved && !blockedDock }
    val folderHoverIndex = (target as? DropTarget.Home)?.index?.takeIf { index ->
        val moving = drag.source?.appId
        val occupant = state.layout.slotAt(index)
        val bounds = drag.regions[DropTarget.Home(index)]?.bounds
        drag.moved && moving != null && !isReservedFolderId(moving) && drag.source?.folderId == null &&
            state.folders.none { moving in it.appIds } &&
            occupant != null && occupant != moving && !isReservedFolderId(occupant) && bounds != null &&
            drag.pointer.x in (bounds.left + bounds.width * .22f)..(bounds.right - bounds.width * .22f) &&
            drag.pointer.y in bounds.top..(bounds.top + bounds.height * .78f)
    }
    var folderDropIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(folderHoverIndex, drag.source?.appId) {
        folderDropIndex = null
        if (folderHoverIndex != null) { delay(600); folderDropIndex = folderHoverIndex }
    }
    val widgetRawTarget = widgetSession?.let { session -> drag.regions.values.firstOrNull {
        it.target is DropTarget.Home && it.page in eligibleDragPages && it.bounds.contains(session.pointer)
    }?.target as? DropTarget.Home }
    val widgetDraft = widgetSession?.let { session -> session.candidate ?: widgetRawTarget?.let { cell ->
        widgetCandidate(state.layout, session.slot, session.targetIndex ?: cell.index, session.span.width, session.span.height)
    } ?: session.targetIndex?.let { widgetCandidate(state.layout, session.slot, it, session.span.width, session.span.height) } }
    val dropHomePage = if (pager.currentPage >= visibleHomePages)
        lastHomePage.coerceIn(0, homePages - 1) else pager.currentPage.coerceIn(0, homePages)
    val previewLayout = remember(state.layout, drag.source, insertionTarget, drag.moved, resizeDraft, folderHoverIndex) {
        val id = drag.source?.appId
        when {
            resizeDraft != null -> placeWidget(state.layout, resizeDraft!!)
            folderHoverIndex != null -> state.layout
            id != null && insertionTarget is DropTarget.Home -> dropApp(state.layout, id, insertionTarget)
            id != null && insertionTarget is DropTarget.Dock -> dropApp(state.layout, id, insertionTarget)
            drag.source?.target is DropTarget.Widget && insertionTarget is DropTarget.Home ->
                moveWidget(state.layout, (drag.source!!.target as DropTarget.Widget).index, insertionTarget.index)
            else -> state.layout
        }
    }
    val edgeWidth = with(LocalDensity.current) { 30.dp.toPx() }
    val edgePointer = widgetSession?.takeIf { it.dragging }?.pointer ?: drag.pointer
    val edgeActive = (drag.active && drag.moved) || widgetSession?.dragging == true
    val edge = if (!edgeActive) 0 else dragEdgeDirection(edgePointer, drag.rootBounds, edgeWidth)
    LaunchedEffect(edgeActive, edge) {
        if (edge != 0) while (drag.active || widgetSession?.dragging == true) {
            delay(650)
            val next = (pager.currentPage + edge).coerceIn(0, homePages)
            if ((!drag.active && widgetSession?.dragging != true) || next == pager.currentPage) break
            // Do not key this effect on currentPage: it changes halfway through the
            // animation and would cancel the turn before the inner grid is visible.
            // Once the hold commits a turn, finish its animation while the finger moves
            // into the incoming page. Leaving the edge cancels only the next hold timer.
            scope.launch { pager.animateScrollToPage(next) }.join()
        }
    }
    fun finishDrag(cancelled: Boolean) {
        val source = drag.source ?: return
        if (cancelled) closeItemOptions()
        val moved = drag.moved
        val rawDestination = if (moved && !cancelled) drag.destination(drag.pointer, eligibleDragPages)?.target else null
        val destination = if (rawDestination is DropTarget.Home && source.target is DropTarget.Widget) {
            model.placement(source.target.index)?.let {
                DropTarget.Home(adjustedWidgetDropIndex(rawDestination.index, it, source.bounds, drag.origin))
            }
                ?: rawDestination
        } else rawDestination
        val changed = when {
            destination is DropTarget.Home && destination.index == folderDropIndex && source.appId != null ->
                state.layout.slotAt(destination.index)?.let { model.createFolder(source.appId, it, destination.index) } != null
            source.folderId != null && destination is DropTarget.Folder ->
                model.addAppToFolder(destination.id, source.appId ?: "")
            source.folderId != null && destination != null && source.appId != null ->
                model.removeAppFromFolder(source.folderId, source.appId, destination)
            destination == DropTarget.Remove -> model.removePlacement(source.target)
            destination is DropTarget.Home && source.target is DropTarget.Widget -> model.moveWidgetTo(source.target.index, destination.index)
            destination != null && source.appId != null -> model.applyDrop(source.appId, destination)
            else -> false
        }
        val returnToLibrary = !changed && ((source.target is DropTarget.Library && source.folderId == null) ||
            (expandedWorkspace && drag.originPage == homePages))
        val destinationHomePage = (destination as? DropTarget.Home)?.index?.let(::homeCellPage)
        val currentWindow = pager.settledPage.coerceIn(0, visibleHomePages - 1)
        val page = when (destination) {
            is DropTarget.Home -> if (expandedWorkspace && homeCellPage(destination.index) in eligibleDragPages) currentWindow else destinationHomePage!!
            is DropTarget.Dock -> dropHomePage
            is DropTarget.Widget -> 0
            else -> if (source.target is DropTarget.Library) pager.currentPage else drag.originPage
        }
        scope.launch {
            // Let a new home page compose before removing the temporary drop page.
            withFrameNanos { }
            drag.clear()
            withFrameNanos { }
            pager.scrollToPage(if (returnToLibrary) model.state.value.homePages else page.coerceIn(0, model.state.value.homePages - 1))
            if (!moved && !cancelled) {
                if (source.target is DropTarget.Widget) openWidgetOptions(source.target.index)
                else if (source.target is DropTarget.Library && source.folderId == null)
                    source.appId?.let(appsById::get)?.let(::openLibraryOptions)
                else if (source.target is DropTarget.Dock) source.appId?.let(appsById::get)?.let(::openAppOptions)
                else if (source.appId?.let(::isFolderId) == true) openFolderId = source.appId
                else if (source.folderId == null) source.appId?.let(appsById::get)?.let(::openAppOptions)
            }
        }
    }

    val homeLayer = rememberGraphicsLayer()
    val foldEffect = rememberHomeFoldEffect(state.foldEffectEnabled, state.foldEffectFollowAngle,
        state.foldEffectStrength, homeVisible = sheet.isEmpty() && !showFirstRun &&
            pager.currentPage >= 0 && pager.currentPage < homePages && !drag.active &&
            selectedId == null && resizeSlot == null && openFolderId == null && selection.isEmpty(),
        session = model.foldEffectSession)
    DisposableEffect(homeLayer) {
        homeLayer.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        LiveDiscover.homeLayer = homeLayer
        onDispose { if (LiveDiscover.homeLayer === homeLayer) LiveDiscover.homeLayer = null }
    }
    Box(Modifier.fillMaxSize().graphicsLayer {
        renderEffect = foldEffect.render(size)
        // The feed frame reuses the pager's render nodes in another window. Give Main
        // a complete render target so cross-window damage cannot erase stationary controls.
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }.onSizeChanged { LiveDiscover.fullSize = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }.testTag("launcher-root").homeDragInput(drag,
        enabled = sheet.isEmpty() && !showFirstRun && selectedId == null && resizeSlot == null && selection.isEmpty() && pager.currentPage >= 0,
        page = pager.currentPage, eligiblePages = eligibleDragPages, onStart = {
            focus.clearFocus(); keyboard?.hide(); haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (drag.source?.folderId != null) openFolderId = null
            if (drag.source?.target is DropTarget.Library && drag.source?.folderId == null) {
                optionSource = drag.source
                selectedId = drag.source?.appId
            }
        },
        onMoveStart = {
            if (drag.source?.target is DropTarget.Library || drag.originPage == homePages) {
                closeItemOptions()
                scope.launch {
                    withFrameNanos { }
                    if (drag.active) pager.scrollToPage(lastHomePage.coerceIn(0, homePages - 1))
                }
            }
        },
        onFinish = { cancelled -> finishDrag(cancelled) })) {
        DuneWallpaper()
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth.value >= 650f
            val preset = if (wide) state.expanded else state.compact
            val density = LocalDensity.current
            LaunchedEffect(maxWidth, maxHeight) {
                if (!wide && maxWidth < maxHeight && !launcherActivity.isInMultiWindowMode)
                    model.rememberCoverWidth(maxWidth.value)
            }
            val inLibrary = pager.currentPage == visibleHomePages
            var statusHeight by remember { mutableFloatStateOf(0f) }
            val geometry = homeGeometry(maxWidth.value, maxHeight.value, preset, state.labels,
                statusHeight = if (state.verticalStatus) statusHeight + 22f else 0f,
                labelHeight = with(density) { 14.sp.toDp().value } + 6f, inLibrary = inLibrary,
                homeBottomSpace = if (isDefaultHome) 44f else 88f, dockSlots = state.dock.size,
                referenceGridWidth = state.coverGridWidth)
            val topPitch = (geometry.widgetHeight + 18f) / 2f
            val currentGrid = WidgetGridSizing(GRID_COLUMNS, GRID_ROWS, geometry.gridWidth / GRID_COLUMNS,
                minOf(topPitch, geometry.rowHeight), maxOf(topPitch, geometry.rowHeight), 10f, 18f,
                topRowHeightDp = topPitch, appRowHeightDp = geometry.rowHeight)
            SideEffect { resizeGrid = currentGrid }
            LaunchedEffect(currentGrid) { resizeAnchor = null; resizeDraft = null }
            LaunchedEffect(sheet, widgetSlot) {
                if (sheet == "widgetActions") {
                    val placement = model.placement(widgetSlot)
                    val bounds = drag.regions[DropTarget.Widget(widgetSlot)]?.bounds
                    if (placement != null && bounds != null) resizeAnchor = WidgetResizeAnchor(placement, bounds)
                }
            }
            SideEffect { expandedWorkspace = geometry.expanded }
            LaunchedEffect(geometry.expanded) {
                if (!geometry.expanded) {
                    val sessionTargetsLeading = widgetSession?.let { session ->
                        session.candidate?.page == -1 || session.targetIndex?.let(::homeCellPage) == -1
                    } == true
                    val savedTargetLeading = widgetTargetIndex != Int.MIN_VALUE && homeCellPage(widgetTargetIndex) == -1
                    if (sessionTargetsLeading || savedTargetLeading) {
                        widgetSession = null
                        widgetTargetIndex = Int.MIN_VALUE
                        widgetExactTarget = false
                        widgetPackage = null
                        widgetProfileSerial = null
                        widgetPlacementMessage = null
                        sheet = ""
                    }
                    val dragTouchesLeading = drag.source?.page == -1 ||
                        ((target as? DropTarget.Home)?.index?.let(::homeCellPage) == -1)
                    if (dragTouchesLeading) {
                        drag.clear()
                    }
                }
            }
            val contentHeight = maxHeight
            val panelWidth = maxWidth - geometry.homeWidth.dp
            val pagerWidth = maxWidth - preset.dockWidth.dp - 28.dp
            val leftColumnOrigin = (maxWidth / 2f - geometry.gridWidth.dp) / 2f - 16.dp
            val homeStride = panelWidth - leftColumnOrigin
            val bottomSpace = if (isDefaultHome) 44.dp else 88.dp
            val workspaceMotion = if (geometry.expanded) remember(firstHome, pagerWidth, homeStride, density) {
                WorkspacePageMotion(firstHome, with(density) { pagerWidth.toPx() }, with(density) { homeStride.toPx() })
            } else null
            val dockScroll = rememberScrollState()
            var gestureOriginInRoot by remember { mutableStateOf(Offset.Zero) }
            var gestureOriginInWindow by remember { mutableStateOf(Offset.Zero) }
            val pagerInputEnabled = pager.currentPage in -firstHome..visibleHomePages && !drag.active &&
                widgetSession == null && resizeSlot == null && sheet.isEmpty() && !showFirstRun && selectedId == null && selection.isEmpty() &&
                openFolderId == null && emptyCellIndex == null && createFolderFirstId == null &&
                launcherActivity.backups.preview == null && !launcherActivity.backups.pickerPending &&
                !launcherActivity.backgrounds.pickerPending && widgets.setupStatus == null &&
                widgets.reconfigureWidgetId == null
            val lockAction = onLockScreen ?: launcherActivity::lockScreen
            val onDoubleTapLock: (() -> Unit)? = if (pagerInputEnabled && state.doubleTapToLock) lockAction else null
            val homeBackgroundEnabled = pagerInputEnabled && pager.currentPage in 0 until visibleHomePages
            val backgroundTarget = homeOptionsTarget(state, pager.currentPage)
            Box(Modifier.fillMaxSize().onGloballyPositioned {
                gestureOriginInRoot = it.boundsInRoot().topLeft
                gestureOriginInWindow = it.boundsInWindow().topLeft
            }.onePageGestures(
                nativePager,
                pageGestures,
                motion = workspaceMotion,
                enabled = pagerInputEnabled,
                // Positive IDs are provider-owned Android views. Leave their vertical
                // stream untouched so scrollable widgets retain native gesture handling.
                // A dock that is already scrolled also gets first use of a downward drag.
                canStartDownwardSwipe = { point ->
                    if (pager.currentPage !in 0 until visibleHomePages) false else {
                        val region = drag.hit(point + gestureOriginInRoot, eligibleDragPages)
                        val rootOnScreen = IntArray(2).also(launcherRootView::getLocationOnScreen)
                        val screenPoint = point + gestureOriginInWindow +
                            Offset(rootOnScreen[0].toFloat(), rootOnScreen[1].toFloat())
                        !(region?.target is DropTarget.Dock && dockScroll.value > 0) &&
                            !nativeWidgetConsumesVerticalGesture(launcherRootView, screenPoint)
                    }
                },
                onDownwardSwipe = launcherActivity::openSystemShade,
                onLeadingOverscroll = if (state.discoverEnabled && firstHome == 0) onDiscover else null,
            ).pointerInput(homeBackgroundEnabled, backgroundTarget, onDoubleTapLock) {
                if (!homeBackgroundEnabled) return@pointerInput
                detectTapGestures(
                    onDoubleTap = onDoubleTapLock?.let { lock -> { lock() } },
                    onLongPress = { emptyCellIndex = backgroundTarget },
                )
            }) {
            val pagerModifier = Modifier.fillMaxHeight().width(pagerWidth)
                .drawWithContent {
                    homeLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(homeLayer)
                    LiveDiscover.host.get()?.invalidateFrame()
                }.testTag("app-pager")
                .discoverSwipe(state.discoverEnabled && firstHome == 0 && pager.currentPage == 0 && !drag.active && sheet.isEmpty() &&
                    !showFirstRun && selectedId == null, onDiscover)
                .onGloballyPositioned {
                    if (firstHome > 0) {
                        val bounds = it.boundsInWindow()
                        LiveDiscover.pagerOrigin = bounds.topLeft
                        val padding = 32 * density.density
                        LiveDiscover.prepare(launcherActivity,
                            android.graphics.Rect((bounds.left + padding).toInt(), (bounds.top + padding).toInt(),
                                (bounds.right - 16 * density.density).toInt(), (bounds.bottom - padding).toInt()), bounds.width)
                    }
                }
                .semantics { stateDescription = if (pager.currentPage == -1) settingsContext.getString(R.string.settings_discover) else if (pager.currentPage == visibleHomePages) settingsContext.getString(R.string.settings_all_apps) else settingsContext.getString(R.string.settings_home_page_of, pager.currentPage + 1, visibleHomePages) }
            if (geometry.expanded) {
                Box(pagerModifier) {
                    // PagerState remains the source of truth for native Discover progress,
                    // snapping, accessibility state, and programmatic page requests.
                    HorizontalPager(nativePager, Modifier.fillMaxSize(), userScrollEnabled = false,
                        key = { if (it < firstHome) "discover" else if (it - firstHome == visibleHomePages) "library" else "home-${it - firstHome}" }) { }
                    ExpandedWorkspace(
                        nativePager = nativePager, motion = workspaceMotion!!, firstHome = firstHome,
                        visibleHomePages = visibleHomePages, panelWidth = panelWidth,
                        contentHeight = contentHeight, bottomSpace = bottomSpace, geometry = geometry,
                        state = state, previewSlots = previewLayout.slots, previewLeadingSlots = previewLayout.leadingSlots,
                        previewWidgetPlacements = previewLayout.widgetPlacements, appsById = appsById,
                        widgets = widgets, drag = drag, target = target, insertionTarget = insertionTarget,
                        libraryQuery = libraryQuery, onLibraryQuery = { libraryQuery = it },
                        onLaunch = onLaunch, onLaunchFrom = onLaunchFrom, onPinned = model::setPinned,
                        onTurnOnWork = { model.turnOnWork(it) },
                        onActions = ::openAppOptions, onLibraryActions = ::openLibraryOptions, onWidget = ::openWidgetOptions,
                        onFolder = { openFolderId = it },
                        onEmptyWidget = { emptyCellIndex = it },
                        onRefresh = model::refresh,
                        iconShape = composeShape,
                        onDoubleTap = onDoubleTapLock,
                    )
                }
            } else {
                HorizontalPager(nativePager, pagerModifier,
                    // Keep adjacent Home panes attached so ordinary back-and-forth paging does
                    // not synchronously inflate provider RemoteViews inside the gesture frame.
                    // Discover is two physical positions before Home 2. Retain both Home
                    // neighbors to avoid reinflating Home 2's RemoteViews during native exit.
                    beyondViewportPageCount = if (firstHome > 0) 2 else 1,
                    userScrollEnabled = !drag.active && resizeSlot == null, flingBehavior = pageFling,
                    key = { if (it < firstHome) "discover" else if (it - firstHome == visibleHomePages) "library" else "home-${it - firstHome}" }) { physicalPage ->
                    val page = physicalPage - firstHome
                    if (page == -1) {
                        DiscoverContent(Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = 16.dp))
                    } else if (page == visibleHomePages) {
                        AppLibrary(state, libraryQuery, { libraryQuery = it }, onLaunch, model::setPinned,
                            onActions = ::openLibraryOptions, modifier = Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = bottomSpace).testTag("library-page"),
                            drag = drag, page = visibleHomePages, onLaunchFrom = onLaunchFrom, onTurnOnWork = { model.turnOnWork(it) })
                    } else {
                        Row(Modifier.fillMaxSize().testTag("home-surface")) {
                            HomePagePane(page, state, previewLayout.slots, previewLayout.leadingSlots, previewLayout.widgetPlacements, appsById, geometry, contentHeight,
                                bottomSpace, widgets, drag, target, insertionTarget, showLargeWidget = false,
                                iconShape = composeShape,
                                onLaunch = onLaunchFrom, onActions = ::openAppOptions,
                                onWidget = ::openWidgetOptions,
                                onFolder = { openFolderId = it },
                                onEmptyWidget = { emptyCellIndex = it },
                                onRefresh = model::refresh,
                                onDoubleTap = onDoubleTapLock)
                        }
                    }
                }
            }
            if (state.verticalStatus) StatusRail(deviceStatus,
                Modifier.align(Alignment.TopEnd).padding(end = 12.dp).offset(y = geometry.contentTop.dp)
                    .width(preset.dockWidth.dp).onSizeChanged {
                        // The normal rail's 20dp location slot and 3dp gap do not move the dock.
                        statusHeight = (with(density) { it.height.toDp().value } -
                            if (contentHeight < 500.dp) 0f else 23f).coerceAtLeast(0f)
                    },
                compact = contentHeight < 500.dp,
                iconSize = dockIconSize(geometry.iconSize).dp)
            Surface(Modifier.align(Alignment.TopEnd).padding(end = 12.dp).offset(y = geometry.dockTop.dp)
                .width(preset.dockWidth.dp).height(geometry.dockHeight.dp).graphicsLayer {
                    // Composite the stationary dock independently of the shared pager layer.
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }.testTag("dock"),
                shape = RoundedCornerShape(30.dp), color = Glass.copy(alpha = .32f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .3f))) {
                Column(Modifier.padding(vertical = 8.dp).verticalScroll(dockScroll), horizontalAlignment = Alignment.CenterHorizontally) {
                    DockAppColumn(state.dock, previewLayout.dock, appsById, geometry.dockRowHeight,
                        dockIconSize(geometry.iconSize), drag, insertionTarget,
                        iconShape = composeShape,
                        onLaunch = onLaunchFrom, onChoose = { index ->
                            val app = appsById[state.dock.getOrNull(index)]
                            if (app != null) openAppOptions(app) else { dockSlot = index; sheet = "dock" }
                        })
                }
            }
            Column(Modifier.align(Alignment.BottomStart).width(pagerWidth).padding(start = 16.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isDefaultHome) FilledTonalButton(onClick = { sheet = ""; onMakeDefault() }, Modifier.heightIn(min = 48.dp).testTag("home-setup")) {
                    Icon(Icons.Rounded.Home, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(settingsContext.getString(R.string.settings_make_default))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    if (state.discoverEnabled && !drag.active) IconButton(onClick = openDiscover, Modifier.size(32.dp).testTag("discover-page-link")) {
                        Icon(Icons.Rounded.Explore, settingsContext.getString(R.string.settings_discover), tint = Color.White.copy(alpha = .65f), modifier = Modifier.size(17.dp))
                    }
                    if (visibleHomePages <= 6) repeat(visibleHomePages) { index ->
                        Box(Modifier.size(28.dp).clip(CircleShape).clickable { scope.launch { pager.animateScrollToPage(index) } }
                            .semantics { contentDescription = if (index == homePages) settingsContext.getString(R.string.settings_new_home) else settingsContext.getString(R.string.settings_home_page, index + 1) }, contentAlignment = Alignment.Center) {
                            if (index == homePages) Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            else Box(Modifier.size(if (index == pager.currentPage) 6.dp else 4.dp).background(Color.White.copy(alpha = if (index == pager.currentPage) 1f else .4f), CircleShape))
                        }
                    } else Text("${minOf(pager.currentPage + 1, homePages)} / $homePages", color = Color.White, fontSize = 12.sp)
                    IconButton(onClick = openLibrary, Modifier.size(32.dp).testTag("library-page-link")) {
                        Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, settingsContext.getString(R.string.settings_all_apps_page), tint = Color.White.copy(alpha = if (pager.currentPage == homePages) 1f else .6f), modifier = Modifier.size(17.dp))
                    }
                }
            }
            if (!drag.active) Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 6.dp)
                .width(preset.dockWidth.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val controlSize = dockIconSize(geometry.iconSize).dp
                if (inLibrary) {
                    CircleControl(Icons.AutoMirrored.Rounded.ArrowBack, settingsContext.getString(R.string.settings_back_home), "library-home", controlSize) {
                        scope.launch { pager.animateScrollToPage(lastHomePage.coerceIn(0, homePages - 1)) }
                    }
                } else {
                    if (pager.currentPage == -1) CircleControl(Icons.AutoMirrored.Rounded.ArrowForward, settingsContext.getString(R.string.settings_back_home), "discover-home", controlSize) { scope.launch { pager.animateScrollToPage(0) } }
                    val searchBounds = remember { android.graphics.Rect() }
                    Box(Modifier.onGloballyPositioned { searchBounds.set(it.boundsInWindow().toAndroidBounds()) }) {
                        CircleControl(Icons.Rounded.Search, if (state.googleSearch) settingsContext.getString(R.string.settings_search_google) else settingsContext.getString(R.string.settings_search_apps), "search", controlSize) {
                            if (!state.googleSearch || !onGoogleSearch(searchBounds)) openLibrary()
                        }
                    }
                }
            }
            if (sheet.isNotEmpty() && sheet != "widgetActions" && sheet != "widgets") {
                val activeCustomizationPage = if (sheet == "settings:wallpaper") CustomizationPage.WALLPAPER else customizationPage
                ModalBottomSheet(onDismissRequest = {
                    customizationPage = CustomizationPage.OVERVIEW
                    sheet = ""; widgetPackage = null; widgetExactTarget = false
                }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
                    containerColor = MaterialTheme.colorScheme.surface) {
                    ModalDialogBackHandler {
                        if ((sheet == "settings" || sheet == "settings:wallpaper") &&
                            activeCustomizationPage != CustomizationPage.OVERVIEW) {
                            customizationPage = CustomizationPage.OVERVIEW
                            sheet = "settings"
                        } else {
                            customizationPage = CustomizationPage.OVERVIEW
                            sheet = ""; widgetPackage = null; widgetExactTarget = false
                        }
                    }
                    when (sheet) {
                        "dock" -> AppPicker(state.apps, dockSlot,
                            onSelect = {
                                if (canPlaceInDock(state.layout, it.id)) {
                                    model.applyDrop(it.id, DropTarget.Dock(dockSlot)); sheet = ""
                                }
                            },
                            onClear = { model.removePlacement(DropTarget.Dock(dockSlot)) },
                            onLongClick = { openAppOptions(it) },
                            canSelect = { canPlaceInDock(state.layout, it.id) },
                            blockedHint = if (state.dock.none { it == null }) settingsContext.getString(R.string.settings_dock_full) else null)
                        "pins" -> Column(Modifier.fillMaxHeight(.9f).imePadding()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { sheet = "" }) { Text(settingsContext.getString(R.string.settings_done)) }
                            }
                            AppLibrary(state, pinQuery, { pinQuery = it }, onLaunch, model::setPinned,
                                onActions = ::openAppOptions, editing = true, modifier = Modifier.weight(1f).fillMaxWidth(),
                                onTurnOnWork = { model.turnOnWork(it) })
                        }
                        "settings", "settings:wallpaper" -> CustomizationSheet(state, wide, model, isDefaultHome,
                            page = activeCustomizationPage, onPage = { customizationPage = it; sheet = "settings" },
                            onMakeDefault = { sheet = ""; onMakeDefault() },
                            onClose = { customizationPage = CustomizationPage.OVERVIEW; sheet = "" }, onEditPins = { sheet = "pins" },
                            onWidget = { widgetSlot = it; widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets" },
                            onAddWidget = { page -> widgetSlot = model.nextWidgetSlot(); widgetTargetIndex = page * HOME_CELLS; widgetPackage = null; widgetProfileSerial = null; widgetExactTarget = false; sheet = "widgets" },
                            onRemoveWidget = widgets::remove,
                            onExportLayout = { sheet = ""; launcherActivity.backups.startExport() },
                            onImportLayout = { sheet = ""; launcherActivity.backups.startImport() },
                            appearance = appearance, onAppearanceMode = onAppearanceMode,
                            onAppearanceManual = onAppearanceManual, onAppearanceDeviceLocation = onAppearanceDeviceLocation,
                            onAppearanceClear = onAppearanceClear,
                            onShadeSetup = { sheet = ""; onShadeSetup() },
                            backgrounds = launcherActivity.backgrounds,
                            onWallpaperPreview = { sheet = ""; onWallpaperPreview() },
                            onDuneWallpaperPreview = { sheet = ""; onDuneWallpaperPreview() },
                            homePage = pager.currentPage.coerceIn(0, homePages - 1))

                    }
                }
            }
            if (showFirstRun) {
                ModalBottomSheet(
                    onDismissRequest = onFinishFirstRun,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("first-run-setup"),
                ) {
                    FirstRunSetupSheet(
                        isDefaultHome = isDefaultHome,
                        onMakeDefault = onMakeDefault,
                        onAddWidget = {
                            onFinishFirstRun()
                            widgetSlot = model.nextWidgetSlot()
                            widgetTargetIndex = pager.currentPage.coerceIn(0, homePages - 1) * HOME_CELLS
                            widgetPackage = null
                            widgetProfileSerial = null
                            widgetExactTarget = false
                            sheet = "widgets"
                        },
                        onExplore = onFinishFirstRun,
                        onSkip = onFinishFirstRun,
                    )
                }
            }
            if (sheet == "widgets") {
                val catalogProfiles = remember(state.profiles) { state.profiles.filter { it.isPersonal || it.isWork } }
                val selectedProfile = catalogProfiles.firstOrNull { it.userSerial == widgetProfileSerial }
                    ?: catalogProfiles.firstOrNull { it.isPersonal } ?: AppProfile(0, settingsContext.getString(R.string.settings_personal), true, false, false, true, true)
                val userManager = remember(launcherActivity) { launcherActivity.getSystemService(UserManager::class.java) }
                val providers = remember(widgetPackage, selectedProfile, sheet, state.apps) {
                    val user = userManager.getUserForSerialNumber(selectedProfile.userSerial)
                    if (user == null || !selectedProfile.available || !selectedProfile.unlocked || selectedProfile.quiet) emptyList()
                    else runCatching { widgetPackage?.let { widgets.providersForPackage(it, user) }
                        ?: widgets.providers(user) }.getOrDefault(emptyList()).filter { provider ->
                        provider.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 &&
                            provider.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_HIDE_FROM_PICKER == 0
                    }
                }
                val catalog by produceState<List<WidgetCatalogEntry>?>(null, providers, selectedProfile.userSerial, sheet) {
                    value = withContext(Dispatchers.IO) { widgetCatalog(launcherActivity, providers, selectedProfile) }
                }
                val topPitch = (geometry.widgetHeight + 18f) / 2f
                val pickerSizing = remember(geometry) { WidgetGridSizing(GRID_COLUMNS, GRID_ROWS,
                    geometry.gridWidth / GRID_COLUMNS, minOf(topPitch, geometry.rowHeight),
                    maxOf(topPitch, geometry.rowHeight), 10f, 18f,
                    topRowHeightDp = topPitch, appRowHeightDp = geometry.rowHeight) }
                val footprint: (AppWidgetProviderInfo) -> WidgetSpan? = { provider ->
                    widgets.sizing(provider, pickerSizing)?.takeIf { it.minimumFitsGrid }?.preferred
                }
                VisualWidgetPicker(catalog, catalogProfiles.ifEmpty { listOf(selectedProfile) }, selectedProfile,
                    onSelectProfile = { widgetProfileSerial = it.userSerial; widgetPlacementMessage = null },
                    onTurnOnWork = { model.turnOnWork(it) }, hiddenForDrag = widgetSession != null,
                    footprint = footprint,
                    onBack = widgetPickerBack,
                    onTap = { provider ->
                        footprint(provider)?.let { preferredSpan ->
                            val existing = model.placement(widgetSlot)
                            val constraints = widgets.sizing(provider, pickerSizing)
                            val span = existing?.let { placement ->
                                WidgetSpan(placement.spanX, placement.spanY).takeIf {
                                    constraints != null && it.width in constraints.minimum.width..constraints.maximum.width &&
                                        it.height in constraints.minimum.height..constraints.maximum.height
                                }
                            } ?: preferredSpan
                            val special = existing?.takeIf { it.row + it.spanY > GRID_ROWS }
                            if (special != null) {
                                widgetSession = WidgetPickerSession(provider, widgetSlot,
                                    WidgetSpan(special.spanX, special.spanY), Offset.Zero,
                                    dragging = false, candidate = special)
                                widgetPlacementMessage = null
                                scope.launch { pager.scrollToPage(special.page.coerceAtLeast(0).coerceAtMost(homePages - 1)) }
                                return@let
                            }
                            val requestedIndex = existing?.let { homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column) }
                                ?: widgetTargetIndex.takeUnless { it == Int.MIN_VALUE } ?: 0
                            val requestedPage = homeCellPage(requestedIndex).coerceIn(if (expandedWorkspace) -1 else 0, homePages)
                            val availablePages = (if (expandedWorkspace) -1 else 0)..homePages
                            val autoPages = (listOf(requestedPage) + availablePages.filter { it != requestedPage })
                            val freeIndex = if (existing != null || widgetExactTarget) requestedIndex.takeIf {
                                widgetCandidate(state.layout, widgetSlot, it, span.width, span.height) != null
                            } else autoPages.asSequence().flatMap { page ->
                                (0 until HOME_CELLS).asSequence().map { homeCellIndex(page, it) }
                            }.firstOrNull { widgetCandidate(state.layout, widgetSlot, it, span.width, span.height) != null }
                            val targetIndex = freeIndex ?: requestedIndex
                            widgetSession = WidgetPickerSession(provider, widgetSlot, span, Offset.Zero,
                                dragging = false, targetIndex = targetIndex)
                            widgetPlacementMessage = if (freeIndex == null)
                                settingsContext.getString(R.string.settings_widget_size_no_room) else null
                            scope.launch { pager.scrollToPage(homeCellPage(targetIndex).coerceIn(0, homePages)) }
                        }
                    },
                    onBuiltin = builtin@{ builtinId ->
                        val existing = model.placement(widgetSlot)
                        val special = existing?.takeIf { it.row + it.spanY > GRID_ROWS }
                        val span = existing?.let { WidgetSpan(it.spanX, it.spanY) } ?: WidgetSpan(2, 2)
                        if (special != null) {
                            widgetSession = WidgetPickerSession(null, widgetSlot, span, Offset.Zero,
                                dragging = false, candidate = special, builtinId = builtinId)
                            widgetPlacementMessage = null
                            scope.launch { pager.scrollToPage(special.page.coerceAtLeast(0).coerceAtMost(homePages - 1)) }
                            return@builtin
                        }
                        val requested = existing?.let {
                            homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column)
                        } ?: widgetTargetIndex.takeUnless { it == Int.MIN_VALUE } ?: 0
                        val requestedPage = homeCellPage(requested).coerceIn(if (expandedWorkspace) -1 else 0, homePages)
                        val availablePages = (if (expandedWorkspace) -1 else 0)..homePages
                        val candidates = if (model.placement(widgetSlot) != null || widgetExactTarget) sequenceOf(requested)
                            else (listOf(requestedPage) + availablePages.filter { it != requestedPage }).asSequence()
                                .flatMap { page -> (0 until HOME_CELLS).asSequence().map { homeCellIndex(page, it) } }
                        val free = candidates.firstOrNull {
                            widgetCandidate(state.layout, widgetSlot, it, span.width, span.height) != null
                        }
                        widgetSession = WidgetPickerSession(null, widgetSlot, span, Offset.Zero,
                            dragging = false, targetIndex = free ?: requested, builtinId = builtinId)
                        widgetPlacementMessage = if (free == null)
                            settingsContext.getString(R.string.settings_widget_card_no_room) else null
                        scope.launch { pager.scrollToPage(homeCellPage(free ?: requested).coerceIn(0, homePages)) }
                    },
                    onDragStart = { provider, point ->
                        footprint(provider)?.let { span ->
                            widgetSession = WidgetPickerSession(provider, widgetSlot, span, point, dragging = true)
                            widgetPlacementMessage = null
                            scope.launch { pager.scrollToPage(lastHomePage.coerceIn(0, homePages - 1)) }
                        }
                    },
                    onDrag = { point -> widgetSession = widgetSession?.copy(pointer = point) },
                    onDrop = {
                        val session = widgetSession
                        if (session != null && widgetDraft != null) {
                            session.provider?.let { widgets.add(widgetDraft, it, pickerSizing) }
                                ?: session.builtinId?.let { widgets.setBuiltin(widgetDraft.copy(id = it)) }
                            widgetSession = null; sheet = ""; widgetPackage = null
                        } else {
                            leaveTemporaryWidgetPage(); widgetSession = null
                            widgetPlacementMessage = settingsContext.getString(R.string.settings_widget_drop_no_room)
                        }
                    },
                    onCancelDrag = {
                        if (widgetSession != null) {
                            leaveTemporaryWidgetPage(); widgetSession = null
                        }
                    })
                widgetSession?.let { session ->
                    val placementDensity = LocalDensity.current
                    val sessionEntry = session.provider?.let { selected -> catalog?.firstOrNull {
                        it.provider.provider == selected.provider && it.provider.profile == selected.profile } }
                    // Legacy overflow replacements are locked to their existing view
                    // bounds and may begin below the canonical seven-row grid. They have
                    // no Home-cell address; specialAnchor below is their visual anchor.
                    val candidateIndex = widgetDraft?.takeIf { session.candidate == null }
                        ?.let { homeCellIndex(it.page, it.row * GRID_COLUMNS + it.column) }
                    val visualIndex = candidateIndex ?: widgetRawTarget?.index ?: session.targetIndex
                    val specialAnchor = session.candidate?.let { drag.regions[DropTarget.Widget(session.slot)]?.bounds }
                    val anchor = specialAnchor ?: visualIndex?.let { drag.regions[DropTarget.Home(it)]?.bounds }
                    Box(Modifier.fillMaxSize().testTag("widget-placement-mode")
                        .then(if (!session.dragging && session.candidate == null) Modifier.pointerInput(session.slot, session.span) {
                            detectTapGestures { local ->
                                val point = local + drag.rootOrigin
                                val cell = drag.regions.values.firstOrNull {
                                    it.target is DropTarget.Home && it.page in eligibleDragPages && it.bounds.contains(point)
                                }?.target as? DropTarget.Home
                                cell?.let { widgetSession = session.copy(pointer = point, targetIndex = it.index) }
                            }
                        } else Modifier)) {
                        Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)
                            .background(Glass.copy(alpha = .97f), RoundedCornerShape(22.dp))
                            .testTag("widget-placement-toolbar"), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = widgetPickerBack) { Text(settingsContext.getString(R.string.settings_back_widgets)) }
                            if (session.candidate != null) Text(settingsContext.getString(R.string.settings_replace_here), color = Ink,
                                modifier = Modifier.testTag("widget-replacement-locked"))
                            val targetPage = homeCellPage(session.targetIndex ?: 0)
                            if (!session.dragging && session.candidate == null) IconButton(
                                enabled = targetPage > if (expandedWorkspace) -1 else 0, onClick = {
                                val local = homeCellLocal(session.targetIndex ?: 0)
                                val page = targetPage - 1
                                widgetSession = session.copy(targetIndex = homeCellIndex(page, local))
                                scope.launch { pager.animateScrollToPage(page.coerceAtLeast(0)) }
                            }) { Icon(Icons.Rounded.ChevronLeft, settingsContext.getString(R.string.settings_previous_page)) }
                            Text(settingsContext.getString(R.string.settings_widget_dimensions, session.span.width, session.span.height), color = Ink)
                            if (!session.dragging && session.candidate == null) IconButton(enabled = targetPage < homePages, onClick = {
                                val local = homeCellLocal(session.targetIndex ?: 0)
                                val page = (targetPage + 1).coerceAtMost(homePages)
                                widgetSession = session.copy(targetIndex = homeCellIndex(page, local))
                                scope.launch { pager.animateScrollToPage(page.coerceAtLeast(0)) }
                            }) { Icon(Icons.Rounded.ChevronRight, settingsContext.getString(R.string.settings_next_page)) }
                            if (!session.dragging) TextButton(enabled = widgetDraft != null, onClick = {
                                widgetDraft?.let { draft ->
                                    val contentSize = specialAnchor?.let { bounds -> with(placementDensity) {
                                        WidgetContentSize(bounds.width.toDp().value, bounds.height.toDp().value)
                                    } }
                                    session.provider?.let { widgets.add(draft, it, pickerSizing, contentSize) }
                                        ?: session.builtinId?.let { widgets.setBuiltin(draft.copy(id = it)) }
                                    widgetSession = null; sheet = ""; widgetPackage = null
                                }
                            }, modifier = Modifier.testTag("widget-placement-apply")) { Text(settingsContext.getString(R.string.settings_place)) }
                            TextButton(onClick = { leaveTemporaryWidgetPage(); widgetSession = null; sheet = ""; widgetPackage = null },
                                modifier = Modifier.testTag("widget-placement-cancel")) { Text(settingsContext.getString(R.string.settings_cancel)) }
                        }
                        if (anchor != null) {
                            val density = LocalDensity.current
                            val cellWidthPx = with(density) { (geometry.gridWidth / GRID_COLUMNS).dp.toPx() }
                            fun pickerRowTop(row: Int): Float = if (row <= 2) row * with(density) { topPitch.dp.toPx() }
                                else with(density) { (geometry.widgetHeight + 18f + (row - 2) * geometry.rowHeight).dp.toPx() }
                            val candidateRow = homeCellLocal(visualIndex ?: 0) / GRID_COLUMNS
                            val previewWidth = specialAnchor?.let { with(density) { it.width.toDp() } }
                                ?: with(density) { (cellWidthPx * session.span.width - 10.dp.toPx()).toDp() }
                            val previewHeight = specialAnchor?.let { with(density) { it.height.toDp() } }
                                ?: with(density) { (pickerRowTop(candidateRow + session.span.height) -
                                    pickerRowTop(candidateRow) - 18.dp.toPx()).coerceAtLeast(48.dp.toPx()).toDp() }
                            val previewX = if (specialAnchor != null) anchor.left
                                else anchor.left + with(density) { 5.dp.toPx() }
                            Surface(Modifier.offset { IntOffset(previewX.roundToInt(), anchor.top.roundToInt()) }
                                .size(previewWidth, previewHeight).testTag("widget-placement-preview")
                                .semantics { stateDescription = if (widgetDraft != null) settingsContext.getString(R.string.settings_ready_place) else settingsContext.getString(R.string.settings_no_room_here) },
                                color = if (widgetDraft != null) Glass.copy(alpha = .82f) else Color(0xFFE7B6B6).copy(alpha = .9f),
                                shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(3.dp,
                                    if (widgetDraft != null) Color.White else Color(0xFFFF6B6B))) {
                                Box(Modifier.fillMaxSize()) {
                                    if (sessionEntry != null) WidgetProviderPreview(sessionEntry, session.span,
                                        Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(18.dp)))
                                    else Column(Modifier.align(Alignment.Center).padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(session.provider?.loadLabel(launcherActivity.packageManager)?.toString()
                                            ?: when (session.builtinId) {
                                                CLOCK_WIDGET -> settingsContext.getString(R.string.settings_clock)
                                                DATE_WIDGET -> settingsContext.getString(R.string.settings_date)
                                                else -> settingsContext.getString(R.string.settings_widget_panel)
                                            }, color = Ink,
                                            textAlign = TextAlign.Center)
                                        Text(settingsContext.getString(R.string.settings_widget_dimensions, session.span.width, session.span.height), color = Ink)
                                    }
                                    if (widgetDraft == null) Box(Modifier.matchParentSize()
                                        .background(Color(0xFFB83B3B).copy(alpha = .34f)), contentAlignment = Alignment.Center) {
                                        Text(settingsContext.getString(R.string.settings_no_room_here), color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        } else if (session.dragging) {
                            Surface(Modifier.offset { IntOffset((session.pointer.x - 90.dp.toPx()).roundToInt(),
                                (session.pointer.y - 60.dp.toPx()).roundToInt()) }.size(180.dp, 120.dp)
                                .testTag("widget-placement-preview").semantics { stateDescription = settingsContext.getString(R.string.settings_no_room_here) },
                                color = Color(0xFFE7B6B6).copy(alpha = .9f), shape = RoundedCornerShape(24.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (sessionEntry != null) WidgetProviderPreview(sessionEntry, session.span,
                                        Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(18.dp)))
                                    Box(Modifier.matchParentSize().background(Color(0xFFB83B3B).copy(alpha = .34f)),
                                        contentAlignment = Alignment.Center) { Text(settingsContext.getString(R.string.settings_no_room_here), color = Color.White) }
                                }
                            }
                        }
                    }
                }
                widgetPlacementMessage?.let { message ->
                    Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),
                        color = Glass, shape = RoundedCornerShape(18.dp)) { Text(message, Modifier.padding(16.dp), color = Ink) }
                }
            }
        }
        if (homeDragActive) {
            folderDropIndex?.let { index ->
                drag.regions[DropTarget.Home(index)]?.bounds?.let { bounds ->
                    val density = LocalDensity.current
                    val size = with(density) { minOf(bounds.width * .78f, bounds.height * .78f).toDp() }
                    Box(Modifier.offset { IntOffset((bounds.center.x - gestureOriginInRoot.x - size.toPx() / 2).roundToInt(),
                        (bounds.top - gestureOriginInRoot.y).roundToInt()) }.size(size)
                        .background(Glass.copy(alpha = .7f), RoundedCornerShape(18.dp))
                        .border(2.dp, Color.White, RoundedCornerShape(18.dp)).testTag("folder-drop-preview"),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.CreateNewFolder, settingsContext.getString(R.string.home_create_folder), tint = Ink)
                    }
                }
            }
            if (drag.moved) {
                if (pager.currentPage > 0) Box(Modifier.align(Alignment.CenterStart).width(6.dp).height(112.dp)
                    .background(Color.White.copy(alpha = if (edge < 0) .9f else .3f), RoundedCornerShape(6.dp)).testTag("drag-edge-left"))
                if (pager.currentPage < homePages) Box(Modifier.align(Alignment.CenterEnd).width(6.dp).height(112.dp)
                    .background(Color.White.copy(alpha = if (edge > 0) .9f else .3f), RoundedCornerShape(6.dp)).testTag("drag-edge-right"))
            }
            appsById[drag.source?.appId]?.let { app ->
                val size = 66.dp
                val px = with(LocalDensity.current) { size.toPx() }
                Image(app.imageBitmap, settingsContext.getString(R.string.settings_moving_app, app.label), Modifier
                    .offset { IntOffset((drag.pointer.x - gestureOriginInRoot.x - px / 2).roundToInt(),
                        (drag.pointer.y - gestureOriginInRoot.y - px * .65f).roundToInt()) }
                    .size(size).shadow(16.dp, composeShape).clip(composeShape).testTag("drag-ghost"))
            }
            drag.source?.appId?.let { state.layout.folder(it) }?.let { folder ->
                Surface(Modifier.offset { IntOffset((drag.pointer.x - gestureOriginInRoot.x - 42.dp.toPx()).roundToInt(),
                    (drag.pointer.y - gestureOriginInRoot.y - 52.dp.toPx()).roundToInt()) }.size(84.dp)
                    .shadow(16.dp, RoundedCornerShape(20.dp)).testTag("folder-drag-ghost"),
                    color = Glass.copy(alpha = .96f), shape = RoundedCornerShape(20.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(folder.title, color = Ink, textAlign = TextAlign.Center) }
                }
            }
            drag.source?.widgetId?.let { id ->
                val width = 144.dp; val height = 108.dp
                val x = with(LocalDensity.current) { width.toPx() }
                val y = with(LocalDensity.current) { height.toPx() }
                Surface(Modifier.offset { IntOffset((drag.pointer.x - gestureOriginInRoot.x - x / 2).roundToInt(),
                    (drag.pointer.y - gestureOriginInRoot.y - y * .65f).roundToInt()) }
                    .size(width, height).shadow(16.dp, RoundedCornerShape(24.dp)).testTag("drag-ghost"),
                    color = Glass.copy(alpha = .95f), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Widgets, null, tint = Ink)
                        Spacer(Modifier.height(8.dp))
                        Text(remember(id, widgets) { widgetLabel(settingsContext, id, widgets) }, color = Ink, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
            }
            if (blockedDock) Surface(
                Modifier.align(Alignment.TopCenter).statusBarsPadding()
                    .padding(top = 10.dp, start = 20.dp, end = 100.dp),
                color = Glass.copy(alpha = .96f), shape = RoundedCornerShape(18.dp)
            ) {
                Text(settingsContext.getString(R.string.settings_dock_full),
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = Ink, fontSize = 13.sp)
            }
            if (drag.moved && drag.source?.target !is DropTarget.Library &&
                drag.source?.appId?.let(::isFolderId) != true) Surface(
                // Keep removal in the right-side control area that is vacated during a drag.
                // A centered target overlaps the expanded workspace's right-hand first cell.
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 12.dp, bottom = 12.dp)
                    .width((if (expandedWorkspace) state.expanded else state.compact).dockWidth.dp).height(64.dp)
                    .dropRegion(drag, DropTarget.Remove).testTag("remove-drop-target"),
                color = if (target == DropTarget.Remove) Color(0xFFB33B3B) else Glass.copy(alpha = .96f), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxSize().padding(vertical = 6.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.DeleteOutline, null)
                    Text(settingsContext.getString(R.string.settings_remove), fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        if (selectedId != null || sheet == "widgetActions" || resizeAnchor != null) {
            Box(Modifier.matchParentSize().clickable(interactionSource = null, indication = null,
                onClick = ::closeItemOptions).testTag("home-item-dismiss"))
        }
        resizeAnchor?.let { anchor ->
            model.placement(anchor.placement.slot)?.let { placement ->
                val info = widgets.manager.getAppWidgetInfo(placement.id)
                val limits = info?.let { widgets.sizing(it, resizeGrid) }
                val draft = resizeDraft ?: placement
                val valid = draft == placement || placeWidget(state.layout, draft) != state.layout
                val bounds = widgetResizeBounds(anchor, draft, resizeGrid, LocalDensity.current.density)
                    .translate(-gestureOriginInRoot)
                WidgetResizeFrame(bounds, placement, resizeGrid, limits, valid,
                    onStart = { sheet = "" }, onPreview = { resizeDraft = it },
                    onCommit = { model.placeWidget(it); resizeDraft = null }, onCancel = { resizeDraft = null })
            }
        }
        if (sheet == "widgetActions") model.placement(widgetSlot)?.let { placement ->
            val bounds = drag.regions[DropTarget.Widget(widgetSlot)]?.bounds ?: optionSource?.bounds
            if (bounds != null) HomeItemPopup(bounds.translate(-gestureOriginInRoot),
                widgetLabel(settingsContext, placement.id, widgets), "widget-options-popup-$widgetSlot", placed = true,
                onSelect = { selection = setOf(DropTarget.Widget(widgetSlot)); closeItemOptions() },
                onRemoveOrAdd = { widgets.remove(widgetSlot); closeItemOptions() },
                onSettings = if (widgets.canReconfigure(placement.id)) {{
                    closeItemOptions(); widgets.reconfigure(placement.id)
                }} else null)
        }
        appsById[selectedId]?.let { app ->
            val index = state.layout.indexOfShortcut(app.id)
            val dockIndex = state.dock.indexOf(app.id)
            val placed = index != null || dockIndex >= 0
            val source = optionSource?.takeIf { it.appId == app.id }
                ?: drag.regions.values.firstOrNull { it.appId == app.id }
            val bounds = source?.bounds ?: Rect(drag.rootBounds.center, androidx.compose.ui.geometry.Size.Zero)
            val inLibrary = source?.target is DropTarget.Library && source.folderId == null
            val hasWidgets = !inLibrary && runCatching { widgets.providersForPackage(app.packageName, app.user) }.getOrDefault(emptyList()).isNotEmpty()
            HomeItemPopup(bounds.translate(-gestureOriginInRoot), app.label,
                if (inLibrary) "library-app-options-popup" else "app-options-popup", placed,
                onSelect = if (placed) {{
                    selection = setOf(if (dockIndex >= 0) DropTarget.Dock(dockIndex) else DropTarget.Home(index!!))
                    closeItemOptions()
                }} else null,
                onRemoveOrAdd = {
                    if (inLibrary) {
                        val homeIndex = model.addToHome(app.id, lastHomePage)
                        if (homeIndex != null) scope.launch { pager.scrollToPage(homeCellPage(homeIndex).coerceAtLeast(0)) }
                    } else if (dockIndex >= 0) model.removePlacement(DropTarget.Dock(dockIndex)) else model.setPinned(app.id, !placed)
                    closeItemOptions()
                }, onSettings = { closeItemOptions(); onAppInfo(app) },
                onUninstall = if (inLibrary) {{ closeItemOptions(); onUninstall(app) }} else null,
                onWidgets = if (hasWidgets) {{
                    val page = lastHomePage.coerceIn(0, homePages - 1)
                    closeItemOptions(); widgetTargetIndex = homeCellIndex(page, 0); widgetExactTarget = false
                    widgetSlot = model.nextWidgetSlot(); widgetPackage = app.packageName
                    widgetProfileSerial = app.userSerial; sheet = "widgets"
                }} else null,
                onFolder = { closeItemOptions(); createFolderFirstId = app.id })
        }
        if (selection.isNotEmpty()) {
            val selectable = drag.regions.values.filter {
                (it.page == null || it.page in eligibleDragPages) && it.movable && it.folderId == null &&
                    it.target !is DropTarget.Library && it.appId?.let(::isFolderId) != true
            }
            HomeSelectionOverlay(selectable, selection, gestureOriginInRoot,
                labelFor = { it.appId?.let(appsById::get)?.label ?: it.widgetId?.let { id -> widgetLabel(settingsContext, id, widgets) }.orEmpty() },
                onToggle = { selection = if (it in selection) selection - it else selection + it },
                onRemove = { widgets.removeSelection(selection); selection = emptySet() },
                onClose = { selection = emptySet() })
        }
        emptyCellIndex?.let { index ->
            ModalBottomSheet(onDismissRequest = { emptyCellIndex = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                EmptySpaceActionSheet(onWidgets = {
                        widgetTargetIndex = index; widgetExactTarget = true; widgetSlot = model.nextWidgetSlot(); widgetPackage = null; widgetProfileSerial = null
                        emptyCellIndex = null; sheet = "widgets"
                    }, onWallpaper = { emptyCellIndex = null; sheet = "settings:wallpaper" },
                    onCustomize = { emptyCellIndex = null; sheet = "settings" }, onClose = { emptyCellIndex = null })
            }
        }
        createFolderFirstId?.let { firstId ->
            val first = appsById[firstId]
            AlertDialog(onDismissRequest = { createFolderFirstId = null }, title = { Text(settingsContext.getString(R.string.settings_create_folder_with, first?.label ?: settingsContext.getString(R.string.settings_app))) },
                text = { LazyColumn(Modifier.heightIn(max = 420.dp).testTag("folder-app-picker")) {
                    items(state.apps.filter { it.id != firstId && it.available }, key = { it.id }) { second ->
                        TextButton(onClick = {
                            val preferredPage = state.layout.indexOfShortcut(firstId)?.let(::homeCellPage)
                                ?.takeIf { it >= 0 || expandedWorkspace } ?: lastHomePage.coerceIn(0, homePages - 1)
                            val blocked = state.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
                            val targetIndex = (0 until HOME_CELLS).map { homeCellIndex(preferredPage, it) }
                                .firstOrNull { it !in blocked && state.layout.slotAt(it) in listOf(null, firstId, second.id) }
                            if (targetIndex != null) model.createFolder(firstId, second.id, targetIndex)
                            createFolderFirstId = null
                        }, modifier = Modifier.fillMaxWidth().testTag("folder-app-${second.id}")) {
                            Text(second.label, Modifier.fillMaxWidth())
                        }
                    }
                } }, confirmButton = { TextButton(onClick = { createFolderFirstId = null }) { Text(settingsContext.getString(R.string.settings_cancel)) } })
        }
        openFolderId?.let { id ->
            state.folders.firstOrNull { it.id == id }?.let { folder ->
                val blocked = state.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
                val destinationPages = (if (expandedWorkspace) listOf(-1) else emptyList()) + (0 until homePages)
                val homeDestinations = destinationPages.mapNotNull { destinationPage ->
                    (0 until HOME_CELLS).map { homeCellIndex(destinationPage, it) }
                        .firstOrNull { it !in blocked && state.layout.slotAt(it) == null }
                }
                FolderPanel(folder, appsById, drag, pager.currentPage, homeDestinations,
                    dockVacancies = state.dock.indices.filter { state.dock[it] == null },
                    onDismiss = { openFolderId = null }, onRename = { model.renameFolder(id, it) },
                    onLaunch = onLaunchFrom,
                    onMoveOut = { appId, destination ->
                        if (model.removeAppFromFolder(id, appId, destination)) openFolderId = model.folder(id)?.id
                    })
            } ?: LaunchedEffect(id) { openFolderId = null }
        }
        launcherActivity.backups.preview?.let { preview ->
            LayoutRestorePreview(preview, onRestore = {
                launcherActivity.backups.applyImport(); sheet = ""
            }, onCancel = launcherActivity.backups::cancelImport)
        }
        if (launcherActivity.backups.pickerPending) AlertDialog(onDismissRequest = {},
            title = { Text(settingsContext.getString(R.string.settings_layout_document)) },
            text = { Text(settingsContext.getString(R.string.settings_document_pending)) },
            confirmButton = { TextButton(onClick = { launcherActivity.backups.resumePendingPicker() },
                modifier = Modifier.testTag("backup-picker-resume")) { Text(settingsContext.getString(R.string.settings_resume)) } },
            dismissButton = { TextButton(onClick = launcherActivity.backups::cancelImport,
                modifier = Modifier.testTag("backup-picker-cancel")) { Text(settingsContext.getString(R.string.settings_cancel)) } })
        if (launcherActivity.backgrounds.pickerPending && !launcherActivity.backgrounds.loading) AlertDialog(
            onDismissRequest = {}, title = { Text(settingsContext.getString(R.string.settings_photo_title)) },
            text = { Text(settingsContext.getString(R.string.settings_photo_interrupted)) },
            confirmButton = { TextButton(onClick = launcherActivity.backgrounds::choosePhoto,
                modifier = Modifier.testTag("background-picker-resume")) { Text(settingsContext.getString(R.string.settings_resume)) } },
            dismissButton = { TextButton(onClick = launcherActivity.backgrounds::cancelPendingSelection,
                modifier = Modifier.testTag("background-picker-cancel")) { Text(settingsContext.getString(R.string.settings_cancel)) } })
        (launcherActivity.backups.errorMessage ?: launcherActivity.backups.successMessage)?.let { message ->
            AlertDialog(onDismissRequest = launcherActivity.backups::clearMessage,
                title = { Text(if (launcherActivity.backups.errorMessage != null) settingsContext.getString(R.string.settings_backup_problem) else settingsContext.getString(R.string.settings_layout_backup)) },
                text = { Text(message) }, confirmButton = { TextButton(onClick = launcherActivity.backups::clearMessage) { Text(settingsContext.getString(R.string.settings_ok)) } })
        }
        widgets.failureMessage?.let { message ->
            AlertDialog(onDismissRequest = widgets::clearFailure, title = { Text(settingsContext.getString(R.string.settings_widget_not_added)) },
                text = { Text(message, Modifier.testTag("widget-bind-error")) },
                confirmButton = { TextButton(onClick = widgets::clearFailure) { Text(settingsContext.getString(R.string.settings_ok)) } })
        }
        if (widgets.pendingPlacement != null && widgets.setupStatus != null) {
            AlertDialog(onDismissRequest = {}, title = { Text(settingsContext.getString(R.string.settings_finish_widget)) },
                text = { Text(settingsContext.getString(R.string.settings_widget_waiting)) },
                confirmButton = { Button(onClick = widgets::finishPendingSetup,
                    modifier = Modifier.semantics { contentDescription = settingsContext.getString(R.string.settings_continue_widget) }) { Text(settingsContext.getString(R.string.settings_finish_setup)) } },
                dismissButton = { TextButton(onClick = { leaveTemporaryWidgetPage(); widgets.cancelPendingSetup() },
                    modifier = Modifier.semantics { contentDescription = settingsContext.getString(R.string.settings_cancel_widget) }) { Text(settingsContext.getString(R.string.settings_cancel)) } })
        }
        widgets.reconfigureWidgetId?.let {
            AlertDialog(onDismissRequest = {}, title = { Text(settingsContext.getString(R.string.settings_widget_settings)) },
                text = { Text(settingsContext.getString(R.string.settings_widget_interrupted)) },
                confirmButton = { Button(onClick = widgets::finishPendingReconfigure,
                    modifier = Modifier.testTag("widget-reconfigure-resume")) { Text(settingsContext.getString(R.string.settings_resume)) } },
                dismissButton = { TextButton(onClick = widgets::cancelPendingReconfigure,
                    modifier = Modifier.testTag("widget-reconfigure-cancel")) { Text(settingsContext.getString(R.string.settings_cancel)) } })
        }
        }
    }
}

@Composable
private fun ExpandedWorkspace(
    nativePager: androidx.compose.foundation.pager.PagerState,
    motion: WorkspacePageMotion,
    firstHome: Int,
    visibleHomePages: Int,
    panelWidth: Dp,
    contentHeight: Dp,
    bottomSpace: Dp,
    geometry: HomeGeometry,
    state: LauncherState,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    previewWidgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    insertionTarget: DropTarget?,
    libraryQuery: String,
    onLibraryQuery: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit,
    onPinned: (String, Boolean) -> Unit,
    onTurnOnWork: (Long) -> Unit,
    onActions: (AppEntry) -> Unit,
    onLibraryActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit,
    onRefresh: () -> Unit,
    iconShape: Shape = RoundedCornerShape(14.dp),
    onDoubleTap: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val viewportWidth = motion.pageWidth
    val stride = motion.homeStride
    val initialHomeOrigin = with(density) { panelWidth.toPx() }
    val homePaneWidth = with(density) { (geometry.gridWidth + 16f).dp.toPx() }
    val stateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val visibleHomes by remember(nativePager, motion, firstHome, visibleHomePages, initialHomeOrigin, homePaneWidth) {
        derivedStateOf(structuralEqualityPolicy()) {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            val scroll = motion.offset(physicalPosition)
            val intersectingHomes = (0 until visibleHomePages).filter { page ->
                val start = initialHomeOrigin + page * stride
                start + homePaneWidth > scroll && start < scroll + viewportWidth
            }
            val nearestLogicalPage = nativePager.currentPage - firstHome
            // While Discover is current, keep the initial Home pair cached. Otherwise Home 2
            // is recreated midway through the first native exit and provider inflation can
            // block the gesture frame even though that pane began offscreen.
            val retentionAnchor = nearestLogicalPage.coerceAtLeast(0)
            (intersectingHomes + (retentionAnchor - 1..retentionAnchor + 1))
                .filter { it in 0 until visibleHomePages }.distinct().sorted()
        }
    }
    val place: Modifier.(Float) -> Modifier = { x ->
        offset {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            IntOffset((x - motion.offset(physicalPosition)).roundToInt(), 0)
        }
    }
    val showDiscover by remember(nativePager, firstHome) {
        derivedStateOf(structuralEqualityPolicy()) {
            firstHome > 0 && nativePager.currentPage + nativePager.currentPageOffsetFraction <= firstHome + .25f
        }
    }
    val leadingX = initialHomeOrigin - stride
    val showLeading by remember(nativePager, motion, firstHome, panelWidth, leadingX, homePaneWidth) {
        derivedStateOf(structuralEqualityPolicy()) {
            val physicalPosition = nativePager.currentPage + nativePager.currentPageOffsetFraction
            val scroll = motion.offset(physicalPosition)
            panelWidth.value > 0f && physicalPosition - firstHome < 1f &&
                leadingX - scroll + homePaneWidth > 0f
        }
    }
    val libraryPhysicalPage = firstHome + visibleHomePages
    val showLibrary by remember(nativePager, libraryPhysicalPage) {
        derivedStateOf(structuralEqualityPolicy()) {
            nativePager.currentPage + nativePager.currentPageOffsetFraction >= libraryPhysicalPage - 1.25f
        }
    }

    Box(Modifier.fillMaxSize().clipToBounds().testTag("expanded-workspace")) {
        if (showDiscover) {
            key("discover-pane") {
                Box(Modifier.place(-viewportWidth).fillMaxSize()) {
                    DiscoverContent(Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, bottom = 16.dp))
                }
            }
        }

        if (showLeading) {
            key("expanded-leading-home") {
                Box(Modifier.place(leadingX).width((geometry.gridWidth + 16f).dp).fillMaxHeight()
                    .testTag("expanded-leading-home")) {
                    HomePagePane(
                        -1, state, previewSlots, previewLeadingSlots, previewWidgetPlacements, appsById, geometry, contentHeight, bottomSpace,
                        widgets, drag, target, insertionTarget, showLargeWidget = true,
                        iconShape = iconShape,
                        onLaunch = onLaunchFrom, onActions = onActions, onWidget = onWidget,
                        onFolder = onFolder, onEmptyWidget = onEmptyWidget, onRefresh = onRefresh,
                        modifier = Modifier,
                        onDoubleTap = onDoubleTap,
                    )
                }
            }
        }

        visibleHomes.forEach { page ->
            key("expanded-home-$page") {
                stateHolder.SaveableStateProvider("expanded-home-$page") {
                    Box(Modifier.place(initialHomeOrigin + page * stride)
                        .width((geometry.gridWidth + 16f).dp).fillMaxHeight()) {
                        HomePagePane(
                            page, state, previewSlots, previewLeadingSlots, previewWidgetPlacements, appsById, geometry, contentHeight, bottomSpace,
                            widgets, drag, target, insertionTarget, showLargeWidget = page > 0,
                            iconShape = iconShape,
                            onLaunch = onLaunchFrom, onActions = onActions, onWidget = onWidget,
                            onFolder = onFolder,
                            onEmptyWidget = onEmptyWidget,
                            onRefresh = onRefresh,
                            onDoubleTap = onDoubleTap,
                        )
                    }
                }
            }
        }

        if (showLibrary) {
            key("library-pane") {
                Box(Modifier.place(initialHomeOrigin + visibleHomePages * stride)
                    .width(with(density) { (viewportWidth - initialHomeOrigin).toDp() }).fillMaxHeight()) {
                    AppLibrary(state, libraryQuery, onLibraryQuery, onLaunch, onPinned,
                        onActions = onLibraryActions,
                        modifier = Modifier.fillMaxSize().padding(start = 24.dp, top = 16.dp, bottom = bottomSpace)
                            .testTag("library-page"),
                        drag = drag, page = visibleHomePages, onLaunchFrom = onLaunchFrom, onTurnOnWork = onTurnOnWork)
                }
            }
        }
    }
}

private fun homeOptionsTarget(state: LauncherState, page: Int): Int {
    val pageStart = homeCellIndex(page, 0)
    return (pageStart until pageStart + HOME_CELLS).firstOrNull { index ->
        state.layout.slotAt(index) == null && state.widgetPlacements.none { index in it.coveredIndices() }
    } ?: pageStart
}

@Composable
private fun HomePagePane(
    page: Int,
    state: LauncherState,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    previewWidgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    geometry: HomeGeometry,
    contentHeight: Dp,
    bottomSpace: Dp,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    insertionTarget: DropTarget?,
    showLargeWidget: Boolean,
    iconShape: Shape = RoundedCornerShape(14.dp),
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit = {},
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onDoubleTap: (() -> Unit)? = null,
) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    val homeScroll = rememberScrollState()
    var paneBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val backgroundTarget = homeOptionsTarget(state, page)
    val verticalEdge = with(LocalDensity.current) { 42.dp.toPx() }
    LaunchedEffect(drag.active, page, paneBounds) {
        while (drag.active) {
            val pointer = drag.pointer
            val amount = when {
                !paneBounds.contains(pointer) -> 0f
                pointer.y < paneBounds.top + verticalEdge && homeScroll.canScrollBackward -> -18f
                pointer.y > paneBounds.bottom - verticalEdge && homeScroll.canScrollForward -> 18f
                else -> 0f
            }
            if (amount != 0f) homeScroll.scrollBy(amount)
            delay(16)
        }
    }
    Box(modifier.testTag("home-page-$page")
        .semantics {
            onLongClick(settingsContext.getString(R.string.settings_home_options)) {
                if (!drag.active) onEmptyWidget(backgroundTarget)
                !drag.active
            }
        }
        .pointerInput(backgroundTarget, drag.active, onDoubleTap) {
            detectTapGestures(
                onDoubleTap = onDoubleTap?.let { lock -> { if (!drag.active) lock() } },
                onLongPress = { if (!drag.active) onEmptyWidget(backgroundTarget) }
            )
        }
        .onGloballyPositioned { paneBounds = it.boundsInRoot() }
        .width((geometry.gridWidth + 16f).dp)
        .height((contentHeight - bottomSpace).coerceAtLeast(0.dp))) {
        Box(Modifier.width(16.dp).fillMaxHeight().testTag("home-options-margin-$page")
            .pointerInput(backgroundTarget, drag.active, onDoubleTap) {
                detectTapGestures(
                    onDoubleTap = if (onDoubleTap != null) {
                        { if (!drag.active) onDoubleTap() }
                    } else null,
                    onLongPress = {
                        if (!drag.active) onEmptyWidget(backgroundTarget)
                    }
                )
            })
        Column(Modifier.offset(x = 16.dp).width(geometry.gridWidth.dp).fillMaxHeight()
            .verticalScroll(homeScroll).padding(top = geometry.contentTop.dp, bottom = 8.dp)) {
            SharedHomeGrid(page, state.homeSlots, state.leadingSlots, previewSlots, previewLeadingSlots, previewWidgetPlacements,
                appsById, geometry, state.labels, widgets, drag, target,
                folders = state.folders, iconShape = iconShape, onLaunch = onLaunch, onActions = onActions, onWidget = onWidget,
                onFolder = onFolder, onEmptyWidget = onEmptyWidget, onDoubleTap = onDoubleTap)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
            if (state.error != null) Text(state.error, color = Color.White,
                modifier = Modifier.clickable(onClick = onRefresh).padding(12.dp))
        }
    }
}

@Composable
private fun CircleControl(icon: ImageVector, label: String, tag: String, visualSize: Dp, action: () -> Unit) {
    IconButton(onClick = action, modifier = Modifier.size(visualSize.coerceAtLeast(48.dp)).testTag(tag)) {
        Box(Modifier.size(visualSize).testTag("$tag-visual").background(Glass.copy(alpha = .22f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = .25f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun SharedHomeGrid(
    page: Int,
    savedSlots: List<String?>,
    savedLeadingSlots: List<String?>,
    previewSlots: List<String?>,
    previewLeadingSlots: List<String?>,
    widgetPlacements: List<WidgetPlacement>,
    appsById: Map<String, AppEntry>,
    geometry: HomeGeometry,
    labels: Boolean,
    widgets: WidgetController,
    drag: HomeDragState,
    target: DropTarget?,
    folders: List<FolderEntry>,
    iconShape: Shape = RoundedCornerShape(14.dp),
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onActions: (AppEntry) -> Unit,
    onWidget: (Int) -> Unit,
    onFolder: (String) -> Unit,
    onEmptyWidget: (Int) -> Unit,
    onDoubleTap: (() -> Unit)? = null,
) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    val rowHeight = geometry.rowHeight
    val iconSize = geometry.iconSize
    val pageStart = homeCellIndex(page, 0)
    val pageRange = pageStart until pageStart + HOME_CELLS
    fun savedAt(index: Int) = if (page == -1) savedLeadingSlots.getOrNull(homeCellLocal(index)) else savedSlots.getOrNull(index)
    fun previewAt(index: Int) = if (page == -1) previewLeadingSlots.getOrNull(homeCellLocal(index)) else previewSlots.getOrNull(index)
    fun savedIndexOf(id: String) = if (page == -1) savedLeadingSlots.indexOf(id).takeIf { it >= 0 }?.let { homeCellIndex(-1, it) }
        else savedSlots.indexOf(id).takeIf { it >= 0 }
    fun previewIndexOf(id: String) = if (page == -1) previewLeadingSlots.indexOf(id).takeIf { it >= 0 }?.let { homeCellIndex(-1, it) }
        else previewSlots.indexOf(id).takeIf { it >= 0 }
    val draggedId = drag.source?.appId
    val homeTarget = (target as? DropTarget.Home)?.index
    val source = drag.source?.target as? DropTarget.Home
    val draggedPreviewIndex = draggedId?.let(::previewIndexOf) ?: -1
    val hiddenIndex = when {
        !drag.active || !drag.moved -> null
        homeTarget != null -> draggedPreviewIndex.takeIf { it >= 0 }
        source != null && target !is DropTarget.Dock -> draggedPreviewIndex.takeIf { it >= 0 }
        else -> null
    }
    val dimDragged = drag.active && !drag.moved && source != null
    val pending = widgets.pendingPlacement?.takeIf { it.page == page }
    val pendingIsReplacement = pending != null && widgetPlacements.any { it.slot == pending.slot }
    val pageWidgets = widgetPlacements.filter { it.page == page } + listOfNotNull(pending?.takeUnless { pendingIsReplacement })
    val renderedRows = maxOf(GRID_ROWS, pageWidgets.maxOfOrNull { it.row + it.spanY } ?: GRID_ROWS)
    val topPitch = (geometry.widgetHeight + 18f) / 2f
    fun rowTop(row: Int) = if (row <= 2) row * topPitch else geometry.widgetHeight + 18f + (row - 2) * rowHeight
    BoxWithConstraints(Modifier.fillMaxWidth().height(rowTop(renderedRows).dp)) {
        val density = LocalDensity.current
        val cellWidth = maxWidth / 4
        val cellWidthPx = with(density) { cellWidth.toPx() }
        val rowHeightPx = with(density) { rowHeight.dp.toPx() }

        repeat(HOME_CELLS) { localIndex ->
            val globalIndex = pageStart + localIndex
            val cell = DropTarget.Home(globalIndex)
            val savedId = savedAt(globalIndex)
            val savedApp = appsById[savedId]
            val savedFolder = folders.firstOrNull { it.id == savedId }
            val previewId = previewAt(globalIndex)
            val highlighted = drag.active && target == cell
            val gap = hiddenIndex == globalIndex
            val row = localIndex / GRID_COLUMNS
            val cellHeight = rowTop(row + 1) - rowTop(row)
            Box(Modifier.offset(x = cellWidth * (localIndex % GRID_COLUMNS), y = rowTop(row).dp)
                .width(cellWidth).height(cellHeight.dp).testTag("home-cell-$globalIndex")
                .dropRegion(drag, cell, savedApp?.id ?: savedFolder?.id, page)
                .combinedClickable(interactionSource = remember(globalIndex) { MutableInteractionSource() }, indication = null,
                    onClick = { savedFolder?.let { onFolder(it.id) } },
                    onLongClick = { if (savedId == null && !drag.active) onEmptyWidget(globalIndex) },
                    onDoubleClick = if (onDoubleTap != null && savedId == null) {
                        { if (!drag.active) onDoubleTap() }
                    } else null),
                contentAlignment = Alignment.TopCenter) {
                if (drag.active && drag.source?.appId != null && (gap || previewId == null)) {
                    val activeSlot = gap || highlighted
                    Box(Modifier.size(iconSize.dp)
                        .testTag(if (gap) "drag-gap-home-$globalIndex" else "empty-home-slot-$globalIndex")
                        .background(Glass.copy(alpha = if (activeSlot) .22f else .08f), RoundedCornerShape(18.dp))
                        .border(if (activeSlot) 2.dp else 1.dp,
                            Color.White.copy(alpha = if (activeSlot) .9f else .5f), RoundedCornerShape(18.dp)))
                }
            }
        }

        val ids = (if (page == -1) savedLeadingSlots + previewLeadingSlots
            else savedSlots.slicePage(pageRange) + previewSlots.slicePage(pageRange)).filterNotNull().distinct()
        ids.forEach { id ->
            val savedIndex = savedIndexOf(id) ?: -1
            val previewIndex = previewIndexOf(id) ?: -1
            val renderIndex = previewIndex.takeIf { it in pageRange } ?: savedIndex.takeIf { it in pageRange } ?: return@forEach
            val app = appsById[id] ?: return@forEach
            key(id) {
                val localIndex = renderIndex - pageStart
                val row = localIndex / GRID_COLUMNS
                val animatedOffset by animateIntOffsetAsState(
                    IntOffset(((localIndex % GRID_COLUMNS) * cellWidthPx).roundToInt(), with(density) { rowTop(row).dp.toPx() }.roundToInt()),
                    label = "home insertion $id",
                )
                val visible = previewIndex in pageRange && renderIndex != hiddenIndex
                val opacity by animateFloatAsState(
                    if (dimDragged && id == draggedId) .28f else 1f,
                    label = "home insertion visibility $id",
                )
                Box(Modifier.offset { animatedOffset }.width(cellWidth).height(rowHeight.dp)
                    .alpha(opacity).testTag("home-app-$id"), contentAlignment = Alignment.TopCenter) {
                    if (visible) AppTile(app, iconSize, labels, shape = iconShape,
                        onClick = { onLaunch(app, it) }, onLongClick = { onActions(app) })
                }
            }
        }
        folders.forEach { folder ->
            val savedIndex = savedIndexOf(folder.id) ?: -1
            val previewIndex = previewIndexOf(folder.id) ?: -1
            val renderIndex = previewIndex.takeIf { it in pageRange } ?: savedIndex.takeIf { it in pageRange } ?: return@forEach
            val localIndex = renderIndex - pageStart
            val row = localIndex / GRID_COLUMNS
            val x = cellWidth * (localIndex % GRID_COLUMNS)
            val y = rowTop(row).dp
            FolderTile(folder, appsById, iconSize, labels, drag, page,
                Modifier.offset(x = x, y = y).width(cellWidth).height(rowHeight.dp)
                    .testTag("home-folder-${folder.id}"), iconShape = iconShape, onClick = { onFolder(folder.id) })
        }
        pageWidgets.forEach { placement ->
            key("widget-${placement.slot}") {
                val x = cellWidth * placement.column + 5.dp
                val width = (cellWidth * placement.spanX - 10.dp).coerceAtLeast(1.dp)
                val y = rowTop(placement.row)
                val height = (rowTop(placement.row + placement.spanY) - y - 18f).coerceAtLeast(48f)
                if (placement == pending) Surface(Modifier.offset(x = x, y = y.dp).width(width).height(height.dp)
                    .testTag("widget-pending-${placement.slot}").semantics(mergeDescendants = true) {
                        contentDescription = settingsContext.getString(R.string.settings_pending_widget, widgets.pendingProvider?.shortClassName ?: settingsContext.getString(R.string.settings_widget))
                    }, color = Glass.copy(alpha = .72f),
                    shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.height(8.dp)); Text(settingsContext.getString(R.string.settings_finish_widget), color = Ink)
                    }
                } else MovableWidget(placement.id, placement.slot, widgets, drag, target,
                    Modifier.offset(x = x, y = y.dp).width(width).height(height.dp), page = page) { onWidget(placement.slot) }
            }
        }
    }
}

@Composable
private fun DockAppColumn(
    savedDock: List<String?>,
    previewDock: List<String?>,
    appsById: Map<String, AppEntry>,
    rowHeight: Float,
    iconSize: Float,
    drag: HomeDragState,
    target: DropTarget?,
    iconShape: Shape = RoundedCornerShape(11.dp),
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onChoose: (Int) -> Unit,
) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    val draggedId = drag.source?.appId
    val dockTarget = (target as? DropTarget.Dock)?.index
    val source = drag.source?.target as? DropTarget.Dock
    val draggedPreviewIndex = previewDock.indexOf(draggedId)
    val hiddenIndex = when {
        !drag.active || !drag.moved -> null
        dockTarget != null -> draggedPreviewIndex.takeIf { it >= 0 }
        source != null && target !is DropTarget.Home -> draggedPreviewIndex.takeIf { it >= 0 }
        else -> null
    }
    val dimDragged = drag.active && !drag.moved && source != null
    val launchBounds = remember(savedDock.size) { List(savedDock.size) { android.graphics.Rect() } }
    val interactions = remember(savedDock.size) { List(savedDock.size) { MutableInteractionSource() } }
    val slotScales = savedDock.indices.map { index ->
        val pressed by interactions[index].collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) .92f else 1f, label = "dock press $index")
        scale
    }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.dp.toPx() }
    Box(Modifier.fillMaxWidth().height((rowHeight * savedDock.size).dp)) {
        savedDock.indices.forEach { index ->
            val cell = DropTarget.Dock(index)
            val savedApp = appsById[savedDock[index]]
            val previewId = previewDock.getOrNull(index)
            val highlighted = drag.active && target == cell
            val gap = hiddenIndex == index
            Box(Modifier.fillMaxWidth().height(rowHeight.dp).offset(y = (rowHeight * index).dp)
                .background(if (highlighted) Color.White.copy(alpha = .3f) else Color.Transparent, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center) {
                when {
                    gap -> Box(Modifier.size(iconSize.dp).testTag("drag-gap-dock-$index")
                        .background(Glass.copy(alpha = .16f), RoundedCornerShape(14.dp))
                        .border(2.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(14.dp)))
                    previewId == null -> Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(rowHeight.dp).offset(y = (rowHeight * index).dp)
                .testTag("dock-slot-$index").dropRegion(drag, cell, savedApp?.id)
                .semantics(mergeDescendants = true) { contentDescription = savedApp?.label ?: settingsContext.getString(R.string.settings_choose_dock_slot, index + 1) }
                .combinedClickable(interactionSource = interactions[index], indication = null, role = Role.Button, onClick = {
                    if (savedApp != null) onLaunch(savedApp, launchBounds[index]) else onChoose(index)
                }, onLongClick = null)
                .semantics { onLongClick(settingsContext.getString(R.string.settings_choose_dock)) { onChoose(index); true } })
        }

        val ids = (savedDock + previewDock).filterNotNull().distinct()
        ids.forEach { id ->
            val savedIndex = savedDock.indexOf(id)
            val previewIndex = previewDock.indexOf(id)
            val renderIndex = previewIndex.takeIf { it >= 0 } ?: savedIndex.takeIf { it >= 0 } ?: return@forEach
            val app = appsById[id] ?: return@forEach
            key(id) {
                val animatedOffset by animateIntOffsetAsState(
                    IntOffset(0, (renderIndex * rowHeightPx).roundToInt()), label = "dock insertion $id")
                val visible = previewIndex >= 0 && renderIndex != hiddenIndex
                val opacity by animateFloatAsState(
                    if (!visible) 0f else if (dimDragged && id == draggedId) .28f else 1f,
                    label = "dock insertion visibility $id",
                )
                Box(Modifier.offset { animatedOffset }.fillMaxWidth().height(rowHeight.dp).alpha(opacity)
                    .testTag("dock-app-$id"), contentAlignment = Alignment.Center) {
                    Image(app.imageBitmap, null, Modifier.size(iconSize.dp).testTag("dock-icon-$id")
                        .onGloballyPositioned { if (savedIndex >= 0) launchBounds[savedIndex].set(it.boundsInWindow().toAndroidBounds()) }
                        .graphicsLayer { scaleX = slotScales[renderIndex]; scaleY = slotScales[renderIndex] }
                        .clip(iconShape))
                }
            }
        }
    }
}

private fun <T> List<T>.slicePage(range: IntRange): List<T> =
    if (isEmpty() || range.first >= size) emptyList() else subList(range.first, minOf(range.last + 1, size))

@Composable
private fun FolderTile(folder: FolderEntry, apps: Map<String, AppEntry>, size: Float, labels: Boolean,
    drag: HomeDragState, page: Int, modifier: Modifier = Modifier, iconShape: Shape = RoundedCornerShape(6.dp), onClick: () -> Unit) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    Column(modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        .semantics(mergeDescendants = true) {
        contentDescription = settingsContext.resources.getQuantityString(R.plurals.settings_folder_apps, folder.appIds.size, folder.title, folder.appIds.size)
    }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * .24f).dp))
            .background(Glass.copy(alpha = .72f)).border(1.dp, Color.White.copy(alpha = .55f), RoundedCornerShape((size * .24f).dp))
            .dropRegion(drag, DropTarget.Folder(folder.id), page = page, folderId = folder.id)
            .testTag("folder-drop-${folder.id}")) {
            folder.appIds.take(4).forEachIndexed { index, id ->
                apps[id]?.let { app ->
                    Image(app.imageBitmap, null, Modifier.align(when (index) {
                        0 -> Alignment.TopStart; 1 -> Alignment.TopEnd; 2 -> Alignment.BottomStart; else -> Alignment.BottomEnd
                    }).padding(5.dp).size((size * .38f).dp).clip(iconShape))
                }
            }
        }
        if (labels) Text(folder.title, color = Color.White, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AppTile(app: AppEntry, size: Float, labels: Boolean, modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape((size * .24f).dp), onClick: (android.graphics.Rect) -> Unit,
    onLongClick: () -> Unit) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .92f else 1f, label = "app press")
    val iconSize by animateDpAsState(size.dp, label = "icon size")
    val bounds = remember { android.graphics.Rect() }
    Column(modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = app.label }
        .clickable(interactionSource = interaction, indication = null,
            role = Role.Button, onClick = { onClick(bounds) })
        .semantics { onLongClick(settingsContext.getString(R.string.settings_app_options)) { onLongClick(); true } }.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Image(app.imageBitmap, null, Modifier.size(iconSize).onGloballyPositioned { bounds.set(it.boundsInWindow().toAndroidBounds()) }
            .graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape))
        if (labels) Text(app.label, color = Color.White, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .55f), Offset(0f, 1f), 3f)), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        color = Glass.copy(alpha = .24f), shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .18f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween, content = content)
    }
}

@Composable
private fun currentTime(): LocalDateTime {
    val time by produceState(LocalDateTime.now()) { while (true) { value = LocalDateTime.now(); delay(1000) } }
    return time
}

@Composable
private fun ClockCard(onClick: () -> Unit) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    val time = currentTime()
    val format = if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm"
    GlassCard(onClick = onClick) {
        Icon(Icons.Rounded.Schedule, settingsContext.getString(R.string.settings_clock_replace), tint = Color.White, modifier = Modifier.size(20.dp))
        Text(time.format(DateTimeFormatter.ofPattern(format)), color = Color.White, fontWeight = FontWeight.Light, fontSize = 30.sp, maxLines = 1)
        Text(settingsContext.getString(R.string.settings_local_time), color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
    }
}

@Composable
private fun DateCard(onClick: () -> Unit) {
    val date = currentTime()
    GlassCard(onClick = onClick) {
        Text(date.format(DateTimeFormatter.ofPattern("EEEE")), color = Color.White, fontSize = 12.sp, maxLines = 1)
        Text(date.dayOfMonth.toString(), color = Color.White, fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 42.sp)
        Text(date.format(DateTimeFormatter.ofPattern("MMMM")), color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
    }
}

@Composable
private fun ExpandedCard(onClick: () -> Unit) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    val date = currentTime()
    GlassCard(onClick = onClick) {
        Column {
            Text(date.format(DateTimeFormatter.ofPattern("EEEE")), color = Color.White, fontSize = 22.sp)
            Text(date.format(DateTimeFormatter.ofPattern("MMMM d")), color = Color.White.copy(alpha = .8f), fontSize = 16.sp)
        }
        Column {
            Icon(Icons.Rounded.Widgets, null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(16.dp))
            Text(settingsContext.getString(R.string.settings_more_room), color = Color.White, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(12.dp))
            Text(settingsContext.getString(R.string.settings_more_widgets), color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onClick) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(settingsContext.getString(R.string.settings_add_widget)) }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun WidgetSlot(id: Int, slot: Int, controller: WidgetController, modifier: Modifier, onAdd: () -> Unit, fallback: @Composable () -> Unit) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    var restoreMessage by remember(slot) { mutableStateOf<String?>(null) }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(24.dp)).testTag("widget-slot-$slot")) {
        val displayedContentSize = WidgetContentSize(maxWidth.value, maxHeight.value)
        if (id == NEEDS_BINDING_WIDGET) {
            val restore = controller.restoreDescriptor(slot)
            Surface(Modifier.fillMaxSize().testTag("widget-restore-$slot"), color = Glass.copy(alpha = .88f),
                shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = .7f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(restore?.title ?: settingsContext.getString(R.string.settings_saved_widget), color = Ink, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Text(restore?.profileLabel ?: settingsContext.getString(R.string.settings_unavailable_profile), color = Ink.copy(alpha = .72f),
                        style = MaterialTheme.typography.bodySmall)
                    restoreMessage?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                    Row {
                        TextButton(onClick = {
                            if (!controller.rebindRestoredWidget(slot, contentSize = displayedContentSize))
                                restoreMessage = settingsContext.getString(R.string.settings_provider_unavailable)
                        },
                            modifier = Modifier.testTag("widget-restore-reconnect-$slot")) { Text(settingsContext.getString(R.string.settings_reconnect)) }
                        TextButton(onClick = onAdd, modifier = Modifier.testTag("widget-restore-replace-$slot")) { Text(settingsContext.getString(R.string.settings_replace)) }
                    }
                }
            }
            return@BoxWithConstraints
        }
        val info = remember(id) { if (id >= 0) controller.manager.getAppWidgetInfo(id) else null }
        if (info == null) fallback()
        else {
            key(id) {
                AndroidView(factory = { context -> controller.host.createView(context, id, info) },
                    modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private fun widgetLabel(settingsContext: android.content.Context, id: Int, controller: WidgetController) = when (id) {
    CLOCK_WIDGET -> settingsContext.getString(R.string.settings_clock)
    DATE_WIDGET -> settingsContext.getString(R.string.settings_date)
    INFO_WIDGET -> settingsContext.getString(R.string.settings_widget_panel)
    EMPTY_WIDGET -> settingsContext.getString(R.string.settings_add_widget)
    else -> controller.label(id)
}

@Composable
private fun MovableWidget(id: Int, slot: Int, controller: WidgetController, drag: HomeDragState,
    target: DropTarget?, modifier: Modifier, page: Int, onAdd: () -> Unit) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    val cell = DropTarget.Widget(slot)
    WidgetSlot(id, slot, controller, modifier.dropRegion(drag, cell, page = page, widgetId = id)
        .alpha(if (drag.source?.target == cell) .3f else 1f)
        .border(if (drag.active && target == cell) 2.dp else 0.dp,
            if (drag.active && target == cell) Color.White else Color.Transparent, RoundedCornerShape(24.dp))
        .semantics { onLongClick(settingsContext.getString(R.string.settings_move_replace_widget)) { onAdd(); true } }, onAdd) {
        when (id) {
            CLOCK_WIDGET -> ClockCard(onAdd)
            DATE_WIDGET -> DateCard(onAdd)
            INFO_WIDGET -> if (slot % 3 == 2) ExpandedCard(onAdd) else GlassCard(onClick = onAdd) {
                Icon(Icons.Rounded.Widgets, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Text(settingsContext.getString(R.string.settings_your_widgets), color = Color.White, fontSize = 15.sp, maxLines = 1)
                Text(settingsContext.getString(R.string.settings_tap_choose), color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
            }
            else -> Surface(Modifier.fillMaxSize().clickable(onClick = onAdd), color = Glass.copy(alpha = .18f),
                shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .25f))) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White)
                    Text(if (id >= 0) settingsContext.getString(R.string.settings_widget_unavailable) else settingsContext.getString(R.string.settings_add_widget), color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AppPicker(apps: List<AppEntry>, dockSlot: Int?, onSelect: (AppEntry) -> Unit, onClear: () -> Unit,
    onLongClick: (AppEntry) -> Unit, canSelect: (AppEntry) -> Boolean = { true }, blockedHint: String? = null) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal = 20.dp).imePadding()) {
        Text(if (dockSlot == null) settingsContext.getString(R.string.settings_your_apps) else settingsContext.getString(R.string.settings_dock_slot, dockSlot + 1), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("search-field"),
            placeholder = { Text(settingsContext.getString(R.string.settings_search_apps)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true,
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, settingsContext.getString(R.string.settings_clear_search)) } }, shape = RoundedCornerShape(20.dp))
        if (dockSlot != null) TextButton(onClick = onClear) { Text(settingsContext.getString(R.string.settings_leave_empty)) }
        if (blockedHint != null) Text(blockedHint, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp).testTag("dock-full-guidance"))
        LazyColumn(Modifier.weight(1f)) {
            if (filtered.isEmpty()) item { Text(settingsContext.getString(R.string.settings_no_apps), Modifier.padding(vertical = 24.dp)) }
            items(filtered, key = { it.id }) { app ->
                val enabled = canSelect(app)
                Row(Modifier.fillMaxWidth().testTag("picker-app-${app.id}")
                    .combinedClickable(enabled = enabled, onClick = { onSelect(app) }, onLongClick = { onLongClick(app) })
                    .alpha(if (enabled) 1f else .45f)
                    .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(app.imageBitmap, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                    Text(app.label, Modifier.padding(start = 16.dp).weight(1f), maxLines = 2)
                    if (dockSlot != null && enabled) Icon(Icons.Rounded.Add, settingsContext.getString(R.string.settings_choose_app, app.label))
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(state: LauncherState, initiallyWide: Boolean, model: LauncherModel, isDefaultHome: Boolean,
    onMakeDefault: () -> Unit, onClose: () -> Unit, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit, onWallpaperPreview: () -> Unit,
    onExportLayout: () -> Unit, onImportLayout: () -> Unit,
    appearance: AppearanceState, onAppearanceMode: (AppearanceMode) -> Unit,
    onAppearanceManual: (String, Double, Double) -> Unit, onAppearanceDeviceLocation: () -> Unit,
    onAppearanceClear: () -> Unit,
    backgrounds: LauncherBackgroundController,
    homePage: Int = 0) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    var wide by rememberSaveable { mutableStateOf(initiallyWide) }
    val p = if (wide) state.expanded else state.compact
    Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(settingsContext.getString(R.string.settings_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, settingsContext.getString(R.string.settings_close)) }
        }
        Button(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth().testTag("default-home-settings")) {
            Text(if (isDefaultHome) settingsContext.getString(R.string.settings_change_home) else settingsContext.getString(R.string.settings_make_default))
        }
        TextButton(onClick = onEditPins, modifier = Modifier.fillMaxWidth()) { Text(settingsContext.getString(R.string.settings_choose_home_apps_lower)) }
        if (state.canUndoEdit) TextButton(onClick = { model.undoEdit(); onClose() }, modifier = Modifier.fillMaxWidth()) {
            Text(settingsContext.getString(R.string.settings_undo))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!wide, { wide = false }, label = { Text(settingsContext.getString(R.string.settings_cover_compact)) })
            FilterChip(wide, { wide = true }, label = { Text(settingsContext.getString(R.string.settings_inner_expanded)) })
        }
        SettingSlider(settingsContext.getString(R.string.settings_icon_size), settingsContext.getString(R.string.settings_dimension_dp, p.iconSize.toInt()), p.iconSize, 40f..68f) { model.setPreset(wide, p.copy(iconSize = it)) }
        SettingSlider(settingsContext.getString(R.string.settings_row_spacing), settingsContext.getString(R.string.settings_dimension_dp, p.rowGap.toInt()), p.rowGap, 0f..28f) { model.setPreset(wide, p.copy(rowGap = it)) }
        SettingSlider(settingsContext.getString(R.string.settings_dock_width), settingsContext.getString(R.string.settings_dimension_dp, p.dockWidth.toInt()), p.dockWidth, 56f..84f) { model.setPreset(wide, p.copy(dockWidth = it)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(settingsContext.getString(R.string.settings_dock_align), Modifier.weight(1f))
            Switch(p.dockAlignToGrid, { model.setPreset(wide, p.copy(dockAlignToGrid = it)) })
        }
        if (!p.dockAlignToGrid) SettingSlider(settingsContext.getString(R.string.settings_dock_position), settingsContext.getString(R.string.settings_percent, (p.dockPosition * 100).toInt()), p.dockPosition, .25f.. .75f) { model.setPreset(wide, p.copy(dockPosition = it)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(settingsContext.getString(R.string.settings_labels_enable), Modifier.weight(1f)); Switch(state.labels, model::setLabels, Modifier.testTag("label-switch"))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(settingsContext.getString(R.string.settings_status_upper_right), Modifier.weight(1f)); Switch(state.verticalStatus, model::setVerticalStatus, Modifier.testTag("status-switch"))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(settingsContext.getString(R.string.settings_google_search), Modifier.weight(1f))
            Switch(state.googleSearch, model::setGoogleSearch, Modifier.testTag("google-search-switch"))
        }
        Text(settingsContext.getString(R.string.settings_google_search_help),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { model.setPreset(wide, LayoutPreset()) }) { Text(settingsContext.getString(R.string.settings_reset_layout)) }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        TextButton(onClick = onWallpaperPreview, modifier = Modifier.fillMaxWidth().testTag("wallpaper-preview")) {
            Icon(Icons.Rounded.Wallpaper, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(settingsContext.getString(R.string.settings_choose_wallpaper))
        }
        Text(settingsContext.getString(R.string.settings_wallpaper_apps_help), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(settingsContext.getString(R.string.settings_launcher_background), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Button(onClick = backgrounds::choosePhoto, enabled = !backgrounds.loading,
            modifier = Modifier.fillMaxWidth().testTag("background-choose")) { Text(settingsContext.getString(R.string.settings_choose_background_photo)) }
        if (!backgrounds.previewPending) LauncherBackgroundButtons(backgrounds)
        if (backgrounds.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("background-loading"))
        (backgrounds.errorMessage ?: backgrounds.successMessage)?.let { message ->
            TextButton(onClick = backgrounds::clearMessage, Modifier.fillMaxWidth().testTag("background-message")) { Text(message) }
        }
        Text(settingsContext.getString(R.string.settings_photo_private_help),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        AppearanceSettings(appearance, onAppearanceMode, onAppearanceManual, onAppearanceDeviceLocation, onAppearanceClear)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(settingsContext.getString(R.string.settings_layout_backup), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExportLayout, modifier = Modifier.weight(1f).testTag("layout-export")) { Text(settingsContext.getString(R.string.settings_save)) }
            OutlinedButton(onClick = onImportLayout, modifier = Modifier.weight(1f).testTag("layout-import")) { Text(settingsContext.getString(R.string.settings_restore)) }
        }
        Text(settingsContext.getString(R.string.settings_restore_review_always), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(settingsContext.getString(R.string.settings_widget_page, homePage + 1), style = MaterialTheme.typography.titleMedium)
        state.widgetPlacements.filter { it.page == homePage }.forEach { placement ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(settingsContext.getString(R.string.settings_widget_placement, placement.spanX, placement.spanY, placement.row + 1), Modifier.weight(1f))
                IconButton(onClick = { onRemoveWidget(placement.slot) },
                    modifier = Modifier.semantics { contentDescription = settingsContext.getString(R.string.settings_remove_widget) }) {
                    Icon(Icons.Rounded.DeleteOutline, null)
                }
                TextButton(onClick = { onWidget(placement.slot) }) { Text(settingsContext.getString(R.string.settings_replace)) }
            }
        }
        if (wide) state.widgetPlacements.filter { it.page == -1 }.forEach { placement ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(settingsContext.getString(R.string.settings_unfolded_page), Modifier.weight(1f))
                IconButton(onClick = { onRemoveWidget(placement.slot) },
                    modifier = Modifier.semantics { contentDescription = settingsContext.getString(R.string.settings_remove_unfolded_widget) }) {
                    Icon(Icons.Rounded.DeleteOutline, null)
                }
                TextButton(onClick = { onWidget(placement.slot) }) { Text(settingsContext.getString(R.string.settings_replace)) }
            }
        }
        TextButton(onClick = { onAddWidget(homePage) }, Modifier.fillMaxWidth()) { Text(settingsContext.getString(R.string.settings_add_widget_page)) }
        Text(settingsContext.getString(R.string.settings_drag_help), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp))
        }
    }
}

@Composable
private fun SettingSlider(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Row { Text(label, Modifier.weight(1f)); Text(valueLabel, color = MaterialTheme.colorScheme.secondary) }
        Slider(value, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label })
    }
}

