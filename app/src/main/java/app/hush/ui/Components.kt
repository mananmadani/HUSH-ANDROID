package app.hush.ui

import android.content.Context
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.foundation.layout.heightIn
import android.widget.Toast
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.hush.net.CollectionItem
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hush.HushVm
import app.hush.Prefs
import app.hush.data.PlaylistInfo
import app.hush.data.SongEntity
import app.hush.player.PlayerState
import coil.compose.AsyncImage

/** Opens an artist page. Provided by MainActivity. */
val LocalOpenArtist = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Opens an album or playlist page. Provided by MainActivity. */
val LocalOpenCollection = staticCompositionLocalOf<(CollectionItem) -> Unit> { {} }

/** The Hush logo (the same three sound bars as the app icon), drawn in code so it stays sharp. */
@Composable
fun HushLogo(size: Dp, modifier: Modifier = Modifier) {
    val bars = Prefs.accentDark
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(
            color = Color(0xFF14141A),
            size = androidx.compose.ui.geometry.Size(s, s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.28f)
        )
        val k = s / 52f
        fun bar(x: Float, top: Float, bottom: Float) {
            drawRoundRect(
                color = bars,
                topLeft = androidx.compose.ui.geometry.Offset(s / 2 + (x - 54f) * k, s / 2 + (top - 54f) * k),
                size = androidx.compose.ui.geometry.Size(8f * k, (bottom - top) * k),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f * k)
            )
        }
        bar(36f, 48f, 60f)
        bar(50f, 38f, 70f)
        bar(64f, 46f, 62f)
    }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

// --- Artwork ------------------------------------------------------------------------------

private val ART_PALETTES = listOf(
    Color(0xFF7C5CFF) to Color(0xFFFF8A9B),
    Color(0xFF1F6F78) to Color(0xFF9AE3D0),
    Color(0xFFFF9E5E) to Color(0xFFFFD98A),
    Color(0xFF3B2A6B) to Color(0xFFE8A0BF),
    Color(0xFF5B1E4F) to Color(0xFFE26A8D),
    Color(0xFF16385E) to Color(0xFF6FB1FC)
)

/** A stable gradient for a song, shown while the cover loads or when there is none. */
fun artBrush(seed: String): Brush {
    val pair = ART_PALETTES[(seed.hashCode() and 0x7fffffff) % ART_PALETTES.size]
    return Brush.linearGradient(listOf(pair.first, pair.second))
}

/** YouTube hands out tiny covers (often 60px). Ask for a big one so posters stay sharp. */
fun hiRes(url: String): String {
    var u = url
    u = Regex("=w\\d+-h\\d+").replace(u, "=w720-h720")
    u = Regex("=s\\d+").replace(u, "=s720")
    u = Regex("/(default|mqdefault)\\.jpg").replace(u, "/hqdefault.jpg")
    return u
}

@Composable
fun Art(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    radius: Dp = 12.dp,
    seed: String = ""
) {
    val sized = if (size == Dp.Unspecified) modifier else modifier.size(size)
    Box(
        sized
            .clip(RoundedCornerShape(radius))
            .background(artBrush(seed.ifEmpty { url.orEmpty() }))
    ) {
        if (url != null) {
            AsyncImage(
                model = hiRes(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun shareSong(context: Context, title: String, artist: String, url: String) {
    val text = buildString {
        append(title)
        if (artist.isNotBlank()) append(" - ").append(artist)
        append("\n").append(url)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share song").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// --- Small building blocks ---------------------------------------------------------------

@Composable
fun CenterMessage(text: String, action: (@Composable () -> Unit)? = null) {
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
            color = Dusk.Muted
        )
        action?.let {
            Box(Modifier.padding(top = 16.dp)) { it() }
        }
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Dusk.Raised)
            .border(1.dp, Dusk.Line, CircleShape)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Dusk.Text, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun HushChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .height(44.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Dusk.Raised)
            .then(if (selected) Modifier else Modifier.border(1.dp, Dusk.Line, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Dusk.OnAccent else Dusk.Text
        )
    }
}

// --- Songs -------------------------------------------------------------------------------

@Composable
fun SongRow(
    song: SongEntity,
    onClick: () -> Unit,
    actions: List<Pair<String, () -> Unit>>,
    onAddToPlaylist: (() -> Unit)? = null
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(song.thumb, 52.dp, radius = 14.dp, seed = song.title)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                color = Dusk.Muted
            )
        }
        if (onAddToPlaylist != null) {
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to playlist", tint = Dusk.Muted)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Dusk.Muted)
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
 * A scrollable song list with the standard Hush actions (play next, add to queue,
 * add to playlist, share) plus an optional extra action such as "Remove".
 */
@Composable
fun SongList(
    songs: List<SongEntity>,
    vm: HushVm,
    modifier: Modifier = Modifier,
    extraAction: Pair<String, (SongEntity) -> Unit>? = null,
    header: (LazyListScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    val openArtist = LocalOpenArtist.current
    val playlists by vm.playlists.collectAsState()
    var pickFor by remember { mutableStateOf<SongEntity?>(null) }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 190.dp)) {
        header?.invoke(this)
        itemsIndexed(songs, key = { _, s -> s.url }) { index, song ->
            val actions = buildList {
                add("Play next" to { vm.player.playNext(song) })
                add("Add to queue" to { vm.player.addToQueue(song) })
                add("Add to playlist" to { pickFor = song })
                if (song.artist.isNotBlank()) add("Go to artist" to { openArtist(song.artist) })
                add("Share" to { shareSong(context, song.title, song.artist, song.url) })
                extraAction?.let { (label, run) -> add(label to { run(song) }) }
            }
            SongRow(
                song,
                onClick = { vm.player.play(songs, index) },
                actions = actions,
                onAddToPlaylist = { pickFor = song }
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerDialog(
    playlists: List<PlaylistInfo>,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Dusk.Surface,
        contentColor = Dusk.Text
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Add to playlist", style = MaterialTheme.typography.titleMedium)

            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("New playlist name", color = Dusk.Muted) },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        Toast.makeText(context, "Added to ${name.trim()}", Toast.LENGTH_SHORT).show()
                        onCreate(name.trim())
                    },
                    enabled = name.isNotBlank()
                ) { Text("Create") }
            }

            if (playlists.isNotEmpty()) {
                Text(
                    "Your playlists",
                    fontSize = 13.sp,
                    color = Dusk.Muted,
                    modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)
                )
            }
            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                items(playlists, key = { it.id }) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(context, "Added to ${p.name}", Toast.LENGTH_SHORT).show()
                                onPick(p.id)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("${p.cnt} songs", fontSize = 12.sp, color = Dusk.Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NamePlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Dusk.Surface,
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

// --- Floating mini player and navigation pill ----------------------------------------------

@Composable
fun MiniPlayer(
    state: PlayerState,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Dusk.Surface)
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.28f), Dusk.Pink.copy(alpha = 0.18f))
                )
            )
            .border(1.dp, Dusk.Line, shape)
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Art(state.art, 44.dp, radius = 12.dp, seed = state.title)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    state.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    state.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    color = Dusk.Muted
                )
            }
            if (state.loading && !state.playing) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Box(Modifier.size(12.dp))
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Dusk.Text.copy(alpha = 0.12f))
                    .clickable(onClickLabel = if (state.playing) "Pause" else "Play", onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.playing) "Pause" else "Play",
                    tint = Dusk.Text
                )
            }
        }
        val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = accent,
            trackColor = Dusk.Text.copy(alpha = 0.12f)
        )
    }
}

private class NavItem(val route: String, val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("home", "Home", Icons.Filled.Home),
    NavItem("search", "Search", Icons.Filled.Search),
    NavItem("library", "Library", Icons.Filled.LibraryMusic)
)

/** The floating pill at the bottom: the active tab expands to show its name. */
@Composable
fun FloatingNav(current: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(Dusk.Raised.copy(alpha = 0.94f))
            .border(1.dp, Dusk.Line, shape)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NAV_ITEMS.forEach { item ->
            if (item.route == current) {
                Row(
                    Modifier
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .clickable(onClickLabel = item.label) { onSelect(item.route) }
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(item.icon, contentDescription = null, tint = Dusk.OnAccent, modifier = Modifier.size(22.dp))
                    Text(item.label, color = Dusk.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = item.label) { onSelect(item.route) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, contentDescription = item.label, tint = Dusk.Muted, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
