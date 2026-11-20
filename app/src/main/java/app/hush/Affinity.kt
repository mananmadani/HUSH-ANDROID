package app.hush

import android.content.Context
import android.content.SharedPreferences
import app.hush.data.SongEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.pow

/**
 * What you really listen to, kept only on this phone (never uploaded, but included in your password-locked backup).
 *
 * Every song and artist gets a taste score. Playing a song all the way through, or replaying it,
 * raises the score. Skipping it in the first seconds lowers it. Old listening fades with time
 * (the score halves about every 45 days), so Hush follows your taste as it changes. The scores
 * and the time of day you play each song feed Home, the Daily Mix and the endless queue.
 */
object Affinity {

    private lateinit var sp: SharedPreferences

    private class SongScore(
        val url: String,
        var title: String,
        var artist: String,
        var thumb: String?,
        var score: Double,
        var at: Long,
        val parts: IntArray = IntArray(4),
        /** When this song last started playing (0 if unknown). */
        var last: Long = 0L
    )

    private class ArtistScore(var score: Double, var at: Long)

    private val songs = HashMap<String, SongScore>()
    private val artists = HashMap<String, ArtistScore>()

    private const val HALF_LIFE_DAYS = 45.0
    private const val MAX_SONGS = 300
    private const val MAX_ARTISTS = 200

    // Time of day: 0 morning, 1 afternoon, 2 evening, 3 late night.
    fun bucketNow(): Int = bucketOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

    private fun bucketOf(hour: Int): Int = when (hour) {
        in 5..11 -> 0
        in 12..16 -> 1
        in 17..21 -> 2
        else -> 3
    }

    fun bucketTitle(bucket: Int): String = when (bucket) {
        0 -> "Your morning picks"
        1 -> "Your afternoon picks"
        2 -> "Your evening picks"
        else -> "Your late-night picks"
    }

    @Synchronized
    fun init(context: Context) {
        sp = context.getSharedPreferences("hush_affinity", Context.MODE_PRIVATE)
        songs.clear()
        artists.clear()
        runCatching {
            val arr = JSONArray(sp.getString("songs", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val s = SongScore(
                    o.getString("u"), o.optString("t"), o.optString("a"),
                    o.optString("i").ifBlank { null }, o.optDouble("s", 0.0), o.optLong("w", 0L)
                )
                val p = o.optJSONArray("b")
                if (p != null) for (k in 0 until minOf(4, p.length())) s.parts[k] = p.optInt(k, 0)
                s.last = o.optLong("p", 0L)
                songs[s.url] = s
            }
        }
        runCatching {
            val root = JSONObject(sp.getString("artists", "{}") ?: "{}")
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val o = root.optJSONObject(k) ?: continue
                artists[k] = ArtistScore(o.optDouble("s", 0.0), o.optLong("w", 0L))
            }
        }
    }

    private fun key(artist: String) = artist.trim().lowercase()

    private fun decayed(score: Double, at: Long, now: Long): Double {
        val days = (now - at).coerceAtLeast(0L) / 86_400_000.0
        return score * 0.5.pow(days / HALF_LIFE_DAYS)
    }

    // --- What happened to a song ---------------------------------------------------------

    /** A song started playing: remember the time of day (this does not change the score). */
    @Synchronized
    fun recordStart(song: SongEntity) {
        if (song.url.isBlank() || song.title.isBlank()) return
        val s = touch(song)
        s.parts[bucketNow()] += 1
        s.last = System.currentTimeMillis()
        save()
    }

    @Synchronized fun finished(song: SongEntity) = change(song, 3.0, 1.0)
    @Synchronized fun replayed(song: SongEntity) = change(song, 2.0, 1.0)
    @Synchronized fun partlyPlayed(song: SongEntity) = change(song, 1.0, 0.0)
    @Synchronized fun skipped(song: SongEntity) = change(song, -3.0, -1.0)

    private fun touch(song: SongEntity): SongScore {
        val now = System.currentTimeMillis()
        val s = songs.getOrPut(song.url) { SongScore(song.url, song.title, song.artist, song.thumb, 0.0, now) }
        if (s.title.isBlank()) s.title = song.title
        if (s.artist.isBlank()) s.artist = song.artist
        if (s.thumb == null) s.thumb = song.thumb
        return s
    }

    private fun change(song: SongEntity, songDelta: Double, artistDelta: Double) {
        if (song.url.isBlank() || song.title.isBlank()) return
        val now = System.currentTimeMillis()
        val s = touch(song)
        s.score = (decayed(s.score, s.at, now) + songDelta).coerceIn(-6.0, 40.0)
        s.at = now
        if (song.artist.isNotBlank() && artistDelta != 0.0) {
            val a = artists.getOrPut(key(song.artist)) { ArtistScore(0.0, now) }
            a.score = (decayed(a.score, a.at, now) + artistDelta).coerceIn(-6.0, 80.0)
            a.at = now
        }
        save()
    }

    // --- What Hush has learned ------------------------------------------------------------

    /** The songs you seem to love most right now, best first. */
    @Synchronized
    fun topSongs(n: Int): List<SongEntity> {
        val now = System.currentTimeMillis()
        return songs.values
            .map { it to decayed(it.score, it.at, now) }
            .filter { it.second >= 2.0 }
            .sortedByDescending { it.second }
            .take(n)
            .map { it.first.toSong() }
    }

    /** Songs you often play at this time of day, best first. */
    @Synchronized
    fun forTimeOfDay(bucket: Int, n: Int): List<SongEntity> {
        val now = System.currentTimeMillis()
        return songs.values
            .filter { it.parts[bucket] >= 1 && decayed(it.score, it.at, now) > 0.0 }
            .sortedByDescending { it.parts[bucket] * 10 + decayed(it.score, it.at, now) }
            .take(n)
            .map { it.toSong() }
    }

    /**
     * Songs you clearly loved (listened through at least once, never skipped away) but have not played for
     * [minDays] days or more, in a different order each time.
     */
    @Synchronized
    fun rediscover(n: Int, minDays: Int = 14): List<SongEntity> {
        val now = System.currentTimeMillis()
        val quiet = minDays * 86_400_000L
        return songs.values
            .filter { it.score >= 3.0 }
            .filter { now - maxOf(it.last, it.at) >= quiet }
            .sortedByDescending { it.score }
            .take(n * 2)
            .shuffled()
            .take(n)
            .map { it.toSong() }
    }

    /** How much you like an artist: 0 or less means no signal or a dislike. */
    @Synchronized
    fun artistScore(artist: String): Double {
        if (artist.isBlank()) return 0.0
        val a = artists[key(artist)] ?: return 0.0
        return decayed(a.score, a.at, System.currentTimeMillis())
    }

    @Synchronized
    fun hasTaste(): Boolean = topSongs(3).size >= 3

    @Synchronized
    fun reset() {
        songs.clear()
        artists.clear()
        sp.edit().remove("songs").remove("artists").apply()
    }

    private fun SongScore.toSong() = SongEntity(url = url, title = title, artist = artist, thumb = thumb, durationSec = 0)

    private fun save() {
        if (songs.size > MAX_SONGS) {
            val now = System.currentTimeMillis()
            songs.values.sortedBy { decayed(it.score, it.at, now) }
                .take(songs.size - MAX_SONGS + 40).forEach { songs.remove(it.url) }
        }
        if (artists.size > MAX_ARTISTS) {
            val now = System.currentTimeMillis()
            artists.entries.sortedBy { decayed(it.value.score, it.value.at, now) }
                .take(artists.size - MAX_ARTISTS + 20).forEach { artists.remove(it.key) }
        }
        val arr = JSONArray()
        songs.values.forEach { s ->
            arr.put(
                JSONObject().put("u", s.url).put("t", s.title).put("a", s.artist).put("i", s.thumb ?: "")
                    .put("s", s.score).put("w", s.at).put("p", s.last).put("b", JSONArray(s.parts.toList()))
            )
        }
        val root = JSONObject()
        artists.forEach { (k, v) -> root.put(k, JSONObject().put("s", v.score).put("w", v.at)) }
        sp.edit().putString("songs", arr.toString()).putString("artists", root.toString()).apply()
    }
}
