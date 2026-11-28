package app.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hush.HushVm
import app.hush.data.SongEntity
import app.hush.net.YtMusic
import kotlinx.coroutines.launch

/** Its own screen for the DJ queue: search for songs, add them, and drag them into order. */
@Composable
fun DjQueueScreen(vm: HushVm, onBack: () -> Unit) {
    val feed = vm.dj.feed
    val songs by feed.songs.collectAsState()
    val ui by vm.dj.ui.collectAsState()
    val queue by vm.dj.queue.collectAsState()
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SongEntity>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            searching = true
            results = try {
                YtMusic.search(q)
            } catch (e: Exception) {
                emptyList<SongEntity>()
            }
            searched = true
            searching = false
        }
    }

    LaunchedEffect(Unit) { feed.ensureLoaded() }

    val showingSearch = query.isNotBlank() && searched

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Text(
                    "Queue and search",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 14.dp)
                )
            }
            val box = RoundedCornerShape(22.dp)
            Row(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(box)
                    .background(Dusk.Raised)
                    .border(1.dp, Dusk.Line, box)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, tint = Dusk.Muted, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    if (query.isEmpty()) Text("Search songs to add", color = Dusk.Muted, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            if (it.isBlank()) {
                                results = emptyList()
                                searched = false
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = Dusk.Text, fontSize = 15.sp),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        "Clear",
                        tint = Dusk.Muted,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable {
                                query = ""
                                results = emptyList()
                                searched = false
                            }
                    )
                }
            }
            Text(
                "Tap a song to add it to the queue. Use the three dots for more choices.",
                fontSize = 12.sp,
                color = Dusk.Muted,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)
            )
        }

        if (showingSearch) {
            sectionHeader("SEARCH RESULTS")
            if (searching) {
                item { Spinner() }
            } else if (results.isEmpty()) {
                item {
                    Text(
                        "No songs found.",
                        color = Dusk.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
            items(results, key = { "r" + it.url }) { song -> AddRow(vm, song, ui.running, context) }
            queueSection(vm, queue.size)
        } else {
            queueSection(vm, queue.size)
            sectionHeader("PICKED FOR YOU")
            items(songs.take(25), key = { "s" + it.url }) { song -> AddRow(vm, song, ui.running, context) }
            item {
                if (searching) Spinner()
                Spacer(Modifier.height(60.dp))
            }
        }
        item { Spacer(Modifier.height(60.dp)) }
    }
}

private fun LazyListScope.sectionHeader(text: String) {
    item {
        Text(
            text,
            fontSize = 12.sp,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.SemiBold,
            color = Dusk.Muted,
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 6.dp)
        )
    }
}

private fun LazyListScope.queueSection(vm: HushVm, size: Int) {
    item {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (size > 0) "UP NEXT ($size)" else "UP NEXT",
                fontSize = 12.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.SemiBold,
                color = Dusk.Muted,
                modifier = Modifier.weight(1f)
            )
            if (size > 0) {
                Text(
                    "Clear",
                    fontSize = 13.sp,
                    color = Dusk.Muted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { vm.dj.clearQueue() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        if (size > 0) {
            Text(
                "Hold the handle on the right and drag to reorder.",
                fontSize = 12.sp,
                color = Dusk.Muted,
                modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
            )
        }
        DjQueueList(vm)
    }
}

@Composable
private fun Spinner() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        LoadingSpinner()
    }
}

@Composable
private fun AddRow(vm: HushVm, song: SongEntity, running: Boolean, context: android.content.Context) {
    SongRow(
        song = song,
        onClick = {
            vm.dj.enqueue(song)
            android.widget.Toast.makeText(context, "Added to the DJ queue", android.widget.Toast.LENGTH_SHORT).show()
        },
        actions = listOf(
            "Add to DJ queue" to {
                vm.dj.enqueue(song)
                android.widget.Toast.makeText(context, "Added to the DJ queue", android.widget.Toast.LENGTH_SHORT).show()
            },
            "Play next" to {
                vm.dj.playNext(song)
                android.widget.Toast.makeText(context, "Playing next", android.widget.Toast.LENGTH_SHORT).show()
            },
            (if (running) "Mix in now" else "Start a set from this") to {
                if (running) vm.dj.mixIn(song) else vm.dj.startWith(song)
            },
            "Don't play this song" to { hideSong(context, song) },
            "Don't play this artist" to { hideArtist(context, song.artist) }
        )
    )
}
