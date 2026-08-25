#!/usr/bin/env python3
"""Generate deterministic synthetic fixtures used only to evaluate Linux OCR."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[2] / "test-media" / "eval-ocr"
FONT = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
FONT_BOLD = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")


def fixture(name: str, heading: str, lines: list[str], colors: tuple[str, str]) -> None:
    image = Image.new("RGB", (1200, 700), colors[0])
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((70, 70, 1130, 630), radius=32, fill=colors[1])
    draw.text((125, 120), heading, font=ImageFont.truetype(FONT_BOLD, 58), fill="#111827")
    for index, line in enumerate(lines):
        draw.text((125, 245 + index * 105), line, font=ImageFont.truetype(FONT, 52), fill="#1f2937")
    image.save(ROOT / name, "PNG", optimize=True)


def main() -> None:
    ROOT.mkdir(exist_ok=True)
    fixture("ocr-flight.png", "FLIGHT CONFIRMATION", ["Air Canada  AC 182", "Toronto to Vancouver"], ("#dbeafe", "#ffffff"))
    fixture("ocr-gate.png", "BOARDING GATE", ["Gate B14", "Boarding 6:45 PM"], ("#dcfce7", "#ffffff"))
    fixture("ocr-receipt.png", "MARKET RECEIPT", ["Subtotal     $38.50", "TOTAL        $42.80"], ("#f3f4f6", "#ffffff"))
    fixture("ocr-meeting.png", "REMINDER", ["Meet at 7:30 PM", "Community Hall"], ("#fef3c7", "#ffffff"))
    print(f"Generated 4 synthetic OCR-only fixtures in {ROOT}")


if __name__ == "__main__":
    main()
