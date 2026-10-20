package com.tvfilmizle

/**
 * Sayfa icinde kumanda ile gezinme: yon tuslari en yakin tiklanabilir ogeye odaklanir, OK tiklar.
 * Sayfa altindaki son ogede DOWN -> tvBridge.exitFocus() ile alt arac cubuguna gecilir.
 */
object Nav {

    const val JS = """
(function(){
  if (window.__tvNavReady) return;
  window.__tvNavReady = true;

  var prev = null;
  var cur = null;

  var st = document.createElement('style');
  st.textContent = '.tv-focus{outline:3px solid #ff9800!important;outline-offset:-3px!important;' +
    'box-shadow:inset 0 0 0 6px rgba(255,152,0,.22)!important;filter:brightness(1.18)!important;}';
  (document.head || document.documentElement).appendChild(st);

  function shown(el){
    if (!el || !el.getBoundingClientRect) return false;
    var r = el.getBoundingClientRect();
    if (r.width < 10 || r.height < 10) return false;
    var s = getComputedStyle(el);
    return s.visibility !== 'hidden' && s.display !== 'none' && s.opacity !== '0';
  }

  function items(){
    var all = document.querySelectorAll('a[href],button,[role=button],[onclick],input,select,textarea');
    var out = [];
    for (var i = 0; i < all.length && out.length < 300; i++) {
      if (shown(all[i])) out.push(all[i]);
    }
    return out;
  }

  function mid(r){ return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; }

  function pick(dir){
    var els = items();
    if (!els.length) return null;
    if (!cur) {
      var ae = document.activeElement;
      if (ae && ae !== document.body && els.indexOf(ae) >= 0) cur = ae;
    }
    if (!cur || els.indexOf(cur) < 0) return els[0];
    var base = cur.getBoundingClientRect();
    var fc = mid(base);
    var best = null, bestScore = 1e9;
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (el === cur) continue;
      var r = el.getBoundingClientRect();
      if (r.width < 10 || r.height < 10) continue;
      var c = mid(r);
      var dx = c.x - fc.x, dy = c.y - fc.y;
      var ovX = r.left < base.right && r.right > base.left;
      var ovY = r.top < base.bottom && r.bottom > base.top;
      var score = 1e9;
      if (dir === 'up' && dy < -6) score = -dy + Math.abs(dx) * 2.2 - (ovX ? -dy * 0.6 : 0);
      if (dir === 'down' && dy > 6) score = dy + Math.abs(dx) * 2.2 - (ovX ? dy * 0.6 : 0);
      if (dir === 'left' && dx < -6) score = -dx + Math.abs(dy) * 2.2 - (ovY ? -dx * 0.6 : 0);
      if (dir === 'right' && dx > 6) score = dx + Math.abs(dy) * 2.2 - (ovY ? dx * 0.6 : 0);
      if (score < bestScore) { bestScore = score; best = el; }
    }
    return best;
  }

  function focusIt(el){
    if (!el) return false;
    if (prev && prev !== el) prev.classList.remove('tv-focus');
    prev = cur = el;
    try { el.focus({ preventScroll: true }); } catch (e) { try { el.focus(); } catch (e2) {} }
    el.classList.add('tv-focus');
    var r = el.getBoundingClientRect();
    if (r.top < 70 || r.bottom > window.innerHeight - 90) {
      try { el.scrollIntoView({ block: 'center' }); } catch (e3) { el.scrollIntoView(); }
    }
    return true;
  }

  function activate(el){
    if (!el) return;
    var t = el.tagName;
    if (t === 'INPUT' || t === 'TEXTAREA' || t === 'SELECT') { el.focus(); el.click(); return; }
    el.click();
  }

  document.addEventListener('keydown', function(e){
    var k = e.keyCode;
    if (k !== 37 && k !== 38 && k !== 39 && k !== 40 && k !== 13) return;
    var ae = document.activeElement;
    if (ae && ae.tagName === 'IFRAME') return;
    if (ae && (ae.tagName === 'INPUT' || ae.tagName === 'TEXTAREA') && (k === 37 || k === 39)) return;

    var dir = k === 38 ? 'up' : k === 40 ? 'down' : k === 37 ? 'left' : k === 39 ? 'right' : null;
    if (dir) {
      var nx = pick(dir);
      if (nx) { focusIt(nx); e.preventDefault(); e.stopPropagation(); return; }
      if (dir === 'down') {
        e.preventDefault(); e.stopPropagation();
        if (window.tvBridge) { try { window.tvBridge.exitFocus(); } catch (err) {} }
      }
      return;
    }
    if (k === 13 && cur) {
      activate(cur);
      e.preventDefault(); e.stopPropagation();
    }
  }, true);

  document.addEventListener('mousedown', function(){
    if (prev) { prev.classList.remove('tv-focus'); prev = null; cur = null; }
  }, true);
})()
"""
}
