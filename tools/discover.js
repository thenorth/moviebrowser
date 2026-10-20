#!/usr/bin/env node
/**
 * Site kesfi: Turkce film sitelerini arama sonuclarindan bulur, dogrular ve sites.json'a ekler.
 * Kullanim: node tools/discover.js [--dry]
 * Bagimlilik yok (Node 18+ fetch).
 */
const fs = require("fs");
const path = require("path");

const ROOT = path.join(__dirname, "..");
const SITES_FILE = path.join(ROOT, "sites.json");

const QUERIES = [
  "Türkçe Dublaj ve Altyazılı HD Film izle",
  "Film İzle | Full HD Filmler",
  "HD Film İzle Türkçe Dublaj Altyazılı Full HD Filmler",
  "Film izle 2026 HD 4K Türkçe Dublaj Altyazılı",
  "HD film izle Türkçe Dublaj Film izle",
  "full hd film izle dublaj",
  "altyazılı film izle hd",
  "türkçe dublaj film izle",
  "yabancı film izle hd",
  "1080p film izle",
  "4k film izle",
  "hd film izle full",
  "film izle sitesi dublaj altyazı"
];

// Asla site olarak eklenmeyecek alan adlari
const BLOCK = [
  "google.", "youtube.", "youtu.be", "facebook.", "instagram.", "twitter.", "x.com", "tiktok.",
  "wikipedia.", "eksisozluk.", "reddit.", "pinterest.", "amazon.", "netflix.", "disneyplus.",
  "blutv.", "tod.tv", "apple.com", "microsoft.", "yandex.", "dailymotion.", "vimeo.", "izlesene.",
  "hdfilmcehennemi.nl", "filmmakinesi.to", "filmdozu.website", "hdfilmizle.to", "ufilmizle.com",
  "filmmodu", "dizimag", "dizipub", "turemzek", "hdfilmdelisi", "ekranda", "forum", "blogspot",
  "medium.com", "quora.", "linkedin.", "whatsapp.", "telegram.", "discord.", "t.me", "apk",
  "apkpure", "uptodown", "play.google", "apps.apple", "github.", "gitlab.", "cloudflare",
  "gelenek", "hdfilmcehennemi.com.tr",
  // arama motorlari
  "bing.", "duckduckgo.", "search.", "yahoo.", "ecosia.", "brave.com",
  // ucretli platformlar
  "nowtv.", "tvplus.", "exxen.", "gain.tv", "tabii.", "puhutv.", "mubi.", "primevideo.",
  "disney", "hbomax", "max.com", "appletv", "tivibu.", "digiturk", "beinsports", "sportstv",
  "kanald.", "atv.com.tr", "showtv", "startv.", "tv8.", "trt.", "ntv.", "cnnturk"
];

const KEYWORDS = ["film izle", "dublaj", "altyaz", "full hd", "hd film", "4k", "izle"];

function normalizeHost(url) {
  try {
    const u = new URL(url);
    return u.hostname.replace(/^www\./, "").toLowerCase();
  } catch { return null; }
}

function isBlocked(host) {
  return BLOCK.some(b => host === b || host.includes(b));
}

function cleanTitle(html) {
  return html
    .replace(/<[^>]+>/g, " ")
    .replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .replace(/&nbsp;/g, " ").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/\s+/g, " ")
    .trim();
}

function trLower(s) {
  return s.replace(/İ/g, "i").replace(/I/g, "ı").toLowerCase();
}

function looksLikeFilmSite(title, snippet) {
  const t = trLower(title + " " + snippet);
  let hits = 0;
  for (const k of KEYWORDS) if (t.includes(k)) hits++;
  return hits >= 2;
}

async function fetchText(url, timeout = 15000) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeout);
  try {
    const r = await fetch(url, {
      signal: ctrl.signal,
      redirect: "follow",
      headers: {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.6",
        "Accept": "text/html,application/xhtml+xml"
      }
    });
    return { status: r.status, text: await r.text(), finalUrl: r.url };
  } catch (e) {
    return { status: 0, text: "", error: String(e) };
  } finally {
    clearTimeout(timer);
  }
}

async function ddg(query) {
  const url = "https://html.duckduckgo.com/html/?q=" + encodeURIComponent(query) + "&kl=tr-tr";
  const r = await fetchText(url);
  if (r.status !== 200) return [];
  const out = [];
  const re = /<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/g;
  let m;
  while ((m = re.exec(r.text)) !== null) {
    let href = m[1].replace(/&amp;/g, "&");
    const title = cleanTitle(m[2]);
    if (href.includes("uddg=")) {
      try { href = decodeURIComponent(new URL("https:" + href.replace(/^\/\//, "//")).searchParams.get("uddg")); } catch {}
    }
    if (!/^https?:\/\//i.test(href)) continue;
    out.push({ url: href, title });
  }
  return out;
}

async function ddgLite(query) {
  const url = "https://lite.duckduckgo.com/lite/?q=" + encodeURIComponent(query) + "&kl=tr-tr";
  const r = await fetchText(url);
  if (r.status !== 200) return [];
  const out = [];
  const re = /<a[^>]+class="result-link"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/g;
  let m;
  while ((m = re.exec(r.text)) !== null) {
    out.push({ url: m[1].replace(/&amp;/g, "&"), title: cleanTitle(m[2]) });
  }
  return out;
}

function decodeBing(href) {
  try {
    const u = new URL(href);
    const v = u.searchParams.get("u");
    if (v && v.startsWith("a1")) {
      const b64 = v.slice(2).replace(/-/g, "+").replace(/_/g, "/");
      const dec = Buffer.from(b64, "base64").toString("utf8");
      if (/^https?:\/\//i.test(dec)) return dec;
    }
  } catch {}
  return href;
}

async function bing(query) {
  const url = "https://www.bing.com/search?q=" + encodeURIComponent(query) + "&setlang=tr&cc=tr&count=30";
  const r = await fetchText(url);
  if (r.status !== 200) return [];
  const out = [];
  const re = /<h2[^>]*><a[^>]+href="([^"]+)"[^>]*>([\s\S]*?)<\/a><\/h2>/g;
  let m;
  while ((m = re.exec(r.text)) !== null) {
    const href = decodeBing(m[1].replace(/&amp;/g, "&"));
    if (!/^https?:\/\//i.test(href)) continue;
    out.push({ url: href, title: cleanTitle(m[2]) });
  }
  return out;
}

async function searchAll(query) {
  for (const fn of [ddg, bing, ddgLite]) {
    try {
      const res = await fn(query);
      if (res.length > 0) return res;
    } catch (e) { /* sonraki kaynak */ }
    await new Promise(r => setTimeout(r, 1200));
  }
  return [];
}

async function validate(host) {
  const urls = ["https://" + host + "/", "http://" + host + "/"];
  for (const u of urls) {
    const r = await fetchText(u, 12000);
    if (r.status === 0) continue;
    // Cloudflare / bot korumasi: site var sayilir
    if (r.status === 403 || r.status === 503 || r.status === 429) return { ok: true, protected: true, html: "" };
    if (r.status >= 400) continue;
    const html = trLower(r.text);
    if (/for sale|buy this domain|domain is parked|satılık alan adı/.test(html)) return { ok: false };
    if (r.text.length < 3000) return { ok: false };
    const hasFilm = html.includes("izle");
    const hasLang = html.includes("dublaj") || html.includes("altyaz");
    if (hasFilm && hasLang) return { ok: true, protected: false, html: r.text };
    return { ok: false };
  }
  return { ok: false };
}

function brandFrom(html, host) {
  const m = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
  let name = m ? cleanTitle(m[1]) : "";
  if (name) {
    name = name.split(/\s+[|–-]\s+/)[0].trim();
    if (name.length > 26) name = name.slice(0, 26).trim();
  }
  if (!name || name.length < 3) name = host.split(".")[0];
  return name.charAt(0).toUpperCase() + name.slice(1);
}

(async () => {
  const dry = process.argv.includes("--dry");
  const current = JSON.parse(fs.readFileSync(SITES_FILE, "utf8"));
  const existing = new Set(current.sites.map(s => normalizeHost(s.url)).filter(Boolean));

  const candidates = new Map(); // host -> {title, url}
  for (const q of QUERIES) {
    const results = await searchAll(q);
    console.log(`[arama] ${q} -> ${results.length} sonuc`);
    for (const r of results) {
      const host = normalizeHost(r.url);
      if (!host) continue;
      if (isBlocked(host)) continue;
      if (existing.has(host)) continue;
      if (!looksLikeFilmSite(r.title, "")) continue;
      if (!candidates.has(host)) candidates.set(host, r);
    }
    await new Promise(res => setTimeout(res, 1500));
  }

  console.log(`\n[aday] ${candidates.size} yeni alan adi dogrulanacak\n`);
  const added = [];
  for (const [host, c] of candidates) {
    const v = await validate(host);
    const mark = v.ok ? (v.protected ? "OK*" : "OK ") : "-- ";
    console.log(`${mark} ${host}  (${c.title.slice(0, 60)})`);
    if (!v.ok) continue;
    added.push({ name: brandFrom(v.html, host), url: "https://" + host + "/" });
    await new Promise(res => setTimeout(res, 800));
  }

  if (added.length === 0) {
    console.log("\nYeni uygun site bulunamadi.");
    return;
  }

  added.sort((a, b) => a.name.localeCompare(b.name, "tr"));
  const merged = {
    version: (current.version || 1) + 1,
    updated: new Date().toISOString().slice(0, 10),
    sites: [...current.sites, ...added]
  };

  console.log(`\n[sonuc] ${added.length} site eklendi:`);
  added.forEach(s => console.log("  + " + s.name + " - " + s.url));

  if (dry) {
    console.log("\n(--dry: dosya yazilmadi)");
  } else {
    fs.writeFileSync(SITES_FILE, JSON.stringify(merged, null, 2) + "\n", "utf8");
    console.log("\nsites.json guncellendi (" + merged.sites.length + " site).");
  }
})();
