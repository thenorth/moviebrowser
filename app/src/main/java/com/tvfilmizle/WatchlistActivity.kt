package com.tvfilmizle

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView

/** Tarayicida "Listeme ekle" ile kaydedilen filmler. */
class WatchlistActivity : Activity() {

    private lateinit var list: ListView
    private lateinit var empty: TextView
    private lateinit var title: TextView
    private val items = ArrayList<Watchlist.Item>()
    private lateinit var adapter: WatchAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watchlist)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        adapter = WatchAdapter(this, items) { item ->
            Watchlist.remove(this, item.url)
            reload()
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
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

    private fun reload() {
        items.clear()
        items.addAll(Watchlist.all(this))
        adapter.notifyDataSetChanged()
        findViewById<TextView>(R.id.count).text = items.size.toString() + " film"
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }
}

private class WatchAdapter(
    private val ctx: Context,
    private val items: List<Watchlist.Item>,
    private val onDelete: (Watchlist.Item) -> Unit
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(ctx)

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_watch, parent, false)
        val item = items[position]
        view.findViewById<TextView>(R.id.watch_title).text = item.title
        view.findViewById<TextView>(R.id.watch_site).text = item.site
        view.findViewById<TextView>(R.id.watch_del).setOnClickListener { onDelete(item) }
        return view
    }
}
