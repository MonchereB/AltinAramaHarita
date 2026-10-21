#!/usr/bin/env python3
"""
Afyonkarahisar bolgesi icin MBTiles (OpenTopoMap) dosyasi uretir / gunceller.
Cikti: app/src/main/assets/offline/afyon.mbtiles

ONEMLI: tile.openstreetmap.org toplu indirmeyi engelliyor ("Access blocked").
Bu betik OpenTopoMap kaynagini kullanir (topografik; altin arama icin daha uygun).
Kullanim: tiles.opentopomap.org - ucretsiz, atif zorunlu.

Betik "resume" destekler: veritabaninda zaten bulunan karolar indirilmez.
"""
import math
import os
import sqlite3
import sys
import threading
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "offline", "afyon.mbtiles")

TILE_URL = "https://tile.opentopomap.org/{z}/{x}/{y}.png"
UA = ("AltinAramaHarita/1.0 (Android offline gold-panning map for Afyonkarahisar; "
      "https://www.opentopomap.org/; egitim amacli)")

# --- Plan: bolge (genis) - dusuk zoomda daha genis kapsama ---
REGION = {
    "min_lat": 37.85, "max_lat": 39.60,
    "min_lon": 28.95, "max_lon": 32.05,
    "zooms": [10, 11],
}
# --- Plan: il geni ---
DISTRICT = {
    "min_lat": 37.95, "max_lat": 39.25,
    "min_lon": 29.35, "max_lon": 31.65,
    "zooms": [12],
}
# --- Plan: yakin cevre (tum altin noktalarini kapsar) ---
MID = {
    "min_lat": 38.00, "max_lat": 39.15,
    "min_lon": 30.00, "max_lon": 31.45,
    "zooms": [13],
}
# --- Plan: sehir merkezi (detay) ---
CITY = {
    "min_lat": 38.68, "max_lat": 38.86,
    "min_lon": 30.40, "max_lon": 30.68,
    "zooms": [14],
}
CITY_CORE = {
    "min_lat": 38.71, "max_lat": 38.82,
    "min_lon": 30.47, "max_lon": 30.61,
    "zooms": [15],
}

THREADS = 3          # sunucuya nazik kalmak icin
DELAY = 0.35         # istek basina bekleme (sn)

_lock = threading.Lock()
_stats = {"ok": 0, "skip": 0, "err": 0}
_tiles = []          # indirilecek (z, x, y_xyz) listesi


def deg2num(lat, lon, zoom):
    lat_rad = math.radians(lat)
    n = 2.0 ** zoom
    x = int((lon + 180.0) / 360.0 * n)
    y = int((1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * n)
    return x, y


def collect(spec):
    """Veritabaninda olmayan karolari listeye ekler."""
    min_lat, max_lat = spec["min_lat"], spec["max_lat"]
    min_lon, max_lon = spec["min_lon"], spec["max_lon"]
    for z in spec["zooms"]:
        x0, y0 = deg2num(max_lat, min_lon, z)
        x1, y1 = deg2num(min_lat, max_lon, z)
        n = 2 ** z
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                if 0 <= y < n:
                    _tiles.append((z, x, y))


def fetch_one(job):
    z, x, y = job
    n = 2 ** z
    row = n - 1 - y  # MBTiles TMS (y cevrilmis)
    url = TILE_URL.format(z=z, x=x, y=y)
    data = None
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=25) as r:
                data = r.read()
            break
        except Exception:
            time.sleep(1.0 * (attempt + 1))
    with _lock:
        if data is None:
            _stats["err"] += 1
            return None
        _stats["ok"] += 1
        if _stats["ok"] % 100 == 0:
            print(f"  ... {_stats['ok']} karo indirildi", flush=True)
    time.sleep(DELAY)
    return (z, x, row, data)


def init_db():
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
        ("name", "Afyonkarahisar OSM"),
        ("type", "baselayer"),
        ("version", "1.0"),
        ("description", "Afyonkarahisar - OpenTopoMap (CC-BY-SA)"),
        ("bounds", "29.35,37.95,31.65,39.25"),
    ])
    con.commit()
    return con, cur


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    fresh = not os.path.exists(OUT)
    if fresh:
        print("Yeni MBTiles olusturuluyor...")
        con, cur = init_db()
    else:
        print(f"Mevcut MBTiles guncelleniyor ({os.path.getsize(OUT)/1048576:.1f} MB)...")
        con, cur = init_db()

    # Eski (engellenmis) karolari temizle
    # NOT: Normalde silinmez - betik "resume" destekler, sadece eksik karolar indirilir.
    # Tam yeniden uretim isterseniz: python tools\build_mbtiles.py --fresh
    if "--fresh" in sys.argv:
        cur.execute("DELETE FROM tiles")
        con.commit()
        print("Eski karolar silindi (--fresh).")

    for spec in (REGION, DISTRICT, MID, CITY, CITY_CORE):
        collect(spec)

    # Veritabaninda zaten olanlari at
    todo = []
    for (z, x, y) in _tiles:
        n = 2 ** z
        row = n - 1 - y
        if cur.execute("SELECT 1 FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?",
                       (z, x, row)).fetchone():
            _stats["skip"] += 1
        else:
            todo.append((z, x, y))
    print(f"Toplam {len(todo)} karo indirilecek "
          f"({_stats['skip']} karo zaten var).")

    with ThreadPoolExecutor(max_workers=THREADS) as pool:
        for res in pool.map(fetch_one, todo):
            if res:
                z, x, row, data = res
                cur.execute("INSERT OR REPLACE INTO tiles VALUES (?,?,?,?)",
                            (z, x, row, sqlite3.Binary(data)))
                if _stats["ok"] % 50 == 0:
                    con.commit()

    con.commit()
    total = cur.execute("SELECT count(*) FROM tiles").fetchone()[0]
    con.close()
    size = os.path.getsize(OUT) / (1024 * 1024)
    print(f"TAMAM: {total} karo ({_stats['ok']} yeni, {_stats['err']} hata), "
          f"{size:.1f} MB -> {OUT}")


if __name__ == "__main__":
    main()
