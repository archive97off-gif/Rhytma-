package echo.music.iad1tya.importer

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

class RealTuneMyMusicCsvTest {
    @Test fun exactUserExportHas119TracksAndEmbeddedPlaylistName() {
        val name = "My Spotify Library (1).csv"
        val bytes = File("../androidApp/src/androidTest/assets/$name").readBytes()
        assertEquals("9709d6460a40628ea09b963f9fa0c6fe91e0503ffdca6f82156ffa77310768c5",
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        val playlist = SpotifyPlaylistFileImporter().parse(name, bytes)
        assertEquals("Dhruv fav", playlist.title)
        assertEquals(119, playlist.tracks.size)
        assertEquals("Thenkizhakku", playlist.tracks.first().title)
        assertEquals(listOf("Santhosh Narayanan"), playlist.tracks.first().artists)
        assertEquals("The Hanging Tree - From \"The Hunger Games: Mockingjay\" Soundtrack", playlist.tracks[1].title)
    }
}
