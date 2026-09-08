package echo.music.iad1tya.importer

import kotlinx.serialization.json.*

/** Local metadata only. Deliberately accepts playlist shapes, never arbitrary recursive JSON. */
class SpotifyPlaylistFileImporter {

    fun parse(fileName: String, bytes: ByteArray): ImportedPlaylist {
        require(bytes.size <= MAX_BYTES) {
            "Choose a playlist file smaller than 5 MB."
        }

        val extension = fileName.substringAfterLast('.', "").lowercase()

        require(extension in setOf("csv", "txt", "json")) {
            "Choose a CSV, TXT or JSON playlist file."
        }

        val text = try {
            bytes.decodeToString(throwOnInvalidSequence = true)
                .removePrefix("\uFEFF")
        } catch (_: Exception) {
            throw IllegalArgumentException("Use a UTF-8 playlist export.")
        }

        require(text.isNotBlank()) {
            "This playlist file is empty."
        }

        val fallback = fallbackPlaylistName(fileName)

        val playlist = when (extension) {
            "json" -> json(text, fallback)

            "csv", "txt" -> {
                if (looksLikeRealCsv(text)) {
                    csv(text, fallback)
                } else {
                    // Some TuneMyMusic exports are line-based:
                    // Artist - Song Title
                    // even when their filename ends in .csv or .txt.csv.
                    txt(
                        text = text,
                        fallback = fallback,
                        artistFirstByDefault = extension == "csv",
                    )
                }
            }

            else -> error("Unsupported playlist format.")
        }

        require(playlist.tracks.isNotEmpty()) {
            "No tracks with both a title and artist were found."
        }

        require(playlist.tracks.size <= MAX_TRACKS) {
            "Import up to $MAX_TRACKS tracks at a time."
        }

        return playlist
    }

    /**
     * Removes stacked playlist extensions.
     *
     * Example:
     * My Spotify Library.txt.csv
     * becomes:
     * My Spotify Library
     */
    private fun fallbackPlaylistName(fileName: String): String {
        var name = fileName.trim()

        while (
            name.endsWith(".csv", ignoreCase = true) ||
            name.endsWith(".txt", ignoreCase = true) ||
            name.endsWith(".json", ignoreCase = true)
        ) {
            name = name.substringBeforeLast('.')
        }

        return name.ifBlank { "Spotify playlist" }
    }

    private fun key(value: String) =
        value.lowercase().filter(Char::isLetterOrDigit)

    private val titles = setOf(
        "trackname",
        "title",
        "song",
        "songname",
        "name",
        "track",
    )

    private val artists = setOf(
        "artist",
        "artistname",
        "artists",
        "artistnames",
        "trackartist",
    )

    /**
     * A file ending in .csv is not automatically treated as CSV.
     *
     * We only consider it structured CSV if the first meaningful row
     * contains recognizable title AND artist columns.
     */
    private fun looksLikeRealCsv(text: String): Boolean {
        val firstLine = text
            .lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() }
            ?: return false

        val row = try {
            csvRows(firstLine).firstOrNull() ?: return false
        } catch (_: Exception) {
            return false
        }

        val header = row.map(::key)

        val hasTitle = header.any { it in titles }
        val hasArtist = header.any { it in artists }

        return row.size >= 2 && hasTitle && hasArtist
    }

    private fun csv(text: String, fallback: String): ImportedPlaylist {
        val rows = csvRows(text)

        val header = rows
            .firstOrNull()
            ?.map(::key)
            .orEmpty()

        val titleIndex = header.indexOfFirst { it in titles }
        val artistIndex = header.indexOfFirst { it in artists }

        require(titleIndex >= 0 && artistIndex >= 0) {
            "CSV needs song title and artist columns. Try a playlist CSV export."
        }

        fun List<String>.field(names: Set<String>) =
            getOrNull(header.indexOfFirst { it in names })
                ?.trim()
                ?.takeIf(String::isNotEmpty)

        val names = rows
            .drop(1)
            .mapNotNull {
                it.field(setOf("playlist", "playlistname"))
            }
            .distinct()

        require(names.size <= 1) {
            "This file contains several playlists. Export one playlist at a time."
        }

        val tracks = rows
            .drop(1)
            .mapIndexed { index, row ->

                require(row.size == header.size) {
                    "CSV row ${index + 2} has a different number of columns."
                }

                track(
                    title = row[titleIndex],
                    artists = listOf(row[artistIndex]),
                    album = row.field(setOf("album", "albumname")),
                    uri = row.field(
                        setOf(
                            "url",
                            "spotifyurl",
                            "trackuri",
                            "spotifyuri",
                        ),
                    ),
                    duration = row
                        .field(setOf("durationms"))
                        ?.toIntOrNull()
                        ?: 0,
                    row = index + 2,
                )
            }

        return ImportedPlaylist(
            names.firstOrNull() ?: fallback,
            thumbnail = null,
            tracks = tracks,
        )
    }

    /** RFC 4180 quoting, escaped quotes, embedded newlines, CRLF and trailing fields. */
    private fun csvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()

        var row = mutableListOf<String>()
        val field = StringBuilder()

        var quoted = false
        var closed = false
        var i = 0

        fun endField() {
            row.add(field.toString().trim())
            field.clear()
            closed = false
        }

        fun endRow() {
            endField()

            if (row.any(String::isNotBlank)) {
                rows.add(row)
            }

            require(rows.size <= MAX_TRACKS + 1) {
                "Import up to $MAX_TRACKS tracks at a time."
            }

            row = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i++]

            if (quoted) {
                if (c == '"') {
                    if (i < text.length && text[i] == '"') {
                        field.append('"')
                        i++
                    } else {
                        quoted = false
                        closed = true
                    }
                } else {
                    field.append(c)
                }
            } else {
                when {
                    c == ',' -> endField()

                    c == '\r' || c == '\n' -> {
                        endRow()

                        if (
                            c == '\r' &&
                            i < text.length &&
                            text[i] == '\n'
                        ) {
                            i++
                        }
                    }

                    c == '"' && field.isBlank() && !closed -> {
                        field.clear()
                        quoted = true
                    }

                    c == '"' || (closed && !c.isWhitespace()) -> {
                        error(
                            "Invalid CSV quoting. Export the playlist as CSV again.",
                        )
                    }

                    !closed -> field.append(c)
                }
            }
        }

        require(!quoted) {
            "The CSV has an unfinished quoted field."
        }

        endRow()

        return rows
    }

    /**
     * Parses simple line-based playlist exports.
     *
     * Important:
     * TuneMyMusic-style disguised CSV files use:
     *
     * Artist - Song Title
     *
     * We split only at the FIRST delimiter so:
     *
     * Artist - Song - Extended Version
     *
     * becomes:
     * artist = Artist
     * title  = Song - Extended Version
     */
    private fun txt(
        text: String,
        fallback: String,
        artistFirstByDefault: Boolean = false,
    ): ImportedPlaylist {

        val lines = text
            .lines()
            .map(String::trim)
            .filter {
                it.isNotEmpty() &&
                    !it.startsWith("#") &&
                    !it.startsWith("Playlist:", true)
            }

        val message =
            "We couldn't understand this playlist format. Try exporting the playlist as CSV or TXT again."

        require(lines.isNotEmpty()) {
            message
        }

        require(lines.size <= MAX_TRACKS + 1) {
            "Import up to $MAX_TRACKS tracks at a time."
        }

        val delimiter = listOf(
            "\t",
            " | ",
            "|",
            " - ",
        ).firstOrNull {
            lines.first().contains(it)
        } ?: error(message)

        /**
         * Split ONLY once.
         *
         * This preserves additional " - " characters inside song titles.
         */
        fun splitLine(line: String): List<String> =
            line
                .split(delimiter, limit = 2)
                .map(String::trim)

        val first = splitLine(lines.first())

        require(first.size == 2) {
            message
        }

        val firstKeys = first.map(::key)

        val headerArtistFirst =
            firstKeys[0] in artists &&
                firstKeys[1] in titles

        val headerTitleFirst =
            firstKeys[0] in titles &&
                firstKeys[1] in artists

        val hasHeader =
            headerArtistFirst || headerTitleFirst

        val artistFirst = when {
            headerArtistFirst -> true
            headerTitleFirst -> false
            else -> artistFirstByDefault
        }

        val tracks = lines
            .drop(if (hasHeader) 1 else 0)
            .mapIndexed { index, line ->

                val fields = splitLine(line)

                require(
                    fields.size == 2 &&
                        fields[0].isNotBlank() &&
                        fields[1].isNotBlank(),
                ) {
                    "Couldn't understand track at line ${index + 1}."
                }

                // Only the artist-first format defines the remainder as a song title.
                require(artistFirst || !fields[1].contains(delimiter)) {
                    "Ambiguous track at line ${index + 1}."
                }

                val artist: String
                val title: String

                if (artistFirst) {
                    artist = fields[0]
                    title = fields[1]
                } else {
                    title = fields[0]
                    artist = fields[1]
                }

                track(
                    title = title,
                    artists = listOf(artist),
                    row = index + 1,
                )
            }

        return ImportedPlaylist(
            fallback,
            thumbnail = null,
            tracks = tracks,
        )
    }

    private fun json(
        text: String,
        fallback: String,
    ): ImportedPlaylist {

        // Limit nesting before parsing to avoid stack exhaustion
        // from hostile input.
        var depth = 0
        var inString = false
        var escaped = false

        text.forEach { c ->
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true

                    '{', '[' -> {
                        depth++

                        require(depth <= 32) {
                            "JSON is too deeply nested."
                        }
                    }

                    '}', ']' -> depth--
                }
            }
        }

        val root = try {
            Json.parseToJsonElement(text)
        } catch (_: Exception) {
            error(
                "This JSON file is malformed. Export it again.",
            )
        }

        var obj = root as? JsonObject

        if (obj?.containsKey("playlists") == true) {
            val playlists =
                obj["playlists"] as? JsonArray
                    ?: error(
                        "Expected a JSON playlist list.",
                    )

            require(playlists.size == 1) {
                "This file contains several playlists. Export one playlist at a time."
            }

            obj =
                playlists.single() as? JsonObject
                    ?: error(
                        "Invalid playlist object.",
                    )
        }

        val entries =
            when (val tracks = obj?.get("tracks")) {
                is JsonArray -> tracks

                is JsonObject ->
                    tracks["items"] as? JsonArray

                else ->
                    obj?.get("items") as? JsonArray
                        ?: root as? JsonArray
            }
                ?: error(
                    "This JSON doesn't contain a supported playlist with tracks.",
                )

        require(entries.size <= MAX_TRACKS) {
            "Import up to $MAX_TRACKS tracks at a time."
        }

        val tracks = entries.mapIndexed { index, entry ->

            val outer =
                entry as? JsonObject
                    ?: error(
                        "Invalid track at row ${index + 1}.",
                    )

            val item =
                outer["track"] as? JsonObject
                    ?: outer

            val title =
                item.string(
                    "title",
                    "name",
                    "trackName",
                    "track",
                    "song",
                )

            val artistElement =
                item["artists"]
                    ?: item["artist"]
                    ?: item["artistName"]

            fun artistName(
                e: JsonElement,
            ): String? =
                (e as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.contentOrNull
                    ?: (e as? JsonObject)
                        ?.string(
                            "name",
                            "artistName",
                        )

            val names =
                if (artistElement is JsonArray) {
                    artistElement.mapNotNull(
                        ::artistName,
                    )
                } else {
                    listOfNotNull(
                        artistElement?.let(
                            ::artistName,
                        ),
                    )
                }

            track(
                title = title.orEmpty(),
                artists = names,
                album =
                    item.string(
                        "album",
                        "albumName",
                    )
                        ?: (item["album"] as? JsonObject)
                            ?.string("name"),
                uri =
                    item.string(
                        "spotifyUri",
                        "trackUri",
                        "uri",
                        "url",
                    ),
                duration =
                    (
                        (
                            item["duration_ms"]
                                ?: item["durationMs"]
                            ) as? JsonPrimitive
                        )
                        ?.intOrNull
                        ?: 0,
                row = index + 1,
            )
        }

        return ImportedPlaylist(
            obj?.string(
                "name",
                "title",
            ) ?: fallback,
            thumbnail = null,
            tracks = tracks,
        )
    }

    private fun JsonObject.string(
        vararg keys: String,
    ) =
        keys.firstNotNullOfOrNull {
            (get(it) as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.contentOrNull
                ?.trim()
                ?.takeIf(String::isNotBlank)
        }

    private fun track(
        title: String,
        artists: List<String>,
        album: String? = null,
        uri: String? = null,
        duration: Int = 0,
        row: Int,
    ): ImportedTrack {

        val names =
            artists
                .map(String::trim)
                .filter(String::isNotBlank)

        require(
            title.isNotBlank() &&
                names.isNotEmpty(),
        ) {
            "Track at row $row needs both a song title and artist. Correct the file and try again."
        }

        return ImportedTrack(
            title.trim(),
            names,
            duration.coerceAtLeast(0),
            album = album,
            providerTrackId = uri,
        )
    }

    companion object {
        const val MAX_BYTES =
            5 * 1024 * 1024

        const val MAX_TRACKS =
            10000
    }
}
