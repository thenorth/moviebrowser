package com.tvfilmizle

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/** Ana ekranin sagindaki bolum panelleri (menu sabit kalir). */
object Panels {

    const val REQ_VPN = 42

    /** Film karti. */
    data class Row(val title: String, val url: String, val poster: String, val site: String)

    private var vpnToggle: Switch? = null
    private var vpnStatus: TextView? = null
    private var vpnUpdating = false

    // ---------------------------------------------------------------- Listem
    /** Donus: OK'a basili tutulunca cagrilacak islem (listeden cikar). */
    fun watchlist(a: Activity, root: View, onOpen: (Row) -> Unit): () -> Unit {
        val grid = root.findViewById<GridView>(R.id.list)
        val empty = root.findViewById<TextView>(R.id.empty)
        val count = root.findViewById<TextView>(R.id.count)
        val rows = ArrayList<Row>()
        val adapter = PanelFilmAdapter(a, rows)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> rows.getOrNull(pos)?.let { onOpen(it) } }

        fun reload() {
            rows.clear()
            Watchlist.all(a).forEach { rows.add(Row(it.title, it.url, it.poster ?: "", it.site)) }
            adapter.notifyDataSetChanged()
            count.text = rows.size.toString() + " film"
            empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
        reload()

        return {
            val pos = grid.selectedItemPosition
            val item = rows.getOrNull(if (pos < 0) 0 else pos)
            if (item != null) {
                AlertDialog.Builder(a)
                    .setTitle(item.title)
                    .setMessage(R.string.remove_confirm)
                    .setPositiveButton(R.string.remove) { _, _ ->
                        Watchlist.remove(a, item.url)
                        Toast.makeText(a, R.string.removed_watch, Toast.LENGTH_SHORT).show()
                        reload()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    // ------------------------------------------------------------ Son izlenenler
    fun recent(a: Activity, root: View, onOpen: (Row) -> Unit): () -> Unit {
        root.findViewById<TextView>(R.id.panel_title)?.text = a.getString(R.string.recent_title)
        val grid = root.findViewById<GridView>(R.id.list)
        val empty = root.findViewById<TextView>(R.id.empty)
        val count = root.findViewById<TextView>(R.id.count)
        val rows = ArrayList<Row>()
        val adapter = PanelFilmAdapter(a, rows)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> rows.getOrNull(pos)?.let { onOpen(it) } }

        fun reload() {
            rows.clear()
            History.all(a).forEach {
                val ep = episodeOf(it.title)
                rows.add(Row(it.title, it.url, it.poster, if (ep.isEmpty()) it.site else it.site + " · " + ep))
            }
            adapter.notifyDataSetChanged()
            count.text = rows.size.toString() + " film"
            empty.text = a.getString(R.string.recent_empty)
            empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
        reload()

        return {
            val pos = grid.selectedItemPosition
            val item = rows.getOrNull(if (pos < 0) 0 else pos)
            if (item != null) {
                History.remove(a, item.url)
                reload()
            }
        }
    }

    // ---------------------------------------------------------------- Kesfet
    /** Donus: OK'a basili tutulunca cagrilacak islem (listeye ekle). */
    fun discover(a: Activity, root: View, onOpen: (Row) -> Unit): () -> Unit {
        val grid = root.findViewById<GridView>(R.id.grid)
        val empty = root.findViewById<TextView>(R.id.empty)
        val count = root.findViewById<TextView>(R.id.count)
        val chipRow = root.findViewById<LinearLayout>(R.id.filter_chips)
        val all = ArrayList<Row>()
        val shown = ArrayList<Row>()
        var filter = ""
        val adapter = PanelFilmAdapter(a, shown)
        grid.adapter = adapter

        grid.setOnItemClickListener { _, _, pos, _ -> shown.getOrNull(pos)?.let { onOpen(it) } }

        fun rebuild() {
            shown.clear()
            if (filter.isEmpty()) shown.addAll(all) else shown.addAll(all.filter { it.site == filter })
            adapter.notifyDataSetChanged()
            count.text = shown.size.toString() + " film"
            empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        }

        // Site filtreleri
        chipRow.removeAllViews()
        val d = a.resources.displayMetrics.density
        fun chip(name: String, site: String) {
            val chip = TextView(a)
            chip.text = name
            chip.textSize = 12f
            chip.setTextColor(Color.parseColor("#F2F6FA"))
            chip.gravity = android.view.Gravity.CENTER
            chip.setBackgroundResource(R.drawable.tool_button_bg)
            chip.isFocusable = true
            chip.isClickable = true
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (34 * d).toInt()
            )
            lp.rightMargin = (8 * d).toInt()
            chip.layoutParams = lp
            chip.setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
            chip.setOnClickListener {
                filter = site
                rebuild()
            }
            chipRow.addView(chip)
        }
        chip(a.getString(R.string.all_sites), "")
        SiteRepository.cached(a).forEach { chip(it.name, it.name) }

        // Onbellek varsa aninda goster
        all.addAll(DiscoverCache.load(a))
        rebuild()
        if (DiscoverCache.isFresh(a)) return { addToWatch(a, shown, grid) }

        empty.text = a.getString(R.string.films_loading)
        empty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE

        val seen = HashSet<String>()
        val sites = SiteRepository.cached(a)
        val done = AtomicInteger(0)
        for (s in sites) {
            Thread {
                Scraper.load(s.url, 6000) { page ->
                    a.runOnUiThread {
                        var added = 0
                        for (f in page.films.take(8)) {
                            if (!seen.add(f.url)) continue
                            all.add(Row(f.title, f.url, f.poster, s.name))
                            added++
                        }
                        if (added > 0) {
                            interleave(all)
                            rebuild()
                        }
                        if (done.incrementAndGet() == sites.size) DiscoverCache.save(a, all)
                    }
                }
            }.start()
        }

        return { addToWatch(a, shown, grid) }
    }

    private fun addToWatch(a: Activity, shown: List<Row>, grid: GridView) {
        val pos = grid.selectedItemPosition
        val r = shown.getOrNull(if (pos < 0) 0 else pos) ?: return
        if (r.url.isEmpty()) return
        if (Watchlist.has(a, r.url)) {
            Toast.makeText(a, R.string.in_watch, Toast.LENGTH_SHORT).show()
        } else {
            Watchlist.add(a, Watchlist.Item(r.title, r.url, r.site, r.poster))
            Toast.makeText(a, R.string.added_watch, Toast.LENGTH_SHORT).show()
        }
    }

    // --------------------------------------------------------------- Detay
    fun detail(a: Activity, root: View, row: Row, onWatch: () -> Unit, onAdd: () -> Unit) {
        root.findViewById<TextView>(R.id.detail_title).text = row.title
        root.findViewById<TextView>(R.id.detail_site).text = row.site
        val img = root.findViewById<ImageView>(R.id.detail_poster)
        if (row.poster.isBlank()) {
            img.setImageDrawable(null)
        } else {
            val cached = Images.cached(row.poster)
            if (cached != null) img.setImageBitmap(cached)
            else Images.load(a, row.poster) { bmp -> if (bmp != null) img.setImageBitmap(bmp) }
        }
        root.findViewById<View>(R.id.detail_watch).setOnClickListener { onWatch() }
        root.findViewById<View>(R.id.detail_add).setOnClickListener { onAdd() }
        root.findViewById<View>(R.id.detail_watch).requestFocus()
    }

    // ---------------------------------------------------------------- Site durumu
    data class Check(val code: Int, val ms: Long)

    private var statusCache: Map<String, Check>? = null
    private var statusTime = 0L

    fun status(a: Activity, root: View) {
        val sites = ArrayList(SiteRepository.cached(a))
        val results = HashMap<String, Check>()
        val list = root.findViewById<ListView>(R.id.list)
        val summary = root.findViewById<TextView>(R.id.status_summary)
        val adapter = StatusAdapter(a, sites, results)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            sites.getOrNull(pos)?.let { open(a, it.url) }
        }

        fun refreshSummary(testing: Boolean, done: Int, total: Int) {
            if (testing) {
                summary.text = a.getString(R.string.status_testing_fmt, done, total)
            } else {
                val ok = results.values.count { it.code in 200..399 }
                summary.text = a.getString(R.string.status_summary_fmt, ok, total)
            }
        }

        fun sortByState() {
            sites.sortBy {
                when (val c = results[it.url]?.code ?: -1) {
                    in 200..399 -> 0
                    403, 429, 503 -> 1
                    -2 -> 2
                    else -> 3
                }
            }
            adapter.notifyDataSetChanged()
        }

        fun runTest() {
            results.clear()
            sites.forEach { results[it.url] = Check(-2, 0) }
            adapter.notifyDataSetChanged()
            refreshSummary(true, 0, sites.size)
            val done = AtomicInteger(0)
            val total = sites.size
            for (s in sites) {
                Thread {
                    val t0 = System.currentTimeMillis()
                    val code = ping(s.url)
                    val ms = System.currentTimeMillis() - t0
                    a.runOnUiThread {
                        results[s.url] = Check(code, ms)
                        adapter.notifyDataSetChanged()
                        val d = done.incrementAndGet()
                        refreshSummary(d < total, d, total)
                        if (d == total) {
                            statusCache = HashMap(results)
                            statusTime = System.currentTimeMillis()
                            sortByState()
                            refreshSummary(false, total, total)
                        }
                    }
                }.start()
            }
        }

        val cached = statusCache
        if (cached != null && System.currentTimeMillis() - statusTime < 10 * 60 * 1000L) {
            results.putAll(cached)
            sortByState()
            refreshSummary(false, sites.size, sites.size)
        } else {
            runTest()
        }

        root.findViewById<View>(R.id.btn_test).setOnClickListener { runTest() }
    }

    // ---------------------------------------------------------------- Ayarlar
    fun settings(a: Activity, root: View) {
        vpnToggle = root.findViewById(R.id.vpn_switch)
        vpnStatus = root.findViewById(R.id.vpn_status)
        val toggle = vpnToggle ?: return
        val status = vpnStatus ?: return

        toggle.isChecked = Warp.connected
        status.text = a.getString(if (Warp.connected) R.string.vpn_on else R.string.vpn_off)
        toggle.setOnCheckedChangeListener { _, isChecked ->
            if (vpnUpdating) return@setOnCheckedChangeListener
            if (isChecked) enableVpn(a) else disableVpn(a, status)
        }

        val ua = root.findViewById<Switch>(R.id.ua_switch)
        ua.isChecked = Prefs.desktopUa(a)
        ua.setOnCheckedChangeListener { _, isChecked -> Prefs.setDesktopUa(a, isChecked) }

        val fs = root.findViewById<Switch>(R.id.fs_switch)
        fs.isChecked = Prefs.autoFullscreen(a)
        fs.setOnCheckedChangeListener { _, isChecked -> Prefs.setAutoFullscreen(a, isChecked) }

        val sleepValue = root.findViewById<TextView>(R.id.sleep_value)
        sleepValue.text = SleepTimer.remainingText(a)
        root.findViewById<View>(R.id.sleep_row).setOnClickListener { pickSleep(a, sleepValue) }
        root.findViewById<View>(R.id.clear_row).setOnClickListener { confirmClear(a) }

        val version = try {
            a.packageManager.getPackageInfo(a.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        root.findViewById<TextView>(R.id.version).text = a.getString(R.string.version_fmt, version)
    }

    /** VPN izin penceresinden donus. */
    fun vpnResult(a: Activity, requestCode: Int, resultCode: Int) {
        if (requestCode != REQ_VPN) return
        if (resultCode == Activity.RESULT_OK) {
            startWarp(a)
        } else {
            vpnUpdating = true
            vpnToggle?.isChecked = false
            vpnUpdating = false
            vpnStatus?.text = a.getString(R.string.vpn_off)
        }
    }

    private fun enableVpn(a: Activity) {
        if (Warp.connected) return
        val prep = VpnService.prepare(a)
        if (prep != null) {
            a.startActivityForResult(prep, REQ_VPN)
            return
        }
        startWarp(a)
    }

    private fun startWarp(a: Activity) {
        vpnStatus?.text = a.getString(R.string.vpn_connecting)
        vpnToggle?.isEnabled = false
        Warp.connect(a) { ok, error ->
            a.runOnUiThread {
                vpnToggle?.isEnabled = true
                vpnUpdating = true
                vpnToggle?.isChecked = ok
                vpnUpdating = false
                vpnStatus?.text =
                    if (ok) a.getString(R.string.vpn_on) else a.getString(R.string.vpn_failed, error)
            }
        }
    }

    private fun disableVpn(a: Activity, status: TextView) {
        Warp.disconnect()
        status.text = a.getString(R.string.vpn_off)
    }

    private fun pickSleep(a: Activity, value: TextView) {
        val labels = arrayOf(a.getString(R.string.sleep_off), "30 dk", "60 dk", "90 dk")
        val minutes = intArrayOf(0, 30, 60, 90)
        AlertDialog.Builder(a)
            .setTitle(R.string.sleep_title)
            .setItems(labels) { _, which ->
                SleepTimer.set(a, minutes[which])
                value.text = SleepTimer.remainingText(a)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmClear(a: Activity) {
        AlertDialog.Builder(a)
            .setMessage(R.string.clear_confirm)
            .setPositiveButton(R.string.clear_title) { _, _ ->
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                WebView(a).apply {
                    clearCache(true)
                    clearFormData()
                    destroy()
                }
                Toast.makeText(a, R.string.clear_done, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- Arama / adres
    /** Panel icindeki tek satirlik giris alani. */
    fun input(
        a: Activity,
        root: View,
        titleRes: Int,
        hintRes: Int,
        actionRes: Int,
        onSubmit: (String) -> Unit
    ) {
        root.findViewById<TextView>(R.id.title).text = a.getString(titleRes)
        val input = root.findViewById<EditText>(R.id.input)
        input.hint = a.getString(hintRes)
        val action = root.findViewById<TextView>(R.id.action)
        action.setText(actionRes)
        val submit = {
            val q = input.text.toString().trim()
            if (q.isNotEmpty()) onSubmit(q)
        }
        action.setOnClickListener { submit() }
        input.setOnEditorActionListener { _, _, _ ->
            submit()
            true
        }
        input.requestFocus()
    }

    // ---------------------------------------------------------------- yardimci
    /** "3. Sezon 5. Bölüm" -> "S3B5" (dizi bolum takibi icin kisa etiket). */
    private val EP_RX = Regex("(?i)(\\d+)\\s*\\.?\\s*sezon\\D{0,12}?(\\d+)\\s*\\.?\\s*b[oö]l[uü]m")

    private fun episodeOf(title: String): String {
        val m = EP_RX.find(title) ?: return ""
        return "S" + m.groupValues[1] + "B" + m.groupValues[2]
    }

    /** Siteleri sirayla karistirir: her siteden birer film, sonra ikinciler... */
    private fun interleave(rows: MutableList<Row>) {
        val groups = LinkedHashMap<String, MutableList<Row>>()
        for (r in rows) groups.getOrPut(r.site) { ArrayList() }.add(r)
        if (groups.size < 2) return
        val merged = ArrayList<Row>(rows.size)
        var i = 0
        while (true) {
            var added = false
            for (g in groups.values) {
                if (i < g.size) {
                    merged.add(g[i])
                    added = true
                }
            }
            if (!added) break
            i++
        }
        rows.clear()
        rows.addAll(merged)
    }

    fun open(a: Activity, url: String) {
        a.startActivity(Intent(a, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, url))
    }

    /** Panellerden film acarken gecmise yaz. */
    fun watch(a: Activity, row: Row) {
        if (row.title.isNotEmpty()) History.add(a, row.title, row.url, row.poster, row.site)
        open(a, row.url)
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

/** Listem / Kesfet / Son izlenenler icin poster karti. */
private class PanelFilmAdapter(private val ctx: Context, private val items: List<Panels.Row>) : BaseAdapter() {

    private val inflater = LayoutInflater.from(ctx)

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_film, parent, false)
        val row = items[position]
        val img = view.findViewById<ImageView>(R.id.film_poster)
        view.findViewById<TextView>(R.id.film_title).text = row.title
        view.findViewById<TextView>(R.id.film_site).apply {
            text = row.site
            visibility = if (row.site.isBlank()) View.GONE else View.VISIBLE
        }

        if (row.poster.isBlank()) {
            img.setImageDrawable(null)
            img.setBackgroundColor(Color.parseColor("#1A2230"))
        } else {
            val cached = Images.cached(row.poster)
            if (cached != null) {
                img.setImageBitmap(cached)
            } else {
                img.setImageDrawable(null)
                img.setBackgroundColor(Color.parseColor("#1A2230"))
            }
            img.tag = row.poster
            Images.load(ctx, row.poster) { bmp ->
                if (bmp != null && img.tag == row.poster) {
                    img.setBackgroundColor(Color.TRANSPARENT)
                    img.setImageBitmap(bmp)
                }
            }
        }
        return view
    }
}

/** Site durumu satiri. */
private class StatusAdapter(
    private val ctx: Context,
    private val items: List<Site>,
    private val results: Map<String, Panels.Check>
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(ctx)

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_status, parent, false)
        val site = items[position]
        val check = results[site.url]
        view.findViewById<TextView>(R.id.status_name).text = site.name

        val host = try {
            android.net.Uri.parse(site.url).host ?: ""
        } catch (e: Exception) {
            ""
        }
        view.findViewById<TextView>(R.id.status_url).text =
            if (host.isNotEmpty() && SiteHealth.isBlocked(ctx, host)) {
                host + "  ·  " + ctx.getString(R.string.blocked_flag)
            } else {
                host
            }

        val dot = view.findViewById<TextView>(R.id.status_dot)
        val text = view.findViewById<TextView>(R.id.status_text)
        when {
            check == null -> {
                dot.setTextColor(Color.parseColor("#8B98A6"))
                text.text = ctx.getString(R.string.status_unknown)
            }
            check.code == -2 -> {
                dot.setTextColor(Color.parseColor("#8B98A6"))
                text.text = ctx.getString(R.string.status_testing)
            }
            check.code == 0 -> {
                dot.setTextColor(Color.parseColor("#FF6B6B"))
                text.text = ctx.getString(R.string.status_blocked)
            }
            check.code in 200..399 -> {
                dot.setTextColor(Color.parseColor("#59D986"))
                text.text = ctx.getString(R.string.status_ok) + " · " + check.code + " · " + check.ms + " ms"
            }
            check.code == 403 || check.code == 429 || check.code == 503 -> {
                dot.setTextColor(Color.parseColor("#FF9F1A"))
                text.text = ctx.getString(R.string.status_protected) + " · " + check.code
            }
            else -> {
                dot.setTextColor(Color.parseColor("#FF6B6B"))
                text.text = ctx.getString(R.string.status_error) + " · " + check.code
            }
        }
        return view
    }
}
