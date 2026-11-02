package com.tvfilmizle

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
    private var apkDownloadId = -1L

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != apkDownloadId) return
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = dm.getUriForDownloadedFile(id) ?: return
            // Android 8+: bilinmeyen kaynaklardan kurulum izni gerekiyor.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
                Toast.makeText(this@MainActivity, R.string.update_allow_install, Toast.LENGTH_LONG).show()
                try {
                    startActivity(
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            android.net.Uri.parse("package:" + packageName)
                        )
                    )
                } catch (e: Exception) {
                    // ayar ekrani yok
                }
                return
            }
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, APK_MIME)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.update_manual, Toast.LENGTH_LONG).show()
            }
        }
    }

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
        registerDownloadReceiver()
        fill(SiteRepository.cached(this))
        Panels.warmFeed(this)
        refreshList(false)
        checkForUpdate()
        maybeShowWhatsNew()
    }

    /** Surum degistiyse (guncelleme sonrasi) yenilikler ekranini bir kez gosterir. */
    private fun maybeShowWhatsNew() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        if (version.isEmpty()) return
        val seen = Prefs.seenVersion(this)
        if (seen.isEmpty()) {
            Prefs.setSeenVersion(this, version)
            return
        }
        if (seen == version) return
        Prefs.setSeenVersion(this, version)
        handler.postDelayed({
            if (isFinishing) return@postDelayed
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.whats_new) + " · v" + version)
                .setMessage(R.string.whats_new_body)
                .setPositiveButton(R.string.whats_new_ok, null)
                .show()
        }, 900)
    }

    /** Soldaki sabit menu; secilen bolum sagda acilir. */
    private fun bindSideBar() {
        sideRow(R.id.side_home) { showSites() }
        sideRow(R.id.side_search) { showSection("search") }
        sideRow(R.id.side_discover) { showSection("discover") }
        sideRow(R.id.side_watchlist) { showSection("watch") }
        sideRow(R.id.side_recent) { showSection("recent") }
        sideRow(R.id.side_series) { showSection("series") }
        sideRow(R.id.side_status) { showSection("status") }
        sideRow(R.id.side_url) { showSection("url") }
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
            "watch", "recent", "series" -> R.layout.activity_watchlist
            "status" -> R.layout.activity_status
            "settings" -> R.layout.activity_settings
            "detail" -> R.layout.panel_detail
            "search" -> R.layout.panel_search
            else -> R.layout.panel_input
        }
        val root = layoutInflater.inflate(layout, panel, false)
        panel.addView(root)
        // Panel kendi basligini tasiyor: ust bosluk kucultulur.
        val d = resources.displayMetrics.density
        val list = when (name) {
            "discover" -> root.findViewById<View>(R.id.grid)
            "status", "watch", "recent", "series", "search" -> root.findViewById<View>(R.id.list)
            else -> null
        }
        val topDp = when (name) {
            "discover" -> 130
            "status" -> 90
            "search" -> 214
            else -> 56
        }
        list?.setPadding((26 * d).toInt(), (topDp * d).toInt(), (26 * d).toInt(), (80 * d).toInt())
        // Panelde geri dugmesi ve alt ipucu seridi gereksiz.
        root.findViewById<View>(R.id.btn_back)?.visibility = View.GONE
        if (name == "discover" || name == "watch" || name == "recent" || name == "series") {
            (root as? ViewGroup)?.let {
                val last = it.getChildAt(it.childCount - 1)
                if (last is LinearLayout) last.visibility = View.GONE
            }
        }
        panelLongPress = when (name) {
            "discover" -> Panels.discover(this, root, onOpen = { row, alt -> openFilm(row, alt) }, onRefresh = {
                DiscoverCache.clear(this)
                Panels.clearFeed()
                showSection("discover")
            })
            "watch" -> Panels.watchlist(this, root) { openFilm(it) }
            "recent" -> Panels.recent(this, root) { openFilm(it) }
            "series" -> Panels.series(this, root) { openFilm(it) }
            "status" -> {
                Panels.status(this, root)
                null
            }
            "settings" -> {
                Panels.settings(
                    this, root,
                    onUpdate = { v -> checkUpdateManually(v) },
                    onBackup = { Backup.save(this) },
                    onRestore = { Backup.load(this) }
                )
                null
            }
            "detail" -> {
                bindDetail(root)
                null
            }
            else -> {
                if (name == "search") {
                    Panels.search(
                        this, root,
                        onOpen = { row -> openFilm(row) },
                        onWeb = { q ->
                            Panels.open(
                                this,
                                "https://www.google.com/search?q=" +
                                    android.net.Uri.encode(q + " film izle")
                            )
                        }
                    )
                } else {
                    Panels.input(
                        this, root, R.string.custom_url, R.string.url_hint, R.string.go,
                        initial = "https://",
                        note = getString(R.string.url_note),
                        historyKind = SearchHistory.URL,
                        onText = { raw ->
                            val host = Scraper.hostOf(if (raw.startsWith("http")) raw else "https://$raw")
                            val noteView = root.findViewById<TextView>(R.id.input_note)
                            noteView.text = if (host.isBlank()) {
                                getString(R.string.url_note)
                            } else if (SiteRepository.cached(this).any {
                                    Scraper.hostOf(it.url).equals(host, true)
                                }) {
                                getString(R.string.site_exists, host)
                            } else {
                                getString(R.string.site_preview, siteNameOf(host), host)
                            }
                        }
                    ) { q -> addAddressAndOpen(q) }
                }
                null
            }
        }
        if (name == "detail") return
        // Menu odakli kalir: asagi/yukari ile bolum degistirmek kolay olsun.
        val row = when (name) {
            "discover" -> R.id.side_discover
            "watch" -> R.id.side_watchlist
            "recent" -> R.id.side_recent
            "series" -> R.id.side_series
            "search" -> R.id.side_search
            "url" -> R.id.side_url
            "status" -> R.id.side_status
            else -> R.id.side_settings
        }
        findViewById<View>(row).requestFocus()
    }

    private var detailRow: Panels.Row? = null
    private var detailAlts: List<Panels.Row> = emptyList()
    private var prevSection = ""

    /** Film kartina basinca once detay paneli acilir. */
    private fun openFilm(row: Panels.Row, alts: List<Panels.Row> = emptyList()) {
        if (section != "detail") prevSection = section
        detailRow = row
        detailAlts = alts
        showSection("detail")
    }

    private fun bindDetail(root: View) {
        val row = detailRow
        if (row == null) {
            showSites()
            return
        }
        Panels.detail(this, root, row, detailAlts, onWatch = {
            Panels.watch(this, row)
        }, onOpenSimilar = { similar -> openFilm(similar) })
    }

    /** Site Ä±zgarasÄ±na doner. */
    private fun showSites() {
        section = ""
        panelLongPress = null
        panel.removeAllViews()
        panel.visibility = View.GONE
        grid.visibility = View.VISIBLE
        // Diger bolumler gibi once menudeki Ana Sayfa satiri secili gorunur.
        findViewById<View>(R.id.side_home).requestFocus()
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

    /** Soldaki menu ile icerik arasÄ±nda odak gecisi; panelde OK'a basili tutma. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isOk = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
        val longAction: (() -> Unit)? = when {
            panelLongPress != null && inside(currentFocus, panel) -> panelLongPress
            grid.hasFocus() && grid.visibility == View.VISIBLE -> ({ favoriteMenu() })
            else -> null
        }
        if (isOk && longAction != null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (panelPressRunnable == null && !panelPressDone) {
                    val r = Runnable {
                        panelPressDone = true
                        longAction.invoke()
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
                        if (!apk.isNullOrEmpty()) downloadApk(apk, tag)
                        else if (!page.isNullOrEmpty()) open(page)
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
        val uri = data?.data
        when (requestCode) {
            Backup.REQ_SAVE -> if (resultCode == RESULT_OK && uri != null) {
                val ok = Backup.write(this, uri)
                Toast.makeText(
                    this,
                    if (ok) R.string.backup_done else R.string.backup_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
            Backup.REQ_LOAD -> if (resultCode == RESULT_OK && uri != null) {
                val ok = Backup.read(this, uri)
                Toast.makeText(
                    this,
                    if (ok) R.string.restore_done else R.string.backup_failed,
                    Toast.LENGTH_SHORT
                ).show()
                if (ok) recreate()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    /** Panel acikken GERI: site Ä±zgarasÄ±na don. */
    override fun onBackPressed() {
        when {
            section == "detail" && prevSection.isNotEmpty() -> showSection(prevSection)
            section.isNotEmpty() -> showSites()
            else -> super.onBackPressed()
        }
    }

    /** Adres ekle: adresi listeye kaydeder ve tarayicida acar. */
    private fun addAddressAndOpen(raw: String) {
        val full = if (raw.startsWith("http")) raw else "https://$raw"
        val host = Scraper.hostOf(full)
        UserSites.add(this, siteNameOf(host).ifBlank { full }, full)
        fill(SiteRepository.cached(this))
        Panels.open(this, full)
    }

    /** Alan adindan site adi: filmperdesi.org -> Filmperdesi. */
    private fun siteNameOf(host: String): String =
        host.removePrefix("www.").substringBefore(".").replaceFirstChar { it.uppercase() }

    /** Site kartinda OK'a basili tut = favori menusu (ekle/cikar, yukari/asagi tasi). */
    private fun favoriteMenu() {
        val pos = if (grid.selectedItemPosition < 0) 0 else grid.selectedItemPosition
        val site = entries.getOrNull(pos) ?: return
        val fav = Favorites.has(this, site.url)
        val isUser = UserSites.all(this).any { it.url == site.url }
        val actions = ArrayList<String>()
        actions.add(getString(if (fav) R.string.fav_remove else R.string.fav_add))
        if (fav) {
            actions.add(getString(R.string.move_up))
            actions.add(getString(R.string.move_down))
        }
        if (isUser) actions.add(getString(R.string.remove_site))

        AlertDialog.Builder(this)
            .setTitle(site.name)
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    getString(R.string.fav_add) -> {
                        Favorites.toggle(this, site.url)
                        Toast.makeText(this, R.string.fav_added, Toast.LENGTH_SHORT).show()
                    }
                    getString(R.string.fav_remove) -> {
                        Favorites.toggle(this, site.url)
                        Toast.makeText(this, R.string.fav_removed, Toast.LENGTH_SHORT).show()
                    }
                    getString(R.string.move_up) -> Favorites.move(this, site.url, true)
                    getString(R.string.move_down) -> Favorites.move(this, site.url, false)
                    getString(R.string.remove_site) -> {
                        UserSites.remove(this, site.url)
                        Toast.makeText(this, R.string.site_removed, Toast.LENGTH_SHORT).show()
                    }
                }
                val list = entries.toList()
                fill(list)
            }
            .show()
    }

    private fun fill(sites: List<Site>) {
        entries.clear()
        val order = Favorites.order(this)
        val rank = HashMap<String, Int>()
        order.forEachIndexed { i, url -> rank[url] = i }
        entries.addAll(sites.sortedBy { rank[it.url] ?: 1000 })
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

    /** Ayarlar > Uygulamayi guncelle: GitHub'daki son surumu kontrol eder. */
    private fun checkUpdateManually(status: TextView) {
        status.text = getString(R.string.update_checking)
        Updater.check(this) { tag, apk, page ->
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (tag == null) {
                    status.text = getString(R.string.update_check_failed)
                    return@runOnUiThread
                }
                val installed = Updater.installedVersion(this)
                if (!Updater.isNewer(tag, installed)) {
                    status.text = getString(R.string.update_latest, installed)
                    return@runOnUiThread
                }
                status.text = getString(R.string.update_found, tag)
                if (!apk.isNullOrEmpty()) downloadApk(apk, tag)
                else if (!page.isNullOrEmpty()) open(page)
            }
        }
    }

    /** APK'yi indirir; bitince kurulum ekranini acar. */
    private fun downloadApk(url: String, tag: String) {
        try {
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(android.net.Uri.parse(url))
                .setTitle("Movie Browser " + tag)
                .setMimeType(APK_MIME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(
                    this, Environment.DIRECTORY_DOWNLOADS, "moviebrowser-$tag.apk"
                )
            apkDownloadId = dm.enqueue(request)
            Toast.makeText(this, R.string.downloading, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(downloadReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            // kayitli degil
        }
    }

    private fun open(url: String) {
        startActivity(Intent(this, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, url))
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
