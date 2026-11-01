package com.tvfilmizle

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Kullanicinin kendi ekledigi siteler. */
object UserSites {

    private const val P = "user_sites"
    private const val K = "items"

    data class Item(val name: String, val url: String)

    fun all(ctx: Context): List<Item> {
        val raw = prefs(ctx).getString(K, null) ?: return emptyList()
        val out = ArrayList<Item>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name").trim()
                val url = o.optString("url").trim()
                if (name.isNotEmpty() && url.isNotEmpty()) out.add(Item(name, url))
            }
        } catch (e: Exception) {
            // bozuk kayit
        }
        return out
    }

    fun add(ctx: Context, name: String, url: String) {
        val list = all(ctx).filterNot { it.url == url }.toMutableList()
        list.add(0, Item(name, url))
        save(ctx, list)
    }

    fun remove(ctx: Context, url: String) {
        save(ctx, all(ctx).filterNot { it.url == url })
    }

    private fun save(ctx: Context, items: List<Item>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
        prefs(ctx).edit().putString(K, arr.toString()).apply()
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE)
}
