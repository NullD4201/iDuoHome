package kr.me.nulld.iduohome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun AppearanceSettings(state: AppearanceState, onMode: (AppearanceMode) -> Unit,
    onManual: (String, Double, Double) -> Unit, onDeviceLocation: () -> Unit, onClear: () -> Unit) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    var place by remember(state.place) { mutableStateOf(state.place) }
    var latitude by remember(state.latitude) { mutableStateOf(state.latitude?.toString().orEmpty()) }
    var longitude by remember(state.longitude) { mutableStateOf(state.longitude?.toString().orEmpty()) }
    var inputError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("appearance-settings")) {
        Text(settingsContext.getString(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
        AppearanceMode.entries.forEach { mode ->
            FilterChip(selected = state.mode == mode, onClick = { onMode(mode) }, label = { Text(when (mode) {
                AppearanceMode.LIGHT -> settingsContext.getString(R.string.settings_light); AppearanceMode.DARK -> settingsContext.getString(R.string.settings_dark); AppearanceMode.SYSTEM -> settingsContext.getString(R.string.settings_follow_system)
                AppearanceMode.SUNRISE_SUNSET -> settingsContext.getString(R.string.settings_sunrise_sunset)
        }) }, modifier = Modifier.testTag("appearance-${mode.name.lowercase()}"))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(settingsContext.getString(R.string.settings_default_background_title), style = MaterialTheme.typography.titleMedium)
            Text(settingsContext.getString(R.string.settings_background_schedule_help),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.fallback?.let { Text(settingsContext.getString(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedTextField(place, { place = it }, Modifier.testTag("appearance-place"), label = { Text(settingsContext.getString(R.string.settings_place_name)) }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(latitude, { latitude = it }, Modifier.weight(1f).testTag("appearance-latitude"), label = { Text(settingsContext.getString(R.string.settings_latitude)) }, singleLine = true)
                OutlinedTextField(longitude, { longitude = it }, Modifier.weight(1f).testTag("appearance-longitude"), label = { Text(settingsContext.getString(R.string.settings_longitude)) }, singleLine = true)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    focusManager.clearFocus(); keyboard?.hide()
                    val lat = latitude.toDoubleOrNull(); val lon = longitude.toDoubleOrNull()
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                        inputError = null; onManual(place, lat, lon)
                    } else inputError = settingsContext.getString(R.string.settings_coordinates_invalid) },
                    modifier = Modifier.fillMaxWidth().testTag("appearance-save-place")) { Text(settingsContext.getString(R.string.settings_use_place)) }
                AppearanceFeedback(inputError, MaterialTheme.colorScheme.error, "appearance-manual-status")
                OutlinedButton(onClick = {
                    focusManager.clearFocus(); keyboard?.hide(); onDeviceLocation()
                }, modifier = Modifier.fillMaxWidth()
                    .testTag("appearance-device-location")) { Text(settingsContext.getString(R.string.settings_use_device_location)) }
                AppearanceFeedback(state.locationStatus, MaterialTheme.colorScheme.onSurfaceVariant,
                    "appearance-location-status")
                if (state.latitude != null) TextButton(onClick = onClear, Modifier.fillMaxWidth()) { Text(settingsContext.getString(R.string.settings_clear_location)) }
            }
        }
    }
}

@Composable
private fun AppearanceFeedback(message: String?, color: androidx.compose.ui.graphics.Color, tag: String) {
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(message) { if (message != null) bringIntoView.bringIntoView() }
    message?.let {
        Text(it, color = color, modifier = Modifier.bringIntoViewRequester(bringIntoView)
            .semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag))
    }
}
