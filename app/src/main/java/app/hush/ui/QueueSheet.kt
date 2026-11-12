package app.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hush.HushVm

/** "Up next" list plus the sleep timer, in one bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(vm: HushVm, onDismiss: () -> Unit) {
    val queue by vm.player.queue.collectAsState()
    val sleep by vm.player.sleep.collectAsState()
    val state by vm.player.state.collectAsState()
    val accent = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.index.coerceAtLeast(0))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Dusk.Surface,
        contentColor = Dusk.Text
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("Sleep timer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(15, 30, 45, 60).forEach { m ->
                    item {
                        HushChip("$m min", sleep.endsAtMs != null && sleep.minutes == m) {
                            vm.player.setSleepMinutes(m)
                        }
                    }
                }
                item { HushChip("End of song", sleep.endOfSong) { vm.player.setSleepEndOfSong() } }
                if (sleep.active) item { HushChip("Off", false) { vm.player.cancelSleep() } }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Up next", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = vm.player::clearUpcoming) { Text("Clear upcoming") }
            }

            LazyColumn(Modifier.heightIn(max = 360.dp), state = listState) {
                itemsIndexed(queue, key = { _, q -> "${q.index}-${q.mediaId}" }) { _, item ->
                    val current = item.index == state.index
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (current) accent.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { vm.player.jumpTo(item.index) }
                            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Art(item.art, 46.dp, radius = 12.dp, seed = item.title)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(
                                item.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (current) accent else Dusk.Text
                            )
                            Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Dusk.Muted)
                        }
                        if (!current) {
                            IconButton(onClick = { vm.player.removeAt(item.index) }) {
                                Icon(Icons.Filled.Close, "Remove from queue", tint = Dusk.Muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
