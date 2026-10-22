#!/usr/bin/env python3
"""Ozel duzeltmeler: ayni noktaya dusmus pinler ve akarsu bulunamayanlar."""
import json, os, math

HERE = os.path.dirname(os.path.abspath(__file__))
LOC = os.path.join(HERE, "..", "app", "src", "main", "assets", "locations.json")

with open(LOC, encoding="utf-8") as f:
    locs = json.load(f)
by_id = {l["id"]: l for l in locs}

# --- Karaağaç: Aşağısağırı ile aynı noktaya düşmüş, köy merkezine sabitle ---
if 16 in by_id:
    l = by_id[16]
    l["lat"] = 38.88941
    l["lon"] = 30.73200
    l["elevation_m"] = "~1120"
    l["terrain"] = "Köy yerleşimi üzerinde (Aşağısağırı yatağına ~1 km) • dere yatağı güneydoğusunda"
    l["indicators"] = (l.get("indicators", "") +
        " || KÖY MERKEZİ: maden yatağının hemen üzerindeki yerleşim; "
        "dere yatağı köyün ~1 km güneydoğusunda").strip(" |")
    l["description"] = ("PİN köy merkezindedir; cevher yatağı (Aşağısağırı) ~1 km güneyde, "
                        "dere yatağı ise köyün güneydoğusundaki vadi yatağındadır || "
                        + l.get("description", ""))
    print("#16 Karaağaç -> 38.88941, 30.73200 (köy merkezi)")

# --- Doğlat: 4 km icinde akarsu yok, yüksek kesimde; en yakin vadi yonu ---
if 2 in by_id:
    l = by_id[2]
    l["terrain"] = ("Yüksek kesim - sahanın kendisi fay zonu üzerinde; "
                    "drenaj akarsuları kuzey ve batıya doğru vadi tabanına iner")
    l["indicators"] = (l.get("indicators", "") +
        " || Bu noktada akarsu yatağı YOK (yüksek kesim); altın taşıyan akarsu "
        "kuzeybatıdaki vadi tabanında, ~2-3 km aşağıda").strip(" |")
    l["description"] = ("PİN cevher zonunun kendisidir (2 km x 500 m E-W fay zonu); "
                        "aranacak alüvyal altın bu zonun kuzeybatısındaki vadi "
                        "tabanındaki dere yatağında birikir || " + l.get("description", ""))
    print("#2 Doğlat -> fay zonu üzerinde (yüksek kesim), akarsu yatağı kuzeybatıda")

# --- Örenkaya: benzer durum ---
if 12 in by_id:
    l = by_id[12]
    l["terrain"] = ("Kuvaterner fay zonu - yüksek plato; akarsu yatağı güneydeki "
                    "vadi tabanında (Kusura / Soğucak)")
    l["indicators"] = (l.get("indicators", "") +
        " || Bu noktada akarsu yatağı YOK (plato); akarsu güneyde ~2-3 km").strip(" |")
    l["description"] = ("PİN fay zonu üzerindeki platodadır; alüvyal altın güneydeki "
                        "dere yatağında (Kusura-Soğucak) birikir || "
                        + l.get("description", ""))
    print("#12 Örenkaya -> plato üzerinde, akarsu güneyde")

# --- Ayni noktaya dusmus pin kontrolu ---
print("\n=== YAKINLIK KONTROLU ===")
pts = [(l["id"], l["lat"], l["lon"]) for l in locs]


def hav(a, b):
    R = 6371000.0
    la1, lo1, la2, lo2 = map(math.radians, [a[1], a[2], b[1], b[2]])
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))


dup = 0
for i in range(len(pts)):
    for j in range(i + 1, len(pts)):
        d = hav(pts[i], pts[j])
        if d < 400:
            print(f"  #${pts[i][0]} ve #{pts[j][0]} birbirine {d:.0f} m")
            dup += 1
if dup == 0:
    print("  Sorun yok - tum pinler farkli noktalarda")

with open(LOC, "w", encoding="utf-8") as f:
    json.dump(locs, f, ensure_ascii=False, indent=2)
print(f"\nKaydedildi: {LOC}")
