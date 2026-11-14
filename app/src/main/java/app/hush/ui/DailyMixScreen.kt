package app.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hush.HushVm

/** The songs in today's Daily Mix, with Play and Shuffle. */
@Composable
fun DailyMixScreen(vm: HushVm, onBack: () -> Unit) {
    val songs = vm.dailyMix
    val haptic = rememberHaptics()
    val accent = MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Column(Modifier.padding(start = 14.dp)) {
                Text("Daily Mix", style = MaterialTheme.typography.titleLarge)
                Text("${songs.size} songs", fontSize = 12.sp, color = Dusk.Muted)
            }
        }

        if (songs.isEmpty()) {
            CenterMessage("Like or play a few songs and your Daily Mix will appear here.")
        } else {
            SongList(
                songs = songs,
                vm = vm,
                modifier = Modifier.weight(1f),
                header = {
                    item {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val pill = RoundedCornerShape(22.dp)
                            Row(
                                Modifier
                                    .height(46.dp)
                                    .clip(pill)
                                    .background(accent)
                                    .clickable { haptic(Tap.Heavy); vm.player.play(songs, 0) }
                                    .padding(horizontal = 22.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, tint = Dusk.OnAccent, modifier = Modifier.size(22.dp))
                                Text(
                                    "Play",
                                    color = Dusk.OnAccent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                            Row(
                                Modifier
                                    .height(46.dp)
                                    .clip(pill)
                                    .background(Dusk.Raised)
                                    .border(1.dp, Dusk.Line, pill)
                                    .clickable { haptic(Tap.Click); vm.player.play(songs, 0, shuffle = true) }
                                    .padding(horizontal = 22.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Shuffle, null, tint = Dusk.Text, modifier = Modifier.size(20.dp))
                                Text(
                                    "Shuffle",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}
