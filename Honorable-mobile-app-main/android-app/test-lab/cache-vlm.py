#!/usr/bin/env python3
import json,subprocess,sys
from pathlib import Path
root=Path(sys.argv[1]);bridge=Path(__file__).with_name("ollama_vlm.py");extensions={".jpg",".jpeg",".png",".webp"}
files=[p for p in root.rglob("*") if p.is_file() and not p.name.startswith(".") and p.suffix.lower() in extensions]
for index,path in enumerate(files,1):
    cache=Path(str(path)+".vlm.json");stat=path.stat()
    try:
        old=json.loads(cache.read_text()) if cache.exists() else {}
        if old.get("source_mtime_ns")==stat.st_mtime_ns and old.get("source_size")==stat.st_size and old.get("model_id")=="moondream:1.8b" and old.get("analysis_version")==2:
            print(f"{index}/{len(files)} {path.name} Vision ✓ (cached)");continue
        result=subprocess.run([sys.executable,str(bridge),str(path)],capture_output=True,text=True,timeout=330)
        if result.returncode or not result.stdout.strip(): raise RuntimeError(result.stderr.strip() or "empty output")
        data=json.loads(result.stdout);data.update({"source_mtime_ns":stat.st_mtime_ns,"source_size":stat.st_size});cache.write_text(json.dumps(data))
        print(f"{index}/{len(files)} {path.name} Vision ✓")
    except Exception as error: print(f"{index}/{len(files)} {path.name} Vision ✗ — {error}")
subprocess.run(["ollama","stop","moondream:1.8b"],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
