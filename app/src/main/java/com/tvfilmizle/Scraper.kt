package com.tvfilmizle

import org.jsoup.Jsoup
import java.net.URL

/** Bir sitenin ana sayfasindaki film kartlarini cikarir (baslik + afis + link). */
object Scraper {

    data class Film(val title: String, val url: String, val poster: String)

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    fun load(pageUrl: String, done: (List<Film>) -> Unit) {
        Thread {
            val out = ArrayList<Film>()
            try {
                val doc = Jsoup.connect(pageUrl)
                    .userAgent(UA)
                    .header("Accept-Language", "tr-TR,tr;q=0.9")
                    .timeout(12000)
                    .followRedirects(true)
                    .get()

                val seen = HashSet<String>()
                for (a in doc.select("a[href]")) {
                    val img = a.selectFirst("img") ?: continue
                    var title = img.attr("alt")
                    if (title.isBlank()) title = a.attr("title")
                    if (title.isBlank()) title = a.text()
                    title = title.replace(Regex("\\s+"), " ").trim()
                    if (title.length < 2 || title.length > 90) continue

                    var poster = img.attr("data-src")
                    if (poster.isBlank()) poster = img.attr("data-lazy-src")
                    if (poster.isBlank()) poster = img.attr("data-original")
                    if (poster.isBlank()) poster = img.attr("src")
                    if (poster.startsWith("//")) poster = "https:" + poster
                    if (!poster.startsWith("http")) continue
                    if (poster.contains("logo", true) || poster.contains("avatar", true)) continue

                    val href = a.absUrl("href")
                    if (!href.startsWith("http")) continue
                    if (href == pageUrl) continue
                    if (href.contains("/category/") || href.contains("/tag/") || href.contains("/page/")) continue
                    if (!seen.add(href)) continue

                    out.add(Film(title, href, poster))
                    if (out.size >= 80) break
                }
            } catch (e: Exception) {
                // site korumali ya da erisilemiyor: bos liste
            }
            done(out)
        }.start()
    }

    fun hostOf(url: String): String = try {
        URL(url).host
    } catch (e: Exception) {
        ""
    }
}
