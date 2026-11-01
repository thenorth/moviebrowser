package com.tvfilmizle

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Son izlenen filmler; ilk sirada en son acilan (kaldigi yerden devam). */
object History {

    data class Item(val title: String, val url: String, val poster: String, val site: String)

    private const val P = "history"
    private const val K = "items"
    private const val MAX = 40

    fun all(ctx: Context): List<Item> {
        val raw = prefs(ctx).getString(K, null) ?: return emptyList()
        val out = ArrayList<Item>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Item(o.optString("title"), o.optString("url"), o.optString("poster"), o.optString("site")))
            }
        } catch (e: Exception) {
            // bozuk kayit
        }
        return out
    }

    fun add(ctx: Context, title: String, url: String, poster: String?, site: String) {
        if (url.isEmpty()) return
        val list = all(ctx).filterNot { it.url == url }.toMutableList()
        list.add(0, Item(title, url, poster ?: "", site))
        while (list.size > MAX) list.removeAt(list.size - 1)
        save(ctx, list)
    }

    fun remove(ctx: Context, url: String) {
        save(ctx, all(ctx).filterNot { it.url == url })
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(K).apply()
    }

    /** Dizi basina son izlenen bolum etiketi. */
    fun lastEpisode(ctx: Context, seriesKey: String): String? =
        prefs(ctx).getString("series_" + seriesKey, null)

    fun setLastEpisode(ctx: Context, seriesKey: String, label: String) {
        prefs(ctx).edit().putString("series_" + seriesKey, label).apply()
    }

    // ------------------------------------------------------------ takip edilen diziler
    data class Follow(
        val key: String,
        val title: String,
        val url: String,
        val poster: String,
        val site: String,
        val label: String
    )

    fun followed(ctx: Context): List<Follow> {
        val raw = prefs(ctx).getString("followed", null) ?: return emptyList()
        val out = ArrayList<Follow>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Follow(
                        o.optString("key"),
                        o.optString("title"),
                        o.optString("url"),
                        o.optString("poster"),
                        o.optString("site"),
                        o.optString("label")
                    )
                )
            }
        } catch (e: Exception) {
            // bozuk kayit
        }
        return out
    }

    /** Dizi takibine ekler/gunceller (en son izlenen bolum etiketiyle). */
    fun follow(
        ctx: Context,
        key: String,
        title: String,
        url: String,
        poster: String?,
        site: String,
        label: String
    ) {
        if (key.isBlank()) return
        val list = followed(ctx).filterNot { it.key == key }.toMutableList()
        list.add(0, Follow(key, title, url, poster ?: "", site, label))
        while (list.size > 30) list.removeAt(list.size - 1)
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("key", it.key)
                    .put("title", it.title)
                    .put("url", it.url)
                    .put("poster", it.poster)
                    .put("site", it.site)
                    .put("label", it.label)
            )
        }
        prefs(ctx).edit().putString("followed", arr.toString()).apply()
    }

    fun unfollow(ctx: Context, key: String) {
        val list = followed(ctx).filterNot { it.key == key }
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("key", it.key)
                    .put("title", it.title)
                    .put("url", it.url)
                    .put("poster", it.poster)
                    .put("site", it.site)
                    .put("label", it.label)
            )
        }
        prefs(ctx).edit().putString("followed", arr.toString()).apply()
    }

    private fun save(ctx: Context, items: List<Item>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("title", it.title)
                    .put("url", it.url)
                    .put("poster", it.poster)
                    .put("site", it.site)
            )
        }
        prefs(ctx).edit().putString(K, arr.toString()).apply()
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE)
}
