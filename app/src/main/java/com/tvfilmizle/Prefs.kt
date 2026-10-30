package com.tvfilmizle

import android.content.Context

object Prefs {
    private const val P = "app_prefs"

    fun desktopUa(ctx: Context): Boolean = get(ctx).getBoolean("desktop_ua", true)

    fun setDesktopUa(ctx: Context, value: Boolean) {
        get(ctx).edit().putBoolean("desktop_ua", value).apply()
    }

    fun autoFullscreen(ctx: Context): Boolean = get(ctx).getBoolean("auto_fullscreen", true)

    fun setAutoFullscreen(ctx: Context, value: Boolean) {
        get(ctx).edit().putBoolean("auto_fullscreen", value).apply()
    }

    fun sleepMinutes(ctx: Context): Int = get(ctx).getInt("sleep_min", 0)

    fun setSleepMinutes(ctx: Context, value: Int) {
        get(ctx).edit().putInt("sleep_min", value).apply()
    }

    fun lastNotifiedVersion(ctx: Context): String = get(ctx).getString("notified_version", "") ?: ""

    fun setLastNotifiedVersion(ctx: Context, value: String) {
        get(ctx).edit().putString("notified_version", value).apply()
    }

    private fun get(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE)
}
