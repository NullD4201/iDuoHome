package kr.me.nulld.iduohome

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CustomizationNavigationIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun ready() {
        compose.waitUntil(15_000) { !model().state.value.loading }
        if (compose.onAllNodesWithTag("setup-skip").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("setup-skip").performClick()
        }
        compose.onNodeWithContentDescription("Home page 1").performClick()
        compose.waitForIdle()
    }

    @Test fun discoverSwitchPreservesHomePageAndPersistsAcrossRecreation() {
        ready()
        val before = model().state.value
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        fun assertPage(value: String) = compose.onNodeWithTag("app-pager").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, value))
        fun openGestures(page: Int) {
            compose.onNodeWithTag("home-page-$page").performSemanticsAction(SemanticsActions.OnLongClick)
            compose.onNodeWithTag("empty-space-customize").performClick()
            compose.onNodeWithTag("customization-gestures").performScrollTo().performClick()
        }
        fun closeSettings() {
            repeat(2) { device.pressBack(); compose.waitForIdle() }
        }
        try {
            compose.runOnIdle {
                model().setDiscoverEnabled(true)
                model().applyDrop(before.apps.first { it.id !in before.dock }.id, DropTarget.Home(HOME_CELLS))
            }
            compose.onNodeWithContentDescription("Home page 2").performClick()
            compose.waitForIdle()
            val pages = model().state.value.homePages
            openGestures(1)
            compose.onNodeWithTag("discover-switch").assertIsOn().performClick().assertIsOff()
            assertPage("Home page 2 of $pages")
            closeSettings()
            compose.onNodeWithTag("discover-page-link").assertDoesNotExist()
            compose.onNodeWithTag("discover-page").assertDoesNotExist()
            repeat(2) { compose.onNodeWithTag("app-pager").performTouchInput { swipeRight() }; compose.waitForIdle() }
            assertPage("Home page 1 of $pages")

            compose.activityRule.scenario.recreate()
            ready()
            org.junit.Assert.assertFalse(model().state.value.discoverEnabled)
            org.junit.Assert.assertFalse(org.json.JSONObject(compose.activity.getSharedPreferences("launcher", 0)
                .getString("state", null)!!).getBoolean("discoverEnabled"))
            compose.onNodeWithTag("discover-page-link").assertDoesNotExist()
            openGestures(0)
            compose.onNodeWithTag("discover-switch").assertIsOff().performClick().assertIsOn()
            closeSettings()
            assertPage("Home page 1 of $pages")
            compose.onNodeWithTag("discover-page-link").performClick()
            compose.waitForIdle()
            assertPage("Discover")
            compose.runOnIdle { model().setDiscoverEnabled(false) }
            assertPage("Home page 1 of $pages")
        } finally {
            compose.runOnIdle {
                model().restoreLayout(before.layout)
                model().setDiscoverEnabled(before.discoverEnabled)
            }
        }
    }

    @Test fun wallpaperOutsideCellsOpensCustomizationWithEitherLockSetting() {
        ready()
        val before = model().state.value.layout
        val lockBefore = model().state.value.doubleTapToLock
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            for (lockEnabled in listOf(false, true)) {
                compose.runOnIdle { model().setDoubleTapToLock(lockEnabled) }
                val pane = compose.onNodeWithTag("home-page-0").fetchSemanticsNode().boundsInRoot
                val pager = compose.onNodeWithTag("app-pager").fetchSemanticsNode().boundsInRoot
                // Above the first row and below the pane, away from the page controls.
                for (point in listOf(Offset(pane.center.x, pane.top + 8f), Offset(pager.left + 24f, pager.bottom - 12f))) {
                    compose.onNodeWithTag("launcher-root").performTouchInput {
                        down(point); advanceEventTime(750); up()
                    }
                    compose.onNodeWithTag("empty-space-customize").performClick()
                    compose.onNodeWithTag("customization-home").assertIsDisplayed()
                    device.pressBack()
                    compose.waitForIdle()
                }
            }
            assertEquals(before, model().state.value.layout)
        } finally {
            compose.runOnIdle { model().setDoubleTapToLock(lockBefore) }
        }
    }


    @Test fun systemBackReturnsFromSubpageToOverviewThenHome() {
        ready()
        compose.onNodeWithTag("settings").assertDoesNotExist()
        compose.openHomeCustomization()
        compose.onNodeWithTag("customization-home").performClick()
        compose.onNodeWithTag("customization-back").assertExists()

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("customization-wallpaper").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("customization-wallpaper").assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("search").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("customization-wallpaper").assertDoesNotExist()
    }

    @Test fun emptySpaceWallpaperOpensPhotoControlsWithoutChangingLayoutOrBindings() {
        ready()
        val before = model().state.value.layout
        val idsBefore = model().state.value.widgetPlacements.map { it.slot to it.id }
        val blocked = before.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }
        val empty = (0 until HOME_CELLS).first { before.slotAt(it) == null && it !in blocked }

        compose.onNodeWithTag("home-cell-$empty").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithTag("empty-space-wallpaper").performClick()
        compose.onNodeWithTag("background-choose").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("wallpaper-preview").assertExists()
        assertEquals(before, model().state.value.layout)
        assertEquals(idsBefore, model().state.value.widgetPlacements.map { it.slot to it.id })
    }

    @Test fun appSelectionBackDoesNotChangePlacement() {
        ready()
        val before = model().state.value.layout
        val appId = (0 until HOME_CELLS).mapNotNull(before::slotAt)
            .first { id -> model().state.value.apps.any { it.id == id } }
        val index = requireNotNull(before.indexOfShortcut(appId))

        val start = compose.onNodeWithTag("home-cell-$index").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("launcher-root").performTouchInput { down(start); advanceEventTime(700); up() }
        compose.onNodeWithTag("home-item-select").performClick()
        compose.onNodeWithTag("home-selection").assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("home-selection").assertDoesNotExist()
        assertEquals(before, model().state.value.layout)
    }

    @Test fun helpIsReachableFromPackedPageEntryAndBackReturnsToCustomization() {
        ready()
        val before = model().state.value.layout
        compose.openHomeCustomization()
        compose.onNodeWithTag("customization-help").performScrollTo().performClick()
        compose.onNodeWithText("Customize any page").assertIsDisplayed()
        compose.onNodeWithTag("help-home-settings").assertExists()
        compose.onNodeWithTag("help-add-widget").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("help-shade-setup").performScrollTo().assertIsDisplayed()

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("customization-help")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        assertEquals(before, model().state.value.layout)
    }
}
