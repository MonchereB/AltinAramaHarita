#!/usr/bin/env python3
"""OpenStreetMap Nominatim ile yer adlarini koordinata cevirir."""
import json, sys, time, urllib.parse, urllib.request

UA = "AltinAramaHarita/1.0 (geology research; contact: user)"

# (etiket, arama sorgusu)
PLACES = [
    ("Doglat",        "Doğlat, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Asagisagirli",  "Aşağısağırı, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Asagisagirlar", "Aşağısağır, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Emirdaglan",    "Emirdağlan, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Bolvadin",      "Bolvadin, Afyonkarahisar, Türkiye"),
    ("Gecek",         "Geçek, Sandıklı, Afyonkarahisar, Türkiye"),
    ("Gecek2",        "Geçek, Bolvadin, Afyonkarahisar, Türkiye"),
    ("Eynehan",       "Eynehan, Sandıklı, Afyonkarahisar, Türkiye"),
    ("Kuzviran",      "Kuzviran, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Mallica",       "Mallıca, Manisa, Türkiye"),
    ("Mallica2",      "Mallıca, Sandıklı, Afyonkarahisar, Türkiye"),
    ("Sandikli",      "Sandıklı, Afyonkarahisar, Türkiye"),
    ("Balcikhisar",   "Balçıkhisar, Şuhut, Kütahya, Türkiye"),
    ("Ilyasli",       "İlyaslı, Şuhut, Kütahya, Türkiye"),
    ("Kocatepe",      "Kocatepe, Şuhut, Kütahya, Türkiye"),
    ("Kocatepe2",     "Kocatepe, Sandıklı, Afyonkarahisar, Türkiye"),
    ("Yesilyurt",     "Yeşilyurt, Bolvadin, Afyonkarahisar, Türkiye"),
    ("Yesilyurt2",    "Yeşilyurt, Sandıklı, Afyonkarahisar, Türkiye"),
    ("Daydali",       "Daydalı, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Dokur",         "Dokur, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Altinyayla",    "Altınyayla, Sandıklı, Afyonkarahisar, Türkiye"),
    ("Altiyayla2",    "Altınyayla, Burdur, Türkiye"),
    ("Aksigazi",      "Akdağ, Sandıklı, Afyonkarahisar, Türkiye"),
    ("Acikol",        "Acıgöl, Afyonkarahisar, Türkiye"),
    ("Ebergolu",      "Eber Gölü, Afyonkarahisar, Türkiye"),
    ("Dinar",         "Dinar, Afyonkarahisar, Türkiye"),
    ("Iscehisar",     "İscehisar, Afyonkarahisar, Türkiye"),
    ("Suhut",         "Şuhut, Afyonkarahisar, Türkiye"),
    ("Sinanpasa",     "Sinanpaşa, Afyonkarahisar, Türkiye"),
    ("Hocalar",       "Hocalar, Afyonkarahisar, Türkiye"),
    ("Bayat",         "Bayat, Afyonkarahisar, Türkiye"),
    ("Kumcay",        "Kumçay, Sandıklı, Afyonkarahisar, Türkiye"),
    ("Cavakli",       "Çavaklı, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Saraykoy",      "Sarayköy, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Kiziloren",     "Kızılören, Bolvadin, Afyonkarahisar, Türkiye"),
    ("Gunduzler",     "Gündüzler, Sandıklı, Afyonkarahisar, Türkiye"),
    ("Cakallar",      "Çakallar, Sandıklı, Afyonkarahisar, Türkiye"),
    ("Konak",         "Konak, Sandıklı, Afyonkarahisar, Türkiye"),
    ("Resadet",       "Reşadiye, Sandıklı, Afyonkarahisar, Türkiye"),
]


def geocode(q):
    url = "https://nominatim.openstreetmap.org/search?" + urllib.parse.urlencode(
        {"q": q, "format": "json", "limit": 1, "countrycodes": "tr"}
    )
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=25) as r:
        data = json.loads(r.read().decode("utf-8"))
    if not data:
        return None
    it = data[0]
    return (float(it["lat"]), float(it["lon"]), it.get("display_name", ""))


results = {}
for tag, q in PLACES:
    try:
        r = geocode(q)
    except Exception as e:
        r = None
        print(f"{tag}: HATA {e}", flush=True)
    if r:
        results[tag] = r
        print(f"{tag:14s} {r[0]:9.5f}, {r[1]:10.5f}  | {r[2][:95]}", flush=True)
    else:
        print(f"{tag:14s} BULUNAMADI  ({q})", flush=True)
    time.sleep(1.1)  # Nominatim politika: max 1 istek/saniye

print("\n=== JSON ===")
print(json.dumps({k: {"lat": v[0], "lon": v[1], "name": v[2]} for k, v in results.items()},
                 ensure_ascii=False, indent=1))
