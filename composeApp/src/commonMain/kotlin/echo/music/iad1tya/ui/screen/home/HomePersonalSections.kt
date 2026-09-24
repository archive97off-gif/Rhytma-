package echo.music.iad1tya.ui.screen.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.Role
import echo.music.iad1tya.ui.component.NowPlayingBottomSheet
import echo.music.iad1tya.domain.utils.toSongEntity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import echo.music.iad1tya.common.Config
import echo.music.iad1tya.domain.data.entities.LocalPlaylistEntity
import echo.music.iad1tya.domain.data.entities.SongEntity
import echo.music.iad1tya.domain.data.model.home.HomeItem
import echo.music.iad1tya.domain.extension.now
import echo.music.iad1tya.domain.mediaservice.handler.PlaylistType
import echo.music.iad1tya.domain.mediaservice.handler.QueueData
import echo.music.iad1tya.domain.utils.toTrack
import echo.music.iad1tya.ui.component.rememberHolderPainter
import echo.music.iad1tya.ui.icon.*
import echo.music.iad1tya.ui.navigation.destination.list.*
import echo.music.iad1tya.viewModel.HomeViewModel
import org.jetbrains.compose.resources.stringResource
import echomusic.composeapp.generated.resources.*

@Composable
internal fun HomeGreeting() {
    val colors = MaterialTheme.colorScheme
    val hour = now().hour
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(
            colors.primaryContainer.copy(alpha = 0.24f), colors.background,
        ))).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(when (hour) {
            in 5..11 -> Res.string.good_morning
            in 12..17 -> Res.string.good_afternoon
            else -> Res.string.good_evening
        }), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
            color = colors.onBackground, modifier = Modifier.semantics { heading() })
        Text("A little more music in your day.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}

@Composable
internal fun HomeSectionHeading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null) TextButton(onClick = onAction) {
            Text(action, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun HomeArtwork(url: String?, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank()) {
            Icon(echoIcons.LibraryMusic, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(28.dp))
        } else {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop,
                placeholder = rememberHolderPainter(), error = rememberHolderPainter(), modifier = Modifier.matchParentSize())
        }
    }
}

/** Finite local shortcuts, not a nested scrolling grid. Font scaling can reduce the column count. */
@Composable
internal fun HomeQuickAccess(playlists: List<LocalPlaylistEntity>, onOpen: (LocalPlaylistEntity) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeading("Quick access")
        BoxWithConstraints {
            val columns = when {
                maxWidth.value / fontScale >= 660 -> 3
                maxWidth.value / fontScale >= 320 -> 2
                else -> 1
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                playlists.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { playlist ->
                            Surface(onClick = { onOpen(playlist) }, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))) {
                                Row(Modifier.heightIn(min = 64.dp).padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HomeArtwork(playlist.thumbnail, Modifier.size(48.dp))
                                    Text(playlist.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeArtworkCard(title: String, subtitle: String?, artwork: String?, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Surface(modifier = Modifier.clip(RoundedCornerShape(20.dp)).combinedClickable(
        role = Role.Button, onClick = onClick, onLongClick = onLongClick,
        onLongClickLabel = if (onLongClick != null) "Song options" else null,
    ), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeArtwork(artwork, Modifier.fillMaxWidth().aspectRatio(1f))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun HomeRecentShelf(songs: List<SongEntity>, onSeeAll: () -> Unit, onPlay: (SongEntity) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(horizontal = 20.dp)) { HomeSectionHeading("Recently played", "See all", onSeeAll) }
        HomeShelf { width ->
            items(songs, key = { it.videoId }) { song ->
                Box(Modifier.width(width)) { HomeArtworkCard(song.title, song.artistName?.joinToString(", "), song.thumbnails) { onPlay(song) } }
            }
        }
    }
}

@Composable
internal fun HomePlaylistShelf(playlists: List<LocalPlaylistEntity>, onCreate: () -> Unit, onOpen: (LocalPlaylistEntity) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(horizontal = 20.dp)) { HomeSectionHeading("Your playlists", "+ Create", onCreate) }
        if (playlists.isEmpty()) {
            Text("Your collection starts here. Create a playlist or bring one you already love.",
                modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        } else HomeShelf { width ->
            items(playlists, key = { it.id }) { playlist ->
                Box(Modifier.width(width)) { HomeArtworkCard(playlist.title, "${playlist.tracks.orEmpty().size} songs", playlist.thumbnail) { onOpen(playlist) } }
            }
        }
    }
}

/** Artwork has a stable ratio; text height is unconstrained and scales independently below it. */
@Composable
private fun HomeShelf(content: androidx.compose.foundation.lazy.LazyListScope.(androidx.compose.ui.unit.Dp) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = (maxWidth * 0.44f).coerceIn(148.dp, 200.dp) * fontScale.coerceIn(1f, 1.35f)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            content(width)
        }
    }
}

@Composable
internal fun HomeDiscoveryShelf(data: HomeItem, navController: NavController, viewModel: HomeViewModel) {
    if (data.contents.none { it != null }) return
    var selectedSong by remember { mutableStateOf<SongEntity?>(null) }
    selectedSong?.let { song ->
        NowPlayingBottomSheet(onDismiss = { selectedSong = null }, song = song, navController = navController)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HomeSectionHeading(data.title)
            if (!data.subtitle.isNullOrBlank()) Text(data.subtitle.orEmpty(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HomeShelf { width ->
            itemsIndexed(data.contents) { _, item ->
                if (item != null) Box(Modifier.width(width)) {
                    HomeArtworkCard(item.title, item.artists?.joinToString(", ") { it.name }, item.thumbnails.lastOrNull()?.url,
                        onLongClick = if (!item.videoId.isNullOrBlank()) ({ selectedSong = item.toTrack().toSongEntity() }) else null,
                    ) {
                        val playlistId = item.playlistId
                        val browseId = item.browseId
                        if (!item.videoId.isNullOrBlank()) {
                            val track = item.toTrack()
                            viewModel.setQueueData(QueueData.Data(
                                listTracks = arrayListOf(track), firstPlayedTrack = track,
                                playlistId = "RDAMVM${item.videoId}", playlistName = item.title,
                                playlistType = PlaylistType.RADIO, continuation = null,
                            ))
                            viewModel.loadMediaItem(track, Config.SONG_CLICK)
                        } else if (!playlistId.isNullOrBlank()) {
                            if (playlistId.startsWith("UC")) navController.navigate(ArtistDestination(playlistId))
                            else navController.navigate(PlaylistDestination(playlistId))
                        } else if (!browseId.isNullOrBlank()) {
                            when {
                                browseId.startsWith("UC") -> navController.navigate(ArtistDestination(browseId))
                                browseId.startsWith("MPSP") -> navController.navigate(PodcastDestination(browseId))
                                else -> navController.navigate(AlbumDestination(browseId))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeEmptyDiscovery(message: String, onRetry: () -> Unit) {
    Surface(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRetry) { Text("Try again", color = MaterialTheme.colorScheme.primary) }
        }
    }
}
