package app.hush

import android.app.Application
import app.hush.data.HushDb
import app.hush.data.Repo
import app.hush.dj.DjEngine
import app.hush.dj.DjFeed
import app.hush.net.HushDownloader
import app.hush.net.UpdateWorker
import app.hush.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.hush.widget.WidgetUpdater
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class HushApp : Application() {

    lateinit var repo: Repo
        private set
    lateinit var player: PlayerController
        private set
    lateinit var dj: DjEngine
        private set

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Stats.init(this)
        Taste.init(this)
        Affinity.init(this)
        UpdateWorker.schedule(this, Prefs.autoUpdate)

        // Ask YouTube Music in the phone's own language and country.
        NewPipe.init(
            HushDownloader(),
            Localization(Region.languageCode, Region.countryCode),
            ContentCountry(Region.countryCode)
        )

        repo = Repo(HushDb.create(this).dao())

        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        player = PlayerController(
            this,
            onTrackStarted = { song ->
                io.launch {
                    repo.recordPlay(song)
                    Stats.recordPlay(song)
                    Affinity.recordStart(song)
                }
            },
            onTick = { Stats.tick(it) }
        )

        dj = DjEngine(
            this,
            DjFeed(),
            onTrackStarted = { song ->
                io.launch {
                    repo.recordPlay(song)
                    Stats.recordPlay(song)
                    Affinity.recordStart(song)
                }
            },
            onTrackEnded = { song, how ->
                io.launch {
                    when (how) {
                        1 -> Affinity.finished(song)
                        2 -> Affinity.skipped(song)
                        else -> Affinity.partlyPlayed(song)
                    }
                }
            },
            onTick = { Stats.tick(it) },
            pauseMain = { player.pauseForDj() }
        )
        // Starting the normal player ends the DJ set, so two things never play at once.
        player.onUserPlay = { dj.stop() }

        // Keep the home-screen widgets in step with what is playing.
        io.launch {
            player.state
                .map { Triple(it.mediaId, it.playing, it.title) }
                .distinctUntilChanged()
                .collect { WidgetUpdater.update(this@HushApp) }
        }
    }
}
