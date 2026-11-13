package app.hush

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.hush.data.SongEntity
import app.hush.net.ArtistItem
import app.hush.net.CollectionItem
import app.hush.net.Spotify
import app.hush.net.YtMusic
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchKind(val label: String) {
    Songs("Songs"), Artists("Artists"), Albums("Albums"), Playlists("Playlists")
}

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

    /** Up to four cover images per playlist, for the collage in Library. */
    val playlistThumbs = repo.playlistThumbs()
        .map { rows ->
            rows.groupBy { it.playlistId }
                .mapValues { (_, list) -> list.mapNotNull { it.thumb }.distinct().take(4) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Home
    var shelves by mutableStateOf<List<Shelf>>(emptyList()); private set
    var homeLoading by mutableStateOf(false); private set
    var homeError by mutableStateOf<String?>(null); private set
    private var homeJob: Job? = null

    // Home mood chips
    var mood by mutableStateOf<Mood?>(null); private set
    var moodSongs by mutableStateOf<List<SongEntity>>(emptyList()); private set
    var moodLoading by mutableStateOf(false); private set
    private var moodJob: Job? = null

    // Search
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<SongEntity>>(emptyList()); private set
    var searching by mutableStateOf(false); private set
    var searchError by mutableStateOf<String?>(null); private set
    var searched by mutableStateOf(false); private set
    var searchKind by mutableStateOf(SearchKind.Songs); private set
    var artistResults by mutableStateOf<List<ArtistItem>>(emptyList()); private set
    var collectionResults by mutableStateOf<List<CollectionItem>>(emptyList()); private set
    var suggestions by mutableStateOf<List<String>>(emptyList()); private set
    var recentSearches by mutableStateOf(Prefs.loadRecentSearches()); private set
    private var suggestJob: Job? = null

    init {
        loadHome()
    }

    // --- Home ----------------------------------------------------------------------------

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

    /** Pick a mood chip on Home (null = All). */
    fun selectMood(selected: Mood?) {
        moodJob?.cancel()
        mood = selected
        moodSongs = emptyList()
        if (selected == null) {
            moodLoading = false
            return
        }
        moodJob = viewModelScope.launch {
            moodLoading = true
            moodSongs = runCatching { YtMusic.search(selected.query) }.getOrDefault(emptyList())
            ensureActive()
            moodLoading = false
        }
    }

    // --- Search --------------------------------------------------------------------------

    /** Called on every keystroke in the search field. */
    fun onQueryChange(text: String) {
        query = text
        suggestJob?.cancel()
        if (text.isBlank() || text.length < 2 || !Prefs.suggestionsEnabled) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250) // wait for a pause in typing
            suggestions = runCatching { YtMusic.suggestions(text.trim()) }.getOrDefault(emptyList())
        }
    }

    fun search(remember: Boolean = true) {
        val q = query.trim()
        if (q.isEmpty()) return
        suggestJob?.cancel()
        suggestions = emptyList()
        if (remember) rememberSearch(q)
        viewModelScope.launch {
            searching = true
            searchError = null
            try {
                when (searchKind) {
                    SearchKind.Songs -> results = YtMusic.search(q)
                    SearchKind.Artists -> artistResults = YtMusic.searchArtists(q)
                    SearchKind.Albums -> collectionResults = YtMusic.searchCollections(q, albums = true)
                    SearchKind.Playlists -> collectionResults = YtMusic.searchCollections(q, albums = false)
                }
                searched = true
            } catch (e: Exception) {
                searchError = "Search failed. Check your connection and try again."
            } finally {
                searching = false
            }
        }
    }

    /** Switch between Songs / Artists / Albums / Playlists; re-runs the search if there is one. */
    fun selectKind(kind: SearchKind) {
        if (kind == searchKind) return
        searchKind = kind
        searched = false
        searchError = null
        if (query.isNotBlank()) search(remember = false)
    }

    /** Search for [text] (a tapped suggestion or recent search). */
    fun searchFor(text: String) {
        query = text
        search()
    }

    /** Search for a mood tile; not added to recent searches. */
    fun searchMood(m: Mood) {
        query = m.query
        searchKind = SearchKind.Songs
        search(remember = false)
    }

    fun clearQuery() {
        suggestJob?.cancel()
        query = ""
        suggestions = emptyList()
        searched = false
        results = emptyList()
        artistResults = emptyList()
        collectionResults = emptyList()
        searchError = null
    }

    private fun rememberSearch(q: String) {
        recentSearches = (listOf(q) + recentSearches.filterNot { it.equals(q, ignoreCase = true) })
            .take(8)
        Prefs.saveRecentSearches(recentSearches)
    }

    fun removeRecentSearch(text: String) {
        recentSearches = recentSearches.filterNot { it == text }
        Prefs.saveRecentSearches(recentSearches)
    }

    fun clearRecentSearches() {
        recentSearches = emptyList()
        Prefs.saveRecentSearches(recentSearches)
    }

    // --- Library -------------------------------------------------------------------------

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

    // --- Import and save -----------------------------------------------------------------

    /** Progress text while an import runs, null when idle. */
    var importStatus by mutableStateOf<String?>(null); private set

    /** Import a YouTube, YouTube Music or (public) Spotify playlist link as a local playlist. */
    fun importPlaylist(link: String, done: (String) -> Unit) {
        if (importStatus != null) return
        viewModelScope.launch {
            importStatus = "Reading the link..."
            val message = try {
                withContext(Dispatchers.IO) { runImport(link.trim()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message?.takeIf { it.isNotBlank() } ?: "Couldn't import that link"
            }
            importStatus = null
            done(message)
        }
    }

    private suspend fun runImport(link: String): String {
        val lower = link.lowercase()
        val name: String
        val songs: List<SongEntity>
        if ("spotify" in lower) {
            val sp = Spotify.read(link)
            name = sp.name
            val found = ArrayList<SongEntity>()
            var checked = 0
            for (chunk in sp.tracks.chunked(5)) {
                val part = coroutineScope {
                    chunk.map { t ->
                        async { runCatching { YtMusic.search("${t.title} ${t.artist}").firstOrNull() }.getOrNull() }
                    }.awaitAll()
                }
                found.addAll(part.filterNotNull())
                checked += chunk.size
                importStatus = "Matching songs $checked/${sp.tracks.size}"
            }
            songs = found
        } else if ("youtu" in lower) {
            importStatus = "Loading playlist..."
            val list = try {
                YtMusic.collection(link)
            } catch (e: Exception) {
                throw IOException("Couldn't read that playlist. Use a public playlist link.")
            }
            name = list.title
            songs = list.songs
        } else {
            throw IOException("Paste a YouTube, YouTube Music or Spotify playlist link")
        }
        if (songs.isEmpty()) throw IOException("No songs could be imported from that link")
        repo.createPlaylistWithSongs(name, songs)
        return "Imported \"$name\" (${songs.size} songs)"
    }

    /** Save an online album or playlist as a local playlist. */
    fun saveCollection(title: String, songs: List<SongEntity>, done: (String) -> Unit) {
        viewModelScope.launch {
            repo.createPlaylistWithSongs(title, songs)
            done("Saved \"$title\" to your playlists")
        }
    }

    // --- Backup and restore --------------------------------------------------------------

    fun exportBackup(uri: Uri, done: (String) -> Unit) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val json = repo.exportBackup()
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!
                        .use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    "Backup saved"
                }.getOrElse { "Couldn't save the backup" }
            }
            done(message)
        }
    }

    fun importBackup(uri: Uri, done: (String) -> Unit) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val json = getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .use { it.readBytes().toString(Charsets.UTF_8) }
                    repo.importBackup(json)
                }.getOrElse { "That file isn't a Hush backup" }
            }
            done(message)
        }
    }
}
