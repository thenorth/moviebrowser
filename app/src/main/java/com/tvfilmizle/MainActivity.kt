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
    private lateinit var heroInitial: TextView
    private lateinit var clock: TextView
    private lateinit var status: TextView
    private lateinit var statusDot: View

    private var refreshing = false
    private var refreshGlyph: TextView? = null
    private var spin: android.animation.ObjectAnimator? = null

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
        heroInitial = findViewById(R.id.hero_initial)
        clock = findViewById(R.id.clock)
        status = findViewById(R.id.status)
        statusDot = findViewById(R.id.status_dot)

        fill(SiteRepository.cached(this))
        refreshList(false)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
        // Uygulama one geldiginde liste 5 dakikadan eskiyse otomatik guncelle.
        val age = System.currentTimeMillis() - SiteRepository.lastUpdate(this)
        if (!refreshing && age > 5 * 60 * 1000L) refreshList(false)
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
        val refreshCard = actionCard("↻", getString(R.string.refresh_list), getString(R.string.refresh_list_sub), SiteKind.REFRESH)
        rowActions.addView(refreshCard)
        refreshGlyph = refreshCard.findViewById(R.id.action_glyph)

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
        touchClick(card)
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
        touchClick(card)
        return card
    }

    /**
     * focusableInTouchMode kartlarda varsayilan davranis ilk dokunusu sadece odaga harcar.
     * Burada dokunus biz isliyoruz: bas -> odak + basili gorunum, birak -> tikla.
     */
    private fun touchClick(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.requestFocus()
                    v.isPressed = true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    v.performClick()
                }
                android.view.MotionEvent.ACTION_CANCEL -> v.isPressed = false
            }
            true
        }
    }

    private fun hero(title: String, sub: String) {
        heroTitle.text = title
        heroSub.text = sub
        heroInitial.text = title.take(1).uppercase()
    }

    private fun dot(color: String) {
        statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(color))
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
        if (refreshing) return
        refreshing = true
        status.text = getString(R.string.status_loading)
        dot("#8B98A6")
        startSpin()
        val started = System.currentTimeMillis()
        SiteRepository.refresh(this) { sites, changed ->
            // Animasyon en az 700 ms gorunsun (arama cok hizli bitiyor).
            val rest = 700L - (System.currentTimeMillis() - started)
            handler.postDelayed({ finishRefresh(sites, changed, manual) }, if (rest > 0) rest else 0)
        }
    }

    private fun finishRefresh(sites: List<Site>?, changed: Boolean, manual: Boolean) {
        refreshing = false
        stopSpin()
        if (sites == null) {
            status.text = getString(R.string.status_offline)
            dot("#FF6B6B")
            if (manual) Toast.makeText(this, R.string.status_offline, Toast.LENGTH_SHORT).show()
        } else {
            fill(sites)
            val msg = getString(R.string.status_updated, sites.size)
            status.text = msg
            dot("#59D986")
            if (changed || manual) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startSpin() {
        val glyph = refreshGlyph ?: return
        spin?.cancel()
        spin = android.animation.ObjectAnimator.ofFloat(glyph, View.ROTATION, 0f, 360f).apply {
            duration = 850
            repeatCount = android.animation.ObjectAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            start()
        }
    }

    private fun stopSpin() {
        spin?.cancel()
        spin = null
        refreshGlyph?.rotation = 0f
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
