#!/usr/bin/env python3
"""Build offline county-level material for the province drill-down map.

The Android app never downloads this: the packed file ships in assets/atlas.
Each province is stored as a list of second-level units (districts / counties /
county-level cities). Provinces whose atlas unit is already county-sized
(Taiwan, Hong Kong, Macao) reuse the existing city unit geometry.

Boundary simplification is topology-preserving: inside one province every ring is
cut into chains at shared vertices, each unique chain is simplified once, and the
same simplified chain is stitched back into both neighbours. Simplifying rings one
by one (the previous approach) let the two sides of a shared border drift apart,
which is what made the county map look crossed / overlapping.
"""
from __future__ import annotations

import collections
import concurrent.futures
import hashlib
import json
from pathlib import Path
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / ".tools" / "county-boundaries"
ATLAS = ROOT / "app/src/main/assets/atlas"
OUTPUT = ATLAS / "china-counties.json"
TOLERANCE = 0.003
GRID = 1_000_000  # topology is matched on a 1e-6 degree lattice (about 11 cm)
DIRECT = {"110000", "120000", "310000", "500000"}
REUSE = {"710000", "810000", "820000"}


def simplify(points: list, tolerance: float) -> list:
    """Douglas-Peucker on a chain of (x, y) degrees; endpoints are always kept."""
    if len(points) < 3:
        return points
    keep = {0, len(points) - 1}
    todo = [(0, len(points) - 1)]
    limit = tolerance * tolerance
    while todo:
        first, last = todo.pop()
        ax, ay = points[first]
        bx, by = points[last]
        dx, dy = bx - ax, by - ay
        length = dx * dx + dy * dy
        largest, index = limit, -1
        for i in range(first + 1, last):
            px, py = points[i]
            if length:
                t = ((px - ax) * dx + (py - ay) * dy) / length
                t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)
            else:
                t = 0.0
            ex, ey = px - ax - t * dx, py - ay - t * dy
            distance = ex * ex + ey * ey
            if distance > largest:
                largest, index = distance, i
        if index >= 0:
            keep.add(index)
            todo.extend(((first, index), (index, last)))
    return [points[i] for i in sorted(keep)]


def _chains(ring: tuple, degree: dict) -> list:
    """Cut a closed ring into chains that meet only at shared vertices."""
    body = ring[:-1]
    nodes = [i for i, point in enumerate(body) if degree[point] != 2]
    if not nodes:
        return [(ring, True)]
    cuts = []
    for position, start in enumerate(nodes):
        end = nodes[(position + 1) % len(nodes)]
        if end > start:
            segment = ring[start:end + 1]
        else:
            segment = ring[start:] + ring[1:end + 1]
        cuts.append((segment, True))
    return cuts


def _canonical(chain: tuple) -> tuple:
    flipped = chain[::-1]
    return chain if chain <= flipped else flipped


def topology_simplify(geometries: list, tolerance: float) -> list:
    """geometries: list of raw GeoJSON geometry dicts of one province.

    Returns a same-length list of simplified geometry dicts whose shared borders
    are byte-identical on both sides.
    """
    rings = []        # (unit index, polygon index, ring index, quantized closed ring)
    edges = set()
    degree = collections.Counter()
    for unit_index, geometry in enumerate(geometries):
        polygons = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
        for polygon_index, polygon in enumerate(polygons):
            for ring_index, ring in enumerate(polygon):
                quantized = [(round(p[0] * GRID), round(p[1] * GRID)) for p in ring]
                cleaned = [quantized[0]]
                for point in quantized[1:]:
                    if point != cleaned[-1]:
                        cleaned.append(point)
                if len(cleaned) < 4:
                    continue
                if cleaned[0] != cleaned[-1]:
                    cleaned.append(cleaned[0])
                rings.append((unit_index, polygon_index, ring_index, tuple(cleaned)))
                for a, b in zip(cleaned, cleaned[1:]):
                    edge = (a, b) if a < b else (b, a)
                    if edge not in edges:
                        edges.add(edge)
                        degree[a] += 1
                        degree[b] += 1

    simplified: dict = {}
    ledgers = {}      # (unit, polygon, ring) -> [(chain key, forward)]
    shared = collections.Counter()
    for unit_index, polygon_index, ring_index, ring in rings:
        pieces = _chains(ring, degree)
        record = []
        for chain, forward in pieces:
            if len(chain) < 2:
                record.append((chain, True))
                continue
            if chain[0] == chain[-1]:
                key, forward = chain, True
            else:
                key = _canonical(chain)
                forward = chain == key
            record.append((key, forward))
            if key not in simplified:
                degrees = [(point[0] / GRID, point[1] / GRID) for point in key]
                simplified[key] = simplify(degrees, tolerance)
            shared[key] += 1
        ledgers[(unit_index, polygon_index, ring_index)] = record

    out = []
    for unit_index, geometry in enumerate(geometries):
        polygons = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
        result = []
        for polygon_index, polygon in enumerate(polygons):
            rebuilt = []
            for ring_index in range(len(polygon)):
                record = ledgers.get((unit_index, polygon_index, ring_index))
                if not record:
                    continue
                points = []
                for key, forward in record:
                    piece = simplified.get(key)
                    if piece is None:
                        piece = [(point[0] / GRID, point[1] / GRID) for point in key]
                    sequence = piece if forward else piece[::-1]
                    sequence = [(round(x, 4), round(y, 4)) for x, y in sequence]
                    for point in sequence:
                        if not points or points[-1] != point:
                            points.append(point)
                if len(points) >= 2 and points[0] != points[-1]:
                    points.append(points[0])
                if len(points) < 4 or _area(points) <= 1e-9:
                    continue
                rebuilt.append([[x, y] for x, y in points])
            if rebuilt:
                result.append(rebuilt)
        if not result:
            result = [[[[p[0], p[1]] for p in ring]] for poly in (
                geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
            ) for ring in poly[:1]]
        kind = "Polygon" if len(result) == 1 else "MultiPolygon"
        out.append({"type": kind, "coordinates": result[0] if kind == "Polygon" else result})
    print(f"    topology: {len(rings)} rings, {len(simplified)} unique chains, "
          f"{sum(1 for c in shared.values() if c > 1)} shared", flush=True)
    return out


def _area(ring: list) -> float:
    total = 0.0
    for (ax, ay), (bx, by) in zip(ring, ring[1:]):
        total += ax * by - bx * ay
    return abs(total) / 2


def fetch(url: str, filename: str) -> dict:
    path = CACHE / filename
    if not path.is_file():
        last = None
        for attempt in range(3):
            try:
                request = urllib.request.Request(url, headers={"User-Agent": "TravelJournal-course-material/1.3"})
                with urllib.request.urlopen(request, timeout=40) as response:
                    data = response.read(16 * 1024 * 1024 + 1)
                if len(data) > 16 * 1024 * 1024:
                    raise ValueError("Boundary file too large")
                json.loads(data)
                path.write_bytes(data)
                break
            except Exception as error:  # noqa: BLE001 - network material
                last = error
                if attempt == 2:
                    raise last
                time.sleep(attempt + 1)
    return json.loads(path.read_bytes())


def center_of(source: dict, props: dict) -> list:
    for key in ("centroid", "center", "cp"):
        value = props.get(key)
        if isinstance(value, list) and len(value) >= 2:
            return [value[0], value[1]]
    shape = source["geometry"]
    polygons = shape["coordinates"] if shape["type"] == "MultiPolygon" else [shape["coordinates"]]
    rings = [ring for poly in polygons for ring in poly]
    biggest = max(rings, key=len)
    return [sum(p[0] for p in biggest) / len(biggest), sum(p[1] for p in biggest) / len(biggest)]


def unit(source: dict, code: str, name: str, province_code: str, province_name: str) -> dict:
    props = source.get("properties") or {}
    return {
        "type": "Feature",
        "properties": {"code": code, "name": name, "provinceCode": province_code, "provinceName": province_name,
                       "center": center_of(source, props)},
        "geometry": source["geometry"],
    }


def main() -> None:
    CACHE.mkdir(parents=True, exist_ok=True)
    city_atlas = json.loads((ATLAS / "china-cities.json").read_text(encoding="utf-8"))
    provinces = [f for f in city_atlas["provinces"] if f["properties"].get("name")]
    cities = {}
    for feature in city_atlas["cities"]:
        props = feature["properties"]
        cities[props["code"]] = props

    plan_targets = []
    for province in provinces:
        props = province["properties"]
        code = str(props["adcode"])
        if code in REUSE:
            continue
        if code in DIRECT:
            plan_targets.append((code, code))
            continue
        for city_code, city_props in cities.items():
            if city_props["provinceCode"] == code:
                plan_targets.append((code, city_code))
    # Municipalities are also their own city unit in the atlas, so they appear in
    # both loops above; de-duplicate while keeping order.
    plan_targets = list(dict.fromkeys(plan_targets))
    print(f"Fetching districts for {len(plan_targets)} parent units", flush=True)

    def children(target: tuple[str, str]) -> tuple[str, list, str]:
        province_code, parent_code = target
        data = fetch(f"https://geo.datav.aliyun.com/areas_v3/bound/{parent_code}_full.json", f"{parent_code}.json")
        name_by_code = cities.get(parent_code, {}).get("provinceName", "")
        found = []
        for feature in data.get("features", []):
            props = feature.get("properties") or {}
            if not props.get("name"):
                continue
            found.append((str(props["adcode"]), props["name"], feature))
        return province_code, found, name_by_code

    collected: dict[str, list] = {str(p["properties"]["adcode"]): [] for p in provinces}
    errors = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        futures = {pool.submit(children, target): target for target in plan_targets}
        for done, future in enumerate(concurrent.futures.as_completed(futures), 1):
            target = futures[future]
            province_code, parent_code = target
            province_name = next((c["provinceName"] for c in cities.values() if c["provinceCode"] == province_code), "")
            try:
                _, found, parent_province_name = future.result()
                province_name = parent_province_name or province_name
                if not found:
                    raise ValueError("no child units")
                for code, name, feature in found:
                    collected[province_code].append(unit(feature, code, name, province_code, province_name))
            except Exception as error:  # noqa: BLE001 - some units have no child geometry
                errors.append(f"{parent_code}: {error}")
                if parent_code in cities:
                    fallback = next(c for c in city_atlas["cities"] if c["properties"]["code"] == parent_code)
                    collected[province_code].append(unit(fallback, parent_code, cities[parent_code]["name"], province_code, province_name))
            if done % 40 == 0:
                print(f"{done}/{len(plan_targets)} parents", flush=True)

    # Reused provinces keep their existing (already county-sized) atlas units.
    for province_code in REUSE:
        for feature in city_atlas["cities"]:
            props = feature["properties"]
            if props["provinceCode"] == province_code:
                collected.setdefault(province_code, []).append(feature)

    # One file per province keeps the phone's parse cost and peak memory to a single
    # province's geometry; the index carries names/centres only, for search and AI hints.
    out_dir = ATLAS / "counties"
    out_dir.mkdir(parents=True, exist_ok=True)
    for stale in out_dir.glob("*.json"):
        stale.unlink()
    built_on = time.strftime("%Y-%m-%d")
    province_meta = []
    index_units = []
    total_units = 0
    total_bytes = 0
    for province in provinces:
        props = province["properties"]
        code = str(props["adcode"])
        name = props["name"]
        units = collected.get(code) or []
        if not units:
            raise ValueError(f"No second-level units for {name}")
        units.sort(key=lambda feature: str(feature["properties"]["code"]))
        shapes = topology_simplify([feature["geometry"] for feature in units], TOLERANCE)
        packed_units = []
        for feature, shape in zip(units, shapes):
            entry = dict(feature)
            entry["geometry"] = shape
            packed_units.append(entry)
        total_units += len(packed_units)
        payload = {"version": 1, "provinceCode": code, "provinceName": name, "tolerance": TOLERANCE,
                   "units": packed_units}
        packed = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode()
        (out_dir / f"{code}.json").write_bytes(packed)
        total_bytes += len(packed)
        province_meta.append({"code": code, "name": name, "units": len(packed_units),
                              "bytes": len(packed), "sha256": hashlib.sha256(packed).hexdigest()})
        index_units.extend({"code": str(feature["properties"]["code"]), "name": feature["properties"]["name"],
                            "provinceCode": code, "provinceName": name,
                            "center": feature["properties"]["center"]} for feature in packed_units)

    index_payload = {"version": 1, "builtOn": built_on, "units": index_units}
    index_packed = json.dumps(index_payload, ensure_ascii=False, separators=(",", ":")).encode()
    (ATLAS / "county-index.json").write_bytes(index_packed)
    OUTPUT.unlink(missing_ok=True)
    manifest = {
        "file": "county-index.json + counties/{provinceCode}.json",
        "builtOn": built_on,
        "provinceUnits": len(province_meta),
        "secondLevelUnits": total_units,
        "indexSha256": hashlib.sha256(index_packed).hexdigest(),
        "indexBytes": len(index_packed),
        "geometryBytes": total_bytes,
        "provinces": province_meta,
        "simplificationToleranceDegrees": TOLERANCE,
        "simplification": "Topology-preserving: rings are cut into chains at shared vertices, each unique chain is "
                          "simplified once and stitched back into every county that uses it, so borders never cross.",
        "coverage": "District / county / county-level city units per province; units without child geometry fall back to the parent city outline.",
        "purpose": "Course travel visualization, not navigation, surveying or legal boundary determination.",
        "attribution": ["DataV.GeoAtlas (https://geo.datav.aliyun.com/areas_v3/bound/), geographic material from Amap Open Platform, for learning/exchange.",
                        "Taiwan / Hong Kong / Macao reuse the county-sized units of china-cities.json."],
        "errors": errors,
    }
    (ATLAS / "SOURCES-counties.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Packed {total_units} second-level units in {len(province_meta)} provinces; "
          f"index {len(index_packed):,} bytes; geometry {total_bytes:,} bytes; SHA256 {manifest['indexSha256']}", flush=True)
    if errors:
        print(f"{len(errors)} parent units fell back to the city outline", flush=True)


if __name__ == "__main__":
    main()