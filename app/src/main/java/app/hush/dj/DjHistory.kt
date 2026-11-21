package app.hush.dj

import android.content.Context
import android.content.SharedPreferences

/**
 * Remembers which songs DJ mode played lately (on the phone only) so a new set does not
 * start with the same songs again.
 */
object DjHistory {
    private const val LIMIT = 400
    private var sp: SharedPreferences? = null
    private val urls = LinkedHashSet<String>()

    @Synchronized
    fun init(context: Context) {
        sp = context.getSharedPreferences("hush_dj", Context.MODE_PRIVATE)
        urls.clear()
        val saved = sp?.getString("played", "") ?: ""
        saved.split("\n").filter { it.isNotBlank() }.forEach { urls.add(it) }
    }

    @Synchronized
    fun has(url: String): Boolean = url in urls

    @Synchronized
    fun add(url: String) {
        urls.remove(url)
        urls.add(url)
        while (urls.size > LIMIT) {
            val oldest = urls.iterator().next()
            urls.remove(oldest)
        }
        sp?.edit()?.putString("played", urls.joinToString("\n"))?.apply()
    }

    @Synchronized
    fun clear() {
        urls.clear()
        sp?.edit()?.remove("played")?.apply()
    }
}
