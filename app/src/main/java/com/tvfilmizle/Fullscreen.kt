package com.tvfilmizle

/**
 * Sayfa ici tam ekran. Oynatıcı genelde cross-origin bir iframe icinde oldugu icin
 * requestFullscreen calismaz; oynatıcı iframe'i sabit konumla tum ekrana yayilir.
 * Reklam/kaplama iframe'leri puanlamayla elenir.
 */
object Fullscreen {

    const val SCAN_JS = """
(function(){
  window.__tvPlayerCandidate = null;
  var best = null, score = 0;
  var ifr = document.querySelectorAll('iframe');
  for (var i = 0; i < ifr.length; i++) {
    var s = scoreFrame(ifr[i]);
    if (s > score) { score = s; best = ifr[i]; }
  }
  window.__tvPlayerCandidate = best;
  window.__tvScoreFrame = scoreFrame;

  function scoreFrame(f){
    var r = f.getBoundingClientRect();
    if (r.width < 160 || r.height < 100) return 0;
    var cs = getComputedStyle(f);
    if (cs.visibility === 'hidden' || cs.display === 'none') return 0;
    if (parseFloat(cs.opacity) < 0.1) return 0;
    var src = f.src || '' ;
    if (/doubleclick|googlesyndication|adsterra|popads|exoclick|propeller|adsystem|adservice/i.test(src)) return 0;
    var s = r.width * r.height;
    if (/video|embed|player|rapidrame|sibnet|stream|watch|movie|episode/i.test(src)) s = s * 2;
    var idc = (String(f.className) + ' ' + f.id).toLowerCase();
    if (/ad-|ads|popup|overlay|banner|promo/.test(idc)) s = s * 0.25;
    return s;
  }
})()
"""

    const val ENTER_JS = """
(function(){
  if (window.__tvFsEl) return 'in';

  function scoreFrame(f){
    var r = f.getBoundingClientRect();
    if (r.width < 160 || r.height < 100) return 0;
    var cs = getComputedStyle(f);
    if (cs.visibility === 'hidden' || cs.display === 'none') return 0;
    if (parseFloat(cs.opacity) < 0.1) return 0;
    var src = f.src || '' ;
    if (/doubleclick|googlesyndication|adsterra|popads|exoclick|propeller|adsystem|adservice/i.test(src)) return 0;
    var s = r.width * r.height;
    if (/video|embed|player|rapidrame|sibnet|stream|watch|movie|episode/i.test(src)) s = s * 2;
    var idc = (String(f.className) + ' ' + f.id).toLowerCase();
    if (/ad-|ads|popup|overlay|banner|promo/.test(idc)) s = s * 0.25;
    return s;
  }

  function neutralize(el){
    var fixes = [];
    var n = el.parentElement;
    while (n && n !== document.body && n !== document.documentElement) {
      var cs = getComputedStyle(n);
      if (cs.transform !== 'none' || cs.filter !== 'none' || cs.perspective !== 'none' ||
          cs.contain !== 'none' || cs.willChange !== 'auto') {
        fixes.push([n, n.getAttribute('style')]);
        n.style.setProperty('transform', 'none', 'important');
        n.style.setProperty('filter', 'none', 'important');
        n.style.setProperty('perspective', 'none', 'important');
        n.style.setProperty('contain', 'none', 'important');
        n.style.setProperty('will-change', 'auto', 'important');
      }
      n = n.parentElement;
    }
    window.__tvFsFixes = fixes;
  }

  function apply(el, kind){
    if (!el) return 'none';
    neutralize(el);
    window.__tvFsFixes = window.__tvFsFixes || [];
    window.__tvFsSeen = [];
    function fix(node, prop, val){
      if (!node || !node.style) return;
      if (window.__tvFsSeen.indexOf(node) >= 0) return;
      window.__tvFsSeen.push(node);
      window.__tvFsFixes.push([node, node.getAttribute('style')]);
      node.style.setProperty(prop, val, 'important');
    }

    // Oynatıcıyı kapatan kirpma/katman sorunlarini kaldir.
    var n = el.parentElement;
    while (n && n !== document.documentElement) {
      var cs2 = getComputedStyle(n);
      if (cs2.overflow !== 'visible') fix(n, 'overflow', 'visible');
      n = n.parentElement;
    }

    // Sayfa basligi / menu / indir-yardim bantlari oynaticinin ustunde kalmasin.
    var rx = /(header|navbar|menu|topbar|top-bar|breadcrumb|download|help|social)/i;
    var all = document.querySelectorAll('body *');
    for (var i2 = 0; i2 < all.length; i2++) {
      var e2 = all[i2];
      if (e2 === el || e2.contains(el)) continue;
      var idc = ((e2.id || '') + ' ' + (e2.className || '')).toString().toLowerCase();
      if (!rx.test(idc)) continue;
      var rr = e2.getBoundingClientRect();
      if (rr.width * rr.height > window.innerWidth * window.innerHeight * 0.5) continue;
      fix(e2, 'display', 'none');
    }

    // Oynaticinin zincirinde olmayan govde bolumleri de gizlensin.
    if (document.body) {
      var chain = [];
      var c = el;
      while (c) { chain.push(c); c = c.parentElement; }
      for (var j = 0; j < document.body.children.length; j++) {
        var ch = document.body.children[j];
        if (chain.indexOf(ch) >= 0 || ch.contains(el)) continue;
        fix(ch, 'display', 'none');
      }
    }

    // Zincirdeki kardesleri gizle: player disinda kalan bantlar (yardim/indir, menu) kalmasin.
    var depth = 0;
    var up2 = el;
    while (up2 && up2.parentElement && up2 !== document.body) {
      depth++;
      if (depth >= 2) {
        var sibs = up2.parentElement.children;
        for (var si = 0; si < sibs.length; si++) {
          if (sibs[si] === up2) continue;
          fix(sibs[si], 'display', 'none');
        }
      }
      up2 = up2.parentElement;
    }

    // Oynaticinin icinde kalan "Yardim / Indir" gibi ust bantlari gizle.
    var words = /^(yardım|yardim|indir|download|help)$/i;
    var scan = document.querySelectorAll('a,button,span,div');
    for (var s3 = 0; s3 < scan.length; s3++) {
      var se = scan[s3];
      if (se === el || se.contains(el) || el.contains(se)) continue;
      if (se.children.length > 1) continue;
      if (!words.test((se.textContent || '').trim())) continue;
      var bar = se;
      for (var up = 0; up < 4 && bar.parentElement; up++) {
        bar = bar.parentElement;
        var bbr = bar.getBoundingClientRect();
        if (bbr.width > window.innerWidth * 0.5 && bbr.height > 10 && bbr.height < 160) break;
      }
      fix(bar, 'display', 'none');
    }

    var prev = el.getAttribute('style');
    el.setAttribute('data-tvfs-prev', prev === null ? '' : prev);
    el.style.cssText = (el.style.cssText || '') +
      ';position:fixed!important;top:0!important;left:0!important;right:0!important;bottom:0!important;' +
      'width:100vw!important;height:100vh!important;z-index:2147483647!important;background:#000!important;' +
      'margin:0!important;padding:0!important;max-width:none!important;max-height:none!important;' +
      'transform:none!important;border:0!important;display:block!important;visibility:visible!important;opacity:1!important;';
    window.__tvFsEl = el;
    document.documentElement.style.overflow = 'hidden';
    if (document.body) document.body.style.overflow = 'hidden';
    return kind;
  }

  // Oynatici kabi: oynatma alanini iceren EN KUCUK kap (sayfa susleri disarida kalir).
  var boxes = document.querySelectorAll(
    '.video-player-container-here, .hdmv-play-container, [class*=player-container], ' +
    '#player, .video-player, .player-wrap');
  var box = null, boxArea = 1e12;
  for (var bi = 0; bi < boxes.length; bi++) {
    var be = boxes[bi];
    if (!be.querySelector('video, iframe, img, canvas')) continue;
    var br0 = be.getBoundingClientRect();
    if (br0.width < 200 || br0.height < 150) continue;
    var ba = br0.width * br0.height;
    if (ba < boxArea) { boxArea = ba; box = be; }
  }
  if (box) return apply(box, 'box');

  // 6) Oynatici kapagi (afis + oynat simgesi): gercek dokunusla baslat.
  if (!window.__tvFsForce) {
    var cov = document.querySelector('.play-that-video, .play-icon, .vjs-big-play-button, ' +
      '[class*=play-button], [class*=playButton], .player-cover, .poster-play');
    if (cov) {
      var cr2 = cov.getBoundingClientRect();
      if (cr2.width > 40 && cr2.height > 40 && cr2.top < window.innerHeight) {
        return JSON.stringify({
          kind: 'tapfs',
          nx: (cr2.left + cr2.width / 2) / window.innerWidth,
          ny: (cr2.top + cr2.height / 2) / window.innerHeight
        });
      }
    }
  }

  // 0) Oynaticinin kendi tam ekran dugmesi varsa onu kullan (kendi bantlarini gizler).
  if (!window.__tvFsForce) {
    var fsBtn = null;
    var fsSel = '.vjs-fullscreen-control, [class*=fullscreen], [class*=full-screen], ' +
                '[class*=fs-button], [id*=fullscreen], [aria-label*=fullscreen i], ' +
                '[title*=fullscreen i], [title*="tam ekran" i]';
    var fsList = (box || document).querySelectorAll(fsSel);
    for (var fi = 0; fi < fsList.length; fi++) {
      var fr0 = fsList[fi].getBoundingClientRect();
      if (fr0.width < 12 || fr0.width > 200 || fr0.height < 12 || fr0.height > 200) continue;
      if (fr0.top < 0 || fr0.bottom > window.innerHeight) continue;
      var fcs = getComputedStyle(fsList[fi]);
      if (fcs.visibility === 'hidden' || fcs.display === 'none' || parseFloat(fcs.opacity) < 0.1) continue;
      fsBtn = fsList[fi];
      break;
    }
    if (fsBtn) {
      var fr1 = fsBtn.getBoundingClientRect();
      return JSON.stringify({
        kind: 'tapfs',
        nx: (fr1.left + fr1.width / 2) / window.innerWidth,
        ny: (fr1.top + fr1.height / 2) / window.innerHeight
      });
    }
  }

  // 1) Oynaticinin kendi tam ekran dugmesi bulunamadiysa: kabi kapla.
  if (box) return apply(box, 'box');

  // 2) Ust belgede gercek bir video varsa once onu dene
  var vids = document.querySelectorAll('video');
  for (var v = 0; v < vids.length; v++) {
    var vr = vids[v].getBoundingClientRect();
    if (vr.width > 160 && vr.height > 100) {
      try { if (vids[v].webkitEnterFullscreen) { vids[v].webkitEnterFullscreen(); return 'native'; } } catch (e) {}
      return apply(vids[v], 'video');
    }
  }

  // 2) Oynatıcı iframe'i (puanlamayla reklamlar elenir)
  var best = null, bestScore = 0;
  var ifr = document.querySelectorAll('iframe');
  for (var i = 0; i < ifr.length; i++) {
    var s = scoreFrame(ifr[i]);
    if (s > bestScore) { bestScore = s; best = ifr[i]; }
  }
  if (best && bestScore > 20000) {
    // Oynatici kendi tam ekran dugmesiyle acilsin: kendi ust bantlarini gizler.
    if (!window.__tvFsForce) {
      var br2 = best.getBoundingClientRect();
      // Dugme ekranda gorunmuyorsa (oynatici sayfa altinda) dogrudan kaplama yap.
      if (br2.top >= 0 && br2.bottom <= window.innerHeight) {
        var fx = (br2.left + br2.width - 26) / window.innerWidth;
        var fy = (br2.top + br2.height - 22) / window.innerHeight;
        return JSON.stringify({ kind: 'tapfs', nx: fx, ny: fy });
      }
    }
    return apply(best, 'iframe');
  }

  return 'none';
})()
"""

  /** Oynatma alani ekrani doldurdu mu? (sitelerin kendi tam ekrani CSS ile de olabilir) */
    const val FILLED_JS = """
(function(){
  var t = document.querySelector('video') ||
          document.querySelector('.video-player-container-here, .hdmv-play-container, [class*=player-container], #player');
  if (!t) return 'none';
  var r = t.getBoundingClientRect();
  return (r.width >= window.innerWidth * 0.9 && r.height >= window.innerHeight * 0.75) ? 'full' : 'win';
})()
"""

    const val EXIT_JS = """
(function(){
  var el = window.__tvFsEl;
  if (el) {
    var prev = el.getAttribute('data-tvfs-prev');
    if (prev === null || prev === '') el.removeAttribute('style'); else el.setAttribute('style', prev);
    el.removeAttribute('data-tvfs-prev');
    window.__tvFsEl = null;
  }
  var fixes = window.__tvFsFixes || [];
  for (var i = 0; i < fixes.length; i++) {
    var n = fixes[i][0], st = fixes[i][1];
    if (st === null || st === '') n.removeAttribute('style'); else n.setAttribute('style', st);
  }
  window.__tvFsFixes = [];
  document.documentElement.style.overflow = '';
  if (document.body) document.body.style.overflow = '';
  return 'out';
})()
"""
}
