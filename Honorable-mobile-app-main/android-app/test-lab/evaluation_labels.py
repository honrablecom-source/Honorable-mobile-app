#!/usr/bin/env python3
import json, sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]/"test-media"
DATA=ROOT/"evaluation.json"
CATEGORIES={"object","scene","color","object+color","activity","person-attribute","sports","animals","food","vehicle","weather","indoor/outdoor","OCR","document","multi-concept","video","video+activity","person+color+scene"}
DIFFICULTIES={"easy","medium","hard"}

def load():
    if not DATA.exists(): return []
    value=json.loads(DATA.read_text())
    if not isinstance(value,list): raise SystemExit("evaluation.json must contain a JSON array")
    return value

def prompt(label):
    print(f"{label}:\n> ",end="",flush=True)
    return input().strip()

def add():
    rows=load();query=prompt("Search description");expected=prompt("Expected file");category=prompt("Category")
    if not query or not expected or not category: raise SystemExit("All three values are required")
    if category not in CATEGORIES: raise SystemExit("Unsupported category. Use: "+", ".join(sorted(CATEGORIES)))
    if not (ROOT/expected).is_file(): raise SystemExit(f"Expected media does not exist under test-media: {expected}")
    rows.append({"query":query,"expected":[expected],"category":category})
    temporary=DATA.with_suffix(".json.tmp");temporary.write_text(json.dumps(rows,indent=2,ensure_ascii=False)+"\n");temporary.replace(DATA)
    print(f"Added query {len(rows)} to {DATA}")

def listing():
    rows=load()
    if not rows: print("No labeled queries.");return
    for index,row in enumerate(rows,1):
        expected=", ".join(row.get("expected",[]));difficulty=row.get("difficulty","-")
        timestamp=row.get("expected_timestamp");window=f" {timestamp.get('start')}–{timestamp.get('end')}s" if isinstance(timestamp,dict) else ""
        print(f"{index}. [{row.get('category','uncategorized')}/{difficulty}] {row.get('query','')} -> {expected}{window}")

def export():
    import base64
    enc=lambda value:base64.b64encode(str(value).encode()).decode()
    for row in load():
        expected=row.get("expected",[]);window=row.get("expected_timestamp") or {}
        print("\t".join([enc(row.get("query","")),enc("\x1f".join(expected)),enc(row.get("category","uncategorized")),enc(row.get("difficulty","")),str(window.get("start","")),str(window.get("end",""))]))

if __name__=="__main__":
    command=sys.argv[1] if len(sys.argv)>1 else ""
    {"add":add,"list":listing,"export":export}.get(command,lambda:(_ for _ in ()).throw(SystemExit("Use: add | list | export")))()
