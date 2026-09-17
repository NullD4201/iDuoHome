package kr.me.nulld.iduohome

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppLibraryActionsIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun root() = compose.onNodeWithTag("launcher-root")
    private fun point(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center -
        root().fetchSemanticsNode().boundsInRoot.topLeft
    private fun page() = compose.onNodeWithTag("app-pager").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
    private fun ready() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        compose.waitUntil(15_000) { !model().state.value.loading }
        if (compose.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("Not now").performClick()
        compose.waitForIdle()
    }
    private fun library(app: AppEntry) {
        compose.onNodeWithTag("library-page-link").performClick()
        compose.onNodeWithTag("library-search").performTextReplacement(app.label)
        compose.runOnIdle {
            WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(10_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true
        }
        compose.onNodeWithTag("all-apps-list").performScrollToNode(hasTestTag("library-app-${app.id}"))
        compose.waitForIdle()
    }
    private fun hold(app: AppEntry): Offset {
        val start = point("library-app-${app.id}")
        root().performTouchInput { down(start); advanceEventTime(700); moveTo(start + Offset(1f, 0f)) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("library-app-options-popup").fetchSemanticsNodes().isNotEmpty() }
        return start
    }

    @Test fun holdShowsTwoActionsWithoutLeavingLibraryAndAddIsIdempotent() {
        ready()
        val before = model().state.value.layout
        val app = model().state.value.apps.first()
        try {
            compose.runOnIdle { model().restoreLayout(HomeLayout(emptyList(), List(4) { null }, emptyList())) }
            library(app)
            val arranged = model().state.value.layout
            hold(app)
            assertEquals("All apps", page())
            compose.onNodeWithTag("library-home-add").assertIsDisplayed()
            compose.onNodeWithTag("library-uninstall").assertIsDisplayed()
            compose.onNodeWithTag("home-item-remove").assertDoesNotExist()
            compose.onNodeWithTag("drag-ghost").assertDoesNotExist()
            android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /data/local/tmp/iduohome-library-popup.png")).use { it.readBytes() }
            assertEquals(arranged, model().state.value.layout)
            root().performTouchInput { up() }
            compose.waitForIdle()
            compose.onNodeWithTag("library-home-add").performClick()
            compose.waitForIdle()
            assertEquals(app.id, model().state.value.layout.slotAt(0))
            assertTrue(page().startsWith("Home page"))
            val added = model().state.value.layout
            library(app)
            compose.onNodeWithTag("library-app-${app.id}").performSemanticsAction(SemanticsActions.OnLongClick)
            compose.onNodeWithTag("library-home-add").performClick()
            compose.waitForIdle()
            assertEquals(added, model().state.value.layout)
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun splitLibraryKeepsHomeAppsAndWidgetsInteractive() {
        ready()
        org.junit.Assume.assumeTrue(compose.activity.resources.configuration.screenWidthDp >= 650)
        val before = model().state.value.layout
        val app = model().state.value.apps.first()
        try {
            compose.runOnIdle {
                model().restoreLayout(HomeLayout(List(9) { if (it == 8) app.id else null }, List(4) { null },
                    listOf(WidgetPlacement(0, CLOCK_WIDGET, 0, 0, 0, 2, 2))))
            }
            compose.onNodeWithContentDescription("Home page 1").performClick()
            compose.waitForIdle()
            val original = compose.onNodeWithTag("home-page-0").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("library-page-link").performClick()
            compose.waitForIdle()
            val home = compose.onNodeWithTag("home-page-0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val library = compose.onNodeWithTag("library-page").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("app-pager").fetchSemanticsNode().boundsInRoot
            assertEquals(original.width, home.width, 2f)
            assertEquals(original.top, home.top, 2f)
            assertTrue(home.right <= library.left)
            assertTrue(library.width < viewport.width * .55f)
            assertEquals(viewport.right, library.right, 2f)
            compose.onNodeWithTag("widget-slot-0").assertIsDisplayed()
            android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /data/local/tmp/iduohome-split-library.png")).use { it.readBytes() }

            compose.onNodeWithTag("home-cell-8").performTouchInput { longClick() }
            compose.waitForIdle()
            assertEquals("All apps", page())
            compose.onNodeWithTag("app-options-popup").assertIsDisplayed()
            compose.onNodeWithTag("home-item-remove").assertIsDisplayed()
            compose.onNodeWithTag("library-uninstall").assertDoesNotExist()
            androidx.test.espresso.Espresso.pressBack()
            compose.waitForIdle()
            compose.onNodeWithTag("widget-slot-0").performTouchInput { longClick() }
            compose.waitForIdle()
            assertEquals("All apps", page())
            compose.onNodeWithTag("widget-options-popup-0").assertIsDisplayed()
            compose.onNodeWithTag("widget-resize-preview-0").assertIsDisplayed()
            androidx.test.espresso.Espresso.pressBack()
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun heldDragContinuesAcrossLibraryDisposalAndPersistsExactHomeCell() {
        ready()
        val before = model().state.value.layout
        val app = model().state.value.apps.first()
        try {
            compose.runOnIdle { model().restoreLayout(HomeLayout(emptyList(), List(4) { null }, emptyList())) }
            library(app)
            val start = hold(app)
            root().performTouchInput { moveTo(start + Offset(0f, 90f), 250) }
            compose.waitUntil(5000) { page().startsWith("Home page") }
            compose.waitForIdle()
            compose.onNodeWithTag("library-app-options-popup").assertDoesNotExist()
            compose.onNodeWithTag("drag-ghost").assertIsDisplayed()
            val end = point("home-cell-27")
            root().performTouchInput { moveTo(end, 350); up() }
            compose.waitForIdle()
            assertEquals(app.id, model().state.value.layout.slotAt(27))
            assertEquals(1, model().state.value.homeSlots.count { it == app.id })
            compose.activityRule.scenario.recreate(); ready()
            assertEquals(app.id, model().state.value.layout.slotAt(27))
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun invalidDropReturnsToFilteredLibraryWithoutChangingLayout() {
        ready()
        val before = model().state.value.layout
        val app = model().state.value.apps.first()
        library(app)
        val start = hold(app)
        root().performTouchInput { moveTo(start + Offset(0f, 90f), 250) }
        compose.waitUntil(5000) { page().startsWith("Home page") }
        root().performTouchInput { moveTo(Offset(1f, 1f), 300); up() }
        compose.waitForIdle()
        assertEquals(before, model().state.value.layout)
        assertEquals("All apps", page())
        compose.onNodeWithTag("library-search").assertTextContains(app.label)
        compose.onNodeWithTag("library-app-options-popup").assertDoesNotExist()
        compose.onNodeWithTag("drag-ghost").assertDoesNotExist()
    }

    @Test fun uninstallOpensSystemConfirmationAndCancelKeepsAppAndLayout() {
        ready()
        val before = model().state.value.layout
        val app = model().state.value.apps.first { it.packageName == "kr.me.nulld.iduohome.test" }
        library(app)
        compose.onNodeWithTag("library-app-${app.id}").performTouchInput { longClick() }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("library-uninstall").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("library-uninstall").performClick()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            assertTrue(device.wait(Until.hasObject(By.text(java.util.regex.Pattern.compile(".*[Uu]ninstall.*"))), 5000))
            assertNotEquals(compose.activity.packageName, device.currentPackageName)
        } finally { device.pressBack() }
        compose.waitForIdle()
        assertTrue(model().state.value.apps.any { it.id == app.id })
        assertEquals(before, model().state.value.layout)
        assertEquals("All apps", page())
    }
}
