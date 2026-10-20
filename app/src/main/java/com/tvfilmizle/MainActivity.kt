package com.tvfilmizle

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Android TV ana ekrani: hero + yatay kart siralari. */
class MainActivity : Activity() {

    private lateinit var rowSites: LinearLayout
    private lateinit var rowActions: LinearLayout
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var clock: TextView
    private lateinit var status: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        rowSites = findViewById(R.id.row_sites)
        rowActions = findViewById(R.id.row_actions)
        heroTitle = findViewById(R.id.hero_title)
        heroSub = findViewById(R.id.hero_sub)
        clock = findViewById(R.id.clock)
        status = findViewById(R.id.status)

        fill(SiteRepository.cached(this))
        refreshList(false)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun fill(sites: List<Site>) {
        rowSites.removeAllViews()
        for (site in sites) rowSites.addView(siteCard(site))

        rowActions.removeAllViews()
        rowActions.addView(actionCard("+", getString(R.string.custom_url), getString(R.string.custom_url_sub), SiteKind.CUSTOM))
        rowActions.addView(actionCard("↻", getString(R.string.refresh_list), getString(R.string.refresh_list_sub), SiteKind.REFRESH))

        hero(
            sites.firstOrNull()?.name ?: (getString(R.string.brand_first) + " " + getString(R.string.brand_second)),
            sites.firstOrNull()?.url ?: getString(R.string.hero_hint)
        )
        rowSites.post { rowSites.getChildAt(0)?.requestFocus() }
    }

    private fun siteCard(site: Site): View {
        val card = layoutInflater.inflate(R.layout.item_site, rowSites, false)
        card.background = gradient(site.name)
        card.findViewById<TextView>(R.id.site_initial).text = site.name.take(1).uppercase()
        card.findViewById<TextView>(R.id.site_name).text = site.name
        card.findViewById<TextView>(R.id.site_url).text = site.url
        card.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) hero(site.name, site.url) }
        card.setOnClickListener { open(site.url) }
        return card
    }

    private fun actionCard(glyph: String, title: String, sub: String, kind: SiteKind): View {
        val card = layoutInflater.inflate(R.layout.item_action, rowActions, false)
        card.findViewById<TextView>(R.id.action_glyph).text = glyph
        card.findViewById<TextView>(R.id.action_title).text = title
        card.findViewById<TextView>(R.id.action_sub).text = sub
        card.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) hero(title, sub) }
        card.setOnClickListener {
            when (kind) {
                SiteKind.CUSTOM -> askUrl()
                SiteKind.REFRESH -> refreshList(true)
                else -> Unit
            }
        }
        return card
    }

    private fun hero(title: String, sub: String) {
        heroTitle.text = title
        heroSub.text = sub
    }

    /** Site adindan turetilen renkli kart arka plani. */
    private fun gradient(name: String): GradientDrawable {
        val hue = (abs(name.hashCode()) % 360).toFloat()
        val top = Color.HSVToColor(floatArrayOf(hue, 0.42f, 0.52f))
        val bottom = Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.20f))
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(top, bottom)).apply {
            cornerRadius = 16 * resources.displayMetrics.density
        }
    }

    private fun refreshList(manual: Boolean) {
        status.text = getString(R.string.status_loading)
        SiteRepository.refresh(this) { sites, changed ->
            if (sites == null) {
                status.text = getString(R.string.status_offline)
                if (manual) Toast.makeText(this, R.string.status_offline, Toast.LENGTH_SHORT).show()
            } else {
                fill(sites)
                status.text = sites.size.toString() + " " + getString(R.string.status_ok) + " · " + timeText()
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
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
}
