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
    var src = f.src || '';
    if (!src) return 0;
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
    var src = f.src || '';
    if (!src) return 0;
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

  // 1) Ust belgede gercek bir video varsa once onu dene
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
  if (best && bestScore > 20000) return apply(best, 'iframe');

  return 'none';
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
