#!/usr/bin/env python3
"""Build course-use offline city material; the Android app never downloads it."""
from __future__ import annotations

import concurrent.futures
import hashlib
import json
import math
from pathlib import Path
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / ".tools" / "city-boundaries"
OUTPUT = ROOT / "app/src/main/assets/atlas"
TAIWAN_URL = "https://github.com/plotdb/pdmaptw/releases/download/v2.4.0/county-2025-03-18.geojson"
TAIWAN_SHA256 = "a809e483184d7762918476058cc9d7e10e9720113187afee2a04b4f7ced3103f"


def fetch(url: str, filename: str, expected: str | None = None) -> tuple[dict, dict]:
    path = CACHE / filename
    if not path.is_file():
        for attempt in range(3):
            try:
                request = urllib.request.Request(url, headers={"User-Agent": "TravelJournal-course-material/1.3"})
                with urllib.request.urlopen(request, timeout=35) as response:
                    data = response.read(12 * 1024 * 1024 + 1)
                if len(data) > 12 * 1024 * 1024:
                    raise ValueError("Boundary file too large")
                json.loads(data)
                path.write_bytes(data)
                break
            except Exception:
                if attempt == 2:
                    raise
                time.sleep(attempt + 1)
    data = path.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if expected and digest != expected:
        raise ValueError(f"Source hash mismatch: {filename}")
    return json.loads(data), {"url": url, "sha256": digest, "bytes": len(data)}


def simplify(ring: list, tolerance: float = 0.008) -> list:
    """Iterative Douglas-Peucker; closed rings and tiny islands are retained."""
    if len(ring) < 8:
        return ring
    keep = {0, len(ring) - 1}
    todo = [(0, len(ring) - 1)]
    while todo:
        first, last = todo.pop()
        a, b = ring[first], ring[last]
        dx, dy = b[0] - a[0], b[1] - a[1]
        length = dx * dx + dy * dy
        largest, index = 0.0, first
        for i in range(first + 1, last):
            p = ring[i]
            t = max(0.0, min(1.0, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / length)) if length else 0.0
            distance = (p[0] - a[0] - t * dx) ** 2 + (p[1] - a[1] - t * dy) ** 2
            if distance > largest:
                largest, index = distance, i
        if largest > tolerance * tolerance:
            keep.add(index)
            todo.extend(((first, index), (index, last)))
    result = [[round(ring[i][0], 5), round(ring[i][1], 5)] for i in sorted(keep)]
    return result if len(result) >= 4 else ring


def geometry(source: dict) -> dict:
    kind = source["type"]
    if kind == "Polygon":
        coords = [simplify(ring) for ring in source["coordinates"]]
    elif kind == "MultiPolygon":
        coords = [[simplify(ring) for ring in polygon] for polygon in source["coordinates"]]
    else:
        raise ValueError(f"Unsupported geometry: {kind}")
    return {"type": kind, "coordinates": coords}


def label_center(feature: dict) -> list:
    props = feature["properties"]
    for key in ("centroid", "center", "cp"):
        center = props.get(key)
        if isinstance(center, list) and len(center) >= 2:
            return center[:2]
    source = feature["geometry"]
    polygons = source["coordinates"] if source["type"] == "MultiPolygon" else [source["coordinates"]]
    points = [p for poly in polygons for p in poly[0]]
    return [(min(p[0] for p in points) + max(p[0] for p in points)) / 2,
            (min(p[1] for p in points) + max(p[1] for p in points)) / 2]


def interior_anchor(shape: dict, proposed: list) -> list:
    """Select an actual interior point, not a bounding-box midpoint over the sea."""
    def project(p: list) -> tuple:
        return p[0], -math.degrees(math.log(math.tan(math.pi / 4 + math.radians(p[1]) / 2)))
    def unproject(p: tuple) -> list:
        return [round(p[0], 6), round(math.degrees(2 * math.atan(math.exp(-math.radians(p[1]))) - math.pi / 2), 6)]
    rings = shape["coordinates"] if shape["type"] == "MultiPolygon" else [shape["coordinates"]]
    polygons = [[[project(p) for p in ring] for ring in poly] for poly in rings]
    def in_ring(p: tuple, ring: list) -> bool:
        x, y = p
        inside = False
        for a, b in zip(ring, ring[1:] + ring[:1]):
            if (a[1] > y) != (b[1] > y) and x < (b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0]:
                inside = not inside
        return inside
    def contains(p: tuple) -> bool:
        return any(in_ring(p, poly[0]) and not any(in_ring(p, hole) for hole in poly[1:]) for poly in polygons)
    if contains(project(proposed)):
        return proposed
    def area(poly: list) -> float:
        ring = poly[0]
        return abs(sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(ring,ring[1:]+ring[:1])))
    outer = max(polygons, key=area)[0]
    double_area = sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(outer,outer[1:]+outer[:1]))
    if double_area:
        cx = sum((a[0]+b[0])*(a[0]*b[1]-b[0]*a[1]) for a,b in zip(outer,outer[1:]+outer[:1]))/(3*double_area)
        cy = sum((a[1]+b[1])*(a[0]*b[1]-b[0]*a[1]) for a,b in zip(outer,outer[1:]+outer[:1]))/(3*double_area)
        if contains((cx,cy)):
            return unproject((cx,cy))
    left,right=min(p[0] for p in outer),max(p[0] for p in outer)
    top,bottom=min(p[1] for p in outer),max(p[1] for p in outer)
    candidates=[(left+(right-left)*(x+.5)/50,top+(bottom-top)*(y+.5)/50) for x in range(50) for y in range(50)]
    candidates.sort(key=lambda p:(p[0]-(left+right)/2)**2+(p[1]-(top+bottom)/2)**2)
    for p in candidates:
        if contains(p):
            return unproject(p)
    raise ValueError("Cannot find a safe city-selection anchor")


def feature(source: dict, code: str, name: str, province: dict) -> dict:
    shape = geometry(source["geometry"])
    return {"type": "Feature", "properties": {
        "code": code, "name": name, "provinceCode": str(province["adcode"]),
        "provinceName": province["name"], "center": interior_anchor(shape, label_center(source))},
        "geometry": shape}


def main() -> None:
    CACHE.mkdir(parents=True, exist_ok=True)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    text = (ROOT / "app/src/main/assets/map/china-boundaries.js").read_text(encoding="utf-8")
    country = json.loads(text.split("=", 1)[1].strip().rstrip(";"))
    provinces = [f for f in country["features"] if f["properties"].get("name")]
    assert len(provinces) == 34
    cities, sources = [], []
    direct = {110000, 120000, 310000, 500000, 810000, 820000}
    targets = [p for p in provinces if p["properties"]["adcode"] not in direct | {710000}]

    def province_cities(province: dict) -> tuple[list, dict]:
        props = province["properties"]
        code = props["adcode"]
        data, origin = fetch(f"https://geo.datav.aliyun.com/areas_v3/bound/{code}_full.json", f"{code}.json")
        entries = [feature(f, str(f["properties"]["adcode"]), f["properties"]["name"], props)
                   for f in data["features"] if f["properties"].get("name")]
        if not entries:
            raise ValueError(f"No city geometry for {props['name']}; province fallback is forbidden")
        print(f"{props['name']}: {len(entries)} city/independent units", flush=True)
        return entries, origin

    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        for entries, source in pool.map(province_cities, targets):
            cities.extend(entries)
            sources.append(source)
    for province in provinces:
        p = province["properties"]
        if p["adcode"] in direct:
            cities.append(feature(province, str(p["adcode"]), p["name"], p))

    taiwan, source = fetch(TAIWAN_URL, "taiwan-county-2025-03-18.geojson", TAIWAN_SHA256)
    sources.append(source)
    taiwan_province = next(p["properties"] for p in provinces if p["properties"]["adcode"] == 710000)
    assert len(taiwan["features"]) == 22, "Expected 22 county/city boundaries"
    for county in taiwan["features"]:
        p = county["properties"]
        name = p.get("name") or p.get("COUNTYNAME")
        code = p.get("code") or p.get("COUNTYCODE")
        assert name and code, p
        cities.append(feature(county, "TW" + str(code), name, taiwan_province))

    assert len({f["properties"]["code"] for f in cities}) == len(cities)
    material = {"version": 1, "provinces": country["features"], "cities": cities}
    packed = json.dumps(material, ensure_ascii=False, separators=(",", ":")).encode()
    (OUTPUT / "china-cities.json").write_bytes(packed)
    manifest = {
        "builtOn": "2026-11-05", "cityUnits": len(cities), "provincialRegions": 34,
        "sha256": hashlib.sha256(packed).hexdigest(), "bytes": len(packed),
        "simplificationToleranceDegrees": 0.008,
        "coverage": "Prefecture-level cities and province-administered units; municipalities and HK/Macao as single units; Taiwan 22 county/city units.",
        "purpose": "Course travel visualization, not navigation, surveying or legal boundary determination.",
        "sources": sources,
        "attribution": [
            "DataV.GeoAtlas; geographic material from Amap Open Platform, for learning/exchange. https://help.aliyun.com/zh/datav/datav-7-0/user-guide/datav-geoatlas-widgets/",
            "NLSC/MOI COUNTY_MOI_1140318 (2025-03-18), derived by plotdb/pdmaptw v2.4.0; Open Government Data License 1.0. https://data.gov.tw/en/datasets/7442",
            "https://github.com/plotdb/pdmaptw/releases/tag/v2.4.0"]}
    (OUTPUT / "SOURCES.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Packed {len(cities)} city units; {len(packed):,} bytes; SHA256 {manifest['sha256']}", flush=True)


if __name__ == "__main__":
    main()
