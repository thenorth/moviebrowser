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
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
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
    fun watchlist(a: Activity, root: View): () -> Unit {
        val grid = root.findViewById<GridView>(R.id.list)
        val empty = root.findViewById<TextView>(R.id.empty)
        val count = root.findViewById<TextView>(R.id.count)
        val rows = ArrayList<Row>()
        val adapter = PanelFilmAdapter(a, rows)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> rows.getOrNull(pos)?.let { open(a, it.url) } }

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

    // ---------------------------------------------------------------- Kesfet
    /** Donus: OK'a basili tutulunca cagrilacak islem (listeye ekle). */
    fun discover(a: Activity, root: View): () -> Unit {
        val grid = root.findViewById<GridView>(R.id.grid)
        val empty = root.findViewById<TextView>(R.id.empty)
        val count = root.findViewById<TextView>(R.id.count)
        val rows = ArrayList<Row>()
        val adapter = PanelFilmAdapter(a, rows)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> rows.getOrNull(pos)?.let { open(a, it.url) } }
        empty.text = a.getString(R.string.films_loading)

        val sites = SiteRepository.cached(a).take(6)
        val collected = java.util.Collections.synchronizedList(ArrayList<Row>())
        val done = AtomicInteger(0)
        for (s in sites) {
            Thread {
                Scraper.load(s.url) { page ->
                    page.films.take(12).forEach { collected.add(Row(it.title, it.url, it.poster, s.name)) }
                    if (done.incrementAndGet() == sites.size) {
                        a.runOnUiThread {
                            val seen = HashSet<String>()
                            rows.clear()
                            for (r in collected) {
                                if (seen.add(r.title.lowercase(Locale("tr")).trim())) rows.add(r)
                            }
                            adapter.notifyDataSetChanged()
                            count.text = rows.size.toString() + " film"
                            empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }
            }.start()
        }

        return {
            val pos = grid.selectedItemPosition
            val r = rows.getOrNull(if (pos < 0) 0 else pos) ?: Row("", "", "", "")
            if (r.url.isNotEmpty()) {
                if (Watchlist.has(a, r.url)) {
                    Toast.makeText(a, R.string.in_watch, Toast.LENGTH_SHORT).show()
                } else {
                    Watchlist.add(a, Watchlist.Item(r.title, r.url, r.site, r.poster))
                    Toast.makeText(a, R.string.added_watch, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ------------------------------------------------------------ Site durumu
    fun status(a: Activity, root: View) {
        val sites = SiteRepository.cached(a)
        val codes = HashMap<String, Int>()
        sites.forEach { codes[it.url] = -1 }
        val adapter = StatusAdapter(a, sites, codes)
        root.findViewById<ListView>(R.id.list).adapter = adapter
        root.findViewById<View>(R.id.btn_test).setOnClickListener {
            sites.forEach { codes[it.url] = 0 }
            adapter.notifyDataSetChanged()
            Thread {
                for (s in sites) {
                    val code = ping(s.url)
                    a.runOnUiThread {
                        codes[s.url] = code
                        adapter.notifyDataSetChanged()
                    }
                }
            }.start()
        }
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
    fun open(a: Activity, url: String) {
        a.startActivity(Intent(a, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, url))
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

/** Listem / Kesfet icin poster karti. */
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
                text.text = ctx.getString(R.string.status_ok) + " Â· " + code
            }
            code == 403 || code == 429 || code == 503 -> {
                dot.setTextColor(Color.parseColor("#FF9F1A"))
                text.text = ctx.getString(R.string.status_protected) + " Â· " + code
            }
            else -> {
                dot.setTextColor(Color.parseColor("#FF6B6B"))
                text.text = ctx.getString(R.string.status_error) + " Â· " + code
            }
        }
        return view
    }
}
