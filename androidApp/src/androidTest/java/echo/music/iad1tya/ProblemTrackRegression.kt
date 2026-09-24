package echo.music.iad1tya

import echo.music.iad1tya.domain.data.model.searchResult.songs.Artist
import echo.music.iad1tya.domain.data.model.searchResult.songs.SongsResult
import echo.music.iad1tya.domain.repository.ImportSongSearchResult
import echo.music.iad1tya.domain.repository.SearchRepository
import echo.music.iad1tya.importer.ImportedPlaylist
import echo.music.iad1tya.viewModel.PlaylistImportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.Koin

/** Fault injection only, separate from the unmodified live-search CSV run. */
internal suspend fun verifyProblemTrackRetry(koin: Koin, source: ImportedPlaylist) {
    val fixture = source.copy(title = "Regression only", tracks = source.tracks.take(3))
    val delegate = koin.get<SearchRepository>()
    lateinit var vm: PlaylistImportViewModel
    var retry = false
    val requests = mutableListOf<Int>()
    val search = object : SearchRepository by delegate {
        override fun getSearchDataSongForImport(query: String) = flow {
            val track = checkNotNull(vm.state.value.currentTrack)
            val index = fixture.tracks.indexOf(track)
            requests.add(index)
            emit(when {
                index == 0 || retry && index == 1 -> ImportSongSearchResult.Success(listOf(
                    SongsResult(null, track.artists.map { Artist(null, it) }, "Song", null, null, null, false,
                        "song", null, track.title, "regression-$index", null, "")))
                index == 1 -> ImportSongSearchResult.Failure(retryable = false)
                else -> ImportSongSearchResult.Success(emptyList())
            })
        }
    }
    vm = withContext(Dispatchers.Main) { PlaylistImportViewModel(koin.get(), search, koin.get(), koin.get(), koin.get()) }
    withContext(Dispatchers.Main) { vm.chooseFile { fixture } }
    withTimeout(10_000) { vm.state.first { !it.isLoading } }
    withContext(Dispatchers.Main) { vm.importFile() }
    val first = withTimeout(10_000) { vm.state.first { !it.isLoading && it.matchingComplete } }
    check(first.matches.size == 3 && first.matches[0].song != null)
    check(first.matches[1].searchFailed && first.matches[2].song == null && !first.matches[2].searchFailed)
    check(first.savedPlaylistId == null && first.playlist == fixture)
    requests.clear()
    retry = true
    withContext(Dispatchers.Main) { vm.importFile() }
    val second = withTimeout(10_000) { vm.state.first { !it.isLoading && it.matchingComplete } }
    check(0 !in requests) { "Retry searched an already matched track" }
    check(1 in requests && 2 in requests)
    check(second.matches[0] === first.matches[0])
    check(second.matches[1].song != null && !second.matches[1].searchFailed)
    check(second.matches[2].song == null && second.playlist == fixture)
}
