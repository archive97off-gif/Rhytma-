package echo.music.iad1tya.expect.ui

import androidx.compose.runtime.Composable
import echo.music.iad1tya.importer.ImportedPlaylist

@Composable
expect fun rememberPlaylistFilePicker(
    onSelected: (suspend () -> ImportedPlaylist) -> Unit,
    onError: (String) -> Unit,
): FilePickerLauncher
