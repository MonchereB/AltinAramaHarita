#!/usr/bin/env python3
"""
pin_streams.json -> locations.json birlestirme.

Her altin noktasi icin:
  - Koordinat, SPESIFIK akarsu noktasina (dere yatagi uzerine) tasinir
  - elevation_m gercek rakimle degistirilir
  - terrain alanina akarsu bilgisi eklenir
  - indicators alanina o noktadaki somut gostergeler yazilir
  - description alaninda noktanin kriterleri belirtilir
"""
import json, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
LOC = os.path.join(HERE, "..", "app", "src", "main", "assets", "locations.json")
STREAMS = os.path.join(HERE, "pin_streams.json")


def fmt_elev(v):
    if v is None:
        return None
    return "~%d" % round(v)


def main():
    with open(LOC, encoding="utf-8") as f:
        locs = json.load(f)
    with open(STREAMS, encoding="utf-8") as f:
        streams = json.load(f)

    updated = 0
    for loc in locs:
        st = streams.get(str(loc["id"]))
        if not st or st.get("error") or st.get("lat") is None:
            print(f"#{loc['id']:2d} {loc['name'][:40]:40s} -> ATLANDI ({st.get('error','veri yok') if st else 'veri yok'})")
            continue

        # 1) Koordinati spesifik akarsu noktasina tasi
        loc["lat"] = st["lat"]
        loc["lon"] = st["lon"]

        # 2) Gercek rakim
        e = fmt_elev(st.get("elev_m"))
        if e:
            loc["elevation_m"] = e

        # 3) Arazi alani: akarsu detayi
        wname = st.get("name") or ""
        wtype = st.get("waterway") or ""
        wlabel = {
            "stream": "dere/çay",
            "river": "nehir/çay",
        }.get(wtype, wtype)
        terrain = loc.get("terrain", "")
        if wname:
            loc["terrain"] = f"{terrain} • {wname} ({wlabel}) yatağı"
        else:
            loc["terrain"] = f"{terrain} • {wlabel} yatağı"

        # 4) Gostergeler: o noktada nelere bakilacagi
        ind = loc.get("indicators", "")
        extra = []
        if st.get("seg_len_m"):
            extra.append(f"işaretlenen akarsu parçası ≈{st['seg_len_m']} m")
        if st.get("dist_from_center_m") is not None:
            extra.append(f"yerleşim merkezine {st['dist_from_center_m']} m")
        if e:
            extra.append(f"nokta rakımı {e} m")
        if extra:
            loc["indicators"] = (ind + " || " if ind else "") + "; ".join(extra)

        # 5) Aciklama: kriterler bu spesifik noktada
        crit = []
        if wname:
            crit.append(f"PİN {wname} ({wlabel}) yatağının üzerindedir")
        else:
            crit.append(f"PİN {wlabel} yatağının üzerindedir")
        crit.append(f"bu noktada rakım {e} m" if e else "rakım bilgisi alınamadı")
        if st.get("seg_len_m"):
            crit.append(f"akarsu bu bölümde ≈{st['seg_len_m']} m uzunlukta")
        crit.append("DERE YATAĞININ TABANI: altın ağır minerallerle birlikte burada çöker")
        loc["description"] = " || ".join(crit) + " || " + loc.get("description", "")

        # 6) Kaynak alanina akarsu detayi
        src = loc.get("source_deposit", "")
        if wname:
            loc["source_deposit"] = src

        updated += 1
        print(f"#{loc['id']:2d} {loc['name'][:34]:34s} -> {st['lat']}, {st['lon']}  {e}  {wname or wlabel}")

    with open(LOC, "w", encoding="utf-8") as f:
        json.dump(locs, f, ensure_ascii=False, indent=2)
    print(f"\n{updated}/{len(locs)} pin guncellendi: {LOC}")


if __name__ == "__main__":
    main()
