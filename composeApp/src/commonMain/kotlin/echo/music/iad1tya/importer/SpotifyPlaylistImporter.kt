package echo.music.iad1tya.importer

import echo.music.iad1tya.spotify.SpotifyOAuthManager
import echo.music.iad1tya.spotify.Spotify
import echo.music.iad1tya.spotify.SpotifyPlaylistItems
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SpotifyPlaylistImporter(
    private val spotify: Spotify,
    private val oauth: SpotifyOAuthManager,
) : PlaylistImporter {
    override val provider = PlaylistProvider.SPOTIFY
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(url: String): Result<ImportedPlaylist> = try {
        val playlistId = parsePlaylistId(url) ?: error("Enter a valid Spotify playlist link")
        val token = getToken()
        val root = json.parseToJsonElement(spotify.getPlaylist(playlistId, token).getOrThrow()).jsonObject
        val title = root["name"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank) ?: error("Spotify playlist has no name")
        val thumbnail = ((root["images"] as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.get("url")?.jsonPrimitive?.contentOrNull
        val tracks = SpotifyPlaylistItems.fetchAll(playlistId) { pageUrl ->
            spotify.getPlaylistItems(playlistId, token, pageUrl).getOrThrow()
        }.map { track ->
            ImportedTrack(title = track.title, artists = track.artists, durationMs = track.durationMs)
        }
        Result.success(ImportedPlaylist(title = title, thumbnail = thumbnail, tracks = tracks))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun getToken(): String = oauth.accessToken()

    private fun parsePlaylistId(value: String): String? =
        Regex("playlist[/:]([A-Za-z0-9]+)").find(value.trim())?.groupValues?.get(1)
            ?: value.trim().takeIf { it.matches(Regex("[A-Za-z0-9]{16,}")) }
}
