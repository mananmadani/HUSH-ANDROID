package app.hush

import android.content.Context
import android.content.SharedPreferences
import app.hush.data.SongEntity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Listening stats, kept only on this phone (never uploaded, not part of backups).
 * Plays are counted per month, so "This month" and "All time" can both be shown.
 */
object Stats {

    data class SongStat(val song: SongEntity, val plays: Int)
    data class ArtistStat(val name: String, val plays: Int)
    data class Summary(
        val listenMs: Long,
        val totalPlays: Int,
        val songs: List<SongStat>,
        val artists: List<ArtistStat>
    )

    private lateinit var sp: SharedPreferences
    private var pendingMs = 0L

    fun init(context: Context) {
        sp = context.getSharedPreferences("hush_stats", Context.MODE_PRIVATE)
    }

    private fun monthKey(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    /** Count one play of [song]. */
    @Synchronized
    fun recordPlay(song: SongEntity) {
        if (song.title.isBlank()) return
        val key = "stats_" + monthKey()
        val root = runCatching { JSONObject(sp.getString(key, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val entry = root.optJSONObject(song.url) ?: JSONObject()
            .put("t", song.title)
            .put("a", song.artist)
            .put("i", song.thumb ?: "")
        entry.put("p", entry.optInt("p", 0) + 1)
        root.put(song.url, entry)
        sp.edit().putString(key, root.toString()).apply()
    }

    /** Called about twice a second while music plays. Saved in batches to keep writes rare. */
    @Synchronized
    fun tick(ms: Long) {
        pendingMs += ms
        if (pendingMs >= 30_000) flush()
    }

    @Synchronized
    private fun flush() {
        if (pendingMs <= 0) return
        val key = "listen_" + monthKey()
        sp.edit().putLong(key, sp.getLong(key, 0L) + pendingMs).apply()
        pendingMs = 0
    }

    @Synchronized
    fun summary(allTime: Boolean): Summary {
        val statKeys = if (allTime) sp.all.keys.filter { it.startsWith("stats_") }
        else listOf("stats_" + monthKey())
        val listenKeys = if (allTime) sp.all.keys.filter { it.startsWith("listen_") }
        else listOf("listen_" + monthKey())

        val songs = LinkedHashMap<String, SongStat>()
        for (key in statKeys) {
            val root = runCatching { JSONObject(sp.getString(key, "{}") ?: "{}") }.getOrDefault(JSONObject())
            val urls = root.keys()
            while (urls.hasNext()) {
                val url = urls.next()
                val e = root.optJSONObject(url) ?: continue
                val plays = e.optInt("p", 0)
                val old = songs[url]
                val song = old?.song ?: SongEntity(
                    url = url,
                    title = e.optString("t"),
                    artist = e.optString("a"),
                    thumb = e.optString("i").ifBlank { null },
                    durationSec = 0
                )
                songs[url] = SongStat(song, (old?.plays ?: 0) + plays)
            }
        }

        val artists = HashMap<String, Int>()
        songs.values.forEach { s ->
            val name = s.song.artist.ifBlank { "Unknown artist" }
            artists[name] = (artists[name] ?: 0) + s.plays
        }

        val listen = listenKeys.sumOf { sp.getLong(it, 0L) } + pendingMs
        return Summary(
            listenMs = listen,
            totalPlays = songs.values.sumOf { it.plays },
            songs = songs.values.sortedByDescending { it.plays },
            artists = artists.map { ArtistStat(it.key, it.value) }.sortedByDescending { it.plays }
        )
    }

    @Synchronized
    fun clear() {
        pendingMs = 0
        sp.edit().clear().apply()
    }
}
