package app.hush.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Asks GitHub whether a newer Hush release exists. It sends nothing about you or your phone,
 * only the plain request any browser would make. It runs only if you switch it on or tap
 * "Check for updates now" in Settings.
 */
object Updater {

    data class Release(val tag: String, val pageUrl: String, val apkUrl: String?, val notes: String)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/mananmadani/HUSH-ANDROID/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Hush")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub answered ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            val tag = json.optString("tag_name")
            if (tag.isBlank()) throw IOException("No release found")

            var apk: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apk = asset.optString("browser_download_url").ifBlank { null }
                        break
                    }
                }
            }
            Release(tag, json.optString("html_url"), apk, json.optString("body"))
        }
    }

    private fun parts(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V").split('.', '-').mapNotNull { it.toIntOrNull() }

    /** True if [tag] (for example "v1.0.9") is newer than [current] (for example "1.0.8"). */
    fun isNewer(tag: String, current: String): Boolean {
        val a = parts(tag)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
