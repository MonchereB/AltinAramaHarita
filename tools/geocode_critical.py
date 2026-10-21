#!/usr/bin/env python3
"""Kritik noktalar icin coklu sorgu denemeli Nominatim kodlama."""
import json, time, urllib.parse, urllib.request

UA = "AltinAramaHarita/1.0 (geology research)"

TARGETS = {
    "OmerGecEK":  ["Ömer Geçek, Afyonkarahisar", "Ömergeçek, Afyonkarahisar", "Omer GecEK, Afyon"],
    "Gazligol":   ["Gazlıgöl, İhsaniye", "Gazlıgöl, Afyonkarahisar", "Gazligol, Afyon"],
    "Gecek":      ["Geçek, Afyonkarahisar", "Ömergeçek", "Gecek, Bolvadin"],
    "Doglat":     ["Doğlat, Bayat, Afyonkarahisar", "Doğlat, Afyonkarahisar", "Doğlat, Türkiye"],
    "Asagisagir": ["Aşağısağır, Emirdağ, Afyonkarahisar", "Aşağısağır, Afyonkarahisar", "Asagisagir, Emirdag"],
    "Kumcay":     ["Kumçay, Sandıklı, Afyonkarahisar", "Kumçay, Afyonkarahisar"],
    "Tuzlukcu":   ["Tuzlukçu, Şuhut", "Tuzlukçu, Afyonkarahisar"],
    "Eynehan":    ["Eynehan, Afyonkarahisar", "Eynehan, Emirdağ"],
    "Kuzviran":   ["Kuzviran, Afyonkarahisar", "Kuzviran, Emirdağ"],
    "Balcikhisar":["Balçıkhisar, Şuhut", "Balçıkhisar, Kütahya"],
    "Ilyasli":    ["İlyaslı, Şuhut", "İlyaslı, Kütahya"],
    "Kocatepe":   ["Kocatepe, Şuhut", "Kocatepe, Kütahya"],
    "Heybeli":    ["Heybeli, Bolvadin", "Heybeli, Afyonkarahisar"],
    "Kizik":      ["Kızık, Afyonkarahisar", "Kızık, İhsaniye"],
    "Uyuz":       ["Uyuz, Afyonkarahisar", "Uyuz, Sinanpaşa"],
    "Hudai":      ["Hüdai, Sandıklı", "Hüdai, Afyonkarahisar"],
    "Kirka":      ["Kırka, Sandıklı", "Kırka, Afyonkarahisar"],
    "Yesilyurt":  ["Yeşilyurt, Çay, Afyonkarahisar", "Yeşilyurt, Afyonkarahisar"],
    "Kiyir":      ["Kıyır, Afyonkarahisar", "Kıyır, Emirdağ"],
    "Altintas":   ["Altıntaş, Afyonkarahisar", "Altıntaş, Dinar"],
    "Civril":     ["Çivril, Denizli", "Çivril"],
    "Kocak":      ["Koçak, Çivril", "Koçak, Denizli"],
    "Cakallar":   ["Çakallar, Sandıklı", "Çakallar, Afyonkarahisar"],
    "Gokbeli":    ["Gökbeli, Sandıklı", "Gökbeli, Afyonkarahisar"],
    "Yorukoglu":  ["Yörükoğlu, Sandıklı", "Yörükoğlu, Afyonkarahisar"],
    "Kiziloren":  ["Kızılören, Bolvadin", "Kızılören, Afyonkarahisar"],
    "Konak":      ["Konak, Sandıklı", "Konak, Emirdağ"],
    "Cavakli":    ["Çavaklı, Emirdağ", "Çavaklı, Afyonkarahisar"],
    "Aciplar":    ["Sarayköy, Emirdağ", "Sarayköy, Afyonkarahisar"],
    "Alapli":     ["Alaplı, Afyonkarahisar", "Alaplı, Sinanpaşa"],
    "Karakaya":   ["Karakaya, Emirdağ", "Karakaya, Afyonkarahisar"],
    "Aglikar":    ["Ağzıkara, Sandıklı", "Ağzıkara, Afyonkarahisar"],
    "KizikUyuz":  ["Kızık Uyuz, Afyonkarahisar"],
    "Fethibey":   ["Fethibey, Afyonkarahisar", "Fethibey, İhsaniye"],
    "Ablak":      ["Ablak, Afyonkarahisar", "Ablak, İhsaniye"],
}


def geocode(q):
    url = "https://nominatim.openstreetmap.org/search?" + urllib.parse.urlencode(
        {"q": q, "format": "json", "limit": 1, "countrycodes": "tr", "accept-language": "tr"})
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=25) as r:
        data = json.loads(r.read().decode("utf-8"))
    if not data:
        return None
    it = data[0]
    return (round(float(it["lat"]), 5), round(float(it["lon"]), 5), it.get("display_name", ""))


out = {}
for tag, queries in TARGETS.items():
    hit = None
    for q in queries:
        try:
            hit = geocode(q)
        except Exception:
            hit = None
        if hit:
            break
        time.sleep(1.0)
    if hit:
        out[tag] = hit
        print(f"{tag:14s} {hit[0]:9.5f}, {hit[1]:10.5f} | {hit[2][:80]}", flush=True)
    else:
        print(f"{tag:14s} -- BULUNAMADI --", flush=True)
    time.sleep(1.0)

print("\n=== JSON ===")
print(json.dumps(out, ensure_ascii=False, indent=1))
