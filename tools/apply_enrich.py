#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
pin_enrich.json (otomatik veri) + Manuel muhendis notlarini locations.json'a isler.

Eklenen alanlar (GoldLocation v2 semasi):
  thumb, soil, climate, stream_regime, dig_points,
  panning_grade, panning_why, field_tips, success_checks, evidence, access

Yedek: locations.json.bak
"""
import json
import os
import shutil
import sys

sys.stdout.reconfigure(encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
LOCS = os.path.join(HERE, "..", "app", "src", "main", "assets", "locations.json")
ENRICH = os.path.join(HERE, "pin_enrich.json")

MAN = {}

MAN[1] = {
    "regime": "MEVSIMLIK",
    "grade": "ÇOK UYGUN",
    "why": ("MTA'nın 11 000 m karotlu sondajla belgelediği, Türkiye'nin en net tanımlı "
            "altınlı demir (Au-Fe oksit) yatağı buradadır. Altın, breşik kuvarsın limonitli "
            "kırık dolgularında 3-10 mikron taneler halinde saptandı. Yatağın eteklerinden "
            "inen drenajlar doğrudan kaynak sayılır; talus çakılları işlenmesi kolaydır."),
    "tips": ("Hasan'ın Açıklı ve Ekrem'in Açıklı zonlarından inen dere kollarını aşağıya "
             "izleyin: iki zonun drenajlarının birleştiği kavşaklar birinci hedef. "
             "Talus eteğinde dere genişleyip yavaşladığı yer ikinci hedef."),
    "checks": ("Altın burada MİKRO tanelidir: savak tabanında parlak sarı ince toz/çizgi "
               "olarak görünür, ışığa tutup arayın. Siyah kum tabanı yoğun birikir (demir "
               "oksitli kaynak); altın genelde siyah kum tabanının üst ucundadır."),
    "evidence": ("MTA 11 000 m karotlu sondaj; altın limonitli breşik kuvarslarda 3-10 "
                 "mikron taneler halinde belgelendi. Türkiye'nin en net tanımlı altınlı "
                 "demir yatağı."),
}

MAN[2] = {
    "regime": "MEVSIMLIK",
    "grade": "ÇOK UYGUN",
    "why": ("Doğlat antimuan-altın yatağı 2 km x 500 m E-W fay zonu: cevher tek bir damar "
            "değil, geniş bir zon boyunca dağılıyor. Zon boyunca yıkanan malzeme, kuzey ve "
            "batı yamaçlarındaki drenajlara altın taşıyarak çok geniş bir plaser kaynağı "
            "olurur."),
    "tips": ("Pin plato üzerindedir: kazı hedefi cevher zonunun değil, zonun kuzey ve batı "
             "yamaçlarından inen vadi tabanlarıdır. Antimuan damar ağızlarının aşağı "
             "akışındaki kavşaklar ile vadi tabanı çakılları çalışın."),
    "checks": ("Antimuan göstergesi: savakta gri-siyah küçük çubuk taneler varsa doğru "
               "zondasınız (antimuan kükürtü). Altın: yumuşak, düzleşen sıcak sarı tane; "
               "tırnakla çizilmez ama tokmakla düzleşir."),
    "evidence": ("2 000 m sondajlı antimuan-altın yatağı; JMO listesinde Doğlat antimuan-"
                 "altın yatağı olarak kayıtlı, çevrede plaser çalışma geçmişi var."),
}

MAN[3] = {
    "regime": "KALICI",
    "grade": "UYGUN",
    "why": ("Gazlıgöl fayının 19 km diri segmenti + kırık kireçtaşı-mermer zemin. Jeotermal "
            "sular fay boyunca mineralleri taşır; karstik çatlak dipleri altını tutar. "
            "Kireçtaşı zona siyah kum az olabilir."),
    "tips": ("Fay istikametini izleyen vadi tabanında beyaz kırık kireçtaşı çatlakları ve "
             "karstik çukurlar önceliklidir. Jeotermal kaynak çıkışının mansabında kalan "
             "birkaç yüz metre çalışılmaya uygundur."),
    "checks": ("Siyah kum az olsa altın çıkabilir (karstik zemin): parlak sarı taneleri "
               "ışıkta arayın; çekiç testi ile piritten ayırın (altın düzleşir, pirit "
               "toz olur)."),
    "evidence": ("JMO: altın-gümüş oluşumunda ikincil önemli yapı fay zonudur; Gazlıgöl "
                 "jeotermal sahaları bu fay hizasında ve hidrotermal aktivite yüksek."),
}

MAN[4] = {
    "regime": "MEVSIMLIK",
    "grade": "UYGUN",
    "why": ("Eynehankuzviran-Beyköy antimon zuhurları; MTA'nın resmi potansiyel altın "
            "sahaları listesinde Beyköy geçer. Antimon+altın birlikteliği Türkiye'de "
            "karakteristiktir; drenaj Kıyır deresiyle kuzeye akar."),
    "tips": ("Kıyır deresi üst havzasında damar ağızlarının 100-300 m mansabı ve kol "
             "kavşakları hedeftir. Sarı-pembe killi damar yakınındaki kum adacıklarına "
             "bakın."),
    "checks": ("Yumuşak düzleşen sarı tane = altın; sert parlak kırılgan tane = pirit "
               "(aptal altını). Mıknatıs testi ile siyah kumdan ayırın."),
    "evidence": ("MTA 27 resmi potansiyel altın sahasında Beyköy; JMO'da Eynehankuzviran "
                 "antimon zuhurları kayıtlı."),
}

MAN[5] = {
    "regime": "MEVSIMLIK",
    "grade": "UYGUN",
    "why": ("Sandıklı Karacaören porfiri (Cu-Mo-Au) resmi kaynak raporlu. Porfiri "
            "sistemlerde altın intrüzyonun üst bölgesinde yoğunlaşır; erozyonla çevre "
            "drenajlara ince altın taşınma olasılığı yüksek."),
    "tips": ("Porfirin eteklerinden inen dere kolunun ovaya çıkış noktası hedeftir. "
             "Yeşilimsi boyalı (bakır) kuvars kırıntısı görülen yatak bölümlerine "
             "öncelik verin."),
    "checks": ("Bakır sistem: savakta yeşilimsi boyalı kuvars kırıntısı geçen yataklarda "
               "ince altın daha muhtemeldir."),
    "evidence": ("MTA kaynak tahmin raporu Sandıklı sınırları içinde Cu-Mo-Au porfiri "
                 "cevherleşmesini belgelendi."),
}

MAN[6] = {
    "regime": "YAGISLA",
    "grade": "UYGUN",
    "why": ("JMO kaydı 'Dokur altınlı antimuan yatağı' — Sandıklı havzasının gevşek ve "
            "geniş kum yatakları savak için en kolay zemindir. Kaynak, Doğlat fay zonu "
            "ailesidir; su yağışla kabarır, hızla söner."),
    "tips": ("Karadirek Çayı kum adacıklarının mansap uçları ve su çizgisine en yakın "
             "geniş kum yatakları hedef. Kavşak çıkışlarında alt kum tabakaya in (0.3-0.5 "
             "m)."),
    "checks": ("Kum oranı yüksek olduğundan savak hızlı çalışır; siyah kum azsa altın "
               "ince taneli olabilir. Işık altında ince sarı çizgileri arayın."),
    "evidence": ("JMO listesinde 'Dokur altınlı antimuan yatağı' kaydı."),
}

MAN[7] = {
    "regime": "KALICI",
    "grade": "ORTA",
    "why": ("Akarçayın Eber'e girişi + Bolvadin fayı. JMO bu alanı 'altın oluşumuna "
            "elverişli' olarak işaretler; ancak ova alüvyonu derin ve ince tanelidir — "
            "altın toz altın halinde yayılıp savak verimi orta-düşük seyreder."),
    "tips": ("Akarçay kavislerinde iç kıyılar ve çakıl adacıklarının mansap uçları hedef. "
             "Dere kanallarının ovada ayrıldığı yerlerdeki yeni eleme çakıllarına bakın."),
    "checks": ("Ova alüvyonundaki altın İNCE tozdur: savak tabanını ışığa tutun ince parıltı "
               "arayın; dolgun tane bekleme."),
    "evidence": ("JMO 'altın oluşumuna elverişli özellikler' kaydı; Eber-Bolvadin fayı "
                 "üzerinde taze yüzey kırıkları var."),
}

MAN[8] = {
    "regime": "MEVSIMLIK",
    "grade": "UYGUN",
    "why": ("Kali Çay üst havzası + volkanik pirit-markasit sahaları (mud-pool ve silika "
            "sinter kayıtlı). Piritli sistemler altın için tipik gösterge çevresidir: "
            "drenaj altını ince plaser olarak taşır."),
    "tips": ("Kali Çay üst kavşaklarının mansabı ve sinter/pirit boyalı yataklara yakın "
             "kolların birleşme noktaları hedef. Kumalar Dağı yamaç kollarını izleyin."),
    "checks": ("Pirit bölgesi uyarısı: parlak sarı taneleri çekiç testiyle ayırın — altın "
               "düzleşip yayılır, pirit toz olur. Savakta siyah kum bol olur, altın "
               "tabanın içinde gizlidir."),
    "evidence": ("JMO: Balçıkhisar-İlyaslı-Kocatepe çevresinde pirit-markasit mud-pool ve "
                 "silika sinter oluşumları kayıtlı; pirit, altının en bilinen yoldaşıdır."),
}

MAN[9] = {
    "regime": "KALICI",
    "grade": "UYGUN",
    "why": ("Geçek altınlı bakır zuhurları (JMO kaydı) + Geçek fayı (AFAD 10 km aktif). "
            "Akarçay üst havzası, Afyon-Kütahya yoluna yakın; kaynak drenajı ulaşılabilir."),
    "tips": ("Yol kenarı kesitlerinde kırmızı-kahve demir oksit boyalı kuvars kırıntısı "
             "gören yatağı mansaba izleyin; kol birleşmeleri ve geniş iç kıyılar hedef."),
    "checks": ("Bakır sistemde demir oksit boyalı kuvars kırıntısı iyi işarettir; savakta "
               "siyah kum yoğunluğu artınca ince altın arayın."),
    "evidence": ("JMO kaydı 'Geçek altınlı bakır zuhurları': kuvars damarlarında bakır+altın "
                 "birlikteliği."),
}

MAN[10] = {
    "regime": "YAGISLA",
    "grade": "ORTA",
    "why": ("Altın kuşağının batı düğümü + MTA potansiyel altın sahası (Karaağaç). "
            "Emirdağ/Bayat ucunda geniş kuru vadiler: akış yağışa bağlı, plaser taşınımı "
            "sınırlı ama kaynak yakındır."),
    "tips": ("Kuru vadi tabanında taşkınların bıraktığı çakıl bantları ve Mallıca çevresi "
             "menşeli vadinin birleşme eksenleri hedef. Taşkın sonrası taze çakıl "
             "dolgunları çalışın."),
    "checks": ("Yağışla kabaran derede taşkın sonrası İLK savak en verimlidir: taze çakıl "
               "yeni malzeme taşımıştır."),
    "evidence": ("MTA resmi potansiyel altın sahası işareti + JMO'da Mallıca bakır "
                 "zuhurları; fay zonunun batı ucundadır."),
}

MAN[11] = {
    "regime": "KALICI",
    "grade": "ORTA",
    "why": ("Sandıklı Büyük Ovası ana fay ekseni. TEMA'ya göre ovada %80 üzeri ruhsatlı "
            "alan var; alüvyon geniş ve ince taneli. Akdağ Milli Parkı sınırına "
            "girmemeye dikkat."),
    "tips": ("Ova dere yatağında çakıl adacıklarının mansap uçları ve tarla kanallarıyla "
             "dere kesişimlerinin hemen mansabı hedef. Kalın alüvyonda 30-40 cm tabana in."),
    "checks": ("İnce kumlu ova materyali: ışık altında altın tozunu arayın; dolgun tane "
               "bekleme (mikro plaser)."),
    "evidence": ("JMO 'Sandıklı sahaları altın oluşumuna elverişli' + TEMA raporunda yoğun "
                 "ruhsat bilgisi — jeolojik ilgi resmi olarak yüksek."),
}

MAN[12] = {
    "regime": "MEVSIMLIK",
    "grade": "ORTA",
    "why": ("Örenkaya fayı (AFAD: 29 km, Mw 6.79 potansiyeli) — pin plato üzerindedir; "
            "plaser güneydeki Kusura/Soğucak vadi yataklarında beklenir. Doğrudan altın "
            "kanıtı yok; ağır mineral birikimi ara göstergedir."),
    "tips": ("Kazı hedefi pin değil GüNEY vadilerdir: Kusura ve Soğucak dere yatakları, "
             "kavşakların mansabı ve yamaç eteği talusu."),
    "checks": ("Siyah kum yoğun ama altın gelmiyorsa yanlış çizgide olabilirsiniz: mansaba "
               "50-100 m inip yeni savak alın."),
    "evidence": ("AFAD fay listesinde Örenkaya 29 km aktif fay — taze kırıklar sürekli "
                 "malzeme arz eder; doğrudan altın kaydı yok."),
}

MAN[13] = {
    "regime": "MEVSIMLIK",
    "grade": "UYGUN",
    "why": ("Sinanpaşa Tınaztepe-Kırka vadisi: geniş taban + düşük eğim + geniş kum "
            "yatakları, plaser birikimi için ideal depozito koşullarıdır. Doğrudan altın "
            "kanıtı yok; jeomorfolojik gerekçe güçlüdür."),
    "tips": ("Geniş tabanlı vadinin orta çizgisi ve kumul adacıkların mansap uçları hedef. "
             "Taban kayası çıkışlarındaki çukur ve çatlaklara öncelik verin."),
    "checks": ("Geniş kum yatağında savak hızlı çalışır; ince altın ve orta yoğunlukta "
               "siyah kum bekleyin."),
    "evidence": ("Jeomorfolojik değerlendirme (geniş tabanlı vadi + geniş kum yatağı + "
                 "düşük eğim: plaser depozito koşullarına uyar); doğrudan altın kaydı yok."),
}

MAN[14] = {
    "regime": "KALICI",
    "grade": "UYGUN",
    "why": ("Büyük Menderes'in başlangıç havzası + Dinar bölgesinin en aktif fayı "
            "(Maymuntaş-Dinar hattı). Kalıcı su yıl boyu çalışma imkanı verir; ova "
            "alüvyonunda altın ince taneli; kaynak yakın."),
    "tips": ("Maymuntaş kaynak çevresi ve kaynak çıkış derisinin mansabı hedef. Kızılören "
             "vadi tabanı kavşakları ve kalın alüvyonlu bölümler ikinci hedef."),
    "checks": ("Kalıcı su + ova alüvyonu: ince toz altın bekleyin; savak ışığı altında "
               "ince parlaklık arayın."),
    "evidence": ("Kaynak çevresi ve Menderes üst havzasında tarihi yıkama geleneği; Dinar "
                 "fayı çok aktif (taze kırık arzı)."),
}

MAN[15] = {
    "regime": "MEVSIMLIK",
    "grade": "ORTA",
    "why": ("Çivril-Koçak Ankara Ticaret Odası'nın resmî altın yatak listesinde; Küfü Çayı "
            "uzun segmenti Akdağ'ın güneye drenajındır. Mevsimliktir."),
    "tips": ("Küfü Çayı çakıl adacıklarının mansap uçları ve eğimin düştüğü bölümler hedef; "
             "Akdağ Milli Parkı sınırına (güneye) girmeyin."),
    "checks": ("Orta yoğunluklu siyah kum; altın ince taneli olabilir."),
    "evidence": ("ATO (Ankara Ticaret Odası) altın yatakları listesinde Çivril-Koçak açıkça "
                 "listelenmiş."),
}

MAN[16] = {
    "regime": "MEVSIMLIK",
    "grade": "UYGUN",
    "why": ("Karaağaç (İscehisar) MTA potansiyel altın sahası — Aşağısağırı yatağına 1 km: "
            "aynı altın besleme havzasındadır. MTA resmî işaret; köyün güneydoğu vadi "
            "yatağı çalışılmaya uygundur."),
    "tips": ("Köyün güneydoğu vadi yatağı ve Aşağısağırı drenajına katılan kolların "
             "kavşakları hedef. Mermer işletme çevresindeki kısıtlı arazilere girmeyin."),
    "checks": ("Aşağısağırı sistemine yakın: siyah kum yoğun ve ince altın muhtemeldir."),
    "evidence": ("MTA 27 resmi potansiyel altın sahası listesinde Karaağaç (İscehisar)."),
}

MAN[17] = {
    "regime": "KALICI",
    "grade": "UYGUN",
    "why": ("Çay Deresi vadisi + 2002 depreminin yüzey kırıkları + Eber güneyi rüsubat "
            "yelpazeleri. Taze fay kırıkları drenaja sürekli yeni malzeme arz eder."),
    "tips": ("2002 kırığı çevresinde taze açılan çatlakların mansabındaki yeni çakıl "
             "dolgunları ve Çay Deresi kavşaklar hedef."),
    "checks": ("Taze kırık bölgelerinde yeni plaser: çakıl dolgusu taze ve gevşektir — "
               "savak kolay çalışır."),
    "evidence": ("AFAD: 2002 Çay depreminde Yeşilyurt-Çay arasında yüzey kırıkları (10 "
                 "cm'ye varan düşmeler) kayıtlı."),
}

MAN[18] = {
    "regime": "MEVSIMLIK",
    "grade": "ORTA",
    "why": ("Karaadilli fayı (AFAD 14 km diri fay) üzerinde volkanik saha; Şuhut volkanik "
            "kuşağının kuzey uzantısı. Doğrudan altın kanıtı yok; kaynak-drenaj mantığına "
            "dayanır."),
    "tips": ("Volkanik yamaçlardan inen dere yataklarının kavşakları ve talus eteği "
             "genişlemeleri hedef; kireçtaşı takılı bölümlerde çatlak tutumlarına bakın."),
    "checks": ("Volkanik kaynaklı yataklarda siyah kum orta yoğunlukta; altın ince taneli "
               "olabilir."),
    "evidence": ("AFAD: Karaadilli fayı 14 km diri fay; JMO'dan doğrudan kayıt yok — "
                 "düşük-orta kanıt düzeyi."),
}


# ---------------------------------------------------------------------------
# ORTAK KONTROLLER + BIRLESTIRME
# ---------------------------------------------------------------------------

COMMON_CHECKS = (
    "1) SİYAH KUM KURALI: savak tabanında koyu renkli ağır kum (manyetit) "
    "birikiyorsa doğru çizgidesiniz; altın ve manyetit aynı yerde birikir. Siyah kum "
    "hiç yoksa mansaba 50-100 m inip yeni savak alın.\n"
    "2) IŞIK TESTİ: altın mat ve sıcak sarıdır, gölgede de parıldar; pirit (aptal "
    "altını) pirinç renginde ve gölgede soluklaşır.\n"
    "3) SERTLİK TESTİ: altın çakıl/tokmakla düzleşir; pirit serttir, toz olur.\n"
    "4) MIKNATIS TESTİ: altın mıknatısa çekilmez; siyah kum çekilir.\n"
    "5) EMİN OLAMAZSANIZ: aynı noktada 3-4 savak alıp kıyaslayın; tek savak "
    "yanıltır."
)

# stream adini terrain metnindeki "• Akarcay (dere/cay) yatagi" deseninden cek
STREAM_RE = re.compile(r"([^.•\n()]+?)\s*\((?:dere/çay)\)")


def stream_name_from(loc):
    m = STREAM_RE.search(loc.get("terrain", ""))
    if m:
        return m.group(1).strip().lstrip("• ").strip()
    return ""


def main():
    if not os.path.exists(ENRICH):
        print("pin_enrich.json yok: oncelikle tools/pin_enrich.py calistirin.")
        sys.exit(1)
    enrich = json.load(open(ENRICH, encoding="utf-8"))
    with open(LOCS, encoding="utf-8") as f:
        data = json.load(f)
    shutil.copy(LOCS, LOCS + ".bak")

    for loc in data:
        sid = int(loc["id"])
        m = MAN.get(sid)
        if not m:
            print(f"#{sid} icin manuel not yok - atlandi")
            continue
        e = enrich.get(str(sid), {})

        loc["thumb"] = f"site_imgs/site_{sid:02d}.jpg"
        loc["stream_regime"] = m["regime"]
        name = stream_name_from(loc)
        if name:
            loc["stream_name"] = name

        loc["panning_grade"] = m["grade"]
        loc["panning_why"] = m["why"]
        loc["field_tips"] = (
            "Aşağıdaki GÖSTER işaretleri haritada hedefi gösterir. Genel kural: "
            "akarsudaki ağır mineral, akışın yavaşladığı yerde birikir.\n" + m["tips"]
        )
        loc["success_checks"] = COMMON_CHECKS + "\n\n" + m["checks"]
        loc["evidence"] = m["evidence"]

        # Ulasim: otomatik + manuel not
        parts = []
        road = e.get("road")
        vil = e.get("village")
        if road:
            parts.append(f"En yakın yol: {road.get('type', '?')} (~{road.get('dist', '?')} m)")
        else:
            parts.append("En yakın yol bulunamadı (patika/yürüyüş)")
        if vil:
            parts.append(f"Yakın yerleşim: {vil.get('name', '?')} (~{vil.get('dist', '?')} m)")
        loc["access"] = "; ".join(parts) + ". " + m.get("access_note", "")

        # Toprak yapi (SoilGrids)
        soil = e.get("soil")
        if soil and "sand" in soil:
            loc["soil"] = {
                "sand": soil["sand"],
                "clay": soil.get("clay", -1),
                "silt": soil.get("silt", -1),
                "kaynak": "SoilGrids (0-5 cm)",
            }

        # Iklim (Open-Meteo archive)
        cl = e.get("climate")
        if cl and cl.get("annual_precip_mm"):
            loc["climate"] = {
                "annual_precip_mm": cl["annual_precip_mm"],
                "spring_share_pct": cl.get("spring_share_pct", -1),
                "winter_share_pct": cl.get("winter_share_pct", -1),
                "kaynak": "Open-Meteo arsivi (2019-2023)",
            }

        # Kazi noktalari (mantik urunu): kavsaklar oncelikli, sonra en keskin egriler
        digs = []
        for c in e.get("confluences", [])[:2]:
            names = [n for n in c.get("names", []) if n and n != "-"]
            title = (", ".join(names[:2]) + " kavşağı") if names else "Kavşak (kolların birleşimi)"
            why = ("İki akarsu hattı burada birleşir; akış yavaşlar ve ağır mineral "
                   "birikir. Kazı: kavşağın MANSABI (aşağısı) — çakıl tabanındaki "
                   "çatlaklar ve büyük taş bölgesi.")
            digs.append({"lat": c["lat"], "lon": c["lon"], "type": "KAVŞAK",
                         "title": title, "why": why, "dist": c["dist"]})
        for b in e.get("bends", [])[:3]:
            why = ("Dere burada {t}° döner; virajın İÇ kıyısında akış yavaşlar. "
                   "Kazı: iç kıyının önündeki çakıl şeridi ve tabanı."
                   ).format(t=b["turn"])
            digs.append({"lat": b["lat"], "lon": b["lon"], "type": "IC_KIYI",
                         "title": "İç kıyı (menderes)", "why": why, "dist": b["dist"]})
        loc["dig_points"] = digs[:3]

    with open(LOCS, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    nz = sum(1 for l in data if l.get("dig_points"))
    print(f"TAMAM: {len(data)} pin guncel; {nz} pinde kazı noktasi var -> locations.json")


if __name__ == "__main__":
    main()

