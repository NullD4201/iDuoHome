package kr.me.nulld.iduohome

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

@SdkSuppress(minSdkVersion = 33)
class FoldGlassRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun innerAndCoverFrostReturnToSharpAndInnerRightPaneStaysUnchanged() {
        check(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val angle = mutableFloatStateOf(180f)
        val inner = mutableStateOf(true)
        val glass = FoldGlass() // Compile both shaders here; a silent fallback cannot pass.
        compose.setContent {
            Canvas(Modifier.size(240.dp, 180.dp).testTag("glass").graphicsLayer {
                val rest = if (inner.value) 180f else 0f
                renderEffect = if (angle.floatValue == rest) null else
                    glass.render(size, inner.value, foldOptics(angle.floatValue, inner.value, 1f), density).asComposeRenderEffect()
            }) {
                drawRect(Color.White)
                val cell = size.width / 24
                for (x in 0..23) for (y in 0..17) if ((x + y) % 2 == 0)
                    drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell, cell))
            }
        }
        val sharp = compose.onNodeWithTag("glass").captureToImage().asAndroidBitmap()
        compose.runOnIdle { angle.floatValue = 95f }
        val folded = compose.onNodeWithTag("glass").captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "fold-glass-test.png")
            .outputStream().use { folded.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        var changedLeft = 0
        for (x in 10 until sharp.width - 10 step 3) for (y in 10 until sharp.height - 10 step 3) {
            val same = sharp.getPixel(x, y) == folded.getPixel(x, y)
            if (x > sharp.width / 2 + 2) assertTrue("Right pane changed at $x,$y", same)
            if (x < sharp.width / 3 && !same) changedLeft++
        }
        assertTrue("Left pane must visibly frost", changedLeft > 100)
        compose.runOnIdle { angle.floatValue = 180f }
        val restored = compose.onNodeWithTag("glass").captureToImage().asAndroidBitmap()
        assertTrue("Resting Home must be unchanged", sharp.sameAs(restored))
        compose.runOnIdle { inner.value = false; angle.floatValue = 70f }
        val cover = compose.onNodeWithTag("glass").captureToImage().asAndroidBitmap()
        var changedRight = 0
        for (x in sharp.width / 2 until sharp.width - 10 step 3) for (y in 10 until sharp.height - 10 step 3)
            if (sharp.getPixel(x, y) != cover.getPixel(x, y)) changedRight++
        assertTrue("Cover effect must extend across the full page", changedRight > 100)
        compose.runOnIdle { angle.floatValue = 0f }
        assertTrue(sharp.sameAs(compose.onNodeWithTag("glass").captureToImage().asAndroidBitmap()))
    }
}
