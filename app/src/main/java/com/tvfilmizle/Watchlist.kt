package com.tvfilmizle

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Tarayicidan/izgaradan eklenen film listesi (baslik + url + site + afis). */
object Watchlist {

    data class Item(
        val title: String,
        val url: String,
        val site: String,
        val poster: String? = null
    )

    private const val P = "watchlist"

    fun all(ctx: Context): List<Item> {
        val raw = prefs(ctx).getString("items", null) ?: return emptyList()
        val out = ArrayList<Item>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Item(
                        o.optString("title"),
                        o.optString("url"),
                        o.optString("site"),
                        o.optString("poster").ifBlank { null }
                    )
                )
            }
        } catch (e: Exception) {
            // bozuk kayit: bos liste
        }
        return out
    }

    fun add(ctx: Context, item: Item) {
        val list = all(ctx).toMutableList()
        list.removeAll { it.url == item.url }
        list.add(0, item)
        save(ctx, list)
    }

    fun remove(ctx: Context, url: String) {
        save(ctx, all(ctx).filterNot { it.url == url })
    }

    fun has(ctx: Context, url: String): Boolean = all(ctx).any { it.url == url }

    private fun save(ctx: Context, items: List<Item>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("title", it.title)
                    .put("url", it.url)
                    .put("site", it.site)
                    .put("poster", it.poster ?: "")
            )
        }
        prefs(ctx).edit().putString("items", arr.toString()).apply()
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE)
}
