#!/usr/bin/env python3
"""Farkli karo kaynaklarini test eder: gercek harita mi, engelleme placeholder'i mi?"""
import os
import urllib.request

OUT = r"C:\Users\bgulb\AppData\Local\Temp\opencode\tiles_test"
os.makedirs(OUT, exist_ok=True)

# Afyonkarahisar merkezi (38.7569, 30.5387), z13
Z, X, Y = 13, 4791, 3137

SOURCES = {
    "osm_standart": f"https://tile.openstreetmap.org/{Z}/{X}/{Y}.png",
    "osm_de": f"https://tile.openstreetmap.de/{Z}/{X}/{Y}.png",
    "carto_voyager": f"https://a.basemaps.cartocdn.com/rastertiles/voyager/{Z}/{X}/{Y}.png",
    "carto_light": f"https://a.basemaps.cartocdn.com/light_all/{Z}/{X}/{Y}.png",
    "opentopo": f"https://tile.opentopomap.org/{Z}/{X}/{Y}.png",
}

UAS = {
    "default": "AltinAramaHarita/1.0 (offline tile builder; egitim amacli)",
    "compliant": ("AltinAramaHarita/1.0 (Android offline gold-panning map; "
                  "https://www.openstreetmap.org/copyright; egitim amacli)"),
}

for name, url in SOURCES.items():
    for ua_name, ua in UAS.items():
        try:
            req = urllib.request.Request(url, headers={"User-Agent": ua})
            with urllib.request.urlopen(req, timeout=25) as r:
                data = r.read()
            fname = os.path.join(OUT, f"{name}_{ua_name}.png")
            with open(fname, "wb") as f:
                f.write(data)
            print(f"{name:15s} [{ua_name:9s}] -> {len(data):7d} bayt  {'PNG' if data[:4]==b'\x89PNG' else '???'}")
        except Exception as e:
            print(f"{name:15s} [{ua_name:9s}] -> HATA: {e}")
