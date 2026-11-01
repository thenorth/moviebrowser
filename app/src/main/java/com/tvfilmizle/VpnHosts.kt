package com.tvfilmizle

import android.content.Context

/** Kullanici bu sitelerde VPN actiysa hatirlanir; sonraki acilista otomatik baglanir. */
object VpnHosts {

    private const val P = "vpn_hosts"
    private const val K = "hosts"

    fun all(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(K, emptySet())?.toSet() ?: emptySet()

    fun add(ctx: Context, host: String) {
        if (host.isBlank()) return
        val set = all(ctx).toMutableSet()
        if (set.add(host)) prefs(ctx).edit().putStringSet(K, set).apply()
    }

    fun remove(ctx: Context, host: String) {
        val set = all(ctx).toMutableSet()
        if (set.remove(host)) prefs(ctx).edit().putStringSet(K, set).apply()
    }

    fun has(ctx: Context, host: String): Boolean = all(ctx).contains(host)

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE)
}
