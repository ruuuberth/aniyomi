package eu.kanade.tachiyomi.data.download.anime

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HlsPlaylistParserTest {

    @Test
    fun `segments and init section are resolved against the playlist url`() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-TARGETDURATION:19
            #EXT-X-PLAYLIST-TYPE:VOD
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-MAP:URI="segs/abc/init.html"
            #EXTINF:10.416667,
            segs/abc/000.html
            #EXTINF:10.416667,
            /other/001.html
            #EXTINF:10.416667,
            https://second.example.com/segs/abc/002.html
            #EXT-X-ENDLIST
        """.trimIndent()

        val parts = HlsPlaylistParser.parse(playlist, "https://cdn.example.com/media/".toHttpUrl())

        assertEquals(
            "https://cdn.example.com/media/segs/abc/init.html",
            parts.init?.toString(),
        )
        assertEquals(
            listOf(
                "https://cdn.example.com/media/segs/abc/000.html",
                "https://cdn.example.com/other/001.html",
                "https://second.example.com/segs/abc/002.html",
            ),
            parts.segments.map { it.toString() },
        )
    }

    @Test
    fun `directives and blank lines are not segments`() {
        val playlist = """
            #EXTM3U
            #EXT-X-ENDLIST

        """.trimIndent()

        val parts = HlsPlaylistParser.parse(playlist, "https://cdn.example.com/media/".toHttpUrl())

        assertNull(parts.init)
        assertEquals(emptyList<Any>(), parts.segments)
    }

    @Test
    fun `a playlist without an init section has none`() {
        val playlist = """
            #EXTM3U
            #EXTINF:4.000000,
            segs/abc/000.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val parts = HlsPlaylistParser.parse(playlist, "https://cdn.example.com/media/".toHttpUrl())

        assertNull(parts.init)
        assertEquals(
            listOf("https://cdn.example.com/media/segs/abc/000.ts"),
            parts.segments.map { it.toString() },
        )
    }
}
