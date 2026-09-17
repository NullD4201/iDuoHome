package kr.me.nulld.iduohome

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CoverSpacingIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }
    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun ready() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        compose.waitUntil(15_000) { !model().state.value.loading }
        if (compose.onAllNodesWithText("Not now").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("Not now").performClick()
        compose.waitForIdle()
    }
    private fun resize(size: String) {
        shell("wm size $size")
        compose.waitUntil(15_000) { compose.activity.windowManager.currentWindowMetrics.bounds.width() == size.substringBefore('x').toInt() }
        compose.waitForIdle()
    }

    @Test fun coverColumnAndRowSpacingMatchBothInnerPanesAndSurviveRecreation() {
        ready()
        val before = model().state.value.layout
        val oldSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        val oldDensity = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
        val beforeCoverWidth = model().state.value.coverWidthDp
        try {
            shell("wm density 360")
            resize("1248x1972")
            compose.waitUntil(5000) { model().state.value.coverWidthDp > 550f }
            val ids = model().state.value.apps.take(13).map { it.id }
            compose.runOnIdle {
                model().restoreLayout(HomeLayout(List(17) { index -> ids.getOrNull(index - 8) }, ids.drop(9), listOf(
                    WidgetPlacement(0, CLOCK_WIDGET, 0, 0, 0, 2, 2),
                    WidgetPlacement(1, DATE_WIDGET, 0, 2, 0, 2, 2),
                    WidgetPlacement(2, CLOCK_WIDGET, -1, 0, 0, 2, 2))))
            }
            compose.onNodeWithContentDescription("Home page 1").performClick()
            compose.waitForIdle()
            val savedLayout = model().state.value.layout
            val coverColumn = bounds("home-cell-9").center.x - bounds("home-cell-8").center.x
            val coverRow = bounds("home-cell-12").top - bounds("home-cell-8").top
            val coverWidget = bounds("widget-slot-0")
            shell("screencap -p /data/local/tmp/iduohome-spacing-cover.png")

            resize("2448x1848")
            assertEquals(coverColumn, bounds("home-cell-9").center.x - bounds("home-cell-8").center.x, 2f)
            assertEquals(coverColumn, bounds("home-cell-${homeCellIndex(-1, 9)}").center.x -
                bounds("home-cell-${homeCellIndex(-1, 8)}").center.x, 2f)
            assertEquals(coverRow, bounds("home-cell-12").top - bounds("home-cell-8").top, 2f)
            assertEquals(coverWidget.width, bounds("widget-slot-0").width, 2f)
            assertEquals(coverWidget.height, bounds("widget-slot-0").height, 2f)
            assertEquals(savedLayout, model().state.value.layout)
            shell("screencap -p /data/local/tmp/iduohome-spacing-inner.png")
            compose.activityRule.scenario.recreate(); ready()
            assertEquals(coverColumn, bounds("home-cell-9").center.x - bounds("home-cell-8").center.x, 2f)
            assertEquals(savedLayout, model().state.value.layout)
        } finally {
            shell("wm size ${oldSize ?: "reset"}")
            shell("wm density ${oldDensity ?: "reset"}")
            compose.waitForIdle()
            compose.runOnIdle {
                model().restoreLayout(before)
                if (beforeCoverWidth > 0f) model().rememberCoverWidth(beforeCoverWidth)
            }
        }
    }
}
