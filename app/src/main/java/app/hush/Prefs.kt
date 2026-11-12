package app.hush

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Small on-device settings. Nothing here ever leaves the phone. */
object Prefs {

    val ACCENTS: List<Pair<String, Color>> = listOf(
        "Violet" to Color(0xFFB8A9FF),
        "Peach" to Color(0xFFFFB59A),
        "Mint" to Color(0xFF8FE3CF),
        "Sky" to Color(0xFF7FB2FF)
    )

    private lateinit var sp: SharedPreferences

    var accentIndex by mutableStateOf(0)
        private set
    var suggestionsEnabled by mutableStateOf(true)
        private set

    val accent: Color
        get() = ACCENTS[accentIndex.coerceIn(0, ACCENTS.lastIndex)].second

    fun init(context: Context) {
        sp = context.getSharedPreferences("hush_prefs", Context.MODE_PRIVATE)
        accentIndex = sp.getInt("accent", 0)
        suggestionsEnabled = sp.getBoolean("suggestions", true)
    }

    fun setAccent(index: Int) {
        accentIndex = index
        sp.edit().putInt("accent", index).apply()
    }

    fun setSuggestions(enabled: Boolean) {
        suggestionsEnabled = enabled
        sp.edit().putBoolean("suggestions", enabled).apply()
    }

    fun loadRecentSearches(): List<String> =
        (sp.getString("recent_searches", "") ?: "").split('\n').filter { it.isNotBlank() }

    fun saveRecentSearches(list: List<String>) {
        sp.edit().putString("recent_searches", list.joinToString("\n")).apply()
    }
}
