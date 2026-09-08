package echo.music.iad1tya.importer

import kotlin.test.*

class SpotifyPlaylistFileImporterTest {
    private val parser = SpotifyPlaylistFileImporter()
    private fun parse(ext: String, text: String) = parser.parse("Fictional playlist.$ext", text.encodeToByteArray())

    @Test fun normalCsv() {
        val result = parse("csv", "Track name,Artist,Album,Playlist\nPaper Moon,The Inventors,Imaginary,Evening")
        assertEquals("Evening", result.title)
        assertEquals("Imaginary", result.tracks.single().album)
    }
    @Test fun quotedCommasEscapedQuotesAndNewlines() {
        val result = parse("csv", "Title,Artist,Album\r\n\"Moon, \"\"Blue\"\"\",\"Ada, Bea\",\"Part\nTwo\"\r\n")
        assertEquals("Moon, \"Blue\"", result.tracks.single().title)
        assertEquals(listOf("Ada, Bea"), result.tracks.single().artists)
        assertEquals("Part\nTwo", result.tracks.single().album)
    }
    @Test fun bomReorderedMissingAlbumAndUtf8() {
        val result = parse("csv", "\uFEFFArtist_Name(s),Spotify_URI,Song_Name\nÉlan,spotify:track:fictional,सपना\n\n")
        assertEquals("सपना", result.tracks.single().title)
        assertNull(result.tracks.single().album)
        assertEquals("Fictional playlist", result.title)
    }
    @Test fun csvHeaderAliases() {
        for (title in listOf("Track Name", "Title", "Song", "Song Name", "Name")) {
            for (artist in listOf("Artist", "Artist Name", "Artists", "Artist(s)")) {
                assertEquals("Example", parse("csv", "$title,$artist\nExample,Inventor").tracks.single().title)
            }
        }
    }
    @Test fun duplicatesPreservePositionalPlaylistSemantics() {
        assertEquals(2, parse("csv", "Title,Artist\nExample,Inventor\nExample,Inventor").tracks.size)
    }
    @Test fun missingTitleOrArtistRejected() {
        for (row in listOf(",Inventor", "Example,", ",")) {
            assertFailsWith<IllegalArgumentException> { parse("csv", "Title,Artist\n$row") }
        }
    }
    @Test fun invalidCsvQuotesAndColumnsRejected() {
        assertFails { parse("csv", "Title,Artist\n\"Unclosed,Inventor") }
        assertFails { parse("csv", "Title,Artist\nExample,Inventor,Extra") }
        assertFails { parse("csv", "Title,Artist\n\"Example\"oops,Inventor") }
    }
    @Test fun multipleCsvPlaylistsRejected() {
        assertFails { parse("csv", "Title,Artist,Playlist\nOne,Inventor,A\nTwo,Inventor,B") }
    }
    @Test fun txtSeparators() {
        for (separator in listOf(" - ", " | ", "|", "\t")) {
            val track = parse("txt", "Example${separator}Inventor").tracks.single()
            assertEquals("Example", track.title)
            assertEquals(listOf("Inventor"), track.artists)
        }
    }
    @Test fun txtExplicitReverseHeaderAndHeadings() {
        val result = parse("txt", "# Export\nPlaylist: Evening\n\nArtist - Title\nInventor - Example")
        assertEquals("Example", result.tracks.single().title)
    }
    @Test fun txtDoesNotGuessArtistFirst() {
        assertEquals("Inventor", parse("txt", "Inventor - Example").tracks.single().title)
    }
    @Test fun invalidOrAmbiguousTxt() {
        assertFails { parse("txt", "Unstructured text") }
        assertFails { parse("txt", "Example - Part - Inventor") }
    }
    @Test fun artistFirstExportsPreserveSeparatorsInTitles() {
        for ((extension, text) in listOf(
            "csv" to "Inventor - Example - Extended Version",
            "txt" to "Artist - Title\nInventor - Example - Extended Version",
        )) {
            val track = parse(extension, text).tracks.single()
            assertEquals(listOf("Inventor"), track.artists)
            assertEquals("Example - Extended Version", track.title)
        }
    }
    @Test fun simpleJson() {
        val result = parse("json", """{"name":"Evening","tracks":[{"title":"Example","artist":"Inventor"}]}""")
        assertEquals("Evening", result.title)
        assertEquals("Example", result.tracks.single().title)
    }
    @Test fun nestedJsonAndArtistObjects() {
        val result = parse("json", """{"tracks":{"items":[{"track":{"name":"Example","artists":[{"name":"Ada"},{"name":"Bea"}],"album":{"name":"Imaginary"}}}]}}""")
        assertEquals(listOf("Ada", "Bea"), result.tracks.single().artists)
        assertEquals("Imaginary", result.tracks.single().album)
    }
    @Test fun jsonArtistsArray() {
        assertEquals(listOf("Ada", "Bea"), parse("json", """[{"song":"Example","artists":["Ada","Bea"]}]""").tracks.single().artists)
    }
    @Test fun accountDataSinglePlaylist() {
        val result = parse("json", """{"playlists":[{"name":"Evening","items":[{"track":{"trackName":"Example","artistName":"Inventor","albumName":"Imaginary","trackUri":"spotify:track:fictional"}}]}]}""")
        assertEquals("Evening", result.title)
        assertEquals("Example", result.tracks.single().title)
    }
    @Test fun multipleAccountPlaylistsRejected() {
        assertFails { parse("json", """{"playlists":[{"name":"A"},{"name":"B"}]}""") }
    }
    @Test fun malformedAndUnrelatedJson() {
        for (text in listOf("{", "{\"name\":\"Profile\"}", "[{\"name\":\"Someone\"}]", "{\"tracks\":[42]}")) {
            assertFails { parse("json", text) }
        }
    }
    @Test fun missingJsonMetadata() {
        assertFails { parse("json", """{"tracks":[{"title":"Example"}]}""") }
        assertFails { parse("json", """{"tracks":[{"artist":"Inventor"}]}""") }
    }
    @Test fun emptyUnsupportedOversizedInvalidEncoding() {
        assertFails { parse("csv", "") }
        assertFails { parse("txt", "   ") }
        assertFails { parse("xml", "<playlist/>") }
        assertFails { parser.parse("large.csv", ByteArray(SpotifyPlaylistFileImporter.MAX_BYTES + 1)) }
        assertFails { parser.parse("invalid.csv", byteArrayOf(0xC3.toByte(), 0x28)) }
    }
    @Test fun numericJsonMetadataRejected() {
        assertFails { parse("json", """{"tracks":[{"title":42,"artist":true}]}""") }
    }
    @Test fun durationAndUriAreOptionalMetadata() {
        val track = parse("json", """{"tracks":[{"trackName":"Example","artistName":"Inventor","trackUri":"spotify:track:fictional","duration_ms":120000}]}""").tracks.single()
        assertEquals(120000, track.durationMs)
        assertEquals("spotify:track:fictional", track.providerTrackId)
    }
    @Test fun excessiveJsonDepthRejected() {
        assertFails { parse("json", "[".repeat(40) + "]".repeat(40)) }
    }
}
