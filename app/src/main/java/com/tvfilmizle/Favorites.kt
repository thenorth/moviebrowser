package com.tvfilmizle

import android.content.Context

/** Izgarada basa sabitlenen siteler. */
object Favorites {

    private const val P = "favorites"
    private const val K = "urls"

    fun all(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(K, emptySet())?.toSet() ?: emptySet()

    fun has(ctx: Context, url: String): Boolean = all(ctx).contains(url)

    /** true = eklendi, false = cikarildi. */
    fun toggle(ctx: Context, url: String): Boolean {
        val set = all(ctx).toMutableSet()
        val added = if (set.contains(url)) {
            set.remove(url)
            false
        } else {
            set.add(url)
            true
        }
        prefs(ctx).edit().putStringSet(K, set).apply()
        return added
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE)
}
