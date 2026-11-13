package app.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hush.HushVm
import app.hush.Stats

private fun listenTime(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

@Composable
fun StatsScreen(vm: HushVm, onBack: () -> Unit) {
    var allTime by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    val summary = remember(allTime, version) { Stats.summary(allTime) }
    val songs = summary.songs.take(10)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 190.dp)) {
        item {
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Text(
                    "Listening stats",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 14.dp)
                )
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HushChip("This month", !allTime) { allTime = false }
                HushChip("All time", allTime) { allTime = true }
            }
        }

        if (summary.totalPlays == 0) {
            item {
                Box(Modifier.fillMaxWidth().height(260.dp)) {
                    CenterMessage("Nothing here yet. Play some songs and your stats will appear. They stay on your phone.")
                }
            }
        } else {
            item {
                Row(
                    Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(listenTime(summary.listenMs), "listened")
                    StatCard("${summary.totalPlays}", "plays")
                    StatCard("${summary.songs.size}", "songs")
                }
            }
            item { SectionTitle("Top songs", Modifier.padding(start = 20.dp, top = 26.dp, bottom = 6.dp)) }
            itemsIndexed(songs, key = { _, s -> "top" + s.song.url }) { i, stat ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.player.play(songs.map { it.song }, i) }
                        .padding(horizontal = 20.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${i + 1}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (i == 0) MaterialTheme.colorScheme.primary else Dusk.Muted,
                        modifier = Modifier.width(32.dp)
                    )
                    Art(stat.song.thumb, 48.dp, radius = 12.dp, seed = stat.song.title)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(stat.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${stat.song.artist} · ${stat.plays} plays",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp,
                            color = Dusk.Muted
                        )
                    }
                }
            }
            item { SectionTitle("Top artists", Modifier.padding(start = 20.dp, top = 26.dp, bottom = 6.dp)) }
            itemsIndexed(summary.artists.take(5), key = { _, a -> "artist" + a.name }) { i, artist ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${i + 1}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (i == 0) MaterialTheme.colorScheme.primary else Dusk.Muted,
                        modifier = Modifier.width(32.dp)
                    )
                    Text(
                        artist.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("${artist.plays} plays", fontSize = 13.sp, color = Dusk.Muted)
                }
            }
            item {
                TextButton(
                    onClick = { Stats.clear(); version++ },
                    modifier = Modifier.padding(start = 12.dp, top = 16.dp)
                ) { Text("Reset stats") }
            }
        }
    }
}

@Composable
private fun RowScope.StatCard(value: String, label: String) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(Dusk.Raised)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(label, fontSize = 12.sp, color = Dusk.Muted)
    }
}
