#!/usr/bin/env python3
"""Deterministic teaching evaluation. Python standard library only."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import statistics
import time
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CATALOG = ROOT / "destinations.json"  # Frozen v1.0 experiment only; not bundled with the app.
FROZEN_INPUTS = ("journals.json", "retrieval_queries.json", "recommendation_cases.json")
FIELDS = ("title", "place", "date", "tags", "photoDescription", "body")


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def input_hashes():
    return {name: digest(ROOT / name) for name in FROZEN_INPUTS} | {"destinations.json": digest(CATALOG)}


def check_freeze():
    expected = read_json(ROOT / "dataset_manifest.json")["sha256"]
    actual = input_hashes()
    if actual != expected:
        raise ValueError("数据或标注已改变。保留旧数据与旧报告，创建新版本并重新独立标注；禁止覆盖冻结清单。")
    return actual


def norm(text):
    return unicodedata.normalize("NFKC", str(text)).lower()


def journal_fields(j):
    """Both methods receive only these fields, with identical truncation."""
    return {
        "id": j["id"],
        "title": str(j["title"])[:300],
        "place": str(j["place"])[:300],
        "date": str(j["date"]),
        "tags": [str(t)[:60] for t in j["tags"][:30]],
        "photoDescription": str(j["photoDescription"])[:1000],
        "body": str(j["body"])[:800],
    }


def query_terms(query):
    # All teaching samples use BMP Han. This mirrors Search.kt for these inputs.
    chunks = re.findall(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]+|[a-z0-9]+", norm(query).strip())
    terms = []
    for chunk in chunks:
        values = [chunk[i:i+2] for i in range(len(chunk)-1)] if ord(chunk[0]) > 127 and len(chunk) > 1 else [chunk]
        terms.extend(values)
    return list(dict.fromkeys(terms))


def local_search(query, journals):
    """Same 5/4/4/1 weights, phrase bonus and tie order as app Search.kt."""
    query = norm(query).strip()
    terms = query_terms(query)
    if not terms:
        return []
    ranked = []
    for j0 in journals:
        j = journal_fields(j0)
        title, place = norm(j["title"]), norm(j["place"])
        tags = norm(" ".join(j["tags"]))
        content = norm(f'{j["body"]} {j["photoDescription"]} {j["date"]}')
        score = 0
        for t in terms:
            score += (5 if t in title else 0) + (4 if t in place else 0)
            score += (4 if t in tags else 0) + (1 if t in content else 0)
        if score:
            score += 8 if query in f"{title} {place} {tags} {content}" else 0
            ranked.append((j["id"], score, j["date"]))
    # Stable ID ascending, date descending, score descending.
    ranked.sort(key=lambda x: x[0])
    ranked.sort(key=lambda x: x[2], reverse=True)
    ranked.sort(key=lambda x: x[1], reverse=True)
    return [row[0] for row in ranked]


def destination_fields(d):
    # Labels, expected IDs and annotation notes never enter a model prompt.
    return {k: d[k] for k in ("id", "name", "region", "tags", "summary", "days", "season", "sourceUrl", "checkedAt")}


def allowed(d, case):
    return (
        (not case["region"] or case["region"] in d["region"])
        and not any(r in d["region"] for r in case["excludeRegions"])
        and not set(case["mustNotTags"]).intersection(d["tags"])
        and d["id"] not in case["excludedIds"]
    )


def tag_recommend(case, destinations):
    """Structured-tag baseline; known filters only, no inferred live claims."""
    out = []
    for d in destinations:
        if not allowed(d, case):
            continue
        score = len(set(case["preferredTags"]).intersection(d["tags"]))
        if score:
            out.append((d["id"], score))
    out.sort(key=lambda row: (-row[1], row[0]))
    return [row[0] for row in out[:3]]


def retrieval_request(q, journals):
    return {"query": q["query"], "records": [journal_fields(j) for j in journals]}


def recommendation_request(case, destinations):
    return {
        "query": case["query"],
        "knownConditions": {k: case[k] for k in ("preferredTags", "region", "mustNotTags", "excludeRegions", "excludedIds")},
        "candidates": [destination_fields(d) for d in destinations],
    }


def validate(journals, queries, cases, destinations):
    expected_fields = {"id", "name", "region", "tags", "summary", "days", "season", "sourceUrl", "checkedAt"}
    for collection in (journals, queries, cases, destinations):
        ids = [x["id"] for x in collection]
        assert len(ids) == len(set(ids)), "duplicate ID"
    jids, dids = {j["id"] for j in journals}, {d["id"] for d in destinations}
    assert 30 <= len(journals) <= 50 and len(queries) >= 30 and len(cases) >= 20
    assert len(destinations) == 30
    for j in journals:
        assert j["example"] is True and j["photos"] == []
        assert len(j["body"]) <= 800
    for q in queries:
        assert set(q["relevantIds"]) <= jids
    for c in cases:
        assert set(c["relevantIds"]) <= dids
        assert set(c["excludedIds"]) <= dids
        assert all(allowed(d, c) for d in destinations if d["id"] in c["relevantIds"]), "label violates explicit filter"
    for d in destinations:
        assert set(d) == expected_fields
        assert isinstance(d["tags"], list) and d["tags"] and all(isinstance(x, str) for x in d["tags"])
        assert all(isinstance(v, str) for k, v in d.items() if k != "tags")
        assert d["checkedAt"] == "2026-11-04"
        assert d["sourceUrl"].startswith(("https://", "http://"))
    return {"journals": len(journals), "queries": len(queries), "noMatchQueries": sum(not q["relevantIds"] for q in queries), "recommendationCases": len(cases), "destinations": len(destinations)}


def retrieval_metrics(queries, rows):
    by_id = {r["queryId"]: r for r in rows}
    positive = [q for q in queries if q["relevantIds"]]
    negatives = [q for q in queries if not q["relevantIds"]]
    def ids(q):
        return by_id.get(q["id"], {}).get("ids", [])
    return {
        "positiveQueryCount": len(positive),
        "hitAt1": sum(bool(set(ids(q)[:1]) & set(q["relevantIds"])) for q in positive) / len(positive),
        "hitAt3": sum(bool(set(ids(q)[:3]) & set(q["relevantIds"])) for q in positive) / len(positive),
        "noMatchQueryCount": len(negatives),
        "noMatchFalsePositiveCount": sum(bool(ids(q)) for q in negatives),
        "noMatchFalsePositiveRate": sum(bool(ids(q)) for q in negatives) / len(negatives),
        "missingResultCount": sum(q["id"] not in by_id for q in queries),
        "failureCount": sum(bool(r.get("error")) for r in rows),
        "failureRate": sum(bool(r.get("error")) for r in rows) / len(queries),
        "meanLatencyMs": mean_latency(rows),
    }


def recommendation_metrics(cases, rows, destinations):
    by_id, db = {r["caseId"]: r for r in rows}, {d["id"]: d for d in destinations}
    positive = [c for c in cases if c["relevantIds"]]
    negative = [c for c in cases if not c["relevantIds"]]
    def ids(c):
        return by_id.get(c["id"], {}).get("ids", [])[:3]
    # Fixed denominator 3: returning one item does not masquerade as full top-3 precision.
    def hit(c):
        return len(set(ids(c)) & set(c["relevantIds"]))
    returned = [(c, item) for c in cases for item in ids(c)]
    violation = sum(item not in db or not allowed(db[item], c) for c, item in returned)
    judged = []
    for r in rows:
        for item in r.get("items", []):
            decision = item.get("explanationSupported")
            if decision is not None:
                assert isinstance(decision, bool)
                judged.append(decision)
    return {
        "positiveCaseCount": len(positive),
        "top3HitRate": sum(hit(c) > 0 for c in positive) / len(positive),
        "top3PrecisionFixedSlots": sum(hit(c) / 3 for c in positive) / len(positive),
        "returnedItemSuitability": sum(hit(c) for c in positive) / sum(len(ids(c)) for c in positive) if any(ids(c) for c in positive) else None,
        "noCandidateCaseCount": len(negative),
        "noCandidateFalsePositiveCount": sum(bool(ids(c)) for c in negative),
        "knownConstraintViolationCount": violation,
        "knownConstraintViolationRate": violation / len(returned) if returned else 0.0,
        "returnedItemCount": len(returned),
        "explanationSupportJudgedCount": len(judged),
        "explanationSupportRate": sum(judged) / len(judged) if judged else None,
        "failureCount": sum(bool(r.get("error")) for r in rows),
        "failureRate": sum(bool(r.get("error")) for r in rows) / len(cases),
        "meanLatencyMs": mean_latency(rows),
        "missingResultCount": sum(c["id"] not in by_id for c in cases),
    }


def mean_latency(rows):
    values = [r["latencyMs"] for r in rows if r.get("latencyMs") is not None]
    return statistics.mean(values) if values else None


def check_model_results(model, queries, cases, journals, destinations, hashes):
    assert model["inputSha256"] == hashes, "Model result uses a different data version"
    collections = (("retrieval", queries, journals, "queryId"), ("recommendation", cases, destinations, "caseId"))
    for key, expected, candidates, field in collections:
        rows = model.get(key, [])
        row_ids = [r[field] for r in rows]
        assert len(row_ids) == len(set(row_ids)), "Duplicate result rows"
        assert set(row_ids) == {r["id"] for r in expected}, "A complete run is required; missing cases cannot be silently omitted"
        valid = {d["id"] for d in candidates}
        for row in rows:
            assert isinstance(row["ids"], list) and all(isinstance(x, str) for x in row["ids"])
            assert len(row["ids"]) == len(set(row["ids"]))
            assert set(row["ids"]) <= valid, "Unknown result ID; invalid response must be recorded as an error with empty IDs"
            assert row.get("latencyMs") is None or row["latencyMs"] >= 0
            if row.get("error"):
                assert not row["ids"], "Failed calls must not invent predictions"


def baseline(journals, queries, cases, destinations):
    retrieval, recommendation = [], []
    for q in queries:
        start = time.perf_counter()
        ids = local_search(q["query"], journals)
        retrieval.append({"queryId": q["id"], "ids": ids, "latencyMs": (time.perf_counter()-start)*1000, "error": None})
    for c in cases:
        start = time.perf_counter()
        ids = tag_recommend(c, destinations)
        items = []
        for id_ in ids:
            d = next(d for d in destinations if d["id"] == id_)
            overlap = sorted(set(c["preferredTags"]) & set(d["tags"]))
            items.append({"id": id_, "reason": "资料标签匹配：" + "、".join(overlap), "explanationSupported": True})
        recommendation.append({"caseId": c["id"], "ids": ids, "items": items, "latencyMs": (time.perf_counter()-start)*1000, "error": None})
    return {"method": "local-keyword-and-structured-tags", "retrieval": retrieval, "recommendation": recommendation}


def markdown_report(report):
    r, d = report["baseline"]["retrievalMetrics"], report["baseline"]["recommendationMetrics"]
    pct = lambda x: f"{x*100:.2f}%"
    return f"""# 教学样例基线实测

数据版本：{report["datasetVersion"]}；执行时间（UTC）：{report["recordedAt"]}。

这是一套开发助手预先起草的虚构教学样例与手工标注，未经独立人工复核，不是真实用户研究，也不是泛化准确率。

## 找回手账

| 方法 | 正例查询 | Hit@1 | Hit@3 | 无匹配误报 |
| --- | ---: | ---: | ---: | ---: |
| 应用同款关键词基线 | {r["positiveQueryCount"]} | {pct(r["hitAt1"])} | {pct(r["hitAt3"])} | {r["noMatchFalsePositiveCount"]}/{r["noMatchQueryCount"]} |
| 用户模型 | 待真实 API 执行 | 待测 | 待测 | 待测 |

基线平均本机计算耗时 {r["meanLatencyMs"]:.3f} ms/查询，失败 {r["failureCount"]} 次。本机短样例耗时不是 Android 耗时，也不能与网络调用总耗时直接等同。

## 目的地推荐

| 方法 | 正例需求 | Top-3 至少一个适合 | Top-3 固定3槽精度 | 返回项目适合度 | 已知条件违反 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 结构化标签基线 | {d["positiveCaseCount"]} | {pct(d["top3HitRate"])} | {pct(d["top3PrecisionFixedSlots"])} | {pct(d["returnedItemSuitability"])} | {d["knownConstraintViolationCount"]}/{d["returnedItemCount"]} |
| 用户模型 | 待真实 API 执行 | 待测 | 待测 | 待测 | 待测 |

资料库外/无候选需求误报：{d["noCandidateFalsePositiveCount"]}/{d["noCandidateCaseCount"]}。基线解释只逐字列出已匹配标签，因此按程序规则可检查出处；这不等于独立评审了自然语言模型解释。模型解释有据程度留待人工逐条复核。预算、实时交通、天气和预约不在已知条件违反率中，r18 必须标为待核对。

明细与数据 SHA-256 见 `baseline_results.json` 和 `dataset_manifest.json`。模型结果不得替代或改写基线。{"" if report.get("model") is None else "本次另有用户模型结果，指标见 JSON 的 model 字段；此表保留初始基线。"}
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-results", type=Path, help="Actual model run JSON, never label-derived")
    parser.add_argument("--output", type=Path, help="New result path; existing files are never overwritten")
    args = parser.parse_args()
    hashes = check_freeze()
    journals, queries, cases = [read_json(ROOT / name) for name in FROZEN_INPUTS]
    destinations = read_json(CATALOG)
    counts = validate(journals, queries, cases, destinations)
    base = baseline(journals, queries, cases, destinations)
    base["retrievalMetrics"] = retrieval_metrics(queries, base["retrieval"])
    base["recommendationMetrics"] = recommendation_metrics(cases, base["recommendation"], destinations)
    from datetime import datetime, timezone
    report = {"datasetVersion": "teaching-v1", "recordedAt": datetime.now(timezone.utc).isoformat(), "inputSha256": hashes, "counts": counts, "baseline": base, "model": None, "modelStatus": "待用户配置 API 后真实执行"}
    if args.model_results:
        model = read_json(args.model_results)
        check_model_results(model, queries, cases, journals, destinations, hashes)
        model["retrievalMetrics"] = retrieval_metrics(queries, model["retrieval"])
        model["recommendationMetrics"] = recommendation_metrics(cases, model["recommendation"], destinations)
        report["model"] = model
        report["modelStatus"] = "读取实际结果文件；解释有据程度需独立人工复核"
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S-%f")
    initial = ROOT / "baseline_results.json"
    output = args.output or (ROOT / f"model_comparison-{stamp}.json" if args.model_results else (initial if not initial.exists() else ROOT / f"baseline_recheck-{stamp}.json"))
    markdown_path = None
    if args.model_results is None:
        initial_markdown = ROOT / "基线实测.md"
        markdown_path = initial_markdown if not initial_markdown.exists() else output.with_suffix(".md")
    if output.exists() or markdown_path is not None and (markdown_path.exists() or markdown_path.resolve() == output.resolve()):
        parser.error("JSON或Markdown输出路径已存在或冲突，请使用全新的 --output .json 文件名")
    write_json(output, report)
    # Explicitly preserve the initial baseline report when evaluating a model.
    if markdown_path is not None:
        markdown_path.write_text(markdown_report(report), encoding="utf-8")
    print(json.dumps({"counts": counts, "retrieval": base["retrievalMetrics"], "recommendation": base["recommendationMetrics"], "modelStatus": report["modelStatus"], "output": str(output)}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
