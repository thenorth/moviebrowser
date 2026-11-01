package com.tvfilmizle

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/** Listem/Dizilerim/ayarlar: JSON olarak yedekle ve geri yukle. */
object Backup {

    const val REQ_SAVE = 21
    const val REQ_LOAD = 22

    // Uygulamanin kalici verileri (onbellek dosyalari disarida).
    private val FILES = arrayOf(
        "watchlist", "history", "favorites", "user_sites",
        "vpn_hosts", "search_history", "app_prefs"
    )

    fun save(a: Activity) {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "movie-browser-yedek.json")
        }
        try {
            a.startActivityForResult(i, REQ_SAVE)
        } catch (e: Exception) {
            a.toast(R.string.backup_failed)
        }
    }

    fun load(a: Activity) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        try {
            a.startActivityForResult(i, REQ_LOAD)
        } catch (e: Exception) {
            a.toast(R.string.backup_failed)
        }
    }

    fun write(a: Activity, uri: Uri): Boolean = try {
        val all = JSONObject()
        for (name in FILES) {
            val prefs = a.getSharedPreferences(name, Context.MODE_PRIVATE)
            val o = JSONObject()
            for ((k, v) in prefs.all) if (v != null) o.put(k, encode(v))
            all.put(name, o)
        }
        a.contentResolver.openOutputStream(uri)?.use { it.write(all.toString().toByteArray()) }
        true
    } catch (e: Exception) {
        false
    }

    fun read(a: Activity, uri: Uri): Boolean = try {
        val text = a.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        }.orEmpty()
        val all = JSONObject(text)
        // Once tumunu oku: bozuk dosya mevcut veriyi silmesin.
        val data = LinkedHashMap<String, Map<String, String>>()
        for (name in FILES) {
            val o = all.optJSONObject(name) ?: continue
            val values = HashMap<String, String>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                values[k] = o.optString(k)
            }
            data[name] = values
        }
        if (data.isEmpty()) {
            false
        } else {
            for ((name, values) in data) {
                val prefs = a.getSharedPreferences(name, Context.MODE_PRIVATE)
                val old = prefs.all
                val e = prefs.edit().clear()
                for ((k, v) in values) decode(e, k, v, old[k])
                e.apply()
            }
            true
        }
    } catch (e: Exception) {
        false
    }

    /** Deger tipi korunur: "i:" int, "l:" long, "b:" bool, "f:" float, "s:" string. */
    private fun encode(v: Any): String = when (v) {
        is Boolean -> "b:$v"
        is Int -> "i:$v"
        is Long -> "l:$v"
        is Float -> "f:$v"
        else -> "s:$v"
    }

    /** Onek yoksa (eski yedek) mevcut degerin tipine uydur: getLong/getBoolean cokmesin. */
    private fun decode(
        e: android.content.SharedPreferences.Editor,
        k: String,
        v: String,
        old: Any?
    ) {
        when {
            v.startsWith("b:") -> e.putBoolean(k, v.substring(2).toBoolean())
            v.startsWith("i:") -> e.putInt(k, v.substring(2).toIntOrNull() ?: 0)
            v.startsWith("l:") -> e.putLong(k, v.substring(2).toLongOrNull() ?: 0L)
            v.startsWith("f:") -> e.putFloat(k, v.substring(2).toFloatOrNull() ?: 0f)
            else -> when (old) {
                is Boolean -> e.putBoolean(k, v.toBoolean())
                is Int -> e.putInt(k, v.toIntOrNull() ?: 0)
                is Long -> e.putLong(k, v.toLongOrNull() ?: 0L)
                is Float -> e.putFloat(k, v.toFloatOrNull() ?: 0f)
                else -> e.putString(k, v.removePrefix("s:"))
            }
        }
    }

    private fun Activity.toast(res: Int) =
        android.widget.Toast.makeText(this, res, android.widget.Toast.LENGTH_SHORT).show()
}
