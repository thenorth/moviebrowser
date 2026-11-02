package com.tvfilmizle

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.text.Editable
import android.text.TextWatcher
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
        val meta: String = "",
        val isSeries: Boolean = false,
        val fresh: Boolean = false
    )

    private var vpnToggle: Switch? = null
    private var vpnStatus: TextView? = null
    private var vpnUpdating = false

    /** Son Keşfet listesi: benzer film onerileri yalniz kaynagi olanlardan secilir. */
    @Volatile
    private var feed: List<Row> = emptyList()

    /** Bolum listeleri: "sonraki bolum" ve otomatik gecis icin. */
    private val episodeLists = HashMap<String, Pair<Row, List<Pair<String, String>>>>()

    /** Verilen bolumden sonraki bolum (bilinen dizi listesinden). */
    fun autoNextFor(url: String): AutoNext.Item? {
        synchronized(episodeLists) {
            for ((_, entry) in episodeLists) {
                val (series, list) = entry
                val i = list.indexOfFirst { it.second == url }
                if (i < 0) continue
                if (i + 1 >= list.size) return null
                val next = list[i + 1]
                return AutoNext.Item(
                    next.first, next.second, series.title, series.url,
                    series.poster, series.site, keyOf(series.title)
                )
            }
        }
        return null
    }

    @Volatile
    private var warming = false

    /** Uygulama acilirken akisi arka planda hazirla (benzer filmler / arama bos kalmasin). */
    fun warmFeed(ctx: Context, onDone: (() -> Unit)? = null) {
        if (onDone != null) synchronized(warmWaiters) { warmWaiters.add(onDone) }
        if (warming) return
        val cached = DiscoverCache.load(ctx)
        if (cached.isNotEmpty()) feed = cached
        if (cached.isNotEmpty() && DiscoverCache.isFresh(ctx)) {
            notifyWarm()
            return
        }

        val app = ctx.applicationContext
        val sites = SiteRepository.cached(app)
        if (sites.isEmpty()) {
            notifyWarm()
            return
        }
        warming = true
        val run = DiscoverCache.runIndex(app)
        val all = ArrayList<Row>()
        val seen = HashSet<String>()
        val done = AtomicInteger(0)
        for (s in sites) {
            Thread {
                Scraper.load(s.url, 6000) { page ->
                    synchronized(all) {
                        for (f in rotated(page.films, run, 10)) {
                            val key = keyOf(f.title)
                            if (key.isEmpty() || !seen.add(key)) continue
                            all.add(Row(f.title, f.url, f.poster, s.name))
                        }
                    }
                    if (done.incrementAndGet() == sites.size) {
                        synchronized(all) { interleave(all) }
                        feed = ArrayList(all)
                        DiscoverCache.save(app, all)
                        warming = false
                        notifyWarm()
                    }
                }
            }.start()
        }
    }

    private val warmWaiters = ArrayList<() -> Unit>()

    /** Bekleyenlere ana is parcaciginda haber ver. */
    private fun notifyWarm() {
        if (warmWaiters.isEmpty()) return
        val list = ArrayList(warmWaiters)
        warmWaiters.clear()
        android.os.Handler(android.os.Looper.getMainLooper()).post { for (cb in list) cb() }
    }

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
        grid.setPadding((26 * d).toInt(), (130 * d).toInt(), (26 * d).toInt(), (80 * d).toInt())

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
            History.all(a).forEachIndexed { idx, it ->
                val ep = episodeOf(it.title)
                val meta = when {
                    idx == 0 && ep.isEmpty() -> "▶ DEVAM"
                    idx == 0 -> "▶ DEVAM · " + ep
                    else -> ep
                }
                rows.add(Row(it.title, it.url, it.poster, it.site, meta))
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

    // ------------------------------------------------------------ Takip edilen diziler
    fun series(a: Activity, root: View, onOpen: (Row) -> Unit): () -> Unit {
        root.findViewById<TextView>(R.id.panel_title)?.text = a.getString(R.string.series_panel)
        val grid = root.findViewById<GridView>(R.id.list)
        val empty = root.findViewById<TextView>(R.id.empty)
        val count = root.findViewById<TextView>(R.id.count)
        val rows = ArrayList<Row>()
        val adapter = PanelFilmAdapter(a, rows)
        grid.adapter = adapter

        fun reload() {
            rows.clear()
            val seen = HashSet<String>()
            History.followed(a).forEach {
                seen.add(it.key)
                val label = it.label.ifBlank { a.getString(R.string.series_no_ep) }
                rows.add(Row(it.title, it.url, it.poster, it.site, metaOf(a, it.key, label)))
            }
            // Listem'e eklenen diziler takip edilmese de burada gorunur.
            Watchlist.all(a).forEach { w ->
                val k = keyOf(w.title)
                if (k.isEmpty() || !seen.add(k)) return@forEach
                if (!Tmdb.looksLikeSeries(w.title, w.url)) return@forEach
                val label = History.lastEpisode(a, k).orEmpty()
                    .ifBlank { a.getString(R.string.series_no_ep) }
                rows.add(Row(w.title, w.url, w.poster.orEmpty(), w.site, metaOf(a, k, label)))
            }
            adapter.notifyDataSetChanged()
            count.text = rows.size.toString() + " dizi"
            empty.text = a.getString(R.string.series_empty)
            empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }

        grid.setOnItemClickListener { _, _, pos, _ ->
            rows.getOrNull(pos)?.let {
                val k = keyOf(it.title)
                if (History.hasNew(a, k)) {
                    History.setHasNew(a, k, false)
                    reload()
                }
                onOpen(it)
            }
        }
        reload()
        if (System.currentTimeMillis() - History.checkedAt(a) > NEW_EP_CHECK_MS) {
            History.setCheckedAt(a, System.currentTimeMillis())
            checkNewEpisodes(a) { if (grid.adapter != null) reload() }
        }

        return {
            val pos = grid.selectedItemPosition
            val item = rows.getOrNull(if (pos < 0) 0 else pos)
            if (item != null) {
                History.unfollow(a, keyOf(item.title))
                // Listem'den de cikar; yoksa panelde yeniden belirir.
                if (item.url.isNotEmpty()) Watchlist.remove(a, item.url)
                Toast.makeText(a, R.string.unfollowed, Toast.LENGTH_SHORT).show()
                reload()
            }
        }
    }

    // ---------------------------------------------------------------- Kesfet
    /** Donus: OK'a basili tutulunca cagrilacak islem (listeye ekle). */
    fun discover(a: Activity, root: View, onOpen: (Row, List<Row>) -> Unit, onRefresh: () -> Unit): () -> Unit {
        val grid = root.findViewById<GridView>(R.id.grid)
        val empty = root.findViewById<TextView>(R.id.empty)
        val count = root.findViewById<TextView>(R.id.count)
        val chipRow = root.findViewById<LinearLayout>(R.id.filter_chips)
        val all = ArrayList<Row>()
        val shown = ArrayList<Row>()
        var siteFilter = ""
        var typeFilter = 0 // 0: hepsi, 1: film, 2: dizi
        var freshOnly = false
        var genreRows: ArrayList<Row>? = null
        var genreActive = ""
        var sortRating = false
        val genreSites = LinkedHashMap<String, MutableList<Pair<String, String>>>()
        val alts = HashMap<String, MutableList<Row>>()
        val adapter = PanelFilmAdapter(a, shown)
        grid.adapter = adapter

        grid.setOnItemClickListener { _, _, pos, _ ->
            shown.getOrNull(pos)?.let { onOpen(it, alts[it.title] ?: emptyList()) }
        }

        fun rebuild() {
            shown.clear()
            val base = genreRows ?: all
            for (r in base) {
                if (siteFilter.isNotEmpty() && !r.site.equals(siteFilter, true)) continue
                if (freshOnly && !r.fresh) continue
                val series = r.isSeries || Regex("(?i)bölüm|bolum|sezon|dizi").containsMatchIn(r.title)
                if (typeFilter == 1 && series) continue
                if (typeFilter == 2 && !series) continue
                shown.add(r)
            }
            if (sortRating) shown.sortByDescending { ratingOf(it.meta) }
            adapter.notifyDataSetChanged()
            count.text = shown.size.toString() + " film"
            feed = ArrayList(all)
            if (shown.isEmpty()) {
                empty.text = a.getString(
                    if (base.isEmpty()) R.string.films_loading else R.string.no_results
                )
                empty.visibility = View.VISIBLE
            } else {
                empty.visibility = View.GONE
            }
        }

        /** Tur cipleri: sitelerin kategori sayfalarindan sonuc toplar. */
        fun loadGenre(genre: String) {
            val targets = genreSites[genre] ?: return
            genreActive = genre
            val list = ArrayList<Row>()
            genreRows = list
            sortRating = false
            siteFilter = ""
            typeFilter = 0
            freshOnly = false
            rebuild()
            empty.text = a.getString(R.string.films_loading)
            empty.visibility = View.VISIBLE
            val left = AtomicInteger(targets.size)
            for (t in targets) {
                Thread {
                    Scraper.load(t.second, 6000) { p ->
                        a.runOnUiThread {
                            for (f in p.films) {
                                val k = keyOf(f.title)
                                if (k.isEmpty() || list.any { keyOf(it.title) == k }) continue
                                list.add(Row(f.title, f.url, f.poster, t.first))
                            }
                            rebuild()
                            if (left.decrementAndGet() == 0) {
                                empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                            }
                        }
                    }
                }.start()
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
            chip("↻ " + a.getString(R.string.reload)) { onRefresh() }
            chip(a.getString(R.string.all_sites)) {
                genreRows = null
                genreActive = ""
                siteFilter = ""
                typeFilter = 0
                freshOnly = false
                rebuild()
            }
            chip(a.getString(R.string.films_filter)) {
                genreRows = null
                genreActive = ""
                siteFilter = ""
                typeFilter = 1
                rebuild()
            }
            chip(a.getString(R.string.series_filter)) {
                genreRows = null
                genreActive = ""
                siteFilter = ""
                typeFilter = 2
                freshOnly = false
                rebuild()
            }
            chip(a.getString(R.string.today_filter)) {
                genreRows = null
                genreActive = ""
                siteFilter = ""
                typeFilter = 0
                freshOnly = true
                rebuild()
            }
            chip("🎲 " + a.getString(R.string.random_film)) {
                val pool = if (shown.isNotEmpty()) shown else all
                if (pool.isNotEmpty()) {
                    val pick = pool[(Math.random() * pool.size).toInt()]
                    onOpen(pick, alts[pick.title] ?: emptyList())
                }
            }
            chip("★ " + a.getString(R.string.rating_sort)) {
                sortRating = !sortRating
                if (sortRating) {
                    enrich(a, all, 48) { rebuild() }
                    if (genreRows != null) enrich(a, genreRows!!, 48) { rebuild() }
                }
                rebuild()
            }
            // Tur filtreleri (Aksiyon, Komedi...) sitelerin kategori menulerinden gelir.
            val genres = synchronized(genreSites) { genreSites.keys.toList() }
            for (g in genres) {
                chip(g) { loadGenre(g) }
            }
            val names = all.map { it.site }.distinct()
            if (siteFilter.isNotEmpty() && names.none { it.equals(siteFilter, true) }) siteFilter = ""
            names.forEach { name ->
                chip(name) {
                    genreRows = null
                    genreActive = ""
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
        genreSites.putAll(DiscoverCache.loadGenres(a))
        rebuild()
        rebuildChips()
        if (DiscoverCache.isFresh(a)) return { addToWatch(a, shown, grid) }

        empty.text = a.getString(R.string.films_loading)
        empty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE

        val sites = SiteRepository.cached(a)
        val done = AtomicInteger(0)
        val run = DiscoverCache.nextRun(a)

        fun absorb(films: List<Scraper.Film>, siteName: String, series: Boolean = false, fresh: Boolean = false) {
            var added = 0
            for (f in films) {
                // Ayni film/dizi baska sitede de olabilir: baslik anahtariyla tekille.
                val key = keyOf(f.title)
                if (key.isEmpty()) continue
                val existing = all.firstOrNull { keyOf(it.title) == key }
                if (existing != null) {
                    if (existing.site != siteName) {
                        alts.getOrPut(existing.title) { ArrayList() }
                            .add(Row(f.title, f.url, f.poster, siteName, isSeries = series, fresh = fresh))
                    }
                    continue
                }
                all.add(Row(f.title, f.url, f.poster, siteName, isSeries = series, fresh = fresh))
                added++
            }
            if (added > 0) {
                interleave(all)
                maybeRefreshChips()
                rebuild()
            }
        }

        fun finishSite() {
            if (done.incrementAndGet() == sites.size) {
                enrich(a, all) { rebuild() }
                DiscoverCache.save(a, all)
            }
        }

        val newRx = Regex("(?i)yeni|son eklen|son film|son eklenen")
        for (s in sites) {
            Thread {
                Scraper.load(s.url, 6000) { page ->
                    a.runOnUiThread { absorb(rotated(page.films, run, 8), s.name) }
                    // Tur cipleri icin kategori adreslerini topla.
                    var addedGenre = false
                    synchronized(genreSites) {
                        for (c in page.categories) {
                            if (!isGenre(c.first)) continue
                            val list = genreSites.getOrPut(c.first) { ArrayList() }
                            if (list.none { it.first == s.name }) {
                                list.add(s.name to c.second)
                                addedGenre = true
                            }
                        }
                    }
                    if (addedGenre) a.runOnUiThread {
                        rebuildChips()
                        DiscoverCache.saveGenres(a, synchronized(genreSites) { HashMap(genreSites) })
                    }
                    val newCat = page.categories.firstOrNull { newRx.containsMatchIn(it.first) }
                    val diziCat = page.categories.firstOrNull { Regex("(?i)dizi").containsMatchIn(it.first) }
                    val others = page.categories.filter { it != newCat && it != diziCat }
                    val extraCat = if (others.isNotEmpty()) others[run % others.size] else null

                    val jobs = ArrayList<Triple<String, Boolean, Boolean>>() // url, series, fresh
                    newCat?.let { jobs.add(Triple(it.second, false, true)) }
                    diziCat?.let { jobs.add(Triple(it.second, true, false)) }
                    extraCat?.let { jobs.add(Triple(it.second, false, false)) }

                    if (jobs.isEmpty()) {
                        a.runOnUiThread { finishSite() }
                        return@load
                    }
                    val left = AtomicInteger(jobs.size)
                    for (j in jobs) {
                        Scraper.load(j.first, 5000) { p2 ->
                            a.runOnUiThread {
                                absorb(rotated(p2.films, run, 6), s.name, j.second, j.third)
                                if (left.decrementAndGet() == 0) finishSite()
                            }
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
            if (r.isSeries || Tmdb.looksLikeSeries(r.title, r.url)) {
                val k = keyOf(r.title)
                History.follow(a, k, r.title, r.url, r.poster, r.site, History.lastEpisode(a, k) ?: "")
            }
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
            primeAltSpeeds(a, alts) {
                val fastest = alts.minByOrNull { siteMsOf(a, it.site) ?: Long.MAX_VALUE }
                val ms = fastest?.let { siteMsOf(a, it.site) }
                if (fastest != null && ms != null) {
                    btn.text = a.getString(R.string.other_sources) + " ⚡ " + fastest.site
                }
            }
            btn.setOnClickListener {
                val sorted = alts.sortedBy { siteMsOf(a, it.site) ?: Long.MAX_VALUE }
                val labels = sorted.map {
                    val ms = siteMsOf(a, it.site)
                    if (ms == null) it.site else it.site + " · " + ms + " ms"
                }.toTypedArray()
                AlertDialog.Builder(a)
                    .setTitle(R.string.other_sources)
                    .setItems(labels) { _, w ->
                        watch(a, sorted[w], play = true)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

        // Film/dizi bilgisi yalniz TMDB'den.
        Thread {
            val isSeries = Tmdb.looksLikeSeries(row.title, row.url)
            val tmdb = Tmdb.load(row.title, isSeries)
            val credits = if (tmdb != null) Tmdb.credits(tmdb.id, tmdb.mediaType) else null
            val similar = if (tmdb != null) buildSimilar(tmdb, row) else emptyList()
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
                // Dizi ise Dizilerim'e de ekle
                if (row.isSeries || Tmdb.looksLikeSeries(row.title, row.url)) {
                    val k = keyOf(row.title)
                    History.follow(
                        a, k, row.title, row.url, row.poster, row.site,
                        History.lastEpisode(a, k) ?: ""
                    )
                }
                Toast.makeText(a, R.string.added_watch, Toast.LENGTH_SHORT).show()
            }
            refreshAdd()
        }

        // Dizi ise bolum listesi + "siradaki bolum"
        val seriesKey = keyOf(row.title)
        val eps = root.findViewById<TextView>(R.id.detail_eps)
        eps.visibility = View.GONE
        val nextBtn = root.findViewById<TextView>(R.id.detail_next)
        nextBtn.visibility = View.GONE
        if (Regex("(?i)bölüm|bolum|sezon").containsMatchIn(row.title) || row.url.contains("/dizi", true)) {
            Scraper.episodes(row.url) { list ->
                a.runOnUiThread {
                    if (eps.parent == null || list.isEmpty()) return@runOnUiThread
                    synchronized(episodeLists) { episodeLists[row.url] = row to list }
                    eps.visibility = View.VISIBLE
                    eps.setOnClickListener {
                        val last = History.lastEpisode(a, seriesKey)
                        val checked = list.indexOfFirst { it.first == last }
                        val labels = list.mapIndexed { i, e ->
                            if (i == checked) "✓ " + e.first else e.first
                        }.toTypedArray()
                        AlertDialog.Builder(a)
                            .setTitle(R.string.episodes)
                            .setSingleChoiceItems(labels, checked) { _, w ->
                                val (label, url) = list[w]
                                History.add(a, label, url, row.poster, row.site)
                                History.setLastEpisode(a, seriesKey, label)
                                History.follow(a, seriesKey, row.title, row.url, row.poster, row.site, label)
                                History.setHasNew(a, seriesKey, false)
                                open(a, url, play = true)
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }

                    val last = History.lastEpisode(a, seriesKey)
                    val idx = list.indexOfFirst { it.first == last }
                    if (idx >= 0 && idx + 1 < list.size) {
                        val next = list[idx + 1]
                        nextBtn.visibility = View.VISIBLE
                        nextBtn.text = a.getString(R.string.next_episode) + " · " + next.first
                        nextBtn.setOnClickListener {
                            History.add(a, next.first, next.second, row.poster, row.site)
                            History.setLastEpisode(a, seriesKey, next.first)
                            History.follow(a, seriesKey, row.title, row.url, row.poster, row.site, next.first)
                            History.setHasNew(a, seriesKey, false)
                            open(a, next.second, play = true)
                        }
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
    fun settings(
        a: Activity,
        root: View,
        onUpdate: (TextView) -> Unit = {},
        onBackup: () -> Unit = {},
        onRestore: () -> Unit = {},
        onResetPlayers: () -> Unit = {}
    ) {
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

        val updateValue = root.findViewById<TextView>(R.id.update_value)
        root.findViewById<View>(R.id.update_row).setOnClickListener { onUpdate(updateValue) }
        root.findViewById<View>(R.id.backup_row).setOnClickListener { onBackup() }
        root.findViewById<View>(R.id.restore_row).setOnClickListener { onRestore() }
        root.findViewById<View>(R.id.reset_players_row).setOnClickListener { onResetPlayers() }

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

    // ---------------------------------------------------------------- Arama
    /** Uygulama ici arama + sitelerin kendi arama sayfalari. */
    fun search(a: Activity, root: View, onOpen: (Row) -> Unit, onWeb: (String) -> Unit) {
        val input = root.findViewById<EditText>(R.id.search_input)
        val grid = root.findViewById<GridView>(R.id.list)
        val empty = root.findViewById<TextView>(R.id.empty)
        val count = root.findViewById<TextView>(R.id.count)
        val web = root.findViewById<TextView>(R.id.search_web)
        val sitesBtn = root.findViewById<TextView>(R.id.search_sites)
        val chipRow = root.findViewById<LinearLayout>(R.id.search_chips)
        val shown = ArrayList<Row>()
        val adapter = PanelFilmAdapter(a, shown)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> shown.getOrNull(pos)?.let { onOpen(it) } }

        var typeFilter = 0 // 0: hepsi, 1: film, 2: dizi
        var searching = false
        var liveDone = 0
        var liveTotal = 0

        // Yerel kaynaklar: ayni baslik tek kez (ilk gelen kazanir).
        val source = LinkedHashMap<String, Row>()
        // Sitelerden gelen sonuclar (oturum boyunca kalir).
        val live = LinkedHashMap<String, Row>()

        fun buildSource() {
            source.clear()
            fun addAll(list: List<Row>) {
                for (r in list) {
                    val k = keyOf(r.title)
                    if (k.isNotEmpty() && r.url.isNotEmpty()) source.putIfAbsent(k, r)
                }
            }
            addAll(feed)
            addAll(DiscoverCache.load(a))
            addAll(Watchlist.all(a).map { Row(it.title, it.url, it.poster.orEmpty(), it.site) })
            addAll(History.followed(a).map { Row(it.title, it.url, it.poster, it.site) })
            addAll(History.all(a).map { Row(it.title, it.url, it.poster, it.site) })
        }

        fun isSeriesRow(r: Row): Boolean =
            r.isSeries || Regex("(?i)bölüm|bolum|sezon|dizi").containsMatchIn(r.title)

        fun refresh() {
            val q = fold(input.text.toString().trim())
            val pool = LinkedHashMap<String, Row>()
            pool.putAll(source)
            pool.putAll(live)
            shown.clear()
            if (q.isNotEmpty()) {
                val hits = pool.values.filter {
                    fold(it.title).contains(q)
                }.filter {
                    when (typeFilter) {
                        1 -> !isSeriesRow(it)
                        2 -> isSeriesRow(it)
                        else -> true
                    }
                }
                shown.addAll(
                    hits.sortedBy {
                        if (fold(it.title).startsWith(q)) 0 else 1
                    }.take(60)
                )
            }
            adapter.notifyDataSetChanged()
            count.text = when {
                q.isEmpty() -> ""
                searching -> a.getString(R.string.search_searching, liveDone, liveTotal)
                else -> a.getString(R.string.search_count, shown.size)
            }
            empty.text = a.getString(
                when {
                    q.isEmpty() -> R.string.search_start
                    searching && shown.isEmpty() -> R.string.search_loading
                    else -> R.string.search_empty
                }
            )
            empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        }

        var searchOne: ((Site, String) -> Unit)? = null

        // Sitelerin kendi arama sayfalarinda ara.
        fun searchSites() {
            val q = input.text.toString().trim()
            if (q.isEmpty() || searching) return
            val sites = SiteRepository.cached(a)
            if (sites.isEmpty()) return
            searching = true
            liveDone = 0
            liveTotal = sites.size
            refresh()
            val query = fold(q)
            for (s in sites) {
                Thread {
                    try {
                        searchOne?.invoke(s, q)
                    } catch (e: Exception) {
                        a.runOnUiThread {
                            liveDone++
                            if (liveDone >= liveTotal) searching = false
                            refresh()
                        }
                    }
                }.start()
            }
        }

        /** Tek sitede arama formunu bulup sonuclari toplar (arka plan is parcacigi). */
        searchOne = { s, q ->
                    val query = fold(q)
                    val host = Scraper.hostOf(s.url)
                    val enc = android.net.Uri.encode(q)
                    var found: List<Scraper.Film> = emptyList()
                    var used: String? = null

                    fun tryTemplate(t: String): Boolean {
                        val url = t.replace("%s", enc)
                        val latch = java.util.concurrent.CountDownLatch(1)
                        var result: List<Scraper.Film> = emptyList()
                        Scraper.load(url, 5000) { p ->
                            result = p.films
                            latch.countDown()
                        }
                        latch.await(6, java.util.concurrent.TimeUnit.SECONDS)
                        // Sonuc gercekten sorguyla ilgili mi?
                        if (result.none { fold(keyOf(it.title)).contains(query) }) return false
                        found = result
                        used = t
                        return true
                    }

                    val remembered = SiteSearch.template(a, host)
                    var ok = remembered != null && tryTemplate(remembered)
                    if (!ok) {
                        // Arama formunu bul (ilk seferde), yoksa yaygin kaliblar.
                        val form = SiteSearch.formTemplate(s.url)
                        if (form != null) ok = tryTemplate(form)
                        if (!ok) {
                            for (t in SiteSearch.generic(s.url)) {
                                if (t == remembered || t == form) continue
                                if (tryTemplate(t)) {
                                    ok = true
                                    break
                                }
                            }
                        }
                    }
                    if (ok) SiteSearch.saveTemplate(a, host, used!!)
                    a.runOnUiThread {
                        for (f in found) {
                            val k = keyOf(f.title)
                            if (k.isEmpty() || live.containsKey(k)) continue
                            // Arama sayfasindaki alakasiz onerileri ele.
                            if (!fold(k).contains(keyOf(query))) continue
                            live[k] = Row(f.title, f.url, f.poster, s.name)
                        }
                        liveDone++
                        if (liveDone >= liveTotal) searching = false
                        refresh()
                    }
        }

        fun setType(value: Int) {
            typeFilter = value
            refresh()
        }

        // Filtre cipleri
        val d = a.resources.displayMetrics.density
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
        chip(a.getString(R.string.all_sites)) { setType(0) }
        chip(a.getString(R.string.films_filter)) { setType(1) }
        chip(a.getString(R.string.series_filter)) { setType(2) }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = refresh()
        })
        input.setOnFocusChangeListener { v, has ->
            if (has) {
                val imm = a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        input.setOnEditorActionListener { _, _, _ ->
            val q = input.text.toString().trim()
            if (q.isNotEmpty()) searchSites()
            true
        }
        sitesBtn.setOnClickListener { searchSites() }
        web.setOnClickListener {
            val q = input.text.toString().trim()
            if (q.isNotEmpty()) onWeb(q)
        }

        buildSource()
        refresh()
        // Onbellek bossa (Kesfet hic acilmadiysa) arka planda sitelerden topla.
        if (source.isEmpty()) {
            empty.text = a.getString(R.string.search_loading)
            empty.visibility = View.VISIBLE
            warmFeed(a) {
                buildSource()
                refresh()
            }
        }
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
        historyKind: String? = null,
        onText: ((String) -> Unit)? = null,
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
        if (onText != null) {
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) = Unit
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) = onText(s?.toString().orEmpty())
            })
        }
        val action = root.findViewById<TextView>(R.id.action)
        action.setText(actionRes)
        val submit = {
            val q = input.text.toString().trim()
            // Sadece "https://" yaziliysa gonderilmesin
            if (q.isNotEmpty() && q != "https://" && q != "http://") {
                if (historyKind != null) SearchHistory.add(a, historyKind, q)
                onSubmit(q)
            }
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

        // Son aramalar / adresler
        if (historyKind != null) {
            val row = root.findViewById<LinearLayout>(R.id.recent_row)
            val items = SearchHistory.all(a, historyKind)
            if (items.isNotEmpty()) {
                row.visibility = View.VISIBLE
                val d = a.resources.displayMetrics.density
                for (t in items) {
                    val chip = TextView(a)
                    chip.text = t
                    chip.textSize = 12f
                    chip.setTextColor(Color.parseColor("#B8C2CC"))
                    chip.gravity = android.view.Gravity.CENTER
                    chip.setBackgroundResource(R.drawable.tool_button_bg)
                    chip.isFocusable = true
                    chip.isClickable = true
                    chip.maxWidth = (260 * d).toInt()
                    chip.maxLines = 1
                    chip.ellipsize = android.text.TextUtils.TruncateAt.END
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        (34 * d).toInt()
                    )
                    lp.rightMargin = (8 * d).toInt()
                    chip.layoutParams = lp
                    chip.setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
                    chip.setOnClickListener {
                        input.setText(t)
                        input.setSelection(t.length)
                    }
                    row.addView(chip)
                }
            }
        }
    }

    // ---------------------------------------------------------------- yardimci
    /** Alternatif kaynaklarin hizini arka planda olcup durum onbellegine yazar. */
    private val statusCacheLock = Any()
    @Volatile
    private var primingSpeeds = false

    private fun siteRoot(a: Activity, name: String): String =
        SiteRepository.cached(a).firstOrNull { it.name.equals(name, true) }?.url ?: ""

    private fun siteMsOf(a: Activity, name: String): Long? {
        val root = siteRoot(a, name)
        if (root.isEmpty()) return null
        return synchronized(statusCacheLock) { statusCache?.get(root)?.ms }
    }

    private fun primeAltSpeeds(a: Activity, alts: List<Row>, onReady: () -> Unit) {
        val pending = alts.filter { siteMsOf(a, it.site) == null }.take(4)
        if (pending.isEmpty() || primingSpeeds) return
        primingSpeeds = true
        Thread {
            for (alt in pending) {
                val root = siteRoot(a, alt.site)
                if (root.isEmpty()) continue
                val t0 = System.currentTimeMillis()
                val code = ping(root)
                val ms = System.currentTimeMillis() - t0
                synchronized(statusCacheLock) {
                    statusCache = (statusCache ?: emptyMap()) + (root to Check(code, ms))
                }
            }
            primingSpeeds = false
            a.runOnUiThread { onReady() }
        }.start()
    }

    /** Kartlarda yil/puan: TMDB'den ilk N film icin arka planda. */
    private fun enrich(a: Activity, rows: ArrayList<Row>, limit: Int = 24, onChange: () -> Unit) {
        if (!Tmdb.enabled()) return
        for (r in rows.take(limit)) {
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

    /** Arama icin Turkce karakterleri sadelestir: "ask" -> "aşk" eslesir. */
    private fun fold(s: String): String = s.lowercase(java.util.Locale("tr"))
        .replace("ı", "i").replace("ş", "s").replace("ç", "c")
        .replace("ğ", "g").replace("ö", "o").replace("ü", "u")

    /** Dizi karti etiketi: "YENI · Kaldigin: <bolum>". */
    private fun metaOf(a: Activity, key: String, label: String): String {
        val base = a.getString(R.string.series_last) + ": " + label
        return if (History.hasNew(a, key)) a.getString(R.string.series_new) + " · " + base else base
    }

    private const val NEW_EP_CHECK_MS = 30 * 60 * 1000L

    /** Tur (kategori) adi mi? Genre cipleri icin. */
    private fun isGenre(name: String): Boolean = GENRE_RX.containsMatchIn(name)

    private val GENRE_RX = Regex(
        "(?i)aksiyon|komedi|dram|korku|gerilim|bilim|animasyon|romantik|suç|suc|macera|" +
            "fantastik|belgesel|savaş|savas|müzik|muzik|aile|gizem|western|spor"
    )

    /** Kart meta satirindaki ★ puan. */
    private fun ratingOf(meta: String): Double =
        Regex("★\\s*([0-9]+[.,]?[0-9]*)").find(meta)?.groupValues?.get(1)
            ?.replace(',', '.')?.toDoubleOrNull() ?: 0.0

    /** Takip edilen dizilerde yeni bolum var mi? Panel acilinca 30 dakikada bir. */
    private fun checkNewEpisodes(a: Activity, onChanged: () -> Unit) {
        val follows = History.followed(a).take(12)
        if (follows.isEmpty()) return
        Thread {
            var changed = false
            for (f in follows) {
                val latch = java.util.concurrent.CountDownLatch(1)
                var top: String? = null
                Scraper.episodes(f.url) { list ->
                    top = list.firstOrNull()?.first
                    latch.countDown()
                }
                latch.await(20, java.util.concurrent.TimeUnit.SECONDS)
                val newest = top ?: continue
                val seen = History.newestSeen(a, f.key)
                History.setNewestSeen(a, f.key, newest)
                if (seen != null && seen != newest && !History.hasNew(a, f.key)) {
                    History.setHasNew(a, f.key, true)
                    changed = true
                }
            }
            if (changed) a.runOnUiThread { onChanged() }
        }.start()
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

    /** Detaydaki "Benzer filmler" seridi: TMDB onerileri + akistan tamamlanir. */
    private fun buildSimilar(tmdb: Tmdb.Info, row: Row): List<Row> {
        val lib = feed
        if (lib.isEmpty()) return emptyList()
        val out = ArrayList<Row>()
        val used = HashSet<String>()
        used.add(keyOf(row.title))

        for (s in Tmdb.recommendations(tmdb.id, tmdb.mediaType)) {
            val hit = matchTitle(lib, s.title) ?: continue
            if (!used.add(keyOf(hit.title))) continue
            out.add(Row(hit.title, hit.url, hit.poster.ifBlank { s.poster }, hit.site, s.year, hit.isSeries))
            if (out.size >= 8) break
        }
        return out
    }

    /** Ayni sitelerden sirayla farkli filmler: her taramada pencere kayar. */
    private fun rotated(films: List<Scraper.Film>, run: Int, per: Int): List<Scraper.Film> {
        if (films.size <= per) return films
        val start = (run * per) % films.size
        val out = ArrayList<Scraper.Film>(per)
        for (i in 0 until per) out.add(films[(start + i) % films.size])
        return out
    }

    /** Keşfet'i elle yenilemek icin: onbellek ve akis sifirlanir. */
    fun clearFeed() {
        feed = emptyList()
    }

    /** Benzer film eslesmesi: once tam baslik, sonra kelime ortakligi. */
    private fun matchTitle(lib: List<Row>, title: String): Row? {        val key = keyOf(title)
        if (key.isEmpty()) return null
        lib.firstOrNull { keyOf(it.title) == key }?.let { return it }
        val words = key.split(" ").filter { it.length > 2 }
        if (words.isEmpty()) return null
        var best: Row? = null
        var bestHits = 0
        for (r in lib) {
            val k = keyOf(r.title)
            if (k.isEmpty()) continue
            val hits = words.count { k.contains(it) }
            if (hits > bestHits) {
                bestHits = hits
                best = r
            }
        }
        return if (bestHits * 10 >= words.size * 7) best else null
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

    fun open(a: Activity, url: String, play: Boolean = false) {
        // Bolum listesi biliniyorsa video bitince sonraki bolume gecilecek.
        AutoNext.pending = autoNextFor(url)
        a.startActivity(
            Intent(a, BrowserActivity::class.java)
                .putExtra(BrowserActivity.EXTRA_URL, url)
                .putExtra(BrowserActivity.EXTRA_PLAY, play)
        )
    }

    /** Panellerden film acarken gecmise yaz. */
    fun watch(a: Activity, row: Row, play: Boolean = false) {
        if (row.title.isNotEmpty()) History.add(a, row.title, row.url, row.poster, row.site)
        open(a, row.url, play)
    }

    private fun ping(url: String): Int = try {        val c = URL(url).openConnection() as HttpURLConnection
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
