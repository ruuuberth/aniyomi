package eu.kanade.tachiyomi.data.download.anime

import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Dispatcher
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Downloads an HLS playlist and its segments in parallel, and writes a local playlist
 * pointing at the downloaded files.
 *
 * The ffmpeg HLS demuxer fetches every segment on its own, one after the other, so a
 * chapter split in hundreds of segments takes dozens of minutes to download. Fetching the
 * segments concurrently and handing ffmpeg a local playlist removes that bottleneck.
 */
internal class HlsDownloader(
    private val userAgentProvider: () -> String,
    private val parallelRequests: Int = DEFAULT_PARALLEL_REQUESTS,
) {

    private val client by lazy {
        OkHttpClient.Builder()
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = parallelRequests
                    maxRequestsPerHost = parallelRequests
                },
            )
            .addInterceptor(UserAgentInterceptor(userAgentProvider))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Downloads the playlist at [playlistUrl] and all of its segments into [targetDir].
     *
     * @param onProgress called with the number of downloaded files and the total.
     * @return the local playlist file, or null when the url doesn't serve an HLS playlist.
     */
    suspend fun download(
        playlistUrl: String,
        headers: Headers,
        targetDir: File,
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> },
    ): File? {
        val url = playlistUrl.toHttpUrlOrNull() ?: return null
        val playlist = fetchText(url, headers) ?: return null

        val parts = HlsPlaylistParser.parse(playlist, url)
        if (parts.segments.isEmpty()) return null

        // fMP4 segments are only valid together with their initialization section, and
        // the file extension has to be one ffmpeg accepts for the local files.
        val extension = if (parts.init != null) MP4_EXTENSION else MPEG_TS_EXTENSION
        val total = parts.segments.size + if (parts.init != null) 1 else 0

        val downloaded = AtomicInteger()
        val init = parts.init?.let {
            downloadFile(it, headers, targetDir, "init$MP4_EXTENSION")?.also { downloaded.incrementAndGet() }
        }
        // Without the initialization section the fMP4 fragments can't be read at all.
        if (parts.init != null && init == null) return null

        val segments = coroutineScope {
            val semaphore = Semaphore(parallelRequests)
            parts.segments.mapIndexed { index, segmentUrl ->
                async {
                    semaphore.withPermit {
                        downloadFile(
                            segmentUrl,
                            headers,
                            targetDir,
                            "segment-%05d$extension".format(index),
                        )?.also {
                            onProgress(downloaded.incrementAndGet(), total)
                        }
                    }
                }
            }.awaitAll()
        }

        if (segments.any { it == null }) {
            // A partial playlist would produce a broken file, so give up on it.
            return null
        }

        return writeLocalPlaylist(playlist, init, segments.filterNotNull(), targetDir)
    }

    private suspend fun fetchText(url: HttpUrl, headers: Headers): String? {
        val request = Request.Builder().url(url).headers(headers).get().build()
        return try {
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return@use null
                val contentType = response.header("Content-Type").orEmpty()
                if (!contentType.contains("mpegurl", ignoreCase = true)) return@use null
                response.body.string()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun downloadFile(
        url: HttpUrl,
        headers: Headers,
        targetDir: File,
        name: String,
    ): File? {
        val request = Request.Builder().url(url).headers(headers).get().build()
        return try {
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return@use null
                val file = File(targetDir, name)
                file.outputStream().use { output ->
                    response.body.byteStream().use { input -> input.copyTo(output) }
                }
                file
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Writes a copy of [playlist] where every URL points at the downloaded file.
     */
    private fun writeLocalPlaylist(
        playlist: String,
        init: File?,
        segments: List<File>,
        targetDir: File,
    ): File? {
        var segmentIndex = 0

        val content = buildString {
            playlist.lineSequence().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("#EXT-X-MAP:") -> {
                        val path = init?.absolutePath
                        if (path != null) {
                            appendLine("#EXT-X-MAP:URI=\"file://$path\"")
                        } else {
                            appendLine(line)
                        }
                    }
                    trimmed.isEmpty() || trimmed.startsWith("#") -> appendLine(line)
                    else -> {
                        val file = segments.getOrNull(segmentIndex)
                        segmentIndex++
                        if (file != null) {
                            appendLine("file://${file.absolutePath}")
                        }
                    }
                }
            }
        }

        val localPlaylist = File(targetDir, LOCAL_PLAYLIST_NAME)
        localPlaylist.writeText(content)
        return localPlaylist
    }

    companion object {
        private const val DEFAULT_PARALLEL_REQUESTS = 1
        private const val LOCAL_PLAYLIST_NAME = "playlist.m3u8"
        private const val MP4_EXTENSION = ".mp4"
        private const val MPEG_TS_EXTENSION = ".ts"
    }
}
