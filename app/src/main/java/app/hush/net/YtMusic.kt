package app.hush.net

import app.hush.data.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Thin wrapper around NewPipeExtractor limited to what a music player needs. */
object YtMusic {

    private val yt get() = ServiceList.YouTube

    /** Search YouTube Music "Songs". */
    suspend fun search(query: String): List<SongEntity> = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            ""
        )
        SearchInfo.getInfo(yt, handler).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { it.toSong() }
    }

    /** Songs YouTube suggests after [url]; used to keep the queue going. */
    suspend fun related(url: String): List<SongEntity> = withContext(Dispatchers.IO) {
        StreamInfo.getInfo(yt, url).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { it.toSong() }
    }

    private data class Cached(val streamUrl: String, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, Cached>()
    private const val TTL_MS = 3L * 60 * 60 * 1000 // stream links last ~6h, refresh after 3h

    /**
     * Resolve a watch URL into a direct audio stream URL. Blocking, only call
     * from a background thread (the player's loader thread does).
     */
    fun resolveStream(watchUrl: String): String {
        val now = System.currentTimeMillis()
        cache[watchUrl]?.let { if (it.expiresAt > now) return it.streamUrl }

        val info = try {
            StreamInfo.getInfo(yt, watchUrl)
        } catch (e: Exception) {
            throw IOException("Could not load stream: ${e.message}", e)
        }
        val best = pickAudio(info.audioStreams)
            ?: throw IOException("No audio stream available")
        val link = best.content
        cache[watchUrl] = Cached(link, now + TTL_MS)
        return link
    }

    /** Prefer AAC/M4A (widest device support), highest bitrate; fall back to anything. */
    private fun pickAudio(streams: List<AudioStream>): AudioStream? {
        val usable = streams.filter { it.isUrl }
        val m4a = usable.filter { it.format?.suffix == "m4a" }
        return (m4a.ifEmpty { usable }).maxByOrNull { it.averageBitrate }
    }

    private fun StreamInfoItem.toSong() = SongEntity(
        url = url,
        title = name ?: "Unknown",
        artist = uploaderName?.removeSuffix(" - Topic") ?: "",
        thumb = thumbnails.maxByOrNull { it.height }?.url,
        durationSec = duration
    )
}
