package app.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hush.HushVm
import app.hush.Prefs

@Composable
fun SettingsScreen(vm: HushVm, onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 14.dp))
        }

        Section("Theme")
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HushChip("Dark", !Prefs.light) { Prefs.setLightTheme(false) }
            HushChip("Light", Prefs.light) { Prefs.setLightTheme(true) }
        }

        Section("Accent colour")
        Prefs.ACCENTS.withIndex().chunked(4).forEach { rowItems ->
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowItems.forEach { (i, accent) ->
                    val selected = i == Prefs.accentIndex
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (Prefs.light) accent.light else accent.dark)
                            .then(if (selected) Modifier.border(3.dp, Dusk.Text, CircleShape) else Modifier)
                            .clickable(onClickLabel = accent.name) { Prefs.setAccent(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Icon(Icons.Filled.Check, accent.name, tint = Dusk.OnAccent)
                    }
                }
            }
        }

        Section("Privacy")
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Search suggestions", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Sends what you type to YouTube to suggest completions. Turn off to keep partial searches on your phone.",
                    fontSize = 12.sp,
                    color = Dusk.Muted
                )
            }
            Switch(
                checked = Prefs.suggestionsEnabled,
                onCheckedChange = { Prefs.setSuggestions(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Dusk.OnAccent,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        SettingsAction("Clear listening history", "Empties Recently played on Home") { vm.clearHistory() }
        SettingsAction("Clear recent searches", "Removes the list in Search") { vm.clearRecentSearches() }

        Section("About")
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HushLogo(44.dp)
            Text("Hush", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
        }
        Text(
            "Hush $version\nAn unofficial, ad-free, tracker-free music player. Not affiliated with YouTube or Google. " +
                "Free software under the GPL-3.0 licence, built on NewPipeExtractor. Typeface: Inter (SIL OFL).",
            fontSize = 13.sp,
            color = Dusk.Muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Text(
            "Developed by Manan Madani",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Dusk.Muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsAction(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, fontSize = 12.sp, color = Dusk.Muted)
    }
}
