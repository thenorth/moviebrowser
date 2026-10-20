package com.tvfilmizle

/**
 * Kaba reklam engelleme: istek seviyesinde (shouldInterceptRequest) ve sayfa ici (CSS/JS).
 * shortcut: hazir reklam listesi degil, bilinen reklam/pop ağları + sayfa kalıpları engellenir.
 * Sitede çalışan oynatıcıya ait isteklere (m3u8/mp4/CDN) dokunulmaz.
 */
object AdBlock {

    private val HOSTS = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googleads.g.doubleclick",
        "googletagservices.com", "google-analytics.com", "adservice.google", "adnxs.com", "adsrvr.org",
        "advertising.com", "adn.com", "adform.net", "adroll.com", "adsrvr", "amazon-adsystem.com",
        "adcash.com", "adclick", "adf.ly", "adfoc.us", "adskeeper.com", "adsterra.com", "adsterra",
        "bidswitch.net", "casalemedia.com", "clickserver.net", "clickadu.com", "clickbank.net",
        "contextweb.com", "criteo.com", "criteo.net", "doubleclick", "exoclick.com", "exosrv.com",
        "googleadsserving", "gumgum.com", "hilltopads.net", "juicyads.com", "adskeeper",
        "media.net", "mgid.com", "mgid", "moatads.com", "onetag-sys.com", "openx.net", "outbrain.com",
        "pangle.com", "popads.net", "popcash.net", "popcutter.com", "poperblock.com", "popunder",
        "prebid.org", "propellerads.com", "propellerclick.com", "pubmatic.com", "revcontent.com",
        "richaudience.com", "rubiconproject.com", "servedbyadbutler.com", "sharethrough.com",
        "smartadserver.com", "taboola.com", "teads.tv", "trafficjunky.net", "underclick.net",
        "valueimpression.com", "yieldmo.com", "zedo.com", "onclickads.net", "onclckds.com",
        "clickadu", "clickbank", "adbreak", "adcash", "dapulse.com", "sa_lunamedia", "supersonicads",
        "adspredictiv.com", "adspredictiv", "predictiv", "jump/next.php", "whitebit"
    )

    private val PATTERNS = listOf(
        "/adserver/", "/adframe/", "/adhandler/", "/adpagead", "/adsense", "/adsystem",
        "/pagead/", "/googletagservices", "/advertising/", "/banner_ad", "/popunder", "/popupunder",
        "/hiddeniframe", "/float_ads", "/ad_break", "/adbreak/", "/adsbox", "/reklam",
        "/adservice/", "/promo_bottom", "/ad_holder", "/adscript", "/click_ads", "/advert"
    )

    fun isBlocked(url: String): Boolean {
        val u = url.lowercase()
        if (u.startsWith("data:") || u.startsWith("blob:") || u.startsWith("javascript:")) return false
        // Video kaynaklari hicbir zaman engellenmez (sorgu dizesi dahil).
        val path = u.substringBefore('?')
        if (path.endsWith(".m3u8") || path.endsWith(".mp4") || path.endsWith(".webm") ||
            path.endsWith(".mkv") || path.endsWith(".ts")
        ) return false
        for (h in HOSTS) if (u.contains(h)) return true
        for (p in PATTERNS) if (u.contains(p)) return true
        return false
    }

    /** Sayfadaki reklam kutularini gizler. */
    const val COSMETIC_JS = """
(function(){
  var sel = [
    '.adsbygoogle','.ad-container','.ad-container-2','[id^="ad-"]','[id^="ads-"]','[id^="banner"]',
    '[class^="ad-"]','[class^="ads-"]','.adbox','.ad-unit','.advert','.advertisement','.adzone',
    '.ad-holder','.ad_holder','.ad-break','#ad_break','.ad-wrap','.ad-wrapper','#ad-wrapper',
    '.banner-ad','.leaderboard','.adsbox','.ads-box','.float-ad','.sticky-ad','.bottom-ad','.top-ad',
    '.overlay-ad','.popunder','.pushunder','.popupunder','.sponsorbox','.sponsor','.taboola',
    '.mgid','#google_ads','[id^="google_ads"]','[class*="reklam"]','[id*="reklam"]','[id*="popup"]',
    'iframe[src*="doubleclick"]','iframe[src*="googlesyndication"]','iframe[src*="adsterra"]',
    'iframe[src*="popads"]','iframe[id*="ad-"]','[id^="BAIDU_"]','.inslider','.sponsor-ad'
  ];
  var kill = function(){
    for (var i = 0; i < sel.length; i++) {
      var nodes = document.querySelectorAll(sel[i]);
      for (var j = 0; j < nodes.length; j++) {
        var n = nodes[j];
        if (!n.parentNode) continue;
        var t = (n.textContent || '').trim();
        if (t.length > 0 && t.length < 120 && /reklam|advertisement|ads by/i.test(t)) {
          n.parentNode.removeChild(n);
        } else {
          n.style.setProperty('display', 'none', 'important');
          n.style.setProperty('visibility', 'hidden', 'important');
          n.style.setProperty('height', '0', 'important');
          n.style.setProperty('min-height', '0', 'important');
          n.style.setProperty('margin', '0', 'important');
          n.style.setProperty('padding', '0', 'important');
        }
      }
    }
    var ifr = document.querySelectorAll('iframe');
    for (var k = 0; k < ifr.length; k++) {
      var src = ifr[k].src || '';
      if (/doubleclick|googlesyndication|adsterra|popads|exoclick|propellerads|googleads/i.test(src)) {
        ifr[k].style.setProperty('display', 'none', 'important');
      }
    }
  };
  kill();
  setTimeout(kill, 1200);
  setTimeout(kill, 3500);
})();
"""
}
