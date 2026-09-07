package echo.music.iad1tya.ui.screen.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import echo.music.iad1tya.importer.ImportedTrackMatch
import echo.music.iad1tya.importer.PlaylistProvider
import echo.music.iad1tya.ui.navigation.destination.list.LocalPlaylistDestination
import echo.music.iad1tya.viewModel.PlaylistImportViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyPlaylistImportScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: PlaylistImportViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedPlaylistId) {
        state.savedPlaylistId?.let { navController.navigate(LocalPlaylistDestination(it)) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Import playlist") },
                navigationIcon = {
                    TextButton(onClick = navController::navigateUp) { Text("Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.setProvider(PlaylistProvider.SPOTIFY) },
                        modifier = Modifier.weight(1f),
                        enabled = state.provider != PlaylistProvider.SPOTIFY && !state.isLoading,
                    ) { Text("Spotify") }
                    Button(
                        onClick = { viewModel.setProvider(PlaylistProvider.JIOSAAVN) },
                        modifier = Modifier.weight(1f),
                        enabled = state.provider != PlaylistProvider.JIOSAAVN && !state.isLoading,
                    ) { Text("JioSaavn") }
                }
            }
            if (state.provider == PlaylistProvider.SPOTIFY) {
                item {
                    echo.music.iad1tya.ui.component.SpotifyConnectCard(enabled = !state.isLoading)
                }
            }
            item {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = viewModel::setUrl,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("${if (state.provider == PlaylistProvider.SPOTIFY) "Spotify" else "JioSaavn"} playlist URL") },
                    singleLine = true,
                    enabled = !state.isLoading,
                )
            }
            item {
                Button(
                    onClick = viewModel::fetchAndMatch,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.url.isNotBlank() && !state.isLoading,
                ) {
                    Text("Fetch and match tracks")
                }
            }
            state.error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
            state.playlist?.let { playlist ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(playlist.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${playlist.tracks.size} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.isLoading) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(if (state.playlist == null) "Loading playlist" else "Matching tracks: ${state.progress}%")
                    }
                }
            }
            if (state.matches.isNotEmpty()) {
                item {
                    Text(
                        "Matched ${state.matches.count { it.song != null }} of ${state.matches.size}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(state.matches, key = { it.source.title + it.source.artists.joinToString() + it.source.durationMs }) {
                    ImportedTrackRow(it)
                }
                item {
                    Button(
                        onClick = viewModel::saveMatchedTracks,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.matches.any { it.song != null } && !state.isLoading,
                    ) {
                        Text("Import playlist")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportedTrackRow(match: ImportedTrackMatch) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(match.source.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(match.source.artists.joinToString(" "), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (match.song == null) "Not matched" else "Matched: ${match.song.title}",
            color = if (match.song == null) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}