#!/usr/bin/env python3
"""Imports a Bear Bones icon pack into the app.

Usage:
  python3 tools/iconpack/import.py sheet1.png sheet2.png sheet3.png sheet4.png
      Icon sheets from CHATGPT_PROMPT.md, in order. Each sheet holds 8 round icons (4 across, 2 down) on a
      plain light background; icons are found by shape, so they don't need to sit on an exact grid.
  python3 tools/iconpack/import.py <folder or .zip of bb_<glyph>.png files>

Writes res/drawable-nodpi/bb_<glyph>.png and res/xml/appfilter.xml (the standard icon-pack map the app reads).
Needs Pillow and numpy.
"""
import shutil
import sys
import tempfile
import zipfile
from collections import deque
from pathlib import Path
from xml.sax.saxutils import quoteattr

import numpy as np
from PIL import Image, ImageDraw

ICON_PX = 192
PER_SHEET = 8

root = Path(__file__).resolve().parents[2]
res = root / "app/src/main/res"
here = Path(__file__).parent


def glyph_table():
    rows = []
    for line in (here / "glyphs.txt").read_text().splitlines():
        if line.strip() and not line.startswith("#"):
            glyph, _, components = [part.strip() for part in line.split("|", 2)]
            rows.append((glyph, [c.strip() for c in components.split(";") if c.strip()]))
    return rows


def find_icons(sheet: Image.Image):
    """Bounding boxes of the dark round icons on a light sheet, in reading order."""
    scale = 300 / max(sheet.size)
    small = sheet.convert("L").resize((max(1, int(sheet.width * scale)), max(1, int(sheet.height * scale))))
    dark = np.array(small) < 110
    h, w = dark.shape
    seen = np.zeros_like(dark)
    boxes = []
    for y in range(h):
        for x in range(w):
            if not dark[y, x] or seen[y, x]:
                continue
            q = deque([(y, x)])
            seen[y, x] = True
            ys, xs = [], []
            while q:
                cy, cx = q.popleft()
                ys.append(cy)
                xs.append(cx)
                for ny, nx in ((cy + 1, cx), (cy - 1, cx), (cy, cx + 1), (cy, cx - 1)):
                    if 0 <= ny < h and 0 <= nx < w and dark[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True
                        q.append((ny, nx))
            if len(ys) > 0.004 * w * h:  # ignore specks
                boxes.append([min(xs) / scale, min(ys) / scale, (max(xs) + 1) / scale, (max(ys) + 1) / scale])
    # Reading order: group into rows by vertical centre, then left to right.
    boxes.sort(key=lambda b: (b[1] + b[3]) / 2)
    rows, current = [], []
    for b in boxes:
        if current and (b[1] + b[3]) / 2 - (current[-1][1] + current[-1][3]) / 2 > (b[3] - b[1]) / 2:
            rows.append(current)
            current = []
        current.append(b)
    if current:
        rows.append(current)
    return [b for row in rows for b in sorted(row, key=lambda b: b[0])]


def cut_icon(sheet: Image.Image, box) -> Image.Image:
    """Square crop around the icon, everything outside its circle made transparent."""
    x0, y0, x1, y1 = box
    side = max(x1 - x0, y1 - y0)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    crop = sheet.convert("RGBA").crop((int(cx - side / 2), int(cy - side / 2), int(cx + side / 2), int(cy + side / 2)))
    big = crop.resize((ICON_PX * 4, ICON_PX * 4), Image.LANCZOS)
    mask = Image.new("L", big.size, 0)
    ImageDraw.Draw(mask).ellipse((2, 2, big.width - 3, big.height - 3), fill=255)
    big.putalpha(mask)
    return big.resize((ICON_PX, ICON_PX), Image.LANCZOS)


def collect_pngs(args, glyphs):
    """Returns {"bb_<glyph>": PIL image or path}."""
    images = [Path(a).expanduser() for a in args]
    if all(p.suffix.lower() in (".png", ".jpg", ".jpeg", ".webp") for p in images) and not any(p.stem.startswith("bb_") for p in images):
        out = {}
        for i, path in enumerate(images):
            names = [f"bb_{g}" for g, _ in glyphs[i * PER_SHEET:(i + 1) * PER_SHEET]]
            sheet = Image.open(path)
            boxes = find_icons(sheet)
            print(f"{path.name}: found {len(boxes)} icons, expected {len(names)}")
            if len(boxes) != len(names):
                sys.exit(f"  {path.name} doesn't have exactly {len(names)} icons; ask ChatGPT to redo that sheet.")
            for name, box in zip(names, boxes):
                out[name] = cut_icon(sheet, box)
        return out
    src = images[0]
    if src.suffix == ".zip":
        tmp = Path(tempfile.mkdtemp())
        zipfile.ZipFile(src).extractall(tmp)
        src = tmp
    return {p.stem.lower(): p for p in src.rglob("*.png")}


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    glyphs = glyph_table()
    found = collect_pngs(sys.argv[1:], glyphs)
    out_dir = res / "drawable-nodpi"
    out_dir.mkdir(parents=True, exist_ok=True)
    items, missing = [], []
    for glyph, components in glyphs:
        name = f"bb_{glyph}"
        icon = found.get(name)
        if icon is None:
            missing.append(name)
            continue
        if isinstance(icon, Image.Image):
            icon.save(out_dir / f"{name}.png")
        else:
            shutil.copy(icon, out_dir / f"{name}.png")
        for c in components:
            items.append(f"    <item component={quoteattr('ComponentInfo{' + c + '}')} drawable=\"{name}\" />")
    (res / "xml").mkdir(exist_ok=True)
    (res / "xml/appfilter.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/iconpack/import.py -->\n<resources>\n'
        + "\n".join(items) + "\n</resources>\n"
    )
    print(f"Imported {len(glyphs) - len(missing)} icons, mapped {len(items)} apps.")
    if missing:
        print("Missing (those apps keep their own icon):", ", ".join(missing))


if __name__ == "__main__":
    main()
