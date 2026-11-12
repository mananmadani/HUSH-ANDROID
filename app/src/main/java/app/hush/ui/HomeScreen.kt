package app.hush.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hush.HushVm
import app.hush.Moods
import app.hush.Region
import app.hush.data.SongEntity
import java.util.Calendar

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 5 -> "Late night"
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        h < 22 -> "Good evening"
        else -> "Late night"
    }
}

@Composable
fun HomeScreen(vm: HushVm, onSettings: () -> Unit) {
    val recent by vm.recent.collectAsState()
    val shelves = vm.shelves
    val nothing = shelves.isEmpty() && recent.isEmpty()
    val mood = vm.mood
    val trending = shelves.firstOrNull { it.title.contains("rending", ignoreCase = true) }
        ?: shelves.firstOrNull { !it.title.startsWith("Because") }
    val hero = trending?.songs?.firstOrNull()
    val others = shelves.filter { it !== trending }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 190.dp)) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), fontSize = 13.sp, color = Dusk.Muted)
                    Text("Hush", style = MaterialTheme.typography.headlineMedium)
                }
                if (vm.homeLoading && !nothing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Box(Modifier.width(12.dp))
                }
                RoundIconButton(Icons.Filled.Refresh, "Refresh", vm::loadHome)
                Box(Modifier.width(8.dp))
                RoundIconButton(Icons.Filled.Settings, "Settings", onSettings)
            }
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { HushChip("All", mood == null) { vm.selectMood(null) } }
                items(Moods.ALL.size) { i ->
                    val m = Moods.ALL[i]
                    HushChip(m.name, mood == m) { vm.selectMood(m) }
                }
            }
        }

        if (mood != null) {
            item { SectionTitle("${mood.name} mix", Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp)) }
            if (vm.moodLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                itemsIndexed(vm.moodSongs, key = { _, s -> s.url }) { i, song ->
                    SongRow(
                        song,
                        onClick = { vm.player.play(vm.moodSongs, i) },
                        actions = listOf(
                            "Play next" to { vm.player.playNext(song) },
                            "Add to queue" to { vm.player.addToQueue(song) }
                        )
                    )
                }
            }
        } else if (nothing && vm.homeLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (nothing && vm.homeError != null) {
            item {
                Box(Modifier.fillMaxWidth().height(360.dp)) {
                    CenterMessage(vm.homeError ?: "") { Button(onClick = vm::loadHome) { Text("Retry") } }
                }
            }
        } else {
            if (hero != null && trending != null) {
                item {
                    Box(Modifier.padding(top = 20.dp)) {
                        HeroCard(hero) { vm.player.play(trending.songs, 0) }
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item { ShelfRow("Recently played", recent, vm) }
            }
            if (trending != null) {
                item { SectionTitle(trending.title, Modifier.padding(start = 20.dp, top = 24.dp, bottom = 6.dp)) }
                itemsIndexed(trending.songs.take(5), key = { _, s -> "rank" + s.url }) { i, song ->
                    RankedRow(i + 1, song) { vm.player.play(trending.songs, i) }
                }
            }
            others.forEach { shelf ->
                item { ShelfRow(shelf.title, shelf.songs, vm) }
            }
        }
        item {
            Text(
                "Developed by Manan Madani",
                fontSize = 12.sp,
                color = Dusk.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
            )
        }
    }
}

@Composable
private fun HeroCard(song: SongEntity, onPlay: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onPlay)
    ) {
        Art(song.thumb, Dp.Unspecified, Modifier.fillMaxSize(), radius = 0.dp, seed = song.title)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEB0B0B10))))
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 18.dp, end = 84.dp)
        ) {
            Text(
                "FEATURED · ${Region.countryName.uppercase()}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                letterSpacing = 1.sp
            )
            Text(
                song.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(song.artist, fontSize = 13.sp, color = Dusk.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, "Play", tint = Dusk.OnAccent, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun RankedRow(rank: Int, song: SongEntity, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$rank",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (rank == 1) MaterialTheme.colorScheme.primary else Dusk.Muted,
            modifier = Modifier.width(34.dp)
        )
        Art(song.thumb, 48.dp, radius = 12.dp, seed = song.title)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = Dusk.Muted)
        }
    }
}

@Composable
private fun ShelfRow(title: String, songs: List<SongEntity>, vm: HushVm) {
    SectionTitle(title, Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp))
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(songs, key = { _, s -> s.url }) { i, song ->
            Column(
                Modifier
                    .width(148.dp)
                    .clickable { vm.player.play(songs, i) }
            ) {
                Art(song.thumb, 148.dp, radius = 20.dp, seed = song.title)
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Dusk.Muted)
            }
        }
    }
}
