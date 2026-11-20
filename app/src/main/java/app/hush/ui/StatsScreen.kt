package app.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hush.HushVm
import app.hush.Stats

private val DANGER = Color(0xFFE5484D)

private fun listenTime(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

private fun plays(n: Int) = if (n == 1) "1 play" else "$n plays"

@Composable
fun StatsScreen(vm: HushVm, onBack: () -> Unit) {
    var allTime by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    val haptic = rememberHaptics()
    val summary = remember(allTime, version) { Stats.summary(allTime) }
    val songs = summary.songs.take(10)
    val artists = summary.artists.take(5)
    val accent = MaterialTheme.colorScheme.primary

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
                    StatCard(listenTime(summary.listenMs), "listened", highlight = true)
                    StatCard("${summary.totalPlays}", "plays")
                    StatCard("${summary.songs.size}", "songs")
                }
            }

            item { SectionTitle("Top songs", Modifier.padding(start = 20.dp, top = 28.dp, bottom = 10.dp)) }
            item {
                Column(Modifier.statsCard()) {
                    songs.forEachIndexed { i, stat ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic(Tap.Tick)
                                    vm.player.play(songs.map { it.song }, i)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${i + 1}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (i == 0) accent else Dusk.Muted,
                                modifier = Modifier.width(28.dp)
                            )
                            Art(stat.song.thumb, 46.dp, radius = 12.dp, seed = stat.song.title)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    stat.song.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    stat.song.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 12.sp,
                                    color = Dusk.Muted
                                )
                            }
                            Text(plays(stat.plays), fontSize = 12.sp, color = Dusk.Muted, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            item { SectionTitle("Top artists", Modifier.padding(start = 20.dp, top = 28.dp, bottom = 10.dp)) }
            item {
                val most = (artists.firstOrNull()?.plays ?: 1).coerceAtLeast(1)
                Column(Modifier.statsCard()) {
                    artists.forEachIndexed { i, artist ->
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${i + 1}",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (i == 0) accent else Dusk.Muted,
                                    modifier = Modifier.width(28.dp)
                                )
                                Text(
                                    artist.name,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(plays(artist.plays), fontSize = 12.sp, color = Dusk.Muted)
                            }
                            Box(
                                Modifier
                                    .padding(start = 28.dp, top = 8.dp)
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Dusk.Line)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(artist.plays.toFloat() / most)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(accent)
                                )
                            }
                        }
                    }
                }
            }

            item {
                val shape = RoundedCornerShape(22.dp)
                Row(
                    Modifier
                        .padding(horizontal = 20.dp, vertical = 28.dp)
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(shape)
                        .border(1.dp, DANGER.copy(alpha = 0.55f), shape)
                        .background(DANGER.copy(alpha = 0.08f))
                        .clickable { haptic(Tap.Tick); confirmReset = true },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = DANGER, modifier = Modifier.size(20.dp))
                    Text(
                        "Reset stats",
                        color = DANGER,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset stats?") },
            text = { Text("Your listening stats on this phone will be cleared.") },
            confirmButton = {
                TextButton(onClick = {
                    haptic(Tap.Confirm)
                    Stats.clear()
                    version++
                    confirmReset = false
                }) { Text("Reset", color = DANGER) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            }
        )
    }
}

private fun Modifier.statsCard(): Modifier {
    val shape = RoundedCornerShape(22.dp)
    return this
        .padding(horizontal = 16.dp)
        .fillMaxWidth()
        .clip(shape)
        .background(Dusk.Raised)
        .border(1.dp, Dusk.Line, shape)
}

@Composable
private fun RowScope.StatCard(value: String, label: String, highlight: Boolean = false) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .weight(1f)
            .clip(shape)
            .background(if (highlight) MaterialTheme.colorScheme.primary else Dusk.Raised)
            .then(if (highlight) Modifier else Modifier.border(1.dp, Dusk.Line, shape))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = if (highlight) Dusk.OnAccent else Dusk.Text
        )
        Text(label, fontSize = 12.sp, color = if (highlight) Dusk.OnAccent.copy(alpha = 0.8f) else Dusk.Muted)
    }
}
