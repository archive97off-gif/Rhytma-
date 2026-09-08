# Spotify playlist file import

Rhytma now offers: export playlist > Choose Playlist File > preview > Import & Match > Open Playlist.

## Files created

- `composeApp/src/commonMain/kotlin/echo/music/iad1tya/importer/SpotifyPlaylistFileImporter.kt`: pure local parser returning the existing ImportedPlaylist / ImportedTrack models.
- `composeApp/src/commonMain/kotlin/echo/music/iad1tya/expect/ui/PlaylistFilePicker.kt`: platform picker contract.
- `composeApp/src/androidMain/kotlin/echo/music/iad1tya/expect/ui/PlaylistFilePicker.android.kt`: Android document picker and bounded stream reader.
- `composeApp/src/commonTest/kotlin/echo/music/iad1tya/importer/SpotifyPlaylistFileImporterTest.kt`: tiny inline fictional fixtures and parser tests.
- `spotify-file-import-report.md`: this report.

## Files modified

- `composeApp/src/commonMain/kotlin/echo/music/iad1tya/ui/screen/library/SpotifyPlaylistImportScreen.kt`: instructions, external TuneMyMusic link, file selection, preview, progress, cancellation, results, unmatched list and Open Playlist. Existing Spotify account/API UI remains in an advanced section; JioSaavn retains URL importing.
- `composeApp/src/commonMain/kotlin/echo/music/iad1tya/viewModel/PlaylistImportViewModel.kt`: file preview state, shared matching loop, per-track failure handling, automatic file-import saving, cancellation and save retry protection.
- `composeApp/build.gradle.kts`: enable Android host unit tests.

## Parsing and privacy

CSV uses a character-based quoting state machine, not comma splitting. It handles quoted commas, escaped quotes, embedded newlines, CRLF, blank rows and UTF-8 BOM. Normalized header names ignore case and punctuation, so columns can be reordered and optional fields omitted. It uses the playlist column or filename for the title. Malformed rows and missing required metadata produce explicit errors before any matching.

TXT accepts title - artist, title | artist and tab separation. An explicit artist/title header enables reversed order. Blank lines, # headings and Playlist: headings are ignored. Ambiguous extra separators are rejected with advice to export CSV. Headerless artist-first text cannot be reliably detected; the preview explains the title-first assumption.

JSON accepts a track array, an object with tracks, tracks.items, and nested track/artist objects. It supports string artists, artist arrays and optional album/duration/URI metadata. A single-playlist account-data wrapper (playlists > items > track, using trackName and artistName) is also accepted. Unrelated JSON, malformed data, numeric title/artist values, excessive nesting and multi-playlist exports are rejected. This is deliberately not a recursive search through arbitrary account data.

Android uses ActivityResultContracts.OpenDocument and ContentResolver.query/openInputStream. It needs no real filesystem path or broad storage permission. The byte limit is enforced while streaming even when the provider does not report a file size. Limits: 5 MiB, 10,000 tracks, UTF-8 and CSV/TXT/JSON extensions. No persistent document access is requested.

Files are parsed locally; they are not uploaded. Matching sends title/artist search queries through Rhytma's existing YouTube Music search repository. No Spotify credentials, passwords, audio extraction or new server are involved. TuneMyMusic opens externally in the browser.

## Matching, results and duplicates

The existing matchTrack, title/artist/duration scores, confidence thresholds and SongEntity conversion are reused. A 30-second per-track timeout or individual search error produces an unmatched row. Matching is cancellable; saving finishes through the existing transactional playlist insertion. No empty playlist is created if nothing matches. Matched songs are saved using the existing repositories, and unmatched source metadata remains visible on the result screen. It is held in ViewModel memory, not persisted as a separate report.

Rhytma's positional playlist rows support repeats. Source repetitions are therefore preserved; identical metadata is searched once per import, and song records are inserted once per video ID. Starting or saving the same completed import again is guarded against. Playback, JioSaavn importer code, database schema, OAuth/PKCE implementation, signing configuration and Firebase configuration were not edited.

## Export format research and limits

[TuneMyMusic FAQ](https://www.tunemymusic.com/help?faq=3) documents a free plan and TXT/CSV file export, but does not publish a fixed CSV schema or TXT ordering. The parser supports the requested common header variants; no signed-in TuneMyMusic export was available for an end-to-end check. Prefer CSV. Semicolon-delimited CSV, ZIP archives and multiple playlists in one file are not supported. TuneMyMusic's third-party terms and export UI can change independently of Rhytma.

[Spotify's account-data documentation](https://support.spotify.com/in-en/article/understanding-your-data/) confirms playlist, song, artist and album metadata in JSON downloads, but does not specify a versioned field-level schema. Account-data support is best effort for the explicit structure above, not a guarantee for all future exports or podcast/local-track variants.

## Verification

Final verification results are recorded below. No Android device was connected; interactive picker, network matching, playback and JioSaavn runtime regression checks were not performed.

- `:composeApp:testAndroidHostTest`: 23 tests, 0 failures, 0 errors, 0 skipped.
- `:spotify:jvmTest`: 13 existing OAuth and playlist-item tests, 0 failures, 0 errors.
- `:androidApp:assembleDebug`: BUILD SUCCESSFUL; affected Compose and Android sources compiled. Final Gradle run: 1m 23s.
- `git diff --check`: passed.
- Build log: `spotify-file-build-final.log` (ignored by Git).
- Universal debug APK: `androidApp/build/outputs/apk/debug/androidApp-universal-debug.apk`.
- Existing build warnings include the stale local.properties SDK path (ANDROID_HOME resolved the installed SDK), deprecations and unstripped native libraries. They did not prevent the build.
