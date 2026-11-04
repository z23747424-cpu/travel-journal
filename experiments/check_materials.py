#!/usr/bin/env python3
"""Offline independent sanity checks for course evaluation materials."""
import json
import math
import tempfile
from pathlib import Path

from evaluate import (
    ROOT, CATALOG, FROZEN_INPUTS, baseline, check_freeze,
    check_model_results, read_json, recommendation_metrics,
    recommendation_request, retrieval_metrics, retrieval_request, validate,
)
from run_model import endpoint, parse_content


def expect_error(fn):
    try:
        fn()
    except (ValueError, AssertionError):
        return
    raise AssertionError("expected a validation error")


def main():
    hashes = check_freeze()
    journals, queries, cases = [read_json(ROOT / n) for n in FROZEN_INPUTS]
    destinations = read_json(CATALOG)
    counts = validate(journals, queries, cases, destinations)

    # Recompute IDs only, preserving the first measured artifact and its timing.
    stored = read_json(ROOT / "baseline_results.json")
    assert stored["inputSha256"] == hashes and stored["model"] is None
    replay = baseline(journals, queries, cases, destinations)
    for key, id_field in (("retrieval", "queryId"), ("recommendation", "caseId")):
        before = [(r[id_field], r["ids"]) for r in stored["baseline"][key]]
        after = [(r[id_field], r["ids"]) for r in replay[key]]
        assert before == after, f"{key}: nondeterministic or stale baseline"

    # Hand-worked metric example: 2 positives, top1=1/2, top3=2/2, FP=1/2.
    toy_queries = [
        {"id": "a", "relevantIds": ["r1"]},
        {"id": "b", "relevantIds": ["r2"]},
        {"id": "c", "relevantIds": []},
        {"id": "d", "relevantIds": []},
    ]
    toy_rows = [
        {"queryId": "a", "ids": ["r1"], "latencyMs": None},
        {"queryId": "b", "ids": ["x", "r2"], "latencyMs": None},
        {"queryId": "c", "ids": ["x"], "latencyMs": None},
        {"queryId": "d", "ids": [], "latencyMs": None},
    ]
    metrics = retrieval_metrics(toy_queries, toy_rows)
    assert metrics["hitAt1"] == .5 and metrics["hitAt3"] == 1
    assert metrics["noMatchFalsePositiveRate"] == .5 and metrics["meanLatencyMs"] is None

    # One returned recommendation uses one of three slots: fixed-slot precision=1/3.
    toy_case = {"id": "a", "relevantIds": ["r1"], "region": "浙江", "excludeRegions": [], "mustNotTags": [], "excludedIds": []}
    toy_dest = [{"id": "r1", "region": "浙江省", "tags": ["水乡"]}]
    toy_rec = [{"caseId": "a", "ids": ["r1"], "items": [], "latencyMs": None}]
    metrics = recommendation_metrics([toy_case], toy_rec, toy_dest)
    assert math.isclose(metrics["top3PrecisionFixedSlots"], 1/3)
    assert metrics["returnedItemSuitability"] == 1 and metrics["knownConstraintViolationCount"] == 0

    # Shared input contains neither relevance labels nor grading notes.
    for q in queries:
        payload = retrieval_request(q, journals)
        assert "relevantIds" not in json.dumps(payload) and "annotationNote" not in json.dumps(payload)
        assert all(set(j) == {"id", "title", "place", "date", "tags", "photoDescription", "body"} for j in payload["records"])
    for c in cases:
        payload = recommendation_request(c, destinations)
        assert "relevantIds" not in json.dumps(payload) and "annotationNote" not in json.dumps(payload)
        assert payload["knownConditions"]["preferredTags"] == c["preferredTags"]

    assert endpoint("https://example.invalid/v1/") == "https://example.invalid/v1/chat/completions"
    assert endpoint("https://example.invalid/v1/chat/completions") == "https://example.invalid/v1/chat/completions"
    expect_error(lambda: endpoint("http://example.invalid/v1"))
    expect_error(lambda: endpoint("https://user:secret@example.invalid/v1"))
    assert parse_content('{"items":[]}', {"r1"}) == []
    expect_error(lambda: parse_content('{"items":[{"id":"unknown","reason":"text"}]}', {"r1"}))
    expect_error(lambda: parse_content('not json', {"r1"}))

    # A partial external run cannot be silently scored as complete.
    expect_error(lambda: check_model_results({"inputSha256": hashes, "retrieval": [], "recommendation": []}, queries, cases, journals, destinations, hashes))
    print(json.dumps({"status": "PASS", "counts": counts, "checks": ["frozen-input-hashes", "catalog-and-label-references", "actual-baseline-replay", "hand-worked-metric-examples", "label-free-shared-inputs", "endpoint-and-ID-validation", "incomplete-run-rejection"], "networkCalls": 0}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
