# 🏆 Altın Arama Harita

**Afyonkarahisar ve çevresi için, tamamen çevrimdışı (offline) çalışan Android altın arama haritası.**

Amatör altın savakçılığı (gold panning) için hazırlanmış; dere yatakları, alüvyal
zeminler ve yasal olarak kısıtlı bölgeler işaretlenmiş harita uygulaması.

---

## 📥 Kurulum (Telefon)

**GitHub Releases** sayfasından `AltinAramaHarita.apk` dosyasını indirin:
👉 **[Releases sayfası](https://github.com/MonchereB/AltinAramaHarita/releases)**

1. APK dosyasını telefona indirin
2. Dosyaya tıklayın → "Bilinen kaynaklardan yükleme" iznini verin
3. Kurun ✅

> **Not:** İlk kurulumdan sonra, uygulama içindeki **Güncelle** butonu ile
> yeni sürümleri uygulama **telefonunuzdan** doğrudan alabilirsiniz.
> Uygulama açılışta GitHub'ı kontrol eder, yeni sürüm varsa sizi uyarır.

---

## 🗺️ Özellikler

| Özellik | Açıklama |
|---|---|
| **Tam çevrimdışı harita** | 2400+ OpenTopoMap karosu uygulamanın içine gömülü — **internetsiz** çalışır |
| **Topografik detay** | Eş yükselti eğrileri, tepe yükseklikleri, patikalar, akarsu yatakları |
| **Altın noktaları** | Dere yatakları koordinat, yaklaşık irtifa, arazi tipi ve mesafe bilgisiyle |
| **Yasaklı alanlar** | Milli park / tabiat parkı sınırları yeşil alan olarak işaretli |
| **Google Haritalar rotası** | Bir noktaya tıklayıp doğrudan rota oluşturun |
| **GPS** | Konumunuzu merkeze alın, pusula ve ölçek çubuğu |
| **Otomatik güncelleme** | GitHub Releases üzerinden uygulama içinden güncelleme |

---

## 🗺️ Harita Kapsamı

| Zoom | Kapsam | Çözünürlük |
|---|---|---|
| 10-11 | 28.95-32.05 E / 37.85-39.60 K | Bölge geneli |
| 12 | 29.35-31.65 E / 37.95-39.25 K | Afyonkarahisar il geneli (varsayılan görünüm) |
| 13 | 30.00-31.45 E / 38.00-39.15 K | Çalışma alanı (~19 m/piksel) |
| 14-15 | 30.40-30.68 E / 38.68-38.86 K | Şehir merkezi (metre hassasiyeti) |

---

## 🛠️ Geliştirme

<details>
<summary><b>Kurulum</b></summary>

1. JDK 17, Android SDK (platform 34, build-tools 34.0.0) ve Android Studio gerekli
2. `local.properties` dosyasında SDK yolunu ayarlayın:
   ```properties
   sdk.dir=C\:\\Users\\<kullanici>\\AppData\\Local\\Android\\Sdk
   ```
3. Projeyi Android Studio'da açın
</details>

<details>
<summary><b>Derleme</b></summary>

```bash
# Debug APK
./gradlew assembleDebug

# İmzalı Release APK
./gradlew assembleRelease

# Türkçe karakterli Windows yollarında
./gradlew clean assembleDebug
```
</details>

<details>
<summary><b>Offline haritayı yeniden üretmek</b></summary>

```bash
py -3 tools/build_mbtiles.py          # eksik karoları indirir (resume)
py -3 tools/build_mbtiles.py --fresh  # sıfırdan üretir
```

Harita **OpenTopoMap** kaynağından üretilir (topografik + atıf zorunlu).
`tile.openstreetmap.org` toplu indirmeyi engellediği için kullanılmaz.
</details>

<details>
<summary><b>Yeni sürüm yayınlamak</b></summary>

```bash
git tag v1.1.0
git push origin v1.1.0
```

GitHub Actions otomatik olarak:
1. Offline haritayı önceki sürümden indirir (yoksa üretir)
2. İmzalı Release APK'sını derler
3. GitHub Releases'e yükler

> Depo → **Settings → Secrets and variables → Actions** bölümünde şu 4 secret tanımlı olmalı:
> `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
</details>

---

## ⚖️ Yasal Uyarı

**Türkiye'de derelerde amatör/hobi amaçlı altın arama (savakçılığı) 3213 sayılı
Maden Kanunu kapsamında serbest değildir.** Maden kaynakları devlete aittir; araştırma
ve çıkarma için resmi ruhsat gerekir. İznsiz faaliyette bulunmak cezai sorumluluk
doğurabilir.

Bu uygulama **sadece bilgi amaçlıdır** ve kesin maden/yasa bilgisi değildir. Haritadaki
noktalar ve kısıtlı alan sınırları **yaklaşık/referans** niteliktedir. Kesin sınırlar
ve maden statüsü için mutlaka:
- **MTA** (Maden Tetkik ve Arama Genel Müdürlüğü)
- **Afyonkarahisar Valiliği / İl Müdürlüğü**
- **Orman ve Su İşleri Bakanlığı** (milli park / tabiat parkı)

kurumlarından yazılı bilgi alınmalıdır.

Uygulama geliştiricisi, verilerin doğruluğundan ve kullanım sonucu doğacak her türlü
yasal/cezai sorumluluğu kabul etmez.

---

## 🧰 Teknolojiler

- **Kotlin** + **Android SDK 34** (minSdk 24)
- **OSMDroid 6.1.1** — OpenStreetMap tabanlı harita motoru, MBTiles desteği
- **OpenTopoMap** — topografik karo kaynağı (CC-BY-SA)
- **GitHub Actions** — otomatik derleme ve sürüm yayını

---

## 📄 Lisans

Harita verileri: © OpenStreetMap katılımcıları, OpenTopoMap (CC-BY-SA)
Kod: Kişisel / eğitim amaçlı kullanım için serbest.
