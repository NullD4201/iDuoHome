package kr.me.nulld.iduohome

import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsLocalizationIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun text(id: Int) = compose.activity.getString(id)

    @Test fun allSettingsPagesAndNativePermissionDialogUseTheSelectedLanguage() {
        check(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val language = InstrumentationRegistry.getArguments().getString("expectedLanguage") ?: "en"
        assertEquals(language, compose.activity.resources.configuration.locales[0].language)
        assertEquals(if (language == "ko") "런처 맞춤 설정" else "Make it yours", text(R.string.settings_title))
        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(15_000) { !model.state.value.loading }
        if (compose.onAllNodesWithTag("setup-skip").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("setup-skip").performClick()
        val before = model.state.value.layout
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.settings_home_page, 1)).performClick()
        compose.onNodeWithTag("home-page-0").performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithTag("empty-space-customize").assertTextContains(text(R.string.settings_customize)).performClick()
        compose.onNodeWithText(text(R.string.settings_title)).assertIsDisplayed()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        fun capture(name: String) {
            compose.waitForIdle()
            device.waitForIdle()
            device.takeScreenshot(File(compose.activity.cacheDir, "settings-$language-$name.png"))
        }
        capture("overview")
        val pages = listOf(
            "wallpaper" to R.string.settings_wallpaper_title,
            "icons" to R.string.settings_icons_title,
            "home" to R.string.settings_home_title,
            "gestures" to R.string.settings_gestures_title,
            "backup" to R.string.settings_backup_title,
            "help" to R.string.settings_help_title,
        )
        for ((page, title) in pages) {
            compose.onNodeWithTag("customization-$page").performScrollTo().performClick()
            compose.onNodeWithText(text(title)).assertIsDisplayed()
            when (page) {
                "wallpaper" -> {
                    compose.onNodeWithTag("appearance-light").performScrollTo().assertTextContains(text(R.string.settings_light))
                    compose.onNodeWithTag("appearance-latitude").performScrollTo().performTextReplacement("100")
                    compose.onNodeWithTag("appearance-longitude").performTextReplacement("0")
                    compose.onNodeWithTag("appearance-save-place").performScrollTo().performClick()
                    compose.onNodeWithTag("appearance-manual-status").assertTextEquals(text(R.string.settings_coordinates_invalid))
                    compose.onNodeWithTag("fold-effect-switch").performScrollTo()
                        .assertContentDescriptionEquals(text(R.string.fold_effect_enable))
                }
                "icons" -> compose.onNodeWithTag("shape-option-circle").performScrollTo()
                    .assertTextContains(text(R.string.settings_shape_circle))
                "home" -> compose.onNodeWithText(text(R.string.settings_cover)).assertIsDisplayed()
                "gestures" -> compose.onNodeWithTag("discover-switch")
                    .assertContentDescriptionEquals(text(R.string.settings_discover_enable))
                "backup" -> {
                    compose.onNodeWithTag("layout-export").assertTextContains(text(R.string.settings_save))
                    compose.onNodeWithTag("layout-import").assertTextContains(text(R.string.settings_restore))
                }
                "help" -> compose.onNodeWithTag("help-shade-setup").performScrollTo()
                    .assertTextContains(text(R.string.settings_shade_setup))
            }
            capture(page)
            compose.onNodeWithTag("customization-back").performClick()
        }
        assertEquals(before, model.state.value.layout)
        compose.onNodeWithTag("customization-help").performScrollTo().performClick()
        compose.onNodeWithTag("help-shade-setup").performScrollTo().performClick()
        assertTrue(device.wait(Until.hasObject(By.text(text(R.string.settings_enable_shade))), 3_000))
        // The platform dialog may render Latin button labels in all caps.
        assertTrue(device.findObject(By.res("android:id/button1")).text
            .equals(text(R.string.settings_open_settings), ignoreCase = true))
        capture("permission")
        device.pressBack()
    }
}
