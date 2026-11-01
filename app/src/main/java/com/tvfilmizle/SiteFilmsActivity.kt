package com.tvfilmizle

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Bir sitenin filmlerini (ve varsa kategorilerini) uygulama icinde gosterir. */
class SiteFilmsActivity : Activity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
    }

    private lateinit var grid: GridView
    private lateinit var empty: TextView
    private lateinit var catScroll: View
    private lateinit var catRow: LinearLayout
    private val films = ArrayList<Scraper.Film>()
    private lateinit var adapter: FilmAdapter
    private var siteUrl = ""
    private var siteName = ""
    private var longPressDone = false
    private var longPressRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_site_films)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        siteUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        siteName = intent.getStringExtra(EXTRA_NAME) ?: Scraper.hostOf(siteUrl)
        if (siteUrl.isEmpty()) {
            finish()
            return
        }

        grid = findViewById(R.id.film_grid)
        empty = findViewById(R.id.empty)
        catScroll = findViewById(R.id.cat_scroll)
        catRow = findViewById(R.id.cat_row)

        findViewById<TextView>(R.id.title).text = siteName
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_site).setOnClickListener { openBrowser(siteUrl) }

        adapter = FilmAdapter(this, films)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            films.getOrNull(position)?.let {
                History.add(this, it.title, it.url, it.poster, siteName)
                openBrowser(it.url)
            }
        }

        load(siteUrl)
    }

    /** OK'a basili tut (>=450 ms) = secili filmi listeye ekle; kisa OK = ac. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isOk = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
        if (!isOk) return super.dispatchKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (longPressRunnable == null && !longPressDone) {
                longPressRunnable = Runnable {
                    longPressDone = true
                    addSelectedToWatchlist()
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

    private fun addSelectedToWatchlist() {
        val film = films.getOrNull(grid.selectedItemPosition) ?: return
        if (Watchlist.has(this, film.url)) {
            Toast.makeText(this, R.string.in_watch, Toast.LENGTH_SHORT).show()
            return
        }
        Watchlist.add(this, Watchlist.Item(film.title, film.url, siteName, film.poster))
        Toast.makeText(this, R.string.added_watch, Toast.LENGTH_SHORT).show()
    }

    private fun load(url: String) {
        empty.visibility = View.VISIBLE
        empty.text = getString(R.string.films_loading)
        Scraper.load(url) { page ->
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                films.clear()
                films.addAll(page.films)
                adapter.notifyDataSetChanged()
                showCategories(page.categories)
                if (films.isEmpty()) {
                    empty.text = getString(R.string.films_empty)
                    grid.postDelayed({
                        if (!isFinishing) {
                            openBrowser(url)
                            finish()
                        }
                    }, 1500)
                } else {
                    empty.visibility = View.GONE
                }
            }
        }
    }

    private fun showCategories(categories: List<Pair<String, String>>) {
        catRow.removeAllViews()
        if (categories.isEmpty()) {
            catScroll.visibility = View.GONE
            setGridTop(92)
            return
        }
        catScroll.visibility = View.VISIBLE
        setGridTop(112)
        for ((name, url) in categories) {
            val chip = TextView(this)
            chip.text = name
            chip.textSize = 12f
            chip.setTextColor(getColorCompat())
            chip.gravity = Gravity.CENTER
            chip.setBackgroundResource(R.drawable.tool_button_bg)
            chip.isFocusable = true
            chip.isClickable = true
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (36 * resources.displayMetrics.density).toInt()
            )
            lp.rightMargin = (8 * resources.displayMetrics.density).toInt()
            chip.layoutParams = lp
            chip.setPadding((16 * resources.displayMetrics.density).toInt(), 0, (16 * resources.displayMetrics.density).toInt(), 0)
            chip.setOnClickListener { load(url) }
            catRow.addView(chip)
        }
    }

    private fun getColorCompat(): android.content.res.ColorStateList =
        android.content.res.ColorStateList.valueOf(Color.parseColor("#F2F6FA"))

    private fun setGridTop(dp: Int) {
        val d = resources.displayMetrics.density
        val side = (38 * d).toInt()
        grid.setPadding(side, (dp * d).toInt(), side, (80 * d).toInt())
    }

    private fun openBrowser(url: String) {
        startActivity(Intent(this, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, url))
    }
}

private class FilmAdapter(private val ctx: Context, private val items: List<Scraper.Film>) : BaseAdapter() {

    private val inflater = LayoutInflater.from(ctx)

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_film, parent, false)
        val film = items[position]
        val img = view.findViewById<ImageView>(R.id.film_poster)
        view.findViewById<TextView>(R.id.film_title).text = film.title

        val cached = Images.cached(film.poster)
        if (cached != null) {
            img.setImageBitmap(cached)
        } else {
            img.setImageDrawable(null)
            img.setBackgroundColor(Color.parseColor("#1A2230"))
        }
        img.tag = film.poster
        Images.load(ctx, film.poster) { bmp ->
            if (bmp != null && img.tag == film.poster) {
                img.setBackgroundColor(Color.TRANSPARENT)
                img.setImageBitmap(bmp)
            }
        }
        return view
    }
}
