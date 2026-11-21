package app.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.hush.dj.DjStats

/** DJ mode's own history and numbers. They are kept apart from normal listening. */
@Composable
fun DjStatsScreen(vm: HushVm, onBack: () -> Unit, onOpenDeck: () -> Unit) {
    val s by DjStats.summary.collectAsState()
    val ui by vm.dj.ui.collectAsState()
    val context = LocalContext.current

    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Text(
                    "DJ history and stats",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 14.dp)
                )
            }
            Text(
                "Kept apart from your normal listening history and stats.",
                fontSize = 12.sp,
                color = Dusk.Muted,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard("Sets", s.sets.toString(), Modifier.weight(1f))
                StatCard("Songs played", s.plays.toString(), Modifier.weight(1f))
                StatCard("Time", djTime(s.listenMs), Modifier.weight(1f))
            }
        }

        if (s.plays == 0) {
            item {
                Text(
                    "Nothing yet. Start a DJ set and it will show up here.",
                    color = Dusk.Muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                )
            }
        }

        if (s.topArtists.isNotEmpty()) {
            item { Header("TOP ARTISTS") }
            items(s.topArtists, key = { "a" + it.first }) { (name, plays) ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(if (plays == 1) "1 play" else "$plays plays", fontSize = 13.sp, color = Dusk.Muted)
                }
            }
        }

        if (s.topSongs.isNotEmpty()) {
            item { Header("TOP SONGS") }
            items(s.topSongs, key = { "t" + it.song.url }) { e ->
                DjSongRow(vm, e.song, ui.running, onOpenDeck, context)
            }
        }

        if (s.recent.isNotEmpty()) {
            item { Header("RECENTLY PLAYED") }
            items(s.recent, key = { "r" + it.song.url }) { e ->
                DjSongRow(vm, e.song, ui.running, onOpenDeck, context)
            }
        }
        item { Spacer(Modifier.height(190.dp)) }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        letterSpacing = 1.4.sp,
        fontWeight = FontWeight.SemiBold,
        color = Dusk.Muted,
        modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 24.dp, bottom = 6.dp)
    )
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(Dusk.Raised)
            .border(1.dp, Dusk.Line, shape)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = Dusk.Muted, modifier = Modifier.padding(top = 2.dp))
    }
}

private fun djTime(ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}
