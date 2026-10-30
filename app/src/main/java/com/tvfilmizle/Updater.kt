package com.tvfilmizle

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** GitHub release'lerinden yeni surum var mi diye bakar. */
object Updater {

    private const val API = "https://api.github.com/repos/thenorth/moviebrowser/releases/latest"

    fun installedVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    /** done(tag, apkUrl, htmlUrl) - tag null ise bilgi alinamadi. */
    fun check(ctx: Context, done: (tag: String?, apk: String?, page: String?) -> Unit) {
        Thread {
            try {
                val c = URL(API).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty("User-Agent", "moviebrowser")
                c.setRequestProperty("Accept", "application/vnd.github+json")
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)
                    ?.bufferedReader()?.use(BufferedReader::readText) ?: ""
                c.disconnect()
                if (code !in 200..299) {
                    done(null, null, null)
                    return@Thread
                }
                val json = JSONObject(text)
                val tag = json.optString("tag_name")
                val page = json.optString("html_url")
                var apk: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        val name = a.optString("name")
                        if (name.endsWith(".apk")) {
                            apk = a.optString("browser_download_url")
                            break
                        }
                    }
                }
                done(tag, apk, page)
            } catch (e: Exception) {
                done(null, null, null)
            }
        }.start()
    }

    /** "v2.6" > "2.5" */
    fun isNewer(tag: String?, installed: String): Boolean {
        if (tag.isNullOrBlank() || installed.isBlank()) return false
        val a = tag.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        val b = installed.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
