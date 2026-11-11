package app.hush

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.hush.data.SongEntity
import app.hush.net.YtMusic
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Shelf(val title: String, val songs: List<SongEntity>)

class HushVm(app: Application) : AndroidViewModel(app) {

    private val hush = app as HushApp
    val repo = hush.repo
    val player = hush.player

    // Library (local only)
    val liked = repo.liked()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists = repo.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Home
    var shelves by mutableStateOf<List<Shelf>>(emptyList()); private set
    var homeLoading by mutableStateOf(false); private set
    var homeError by mutableStateOf<String?>(null); private set

    // Search
    var query by mutableStateOf("")
    var results by mutableStateOf<List<SongEntity>>(emptyList()); private set
    var searching by mutableStateOf(false); private set
    var searchError by mutableStateOf<String?>(null); private set
    var searched by mutableStateOf(false); private set

    init {
        loadHome()
    }

    fun loadHome() {
        viewModelScope.launch {
            homeLoading = true
            homeError = null
            try {
                val loaded = coroutineScope {
                    HOME_SHELVES.map { (title, q) ->
                        async {
                            Shelf(title, runCatching { YtMusic.search(q) }.getOrDefault(emptyList()))
                        }
                    }.awaitAll()
                }.filter { it.songs.isNotEmpty() }
                shelves = loaded
                if (loaded.isEmpty()) homeError = "Couldn't load music. Check your connection and try again."
            } catch (e: Exception) {
                homeError = "Couldn't load music. Check your connection and try again."
            } finally {
                homeLoading = false
            }
        }
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            searching = true
            searchError = null
            try {
                results = YtMusic.search(q)
                searched = true
            } catch (e: Exception) {
                searchError = "Search failed. Check your connection and try again."
            } finally {
                searching = false
            }
        }
    }

    fun setLiked(song: SongEntity, like: Boolean) {
        viewModelScope.launch { repo.setLiked(song, like) }
    }

    fun createPlaylist(name: String, thenAdd: SongEntity? = null) {
        viewModelScope.launch {
            val id = repo.createPlaylist(name)
            if (thenAdd != null) repo.addToPlaylist(id, thenAdd)
        }
    }

    fun addToPlaylist(playlistId: Long, song: SongEntity) {
        viewModelScope.launch { repo.addToPlaylist(playlistId, song) }
    }

    fun removeFromPlaylist(playlistId: Long, song: SongEntity) {
        viewModelScope.launch { repo.removeFromPlaylist(playlistId, song.url) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { repo.deletePlaylist(id) }
    }

    private companion object {
        val HOME_SHELVES = listOf(
            "Top hits" to "top hits",
            "New releases" to "new songs",
            "Chill" to "chill songs",
            "Workout" to "workout songs"
        )
    }
}
