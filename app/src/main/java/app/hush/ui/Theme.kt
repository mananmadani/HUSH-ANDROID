package app.hush.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HushColors = darkColorScheme(
    primary = Color(0xFFB8A9FF),
    onPrimary = Color(0xFF1B1240),
    secondary = Color(0xFF9A93B8),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFEDEBF5),
    surface = Color(0xFF0B0B0D),
    onSurface = Color(0xFFEDEBF5),
    surfaceVariant = Color(0xFF1A1A20),
    onSurfaceVariant = Color(0xFF9E9BAA),
    surfaceContainer = Color(0xFF131317),
    surfaceContainerHigh = Color(0xFF1A1A20),
    error = Color(0xFFFF8A8A)
)

@Composable
fun HushTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HushColors, content = content)
}
