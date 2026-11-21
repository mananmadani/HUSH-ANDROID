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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hush.HushVm
import app.hush.Prefs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow

/** The DJ tab: an endless list of club-friendly songs picked from what you listen to. */
@Composable
fun DjScreen(vm: HushVm, onOpenDeck: () -> Unit) {
    val feed = vm.dj.feed
    val songs by feed.songs.collectAsState()
    val loading by feed.loading.collectAsState()
    val ui by vm.dj.ui.collectAsState()
    val context = LocalContext.current
    val haptic = rememberHaptics()
    val accent = MaterialTheme.colorScheme.primary
    val state = rememberLazyListState()

    LaunchedEffect(Unit) { feed.ensureLoaded() }

    // Load more as the end of the list comes near.
    LaunchedEffect(state, songs.size) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .map { it >= songs.size - 6 }
            .distinctUntilChanged()
            .collect { near -> if (near && songs.isNotEmpty()) feed.loadMore() }
    }

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
                Text(
                    "Tap any song to start the set from it.",
                    fontSize = 12.sp,
                    color = Dusk.Muted,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                )
            }
        }

        itemsIndexed(songs, key = { _, s -> s.url }) { index, song ->
            SongRow(
                song = song,
                onClick = {
                    vm.dj.start(index)
                    onOpenDeck()
                },
                actions = listOf(
                    "Don't play this song" to { hideSong(context, song) },
                    "Don't play this artist" to { hideArtist(context, song.artist) }
                )
            )
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
