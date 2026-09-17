package kr.me.nulld.iduohome

import kotlin.math.abs
import kotlin.math.sin

/** Runtime-only state, retained by LauncherModel across a cover/inner Activity recreation. */
internal class FoldEffectSession {
    private var inner: Boolean? = null
    private var lastVisibleAt = Long.MIN_VALUE

    fun viewport(widthDp: Int, heightDp: Int, now: Long): Boolean {
        // Rotation alone must not be mistaken for opening the phone.
        val next = isInnerFoldViewport(widthDp, heightDp)
        val changed = inner != null && inner != next && lastVisibleAt != Long.MIN_VALUE &&
            now - lastVisibleAt in 0..2_500
        inner = next
        lastVisibleAt = now
        return changed
    }

    fun visibleAt(now: Long) { lastVisibleAt = now }
    fun clear() { inner = null; lastVisibleAt = Long.MIN_VALUE }
}

// ponytail: Fold8's nearly square inner panel vs tall cover; a different panel aspect ratio
// would need a WindowManager folding-feature/display descriptor instead of this threshold.
internal fun isInnerFoldViewport(width: Int, height: Int): Boolean =
    width > 0 && height > 0 && minOf(width, height).toFloat() / maxOf(width, height) >= .70f

/** Samsung may expose only 0/90/180 posture values. Those are never called precise angles. */
internal class FoldAngleSamples {
    var continuous = false
        private set
    private var firstIntermediate: Float? = null
    private var lastTimestamp = -1L

    fun accept(angle: Float, timestamp: Long): Boolean {
        if (!angle.isFinite() || angle !in 0f..180f || timestamp <= lastTimestamp) return false
        lastTimestamp = timestamp
        if (listOf(0f, 90f, 180f).all { abs(angle - it) > .5f }) {
            val first = firstIntermediate
            if (first == null) firstIntermediate = angle
            else if (abs(first - angle) >= 1f) continuous = true
        }
        return true
    }
}

/** Projection/optical amounts adapted from Folduo's GlassProjection and FoldPolicy (MIT).
 * Copyright (c) 2026 bunkaich. See assets/licenses/MIT-Folduo.txt.
 */
internal data class FoldOptics(val amount: Float, val expansion: Float, val taper: Float, val depth: Float)

internal fun foldOptics(angle: Float, inner: Boolean, strength: Float): FoldOptics {
    val safeAngle = if (angle.isFinite()) angle.coerceIn(0f, 180f) else if (inner) 180f else 0f
    val gain = if (strength.isFinite()) strength.coerceIn(.25f, 1f) else .7f
    val amount = (if (inner) (176f - safeAngle) / 86f else (safeAngle - 1.5f) / 88.5f).coerceIn(0f, 1f)
    val t = (safeAngle / 90f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    return FoldOptics(amount * gain, .39f * t * gain, .144f * t * gain,
        (sin(Math.toRadians(minOf(90f, 180f - safeAngle).toDouble())) / 16).toFloat() * gain)
}
