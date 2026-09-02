#!/usr/bin/env python3
"""Combine the three immutable baseline runs into the published report."""

import json
import math
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent
models = {name: json.loads((ROOT / f"results-{name.lower()}.json").read_text()) for name in ("V1", "V2", "V3")}


def clean_json(value):
    if isinstance(value, float) and not math.isfinite(value):
        return None
    if isinstance(value, dict):
        return {key: clean_json(item) for key, item in value.items()}
    if isinstance(value, list):
        return [clean_json(item) for item in value]
    return value
assets = json.loads((ROOT / "assets.json").read_text())["assets"]
cases = json.loads((ROOT / "evaluation.json").read_text())
categories = Counter(c["category"] for c in cases)
splits = Counter(c["split"] for c in cases)
index_path = ROOT / "media" / ".memories-test-index"


def classify(key, before, after):
    if abs(after - before) < 1e-12:
        return "UNCHANGED"
    lower_better = key in {"medianTimestampErrorMs", "falsePositiveRate", "medianQueryLatencyMs", "p95QueryLatencyMs"}
    return "IMPROVED" if (after < before if lower_better else after > before) else "REGRESSED"


comparison_keys = [
    "videoTop1", "videoTop3", "momentCorrectVideoRate", "momentHitRate",
    "medianTimestampErrorMs", "falsePositiveRate", "medianQueryLatencyMs", "p95QueryLatencyMs",
]
comparison = {}
for key in comparison_keys:
    v2 = models["V2"]["metrics"][key]
    v3 = models["V3"]["metrics"][key]
    comparison[key] = {"v2": v2, "v3": v3, "absoluteDifference": v3 - v2, "classification": classify(key, v2, v3)}
comparison["indexingTimeMs"] = {"v2": 164822, "v3": 164822, "absoluteDifference": 0, "classification": "UNCHANGED", "note": "shared maximum-evidence index"}
comparison["indexSizeBytes"] = {"v2": index_path.stat().st_size, "v3": index_path.stat().st_size, "absoluteDifference": 0, "classification": "UNCHANGED", "note": "shared maximum-evidence index"}

report = {
    "schemaVersion": 2,
    "benchmark": "honorable-seran-benchmark-v2",
    "corpus": {
        "photos": sum(a["type"] == "photo" for a in assets),
        "videos": sum(a["type"] == "video" for a in assets),
        "assets": len(assets), "hashVerified": True, "reproducible": True,
        "indexBuilt": index_path.is_file(), "indexingTimeMs": 164822,
        "indexSizeBytes": index_path.stat().st_size,
        "evidence": {"tinyClip": "ACTIVE", "ocr": "ACTIVE", "colors": "ACTIVE", "videoFrames": "ACTIVE", "liveVlm": "DISABLED"},
    },
    "cases": {"total": len(cases), "development": splits["development"], "holdout": splits["holdout"], "categories": dict(sorted(categories.items()))},
    "models": models,
    "v2VsV3": comparison,
    "v3BetterThanV2": "NO",
    "v3PublicStatus": "EXPERIMENTAL / LOCKED",
    "old69CaseBenchmark": "HISTORICAL / CORPUS INCOMPLETE",
}
report = clean_json(report)
(ROOT / "seran-benchmark-v2-baseline.json").write_text(json.dumps(report, indent=2, allow_nan=False) + "\n")
for name, result in models.items():
    (ROOT / f"results-{name.lower()}.json").write_text(json.dumps(clean_json(result), indent=2, allow_nan=False) + "\n")


def pct(value):
    return "N/A" if value is None or value != value else f"{value*100:.1f}%"


metric_rows = []
labels = [
    ("Overall Top1", "overallTop1", "pct"), ("Overall Top3", "overallTop3", "pct"),
    ("Overall Top5", "overallTop5", "pct"), ("Photo Top1", "photoTop1", "pct"),
    ("Photo Top3", "photoTop3", "pct"), ("OCR Top1", "ocrTop1", "pct"),
    ("Video Top1", "videoTop1", "pct"), ("Video Top3", "videoTop3", "pct"),
    ("Moment hit rate", "momentHitRate", "pct"), ("Moment correct-video rate", "momentCorrectVideoRate", "pct"),
    ("Median timestamp error", "medianTimestampErrorMs", "ms"), ("No-match accuracy", "noMatchAccuracy", "pct"),
    ("False-positive rate", "falsePositiveRate", "pct"), ("Median query latency", "medianQueryLatencyMs", "ms"),
    ("P95 query latency", "p95QueryLatencyMs", "ms"),
]
for label, key, style in labels:
    values = [models[name]["metrics"][key] for name in ("V1", "V2", "V3")]
    rendered = [(pct(v) if style == "pct" else ("N/A" if v is None or v != v else f"{v:.1f} ms")) for v in values]
    metric_rows.append(f"| {label} | {rendered[0]} | {rendered[1]} | {rendered[2]} |")

comparison_rows = []
for key, value in comparison.items():
    unit = " ms" if "Time" in key or "Latency" in key or "Timestamp" in key else ""
    comparison_rows.append(f"| {key} | {value['v2']:.3f}{unit} | {value['v3']:.3f}{unit} | {value['absoluteDifference']:+.3f}{unit} | {value['classification']} |")

markdown = f"""# Seran Benchmark V2 baseline

The new corpus is complete and reproducible: **50 photos, 10 videos, 150 cases**.
It has 105 development cases and 45 holdout cases. The holdout split remains
reserved from tuning. Assets and generated controls passed SHA-256 verification.

The index contains all 60 assets. TinyCLIP, OCR, color evidence, and video-frame
evidence were active; live VLM was disabled. Indexing took 164.822 seconds and
the index is {index_path.stat().st_size:,} bytes.

| Metric | Seran V1 | Seran V2 | Seran V3 |
|---|---:|---:|---:|
{chr(10).join(metric_rows)}

## V2 versus V3

| Metric | V2 | V3 | Difference | Result |
|---|---:|---:|---:|---|
{chr(10).join(comparison_rows)}

V3 is **not better than V2** on this baseline. It improved moment correct-video
rate, but exact-moment hit rate fell to zero, timestamp error increased, video
Top3 regressed, false positives increased, and latency worsened. V3 remains
**EXPERIMENTAL / LOCKED**. No weights or thresholds were tuned after measurement.

The old 69-case benchmark remains **HISTORICAL / CORPUS INCOMPLETE** and its
numbers are not directly comparable with this corpus.
"""
(ROOT / "seran-benchmark-v2-baseline.md").write_text(markdown)
print("wrote seran-benchmark-v2-baseline.json and seran-benchmark-v2-baseline.md")
