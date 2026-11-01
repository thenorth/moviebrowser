package com.tvfilmizle

import org.jsoup.Jsoup
import java.net.URL

/** Bir sitenin ana sayfasindaki film kartlarini ve kategori linklerini cikarir. */
object Scraper {

    data class Film(val title: String, val url: String, val poster: String)

    data class Page(
        val films: List<Film>,
        val categories: List<Pair<String, String>>
    )

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private const val CATEGORY_RX =
        "(?i)film|dizi|tür|yıl|yeni|popüler|keşfet|aksiyon|komedi|korku|dram"

    fun load(pageUrl: String, timeoutMs: Int = 12000, done: (Page) -> Unit) {
        Thread {
            val films = ArrayList<Film>()
            val cats = LinkedHashMap<String, String>()
            try {
                val doc = Jsoup.connect(pageUrl)
                    .userAgent(UA)
                    .header("Accept-Language", "tr-TR,tr;q=0.9")
                    .timeout(timeoutMs)
                    .followRedirects(true)
                    .get()

                val seen = HashSet<String>()

                // Asil izgara once: bazi temalar afisi li icinde, linkten ayri tutar.
                for (li in doc.select("li:has(img)")) {
                    val a = li.selectFirst("a[href]") ?: continue
                    val img = li.selectFirst("img") ?: continue
                    var title = li.selectFirst("a.tt, .film-title")?.text().orEmpty()
                    if (title.isBlank()) title = img.attr("alt")
                    if (title.isBlank()) title = a.text()
                    title = title.replace(Regex("\\s+"), " ").trim()
                    val poster = posterOf(img) ?: continue

                    addFilm(films, seen, title, a.absUrl("href"), poster, pageUrl)
                    if (films.size >= 90) break
                }

                // Sonra link icindeki gorseller (slider/one cikanlar).
                for (a in doc.select("a[href]")) {
                    val img = a.selectFirst("img") ?: continue
                    var title = img.attr("alt")
                    if (title.isBlank()) title = a.attr("title")
                    if (title.isBlank()) title = a.text()
                    title = title.replace(Regex("\\s+"), " ").trim()
                    val poster = posterOf(img) ?: continue

                    val href = a.absUrl("href")
                    addFilm(films, seen, title, href, poster, pageUrl)
                    if (films.size >= 90) break
                }

                for (a in doc.select("nav a[href], header a[href], .menu a[href], #menu a[href], .main-menu a[href]")) {
                    val t = a.text().replace(Regex("\\s+"), " ").trim()
                    if (t.length !in 2..20) continue
                    if (!Regex(CATEGORY_RX).containsMatchIn(t)) continue
                    val href = a.absUrl("href")
                    if (!href.startsWith("http")) continue
                    cats.putIfAbsent(t, href)
                }
            } catch (e: Exception) {
                // site korumali ya da erisilemiyor: bos sonuc
            }
            done(Page(films, cats.entries.map { it.key to it.value }.take(8)))
        }.start()
    }

    fun hostOf(url: String): String = try {
        URL(url).host
    } catch (e: Exception) {
        ""
    }

    /** Gorselin poster adresini secer; logo/platform gorsellerini eler. */
    private fun posterOf(img: org.jsoup.nodes.Element): String? {
        var p = img.attr("data-src")
        if (p.isBlank()) p = img.attr("data-lazy-src")
        if (p.isBlank()) p = img.attr("data-original")
        if (p.isBlank()) p = img.attr("src")
        if (p.startsWith("//")) p = "https:" + p
        if (!p.startsWith("http")) return null
        if (p.contains("logo", true) || p.contains("avatar", true)) return null
        if (p.contains("platform", true) || p.contains("/icon", true)) return null
        return p
    }

    /** Baslik/url kontrollerinden gecen filmi listeye ekler (ayni film iki kez girmez). */
    private fun addFilm(
        films: MutableList<Film>,
        seen: MutableSet<String>,
        title: String,
        href: String,
        poster: String,
        pageUrl: String
    ) {
        if (title.length !in 2..90) return
        if (title.contains("|")) return
        if (title.length > 55 && title.contains("izle", true)) return
        if (!href.startsWith("http") || href == pageUrl) return
        try {
            if (java.net.URL(href).path.length <= 1) return
        } catch (e: Exception) {
            return
        }
        if (href.contains("/category/") || href.contains("/kategori/")) return
        if (href.contains("/tag/") || href.contains("/page/")) return
        if (!seen.add(href)) return
        films.add(Film(title, href, poster))
    }
}
