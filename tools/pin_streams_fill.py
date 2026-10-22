#!/usr/bin/env python3
"""
Eksik pin noktalarini tamamlar (hizli + kademeli kayit).
Mevcut pin_streams.json varsa onu okur, olmayanlari Overpass + elevation ile bulur.
"""
import json, math, os, sys, time, urllib.parse, urllib.request

UA = "AltinAramaHarita/1.0 (geology research)"
MIRRORS = [
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass-api.de/api/interpreter",
    "https://overpass.osm.ch/api/interpreter",
]

SITES = {
    2:  (39.00151, 30.80537, "Doğlat"),
    3:  (38.93193, 30.50070, "Gazlıgöl"),
    6:  (38.51071, 30.16785, "Dodurga"),
    12: (38.37062, 30.16001, "Örenkaya"),
    13: (38.72928, 30.37886, "Tınaztepe"),
    14: (38.06563, 30.15620, "Dinar"),
    15: (38.32207, 29.77110, "Koçak/Çivril"),
    16: (38.88941, 30.73200, "Karaağaç"),
    17: (38.59752, 31.06351, "Yeşilyurt"),
    18: (38.56329, 30.50857, "Çakırözü"),
}

HERE = os.path.dirname(os.path.abspath(__file__))
OUTF = os.path.join(HERE, "pin_streams.json")
SEEDF = os.path.join(HERE, "pin_streams_seed.json")


def load():
    out = {}
    if os.path.exists(SEEDF):
        with open(SEEDF, encoding="utf-8") as f:
            out = {k: v for k, v in json.load(f).items()}
    if os.path.exists(OUTF):
        with open(OUTF, encoding="utf-8") as f:
            for k, v in json.load(f).items():
                if v.get("lat") is not None:
                    out[k] = v
    return out


def save(out):
    with open(OUTF, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)


def overpass_water(lat, lon, radius):
    q = f"""[out:json][timeout:45];
(way["waterway"~"^(stream|river)$"](around:{radius},{lat},{lon});
relation["waterway"~"^(stream|river)$"](around:{radius},{lat},{lon}););
out geom tags;"""
    data = urllib.parse.urlencode({"data": q}).encode()
    last = None
    for mirror in MIRRORS:
        try:
            req = urllib.request.Request(mirror, data=data, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=40) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:
            last = e
    raise RuntimeError(str(last)[:80])


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
        t = el.get("tags", {})
        for seg in segments(el):
            if len(seg) < 2:
                continue
            length = sum(haversine(seg[i], seg[i+1]) for i in range(len(seg)-1))
            if length < 120:
                continue
            mid = seg[len(seg)//2]
            d = haversine((lat, lon), mid)
            score = length - d * 0.6
            if best is None or score > best["score"]:
                best = {"score": score, "pt": mid, "length": length,
                        "type": t.get("waterway", ""), "name": t.get("name", ""), "dist": d}
    return best


def elevation(lat, lon):
    for _ in range(2):
        try:
            url = ("https://api.open-elevation.com/api/v1/lookup?locations="
                   f"{lat:.5f},{lon:.5f}")
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=25) as r:
                d = json.loads(r.read().decode("utf-8"))
            res = d.get("results") or []
            if res:
                return res[0].get("elevation")
        except Exception:
            time.sleep(2)
    return None


def main():
    out = load()
    print(f"Mevcut: {len(out)} nokta. Eksikler araniyor...", flush=True)
    for sid, (lat, lon, ad) in SITES.items():
        k = str(sid)
        if k in out and out[k].get("lat") is not None:
            print(f"#{sid:2d} {ad:14s} -> zaten var", flush=True)
            continue
        entry = {"id": sid, "ad": ad, "center": [lat, lon]}
        try:
            elems = overpass_water(lat, lon, 4000).get("elements", [])
        except Exception as e:
            entry["error"] = f"overpass: {str(e)[:60]}"
            out[k] = entry
            save(out)
            print(f"#{sid:2d} {ad:14s} OVERPASS HATA: {str(e)[:50]}", flush=True)
            continue
        best = pick_point(lat, lon, elems)
        if not best:
            entry["error"] = "akarsu bulunamadi"
            out[k] = entry
            save(out)
            print(f"#{sid:2d} {ad:14s} akarsu yok ({len(elems)} eleman)", flush=True)
            continue
        plat, plon = best["pt"]
        entry.update({
            "lat": round(plat, 5), "lon": round(plon, 5),
            "elev_m": elevation(plat, plon),
            "waterway": best["type"], "name": best["name"],
            "seg_len_m": round(best["length"]),
            "dist_from_center_m": round(best["dist"]),
        })
        out[k] = entry
        save(out)
        print(f"#{sid:2d} {ad:14s} -> {plat:.5f},{plon:.5f} rakim={entry['elev_m']} "
              f"{best['type']} '{best['name']}' uz={best['length']:.0f}m", flush=True)

    save(out)
    ok = sum(1 for v in out.values() if v.get("lat") is not None)
    print(f"\nTAMAM: {ok}/{len(SITES)+8} noktada spesifik akarsu konumu", flush=True)


if __name__ == "__main__":
    main()
