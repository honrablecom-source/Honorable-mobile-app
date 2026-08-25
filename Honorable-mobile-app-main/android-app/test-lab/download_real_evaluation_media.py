#!/usr/bin/env python3
"""Download a fixed, non-sensitive Unsplash evaluation set; never used as training data."""

import json
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2] / "test-media" / "real-expanded"
SOURCES = [
    "https://images.unsplash.com/photo-1554068865-24cecd4e34b8?w=1280&q=85",
    "https://images.unsplash.com/photo-1595435934249-5df7ed86e1c0?w=1280&q=85",
    "https://images.unsplash.com/photo-1542144582-1ba00456b5e3?w=1280&q=85",
    "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1280&q=85",
    "https://images.unsplash.com/photo-1519046904884-53103b34b206?w=1280&q=85",
    "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=1280&q=85",
    "https://images.unsplash.com/photo-1542272604-787c3835535d?w=1280&q=85",
    "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=1280&q=85",
    "https://images.unsplash.com/photo-1460353581641-37baddab0fa2?w=1280&q=85",
    "https://images.unsplash.com/photo-1524758631624-e2822e304c36?w=1280&q=85",
    "https://images.unsplash.com/photo-1497366754035-f200968a6e72?w=1280&q=85",
    "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=1280&q=85",
    "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=1280&q=85",
    "https://images.unsplash.com/photo-1488426862026-3ee34a7d66df?w=1280&q=85",
]


def main() -> None:
    ROOT.mkdir(exist_ok=True)
    manifest=[]
    for index,url in enumerate(SOURCES,1):
        destination=ROOT/f"real-{index:02d}.jpg"
        request=urllib.request.Request(url,headers={"User-Agent":"Honorable local evaluation/1.0"})
        with urllib.request.urlopen(request,timeout=60) as response:
            data=response.read()
        if not data.startswith(b"\xff\xd8"):
            raise RuntimeError(f"Expected JPEG from {url}")
        destination.write_bytes(data)
        manifest.append({"file":destination.name,"source":url,"purpose":"local search evaluation only"})
        print(f"Downloaded {destination.name}: {len(data)} bytes")
    (ROOT/"sources.json").write_text(json.dumps(manifest,indent=2)+"\n")


if __name__ == "__main__":
    main()
