#!/usr/bin/env python3
"""Download fixed public Google sample clips for local video-search evaluation."""

import json
import urllib.request
from pathlib import Path


ROOT=Path(__file__).resolve().parents[2]/"test-media"/"video-expanded"
SOURCES=[
    "https://download.samplelib.com/mp4/sample-5s.mp4",
    "https://download.samplelib.com/mp4/sample-10s.mp4",
    "https://download.samplelib.com/mp4/sample-15s.mp4",
]


def main()->None:
    ROOT.mkdir(exist_ok=True)
    manifest=[]
    for index,url in enumerate(SOURCES,1):
        destination=ROOT/f"video-{index:02d}.mp4"
        request=urllib.request.Request(url,headers={"User-Agent":"Honorable local evaluation/1.0"})
        with urllib.request.urlopen(request,timeout=120) as response:
            destination.write_bytes(response.read())
        manifest.append({"file":destination.name,"source":url,"purpose":"local search evaluation only"})
        print(f"Downloaded {destination.name}: {destination.stat().st_size} bytes")
    (ROOT/"sources.json").write_text(json.dumps(manifest,indent=2)+"\n")


if __name__=="__main__":
    main()
