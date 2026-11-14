package app.hush.player

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.hush.net.YtMusic

/**
 * Foreground media service: owns the player, shows the notification and
 * lock-screen controls. Queue items carry the YouTube watch URL; the real
 * stream link is resolved lazily right before each track loads, so a long
 * queue never holds expired links.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var transitions: TransitionEngine? = null

    override fun onCreate() {
        super.onCreate()

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0")
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)

        val resolving = ResolvingDataSource.Factory(http) { spec: DataSpec ->
            val host = spec.uri.host.orEmpty()
            if (host.endsWith("youtube.com") || host.endsWith("youtu.be")) {
                spec.withUri(Uri.parse(YtMusic.resolveStream(spec.uri.toString())))
            } else {
                spec
            }
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        session = MediaSession.Builder(this, player).build()

        // Smooth song changes and DJ mode (both are switched in Settings).
        transitions = TransitionEngine(player) { buildSecondPlayer(resolving) }.also { it.start() }
    }

    /**
     * The helper player for the crossfade. It plays from the same sources as the main player,
     * never asks for audio focus (the main player owns it), and keeps only a small buffer,
     * because it only ever plays the first seconds of the next song.
     */
    private fun buildSecondPlayer(source: ResolvingDataSource.Factory): ExoPlayer =
        ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(source))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(8_000, 20_000, 1_500, 3_000)
                    .build()
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        transitions?.release()
        transitions = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
