package com.tvfilmizle

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Film/dizi bilgisi: TMDB (varsa API anahtari) - Turkce baslik, yil, puan, ozet, afis.
 * Anahtar APK'da duz metin durmaz: BuildConfig.TMDB_KEY_OBF (XOR+Base64).
 */
object Tmdb {

    data class Info(
        val id: Int,
        val mediaType: String, // "movie" | "tv"
        val title: String,
        val year: String,
        val rating: String,
        val overview: String,
        val poster: String
    )

    data class Similar(val title: String, val year: String, val poster: String)

    data class Credits(val role: String, val names: List<String>, val cast: List<String>)

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

    /** Dizi mi? Baslik/URL ipuclarina bakar. */
    fun looksLikeSeries(title: String, url: String = ""): Boolean =
        Regex("(?i)bölüm|bolum|sezon|dizi").containsMatchIn(title) || url.contains("/dizi", true)

    fun load(rawTitle: String, preferTv: Boolean = false): Info? {
        val key = apiKey
        if (key.isBlank()) return null
        val title = clean(rawTitle)
        if (title.isEmpty()) return null
        val cacheKey = (if (preferTv) "tv:" else "movie:") + title
        synchronized(cache) { if (cache.containsKey(cacheKey)) return cache[cacheKey] }

        val info = try {
            val first = search(key, title, if (preferTv) "tv" else "movie")
            first ?: search(key, title, if (preferTv) "movie" else "tv")
        } catch (e: Exception) {
            null
        }
        synchronized(cache) { cache[cacheKey] = info }
        return info
    }

    private fun clean(raw: String): String {
        var t = raw.substringBefore(" – ").substringBefore(" - ").substringBefore("|")
        t = t.replace(
            Regex("(?i)\\b(filmi|film|full|hd|izle|türkçe|turkce|dublaj|altyazılı|altyazili)\\b"),
            " "
        )
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private fun search(key: String, title: String, type: String): Info? {
        val year = Regex("((?:19|20)\\d{2})").find(title)?.groupValues?.get(1).orEmpty()
        val q = URLEncoder.encode(title.replace(Regex("\\(\\d{4}\\)"), "").trim(), "UTF-8")
        val url = "https://api.themoviedb.org/3/search/$type?api_key=$key&language=tr-TR" +
            "&query=$q&include_adult=false" + if (year.isNotEmpty()) "&year=$year" else ""
        val json = get(url) ?: return null
        val arr = JSONObject(json).optJSONArray("results") ?: return null
        if (arr.length() == 0) return null

        var best: JSONObject? = null
        var bestScore = -1.0
        for (i in 0 until minOf(arr.length(), 5)) {
            val o = arr.getJSONObject(i)
            var score = o.optDouble("vote_count", 0.0) / 1000.0
            val release = o.optString(if (type == "tv") "first_air_date" else "release_date")
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
        val release = o.optString(if (type == "tv") "first_air_date" else "release_date")
        val name = o.optString(if (type == "tv") "name" else "title")
            .ifBlank { o.optString("original_" + if (type == "tv") "name" else "title") }
        return Info(
            id = o.optInt("id"),
            mediaType = type,
            title = name,
            year = if (release.length >= 4) release.substring(0, 4) else "",
            rating = if (o.optDouble("vote_average", 0.0) > 0) {
                String.format(java.util.Locale.US, "%.1f", o.optDouble("vote_average"))
            } else "",
            overview = overview,
            poster = if (posterPath.isBlank()) "" else "https://image.tmdb.org/t/p/w500$posterPath"
        )
    }

    /** Benzer film/diziler. */
    fun recommendations(id: Int, type: String): List<Similar> {
        val key = apiKey
        if (key.isBlank() || id <= 0) return emptyList()
        val json = get(
            "https://api.themoviedb.org/3/$type/$id/recommendations?api_key=$key&language=tr-TR&page=1"
        ) ?: return emptyList()
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Similar>()
        for (i in 0 until minOf(arr.length(), 8)) {
            val o = arr.getJSONObject(i)
            val title = o.optString(if (type == "tv") "name" else "title")
                .ifBlank { o.optString("original_title") }
            if (title.isBlank()) continue
            val release = o.optString(if (type == "tv") "first_air_date" else "release_date")
            val posterPath = o.optString("poster_path")
            out.add(
                Similar(
                    title,
                    if (release.length >= 4) release.substring(0, 4) else "",
                    if (posterPath.isBlank()) "" else "https://image.tmdb.org/t/p/w200$posterPath"
                )
            )
        }
        return out
    }

    /** Yonetmen (film) / yaratici (dizi) + ilk oyuncular. */
    fun credits(id: Int, type: String): Credits? {
        val key = apiKey
        if (key.isBlank() || id <= 0) return null
        val json = if (type == "tv") {
            get("https://api.themoviedb.org/3/tv/$id?api_key=$key&language=tr-TR&append_to_response=credits")
        } else {
            get("https://api.themoviedb.org/3/movie/$id/credits?api_key=$key&language=tr-TR")
        } ?: return null

        val root = JSONObject(json)
        val names = ArrayList<String>()
        val cast = ArrayList<String>()

        if (type == "tv") {
            root.optJSONArray("created_by")?.let { cb ->
                for (i in 0 until minOf(cb.length(), 3)) {
                    val n = cb.getJSONObject(i).optString("name")
                    if (n.isNotBlank()) names.add(n)
                }
            }
            root.optJSONObject("credits")?.optJSONArray("cast")?.let { list ->
                for (i in 0 until minOf(list.length(), 8)) {
                    val n = list.getJSONObject(i).optString("name")
                    if (n.isNotBlank()) cast.add(n)
                }
            }
        } else {
            root.optJSONArray("crew")?.let { crew ->
                for (i in 0 until crew.length()) {
                    val c = crew.getJSONObject(i)
                    if (c.optString("job").equals("Director", true)) {
                        val n = c.optString("name")
                        if (n.isNotBlank()) {
                            names.add(n)
                            break
                        }
                    }
                }
            }
            root.optJSONArray("cast")?.let { list ->
                for (i in 0 until minOf(list.length(), 8)) {
                    val n = list.getJSONObject(i).optString("name")
                    if (n.isNotBlank()) cast.add(n)
                }
            }
        }
        if (names.isEmpty() && cast.isEmpty()) return null
        return Credits(if (type == "tv") "Yaratıcı" else "Yönetmen", names, cast)
    }

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
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
