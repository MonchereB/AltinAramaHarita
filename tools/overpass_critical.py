#!/usr/bin/env python3
"""Kritik altin/jeoloji noktalarinin koordinatlarini Overpass ile bulur."""
import json, sys, time, urllib.parse, urllib.request

UA = "AltinAramaHarita/1.0 (geology research)"

NAMES = [
    "Ömer Geçek", "Ömergeçek", "Gazlıgöl", "Geçek", "Doğlat", "Aşağısağır",
    "Sandıklı", "Bolvadin", "Dinar", "Şuhut", "Emirdağ", "Bayat", "Kumçay",
    "Tuzlukçu", "Eynehan", "Kuzviran", "Kıyır", "Heybeli", "Kızık", "Uyuz",
    "Hüdai", "Kırka", "Eber Gölü", "Acıgöl", "Akdağ", "Gökbeli", "Yörükoğlu",
    "Saraycık", "Ağzıkara", "Basmacık", "Kumçay", "Çivril", "Koçak",
    "Kınık", "Çalçak", "Fethibey", "Ablak", "Yassıören", "Güneyköy",
    "Kumdan", "Sarayköy", "Bereket", "Aşağıpınarbaşı", "Alaplı",
]


def overpass(names):
    nf = "|".join(sorted(set(names)))
    q = f"""
[out:json][timeout:180];
(
  node["place"~"village|hamlet|isolated_dwelling|town|suburb"]["name"~"^({nf})$"];
  way["place"]["name"~"^({nf})$"];
  relation["place"]["name"~"^({nf})$"];
);
out center tags;
"""
    data = urllib.parse.urlencode({"data": q}).encode()
    req = urllib.request.Request("https://overpass-api.de/api/interpreter", data=data,
                                 headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=240) as r:
        return json.loads(r.read().decode("utf-8"))


found = {}
for i in range(0, len(NAMES), 12):
    batch = NAMES[i:i + 12]
    try:
        res = overpass(batch)
    except Exception as e:
        print(f"HATA: {e}", file=sys.stderr)
        continue
    for el in res.get("elements", []):
        lat = el.get("lat") or (el.get("center") or {}).get("lat")
        lon = el.get("lon") or (el.get("center") or {}).get("lon")
        t = el.get("tags", {})
        nm = t.get("name")
        if lat and lon and nm:
            key = nm.strip()
            if key not in found:
                found[key] = (round(lat, 5), round(lon, 5), t.get("place", ""),
                              t.get("admin_level", ""), t.get("county", "") or t.get("district", ""))
    time.sleep(4)

for nm in NAMES:
    hit = found.get(nm)
    if hit:
        print(f"{nm:18s} {hit[0]:9.5f}, {hit[1]:10.5f}  [{hit[2]}] {hit[4]}")
    else:
        print(f"{nm:18s} -- BULUNAMADI --")

print("\n=== JSON ===")
print(json.dumps(found, ensure_ascii=False, indent=1))
