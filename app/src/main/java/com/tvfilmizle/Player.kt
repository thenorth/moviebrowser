package com.tvfilmizle

/**
 * Oynatıcı analizi + oynat/duraklat.
 * - Ust belgede (veya ayni kaynakli iframe'de) video varsa dogrudan kontrol edilir.
 * - Cross-origin oynatıcı iframe'inde DOM'a erisilemez; iframe merkezine gercek dokunus
 *   gonderilir (oynatıcılarin cogu merkeze tiklayinca oynat/duraklat yapar).
 * Donus: "video" | "none" | JSON {"kind":"iframe","nx":0.5,"ny":0.4}
 */
object Player {

    const val TOGGLE_JS = """
(function(){
  function bigVideo(doc){
    var vids = doc.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      var r = vids[i].getBoundingClientRect();
      if (r.width > 120 && r.height > 80) return vids[i];
    }
    return null;
  }

  var v = bigVideo(document);
  if (v) { if (v.paused) { v.play(); } else { v.pause(); } return 'video'; }

  for (var f = 0; f < window.frames.length; f++) {
    try {
      var fv = bigVideo(window.frames[f].document);
      if (fv) { if (fv.paused) { fv.play(); } else { fv.pause(); } return 'video'; }
    } catch (e) {}
  }

  var best = null, score = 0;
  var ifr = document.querySelectorAll('iframe');
  for (var j = 0; j < ifr.length; j++) {
    var el = ifr[j];
    var r2 = el.getBoundingClientRect();
    if (r2.width < 160 || r2.height < 100) continue;
    var cs = getComputedStyle(el);
    if (cs.visibility === 'hidden' || cs.display === 'none') continue;
    if (!el.src) continue;
    var s = r2.width * r2.height;
    if (/video|embed|player|rapidrame|sibnet|stream|watch/i.test(el.src)) s = s * 2;
    var idc = (String(el.className) + ' ' + el.id).toLowerCase();
    if (/ad-|ads|popup|overlay|banner|promo/.test(idc)) s = s * 0.25;
    if (s > score) { score = s; best = el; }
  }
  if (best && score > 20000) {
    var b = best.getBoundingClientRect();
    var nx = (b.left + b.width / 2) / window.innerWidth;
    var ny = (b.top + b.height / 2) / window.innerHeight;
    try { best.focus(); } catch (e) {}
    return JSON.stringify({ kind: 'iframe', nx: nx, ny: ny });
  }

  // 4) Oynatıcı henüz yüklenmedi: oynatıcı alanındaki "oynat" tetikleyicisine bas
  var scope = document.querySelector('.video-player-container-here, .player-container, ' +
              '[class*=player-container], #player, .player') || document;
  var triggers = ['.play-that-video', '.vjs-big-play-button', '[class*=play-button]',
                  '[class*=playButton]', '[id*=play-button]', '[class*=play-icon]',
                  '.player-cover', '.poster-play', '.play-btn'];
  for (var t = 0; t < triggers.length; t++) {
    var el2 = scope.querySelector(triggers[t]);
    if (!el2) continue;
    var pr = el2.getBoundingClientRect();
    if (pr.width < 20 || pr.height < 20) continue;
    if (getComputedStyle(el2).visibility === 'hidden') continue;
    if (el2.tagName === 'A') {
      var href = el2.getAttribute('href') || '';
      if (href && href.indexOf('javascript:') !== 0 && href.charAt(0) !== '#') continue;
    }
    try { el2.click(); } catch (e) {}
    return 'started';
  }

  return 'none';
})()
"""

    /** Sayfa acilinca oynatıcıyi isaretler (arac cubugu icin hazir bilgi). */
    const val ANALYZE_JS = """
(function(){
  window.__tvPlayerKind = 'none';
  var vids = document.querySelectorAll('video');
  for (var i = 0; i < vids.length; i++) {
    var r = vids[i].getBoundingClientRect();
    if (r.width > 120 && r.height > 80) { window.__tvPlayerKind = 'video'; return; }
  }
  var ifr = document.querySelectorAll('iframe');
  for (var j = 0; j < ifr.length; j++) {
    var b = ifr[j].getBoundingClientRect();
    if (b.width > 160 && b.height > 100 && ifr[j].src) { window.__tvPlayerKind = 'iframe'; return; }
  }
})()
"""
}
