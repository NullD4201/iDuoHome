package kr.me.nulld.iduohome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun LayoutRestorePreview(preview: LayoutImportPreview, onRestore: () -> Unit, onCancel: () -> Unit) {
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(onDismissRequest = onCancel, modifier = Modifier.testTag("layout-restore-preview"),
        title = { Text(settingsContext.getString(R.string.settings_review_restore)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(settingsContext.getString(R.string.settings_restore_counts,
                    settingsContext.resources.getQuantityString(R.plurals.settings_count_apps, preview.appCount, preview.appCount),
                    settingsContext.resources.getQuantityString(R.plurals.settings_count_folders, preview.folderCount, preview.folderCount),
                    settingsContext.resources.getQuantityString(R.plurals.settings_count_widgets, preview.widgetCount, preview.widgetCount)))
                if (preview.layout.leadingSlots.any { it != null } || preview.layout.widgetPlacements.any { it.page == -1 })
                    Text(settingsContext.getString(R.string.settings_includes_unfolded), style = MaterialTheme.typography.bodySmall)
                Text(settingsContext.getString(R.string.settings_restore_settings))
                Text(settingsContext.getString(R.string.settings_photo_not_in_backup),
                    style = MaterialTheme.typography.bodySmall)
                if (preview.missingApps.isNotEmpty()) {
                    Text(settingsContext.getString(R.string.settings_missing_apps, preview.missingApps.size), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error)
                    preview.missingApps.forEach { saved ->
                        val label = saved.substringAfterLast('(').removeSuffix(")").takeIf { it.isNotBlank() } ?: settingsContext.getString(R.string.settings_unavailable_app)
                        Text(settingsContext.getString(R.string.settings_missing_app_position, label))
                    }
                }
                if (preview.profileIssues.isNotEmpty()) {
                    Text(settingsContext.getString(R.string.settings_profile_attention), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error)
                    preview.profileIssues.forEach { Text(settingsContext.getString(R.string.settings_restore_profile_issue, it.first, it.second)) }
                }
                val reconnect = preview.layout.widgetPlacements.count { it.id == NEEDS_BINDING_WIDGET }
                if (reconnect > 0) Text(settingsContext.resources.getQuantityString(R.plurals.settings_restore_reconnect, reconnect, reconnect))
                Text(settingsContext.getString(R.string.settings_restore_unchanged), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { Button(onClick = onRestore, modifier = Modifier.testTag("layout-restore-apply")) { Text(settingsContext.getString(R.string.settings_restore)) } },
        dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.testTag("layout-restore-cancel")) { Text(settingsContext.getString(R.string.settings_cancel)) } })
}
