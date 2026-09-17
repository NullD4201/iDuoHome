package kr.me.nulld.iduohome

import android.appwidget.AppWidgetManager
import android.content.Context
import android.net.Uri
import android.os.UserManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupController(
    private val activity: ComponentActivity,
    private val model: LauncherModel,
    private val widgets: WidgetController,
    private val onExternalResultChanged: (Boolean) -> Unit,
) {
    var preview by mutableStateOf<LayoutImportPreview?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var successMessage by mutableStateOf<String?>(null)
        private set
    var pickerPending by mutableStateOf(false)
        private set

    private val store = activity.getSharedPreferences("layout_backup_pending", Context.MODE_PRIVATE)
    private val userManager = activity.getSystemService(UserManager::class.java)
    private val scope = layoutBackupScope(activity)
    private var operation: String? = null
    private var generation = 0
    private var importRaw: String? = null
    private val createDocument = activity.activityResultRegistry.register(
        "duo.backup.create", activity, ActivityResultContracts.CreateDocument("application/json")
    ) createCallback@{ uri ->
        if (operation != OP_EXPORT) return@createCallback
        if (uri == null) clearTransaction() else {
            store.edit {putString(KEY_URI, uri.toString())}
            writeExport(uri, generation)
        }
    }
    private val openDocument = activity.activityResultRegistry.register(
        "duo.backup.open", activity, ActivityResultContracts.OpenDocument()
    ) openCallback@{ uri ->
        if (operation != OP_IMPORT) return@openCallback
        if (uri == null) clearTransaction() else {
            store.edit {putString(KEY_URI, uri.toString())}
            readImport(uri, generation)
        }
    }

    fun restore() {
        val saved = runCatching { Triple(store.getString(KEY_OPERATION, null), store.getString(KEY_PREVIEW, null), store.getString(KEY_URI, null)) }.getOrNull()
        operation = saved?.first
        val raw = saved?.second
        val savedUri = saved?.third
        importRaw = raw.takeIf { operation == OP_PREVIEW }
        if (raw != null || operation != null) onExternalResultChanged(true)
        if (operation == OP_PREVIEW && raw == null) clearTransaction()
        else if (raw != null) parsePreview(raw, persist = false)
        else if (savedUri != null && operation == OP_IMPORT) readImport(savedUri.toUri(), generation)
        else if (savedUri != null && operation == OP_EXPORT) writeExport(savedUri.toUri(), generation)
        else if (operation != null) {
            pickerPending = true
            onExternalResultChanged(true)
        } else onExternalResultChanged(false)
    }

    fun startExport(fileName: String = "duo-launcher-layout.json") {
        val state = model.state.value
        val raw = runCatching { encodeLayoutBackup(state, widgetDescriptors(state), scope) }.getOrElse {
            errorMessage = activity.getString(R.string.settings_backup_prepare_failed); return
        }
        begin(OP_EXPORT, raw)
        try { createDocument.launch(fileName) }
        catch (error: Exception) { errorMessage = activity.getString(R.string.settings_document_picker_unavailable); clearTransaction(false) }
    }

    fun startImport() {
        begin(OP_IMPORT)
        try { openDocument.launch(arrayOf("application/json", "text/json", "text/plain")) }
        catch (error: Exception) { errorMessage = activity.getString(R.string.settings_document_picker_unavailable); clearTransaction(false) }
    }

    fun applyImport(): Boolean {
        val raw = importRaw ?: return false
        val token = generation
        activity.lifecycleScope.launch {
            val state = model.state.first { !it.loading }
            val result = runCatching { withContext(Dispatchers.Default) {
                decodeLayoutBackup(raw, state.apps, state.profiles, scope)
            } }
            result.rethrowCancellation()
            if (token != generation || operation != OP_PREVIEW) return@launch
            result.onSuccess {
                val changed = model.applyImportedLayout(it)
                successMessage = if (changed) activity.getString(R.string.settings_layout_restored) else activity.getString(R.string.settings_layout_already_active)
                clearTransaction(clearMessages = false)
            }.onFailure { errorMessage = activity.getString(R.string.settings_backup_no_longer_valid) }
        }
        return true
    }

    fun cancelImport() = clearTransaction()
    fun clearMessage() { errorMessage = null; successMessage = null }

    fun resumePendingPicker(): Boolean = when (operation) {
        OP_EXPORT -> runCatching { createDocument.launch("duo-launcher-layout.json") }.isSuccess
        OP_IMPORT -> runCatching { openDocument.launch(arrayOf("application/json", "text/json", "text/plain")) }.isSuccess
        else -> false
    }

    private fun begin(value: String, payload: String? = null) {
        generation++
        preview = null; errorMessage = null; successMessage = null
        importRaw = null
        operation = value; pickerPending = true
        store.edit { clear().putString(KEY_OPERATION, value)
            payload?.let { putString(KEY_EXPORT, it) }
        }
        onExternalResultChanged(true)
    }

    private fun writeExport(uri: Uri, token: Int) {
        activity.lifecycleScope.launch {
            val result = runCatching {
                val raw = store.getString(KEY_EXPORT, null) ?: error(activity.getString(R.string.settings_export_unavailable))
                withContext(Dispatchers.IO) {
                    activity.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(raw) }
                        ?: error(activity.getString(R.string.settings_document_open_failed))
                }
            }
            result.rethrowCancellation()
            if (token != generation || operation != OP_EXPORT) return@launch
            result.onSuccess { successMessage = activity.getString(R.string.settings_backup_saved) }
                .onFailure { errorMessage = activity.getString(R.string.settings_backup_save_failed) }
            clearTransaction(clearMessages = false)
        }
    }

    private fun readImport(uri: Uri, token: Int) {
        activity.lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { readBounded(uri) } }
            result.rethrowCancellation()
            if (token != generation || operation != OP_IMPORT) return@launch
            result.onSuccess {
                operation = OP_PREVIEW; importRaw = it
                store.edit {putString(KEY_OPERATION, OP_PREVIEW).putString(KEY_PREVIEW, it).remove(KEY_URI)}
                parsePreview(it, persist = false)
            }
                .onFailure {
                    errorMessage = activity.getString(R.string.settings_backup_read_failed)
                    clearTransaction(clearMessages = false)
                }
        }
    }

    private fun parsePreview(raw: String, persist: Boolean) {
        val token = generation
        activity.lifecycleScope.launch {
            val state = model.state.first { !it.loading }
            val result = runCatching { withContext(Dispatchers.Default) {
                decodeLayoutBackup(raw, state.apps, state.profiles, scope)
            } }
            result.rethrowCancellation()
            result.onSuccess {
                if (token != generation || operation != OP_PREVIEW) return@onSuccess
                importRaw = raw
                preview = it; pickerPending = false; operation = OP_PREVIEW
                if (persist) store.edit {putString(KEY_OPERATION, OP_PREVIEW).putString(KEY_PREVIEW, raw)}
                onExternalResultChanged(true)
            }.onFailure {
                if (token != generation) return@onFailure
                errorMessage = activity.getString(R.string.settings_backup_invalid)
                clearTransaction(clearMessages = false)
            }
        }
    }

    private fun readBounded(uri: Uri): String {
        val input = activity.contentResolver.openInputStream(uri) ?: error(activity.getString(R.string.settings_document_open_failed))
        return input.use {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_LAYOUT_BACKUP_BYTES) { activity.getString(R.string.settings_backup_too_large) }
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    private fun widgetDescriptors(state: LauncherState): List<BackupWidgetDescriptor> = state.widgetPlacements.mapNotNull { placement ->
        if (placement.id < 0) return@mapNotNull null
        val info = widgets.manager.getAppWidgetInfo(placement.id) ?: error(activity.getString(R.string.settings_widget_slot_unavailable, placement.slot))
        BackupWidgetDescriptor(placement.slot, info.provider.flattenToString(), userManager.getSerialNumberForUser(info.profile),
            info.loadLabel(activity.packageManager).toString(), if (info.profile == android.os.Process.myUserHandle()) activity.getString(R.string.settings_personal) else activity.getString(R.string.settings_work),
            isWork = info.profile != android.os.Process.myUserHandle())
    }

    private fun clearTransaction(clearMessages: Boolean = true) {
        generation++
        operation = null; pickerPending = false; preview = null
        importRaw = null
        store.edit {clear()}
        onExternalResultChanged(false)
        if (clearMessages) clearMessage()
    }

    private fun Result<*>.rethrowCancellation() {
        exceptionOrNull()?.let { if (it is CancellationException) throw it }
    }

    companion object {
        private const val KEY_OPERATION = "operation"
        private const val KEY_PREVIEW = "preview"
        private const val KEY_EXPORT = "export"
        private const val KEY_URI = "uri"
        private const val OP_EXPORT = "export"
        private const val OP_IMPORT = "import"
        private const val OP_PREVIEW = "preview"
    }
}
