package app.hush.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** A song. `url` is the watch URL and doubles as the unique id and the player's media id. */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val url: String,
    val title: String,
    val artist: String,
    val thumb: String?,
    val durationSec: Long
)

@Entity(tableName = "liked")
data class LikedEntity(@PrimaryKey val url: String, val at: Long)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "playlist_items", primaryKeys = ["playlistId", "url"])
data class PlaylistItemEntity(val playlistId: Long, val url: String, val at: Long)

data class PlaylistInfo(val id: Long, val name: String, val cnt: Int)

@Dao
interface HushDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSong(song: SongEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun like(item: LikedEntity)

    @Query("DELETE FROM liked WHERE url = :url")
    suspend fun unlike(url: String)

    @Query("SELECT EXISTS(SELECT 1 FROM liked WHERE url = :url)")
    fun isLiked(url: String): Flow<Boolean>

    @Query("SELECT s.* FROM songs s JOIN liked l ON s.url = l.url ORDER BY l.at DESC")
    fun liked(): Flow<List<SongEntity>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :id")
    suspend fun clearPlaylist(id: Long)

    @Query(
        "SELECT p.id AS id, p.name AS name, " +
            "(SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS cnt " +
            "FROM playlists p ORDER BY p.id DESC"
    )
    fun playlists(): Flow<List<PlaylistInfo>>

    @Query("SELECT name FROM playlists WHERE id = :id")
    fun playlistName(id: Long): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToPlaylist(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :pid AND url = :url")
    suspend fun removeFromPlaylist(pid: Long, url: String)

    @Query(
        "SELECT s.* FROM songs s JOIN playlist_items i ON s.url = i.url " +
            "WHERE i.playlistId = :pid ORDER BY i.at ASC"
    )
    fun playlistSongs(pid: Long): Flow<List<SongEntity>>
}

@Database(
    entities = [SongEntity::class, LikedEntity::class, PlaylistEntity::class, PlaylistItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HushDb : RoomDatabase() {
    abstract fun dao(): HushDao

    companion object {
        fun create(context: Context): HushDb =
            Room.databaseBuilder(context, HushDb::class.java, "hush.db").build()
    }
}
