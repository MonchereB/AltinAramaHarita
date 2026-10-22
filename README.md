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
| **Tam çevrimdışı harita** | 3000+ OpenTopoMap karosu uygulamanın içine gömülü — **internetsiz** çalışır |
| **Net karo çizimi** | Karolar 1:1 pikselde çizilir (DPI büyütme kapalı) + z16/z17 detay karoları → dere ve mendereler net görünür |
| **Spesifik noktalar** | Her pin bir **köy merkezine değil**, kriterlerin karşılandığı **akarsu yatağındaki tam noktaya** sabitlenmiştir; o noktanın rakımı, akarsu adı ve segment uzunluğu yazılıdır |
| **Jeolojik gerekçe** | Her pin: sıralama, kanıt gücü, bilinen cevher, litoloji, arazide aranacak göstergeler ve maden mühendisi değerlendirmesi |
| **Yasaklı alanlar** | Milli park / jeotermal alan sınırları haritada görünür (yalnızca merkez işaretine tıklayınca detay açılır) |
| **Google Haritalar rotası** | Bir noktaya tıklayıp doğrudan rota oluşturun |
| **⋮ Menü** | Sağ üstteki üç çizgi menüsü: Sürüm Güncelleme, Yasal Uyarı, Çevrimdışı Harita Bilgisi — en altında sürüm numarası |
| **Otomatik güncelleme** | GitHub Releases üzerinden; yüzde göstergeli indirme, doğrulama, kurulum ve **kurulum sonrası otomatik yeniden başlatma** |
| **GPS** | Konumunuzu merkeze alın, pusula ve ölçek çubuğu |

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

## 🔬 Jeolojik Araştırma — Altın Nerede ve Neden Aranır?

Uygulamadaki 18 nokta **yayınlanmış jeolojik kaynaklara** dayanarak sıralandı. Her pin;
sıralaması, kanıt gücü, bilinen cevher, litoloji, arazide aranacak göstergeler ve
"maden mühendisi gerekçesi" taşır.

### 1. Alüvyal (plase) altın için aranan koşullar — önem sırasıyla

| # | Koşul | Neden |
|---|---|---|
| 1 | **Birincil cevher kaynağı** | Altın kuvars damarlarında 3-10 mikron boyutunda; damarlar ayrışınca altın serbest kalır |
| 2 | **Drenaj bağlantısı** | Altın yıkanmaz, **taşınır**; kaynaktan aşağı doğru dere boyunca taşınır |
| 3 | **Fay / kırık zonu** | Hidrotermal akışkan kanalı + yeniden depolama yapısı (Afyon'da 31 diri fay) |
| 4 | **Depo (trap) yapısı** | Dere kırılmaları, eğim değişimleri, boulder arkası, taban kaya çatlakları |
| 5 | **Ağır mineral varlığı** | "Siyah kum" (magnetit/ilmenit) — manyetik test ile kolayca sınanır |
| 6 | **Akış rejimi** | Düşük hız = daha iyi çökelme (DEÜ Prof. Dr. Deliormanlı) |

### 2. Sıralama (yüksek → düşük olasılık)

| # | Nokta | Kanıt | Güven |
|---|---|---|---|
| 1 | **Aşağısağırı Altınlı Demir Yatağı** | MTA 11.000 m karot sondaj; altın kuvarsta 3-10 mikron | ÇOK YÜKSEK |
| 2 | **Doğlat Altınlı Antimuan Yatağı** | 2 km × 500 m E-W fay zonu, 2000 m sondaj | ÇOK YÜKSEK |
| 3 | **Gazlıgöl Fayı / Ömer-Gecek** | 19 km diri fay + 4 jeotermal saha; ders kitabı altın bölgesi | YÜKSEK |
| 4 | **Beyköy – Kıyır – Eynehankuzviran** | Antimon zuhurları + MTA "Beyköy" potansiyel altın sahası | YÜKSEK |
| 5 | **Karacaören Sandıklı Porfiri** | MTA Cu-Mo-Au maden jeolojisi raporu (Sandıklı sınırları içinde) | YÜKSEK |
| 6 | **Dodurga (Dokur)** | JMO altınlı antimuan yatağı + geniş kum yatakları | YÜKSEK |
| 7 | **Bolvadin – Eber Gölü** | Bolvadin Fayı (16 km) + geniş alüvyal ova | ORTA-YÜKSEK |
| 8 | **Şuhut – Balçıkhisar/İlyaslı/Kocatepe** | Pirit + markasit + silika sinter göstergeleri | ORTA-YÜKSEK |
| 9-18 | Geçek, Mallıca, Sandıklı, Örenkaya, Sinanpaşa, Dinar, Çivril, Karaağaç, Yeşilyurt, Çakırözü | Fay zonları, jeomorfoloji, resmî potansiyel listeler | ORTA → DÜŞÜK |

### 3. Kaynaklar

- **JMO (Jeoloji Mühendisleri Odası) / ETKB — M. Orhan ÖZKOÇAK**
  - *"Afyon-Bayat-Doğlat volkanik sahası altınlı antimuan yatağı"* (TJK 1992)
  - *"Afyon-Aşağısağırı altınlı demir yatağı"* (MTA 11.000 m karot sondaj)
  - *"Gold and Silver Exploration Viewpoint"* — **Mallıca–Doğlat–Geçek–Sandıklı fay zonu** "altın ve gümüş yataklarının oluşumunda ikinci önemli yapısal unsur"
- **AFAD / MEB İRAP** — Afyonkarahisar'dan geçen **31 fayın** parametrik tablosu (Gazlıgöl 19 km, Geçek 10 km, Bolvadin 16 km, Sandıklı 11 km, Dinar 4 segment, Karakent 33 km…)
- **MTA** — 27 resmi "potansiyel altın sahası" listesi (Beyköy, Karaağaç, Olucak…), Karacaören Sandıklı Porfiri raporu
- **TEMA** — *Afyon ve Çevresinde Madencilik* (Sandıklı Büyük Ovası'nın %80'i ruhsatlı)
- **Ankara Ticaret Odası (ATO) altın haritası** — Denizli/Çivril-Koçak yatağı
- **Saha verisi (Yeni Şafak, 20.05.2026)** — Ege'de kırıntı madenciliği: **günde 0,3-1,5 gram**; bulunan altın **19-23 ayar** (doğal plase altını)
- **DEÜ Maden Mühendisliği Bölüm Başkanı Prof. Dr. Ahmet Hamdi Deliormanlı** — "Ana yataklardan taşınmış altınlar, derelerin akışkan ortamını yitirdiği bölgelerde çökelir; büyük bir zenginlik bulmak mümkün değil."
- Koordinatlar **Nominatim** ve **Overpass API** ile doğrulandı.

### 4. Arazide pratik kontrol listesi

1. **Siyah kum testi** — Manyetik taşınız varsa ve siyah kum tutuyorsa, altın ihtimali yüksek demektir (kanıt değil, ama gereklilik)
2. **Kırık kuvars** — Limonitle dolmuş, breşik kuvars arayın (Aşağısağırı ve Doğlat'ın tipik göstergesi)
3. **Pirit / markasit** — Altının en karakteristik gösterge mineralleri
4. **Dere kırığı** — Ana dere kolundan sapmış, tabanı dikleşen yan dere kolları
5. **Boulder arkası** — Akış hızı düşer, ağır mineral ve altın birikir
6. **Taban kaya çatlakları** — Çatlak taban kayaya sıkışmış altın, panla zor çıkar ama vardır
7. **Ayrışma zonu** — Cevherin üstündeki toprak kırmızı-sarı boyalıysa altın taşınıyor demektir

> 📌 **Pinler köy merkezinde değil, akarsu yatağında olan noktalardadır.**
> Diyalogdaki "Bu noktada rakım … m" ve "akarsu bu bölümde ≈… m uzunlukta"
> bilgisi, o spesifik noktanın verisidir (Open-Elevation API + OSM akarsu geometrisi).

> ⚠️ **Unutmayın:** Siyah kum **altın kanıtı değildir**, sadece olasılık artırıcıdır.
> Ve Türkiye'de bu faaliyet için **resmi ruhsat** gereklidir (3213 sayılı Maden Kanunu).

---

## 📄 Lisans

Harita verileri: © OpenStreetMap katılımcıları, OpenTopoMap (CC-BY-SA)
Kod: Kişisel / eğitim amaçlı kullanım için serbest.
