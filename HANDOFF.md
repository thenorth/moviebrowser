# Devir Notu — Movie Browser (son güncelleme: 31.10.2026, v3.8)

Sonraki oturum için kısa durum. Ayrıntılı geçmiş git log'da.

## Durum
- Sürüm: `versionName 3.8` / `versionCode 38` (yerel commit, **push YOK**). Push kullanıcı "push" deyince.
- Paket: `com.tvfilmizle` (üzerine kurulum için değişmemeli).
- APK: `MovieBrowser-3.2.apk` (debug imzalı, sideload).
- Site listesi: `sites.json` (GitHub `thenorth/moviebrowser`), açılışta + 5 dk sonra çekilir; günlük keşif `.github/workflows/update-sites.yml`.

## Bu turda eklenenler (v3.2)
1. **Listem afişli**: `Watchlist.Item.poster`; `WatchlistActivity` 6 sütunlu afiş ızgarası, OK'a basılı tut = listeden çıkar.
2. **Afiş kaynağı**: film ızgarasından eklerken `Scraper.Film.poster`; tarayıcıdan eklerken sayfanın `og:image`/`twitter:image` değeri (`BrowserActivity.addToWatchlist`).
3. **Keşfet**: `DiscoverActivity` — ilk 6 siteden paralel kazıma, başlığa göre tekilleştirme, kart üstünde site adı (`item_film.xml` → `film_site`). Ana ekranda "Keşfet" kartı (`SiteKind.DISCOVER`).
4. **Kategoriler (dizi/film)**: `SiteFilmsActivity` üstünde chip şeridi, `Scraper.Page.categories` (nav/menü linklerinden), chip = o kategori sayfasını yükle.
5. **Scraper iyileştirmesi**: `li:has(img)` geçişi (afiş `<a>` dışında olan temalar), platform/logo görselleri elenir, gerçek ızgara slider'dan önce gelir.
6. **OYNAT = tam ekran (v3.3)**: oynatma başlayınca sinema modu; oynatıcı yoksa 1,5 sn sonra bir kez daha dener.
7. **2. oynat düğmesi (v3.4)**: `Player.PLAY_JS` — aynı kaynaklı videoyu oynatır / sayfa içi oynat düğmesine basar; bulamazsa oynatıcı iframe'i için ekran merkezine bir kez dokunur (`autoStarted`, sayfa başına 1 kez). Duraklatma için `TOGGLE_JS` artık `paused` döner.
8. **Solda sabit menü (v3.5)**: eylem kartları ızgaradan çıktı; `activity_main.xml` içinde 222dp'lik `side_bar` (7 satır: ara, keşfet, listem, durum, adres, yenile, ayarlar). Izgara 5 sütun, `paddingLeft 262dp`. D-pad: ızgaranın ilk sütununda SOL → menü (`MainActivity.dispatchKeyEvent`), SAĞ → ızgara; menüden dönüşte `highlightSelected()` halkayı tazeler. `SiteKind` kaldırıldı.
9. **Sağdan açılan paneller + ikonlar (v3.6)**: `Panel.kt` + `PanelTheme` — Listem, Keşfet, Site durumu, Ayarlar artık ana ekranın sağında panel olarak açılıyor, soldaki menü görünür kalıyor (pencere `Gravity.END`, genişlik = ekran - 248dp, şeffaf zemin). Menü ikonları vektör: `ic_search/ic_discover/ic_star/ic_check/ic_plus/ic_refresh/ic_settings`. Touch-mode odak düzeltmeleri: `focusableInTouchMode` (grid + menü satırları), `fill()` sonunda `setSelection(0)` + `highlightSelected()`.
10. **Panel geri tuşu kaldırıldı (v3.7)**: panel layout'larındaki `btn_back` silindi; panellerde **SOL** = menüye dön (`dispatchKeyEvent` → `finish()`; ızgaralarda yalnız ilk sütunda). Listem/Keşfet alt ipucunda "SOL Menü" yazıyor. Kumanda: ana ekranda SOL → menü, menüde AŞAĞI/YUKARI + OK, panelde SOL → menü.
11. **Menü/kumanda düzeltmesi (v3.8)**: `selectedItemPosition` açılışta -1 olabildiği için SOL menüye hiç geçmiyordu → -1 iken 0 kabul edilir (ana ekran + Listem/Keşfet). Menü satırlarına `OnKeyListener` eklendi (DPAD_CENTER/ENTER/NUMPAD_ENTER/BUTTON_A, ACTION_DOWN) — UP göndermeyen kumandalarda da OK çalışır. `Panel.apply` artık pencere `attributes` (gravity/width/height) da yazıyor.

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
