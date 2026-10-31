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

/** Afisli izleme listesi. */
class WatchlistActivity : Activity() {

    private lateinit var grid: GridView
    private lateinit var empty: TextView
    private lateinit var count: TextView
    private val items = ArrayList<Watchlist.Item>()
    private lateinit var adapter: WatchAdapter
    private var longPressDone = false
    private var longPressRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watchlist)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        Panel.apply(this)

        grid = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        count = findViewById(R.id.count)
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        adapter = WatchAdapter(this, items)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            items.getOrNull(position)?.let {
                startActivity(
                    Intent(this, BrowserActivity::class.java)
                        .putExtra(BrowserActivity.EXTRA_URL, it.url)
                )
            }
        }
        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    /** OK'a basili tut = listeden cikar. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isOk = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
        if (!isOk) return super.dispatchKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (longPressRunnable == null && !longPressDone) {
                longPressRunnable = Runnable {
                    longPressDone = true
                    removeSelected()
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

    private fun removeSelected() {
        val item = items.getOrNull(grid.selectedItemPosition) ?: return
        Watchlist.remove(this, item.url)
        Toast.makeText(this, R.string.removed_watch, Toast.LENGTH_SHORT).show()
        reload()
    }

    private fun reload() {
        items.clear()
        items.addAll(Watchlist.all(this))
        adapter.notifyDataSetChanged()
        count.text = items.size.toString() + " film"
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }
}

private class WatchAdapter(private val ctx: Context, private val items: List<Watchlist.Item>) : BaseAdapter() {

    private val inflater = LayoutInflater.from(ctx)

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_film, parent, false)
        val item = items[position]
        val img = view.findViewById<ImageView>(R.id.film_poster)
        view.findViewById<TextView>(R.id.film_title).text = item.title

        val poster = item.poster
        if (poster.isNullOrBlank()) {
            img.setImageDrawable(null)
            img.setBackgroundColor(Color.parseColor("#1A2230"))
        } else {
            val cached = Images.cached(poster)
            if (cached != null) {
                img.setImageBitmap(cached)
            } else {
                img.setImageDrawable(null)
                img.setBackgroundColor(Color.parseColor("#1A2230"))
            }
            img.tag = poster
            Images.load(ctx, poster) { bmp ->
                if (bmp != null && img.tag == poster) {
                    img.setBackgroundColor(Color.TRANSPARENT)
                    img.setImageBitmap(bmp)
                }
            }
        }
        return view
    }
}
