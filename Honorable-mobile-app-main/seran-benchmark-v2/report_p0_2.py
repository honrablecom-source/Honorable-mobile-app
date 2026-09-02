#!/usr/bin/env python3
"""Compare P0 #2 using development-only result files."""
import json
from pathlib import Path

root=Path(__file__).resolve().parent
before={m:json.loads((root/f"development-p0-1-final-{m}.json").read_text()) for m in ("v2","v3")}
after={m:json.loads((root/f"development-p0-2-{m}.json").read_text()) for m in ("v2","v3")}
assert all(x["queryCount"]==105 for x in [*before.values(),*after.values()])

def mapped(run):return {q["query"]:q for q in run["queries"]}
def correct(q):return bool(q["expected"] and q["top5"] and q["top5"][0]["uri"] in q["expected"])

report={"scope":{"developmentCases":105,"holdoutTouched":False},"models":{},"rankingChanged":False}
for model in ("v2","v3"):
    old,new=mapped(before[model]),mapped(after[model]);cases=[];new_rejections=[]
    for query,q in new.items():
        if q["category"]=="NO-MATCH":
            prior=old[query];top=q["top5"][0] if q["top5"] else None
            cases.append({"query":query,"top1Candidate":top["uri"] if top else None,"top1Score":top["score"] if top else None,"margin":q["top1Top2Margin"],"signalAgreement":q["signalAgreement"],"confidenceBefore":prior["confident"],"confidenceAfter":q["confident"],"decision":q["confidenceDecision"],"tier":q["confidenceTier"],"reason":q["confidenceReason"]})
        if correct(old[query]) and old[query]["confident"] and not q["confident"]:
            new_rejections.append({"query":query,"category":q["category"],"reason":q["confidenceReason"]})
    report["models"][model.upper()]={"metricsBefore":before[model]["metrics"],"metricsAfter":after[model]["metrics"],"noMatchCases":cases,"newlyRejectedCorrectTop1":new_rejections}

(root/"seran-p0-2-confidence-report.json").write_text(json.dumps(report,indent=2)+"\n")
lines=["# Seran P0 #2 confidence calibration","","Scope: 105 development cases only. Holdout touched: NO. Candidate ranking changed: NO.",""]
for model,data in report["models"].items():
    b=data["metricsBefore"];a=data["metricsAfter"]
    lines += [f"## {model}","",f"No-match accuracy: {b['noMatchAccuracy']:.1%} → {a['noMatchAccuracy']:.1%}; false-positive rate: {b['falsePositiveRate']:.1%} → {a['falsePositiveRate']:.1%}. Newly rejected previously accepted correct Top1 results: {len(data['newlyRejectedCorrectTop1'])}.","","| Query | Top1 | Score | Margin | Agreement | Before | After | Reason |","|---|---|---:|---:|---:|---|---|---|"]
    for c in data["noMatchCases"]:
        lines.append(f"| {c['query']} | `{c['top1Candidate'] or 'none'}` | {c['top1Score'] if c['top1Score'] is not None else 'n/a'} | {c['margin']:.3f} | {c['signalAgreement']:.2f} | {'ACCEPT' if c['confidenceBefore'] else 'NO_MATCH'} | {c['decision']} | {c['reason']} |")
    lines.append("")
(root/"seran-p0-2-confidence-report.md").write_text("\n".join(lines))
print("development=105 holdout=0")
