package app.hush.dj

import android.content.Context
import android.content.SharedPreferences
import app.hush.data.SongEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * DJ mode's own history and numbers, kept apart from normal listening. Nothing here touches your
 * normal history, stats or taste, and nothing leaves the phone.
 */
object DjStats {

    class Entry(
        val song: SongEntity,
        val plays: Int,
        val finished: Int,
        val skipped: Int,
        val lastAt: Long
    )

    class Summary(
        val sets: Int = 0,
        val listenMs: Long = 0L,
        val plays: Int = 0,
        /** Newest first. */
        val recent: List<Entry> = emptyList(),
        val topSongs: List<Entry> = emptyList(),
        val topArtists: List<Pair<String, Int>> = emptyList()
    )

    private const val MAX_SONGS = 500

    private var sp: SharedPreferences? = null
    private val songs = LinkedHashMap<String, Entry>()
    private var sets = 0
    private var listenMs = 0L
    private var pendingMs = 0L

    private val _summary = MutableStateFlow(Summary())
    val summary: StateFlow<Summary> = _summary.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        sp = context.getSharedPreferences("hush_dj_stats", Context.MODE_PRIVATE)
        songs.clear()
        sets = 0
        listenMs = 0L
        try {
            val root = JSONObject(sp?.getString("data", "{}") ?: "{}")
            sets = root.optInt("sets", 0)
            listenMs = root.optLong("ms", 0L)
            val all = root.optJSONObject("songs")
            if (all != null) {
                for (url in all.keys()) {
                    val o = all.optJSONObject(url) ?: continue
                    songs[url] = Entry(
                        SongEntity(
                            url = url,
                            title = o.optString("t"),
                            artist = o.optString("a"),
                            thumb = o.optString("i").ifBlank { null },
                            durationSec = o.optLong("d", 0L)
                        ),
                        o.optInt("p", 0), o.optInt("f", 0), o.optInt("s", 0), o.optLong("at", 0L)
                    )
                }
            }
        } catch (e: Exception) {
            songs.clear()
        }
        publish()
    }

    @Synchronized
    fun startSet() {
        sets++
        save()
    }

    @Synchronized
    fun recordStart(song: SongEntity) {
        if (song.title.isBlank()) return
        val old = songs[song.url]
        songs[song.url] = Entry(
            song, (old?.plays ?: 0) + 1, old?.finished ?: 0, old?.skipped ?: 0, System.currentTimeMillis()
        )
        trim()
        save()
    }

    /** [how]: 1 = played through, 0 = left part-way, 2 = skipped early. */
    @Synchronized
    fun ended(song: SongEntity, how: Int) {
        val old = songs[song.url] ?: return
        songs[song.url] = Entry(
            old.song, old.plays,
            old.finished + (if (how == 1) 1 else 0),
            old.skipped + (if (how == 2) 1 else 0),
            old.lastAt
        )
        save()
    }

    @Synchronized
    fun tick(ms: Long) {
        listenMs += ms
        pendingMs += ms
        if (pendingMs >= 30_000L) {
            pendingMs = 0L
            save()
        }
    }

    @Synchronized
    fun clear() {
        songs.clear()
        sets = 0
        listenMs = 0L
        pendingMs = 0L
        save()
    }

    /** Songs DJ mode played most, for DJ suggestions. */
    @Synchronized
    fun topSongs(n: Int): List<SongEntity> =
        songs.values
            .sortedByDescending { it.plays + it.finished * 2 - it.skipped * 3 }
            .filter { it.plays + it.finished * 2 - it.skipped * 3 > 0 }
            .take(n)
            .map { it.song }

    private fun trim() {
        while (songs.size > MAX_SONGS) {
            val oldest = songs.values.minByOrNull { it.lastAt } ?: break
            songs.remove(oldest.song.url)
        }
    }

    private fun save() {
        val all = JSONObject()
        songs.values.forEach {
            all.put(
                it.song.url,
                JSONObject()
                    .put("t", it.song.title)
                    .put("a", it.song.artist)
                    .put("i", it.song.thumb ?: "")
                    .put("d", it.song.durationSec)
                    .put("p", it.plays)
                    .put("f", it.finished)
                    .put("s", it.skipped)
                    .put("at", it.lastAt)
            )
        }
        val root = JSONObject().put("sets", sets).put("ms", listenMs).put("songs", all)
        sp?.edit()?.putString("data", root.toString())?.apply()
        publish()
    }

    private fun publish() {
        val list = songs.values.toList()
        val artists = HashMap<String, Int>()
        list.forEach { if (it.song.artist.isNotBlank()) artists[it.song.artist] = (artists[it.song.artist] ?: 0) + it.plays }
        _summary.value = Summary(
            sets = sets,
            listenMs = listenMs,
            plays = list.sumOf { it.plays },
            recent = list.sortedByDescending { it.lastAt }.take(100),
            topSongs = list.sortedByDescending { it.plays }.take(10),
            topArtists = artists.entries.sortedByDescending { it.value }.take(10).map { it.key to it.value }
        )
    }
}
