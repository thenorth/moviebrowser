const sites = [
  ["HD Film Cehennemi", "https://www.hdfilmcehennemi.nl/"],
  ["Film Makinesi", "https://filmmakinesi.to/"],
  ["Film Dozu", "https://filmdozu.website/"],
  ["HD Film Izle", "https://www.hdfilmizle.to/"],
  ["U Film Izle", "https://ufilmizle.com/"],
  ["JetFilmizle", "https://jetfilmizle.sbs/"],
  ["SineMerkez", "https://sinemerkez.com/"],
  ["İzle Son HD Film", "https://izlesonhdfilm.com/"],
  ["Film İzle Ya", "https://filmizleya.com/"],
  ["HDFilmizlesende", "https://hdfilmizlesende.com/"],
  ["HDFilmZevki", "https://hdfilmzevki.co/"],
  ["FilmIzzle", "https://filmizzle.com/"],
  ["HD Film İzle (2)", "https://film-izle.com.tr/"],
  ["HD Film ve Dizi", "https://filmcenneti.org/"]
];
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

(async () => {
  const out = [];
  for (const [name, url] of sites) {
    const t0 = Date.now();
    const ctrl = new AbortController();
    const to = setTimeout(() => ctrl.abort(), 15000);
    try {
      const r = await fetch(url, {
        redirect: "follow",
        signal: ctrl.signal,
        headers: { "User-Agent": UA, "Accept-Language": "tr-TR,tr;q=0.9", "Accept": "text/html,application/xhtml+xml" }
      });
      clearTimeout(to);
      const html = await r.text();
      const title = (html.match(/<title[^>]*>([\s\S]*?)<\/title>/i) || [, ""])[1].replace(/\s+/g, " ").trim().slice(0, 55);
      const cf = /just a moment|cf-browser-verification|challenge-platform|attention required/i.test(html);
      const film = /izle/i.test(html) && /(dublaj|altyaz)/i.test(html);
      out.push({ name, status: r.status, final: r.url.slice(0, 70), kb: Math.round(html.length / 1024), cf, film, title, ms: Date.now() - t0 });
    } catch (e) {
      clearTimeout(to);
      out.push({ name, error: String(e.message || e).slice(0, 50), ms: Date.now() - t0 });
    }
  }
  console.log(JSON.stringify(out, null, 1));
})();
