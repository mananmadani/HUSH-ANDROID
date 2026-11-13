package app.hush

import android.app.Application
import app.hush.data.HushDb
import app.hush.data.Repo
import app.hush.net.HushDownloader
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

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Stats.init(this)

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
                }
            },
            onTick = { Stats.tick(it) }
        )

        // Keep the home-screen widgets in step with what is playing.
        io.launch {
            player.state
                .map { Triple(it.mediaId, it.playing, it.title) }
                .distinctUntilChanged()
                .collect { WidgetUpdater.update(this@HushApp) }
        }
    }
}
