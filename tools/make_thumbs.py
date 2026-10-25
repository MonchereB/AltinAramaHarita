#!/usr/bin/env python3
"""
Uydu haritasından her altin noktasi icin kucuk resim uretir.
Girdi: app/src/main/assets/offline/afyon_sat.mbtiles  (build_satellite.py once CALISTRILMALI)
Cikti: app/src/main/assets/site_imgs/site_XX.jpg  (512x512 JPEG ~60-100 KB)

pin koordinatlari locations.json'dan okunur; z15 3x3 karo ortasi cekilir.
"""
import io
import json
import math
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
MBTILES = os.path.join(HERE, "..", "app", "src", "main", "assets", "offline", "afyon_sat.mbtiles")
IMG_DIR = os.path.join(HERE, "..", "app", "src", "main", "assets", "site_imgs")
LOCS = os.path.join(HERE, "..", "app", "src", "main", "assets", "locations.json")

TILE_Z = 15      # ~4.8 m/merkezim — dere + baglam gorunur
RADIUS = 1       # 3x3 karo
OUT_SIZE = 512
JPEG_Q = 82


def tile_bounds(lat, lon, zoom, radius):
    n = 2 ** zoom
    lat_rad = math.radians(lat)
    x = int((lon + 180.0) / 360.0 * n)
    y = int((1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * n)
    return (zoom, x, y)


def read_tile(cur, z, x, y_tms):
    row = cur.execute(
        "SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?",
        (z, x, y_tms),
    ).fetchone()
    if row:
        return Image.open(io.BytesIO(row[0])).convert("RGB")
    return Image.new("RGB", (256, 256), (34, 44, 40))  # karo yoksa koyu gri


def main():
    os.makedirs(IMG_DIR, exist_ok=True)
    import sqlite3
    con = sqlite3.connect(MBTILES)
    cur = con.cursor()
    with open(LOCS, encoding="utf-8") as f:
        data = json.load(f)

    total_kb = 0
    for loc in data:
        sid = int(loc["id"])
        z, x, y = tile_bounds(loc["lat"], loc["lon"], TILE_Z, RADIUS)
        n = 2 ** z
        # tam karo icerisine konum (0..1) -> bicim icin
        lat_r = math.radians(float(loc["lat"]))
        fx = ((float(loc["lon"]) + 180.0) / 360.0 * n) - x
        fy = (1.0 - math.asinh(math.tan(lat_r)) / math.pi) / 2.0 * n - y

        img = Image.new("RGB", (256 * (2 * RADIUS + 1), 256 * (2 * RADIUS + 1)), (20, 24, 28))
        for dx in range(-RADIUS, RADIUS + 1):
            for dy in range(-RADIUS, RADIUS + 1):
                tx, ty = x + dx, y + dy
                tile = read_tile(cur, z, tx, n - 1 - ty)  # TMS cevrimi
                img.paste(tile, (256 * (RADIUS + dx), 256 * (RADIUS + dy)))

        # kisi karenip 512 ve kucuk konum cizgisi
        img = img.resize((OUT_SIZE, OUT_SIZE), Image.LANCZOS)
        d = ImageDraw.Draw(img)
        # pin'in kare icindeki yeri: (RADIUS + fx) / (2R+1) orani x OUT_SIZE
        px = (RADIUS + fx) / (2 * RADIUS + 1) * OUT_SIZE
        py = (RADIUS + fy) / (2 * RADIUS + 1) * OUT_SIZE
        for w, rad in ((5, 12), (3, 7)):
            d.ellipse([px - rad, py - rad, px + rad, py + rad],
                      outline=(255, 64, 32), width=w)
        out = os.path.join(IMG_DIR, f"site_{sid:02d}.jpg")
        img.save(out, "JPEG", quality=JPEG_Q, optimize=True)
        kb = os.path.getsize(out) / 1024
        total_kb += kb
        print(f"#{sid:2d} {loc['name'][:24]:24s} site_{sid:02d}.jpg  {kb:5.0f} KB", flush=True)

    con.close()
    print(f"TAMAM: {len(data)} resim, toplam {total_kb/1024:.2f} MB")


if __name__ == "__main__":
    main()
