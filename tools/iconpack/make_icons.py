#!/usr/bin/env python3
"""Draws the built-in Bear Bones icon pack and writes it into the app.

Usage:
  python3 tools/iconpack/make_icons.py            write the icons into the app
  python3 tools/iconpack/make_icons.py sheet.svg  also write a contact sheet of every icon

Every icon is a bone-white glyph on an espresso-brown disc, and every glyph has a little dog bone in it.
The icons are plain vector drawables, so they stay sharp at any size and add only a few KB to the APK.

Writes res/drawable/bb_<glyph>.xml and res/xml/appfilter.xml (the standard icon-pack map the app reads).
Which apps use which glyph is listed in glyphs.txt. Needs only the Python standard library.
"""
import math
import sys
from pathlib import Path
from xml.sax.saxutils import quoteattr

SIZE = 96  # viewport; glyphs are drawn inside roughly 24..72
BG = "#2A1F17"  # espresso brown, same as the app icon
BONE = "#EDE3CF"  # bone white, same as the app icon
W = 4.5  # outline thickness
ZOOM = 1.18  # glyphs are drawn small and enlarged about the centre, so the numbers below stay round

root = Path(__file__).resolve().parents[2]
res = root / "app/src/main/res"
here = Path(__file__).parent


def n(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


# --- Shapes. Each returns a list of (kind, path data, stroke width, color). kind: "fill" or "stroke".
def fill(d, color=BONE):
    return [("fill", d, 0, color)]


def stroke(d, width=W, color=BONE):
    return [("stroke", d, width, color)]


def circle_d(cx, cy, r):
    return f"M{n(cx - r)},{n(cy)} a{n(r)},{n(r)} 0 1,0 {n(2 * r)},0 a{n(r)},{n(r)} 0 1,0 {n(-2 * r)},0 z"


def dot(cx, cy, r, color=BONE):
    return fill(circle_d(cx, cy, r), color)


def ring(cx, cy, r, width=W):
    return stroke(circle_d(cx, cy, r), width)


def line(*pts, width=W, color=BONE, close=False):
    d = "M" + " L".join(f"{n(x)},{n(y)}" for x, y in pts) + (" z" if close else "")
    return stroke(d, width, color)


def rrect_d(x0, y0, x1, y1, r):
    return (f"M{n(x0 + r)},{n(y0)} H{n(x1 - r)} a{n(r)},{n(r)} 0 0 1 {n(r)},{n(r)} V{n(y1 - r)} "
            f"a{n(r)},{n(r)} 0 0 1 {n(-r)},{n(r)} H{n(x0 + r)} a{n(r)},{n(r)} 0 0 1 {n(-r)},{n(-r)} V{n(y0 + r)} "
            f"a{n(r)},{n(r)} 0 0 1 {n(r)},{n(-r)} z")


def box(x0, y0, x1, y1, r, width=W, solid=False):
    """Rounded outline; solid=True also paints the inside so things drawn earlier hide behind it."""
    d = rrect_d(x0, y0, x1, y1, r)
    return (fill(d, BG) if solid else []) + stroke(d, width)


def outline(d, width=W, solid=False):
    return (fill(d, BG) if solid else []) + stroke(d, width)


def knobs(x, y, dx, dy, k, color=BONE, grow=0.0):
    """The two round knuckles at one end of a bone; (dx, dy) points along the shaft, outward."""
    length = math.hypot(dx, dy)
    ux, uy = dx / length, dy / length
    px, py = -uy, ux
    out = []
    for side in (-1, 1):
        cx = x + ux * k * 0.25 + px * side * k * 0.8
        cy = y + uy * k * 0.25 + py * side * k * 0.8
        out += dot(cx, cy, k + grow, color)
    return out


def bone(x1, y1, x2, y2, w=4.5, k=None, halo=False, ends=(True, True)):
    """A dog bone from (x1, y1) to (x2, y2). halo=True gives it a dark edge so it reads on top of lines."""
    k = k if k is not None else w * 0.76
    out = []
    passes = [(BG, 2.2), (BONE, 0.0)] if halo else [(BONE, 0.0)]
    for color, grow in passes:
        out += line((x1, y1), (x2, y2), width=w + 2 * grow, color=color)
        if ends[0]:
            out += knobs(x1, y1, x1 - x2, y1 - y2, k, color, grow)
        if ends[1]:
            out += knobs(x2, y2, x2 - x1, y2 - y1, k, color, grow)
    return out


def crossed(cx, cy, r, w=3.6):
    """Two bones crossed in an X, with one shared dark edge so they read on top of lines."""
    a, b = (cx - r, cy - r, cx + r, cy + r), (cx + r, cy - r, cx - r, cy + r)
    halo = [s for s in bone(*a, w, halo=True) + bone(*b, w, halo=True) if s[3] == BG]
    return halo + bone(*a, w) + bone(*b, w)


def bubble(x0, y0, x1, y1, r, tail="left", solid=False):
    """Speech bubble; the tail hangs off the bottom edge on the given side."""
    if tail == "left":
        bottom = f"H{n(x0 + r + 10)} L{n(x0 + r - 1)},{n(y1 + 8.5)} L{n(x0 + r)},{n(y1)}"
    else:
        bottom = f"H{n(x1 - r)} L{n(x1 - r + 1)},{n(y1 + 8.5)} L{n(x1 - r - 10)},{n(y1)} H{n(x0 + r)}"
    d = (f"M{n(x0 + r)},{n(y0)} H{n(x1 - r)} a{n(r)},{n(r)} 0 0 1 {n(r)},{n(r)} V{n(y1 - r)} "
         f"a{n(r)},{n(r)} 0 0 1 {n(-r)},{n(r)} {bottom} a{n(r)},{n(r)} 0 0 1 {n(-r)},{n(-r)} V{n(y0 + r)} "
         f"a{n(r)},{n(r)} 0 0 1 {n(r)},{n(-r)} z")
    return outline(d, solid=solid)


def cloud(x, y, s=1.0, solid=False, width=W):
    """Cloud whose flat bottom starts at (x, y) on the left; about 47*s wide and 35*s tall."""
    d = (f"M{n(x)},{n(y)} a{n(10 * s)},{n(10 * s)} 0 0 1 {n(-1 * s)},{n(-19.9 * s)} "
         f"a{n(13 * s)},{n(13 * s)} 0 0 1 {n(25 * s)},{n(-3 * s)} "
         f"a{n(11.5 * s)},{n(11.5 * s)} 0 0 1 {n(0)},{n(22.9 * s)} z")
    return outline(d, width=width, solid=solid)


def sparkle(cx, cy, s):
    q = s * 0.13
    d = (f"M{n(cx)},{n(cy - s)} Q{n(cx + q)},{n(cy - q)} {n(cx + s)},{n(cy)} Q{n(cx + q)},{n(cy + q)} {n(cx)},{n(cy + s)} "
         f"Q{n(cx - q)},{n(cy + q)} {n(cx - s)},{n(cy)} Q{n(cx - q)},{n(cy - q)} {n(cx)},{n(cy - s)} z")
    return fill(d) + stroke(d, 1.5)


# --- The glyphs, in the order of glyphs.txt.
def icons():
    g = {}

    # Handset shaped like a bent bone, ringing.
    g["phone"] = (stroke("M35,35 Q35,61 61,61", 7.5)
                  + knobs(35, 35, 0, -1, 5.2) + knobs(61, 61, 1, 0, 5.2)
                  + stroke("M49,30 A17,17 0 0 1 66,47", 3.6) + stroke("M49,38.5 A8.5,8.5 0 0 1 57.5,47", 3.6))

    g["messages"] = bubble(26, 28, 70, 58, 10) + bone(39, 43, 57, 43)

    # The shutter button on top is a bone.
    g["camera"] = box(26, 37, 70, 67, 7) + ring(48, 52, 7.5, 4) + bone(41, 30.5, 55, 30.5, 4)

    # The equator is a bone.
    g["browser"] = (ring(48, 48, 20) + stroke("M48,28 a9,20 0 1,0 0,40 a9,20 0 1,0 0,-40 z", 3.4)
                    + bone(25, 48, 71, 48, 4.6, halo=True))

    # A bone posted in an envelope.
    g["mail"] = (bone(42, 44, 56, 26, 4.6) + box(26, 38, 70, 68, 5, solid=True)
                 + line((28.5, 41), (48, 55), (67.5, 41)))

    # Treasure map: crossed bones mark the spot.
    g["maps"] = (line((26, 34), (40, 29), (56, 34), (70, 29), (70, 62), (56, 67), (40, 62), (26, 67), close=True)
                 + crossed(48, 48, 7.5))

    # A bone in the sky where the sun would be.
    g["photos"] = (box(26, 30, 70, 66, 6) + line((30, 62), (41, 49), (48, 56), (55, 50), (66, 62), width=4)
                   + bone(50, 40, 60, 38, 3.2))

    # Three crossed bones make a six-toothed gear.
    gear = []
    for deg in (90, 30, 150):
        dx, dy = math.cos(math.radians(deg)) * 21, math.sin(math.radians(deg)) * 21
        gear += bone(48 - dx, 48 - dy, 48 + dx, 48 + dy, 6, 4.3)
    g["settings"] = gear + dot(48, 48, 12) + dot(48, 48, 5.2, BG)

    # Bone hands.
    g["clock"] = (ring(48, 48, 20) + bone(48, 48, 48, 35.5, 3.6, ends=(False, True))
                  + bone(48, 48, 56.5, 53, 3.6, ends=(False, True)) + dot(48, 48, 3.2))

    g["calendar"] = (box(27, 31, 69, 68, 6) + line((27, 42), (69, 42), width=4)
                     + line((38, 25.5), (38, 33)) + line((58, 25.5), (58, 33)) + bone(40, 55, 56, 55))

    # A head on bone shoulders.
    g["contacts"] = ring(48, 37, 9) + bone(35, 60, 61, 60, 8.5, 5.8)

    g["files"] = (outline("M26,37 a4,4 0 0 1 4,-4 h10 l5,5 h21 a4,4 0 0 1 4,4 v21 a4,4 0 0 1 -4,4 h-36 a4,4 0 0 1 -4,-4 z")
                  + bone(40, 53, 56, 53))

    g["store"] = (line((31, 40), (65, 40), (68, 68), (28, 68), close=True)
                  + stroke("M40,40 v-4 a8,8 0 0 1 16,0 v4", 4) + bone(40, 56, 56, 52))

    # The beam joining the two notes is a bone.
    g["music"] = (fill("M30.5,63 a6,4.7 0 1,0 12,0 a6,4.7 0 1,0 -12,0 z") + fill("M53.5,59 a6,4.7 0 1,0 12,0 a6,4.7 0 1,0 -12,0 z")
                  + line((40.5, 63), (40.5, 37), width=4) + line((63.5, 59), (63.5, 33), width=4)
                  + bone(40.5, 36, 63.5, 32, 5.4, 4))

    # A screen on a bone stand.
    g["video"] = (box(26, 28, 70, 59, 6) + fill("M43.5,37 L55.5,43.5 L43.5,50 z") + stroke("M43.5,37 L55.5,43.5 L43.5,50 z", 2.5)
                  + bone(40, 68.5, 56, 68.5, 4))

    g["cloud"] = cloud(36, 65) + bone(40, 54, 56, 54, 4)

    g["notes"] = (box(30, 27, 66, 69, 5) + bone(39, 39, 57, 39, 3.8)
                  + line((37.5, 50), (58.5, 50), width=3.6) + line((37.5, 59), (50, 59), width=3.6))

    # The plus key is two crossed bones.
    g["calculator"] = (box(30, 26, 66, 70, 6) + line((38, 36), (58, 36), width=5)
                       + bone(40.5, 55, 55.5, 55, 3.4) + bone(48, 47.5, 48, 62.5, 3.4))

    # Sun behind a cloud, and it's raining a bone.
    sun = dot(60, 36, 7)
    for deg in (-150, -105, -60, -15, 30):
        c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
        sun += line((60 + c * 10.5, 36 + s * 10.5), (60 + c * 13.5, 36 + s * 13.5), width=3.2)
    g["weather"] = sun + cloud(35, 59, 0.78, solid=True) + bone(40, 69, 54, 67, 3.4)

    # A bone tucked in like a card.
    g["wallet"] = (bone(38, 40, 50, 25, 4.6) + box(26, 35, 70, 67, 6, solid=True)
                   + box(56, 45, 70, 57, 4, width=4, solid=True) + dot(62, 51, 1.8))

    # "A" on one side, bear language on the other.
    g["translate"] = (bubble(25, 26, 52, 49, 7) + stroke("M33,43.5 L38.5,31.5 L44,43.5 M35.2,39.5 H41.8", 3.2)
                      + bubble(44, 42, 71, 64, 7, tail="right", solid=True) + bone(52, 53, 63, 53, 3.2))

    # A microphone on a bone base.
    g["recorder"] = (fill(rrect_d(41, 24, 55, 50, 7)) + stroke("M34.5,44 a13.5,13.5 0 0 0 27,0", 4)
                     + line((48, 57.5), (48, 66), width=4) + bone(41, 68.5, 55, 68.5, 4))

    # A magic wand that is a bone.
    g["assistant"] = bone(33, 65, 52, 46, 5, 3.9) + sparkle(61, 35, 11.5) + sparkle(36.5, 37, 5) + sparkle(66, 59, 4.2)

    # The handle is a bone.
    g["search"] = ring(43, 43, 13, 5) + bone(53.5, 53.5, 66, 66, 5.6, 4.2, ends=(False, True))

    g["chat"] = (bubble(38, 26, 71, 49, 8, tail="right") + bubble(25, 40, 60, 62, 8, solid=True)
                 + bone(35.5, 51, 49.5, 51, 3.8))

    g["videocall"] = (box(25, 35, 56, 61, 6) + line((56, 45), (70, 37), (70, 59), (56, 51), close=True)
                      + bone(33.5, 48, 47.5, 48, 4))

    g["shield"] = (outline("M48,25 L67,32 V46 C67,58 58,66.5 48,71 C38,66.5 29,58 29,46 V32 Z")
                   + crossed(48, 46.5, 7))

    # The pin has found the buried bone.
    g["locate"] = (outline("M48,62 C40,52.5 34.5,46 34.5,38 a13.5,13.5 0 0 1 27,0 C61.5,46 56,52.5 48,62 z")
                   + dot(48, 38, 4.6) + bone(38, 70.5, 58, 70.5, 3.8))

    # One bone in the cart.
    g["shopping"] = (stroke("M24.5,32 H31 L36,58 H63 L67.5,40 H33")
                     + bone(47.5, 50, 55, 28, 4.4) + dot(40.5, 66, 3.4) + dot(59, 66, 3.4))

    # A controller is basically a fat bone.
    g["games"] = (line((33, 48), (63, 48), width=19)
                  + dot(32.5, 41.5, 9) + dot(32.5, 54.5, 9) + dot(63.5, 41.5, 9) + dot(63.5, 54.5, 9)
                  + line((33.5, 43.5), (33.5, 52.5), width=3.4, color=BG) + line((29, 48), (38, 48), width=3.4, color=BG)
                  + dot(58.5, 51.5, 2.7, BG) + dot(65, 45, 2.7, BG))

    # The slash is a bone.
    g["code"] = line((38, 36), (27, 48), (38, 60)) + line((58, 36), (69, 48), (58, 60)) + bone(43.5, 61, 52.5, 35, 4)

    # A bone tucked behind a busy thread.
    g["forum"] = (bone(55, 38, 67, 25, 4.4) + bubble(26, 34, 70, 60, 7, solid=True)
                  + dot(37, 47, 2.9) + dot(48, 47, 2.9) + dot(59, 47, 2.9))
    return g


def glyph_table():
    rows = []
    for raw in (here / "glyphs.txt").read_text().splitlines():
        if raw.strip() and not raw.startswith("#"):
            glyph, _, components = [part.strip() for part in raw.split("|", 2)]
            rows.append((glyph, [c.strip() for c in components.split(";") if c.strip()]))
    return rows


def disc():
    return circle_d(SIZE / 2, SIZE / 2, SIZE / 2)


def vector_xml(shapes):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           "<!-- Generated by tools/iconpack/make_icons.py; edit the script, not this file. -->",
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="96dp" android:height="96dp"',
           f'    android:viewportWidth="{SIZE}" android:viewportHeight="{SIZE}">']
    out.append(f'    <path android:fillColor="{BG}" android:pathData="{disc()}" />')
    out.append(f'    <group android:pivotX="{SIZE // 2}" android:pivotY="{SIZE // 2}" android:scaleX="{ZOOM}" android:scaleY="{ZOOM}">')
    for kind, d, width, color in shapes:
        if kind == "fill":
            out.append(f'        <path android:fillColor="{color}" android:pathData="{d}" />')
        else:
            out.append(f'        <path android:strokeColor="{color}" android:strokeWidth="{n(width)}" '
                       f'android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{d}" />')
    return "\n".join(out + ["    </group>", "</vector>", ""])


def svg_group(shapes):
    c = SIZE / 2
    out = [f'<path fill="{BG}" d="{disc()}"/><g transform="translate({c} {c}) scale({ZOOM}) translate({-c} {-c})">']
    for kind, d, width, color in shapes:
        if kind == "fill":
            out.append(f'<path fill="{color}" d="{d}"/>')
        else:
            out.append(f'<path fill="none" stroke="{color}" stroke-width="{n(width)}" stroke-linecap="round" '
                       f'stroke-linejoin="round" d="{d}"/>')
    return "".join(out) + "</g>"


def sheet_svg(names, drawn, cols=8, cell=132, label=True):
    rows = math.ceil(len(names) / cols)
    row_h = cell + (22 if label else 0)
    w, h = cols * cell + 36, rows * row_h + 36
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">',
           f'<rect width="{w}" height="{h}" fill="#ECEAF4"/>']
    for i, name in enumerate(names):
        x, y = 18 + (i % cols) * cell + (cell - SIZE) / 2, 18 + (i // cols) * row_h + (cell - SIZE) / 2
        out.append(f'<g transform="translate({n(x)},{n(y)})">{svg_group(drawn[name])}</g>')
        if label:
            out.append(f'<text x="{n(x + SIZE / 2)}" y="{n(y + SIZE + 20)}" text-anchor="middle" '
                       f'font-family="sans-serif" font-size="13" fill="#2A1F17">{name}</text>')
    return "\n".join(out + ["</svg>", ""])


def main():
    drawn = icons()
    table = glyph_table()
    missing = [glyph for glyph, _ in table if glyph not in drawn]
    if missing:
        sys.exit("glyphs.txt lists glyphs that make_icons.py doesn't draw: " + ", ".join(missing))

    out_dir = res / "drawable"
    for old in out_dir.glob("bb_*.xml"):
        old.unlink()
    items = []
    for glyph, components in table:
        (out_dir / f"bb_{glyph}.xml").write_text(vector_xml(drawn[glyph]))
        for c in components:
            items.append(f"    <item component={quoteattr('ComponentInfo{' + c + '}')} drawable=\"bb_{glyph}\" />")
    (res / "xml").mkdir(exist_ok=True)
    (res / "xml/appfilter.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/iconpack/make_icons.py from glyphs.txt -->\n'
        "<resources>\n" + "\n".join(items) + "\n</resources>\n")
    print(f"Wrote {len(table)} icons, mapped {len(items)} apps.")

    if len(sys.argv) > 1:
        Path(sys.argv[1]).write_text(sheet_svg([glyph for glyph, _ in table], drawn))
        print("Contact sheet:", sys.argv[1])


if __name__ == "__main__":
    main()
