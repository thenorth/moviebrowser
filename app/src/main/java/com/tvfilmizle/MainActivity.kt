package com.tvfilmizle

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
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
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Ana ekran: tam ekran poster izgarasi (film afisi gorunumu). */
class MainActivity : Activity() {

    private val entries = ArrayList<Site>()
    private lateinit var grid: GridView
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

        // Baska uygulamadan gelen link: dogrudan tarayicida ac.
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            open(intent.data.toString())
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        grid = findViewById(R.id.site_grid)
        clock = findViewById(R.id.clock)
        status = findViewById(R.id.status)
        statusDot = findViewById(R.id.status_dot)

        adapter = SiteAdapter(this, entries)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            entries.getOrNull(position)?.let { activate(it) }
        }
        grid.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                for (i in 0 until grid.childCount) {
                    val child = grid.getChildAt(i)
                    setFocused(child, child === view)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        })

        fill(SiteRepository.cached(this))
        refreshList(false)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
        val age = System.currentTimeMillis() - SiteRepository.lastUpdate(this)
        if (!refreshing && age > 5 * 60 * 1000L) refreshList(false)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun fill(sites: List<Site>) {        entries.clear()
        entries.addAll(sites)
        entries.add(Site(getString(R.string.custom_url), getString(R.string.custom_url_sub), SiteKind.CUSTOM))
        entries.add(Site(getString(R.string.refresh_list), getString(R.string.refresh_list_sub), SiteKind.REFRESH))
        entries.add(Site(getString(R.string.settings), getString(R.string.vpn_title), SiteKind.SETTINGS))
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
    }

    private fun activate(site: Site) {
        when (site.kind) {
            SiteKind.CUSTOM -> askUrl()
            SiteKind.REFRESH -> refreshList(true)
            SiteKind.SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
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

    /** Odaktaki poster: %6 buyume + golge + halka. */
    private fun setFocused(v: View, on: Boolean) {
        v.foreground = if (on) resources.getDrawable(R.drawable.poster_ring, theme) else null
        v.animate().cancel()
        val s = if (on) 1.06f else 1f
        v.animate()
            .scaleX(s)
            .scaleY(s)
            .translationZ(if (on) 14f * resources.displayMetrics.density else 0f)
            .setDuration(150)
            .start()
    }

    private fun refreshBadge(): TextView? {
        val pos = entries.indexOfFirst { it.kind == SiteKind.REFRESH }
        if (pos < 0) return null
        val child = grid.getChildAt(pos - grid.firstVisiblePosition) ?: return null
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

private class SiteAdapter(private val ctx: Context, private val items: List<Site>) : BaseAdapter() {
    private val inflater = LayoutInflater.from(ctx)

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_poster, parent, false)
        val site = items[position]
        view.foreground = null
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationZ = 0f
        view.background = art(ctx, site)
        val host = try { android.net.Uri.parse(site.url).host } catch (e: Exception) { null }
        val blocked = site.kind == SiteKind.SITE && SiteHealth.isBlocked(ctx, host)
        view.findViewById<TextView>(R.id.site_flag).visibility = if (blocked) View.VISIBLE else View.GONE
        val badge = view.findViewById<TextView>(R.id.site_badge)
        badge.text = when (site.kind) {
            SiteKind.CUSTOM -> "+"
            SiteKind.REFRESH -> "↻"
            SiteKind.SETTINGS -> "⚙"
            SiteKind.SITE -> site.name.take(1).uppercase()
        }
        badge.alpha = if (site.kind == SiteKind.SITE) 0.22f else 1f
        badge.setTextColor(if (site.kind == SiteKind.SITE) Color.WHITE else Color.parseColor("#FF9F1A"))
        view.findViewById<TextView>(R.id.site_name).text = site.name
        view.findViewById<TextView>(R.id.site_url).text = site.url
        return view
    }

    /** Site adindan turetilen afis renkleri; islem kartlari koyu yuzey. */
    private fun art(ctx: Context, site: Site): Drawable {
        val d = ctx.resources.displayMetrics.density
        return if (site.kind == SiteKind.SITE) {
            val hue = (abs(site.name.hashCode()) % 360).toFloat()
            val top = Color.HSVToColor(floatArrayOf(hue, 0.40f, 0.44f))
            val bottom = Color.HSVToColor(floatArrayOf(hue, 0.62f, 0.15f))
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(top, bottom)).apply {
                cornerRadius = 12 * d
            }
        } else {
            GradientDrawable().apply {
                setColor(Color.parseColor("#141A22"))
                cornerRadius = 12 * d
                setStroke((1 * d).toInt(), Color.parseColor("#33FFFFFF"))
            }
        }
    }
}
