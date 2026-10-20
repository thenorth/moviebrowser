package com.tvfilmizle

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONTokener
import java.io.ByteArrayInputStream

/** TV tarayici: WebView + reklam engelleme + oynatici secenekleri. */
class BrowserActivity : Activity() {

    companion object {
        const val EXTRA_URL = "url"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    private lateinit var container: FrameLayout
    private lateinit var fsContainer: FrameLayout
    private lateinit var web: WebView
    private lateinit var toolbar: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var urlBar: EditText

    private val children = mutableListOf<WebView>()
    private var active: WebView? = null
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var homeUrl = ""
    private var cinema = false

    /** Sayfa dibinde DOWN: odagi alt arac cubuguna tasir (kumanda ile menuye erisim). */
    private val bridge = object {
        @JavascriptInterface
        fun exitFocus() {
            runOnUiThread {
                toolbar.visibility = View.VISIBLE
                toolbar.getChildAt(0)?.requestFocus()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        container = findViewById(R.id.web_container)
        fsContainer = findViewById(R.id.fullscreen_container)
        toolbar = findViewById(R.id.toolbar)
        progress = findViewById(R.id.progress)
        urlBar = findViewById(R.id.url_bar)

        CookieManager.getInstance().setAcceptCookie(true)

        web = newWebView()
        container.addView(
            web,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        active = web

        homeUrl = intent.getStringExtra(EXTRA_URL)
            ?: SiteRepository.cached(this).firstOrNull()?.url
            ?: Sites.DEFAULT.first().url
        web.loadUrl(homeUrl)

        bindButtons()
    }

    private fun bindButtons() {
        findViewById<View>(R.id.btn_back).setOnClickListener { if (!goBackInWeb()) finish() }
        findViewById<View>(R.id.btn_reload).setOnClickListener { active?.reload() }
        findViewById<View>(R.id.btn_home).setOnClickListener { active?.loadUrl(homeUrl) }
        findViewById<View>(R.id.btn_full).setOnClickListener { toggleCinema() }
        findViewById<View>(R.id.btn_play).setOnClickListener {
            active?.evaluateJavascript(PlayerOptions.MEDIA_TOGGLE, null)
        }
        findViewById<View>(R.id.btn_options).setOnClickListener { showPlayerOptions() }
        findViewById<View>(R.id.btn_sites).setOnClickListener { showSites() }
        findViewById<View>(R.id.btn_go).setOnClickListener { loadTyped(urlBar.text.toString()) }

        urlBar.setOnEditorActionListener { _, actionId, event ->
            val go = actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (go) {
                loadTyped(urlBar.text.toString())
                true
            } else false
        }
    }

    private fun loadTyped(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        val url = if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
        active?.loadUrl(url)
        urlBar.clearFocus()
    }

    // ---------------------------------------------------------------- WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun newWebView(): WebView {
        val w = WebView(this)
        val s: WebSettings = w.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setSupportMultipleWindows(true)
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        s.userAgentString = UA
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.setGeolocationEnabled(false)
        w.setBackgroundColor(Color.BLACK)
        w.overScrollMode = View.OVER_SCROLL_NEVER
        w.addJavascriptInterface(bridge, "tvBridge")
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.webViewClient = client
        w.webChromeClient = chrome
        w.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) active = w }
        return w
    }

    private val client = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (AdBlock.isBlocked(request.url.toString())) {
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme?.lowercase()
            return !(scheme == "http" || scheme == "https" || scheme == "about" || scheme == "data")
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            if (view !== web && AdBlock.isBlocked(url)) {
                removeChild(view)
                return
            }
            if (view === active) urlBar.setText(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            view.evaluateJavascript(AdBlock.COSMETIC_JS, null)
            view.evaluateJavascript(PlayerOptions.FOCUS_JS, null)
            view.evaluateJavascript(Nav.JS, null)
            if (view === active && !urlBar.hasFocus()) view.requestFocus()
        }
    }

    private val chrome = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress.progress = newProgress
            progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
        }

        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) = showFullscreen(view, callback)

        override fun onHideCustomView() = hideFullscreen()

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message
        ): Boolean {
            // Oynatici yeni pencere acinca ayni ekranda gosterilir (TV icin en kullanisli yol).
            val child = newWebView()
            children.add(child)
            container.addView(
                child,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
            (resultMsg.obj as WebView.WebViewTransport).webView = child
            resultMsg.sendToTarget()
            active = child
            child.requestFocus()
            return true
        }

        override fun onCloseWindow(window: WebView) = removeChild(window)

        override fun onPermissionRequest(request: PermissionRequest) {
            request.grant(request.resources)
        }
    }

    // ------------------------------------------------------------ tam ekran

    /** Araç çubuğunu ve sistem çubuklarını gizler; iframe oynatıcıda da çalışır. */
    private fun toggleCinema() {
        cinema = !cinema
        toolbar.visibility = if (cinema) View.GONE else View.VISIBLE
        if (cinema) hideSystemBars() else showSystemBars()
        // Oynatici kisayolu ile gercek tam ekran isterse (video ust belgede ise)
        active?.evaluateJavascript(PlayerOptions.MEDIA_FULLSCREEN, null)
        if (cinema) Toast.makeText(this, R.string.cinema_hint, Toast.LENGTH_SHORT).show()
    }

    private fun showFullscreen(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        if (view == null) return
        customView = view
        customCallback = callback
        fsContainer.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        fsContainer.visibility = View.VISIBLE
        web.visibility = View.GONE
        toolbar.visibility = View.GONE
        hideSystemBars()
    }

    private fun hideFullscreen() {
        customView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        customView = null
        customCallback = null
        fsContainer.visibility = View.GONE
        web.visibility = View.VISIBLE
        toolbar.visibility = if (cinema) View.GONE else View.VISIBLE
        showSystemBars()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun showSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    // ------------------------------------------------------------ oynatici secenekleri

    private fun showPlayerOptions() {
        val targets = ArrayList<WebView>()
        active?.let { targets.add(it) }
        if (web !in targets) targets.add(web)
        for (child in children) if (child !in targets) targets.add(child)
        gatherOptions(targets, 0, ArrayList())
    }

    private data class Option(val label: String, val script: String?, val view: WebView)

    private fun gatherOptions(targets: List<WebView>, index: Int, acc: MutableList<Option>) {
        if (index >= targets.size) {
            showOptionsDialog(acc)
            return
        }
        val target = targets[index]
        target.evaluateJavascript(PlayerOptions.COLLECT_JS) { result ->
            val found = parseOptions(result)
            android.util.Log.d("TVFilm", "secenek " + target.url + " -> " + found.size)
            found.forEach { (label, script) ->
                if (acc.none { it.label == label }) acc.add(Option(label, script, target))
            }
            gatherOptions(targets, index + 1, acc)
        }
    }

    private fun showOptionsDialog(options: List<Option>) {
        if (options.isEmpty()) {
            Toast.makeText(this, R.string.no_options, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.player_options)
            .setItems(options.map { it.label }.toTypedArray()) { _, which ->
                val option = options[which]
                option.script?.let { option.view.evaluateJavascript(it, null) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun parseOptions(result: String?): List<Pair<String, String?>> {
        if (result.isNullOrBlank() || result == "null") return emptyList()
        return try {
            val decoded = JSONTokener(result).nextValue()
            val array = if (decoded is String) JSONArray(decoded) else JSONArray(result)
            val out = ArrayList<Pair<String, String?>>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                out.add(obj.optString("label") to obj.optString("script"))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun showSites() {
        val sites = SiteRepository.cached(this)
        val names = sites.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.sites)
            .setItems(names) { _, which ->
                homeUrl = sites[which].url
                web.loadUrl(homeUrl)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** MENU tusu: kumandanin ulasamadigi eylemler tek listede. */
    private fun showMenu() {
        val items = arrayOf(
            getString(R.string.back), getString(R.string.reload), getString(R.string.home),
            getString(R.string.sites), getString(R.string.options), getString(R.string.play),
            getString(R.string.fullscreen), getString(R.string.custom_url)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.menu)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> if (!goBackInWeb()) finish()
                    1 -> active?.reload()
                    2 -> active?.loadUrl(homeUrl)
                    3 -> showSites()
                    4 -> showPlayerOptions()
                    5 -> active?.evaluateJavascript(PlayerOptions.MEDIA_TOGGLE, null)
                    6 -> toggleCinema()
                    7 -> {
                        urlBar.requestFocus()
                        urlBar.selectAll()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------ gezinme

    private fun goBackInWeb(): Boolean {
        val child = children.lastOrNull()
        if (child != null) {
            if (child.canGoBack()) child.goBack() else removeChild(child)
            return true
        }
        if (web.canGoBack()) {
            web.goBack()
            return true
        }
        return false
    }

    private fun removeChild(w: WebView) {
        children.remove(w)
        (w.parent as? ViewGroup)?.removeView(w)
        w.stopLoading()
        w.destroy()
        active = children.lastOrNull() ?: web
    }

    override fun onBackPressed() {
        if (cinema) {
            toggleCinema()
            return
        }
        if (!goBackInWeb()) super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showMenu()
            return true
        }
        val w = active ?: web
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                w.evaluateJavascript(PlayerOptions.MEDIA_PLAY, null); return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                w.evaluateJavascript(PlayerOptions.MEDIA_PAUSE, null); return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                w.evaluateJavascript(PlayerOptions.MEDIA_TOGGLE, null); return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                w.evaluateJavascript(PlayerOptions.MEDIA_FF, null); return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                w.evaluateJavascript(PlayerOptions.MEDIA_RW, null); return true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                w.evaluateJavascript(PlayerOptions.MEDIA_PAUSE, null); return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
        children.forEach { it.onPause() }
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        children.forEach { it.onResume() }
    }

    override fun onDestroy() {
        hideFullscreen()
        children.forEach { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        children.clear()
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }
}
