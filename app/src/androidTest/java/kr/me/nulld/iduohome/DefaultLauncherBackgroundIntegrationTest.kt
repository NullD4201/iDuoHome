package kr.me.nulld.iduohome

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import kotlin.math.abs
import java.time.LocalDate
import java.time.ZoneId

class DefaultLauncherBackgroundIntegrationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sunsetAndSunriseRepaintTheBackgroundWhileAppThemeStaysLight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = launcherBackgroundPreferences(context)
        val oldSettings = listOf("useSystemWallpaper", "photoEnabled").associateWith { prefs.all[it] as? Boolean }
        val previousBitmap = LauncherBackgroundCache.bitmap
        val previousIdentity = LauncherBackgroundCache.identity
        val location = AppearanceState(mode = AppearanceMode.LIGHT, latitude = 37.5665, longitude = 126.9780)
        val date = LocalDate.of(2026, 9, 14)
        val zone = ZoneId.of("Asia/Seoul")
        val sunset = solarSchedule(date, location.latitude!!, location.longitude!!, zone).sunset!!
        val nextSunrise = solarSchedule(date.plusDays(1), location.latitude, location.longitude, zone).sunrise!!
        val appearance = mutableStateOf(resolveAppearance(location, false, sunset.minusSeconds(1)))
        fun centerPixel(): Int {
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2].toArgb()
        }
        try {
            prefs.edit().putBoolean("useSystemWallpaper", false).putBoolean("photoEnabled", false).commit()
            compose.runOnUiThread { LauncherBackgroundCache.changed(null) }
            compose.setContent {
                DuoTheme(appearance.value.dark, backgroundDark = appearance.value.backgroundDark) {
                    Box(Modifier.size(100.dp)) { DuneWallpaper(wide = false) }
                }
            }
            val daylight = centerPixel()
            compose.runOnIdle { appearance.value = resolveAppearance(location, false, sunset) }
            val night = centerPixel()
            assertNotEquals(daylight, night)
            assertFalse(appearance.value.dark)
            compose.runOnIdle { appearance.value = resolveAppearance(location, false, nextSunrise.minusSeconds(1)) }
            assertEquals(night, centerPixel())
            compose.runOnIdle { appearance.value = resolveAppearance(location, false, nextSunrise) }
            assertEquals(daylight, centerPixel())
            assertFalse(appearance.value.dark)
        } finally {
            prefs.edit().apply {
                oldSettings.forEach { (key, value) -> if (value == null) remove(key) else putBoolean(key, value) }
            }.commit()
            compose.runOnUiThread { LauncherBackgroundCache.changed(previousBitmap, previousIdentity) }
        }
    }

    @Test fun bundledImagesFollowWindowWidthAndThemeAndRespectSystemAndPhotoChoices() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = launcherBackgroundPreferences(context)
        val previousSystem = prefs.all["useSystemWallpaper"] as? Boolean
        val previousPhotoEnabled = prefs.all["photoEnabled"] as? Boolean
        val previousBitmap = LauncherBackgroundCache.bitmap
        val previousIdentity = LauncherBackgroundCache.identity
        val photo = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        fun render(width: Int, height: Int, density: Float, dark: Boolean,
            customPhoto: Bitmap? = null, liveWallpaper: Boolean = false): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(android.graphics.Canvas(bitmap)), Size(width.toFloat(), height.toFloat())) {
                drawLauncherBackground(context, customPhoto?.asImageBitmap(), dark, showDefaultFallback = liveWallpaper)
            }
            return bitmap
        }
        try {
            prefs.edit().remove("useSystemWallpaper").commit()
            // A dense cover display is still narrow in dp; landscape/expanded windows use main.
            for ((width, height, density) in listOf(Triple(1125, 2001, 3f), Triple(1950, 1401, 3f))) {
                for (dark in listOf(false, true)) {
                    val resource = if (width == 1125) {
                        if (dark) R.drawable.cover_dark else R.drawable.cover_light
                    } else if (dark) R.drawable.main_dark else R.drawable.main_light
                    val source = BitmapFactory.decodeResource(context.resources, resource)
                    val actual = render(width, height, density, dark)
                    try {
                        val expectedPixel = source.getPixel(source.width / 2, source.height / 2)
                        val actualPixel = actual.getPixel(width / 2, height / 2)
                        for (channel in listOf<(Int) -> Int>(Color::red, Color::green, Color::blue)) {
                            assertTrue("Wrong image for width=$width density=$density dark=$dark",
                                abs(channel(expectedPixel) - channel(actualPixel)) <= 8)
                        }
                        assertEquals(255, Color.alpha(actual.getPixel(0, 0)))
                        assertEquals(255, Color.alpha(actual.getPixel(width - 1, height - 1)))
                    } finally { source.recycle(); actual.recycle() }
                }
            }
            prefs.edit().putBoolean("useSystemWallpaper", true).commit()
            render(375, 600, 1f, false).let {
                assertEquals(Color.TRANSPARENT, it.getPixel(187, 300)); it.recycle()
            }
            render(375, 600, 1f, true, customPhoto = photo).let {
                assertEquals(Color.MAGENTA, it.getPixel(187, 300)); it.recycle()
            }
            render(375, 600, 1f, true, liveWallpaper = true).let {
                assertEquals(255, Color.alpha(it.getPixel(187, 300))); it.recycle()
            }
            prefs.edit().putBoolean("photoEnabled", false).commit()
            compose.runOnUiThread { LauncherBackgroundCache.changed(null) }
            compose.setContent {
                Box(Modifier.size(100.dp).background(androidx.compose.ui.graphics.Color.Magenta)) {
                    DuneWallpaper(wide = false)
                }
            }
            fun centerPixel(): Int {
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                return pixels[pixels.width / 2, pixels.height / 2].toArgb()
            }
            assertEquals(Color.MAGENTA, centerPixel())
            compose.runOnIdle {
                prefs.edit().putBoolean("useSystemWallpaper", false).commit()
                LauncherBackgroundCache.changed(null)
            }
            assertNotEquals(Color.MAGENTA, centerPixel())
            compose.runOnIdle {
                prefs.edit().putBoolean("useSystemWallpaper", true).commit()
                LauncherBackgroundCache.changed(null)
            }
            assertEquals(Color.MAGENTA, centerPixel())
        } finally {
            photo.recycle()
            prefs.edit().apply {
                if (previousSystem == null) remove("useSystemWallpaper")
                else putBoolean("useSystemWallpaper", previousSystem)
                if (previousPhotoEnabled == null) remove("photoEnabled")
                else putBoolean("photoEnabled", previousPhotoEnabled)
            }.commit()
            compose.runOnUiThread { LauncherBackgroundCache.changed(previousBitmap, previousIdentity) }
        }
    }
}
