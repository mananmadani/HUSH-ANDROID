package app.hush.data

import org.json.JSONArray
import org.json.JSONObject

class Repo(private val dao: HushDao) {
    fun liked() = dao.liked()
    fun isLiked(url: String) = dao.isLiked(url)
    fun playlists() = dao.playlists()
    fun playlistName(id: Long) = dao.playlistName(id)
    fun playlistSongs(id: Long) = dao.playlistSongs(id)
    fun playlistThumbs() = dao.playlistThumbs()

    fun recent() = dao.recent()

    suspend fun recordPlay(song: SongEntity) {
        dao.upsertSong(song)
        dao.addHistory(HistoryEntity(song.url, System.currentTimeMillis()))
        dao.trimHistory()
    }

    suspend fun clearHistory() = dao.clearHistory()

    suspend fun setLiked(song: SongEntity, like: Boolean) {
        if (like) {
            dao.upsertSong(song)
            dao.like(LikedEntity(song.url, System.currentTimeMillis()))
        } else {
            dao.unlike(song.url)
        }
    }

    suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(PlaylistEntity(name = name.trim().ifEmpty { "New playlist" }))

    suspend fun deletePlaylist(id: Long) {
        dao.clearPlaylist(id)
        dao.deletePlaylist(id)
    }

    suspend fun addToPlaylist(playlistId: Long, song: SongEntity) {
        dao.upsertSong(song)
        dao.addToPlaylist(PlaylistItemEntity(playlistId, song.url, System.currentTimeMillis()))
    }

    suspend fun removeFromPlaylist(playlistId: Long, url: String) =
        dao.removeFromPlaylist(playlistId, url)

    // --- Backup and restore --------------------------------------------------------------
    // A plain JSON file the person keeps. Liked songs and playlists only (not play history).

    suspend fun exportBackup(): String {
        val songs = dao.allSongs().associateBy { it.url }
        val liked = dao.allLiked()
        val playlists = dao.allPlaylists()
        val items = dao.allPlaylistItems().groupBy { it.playlistId }

        val needed = LinkedHashSet<String>()
        liked.forEach { needed.add(it.url) }
        items.values.flatten().forEach { needed.add(it.url) }

        val songArray = JSONArray()
        needed.mapNotNull { songs[it] }.forEach { s ->
            songArray.put(
                JSONObject()
                    .put("url", s.url)
                    .put("title", s.title)
                    .put("artist", s.artist)
                    .put("thumb", s.thumb ?: JSONObject.NULL)
                    .put("durationSec", s.durationSec)
            )
        }

        val likedArray = JSONArray()
        liked.forEach { likedArray.put(JSONObject().put("url", it.url).put("at", it.at)) }

        val playlistArray = JSONArray()
        playlists.forEach { p ->
            val itemArray = JSONArray()
            (items[p.id] ?: emptyList()).forEach {
                itemArray.put(JSONObject().put("url", it.url).put("at", it.at))
            }
            playlistArray.put(JSONObject().put("name", p.name).put("items", itemArray))
        }

        return JSONObject()
            .put("app", "Hush")
            .put("version", 1)
            .put("songs", songArray)
            .put("liked", likedArray)
            .put("playlists", playlistArray)
            .toString(2)
    }

    /** Merges a backup into the library. Returns a short summary for the person. */
    suspend fun importBackup(json: String): String {
        val root = JSONObject(json)
        require(root.optString("app") == "Hush") { "Not a Hush backup" }

        val songs = root.optJSONArray("songs") ?: JSONArray()
        for (i in 0 until songs.length()) {
            val o = songs.getJSONObject(i)
            dao.upsertSong(
                SongEntity(
                    url = o.getString("url"),
                    title = o.getString("title"),
                    artist = o.optString("artist", ""),
                    thumb = if (o.isNull("thumb")) null else o.getString("thumb"),
                    durationSec = o.optLong("durationSec", 0)
                )
            )
        }

        val liked = root.optJSONArray("liked") ?: JSONArray()
        for (i in 0 until liked.length()) {
            val o = liked.getJSONObject(i)
            dao.like(LikedEntity(o.getString("url"), o.optLong("at", System.currentTimeMillis())))
        }

        val playlists = root.optJSONArray("playlists") ?: JSONArray()
        for (i in 0 until playlists.length()) {
            val p = playlists.getJSONObject(i)
            val name = p.getString("name")
            val id = dao.playlistIdByName(name) ?: dao.insertPlaylist(PlaylistEntity(name = name))
            val items = p.optJSONArray("items") ?: JSONArray()
            for (j in 0 until items.length()) {
                val o = items.getJSONObject(j)
                dao.addToPlaylist(
                    PlaylistItemEntity(id, o.getString("url"), o.optLong("at", System.currentTimeMillis()))
                )
            }
        }

        return "Restored ${songs.length()} songs and ${playlists.length()} playlists"
    }
}
