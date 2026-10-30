package com.tvfilmizle

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ana ekran: solda dikey site listesi, sagda secili sitenin onizlemesi (TV ana ekran mantigi).
 */
class MainActivity : Activity() {

    private val entries = ArrayList<Site>()
    private lateinit var list: ListView
    private lateinit var clock: TextView
    private lateinit var status: TextView
    private lateinit var statusDot: View
    private lateinit var adapter: SiteAdapter

    private var refreshing = false
    private var spin: ObjectAnimator? = null

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        list = findViewById(R.id.site_list)
        clock = findViewById(R.id.clock)
        status = findViewById(R.id.status)
        statusDot = findViewById(R.id.status_dot)

        adapter = SiteAdapter(this, entries)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            entries.getOrNull(position)?.let { activate(it) }
        }

        fill(SiteRepository.cached(this))
        refreshList(false)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
        // Uygulama one geldiginde liste 5 dakikadan eskiyse otomatik guncelle.
        val age = System.currentTimeMillis() - SiteRepository.lastUpdate(this)
        if (!refreshing && age > 5 * 60 * 1000L) refreshList(false)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun fill(sites: List<Site>) {
        entries.clear()
        entries.addAll(sites)
        entries.add(Site(getString(R.string.custom_url), getString(R.string.custom_url_sub), SiteKind.CUSTOM))
        entries.add(Site(getString(R.string.refresh_list), getString(R.string.refresh_list_sub), SiteKind.REFRESH))
        adapter.notifyDataSetChanged()
        list.setSelection(0)
    }

    private fun activate(site: Site) {
        when (site.kind) {
            SiteKind.CUSTOM -> askUrl()
            SiteKind.REFRESH -> refreshList(true)
            SiteKind.SITE -> open(site.url)
        }
    }

    private fun refreshList(manual: Boolean) {
        if (refreshing) return
        refreshing = true
        status.text = getString(R.string.status_loading)
        dot("#8B98A6")
        startSpin()
        val started = System.currentTimeMillis()
        SiteRepository.refresh(this) { sites, changed ->
            // Animasyon en az 700 ms gorunsun (arama cok hizli bitiyor).
            val rest = 700L - (System.currentTimeMillis() - started)
            handler.postDelayed({ finishRefresh(sites, changed, manual) }, if (rest > 0) rest else 0)
        }
    }

    private fun finishRefresh(sites: List<Site>?, changed: Boolean, manual: Boolean) {
        refreshing = false
        stopSpin()
        if (sites == null) {
            status.text = getString(R.string.status_offline)
            dot("#FF6B6B")
            if (manual) Toast.makeText(this, R.string.status_offline, Toast.LENGTH_SHORT).show()
        } else {
            fill(sites)
            val msg = getString(R.string.status_updated, sites.size)
            status.text = msg
            dot("#59D986")
            if (changed || manual) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dot(color: String) {
        statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(color))
    }

    private fun refreshBadge(): TextView? {
        val pos = entries.indexOfFirst { it.kind == SiteKind.REFRESH }
        if (pos < 0) return null
        val child = list.getChildAt(pos - list.firstVisiblePosition) ?: return null
        return child.findViewById(R.id.site_badge)
    }

    private fun startSpin() {
        val glyph = refreshBadge() ?: return
        spin?.cancel()
        spin = ObjectAnimator.ofFloat(glyph, View.ROTATION, 0f, 360f).apply {
            duration = 850
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopSpin() {
        spin?.cancel()
        spin = null
        refreshBadge()?.rotation = 0f
    }

    private fun open(url: String) {
        startActivity(Intent(this, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, url))
    }

    private fun askUrl() {
        val input = EditText(this).apply {
            setSingleLine()
            hint = "https://"
            setPadding(48, 36, 48, 36)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.custom_url)
            .setView(input)
            .setPositiveButton(R.string.go) { _, _ ->
                val raw = input.text.toString().trim()
                if (raw.isNotEmpty()) open(if (raw.startsWith("http")) raw else "https://$raw")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener { input.requestFocus() }
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
}

private class SiteAdapter(ctx: Context, private val items: List<Site>) : BaseAdapter() {
    private val inflater = LayoutInflater.from(ctx)

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_site, parent, false)
        val site = items[position]
        view.findViewById<TextView>(R.id.site_badge).text = when (site.kind) {
            SiteKind.CUSTOM -> "+"
            SiteKind.REFRESH -> "↻"
            SiteKind.SITE -> site.name.take(1).uppercase()
        }
        view.findViewById<TextView>(R.id.site_name).text = site.name
        view.findViewById<TextView>(R.id.site_url).text = site.url
        return view
    }
}
