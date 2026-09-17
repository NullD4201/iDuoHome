package kr.me.nulld.iduohome

import android.content.Context
import androidx.core.content.edit
import androidx.compose.runtime.*
import java.time.*
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

enum class AppearanceMode { LIGHT, DARK, SYSTEM, SUNRISE_SUNSET }
data class AppearanceState(val mode: AppearanceMode = AppearanceMode.LIGHT, val place: String = "",
    val latitude: Double? = null, val longitude: Double? = null, val locationTime: Long = 0,
    val deviceLocation: Boolean = false, val dark: Boolean = false, @androidx.annotation.StringRes val fallback: Int? = null,
    val locationStatus: String? = null, val backgroundDark: Boolean = false)

internal fun resolveAppearance(value: AppearanceState, systemDark: Boolean,
    now: ZonedDateTime = ZonedDateTime.now()): AppearanceState {
    val lat = value.latitude; val lon = value.longitude
    val (backgroundDark, fallback) = when {
        lat == null || lon == null -> systemDark to R.string.settings_background_no_location
        value.deviceLocation && now.toInstant().toEpochMilli() - value.locationTime > 30L * 24 * 60 * 60 * 1000 ->
            systemDark to R.string.settings_background_stale_location
        else -> runCatching {
            solarSchedule(now.toLocalDate(), lat, lon, now.zone).isDark(now) to null
        }.getOrElse { systemDark to R.string.settings_background_location_unavailable }
    }
    val dark = when (value.mode) {
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
        AppearanceMode.SYSTEM -> systemDark
        AppearanceMode.SUNRISE_SUNSET -> backgroundDark
    }
    return value.copy(dark = dark, backgroundDark = backgroundDark, fallback = fallback)
}

class AppearanceStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private fun currentSystemDark() = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    var state by mutableStateOf(load(currentSystemDark())); private set
    init { DuoAppearanceRuntime.update(state) }
    private fun load(systemDark: Boolean): AppearanceState {
        val mode = runCatching { AppearanceMode.valueOf(prefs.getString("mode", "LIGHT")!!) }.getOrDefault(AppearanceMode.LIGHT)
        val lat = runCatching { prefs.getString("lat", null)?.toDoubleOrNull() }.getOrNull()
        val lon = runCatching { prefs.getString("lon", null)?.toDoubleOrNull() }.getOrNull()
        return resolveAppearance(AppearanceState(mode, runCatching { prefs.getString("place", "") ?: "" }.getOrDefault(""), lat, lon,
            runCatching { prefs.getLong("locationTime", 0) }.getOrDefault(0),
            runCatching { prefs.getBoolean("deviceLocation", false) }.getOrDefault(false)), systemDark).let {
                if (it.deviceLocation) it.copy(place = context.getString(R.string.settings_approximate_location)) else it
            }
    }
    fun setMode(mode: AppearanceMode, systemDark: Boolean) { save(state.copy(mode = mode), systemDark) }
    fun setManual(place: String, latitude: Double, longitude: Double, systemDark: Boolean) {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        save(state.copy(place = place.trim(), latitude = latitude, longitude = longitude,
            locationTime = System.currentTimeMillis(), deviceLocation = false), systemDark)
    }
    fun setDeviceLocation(latitude: Double, longitude: Double, systemDark: Boolean) = save(state.copy(
        place = context.getString(R.string.settings_approximate_location), latitude = latitude, longitude = longitude,
        locationTime = System.currentTimeMillis(), deviceLocation = true, locationStatus = null), systemDark)
    fun locationStatus(message: String?) { state = state.copy(locationStatus = message) }
    fun clearLocation(systemDark: Boolean) = save(state.copy(place = "", latitude = null, longitude = null,
        locationTime = 0, deviceLocation = false), systemDark)
    fun refresh(systemDark: Boolean) { state = resolveAppearance(state, systemDark); DuoAppearanceRuntime.update(state) }
    fun reloadFromPreferences(systemDark: Boolean = currentSystemDark()) {
        val transientStatus = state.locationStatus
        state = load(systemDark).copy(locationStatus = transientStatus)
        DuoAppearanceRuntime.update(state)
    }
    private fun save(value: AppearanceState, systemDark: Boolean) {
        prefs.edit {putString("mode", value.mode.name).putString("place", value.place)
                .putString("lat", value.latitude?.toString()).putString("lon", value.longitude?.toString())
            .putLong("locationTime", value.locationTime).putBoolean("deviceLocation", value.deviceLocation)}
        state = resolveAppearance(value, systemDark)
        DuoAppearanceRuntime.update(state)
    }
}

object DuoAppearanceRuntime {
    @Volatile var dark: Boolean = false
    @Volatile var backgroundDark: Boolean = false

    fun update(state: AppearanceState) {
        val changed = dark != state.dark || backgroundDark != state.backgroundDark
        dark = state.dark; backgroundDark = state.backgroundDark
        if (changed) LauncherBackgroundCache.changed(LauncherBackgroundCache.bitmap, LauncherBackgroundCache.identity)
    }
}

data class DuoPalette(val ink: androidx.compose.ui.graphics.Color, val glass: androidx.compose.ui.graphics.Color,
    val backgroundTop: androidx.compose.ui.graphics.Color, val backgroundBottom: androidx.compose.ui.graphics.Color,
    val dark: Boolean, val backgroundDark: Boolean = dark)
val LightDuoPalette = DuoPalette(androidx.compose.ui.graphics.Color(0xFF243A46), androidx.compose.ui.graphics.Color(0xFFE8EFF2),
    androidx.compose.ui.graphics.Color(0xFF41687E), androidx.compose.ui.graphics.Color(0xFFD8CEB6), false)
val DarkDuoPalette = DuoPalette(androidx.compose.ui.graphics.Color(0xFFEAF3F6), androidx.compose.ui.graphics.Color(0xFF263A43),
    androidx.compose.ui.graphics.Color(0xFF132832), androidx.compose.ui.graphics.Color(0xFF463F35), true)
val LocalDuoPalette = staticCompositionLocalOf { LightDuoPalette }

@Composable
fun rememberSavedAppearance(): AppearanceState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember(context) { AppearanceStore(context.applicationContext) }
    DisposableEffect(context, store, lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) { store.reloadFromPreferences() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED)
        }
        var registered = false
        fun register() { if (!registered) {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true; store.reloadFromPreferences()
        } }
        fun unregister() { if (registered) { runCatching { context.unregisterReceiver(receiver) }; registered = false } }
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_START -> register()
            Lifecycle.Event.ON_STOP -> unregister()
            else -> Unit
        } }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) register()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); unregister() }
    }
    return store.state
}
