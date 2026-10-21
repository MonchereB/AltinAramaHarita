#!/usr/bin/env python3
"""2. tur: JMO/derst kitabinda gecen altin bolgeleri + jeotermal alanlar."""
import json, sys, time, urllib.parse, urllib.request

UA = "AltinAramaHarita/1.0 (geology research)"
PLACES = [
    ("OmerGecEK",     "Ömer Geçek, Afyonkarahisar, Türkiye"),
    ("Gazligol",     "Gazlıgöl, İhsaniye, Afyonkarahisar, Türkiye"),
    ("Gazligol2",    "Gazlıgöl, Sandıklı, Afyonkarahisar, Türkiye"),
    ("Tuzlukcu",     "Tuzlukçu, Şuhut, Afyonkarahisar, Türkiye"),
    ("Kumcay",       "Kumçay, Sandıklı, Afyonkarahisar, Türkiye"),
    ("Kiyir",        "Kıyır, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Heybeli",      "Heybeli, Bolvadin, Afyonkarahisar, Türkiye"),
    ("Kizik",        "Kızık, Afyonkarahisar, Türkiye"),
    ("Uyuz",         "Uyuz, Afyonkarahisar, Türkiye"),
    ("Alapli",       "Alaplı, Afyonkarahisar, Türkiye"),
    ("Hudai",        "Hüdai, Sandıklı, Afyonkarahisar, Türkiye"),
    ("Doglat2",      "Doğlat, Bayat, Afyonkarahisar, Türkiye"),
    ("Kocatepe3",    "Kocatepe, Şuhut, Kütahya, Türkiye"),
    ("Bayat_AFY",    "Bayat, Afyonkarahisar, Türkiye"),
    ("Acipinar",     "Acıpınar, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Yesilyurt3",   "Yeşilyurt, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Altnytas",     "Altıntaş, Dinar, Afyonkarahisar, Türkiye"),
    ("Altintas",     "Altıntaş, Afyonkarahisar, Türkiye"),
    ("Saraykoy2",    "Sarayköy, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Karakaya",     "Karakaya, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Suhut2",       "Şuhut, Kütahya, Türkiye"),
    ("Bolvadin2",    "Bolvadin, Afyonkarahisar, Türkiye"),
    ("Ihsaniye",     "İhsaniye, Afyonkarahisar, Türkiye"),
    ("SakaryaKoyu",  "Sakarya, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Kirka",        "Kırka, Sandıklı, Afyonkarahisar, Türkiye"),
    ("Ciftlik",      "Çiftlik, Sandıklı, Afyonkarahisar, Türkiye"),
    ("Bereket",      "Bereket, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Guneykoy",     "Güneyköy, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Yassioren",    "Yassıören, Emirdağ, Afyonkarahisar, Türkiye"),
    ("Kusura",       "Kuşura, Emirdağ, Afyonkarahisar, Türkiye"),
]


def geocode(q):
    url = "https://nominatim.openstreetmap.org/search?" + urllib.parse.urlencode(
        {"q": q, "format": "json", "limit": 1, "countrycodes": "tr"})
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
    if r:
        results[tag] = r
        print(f"{tag:14s} {r[0]:9.5f}, {r[1]:10.5f}  | {r[2][:90]}", flush=True)
    else:
        print(f"{tag:14s} BULUNAMADI  ({q})", flush=True)
    time.sleep(1.1)

print("\n=== JSON ===")
print(json.dumps({k: {"lat": v[0], "lon": v[1], "name": v[2]} for k, v in results.items()},
                 ensure_ascii=False, indent=1))
