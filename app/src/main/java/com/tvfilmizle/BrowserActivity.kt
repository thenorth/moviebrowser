package com.tvfilmizle

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream

/** TV tarayici: WebView + reklam engelleme + oynatici secenekleri. */
class BrowserActivity : Activity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_PLAY = "play"
        private const val QUALITY_JS = "(function(){var v=document.querySelector('video');if(!v||!v.videoHeight)return '';return v.videoWidth+'x'+v.videoHeight;})()"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val REQ_VPN = 7
        private val CHALLENGE = Regex(
            "(?i)just a moment|bir dakika|attention required|verify you are human|checking your browser"
        )
    }

    private lateinit var container: FrameLayout
    private lateinit var fsContainer: FrameLayout
    private lateinit var web: WebView
    private lateinit var toolbar: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var urlBar: EditText

    private val children = mutableListOf<WebView>()
    private var active: WebView? = null
    private var advanced = false
    private lateinit var qualityBadge: TextView
    @Volatile
    private var pageUrl = ""
    private val qualityHandler = Handler(Looper.getMainLooper())
    private var autoPlay = false
    private var pendingFsSelector = ""
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var homeUrl = ""
    private var fsActive = false
    private var autoStarted = false
    private var crashUrl = ""
    private var crashCount = 0
    private var erroredHost: String? = null
    private val autoVpnHosts = HashSet<String>()
    private var defaultUa = ""
    private var uaRetried = false
    private var pendingReloadUrl: String? = null

    /** Sayfa dibinde DOWN: odagi alt arac cubuguna tasir (kumanda ile menuye erisim). */
    private val bridge = object {
        @JavascriptInterface
        fun exitFocus() {
            runOnUiThread {
                toolbar.visibility = View.VISIBLE
                toolbar.getChildAt(0)?.requestFocus()
            }
        }

        @JavascriptInterface
        fun playerStarted() {
            runOnUiThread {
                if ((autoPlay || Prefs.autoFullscreen(this@BrowserActivity)) && !fsActive) enterFullscreen()
            }
        }

        @JavascriptInterface
        fun videoEnded() {
            runOnUiThread { playNextEpisode() }
        }
    }

    /** Bolum bittiyse siradaki bolume gec (bolum listesi biliniyorsa). */
    private fun playNextEpisode() {
        if (advanced) return
        val n = AutoNext.pending ?: return
        advanced = true
        History.add(this, n.label, n.url, n.poster, n.site)
        History.setLastEpisode(this, n.seriesKey, n.label)
        History.follow(this, n.seriesKey, n.seriesTitle, n.seriesUrl, n.poster, n.site, n.label)
        History.setHasNew(this, n.seriesKey, false)
        AutoNext.pending = Panels.autoNextFor(n.url)
        Toast.makeText(this, n.label, Toast.LENGTH_SHORT).show()
        (active ?: web).loadUrl(n.url)
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
        qualityBadge = findViewById(R.id.quality_badge)
        qualityHandler.postDelayed(qualityTick, 3000)

        CookieManager.getInstance().setAcceptCookie(true)

        web = newWebView()
        container.addView(
            web,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        active = web

        autoPlay = intent.getBooleanExtra(EXTRA_PLAY, false)
        homeUrl = intent.getStringExtra(EXTRA_URL)
            ?: SiteRepository.cached(this).firstOrNull()?.url
            ?: Sites.DEFAULT.first().url
        web.loadUrl(homeUrl)

        bindButtons()
        // Video izlerken ekran kapanmasin.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Immersive yerine basit fullscreen: diyaloglarin pencere odagi bozulmaz.
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    private fun bindButtons() {
        findViewById<View>(R.id.btn_app_home).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_back).setOnClickListener { if (!goBackInWeb()) finish() }
        findViewById<View>(R.id.btn_reload).setOnClickListener { active?.reload() }
        findViewById<View>(R.id.btn_home).setOnClickListener { active?.loadUrl(homeUrl) }
        findViewById<View>(R.id.btn_full).setOnClickListener { toggleFullscreen() }
        findViewById<View>(R.id.btn_play).setOnClickListener { togglePlay() }
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
        s.userAgentString = if (Prefs.desktopUa(this)) UA else s.userAgentString
        if (defaultUa.isEmpty()) defaultUa = s.userAgentString
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.setGeolocationEnabled(false)
        w.setBackgroundColor(Color.BLACK)
        w.overScrollMode = View.OVER_SCROLL_NEVER
        w.addJavascriptInterface(bridge, "tvBridge")
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.webViewClient = client
        w.webChromeClient = chrome
        w.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) active = w }
        w.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            try {
                val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val req = DownloadManager.Request(Uri.parse(url))
                req.setMimeType(mimeType)
                req.setTitle(name)
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(this, R.string.downloading, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
            }
        }
        return w
    }

    private val client = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (AdBlock.isBlocked(request.url.toString())) {
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
            // HLS ana listesi: dusuk cozunurlukleri at (en az 1080p).
            Quality.rewrite(request.url.toString(), pageUrl)?.let { return it }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (AdBlock.isBlocked(url)) return true
            val scheme = request.url.scheme?.lowercase()
            return !(scheme == "http" || scheme == "https" || scheme == "about" || scheme == "data")
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            if (view !== web && AdBlock.isBlocked(url)) {
                removeChild(view)
                return
            }
            pageUrl = url
            if (view === active) {
                urlBar.setText(url)
                fsActive = false
                autoStarted = false
                uaRetried = false
                advanced = false
                toolbar.visibility = View.VISIBLE
                // Daha once bu sitede VPN acildiysa otomatik baglan.
                val host = try { android.net.Uri.parse(url).host } catch (e: Exception) { null }
                if (host != null && VpnHosts.has(this@BrowserActivity, host) &&
                    !Warp.connected && autoVpnHosts.add(host)
                ) {
                    Toast.makeText(this@BrowserActivity, R.string.vpn_auto, Toast.LENGTH_SHORT).show()
                    Warp.connect(this@BrowserActivity) { ok, _ ->
                        runOnUiThread { if (ok) view.loadUrl(url) }
                    }
                }
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            val host = try { android.net.Uri.parse(url).host } catch (e: Exception) { null }
            if (host != null && host != erroredHost) SiteHealth.ok(this@BrowserActivity, host)
            erroredHost = null
            view.evaluateJavascript(AdBlock.COSMETIC_JS, null)
            view.evaluateJavascript(PlayerOptions.FOCUS_JS, null)
            view.evaluateJavascript(Nav.JS, null)
            view.evaluateJavascript(Fullscreen.SCAN_JS, null)
            view.evaluateJavascript(Player.ANALYZE_JS, null)
            // Oynatıcı sayfaya geç eklenebiliyor; bir kez daha tara.
            view.postDelayed({ view.evaluateJavascript(Fullscreen.SCAN_JS, null) }, 3000)
            view.postDelayed({ view.evaluateJavascript(Player.ANALYZE_JS, null) }, 3000)
            if (view === active && !urlBar.hasFocus()) view.requestFocus()
            // "İzle" ile gelindiyse oynatıcıyı kendiliğinden başlat.
            if (view === active && autoPlay) scheduleAutoStart(2600)

            // Bot dogrulamasi takildiysa bir kez varsayilan kimlikle dene.
            if (Prefs.desktopUa(this@BrowserActivity) && !uaRetried) {
                view.evaluateJavascript("document.title") { title ->
                    if (title != null && CHALLENGE.containsMatchIn(title)) {
                        uaRetried = true
                        if (defaultUa.isNotEmpty()) view.settings.userAgentString = defaultUa
                        view.reload()
                    }
                }
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) error.errorCode else 0
            val reason = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) error.description.toString() else ""
            android.util.Log.w("TVFilm", "yukleme hatasi: " + request.url + " -> " + code + " " + reason)
            val msg = when (code) {
                ERROR_HOST_LOOKUP -> getString(R.string.err_dns)
                ERROR_CONNECT, ERROR_TIMEOUT, ERROR_FAILED_SSL_HANDSHAKE ->
                    getString(R.string.err_network)
                else -> getString(R.string.load_error, reason)
            }
            erroredHost = request.url.host
            SiteHealth.fail(this@BrowserActivity, request.url.host)
            val host = request.url.host
            // Site bilinen engelli ise VPN'i kendisi acmayi dene.
            if (host != null && SiteHealth.isBlocked(this@BrowserActivity, host) &&
                !Warp.connected && autoVpnHosts.add(host)
            ) {
                Toast.makeText(this@BrowserActivity, R.string.vpn_auto, Toast.LENGTH_SHORT).show()
                enableVpnThenReload(request.url.toString())
                return
            }
            showLoadErrorDialog(request.url.toString(), msg)
            urlBar.setText(request.url.toString())
        }

        /** Renderer bellek yetersizliginden coktugunde uygulama kapanmasin; sayfayi yeniden kur. */        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val url = view.url ?: homeUrl
            if (url == crashUrl) crashCount++ else {
                crashUrl = url
                crashCount = 1
            }
            if (crashCount > 2) {
                Toast.makeText(this@BrowserActivity, R.string.page_too_heavy, Toast.LENGTH_LONG).show()
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                if (view === web) {
                    web = newWebView()
                    container.addView(
                        web,
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    )
                    active = web
                }
                return true
            }

            Toast.makeText(this@BrowserActivity, R.string.reloading, Toast.LENGTH_SHORT).show()
            if (view === web) {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                val fresh = newWebView()
                web = fresh
                container.addView(
                    fresh,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                )
                active = fresh
                fresh.loadUrl(url)
            } else {
                removeChild(view)
                active = web
                web.loadUrl(url)
            }
            return true
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

    private fun enterFullscreen(retries: Int = 3) {
        if (fsActive || isFinishing || customView != null) return
        val w = active ?: web
        w.evaluateJavascript("window.__tvFsHint=" + org.json.JSONObject.quote(PlayerProfile.fullscreen(this, hostOfPage(w)) ?: ""), null)
        w.evaluateJavascript(Fullscreen.ENTER_JS) { result ->
            val state = result?.trim()?.trim('"') ?: "none"
            if (state.startsWith("{")) {
                // Oynaticinin kendi tam ekran dugmesine dokun; olmazsa kendimiz kaplariz.
                try {
                    val o = org.json.JSONObject(state)
                    if (o.optString("kind") == "tapfs") {
                        pendingFsSelector = o.optString("sel")
                        tapWeb(o.optDouble("nx", 0.96).toFloat(), o.optDouble("ny", 0.96).toFloat())
                        w.postDelayed({ afterPlayerFullscreenTap(w) }, 1600)
                        return@evaluateJavascript
                    }
                } catch (e: Exception) {
                }
            }
            if (state == "video" || state == "iframe" || state == "native" || state == "box") {
                fsActive = true
                toolbar.visibility = View.GONE
                hideSystemBars()
            } else if (retries > 0) {
                // Oynatıcı yeni açılmış olabilir: kısa sonra bir kez daha dene.
                w.postDelayed({ enterFullscreen(retries - 1) }, 1500)
            }
        }
    }

    /** Player'ın kendi tam ekran düğmesine basıldı: ekranı doldurduysa bitir, değilse kendimiz kaplarız. */
    private fun afterPlayerFullscreenTap(w: WebView) {
        if (isFinishing || customView != null) {
            rememberFs(w)
            return
        }
        w.evaluateJavascript(Fullscreen.FILLED_JS) { r ->
            if (r?.contains("full") == true) {
                rememberFs(w)
                fsActive = true
                toolbar.visibility = View.GONE
                hideSystemBars()
            } else if (!isFinishing && customView == null) {
                w.evaluateJavascript("window.__tvFsForce=true", null)
                enterFullscreen(2)
            }
        }
    }

    private fun hostOfPage(w: WebView): String = try {
        android.net.Uri.parse(w.url ?: "").host ?: ""
    } catch (e: Exception) {
        ""
    }

    /** Bu sitede ise yarayan tam ekran secicisini hatirla. */
    private fun rememberFs(w: WebView) {
        if (pendingFsSelector.isEmpty()) return
        PlayerProfile.saveFullscreen(this, hostOfPage(w), pendingFsSelector)
        pendingFsSelector = ""
    }

    /** Yukleme hatasi: sebep + tek tusla VPN. */
    private fun showLoadErrorDialog(url: String, message: String) {
        if (isFinishing) return
        AlertDialog.Builder(this)
            .setTitle(R.string.load_error_title)
            .setMessage(message)
            .setPositiveButton(R.string.vpn_enable) { _, _ -> enableVpnThenReload(url) }
            .setNeutralButton(R.string.reload) { _, _ -> (active ?: web).reload() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun enableVpnThenReload(url: String) {
        // Bu site icin VPN'i hatirla: sonraki acilista otomatik baglanir.
        android.net.Uri.parse(url).host?.let { VpnHosts.add(this, it) }
        pendingReloadUrl = url
        val prep = VpnService.prepare(this)
        if (prep != null) {
            startActivityForResult(prep, REQ_VPN)
            return
        }
        connectVpnThenReload()
    }

    private fun connectVpnThenReload() {
        Toast.makeText(this, R.string.vpn_connecting, Toast.LENGTH_SHORT).show()
        Warp.connect(this) { ok, error ->
            runOnUiThread {
                if (ok) {
                    Toast.makeText(this, R.string.vpn_on, Toast.LENGTH_SHORT).show()
                    pendingReloadUrl?.let { (active ?: web).loadUrl(it) }
                } else {
                    Toast.makeText(this, getString(R.string.vpn_failed, error), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) connectVpnThenReload()
    }

    /** Oynatıcıyı (video ya da en büyük iframe) tespit edip tüm ekrana yayar. */
    private fun toggleFullscreen() {
        val w = active ?: web
        if (fsActive) {
            w.evaluateJavascript(Fullscreen.EXIT_JS, null)
            fsActive = false
            toolbar.visibility = View.VISIBLE
            showSystemBars()
            return
        }
        w.evaluateJavascript("window.__tvFsHint=" + org.json.JSONObject.quote(PlayerProfile.fullscreen(this, hostOfPage(w)) ?: ""), null)
        w.evaluateJavascript(Fullscreen.ENTER_JS) { result ->
            val state = result?.trim()?.trim('"') ?: "none"
            if (state == "none") {
                Toast.makeText(this, R.string.no_player, Toast.LENGTH_SHORT).show()
            } else if (state.startsWith("{")) {
                // Oynaticinin kendi tam ekrani; olmazsa 1.6 sn sonra kendimiz kaplariz.
                try {
                    val o = org.json.JSONObject(state)
                    if (o.optString("kind") == "tapfs") {                        pendingFsSelector = o.optString("sel")
                        tapWeb(o.optDouble("nx", 0.96).toFloat(), o.optDouble("ny", 0.96).toFloat())
                        w.postDelayed({ afterPlayerFullscreenTap(w) }, 1600)
                        return@evaluateJavascript
                    }
                } catch (e: Exception) {
                }
                Toast.makeText(this, R.string.no_player, Toast.LENGTH_SHORT).show()
            } else if (state == "video" || state == "iframe" || state == "native" || state == "box") {
                fsActive = true
                toolbar.visibility = View.GONE
                hideSystemBars()
                Toast.makeText(this, R.string.cinema_hint, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.no_player, Toast.LENGTH_SHORT).show()
            }
        }
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
        toolbar.visibility = if (fsActive) View.GONE else View.VISIBLE
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
    /** Sayfayi listeye ekler (baslik + url + site + afis). */
    private fun addToWatchlist() {
        val w = active ?: web
        val url = w.url ?: return
        if (Watchlist.has(this, url)) {
            Toast.makeText(this, R.string.in_watch, Toast.LENGTH_SHORT).show()
            return
        }
        val title = w.title?.takeIf { it.isNotBlank() } ?: url
        val site = Scraper.hostOf(url)
        Watchlist.add(this, Watchlist.Item(title, url, site))
        Toast.makeText(this, R.string.added_watch, Toast.LENGTH_SHORT).show()

        // Afis: sayfadaki og:image (varsa) ile kaydi tamamla.
        w.evaluateJavascript(
            """(function(){var m=document.querySelector('meta[property="og:image"],meta[name="twitter:image"],link[rel="image_src"]');if(!m)return '';var v=m.content||m.href||'';if(!v)return '';try{return new URL(v,location.href).href}catch(e){return ''}})()"""
        ) { result ->
            val poster = try {
                JSONTokener(result ?: "null").nextValue() as? String
            } catch (e: Exception) {
                null
            }
            if (!poster.isNullOrBlank()) {
                Watchlist.add(this, Watchlist.Item(title, url, site, poster))
            }
        }
    }

    private fun togglePlay() {
        val w = active ?: web
        w.evaluateJavascript(
            "window.__tvPlayHint=" + org.json.JSONObject.quote(PlayerProfile.play(this, hostOfPage(w)) ?: ""),
            null
        )
        w.evaluateJavascript(Player.TOGGLE_JS) { result ->
            val value = try { JSONTokener(result ?: "null").nextValue() } catch (e: Exception) { null }
            if (value !is String) return@evaluateJavascript
            when {
                value == "video" -> {
                    enterFullscreen()
                    scheduleAutoStart()
                }
                value == "paused" -> Unit
                value == "started" -> {
                    Toast.makeText(this, R.string.starting, Toast.LENGTH_SHORT).show()
                    w.postDelayed({ enterFullscreen() }, 1200)
                    scheduleAutoStart(2600)
                }
                value == "none" -> Toast.makeText(this, R.string.no_player, Toast.LENGTH_SHORT).show()
                value.startsWith("{") -> {
                    try {
                        val o = JSONObject(value)
                        // Bu sitede ise yarayan oynat ogesini hatirla.
                        val sel = o.optString("sel")
                        if (sel.isNotEmpty() && Regex("(?i)play|oynat|cover|poster").containsMatchIn(sel)) {
                            PlayerProfile.savePlay(this, hostOfPage(w), sel)
                        }
                        tapWeb(o.optDouble("nx", 0.5).toFloat(), o.optDouble("ny", 0.45).toFloat())
                    } catch (e: Exception) {
                    }
                    w.postDelayed({ enterFullscreen() }, 1800)
                    scheduleAutoStart(3200)
                }
            }
        }
    }

    /** Sayfa basina bir kez: oynatıcının ikinci oynat düğmesini kendiliğinden başlatır. */
    private fun scheduleAutoStart(delayMs: Long = 3000) {
        if (autoStarted) return
        autoStarted = true
        web.postDelayed({ if (!isFinishing) autoStartPlayback() }, delayMs)
    }

    private fun autoStartPlayback() {
        val w = active ?: web
        w.evaluateJavascript(Player.PLAY_JS) { result ->
            val value = try { JSONTokener(result ?: "null").nextValue() as? String } catch (e: Exception) { null }
            // Ayni kaynakli video/dugme yoksa oynatıcı iframe icindedir: merkezdeki buyuk oynat dugmesine dokun.
            if (value == "none") tapWeb(0.5f, 0.5f)
            // "Izle" ile gelindiyse oynatici hazir olunca tam ekrana gec.
            if (autoPlay) {
                val delay = if (value == "none") 2000L else 600L
                w.postDelayed({ enterFullscreen(6) }, delay)
            }
        }
    }

    private fun tapWeb(nx: Float, ny: Float) {
        val w = active ?: web
        if (w.width <= 0 || w.height <= 0) return
        val x = nx.coerceIn(0.05f, 0.95f) * w.width
        val y = ny.coerceIn(0.05f, 0.95f) * w.height
        val now = android.os.SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
        w.dispatchTouchEvent(down)
        down.recycle()
        val up = android.view.MotionEvent.obtain(now, now + 60, android.view.MotionEvent.ACTION_UP, x, y, 0)
        w.dispatchTouchEvent(up)
        up.recycle()
    }

    private fun showMenu() {
        val items = arrayOf(
            getString(R.string.back), getString(R.string.reload), getString(R.string.home),
            getString(R.string.sites), getString(R.string.options), getString(R.string.play),
            getString(R.string.fullscreen), getString(R.string.custom_url), getString(R.string.add_watch),
            getString(R.string.open_external)
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
                    5 -> togglePlay()
                    6 -> toggleFullscreen()
                    7 -> {
                        urlBar.requestFocus()
                        urlBar.selectAll()
                    }
                    8 -> addToWatchlist()
                    9 -> openExternal()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------ gezinme

    /** Videoyu harici oynaticida (VLC vb.) acar: once akis URL'si, yoksa sayfa URL'si. */
    private fun openExternal() {
        val w = active ?: web
        w.evaluateJavascript(Player.STREAM_JS) { result ->
            var url = try {
                JSONTokener(result ?: "null").nextValue() as? String
            } catch (e: Exception) {
                null
            }
            if (url.isNullOrBlank()) url = w.url
            if (url.isNullOrBlank()) {
                Toast.makeText(this, R.string.no_stream, Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        .setDataAndType(android.net.Uri.parse(url), "video/*")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                Toast.makeText(this, R.string.no_external_player, Toast.LENGTH_LONG).show()
            }
        }
    }

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
        // Site oynaticisinin kendi tam ekrani acikken once onu kapat.
        if (customView != null) {
            hideFullscreen()
            customCallback?.onCustomViewHidden()
            return
        }
        if (fsActive) {
            toggleFullscreen()
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
                togglePlay(); return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                w.evaluateJavascript(PlayerOptions.MEDIA_PAUSE, null); return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlay(); return true
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
        SleepTimer.attach(this)
        web.onResume()
        children.forEach { it.onResume() }
    }

    /** Oynatilan videonun cozunurlugunu kucuk rozette gosterir. */
    private val qualityTick = object : Runnable {
        override fun run() {
            val w = active ?: web
            w.evaluateJavascript(QUALITY_JS) { r ->
                if (isFinishing) return@evaluateJavascript
                val h = Regex("x(\\d+)\"").find(r ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (h <= 0) {
                    // Iframe oynaticilarda video gorunmez: HLS listesinden bilinen kaliteyi goster.
                    val q = Quality.lastHeight
                    if (q > 0) {
                        qualityBadge.visibility = View.VISIBLE
                        qualityBadge.text = q.toString() + "p"
                        qualityBadge.setTextColor(Color.parseColor("#B8C2CC"))
                    } else {
                        qualityBadge.visibility = View.GONE
                    }
                } else {
                    qualityBadge.visibility = View.VISIBLE
                    qualityBadge.text = h.toString() + "p"
                    qualityBadge.setTextColor(
                        if (h >= 1080) Color.parseColor("#B8C2CC") else Color.parseColor("#FF9F1A")
                    )
                }
            }
            qualityHandler.postDelayed(this, 4000)
        }
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
