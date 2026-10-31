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
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
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
            entries.getOrNull(position)?.let { openSite(it) }
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
        // Odak menuden donunce de secili kart gorunsun.
        grid.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) grid.post { highlightSelected() } }

        bindSideBar()
        fill(SiteRepository.cached(this))
        grid.post { grid.requestFocus() }
        refreshList(false)
        checkForUpdate()
    }

    /** Soldaki sabit menu. */
    private fun bindSideBar() {
        findViewById<View>(R.id.side_search).setOnClickListener { askSearch() }
        findViewById<View>(R.id.side_discover).setOnClickListener {
            startActivity(Intent(this, DiscoverActivity::class.java))
        }
        findViewById<View>(R.id.side_watchlist).setOnClickListener {
            startActivity(Intent(this, WatchlistActivity::class.java))
        }
        findViewById<View>(R.id.side_status).setOnClickListener {
            startActivity(Intent(this, StatusActivity::class.java))
        }
        findViewById<View>(R.id.side_url).setOnClickListener { askUrl() }
        findViewById<View>(R.id.side_refresh).setOnClickListener { refreshList(true) }
        findViewById<View>(R.id.side_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    /** Secili kartin halkasini tazeler (odak menuden donunce). */
    private fun highlightSelected() {
        val pos = if (grid.selectedItemPosition < 0) 0 else grid.selectedItemPosition
        val idx = pos - grid.firstVisiblePosition
        for (i in 0 until grid.childCount) {
            setFocused(grid.getChildAt(i), i == idx)
        }
    }

    /** Soldaki menu ile ızgara arasında odak gecisi (GridView LEFT tusunu kendi yutuyor). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && grid.hasFocus() &&
                grid.selectedItemPosition % grid.numColumns == 0
            ) {
                findViewById<View>(R.id.side_search).requestFocus()
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && !grid.hasFocus()) {
                grid.requestFocus()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** GitHub'daki son release ile kurulu surumu karsilastirir. */
    private fun checkForUpdate() {
        Updater.check(this) { tag, apk, page ->
            if (tag == null) return@check
            val installed = Updater.installedVersion(this)
            if (!Updater.isNewer(tag, installed)) return@check
            if (Prefs.lastNotifiedVersion(this) == tag) return@check
            runOnUiThread {
                Prefs.setLastNotifiedVersion(this, tag)
                AlertDialog.Builder(this)
                    .setTitle(R.string.update_title)
                    .setMessage(getString(R.string.update_msg, tag, installed))
                    .setPositiveButton(R.string.update_yes) { _, _ ->
                        val url = if (!apk.isNullOrEmpty()) apk else page
                        if (!url.isNullOrEmpty()) open(url)
                    }
                    .setNegativeButton(R.string.update_later, null)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SleepTimer.attach(this)
        handler.post(tick)
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
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
        grid.post {
            val side = findViewById<View>(R.id.side_bar)
            if (!side.hasFocus() && !grid.hasFocus()) grid.requestFocus()
            if (!side.hasFocus()) grid.setSelection(0)
            highlightSelected()
        }
    }

    private fun openSite(site: Site) {
        startActivity(
            Intent(this, SiteFilmsActivity::class.java)
                .putExtra(SiteFilmsActivity.EXTRA_URL, site.url)
                .putExtra(SiteFilmsActivity.EXTRA_NAME, site.name)
        )
    }

    /** Google'da film arar (sonuc tarayicida acilir). */
    private fun askSearch() {
        val input = EditText(this).apply {
            setSingleLine()
            hint = getString(R.string.search_hint)
            setPadding(48, 36, 48, 36)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.search_title)
            .setView(input)
            .setPositiveButton(R.string.search_action) { _, _ ->
                val q = input.text.toString().trim()
                if (q.isNotEmpty()) {
                    open("https://www.google.com/search?q=" + android.net.Uri.encode(q + " film izle"))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener { input.requestFocus() }
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
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

    private fun refreshBadge(): View? = findViewById(R.id.side_refresh_glyph)

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
        view.background = art(site)
        val host = try { android.net.Uri.parse(site.url).host } catch (e: Exception) { null }
        val blocked = SiteHealth.isBlocked(ctx, host)
        view.findViewById<TextView>(R.id.site_flag).visibility = if (blocked) View.VISIBLE else View.GONE
        val badge = view.findViewById<TextView>(R.id.site_badge)
        val logo = view.findViewById<ImageView>(R.id.site_logo)
        badge.text = site.name.take(1).uppercase()
        badge.setTextColor(Color.WHITE)
        badge.alpha = 1f
        val known = host?.let { Favicons.cached(it) }
        if (known != null) {
            logo.tag = host
            logo.setImageBitmap(known)
            logo.visibility = View.VISIBLE
            badge.visibility = View.GONE
        } else {
            logo.setImageDrawable(null)
            logo.visibility = View.GONE
            badge.visibility = View.VISIBLE
            if (host != null && logo.tag != host) {
                logo.tag = host
                Favicons.load(ctx, host) { bmp ->
                    if (bmp != null && logo.tag == host) {
                        logo.setImageBitmap(bmp)
                        logo.visibility = View.VISIBLE
                        badge.visibility = View.GONE
                    }
                }
            }
        }
        view.findViewById<TextView>(R.id.site_name).text = site.name
        view.findViewById<TextView>(R.id.site_url).text = site.url
        return view
    }

    /** Site adindan turetilen afis renkleri. */
    private fun art(site: Site): Drawable {
        val d = ctx.resources.displayMetrics.density
        val hue = (abs(site.name.hashCode()) % 360).toFloat()
        val top = Color.HSVToColor(floatArrayOf(hue, 0.40f, 0.44f))
        val bottom = Color.HSVToColor(floatArrayOf(hue, 0.62f, 0.15f))
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(top, bottom)).apply {
            cornerRadius = 12 * d
        }
    }
}
