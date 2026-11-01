# Devir Notu — Movie Browser (son güncelleme: 31.10.2026, v3.14)

Sonraki oturum için kısa durum. Ayrıntılı geçmiş git log'da.

## Durum
- **Sürüm numarası kuralı:** her değişiklikte artırma; kullanıcı "bitince/push" deyince tek seferde artır. Ara turlarda APK'yı aynı sürüm adıyla üretme, sadece `assembleDebug` + kur.
- Sürüm: `versionName 3.14` / `versionCode 44` (yerel commit, **push YOK**). Push kullanıcı "push" deyince.
- Paket: `com.tvfilmizle` (üzerine kurulum için değişmemeli).
- APK: `MovieBrowser-3.2.apk` (debug imzalı, sideload).
- Site listesi: `sites.json` (GitHub `thenorth/moviebrowser`), açılışta + 5 dk sonra çekilir; günlük keşif `.github/workflows/update-sites.yml`.

## Bu turda eklenenler (v3.14+)
- **Keşfet önbelleği** (`DiscoverCache`): sonuçlar 10 dk taze; sekmeye dönünce anında.
- **Son izlenenler + devam** (`History`): panel + menü satırı; en son açılan film en üstte; uzun basış = geçmişten çıkar.
- **Film detay paneli** (`panel_detail.xml`): afiş, ad, site, İZLE / Listeme ekle; GERİ bir önceki bölüme döner.
- **Keşfet filtresi**: site çipleri (Tümü + siteler).
- **Site favorileri** (`Favorites`): site kartında OK'a basılı tut → favori; favoriler ızgaranın başına geçer.
- **Otomatik VPN** (`BrowserActivity`): SiteHealth engelli işaretlediyse ve WARP kapalıysa hata anında VPN'i kendisi açar (host başına 1 kez).
- **Uygulama içi güncelleme**: APK DownloadManager ile iner, bitince kurulum ekranı açılır (`downloadReceiver`).
- **Dizi bölüm etiketi**: Son izlenenler'de başlıkta "3. Sezon 5. Bölüm" varsa site etiketi `S3B5` gösterir (tam sezon/bölüm listesi henüz yok).

### v3.2 notları

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
12. **Tek dokunuş (v3.9)**: menü satırları `focusableInTouchMode` olduğu için farede ilk dokunuş yalnız odak veriyordu; `OnTouchListener` ACTION_UP'ta `performClick()` çağırıyor → menü tek tıkla açılıyor. (Aynı turda 3.8'in tek-SOL düzeltmesi de dahil.)
13. **Geri alma (v3.11)**: 3.5–3.10 arası menü/panel denemesi geri alındı (kaynak `1726fa3` / v3.4 durumuna döndü). Sol menü ve sağ paneller yok; eylem kartları yine ana ızgarada (6 sütun), bölümler tam ekran ve **geri düğmeleri geri geldi**. Vektör ikonlar kartlarda korunuyor (`ic_*`, kart içindeki `site_logo`), yenileme ikonu dönüyor. Panel.kt silindi.
14. **Sol menü geri (v3.12)**: sol sabit menü vektör ikonlarla geri geldi (5 sütunlu ızgara, `paddingLeft 262dp`), ama bölümler **panel değil tam ekran** açılıyor ve geri düğmeleri var (panellerin cihazda çalışmama riski elendi). SOL → menü (seçim -1 iken de), menüde OK tek basışta (`OnKeyListener` DOWN), farede tek dokunuş (`OnTouchListener` → `performClick`), SAĞ → ızgara. `Panel.kt`/`PanelTheme` yok.
15. **Menü sabit + sağ panel (v3.13)**: bölümler artık **aynı Activity içinde** sağdaki `panel` FrameLayout'unda açılıyor (ayrı pencere yok). `Panels.kt` dört bölümü çizer: Listem, Keşfet, Site durumu, Ayarlar. `WatchlistActivity/DiscoverActivity/StatusActivity/SettingsActivity` silindi (manifest'ten de). Kumanda: menüde AŞAĞI/YUKARI + OK → panel yerinde değişir (odak menüde kalır); SAĞ → panel içeriği; panelde SOL → menü; GERİ → site ızgarası. Panellerde geri düğmesi ve alt ipucu gizlenir; üst boşluk 56dp'ye çekildi.
16. **Ara + Adres panelleri (v3.14)**: Film ara ve Adres gir artık dialog değil; `panel_input.xml` (başlık + **480dp giriş alanı + yanında buton**) ile sağ panelde açılıyor. `Panels.input(...)`; ara → Google (`<sorgu> film izle`), adres → `https://` eklenir. Odak giriş alanında, klavye otomatik açılır.
17. **Ana Sayfa satırı + listeden silme**: menünün ilk satırı `side_home` (ev ikonu) → site ızgarası. Listem panelinde **OK'a basılı tut** → "Listeden çıkarılsın mı?" onayı (SİL/İPTAL); uzun basış yalnız panel içeriği odaklıyken çalışır. Menü satır yüksekliği 46dp/6dp (8 satır 540dp TV'ye sığsın).
18. **Keşfet hızlandırıldı**: artık **tüm siteler** paralel taranır (6 site sınırı yok), site başına zaman aşımı 6 sn; sonuçlar geldikçe **anında** eklenir (en hızlı site önce). Tekilleştirme başlık yerine **URL** ile: aynı film farklı sitelerden ayrı kart olarak (site etiketiyle) girer. Her parti geldiğinde `interleave()` ile siteler sırayla karıştırılır (her siteden birer film), böylece akış tek siteden görünmez. Site başına 8 film.

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
