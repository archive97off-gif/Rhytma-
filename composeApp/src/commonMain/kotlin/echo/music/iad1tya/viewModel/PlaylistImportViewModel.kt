package echo.music.iad1tya.viewModel

import androidx.lifecycle.viewModelScope
import echo.music.iad1tya.domain.data.entities.LocalPlaylistEntity
import echo.music.iad1tya.domain.data.entities.SongEntity
import echo.music.iad1tya.domain.data.model.searchResult.songs.SongsResult
import echo.music.iad1tya.domain.repository.LocalPlaylistRepository
import echo.music.iad1tya.domain.repository.SearchRepository
import echo.music.iad1tya.domain.repository.SongRepository
import echo.music.iad1tya.domain.utils.Resource
import echo.music.iad1tya.importer.ImportedPlaylist
import echo.music.iad1tya.importer.ImportedTrack
import echo.music.iad1tya.importer.ImportedTrackMatch
import echo.music.iad1tya.importer.SpotifyPlaylistImporter
import echo.music.iad1tya.importer.JioSaavnPlaylistImporter
import echo.music.iad1tya.importer.PlaylistImporter
import echo.music.iad1tya.importer.PlaylistProvider
import echo.music.iad1tya.viewModel.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class PlaylistImportState(
    val provider: PlaylistProvider = PlaylistProvider.SPOTIFY,
    val url: String = "",
    val playlist: ImportedPlaylist? = null,
    val matches: List<ImportedTrackMatch> = emptyList(),
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val error: String? = null,
    val savedPlaylistId: Long? = null,
)

class PlaylistImportViewModel(
    private val importer: SpotifyPlaylistImporter,
    private val searchRepository: SearchRepository,
    private val songRepository: SongRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val jioSaavnImporter: JioSaavnPlaylistImporter,
) : BaseViewModel() {
    private val _state = MutableStateFlow(PlaylistImportState())
    val state: StateFlow<PlaylistImportState> = _state.asStateFlow()

    private val importers: Map<PlaylistProvider, PlaylistImporter> = mapOf(
        PlaylistProvider.SPOTIFY to importer,
        PlaylistProvider.JIOSAAVN to jioSaavnImporter,
    )

    fun setUrl(url: String) {
        _state.value = _state.value.copy(url = url, error = null)
    }

    fun setProvider(provider: PlaylistProvider) {
        _state.value = state.value.copy(provider = provider, url = "", playlist = null, matches = emptyList(), error = null)
    }

    fun fetchAndMatch() {
        val url = state.value.url.trim()
        if (url.isBlank() || state.value.isLoading) return
        viewModelScope.launch(Dispatchers.Default) {
            _state.value = state.value.copy(isLoading = true, error = null, playlist = null, matches = emptyList())
            importers.getValue(state.value.provider).fetch(url).fold(
                onSuccess = { playlist ->
                    _state.value = state.value.copy(playlist = playlist, progress = 0)
                    val matches = playlist.tracks.mapIndexed { index, track ->
                        val match = matchTrack(track)
                        _state.value = state.value.copy(
                            matches = _state.value.matches + match,
                            progress = ((index + 1) * 100 / playlist.tracks.size.coerceAtLeast(1)),
                        )
                        match
                    }
                    _state.value = state.value.copy(isLoading = false, matches = matches, progress = 100)
                },
                onFailure = { error ->
                    _state.value = state.value.copy(isLoading = false, error = error.message ?: "Unable to load Spotify playlist")
                },
            )
        }
    }

    fun saveMatchedTracks() {
        val current = state.value
        val playlist = current.playlist ?: return
        val matched = current.matches.mapNotNull { it.song }
        if (matched.isEmpty() || current.isLoading) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = current.copy(isLoading = true, error = null)
            matched.forEach { songRepository.insertSong(it).firstOrNull() }
            val videoIds = matched.map { it.videoId }
            val playlistId = localPlaylistRepository.insertLocalPlaylistWithTracks(
                LocalPlaylistEntity(
                    title = playlist.title,
                    thumbnail = playlist.thumbnail,
                    tracks = videoIds,
                ),
                videoIds,
            )
            _state.value = _state.value.copy(isLoading = false, savedPlaylistId = playlistId)
        }
    }

    private suspend fun matchTrack(track: ImportedTrack): ImportedTrackMatch {
        val query = (track.artists + track.title).joinToString(" ")
        val result = searchRepository.getSearchDataSong(query).first()
        val candidates = (result as? Resource.Success<ArrayList<SongsResult>>)?.data.orEmpty()
        val best = candidates.maxByOrNull { candidate -> score(track, candidate) }
        if (best == null) return ImportedTrackMatch(track, null, 0.0)
        val confidence = score(track, best)
        return ImportedTrackMatch(
            source = track,
            song = best.takeIf { confidence >= MATCH_THRESHOLD && artistScore(track, it) >= ARTIST_THRESHOLD }
                ?.toSongEntity(),
            confidence = confidence,
        )
    }

    private fun score(track: ImportedTrack, candidate: SongsResult): Double =
        titleScore(track.title, candidate.title.orEmpty()) * 0.55 +
            artistScore(track, candidate) * 0.35 +
            durationScore(track.durationMs, candidate.durationSeconds) * 0.10

    private fun artistScore(track: ImportedTrack, candidate: SongsResult): Double =
        similarity(track.artists.joinToString(" "), candidate.artists.orEmpty().joinToString(" ") { it.name })

    private fun titleScore(source: String, candidate: String): Double = similarity(source, candidate)

    private fun similarity(first: String, second: String): Double {
        val left = normalize(first).split(' ').filter(String::isNotBlank).toSet()
        val right = normalize(second).split(' ').filter(String::isNotBlank).toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return (left intersect right).size.toDouble() / (left union right).size.toDouble()
    }

    private fun normalize(value: String): String = buildString {
        value.lowercase().forEach { character ->
            append(if (character.isLetterOrDigit()) character else ' ')
        }
    }.replace(Regex("\\s+"), " ").trim()

    private fun durationScore(sourceMs: Int, candidateSeconds: Int?): Double {
        if (sourceMs <= 0 || candidateSeconds == null) return 0.5
        return (1.0 - (kotlin.math.abs(sourceMs / 1000 - candidateSeconds) / 30.0)).coerceIn(0.0, 1.0)
    }

    private fun SongsResult.toSongEntity() = SongEntity(
        videoId = videoId,
        albumId = album?.id,
        albumName = album?.name,
        artistId = artists?.mapNotNull { it.id },
        artistName = artists?.map { it.name },
        duration = duration.orEmpty(),
        durationSeconds = durationSeconds ?: 0,
        isAvailable = true,
        isExplicit = isExplicit == true,
        likeStatus = "",
        thumbnails = thumbnails?.firstOrNull()?.url,
        title = title.orEmpty(),
        videoType = videoType.orEmpty(),
        category = category,
        resultType = resultType,
        liked = false,
        totalPlayTime = 0,
        favoriteAt = null,
        downloadedAt = null,
    )

    companion object {
        private const val MATCH_THRESHOLD = 0.68
        private const val ARTIST_THRESHOLD = 0.35
    }
}
