#!/usr/bin/env python3
"""
Overpass API ile Afyonkarahisar ilindeki koy/yer adlarini bulur.
Nominatim kucuk turk köylerini kacirdigi icin Overpass kullanilir.
"""
import json, sys, time, urllib.parse, urllib.request

UA = "AltinAramaHarita/1.0 (geology research)"

NAMES = [
    "Doğlat", "Aşağısağır", "Aşağısağırı", "Emirdağlan", "Emirdağ", "Daydalı",
    "Geçek", "Eynehan", "Kuzviran", "Mallıca", "Kıyır", "Balçıkhisar", "İlyaslı",
    "Kocatepe", "Yeşilyurt", "Dokur", "Altınyayla", "Çavaklı", "Sarayköy",
    "Kızılören", "Gündüzler", "Çakallar", "Konak", "Kumçay", "Tuzlukçu",
    "Gazlıgöl", "Ömer Geçek", "Ömergeçek", "Heybeli", "Kızık", "Uyuz",
    "Alaplı", "Hüdai", "Kırka", "Çiftlik", "Bereket", "Güneyköy", "Yassıören",
    "Kuşura", "Karakaya", "Altıntaş", "Aşağıpınarbaşı", "Civril", "Ağzıkara",
    "Kumçay", "Gökbeli", "Yörükoğlu", "Özburun", "Tınaztepe", "Kırkpınar",
    "Balcılar", "Kusura", "Kırka", "Belen", "Gömü", "Yatacık", "Alanyurt",
    "Saraycık", "Hamidiye", "Kusura", "Cumhuriyet", "Bostanlı", "Yunus",
]


def overpass(names):
    name_filter = "|".join(sorted(set(names)))
    q = f"""
[out:json][timeout:120];
area["name"~"Afyonkarahisar"]["admin_level"~"4|6"]->.a;
(
  node["place"~"village|hamlet|isolated_dwelling|town|suburb"]["name"~"^({name_filter})$"](area.a);
);
out center tags;
"""
    url = "https://overpass-api.de/api/interpreter"
    data = urllib.parse.urlencode({"data": q}).encode()
    req = urllib.request.Request(url, data=data, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=180) as r:
        return json.loads(r.read().decode("utf-8"))


found = {}
for batch_start in range(0, len(NAMES), 20):
    batch = NAMES[batch_start:batch_start + 20]
    try:
        res = overpass(batch)
    except Exception as e:
        print(f"Overpass hatasi: {e}", file=sys.stderr)
        continue
    for el in res.get("elements", []):
        lat = el.get("lat") or (el.get("center") or {}).get("lat")
        lon = el.get("lon") or (el.get("center") or {}).get("lon")
        nm = el.get("tags", {}).get("name")
        if lat and lon and nm:
            found.setdefault(nm, (lat, lon, el.get("tags", {}).get("place", "")))
    time.sleep(3)

for nm in NAMES:
    if nm in found:
        lat, lon, place = found[nm]
        print(f"{nm:20s} {lat:9.5f}, {lon:10.5f}   [{place}]")
    else:
        print(f"{nm:20s} -- BULUNAMADI --")

print("\n=== JSON ===")
print(json.dumps(found, ensure_ascii=False, indent=1))
