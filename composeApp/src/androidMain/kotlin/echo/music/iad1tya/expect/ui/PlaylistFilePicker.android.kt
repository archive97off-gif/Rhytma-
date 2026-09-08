package echo.music.iad1tya.expect.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import echo.music.iad1tya.importer.ImportedPlaylist
import echo.music.iad1tya.importer.SpotifyPlaylistFileImporter
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Composable
actual fun rememberPlaylistFilePicker(
    onSelected: (suspend () -> ImportedPlaylist) -> Unit,
    onError: (String) -> Unit,
): FilePickerLauncher {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onSelected {
            var name: String? = null
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0)
                    require(cursor.isNull(1) || cursor.getLong(1) <= SpotifyPlaylistFileImporter.MAX_BYTES) { "Choose a playlist file smaller than 5 MB." }
                }
            }
            val fileName = name ?: error("Couldn't read the filename. Choose a CSV, TXT or JSON file from Downloads.")
            require(fileName.substringAfterLast('.', "").lowercase() in setOf("csv", "txt", "json")) { "Choose a CSV, TXT or JSON playlist file." }
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= SpotifyPlaylistFileImporter.MAX_BYTES) { "Choose a playlist file smaller than 5 MB." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: error("Couldn't read this file. Download it to your device and try again.")
            SpotifyPlaylistFileImporter().parse(fileName, bytes)
        }
    }
    return object : FilePickerLauncher {
        override fun launch() {
            try {
                // Some document providers label CSV/JSON as octet-stream. Validate extension after selection.
                launcher.launch(arrayOf("text/*", "application/json", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
            } catch (_: Exception) { onError("Couldn't open the file picker on this device.") }
        }
    }
}
