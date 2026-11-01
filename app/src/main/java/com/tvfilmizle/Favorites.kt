package com.tvfilmizle

import android.content.Context

/** Izgarada basa sabitlenen siteler (sira kullanici tarafindan degistirilebilir). */
object Favorites {

    private const val P = "favorites"
    private const val K = "urls"
    private const val K_ORDER = "order"

    fun all(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(K, emptySet())?.toSet() ?: emptySet()

    fun has(ctx: Context, url: String): Boolean = all(ctx).contains(url)

    /** Favori sirasi (bastan sona). */
    fun order(ctx: Context): List<String> =
        prefs(ctx).getString(K_ORDER, "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList()

    /** true = eklendi, false = cikarildi. */
    fun toggle(ctx: Context, url: String): Boolean {
        val set = all(ctx).toMutableSet()
        val order = order(ctx).toMutableList()
        val added = if (set.contains(url)) {
            set.remove(url)
            order.remove(url)
            false
        } else {
            set.add(url)
            order.remove(url)
            order.add(url)
            true
        }
        prefs(ctx).edit()
            .putStringSet(K, set)
            .putString(K_ORDER, order.joinToString("|"))
            .apply()
        return added
    }

    /** Favori sirasinda bir yukari/asagi tasir. */
    fun move(ctx: Context, url: String, up: Boolean) {
        val order = order(ctx).toMutableList()
        val i = order.indexOf(url)
        if (i < 0) return
        val j = if (up) i - 1 else i + 1
        if (j !in order.indices) return
        val tmp = order[i]
        order[i] = order[j]
        order[j] = tmp
        prefs(ctx).edit().putString(K_ORDER, order.joinToString("|")).apply()
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE)
}
