# Kurulum ve Kullanım Kılavuzu

Ortamınız **tamamen hazırlandı**. Bu dosya neyin kurulduğunu ve nasıl kullanacağınızı anlatır.

---

## 1. Kurulan Bileşenler

| Bileşen | Sürüm | Konum |
|---|---|---|
| **JDK (Temurin)** | 17.0.20.1 | `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` |
| **Android SDK** | cmdline-tools latest | `%LOCALAPPDATA%\Android\Sdk` |
| ├ platform-tools (adb) | — | `%LOCALAPPDATA%\Android\Sdk\platform-tools` |
| ├ platforms | android-34 | `%LOCALAPPDATA%\Android\Sdk\platforms\android-34` |
| ├ build-tools | 34.0.0 | `%LOCALAPPDATA%\Android\Sdk\build-tools\34.0.0` |
| ├ emulator | — | `%LOCALAPPDATA%\Android\Sdk\emulator` |
| └ system-image | android-34 default x86_64 | `%LOCALAPPDATA%\Android\Sdk\system-images` |
| **Android Studio** | 2026.2.1.8 | `C:\Program Files\Android\Android Studio` |
| **Gradle** | 8.7 (wrapper dahil) | Proje içinde `gradlew.bat` |
| **SDK lisansları** | Tümü kabul edildi | — |

Ortam değişkenleri (PATH) kalıcı olarak ayarlandı: `JAVA_HOME`, `ANDROID_HOME`, `adb`, `sdkmanager`, `emulator`.

---

## 2. Hızlı Başlangıç

### Yöntem A — Komut satırı ile (en kolay)
Proje klasöründe çift tıklayın:
```
derle.bat
```
APK oluşturulur ve proje köküne `AltinAramaHarita.apk` olarak kopyalanır.

### Yöntem B — Android Studio ile
1. Android Studio'yu açın.
2. **Open** → `AltinAramaHarita` klasörünü seçin.
3. Gradle senkronizasyonunu bekleyin (ilk seferde biraz sürer).
4. **Run ▶** butonuna basın veya **Build > Make Project**.

### Yöntem C — Emülatör ile test
```
emulator-baslat.bat
```
Ardından ayrı bir komut isteminde:
```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

---

## 3. Telefona Kurulum (Önerilen yöntem)

Sahada gerçek telefon kullanmak en doğrusu (GPS gerçek konum verir).

1. Telefonda **Geliştirici Seçenekleri** → **USB Hata Ayıklama**'yı açın.
2. Telefonu USB ile bilgisayara bağlayın.
3. Telefonda "USB ile hata ayıklama" onayını kabul edin.
4. Komut isteminde:
   ```
   adb install -r AltinAramaHarita.apk
   ```
5. Alternatif: APK'yı telefona kopyalayıp dosyaya tıklayarak "bilinmeyen kaynaklardan yükleme" iznini verin.

**Uygulamayı test etmek için** `About > Build number` (7 kez tıklama) ile Geliştirici Modu'nu açıp USB hata ayıklamayı etkinleştirin.

---

## 4. Önemli: Türkçe Karakterli Klasör Yolu Sorunu

Proje klasörü `Masaüstü\Altın Arama Harita` yolunda olduğu için (Türkçe karakter: ü, ı, ş) Android Gradle Plugin varsayılan olarak yolu reddeder. Bu nedenle `gradle.properties` içine şu satır eklendi:

```properties
android.overridePathCheck=true
```

Bu, kurulumunuzun çalışması için **gereklidir**. İleride sorun yaşarsanız en garantili çözüm projeyi ASCII karakterli bir yola taşımaktır, örneğin:
```
C:\AndroidProjeler\AltinAramaHarita
```

---

## 5. Çevrimdışı (Offline) Harita — HAZIR

Uygulama **gerçek çevrimdışı harita** desteğine sahiptir. `assets/offline/afyon.mbtiles`
dosyası varsa uygulama **tamamen internetsiz** çalışır.

### Mevcut Durum
- `assets/offline/afyon.mbtiles` → projeye **dahil** (Afyonkarahisar + çevresi)
- Uygulama açılışta bu dosyayı `filesDir/osmdroid_tiles/` klasörüne kopyalar
- OSMDroid'in arşiv tarayıcısı bu klasörü otomatik tarar ve karoları yerel MBTiles'ten okur
- Sol alt köşedeki etiket **"ÇEVRİMDIŞI HARİTA"** veya **"ÇEVRİMİÇİ HARİTA"** modunu gösterir
- Dosya yoksa otomatik olarak çevrimiçi OpenStreetMap'e düşer

### Kapsam (zoom planı)

| Plan | Zoom | Kapsam (K / D) | Amaç |
|---|---|---|---|
| BÖLGE | 10, 11 | 37.85-39.60 / 28.95-32.05 | Geniş bölge görünümü, ilçeler arası rota |
| İL | 12 | 37.95-39.25 / 29.35-31.65 | Afyonkarahisar il geneli (varsayılan açılış görünümü) |
| YAKIN ÇEVRE | 13 | 38.00-39.15 / 30.00-31.45 | Tüm altın noktalarını kapsayan çalışma alanı (~19 m/piksel) |
| ŞEHİR | 14 | 38.68-38.86 / 30.40-30.68 | Şehir merkezi ve yakın dere yatakları |
| ŞEHİR ÇEKİRDEK | 15 | 38.71-38.82 / 30.47-30.61 | Afyonkarahisar merkez, metre hassasiyetinde konumlandırma |

> Uygulama varsayılan olarak **zoom 12** ile açılır — bu zoomda çevrimdışı harita
> ekranın tamamını kaplar. Daha uzağa yakınlaştırmak (zoom 10-11) bölge genelini
> gösterir; daha yakına (zoom 13-15) altın noktalarına odaklanır.

### Neden OpenTopoMap?

`tile.openstreetmap.org` sunucusu **toplu indirmeyi engeller** ("Access blocked"
placeholder karoları döndürür) — bu, OpenStreetMap kullanım politikasının gereğidir.
Bu yüzden offline harita **OpenTopoMap** kaynağından üretilir:
- Topografik (eş yükselti eğrileri, patikalar, akarsu yatakları) → **altın arama için çok daha uygun**
- Ücretsiz, atıf zorunlu: *"Harita verileri © OpenStreetMap katılımcıları, OpenTopoMap (CC-BY-SA)"*

Uygulama canlı modda (çevrimiçi) OpenStreetMap'in standart karolarını kullanır,
çevrimdışı modda ise gömülü OpenTopoMap MBTiles'ini.

### Kendi Bölgenizin Haritasını Üretmek
Proje içinde hazır bir Python betiği var:

```bash
py -3 tools\build_mbtiles.py
```

Betik, OpenTopoMap karolarını indirip MBTiles (SQLite) formatında birleştirir.
3 iş parçacığı ve istek başına 0.35 sn bekleme kullanır (sunucuya nazik kalmak için).
Varsayılan bölge Afyonkarahisar'dır; değiştirmek için `tools/build_mbtiles.py`
dosyasındaki `REGION`, `DISTRICT`, `MID`, `CITY`, `CITY_CORE` sözlüklerini düzenleyin.

**Resume:** Betik veritabanında zaten bulunan karoları indirmez, sadece eksikleri
tamamlar. Tam sıfırdan üretmek için:
```bash
py -3 tools\build_mbtiles.py --fresh
```

Betikten sonra `assets/offline/afyon.mbtiles` güncellenir, `derle.bat` ile APK
yeniden derlenir.

> Yardımcı araç: `tools/test_tile_sources.py` — farklı karo sunucularını test eder,
> hangisinin gerçek harita döndürdüğünü (engelleme placeholder'ı mı?) gösterir.

> ⚠️ İndirme bir gecede veya boşta kaldığınızda başlatın. ~2500 karo indirilir.

### Hazır MBTiles Alternatifleri
- [OpenMapTiles — Afyonkarahisar](https://openmaptiles.org/downloads/afyonkarahisar/)
- [MapTiler](https://maptiler.com/download/)
- [Mobile Atlas Creator](https://github.com/ActiveM4/Mobile-Atlas-Creator) (kendi bölgenizi seçin)

### Teknik Not (OSMDroid 6.1.1) — ÖNEMLİ
MBTiles desteği `org.osmdroid.tileprovider.modules.MBTilesFileArchive` ile gelir.
İki kritik nokta var:

1. **Arşiv dizini:** OSMDroid arşiv dosyalarını tile source'un `basePath`'inden değil,
   `Configuration.getInstance().setOsmdroidBasePath(...)` ile verilen **dizinden** tarar.
   `TileSourceProvider.kt` MBTiles'i uygulama dizinine kopyalar ve o dizini base path
   olarak ayarlar.

2. **Sıralama kritik:** OSMDroid, `MapView` **constructor'ında** arşiv listesini
   oluşturur ve `setTileSource()` bu listeyi **yenilemez**. Bu yüzden
   `TileSourceProvider.prepare()` çağrısı `setContentView()`'den **önce** yapılmalıdır.
   Aksi halde arşiv listesi boş kalır ve karolar yüklenmez (harita boş görünür).

MBTiles satır sırası **TMS** (y çevrilmiş) standardındadır — betik buna uygun yazılmıştır.

### Sorun Giderme: Harita Boş Görünüyorsa
```bash
adb logcat | findstr OsmDroid
```
- `"Found tile"` satırları görünüyorsa → arşiv çalışıyor, sorun yok.
- `"Use tile from archive"` **görünmüyorsa** → arşiv listesi boş demektir;
  `TileSourceProvider.prepare()`'ın `setContentView()`'den önce çağrıldığını doğrulayın.

---

## 6. Sık Karşılaşılan Sorunlar

| Sorun | Çözüm |
|---|---|
| `SDK location not found` | `local.properties` dosyasını kontrol edin. `sdk.dir` yolu doğru olmalı. |
| `JAVA_HOME is not set` | Yeni bir komut istemi açın (PATH değişiklikleri eski pencerelerde görünmez). |
| Gradle "non-ASCII path" hatası | `gradle.properties` içinde `android.overridePathCheck=true` olduğundan emin olun. |
| **APK beklenmedik büyük (2 kat)** | `derle.bat` zaten `clean assembleDebug` çalıştırır. Elle derlerken de önce `gradlew clean` deyin — değişen MBTiles asset'inde bayat metadata APK'yı şişirir. |
| Harita açılıyor ama karolar gelmiyor | `adb logcat \| findstr OsmDroid` → "Use tile from archive" görünmüyorsa arşiv listesi boştur (bkz. Teknik Not). |
| Harita gri/boş, "Access blocked" yazısı çıkıyor | MBTiles yanlış kaynaktan üretilmiş. `py -3 tools\build_mbtiles.py --fresh` ile yeniden üretin (OpenTopoMap kullanılmalı). |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | `adb uninstall com.altinarama.harita` sonra tekrar yükleyin. |
| Emülatör açılmıyor | Hyper-V'nin etkin olduğundan emin olun (Windows özelliklerinden açabilirsiniz). |

---

## 7. Derleme komutları (referans)

```bash
# Debug APK üret
./gradlew assembleDebug

# İmzalı Release APK üret
./gradlew assembleRelease

# APK'yı telefona yükle
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Temiz derleme
./gradlew clean assembleDebug

# Android Studio'dan logları izle
adb logcat | findstr "altinarama"
```

---

## 8. GitHub Yayın ve Otomatik Güncelleme

### Depo
👉 **https://github.com/MonchereB/AltinAramaHarita**

Kaynak kod depoda; **81 MB'lık offline harita ve imzalama anahtarı depoya girmez**
(`.gitignore` ile dışlandı). CI bunları kendisi hazırlar.

### Yeni sürüm yayınlama
```bash
git tag v1.1.0
git push origin v1.1.0
```
GitHub Actions otomatik olarak:
1. Önceki sürümdeki `afyon.mbtiles`'ı indirir (yoksa `tools/build_mbtiles.py` ile üretir)
2. İmzalı Release APK'sını derler
3. Releases sayfasına `AltinAramaHarita.apk` olarak yükler

### GitHub Secrets (ilk kurulumda bir kez)
**Repo → Settings → Secrets and variables → Actions → New repository secret**

| Secret | Değer |
|---|---|
| `KEYSTORE_BASE64` | `altinarama.keystore` dosyasının base64 içeriği |
| `KEYSTORE_PASSWORD` | `altinarama` |
| `KEY_ALIAS` | `altinarama` |
| `KEY_PASSWORD` | `altinarama` |

Base64 içeriğini üretmek:
```bash
certutil -encode altinarama.keystore keystore_b64.txt
# certutil "BEGIN CERTIFICATE" ekler; onu silip geri kalanı kullanın
```

### Telefonda güncelleme
Uygulama açılışta GitHub'ı kontrol eder. Yeni sürüm varsa **Güncelle** butonu
(varsayılan olarak açılışta kontrol edilir) ile APK indirilir ve Android'un kendi
kurulum onay penceresi gösterilir.

> **Önemli:** Güncellemenin çalışması için **kurulu APK ile yeni APK aynı imza
> anahtarıyla** imzalanmalıdır. Bu yüzden telefona **Release** APK'sı kurulmalıdır
> (debug APK'sı Release APK'sını güncelleyemez).

### İlk kurulum (bu depoyu klonlayanlar için)
```bash
git clone https://github.com/MonchereB/AltinAramaHarita.git
cd AltinAramaHarita
# Kendi imza anahtarınız olmadan derlemek isterseniz:
# keystore.properties dosyasini olusturun veya silin (unsigned olur)
```

### Depo bilgilerini değiştirmek
`app/src/main/java/com/altinarama/harita/UpdateChecker.kt` içinde:
```kotlin
const val GITHUB_OWNER = "MonchereB"
const val GITHUB_REPO = "AltinAramaHarita"
```
