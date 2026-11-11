package app.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.hush.HushVm
import coil.compose.AsyncImage

@Composable
fun PlayerScreen(vm: HushVm, onClose: () -> Unit) {
    val state by vm.player.state.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val liked by remember(state.mediaId) {
        vm.repo.isLiked(state.mediaId ?: "")
    }.collectAsState(initial = false)

    var scrub by remember { mutableStateOf<Float?>(null) }
    var picking by remember { mutableStateOf(false) }

    val fraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    val shownMs = scrub?.let { (it * state.durationMs).toLong() } ?: state.positionMs

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close player") }
        }

        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (state.art != null) {
                AsyncImage(
                    model = state.art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { picking = true }) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist")
            }
            IconButton(onClick = { state.asSong()?.let { vm.setLiked(it, !liked) } }) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (liked) "Unlike" else "Like",
                    tint = if (liked) MaterialTheme.colorScheme.primary else Color.Unspecified
                )
            }
        }

        Slider(
            value = scrub ?: fraction.coerceIn(0f, 1f),
            onValueChange = { scrub = it },
            onValueChangeFinished = {
                scrub?.let { vm.player.seekTo((it * state.durationMs).toLong()) }
                scrub = null
            },
            enabled = state.durationMs > 0,
            modifier = Modifier.padding(top = 12.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(shownMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTime(state.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = vm.player::toggleShuffle) {
                Icon(
                    Icons.Filled.Shuffle, "Shuffle",
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = vm.player::previous, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, "Previous", modifier = Modifier.size(36.dp))
            }
            FilledIconButton(onClick = vm.player::toggle, modifier = Modifier.size(72.dp)) {
                if (state.loading && !state.playing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.playing) "Pause" else "Play",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            IconButton(onClick = vm.player::next, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, "Next", modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = vm.player::cycleRepeat) {
                val on = state.repeatMode != Player.REPEAT_MODE_OFF
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "Repeat",
                    tint = if (on) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (picking) {
        PlaylistPickerDialog(
            playlists = playlists,
            onPick = { id -> state.asSong()?.let { vm.addToPlaylist(id, it) }; picking = false },
            onCreate = { name -> vm.createPlaylist(name, state.asSong()); picking = false },
            onDismiss = { picking = false }
        )
    }
}
