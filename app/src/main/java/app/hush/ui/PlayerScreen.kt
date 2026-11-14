package app.hush.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import app.hush.HushVm
import app.hush.Prefs
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlayerScreen(vm: HushVm, onClose: () -> Unit) {
    val context = LocalContext.current
    val state by vm.player.state.collectAsState()
    val sleep by vm.player.sleep.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val liked by remember(state.mediaId) {
        vm.repo.isLiked(state.mediaId ?: "")
    }.collectAsState(initial = false)
    val accent = MaterialTheme.colorScheme.primary
    val openArtist = LocalOpenArtist.current
    val haptic = rememberHaptics()

    var scrub by remember { mutableStateOf<Float?>(null) }
    var picking by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    // Colour wash taken from the cover art.
    var target by remember { mutableStateOf(Dusk.Violet) }
    LaunchedEffect(state.art) {
        val url = state.art ?: return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(96).build()
        val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        if (bitmap != null) {
            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap).maximumColorCount(12).generate()
            }
            val rgb = palette.getDarkVibrantColor(
                palette.getDarkMutedColor(palette.getMutedColor(android.graphics.Color.rgb(124, 92, 255)))
            )
            target = Color(rgb)
        }
    }
    val wash by animateColorAsState(target, tween(700), label = "wash")

    val fraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    val shownMs = scrub?.let { (it * state.durationMs).toLong() } ?: state.positionMs

    Box(
        Modifier
            .fillMaxSize()
            .background(Dusk.Bg)
            .background(Brush.verticalGradient(listOf(wash.copy(alpha = if (Prefs.light) 0.4f else 0.65f), Dusk.Bg)))
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 160f) onClose() },
                    onVerticalDrag = { _, delta -> total += delta }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIconButton(Icons.Filled.KeyboardArrowDown, "Close player", onClose)
                Text(
                    "NOW PLAYING",
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Dusk.Muted,
                    letterSpacing = 1.5.sp
                )
                RoundIconButton(Icons.AutoMirrored.Filled.QueueMusic, "Queue", { showQueue = true })
            }

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                Art(state.art, side, radius = 32.dp, seed = state.title)
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        state.artist,
                        modifier = Modifier.clickable(enabled = state.artist.isNotBlank()) { openArtist(state.artist) },
                        fontSize = 15.sp,
                        color = Dusk.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { haptic(Tap.Tick); picking = true }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist")
                }
                IconButton(onClick = {
                    haptic(Tap.Confirm)
                    state.asSong()?.let { vm.setLiked(it, !liked) }
                }) {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (liked) "Unlike" else "Like",
                        tint = if (liked) accent else Dusk.Text
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
                colors = SliderDefaults.colors(
                    thumbColor = Dusk.Text,
                    activeTrackColor = Dusk.Text,
                    inactiveTrackColor = Dusk.Text.copy(alpha = 0.2f)
                ),
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(shownMs), fontSize = 12.sp, color = Dusk.Muted)
                Text(formatTime(state.durationMs), fontSize = 12.sp, color = Dusk.Muted)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { haptic(Tap.Click); vm.player.toggleShuffle() }) {
                    Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (state.shuffle) accent else Dusk.Muted)
                }
                IconButton(onClick = { haptic(Tap.Heavy); vm.player.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Previous", modifier = Modifier.size(36.dp))
                }
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .clickable(onClickLabel = if (state.playing) "Pause" else "Play") {
                            haptic(Tap.Heavy)
                            vm.player.toggle()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (state.loading && !state.playing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp,
                            color = Dusk.OnAccent
                        )
                    } else {
                        Icon(
                            if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.playing) "Pause" else "Play",
                            tint = Dusk.OnAccent,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                IconButton(onClick = { haptic(Tap.Heavy); vm.player.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, "Next", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { haptic(Tap.Click); vm.player.cycleRepeat() }) {
                    val on = state.repeatMode != Player.REPEAT_MODE_OFF
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        "Repeat",
                        tint = if (on) accent else Dusk.Muted
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    state.mediaId?.let { shareSong(context, state.title, state.artist, it) }
                }) { Icon(Icons.Filled.Share, "Share", tint = Dusk.Muted) }
                IconButton(onClick = { haptic(Tap.Tick); showQueue = true }) {
                    Icon(Icons.Filled.Bedtime, "Sleep timer", tint = if (sleep.active) accent else Dusk.Muted)
                }
            }
        }
    }

    if (picking) {
        PlaylistPickerDialog(
            playlists = playlists,
            onPick = { id -> haptic(Tap.Confirm); state.asSong()?.let { vm.addToPlaylist(id, it) }; picking = false },
            onCreate = { name -> haptic(Tap.Confirm); vm.createPlaylist(name, state.asSong()); picking = false },
            onDismiss = { picking = false }
        )
    }
    if (showQueue) QueueSheet(vm) { showQueue = false }
}
