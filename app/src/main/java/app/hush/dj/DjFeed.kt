package app.hush.dj

import app.hush.Affinity
import app.hush.Region
import app.hush.Taste
import app.hush.data.SongEntity
import app.hush.net.YtMusic
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The endless list of DJ-friendly songs on the DJ tab. Each time it is asked for more, it looks up
 * remixes and club versions of what you listen to most, mixed with dance styles such as techno and
 * house. Everything about you stays on the phone; only the search words go to YouTube Music.
 */
class DjFeed {

    private val _songs = MutableStateFlow<List<SongEntity>>(emptyList())
    val songs: StateFlow<List<SongEntity>> = _songs.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val lock = Mutex()
    private val seen = HashSet<String>()
    private var queries: ArrayDeque<String> = ArrayDeque()
    private var round = 0

    private val styles = listOf(
        "techno", "tech house", "house music", "deep house", "progressive house", "melodic techno",
        "afro house", "bass house", "electro house", "trance", "minimal techno", "dance remix",
        "club remix", "edm", "dj mix", "festival edm", "drum and bass", "future house"
    )

    private val desiStyles = listOf("bollywood club remix", "punjabi dj remix", "desi house remix", "hindi edm")

    private val suffixes = listOf(
        "remix", "club mix", "dj edit", "extended mix", "bootleg", "mashup", "club edit",
        "radio edit", "vip mix", "festival mix", "dance edit"
    )

    /** Extra words so the same search does not bring back the same results every time. */
    private val flavours = listOf(
        "2023", "2024", "2025", "2026", "new", "best", "popular", "viral", "underground",
        "party", "club", "festival", "late night", "peak time", "warm up", "bangers"
    )

    /** Clears the list so the next load starts fresh (for example after your taste changed). */
    suspend fun reset() = lock.withLock {
        seen.clear()
        queries = ArrayDeque()
        round = 0
        _songs.value = emptyList<SongEntity>()
    }

    /** Starts over with a fresh list. */
    suspend fun refresh() {
        reset()
        loadMore()
    }

    /** Loads a first screenful if the list is empty. */
    suspend fun ensureLoaded() {
        if (_songs.value.size < 12) loadMore()
    }

    /** Adds the next batch of songs to the end of the list. */
    suspend fun loadMore() {
        if (!lock.tryLock()) return
        try {
            _loading.value = true
            var added = 0
            var attempts = 0
            // Keep going until something new turns up, but never forever.
            while (added < 6 && attempts < 4) {
                attempts++
                if (queries.isEmpty()) queries = buildQueries()
                val batch = ArrayList<String>()
                repeat(3) { queries.removeFirstOrNull()?.let { batch.add(it) } }
                if (batch.isEmpty()) break

                val results = coroutineScope {
                    batch.map { q ->
                        async {
                            try {
                                YtMusic.search(q)
                            } catch (e: Exception) {
                                emptyList<SongEntity>()
                            }
                        }
                    }.map { it.await() }
                }

                // Take songs from each search in turn so the list stays varied.
                val fresh = ArrayList<SongEntity>()
                val mixed = results.map { it.shuffled() }
                val depth = mixed.maxOfOrNull { it.size } ?: 0
                // The last few artists, so the same artist does not come up again and again.
                val recent = ArrayList<String>()
                _songs.value.takeLast(4).forEach { recent.add(it.artist.lowercase()) }
                for (i in 0 until depth) {
                    for (list in mixed) {
                        val s = list.getOrNull(i) ?: continue
                        if (s.url in seen) continue
                        if (s.durationSec in 1L..89L || s.durationSec > 720L) continue
                        if (Taste.isBlocked(s)) continue
                        if (DjHistory.has(s.url)) continue
                        val artist = s.artist.lowercase()
                        if (artist.isNotBlank() && recent.takeLast(4).contains(artist)) continue
                        seen.add(s.url)
                        fresh.add(s)
                        recent.add(artist)
                    }
                }
                if (fresh.isNotEmpty()) {
                    _songs.value = _songs.value + fresh
                    added += fresh.size
                }
            }
        } finally {
            _loading.value = false
            lock.unlock()
        }
    }

    /** Searches built from what you listen to most, mixed with plain dance styles. */
    private fun buildQueries(): ArrayDeque<String> {
        val suffix = suffixes[round % suffixes.size]
        round++

        val personal = ArrayList<String>()
        if (Affinity.hasTaste()) {
            val top = Affinity.topSongs(10).shuffled()
            top.forEach { song ->
                if (song.title.isNotBlank()) personal.add("${cleanTitle(song.title)} remix")
                if (song.artist.isNotBlank()) personal.add("${song.artist} $suffix")
            }
        }
        val generic = ArrayList<String>(styles)
        if (Region.countryCode == "IN") generic.addAll(desiStyles)
        generic.shuffle()

        // One of yours, then a style, then another of yours...
        val out = ArrayDeque<String>()
        val flavour = flavours.random()
        val a = personal.distinct().shuffled().iterator()
        val b = generic.map { if ((0..2).random() == 0) "$it $flavour" else it }.iterator()
        while (a.hasNext() || b.hasNext()) {
            if (a.hasNext()) out.addLast(a.next())
            if (b.hasNext()) out.addLast(b.next())
            if (a.hasNext()) out.addLast(a.next())
        }
        return out
    }

    private fun cleanTitle(title: String): String =
        title.replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
            .replace(Regex("(?i)official|video|audio|lyrics?|full song|hd|4k"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
