package echo.music.iad1tya.importer

import echo.music.iad1tya.domain.manager.DataStoreManager
import echo.music.iad1tya.spotify.Spotify
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class SpotifyPlaylistImporter(
    private val spotify: Spotify,
    private val dataStoreManager: DataStoreManager,
) : PlaylistImporter {
    override val provider = PlaylistProvider.SPOTIFY
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(url: String): Result<ImportedPlaylist> = runCatching {
        val playlistId = parsePlaylistId(url) ?: error("Enter a valid Spotify playlist link")
        val token = getToken()
        var offset = 0
        var title = ""
        var thumbnail: String? = null
        val tracks = mutableListOf<ImportedTrack>()

        while (true) {
            val root = json.parseToJsonElement(
                spotify.getPlaylist(playlistId, token, offset).getOrThrow(),
            ).jsonObject
            if (title.isBlank()) {
                title = root["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                thumbnail = root["images"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("url")?.jsonPrimitive?.contentOrNull
            }
            val items = root["tracks"]?.jsonObject?.get("items")?.jsonArray.orEmpty()
            items.forEach { item ->
                val track = item.jsonObject["track"]?.jsonObject ?: return@forEach
                val trackTitle = track["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val artists = track["artists"]?.jsonArray.orEmpty().mapNotNull {
                    it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                }
                if (trackTitle.isNotBlank() && artists.isNotEmpty()) {
                    tracks += ImportedTrack(
                        title = trackTitle,
                        artists = artists,
                        durationMs = track["duration_ms"]?.jsonPrimitive?.intOrNull ?: 0,
                    )
                }
            }
            if (items.isEmpty() || items.size < 100) break
            offset += items.size
        }
        if (title.isBlank()) error("Spotify playlist has no name")
        ImportedPlaylist(
            title = title,
            description = null,
            thumbnail = thumbnail,
            tracks = tracks,
        )
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun getToken(): String {
        val existing = dataStoreManager.spotifyPersonalToken.first()
        val expiry = dataStoreManager.spotifyPersonalTokenExpires.first()
        if (existing.isNotBlank() && expiry > Clock.System.now().toEpochMilliseconds()) return existing
        val spdc = dataStoreManager.spdc.first().removePrefix("sp_dc=")
        if (spdc.isBlank()) error("Sign in to Spotify first")
        val refreshed = spotify.getPersonalTokenWithTotp(spdc).getOrThrow()
        dataStoreManager.setSpotifyPersonalToken(refreshed.accessToken)
        dataStoreManager.setSpotifyPersonalTokenExpires(refreshed.accessTokenExpirationTimestampMs)
        return refreshed.accessToken
    }

    private fun parsePlaylistId(value: String): String? =
        Regex("playlist[/:]([A-Za-z0-9]+)").find(value.trim())?.groupValues?.get(1)
            ?: value.trim().takeIf { it.matches(Regex("[A-Za-z0-9]{16,}")) }
}
