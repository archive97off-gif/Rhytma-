package echo.music.iad1tya.importer

import echo.music.iad1tya.domain.data.entities.SongEntity

enum class PlaylistProvider {
    SPOTIFY,
    JIOSAAVN,
}

data class ImportedTrack(
    val title: String,
    val artists: List<String>,
    val durationMs: Int,
    val album: String? = null,
    val artwork: String? = null,
    val providerTrackId: String? = null,
)

data class ImportedPlaylist(
    val title: String,
    val description: String? = null,
    val thumbnail: String?,
    val tracks: List<ImportedTrack>,
)

data class ImportedTrackMatch(
    val source: ImportedTrack,
    val song: SongEntity?,
    val confidence: Double,
)

interface PlaylistImporter {
    val provider: PlaylistProvider

    suspend fun fetch(url: String): Result<ImportedPlaylist>
}