package app.hush.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hush.HushVm
import app.hush.data.PlaylistInfo
import app.hush.data.SongEntity
import app.hush.player.PlayerState
import coil.compose.AsyncImage

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
fun Art(url: String?, size: Dp, modifier: Modifier = Modifier, radius: Dp = 8.dp) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(size)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        }
    }
}

@Composable
fun SongRow(
    song: SongEntity,
    onClick: () -> Unit,
    actions: List<Pair<String, () -> Unit>>
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(song.thumb, 52.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                actions.forEach { (label, run) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { menu = false; run() }
                    )
                }
            }
        }
    }
}

/**
 * A scrollable song list with the standard Hush actions (play next, add to
 * queue, add to playlist) plus an optional extra action such as "Remove".
 */
@Composable
fun SongList(
    songs: List<SongEntity>,
    vm: HushVm,
    modifier: Modifier = Modifier,
    extraAction: Pair<String, (SongEntity) -> Unit>? = null,
    header: (LazyListScope.() -> Unit)? = null
) {
    val playlists by vm.playlists.collectAsState()
    var pickFor by remember { mutableStateOf<SongEntity?>(null) }

    LazyColumn(modifier = modifier) {
        header?.invoke(this)
        itemsIndexed(songs, key = { _, s -> s.url }) { index, song ->
            val actions = buildList {
                add("Play next" to { vm.player.playNext(song) })
                add("Add to queue" to { vm.player.addToQueue(song) })
                add("Add to playlist" to { pickFor = song })
                extraAction?.let { (label, run) -> add(label to { run(song) }) }
            }
            SongRow(song, onClick = { vm.player.play(songs, index) }, actions = actions)
        }
    }

    pickFor?.let { song ->
        PlaylistPickerDialog(
            playlists = playlists,
            onPick = { vm.addToPlaylist(it, song); pickFor = null },
            onCreate = { name -> vm.createPlaylist(name, song); pickFor = null },
            onDismiss = { pickFor = null }
        )
    }
}

@Composable
fun PlaylistPickerDialog(
    playlists: List<PlaylistInfo>,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                playlists.forEach { p ->
                    Text(
                        p.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(p.id) }
                            .padding(vertical = 12.dp)
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("New playlist") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun NamePlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MiniPlayer(state: PlayerState, onToggle: () -> Unit, onOpen: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(state.art, 44.dp, radius = 6.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        state.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Box(Modifier.width(12.dp))
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.playing) "Pause" else "Play"
                    )
                }
            }
            val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
