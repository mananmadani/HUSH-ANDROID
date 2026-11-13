package app.hush

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** An accent colour: a soft one for the dark theme and a deeper one for the light theme. */
data class Accent(val name: String, val dark: Color, val light: Color)

/** Small on-device settings. Nothing here ever leaves the phone. */
object Prefs {

    val ACCENTS: List<Accent> = listOf(
        Accent("Violet", Color(0xFFB8A9FF), Color(0xFF6B4EFF)),
        Accent("Peach", Color(0xFFFFB59A), Color(0xFFE2602F)),
        Accent("Mint", Color(0xFF8FE3CF), Color(0xFF0E8F73)),
        Accent("Sky", Color(0xFF7FB2FF), Color(0xFF2468D8)),
        Accent("Rose", Color(0xFFFF9EBB), Color(0xFFD6336C)),
        Accent("Gold", Color(0xFFFFD36B), Color(0xFFB77900)),
        Accent("Teal", Color(0xFF6FE0E8), Color(0xFF00899A))
    )

    private lateinit var sp: SharedPreferences

    var accentIndex by mutableStateOf(0)
        private set
    var suggestionsEnabled by mutableStateOf(true)
        private set
    var light by mutableStateOf(false)
        private set
    var autoUpdate by mutableStateOf(false)
        private set

    private val current: Accent
        get() = ACCENTS[accentIndex.coerceIn(0, ACCENTS.lastIndex)]

    /** The accent for the theme in use. */
    val accent: Color
        get() = if (light) current.light else current.dark

    /** The soft accent, for the widgets, which are always dark. */
    val accentDark: Color
        get() = current.dark

    fun init(context: Context) {
        sp = context.getSharedPreferences("hush_prefs", Context.MODE_PRIVATE)
        accentIndex = sp.getInt("accent", 0)
        suggestionsEnabled = sp.getBoolean("suggestions", true)
        light = sp.getBoolean("light", false)
        autoUpdate = sp.getBoolean("auto_update", false)
    }

    fun setAccent(index: Int) {
        accentIndex = index
        sp.edit().putInt("accent", index).apply()
    }

    fun setSuggestions(enabled: Boolean) {
        suggestionsEnabled = enabled
        sp.edit().putBoolean("suggestions", enabled).apply()
    }

    fun setLightTheme(value: Boolean) {
        light = value
        sp.edit().putBoolean("light", value).apply()
    }

    fun enableAutoUpdate(enabled: Boolean) {
        autoUpdate = enabled
        sp.edit().putBoolean("auto_update", enabled).apply()
    }

    fun lastCheckAt(): Long = sp.getLong("last_update_check", 0L)

    fun markChecked() {
        sp.edit().putLong("last_update_check", System.currentTimeMillis()).apply()
    }

    fun dismissedTag(): String = sp.getString("dismissed_update", "") ?: ""

    fun dismissTag(tag: String) {
        sp.edit().putString("dismissed_update", tag).apply()
    }

    fun loadRecentSearches(): List<String> =
        (sp.getString("recent_searches", "") ?: "").split('\n').filter { it.isNotBlank() }

    fun saveRecentSearches(list: List<String>) {
        sp.edit().putString("recent_searches", list.joinToString("\n")).apply()
    }
}
