package app.hush.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hush.HushVm
import app.hush.SearchKind
import app.hush.net.ArtistItem
import app.hush.net.CollectionItem
import app.hush.Mood
import app.hush.Moods
import app.hush.data.PlaylistInfo
import app.hush.data.SongEntity

private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

// --- Search -------------------------------------------------------------------

@Composable
fun SearchScreen(vm: HushVm) {
    val focus: FocusManager = LocalFocusManager.current
    val accent = MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Search", Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp))
        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::onQueryChange,
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            placeholder = { Text("Songs, artists", color = Dusk.Muted) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Dusk.Muted) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = vm::clearQuery) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Dusk.Muted)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = Dusk.Line,
                focusedContainerColor = Dusk.Raised,
                unfocusedContainerColor = Dusk.Raised,
                cursorColor = accent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.search() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SearchKind.values().toList()) { kind ->
                HushChip(kind.label, vm.searchKind == kind) { vm.selectKind(kind) }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                vm.searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                vm.searchError != null -> CenterMessage(vm.searchError ?: "") {
                    Button(onClick = { vm.search() }) { Text("Retry") }
                }

                vm.suggestions.isNotEmpty() -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 190.dp)
                ) {
                    items(vm.suggestions) { text ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { focus.clearFocus(); vm.searchFor(text) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Search, null, tint = Dusk.Muted, modifier = Modifier.size(20.dp))
                            Text(text, modifier = Modifier.padding(start = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                vm.searched && when (vm.searchKind) {
                    SearchKind.Songs -> vm.results.isEmpty()
                    SearchKind.Artists -> vm.artistResults.isEmpty()
                    else -> vm.collectionResults.isEmpty()
                } -> CenterMessage("Nothing found.")

                vm.searched -> when (vm.searchKind) {
                    SearchKind.Songs -> SongList(vm.results, vm, Modifier.fillMaxSize())
                    SearchKind.Artists -> ArtistResults(vm.artistResults)
                    else -> CollectionResults(vm.collectionResults)
                }
                else -> BrowseSearch(vm, focus)
            }
        }
    }
}

private fun compactCount(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}

@Composable
private fun ArtistResults(artists: List<ArtistItem>) {
    val openArtist = LocalOpenArtist.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 190.dp)) {
        items(artists) { artist ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { openArtist(artist.name) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(artist.thumb, 56.dp, radius = 28.dp, seed = artist.name)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(artist.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (artist.subscribers > 0) "Artist · ${compactCount(artist.subscribers)} subscribers" else "Artist",
                        fontSize = 12.sp,
                        color = Dusk.Muted
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionResults(items: List<CollectionItem>) {
    val openCollection = LocalOpenCollection.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 190.dp)) {
        items(items) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { openCollection(item) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(item.thumb, 56.dp, radius = 14.dp, seed = item.title)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val detail = listOf(item.subtitle, if (item.count > 0) "${item.count} songs" else "")
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    Text(detail, fontSize = 12.sp, color = Dusk.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun BrowseSearch(vm: HushVm, focus: FocusManager) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 190.dp)) {
        if (vm.recentSearches.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Recent searches", Modifier.weight(1f))
                    TextButton(onClick = vm::clearRecentSearches) { Text("Clear") }
                }
            }
            items(vm.recentSearches) { text ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { focus.clearFocus(); vm.searchFor(text) }
                        .padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.History, null, tint = Dusk.Muted, modifier = Modifier.size(20.dp))
                    Text(
                        text,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { vm.removeRecentSearch(text) }) {
                        Icon(Icons.Filled.Close, "Remove", tint = Dusk.Muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        item { SectionTitle("Browse by mood", Modifier.padding(start = 20.dp, top = 22.dp, bottom = 10.dp)) }
        items(Moods.ALL.chunked(2)) { pair ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { m -> MoodTile(m) { focus.clearFocus(); vm.searchMood(m) } }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.MoodTile(mood: Mood, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(88.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(mood.from, mood.to)))
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(mood.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

// --- Library ------------------------------------------------------------------

@Composable
fun LibraryScreen(vm: HushVm, onOpenLiked: () -> Unit, onOpenPlaylist: (Long) -> Unit) {
    val context = LocalContext.current
    val liked by vm.liked.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val thumbs by vm.playlistThumbs.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) vm.exportBackup(uri) { toast(context, it) } }
    val import = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importBackup(uri) { toast(context, it) } }

    val cells: List<PlaylistInfo?> = listOf<PlaylistInfo?>(null) + playlists

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 190.dp)) {
        item { ScreenTitle("Library", Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) }
        item {
            val accent = MaterialTheme.colorScheme.primary
            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Dusk.Violet, Dusk.Pink)))
                    .clickable(onClick = onOpenLiked)
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text("Liked songs", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("${liked.size} songs", fontSize = 13.sp, color = Color(0xE6FFFFFF))
                }
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable(enabled = liked.isNotEmpty()) { vm.player.play(liked, 0) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PlayArrow, "Play liked songs", tint = Color.White)
                }
            }
        }
        item { SectionTitle("Playlists", Modifier.padding(start = 20.dp, top = 26.dp, bottom = 10.dp)) }
        items(cells.chunked(2)) { pair ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                pair.forEach { p ->
                    Column(Modifier.weight(1f)) {
                        if (p == null) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Dusk.Raised)
                                    .border(1.dp, Dusk.Line, RoundedCornerShape(22.dp))
                                    .clickable { creating = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Add, "New playlist", tint = Dusk.Muted, modifier = Modifier.size(32.dp))
                            }
                            Text("New playlist", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            Collage(
                                thumbs[p.id].orEmpty(),
                                p.name,
                                Modifier.fillMaxWidth().clickable { onOpenPlaylist(p.id) }
                            )
                            Text(
                                p.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Text("${p.cnt} songs", fontSize = 12.sp, color = Dusk.Muted)
                        }
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
        item { SectionTitle("Import and backup", Modifier.padding(start = 20.dp, top = 26.dp, bottom = 4.dp)) }
        item {
            ActionRow(Icons.Filled.Link, "Import a playlist", "From a YouTube or Spotify link") {
                importing = true
            }
        }
        item {
            ActionRow(Icons.Filled.Upload, "Back up library", "Save likes and playlists to a file") {
                export.launch("hush-backup.json")
            }
        }
        item {
            ActionRow(Icons.Filled.Download, "Restore from backup", "Add likes and playlists from a file") {
                import.launch(arrayOf("*/*"))
            }
        }
    }

    if (importing) {
        ImportDialog(vm) { importing = false }
    }
    if (creating) {
        NamePlaylistDialog(
            onCreate = { vm.createPlaylist(it); creating = false },
            onDismiss = { creating = false }
        )
    }
}

@Composable
private fun ImportDialog(vm: HushVm, onClose: () -> Unit) {
    val context = LocalContext.current
    var link by remember { mutableStateOf("") }
    val busy = vm.importStatus != null
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        containerColor = Dusk.Surface,
        title = { Text("Import a playlist") },
        text = {
            Column {
                Text(
                    "Paste a link to a public playlist from YouTube, YouTube Music or Spotify.",
                    fontSize = 13.sp,
                    color = Dusk.Muted
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    enabled = !busy,
                    singleLine = true,
                    placeholder = { Text("https://...", color = Dusk.Muted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
                vm.importStatus?.let {
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(it, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = link.isNotBlank() && !busy,
                onClick = {
                    vm.importPlaylist(link) { message ->
                        toast(context, message)
                        if (message.startsWith("Imported")) onClose()
                    }
                }
            ) { Text("Import") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Cancel") } }
    )
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Dusk.Raised),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Dusk.Text, modifier = Modifier.size(20.dp)) }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = Dusk.Muted)
        }
    }
}

@Composable
fun Collage(thumbs: List<String>, seed: String, modifier: Modifier) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(22.dp))
            .background(artBrush(seed))
    ) {
        when {
            thumbs.isEmpty() -> Unit
            thumbs.size < 4 -> Art(thumbs[0], Dp.Unspecified, Modifier.fillMaxSize(), radius = 0.dp, seed = seed)
            else -> Column(Modifier.fillMaxSize()) {
                repeat(2) { r ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        repeat(2) { c ->
                            Art(
                                thumbs[r * 2 + c],
                                Dp.Unspecified,
                                Modifier.weight(1f).fillMaxHeight(),
                                radius = 0.dp,
                                seed = seed + c
                            )
                        }
                    }
                }
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
    songs: List<SongEntity>,
    vm: HushVm,
    onBack: () -> Unit,
    extraAction: Pair<String, (SongEntity) -> Unit>,
    onDelete: (() -> Unit)?
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            )
            if (onDelete != null) RoundIconButton(Icons.Filled.Delete, "Delete playlist", onDelete)
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
                            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
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
