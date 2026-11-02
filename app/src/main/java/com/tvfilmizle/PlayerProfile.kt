package com.tvfilmizle

import android.content.Context

/**
 * Site basina oynatici kesfi: hangi tam ekran / oynat ogesinin calistigi hatirlanir.
 * Yeni siteler ilk acilista genel kurallarla bulunur, sonra burada saklanir.
 */
object PlayerProfile {

    private const val P = "player_profile"

    fun fullscreen(ctx: Context, host: String): String? =
        prefs(ctx).getString("fs_$host", null)

    fun saveFullscreen(ctx: Context, host: String, selector: String) {
        if (host.isEmpty() || selector.isEmpty()) return
        prefs(ctx).edit().putString("fs_$host", selector).apply()
    }

    fun play(ctx: Context, host: String): String? =
        prefs(ctx).getString("play_$host", null)

    fun savePlay(ctx: Context, host: String, selector: String) {
        if (host.isEmpty() || selector.isEmpty()) return
        prefs(ctx).edit().putString("play_$host", selector).apply()
    }

    /** Ogrenilmis tum oynatici secicilerini sil. */
    fun clearAll(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE)
}
