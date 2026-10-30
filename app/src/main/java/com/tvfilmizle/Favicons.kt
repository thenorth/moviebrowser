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

/** Site favicon'larini (Google servisi) indirir, bellekte ve diskte onbellekler. */
object Favicons {

    private val cache = LruCache<String, Bitmap>(64)
    private val inFlight = HashSet<String>()

    fun cached(host: String): Bitmap? = cache.get(host)

    fun load(ctx: Context, host: String, done: (Bitmap?) -> Unit) {
        cached(host)?.let { done(it); return }
        synchronized(inFlight) {
            if (inFlight.contains(host)) return
            inFlight.add(host)
        }
        val app = ctx.applicationContext
        Thread {
            var bmp: Bitmap? = null
            val file = File(app.cacheDir, "fav_" + host.replace('.', '_') + ".png")
            if (file.exists()) bmp = BitmapFactory.decodeFile(file.absolutePath)
            if (bmp == null) {
                try {
                    val c = URL("https://www.google.com/s2/favicons?sz=128&domain=" + host)
                        .openConnection() as HttpURLConnection
                    c.connectTimeout = 6000
                    c.readTimeout = 6000
                    if (c.responseCode in 200..299) {
                        val bytes = c.inputStream.use { it.readBytes() }
                        bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) file.writeBytes(bytes)
                    }
                    c.disconnect()
                } catch (e: Exception) {
                    // yoksay: harf gösterilir
                }
            }
            if (bmp != null) cache.put(host, bmp)
            synchronized(inFlight) { inFlight.remove(host) }
            Handler(Looper.getMainLooper()).post { done(bmp) }
        }.start()
    }
}
