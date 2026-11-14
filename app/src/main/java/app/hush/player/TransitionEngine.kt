package app.hush.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.hush.Prefs
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Shared flags between the app side and the playback service (same process). */
object Fades {
    /** True while the "end of song" sleep timer is on: the next song must not start early. */
    @Volatile
    var endOfSongSleep: Boolean = false
}

/**
 * Smooth song changes and the beta DJ mode.
 *
 * How the crossfade works, with two players:
 *  1. About 10 seconds before a song ends, a second player quietly loads the next song.
 *  2. When the fade starts, the second player starts the next song while the main player's
 *     song fades out. The two volumes follow a quarter circle, so loudness stays even.
 *  3. Just before the old song ends, the main player jumps into the same place of the next
 *     song, the two blend for a moment, and the second player is thrown away.
 *
 * The queue, shuffle, repeat, the notification and the lock screen only ever see the main
 * player. If anything goes wrong (the next song is slow to load, the queue changes, the user
 * skips), the second player is dropped and the old single-player fade is used instead.
 *
 * DJ mode adds a tempo ride on top: the ending song speeds up a little and the next one
 * settles back to normal speed. The pitch stays the same.
 */
@UnstableApi
class TransitionEngine(
    private val player: Player,
    /** Builds the second player: same sources as the main one, but it never takes audio focus. */
    private val createAux: () -> ExoPlayer
) {

    private enum class Phase { IDLE, ARMED, FADE, HANDOFF, BLEND }

    private val handler = Handler(Looper.getMainLooper())

    private var phase = Phase.IDLE
    private var aux: ExoPlayer? = null
    private var auxId: String? = null
    private var currentId: String? = null
    private var doneForId: String? = null
    private var fadeMs = 0L
    private var ourSeekUntil = 0L
    private var handoffAt = 0L
    private var blendAt = 0L
    private var corrected = false
    private var auxFailed = false

    // Single-player fades (the fallback, and the fade-in after skipping).
    private var introActive = false
    private var introMs = 0L

    private var lastVolume = 1f
    private var lastSpeed = 1f
    private var lastAuxSpeed = 1f

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // The jump the crossfade makes by itself is not a new song for us.
            if (phase == Phase.HANDOFF && SystemClock.uptimeMillis() < ourSeekUntil) {
                ourSeekUntil = 0L
                return
            }
            // Any other change of song (it ended, skipped, tapped): forget the second player.
            drop()
            if (!enabled()) {
                introActive = false
                setVolume(1f)
                setSpeed(1f)
                return
            }
            when (reason) {
                Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> {
                    introActive = false
                    setVolume(1f)
                    setSpeed(1f)
                }
                // Followed on by itself after a plain fade-out.
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> startIntro(wantedMs())
                // Skipped, or a song was tapped: a short fade-in so it never starts with a bump.
                else -> startIntro(QUICK_MS)
            }
        }
    }

    private val auxListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            auxFailed = true
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            runCatching { update() }
            val next = when {
                phase == Phase.FADE || phase == Phase.HANDOFF || phase == Phase.BLEND -> FAST_MS
                player.isPlaying -> NORMAL_MS
                else -> SLOW_MS
            }
            handler.postDelayed(this, next)
        }
    }

    fun start() {
        player.addListener(listener)
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        player.removeListener(listener)
        drop()
        setVolume(1f)
        setSpeed(1f)
    }

    private fun enabled(): Boolean = Prefs.smoothChanges || Prefs.djMode

    /** The fade length asked for in Settings. DJ mode needs a little more room. */
    private fun wantedMs(): Long {
        val w = Prefs.fadeSeconds.coerceIn(1, 15) * 1000L
        return if (Prefs.djMode) max(w, DJ_MIN_MS) else w
    }

    private fun startIntro(ms: Long) {
        introActive = true
        introMs = ms
        setVolume(0f)
    }

    // --- The main loop ------------------------------------------------------------------

    private fun update() {
        if (!enabled()) {
            drop()
            introActive = false
            setVolume(1f)
            setSpeed(1f)
            return
        }
        if (auxFailed) {
            auxFailed = false
            val was = phase
            drop()
            if (was == Phase.HANDOFF || was == Phase.BLEND) {
                setVolume(1f)
                setSpeed(1f)
            }
        }
        when (phase) {
            Phase.FADE -> { runFade(); return }
            Phase.HANDOFF -> { runHandoff(); return }
            Phase.BLEND -> { runBlend(); return }
            else -> Unit
        }

        if (!player.isPlaying) return
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return

        val dj = Prefs.djMode
        // Never let the fades take more than a third of a short song.
        val fade = min(wantedMs(), duration / 3)
        if (fade < 500L) return

        val position = player.currentPosition.coerceAtLeast(0L)
        val remaining = duration - position
        val id = player.currentMediaItem?.mediaId
        val nextIndex = player.nextMediaItemIndex
        val canFade = id != null && nextIndex != C.INDEX_UNSET &&
            player.repeatMode != Player.REPEAT_MODE_ONE

        if (canFade && id != null && doneForId != id && !Fades.endOfSongSleep) {
            if (phase == Phase.IDLE && remaining <= fade + ARM_AHEAD_MS) arm(id, nextIndex)
            val x = aux
            if (phase == Phase.ARMED && x != null) {
                when {
                    // The queue changed under us.
                    player.getMediaItemAt(nextIndex).mediaId != auxId -> {
                        drop()
                        doneForId = id
                    }
                    remaining <= fade && x.playbackState == Player.STATE_READY -> {
                        startFade(x, id, fade)
                        return
                    }
                    // The next song was not ready in time: use the plain fade instead.
                    remaining <= (fade - GRACE_MS).coerceAtLeast(400L) -> {
                        drop()
                        doneForId = id
                    }
                }
            }
        }

        when {
            // End of a song, no second player: fade out alone (the fallback).
            canFade && aux == null && remaining <= fade -> {
                val t = (remaining.toFloat() / fade).coerceIn(0f, 1f) // 1 -> 0
                setVolume(ease(t))
                setSpeed(if (dj) 1f + MAX_SPEED_UP * (1f - t) else 1f)
            }
            // Start of a song after a plain fade, or after a skip.
            introActive -> {
                val len = min(introMs, duration / 3)
                if (len < 300L || position >= len) {
                    introActive = false
                    setVolume(1f)
                    setSpeed(1f)
                } else {
                    val t = (position.toFloat() / len).coerceIn(0f, 1f) // 0 -> 1
                    setVolume(ease(t))
                    setSpeed(if (dj && introMs > QUICK_MS) 1f + MAX_SPEED_UP * (1f - t) else 1f)
                }
            }
            // Middle of a song, or waiting for the fade to start.
            else -> {
                setVolume(1f)
                setSpeed(1f)
            }
        }
    }

    // --- Second player ----------------------------------------------------------------

    private fun arm(id: String, nextIndex: Int) {
        val item = player.getMediaItemAt(nextIndex)
        val x = runCatching { createAux() }.getOrNull()
        if (x == null) {
            doneForId = id
            return
        }
        auxFailed = false
        x.addListener(auxListener)
        x.volume = 0f
        x.setMediaItem(item)
        x.prepare()
        x.playWhenReady = false
        aux = x
        auxId = item.mediaId
        currentId = id
        lastAuxSpeed = 1f
        phase = Phase.ARMED
    }

    private fun startFade(x: ExoPlayer, id: String, fade: Long) {
        doneForId = id
        fadeMs = fade
        x.volume = 0f
        x.playWhenReady = player.playWhenReady
        introActive = false
        phase = Phase.FADE
    }

    /** The old song fades out on the main player while the next one fades in on the second. */
    private fun runFade() {
        val x = aux
        if (x == null) {
            phase = Phase.IDLE
            return
        }
        followPause(x)
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return
        val remaining = duration - player.currentPosition

        // The slider was dragged back, or the song changed: stop the crossfade.
        if (remaining > fadeMs + 1500L || player.currentMediaItem?.mediaId != currentId) {
            drop()
            setVolume(1f)
            setSpeed(1f)
            return
        }

        val span = (fadeMs - HANDOFF_MS).coerceAtLeast(300L)
        val p = ((fadeMs - remaining).toFloat() / span).coerceIn(0f, 1f) // 0 -> 1
        setVolume(cosOf(p))
        setAuxVolume(x, sinOf(p))
        if (Prefs.djMode) {
            setSpeed(1f + MAX_SPEED_UP * p)
            setAuxSpeed(x, 1f + MAX_SPEED_UP * (1f - p))
        }
        if (remaining <= HANDOFF_MS) startHandoff(x)
    }

    /** The main player jumps into the next song, at the place the second player has reached. */
    private fun startHandoff(x: ExoPlayer) {
        val idx = player.nextMediaItemIndex
        if (idx == C.INDEX_UNSET || player.getMediaItemAt(idx).mediaId != auxId) {
            // The queue changed: let the old song end on its own.
            drop()
            return
        }
        setAuxVolume(x, 1f)
        setAuxSpeed(x, 1f)
        setSpeed(1f)
        setVolume(0f)
        phase = Phase.HANDOFF
        handoffAt = SystemClock.uptimeMillis()
        ourSeekUntil = handoffAt + 3000L
        corrected = false
        player.seekTo(idx, x.currentPosition + SEEK_AHEAD_MS)
    }

    private fun runHandoff() {
        val x = aux
        if (x == null) {
            phase = Phase.IDLE
            return
        }
        followPause(x)
        val now = SystemClock.uptimeMillis()
        if (now - handoffAt > HANDOFF_TIMEOUT_MS) {
            // The main player could not follow in time: give up quietly.
            drop()
            setVolume(1f)
            return
        }
        if (player.currentMediaItem?.mediaId == auxId && player.isPlaying) {
            if (!corrected) {
                corrected = true
                // The two copies of the song should be at the same place; nudge once if not.
                val gap = x.currentPosition - player.currentPosition
                if (abs(gap) > ALIGN_MS && abs(gap) < 3000L) {
                    player.seekTo(player.currentPosition + gap + SEEK_AHEAD_MS)
                    return
                }
            }
            phase = Phase.BLEND
            blendAt = now
        }
    }

    /** A short blend from the second player to the main player, then the second one goes. */
    private fun runBlend() {
        val x = aux
        if (x == null) {
            phase = Phase.IDLE
            return
        }
        followPause(x)
        val q = ((SystemClock.uptimeMillis() - blendAt).toFloat() / BLEND_MS).coerceIn(0f, 1f)
        setVolume(sinOf(q))
        setAuxVolume(x, cosOf(q))
        if (q >= 1f) {
            drop()
            introActive = false
            setVolume(1f)
        }
    }

    /** Keep the second player paused or playing together with the main one. */
    private fun followPause(x: ExoPlayer) {
        val want = player.playWhenReady
        if (x.playWhenReady != want && x.playbackState != Player.STATE_ENDED) {
            x.playWhenReady = want
        }
    }

    /** Throw the second player away and go back to idle. */
    private fun drop() {
        val x = aux
        aux = null
        auxId = null
        phase = Phase.IDLE
        lastAuxSpeed = 1f
        if (x != null) {
            runCatching {
                x.removeListener(auxListener)
                x.release()
            }
        }
    }

    // --- Volume and speed -------------------------------------------------------------

    /** Smooth start and finish, so a fade is not heard as a straight line. */
    private fun ease(t: Float): Float = t * t * (3f - 2f * t)

    /** Equal-power curves: cos goes 1 -> 0 and sin goes 0 -> 1, so the sum stays even. */
    private fun cosOf(t: Float): Float = cos(PI / 2.0 * t).toFloat()

    private fun sinOf(t: Float): Float = sin(PI / 2.0 * t).toFloat()

    private fun setVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        if (abs(v - lastVolume) > 0.005f || (v == 1f && lastVolume != 1f)) {
            player.volume = v
            lastVolume = v
        }
    }

    private fun setAuxVolume(x: ExoPlayer, value: Float) {
        x.volume = value.coerceIn(0f, 1f)
    }

    private fun setSpeed(value: Float) {
        val s = (value * 100f).roundToInt() / 100f
        if (s != lastSpeed) {
            player.setPlaybackParameters(PlaybackParameters(s))
            lastSpeed = s
        }
    }

    private fun setAuxSpeed(x: ExoPlayer, value: Float) {
        val s = (value * 100f).roundToInt() / 100f
        if (s != lastAuxSpeed) {
            x.setPlaybackParameters(PlaybackParameters(s))
            lastAuxSpeed = s
        }
    }

    private companion object {
        const val FAST_MS = 50L
        const val NORMAL_MS = 100L
        const val SLOW_MS = 1000L

        /** A short fade-in after a skip or a tapped song. */
        const val QUICK_MS = 1500L
        const val DJ_MIN_MS = 6000L

        /** The second player starts loading the next song this long before the fade. */
        const val ARM_AHEAD_MS = 10_000L

        /** How long past the start of the fade we still wait for the second player. */
        const val GRACE_MS = 1500L

        /** The main player jumps into the next song this long before the old one ends. */
        const val HANDOFF_MS = 600L

        /** Length of the short blend between the two players at the end. */
        const val BLEND_MS = 400L

        /** Guess of how far the song has moved while the main player is jumping. */
        const val SEEK_AHEAD_MS = 150L
        const val ALIGN_MS = 120L
        const val HANDOFF_TIMEOUT_MS = 6000L

        /** 6 percent faster at the very end of a song. */
        const val MAX_SPEED_UP = 0.06f
    }
}
