package com.tvfilmizle

import android.content.Context

/**
 * Bir sitenin bu agda acilip acilmadigini yerelde hatirlar.
 * 2 basarisiz denemeden sonra "engelli" sayilir; basarili yuklemede isaret kalkar.
 */
object SiteHealth {

    private const val PREFS = "site_health"
    private const val WEEK = 7L * 24 * 60 * 60 * 1000

    fun isBlocked(ctx: Context, host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fails = p.getInt("f_" + host, 0)
        val whenTime = p.getLong("t_" + host, 0L)
        if (fails < 2) return false
        if (System.currentTimeMillis() - whenTime > WEEK) {
            p.edit().remove("f_" + host).remove("t_" + host).apply()
            return false
        }
        return true
    }

    fun fail(ctx: Context, host: String?) {
        if (host.isNullOrEmpty()) return
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val n = p.getInt("f_" + host, 0) + 1
        p.edit().putInt("f_" + host, n).putLong("t_" + host, System.currentTimeMillis()).apply()
    }

    fun ok(ctx: Context, host: String?) {
        if (host.isNullOrEmpty()) return
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.contains("f_" + host)) p.edit().remove("f_" + host).remove("t_" + host).apply()
    }
}
