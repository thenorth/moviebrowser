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
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
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
    private lateinit var panel: FrameLayout
    private lateinit var clock: TextView
    private lateinit var status: TextView
    private lateinit var statusDot: View
    private lateinit var adapter: SiteAdapter
    private var section = ""
    private var panelLongPress: (() -> Unit)? = null
    private var panelPressDone = false
    private var panelPressRunnable: Runnable? = null

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
        panel = findViewById(R.id.panel)
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
        refreshList(false)
        checkForUpdate()
    }

    /** Soldaki sabit menu; secilen bolum sagda acilir. */
    private fun bindSideBar() {
        sideRow(R.id.side_search) { askSearch() }
        sideRow(R.id.side_discover) { showSection("discover") }
        sideRow(R.id.side_watchlist) { showSection("watch") }
        sideRow(R.id.side_status) { showSection("status") }
        sideRow(R.id.side_url) { askUrl() }
        sideRow(R.id.side_refresh) { refreshList(true) }
        sideRow(R.id.side_settings) { showSection("settings") }
    }

    /** Bolumu sagdaki panelde acar; menu yerinde kalir. */
    private fun showSection(name: String) {
        section = name
        grid.visibility = View.GONE
        panel.visibility = View.VISIBLE
        panel.removeAllViews()
        panelLongPress = null
        val layout = when (name) {
            "discover" -> R.layout.activity_discover
            "watch" -> R.layout.activity_watchlist
            "status" -> R.layout.activity_status
            else -> R.layout.activity_settings
        }
        val root = layoutInflater.inflate(layout, panel, false)
        panel.addView(root)
        // Panel kendi basligini tasiyor: ust bosluk kucultulur.
        val d = resources.displayMetrics.density
        val list = when (name) {
            "discover" -> root.findViewById<View>(R.id.grid)
            "status", "watch" -> root.findViewById<View>(R.id.list)
            else -> null
        }
        list?.setPadding((38 * d).toInt(), (56 * d).toInt(), (38 * d).toInt(), (80 * d).toInt())
        // Panelde geri dugmesi ve alt ipucu seridi gereksiz.
        root.findViewById<View>(R.id.btn_back)?.visibility = View.GONE
        if (name == "discover" || name == "watch") {
            (root as? ViewGroup)?.let {
                val last = it.getChildAt(it.childCount - 1)
                if (last is LinearLayout) last.visibility = View.GONE
            }
        }
        panelLongPress = when (name) {
            "discover" -> Panels.discover(this, root)
            "watch" -> Panels.watchlist(this, root)
            "status" -> {
                Panels.status(this, root)
                null
            }
            else -> {
                Panels.settings(this, root)
                null
            }
        }
        // Menu odakli kalir: asagi/yukari ile bolum degistirmek kolay olsun.
        val row = when (name) {
            "discover" -> R.id.side_discover
            "watch" -> R.id.side_watchlist
            "status" -> R.id.side_status
            else -> R.id.side_settings
        }
        findViewById<View>(row).requestFocus()
    }

    /** Site ızgarasına doner. */
    private fun showSites() {
        section = ""
        panelLongPress = null
        panel.removeAllViews()
        panel.visibility = View.GONE
        grid.visibility = View.VISIBLE
        grid.post { grid.requestFocus() }
    }

    /** Menude OK hem dokunusla hem kumandayla calisir (ilk dokunus sadece odak vermesin). */
    private fun sideRow(id: Int, action: () -> Unit) {
        val row = findViewById<View>(id)
        row.setOnClickListener { action() }
        row.setOnTouchListener { v, e ->
            when (e.action) {
                android.view.MotionEvent.ACTION_DOWN -> true
                android.view.MotionEvent.ACTION_UP -> {
                    v.performClick()
                    true
                }
                else -> false
            }
        }
        row.setOnKeyListener { _, keyCode, event ->
            val ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                keyCode == KeyEvent.KEYCODE_BUTTON_A
            if (ok && event.action == KeyEvent.ACTION_DOWN) {
                action()
                true
            } else {
                false
            }
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

    /** Soldaki menu ile icerik arasında odak gecisi; panelde OK'a basili tutma. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isOk = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
        if (isOk && panelLongPress != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (panelPressRunnable == null && !panelPressDone) {
                    val r = Runnable {
                        panelPressDone = true
                        panelLongPress?.invoke()
                    }
                    panelPressRunnable = r
                    panel.postDelayed(r, 450)
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                panelPressRunnable?.let { panel.removeCallbacks(it) }
                panelPressRunnable = null
                if (panelPressDone) {
                    panelPressDone = false
                    return true
                }
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val side = findViewById<View>(R.id.side_bar)
            val focused = currentFocus
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (grid.visibility == View.VISIBLE && grid.hasFocus()) {
                    val pos = if (grid.selectedItemPosition < 0) 0 else grid.selectedItemPosition
                    if (pos % grid.numColumns == 0) {
                        findViewById<View>(R.id.side_search).requestFocus()
                        return true
                    }
                } else if (section.isNotEmpty() && !inside(focused, side)) {
                    findViewById<View>(R.id.side_search).requestFocus()
                    return true
                }
            }
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && inside(focused, side)) {
                focusContent()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun inside(v: View?, ancestor: View): Boolean {
        var x: View? = v
        while (x != null) {
            if (x === ancestor) return true
            x = x.parent as? View
        }
        return false
    }

    /** Izgara/liste ilk sutunda mi? (secim yoksa ilk oge kabul edilir) */
    private fun focusContent() {
        var target: View? = null
        if (section.isEmpty()) {
            target = grid
        } else {
            val f = findFocusable(panel)
            if (f != null) target = f
        }
        target?.requestFocus()
    }

    private fun findFocusable(group: ViewGroup): View? {
        for (i in 0 until group.childCount) {
            val c = group.getChildAt(i)
            if (c.visibility != View.VISIBLE) continue
            if (c.isFocusable) return c
            if (c is ViewGroup) findFocusable(c)?.let { return it }
        }
        return null
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
        // Listem tarayicidan donuste tazelensin.
        if (section == "watch") {
            showSection("watch")
            panel.post { focusContent() }
        }
        val age = System.currentTimeMillis() - SiteRepository.lastUpdate(this)
        if (!refreshing && age > 5 * 60 * 1000L) refreshList(false)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Panels.vpnResult(this, requestCode, resultCode)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    /** Panel acikken GERI: site ızgarasına don. */
    override fun onBackPressed() {
        if (section.isNotEmpty()) {
            showSites()
        } else {
            super.onBackPressed()
        }
    }

    private fun fill(sites: List<Site>) {
        entries.clear()
        entries.addAll(sites)
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
        grid.post {
            val side = findViewById<View>(R.id.side_bar)
            if (grid.visibility == View.VISIBLE && !side.hasFocus() && !grid.hasFocus()) {
                grid.requestFocus()
            }
            if (grid.visibility == View.VISIBLE && !side.hasFocus()) grid.setSelection(0)
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
