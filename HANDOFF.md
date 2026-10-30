# Devir Notu — TV Film (son güncelleme: 21.10.2026)

Bu dosya bir sonraki oturum için hatırlatma listesidir.

## Durum
- APK: `TvFilm-1.0.apk` (debug imzalı, sideload)
- Repo: `github.com/thenorth/moviebrowser` (public — uygulama listeyi buradan çeker)
- Uygulama açılışta `sites.json`'ı indirir, 5 dk'dan eskiyse öne gelince yeniler, çevrimdışıysa son/ varsayılan listeyi kullanır.
- **Liste v3 (14 site)** — GitHub Action 26.10.2026'da çalışıp 2 site ekledi (film-izle.com.tr, filmcenneti.org).

## Yapılanlar (özet)
1. **Android TV WebView tarayıcı**: site listesi, reklam engelleme, oynatıcı seçenekleri, tam ekran, kumanda ile sayfa içi gezinme.
2. **Uzaktan liste**: `sites.json` + `SiteRepository`; "Listeyi güncelle" kartı (dönen ikon, "Liste güncellendi · N site").
3. **UI**: hero paneli + yatay kart sıraları, odakta büyüme/çerçeve, saat, alt tuş ipuçları. Renkler: zemin `#0A0F16`, vurgu `#FF9F1A`, metin `#F2F6FA`.
4. **Kumanda**: `Nav.kt` spatial navigation (yön tuşları + OK, turuncu çerçeve), `MENU` = eylem listesi, sayfa dibinde DOWN = araç çubuğu.
5. **Tam ekran** (`Fullscreen.kt`): oynatıcı iframe'ini `position:fixed; 100vw×100vh` yapar, ata `transform/filter` engellerini geçici kaldırır, reklam iframe'lerini puanlamayla eler; GERİ çıkar.
6. **Oynat** (`Player.kt`): video varsa doğrudan; cross-origin iframe ise oynatıcı merkezine gerçek dokunuş; oynatıcı kapalıysa sitedeki play tetikleyicisi (yalnızca oynatıcı kapsayıcısında).
7. **Liste keşfi**: `tools/discover.js` (13 Türkçe sorgu → başlık filtresi → site doğrulama → `sites.json`), `.github/workflows/update-sites.yml` haftalık çalışır.
8. **Adblock**: istek + sayfa içi + navigasyon engeli (`shouldOverrideUrlLoading`).

## Önemli dosyalar
| Dosya | İş |
|---|---|
| `MainActivity.kt` | Ana ekran, site kartları, güncelleme animasyonu |
| `BrowserActivity.kt` | WebView, araç çubuğu, tam ekran, menü, medya tuşları |
| `Player.kt` | Oynatıcı analizi + oynat/duraklat |
| `Fullscreen.kt` | Sayfa içi tam ekran |
| `Nav.kt` | Kumanda ile sayfa içi gezinme (JS) |
| `PlayerOptions.kt` | Kalite/sunucu/dil seçeneklerini toplama |
| `AdBlock.kt` | Reklam kuralları |
| `SiteRepository.kt` | `sites.json` indirme/önbellek |
| `Sites.kt` | Çevrimdışı varsayılan liste (12 site) |
| `sites.json` | Güncel site listesi (12 site) |
| `tools/discover.js` | Otomatik keşif |
| `.github/workflows/update-sites.yml` | Haftalık keşif |

## Doğrulama
- BlueStacks 5 Pie64 (Android 9, x86_64): fare tıkı, D-pad gezinme, site yükleme (masaüstü düzeni), film sayfası + oynatıcı, tam ekran, oynatıcı seçenekleri, liste güncelleme, çevrimdışı hata durumu.
- `AdBlockTest` 3/3 (ASCII yol kopyasında).

## Sıradaki işler / bilinen açıklar
1. **Gerçek TV testi yok**: overscan, kumanda tuş eşlemesi, medya tuşları sahada denenmeli.
2. **Cross-origin oynatıcı**: durum okunamadığı için OYNAT aç/kapa gibi; merkez dokunuşunu yok sayan oynatıcılarda kumanda ile kendi butonuna basılmalı.
3. **DRM (Widevine)** ve cihazın desteklemediği HLS kaynakları oynatılamayabilir.
4. **Adblock** liste tabanlı; yeni ağlar eklenmeli (`AdBlock.kt`).
5. **discover.js** arama motoru IP engeline takılabilir (DDG → Bing → DDG Lite fallback); sonuç yoksa liste değişmez.
6. **Yol**: proje klasörü Türkçe karakter içeriyor; birim testleri ASCII yolda çalışıyor (`android.overridePathCheck=true`).
7. **Açılmayan siteler (30.10.2026 ölçümü)** — hepsi Cloudflare arkasında, DNS'leri sağlam:
   - `hdfilmizle.to`, `filmdozu.website`, `film-izle.com.tr`, `filmcenneti.org`: TLS el sıkışması sıfırlanıyor (`net_error -101`) → site/Cloudflare bu IP aralığını engelliyor. VPN/farklı ağ ile denenmeli; uygulamadan çözülemez.
   - `filmizleya.com`: "Account Suspended" → listeden çıkarıldı.
   - `jetfilmizle.sbs`: `jetfilmizle.wiki`'ye yönleniyor → URL güncellendi.
   - Ağır sayfalarda WebView renderer çöküyordu → `largeHeap` + `onRenderProcessGone` ile otomatik kurtarma eklendi, yükleme hatası artık toast + log olarak görünüyor.

## Komutlar
```bash
gradlew assembleDebug                 # APK
node tools/discover.js --dry          # keşif raporu
node tools/discover.js                # listeye ekle
```

**Araç zinciri (kalıcı):**
```powershell
$env:JAVA_HOME="C:\Users\TheNorth\android-toolchain\jdk"
$env:ANDROID_HOME="C:\Users\TheNorth\android-toolchain\sdk"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug
```
(Önceki oturumdaki `%TEMP%\opencode\tools` kopyası silindi; yukarıdaki kalıcı klasörü kullan.)

BlueStacks ADB: `127.0.0.1:5555` (gerekirse `adb connect 127.0.0.1:5555`).

## Hatırlat (sonraki oturum)
- [ ] Gerçek TV'de uçtan uca test (film seç → oynat → tam ekran → GERİ)
- [ ] `v1.1` release oluştur ve APK'yı ekle
- [ ] Cross-origin oynatıcı için tuş yönlendirme (space / ok) dene
- [ ] Keşif betiğini bir kez GitHub Actions üzerinde elle çalıştırıp doğrula
