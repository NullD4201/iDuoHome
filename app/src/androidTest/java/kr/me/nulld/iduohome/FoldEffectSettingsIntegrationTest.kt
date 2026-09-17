package kr.me.nulld.iduohome

import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

class FoldEffectSettingsIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]

    @Test fun switchesPersistWithoutChangingLayoutOrWidgetBindings() {
        check(Build.HARDWARE in listOf("ranchu", "goldfish"))
        compose.waitUntil(15_000) { !model().state.value.loading }
        if (compose.onAllNodesWithTag("setup-skip").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("setup-skip").performClick()
        val before = model().state.value
        try {
            compose.runOnIdle { model().setFoldEffectEnabled(false); model().setFoldEffectFollowAngle(true) }
            compose.onNodeWithContentDescription("Home page 1").performClick()
            compose.onNodeWithTag("home-page-0").performSemanticsAction(SemanticsActions.OnLongClick)
            compose.onNodeWithTag("empty-space-customize").performClick()
            compose.onNodeWithTag("customization-wallpaper").performScrollTo().performClick()
            compose.onNodeWithTag("fold-effect-switch").performScrollTo().assertIsOff().performClick().assertIsOn()
            compose.onNodeWithTag("fold-angle-switch").performScrollTo().assertIsOn().performClick().assertIsOff()
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
                File(compose.activity.cacheDir, "fold-settings-test.png"))
            compose.runOnIdle { model().setFoldEffectStrength(.45f) }
            val saved = JSONObject(compose.activity.getSharedPreferences("launcher", 0).getString("state", null)!!)
            assertTrue(saved.getBoolean("foldEffectEnabled"))
            assertFalse(saved.getBoolean("foldEffectFollowAngle"))
            assertEquals(.45, saved.getDouble("foldEffectStrength"), .0001)
            assertEquals(before.layout, model().state.value.layout)
            val store = ViewModelStore()
            try {
                compose.runOnIdle {
                    val loaded = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory
                        .getInstance(compose.activity.application))[LauncherModel::class.java].state.value
                    assertTrue(loaded.foldEffectEnabled)
                    assertFalse(loaded.foldEffectFollowAngle)
                    assertEquals(.45f, loaded.foldEffectStrength, .0001f)
                    assertEquals(before.layout, loaded.layout)
                }
            } finally { compose.runOnIdle { store.clear() } }
            compose.onNodeWithTag("fold-effect-switch").performScrollTo().performClick().assertIsOff()
            compose.onNodeWithTag("fold-angle-switch").assertDoesNotExist()
        } finally {
            compose.runOnIdle {
                model().setFoldEffectEnabled(before.foldEffectEnabled)
                model().setFoldEffectFollowAngle(before.foldEffectFollowAngle)
                model().setFoldEffectStrength(before.foldEffectStrength)
            }
        }
    }

    @Test fun disablingEffectOrSystemAnimationsClearsTheLiveRenderState() {
        check(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val enabled = mutableStateOf(false)
        val session = FoldEffectSession()
        lateinit var effect: HomeFoldEffect
        val configuration = compose.activity.resources.configuration
        val inner = isInnerFoldViewport(configuration.screenWidthDp, configuration.screenHeightDp)
        val rest = if (inner) 180f else 0f
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
        val oldScale = shell("settings get global animator_duration_scale")
        try {
            shell("settings put global animator_duration_scale 5")
            compose.activity.setContent {
                val current = rememberHomeFoldEffect(enabled.value, true, .7f, true, session)
                SideEffect { effect = current }
                Box(Modifier.fillMaxSize())
            }
            compose.waitForIdle()
            compose.runOnIdle {
                session.viewport(if (inner) 400 else 750, 900, android.os.SystemClock.uptimeMillis())
                enabled.value = true
            }
            compose.waitUntil(2_000) { effect.angle.floatValue != rest }
            compose.runOnIdle { enabled.value = false }
            compose.waitUntil(2_000) { effect.angle.floatValue == rest }
            compose.runOnIdle {
                session.viewport(if (inner) 400 else 750, 900, android.os.SystemClock.uptimeMillis())
                enabled.value = true
            }
            compose.waitUntil(2_000) { effect.angle.floatValue != rest }
            shell("settings put global animator_duration_scale 0")
            compose.waitUntil(2_000) { effect.angle.floatValue == rest }
        } finally {
            if (oldScale == "null") shell("settings delete global animator_duration_scale")
            else shell("settings put global animator_duration_scale $oldScale")
        }
    }
}
