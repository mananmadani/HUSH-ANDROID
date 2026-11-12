package app.hush.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.hush.Prefs
import app.hush.R

/** The Dusk palette. The accent colour is chosen in Settings and lives in [Prefs]. */
object Dusk {
    val Bg = Color(0xFF0B0B10)
    val Surface = Color(0xFF14141C)
    val Raised = Color(0xFF1C1C27)
    val Line = Color(0x1AFFFFFF)
    val Text = Color(0xFFF2F0FA)
    val Muted = Color(0xFFA19DB4)
    val OnAccent = Color(0xFF1B1240)
    val Violet = Color(0xFF7C5CFF)
    val Pink = Color(0xFFFF8A9B)
}

/** Inter, bundled with the app (SIL Open Font License), so no font service is contacted. */
val HushFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold)
)

private fun hushTypography(): Typography {
    val base = Typography()
    fun TextStyle.inter() = copy(fontFamily = HushFont)
    return Typography(
        displayLarge = base.displayLarge.inter(),
        displayMedium = base.displayMedium.inter(),
        displaySmall = base.displaySmall.inter(),
        headlineLarge = base.headlineLarge.inter(),
        headlineMedium = base.headlineMedium.inter().copy(
            fontSize = 32.sp, lineHeight = 36.sp,
            fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp
        ),
        headlineSmall = base.headlineSmall.inter().copy(
            fontSize = 28.sp, lineHeight = 31.sp,
            fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp
        ),
        titleLarge = base.titleLarge.inter().copy(
            fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp
        ),
        titleMedium = base.titleMedium.inter().copy(
            fontSize = 19.sp, lineHeight = 24.sp,
            fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp
        ),
        titleSmall = base.titleSmall.inter(),
        bodyLarge = base.bodyLarge.inter(),
        bodyMedium = base.bodyMedium.inter(),
        bodySmall = base.bodySmall.inter(),
        labelLarge = base.labelLarge.inter(),
        labelMedium = base.labelMedium.inter(),
        labelSmall = base.labelSmall.inter()
    )
}

@Composable
fun HushTheme(content: @Composable () -> Unit) {
    val accent = Prefs.accent
    val colors = darkColorScheme(
        primary = accent,
        onPrimary = Dusk.OnAccent,
        secondary = Dusk.Muted,
        background = Dusk.Bg,
        onBackground = Dusk.Text,
        surface = Dusk.Bg,
        onSurface = Dusk.Text,
        surfaceVariant = Dusk.Raised,
        onSurfaceVariant = Dusk.Muted,
        surfaceContainer = Dusk.Surface,
        surfaceContainerHigh = Dusk.Raised,
        surfaceContainerHighest = Dusk.Raised,
        outline = Dusk.Line,
        error = Color(0xFFFF8A8A)
    )
    val typography = remember { hushTypography() }
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
