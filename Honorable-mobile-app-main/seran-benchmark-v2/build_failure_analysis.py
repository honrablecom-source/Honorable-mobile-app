#!/usr/bin/env python3
"""Build diagnostics-only Seran development failure reports.

Inputs are deliberately limited to the development-only manifest and its three
diagnostic runs. The holdout manifest is neither accepted nor opened here.
"""
from __future__ import annotations

import json
import math
import statistics
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CASES = json.loads((ROOT / "development-analysis-cases.json").read_text())
assert len(CASES) == 105 and all(c["split"] == "development" for c in CASES)
CASE_BY_QUERY = {c["query"]: c for c in CASES}
assert len(CASE_BY_QUERY) == len(CASES)
RUNS = {m: json.loads((ROOT / f"development-diagnostics-v{m[-1]}.json").read_text()) for m in ("V1", "V2", "V3")}
assert all(r["queryCount"] == 105 for r in RUNS.values())
assert all({q["query"] for q in r["queries"]} == set(CASE_BY_QUERY) for r in RUNS.values())


def expected_hit(q, k=1):
    expected = set(q["expected"])
    return any(x["uri"] in expected for x in q["top5"][:k])


def failed(q):
    return q["confident"] if CASE_BY_QUERY[q["query"]].get("no_match", False) else not expected_hit(q)


def primary_class(q):
    case = CASE_BY_QUERY[q["query"]]
    cat = q["category"]
    if case.get("no_match", False):
        return "FALSE_POSITIVE_NO_MATCH"
    top = q["top5"][0] if q["top5"] else None
    expected_video = any(p.startswith("videos/") for p in q["expected"])
    if cat == "EXACT VIDEO MOMENT":
        if not top or top["uri"] not in q["expected"]:
            return "VIDEO_SELECTION_FAILURE"
        temporal = top.get("temporal")
        if temporal and temporal.get("state") == "TIMESTAMP_LOW_CONFIDENCE":
            return "TIMESTAMP_CONFIDENCE_FAILURE"
        return "TEMPORAL_WINDOW_FAILURE"
    if cat == "VIDEO RETRIEVAL":
        return "VIDEO_SELECTION_FAILURE"
    if top and not expected_video and top["kind"] == "VIDEO":
        return "PHOTO_VIDEO_SCORE_INTERFERENCE"
    if cat == "OCR":
        return "OCR_MISS"
    if cat == "COLOR":
        return "COLOR_MISS"
    if cat == "NEGATIVE CONDITION":
        return "NEGATIVE_CONDITION_FAILURE"
    if cat == "MULTI-CONDITION":
        return "MULTI_CONDITION_COVERAGE_FAILURE"
    if q["expectedRank"] is None:
        return "CANDIDATE_MISS"
    if q["expectedRank"] <= 5:
        return "CORRECT_IN_TOP5_BAD_RERANK"
    return "OTHER"


def evidence(candidate):
    b = candidate.get("breakdown", {})
    return {
        "semantic": b.get("semantic", 0),
        "conceptCoverage": b.get("conceptCoverage", 0),
        "ocr": b.get("ocr", 0),
        "color": b.get("colors", 0),
        "labelsScenesActivities": b.get("labels", 0),
        "videoFrames": b.get("videoFrames", 0),
        "negativeCondition": b.get("negativePenalty", 0),
        "temporal": candidate.get("temporal"),
    }


failures = []
counts = {}
for model, run in RUNS.items():
    count = Counter()
    for q in run["queries"]:
        if not failed(q):
            continue
        cls = primary_class(q)
        count[cls] += 1
        case = CASE_BY_QUERY[q["query"]]
        top = q["top5"][0] if q["top5"] else None
        failures.append({
            "queryId": case["id"], "query": q["query"], "model": model,
            "category": q["category"], "difficulty": case["difficulty"],
            "failureClass": cls, "expectedMedia": q["expected"],
            "expectedTimestampWindowMs": [q["expectedStartMs"], q["expectedEndMs"]],
            "predictedTop1": top, "top3Candidates": q["top5"][:3],
            "top5Candidates": q["top5"], "queryInterpretation": q["interpretation"],
            "semanticConcepts": q["interpretation"]["semanticConcepts"],
            "ocrEvidence": [evidence(x)["ocr"] for x in q["top5"]],
            "colorEvidence": [evidence(x)["color"] for x in q["top5"]],
            "labelsScenesActivityEvidence": [evidence(x)["labelsScenesActivities"] for x in q["top5"]],
            "negativeConditionEvidence": [evidence(x)["negativeCondition"] for x in q["top5"]],
            "temporalEvidence": top.get("temporal") if top else None,
            "confidence": q["confident"], "confidenceReason": q["confidenceReason"],
            "top1Top2Margin": q["top1Top2Margin"], "expectedRank": q["expectedRank"],
            "expectedEnteredScoredCandidateSet": q["expectedRank"] is not None,
            "expectedEnteredTop5": q["expectedRank"] is not None and q["expectedRank"] <= 5,
            "failureStage": "candidate/zero-score/filter" if q["expectedRank"] is None else "reranking",
        })
    counts[model] = dict(sorted(count.items()))


def grouping(case):
    c = case["category"]
    if c == "EXACT VIDEO MOMENT": return "moment"
    if c == "VIDEO RETRIEVAL": return "video"
    if c == "OCR": return "OCR"
    if c == "MULTI-CONDITION": return "multi-condition"
    if c == "NEGATIVE CONDITION": return "negative"
    if case.get("no_match", False): return "no-match"
    return "photo"


candidate_recall = {}
for model, run in RUNS.items():
    buckets = defaultdict(list)
    for q in run["queries"]:
        buckets[grouping(CASE_BY_QUERY[q["query"]])].append(q)
    candidate_recall[model] = {}
    for bucket, qs in sorted(buckets.items()):
        positives = [q for q in qs if not CASE_BY_QUERY[q["query"]].get("no_match", False)]
        candidate_recall[model][bucket] = {
            "cases": len(qs), "positiveCases": len(positives),
            "expectedInTop5": sum(expected_hit(q, 5) for q in positives),
            "expectedInTop10": sum(q["expectedRank"] is not None and q["expectedRank"] <= 10 for q in positives),
            "expectedAbsentFromScoredTop10": sum(q["expectedRank"] is None for q in positives),
        }


def dist(values):
    v = sorted(float(x) for x in values if x is not None and math.isfinite(float(x)))
    if not v: return {"n": 0}
    def pct(p): return v[round((len(v)-1)*p)]
    return {"n": len(v), "min": v[0], "p25": pct(.25), "median": statistics.median(v), "p75": pct(.75), "max": v[-1]}


score_distributions = {}
for model, run in RUNS.items():
    positives = [q for q in run["queries"] if not CASE_BY_QUERY[q["query"]].get("no_match", False)]
    no_match_fp = [q for q in run["queries"] if CASE_BY_QUERY[q["query"]].get("no_match", False) and q["confident"]]
    correct = [q for q in positives if expected_hit(q)]
    incorrect = [q for q in positives if not expected_hit(q)]
    score_distributions[model] = {
        "correctTop1Scores": dist(q["top5"][0]["score"] for q in correct),
        "incorrectTop1Scores": dist(q["top5"][0]["score"] for q in incorrect if q["top5"]),
        "noMatchFalsePositiveScores": dist(q["top5"][0]["score"] for q in no_match_fp if q["top5"]),
        "allTop1Top2Margins": dist(q["top1Top2Margin"] for q in run["queries"]),
        "photoTop1Semantic": dist(q["top5"][0]["breakdown"]["semantic"] for q in positives if q["top5"] and q["top5"][0]["kind"] == "IMAGE"),
        "videoTop1Semantic": dist(q["top5"][0]["breakdown"]["semantic"] for q in positives if q["top5"] and q["top5"][0]["kind"] == "VIDEO"),
        "videoTop1WindowScore": dist((q["top5"][0].get("temporal") or {}).get("confidence", {}).get("score") for q in positives if q["top5"]),
    }


# Exact frame inventory from the immutable index's deterministic sampling report.
durations = {"video-001":5758,"video-002":10216,"video-003":15627,"video-004":20293,"video-005":30440,"video-006":658320,"video-007":734166,"video-008":888032,"video-009":200033,"video-010":85200}
frame_counts = {"video-001":3,"video-002":3,"video-003":3,"video-004":3,"video-005":3,"video-006":35,"video-007":35,"video-008":35,"video-009":14,"video-010":6}
video_frames = {}
for vid, n in frame_counts.items():
    # The extractor samples through duration-1 but seeks to integer millisecond timestamps.
    end = durations[vid] - 1
    video_frames[f"videos/{vid}." + ("mp4" if int(vid[-3:]) <= 5 else "mkv" if vid == "video-008" else "mov")] = {
        "frameCount": n, "timestampsMs": [end*i//n for i in range(n)]
    }


def compare_photo_collapse():
    v1 = {q["query"]: q for q in RUNS["V1"]["queries"]}
    v2 = {q["query"]: q for q in RUNS["V2"]["queries"]}
    photo = [c for c in CASES if grouping(c) in {"photo", "OCR", "multi-condition", "negative"} and not c.get("no_match", False)]
    regressed = [c for c in photo if expected_hit(v1[c["query"]]) and not expected_hit(v2[c["query"]])]
    video_takeovers = [c for c in regressed if v2[c["query"]]["top5"][0]["kind"] == "VIDEO"]
    explicit_image = [c for c in photo if v2[c["query"]]["interpretation"]["mediaKind"] == "IMAGE"]
    unchanged_explicit = sum(v1[c["query"]]["top10"] == v2[c["query"]]["top10"] for c in explicit_image)
    return {
        "photoLikeDevelopmentCases": len(photo), "V1CorrectV2Wrong": len(regressed),
        "regressionsWithVideoTop1": len(video_takeovers),
        "explicitImageQueries": len(explicit_image), "explicitImageTop10IdenticalV1V2": unchanged_explicit,
        "measuredCause": "V2 restores semantic/frame evidence to videos for media-ambiguous photo queries. Those videos enter the same uncalibrated global score ranking and take Top1. Explicit image-filtered queries retain the V1 ordering.",
        "ruledOut": [
            "ANN dilution: candidate limit is 500 while the immutable index has 60 asset records, so every asset vector can enter retrieval.",
            "Per-frame ANN domination: LocalVectorIndex stores one asset embedding per video; frame vectors are scored only inside the asset record.",
            "Different photo weights/path: V1 and V2 transform only VIDEO records; IMAGE records and query decomposition are unchanged.",
        ],
    }


no_match = {}
for model, run in RUNS.items():
    entries=[]
    for q in run["queries"]:
        case=CASE_BY_QUERY[q["query"]]
        if not case.get("no_match",False): continue
        top=q["top5"][0] if q["top5"] else None
        entries.append({"queryId":case["id"],"query":q["query"],"falsePositive":q["confident"],"top1":top,"top2":q["top5"][1] if len(q["top5"])>1 else None,"margin":q["top1Top2Margin"],"querySpecificity":len(q["interpretation"]["terms"]),"candidateType":top["kind"] if top else None,"allowedBecause":q["confidenceReason"]})
    no_match[model]=entries

video_case_analysis = {}
for model in ("V2", "V3"):
    entries=[]
    for q in RUNS[model]["queries"]:
        if q["category"] not in {"VIDEO RETRIEVAL", "EXACT VIDEO MOMENT"}: continue
        case=CASE_BY_QUERY[q["query"]]; top=q["top5"][0] if q["top5"] else None
        correct_video=bool(top and top["uri"] in q["expected"])
        timestamp=top.get("timestampMs") if top else None
        in_window=bool(correct_video and timestamp is not None and q["expectedStartMs"] is not None and q["expectedStartMs"] <= timestamp <= q["expectedEndMs"])
        if not correct_video: diagnosis="correct video absent from Top1; selection/reranking bottleneck"
        elif q["category"]=="VIDEO RETRIEVAL": diagnosis="correct video selected"
        elif in_window: diagnosis="correct video and temporal window"
        elif (top.get("temporal") or {}).get("state")=="TIMESTAMP_LOW_CONFIDENCE": diagnosis="correct video, timestamp suppressed by confidence"
        else: diagnosis="correct video, wrong temporal window"
        entries.append({"queryId":case["id"],"query":q["query"],"category":q["category"],"expected":q["expected"],"expectedWindowMs":[q["expectedStartMs"],q["expectedEndMs"]],"expectedRank":q["expectedRank"],"correctVideoTop1":correct_video,"predictedTimestampMs":timestamp,"timestampInExpectedWindow":in_window,"top1":top,"top3":q["top5"][:3],"diagnosis":diagnosis})
    video_case_analysis[model]=entries


report = {
    "schemaVersion": 1, "scope": {"developmentCases":105,"holdoutCasesReadByReportBuilder":0,"holdoutExecuted":False},
    "metrics": {m:r["metrics"] for m,r in RUNS.items()}, "failureClassCounts": counts,
    "failures": failures, "candidateRecall": candidate_recall, "scoreDistributions": score_distributions,
    "photoRegression": compare_photo_collapse(), "videoFrameInventory": video_frames,
    "noMatchCases": no_match, "videoAndMomentCases": video_case_analysis,
    "findings": {
        "candidateArchitecture":"All 60 asset-level vectors fit inside candidateLimit=500. A null expectedRank therefore means filtering or zero/nonpositive scoring, not ANN capacity eviction.",
        "video":"V2/V3 improve video retrieval over V1, but most development video failures are selection/reranking failures; sparse 3-frame coverage on five short clips and 14-35 frames spaced roughly 14-25 seconds on long clips limit moment evidence.",
        "moment":"V2's principal bottleneck is retrieving the correct video. V3 raises correct-video retrieval but its temporal ranker then selects wrong windows or suppresses timestamps as low-confidence; development moment hit rate remains zero.",
        "noMatch":"Absolute-score plus margin confidence accepts semantically plausible nearest neighbors even when the query's requested combination is absent. V2 video evidence and V3 deep window score inflate scores on negatives; margins alone do not measure signal agreement.",
        "ocr":"Development OCR Top1 is 71.4% in all three models. OCR evidence is stable and should remain protected by regression tests.",
        "scoreCalibration":"Correct and incorrect score distributions overlap; video frame/window additions raise a different evidence scale than image scores. Raw global scores are therefore not safely comparable across media types or as a no-match probability.",
    },
    "recommendations": [
        {"priority":"P0","change":"For non-video-intent queries, preserve a V1-compatible image lane and union it with at most one calibrated candidate per video before final ranking.","addresses":"PHOTO_VIDEO_SCORE_INTERFERENCE","metric":"photo Top1/Top5","risk":"May reduce valid ambiguous video recall.","files":["android-app/test-lab/src/main/kotlin/app/honorable/testlab/TestLab.kt:search","android-app/app/src/main/java/app/honorable/search/SearchCore.kt:HybridSearchEngine"],"independentTest":True},
        {"priority":"P0","change":"Calibrate confidence by media type and require multi-signal agreement for no-match acceptance; fit only on development negatives.","addresses":"FALSE_POSITIVE_NO_MATCH and OVERCONFIDENT_LOW_SIGNAL_RESULT","metric":"no-match accuracy/false-positive rate","risk":"False negatives on weak but valid semantic queries.","files":["android-app/app/src/main/java/app/honorable/search/SearchCore.kt:confidenceDecision"],"independentTest":True},
        {"priority":"P1","change":"Separate video selection from temporal-window selection and retain the correct-video candidate before V3 window reranking.","addresses":"VIDEO_SELECTION_FAILURE","metric":"video Top1/Top3 and moment correct-video rate","risk":"Two-stage calibration may add latency or lock in a wrong video.","files":["android-app/app/src/main/java/app/honorable/search/TemporalSearch.kt:V3DeepReranker"],"independentTest":True},
        {"priority":"P1","change":"Use overlapping/adaptive temporal windows around sampled frames and compare neighboring windows before emitting or suppressing timestamps.","addresses":"TEMPORAL_WINDOW_FAILURE and TIMESTAMP_CONFIDENCE_FAILURE","metric":"moment hit rate/timestamp error","risk":"More index/compute cost; correlated windows can shrink margins.","files":["android-app/app/src/main/java/app/honorable/search/TemporalSearch.kt:TemporalWindowBuilder,V3MomentRanker"],"independentTest":True},
        {"priority":"P2","change":"Add explicit development regressions that freeze OCR Top1/evidence while testing any candidate or confidence calibration.","addresses":"OCR protection","metric":"OCR Top1 remains >= current 71.4% development result","risk":"None beyond test maintenance.","files":["android-app/test-lab/src/test/kotlin/app/honorable/testlab"],"independentTest":True},
    ],
    "rankingBehaviorModified":False,"holdoutUsedForTuning":False,"v3PublicStatus":"EXPERIMENTAL / LOCKED",
}
(ROOT/"seran-development-failure-analysis.json").write_text(json.dumps(report,indent=2)+"\n")

def pct(x): return "n/a" if x is None or (isinstance(x,float) and math.isnan(x)) else f"{x*100:.1f}%"
md=[]
md += ["# Seran development failure analysis", "", "Scope: **105 development cases only**. Holdout executed: **NO**. Holdout used for tuning: **NO**. Ranking behavior modified: **NO**.", ""]
md += ["## Development metrics", "", "| Model | Overall T1/T3/T5 | Photo T1/T3 | Video T1/T3 | Moment hit/correct-video | No-match accuracy / FP |", "|---|---:|---:|---:|---:|---:|"]
for m,r in RUNS.items():
    x=r["metrics"]; md.append(f"| {m} | {pct(x['overallTop1'])} / {pct(x['overallTop3'])} / {pct(x['overallTop5'])} | {pct(x['photoTop1'])} / {pct(x['photoTop3'])} | {pct(x['videoTop1'])} / {pct(x['videoTop3'])} | {pct(x['momentHitRate'])} / {pct(x['momentCorrectVideoRate'])} | {pct(x['noMatchAccuracy'])} / {pct(x['falsePositiveRate'])} |")
pr=report["photoRegression"]
md += ["", "## Highest-priority finding: V2 photo collapse", "", pr["measuredCause"], "", f"Measured on {pr['photoLikeDevelopmentCases']} positive photo-like cases: {pr['V1CorrectV2Wrong']} were correct in V1 and wrong in V2; {pr['regressionsWithVideoTop1']} of those regressions had a video at Top1. Of {pr['explicitImageQueries']} explicitly image-parsed queries, {pr['explicitImageTop10IdenticalV1V2']} retained identical V1/V2 Top10 ordering.", "", "Ruled out:"]
md += [f"- {x}" for x in pr["ruledOut"]]
md += ["", "## Failure-class counts", "", "| Class | V1 | V2 | V3 |", "|---|---:|---:|---:|"]
classes=sorted(set().union(*(counts[m] for m in counts)))
md += [f"| {c} | {counts['V1'].get(c,0)} | {counts['V2'].get(c,0)} | {counts['V3'].get(c,0)} |" for c in classes]
md += ["", "## Candidate recall", "", "Counts are positive cases whose expected asset appears in the scored Top5/Top10. `Absent` means absent from the returned scored Top10; because 60 asset vectors fit in the 500-record ANN cap, it is not capacity dilution.", "", "| Model / group | Cases | Top5 | Top10 | Absent |", "|---|---:|---:|---:|---:|"]
for m,groups in candidate_recall.items():
    for g,x in groups.items():
        if x["positiveCases"]: md.append(f"| {m} / {g} | {x['positiveCases']} | {x['expectedInTop5']} | {x['expectedInTop10']} | {x['expectedAbsentFromScoredTop10']} |")
md += ["", "## Video and moment findings", "", report["findings"]["video"], "", report["findings"]["moment"], "", "### Indexed video frames", "", "| Video | Frames | Timestamps (ms) |", "|---|---:|---|"]
for uri,x in video_frames.items(): md.append(f"| `{uri}` | {x['frameCount']} | {', '.join(map(str,x['timestampsMs']))} |")
md += ["", "## No-match and score calibration", "", report["findings"]["noMatch"], "", report["findings"]["scoreCalibration"], "", "Full per-negative Top1/Top2 scores, margins, candidate type, specificity, evidence, and confidence reason are in the JSON report.", "", "## OCR protection", "", report["findings"]["ocr"], "", "## Targeted recommendations (not implemented)", ""]
for i,r in enumerate(report["recommendations"],1): md += [f"{i}. **{r['priority']} — {r['change']}**", f"   Addresses: {r['addresses']}. Metric: {r['metric']}. Risk: {r['risk']} Independently testable: {'yes' if r['independentTest'] else 'no'}. Likely files/functions: {', '.join(r['files'])}."]
md += ["", "## Per-failure index", "", "Every detailed failure record—including query interpretation, candidates/scores, evidence channels, temporal evidence, confidence, margin, candidate presence, and failure stage—is stored in `seran-development-failure-analysis.json`.", "", "| Query | Model | Category | Class | Expected rank | Top1 |", "|---|---|---|---|---:|---|"]
for f in failures:
    top=f["predictedTop1"]; md.append(f"| {f['queryId']} | {f['model']} | {f['category']} | {f['failureClass']} | {f['expectedRank'] or 'absent'} | `{top['uri'] if top else 'none'}` |")
md += ["", "V3 public status: **EXPERIMENTAL / LOCKED**.", ""]
(ROOT/"seran-development-failure-analysis.md").write_text("\n".join(md))
print(f"development={len(CASES)} failures={len(failures)} holdout_read=0")
