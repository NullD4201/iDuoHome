package kr.me.nulld.iduohome

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.graphics.BlendMode
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Effects stay on our own live Home render layer, including native widgets. No screen capture,
 * overlay permission, wallpaper replacement, or Samsung display-control API is needed.
 */
internal class HomeFoldEffect(private val inner: Boolean, private val density: Float, private val strength: Float) {
    val angle = mutableFloatStateOf(if (inner) 180f else 0f)
    private val glass by lazy { if (Build.VERSION.SDK_INT >= 33) runCatching { FoldGlass() }.getOrNull() else null }

    fun render(size: Size): androidx.compose.ui.graphics.RenderEffect? {
        val optics = foldOptics(angle.floatValue, inner, strength)
        if (optics.amount < .001f || size.width <= 0f || size.height <= 0f) return null
        return if (Build.VERSION.SDK_INT >= 33 && glass != null) {
            glass!!.render(size, inner, optics, density).asComposeRenderEffect()
        } else {
            // Android 12 still gets a native blur; AGSL perspective requires Android 13.
            RenderEffect.createBlurEffect(12f * density * optics.amount, 12f * density * optics.amount,
                Shader.TileMode.CLAMP).asComposeRenderEffect()
        }
    }
}

@Composable
@android.annotation.SuppressLint("ConfigurationScreenWidthHeight") // Posture classification, never layout sizing.
internal fun rememberHomeFoldEffect(enabled: Boolean, followAngle: Boolean, strength: Float,
    homeVisible: Boolean, session: FoldEffectSession): HomeFoldEffect {
    val activity = androidx.activity.compose.LocalActivity.current as MainActivity
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    val inner = isInnerFoldViewport(width, height)
    val density = LocalDensity.current.density
    val effect = remember(inner, density, strength) { HomeFoldEffect(inner, density, strength) }
    DisposableEffect(activity, view, lifecycle, effect, enabled, followAngle, homeVisible, width, height) {
        val sensors = activity.getSystemService(SensorManager::class.java)
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        val handler = Handler(Looper.getMainLooper())
        val rest = if (inner) 180f else 0f
        var animator: ValueAnimator? = null
        var running = false
        var samples = FoldAngleSamples()
        var previousAngle: Float? = null
        var lastPulseAt = Long.MIN_VALUE
        var startedAtNs = 0L

        fun animate(target: Float, durationMs: Long) {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(effect.angle.floatValue, target).apply {
                duration = durationMs
                addUpdateListener { effect.angle.floatValue = it.animatedValue as Float }
                start()
            }
        }
        fun pulse() {
            val now = SystemClock.uptimeMillis()
            if (lastPulseAt != Long.MIN_VALUE && now - lastPulseAt < 500) return
            lastPulseAt = now
            animator?.cancel()
            effect.angle.floatValue = 90f
            animate(rest, 380)
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!running || event.timestamp < startedAtNs || !ValueAnimator.areAnimatorsEnabled()) return
                val angle = event.values.firstOrNull() ?: return
                if (!samples.accept(angle, event.timestamp)) return
                val previous = previousAngle
                previousAngle = angle
                if (samples.continuous && followAngle) animate(angle, 60)
                else if (previous != null && previous != angle && angle in 1f..175f) pulse()
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        fun stop() {
            if (running) session.visibleAt(SystemClock.uptimeMillis())
            running = false
            sensors.unregisterListener(listener)
            animator?.cancel(); animator = null
            effect.angle.floatValue = rest
        }
        fun refresh() {
            val active = enabled && homeVisible && !activity.isInMultiWindowMode && view.hasWindowFocus() &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && ValueAnimator.areAnimatorsEnabled()
            if (!active) { stop(); return }
            if (running) return
            running = true
            startedAtNs = SystemClock.elapsedRealtimeNanos()
            samples = FoldAngleSamples(); previousAngle = null
            if (session.viewport(width, height, SystemClock.uptimeMillis())) pulse()
            // Standard Android sensor only. Protected Samsung sensors are intentionally not requested.
            if (sensor != null) runCatching {
                sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME, handler)
            }
        }
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = refresh()
            override fun onPause(owner: LifecycleOwner) = stop()
            // Samsung may stop the old display before reporting a configuration change.
            // Keep only its geometry for the session's short handoff timeout (never pixels).
        }
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { refresh() }
        val motionSettings = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) { refresh() }
        }
        if (!enabled) session.clear()
        lifecycle.addObserver(observer)
        view.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        activity.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, motionSettings)
        refresh()
        onDispose {
            stop()
            lifecycle.removeObserver(observer)
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
            activity.contentResolver.unregisterContentObserver(motionSettings)
        }
    }
    return effect
}

/** Folduo projection, composed with Android's GPU blur over live content. This avoids its
 * frozen screenshot cache and privileged dual-display transport. The blur mask approximates
 * the original variable-radius frost while preserving a sharp right pane and hinge seam.
 * Copyright (c) 2026 bunkaich; projection adapted under MIT (assets/licenses/MIT-Folduo.txt).
 */
@RequiresApi(33)
internal class FoldGlass {
    private val projection = RuntimeShader("""
        uniform shader content;
        uniform float2 size;
        uniform float inner;
        uniform float2 pose;
        uniform float depth;
        half4 main(float2 p) {
            float page = size.x * mix(1.0, .5, inner);
            if (inner > .5 && p.x >= page) return content.eval(p);
            float hinge = inner * page;
            float direction = mix(1.0, -1.0, inner);
            float distance = clamp((p.x - hinge) * direction / page, 0.0, 1.0);
            float u = distance / (1.0 + pose.x);
            float2 q = float2(hinge + direction * page * u,
                size.y * .5 + (p.y - size.y * .5) / (1.0 - pose.y * u));
            if (inner > .5) q = float2(p.x,
                size.y * .5 + (p.y - size.y * .5) / (1.0 - depth * distance));
            return content.eval(clamp(q, float2(.5), size - float2(.5)));
        }
    """.trimIndent())
    private val frost = RuntimeShader("""
        uniform shader content;
        uniform float width;
        uniform float inner;
        half4 main(float2 p) {
            float page = width * mix(1.0, .5, inner);
            float distance = clamp((p.x - inner * page) * mix(1.0, -1.0, inner) / page, 0.0, 1.0);
            float weight = inner > .5 ? distance * distance : .45 + .55 * sqrt(distance);
            if (inner > .5 && p.x >= page) return half4(0);
            return content.eval(p) * half(weight);
        }
    """.trimIndent())

    fun render(size: Size, inner: Boolean, optics: FoldOptics, density: Float): RenderEffect {
        projection.setFloatUniform("size", size.width, size.height)
        projection.setFloatUniform("inner", if (inner) 1f else 0f)
        projection.setFloatUniform("pose", optics.expansion, optics.taper)
        projection.setFloatUniform("depth", optics.depth)
        frost.setFloatUniform("width", size.width)
        frost.setFloatUniform("inner", if (inner) 1f else 0f)
        val sharp = RenderEffect.createRuntimeShaderEffect(projection, "content")
        val radius = (28f * density * optics.amount).coerceAtLeast(.1f)
        val blurred = RenderEffect.createBlurEffect(radius, radius, sharp, Shader.TileMode.CLAMP)
        val masked = RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(frost, "content"), blurred)
        return RenderEffect.createBlendModeEffect(sharp, masked, BlendMode.SRC_OVER)
    }
}
