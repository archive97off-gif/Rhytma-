package echo.music.iad1tya

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import echo.music.iad1tya.domain.repository.LocalPlaylistRepository
import echo.music.iad1tya.importer.SpotifyPlaylistFileImporter
import echo.music.iad1tya.viewModel.LibraryViewModel
import echo.music.iad1tya.viewModel.PlaylistImportViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import java.io.File
import java.security.MessageDigest

/** Opt-in device regression: real parser, ViewModel, live search, Room and library state.
 * Supply the user's file in the test APK's assets; never substitute matching responses.
 * Run with -PplaylistTestRunner=echo.music.iad1tya.RealCsvInstrumentation.
 */
class RealCsvInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val report = JSONObject()
        fun checkpoint() {
            File(targetContext.filesDir, "real-csv-result.json").writeText(report.toString(2))
        }
        try {
            runOnMainSync { targetContext.startActivity(targetContext.packageManager.getLaunchIntentForPackage(targetContext.packageName)!!) }
            report.put("stage", "application started"); checkpoint()
            runBlocking {
                val name = "My Spotify Library (1).csv"
                val bytes = context.assets.open(name).use { it.readBytes() }
                val parsed = SpotifyPlaylistFileImporter().parse(name, bytes)
                report.put("file", name).put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
                    .put("playlist", parsed.title).put("parsed", parsed.tracks.size)
                check(parsed.title == "Dhruv fav")
                check(parsed.tracks.size == 119)
                val koin = GlobalContext.get()
                verifyProblemTrackRetry(koin, parsed)
                report.put("faultInjectionRetryPassed", true)
                val vm = withContext(Dispatchers.Main) { koin.get<PlaylistImportViewModel>() }
                withContext(Dispatchers.Main) { vm.chooseFile { SpotifyPlaylistFileImporter().parse(name, bytes) } }
                withTimeout(30_000) { vm.state.first { !it.isLoading } }
                check(vm.state.value.playlist == parsed)
                var reset = false
                var greatestMatchCount = 0
                val observer = launch(Dispatchers.Main.immediate) {
                    vm.state.collect { state ->
                        if (state.playlist == null || state.matches.size < greatestMatchCount) reset = true
                        greatestMatchCount = maxOf(greatestMatchCount, state.matches.size)
                        report.put("processed", state.matches.size).put("reset", reset)
                        checkpoint()
                    }
                }
                withContext(Dispatchers.Main) { vm.importFile() }
                val completed = withTimeout(30 * 60_000L) { vm.state.first { !it.isLoading && it.matchingComplete } }
                observer.cancelAndJoin()
                check(!reset)
                check(completed.matches.size == 119)
                check(completed.savedPlaylistId == null) { "Must wait for explicit import" }
                report.put("matched", completed.matches.count { it.song != null })
                    .put("unmatched", completed.matches.count { it.song == null && !it.searchFailed })
                    .put("searchFailures", completed.matches.count { it.searchFailed })
                    .put("tracks", JSONArray().apply {
                        completed.matches.forEach { match -> put(JSONObject()
                            .put("title", match.source.title).put("artists", match.source.artists.joinToString(", "))
                            .put("status", if (match.song != null) "matched" else if (match.searchFailed) "search failed" else "no confident eligible candidate")
                            .put("confidence", match.confidence).put("videoId", match.song?.videoId ?: JSONObject.NULL)
                            .put("matchedTitle", match.song?.title ?: JSONObject.NULL)) }
                    })
                checkpoint()
                check(completed.matches.any { it.song != null }) { "Live source returned no successful matches; save cannot be tested" }
                withContext(Dispatchers.Main) { vm.saveMatchedTracks() }
                val saved = withTimeout(60_000) { vm.state.first { !it.isLoading } }
                val id = checkNotNull(saved.savedPlaylistId) { saved.error.orEmpty() }
                val repository = koin.get<LocalPlaylistRepository>()
                val expected = completed.matches.mapNotNull { it.song?.videoId }
                check(repository.getListTrackVideoId(id) == expected)
                val songs = repository.getFullPlaylistTracks(id)
                check(songs.map { it.videoId } == expected)
                val library = withContext(Dispatchers.Main) { koin.get<LibraryViewModel>().also { it.getLocalPlaylist() } }
                withTimeout(30_000) { library.yourLocalPlaylist.first { it.data?.any { p -> p.id == id && p.title == parsed.title } == true } }
                check(repository.getLocalPlaylist(id).first { it.data != null }.data!!.title == parsed.title)
                withContext(Dispatchers.Main) { vm.saveMatchedTracks() }
                check(vm.state.value.savedPlaylistId == id)
                report.put("playlistId", id).put("saved", songs.size).put("library", true).put("openedThroughRepository", true)
                    .put("passed", true)
                checkpoint()
            }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", report.toString(2)) })
        } catch (error: Throwable) {
            report.put("passed", false).put("error", error.stackTraceToString())
            checkpoint()
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", report.toString(2)) })
        }
    }
}
