package com.tvfilmizle

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Site listesi GitHub'daki sites.json'dan gelir; yoksa/cevrimdisiysa son bilinen liste (yoksa varsayilan) kullanilir.
 * Listeyi guncellemek icin: https://github.com/thenorth/moviebrowser/blob/main/sites.json
 */
object SiteRepository {

    const val URL = "https://raw.githubusercontent.com/thenorth/moviebrowser/main/sites.json"

    private const val PREFS = "tvfilm_sites"
    private const val K_JSON = "json"
    private const val K_TIME = "time"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun cached(ctx: Context): List<Site> = parse(prefs(ctx).getString(K_JSON, null)) ?: Sites.DEFAULT

    fun lastUpdate(ctx: Context): Long = prefs(ctx).getLong(K_TIME, 0L)

    /** Ag disinda calisir; sonuc ana thread'de doner. */
    fun refresh(ctx: Context, done: (sites: List<Site>?, changed: Boolean) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            var text: String? = null
            try {
                val c = URL(URL).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty("Cache-Control", "no-cache")
                if (c.responseCode in 200..299) {
                    text = c.inputStream.bufferedReader().use { it.readText() }
                }
                c.disconnect()
            } catch (e: Exception) {
                text = null
            }
            val parsed = parse(text)
            val changed = parsed != null && parsed != cached(app)
            if (parsed != null) {
                prefs(app).edit().putString(K_JSON, text).putLong(K_TIME, System.currentTimeMillis()).apply()
            }
            Handler(Looper.getMainLooper()).post { done(parsed, changed) }
        }.start()
    }

    private fun parse(json: String?): List<Site>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONObject(json).optJSONArray("sites") ?: return null
            val out = ArrayList<Site>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name").trim()
                val url = o.optString("url").trim()
                if (name.isNotEmpty() && url.isNotEmpty()) out.add(Site(name, url))
            }
            if (out.isEmpty()) null else out
        } catch (e: Exception) {
            null
        }
    }
}
