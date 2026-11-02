package com.tvfilmizle

import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * HLS ana listesini (master playlist) filtreler: alttaki dusuk cozunurlukler atilir,
 * boylece oynatici "Auto (240p)" yerine en az 1080p'yi secer.
 */
object Quality {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val MIN_HEIGHT = 1080

    /** Son suzulen listenin en dusuk izin verilen yuksekligi (rozet icin). */
    @Volatile
    var lastHeight = 0
        private set

    private val cache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 30
    }

    /** Ana liste ise yeniden yazilmis halini, degilse null dondurur (WebView normal yukler). */
    fun rewrite(url: String, pageUrl: String?): WebResourceResponse? {
        if (!url.contains(".m3u8", true)) return null
        synchronized(cache) { cache[url]?.let { return response(it) } }
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "*/*")
                if (!pageUrl.isNullOrEmpty()) setRequestProperty("Referer", pageUrl)
                CookieManager.getInstance().getCookie(url)?.let { setRequestProperty("Cookie", it) }
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            if (!body.contains("#EXT-X-STREAM-INF")) return null
            val filtered = filter(body)
            synchronized(cache) { cache[url] = filtered }
            response(filtered)
        } catch (e: Exception) {
            null
        }
    }

    private fun response(body: String): WebResourceResponse =
        WebResourceResponse(
            "application/vnd.apple.mpegurl",
            "utf-8",
            ByteArrayInputStream(body.toByteArray())
        ).apply {
            setResponseHeaders(mapOf("Access-Control-Allow-Origin" to "*"))
        }

    /** Testler icin: master listeyi cozunurluge gore suzer. */
    internal fun filterForTest(text: String): String = filter(text)

    /** Master listeyi cozunurluge gore suzer. */
    private fun filter(text: String): String {
        val lines = text.lines()
        val first = lines.indexOfFirst { it.trim().startsWith("#EXT-X-STREAM-INF") }
        if (first < 0) return text
        val head = lines.subList(0, first)
        val variants = ArrayList<Pair<String, String>>() // (STREAM-INF satiri, URI)
        var i = first
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                var j = i + 1
                while (j < lines.size && (lines[j].trim().isEmpty() || lines[j].trim().startsWith("#"))) j++
                if (j < lines.size) {
                    variants.add(line to lines[j].trim())
                    i = j + 1
                    continue
                }
            }
            i++
        }
        if (variants.isEmpty()) return text
        val heights = variants.map { heightOf(it.first) }
        val max = heights.maxOrNull() ?: 0
        val target = if (max >= MIN_HEIGHT) MIN_HEIGHT else max
        lastHeight = target
        val keep = variants.filterIndexed { idx, _ -> heights[idx] >= target }
        if (keep.isEmpty() || keep.size == variants.size) return text

        val sb = StringBuilder()
        head.forEach { sb.append(it).append('\n') }
        keep.forEach { (inf, uri) ->
            sb.append(inf).append('\n').append(uri).append('\n')
        }
        return sb.toString()
    }

    private fun heightOf(streamInf: String): Int =
        Regex("(?i)RESOLUTION=\\d+x(\\d+)").find(streamInf)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
