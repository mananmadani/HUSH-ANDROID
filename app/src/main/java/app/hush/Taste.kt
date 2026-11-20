package app.hush

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.hush.data.SongEntity
import org.json.JSONObject

/**
 * What Hush has learned about your taste, kept only on this phone (never uploaded, but included in your
 * password-locked backup).
 *
 * Two things live here:
 *  - songs and artists you chose "Don't play this" for, and
 *  - how often you skip or finish songs of each artist. An artist you keep skipping early is left
 *    out of suggestions (the endless queue, Daily Mix and the "Because you liked" rows). Your own
 *    playlists and your searches are never changed by this.
 */
object Taste {

    private lateinit var sp: SharedPreferences

    private val blockedSongs = LinkedHashSet<String>()
    private val blockedArtists = LinkedHashSet<String>()

    /** Per artist (lower case): [skipped early, played through]. */
    private val counts = HashMap<String, IntArray>()

    /** How many songs and artists are hidden, for Settings. */
    var blockedCount by mutableStateOf(0)
        private set

    /** How many artists Hush has learned something about, for Settings. */
    var learnedCount by mutableStateOf(0)
        private set

    @Synchronized
    fun init(context: Context) {
        sp = context.getSharedPreferences("hush_taste", Context.MODE_PRIVATE)
        blockedSongs.clear()
        blockedArtists.clear()
        counts.clear()
        (sp.getString("blocked_songs", "") ?: "").split('\n').filter { it.isNotBlank() }.forEach { blockedSongs.add(it) }
        (sp.getString("blocked_artists", "") ?: "").split('\n').filter { it.isNotBlank() }.forEach { blockedArtists.add(it) }
        runCatching {
            val root = JSONObject(sp.getString("counts", "{}") ?: "{}")
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val parts = root.optString(k).split(':')
                if (parts.size == 2) {
                    counts[k] = intArrayOf(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0)
                }
            }
        }
        publish()
    }

    private fun key(artist: String) = artist.trim().lowercase()

    private fun publish() {
        blockedCount = blockedSongs.size + blockedArtists.size
        learnedCount = counts.size
    }

    // --- Don't play this ----------------------------------------------------------------

    @Synchronized
    fun isBlocked(song: SongEntity): Boolean =
        song.url in blockedSongs || (song.artist.isNotBlank() && key(song.artist) in blockedArtists)

    @Synchronized
    fun blockSong(url: String) {
        if (url.isBlank()) return
        blockedSongs.add(url)
        sp.edit().putString("blocked_songs", blockedSongs.joinToString("\n")).apply()
        publish()
    }

    @Synchronized
    fun blockArtist(name: String) {
        if (name.isBlank()) return
        blockedArtists.add(key(name))
        sp.edit().putString("blocked_artists", blockedArtists.joinToString("\n")).apply()
        publish()
    }

    @Synchronized
    fun resetBlocked() {
        blockedSongs.clear()
        blockedArtists.clear()
        sp.edit().remove("blocked_songs").remove("blocked_artists").apply()
        publish()
    }

    // --- Learning from skips ------------------------------------------------------------

    /** A song by [artist] was skipped in its first seconds. */
    @Synchronized
    fun recordSkip(artist: String) = bump(artist, 0)

    /** A song by [artist] was played through. */
    @Synchronized
    fun recordFinish(artist: String) = bump(artist, 1)

    private fun bump(artist: String, slot: Int) {
        if (artist.isBlank()) return
        val c = counts.getOrPut(key(artist)) { IntArray(2) }
        c[slot] += 1
        // Old habits fade: once there is a lot of history, halve it so new taste wins.
        if (c[0] + c[1] > 30) {
            c[0] /= 2
            c[1] /= 2
        }
        if (counts.size > 400) {
            // Keep the file small: drop the artists with the least history.
            counts.entries.sortedBy { it.value[0] + it.value[1] }.take(100).forEach { counts.remove(it.key) }
        }
        val root = JSONObject()
        counts.forEach { (k, v) -> root.put(k, "${v[0]}:${v[1]}") }
        sp.edit().putString("counts", root.toString()).apply()
        publish()
    }

    /** True for an artist you have skipped early again and again, and rarely finished. */
    @Synchronized
    fun isSkippedOften(artist: String): Boolean {
        if (artist.isBlank()) return false
        val c = counts[key(artist)] ?: return false
        return c[0] >= 3 && c[0] >= c[1] + 2
    }

    @Synchronized
    fun resetLearning() {
        counts.clear()
        sp.edit().remove("counts").apply()
        publish()
    }

    // --- Filters ------------------------------------------------------------------------

    /** Take out songs and artists you said "Don't play this" for. */
    fun filterBlocked(songs: List<SongEntity>): List<SongEntity> = songs.filterNot { isBlocked(it) }

    /** For suggestions only: also leave out artists you keep skipping. */
    fun filterSuggestions(songs: List<SongEntity>): List<SongEntity> =
        songs.filterNot { isBlocked(it) || isSkippedOften(it.artist) }
}
