#!/usr/bin/env python3
"""
Her altin noktasi icin SPESIFIK akarsu noktasi + gercek rakim bulur.
  1) Overpass (coklu ayna + yeniden deneme) ile yakin cevredeki akarsu geometrisi
  2) En uygun spesifik nokta sec (dere yatagi uzerinde, merkeze yakin)
  3) open-elevation API ile o noktanin gercek rakimini al
"""
import json, math, os, sys, time, urllib.parse, urllib.request

UA = "AltinAramaHarita/1.0 (geology research)"
MIRRORS = [
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass-api.de/api/interpreter",
    "https://overpass.osm.ch/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
]

SITES = [
    (1,  38.90000, 30.73000, "Aşağısağırı"),
    (2,  39.00151, 30.80537, "Doğlat"),
    (3,  38.93193, 30.50070, "Gazlıgöl"),
    (4,  39.03604, 30.68992, "Kıyır"),
    (5,  38.48605, 30.29019, "Karacaören"),
    (6,  38.51071, 30.16785, "Dodurga"),
    (7,  38.71159, 31.04549, "Bolvadin"),
    (8,  38.43787, 30.52067, "Balçıkhisar"),
    (9,  38.85092, 30.42633, "Geçek"),
    (10, 39.09083, 30.82524, "Mallıca"),
    (11, 38.45847, 30.26080, "Sandıklı"),
    (12, 38.37062, 30.16001, "Örenkaya"),
    (13, 38.72928, 30.37886, "Tınaztepe"),
    (14, 38.06563, 30.15620, "Dinar"),
    (15, 38.32207, 29.77110, "Koçak/Çivril"),
    (16, 38.88941, 30.73200, "Karaağaç"),
    (17, 38.59752, 31.06351, "Yeşilyurt"),
    (18, 38.56329, 30.50857, "Çakırözü"),
]


def overpass_water(lat, lon, radius):
    q = f"""[out:json][timeout:60];
(way["waterway"~"^(stream|river)$"](around:{radius},{lat},{lon});
relation["waterway"~"^(stream|river)$"](around:{radius},{lat},{lon}););
out geom tags;"""
    data = urllib.parse.urlencode({"data": q}).encode()
    last = None
    for mirror in MIRRORS:
        for attempt in range(2):
            try:
                req = urllib.request.Request(mirror, data=data, headers={"User-Agent": UA})
                with urllib.request.urlopen(req, timeout=70) as r:
                    return json.loads(r.read().decode("utf-8")), mirror
            except Exception as e:
                last = e
                time.sleep(3)
    raise RuntimeError(f"Overpass basarisiz: {last}")


def segments(el):
    if el.get("type") == "way" and el.get("geometry"):
        return [[(g["lat"], g["lon"]) for g in el["geometry"]]]
    if el.get("type") == "relation":
        out = []
        for m in el.get("members", []):
            if m.get("geometry"):
                out.append([(g["lat"], g["lon"]) for g in m["geometry"]])
        return out
    if el.get("lat") and el.get("lon"):
        return [[(el["lat"], el["lon"])]]
    return []


def haversine(a, b):
    R = 6371000.0
    la1, lo1, la2, lo2 = map(math.radians, [a[0], a[1], b[0], b[1]])
    dlat, dlon = la2 - la1, lo2 - lo1
    h = math.sin(dlat/2)**2 + math.cos(la1)*math.cos(la2)*math.sin(dlon/2)**2
    return 2 * R * math.asin(math.sqrt(h))


def pick_point(lat, lon, elems):
    best = None
    for el in elems:
        tags = el.get("tags", {})
        wtype = tags.get("waterway", "")
        name = tags.get("name", "")
        for seg in segments(el):
            if len(seg) < 2:
                continue
            length = sum(haversine(seg[i], seg[i+1]) for i in range(len(seg)-1))
            if length < 120:
                continue
            mid = seg[len(seg)//2]
            d = haversine((lat, lon), mid)
            # Uzun ve merkeze yakin akarsu tercih edilir
            score = length - d * 0.6
            if best is None or score > best["score"]:
                best = {"score": score, "pt": mid, "length": length, "type": wtype,
                        "name": name, "dist": d}
    return best


def elevation(lat, lon):
    for attempt in range(3):
        try:
            url = ("https://api.open-elevation.com/api/v1/lookup?locations="
                   f"{lat:.5f},{lon:.5f}")
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                d = json.loads(r.read().decode("utf-8"))
            res = d.get("results") or []
            if res:
                return res[0].get("elevation")
        except Exception:
            time.sleep(3)
    return None


out = {}
for sid, lat, lon, ad in SITES:
    entry = {"id": sid, "ad": ad, "center": [lat, lon]}
    try:
        res, mirror = overpass_water(lat, lon, 4000)
        elems = res.get("elements", [])
    except Exception as e:
        entry["error"] = str(e)[:120]
        out[sid] = entry
        print(f"#{sid:2d} {ad:14s} OVERPASS HATA: {str(e)[:70]}", flush=True)
        continue

    best = pick_point(lat, lon, elems)
    if not best:
        entry["error"] = "akarsu bulunamadi"
        out[sid] = entry
        print(f"#{sid:2d} {ad:14s} akarsu yok ({len(elems)} eleman)", flush=True)
        time.sleep(2)
        continue

    plat, plon = best["pt"]
    elev = elevation(plat, plon)
    entry.update({
        "lat": round(plat, 5), "lon": round(plon, 5),
        "elev_m": elev,
        "waterway": best["type"], "name": best["name"],
        "seg_len_m": round(best["length"]),
        "dist_from_center_m": round(best["dist"]),
        "mirror": mirror,
    })
    out[sid] = entry
    print(f"#{sid:2d} {ad:14s} -> {plat:.5f},{plon:.5f} rakim={elev}m "
          f"{best['type']} '{best['name']}' uz={best['length']:.0f}m "
          f"mesafe={best['dist']:.0f}m", flush=True)
    time.sleep(1.5)

here = os.path.dirname(os.path.abspath(__file__))
with open(os.path.join(here, "pin_streams.json"), "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=1)
print("\nKaydedildi: tools/pin_streams.json")
