package app.hush.dj

import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import app.hush.Prefs
import app.hush.Taste
import app.hush.data.SongEntity
import app.hush.net.YtMusic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** A song that has been downloaded and listened to, ready to be mixed. */
class DjTrack(val song: SongEntity, val file: File, val a: BeatAnalyzer.Result)

data class DjDeckUi(
    val title: String = "",
    val artist: String = "",
    val art: String? = null,
    val bpm: Float = 0f,
    val speed: Float = 1f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** Which beat of a four-beat bar is sounding (0 to 3), or -1. */
    val beat: Int = -1,
    val audible: Boolean = false,
    val loaded: Boolean = false
)

data class DjUi(
    val running: Boolean = false,
    val playing: Boolean = false,
    val status: String = "",
    val decks: List<DjDeckUi> = listOf(DjDeckUi(), DjDeckUi()),
    val master: Int = 0,
    /** 0 to 1 while two songs are being mixed, otherwise -1. */
    val blend: Float = -1f,
    val energy: Float = 0f,
    val energyTarget: Float = 0f,
    /** The tempo the room is hearing right now. */
    val mixBpm: Float = 0f,
    val nextTitle: String? = null,
    val secondsToMix: Int = -1,
    val title: String = "",
    val artist: String = "",
    val art: String? = null
)

/**
 * The DJ. It plays two songs on two "decks" at once, downloads and listens to the next songs in the
 * background, and mixes them: it nudges the speed of the incoming song so the beats line up, brings
 * it in on a beat, swaps the bass, and fades the old song out. Songs are picked by how well they
 * fit the tempo and the energy the set is aiming for.
 */
@OptIn(UnstableApi::class)
class DjEngine(
    private val app: Context,
    val feed: DjFeed,
    /** A song just started (used for history and taste learning). */
    private val onTrackStarted: (SongEntity) -> Unit,
    /** A song is over: 1 = played through, 0 = left part-way, 2 = skipped early. */
    private val onTrackEnded: (SongEntity, Int) -> Unit,
    /** Listening time, about every 500 ms while music plays. */
    private val onTick: (Long) -> Unit,
    /** Pauses the normal player when a DJ set starts. */
    private val pauseMain: () -> Unit
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _ui = MutableStateFlow(DjUi())
    val ui: StateFlow<DjUi> = _ui.asStateFlow()

    private enum class Phase { Loading, Playing, Mixing }

    private class Plan(
        val next: DjTrack,
        var beatMatched: Boolean,
        /** Where in the current song (ms) the new song comes in. */
        var t: Double,
        /** How long the mix lasts (ms of the current song). */
        var len: Double,
        val inStart: Double,
        var inSpeed: Float,
        val outcome: Int
    ) {
        var loaded = false
        var startWall = 0L
    }

    private inner class Deck(val id: Int) {
        val fx = DjFx()
        val player: ExoPlayer = ExoPlayer.Builder(app, renderers(fx))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        var track: DjTrack? = null
        var speed = 1f
        var failed = false

        init {
            player.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    failed = true
                }
            })
        }

        fun setSpeed(value: Float, force: Boolean = false) {
            if (force || abs(value - speed) > 0.0002f) {
                speed = value
                player.playbackParameters = PlaybackParameters(value, 1f)
            }
        }

        fun load(t: DjTrack, startMs: Long, speedValue: Float) {
            track = t
            failed = false
            fx.gain = 0f
            fx.bassCut = 0f
            fx.lowPassHz = 20000f
            fx.echoSend = 0f
            speed = speedValue
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(t.file)), startMs)
            player.playbackParameters = PlaybackParameters(speedValue, 1f)
            player.playWhenReady = false
            player.prepare()
        }

        fun clear() {
            try {
                player.stop()
                player.clearMediaItems()
            } catch (_: Exception) {
            }
            track?.file?.delete()
            track = null
            failed = false
        }

        fun position(): Double = player.currentPosition.toDouble()
        fun ready(): Boolean = player.playbackState == Player.STATE_READY
        fun ended(): Boolean = failed || player.playbackState == Player.STATE_ENDED
        fun release() {
            try {
                player.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun renderers(fx: DjFx) = RenderersFactory { handler, _, audioListener, _, _ ->
        val sink = DefaultAudioSink.Builder(app)
            .setAudioProcessors(arrayOf<AudioProcessor>(fx))
            .build()
        arrayOf<Renderer>(
            MediaCodecAudioRenderer(app, MediaCodecSelector.DEFAULT, false, handler, audioListener, sink)
        )
    }

    // --- State ---------------------------------------------------------------------------------

    private var running = false
    private var paused = false
    private var pausedByFocus = false
    private var session = 0
    private var decks: List<Deck> = emptyList()
    private var master = 0
    private var phase = Phase.Loading
    private var plan: Plan? = null
    private val pool = ArrayList<DjTrack>()
    private val claimed = HashSet<String>()
    private var cursor = 0
    private var status = ""
    private var startedAt = 0L
    private var blendProgress = -1f
    private var lastNudge = 0L
    private var relaxAcc = 0L
    private var tickAcc = 0L

    private var loopJob: Job? = null
    private var prepJob: Job? = null
    private var bootJob: Job? = null
    private var tailJob: Job? = null

    private val dir = File(app.cacheDir, "dj")
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> if (running && !paused) pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (running && !paused) {
                pausedByFocus = true
                pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (pausedByFocus) {
                pausedByFocus = false
                resume()
            }
        }
    }

    val isRunning: Boolean get() = running

    // --- Controls ------------------------------------------------------------------------------

    /** Starts a DJ set with the song at [index] of the DJ list. */
    fun start(index: Int) {
        val list = feed.songs.value
        if (list.isEmpty()) return
        stopInternal()
        pauseMain()

        running = true
        paused = false
        pausedByFocus = false
        session++
        val mySession = session
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        decks = listOf(Deck(0), Deck(1))
        master = 0
        phase = Phase.Loading
        plan = null
        pool.clear()
        claimed.clear()
        cursor = index.coerceIn(0, list.lastIndex)
        startedAt = SystemClock.elapsedRealtime()
        status = "Getting the first track ready..."
        requestFocus()
        ContextCompat.startForegroundService(app, Intent(app, DjService::class.java))
        publish()

        bootJob = scope.launch {
            var first: DjTrack? = null
            var tries = 0
            while (first == null && tries < 6 && isActive && session == mySession) {
                val song = nextCandidate() ?: break
                tries++
                first = prepareSong(song) { text ->
                    scope.launch {
                        if (session == mySession && phase == Phase.Loading) {
                            status = text
                            publish()
                        }
                    }
                }
            }
            if (session != mySession || !running) return@launch
            if (first == null) {
                status = "Couldn't get a track ready. Check your connection and try again."
                publish()
                delay(2500)
                if (session == mySession) stop()
                return@launch
            }
            val d = decks[0]
            d.load(first, first.a.beatAtOrAfter(first.a.startMs.toDouble()).toLong(), 1f)
            d.fx.gain = 1f
            d.player.playWhenReady = true
            master = 0
            phase = Phase.Playing
            status = ""
            onTrackStarted(first.song)
            loop(mySession)
            prepLoop(mySession)
        }
    }

    fun stop() {
        if (!running && decks.isEmpty()) return
        val m = decks.getOrNull(master)
        val t = m?.track
        if (running && t != null) onTrackEnded(t.song, 0)
        stopInternal()
        _ui.value = DjUi()
    }

    private fun stopInternal() {
        running = false
        session++
        loopJob?.cancel()
        prepJob?.cancel()
        bootJob?.cancel()
        tailJob?.cancel()
        decks.forEach {
            it.release()
        }
        decks = emptyList()
        pool.clear()
        plan = null
        blendProgress = -1f
        dir.listFiles()?.forEach { it.delete() }
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        try {
            app.stopService(Intent(app, DjService::class.java))
        } catch (_: Exception) {
        }
    }

    fun pause() {
        if (!running) return
        paused = true
        decks.forEach { it.player.playWhenReady = false }
        publish()
    }

    fun resume() {
        if (!running) return
        paused = false
        requestFocus()
        val m = decks.getOrNull(master) ?: return
        m.player.playWhenReady = true
        if (phase == Phase.Mixing) decks.getOrNull(1 - master)?.player?.playWhenReady = true
        publish()
    }

    fun toggle() {
        if (paused) resume() else pause()
    }

    /** Brings in the next song now: on the next beat, with a short mix. [quick] means a very short one. */
    fun mixNow(quick: Boolean) {
        if (!running || phase != Phase.Playing || paused) return
        val m = decks[master]
        val mt = m.track ?: return
        val pos = m.position()
        val outcome = if (quick) (if (pos - mt.a.startMs < 20_000.0) 2 else 0) else 1
        val existing = plan
        if (existing == null) {
            val next = pickNext(mt, m.speed)
            if (next == null) {
                status = "The next track is still getting ready..."
                publish()
                return
            }
            plan = buildPlan(m, mt, next, pos, if (quick) QUICK else NOW, outcome)
        } else {
            val fresh = buildPlan(m, mt, existing.next, pos, if (quick) QUICK else NOW, outcome)
            // Keep what has already been loaded, just move the time and length.
            existing.beatMatched = fresh.beatMatched
            existing.t = fresh.t
            existing.len = fresh.len
            existing.inSpeed = fresh.inSpeed
            if (existing.loaded) decks[1 - master].setSpeed(fresh.inSpeed)
        }
        status = ""
        publish()
    }

    // --- Background work -----------------------------------------------------------------------

    private suspend fun nextCandidate(): SongEntity? {
        var rounds = 0
        while (rounds < 3) {
            val list = feed.songs.value
            while (cursor < list.size) {
                val s = list[cursor++]
                if (Taste.isBlocked(s)) continue
                if (claimed.add(s.url)) return s
            }
            rounds++
            feed.loadMore()
            if (feed.songs.value.size <= list.size) delay(1500)
        }
        return null
    }

    private suspend fun prepareSong(song: SongEntity, stage: ((String) -> Unit)? = null): DjTrack? {
        return try {
            stage?.invoke("Finding the song...")
            val file = download(song, stage)
            stage?.invoke("Listening for the beat...")
            val analysis = withContext(Dispatchers.Default) { BeatAnalyzer.analyze(file.absolutePath) }
            if (analysis == null) {
                file.delete()
                null
            } else {
                DjTrack(song, file, analysis)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun download(song: SongEntity, stage: ((String) -> Unit)? = null): File =
        withContext(Dispatchers.IO) {
            val link = YtMusic.resolveStream(song.url)
            val name = "t" + (song.url.hashCode().toLong() and 0xffffffffL) + ".dj"
            val out = File(dir, name)
            if (out.exists() && out.length() > 200_000L) return@withContext out
            val part = File(dir, "$name.part")
            part.delete()

            val total = Regex("[?&]clen=(\\d+)").find(link)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            if (total > 200_000L) {
                try {
                    downloadChunks(link, total, part, stage)
                } catch (e: CancellationException) {
                    part.delete()
                    throw e
                } catch (e: Exception) {
                    // Fall back to one plain request below.
                    part.delete()
                }
            }
            if (!part.exists() || part.length() < 200_000L) {
                part.delete()
                stage?.invoke("Downloading...")
                val request = Request.Builder().url(link).header("User-Agent", UA).build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty response")
                    body.byteStream().use { input -> part.outputStream().use { o -> input.copyTo(o) } }
                }
            }
            if (part.length() < 200_000L) {
                part.delete()
                throw IOException("Download too small")
            }
            part.renameTo(out)
            out
        }

    /** YouTube slows down whole-file requests, so ask for the song in pieces, several at once. */
    private suspend fun downloadChunks(link: String, total: Long, part: File, stage: ((String) -> Unit)?) {
        val chunk = 1_000_000L
        val count = ((total + chunk - 1) / chunk).toInt()
        val done = java.util.concurrent.atomic.AtomicLong(0)
        val next = java.util.concurrent.atomic.AtomicInteger(0)
        val file = java.io.RandomAccessFile(part, "rw")
        try {
            file.setLength(total)
            kotlinx.coroutines.coroutineScope {
                val workers = (1..4).map {
                    launch(Dispatchers.IO) {
                        while (true) {
                            val i = next.getAndIncrement()
                            if (i >= count) break
                            val from = i * chunk
                            val to = minOf(total - 1, from + chunk - 1)
                            val request = Request.Builder()
                                .url("$link&range=$from-$to")
                                .header("User-Agent", UA)
                                .build()
                            http.newCall(request).execute().use { response ->
                                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                                val bytes = (response.body ?: throw IOException("Empty response")).bytes()
                                if (bytes.size.toLong() != to - from + 1) throw IOException("Short chunk")
                                synchronized(file) {
                                    file.seek(from)
                                    file.write(bytes)
                                }
                                val now = done.addAndGet(bytes.size.toLong())
                                stage?.invoke("Downloading... ${(now * 100 / total).toInt()}%")
                            }
                        }
                    }
                }
                workers.forEach { it.join() }
            }
        } finally {
            file.close()
        }
    }

    /** Keeps a few songs downloaded and analysed ahead of time. */
    private fun prepLoop(mySession: Int) {
        prepJob = scope.launch {
            while (isActive && session == mySession) {
                if (pool.size < POOL_TARGET) {
                    val song = nextCandidate()
                    if (song == null) {
                        delay(3000)
                    } else {
                        val t = prepareSong(song)
                        if (t != null && session == mySession && running) pool.add(t) else t?.file?.delete()
                    }
                } else {
                    delay(1000)
                }
            }
        }
    }

    private fun loop(mySession: Int) {
        loopJob = scope.launch {
            var last = SystemClock.elapsedRealtime()
            var n = 0
            while (isActive && session == mySession) {
                delay(20)
                val now = SystemClock.elapsedRealtime()
                val dt = now - last
                last = now
                try {
                    step(now, dt)
                } catch (e: Exception) {
                    // A bad moment in one step must never stop the music.
                }
                n++
                if (n % 2 == 0) publish()
                if (!paused) {
                    tickAcc += dt
                    if (tickAcc >= 500) {
                        tickAcc -= 500
                        onTick(500)
                    }
                }
            }
        }
    }

    // --- The mixing brain ----------------------------------------------------------------------

    private fun step(now: Long, dt: Long) {
        if (paused) return
        val m = decks[master]
        val mt = m.track ?: return
        when (phase) {
            Phase.Loading -> {}
            Phase.Playing -> {
                val pos = m.position()
                if (m.ended()) {
                    advanceDirect()
                    return
                }
                if (plan == null) {
                    relaxSpeed(m, dt)
                    maybePlan(m, mt, pos)
                }
                val p = plan ?: return
                val inc = decks[1 - master]
                if (!p.loaded) loadIncoming(p, inc)
                if (inc.failed) {
                    // The next song would not play: forget it and choose another.
                    inc.clear()
                    plan = null
                    return
                }
                if (p.loaded && inc.ready() && (p.t - pos) / m.speed <= LEAD_MS) {
                    if (p.beatMatched && pos - p.t > 90.0) retime(m, mt, p, pos) else startMix(now, p, m, inc)
                }
            }
            Phase.Mixing -> mixStep(now, m, decks[1 - master])
        }
    }

    private fun relaxSpeed(m: Deck, dt: Long) {
        relaxAcc += dt
        if (relaxAcc < 500) return
        relaxAcc = 0
        val diff = 1f - m.speed
        if (abs(diff) < 0.0006f) return
        m.setSpeed(m.speed + (if (diff > 0) 0.0006f else -0.0006f))
    }

    /** About 40 seconds before the song's outro, choose what comes next and get it ready. */
    private fun maybePlan(m: Deck, mt: DjTrack, pos: Double) {
        val (t, _) = provisional(m, mt, pos)
        if (pos < t - PLAN_AHEAD_MS) return
        val next = pickNext(mt, m.speed)
        if (next == null) {
            status = "Preparing the next track..."
            return
        }
        status = ""
        plan = buildPlan(m, mt, next, pos, NORMAL, 1)
    }

    private fun endOf(m: Deck, mt: DjTrack): Double {
        val dur = m.player.duration
        val real = if (dur > 0) dur.toDouble() else mt.a.durationMs.toDouble()
        return min(mt.a.endMs.toDouble(), real)
    }

    /** Where, and for how long, the mix would happen if nothing were chosen yet. */
    private fun provisional(m: Deck, mt: DjTrack, pos: Double): Pair<Double, Double> {
        val a = mt.a
        val end = endOf(m, mt)
        if (a.hasBeat) {
            for (beats in intArrayOf(32, 16, 8)) {
                val len = beats * a.periodMs
                val t = a.beatAtOrBefore(end - len - 500.0)
                if (t >= pos + 3000.0) return t to len
            }
        }
        val len = 7000.0
        return max(pos + 1500.0, end - len - 300.0) to len
    }

    private fun buildPlan(m: Deck, mt: DjTrack, next: DjTrack, pos: Double, mode: Int, outcome: Int): Plan {
        val a = mt.a
        val n = next.a
        val end = endOf(m, mt)
        val ratio = if (a.hasBeat && n.hasBeat) (a.bpm * m.speed) / n.bpm else 0f
        val matchable = ratio > 0f && abs(ratio - 1f) <= MAX_TEMPO_SHIFT

        if (matchable) {
            var t: Double
            var len: Double
            if (mode == NORMAL) {
                val (pt, plen) = provisional(m, mt, pos)
                t = pt
                len = plen
            } else {
                t = a.beatAtOrAfter(pos + 1200.0)
                len = (if (mode == QUICK) 4 else 16) * a.periodMs
            }
            if (t + len > end) {
                val fit = floor((end - t) / a.periodMs)
                len = fit * a.periodMs
            }
            if (len >= 2 * a.periodMs && t >= pos) {
                return Plan(next, true, t, len, n.beatAtOrAfter(n.startMs.toDouble()), ratio, outcome)
            }
        }
        val len = when (mode) {
            QUICK -> 2500.0
            NOW -> 5000.0
            else -> 7000.0
        }
        val t = if (mode == NORMAL) max(pos + 1500.0, end - len - 300.0) else pos + 250.0
        return Plan(next, false, t, len, n.startMs.toDouble(), 1f, outcome)
    }

    /** The planned moment slipped by (the next song was late): aim for a later beat. */
    private fun retime(m: Deck, mt: DjTrack, p: Plan, pos: Double) {
        val a = mt.a
        val end = endOf(m, mt)
        val t = a.beatAtOrAfter(pos + 800.0)
        val fit = floor((end - t) / a.periodMs)
        val len = min(p.len, fit * a.periodMs)
        if (len >= 2 * a.periodMs) {
            p.t = t
            p.len = len
        } else {
            p.beatMatched = false
            p.inSpeed = 1f
            p.t = pos + 250.0
            p.len = 5000.0
            decks[1 - master].setSpeed(1f)
        }
    }

    private fun loadIncoming(p: Plan, inc: Deck) {
        tailJob?.cancel()
        inc.clear()
        inc.load(p.next, p.inStart.toLong(), p.inSpeed)
        p.loaded = true
    }

    private fun startMix(now: Long, p: Plan, m: Deck, inc: Deck) {
        inc.fx.gain = 0f
        inc.player.playWhenReady = true
        p.startWall = now
        lastNudge = now
        phase = Phase.Mixing
        blendProgress = 0f
        onTrackStarted(p.next.song)
    }

    private fun mixStep(now: Long, m: Deck, inc: Deck) {
        val p = plan
        if (p == null) {
            phase = Phase.Playing
            return
        }
        val pos = m.position()
        val progress = if (p.beatMatched) (pos - p.t) / p.len else (now - p.startWall) / p.len
        val pr = progress.toFloat().coerceIn(0f, 1f)
        blendProgress = pr
        applyAutomation(m, inc, p, pr)
        if (p.beatMatched && now - lastNudge >= 120) {
            lastNudge = now
            nudge(m, inc, p)
        }
        if (pr >= 1f || m.ended()) finishMix(m, inc, p)
    }

    /** Closed loop: if the new song's beat runs ahead or behind, speed it up or slow it a hair. */
    private fun nudge(m: Deck, inc: Deck, p: Plan) {
        val mt = m.track ?: return
        val it = inc.track ?: return
        val phM = mt.a.phaseAt(m.position())
        val phI = it.a.phaseAt(inc.position())
        var e = phI - phM
        if (e > 0.5f) e -= 1f
        if (e < -0.5f) e += 1f
        val correction = (e * 0.25f).coerceIn(-0.03f, 0.03f)
        inc.setSpeed(p.inSpeed * (1f - correction))
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun applyAutomation(m: Deck, inc: Deck, p: Plan, pr: Float) {
        val angle = (pr * (PI / 2.0))
        m.fx.gain = cos(angle).toFloat()
        inc.fx.gain = sin(angle).toFloat()
        if (!p.beatMatched) {
            m.fx.bassCut = 0f
            inc.fx.bassCut = 0f
            m.fx.lowPassHz = 20000f
            inc.fx.lowPassHz = 20000f
            m.fx.echoSend = 0f
            return
        }
        if (Prefs.djBassSwap) {
            val swap = smooth(0.40f, 0.56f, pr)
            m.fx.bassCut = swap
            inc.fx.bassCut = 1f - swap
        } else {
            m.fx.bassCut = 0f
            inc.fx.bassCut = 0f
        }
        if (Prefs.djFilter) {
            val open = smooth(0f, 0.5f, pr)
            inc.fx.lowPassHz = if (open >= 0.995f) 20000f else 400f * 50f.pow(open)
            val close = smooth(0.6f, 1f, pr)
            m.fx.lowPassHz = if (close <= 0.005f) 20000f else 20000f * 0.06f.pow(close)
        } else {
            inc.fx.lowPassHz = 20000f
            m.fx.lowPassHz = 20000f
        }
        val period = m.track?.a?.periodMs ?: 450.0
        m.fx.echoDelayMs = (period * 0.75).toFloat()
        m.fx.echoSend = if (Prefs.djEcho && pr > 0.8f) 1f else 0f
    }

    private fun finishMix(old: Deck, inc: Deck, p: Plan) {
        val oldTrack = old.track
        old.fx.gain = 0f
        old.fx.bassCut = 1f
        inc.fx.gain = 1f
        inc.fx.bassCut = 0f
        inc.fx.lowPassHz = 20000f
        inc.fx.echoSend = 0f
        inc.setSpeed(inc.speed.coerceIn(0.9f, 1.1f))
        if (oldTrack != null) onTrackEnded(oldTrack.song, p.outcome)

        // Let the echo ring out, then let the old song go.
        val period = oldTrack?.a?.periodMs ?: 450.0
        val tail = if (Prefs.djEcho && p.beatMatched) (period * 4.0 / max(0.5f, old.speed)).toLong() else 300L
        old.fx.echoSend = 0f
        val mySession = session
        tailJob?.cancel()
        tailJob = scope.launch {
            delay(tail)
            if (session == mySession) old.clear()
        }

        master = 1 - master
        plan = null
        phase = Phase.Playing
        blendProgress = -1f
        status = ""
    }

    /** The playing song ended before a mix could happen (or failed): start the next one straight away. */
    private fun advanceDirect() {
        val m = decks[master]
        val mt = m.track
        val p = plan
        val next = p?.next ?: pickNext(mt ?: return, m.speed)
        if (next == null) {
            status = "Preparing the next track..."
            return
        }
        if (mt != null) onTrackEnded(mt.song, 1)
        val inc = decks[1 - master]
        tailJob?.cancel()
        if (p == null || !p.loaded) {
            inc.clear()
            inc.load(next, next.a.beatAtOrAfter(next.a.startMs.toDouble()).toLong(), 1f)
        } else {
            inc.setSpeed(1f)
        }
        inc.fx.gain = 1f
        inc.player.playWhenReady = true
        m.clear()
        master = 1 - master
        plan = null
        phase = Phase.Playing
        status = ""
        onTrackStarted(next.song)
    }

    // --- Choosing songs ------------------------------------------------------------------------

    /** The energy the set is aiming for right now: steady, building, or rising and falling. */
    private fun energyTarget(): Float {
        val minutes = (SystemClock.elapsedRealtime() - startedAt) / 60000f
        return when (Prefs.djShape) {
            0 -> 0.65f
            1 -> 0.35f + 0.6f * (minutes / 40f).coerceIn(0f, 1f)
            else -> (0.62f + 0.30f * sin(2.0 * PI * minutes / 36.0 - PI / 2.0)).toFloat()
        }
    }

    private fun pickNext(mt: DjTrack, speed: Float): DjTrack? {
        if (pool.isEmpty()) return null
        val target = energyTarget()
        val mixBpm = mt.a.bpm * speed
        var best: DjTrack? = null
        var bestCost = Float.MAX_VALUE
        for (cand in pool) {
            var cost = abs(cand.a.energy - target) * 3f
            cost += if (mt.a.hasBeat && cand.a.hasBeat) {
                val r = mixBpm / cand.a.bpm
                if (abs(r - 1f) <= MAX_TEMPO_SHIFT) abs(r - 1f) * 10f else 6f
            } else {
                3f
            }
            if (cost < bestCost) {
                bestCost = cost
                best = cand
            }
        }
        if (best != null) pool.remove(best)
        return best
    }

    // --- What the screen shows -----------------------------------------------------------------

    private fun publish() {
        if (!running) return
        val list = decks.map { d ->
            val t = d.track
            if (t == null) {
                DjDeckUi()
            } else {
                val pos = d.position()
                DjDeckUi(
                    title = t.song.title,
                    artist = t.song.artist,
                    art = t.song.thumb,
                    bpm = t.a.bpm,
                    speed = d.speed,
                    positionMs = pos.toLong(),
                    durationMs = t.a.durationMs,
                    beat = if (t.a.hasBeat && d.player.isPlaying) ((t.a.beatNumberAt(pos) % 4) + 4) % 4 else -1,
                    audible = d.id == master || phase == Phase.Mixing,
                    loaded = true
                )
            }
        }
        val m = decks.getOrNull(master)
        val mt = m?.track
        var seconds = -1
        if (m != null && mt != null && phase == Phase.Playing) {
            val pos = m.position()
            val t = plan?.t ?: provisional(m, mt, pos).first
            seconds = ((t - pos) / m.speed / 1000.0).toInt().coerceAtLeast(0)
        }
        val next = plan?.next ?: pool.firstOrNull()
        _ui.value = DjUi(
            running = true,
            playing = !paused && phase != Phase.Loading,
            status = status,
            decks = list,
            master = master,
            blend = if (phase == Phase.Mixing) blendProgress else -1f,
            energy = mt?.a?.energy ?: 0f,
            energyTarget = energyTarget(),
            mixBpm = if (mt != null && m != null && mt.a.hasBeat) mt.a.bpm * m.speed else 0f,
            nextTitle = next?.song?.title,
            secondsToMix = seconds,
            title = mt?.song?.title ?: "",
            artist = mt?.song?.artist ?: "",
            art = mt?.song?.thumb
        )
    }

    private fun requestFocus() {
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private companion object {
        const val UA = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
        const val NORMAL = 0
        const val NOW = 1
        const val QUICK = 2
        const val POOL_TARGET = 3
        const val PLAN_AHEAD_MS = 40_000.0
        const val LEAD_MS = 45.0
        const val MAX_TEMPO_SHIFT = 0.08f
    }
}
