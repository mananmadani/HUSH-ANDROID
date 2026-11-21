package app.hush.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.hush.data.SongEntity
import app.hush.Affinity
import app.hush.Taste
import app.hush.net.YtMusic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val onTrackStarted: (SongEntity) -> Unit = {},
    /** Called about every 500 ms while music is playing (used for listening time). */
    private val onTick: (Long) -> Unit = {}
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private val queued = ArrayList<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    /** A short, plain message when the current song could not be played, otherwise null. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sleep = MutableStateFlow(SleepState())
    val sleep: StateFlow<SleepState> = _sleep.asStateFlow()
    private var sleepJob: Job? = null

    /** Set when the user picks another song on purpose (back, queue), so it is not counted as a skip. */
    private var ignoreNextSkip = false

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            _error.value = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                    "No internet connection. Check it and try again."
                else -> "Couldn't load this song. It may be unavailable right now."
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // A headset button or lock screen must not start this player on top of a DJ set.
            if (isPlaying && djBusy?.invoke() == true) controller?.pause()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                _error.value = null
            }
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
            _error.value = null
            learnFrom(item.mediaId, reason)
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
                if (controller?.isPlaying == true) {
                    refresh()
                    onTick(500)
                }
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

    /**
     * Keeps the music going when the queue runs out. It looks at the last few songs, not just the
     * very last one, so the mood stays steady. Suggestions are blended between those songs, songs
     * and artists you said "Don't play this" for (or keep skipping) are left out, and the same
     * artist is not played back to back.
     */
    private fun extendQueue(lastMediaId: String) {
        val c = controller ?: return
        val count = c.mediaItemCount
        if (count == 0) return
        val seeds = (0 until count).toList().takeLast(4).map { c.getMediaItemAt(it).mediaId }
            .ifEmpty { listOf(lastMediaId) }
        val lastArtist = c.getMediaItemAt(count - 1).mediaMetadata.artist?.toString().orEmpty()
        scope.launch {
            val lists = coroutineScope {
                seeds.map { seed ->
                    async { runCatching { YtMusic.related(seed) }.getOrDefault(emptyList()) }
                }.awaitAll()
            }
            // Take one from each list in turn, so no single song dominates.
            val pending = lists.map { it.toMutableList() }
            val merged = ArrayList<SongEntity>()
            while (pending.any { it.isNotEmpty() }) {
                for (list in pending) if (list.isNotEmpty()) merged.add(list.removeAt(0))
            }
            withController { ctl ->
                val existing = (0 until ctl.mediaItemCount).map { ctl.getMediaItemAt(it).mediaId }.toSet()
                val fresh = Taste.filterSuggestions(merged)
                    .distinctBy { it.url }
                    .filter { it.url !in existing }
                    .sortedByDescending { if (Affinity.artistScore(it.artist) > 0.5) 1 else 0 }
                    .take(20)
                if (fresh.isNotEmpty()) ctl.addMediaItems(spreadArtists(fresh, lastArtist).map(::toItem))
            }
        }
    }

    /** Reorder so the same artist does not come twice in a row, where it can be avoided. */
    private fun spreadArtists(songs: List<SongEntity>, lastArtist: String): List<SongEntity> {
        val pending = songs.toMutableList()
        val out = ArrayList<SongEntity>()
        var previous = lastArtist.trim().lowercase()
        while (pending.isNotEmpty()) {
            val i = pending.indexOfFirst { it.artist.trim().lowercase() != previous }.let { if (it < 0) 0 else it }
            val song = pending.removeAt(i)
            out.add(song)
            previous = song.artist.trim().lowercase()
        }
        return out
    }

    /**
     * Learn from how the last song ended: played through counts for the artist, skipped in the
     * first seconds counts against. The state still describes the old song at this moment.
     */
    private fun learnFrom(newId: String, reason: Int) {
        val old = _state.value
        val oldId = old.mediaId ?: return
        val song = old.asSong()
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            if (song != null) Affinity.replayed(song)
            return
        }
        if (oldId == newId || old.artist.isBlank() || song == null) return
        when (reason) {
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> {
                Taste.recordFinish(old.artist)
                Affinity.finished(song)
            }
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> {
                if (ignoreNextSkip) {
                    ignoreNextSkip = false
                } else if (old.positionMs < SKIP_WITHIN_MS && old.durationMs > MIN_SONG_MS) {
                    Taste.recordSkip(old.artist)
                    Affinity.skipped(song)
                } else if (old.durationMs > MIN_SONG_MS && old.positionMs > old.durationMs / 2) {
                    Affinity.partlyPlayed(song)
                }
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
    fun play(songs: List<SongEntity>, index: Int = 0, shuffle: Boolean = false, whole: Boolean = false) {
        if (songs.isEmpty()) return
        // While a DJ set is on, a chosen song is mixed into the set instead of starting this player.
        if (djPlay?.invoke(songs, index, shuffle, whole) == true) return
        // Songs marked "Don't play this" are left out, unless that exact song was tapped.
        val queue = songs.filterIndexed { i, s -> !Taste.isBlocked(s) || (!shuffle && i == index) }
            .ifEmpty { songs }
        withController { c ->
            c.shuffleModeEnabled = shuffle
            // A shuffled start begins at a random song, not always the first one in the list.
            val start = if (shuffle) {
                queue.indices.random()
            } else {
                songs.take(index.coerceIn(0, songs.lastIndex)).count { !Taste.isBlocked(it) }
                    .coerceIn(0, queue.lastIndex)
            }
            c.setMediaItems(queue.map(::toItem), start, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playNext(song: SongEntity) {
        if (djQueue?.invoke(song, true) == true) return
        playNextNormal(song)
    }

    private fun playNextNormal(song: SongEntity) = withController { c ->
        if (c.mediaItemCount == 0) play(listOf(song))
        else c.addMediaItem(c.currentMediaItemIndex + 1, toItem(song))
    }

    fun addToQueue(song: SongEntity) {
        if (djQueue?.invoke(song, false) == true) return
        addToQueueNormal(song)
    }

    private fun addToQueueNormal(song: SongEntity) = withController { c ->
        if (c.mediaItemCount == 0) play(listOf(song)) else c.addMediaItem(toItem(song))
    }

    /** Try the current song again after a failure. */
    fun retry() = withController { c ->
        _error.value = null
        c.prepare()
        c.play()
    }

    /** Give up on a song that will not load and go on to the next one. */
    fun skipFailed() = withController { c ->
        _error.value = null
        ignoreNextSkip = true
        if (c.hasNextMediaItem()) c.seekToNextMediaItem()
        c.prepare()
        c.play()
    }

    fun toggle() {
        if (djToggle?.invoke() == true) return
        withController { c -> if (c.isPlaying) c.pause() else c.play() }
    }

    /** Hooks the DJ set uses while it runs. Each returns true when the DJ handled the request. */
    var djPlay: ((List<SongEntity>, Int, Boolean, Boolean) -> Boolean)? = null
    var djToggle: (() -> Boolean)? = null
    var djQueue: ((SongEntity, Boolean) -> Boolean)? = null
    var djBusy: (() -> Boolean)? = null

    /** The DJ set is starting: silence the normal player. */
    fun pauseForDj() = withController { it.pause() }
    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController {
        // Behave like every music app: restart the song first, go back if already near the start.
        if (it.currentPosition > 3000 || !it.hasPreviousMediaItem()) it.seekTo(0)
        else {
            ignoreNextSkip = true
            it.seekToPreviousMediaItem()
        }
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
            if (index != it.currentMediaItemIndex) ignoreNextSkip = true
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

    private companion object {
        /** A song left within this long counts as a skip. */
        const val SKIP_WITHIN_MS = 30_000L

        /** Very short tracks are never counted as skips. */
        const val MIN_SONG_MS = 60_000L
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
