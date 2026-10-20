package com.tvfilmizle

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Site secimi: liste GitHub'daki sites.json'dan gelir, cevrimdisi son bilinen liste kullanilir. */
class MainActivity : Activity() {

    private val entries = ArrayList<Site>()
    private lateinit var grid: GridView
    private lateinit var status: TextView
    private lateinit var adapter: SiteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        grid = findViewById(R.id.site_grid)
        status = findViewById(R.id.status)

        adapter = SiteAdapter(this, entries)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            val site = entries[position]
            when (site.kind) {
                SiteKind.CUSTOM -> askUrl()
                SiteKind.REFRESH -> refreshList(true)
                SiteKind.SITE -> open(site.url)
            }
        }

        fill(SiteRepository.cached(this))
        refreshList(false)
    }

    private fun fill(sites: List<Site>) {
        entries.clear()
        entries.addAll(sites)
        entries.add(Site(getString(R.string.custom_url), getString(R.string.custom_url_sub), SiteKind.CUSTOM))
        entries.add(Site(getString(R.string.refresh_list), getString(R.string.refresh_list_sub), SiteKind.REFRESH))
        adapter.notifyDataSetChanged()
    }

    private fun refreshList(manual: Boolean) {
        status.text = getString(R.string.status_loading)
        SiteRepository.refresh(this) { sites, changed ->
            if (sites == null) {
                status.text = getString(R.string.status_offline)
                if (manual) Toast.makeText(this, R.string.status_offline, Toast.LENGTH_SHORT).show()
            } else {
                fill(sites)
                status.text = getString(R.string.status_ok) + " · " + sites.size + " site · " + timeText()
                if (changed || manual) Toast.makeText(this, R.string.list_updated, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun timeText(): String {
        val t = SiteRepository.lastUpdate(this)
        if (t <= 0L) return ""
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))
    }

    private fun open(url: String) {
        startActivity(Intent(this, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, url))
    }

    private fun askUrl() {
        val input = EditText(this).apply {
            setSingleLine()
            hint = "https://"
            setPadding(48, 36, 48, 36)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.custom_url)
            .setView(input)
            .setPositiveButton(R.string.go) { _, _ ->
                val raw = input.text.toString().trim()
                if (raw.isNotEmpty()) open(if (raw.startsWith("http")) raw else "https://$raw")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener { input.requestFocus() }
        dialog.show()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
}

private class SiteAdapter(ctx: Context, private val items: List<Site>) : BaseAdapter() {
    private val inflater = LayoutInflater.from(ctx)

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_site, parent, false)
        val site = items[position]
        view.findViewById<TextView>(R.id.site_badge).text = when (site.kind) {
            SiteKind.CUSTOM -> "+"
            SiteKind.REFRESH -> "↻"
            SiteKind.SITE -> site.name.take(1).uppercase()
        }
        view.findViewById<TextView>(R.id.site_name).text = site.name
        view.findViewById<TextView>(R.id.site_url).text = site.url
        return view
    }
}
