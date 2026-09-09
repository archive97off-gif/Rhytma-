package echo.music.iad1tya.importer

import echo.music.iad1tya.domain.data.model.searchResult.songs.SongsResult
import echo.music.iad1tya.domain.repository.ImportSongSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** One instance per import. Sequential requests and bounded, import-local caches. */
internal class SpotifyTrackMatcher(
    private val search: suspend (String) -> ImportSongSearchResult,
    private val requestTimeoutMs: Long = 6_000,
    private val retryDelay: suspend () -> Unit = { delay(250) },
) {
    sealed interface Outcome {
        data class Matched(val candidate: SongsResult, val confidence: Double) : Outcome
        data class NoMatch(val confidence: Double = 0.0) : Outcome
        data object SearchFailed : Outcome
    }

    private val trackCache = mutableMapOf<ImportedTrack, Outcome>()
    private val queryCache = linkedMapOf<String, List<SongsResult>>()

    suspend fun match(track: ImportedTrack): Outcome {
        currentCoroutineContext().ensureActive()
        trackCache[track]?.let { return it }
        val title = cleanTitle(track.title)
        val artist = track.artists.joinToString(" ") { normalize(it) }.trim()
        if (title.isBlank() || artist.isBlank()) return Outcome.NoMatch()
        // Same cleaned metadata for both querying and scoring. Reversal is the sole fallback.
        val primary = "$artist ${normalize(title)}"
        val fallback = "${normalize(title)} $artist"
        var bestConfidence = 0.0
        for (query in listOf(primary, fallback).distinct()) {
            when (val result = searchPage(query)) {
                is ImportSongSearchResult.Failure -> return Outcome.SearchFailed
                is ImportSongSearchResult.Success -> {
                    val ranked = result.songs.filter { it.videoId.isNotBlank() }
                        .map { rank(track, it) }
                        .sortedWith(
                            compareByDescending<Ranked> { it.score }
                                .thenByDescending { it.title }
                                .thenByDescending { it.artist }
                                .thenByDescending { it.duration }
                                .thenBy { it.song.videoId }
                                .thenBy { it.song.toString() },
                        )
                    bestConfidence = maxOf(bestConfidence, ranked.firstOrNull()?.score ?: 0.0)
                    // Filter eligibility before choosing: an ineligible top score cannot hide a valid song.
                    val best = ranked.firstOrNull {
                        it.sameVersion && it.title >= 0.80 && it.artist >= 0.70 && it.score >= 0.85
                    }
                    if (best != null) {
                        return Outcome.Matched(best.song, best.score).also { trackCache[track] = it }
                    }
                }
            }
        }
        return Outcome.NoMatch(bestConfidence).also { trackCache[track] = it }
    }

    private suspend fun searchPage(query: String): ImportSongSearchResult {
        queryCache[query]?.let { return ImportSongSearchResult.Success(it) }
        repeat(2) { attempt ->
            currentCoroutineContext().ensureActive()
            val result = try {
                withTimeoutOrNull(requestTimeoutMs) { search(query) }
                    ?: ImportSongSearchResult.Failure(retryable = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Production repository classifies transport errors. Unexpected bugs are not retried.
                ImportSongSearchResult.Failure(retryable = false)
            }
            currentCoroutineContext().ensureActive()
            when (result) {
                is ImportSongSearchResult.Success -> {
                    if (queryCache.size >= 256) queryCache.remove(queryCache.keys.first())
                    val songs = result.songs.toList()
                    queryCache[query] = songs
                    return ImportSongSearchResult.Success(songs)
                }
                is ImportSongSearchResult.Failure -> {
                    if (!result.retryable || attempt == 1) return result
                    retryDelay()
                }
            }
        }
        error("Unreachable")
    }

    private data class Ranked(
        val song: SongsResult,
        val title: Double,
        val artist: Double,
        val duration: Double,
        val sameVersion: Boolean,
    ) {
        val score: Double get() = title * 0.55 + artist * 0.35 + duration * 0.10
    }

    private fun rank(track: ImportedTrack, song: SongsResult): Ranked {
        val sourceTitle = cleanTitle(track.title)
        val candidateTitle = cleanTitle(song.title.orEmpty())
        return Ranked(
            song,
            similarity(sourceTitle, candidateTitle),
            // Lead artist agreement is mandatory; a shared featured artist is not enough.
            maxOf(song.artists.orEmpty().maxOfOrNull {
                similarity(track.artists.firstOrNull().orEmpty(), it.name)
            } ?: 0.0, similarity(
                track.artists.firstOrNull().orEmpty(),
                song.artists.orEmpty().joinToString(" ") { it.name },
            )),
            durationScore(track.durationMs, song.durationSeconds),
            qualifiers(sourceTitle) == qualifiers(candidateTitle),
        )
    }

    private fun durationScore(sourceMs: Int, seconds: Int?): Double =
        if (sourceMs <= 0 || seconds == null || seconds <= 0) 0.5
        else (1.0 - kotlin.math.abs(sourceMs / 1000.0 - seconds) / 30.0).coerceIn(0.0, 1.0)

    private fun qualifiers(title: String): Set<String> = buildSet {
        // Retain named mixes, versions and language annotations even when the base title agrees.
        Regex("\\([^)]*\\)|\\[[^]]*]|\\s[-–—]\\s.*$").findAll(title).forEach {
            normalize(it.value).takeIf(String::isNotEmpty)?.let(::add)
        }
        Regex("\\b(remix|mix|live|acoustic|instrumental|karaoke|cover|version|edit|sped up|slowed|remake)\\b.*$", RegexOption.IGNORE_CASE)
            .find(title)?.value?.let { add(normalize(it)) }
    }

    private fun similarity(first: String, second: String): Double {
        val a = normalize(first)
        val b = normalize(second)
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        // Malformed export fields must not trigger quadratic work on megabytes of text.
        if (a.length > 512 || b.length > 512) return 0.0
        val left = a.split(' ').toSet()
        val right = b.split(' ').toSet()
        val overlap = (left intersect right).size.toDouble() / (left union right).size
        // Token overlap alone treats reordered titles as identical. Require sequence agreement too.
        val previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            var diagonal = previous[0]
            previous[0] = i + 1
            for (j in b.indices) {
                val above = previous[j + 1]
                previous[j + 1] = minOf(previous[j] + 1, above + 1, diagonal + if (a[i] == b[j]) 0 else 1)
                diagonal = above
            }
        }
        return minOf(overlap, 1.0 - previous[b.length].toDouble() / maxOf(a.length, b.length))
    }

    internal companion object {
        fun normalize(value: String): String = value.lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
            .replace(Regex("\\s+"), " ").trim()

        fun cleanTitle(value: String): String = value
            // Strip clear numbering; retain meaningful titles such as "1979" and "99 Luftballons".
            .replace(Regex("^\\s*(?:0\\d{1,2}\\s+|\\d{1,3}[.)_-]\\s*)"), "")
            .replace(Regex("[\\[(](?:feat\\.?|ft\\.?)\\s+[^)\\]]+[)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[\\[(]from\\s+[\"'][^)\\]]+[)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?:\\s[-–—]\\s|\\s*[\\[(])(?:\\d{4}\\s+)?remaster(?:ed)?(?:\\s+\\d{4})?[)\\]]?\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?:\\s[-–—]\\s|\\s*[\\[(])(?:official audio|official video|lyrics?)[)\\]]?\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ").trim()
    }
}
