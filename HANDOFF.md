# Devir Notu — Movie Browser (son güncelleme: 31.10.2026, v3.2)

Sonraki oturum için kısa durum. Ayrıntılı geçmiş git log'da.

## Durum
- Sürüm: `versionName 3.2` / `versionCode 32` (yerel commit, **push YOK**). Push kullanıcı "push" deyince.
- Paket: `com.tvfilmizle` (üzerine kurulum için değişmemeli).
- APK: `MovieBrowser-3.2.apk` (debug imzalı, sideload).
- Site listesi: `sites.json` (GitHub `thenorth/moviebrowser`), açılışta + 5 dk sonra çekilir; günlük keşif `.github/workflows/update-sites.yml`.

## Bu turda eklenenler (v3.2)
1. **Listem afişli**: `Watchlist.Item.poster`; `WatchlistActivity` 6 sütunlu afiş ızgarası, OK'a basılı tut = listeden çıkar.
2. **Afiş kaynağı**: film ızgarasından eklerken `Scraper.Film.poster`; tarayıcıdan eklerken sayfanın `og:image`/`twitter:image` değeri (`BrowserActivity.addToWatchlist`).
3. **Keşfet**: `DiscoverActivity` — ilk 6 siteden paralel kazıma, başlığa göre tekilleştirme, kart üstünde site adı (`item_film.xml` → `film_site`). Ana ekranda "Keşfet" kartı (`SiteKind.DISCOVER`).
4. **Kategoriler (dizi/film)**: `SiteFilmsActivity` üstünde chip şeridi, `Scraper.Page.categories` (nav/menü linklerinden), chip = o kategori sayfasını yükle.
5. **Scraper iyileştirmesi**: `li:has(img)` geçişi (afiş `<a>` dışında olan temalar), platform/logo görselleri elenir, gerçek ızgara slider'dan önce gelir.

## Doğrulanan (BlueStacks, Android 9)
- Keşfet ızgarası 24 film, site etiketi görünür; platform logo kartları elendi.
- Listem afişli ızgara (2 film), uzun basış = çıkar.
- SineMerkez: ana sayfa 27 film, kategori sayfası 28 film (ilk sırada kategori filmleri), chip tıklaması çalışıyor.
- Film ızgarasında OK'a basılı tut = Listeme ekle; chip şeridi ızgarayla çakışmıyor.

## Doğrulanmayan / riskli
- Tarayıcıdan `og:image` ile afiş kaydı (kod derlendi, uçtan uca denenmedi).
- Keşfet'te diğer sitelerin çoğu bu ağda kazınamıyor (SNI engeli) → tek site içeriği görülebilir.
- Kategori chip'leri bazı temalarda aynı içeriği döndürebilir (site sayfası gerçekten aynıysa).

## Build
```powershell
$env:JAVA_HOME="C:\Users\TheNorth\android-toolchain\jdk"
$env:ANDROID_HOME="C:\Users\TheNorth\android-toolchain\sdk"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug
```
APK: `app\build\outputs\apk\debug\app-debug.apk` → kök dizine `MovieBrowser-<sürüm>.apk` olarak kopyalanır.

## Sıradaki fikirler
- Son izlenenler / kaldığın yerden devam
- Film detay ekranı (özet + tür + fragman)
- Site favorileri (ızgaranın başına sabitle)
- Keşfet'te site filtresi (şu an sessizce ilk 6 site)
