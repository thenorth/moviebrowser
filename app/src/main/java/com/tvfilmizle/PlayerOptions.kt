package com.tvfilmizle

object PlayerOptions {

    /** Sayfadaki oynatici seceneklerini (kalite, sunucu, dil, video kaynagi) toplar; her secenek uygulanabilir JS olarak doner. */
    const val COLLECT_JS = """
(function(){
  var out = [];
  var seen = {};
  var counter = 0;
  function add(label, script){
    label = (label || '').replace(/\s+/g, ' ').trim();
    if (!label || label.length > 60 || seen[label]) return;
    if (/^(close|kapat|x|×)$/i.test(label)) return;
    seen[label] = 1;
    out.push({label: label, script: script});
  }
  function labelOf(el){
    return String(el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim();
  }
  function tag(el){
    var id = el.getAttribute('data-tvopt');
    if (id) return id;
    id = 'tvopt' + (counter++);
    el.setAttribute('data-tvopt', id);
    return id;
  }
  function click(el, label){
    if (!el) return;
    add(label, 'var e=document.querySelector("[data-tvopt=' + tag(el) + '"]");if(e){e.click();}');
  }

  // 1) <select>: kalite / sunucu / dil
  var sels = document.querySelectorAll('select');
  for (var i = 0; i < sels.length; i++) {
    var sel = sels[i];
    var sid = tag(sel);
    for (var j = 0; j < sel.options.length; j++) {
      var t = (sel.options[j].textContent || sel.options[j].value || '').trim();
      if (!t) continue;
      add(t, 'var e=document.querySelector("[data-tvopt=' + sid + '"]");if(e){e.selectedIndex=' + j + ';e.dispatchEvent(new Event("change",{bubbles:true}));}');
    }
  }

  // 2) Oynatici / sunucu / dil kapsayicilarindaki secenekler
  var boxes = document.querySelectorAll(
    '[class*=alternative],[class*=player],[class*=server],[class*=source],[class*=quality],' +
    '[class*=language],[class*=dublaj],[class*=altyaz],[class*=altyap],[class*=subtit],[id*=player],[id*=server]'
  );
  for (var b = 0; b < boxes.length; b++) {
    var items = boxes[b].querySelectorAll('button, a, [role=button], [onclick], li');
    for (var k = 0; k < items.length; k++) {
      var el = items[k];
      var txt = labelOf(el);
      if (!txt || txt.length > 60 || el.children.length > 2) continue;
      click(el, txt);
    }
  }

  // 3) Metinden yakalanan kalite / dil secenekleri
  var rx = /(1080|720|480|360|240|4k|hdrip|webrip|bluray|dvdrip|hdtv|sunucu|server|altyap|kalite|quality|resol|m3u8|mp4|mkv|turk|türk|ingiliz|english|dublaj|dub|subtitle|altyaz|full|dual)/i;
  var nodes = document.querySelectorAll('button, a, li, [role="button"], [onclick]');
  for (var n = 0; n < nodes.length; n++) {
    var nd = nodes[n];
    var tx = labelOf(nd);
    if (!tx || tx.length > 40 || !rx.test(tx)) continue;
    if (nd.children.length > 3) continue;
    if (nd.closest('[class*=breadcrumb],[class*=crumbs],header,footer')) continue;
    if (nd.tagName === 'A') {
      var href = nd.getAttribute('href') || '';
      if (href && href.charAt(0) !== '#' && href.indexOf('javascript:') !== 0) continue;
    }
    click(nd, tx);
  }

  // 4) iframe oynatici kaynaklari (dogrudan oynatmak icin)
  var frames = document.querySelectorAll('iframe');
  for (var f = 0; f < frames.length; f++) {
    var src = frames[f].src || '';
    if (!src) continue;
    if (/doubleclick|googlesyndication|adsterra|popads|exoclick|adservice|googleads/i.test(src)) continue;
    var host = src.split('/')[2] || 'iframe';
    add('Oynatici: ' + host, 'location.href = ' + JSON.stringify(src) + ';');
  }

  // 5) <video> / <source> kaynaklari
  var vids = document.querySelectorAll('video');
  for (var v = 0; v < vids.length; v++) {
    var vsrc = vids[v].currentSrc || vids[v].src || '';
    var sources = vids[v].querySelectorAll('source');
    for (var s = 0; s < sources.length; s++) {
      var ssrc = sources[s].src || '';
      if (!ssrc) continue;
      add('Video kaynagi: ' + (ssrc.split('/')[2] || ''), 'location.href = ' + JSON.stringify(ssrc) + ';');
    }
    if (!vsrc) continue;
    add('Video ' + (v + 1) + ': ' + (vsrc.split('/')[2] || ''), 'location.href = ' + JSON.stringify(vsrc) + ';');
  }

  return JSON.stringify(out.slice(0, 60));
})()
"""

    /** TV kumandasinda odagin nerede oldugunu gormek icin odak cercevesi. */
    const val FOCUS_JS = """
(function(){
  if (!document.head) return;
  var s = document.createElement('style');
  s.type = 'text/css';
  s.appendChild(document.createTextNode('a:focus,button:focus,select:focus,input:focus,li:focus,[tabindex]:focus{outline:3px solid #ff9800 !important;background:rgba(255,152,0,0.18) !important;}'));
  document.head.appendChild(s);
})()
"""

    private const val FIND_VIDEO = """
var v=document.querySelector('video');
if(!v){try{for(var i=0;i<frames.length;i++){try{v=frames[i].document.querySelector('video');if(v)break;}}catch(e){}}}catch(e){}}
"""

    val MEDIA_PLAY = "(function(){$FIND_VIDEO if(v){v.play();}})()"
    val MEDIA_PAUSE = "(function(){$FIND_VIDEO if(v){v.pause();}})()"
    val MEDIA_TOGGLE = "(function(){$FIND_VIDEO if(v){if(v.paused){v.play();}else{v.pause();}}})()"
    val MEDIA_FF = "(function(){$FIND_VIDEO if(v){v.currentTime+=15;v.play();}})()"
    val MEDIA_RW = "(function(){$FIND_VIDEO if(v){v.currentTime-=15;v.play();}})()"
    val MEDIA_FULLSCREEN = """
(function(){
  $FIND_VIDEO
  if(!v) return;
  var el = v;
  if (el.requestFullscreen) { el.requestFullscreen(); }
  else if (el.webkitRequestFullscreen) { el.webkitRequestFullscreen(); }
  else if (el.webkitEnterFullscreen) { el.webkitEnterFullscreen(); }
})()
"""
}
