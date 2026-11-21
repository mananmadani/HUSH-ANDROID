package app.hush.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.hush.HushVm
import app.hush.Prefs
import app.hush.data.SongEntity
import app.hush.net.YtMusic
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The DJ tab: search, your queue, and an endless list of club-friendly songs picked from what you listen to. */
@Composable
fun DjScreen(vm: HushVm, onOpenDeck: () -> Unit) {
    val feed = vm.dj.feed
    val songs by feed.songs.collectAsState()
    val loading by feed.loading.collectAsState()
    val ui by vm.dj.ui.collectAsState()
    val queue by vm.dj.queue.collectAsState()
    val context = LocalContext.current
    val haptic = rememberHaptics()
    val accent = MaterialTheme.colorScheme.primary
    val state = rememberLazyListState()
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

    // Load more as the end of the list comes near.
    LaunchedEffect(state, songs.size) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .map { it >= songs.size - 6 }
            .distinctUntilChanged()
            .collect { near -> if (near && songs.isNotEmpty()) feed.loadMore() }
    }

    val showingSearch = query.isNotBlank() && searched

    LazyColumn(state = state, modifier = Modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                Text("DJ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Club-ready songs picked from what you listen to. Hush mixes them on the beat.",
                    fontSize = 13.sp,
                    color = Dusk.Muted,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Search
                val box = RoundedCornerShape(22.dp)
                Row(
                    Modifier
                        .padding(top = 14.dp)
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

                Row(
                    Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Steady", "Build up", "Waves").forEachIndexed { i, label ->
                        HushChip(label, Prefs.djShape == i) { Prefs.chooseDjShape(i) }
                    }
                }
                val pill = RoundedCornerShape(24.dp)
                Row(
                    Modifier
                        .padding(top = 14.dp)
                        .height(50.dp)
                        .clip(pill)
                        .background(accent)
                        .clickable {
                            haptic(Tap.Heavy)
                            if (ui.running) onOpenDeck() else if (songs.isNotEmpty()) {
                                vm.dj.start(0)
                                onOpenDeck()
                            }
                        }
                        .padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (ui.running) Icons.Filled.Album else Icons.Filled.PlayArrow,
                        null,
                        tint = Dusk.OnAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        if (ui.running) "Open the decks" else "Start DJ set",
                        color = Dusk.OnAccent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        if (queue.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "UP NEXT",
                        fontSize = 12.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Dusk.Muted,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Clear",
                        fontSize = 13.sp,
                        color = accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { vm.dj.clearQueue() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Text(
                    "Hold the handle and drag to reorder.",
                    fontSize = 12.sp,
                    color = Dusk.Muted,
                    modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
                )
                DjQueueList(vm)
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (showingSearch) "SEARCH RESULTS" else "PICKED FOR YOU",
                    fontSize = 12.sp,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Dusk.Muted,
                    modifier = Modifier.weight(1f)
                )
                if (!showingSearch) {
                    Icon(
                        Icons.Filled.Refresh,
                        "Refresh the list",
                        tint = accent,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptic(Tap.Click)
                                scope.launch { feed.refresh() }
                            }
                            .padding(7.dp)
                    )
                }
            }
        }

        if (showingSearch) {
            if (results.isEmpty() && !searching) {
                item {
                    Text(
                        "No songs found.",
                        color = Dusk.Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
            itemsIndexed(results, key = { _, s -> "r" + s.url }) { _, song ->
                DjSongRow(vm, song, ui.running, onOpenDeck, context)
            }
            item {
                if (searching) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                    }
                }
                Spacer(Modifier.height(190.dp))
            }
        } else {
            itemsIndexed(songs, key = { _, s -> s.url }) { index, song ->
                DjSongRow(vm, song, ui.running, onOpenDeck, context, feedIndex = index)
            }
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                    } else if (songs.isEmpty()) {
                        Text("Couldn't load songs. Check your connection.", color = Dusk.Muted, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(190.dp))
            }
        }
    }
}

@Composable
private fun DjSongRow(
    vm: HushVm,
    song: SongEntity,
    running: Boolean,
    onOpenDeck: () -> Unit,
    context: android.content.Context,
    feedIndex: Int = -1
) {
    SongRow(
        song = song,
        onClick = {
            if (running) {
                vm.dj.mixIn(song)
                Toast.makeText(context, "Mixing in ${song.title}", Toast.LENGTH_SHORT).show()
            } else {
                if (feedIndex >= 0) vm.dj.start(feedIndex) else vm.dj.startWith(song)
                onOpenDeck()
            }
        },
        actions = listOf(
            (if (running) "Mix in now" else "Start set from this") to {
                if (running) vm.dj.mixIn(song) else {
                    if (feedIndex >= 0) vm.dj.start(feedIndex) else vm.dj.startWith(song)
                    onOpenDeck()
                }
            },
            "Play next" to {
                vm.dj.playNext(song)
                Toast.makeText(context, "Playing next in the DJ set", Toast.LENGTH_SHORT).show()
            },
            "Add to DJ queue" to {
                vm.dj.enqueue(song)
                Toast.makeText(context, "Added to the DJ queue", Toast.LENGTH_SHORT).show()
            },
            "Don't play this song" to { hideSong(context, song) },
            "Don't play this artist" to { hideArtist(context, song.artist) }
        )
    )
}

/** Your queue. Drag the handle on the right to move a song, tap the cross to remove it. */
@Composable
fun DjQueueList(vm: HushVm) {
    val queue by vm.dj.queue.collectAsState()
    val rowHeight = 64.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    var draggingUrl by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableStateOf(0f) }

    if (queue.isEmpty()) {
        Text(
            "Nothing queued. Search above, or use \"Add to DJ queue\" on any song.",
            color = Dusk.Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        return
    }

    Column(Modifier.fillMaxWidth()) {
        queue.forEach { song ->
            key(song.url) {
                val dragging = draggingUrl == song.url
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragY else 0f }
                        .background(if (dragging) Dusk.Raised else Color.Transparent)
                        .padding(start = 20.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Art(song.thumb, 44.dp, radius = 12.dp, seed = song.title)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            song.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            song.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            color = Dusk.Muted
                        )
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { vm.dj.removeFromQueue(song.url) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, "Remove from queue", tint = Dusk.Muted, modifier = Modifier.size(20.dp))
                    }
                    Box(
                        Modifier
                            .size(width = 48.dp, height = rowHeight)
                            .pointerInput(song.url) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingUrl = song.url
                                        dragY = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragY += amount.y
                                        val list = vm.dj.queue.value
                                        val i = list.indexOfFirst { it.url == song.url }
                                        if (i >= 0) {
                                            if (dragY > rowPx * 0.5f && i < list.lastIndex) {
                                                vm.dj.moveInQueue(i, i + 1)
                                                dragY -= rowPx
                                            } else if (dragY < -rowPx * 0.5f && i > 0) {
                                                vm.dj.moveInQueue(i, i - 1)
                                                dragY += rowPx
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggingUrl = null
                                        dragY = 0f
                                    },
                                    onDragCancel = {
                                        draggingUrl = null
                                        dragY = 0f
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.DragHandle, "Drag to reorder", tint = Dusk.Muted)
                    }
                }
            }
        }
    }
}
