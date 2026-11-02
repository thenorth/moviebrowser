package com.tvfilmizle

import android.content.Context
import org.jsoup.Jsoup
import java.net.URL

/**
 * Sitelerin kendi arama sayfalari.
 * Once sitenin arama formundan adres kalibi cikarilir ve site basina hatirlanir;
 * bulunamazsa yaygin kaliblar denenir.
 */
object SiteSearch {

    private const val P = "site_search"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** Yaygin arama kaliblari: %s yerine sorgu yazilir. */
    fun generic(base: String): List<String> {
        val root = base.trimEnd('/')
        return listOf("$root/?s=%s", "$root/arama/?q=%s", "$root/?q=%s")
    }

    fun template(ctx: Context, host: String): String? = try {
        prefs(ctx).getString(host, null)?.takeIf { it.contains("%s") }
    } catch (e: ClassCastException) {
        // Eski surum kalibi sayi olarak saklamisti.
        null
    }

    fun saveTemplate(ctx: Context, host: String, template: String) {
        prefs(ctx).edit().putString(host, template).apply()
    }

    fun forget(ctx: Context, host: String) {
        prefs(ctx).edit().remove(host).apply()
    }

    /** Sitenin arama formundan "adres?alan=%s" kalibi cikarir. */
    fun formTemplate(base: String): String? = try {
        val doc = Jsoup.connect(base).userAgent(UA).timeout(8000).get()
        var best: String? = null
        var bestScore = 0
        for (f in doc.select("form")) {
            if (f.attr("method").equals("post", true)) continue
            val input = f.select("input[name]").firstOrNull() ?: continue
            val name = input.attr("name").trim()
            if (name.isEmpty() || name.contains("[") || name.contains("]")) continue
            var score = 0
            if (name.lowercase() in setOf("s", "q", "arama", "search", "sorgu", "keyword", "kelime")) score += 3
            if (input.attr("type").equals("search", true)) score += 2
            val action = f.attr("action")
            if (action.contains("ara", true) || action.contains("search", true)) score += 1
            if (score <= bestScore) continue
            val abs = try {
                URL(URL(base), action.ifBlank { base }).toString()
            } catch (e: Exception) {
                base
            }
            val sep = if (abs.contains("?")) "&" else "?"
            best = abs + sep + name + "=%s"
            bestScore = score
        }
        best
    } catch (e: Exception) {
        null
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(P, Context.MODE_PRIVATE)
}
