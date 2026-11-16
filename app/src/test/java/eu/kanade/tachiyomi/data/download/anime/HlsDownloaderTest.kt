package eu.kanade.tachiyomi.data.download.anime

import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class HlsDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var targetDir: File

    private val inFlight = AtomicInteger()
    private val maxInFlight = AtomicInteger()

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        targetDir = File.createTempFile("hls", "").also {
            it.delete()
            it.mkdirs()
        }
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
        targetDir.deleteRecursively()
    }

    @Test
    fun `segments are downloaded and the local playlist points at them`() = runBlocking {
        server.dispatcher = dispatcher(contentType = "application/vnd.apple.mpegurl")

        val playlist = downloader().download(server.url("/playlist.m3u8").toString(), Headers.EMPTY, targetDir)

        assertNotNull(playlist)
        assertEquals("playlist.m3u8", playlist!!.name)

        val content = playlist.readText()
        assertTrue(content.contains("#EXT-X-MAP:URI=\"file://"), "the init section isn't local: $content")
        SEGMENT_PATHS.forEachIndexed { index, _ ->
            val file = File(targetDir, "segment-%05d.mp4".format(index))
            assertTrue(file.exists(), "the segment $index wasn't downloaded")
            assertEquals("data:/segs/$index.mp4", file.readText())
            assertTrue(content.contains("file://${file.absolutePath}"), "the segment $index isn't in the playlist")
        }
    }

    @Test
    fun `segments are downloaded in parallel`() = runBlocking {
        server.dispatcher = dispatcher(contentType = "application/vnd.apple.mpegurl")

        val playlist = downloader().download(server.url("/playlist.m3u8").toString(), Headers.EMPTY, targetDir)

        assertNotNull(playlist)
        assertTrue(
            maxInFlight.get() > 1,
            "the segments were downloaded one after the other (max in flight: ${maxInFlight.get()})",
        )
    }

    @Test
    fun `a url that doesn't serve a playlist is ignored`() = runBlocking {
        server.dispatcher = dispatcher(contentType = "text/html")

        val playlist = downloader().download(server.url("/playlist.m3u8").toString(), Headers.EMPTY, targetDir)

        assertNull(playlist)
    }

    private fun downloader() = HlsDownloader(userAgentProvider = { "test-agent" })

    private fun dispatcher(contentType: String) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.path == "/playlist.m3u8") {
                return MockResponse()
                    .setHeader("Content-Type", contentType)
                    .setBody(playlist())
            }

            val current = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { maxOf(it, current) }
            try {
                Thread.sleep(100)
                return MockResponse().setBody("data:${request.path}")
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

    private fun playlist() = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:3")
        appendLine("#EXT-X-TARGETDURATION:10")
        appendLine("#EXT-X-MAP:URI=\"segs/init.mp4\"")
        SEGMENT_PATHS.forEach { path ->
            appendLine("#EXTINF:10.000000,")
            appendLine(path)
        }
        appendLine("#EXT-X-ENDLIST")
    }

    companion object {
        private val SEGMENT_PATHS = listOf("/segs/0.mp4", "/segs/1.mp4", "/segs/2.mp4", "/segs/3.mp4")
    }
}
