package app.hush.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hush.HushVm
import app.hush.Prefs
import app.hush.dj.DjDeckUi
import app.hush.dj.DjUi
import kotlin.math.roundToInt

@Composable
fun DjPlayerScreen(vm: HushVm, onClose: () -> Unit, onOpenQueue: () -> Unit) {
    val ui by vm.dj.ui.collectAsState()
    val haptic = rememberHaptics()
    val accent = MaterialTheme.colorScheme.primary

    // When the set ends, leave the deck screen.
    var wasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(ui.running) {
        if (ui.running) wasRunning = true else if (wasRunning) onClose()
    }

    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )
    val wave by spin.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )

    val glow = 0.10f + 0.16f * ui.pulse * (0.4f + 0.6f * ui.energy)
    Column(
        Modifier
            .fillMaxSize()
            .background(Dusk.Bg)
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(accent.copy(alpha = glow), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(540f, 500f),
                    radius = 1300f
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
      Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Filled.KeyboardArrowDown, "Close", onClose)
            Text(
                "DJ DECKS",
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold,
                color = Dusk.Muted,
                modifier = Modifier.padding(start = 14.dp).weight(1f)
            )
            if (ui.phaseName.isNotBlank()) {
                Text(
                    ui.phaseName,
                    fontSize = 12.sp,
                    letterSpacing = 1.6.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
            if (ui.beatsToMix in 1..8) {
                Text(
                    "DROP IN ${ui.beatsToMix}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp,
                    color = accent
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Deck(ui.decks[0], "A", spinning = ui.playing && ui.decks[0].audible, angle = angle, pulse = ui.pulse, modifier = Modifier.weight(1f))
            Deck(ui.decks[1], "B", spinning = ui.playing && ui.decks[1].audible, angle = angle, pulse = ui.pulse, modifier = Modifier.weight(1f))
        }

        DjBars(ui.pulse, ui.energy, ui.playing, wave)

        // Crossfader: shows where the sound is moving between the decks.
        val side = if (ui.master == 0) 0f else 1f
        val fader = if (ui.blend < 0f) side else if (ui.master == 0) ui.blend else 1f - ui.blend
        Label("CROSSFADER")
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            val knob = 44.dp
            val travel = maxWidth - knob
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Dusk.Raised)
            )
            Box(
                Modifier
                    .offset(x = travel * fader)
                    .align(Alignment.CenterStart)
                    .size(width = knob, height = 30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent)
            )
        }
        Text(
            when {
                ui.status.isNotBlank() -> ui.status
                ui.blend >= 0f -> "Mixing... ${(ui.blend * 100).roundToInt()}%"
                ui.secondsToMix >= 0 && ui.nextTitle != null -> "Next mix in ${ui.secondsToMix}s"
                else -> ""
            },
            fontSize = 12.sp,
            color = Dusk.Muted,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            textAlign = TextAlign.Center
        )
        if (ui.nextTitle != null) {
            Text(
                "Up next: ${ui.nextTitle}",
                fontSize = 12.sp,
                color = Dusk.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // Energy meter: how hard the set is going, and where it is aiming.
        Label("ENERGY")
        EnergyBar(ui.energy, ui.energyTarget)

        Label("SET MOOD")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Steady", "Build up", "Waves").forEachIndexed { i, label ->
                HushChip(label, Prefs.djShape == i) { Prefs.chooseDjShape(i) }
            }
        }

        Label("UP NEXT")
        val queue by vm.dj.queue.collectAsState()
        if (queue.isEmpty()) {
            Text("Nothing queued. Hush picks the next songs for you.", fontSize = 13.sp, color = Dusk.Muted)
        }
        queue.take(3).forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(s.thumb, 40.dp, radius = 10.dp, seed = s.title)
                Text(
                    s.title,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
        if (queue.size > 3) {
            Text("+${queue.size - 3} more", fontSize = 12.sp, color = Dusk.Muted, modifier = Modifier.padding(top = 4.dp))
        }
        Box(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Dusk.Line, RoundedCornerShape(24.dp))
                .clickable { haptic(Tap.Click); onOpenQueue() },
            contentAlignment = Alignment.Center
        ) {
            Text("Queue and search", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
      }

      // The controls stay on screen, so they are always within reach.
      Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(Icons.Filled.Stop, "Stop the set", { haptic(Tap.Heavy); vm.dj.stop() })
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable { haptic(Tap.Click); vm.dj.toggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (ui.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (ui.playing) "Pause" else "Play",
                    tint = Dusk.OnAccent,
                    modifier = Modifier.size(36.dp)
                )
            }
            RoundIconButton(Icons.Filled.SkipNext, "Skip to the next song", { haptic(Tap.Click); vm.dj.mixNow(true) })
        }
        val pill = RoundedCornerShape(26.dp)
        Box(
            Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(pill)
                .background(Dusk.Raised)
                .border(1.dp, accent, pill)
                .clickable { haptic(Tap.Heavy); vm.dj.mixNow(false) },
            contentAlignment = Alignment.Center
        ) {
            Text("MIX NOW", fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = accent)
        }
      }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.SemiBold,
        color = Dusk.Muted,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun Deck(deck: DjDeckUi, name: String, spinning: Boolean, angle: Float, pulse: Float, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(Dusk.Raised)
            .border(1.dp, if (deck.audible) accent else Dusk.Line, shape)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("DECK $name", fontSize = 11.sp, letterSpacing = 1.6.sp, color = Dusk.Muted, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier
                .padding(vertical = 10.dp)
                .size(110.dp)
                .graphicsLayer {
                    rotationZ = if (spinning) angle else 0f
                    val bump = if (deck.audible && spinning) 1f + 0.04f * pulse else 1f
                    scaleX = bump
                    scaleY = bump
                },
            contentAlignment = Alignment.Center
        ) {
            if (deck.loaded) {
                Art(deck.art, 110.dp, radius = 55.dp, seed = deck.title)
            } else {
                Box(Modifier.size(110.dp).clip(CircleShape).background(Dusk.Surface))
            }
            Box(Modifier.size(18.dp).clip(CircleShape).background(Dusk.Bg))
        }
        Text(
            if (deck.loaded) deck.title else "Empty",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            deck.artist,
            fontSize = 11.sp,
            color = Dusk.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (deck.loaded && deck.bpm > 0f) "${(deck.bpm * deck.speed).roundToInt()} BPM  ${speedText(deck.speed)}"
            else if (deck.loaded) "free tempo" else "",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until 4) {
                val on = deck.beat == i
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (on) accent else Dusk.Line)
                )
            }
        }
    }
}

private fun speedText(speed: Float): String {
    val pct = (speed - 1f) * 100f
    val r = (pct * 10f).roundToInt() / 10f
    return if (r >= 0f) "+$r%" else "$r%"
}

@Composable
private fun EnergyBar(energy: Float, target: Float) {
    val accent = MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(CircleShape)
            .background(Dusk.Raised)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(maxWidth * energy.coerceIn(0.03f, 1f))
                .clip(CircleShape)
                .background(accent)
        )
        Box(
            Modifier
                .offset(x = maxWidth * target.coerceIn(0f, 1f) - 1.dp)
                .fillMaxHeight()
                .width(2.dp)
                .background(Dusk.Text)
        )
    }
}

/** Bars that move with the beat and the energy. They follow the rhythm; they are not a true spectrum. */
@Composable
private fun DjBars(pulse: Float, energy: Float, playing: Boolean, wave: Float) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .height(54.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val count = 28
        for (i in 0 until count) {
            val swing = 0.5f + 0.5f * kotlin.math.sin(2.0 * Math.PI * (wave * (1 + i % 3) + i * 0.137)).toFloat()
            val live = if (playing) (0.18f + 0.82f * swing) * (0.30f + 0.70f * pulse) * (0.35f + 0.65f * energy) else 0.06f
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(live.coerceIn(0.06f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent.copy(alpha = 0.55f + 0.35f * live))
            )
        }
    }
}

/** The small bar above the navigation while a DJ set is running. */
@Composable
fun DjMiniBar(state: DjUi, onToggle: () -> Unit, onOpen: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(22.dp)
    val haptic = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Dusk.Surface)
            .background(accent.copy(alpha = 0.22f))
            .border(1.dp, Dusk.Line, shape)
            .clickable { haptic(Tap.Tick); onOpen() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(state.art, 44.dp, radius = 22.dp, seed = state.title)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                if (state.title.isNotBlank()) state.title else "DJ mode",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (state.status.isNotBlank()) state.status else "DJ set" +
                    (if (state.mixBpm > 0f) "  ${state.mixBpm.roundToInt()} BPM" else ""),
                fontSize = 12.sp,
                color = Dusk.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RoundIconButton(
            if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (state.playing) "Pause" else "Play",
            { haptic(Tap.Click); onToggle() }
        )
    }
}
