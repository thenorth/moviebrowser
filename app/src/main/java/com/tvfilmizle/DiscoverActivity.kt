package com.tvfilmizle

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Birden cok siteden filmleri tek akista toplar. */
class DiscoverActivity : Activity() {

    data class Entry(val film: Scraper.Film, val site: String)

    private lateinit var grid: GridView
    private lateinit var empty: TextView
    private lateinit var count: TextView
    private val entries = ArrayList<Entry>()
    private lateinit var adapter: DiscoverAdapter
    private var longPressDone = false
    private var longPressRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discover)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        grid = findViewById(R.id.grid)
        empty = findViewById(R.id.empty)
        count = findViewById(R.id.count)
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        adapter = DiscoverAdapter(this, entries)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            entries.getOrNull(position)?.let {
                startActivity(
                    Intent(this, BrowserActivity::class.java)
                        .putExtra(BrowserActivity.EXTRA_URL, it.film.url)
                )
            }
        }

        load()
    }

    /** OK'a basili tut = listeye ekle. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isOk = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
        if (!isOk) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (longPressRunnable == null && !longPressDone) {
                longPressRunnable = Runnable {
                    longPressDone = true
                    addSelected()
                }
                grid.postDelayed(longPressRunnable, 450)
            }
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_UP) {
            longPressRunnable?.let { grid.removeCallbacks(it) }
            longPressRunnable = null
            if (longPressDone) {
                longPressDone = false
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun addSelected() {
        val e = entries.getOrNull(grid.selectedItemPosition) ?: return
        if (Watchlist.has(this, e.film.url)) {
            Toast.makeText(this, R.string.in_watch, Toast.LENGTH_SHORT).show()
            return
        }
        Watchlist.add(this, Watchlist.Item(e.film.title, e.film.url, e.site, e.film.poster))
        Toast.makeText(this, R.string.added_watch, Toast.LENGTH_SHORT).show()
    }

    private fun load() {
        val sites = SiteRepository.cached(this).take(6)
        if (sites.isEmpty()) {
            empty.text = getString(R.string.films_empty)
            return
        }
        empty.visibility = View.VISIBLE
        empty.text = getString(R.string.films_loading)

        val collected = Collections.synchronizedList(ArrayList<Entry>())
        val done = AtomicInteger(0)
        for (s in sites) {
            Thread {
                Scraper.load(s.url) { page ->
                    page.films.take(12).forEach { collected.add(Entry(it, s.name)) }
                    if (done.incrementAndGet() == sites.size) {
                        runOnUiThread {
                            if (isFinishing) return@runOnUiThread
                            show(collected)
                        }
                    }
                }
            }.start()
        }
    }

    private fun show(list: List<Entry>) {
        val seen = HashSet<String>()
        entries.clear()
        for (e in list) {
            val key = e.film.title.lowercase(Locale("tr")).trim()
            if (seen.add(key)) entries.add(e)
        }
        adapter.notifyDataSetChanged()
        count.text = entries.size.toString() + " film"
        if (entries.isEmpty()) {
            empty.text = getString(R.string.films_empty)
            empty.visibility = View.VISIBLE
        } else {
            empty.visibility = View.GONE
        }
    }
}

private class DiscoverAdapter(private val ctx: Context, private val items: List<DiscoverActivity.Entry>) : BaseAdapter() {

    private val inflater = LayoutInflater.from(ctx)

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_film, parent, false)
        val entry = items[position]
        val img = view.findViewById<ImageView>(R.id.film_poster)
        view.findViewById<TextView>(R.id.film_title).text = entry.film.title
        val siteLabel = view.findViewById<TextView>(R.id.film_site)
        siteLabel.text = entry.site
        siteLabel.visibility = View.VISIBLE

        val cached = Images.cached(entry.film.poster)
        if (cached != null) {
            img.setImageBitmap(cached)
        } else {
            img.setImageDrawable(null)
            img.setBackgroundColor(Color.parseColor("#1A2230"))
        }
        img.tag = entry.film.poster
        Images.load(ctx, entry.film.poster) { bmp ->
            if (bmp != null && img.tag == entry.film.poster) {
                img.setBackgroundColor(Color.TRANSPARENT)
                img.setImageBitmap(bmp)
            }
        }
        return view
    }
}
