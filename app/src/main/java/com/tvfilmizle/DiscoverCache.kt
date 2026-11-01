package com.tvfilmizle

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Kesfet sonuclari: 10 dk taze sayilir, sekmeye donunce aninda acilir. */
object DiscoverCache {

    private const val P = "discover_cache2"
    private const val K = "rows"
    private const val K_TIME = "time"
    private const val K_RUN = "run"
    private const val TTL = 5 * 60 * 1000L

    fun isFresh(ctx: Context): Boolean =
        System.currentTimeMillis() - prefs(ctx).getLong(K_TIME, 0L) < TTL

    /** Her taramada artan sayac: sitelerden farkli filmler secilir. */
    fun runIndex(ctx: Context): Int = prefs(ctx).getInt(K_RUN, 0)

    fun nextRun(ctx: Context): Int {
        val r = prefs(ctx).getInt(K_RUN, 0)
        prefs(ctx).edit().putInt(K_RUN, r + 1).apply()
        return r
    }

    fun load(ctx: Context): List<Panels.Row> {
        val raw = prefs(ctx).getString(K, null) ?: return emptyList()
        val out = ArrayList<Panels.Row>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Panels.Row(
                        o.optString("title"),
                        o.optString("url"),
                        o.optString("poster"),
                        o.optString("site"),
                        o.optString("meta"),
                        o.optBoolean("series"),
                        o.optBoolean("fresh")
                    )
                )
            }
        } catch (e: Exception) {
            // bozuk onbellek
        }
        return out
    }

    fun save(ctx: Context, rows: List<Panels.Row>) {
        val arr = JSONArray()
        rows.forEach {
            arr.put(
                JSONObject()
                    .put("title", it.title)
                    .put("url", it.url)
                    .put("poster", it.poster)
                    .put("site", it.site)
                    .put("meta", it.meta)
                    .put("series", it.isSeries)
                    .put("fresh", it.fresh)
            )
        }
        prefs(ctx).edit().putString(K, arr.toString())
            .putLong(K_TIME, System.currentTimeMillis()).apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE)
}
