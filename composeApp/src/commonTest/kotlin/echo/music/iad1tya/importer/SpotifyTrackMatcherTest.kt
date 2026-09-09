package echo.music.iad1tya.importer

import echo.music.iad1tya.domain.data.model.searchResult.songs.Artist
import echo.music.iad1tya.domain.data.model.searchResult.songs.SongsResult
import echo.music.iad1tya.domain.repository.ImportSongSearchResult
import echo.music.iad1tya.domain.utils.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SpotifyTrackMatcherTest {
    private fun track(title: String = "Paper Moon", artist: String = "The Inventors", duration: Int = 0) =
        ImportedTrack(title, listOf(artist), duration)

    private fun song(
        title: String = "Paper Moon", artist: String = "The Inventors", id: String = "a", seconds: Int? = null,
    ) = SongsResult(null, listOf(Artist(null, artist)), "Song", null, seconds, null, false,
        "song", null, title, id, null, "")

    private class FakeSearch(val response: suspend (String, Int) -> ImportSongSearchResult) {
        val queries = mutableListOf<String>()
        var retries = 0
        fun matcher(timeout: Long = 6_000) = SpotifyTrackMatcher(
            search = { query -> queries.add(query); response(query, queries.size) },
            requestTimeoutMs = timeout,
            retryDelay = { retries++ },
        )
    }

    private fun success(vararg songs: SongsResult) = ImportSongSearchResult.Success(songs.toList())
    private fun matchedId(outcome: SpotifyTrackMatcher.Outcome) =
        assertIs<SpotifyTrackMatcher.Outcome.Matched>(outcome).candidate.videoId

    @Test fun exactMatchUsesOneArtistTitleSearch() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> success(song()) }
        assertEquals("a", matchedId(fake.matcher().match(track())))
        assertEquals(listOf("the inventors paper moon"), fake.queries)
        assertEquals(0, fake.retries)
    }

    @Test fun trackNumberPrefix() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> success(song(title = "Thenkizhakku")) }
        assertEquals("a", matchedId(fake.matcher().match(track(title = "01 Thenkizhakku"))))
        assertEquals(listOf("the inventors thenkizhakku"), fake.queries)
    }

    @Test fun featuredMetadata() = runBlocking<Unit> {
        val candidate = song().copy(artists = listOf(Artist(null, "The Inventors"), Artist(null, "Bea")))
        for (suffix in listOf(" (feat. Bea)", " [ft. Bea]")) {
            val fake = FakeSearch { _, _ -> success(candidate) }
            assertEquals("a", matchedId(fake.matcher().match(track(title = "Paper Moon$suffix"))))
            assertEquals(1, fake.queries.size)
        }
    }

    @Test fun soundtrackMetadata() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> success(song()) }
        assertEquals("a", matchedId(fake.matcher().match(track(title = "Paper Moon (From \"Movie\")"))))
        assertEquals(1, fake.queries.size)
    }

    @Test fun remasterAndPresentationMetadata() = runBlocking<Unit> {
        for (suffix in listOf(" - Remastered", " - 2011 Remaster", " (Remastered 2011)", " - Official Audio", " - Lyrics")) {
            val fake = FakeSearch { _, _ -> success(song()) }
            assertEquals("a", matchedId(fake.matcher().match(track(title = "Paper Moon$suffix"))))
            assertEquals(1, fake.queries.size)
        }
    }

    @Test fun candidateMetadataIsCleanedToo() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> success(song(title = "Paper Moon (Official Audio)")) }
        assertEquals("a", matchedId(fake.matcher().match(track())))
    }

    @Test fun meaningfulNumbersAndWordsAreRetained() {
        for (title in listOf("1979", "99 Luftballons", "Lyrics", "From Here", "My Official Audio Diary")) {
            assertEquals(title, SpotifyTrackMatcher.cleanTitle(title))
        }
    }

    @Test fun versionsMixesAndLanguagesCannotBeErased() = runBlocking<Unit> {
        for (title in listOf("Paper Moon (Live)", "Paper Moon - Night Remix", "Paper Moon (Tamil)", "Paper Moon - Acoustic Version")) {
            val fake = FakeSearch { _, _ -> success(song(title = title)) }
            assertIs<SpotifyTrackMatcher.Outcome.NoMatch>(fake.matcher().match(track()))
        }
        val fake = FakeSearch { _, _ -> success(song(title = "Paper Moon - Night Remix")) }
        assertIs<SpotifyTrackMatcher.Outcome.NoMatch>(fake.matcher().match(track(title = "Paper Moon - Day Remix")))
    }

    @Test fun sameVersionCanMatch() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> success(song(title = "Paper Moon (Tamil)")) }
        assertEquals("a", matchedId(fake.matcher().match(track(title = "Paper Moon (Tamil)"))))
    }

    @Test fun wrongArtistCannotWinRegardlessOfOrder() = runBlocking<Unit> {
        val wrong = song(artist = "Different Band", id = "0")
        val right = song(id = "z")
        for (candidates in listOf(listOf(wrong, right), listOf(right, wrong))) {
            val fake = FakeSearch { _, _ -> ImportSongSearchResult.Success(candidates) }
            assertEquals("z", matchedId(fake.matcher().match(track())))
        }
        val fake = FakeSearch { _, _ -> success(wrong) }
        assertIs<SpotifyTrackMatcher.Outcome.NoMatch>(fake.matcher().match(track()))
    }

    @Test fun featuredArtistAloneIsInsufficient() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> success(song(artist = "Bea")) }
        assertIs<SpotifyTrackMatcher.Outcome.NoMatch>(fake.matcher().match(track().copy(artists = listOf("The Inventors", "Bea"))))
    }

    @Test fun exportedCombinedArtistFieldMatchesCompleteCredits() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> success(song().copy(
            artists = listOf(Artist(null, "The Inventors"), Artist(null, "Bea")),
        )) }
        assertEquals("a", matchedId(fake.matcher().match(track(artist = "The Inventors, Bea"))))
    }

    @Test fun genericWordsAndReorderedTitlesAreNotEnough() = runBlocking<Unit> {
        for (title in listOf("Paper in the Moon", "Moon Paper", "Under the Moon")) {
            val fake = FakeSearch { _, _ -> success(song(title = title)) }
            assertIs<SpotifyTrackMatcher.Outcome.NoMatch>(fake.matcher().match(track()))
        }
    }

    @Test fun temporaryResourceErrorThenSuccessRetriesOnlyFailedQuery() = runBlocking<Unit> {
        val fake = FakeSearch { _, attempt ->
            ImportSongSearchResult.fromResource(
                if (attempt == 1) Resource.Error("Temporary search failure") else Resource.Success(arrayListOf(song())),
            )
        }
        assertEquals("a", matchedId(fake.matcher().match(track())))
        assertEquals(listOf("the inventors paper moon", "the inventors paper moon"), fake.queries)
        assertEquals(1, fake.retries)
    }

    @Test fun successfulEmptyResultsUseOnlyOneFallbackWithoutRetry() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> success() }
        assertIs<SpotifyTrackMatcher.Outcome.NoMatch>(fake.matcher().match(track()))
        assertEquals(listOf("the inventors paper moon", "paper moon the inventors"), fake.queries)
        assertEquals(0, fake.retries)
    }

    @Test fun poorPrimaryUsesFallbackWhichCanMatch() = runBlocking<Unit> {
        val fake = FakeSearch { _, attempt -> if (attempt == 1) success(song(artist = "Wrong Band")) else success(song()) }
        assertEquals("a", matchedId(fake.matcher().match(track())))
        assertEquals(2, fake.queries.size)
        assertEquals(0, fake.retries)
    }

    @Test fun exhaustedFailureIsNotNoMatchAndDoesNotTriggerFallback() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> ImportSongSearchResult.Failure(true) }
        assertIs<SpotifyTrackMatcher.Outcome.SearchFailed>(fake.matcher().match(track()))
        assertEquals(2, fake.queries.size)
        assertEquals(1, fake.retries)
    }

    @Test fun failedFallbackIsNotNoMatch() = runBlocking<Unit> {
        val fake = FakeSearch { _, attempt -> if (attempt == 1) success() else ImportSongSearchResult.Failure(true) }
        assertIs<SpotifyTrackMatcher.Outcome.SearchFailed>(fake.matcher().match(track()))
        assertEquals(3, fake.queries.size)
    }

    @Test fun permanentFailureIsNotRetried() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> ImportSongSearchResult.Failure(false) }
        assertIs<SpotifyTrackMatcher.Outcome.SearchFailed>(fake.matcher().match(track()))
        assertEquals(1, fake.queries.size)
    }

    @Test fun unexpectedExceptionIsFailureWithoutBlindRetry() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> error("Bad response parsing") }
        assertIs<SpotifyTrackMatcher.Outcome.SearchFailed>(fake.matcher().match(track()))
        assertEquals(1, fake.queries.size)
    }

    @Test fun timeoutIsBoundedAndNeverNoMatch() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> delay(10_000); success() }
        assertIs<SpotifyTrackMatcher.Outcome.SearchFailed>(fake.matcher(timeout = 30).match(track()))
        assertEquals(2, fake.queries.size)
    }

    @Test fun cancellationPropagatesWithoutRetry() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> throw CancellationException("User cancelled") }
        assertFailsWith<CancellationException> { fake.matcher().match(track()) }
        assertEquals(1, fake.queries.size)
    }

    @Test fun tiesResolveByStableVideoId() = runBlocking<Unit> {
        val songs = listOf(song(id = "c"), song(id = "a"), song(id = "b"))
        for (candidates in listOf(songs, songs.reversed(), listOf(songs[1], songs[2], songs[0]))) {
            val fake = FakeSearch { _, _ -> ImportSongSearchResult.Success(candidates) }
            assertEquals("a", matchedId(fake.matcher().match(track())))
        }
    }

    @Test fun durationBreaksOtherwiseEqualCandidates() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> success(song(id = "a", seconds = 220), song(id = "z", seconds = 180)) }
        assertEquals("z", matchedId(fake.matcher().match(track(duration = 180_000))))
    }

    @Test fun duplicateTracksReuseOutcomeAndRetainPositions() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> success(song()) }
        val matcher = fake.matcher()
        val results = listOf(track(), track()).map { matcher.match(it) }
        assertEquals(listOf("a", "a"), results.map(::matchedId))
        assertEquals(1, fake.queries.size)
    }

    @Test fun normalizedQueryCacheReusesSearchButRescoresDuration() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> success(song(id = "a", seconds = 180), song(id = "b", seconds = 220)) }
        val matcher = fake.matcher()
        assertEquals("a", matchedId(matcher.match(track(duration = 180_000))))
        assertEquals("b", matchedId(matcher.match(track(title = "Paper  Moon", duration = 220_000))))
        assertEquals(1, fake.queries.size)
    }

    @Test fun duplicateNoMatchDoesNotSearchAgain() = runBlocking<Unit> {
        val fake = FakeSearch { _, _ -> success() }
        val matcher = fake.matcher()
        repeat(2) { assertIs<SpotifyTrackMatcher.Outcome.NoMatch>(matcher.match(track())) }
        assertEquals(2, fake.queries.size)
    }

    @Test fun failedSearchIsNotCachedAsNegativeMatch() = runBlocking<Unit> {
        val fake = FakeSearch { _, attempt -> if (attempt <= 2) ImportSongSearchResult.Failure(true) else success(song()) }
        val matcher = fake.matcher()
        assertIs<SpotifyTrackMatcher.Outcome.SearchFailed>(matcher.match(track()))
        assertEquals("a", matchedId(matcher.match(track())))
        assertEquals(3, fake.queries.size)
    }

    @Test fun normal119TrackImportUses119Searches() = runBlocking<Unit> {
        val fake = FakeSearch { query, _ -> success(song(title = query.removePrefix("the inventors "))) }
        val matcher = fake.matcher()
        repeat(119) { assertIs<SpotifyTrackMatcher.Outcome.Matched>(matcher.match(track(title = "Song $it"))) }
        assertEquals(119, fake.queries.size)
        assertEquals(0, fake.retries)
    }
}
