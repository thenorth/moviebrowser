# TV Film — Android TV film tarayıcısı

Android TV (ve telefon/emülatör) için WebView tabanlı tarayıcı. Açılışta site listesini
bu repodaki **`sites.json`** dosyasından çeker; listede yaptığınız değişiklik uygulamanın
bir sonraki açılışında (veya "Listeyi güncelle" kartına basınca) anında geçerli olur.

## Özellikler

- **Site listesi GitHub'dan**: `sites.json` düzenlenir, uygulama açılışta çeker. Ağ yoksa son bilinen liste kullanılır.
- **Kumanda ile gezinme**: yön tuşları sayfadaki linklere/butonlara odaklanır (turuncu çerçeve), OK tıklar.
  Sayfanın en altında DOWN → alt araç çubuğu. `MENU` tuşu tüm eylemleri listeler.
- **Reklam engelleme**: istek seviyesinde reklam/pop ağları + sayfa içi reklam kutuları.
- **Oynatıcı seçenekleri**: sayfadaki kalite/sunucu/dil seçenekleri, iframe oynatıcılar ve video kaynakları tek listede.
- **Tam ekran**: araç çubuğu + sistem çubukları gizlenir (GERİ ile çıkış).
- **Yeni pencere**: oynatıcı yeni sekme açarsa aynı ekranda gösterilir.
- Medya tuşları: oynat / duraklat / ileri / geri.

## Site listesini güncelleme

`sites.json`:

```json
{
  "version": 1,
  "updated": "2026-10-20",
  "sites": [
    { "name": "Site Adı", "url": "https://ornek.com/" }
  ]
}
```

GitHub üzerinden dosyayı düzenleyip commit edin — uygulama bir sonraki açılışta listeyi çeker.
Uygulama içinden zorlamak için ana ekrandaki **Listeyi güncelle** kartına basın.

Liste adresi: `https://raw.githubusercontent.com/thenorth/moviebrowser/main/sites.json`

## Derleme

```bash
gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
gradlew test               # reklam engelleme kural testleri
```

APK Android 5.1 (API 21) ve üzerinde çalışır. TV'ye kurmak için:

```bash
adb connect <TV-IP>:5555
adb install -r app-debug.apk
```

veya APK'yı USB ile TV'ye kopyalayıp dosya yöneticisinden kurun.

## Yapı

| Dosya | İş |
|---|---|
| `app/src/main/java/com/tvfilmizle/MainActivity.kt` | Site listesi ekranı (GridView, uzaktan liste) |
| `.../BrowserActivity.kt` | WebView, araç çubuğu, tam ekran, MENU |
| `.../AdBlock.kt` | Reklam kuralları (istek + sayfa içi) |
| `.../PlayerOptions.kt` | Oynatıcı seçeneklerini toplayan JS |
| `.../Nav.kt` | Kumanda ile sayfa içi odak gezinmesi (JS) |
| `.../SiteRepository.kt` | `sites.json` indirme/önbellek |
| `sites.json` | Güncel site listesi (buradan yönetilir) |

> Not: Proje yolu Türkçe karakter içeriyorsa Windows'ta AGP uyarı verir; `gradle.properties`
> içindeki `android.overridePathCheck=true` bunu susturur. Birim testleri ASCII yolda çalışır.
