package app.hush.data

class Repo(private val dao: HushDao) {
    fun liked() = dao.liked()
    fun isLiked(url: String) = dao.isLiked(url)
    fun playlists() = dao.playlists()
    fun playlistName(id: Long) = dao.playlistName(id)
    fun playlistSongs(id: Long) = dao.playlistSongs(id)

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
}
