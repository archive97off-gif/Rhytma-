package echo.music.iad1tya.ui.screen.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.music.iad1tya.importer.ImportedPlaylist
import echo.music.iad1tya.importer.ImportedTrack
import echo.music.iad1tya.importer.PlaylistProvider
import echo.music.iad1tya.ui.icon.*
import echo.music.iad1tya.ui.theme.LocalIsDarkTheme
import echo.music.iad1tya.viewModel.PlaylistImportState

/** Brand tint is local to the importer; all surfaces and text follow the app theme. */
@Composable
internal fun spotifyImportAccent(): Color =
    if (LocalIsDarkTheme.current) Color(0xFFA9E2BD) else Color(0xFF24613C)

@Composable
internal fun ImportGlassCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.background(Brush.linearGradient(listOf(
                colors.surfaceContainerHigh.copy(alpha = 0.88f),
                colors.surfaceContainerLow.copy(alpha = 0.72f),
            ))).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
internal fun ImportServiceSelector(provider: PlaylistProvider, enabled: Boolean, onSelect: (PlaylistProvider) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerLow).selectableGroup().padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PlaylistProvider.entries.forEach { service ->
            val selected = service == provider
            val accent = if (service == PlaylistProvider.SPOTIFY) spotifyImportAccent() else colors.primary
            val background by animateColorAsState(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
            val foreground by animateColorAsState(if (selected) accent else colors.onSurfaceVariant)
            Surface(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = { if (!selected) onSelect(service) }),
                shape = RoundedCornerShape(16.dp),
                color = background,
                border = if (selected) BorderStroke(1.dp, accent.copy(alpha = 0.35f)) else null,
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected) Icon(echoIcons.Check, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
                    Text(if (service == PlaylistProvider.SPOTIFY) "Spotify" else "JioSaavn", color = foreground,
                        style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
internal fun SpotifyImportHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Text("YOUR MUSIC, TOGETHER", color = spotifyImportAccent(), style = MaterialTheme.typography.labelLarge, letterSpacing = 1.5.sp)
        Text("Spotify Playlist Import", color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, lineHeight = 38.sp),
            fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        Text("Transfer your Spotify playlist to Rhytma.", color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp))
        ImportSupportingText("Free file importing. No Premium developer access needed. Export your playlist and Rhytma will match the songs for you.")
    }
}

@Composable
internal fun ImportSupportingText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp))
}

@Composable
internal fun ImportGuideCard(compact: Boolean, onOpenWebsite: () -> Unit) {
    var expanded by remember(compact) { mutableStateOf(!compact) }
    ImportGlassCard {
        Text("HOW TO IMPORT", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge,
            letterSpacing = 1.sp, modifier = Modifier.semantics { heading() })
        if (compact) ImportTextAction(if (expanded) "Hide steps" else "Show steps", { expanded = !expanded })
        if (expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Open TuneMyMusic", "Choose Spotify and connect your account", "Select the playlist you want",
                "Export it as CSV or TXT", "Return to Rhytma and choose the file", "Preview, match and import").forEachIndexed { index, text ->
                ImportStep(index + 1, text)
            }
        }
        ImportAction("Open TuneMyMusic", onOpenWebsite, primary = false, icon = echoIcons.ArrowOutward,
            iconDescription = "Opens external website")
        }
    }
}

@Composable
private fun ImportStep(number: Int, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(CircleShape).background(spotifyImportAccent().copy(alpha = 0.10f)).padding(8.dp), contentAlignment = Alignment.Center) {
            Text(number.toString().padStart(2, '0'), color = spotifyImportAccent(), style = MaterialTheme.typography.labelLarge)
        }
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp))
    }
}

/** Explicit foreground prevents the app's colored TextStyles overriding button contrast. */
@Composable
internal fun ImportAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = true,
    icon: ImageVector? = null,
    iconDescription: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = if (primary) colors.onSurface else colors.surfaceContainerLow.copy(alpha = 0.75f),
        contentColor = if (primary) colors.surface else colors.onSurface,
        disabledContainerColor = colors.surfaceContainerHighest,
        disabledContentColor = colors.onSurfaceVariant,
    )
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp), colors = buttonColors,
        border = if (primary) null else BorderStroke(1.dp, colors.outlineVariant),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = iconDescription, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, color = LocalContentColor.current, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
internal fun ImportTextAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
        Text(label, color = LocalContentColor.current, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun PlaylistFileAction(hasPlaylist: Boolean, enabled: Boolean, onChoose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ImportAction(if (hasPlaylist) "Change Playlist File" else "Choose Playlist File", onChoose, enabled,
            primary = !hasPlaylist, icon = echoIcons.Add)
        ImportSupportingText("CSV \u2022 TXT \u2022 JSON  /  Maximum 5 MB")
    }
}

@Composable
internal fun ImportPrivacyRow() {
    Row(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(echoIcons.Info, contentDescription = null, tint = spotifyImportAccent(), modifier = Modifier.size(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("File processed locally", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
            ImportSupportingText("Only track metadata: title, artist and album. Your Spotify password is never entered into Rhytma.")
        }
    }
}

@Composable
internal fun SelectedPlaylistCard(playlist: ImportedPlaylist, fromFile: Boolean) {
    ImportGlassCard {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(echoIcons.LibraryMusic, contentDescription = null, tint = spotifyImportAccent(), modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (fromFile) "PLAYLIST FILE READY" else "PLAYLIST LOADED", color = spotifyImportAccent(), style = MaterialTheme.typography.labelLarge)
                Text(playlist.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() })
                ImportSupportingText("${playlist.tracks.size} tracks")
            }
            Icon(echoIcons.CheckCircle, contentDescription = "Playlist loaded", tint = spotifyImportAccent(), modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
internal fun PlaylistPreviewRow(track: ImportedTrack, number: Int, status: String? = null, unmatched: Boolean = false) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(number.toString().padStart(2, '0'), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 26.dp).padding(top = 2.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(track.title, color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp), fontWeight = FontWeight.Medium)
                ImportSupportingText(track.artists.joinToString(", "))
                if (status != null) Text(status, color = if (unmatched) MaterialTheme.colorScheme.error else spotifyImportAccent(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}

@Composable
internal fun ImportProgressCard(state: PlaylistImportState, onCancel: () -> Unit) {
    val progress by animateFloatAsState(state.progress / 100f)
    ImportGlassCard {
        Text(when {
            state.isSaving -> "Creating your playlist"
            state.playlist == null -> "Reading your playlist"
            else -> "Matching your playlist"
        }, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        if (state.playlist != null && !state.isSaving) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp),
                color = spotifyImportAccent(), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            ImportSupportingText("${state.matches.size} / ${state.playlist.tracks.size} tracks")
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = spotifyImportAccent(),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        }
        state.currentTrack?.let { track ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ImportSupportingText("Currently matching")
                Text(track.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                ImportSupportingText(track.artists.joinToString(", "))
            }
        }
        if (!state.isSaving) ImportTextAction("Cancel", onCancel)
    }
}

@Composable
internal fun ImportResultCard(state: PlaylistImportState, onOpen: (Long) -> Unit) {
    val matched = state.matches.count { it.song != null }
    val unmatched = state.matches.size - matched
    ImportGlassCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (state.savedPlaylistId != null) echoIcons.CheckCircle else echoIcons.LibraryMusic,
                contentDescription = null, tint = spotifyImportAccent(), modifier = Modifier.size(28.dp))
            Text(if (state.savedPlaylistId != null) "Playlist imported" else "Matching complete",
                color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() })
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$matched songs matched", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
            ImportSupportingText("$unmatched couldn't be found")
        }
        if (matched == 0) ImportSupportingText("No playlist was created. Check the metadata or your connection, then try again.")
        state.savedPlaylistId?.let { id -> ImportAction("Open Playlist", { onOpen(id) }, icon = echoIcons.PlayArrow) }
    }
}
