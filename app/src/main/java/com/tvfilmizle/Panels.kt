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
import android.view.inputmethod.InputMethodManager
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
    data class Row(
        val title: String,
        val url: String,
        val poster: String,
        val site: String,
        val meta: String = ""
    )

    private var vpnToggle: Switch? = null
    private var vpnStatus: TextView? = null
    private var vpnUpdating = false

    /** Son Keşfet listesi: benzer film onerileri yalniz kaynagi olanlardan secilir. */
    @Volatile
    private var feed: List<Row> = emptyList()

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

        // Arama alani
        val filter = root.findViewById<EditText>(R.id.filter_input)
        filter.visibility = View.VISIBLE
        filter.hint = a.getString(R.string.watch_filter_hint)
        var query = ""
        val d = a.resources.displayMetrics.density
        grid.setPadding((38 * d).toInt(), (130 * d).toInt(), (38 * d).toInt(), (80 * d).toInt())

        fun reload() {
            rows.clear()
            Watchlist.all(a)
                .map { Row(it.title, it.url, it.poster ?: "", it.site) }
                .filter { query.isEmpty() || it.title.lowercase(java.util.Locale("tr")).contains(query) }
                .forEach { rows.add(it) }
            adapter.notifyDataSetChanged()
            count.text = rows.size.toString() + " film"
            empty.text = a.getString(R.string.watch_empty)
            empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
        filter.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                query = s?.toString()?.trim()?.lowercase(java.util.Locale("tr")) ?: ""
                reload()
            }
        })
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
                val label = if (ep.isEmpty()) it.site else it.site + " · " + ep
                rows.add(Row(it.title, it.url, it.poster, label))
            }
            adapter.notifyDataSetChanged()
            // Ilk kart: kaldigin yerden devam.
            if (rows.isNotEmpty()) {
                val first = rows[0]
                rows[0] = first.copy(site = "▶ " + a.getString(R.string.resume_badge) + " · " + first.site)
            }
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
    fun discover(a: Activity, root: View, onOpen: (Row, List<Row>) -> Unit): () -> Unit {
        val grid = root.findViewById<GridView>(R.id.grid)
        val empty = root.findViewById<TextView>(R.id.empty)
        val count = root.findViewById<TextView>(R.id.count)
        val chipRow = root.findViewById<LinearLayout>(R.id.filter_chips)
        val all = ArrayList<Row>()
        val shown = ArrayList<Row>()
        var siteFilter = ""
        var typeFilter = 0 // 0: hepsi, 1: film, 2: dizi
        val alts = HashMap<String, MutableList<Row>>()
        val adapter = PanelFilmAdapter(a, shown)
        grid.adapter = adapter

        grid.setOnItemClickListener { _, _, pos, _ ->
            shown.getOrNull(pos)?.let { onOpen(it, alts[it.title] ?: emptyList()) }
        }

        fun rebuild() {
            shown.clear()
            for (r in all) {
                if (siteFilter.isNotEmpty() && !r.site.equals(siteFilter, true)) continue
                val series = Regex("(?i)bölüm|bolum|sezon|dizi").containsMatchIn(r.title)
                if (typeFilter == 1 && series) continue
                if (typeFilter == 2 && !series) continue
                shown.add(r)
            }
            adapter.notifyDataSetChanged()
            count.text = shown.size.toString() + " film"
            feed = ArrayList(all)
            if (shown.isEmpty()) {
                empty.text = a.getString(
                    if (all.isEmpty()) R.string.films_loading else R.string.no_results
                )
                empty.visibility = View.VISIBLE
            } else {
                empty.visibility = View.GONE
            }
        }

        // Filtre cipleri: tur (film/dizi) + sadece sonucu olan siteler
        chipRow.removeAllViews()
        val d = a.resources.displayMetrics.density
        var chipKey = ""
        fun chip(name: String, onClick: () -> Unit) {
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
            chip.setOnClickListener { onClick() }
            chipRow.addView(chip)
        }
        fun rebuildChips() {
            chipRow.removeAllViews()
            chip(a.getString(R.string.all_sites)) {
                siteFilter = ""
                typeFilter = 0
                rebuild()
            }
            chip(a.getString(R.string.films_filter)) {
                siteFilter = ""
                typeFilter = 1
                rebuild()
            }
            chip(a.getString(R.string.series_filter)) {
                siteFilter = ""
                typeFilter = 2
                rebuild()
            }
            val names = all.map { it.site }.distinct()
            if (siteFilter.isNotEmpty() && names.none { it.equals(siteFilter, true) }) siteFilter = ""
            names.forEach { name ->
                chip(name) {
                    siteFilter = if (siteFilter == name) "" else name
                    typeFilter = 0
                    rebuild()
                }
            }
            chipKey = names.joinToString("|")
        }
        fun maybeRefreshChips() {
            val key = all.map { it.site }.distinct().joinToString("|")
            if (key != chipKey) rebuildChips()
        }

        // Onbellek varsa aninda goster
        all.addAll(DiscoverCache.load(a))
        rebuild()
        rebuildChips()
        if (DiscoverCache.isFresh(a)) return { addToWatch(a, shown, grid) }

        empty.text = a.getString(R.string.films_loading)
        empty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE

        val sites = SiteRepository.cached(a)
        val done = AtomicInteger(0)
        for (s in sites) {
            Thread {
                Scraper.load(s.url, 6000) { page ->
                    a.runOnUiThread {
                        var added = 0
                        for (f in page.films.take(8)) {
                            // Ayni film/dizi baska sitede de olabilir: baslik anahtariyla tekille.
                            val key = keyOf(f.title)
                            if (key.isEmpty()) continue
                            val existing = all.firstOrNull { keyOf(it.title) == key }
                            if (existing != null) {
                                if (existing.site != s.name) {
                                    alts.getOrPut(existing.title) { ArrayList() }
                                        .add(Row(f.title, f.url, f.poster, s.name))
                                }
                                continue
                            }
                            all.add(Row(f.title, f.url, f.poster, s.name))
                            added++
                        }
                        if (added > 0) {
                            interleave(all)
                            maybeRefreshChips()
                            rebuild()
                        }
                        if (done.incrementAndGet() == sites.size) {
                            enrich(a, all) { rebuild() }
                            DiscoverCache.save(a, all)
                        }
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
    fun detail(
        a: Activity,
        root: View,
        row: Row,
        alts: List<Row> = emptyList(),
        onWatch: () -> Unit,
        onOpenSimilar: (Row) -> Unit = {}
    ) {
        val titleView = root.findViewById<TextView>(R.id.detail_title)
        val siteView = root.findViewById<TextView>(R.id.detail_site)
        val info = root.findViewById<TextView>(R.id.detail_info)
        val img = root.findViewById<ImageView>(R.id.detail_poster)

        titleView.text = row.title
        siteView.text = row.site
        info.text = a.getString(R.string.info_loading)

        // Kaynak URL yoksa (benzer filmler) izleme/liste dugmeleri gizlenir.
        val watchBtn = root.findViewById<View>(R.id.detail_watch)
        val addBtn = root.findViewById<View>(R.id.detail_add)
        if (row.url.isBlank()) {
            watchBtn.visibility = View.GONE
            addBtn.visibility = View.GONE
            root.findViewById<TextView>(R.id.detail_hint).text = a.getString(R.string.no_source_hint)
        }

        fun loadPoster(url: String) {
            if (url.isBlank()) {
                img.setImageDrawable(null)
                return
            }
            val cached = Images.cached(url)
            if (cached != null) {
                img.setImageBitmap(cached)
            } else {
                img.setImageDrawable(null)
                Images.load(a, url) { bmp -> if (bmp != null) img.setImageBitmap(bmp) }
            }
        }
        loadPoster(row.poster)

        // Ayni film baska sitelerde de varsa kaynak degistirme
        if (alts.isNotEmpty()) {
            val btn = root.findViewById<TextView>(R.id.detail_sources)
            btn.visibility = View.VISIBLE
            btn.setOnClickListener {
                AlertDialog.Builder(a)
                    .setTitle(R.string.other_sources)
                    .setItems(alts.map { it.site }.toTypedArray()) { _, w ->
                        watch(a, alts[w])
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

        // Once TMDB (film/dizi), yoksa Turkce Vikipedi.
        Thread {
            val isSeries = Tmdb.looksLikeSeries(row.title, row.url)
            val tmdb = Tmdb.load(row.title, isSeries)
            val credits = if (tmdb != null) Tmdb.credits(tmdb.id, tmdb.mediaType) else null
            // Benzer filmler: yalniz elimizde kaynagi (URL) olanlar
            val similar = if (tmdb != null) {
                val lib = feed
                Tmdb.recommendations(tmdb.id, tmdb.mediaType).mapNotNull { s ->
                    val key = keyOf(s.title)
                    val hit = lib.firstOrNull { keyOf(it.title) == key } ?: return@mapNotNull null
                    Row(
                        hit.title,
                        hit.url,
                        hit.poster.ifBlank { s.poster },
                        hit.site,
                        s.year
                    )
                }
            } else emptyList<Row>()
            val wiki = if (tmdb == null) FilmInfo.load(row.title) else null
            a.runOnUiThread {
                if (info.parent == null) return@runOnUiThread
                if (tmdb != null) {
                    titleView.text =
                        if (tmdb.year.isEmpty()) tmdb.title else tmdb.title + " (" + tmdb.year + ")"
                    info.text = tmdb.overview
                    siteView.text = when {
                        tmdb.rating.isEmpty() -> row.site
                        row.site.isEmpty() -> "★ " + tmdb.rating
                        else -> row.site + " · ★ " + tmdb.rating
                    }
                    if (tmdb.poster.isNotEmpty()) loadPoster(tmdb.poster)

                    // Yonetmen + oyuncular: aciklamanin altinda
                    if (credits != null) {
                        val parts = ArrayList<String>()
                        if (credits.names.isNotEmpty()) {
                            parts.add(credits.role + ": " + credits.names.joinToString(", "))
                        }
                        if (credits.cast.isNotEmpty()) {
                            parts.add("Oyuncular: " + credits.cast.take(5).joinToString(", "))
                        }
                        val castView = root.findViewById<TextView>(R.id.detail_cast)
                        castView.text = parts.joinToString("   ·   ")
                        castView.visibility = View.VISIBLE
                    }

                    if (similar.isNotEmpty()) showSimilar(a, root, similar, onOpenSimilar, row.url.isBlank())
                } else if (!wiki.isNullOrBlank()) {
                    info.text = wiki
                } else {
                    info.text = a.getString(R.string.info_none)
                }
            }
        }.start()

        root.findViewById<View>(R.id.detail_watch).setOnClickListener { onWatch() }

        // Listeme ekle / Listemden cikar (duruma gore)
        val add = root.findViewById<TextView>(R.id.detail_add)
        fun refreshAdd() {
            add.setText(
                if (Watchlist.has(a, row.url)) R.string.remove_watch else R.string.add_watch
            )
        }
        refreshAdd()
        add.setOnClickListener {
            if (Watchlist.has(a, row.url)) {
                Watchlist.remove(a, row.url)
                Toast.makeText(a, R.string.removed_watch, Toast.LENGTH_SHORT).show()
            } else {
                Watchlist.add(a, Watchlist.Item(row.title, row.url, row.site, row.poster))
                Toast.makeText(a, R.string.added_watch, Toast.LENGTH_SHORT).show()
            }
            refreshAdd()
        }

        // Dizi ise bolum listesi
        val eps = root.findViewById<TextView>(R.id.detail_eps)
        eps.visibility = View.GONE
        if (Regex("(?i)bölüm|bolum|sezon").containsMatchIn(row.title) || row.url.contains("/dizi", true)) {
            Scraper.episodes(row.url) { list ->
                a.runOnUiThread {
                    if (eps.parent == null || list.isEmpty()) return@runOnUiThread
                    eps.visibility = View.VISIBLE
                    eps.setOnClickListener {
                        AlertDialog.Builder(a)
                            .setTitle(R.string.episodes)
                            .setItems(list.map { it.first }.toTypedArray()) { _, w ->
                                val (label, url) = list[w]
                                History.add(a, label, url, row.poster, row.site)
                                open(a, url)
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                }
            }
        }

        if (row.url.isNotBlank()) root.findViewById<View>(R.id.detail_watch).requestFocus()
    }

    /** Benzer filmler: detayin altinda kucuk afis seridi. */
    private fun showSimilar(
        a: Activity,
        root: View,
        list: List<Row>,
        onOpen: (Row) -> Unit,
        focusFirst: Boolean = false
    ) {
        val row = root.findViewById<LinearLayout>(R.id.detail_similar_row)
        root.findViewById<View>(R.id.detail_similar_title).visibility = View.VISIBLE
        root.findViewById<View>(R.id.detail_similar_scroll).visibility = View.VISIBLE
        row.removeAllViews()
        val d = a.resources.displayMetrics.density
        val w = (86 * d).toInt()
        val h = (124 * d).toInt()
        for (s in list) {
            val card = LinearLayout(a)
            card.orientation = LinearLayout.VERTICAL
            card.isFocusable = true
            card.isClickable = true
            card.layoutParams = LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { rightMargin = (10 * d).toInt() }

            val img = ImageView(a)
            img.layoutParams = LinearLayout.LayoutParams(w, h)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            img.setBackgroundColor(Color.parseColor("#1A2230"))
            if (s.poster.isNotEmpty()) {
                Images.load(a, s.poster) { bmp -> if (bmp != null) img.setImageBitmap(bmp) }
            }
            card.addView(img)

            val title = TextView(a)
            val year = s.meta
            title.text = if (year.isEmpty()) s.title else s.title + " (" + year + ")"
            title.textSize = 9.5f
            title.setTextColor(Color.parseColor("#B8C2CC"))
            title.maxLines = 2
            title.ellipsize = android.text.TextUtils.TruncateAt.END
            title.layoutParams = LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (4 * d).toInt() }
            card.addView(title)

            card.setOnFocusChangeListener { v, has ->
                v.setBackgroundResource(if (has) R.drawable.tool_button_bg else 0)
            }
            card.setOnClickListener {
                onOpen(s)
            }
            row.addView(card)
        }
        if (focusFirst) row.getChildAt(0)?.requestFocus()
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
        initial: String = "",
        note: String = "",
        onSubmit: (String) -> Unit
    ) {
        root.findViewById<TextView>(R.id.title).text = a.getString(titleRes)
        val input = root.findViewById<EditText>(R.id.input)
        input.hint = a.getString(hintRes)
        if (initial.isNotEmpty()) {
            input.setText(initial)
            input.setSelection(initial.length)
        }
        val noteView = root.findViewById<TextView>(R.id.input_note)
        if (note.isNotEmpty()) {
            noteView.text = note
            noteView.visibility = View.VISIBLE
        }
        val action = root.findViewById<TextView>(R.id.action)
        action.setText(actionRes)
        val submit = {
            val q = input.text.toString().trim()
            // Sadece "https://" yaziliysa gonderilmesin
            if (q.isNotEmpty() && q != "https://" && q != "http://") onSubmit(q)
        }
        action.setOnClickListener { submit() }
        input.setOnEditorActionListener { _, _, _ ->
            submit()
            true
        }
        input.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                val imm = a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    // ---------------------------------------------------------------- yardimci
    /** Kartlarda yil/puan: TMDB'den ilk 24 film icin arka planda. */
    private fun enrich(a: Activity, rows: ArrayList<Row>, onChange: () -> Unit) {
        if (!Tmdb.enabled()) return
        for (r in rows.take(24)) {
            Thread {
                val info = Tmdb.load(r.title, Tmdb.looksLikeSeries(r.title)) ?: return@Thread
                val parts = ArrayList<String>()
                if (info.year.isNotEmpty()) parts.add(info.year)
                if (info.rating.isNotEmpty()) parts.add("★ " + info.rating)
                if (parts.isEmpty()) return@Thread
                val meta = parts.joinToString(" · ")
                a.runOnUiThread {
                    val i = rows.indexOfFirst { it.url == r.url }
                    if (i >= 0) {
                        rows[i] = rows[i].copy(meta = meta)
                        onChange()
                    }
                }
            }.start()
        }
    }

    /** Baslik normalizasyonu: ayni film farkli sitelerde tekrarlanmasin. */
    private fun keyOf(title: String): String {
        var t = title.lowercase(java.util.Locale("tr"))
        t = t.replace(Regex("\\(\\d{4}\\)"), " ")
        t = t.replace(
            Regex("(?i)\\b(filmi|film|full|hd|izle|türkçe|turkce|dublaj|altyazılı|altyazili|720p|1080p|4k)\\b"),
            " "
        )
        t = t.replace(Regex("[^a-z0-9çğıöşü ]"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

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
            text = if (row.meta.isEmpty()) row.site else row.site + " · " + row.meta
            visibility = if (row.site.isBlank() && row.meta.isBlank()) View.GONE else View.VISIBLE
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
