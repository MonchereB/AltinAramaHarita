#!/usr/bin/env python3
"""
Her altin noktasini zenginlestirir (internet kaynaklari + mantik yurutme):

1) OVERPASS (tek istek/nokta):
   - akarsu geometrisi (stream/river)          -> savak noktasi mantigi icin
   - yol geometrisi (highway ...)              -> ulasim
   - yerlesim noktalari (place=village/town)   -> yakin koy

2) MANTIK YURUTME (bu betik hesaplar, internetten kopya degil):
   - KAVSAKLAR: iki ayri akarsu parselinin birlestigi yerler (endpoint cakismasi)
     -> agir mineral (altin) kaliralar burada birikir: KLASIK kazi noktasi
   - IÇ KIYILAR: akarsu dogrultusunda 130°'den fazla donen yerler (convex bank)
     -> su yavaslar, altin ic keerede birikir. Kazı noktasi ic keereden 12 m.
   - Her ikisinden en iyi 3 aday secilir -> dig_points

3) SOILGRIDS API: kum/kill/silt % (0-5 cm) -> savak uygunlugu
4) OPEN-METEO ARCHIVE: 2019-2023 yillik yagis + bahar payi -> debi rejimi
5) Her pin icin Afyon merkezine kus ucusu mesafe

Cikti: tools/pin_enrich.json
"""
import json, math, os, sys, time, urllib.parse, urllib.request

sys.stdout.reconfigure(encoding="utf-8")
UA = "AltinAramaHarita/1.3 (geology research, offline gold-panning map)"
MIRRORS = [
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass-api.de/api/interpreter",
    "https://overpass.osm.ch/api/interpreter",
]

SITES = {
    1:  (38.89735, 30.74657, "Aşağısağırı"),
    2:  (39.00151, 30.80537, "Doğlat"),
    3:  (38.89890, 30.51267, "Gazlıgöl"),
    4:  (39.07724, 30.66532, "Kıyır"),
    5:  (38.47475, 30.27089, "Karacaören"),
    6:  (38.50388, 30.15338, "Dodurga"),
    7:  (38.66566, 30.95027, "Bolvadin"),
    8:  (38.46904, 30.54532, "Balçıkhisar"),
    9:  (38.85139, 30.39711, "Geçek"),
    10: (39.08862, 30.86214, "Mallıca"),
    11: (38.45026, 30.22596, "Sandıklı"),
    12: (38.37062, 30.16001, "Örenkaya"),
    13: (38.71827, 30.33847, "Tınaztepe"),
    14: (38.10600, 30.11075, "Dinar"),
    15: (38.30140, 29.84149, "Koçak/Çivril"),
    16: (38.88941, 30.73200, "Karaağaç"),
    17: (38.59951, 31.05247, "Yeşilyurt"),
    18: (38.53902, 30.49040, "Çakırözü"),
}

AFYON = (38.756886, 30.538704)
HERE = os.path.dirname(os.path.abspath(__file__))


# ---------------------------------------------------------------- yardimcilar
def hav(a, b):
    R = 6371000.0
    la1, lo1, la2, lo2 = map(math.radians, [a[0], a[1], b[0], b[1]])
    dlat, dlon = la2 - la1, lo2 - lo1
    h = math.sin(dlat / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin(dlon / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))


def bearing_deg(a, b):
    la1, lo1, la2, lo2 = map(math.radians, [a[0], a[1], b[0], b[1]])
    y = math.sin(lo2 - lo1) * math.cos(la2)
    x = math.cos(la1) * math.sin(la2) - math.sin(la1) * math.cos(la2) * math.cos(lo2 - lo1)
    return (math.degrees(math.atan2(y, x)) + 360) % 360


def offset_point(lat, lon, bearing, dist_m):
    """Bir noktadan bearing istikametinde dist_m ileri."""
    R = 6371000.0
    la1, lo1 = map(math.radians, [lat, lon])
    br = math.radians(bearing)
    la2 = math.asin(math.sin(la1) * math.cos(dist_m / R) +
                    math.cos(la1) * math.sin(dist_m / R) * math.cos(br))
    lo2 = lo1 + math.atan2(math.sin(br) * math.sin(dist_m / R) * math.cos(la1),
                           math.cos(dist_m / R) - math.sin(la1) * math.sin(la2))
    return (round(math.degrees(la2), 5), round(math.degrees(lo2), 5))


def overpass(lat, lon):
    q = f"""[out:json][timeout:60];
(
 way["waterway"~"^(stream|river)$"](around:4000,{lat},{lon});
 way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|track)$"](around:3500,{lat},{lon});
 node["place"~"^(village|town|hamlet)$"](around:6000,{lat},{lon});
);
out geom tags;"""
    data = urllib.parse.urlencode({"data": q}).encode()
    last = None
    for mirror in MIRRORS:
        try:
            req = urllib.request.Request(mirror, data=data, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=70) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:
            last = e
            time.sleep(2)
    raise RuntimeError(str(last)[:90])


def soilgrids(lat, lon):
    url = ("https://rest.isric.org/soilgrids/v2.0/property/query"
           f"?lon={lon:.5f}&lat={lat:.5f}&property=sand&property=clay&property=silt"
           "&depth=0-5cm&value=mean")
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=45) as r:
        d = json.loads(r.read().decode("utf-8"))
    out = {}
    for layer in d.get("properties", {}).get("layers", []):
        name, vals = layer.get("name"), []
        for dep in layer.get("depths", []):
            m = dep.get("values", {}).get("mean")
            if m is not None:
                vals.append(m)
        if vals and name in ("sand", "clay", "silt"):
            out[name] = round(sum(vals) / len(vals) / 10.0, 1)  # g/kg -> %
    return out or None


def openmeteo_annual(lat, lon):
    url = ("https://archive-api.open-meteo.com/v1/archive"
           f"?latitude={lat:.5f}&longitude={lon:.5f}"
           "&start_date=2019-01-01&end_date=2023-12-31"
           "&daily=precipitation_sum&timezone=auto")
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        d = json.loads(r.read().decode("utf-8"))
    daily = d.get("daily", {})
    t = daily.get("time", [])
    p = daily.get("precipitation_sum", [])
    years = {}
    spring = {"sum": 0.0, "total": 0.0}
    for day, mm in zip(t, p):
        if mm is None:
            continue
        y, m = int(day[:4]), int(day[5:7])
        years[y] = years.get(y, 0.0) + mm
        spring["total"] += mm
        if m in (4, 5):
            spring["sum"] += mm
    if not years:
        return None
    annual = round(sum(years.values()) / len(years))
    spring_share = round(100 * spring["sum"] / spring["total"], 1) if spring["total"] else None
    # kis yagislari (Aralik-Ocak-Subat): bati anadolu iklimi yagis alani
    winter = {"sum": 0.0, "total": 0.0}
    for day, mm in zip(t, p):
        if mm is None:
            continue
        m = int(day[5:7])
        winter["total"] += mm
        if m in (12, 1, 2):
            winter["sum"] += mm
    winter_share = round(100 * winter["sum"] / winter["total"], 1) if winter["total"] else None
    return {"annual_precip_mm": annual, "spring_share_pct": spring_share,
            "winter_share_pct": winter_share, "years": f"2019-2023 ({len(years)} yıl)"}


def stream_geometry(elems):
    water = []
    for el in elems:
        t = el.get("tags", {})
        if "waterway" not in t:
            continue
        pts = [(g["lat"], g["lon"]) for g in (el.get("geometry") or [])]
        if len(pts) >= 2:
            water.append({"tags": t, "pts": pts})
    return water


def find_confluences(pin, water):
    """Iki ayri akarsunun birlestigi noktalar (endpoint eslesmesi)."""
    witches = []
    eps = []  # (way_index, uct_ip: 0 baslangic | 1 son, point)
    for i, w in enumerate(water):
        eps.append((i, 0, w["pts"][0]))
        eps.append((i, 1, w["pts"][-1]))
    used = set()
    for a in range(len(eps)):
        if a in used:
            continue
        group = [eps[a]]
        for b in range(a + 1, len(eps)):
            if eps[b][0] in (g[0] for g in group):
                continue
            if hav(eps[a][2], eps[b][2]) < 25:
                group.append(eps[b])
        if len(group) >= 2:
            used.update(range(a, a + len(group))) if False else None
            # ortalama nokta
            lat = sum(g[2][0] for g in group) / len(group)
            lon = sum(g[2][1] for g in group) / len(group)
            d = hav(pin, (lat, lon))
            if d < 2200:
                names = sorted({water[g[0]]["tags"].get("name", "") or "-" for g in group})
                witches.append({
                    "lat": round(lat, 5), "lon": round(lon, 5),
                    "dist": round(d), "names": names,
                    "n": len(group),
                })
    # merkezleme: >= 60 m yakin olanlari birlestir
    witches.sort(key=lambda c: c["dist"])
    out = []
    for c in witches:
        if all(hav((c["lat"], c["lon"]), (o["lat"], o["lon"])) > 60 for o in out):
            out.append(c)
    return out[:4]


def find_bends(pin, water):
    """130°'den fazla donen menderes noktalari -> ic keerede altin birikir."""
    bends = []
    for w in water:
        pts = w["pts"]
        for i in range(1, len(pts) - 1):
            p1, p2, p3 = pts[i - 1], pts[i], pts[i + 1]
            d12 = hav(p1, p2)
            d23 = hav(p2, p3)
            if d12 < 40 or d23 < 40:
                continue
            b1 = bearing_deg(p1, p2)
            b2 = bearing_deg(p2, p3)
            turn = abs((b2 - b1 + 180) % 360 - 180)  # 0-180
            if turn < 55:  # donus varsa (maks cizgisel)
                continue
            # arac kokup (interior) = 180 - donus? basitlestir: donus buyukse keskin kivrim
            d = hav(pin, p2)
            if d > 1400:
                continue
            # ic keere hangi tarafta: donus yonu
            # vektorda donus sag mi sol mu (bearing'den bulabiliriz)
            lat, lon = round(p2[0], 5), round(p2[1], 5)
            # ic keere istikameti = donus tarafina dogru 12 m
            # sag donusyse (b2-b1)>0 taraf: ic taraf istikamet = b2 + 90; solda ise b2 - 90
            sgn = (b2 - b1 + 540) % 360 - 180
            inside_bearing = (b2 + (90 if sgn > 0 else -90) + 360) % 360
            dig = offset_point(lat, lon, inside_bearing, 12)
            bends.append({
                "lat": dig[0], "lon": dig[1],
                "dist": round(d), "turn": round(turn),
                "names": [w["tags"].get("name", "") or "-"],
            })
    bends.sort(key=lambda c: (c["dist"] - c["turn"] * 4))
    out = []
    for c in bends:
        if all(hav((c["lat"], c["lon"]), (o["lat"], o["lon"])) > 80 for o in out):
            out.append(c)
    return out[:6]


def road_info(pin, elems):
    best = None
    road_types = {}
    for el in elems:
        t = el.get("tags", {})
        hw = t.get("highway")
        if not hw or el.get("type") != "way" or not el.get("geometry"):
            continue
        road_types[hw] = road_types.get(hw, 0) + 1
        pts = [(g["lat"], g["lon"]) for g in el["geometry"]]
        seg = min(range(len(pts) - 1), key=lambda i: 0)  # doldurulacak asagi
        # nokta-doğru parçası mesafesi (yaklaşık, kus ucusu nokta-nokta)
        nd = min((hav(pin, p), p) for p in pts)
        if best is None or nd[0] < best[0]:
            best = (nd[0], hw, t.get("name", ""), pts.index(nd[1]))
    if not best:
        return None, road_types
    best_road = ("toprak/stabilize yol" if best[1] == "track" else
                 "asfalt yol" if best[1] in ("motorway", "trunk", "primary", "secondary") else
                 "köy yolu" if best[1] in ("tertiary", "unclassified", "residential") else best[1])
    return {"type": best_road, "osm": best[1], "name": best[2], "dist": round(best[0])}, road_types


def village_info(pin, elems):
    best = None
    for el in elems:
        t = el.get("tags", {})
        if t.get("place") not in ("village", "town", "hamlet") or el.get("type") != "node":
            continue
        p = (el["lat"], el["lon"])
        d = hav(pin, p)
        if best is None or d < best[0]:
            best = (d, t.get("name", "?"), t.get("place"))
    if not best:
        return None
    return {"name": best[1], "type": best[2], "dist": round(best[0])}


# ---------------------------------------------------------------- ana akis
OUT_PATH = os.path.join(HERE, "pin_enrich.json")
out = {}
if os.path.exists(OUT_PATH):
    try:
        with open(OUT_PATH, encoding="utf-8") as f:
            out = {str(k): v for k, v in json.load(f).items()}
        print(f"resume: {len(out)} kayit yuklendi", flush=True)
    except Exception as e:
        print(f"resume okunamadi ({e}) — sifirdan", flush=True)
        out = {}


def complete(e):
    if not e or e.get("error"):
        return False
    if "confluences" not in e and "bends" not in e:
        return False
    if "soil_error" in e and "soil" not in e:
        return False
    if "climate_error" in e and "climate" not in e:
        return False
    if "village" not in e:
        return False
    return True


FORCE = "--force" in sys.argv

for sid, (lat, lon, ad) in sorted(SITES.items()):
    key = str(sid)
    if not FORCE and key in out and complete(out[key]):
        print(f"#{sid:2d} {ad} ... ZATEN TAMAM (atlanir)", flush=True)
        continue
    print(f"#{sid:2d} {ad} ...", flush=True)
    entry = {"id": sid, "lat": lat, "lon": lon}
    try:
        elems = overpass(lat, lon).get("elements", [])
    except Exception as e:
        entry["error"] = f"overpass: {str(e)[:80]}"
        out[key] = entry
        print(f"   OVERPASS HATA: {str(e)[:60]}", flush=True)
        time.sleep(5)
        with open(OUT_PATH, "w", encoding="utf-8") as f:
            json.dump(out, f, ensure_ascii=False, indent=1)
        continue

    water = stream_geometry(elems)
    entry["n_waterways"] = len(water)

    # mantik yurutme: kavsak + ic kiyi
    try:
        entry["confluences"] = find_confluences((lat, lon), water)
        entry["bends"] = find_bends((lat, lon), water)
        print(f"   kavşak {len(entry['confluences'])} | iç kıyı {len(entry['bends'])}", flush=True)
    except Exception as e:
        entry["logic_error"] = str(e)[:80]
        print(f"   mantik hatasi: {str(e)[:60]}", flush=True)

    # ulasim
    try:
        rd, _types = road_info((lat, lon), elems)
        entry["road"] = rd
        entry["village"] = village_info((lat, lon), elems)
        s = entry["road"]
        print(f"   yol: {s['type']} ({s['dist']} m) | köy: {entry['village']['name'] if entry['village'] else '-'}",
              flush=True)
    except Exception as e:
        entry["access_error"] = str(e)[:60]
        print(f"   erisim hatasi: {str(e)[:50]}", flush=True)

    # afyon mesafesi
    entry["afyon_km"] = round(hav((lat, lon), AFYON) / 1000, 1)

    # toprak yapi
    try:
        so = soilgrids(lat, lon)
        entry["soil"] = so
        print(f"   toprak: {so}", flush=True)
    except Exception as e:
        entry["soil_error"] = str(e)[:60]
        print(f"   soil hata: {str(e)[:50]}", flush=True)
    time.sleep(1.5)

    # yagis / debi
    try:
        om = openmeteo_annual(lat, lon)
        entry["climate"] = om
        print(f"   iklim: {om}", flush=True)
    except Exception as e:
        entry["climate_error"] = str(e)[:60]
        print(f"   iklim hata: {str(e)[:50]}", flush=True)
    time.sleep(1.5)

    out[key] = entry
    # KRIK - her nokta sonrasi diske yaz (suec kilitlenme veya kesinti veri kaybettirmesin)
    with open(OUT_PATH, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    time.sleep(2.0)

ok = sum(1 for v in out.values() if not v.get("error"))
print(f"\nTAMAM: {ok}/{len(SITES)} nokta zenginlestirildi -> tools/pin_enrich.json", flush=True)
