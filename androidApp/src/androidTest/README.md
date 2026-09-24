# Real TuneMyMusic CSV device regression

The fixture `assets/My Spotify Library (1).csv` is the exact supplied export, SHA-256
`9709d6460a40628ea09b963f9fa0c6fe91e0503ffdca6f82156ffa77310768c5`.
It contains personal playlist metadata and is deliberately confined to test assets.

From the repository root in PowerShell:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest '-PplaylistTestRunner=echo.music.iad1tya.RealCsvInstrumentation'
adb install -r androidApp/build/outputs/apk/debug/androidApp-universal-debug.apk
adb install -r androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
adb shell am instrument -w com.dhruv.rhytma.dev.test/echo.music.iad1tya.RealCsvInstrumentation
adb shell run-as com.dhruv.rhytma.dev cat files/real-csv-result.json
```

Use a test device/emulator: this opt-in integration **creates a real local playlist**.
The runner uses the production parser, PlaylistImportViewModel, SpotifyTrackMatcher,
SearchRepository, SongRepository, transactional LocalPlaylistRepository, Room database,
and LibraryViewModel. Live search responses are not substituted. Source metadata is
sent to the same music search source used by the app.

The runner asserts all 119 outcomes are retained, monitors for state resets, explicitly
saves matched songs, verifies ordered persisted video IDs and song records, checks the
new ID in Your Library's ViewModel, opens it through the playlist repository, and checks
that pressing save again does not create a second playlist. The JSON report contains
per-track outcomes. Check `passed: true`; an adb exit code alone is not a test assertion.

A separate `ProblemTrackRegression` injects controlled search responses into the real
ViewModel to exercise success, search failure, unmatched, and retry. It never saves its
synthetic songs and its results are not counted as live CSV matches. It asserts that
retry does not search or replace a successful match.

The host regression additionally verifies the exact fixture hash, playlist name, all
119 parsed tracks, and quoted title handling. The device test is intentionally opt-in
because live search results and connectivity are external and can change.
