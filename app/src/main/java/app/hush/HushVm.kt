package app.hush

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.hush.data.SongEntity
import app.hush.net.YtMusic
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Shelf(val title: String, val songs: List<SongEntity>)

/** One Home row and how to fetch it. */
private class ShelfSource(val title: String, val load: suspend () -> List<SongEntity>)

class HushVm(app: Application) : AndroidViewModel(app) {

    private val hush = app as HushApp
    val repo = hush.repo
    val player = hush.player

    // Library and history (local only)
    val liked = repo.liked()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists = repo.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recent = repo.recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Home
    var shelves by mutableStateOf<List<Shelf>>(emptyList()); private set
    var homeLoading by mutableStateOf(false); private set
    var homeError by mutableStateOf<String?>(null); private set
    private var homeJob: Job? = null

    // Search
    var query by mutableStateOf("")
    var results by mutableStateOf<List<SongEntity>>(emptyList()); private set
    var searching by mutableStateOf(false); private set
    var searchError by mutableStateOf<String?>(null); private set
    var searched by mutableStateOf(false); private set

    init {
        loadHome()
    }

    /**
     * Builds Home: up to two "Because you liked ..." rows from your liked songs (on-device),
     * then rows for your region. Rows appear as soon as each one arrives.
     */
    fun loadHome() {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            homeLoading = true
            homeError = null
            shelves = emptyList()

            val likedSongs = runCatching { repo.liked().first() }.getOrDefault(emptyList())
            val likedUrls = likedSongs.map { it.url }.toSet()
            val picks = likedSongs.shuffled().take(2)

            val sources = buildList {
                picks.forEach { song ->
                    add(
                        ShelfSource("Because you liked ${song.title}") {
                            YtMusic.related(song.url).filter { it.url !in likedUrls }
                        }
                    )
                }
                Shelves.forRegion(Region.countryCode, Region.countryName).forEach { spec ->
                    add(ShelfSource(spec.title) { YtMusic.search(spec.query) })
                }
            }

            val slots = arrayOfNulls<Shelf>(sources.size)
            coroutineScope {
                sources.forEachIndexed { index, source ->
                    launch {
                        val songs = runCatching { source.load() }.getOrDefault(emptyList())
                        ensureActive() // a newer refresh may have replaced this one
                        if (songs.isNotEmpty()) {
                            slots[index] = Shelf(source.title, songs)
                            shelves = slots.filterNotNull()
                        }
                    }
                }
            }

            homeLoading = false
            if (shelves.isEmpty()) {
                homeError = "Couldn't load music. Check your connection and try again."
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

    fun clearHistory() {
        viewModelScope.launch { repo.clearHistory() }
    }
}
