package com.tvfilmizle

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Film bilgisi: TMDB (varsa API anahtari) - Turkce baslik, yil, puan, ozet, afis.
 * Anahtar `local.properties` -> TMDB_API_KEY ya da ortam degiskeni ile verilir; repoya girmez.
 */
object Tmdb {

    data class Info(
        val id: Int,
        val title: String,
        val year: String,
        val rating: String,
        val overview: String,
        val poster: String
    )

    private val cache = HashMap<String, Info?>()

    /** Anahtar APK'da duz metin durmaz: BuildConfig'de XOR+Base64 saklanir. */
    private val apiKey: String by lazy {
        val obf = BuildConfig.TMDB_KEY_OBF
        if (obf.isBlank()) return@lazy ""
        try {
            val raw = android.util.Base64.decode(obf, android.util.Base64.NO_WRAP)
            val out = ByteArray(raw.size)
            for (i in raw.indices) out[i] = ((raw[i].toInt() and 0xFF) xor 0x5A).toByte()
            String(out, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    fun enabled(): Boolean = apiKey.isNotBlank()

    fun load(rawTitle: String): Info? {
        val key = apiKey
        if (key.isBlank()) return null
        val title = clean(rawTitle)
        if (title.isEmpty()) return null
        synchronized(cache) { if (cache.containsKey(title)) return cache[title] }

        val info = try {
            search(key, title)
        } catch (e: Exception) {
            null
        }
        synchronized(cache) { cache[title] = info }
        return info
    }

    private fun clean(raw: String): String {
        var t = raw.substringBefore(" – ").substringBefore(" - ").substringBefore("|")
        t = t.replace(Regex("(?i)\\b(filmi|film|full|hd|izle|türkçe|turkce|dublaj|altyazılı|altyazili)\\b"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private fun search(key: String, title: String): Info? {
        val year = Regex("((?:19|20)\\d{2})").find(title)?.groupValues?.get(1).orEmpty()
        val q = URLEncoder.encode(title.replace(Regex("\\(\\d{4}\\)"), "").trim(), "UTF-8")
        val url = "https://api.themoviedb.org/3/search/movie?api_key=$key&language=tr-TR" +
            "&query=$q&include_adult=false" + if (year.isNotEmpty()) "&year=$year" else ""
        val json = get(url) ?: return null
        val arr = JSONObject(json).optJSONArray("results") ?: return null
        if (arr.length() == 0) return null

        var best: JSONObject? = null
        var bestScore = -1.0
        for (i in 0 until minOf(arr.length(), 5)) {
            val o = arr.getJSONObject(i)
            var score = o.optDouble("vote_count", 0.0) / 1000.0
            val release = o.optString("release_date")
            if (year.isNotEmpty() && release.startsWith(year)) score += 5
            if (best == null || score > bestScore) {
                best = o
                bestScore = score
            }
        }
        val o = best ?: return null
        val overview = o.optString("overview").trim()
        if (overview.isEmpty()) return null

        val posterPath = o.optString("poster_path")
        val release = o.optString("release_date")
        return Info(
            id = o.optInt("id"),
            title = o.optString("title").ifBlank { o.optString("original_title") },
            year = if (release.length >= 4) release.substring(0, 4) else "",
            rating = if (o.optDouble("vote_average", 0.0) > 0) {
                String.format(java.util.Locale.US, "%.1f", o.optDouble("vote_average"))
            } else "",
            overview = overview,
            poster = if (posterPath.isBlank()) "" else "https://image.tmdb.org/t/p/w500$posterPath"
        )
    }

    /** Benzer filmler (baslik, yil). */
    fun recommendations(id: Int): List<Pair<String, String>> {
        val key = apiKey
        if (key.isBlank() || id <= 0) return emptyList()
        val json = get(
            "https://api.themoviedb.org/3/movie/$id/recommendations?api_key=$key&language=tr-TR&page=1"
        ) ?: return emptyList()
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Pair<String, String>>()
        for (i in 0 until minOf(arr.length(), 8)) {
            val o = arr.getJSONObject(i)
            val title = o.optString("title").ifBlank { o.optString("original_title") }
            if (title.isBlank()) continue
            val release = o.optString("release_date")
            out.add(title to if (release.length >= 4) release.substring(0, 4) else "")
        }
        return out
    }

    /** Yonetmen + ilk oyuncular. */
    fun credits(id: Int): List<String> {
        val key = apiKey
        if (key.isBlank() || id <= 0) return emptyList()
        val json = get("https://api.themoviedb.org/3/movie/$id/credits?api_key=$key&language=tr-TR")
            ?: return emptyList()
        val root = JSONObject(json)
        val out = ArrayList<String>()

        root.optJSONArray("crew")?.let { crew ->
            for (i in 0 until crew.length()) {
                val c = crew.getJSONObject(i)
                if (c.optString("job").equals("Director", true)) {
                    val n = c.optString("name")
                    if (n.isNotBlank()) out.add(n)
                }
            }
        }
        root.optJSONArray("cast")?.let { cast ->
            for (i in 0 until minOf(cast.length(), 8)) {
                val n = cast.getJSONObject(i).optString("name")
                if (n.isNotBlank()) out.add(n)
            }
        }
        return out
    }

    private fun get(url: String): String? {        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        c.setRequestProperty("Accept", "application/json")
        return try {
            if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            c.disconnect()
        }
    }
}
