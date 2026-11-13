package app.hush.net

import app.hush.data.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class ArtistItem(val name: String, val thumb: String?, val subscribers: Long)

data class CollectionItem(
    val title: String,
    val subtitle: String,
    val url: String,
    val thumb: String?,
    val count: Long
)

data class OnlineList(val title: String, val subtitle: String, val cover: String?, val songs: List<SongEntity>)

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

    /** Search-as-you-type completions from YouTube. Only called if suggestions are switched on. */
    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        yt.suggestionExtractor.suggestionList(query).take(6)
    }

    /** Search YouTube Music "Artists". */
    suspend fun searchArtists(query: String): List<ArtistItem> = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS),
            ""
        )
        SearchInfo.getInfo(yt, handler).relatedItems
            .filterIsInstance<ChannelInfoItem>()
            .map { ArtistItem(it.name ?: "", it.thumbnails.maxByOrNull { t -> t.height }?.url, it.subscriberCount) }
            .filter { it.name.isNotBlank() }
    }

    /** Search YouTube Music "Albums" or "Playlists". */
    suspend fun searchCollections(query: String, albums: Boolean): List<CollectionItem> =
        withContext(Dispatchers.IO) {
            val filter = if (albums) YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS
            else YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS
            val handler = yt.searchQHFactory.fromQuery(query, listOf(filter), "")
            SearchInfo.getInfo(yt, handler).relatedItems
                .filterIsInstance<PlaylistInfoItem>()
                .map {
                    CollectionItem(
                        title = it.name ?: "Untitled",
                        subtitle = it.uploaderName ?: "",
                        url = it.url,
                        thumb = it.thumbnails.maxByOrNull { t -> t.height }?.url,
                        count = it.streamCount
                    )
                }
        }

    /** The songs of an album or playlist (up to [max]). */
    suspend fun collection(url: String, fallbackCover: String? = null, max: Int = 500): OnlineList =
        withContext(Dispatchers.IO) {
            val info = PlaylistInfo.getInfo(yt, url)
            val songs = ArrayList<SongEntity>()
            info.relatedItems.filterIsInstance<StreamInfoItem>().forEach { songs.add(it.toSong()) }
            var page = info.nextPage
            while (page != null && songs.size < max) {
                val more = PlaylistInfo.getMoreItems(yt, url, page)
                more.items.filterIsInstance<StreamInfoItem>().forEach { songs.add(it.toSong()) }
                page = more.nextPage
            }
            val cover = fallbackCover ?: songs.firstNotNullOfOrNull { it.thumb }
            OnlineList(
                title = info.name ?: "Playlist",
                subtitle = info.uploaderName ?: "",
                cover = cover,
                songs = songs.map { if (it.thumb == null) it.copy(thumb = cover) else it }
            )
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
