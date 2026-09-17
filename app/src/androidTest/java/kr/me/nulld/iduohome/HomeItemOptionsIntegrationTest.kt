package kr.me.nulld.iduohome

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Uses built-in widgets and installed app IDs, without depending on a particular device catalog. */
class HomeItemOptionsIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun root() = compose.onNodeWithTag("launcher-root")
    private fun controller() = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
        .get(compose.activity) as WidgetController
    private fun point(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center -
        root().fetchSemanticsNode().boundsInRoot.topLeft
    private fun ready() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        compose.waitUntil(15_000) { !model().state.value.loading }
        if (compose.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("Not now").performClick()
        compose.waitForIdle()
    }
    private fun arrange(widget: WidgetPlacement? = null): Pair<HomeLayout, List<String>> {
        ready()
        val before = model().state.value.layout
        val ids = model().state.value.apps.take(2).map { it.id }
        compose.runOnIdle {
            model().restoreLayout(HomeLayout(List(10) { when (it) { 8 -> ids[0]; 9 -> ids[1]; else -> null } },
                List(4) { null }, listOfNotNull(widget)))
        }
        compose.waitForIdle()
        return before to ids
    }

    @Test fun widgetPopupAnchorsBelowAndDragResizeCommitsWithoutApply() {
        val widget = WidgetPlacement(0, CLOCK_WIDGET, 0, 0, 0, 2, 2)
        val (before, _) = arrange(widget)
        try {
            compose.onNodeWithTag("widget-slot-0").performTouchInput { longClick() }
            val popup = compose.onNodeWithTag("widget-options-popup-0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val bounds = compose.onNodeWithTag("widget-slot-0").fetchSemanticsNode().boundsInRoot
            assertTrue("Menu must be below the selected widget: $popup / $bounds", popup.top >= bounds.bottom)
            val frame = compose.onNodeWithTag("widget-resize-preview-0").fetchSemanticsNode().boundsInRoot
            assertEquals(bounds.top, frame.top, 2f)
            assertEquals(bounds.left, frame.left, 2f)
            assertEquals(bounds.bottom, frame.bottom, 2f)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /data/local/tmp/iduohome-widget-popup.png")).use { it.readBytes() }
            compose.onNodeWithTag("widget-resize-preview-0").assertIsDisplayed()
            val handle = "widget-resize-handle-0-1-0"
            val start = point(handle)
            val pitch = bounds.width / 2f
            root().performTouchInput { down(start); moveTo(start + Offset(pitch * 1.2f, 0f), 400); up() }
            compose.waitForIdle()
            assertEquals(3, model().placement(0)?.spanX)
            compose.onNodeWithTag("widget-options-popup-0").assertDoesNotExist()
            pressBack()
            compose.activityRule.scenario.recreate(); ready()
            assertEquals(3, model().placement(0)?.spanX)
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun resizeCollisionAndGestureCancellationPreserveLayout() {
        val widget = WidgetPlacement(0, CLOCK_WIDGET, 0, 0, 0, 2, 2)
        val (before, _) = arrange(widget)
        try {
            val arranged = model().state.value.layout
            compose.onNodeWithTag("widget-slot-0").performTouchInput { longClick() }
            val start = point("widget-resize-handle-0-0-1")
            root().performTouchInput { down(start); moveTo(start + Offset(0f, 180f), 400); up() }
            compose.waitForIdle()
            assertEquals(arranged, model().state.value.layout)
            val horizontal = point("widget-resize-handle-0-1-0")
            root().performTouchInput { down(horizontal); moveTo(horizontal + Offset(200f, 0f), 400); cancel() }
            compose.waitForIdle()
            assertEquals(arranged, model().state.value.layout)
            pressBack()
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun appPopupSelectRemoveAndUndoKeepInstalledApp() {
        val (before, ids) = arrange()
        try {
            val arranged = model().state.value.layout
            compose.onNodeWithTag("home-cell-8").performTouchInput { longClick() }
            compose.onNodeWithTag("app-options-popup").assertIsDisplayed()
            compose.onNodeWithTag("home-item-select").performClick()
            compose.onNodeWithTag("home-selection").assertIsDisplayed()
            compose.onNodeWithTag("home-selection-remove").performClick()
            assertNull(model().state.value.layout.indexOfShortcut(ids[0]))
            assertTrue(model().state.value.apps.any { it.id == ids[0] })
            compose.runOnIdle { assertTrue(model().undoEdit()) }
            assertEquals(arranged, model().state.value.layout)
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun quickDropReordersButHoldingOverAppCreatesFolder() {
        val (before, ids) = arrange()
        try {
            val arranged = model().state.value.layout
            var start = point("home-cell-8"); var end = point("home-cell-9")
            root().performTouchInput { down(start); advanceEventTime(700); moveTo(end, 250); up() }
            compose.waitForIdle()
            assertEquals(ids[0], model().state.value.layout.slotAt(9))
            assertTrue(model().state.value.folders.isEmpty())
            compose.runOnIdle { model().restoreLayout(arranged) }
            compose.waitForIdle()
            start = point("home-cell-8"); end = point("home-cell-9")
            root().performTouchInput { down(start); advanceEventTime(700); moveTo(end, 250) }
            compose.waitUntil(4000) { compose.onAllNodesWithTag("folder-drop-preview").fetchSemanticsNodes().isNotEmpty() }
            root().performTouchInput { up() }
            compose.waitForIdle()
            val folder = model().state.value.folders.single()
            assertEquals(ids.toSet(), folder.appIds.toSet())
            assertEquals(folder.id, model().state.value.layout.slotAt(9))
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun movingWidgetSwapsThenDisplacesAppsAndUndoRestoresEverything() {
        val first = WidgetPlacement(0, CLOCK_WIDGET, 0, 0, 0, 2, 2)
        val second = WidgetPlacement(1, DATE_WIDGET, 0, 0, 3, 2, 2)
        val (before, ids) = arrange(first)
        fun moveTo(index: Int) {
            val origin = root().fetchSemanticsNode().boundsInRoot.topLeft
            val start = compose.onNodeWithTag("widget-slot-0").fetchSemanticsNode().boundsInRoot.topLeft - origin + Offset(18f, 18f)
            val end = compose.onNodeWithTag("home-cell-$index").fetchSemanticsNode().boundsInRoot.topLeft - origin + Offset(18f, 18f)
            root().performTouchInput { down(start); advanceEventTime(700); moveTo(end, 350); up() }
            compose.waitForIdle()
        }
        try {
            compose.runOnIdle { assertTrue(model().placeWidget(second)) }
            compose.waitForIdle()
            val arranged = model().state.value.layout
            moveTo(12)
            assertEquals(3, model().placement(0)?.row)
            assertEquals(0, model().placement(1)?.row)
            compose.runOnIdle { assertTrue(model().undoEdit()) }
            assertEquals(arranged, model().state.value.layout)
            compose.runOnIdle { model().removePlacement(DropTarget.Widget(1)) }
            compose.waitForIdle()
            val beforeDisplace = model().state.value.layout
            moveTo(8)
            assertEquals(2, model().placement(0)?.row)
            assertEquals(ids[0], model().state.value.layout.slotAt(0))
            assertEquals(ids[1], model().state.value.layout.slotAt(1))
            compose.runOnIdle { assertTrue(model().undoEdit()) }
            assertEquals(beforeDisplace, model().state.value.layout)
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun nativeWidgetResizesAndSettingsRetainBinding() {
        val (before, _) = arrange()
        val widgets = controller()
        val provider = widgets.personalProviders().first { it.provider.className.endsWith("OptionalConfigWidgetProvider") }
        val id = widgets.host.allocateAppWidgetId()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
            assertTrue(widgets.manager.bindAppWidgetIdIfAllowed(id, provider.provider))
            automation.dropShellPermissionIdentity()
            compose.runOnIdle { assertTrue(model().placeWidget(WidgetPlacement(0, id, 0, 0, 0, 2, 2))) }
            compose.waitForIdle()
            compose.onNodeWithTag("widget-slot-0").performTouchInput { longClick() }
            compose.onNodeWithTag("home-item-settings").assertIsEnabled()
            val beforeWidth = widgets.manager.getAppWidgetOptions(id).getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val start = point("widget-resize-handle-0-1-0")
            val width = compose.onNodeWithTag("widget-slot-0").fetchSemanticsNode().boundsInRoot.width
            root().performTouchInput { down(start); moveTo(start + Offset(width * .65f, 0f), 400); up() }
            compose.waitForIdle()
            assertEquals(3, model().placement(0)?.spanX)
            assertTrue(widgets.manager.getAppWidgetOptions(id).getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) > beforeWidth)
            pressBack()
            compose.waitForIdle()
            compose.onNodeWithTag("widget-resize-preview-0").assertDoesNotExist()
            compose.onNodeWithTag("widget-slot-0").performTouchInput { longClick() }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("widget-options-popup-0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("home-item-settings").performClick()
            compose.waitUntil(5000) { widgets.reconfigureWidgetId == id }
            val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.text("Configure fixture widget")), 5000))
            device.pressBack()
            compose.waitUntil(5000) { widgets.reconfigureWidgetId == null }
            assertEquals(id, model().placement(0)?.id)
            assertEquals(provider.provider, widgets.manager.getAppWidgetInfo(id)?.provider)
        } finally {
            automation.dropShellPermissionIdentity()
            compose.runOnIdle { model().restoreLayout(before) }
            widgets.host.deleteAppWidgetId(id)
        }
    }
}
