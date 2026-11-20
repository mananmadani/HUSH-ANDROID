package app.hush.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Reads the song list of a PUBLIC Spotify playlist or album from its public embed page.
 * No Spotify account or login is used. Only the link you paste is sent to Spotify.
 */
object Spotify {

    data class Track(val title: String, val artist: String)
    data class SpotifyList(val name: String, val tracks: List<Track>)

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val LINK = Regex("open\\.spotify\\.com/(?:intl-[a-zA-Z-]+/)?(playlist|album)/([A-Za-z0-9]+)")
    private val DATA = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL)

    suspend fun read(link: String): SpotifyList = withContext(Dispatchers.IO) {
        val match = LINK.find(link)
            ?: throw IOException("That doesn't look like a Spotify playlist or album link")
        val (type, id) = match.destructured

        val request = Request.Builder()
            .url("https://open.spotify.com/embed/$type/$id")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .build()
        val html = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Spotify didn't open that link. Is the playlist public?")
            response.body?.string().orEmpty()
        }

        val json = DATA.find(html)?.groupValues?.get(1)
            ?: throw IOException("Couldn't read that Spotify page. Is the playlist public?")
        val entity = findEntity(JSONObject(json))
            ?: throw IOException("Couldn't find any songs in that Spotify link")

        val list = entity.getJSONArray("trackList")
        val tracks = ArrayList<Track>()
        for (i in 0 until list.length()) {
            val t = list.optJSONObject(i) ?: continue
            val title = t.optString("title").trim()
            if (title.isEmpty()) continue
            tracks.add(Track(title, t.optString("subtitle").replace(' ', ' ').trim()))
        }
        val name = entity.optString("name").ifBlank { entity.optString("title") }.ifBlank { "Spotify playlist" }
        SpotifyList(name, tracks)
    }

    /** Finds the object that holds the "trackList" array, wherever it sits in the page data. */
    private fun findEntity(node: Any?): JSONObject? {
        when (node) {
            is JSONObject -> {
                if (node.optJSONArray("trackList") != null) return node
                val keys = node.keys()
                while (keys.hasNext()) {
                    findEntity(node.opt(keys.next()))?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    findEntity(node.opt(i))?.let { return it }
                }
            }
        }
        return null
    }
}
