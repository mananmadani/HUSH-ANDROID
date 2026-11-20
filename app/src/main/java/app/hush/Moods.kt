package app.hush

import androidx.compose.ui.graphics.Color

/** A mood: the chip on Home and the tile in Search both use these. */
data class Mood(val name: String, val query: String, val from: Color, val to: Color)

object Moods {
    val ALL: List<Mood> = listOf(
        Mood("Energise", "energetic upbeat songs", Color(0xFFFF7A59), Color(0xFFFFC26B)),
        Mood("Chill", "chill songs", Color(0xFF1F6F78), Color(0xFF9AE3D0)),
        Mood("Focus", "focus instrumental study music", Color(0xFF3B4A9B), Color(0xFF8FA7FF)),
        Mood("Party", "party songs", Color(0xFFC23B8A), Color(0xFFFF8A9B)),
        Mood("Romance", "romantic songs", Color(0xFF7A1F4F), Color(0xFFE26A8D)),
        Mood("Sleep", "sleep calm music", Color(0xFF1B1F4A), Color(0xFF5B6BC0))
    )
}
