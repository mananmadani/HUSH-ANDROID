package app.hush.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hush.HushVm
import app.hush.Prefs

@Composable
fun SettingsScreen(vm: HushVm, onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = rememberHaptics()
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

        GroupLabel("APPEARANCE")
        SettingsCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Theme", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HushChip("Dark", !Prefs.light) { Prefs.setLightTheme(false) }
                    HushChip("Light", Prefs.light) { Prefs.setLightTheme(true) }
                }
            }
            CardDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("Accent", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Prefs.ACCENTS.forEachIndexed { i, accent ->
                        val selected = i == Prefs.accentIndex
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (Prefs.light) accent.light else accent.dark)
                                .then(if (selected) Modifier.border(2.5.dp, Dusk.Text, CircleShape) else Modifier)
                                .clickable(onClickLabel = accent.name) {
                                    haptic(Tap.Click)
                                    Prefs.setAccent(i)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(Icons.Filled.Check, accent.name, tint = Dusk.OnAccent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        GroupLabel("PRIVACY AND FEEL")
        SettingsCard {
            SwitchRow("Search suggestions", "Sends typing to YouTube", Prefs.suggestionsEnabled) {
                Prefs.setSuggestions(it)
            }
            CardDivider()
            SwitchRow("Haptic feedback", "Vibrate on taps", Prefs.haptics) { Prefs.enableHaptics(it) }
            CardDivider()
            SettingRow("Clear listening history") { vm.clearHistory() }
            CardDivider()
            SettingRow("Clear recent searches") { vm.clearRecentSearches() }
        }

        GroupLabel("UPDATES")
        SettingsCard {
            SwitchRow("Check for updates automatically", null, Prefs.autoUpdate) { Prefs.enableAutoUpdate(it) }
            CardDivider()
            SettingRow(
                "Check for updates now",
                trailing = if (vm.checkingUpdate) "Checking..." else null,
                chevron = !vm.checkingUpdate
            ) {
                vm.checkForUpdate(manual = true) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            }
            vm.updateAvailable?.let { release ->
                CardDivider()
                SettingRow("Download ${release.tag}", accent = true, chevron = true) {
                    openUrl(context, release.apkUrl ?: release.pageUrl)
                }
            }
        }

        GroupLabel("ABOUT")
        SettingsCard {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HushLogo(44.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text("Hush", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Version $version", fontSize = 12.sp, color = Dusk.Muted)
                }
            }
        }
        Text(
            "Developed by Manan Madani",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Dusk.Muted,
            modifier = Modifier.padding(start = 24.dp, top = 16.dp)
        )
        Text(
            "Free software (GPL-3.0). Inter typeface (OFL).",
            fontSize = 12.sp,
            color = Dusk.Muted,
            modifier = Modifier.padding(start = 24.dp, top = 2.dp)
        )

        // Keeps the last lines clear of the mini player.
        Spacer(Modifier.height(190.dp))
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        color = Dusk.Muted,
        modifier = Modifier.padding(start = 24.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Dusk.Raised)
            .border(1.dp, Dusk.Line, shape),
        content = content
    )
}

@Composable
private fun CardDivider() {
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Dusk.Line)
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { haptic(Tap.Click); onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Dusk.Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = { haptic(Tap.Click); onChange(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Dusk.OnAccent,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Dusk.Muted,
                uncheckedTrackColor = Dusk.Surface,
                uncheckedBorderColor = Dusk.Line
            ),
            modifier = Modifier.scale(0.85f)
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    trailing: String? = null,
    chevron: Boolean = false,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val haptic = rememberHaptics()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { haptic(Tap.Tick); onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (accent) MaterialTheme.colorScheme.primary else Dusk.Text,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) Text(trailing, fontSize = 13.sp, color = Dusk.Muted)
        if (chevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Dusk.Muted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
