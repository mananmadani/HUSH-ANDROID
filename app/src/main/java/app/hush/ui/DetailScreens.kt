package app.hush.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hush.HushVm
import app.hush.data.SongEntity
import app.hush.net.CollectionItem
import app.hush.net.OnlineList
import app.hush.net.YtMusic
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// --- Artist page -----------------------------------------------------------------------

private class ArtistData(
    val thumb: String?,
    val subscribers: Long,
    val songs: List<SongEntity>,
    val albums: List<CollectionItem>
)

/** Artist pages are built from YouTube Music search: the artist, their songs and their albums. */
private suspend fun loadArtist(name: String): ArtistData = coroutineScope {
    val artists = async { runCatching { YtMusic.searchArtists(name) }.getOrDefault(emptyList()) }
    val songs = async { YtMusic.search(name) }
    val albums = async { runCatching { YtMusic.searchCollections(name, albums = true) }.getOrDefault(emptyList()) }

    val found = artists.await()
    val match = found.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: found.firstOrNull()
    val all = songs.await()
    ArtistData(
        thumb = match?.thumb,
        subscribers = match?.subscribers ?: -1,
        songs = all.filter { it.artist.contains(name, ignoreCase = true) }.ifEmpty { all },
        albums = albums.await().filter { it.subtitle.contains(name, ignoreCase = true) }
    )
}

@Composable
private fun BackBar(onBack: () -> Unit) {
    Row(Modifier.padding(start = 12.dp, top = 12.dp)) {
        RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
    }
}

@Composable
fun ArtistScreen(vm: HushVm, name: String, onBack: () -> Unit) {
    val openCollection = LocalOpenCollection.current
    var data by remember(name) { mutableStateOf<ArtistData?>(null) }
    var failed by remember(name) { mutableStateOf(false) }
    LaunchedEffect(name) {
        runCatching { loadArtist(name) }
            .onSuccess { data = it }
            .onFailure { failed = true }
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack)
        val d = data
        when {
            d != null -> SongList(
                songs = d.songs,
                vm = vm,
                modifier = Modifier.fillMaxSize(),
                header = {
                    item {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Art(d.thumb, 96.dp, radius = 48.dp, seed = name)
                            Column(Modifier.padding(start = 16.dp)) {
                                Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (d.subscribers > 0) "Artist · ${compact(d.subscribers)} subscribers" else "Artist",
                                    fontSize = 13.sp,
                                    color = Dusk.Muted
                                )
                            }
                        }
                    }
                    item { PlayShuffleRow(d.songs, vm) }
                    if (d.albums.isNotEmpty()) {
                        item { SectionTitle("Albums", Modifier.padding(start = 20.dp, top = 12.dp, bottom = 10.dp)) }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(d.albums) { album ->
                                    Column(Modifier.width(132.dp).clickable { openCollection(album) }) {
                                        Art(album.thumb, 132.dp, radius = 20.dp, seed = album.title)
                                        Text(
                                            album.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                        Text("Album", fontSize = 12.sp, color = Dusk.Muted)
                                    }
                                }
                            }
                        }
                    }
                    item { SectionTitle("Songs", Modifier.padding(start = 20.dp, top = 22.dp, bottom = 4.dp)) }
                }
            )

            failed -> CenterMessage("Couldn't load this artist. Check your connection and try again.")
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

// --- Album / playlist page -------------------------------------------------------------

@Composable
fun CollectionScreen(vm: HushVm, entry: CollectionItem, onBack: () -> Unit) {
    val context = LocalContext.current
    var data by remember(entry.url) { mutableStateOf<OnlineList?>(null) }
    var failed by remember(entry.url) { mutableStateOf(false) }
    LaunchedEffect(entry.url) {
        runCatching { YtMusic.collection(entry.url, entry.thumb) }
            .onSuccess { data = it }
            .onFailure { failed = true }
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(onBack)
        val d = data
        when {
            d != null -> SongList(
                songs = d.songs,
                vm = vm,
                modifier = Modifier.fillMaxSize(),
                header = {
                    item {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Art(d.cover ?: entry.thumb, 110.dp, radius = 20.dp, seed = entry.title)
                            Column(Modifier.padding(start = 16.dp)) {
                                Text(
                                    d.title.ifBlank { entry.title },
                                    style = MaterialTheme.typography.headlineSmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sub = listOf(d.subtitle.ifBlank { entry.subtitle }, "${d.songs.size} songs")
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                                Text(sub, fontSize = 13.sp, color = Dusk.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    item { PlayShuffleRow(d.songs, vm) }
                    item {
                        OutlinedButton(
                            onClick = {
                                vm.saveCollection(d.title.ifBlank { entry.title }, d.songs) {
                                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        ) {
                            Icon(Icons.Filled.Add, null)
                            Text("Save to my playlists", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            )

            failed -> CenterMessage("Couldn't load this one. Check your connection and try again.")
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

@Composable
private fun PlayShuffleRow(songs: List<SongEntity>, vm: HushVm) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = { vm.player.play(songs, 0) }, enabled = songs.isNotEmpty()) {
            Icon(Icons.Filled.PlayArrow, null)
            Text("Play", modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(onClick = { vm.player.play(songs, 0, shuffle = true) }, enabled = songs.isNotEmpty()) {
            Icon(Icons.Filled.Shuffle, null)
            Text("Shuffle", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

private fun compact(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}
