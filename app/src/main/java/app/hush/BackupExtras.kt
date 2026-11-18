package app.hush

import android.content.Context
import org.json.JSONObject

/**
 * The parts of a backup that live outside the library database: what Hush learned about your taste,
 * your listening stats and your settings. Together with the library this is everything, so moving
 * to a new phone keeps your recommendations.
 *
 * Not included on purpose: anything specific to one phone, such as the last update check.
 */
object BackupExtras {

    /** Everything in these is copied across and replaced on restore. */
    private val LEARNED = listOf("hush_stats", "hush_taste", "hush_affinity")

    /** Only these settings are copied, not the update-check bookkeeping. */
    private val SETTINGS = setOf("accent", "suggestions", "light", "auto_update", "haptics", "recent_searches")

    fun dump(context: Context): JSONObject {
        val stores = JSONObject()
        LEARNED.forEach { name ->
            stores.put(name, readStore(context, name, null))
        }
        stores.put("hush_prefs", readStore(context, "hush_prefs", SETTINGS))
        return stores
    }

    private fun readStore(context: Context, name: String, only: Set<String>?): JSONObject {
        val out = JSONObject()
        val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
        for ((key, value) in all) {
            if (only != null && key !in only) continue
            val entry = JSONObject()
            when (value) {
                is String -> entry.put("t", "s").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Boolean -> entry.put("t", "b").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                else -> continue
            }
            out.put(key, entry)
        }
        return out
    }

    /** Writes the backup's stores to this phone. Returns true if the backup had any. */
    fun restore(context: Context, stores: JSONObject?): Boolean {
        if (stores == null) return false
        var any = false
        LEARNED.forEach { name ->
            val data = stores.optJSONObject(name) ?: return@forEach
            writeStore(context, name, data, null, clear = true)
            any = true
        }
        stores.optJSONObject("hush_prefs")?.let {
            writeStore(context, "hush_prefs", it, SETTINGS, clear = false)
            any = true
        }
        // Load the restored values into the running app.
        Stats.init(context)
        Taste.init(context)
        Affinity.init(context)
        Prefs.init(context)
        return any
    }

    private fun writeStore(context: Context, name: String, data: JSONObject, only: Set<String>?, clear: Boolean) {
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
        if (clear) editor.clear()
        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (only != null && key !in only) continue
            val entry = data.optJSONObject(key) ?: continue
            when (entry.optString("t")) {
                "s" -> editor.putString(key, entry.optString("v"))
                "i" -> editor.putInt(key, entry.optInt("v"))
                "l" -> editor.putLong(key, entry.optLong("v"))
                "b" -> editor.putBoolean(key, entry.optBoolean("v"))
                "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
            }
        }
        editor.apply()
    }
}
