#!/usr/bin/env python3
"""Create deterministic, evaluation-only image degradations from real test media."""

from pathlib import Path
from PIL import Image, ImageEnhance, ImageFilter


ROOT = Path(__file__).resolve().parents[2] / "test-media"
OUTPUT = ROOT / "eval-derived"
SOURCE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


def fitted(image: Image.Image, maximum: int = 1600) -> Image.Image:
    value = image.convert("RGB")
    value.thumbnail((maximum, maximum), Image.Resampling.LANCZOS)
    return value


def save(image: Image.Image, source: Path, variant: str, quality: int = 90) -> None:
    destination = OUTPUT / f"{source.stem}__{variant}.jpg"
    image.save(destination, "JPEG", quality=quality, optimize=True)


def generate(source: Path) -> int:
    original = fitted(Image.open(source))
    width, height = original.size
    save(original.filter(ImageFilter.GaussianBlur(1.5)), source, "mild-blur")
    save(original.filter(ImageFilter.GaussianBlur(5.0)), source, "strong-blur")
    save(ImageEnhance.Brightness(original).enhance(0.28), source, "low-light")
    save(ImageEnhance.Contrast(original).enhance(0.35), source, "low-contrast")
    save(original, source, "jpeg-compression", quality=18)
    short = max(96, min(width, height) // 8)
    scale = short / min(width, height)
    low_res = original.resize((max(1, round(width * scale)), max(1, round(height * scale))), Image.Resampling.LANCZOS)
    save(low_res, source, "low-resolution", quality=82)
    crop_width, crop_height = round(width * 0.62), round(height * 0.62)
    left, top = (width - crop_width) // 2, (height - crop_height) // 2
    save(original.crop((left, top, left + crop_width, top + crop_height)), source, "center-crop")
    return 7


def main() -> None:
    OUTPUT.mkdir(exist_ok=True)
    sources = sorted(path for path in ROOT.iterdir() if path.is_file() and path.suffix.lower() in SOURCE_EXTENSIONS)
    count = sum(generate(source) for source in sources)
    print(f"Generated {count} evaluation-only variants from {len(sources)} images in {OUTPUT}")


if __name__ == "__main__":
    main()
