package com.tvfilmizle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Herhangi bir gorsel URL'sini indirir; bellek + disk onbellegi. */
object Images {

    private val cache = LruCache<String, Bitmap>(80)
    private val inFlight = HashSet<String>()

    fun cached(url: String): Bitmap? = cache.get(url)

    fun load(ctx: Context, url: String, done: (Bitmap?) -> Unit) {
        cached(url)?.let { done(it); return }
        synchronized(inFlight) {
            if (inFlight.contains(url)) return
            inFlight.add(url)
        }
        val app = ctx.applicationContext
        Thread {
            var bmp: Bitmap? = null
            val file = File(app.cacheDir, "img_" + Integer.toHexString(url.hashCode()) + ".img")
            if (file.exists()) bmp = BitmapFactory.decodeFile(file.absolutePath)
            if (bmp == null) {
                try {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.connectTimeout = 8000
                    c.readTimeout = 8000
                    c.setRequestProperty("User-Agent", "Mozilla/5.0")
                    c.setRequestProperty("Referer", "https://" + URL(url).host + "/")
                    if (c.responseCode in 200..299) {
                        val bytes = c.inputStream.use { it.readBytes() }
                        bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null && bytes.size < 2_000_000) file.writeBytes(bytes)
                    }
                    c.disconnect()
                } catch (e: Exception) {
                    // yoksay
                }
            }
            if (bmp != null) cache.put(url, bmp)
            synchronized(inFlight) { inFlight.remove(url) }
            Handler(Looper.getMainLooper()).post { done(bmp) }
        }.start()
    }
}
