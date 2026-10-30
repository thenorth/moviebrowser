package com.tvfilmizle

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL

/** Sitelerin erisilebilirlik durumu: canli test. */
class StatusActivity : Activity() {

    private val sites = ArrayList<Site>()
    private val codes = HashMap<String, Int>() // -1: test edilmedi
    private var testing = false
    private lateinit var adapter: StatusAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        sites.addAll(SiteRepository.cached(this))
        sites.forEach { codes[it.url] = -1 }

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_test).setOnClickListener { testAll() }

        adapter = StatusAdapter(this, sites, codes)
        findViewById<ListView>(R.id.list).adapter = adapter
    }

    private fun testAll() {
        if (testing) return
        testing = true
        sites.forEach { codes[it.url] = 0 }
        adapter.notifyDataSetChanged()
        Thread {
            for (site in sites) {
                val code = ping(site.url)
                runOnUiThread {
                    codes[site.url] = code
                    adapter.notifyDataSetChanged()
                }
            }
            runOnUiThread { testing = false }
        }.start()
    }

    private fun ping(url: String): Int = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.requestMethod = "GET"
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        val code = c.responseCode
        c.disconnect()
        code
    } catch (e: Exception) {
        0
    }
}

private class StatusAdapter(
    private val ctx: Context,
    private val items: List<Site>,
    private val codes: Map<String, Int>
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(ctx)

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_status, parent, false)
        val site = items[position]
        val code = codes[site.url] ?: -1
        view.findViewById<TextView>(R.id.status_name).text = site.name

        val dot = view.findViewById<TextView>(R.id.status_dot)
        val text = view.findViewById<TextView>(R.id.status_text)
        when {
            code == -1 -> {
                dot.setTextColor(Color.parseColor("#8B98A6"))
                text.text = ctx.getString(R.string.status_unknown)
            }
            code == 0 -> {
                dot.setTextColor(Color.parseColor("#FF6B6B"))
                text.text = ctx.getString(R.string.status_blocked)
            }
            code in 200..399 -> {
                dot.setTextColor(Color.parseColor("#59D986"))
                text.text = ctx.getString(R.string.status_ok) + " · " + code
            }
            code == 403 || code == 429 || code == 503 -> {
                dot.setTextColor(Color.parseColor("#FF9F1A"))
                text.text = ctx.getString(R.string.status_protected) + " · " + code
            }
            else -> {
                dot.setTextColor(Color.parseColor("#FF6B6B"))
                text.text = ctx.getString(R.string.status_error) + " · " + code
            }
        }
        return view
    }
}
