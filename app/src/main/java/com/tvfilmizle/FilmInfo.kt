package com.tvfilmizle

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Film ozeti: Turkce Vikipedi (ucretsiz, anahtar gerekmez). */
object FilmInfo {

    private val cache = HashMap<String, String?>()

    /** Bloklayici; arka planda cagrilmali. Bos sonuc = bulunamadi. */
    fun load(rawTitle: String): String? {
        val title = clean(rawTitle)
        if (title.isEmpty()) return null
        synchronized(cache) { if (cache.containsKey(title)) return cache[title] }

        val text = try {
            find(title)
        } catch (e: Exception) {
            null
        }
        return remember(title, text)
    }

    private fun remember(title: String, value: String?): String? {
        synchronized(cache) { cache[title] = value }
        return value
    }

    /** Basliktan site eklentilerini atar: "X (2016) – Filmi Full izle" -> "X (2016)" */
    private fun clean(raw: String): String {
        var t = raw.substringBefore(" – ").substringBefore(" - ").substringBefore("|")
        t = t.replace(
            Regex("(?i)\\b(filmi|film|full|hd|izle|türkçe|turkce|dublaj|altyazılı|altyazili)\\b"),
            " "
        )
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private fun find(title: String): String? {
        val base = title.replace(Regex("\\(\\d{4}\\)"), "").trim().ifEmpty { title }
        val queries = listOf(
            "intitle:\"$base\" film",
            "$base film",
            "$base sinema"
        )
        for (q in queries) {
            for (page in search(q)) {
                val text = extract(page) ?: continue
                // Yanlis maddeyi (baska film, universite vb.) ele.
                if (!Regex("(?i)\\bfilm\\b|sinema|yönetmen|oyuncu").containsMatchIn(text)) continue
                if (!relevant(base, page, text)) continue
                return finish(text)
            }
        }
        return null
    }

    /** Baslik ile madde gercekten ayni film mi? */
    private fun relevant(base: String, page: String, text: String): Boolean {
        val b = norm(base)
        val p = norm(page)
        if (b.isEmpty()) return false
        if (p.contains(b) || b.contains(p)) return true
        val words = b.split(" ").filter { it.length > 2 }
        if (words.isEmpty()) return false
        val head = norm(text.take(220))
        val hits = words.count { head.contains(it) || p.contains(it) }
        return hits * 3 >= words.size * 2
    }

    private fun norm(s: String): String = s.lowercase()
        .replace('ı', 'i').replace('İ', 'i')
        .replace('ş', 's').replace('ğ', 'g')
        .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun search(query: String): List<String> {
        val json = get(
            "https://tr.wikipedia.org/w/api.php?action=query&list=search&srsearch=" + enc(query) +
                "&srlimit=5&format=json&utf8=1"
        ) ?: return emptyList()
        val arr = JSONObject(json).getJSONObject("query").getJSONArray("search")
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i).optString("title")
            if (t.isNotBlank()) out.add(t)
        }
        return out
    }

    private fun extract(page: String): String? {
        val json = get(
            "https://tr.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=1&explaintext=1" +
                "&redirects=1&format=json&utf8=1&titles=" + enc(page)
        ) ?: return null
        val pages = JSONObject(json).getJSONObject("query").getJSONObject("pages")
        val keys = pages.keys()
        while (keys.hasNext()) {
            val text = pages.getJSONObject(keys.next()).optString("extract")
            if (text.isNotBlank()) return text
        }
        return null
    }

    /** Uzun ozeti ~520 karaktere indirir, cumle sonunda keser. */
    private fun finish(text: String): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= 520) return clean
        val cut = clean.substring(0, 520)
        val dot = cut.lastIndexOf(". ")
        return if (dot > 200) cut.substring(0, dot + 1) else cut.trimEnd() + "…"
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        c.setRequestProperty("User-Agent", "MovieBrowser/1.0 (Android TV)")
        c.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9")
        return try {
            if (c.responseCode in 200..299) {
                c.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } finally {
            c.disconnect()
        }
    }
}
