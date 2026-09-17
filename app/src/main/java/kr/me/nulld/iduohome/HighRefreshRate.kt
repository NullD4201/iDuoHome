package kr.me.nulld.iduohome

import android.view.Surface
import android.view.Window

/**
 * Optimizes the window for high-refresh-rate displays (90Hz, 120Hz, 144Hz, 165Hz).
 * Queries the display subsystem for the highest supported frame rate and locks the
 * display controller to prevent power management throttling down to 60Hz during gestures.
 */
internal fun Window.enableHighRefreshRate() {
    try {
        val display = context.display

        val modes = display?.supportedModes ?: emptyArray()
        val highestMode = modes.maxByOrNull { it.refreshRate }

        if (highestMode != null && highestMode.refreshRate > 60f) {
            val maxRate = highestMode.refreshRate
            val params = attributes
            params.preferredDisplayModeId = highestMode.modeId
            params.preferredRefreshRate = maxRate

            runCatching {
                val minField = params.javaClass.getField("preferredMinDisplayRefreshRate")
                minField.setFloat(params, maxRate)
            }
            runCatching {
                val maxField = params.javaClass.getField("preferredMaxDisplayRefreshRate")
                maxField.setFloat(params, maxRate)
            }

            attributes = params

            decorView.post {
                runCatching {
                    val surfaceControl = decorView.rootSurfaceControl
                    if (surfaceControl != null) {
                        val method = surfaceControl.javaClass.methods.firstOrNull { it.name == "setFrameRate" }
                        if (method != null) {
                            if (method.parameterCount == 2) {
                                method.invoke(surfaceControl, maxRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                            } else if (method.parameterCount == 3) {
                                method.invoke(surfaceControl, maxRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, 0)
                            }
                        }
                    }
                }
            }
        }
    } catch (_: Throwable) {
        // Fall back gracefully if display mode switching is restricted by device policy
    }
}
