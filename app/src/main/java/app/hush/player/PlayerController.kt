package app.hush.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.hush.data.SongEntity
import app.hush.net.YtMusic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerState(
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val art: String? = null,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false,
    /** Position of the current song in the queue. */
    val index: Int = 0
) {
    /** The current track as a song, for liking / adding to playlists. */
    fun asSong(): SongEntity? = mediaId?.let {
        SongEntity(it, title, artist, art, durationMs / 1000)
    }
}

/** One row of the queue as shown in the queue sheet. */
data class QueueItem(
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    val art: String?
)

/** Sleep timer: stop after [minutes] (ending at [endsAtMs]) or at the end of the current song. */
data class SleepState(
    val minutes: Int = 0,
    val endsAtMs: Long? = null,
    val endOfSong: Boolean = false
) {
    val active: Boolean get() = endsAtMs != null || endOfSong
}

/** UI-side handle to the playback service. Lives as long as the app process. */
class PlayerController(
    private val context: Context,
    /** Called each time a new track starts, used for the on-device "Recently played" row. */
    private val onTrackStarted: (SongEntity) -> Unit = {}
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private val queued = ArrayList<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _sleep = MutableStateFlow(SleepState())
    val sleep: StateFlow<SleepState> = _sleep.asStateFlow()
    private var sleepJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) refreshQueue()
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                player.playbackState == Player.STATE_ENDED
            ) {
                // The queue finished, so an "end of song" sleep timer has done its job.
                if (_sleep.value.endOfSong) _sleep.value = SleepState()
            }
            refresh()
        }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            val c = controller ?: return
            if (item == null) return
            onTrackStarted(item.toSong())

            // "End of song" sleep timer: let the song finish, then stop before the next one.
            if (_sleep.value.endOfSong && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                c.pause()
                _sleep.value = SleepState()
            }

            // Reached the end of the queue: quietly append related songs so music keeps going.
            if (!c.hasNextMediaItem()) extendQueue(item.mediaId)
        }
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            queued.forEach { it(c) }
            queued.clear()
            refreshQueue()
            refresh()
        }, ContextCompat.getMainExecutor(context))

        scope.launch {
            while (true) {
                delay(500)
                if (controller?.isPlaying == true) refresh()
            }
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else queued.add(block)
    }

    private fun refresh() {
        val c = controller ?: return
        val md = c.mediaMetadata
        _state.value = PlayerState(
            mediaId = c.currentMediaItem?.mediaId,
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            art = md.artworkUri?.toString(),
            playing = c.isPlaying,
            loading = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = if (c.duration > 0) c.duration else 0,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            hasNext = c.hasNextMediaItem(),
            hasPrev = c.hasPreviousMediaItem(),
            index = c.currentMediaItemIndex
        )
    }

    private fun refreshQueue() {
        val c = controller ?: return
        _queue.value = (0 until c.mediaItemCount).map { i ->
            val item = c.getMediaItemAt(i)
            QueueItem(
                index = i,
                mediaId = item.mediaId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                art = item.mediaMetadata.artworkUri?.toString()
            )
        }
    }

    private fun extendQueue(lastMediaId: String) {
        scope.launch {
            val more = runCatching { YtMusic.related(lastMediaId) }.getOrDefault(emptyList())
            withController { c ->
                val existing = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }.toSet()
                val fresh = more.filter { it.url !in existing }.take(20)
                if (fresh.isNotEmpty()) c.addMediaItems(fresh.map(::toItem))
            }
        }
    }

    private fun MediaItem.toSong() = SongEntity(
        url = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        thumb = mediaMetadata.artworkUri?.toString(),
        durationSec = 0
    )

    private fun toItem(s: SongEntity): MediaItem =
        MediaItem.Builder()
            .setMediaId(s.url)
            .setUri(s.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(s.title)
                    .setArtist(s.artist)
                    .setArtworkUri(s.thumb?.let { Uri.parse(it) })
                    .build()
            )
            .build()

    // --- Commands -----------------------------------------------------------

    /** Replace the queue with [songs] and start at [index]. */
    fun play(songs: List<SongEntity>, index: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(songs.map(::toItem), index.coerceIn(0, songs.lastIndex), 0L)
            c.prepare()
            c.play()
        }
    }

    fun playNext(song: SongEntity) = withController { c ->
        if (c.mediaItemCount == 0) play(listOf(song))
        else c.addMediaItem(c.currentMediaItemIndex + 1, toItem(song))
    }

    fun addToQueue(song: SongEntity) = withController { c ->
        if (c.mediaItemCount == 0) play(listOf(song)) else c.addMediaItem(toItem(song))
    }

    fun toggle() = withController { c -> if (c.isPlaying) c.pause() else c.play() }
    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController {
        // Behave like every music app: restart the song first, go back if already near the start.
        if (it.currentPosition > 3000 || !it.hasPreviousMediaItem()) it.seekTo(0)
        else it.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // --- Queue --------------------------------------------------------------

    fun jumpTo(index: Int) = withController {
        if (index in 0 until it.mediaItemCount) {
            it.seekTo(index, 0L)
            it.play()
        }
    }

    fun removeAt(index: Int) = withController {
        if (index in 0 until it.mediaItemCount) it.removeMediaItem(index)
    }

    /** Remove everything after the song that is playing now. */
    fun clearUpcoming() = withController { c ->
        val from = c.currentMediaItemIndex + 1
        if (from < c.mediaItemCount) c.removeMediaItems(from, c.mediaItemCount)
    }

    // --- Sleep timer --------------------------------------------------------

    fun setSleepMinutes(minutes: Int) {
        sleepJob?.cancel()
        val ms = minutes * 60_000L
        _sleep.value = SleepState(minutes = minutes, endsAtMs = System.currentTimeMillis() + ms)
        sleepJob = scope.launch {
            delay(ms)
            withController { it.pause() }
            _sleep.value = SleepState()
        }
    }

    fun setSleepEndOfSong() {
        sleepJob?.cancel()
        _sleep.value = SleepState(endOfSong = true)
    }

    fun cancelSleep() {
        sleepJob?.cancel()
        _sleep.value = SleepState()
    }
}
