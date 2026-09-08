package echo.music.iad1tya.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import echo.music.iad1tya.expect.ui.rememberPlaylistFilePicker
import echo.music.iad1tya.importer.PlaylistProvider
import echo.music.iad1tya.ui.component.SpotifyConnectCard
import echo.music.iad1tya.ui.icon.ArrowBackIosNew
import echo.music.iad1tya.ui.icon.echoIcons
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
    var advanced by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val filePicker = rememberPlaylistFilePicker(viewModel::chooseFile, viewModel::showFileError)
    val colors = MaterialTheme.colorScheme
    val layoutDirection = LocalLayoutDirection.current
    // Scope the contrast correction to this screen, including the unchanged advanced connect UI.
    val typography = MaterialTheme.typography
    MaterialTheme(typography = typography.copy(
        bodyLarge = typography.bodyLarge.copy(color = Color.Unspecified),
        labelLarge = typography.labelLarge.copy(color = Color.Unspecified),
    )) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.background,
            topBar = {
                TopAppBar(
                    title = { Text("Import playlist", color = colors.onSurface, style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(echoIcons.ArrowBackIosNew, contentDescription = "Back", tint = colors.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).padding(
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = (innerPadding.calculateBottomPadding() - padding.calculateBottomPadding()).coerceAtLeast(0.dp),
            )
                .background(Brush.verticalGradient(listOf(colors.surfaceContainerLow, colors.background))),
                contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item {
                        ImportServiceSelector(state.provider, !state.isLoading, viewModel::setProvider)
                    }
                    if (state.provider == PlaylistProvider.SPOTIFY) {
                        item { SpotifyImportHeader() }
                        item { PlaylistFileAction(state.playlist != null, !state.isLoading, filePicker::launch) }
                        item {
                            ImportGuideCard(compact = state.playlist != null || state.isLoading) {
                                try { uriHandler.openUri("https://www.tunemymusic.com/") }
                                catch (_: Exception) { viewModel.showFileError("Couldn't open a browser. Visit tunemymusic.com to export your playlist.") }
                            }
                        }
                        item { ImportPrivacyRow() }
                    }
                    if (state.provider == PlaylistProvider.JIOSAAVN) {
                        item {
                            ImportUrlForm(state.url, false, state.isLoading, viewModel::setUrl, viewModel::fetchAndMatch)
                        }
                    }
                    state.error?.let { message ->
                        item {
                            ImportGlassCard {
                                Text("Import needs attention", color = colors.error, style = MaterialTheme.typography.titleMedium)
                                Text(message, color = colors.onSurface, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    state.playlist?.let { playlist ->
                        item { SelectedPlaylistCard(playlist, state.isFileImport) }
                    }
                    if (state.isFileImport && state.playlist != null && !state.matchingComplete && !state.isLoading) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ImportAction("Import & Match ${state.playlist!!.tracks.size} Songs", viewModel::importFile)
                                ImportTextAction("Cancel", viewModel::clearFile)
                                Text("Playlist preview", color = colors.onSurface, style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.semantics { heading() })
                                ImportSupportingText("TXT without a header is read as title then artist. Repeated tracks are kept.")
                            }
                        }
                        itemsIndexed(state.playlist!!.tracks) { index, track -> PlaylistPreviewRow(track, index + 1) }
                    }
                    if (state.isLoading) {
                        item { ImportProgressCard(state, viewModel::cancelImport) }
                    }
                    if (state.matchingComplete) {
                        item { ImportResultCard(state) { id -> navController.navigate(LocalPlaylistDestination(id)) } }
                        if (state.matches.any { it.song == null }) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Unmatched songs", color = colors.onSurface, style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.semantics { heading() })
                                    ImportSupportingText("These songs couldn't be found. Your matched songs can still be imported.")
                                }
                            }
                            itemsIndexed(state.matches.filter { it.song == null }) { index, match ->
                                PlaylistPreviewRow(match.source, index + 1, status = "Not matched", unmatched = true)
                            }
                        }
                        if (!state.isFileImport) {
                            itemsIndexed(state.matches.filter { it.song != null }) { index, match ->
                                PlaylistPreviewRow(match.source, index + 1, status = "Matched: ${match.song!!.title}")
                            }
                        }
                        if (state.savedPlaylistId == null && state.matches.any { it.song != null }) {
                            item {
                                ImportAction(if (state.isFileImport) "Retry saving playlist" else "Import playlist",
                                    viewModel::saveMatchedTracks, enabled = !state.isLoading)
                            }
                        }
                        if (state.isFileImport && state.savedPlaylistId == null && !state.isLoading) {
                            item { ImportTextAction("Retry matching", viewModel::importFile) }
                        }
                    }
                    if (state.provider == PlaylistProvider.SPOTIFY) {
                        item {
                            ImportTextAction(if (advanced) "Hide advanced import" else "Advanced: Spotify API import",
                                { advanced = !advanced }, enabled = !state.isLoading)
                        }
                        if (advanced) {
                            item {
                                ImportGlassCard {
                                    ImportSupportingText("API importing requires developer setup and may require Premium access. File importing needs no Spotify API credentials.")
                                    SpotifyConnectCard(enabled = !state.isLoading)
                                }
                            }
                        }
                    }
                    // Keep the same URL action availability when the advanced section is open.
                    if (state.provider == PlaylistProvider.SPOTIFY && advanced) {
                        item { ImportUrlForm(state.url, true, state.isLoading, viewModel::setUrl, viewModel::fetchAndMatch) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportUrlForm(url: String, spotify: Boolean, loading: Boolean, onUrl: (String) -> Unit, onFetch: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = url, onValueChange = onUrl, modifier = Modifier.fillMaxWidth(),
            label = { Text("${if (spotify) "Spotify" else "JioSaavn"} playlist URL") }, singleLine = true, enabled = !loading)
        ImportAction("Fetch and match tracks", onFetch, enabled = url.isNotBlank() && !loading)
    }
}
