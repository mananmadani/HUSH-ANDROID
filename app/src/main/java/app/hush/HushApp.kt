package app.hush

import android.app.Application
import app.hush.data.HushDb
import app.hush.data.Repo
import app.hush.net.HushDownloader
import app.hush.player.PlayerController
import org.schabi.newpipe.extractor.NewPipe

class HushApp : Application() {

    lateinit var repo: Repo
        private set
    lateinit var player: PlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(HushDownloader())
        repo = Repo(HushDb.create(this).dao())
        player = PlayerController(this)
    }
}
