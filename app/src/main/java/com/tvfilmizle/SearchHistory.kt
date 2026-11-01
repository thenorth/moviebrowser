package com.tvfilmizle

import android.content.Context
import org.json.JSONArray

/** Adres ve film arama gecmisi. */
object SearchHistory {

    private const val P = "search_history"
    private const val MAX = 6

    const val SEARCH = "search"
    const val URL = "url"

    fun all(ctx: Context, kind: String): List<String> {
        val raw = prefs(ctx).getString(kind, null) ?: return emptyList()
        val out = ArrayList<String>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotBlank()) out.add(s)
            }
        } catch (e: Exception) {
            // bozuk gecmis
        }
        return out
    }

    fun add(ctx: Context, kind: String, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val list = all(ctx, kind).filterNot { it.equals(t, true) }.toMutableList()
        list.add(0, t)
        while (list.size > MAX) list.removeAt(list.size - 1)
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs(ctx).edit().putString(kind, arr.toString()).apply()
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE)
}
