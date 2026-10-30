package com.tvfilmizle

import android.content.Context

object Prefs {
    private const val P = "app_prefs"

    fun desktopUa(ctx: Context): Boolean =
        ctx.applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE).getBoolean("desktop_ua", true)

    fun setDesktopUa(ctx: Context, value: Boolean) {
        ctx.applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE)
            .edit().putBoolean("desktop_ua", value).apply()
    }
}
