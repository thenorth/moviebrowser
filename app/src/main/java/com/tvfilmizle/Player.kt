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
  if (v) { if (v.paused) { try { v.play(); } catch (e) {} return 'video'; } v.pause(); return 'paused'; }

  for (var f = 0; f < window.frames.length; f++) {
    try {
      var fv = bigVideo(window.frames[f].document);
      if (fv) { if (fv.paused) { try { fv.play(); } catch (e) {} return 'video'; } fv.pause(); return 'paused'; }
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

  // 5) Oynat dugmesi bir div/resim uzerinde: en buyuk afis/kaplama alanina dokun.
  var bestEl = null, bestArea = 0;
  var cand = document.querySelectorAll('img, [class*=poster], [class*=cover], [class*=thumb], [class*=player]');
  for (var k = 0; k < cand.length; k++) {
    var ce = cand[k];
    var cr = ce.getBoundingClientRect();
    if (cr.width < 160 || cr.height < 120) continue;
    var ccs = getComputedStyle(ce);
    if (ccs.visibility === 'hidden' || ccs.display === 'none') continue;
    if (cr.top >= window.innerHeight) continue;
    var area = cr.width * cr.height;
    if (area > bestArea) { bestArea = area; bestEl = ce; }
  }
  if (bestEl && bestArea > window.innerWidth * window.innerHeight * 0.12) {
    var br = bestEl.getBoundingClientRect();
    var nx2 = (br.left + br.width / 2) / window.innerWidth;
    var ny2 = (br.top + br.height / 2) / window.innerHeight;
    return JSON.stringify({ kind: 'tap', nx: nx2, ny: ny2 });
  }

  return 'none';
})()
"""

    /**
     * Oynatıcının ikinci (büyük) oynat düğümünü başlatır; video zaten oynuyorsa dokunmaz.
     * Donus: "playing" | "started" | "none" (none = iframe ici, merkeze dokunmak gerekir).
     */
    const val PLAY_JS = """
(function(){
  function big(doc){
    try {
      var vs = doc.querySelectorAll('video');
      for (var i = 0; i < vs.length; i++) {
        var r = vs[i].getBoundingClientRect();
        if (r.width > 120 && r.height > 80) return vs[i];
      }
    } catch (e) {}
    return null;
  }

  var v = big(document);
  if (v) { if (!v.paused) return 'playing'; try { v.play(); } catch (e) {} return 'started'; }

  for (var f = 0; f < window.frames.length; f++) {
    var fv = big(window.frames[f]);
    if (fv) { if (!fv.paused) return 'playing'; try { fv.play(); } catch (e) {} return 'started'; }
  }

  var triggers = ['.vjs-big-play-button', '.plyr__control[data-plyr=play]',
                  '[class*=play-button]', '[class*=playButton]', '[id*=play-button]',
                  '[class*=play-icon]', '.play-btn', '.poster-play', '.player-cover'];
  for (var t = 0; t < triggers.length; t++) {
    var els = document.querySelectorAll(triggers[t]);
    for (var k = 0; k < els.length; k++) {
      var el = els[k], r = el.getBoundingClientRect();
      if (r.width < 20 || r.height < 20) continue;
      var cs = getComputedStyle(el);
      if (cs.visibility === 'hidden' || cs.display === 'none') continue;
      try { el.click(); return 'started'; } catch (e) {}
    }
  }
  return 'none';
})()
"""

    /** Sayfadaki video akis URL'si (harici oynatici icin). */
    const val STREAM_JS = """
(function(){
  try {
    var v = document.querySelector('video');
    if (v) {
      if (v.currentSrc) return v.currentSrc;
      if (v.src) return v.src;
      var s = v.querySelector('source');
      if (s && s.src) return s.src;
    }
    var all = document.querySelectorAll('video source, video');
    for (var i = 0; i < all.length; i++) { if (all[i].src) return all[i].src; }
    var frames = document.querySelectorAll('iframe');
    for (var j = 0; j < frames.length; j++) {
      var src = frames[j].src || '';
      if (/\.m3u8|\.mp4/i.test(src)) return src;
    }
  } catch (e) {}
  return '';
})()
"""

    /**
     * Sayfa acilinca oynatıcıyi isaretler ve video oynamaya baslayinca kopruye haber verir
     * (otomatik tam ekran icin).
     */
    const val ANALYZE_JS = """
(function(){
  function notify(){
    try {
      var b = window.tvBridge || (window.parent && window.parent.tvBridge);
      if (b && b.playerStarted) b.playerStarted();
    } catch (e) {}
  }

  function hook(doc){
    try {
      var vs = doc.querySelectorAll('video');
      for (var i = 0; i < vs.length; i++) {
        var v = vs[i];
        if (v.getAttribute('data-tvhooked')) continue;
        v.setAttribute('data-tvhooked', '1');
        v.addEventListener('playing', notify);
        v.addEventListener('ended', function(){
          try {
            var b = window.tvBridge || (window.parent && window.parent.tvBridge);
            if (b && b.videoEnded) b.videoEnded();
          } catch (e) {}
        });
      }
    } catch (e) {}
  }

  hook(document);
  for (var f = 0; f < window.frames.length; f++) {
    try { hook(window.frames[f].document); } catch (e) {}
  }

  window.__tvPlayerKind = 'none';
  var vids = document.querySelectorAll('video');
  for (var i2 = 0; i2 < vids.length; i2++) {
    var r = vids[i2].getBoundingClientRect();
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
