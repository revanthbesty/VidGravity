package com.vidgravity.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ShareInboxItem(val id: String, val url: String, val title: String, val savedAt: Long)

object ShareInboxStore {
    private const val PREFS = "vg-share-inbox"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 100

    @Synchronized
    fun list(context: Context): List<ShareInboxItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val url = obj.optString("url").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                ShareInboxItem(id, url, obj.optString("title", url), obj.optLong("savedAt"))
            }.sortedByDescending { it.savedAt }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(context: Context, url: String, title: String): ShareInboxItem {
        require(url.startsWith("https://") && url.length <= 8192) { "Only normalized public HTTPS links can be saved." }
        val existing = list(context).firstOrNull { it.url == url }
        if (existing != null) return existing
        val item = ShareInboxItem(java.util.UUID.randomUUID().toString(), url, title.take(180).ifBlank { url }, System.currentTimeMillis())
        val array = JSONArray()
        (list(context).take(MAX_ITEMS - 1) + item).forEach { row ->
            array.put(JSONObject().put("id", row.id).put("url", row.url).put("title", row.title).put("savedAt", row.savedAt))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).apply()
        return item
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        val array = JSONArray()
        list(context).filterNot { it.id == id }.forEach { row ->
            array.put(JSONObject().put("id", row.id).put("url", row.url).put("title", row.title).put("savedAt", row.savedAt))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).apply()
    }
}
