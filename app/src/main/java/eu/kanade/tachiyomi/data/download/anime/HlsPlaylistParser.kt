package eu.kanade.tachiyomi.data.download.anime

import okhttp3.HttpUrl

/**
 * Parser for the HLS playlists (m3u8) served by the anime sources.
 */
internal object HlsPlaylistParser {

    /**
     * The parts of a playlist that have to be fetched.
     */
    data class Parts(
        /**
         * URL of the media initialization section (`EXT-X-MAP`), if the playlist has one.
         */
        val init: HttpUrl?,
        /**
         * URLs of the media segments, in playlist order.
         */
        val segments: List<HttpUrl>,
    )

    private const val INIT_TAG = "#EXT-X-MAP:"

    private val INIT_URI_REGEX = Regex("URI=\"([^\"]+)\"")

    /**
     * Parses a playlist, resolving every URL against [baseUrl].
     */
    fun parse(playlist: String, baseUrl: HttpUrl): Parts {
        var init: HttpUrl? = null
        val segments = mutableListOf<HttpUrl>()

        playlist.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith(INIT_TAG) -> {
                    val uri = INIT_URI_REGEX.find(trimmed)?.groupValues?.get(1)
                    init = uri?.let { baseUrl.resolve(it) } ?: init
                }
                trimmed.isEmpty() || trimmed.startsWith("#") -> Unit
                else -> baseUrl.resolve(trimmed)?.let(segments::add)
            }
        }

        return Parts(init, segments)
    }
}
