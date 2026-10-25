#!/usr/bin/env python3
"""
Afyonkarahisar bolgesi icin UYDU MBTiles (Google uydu katmani) uretir.
Cikti: app/src/main/assets/offline/afyon_sat.mbtiles

Kaynak: Google uydu katmani (mt*.google.com/vt/lyrs=s) - "CANLI" canli akis
  gerceklestirmek cunku Esri kirsal kesimde z19'da oluyor;
  Google'in kirsal kesimde z20'ye kadar gercek detayi var (~0.15 m/px).

Plan:
  z10-11  bolge (genis)      37.85-39.60 K / 28.95-32.05 D
  z12     il geni            37.95-39.25 K / 29.35-31.65 D
  z13     yakin cevre        38.00-39.15 K / 30.00-31.45 D
  z14-17  her altin noktasi etrafi (z14 3x3, z15 5x5, z16 5x5, z17 3x3)
  z18-20  DERIN BLOK:
    - her altin noktasi   : z18 5x5, z19 5x5, z20 3x3 (pin cevresi maximum detay)
    - her kazı noktasi    : z18 3x3, z19 3x3, z20 3x3 (locations.json dig_points)

Betik "resume" destekler: veritabaninda zaten olan karolar indirilmez.
--fresh argümani classname'in tiles tablosunu siler ve VACUUM yapar (kaynak
degisiminde kullanilir - tum karolari yeniden indir).
"""
import json
import math
import os
import sqlite3
import sys
import threading
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "offline", "afyon_sat.mbtiles")

# Google uydu katmani (mt0..mt3 rotasyonlu)
UA = ("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
      "AltinAramaHarita/1.3 offline satellite tiles")

REGION = {"min_lat": 37.85, "max_lat": 39.60, "min_lon": 28.95, "max_lon": 32.05, "zooms": [10, 11]}
DISTRICT = {"min_lat": 37.95, "max_lat": 39.25, "min_lon": 29.35, "max_lon": 31.65, "zooms": [12]}
MID = {"min_lat": 38.00, "max_lat": 39.15, "min_lon": 30.00, "max_lon": 31.45, "zooms": [13]}

# pin etrafi: z14 3x3 (~4.5 km), z15 5x5 (~4.5 km), z16 5x5 (~2.3 km), z17 3x3 (~0.7 km)
# derin:  z18 5x5 (~0.4 km), z19 5x5 (~0.2 km), z20 3x3 (~90 m)
SITE_ZOOMS = [14, 15, 16, 17, 18, 19, 20]
SITE_BLOCK = {14: 1, 15: 2, 16: 2, 17: 1, 18: 2, 19: 2, 20: 1}
# kazı noktasi etrafi: z18 3x3, z19 3x3, z20 3x3
DIG_ZOOMS = [18, 19, 20]
DIG_BLOCK = {18: 1, 19: 1, 20: 1}

THREADS = 6
DELAY = 0.15
MIN_JPEG_BYTES = 600   # boz/kirmizi placeholder karolari ele

_lock = threading.Lock()
_stats = {"ok": 0, "skip": 0, "err": 0}
_seen = {}
TODO_ALL = []

def deg2num(lat, lon, zoom):
    lat_rad = math.radians(lat)
    n = 2.0 ** zoom
    x = int((lon + 180.0) / 360.0 * n)
    y = int((1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * n)
    return x, y

def seen_add(z, x, y):
    """Duplikat karo toplamayi engelle (pin+kazi dosyalari orusabilir)."""
    if (z, x, y) in _seen:
        return False
    _seen[(z, x, y)] = True
    return True

def collect(spec):
    for z in spec["zooms"]:
        x0, y0 = deg2num(spec["max_lat"], spec["min_lon"], z)
        x1, y1 = deg2num(spec["min_lat"], spec["max_lon"], z)
        n = 2 ** z
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                if 0 <= y < n:
                    seen_add(z, x, y)

def collect_zone(lat, lon, zooms, blocks, label, detalist):
    cx, cy = deg2num(lat, lon, max(zooms))
    for z in zooms:
        r = blocks.get(z, 1)
        zx, zy = deg2num(lat, lon, z)
        n = 2 ** z
        for x in range(zx - r, zx + r + 1):
            for y in range(zy - r, zy + r + 1):
                if 0 <= x < n and 0 <= y < n:
                    seen_add(z, x, y)
    detalist.append(label)

def read_json(path):
    if not os.path.exists(path):
        return None
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return None

def collect_sites_and_digs():
    labeled = []
    loc_path = os.path.join(HERE, "..", "app", "src", "main", "assets", "locations.json")
    data = read_json(loc_path)
    if data:
        pin_count, dig_count = 0, 0
        for d in data:
            try:
                lat, lon = float(d["lat"]), float(d["lon"])
            except Exception:
                continue
            collect_zone(lat, lon, SITE_ZOOMS, SITE_BLOCK,
                         d.get("name", ""), labeled)
            pin_count += 1
            for dp in d.get("dig_points", [])[:3]:
                try:
                    dla, dlo = float(dp["lat"]), float(dp["lon"])
                except Exception:
                    continue
                collect_zone(dla, dlo, DIG_ZOOMS, DIG_BLOCK,
                             f"  kazi: {dp.get('type', '?')} @( {dla:.5f},{dlo:.5f})", labeled)
                dig_count += 1
        print(f"  {pin_count} altin noktasi (z14-20) + {dig_count} kazi noktasi (z18-20)",
              flush=True)
        return pin_count, dig_count
    print("locations.json bulunamadi! Biner karo az kalir.")
    return 0, 0

def fetch_one(job):
    z, x, y = job
    row = 2 ** z - 1 - y  # MBTiles TMS
    host = (x + y + z) % 4
    url = f"https://mt{host}.google.com/vt/lyrs=s&hl=tr&x={x}&y={y}&z={z}"
    data = None
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                data = r.read()
            if len(data) < MIN_JPEG_BYTES:
                with _lock:
                    _stats["err"] += 1
                print(f"  TINY z{z} {x},{y} = {len(data)} bayt (placeholder?), atlandi",
                      flush=True)
                return None
            break
        except Exception:
            time.sleep(0.8 * (attempt + 1))
    with _lock:
        if data is None:
            _stats["err"] += 1
        else:
            _stats["ok"] += 1
            if _stats["ok"] % 200 == 0:
                print(f"  ... {_stats['ok']}/{len(TODO_ALL)} uydu karosu", flush=True)
    time.sleep(DELAY)
    return None if data is None else (z, x, row, data)

def init_db():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    con = sqlite3.connect(OUT)
    cur = con.cursor()
    cur.execute("PRAGMA synchronous=0")
    cur.execute("CREATE TABLE IF NOT EXISTS metadata (name text, value text)")
    cur.execute("CREATE TABLE IF NOT EXISTS tiles (zoom_level integer, tile_column integer, "
                "tile_row integer, tile_data blob)")
    cur.execute("CREATE UNIQUE INDEX IF NOT EXISTS tile_index ON tiles "
                "(zoom_level, tile_column, tile_row)")
    cur.execute("CREATE UNIQUE INDEX IF NOT EXISTS name ON metadata (name)")
    cur.executemany("INSERT OR REPLACE INTO metadata VALUES (?, ?)", [
        ("name", "Afyonkarahisar Uydu"),
        ("type", "baselayer"),
        ("version", "1.1"),
        ("description", "Afyonkarahisar uydu goruntusu - Google uydu katmani (kişisel kullanim)"),
        ("bounds", "29.35,37.95,31.65,39.25"),
    ])
    con.commit()
    return con, cur

def main():
    global todo
    con, cur = init_db()
    if os.path.exists(OUT):
        print(f"Mevcut uydu MBTiles: {os.path.getsize(OUT)/1048576:.1f} MB")

    if "--fresh" in sys.argv:
        cur.execute("DELETE FROM tiles")
        con.commit()
        cur.execute("VACUUM")
        con.commit()
        print("Eski uydu karolar silindi (--fresh + VACUUM).")

    for spec in (REGION, DISTRICT, MID):
        collect(spec)

    pins, digs = collect_sites_and_digs()
    todo = list(_seen.keys())
    todo.sort()
    fetched = []
    for (z, x, y) in todo:
        n = 2 ** z
        row = n - 1 - y
        if cur.execute("SELECT 1 FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?",
                       (z, x, row)).fetchone():
            _stats["skip"] += 1
        else:
            fetched.append((z, x, y))
    print(f"Toplam {len(fetched)} uydu karosu indirilecek ({_stats['skip']} zaten var).")
    TODO_ALL.append(len(fetched))

    with ThreadPoolExecutor(max_workers=THREADS) as pool:
        done = 0
        for res in pool.map(fetch_one, fetched):
            if res:
                z, x, row, data = res
                cur.execute("INSERT OR REPLACE INTO tiles VALUES (?,?,?,?)",
                            (z, x, row, sqlite3.Binary(data)))
                done += 1
                if done % 100 == 0:
                    con.commit()

    con.commit()
    total = cur.execute("SELECT count(*) FROM tiles").fetchone()[0]
    con.close()
    size = os.path.getsize(OUT) / (1024 * 1024)
    print(f"TAMAM: {total} uydu karosu ({_stats['ok']} yeni, {_stats['err']} hata), "
          f"{size:.1f} MB -> {OUT}")
    # bolge dagitimi
    counts = {}
    db = sqlite3.connect(OUT)
    for (z, c) in db.execute("SELECT zoom_level, count(*) FROM tiles GROUP BY zoom_level"):
        counts[z] = c
    db.close()
    print("  zoom dagilimi:", dict(sorted(counts.items())), flush=True)

if __name__ == "__main__":
    main()
