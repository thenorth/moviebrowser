package com.tvfilmizle

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView

/** Bir sitenin filmlerini uygulama icinde gosterir; secilen film tarayicida acilir. */
class SiteFilmsActivity : Activity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
    }

    private lateinit var grid: GridView
    private lateinit var empty: TextView
    private val films = ArrayList<Scraper.Film>()
    private lateinit var adapter: FilmAdapter
    private var siteUrl = ""
    private var siteName = ""

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
        findViewById<TextView>(R.id.title).text = siteName
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_site).setOnClickListener { openBrowser(siteUrl) }

        adapter = FilmAdapter(this, films)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            films.getOrNull(position)?.let { openBrowser(it.url) }
        }

        load()
    }

    private fun load() {
        empty.visibility = View.VISIBLE
        empty.text = getString(R.string.films_loading)
        Scraper.load(siteUrl) { list ->
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                films.clear()
                films.addAll(list)
                adapter.notifyDataSetChanged()
                if (films.isEmpty()) {
                    empty.text = getString(R.string.films_empty)
                    // Kazinamadi: siteyi tarayicida ac
                    grid.postDelayed({
                        if (!isFinishing) {
                            openBrowser(siteUrl)
                            finish()
                        }
                    }, 1500)
                } else {
                    empty.visibility = View.GONE
                }
            }
        }
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
