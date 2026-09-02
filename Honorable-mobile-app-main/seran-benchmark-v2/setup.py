#!/usr/bin/env python3
"""Reconstruct and integrity-check the permanent Seran benchmark corpus."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import urllib.request
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
MEDIA = ROOT / "media"
MANIFEST = ROOT / "assets.json"
FONT = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
FONT_BOLD = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def dimensions(path: Path) -> tuple[int, int]:
    with Image.open(path) as image:
        return image.size


def duration_ms(path: Path) -> int:
    result = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", str(path)],
        check=True, capture_output=True, text=True,
    )
    return round(float(result.stdout.strip()) * 1000)


def generate_control(asset: dict, destination: Path) -> None:
    spec = asset["generator"]
    image = Image.new("RGB", tuple(spec.get("size", [1200, 700])), spec["background"])
    draw = ImageDraw.Draw(image)
    if spec["kind"] == "ocr":
        draw.rounded_rectangle((70, 70, 1130, 630), radius=32, fill=spec["card"])
        draw.text((125, 120), spec["heading"], font=ImageFont.truetype(FONT_BOLD, 58), fill="#111827")
        for index, line in enumerate(spec["lines"]):
            draw.text((125, 245 + index * 105), line, font=ImageFont.truetype(FONT, 52), fill="#1f2937")
    else:
        split = image.width // 2
        draw.rectangle((0, 0, split, image.height), fill=spec["left"])
        draw.rectangle((split, 0, image.width, image.height), fill=spec["right"])
        draw.ellipse((420, 210, 780, 570), fill=spec["object"])
    image.save(destination, "PNG", optimize=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--verify-only", action="store_true")
    parser.add_argument("--lock", action="store_true", help="maintainer-only: record hashes and measured metadata")
    args = parser.parse_args()
    data = json.loads(MANIFEST.read_text())
    MEDIA.mkdir(exist_ok=True)
    failures: list[str] = []
    for asset in data["assets"]:
        destination = MEDIA / asset["path"]
        destination.parent.mkdir(parents=True, exist_ok=True)
        if not destination.exists() and not args.verify_only:
            if "generator" in asset:
                generate_control(asset, destination)
            else:
                request = urllib.request.Request(asset["source"], headers={"User-Agent": "Honorable-Seran-Benchmark/2.0"})
                with urllib.request.urlopen(request, timeout=180) as response, destination.open("wb") as output:
                    while chunk := response.read(1024 * 1024):
                        output.write(chunk)
        if not destination.is_file():
            failures.append(f"MISSING {asset['id']}: {destination}")
            continue
        actual = sha256(destination)
        if args.lock:
            asset["sha256"] = actual
            if asset["type"] == "photo":
                asset["dimensions"] = list(dimensions(destination))
            else:
                asset["duration_ms"] = duration_ms(destination)
        elif actual != asset.get("sha256"):
            failures.append(f"HASH MISMATCH {asset['id']}: expected {asset.get('sha256')} got {actual}")
    if args.lock:
        MANIFEST.write_text(json.dumps(data, indent=2) + "\n")
    if failures:
        raise SystemExit("Benchmark integrity failure:\n" + "\n".join(failures))
    photos = sum(a["type"] == "photo" for a in data["assets"])
    videos = sum(a["type"] == "video" for a in data["assets"])
    print(f"SERAN BENCHMARK V2 VERIFIED: photos={photos} videos={videos} assets={len(data['assets'])}")


if __name__ == "__main__":
    main()
