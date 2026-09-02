#!/usr/bin/env python3
"""Materialize only the development split for diagnostics; never emits holdout cases."""

import json
from pathlib import Path

root = Path(__file__).resolve().parent
cases = json.loads((root / "evaluation.json").read_text())
development = [case for case in cases if case.get("split") == "development"]
assert len(cases) == 150 and len(development) == 105
(root / "development-analysis-cases.json").write_text(json.dumps(development, indent=2) + "\n")
print("development=105 holdout_written=0")
