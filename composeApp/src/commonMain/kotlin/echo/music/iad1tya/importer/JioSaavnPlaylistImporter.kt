package echo.music.iad1tya.importer

import echo.music.iad1tya.domain.extension.decodeHtmlEntities
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class JioSaavnPlaylistImporter : PlaylistImporter {
    override val provider = PlaylistProvider.JIOSAAVN
    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetch(url: String): Result<ImportedPlaylist> = runCatching {
        val normalizedUrl = url.trim()
        require(Regex("https?://(www\\.)?(jiosaavn|saavn)\\.com/", RegexOption.IGNORE_CASE).containsMatchIn(normalizedUrl)) {
            "Enter a valid public JioSaavn playlist link"
        }
        val page = client.get(normalizedUrl).bodyAsText()
        val listId = listIdFrom(page) ?: listIdFrom(normalizedUrl)
            ?: error("This JioSaavn page does not expose public playlist metadata")

        var pageNumber = 1
        var title: String? = null
        var thumbnail: String? = null
        var declaredTrackCount: Int? = null
        var fetchedItemCount = 0
        val tracks = mutableListOf<ImportedTrack>()

        while (true) {
            val root = fetchPage(listId, pageNumber)
            if (title == null) {
                title = (root["listname"]?.jsonPrimitive?.contentOrNull
                    ?: root["title"]?.jsonPrimitive?.contentOrNull)
                    ?.let(::decodeHtmlEntities)
                    ?: error("JioSaavn playlist has no name")
                thumbnail = root["image"]?.jsonPrimitive?.contentOrNull
                declaredTrackCount = root["list_count"]?.jsonPrimitive?.intOrNull
            }

            val items = (root["list"] ?: root["songs"]) as? JsonArray
                ?: error("JioSaavn playlist response has no song list")
            tracks += items.mapNotNull { (it as? JsonObject)?.toImportedTrack() }
            fetchedItemCount += items.size

            if (
                items.isEmpty() ||
                items.size < PAGE_SIZE ||
                declaredTrackCount?.let { fetchedItemCount >= it } == true
            ) {
                break
            }
            pageNumber++
        }

        if (declaredTrackCount != null && declaredTrackCount > 0 && tracks.isEmpty()) {
            error("JioSaavn returned playlist items, but none contained usable track metadata")
        }

        ImportedPlaylist(
            title = requireNotNull(title),
            thumbnail = thumbnail,
            tracks = tracks,
        )
    }

    private suspend fun fetchPage(listId: String, page: Int): JsonObject {
        val response = client.get(API_URL) {
            parameter("__call", "playlist.getDetails")
            parameter("listid", listId)
            parameter("p", page)
            parameter("n", PAGE_SIZE)
            parameter("api_version", 4)
            parameter("_format", "json")
            parameter("_marker", 0)
            parameter("ctx", "web6dot0")
        }.bodyAsText()
        return json.parseToJsonElement(response).jsonObject
    }

    private fun JsonObject.toImportedTrack(): ImportedTrack? {
        val moreInfo = this["more_info"] as? JsonObject
        val artistMap = moreInfo?.get("artistMap") as? JsonObject
        val mappedArtists = (artistMap?.get("primary_artists") as? JsonArray).orEmpty().mapNotNull {
            (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
        }
        val legacyArtists = (this["primary_artists"]?.jsonPrimitive?.contentOrNull
            ?: this["singers"]?.jsonPrimitive?.contentOrNull.orEmpty())
            .split(',')
        val artists = (mappedArtists.ifEmpty { legacyArtists })
            .map { decodeHtmlEntities(it.trim()) }
            .filter(String::isNotBlank)
            .distinct()
        if (artists.isEmpty()) return null

        val title = (this["title"]?.jsonPrimitive?.contentOrNull
            ?: this["song"]?.jsonPrimitive?.contentOrNull)
            ?.let(::decodeHtmlEntities)
            ?: return null
        val durationSeconds = moreInfo?.get("duration")?.jsonPrimitive?.intOrNull
            ?: this["duration"]?.jsonPrimitive?.intOrNull
            ?: 0

        return ImportedTrack(
            title = title,
            artists = artists,
            durationMs = durationSeconds * 1000,
            album = (moreInfo?.get("album")?.jsonPrimitive?.contentOrNull
                ?: this["album"]?.jsonPrimitive?.contentOrNull)
                ?.let(::decodeHtmlEntities),
            artwork = this["image"]?.jsonPrimitive?.contentOrNull,
            providerTrackId = this["id"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun listIdFrom(value: String): String? {
        val patterns = listOf(
            Regex("[?&](?:listid|list_id)=([^&\\\"]+)", RegexOption.IGNORE_CASE),
            Regex("(?:playlistId|listid|list_id)\\\"?\\s*[:=]\\s*\\\"([^\\\"]+)", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { it.find(value)?.groupValues?.get(1) }
    }

    private companion object {
        const val API_URL = "https://www.jiosaavn.com/api.php"
        const val PAGE_SIZE = 50
    }
}
