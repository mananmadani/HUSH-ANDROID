package app.hush.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hush.HushVm

@Composable
private fun CenterMessage(text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        action?.let {
            Box(Modifier.padding(top = 16.dp)) { it() }
        }
    }
}

@Composable
fun ScreenTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

// --- Home ---------------------------------------------------------------------

@Composable
private fun ShelfRow(title: String, songs: List<app.hush.data.SongEntity>, vm: HushVm) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(songs, key = { _, s -> s.url }) { i, song ->
            Column(
                Modifier
                    .width(140.dp)
                    .clickable { vm.player.play(songs, i) }
            ) {
                Art(song.thumb, 140.dp, radius = 10.dp)
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun HomeScreen(vm: HushVm) {
    val recent by vm.recent.collectAsState()
    val nothingToShow = vm.shelves.isEmpty() && recent.isEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Hush",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            if (vm.homeLoading && !nothingToShow) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            IconButton(onClick = vm::loadHome) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        when {
            nothingToShow && vm.homeLoading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            nothingToShow && vm.homeError != null ->
                CenterMessage(vm.homeError!!) { Button(onClick = vm::loadHome) { Text("Retry") } }

            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (recent.isNotEmpty()) {
                    item { ShelfRow("Recently played", recent, vm) }
                }
                itemsIndexed(vm.shelves) { _, shelf ->
                    ShelfRow(shelf.title, shelf.songs, vm)
                }
            }
        }
    }
}

// --- Search -------------------------------------------------------------------

@Composable
fun SearchScreen(vm: HushVm) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            singleLine = true,
            placeholder = { Text("Songs, artists") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
        when {
            vm.searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            vm.searchError != null -> CenterMessage(vm.searchError!!) {
                Button(onClick = vm::search) { Text("Retry") }
            }

            vm.searched && vm.results.isEmpty() -> CenterMessage("No songs found.")
            !vm.searched -> CenterMessage("Search for any song.")
            else -> SongList(vm.results, vm, Modifier.fillMaxSize())
        }
    }
}

// --- Library ------------------------------------------------------------------

@Composable
fun LibraryScreen(vm: HushVm, onOpenLiked: () -> Unit, onOpenPlaylist: (Long) -> Unit) {
    val liked by vm.liked.collectAsState()
    val playlists by vm.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Library")
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                LibraryRow(
                    icon = { Icon(Icons.Filled.Favorite, null, tint = MaterialTheme.colorScheme.primary) },
                    title = "Liked songs",
                    subtitle = "${liked.size} songs",
                    onClick = onOpenLiked
                )
            }
            item {
                LibraryRow(
                    icon = { Icon(Icons.Filled.Add, null) },
                    title = "New playlist",
                    subtitle = null,
                    onClick = { creating = true }
                )
            }
            itemsIndexed(playlists, key = { _, p -> p.id }) { _, p ->
                LibraryRow(
                    icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    title = p.name,
                    subtitle = "${p.cnt} songs",
                    onClick = { onOpenPlaylist(p.id) }
                )
            }
            item {
                LibraryRow(
                    icon = { Icon(Icons.Filled.History, null) },
                    title = "Clear listening history",
                    subtitle = "Empties Recently played on Home",
                    onClick = vm::clearHistory
                )
            }
        }
    }

    if (creating) {
        NamePlaylistDialog(
            onCreate = { vm.createPlaylist(it); creating = false },
            onDismiss = { creating = false }
        )
    }
}

@Composable
private fun LibraryRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(40.dp)) { icon() }
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- Liked songs / playlist detail --------------------------------------------

@Composable
fun LikedScreen(vm: HushVm, onBack: () -> Unit) {
    val liked by vm.liked.collectAsState()
    SongListPage(
        title = "Liked songs",
        songs = liked,
        vm = vm,
        onBack = onBack,
        extraAction = "Unlike" to { vm.setLiked(it, false) },
        onDelete = null
    )
}

@Composable
fun PlaylistScreen(vm: HushVm, id: Long, onBack: () -> Unit) {
    val name by remember(id) { vm.repo.playlistName(id) }.collectAsState(initial = "")
    val songs by remember(id) { vm.repo.playlistSongs(id) }.collectAsState(initial = emptyList())
    SongListPage(
        title = name ?: "Playlist",
        songs = songs,
        vm = vm,
        onBack = onBack,
        extraAction = "Remove from playlist" to { vm.removeFromPlaylist(id, it) },
        onDelete = { vm.deletePlaylist(id); onBack() }
    )
}

@Composable
private fun SongListPage(
    title: String,
    songs: List<app.hush.data.SongEntity>,
    vm: HushVm,
    onBack: () -> Unit,
    extraAction: Pair<String, (app.hush.data.SongEntity) -> Unit>,
    onDelete: (() -> Unit)?
) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete playlist") }
            }
        }
        if (songs.isEmpty()) {
            CenterMessage("Nothing here yet.")
        } else {
            SongList(
                songs = songs,
                vm = vm,
                modifier = Modifier.fillMaxSize(),
                extraAction = extraAction,
                header = {
                    item {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(onClick = { vm.player.play(songs, 0) }) {
                                Icon(Icons.Filled.PlayArrow, null)
                                Text("Play", modifier = Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(onClick = { vm.player.play(songs, 0, shuffle = true) }) {
                                Icon(Icons.Filled.Shuffle, null)
                                Text("Shuffle", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            )
        }
    }
}
