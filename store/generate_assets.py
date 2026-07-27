"""Regenerate the Play Store graphics from the app's launcher-icon motif.

Run from the repo root:  python store/generate_assets.py

Play requires a 32-bit PNG store icon at 512x512 and a feature graphic at
1024x500. Both are drawn here at 4x and downsampled, since PIL has no
antialiased stroking of its own.

The motif mirrors res/drawable/ic_launcher_foreground.xml — viewfinder corner
brackets around a spot-meter circle. Keep the two in step if either changes.
"""

import math
import os

from PIL import Image, ImageDraw, ImageFont

BG = (14, 14, 16)          # ic_launcher_background
BG_LIFT = (26, 26, 31)     # centre of the feature-graphic gradient
AMBER = (255, 183, 77)     # #FFB74D, the accent used throughout the app
DIM = (150, 150, 158)

SS = 4  # supersampling factor
OUT = os.path.dirname(os.path.abspath(__file__))

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\bahnschrift.ttf",
    r"C:\Windows\Fonts\ARIALNB.TTF",
    r"C:\Windows\Fonts\arialbd.ttf",
]

_CORNERS = ((-1, -1), (1, -1), (1, 1), (-1, 1))  # sign-x, sign-y


def font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default(size)


def fitted(d, text, size, max_width):
    """Largest font at or below `size` that keeps `text` inside `max_width`.
    Play crops the feature graphic on some surfaces, so nothing may run to
    the edge and get clipped."""
    while size > 8:
        f = font(size)
        if d.textlength(text, font=f) <= max_width:
            return f
        size = int(size * 0.95)
    return font(size)


def draw_viewfinder(d, cx, cy, half, stroke):
    """Four rounded corner brackets, a spot circle, and a centre dot."""
    radius = half * 0.125   # corner rounding
    arm = half * 0.30       # straight run of each bracket
    cap = stroke / 2

    for sx, sy in _CORNERS:
        x, y = cx + sx * half, cy + sy * half
        ox, oy = x - sx * radius, y - sy * radius  # centre of the corner arc

        # One polyline per bracket, arc included: drawing the arc separately
        # leaves a visible step where it meets the straight runs.
        pts = [(x - sx * (radius + arm), y)]
        a0 = 90 if sy > 0 else 270          # where the horizontal run meets the arc
        a1 = 0 if sx > 0 else 180           # where the arc meets the vertical run
        sweep = ((a1 - a0 + 180) % 360) - 180
        for i in range(13):
            t = math.radians(a0 + sweep * i / 12)
            pts.append((ox + radius * math.cos(t), oy + radius * math.sin(t)))
        pts.append((x, y - sy * (radius + arm)))

        d.line(pts, fill=AMBER, width=stroke, joint="curve")
        for px, py in (pts[0], pts[-1]):  # stand in for round line caps
            d.ellipse([px - cap, py - cap, px + cap, py + cap], fill=AMBER)

    spot = half * 0.46
    d.ellipse([cx - spot, cy - spot, cx + spot, cy + spot], outline=AMBER, width=stroke)

    dot = half * 0.13
    d.ellipse([cx - dot, cy - dot, cx + dot, cy + dot], fill=AMBER)


def store_icon(size=512):
    # Play asks for a 32-bit PNG here, so this one keeps an alpha channel even
    # though the artwork is fully opaque. The feature graphic stays 24-bit.
    S = size * SS
    img = Image.new("RGBA", (S, S), BG + (255,))
    d = ImageDraw.Draw(img)
    draw_viewfinder(d, S / 2, S / 2, half=S * 0.29, stroke=int(S * 0.032))
    img = img.resize((size, size), Image.LANCZOS)
    path = os.path.join(OUT, "play-icon-512.png")
    img.save(path)
    return path


def feature_graphic(w=1024, h=500):
    W, H = w * SS, h * SS
    img = Image.new("RGB", (W, H), BG)

    # Soft radial lift behind the mark so the panel does not read as flat black.
    glow = Image.new("L", (W, H), 0)
    gd = ImageDraw.Draw(glow)
    gcx, gcy = W * 0.27, H * 0.5
    for i in range(60, 0, -1):
        rr = W * 0.42 * (i / 60)
        gd.ellipse([gcx - rr, gcy - rr, gcx + rr, gcy + rr], fill=int(255 * (1 - i / 60) ** 2))
    img = Image.composite(Image.new("RGB", (W, H), BG_LIFT), img, glow)

    d = ImageDraw.Draw(img)
    draw_viewfinder(d, gcx, gcy, half=H * 0.30, stroke=int(H * 0.030))

    x = W * 0.47
    avail = W * 0.92 - x  # keep an 8% right margin clear of the crop
    line1 = "THIRD STOP"
    line2 = "A light meter for film cameras"
    line3 = "Reflective + incident  ·  reciprocity  ·  no tracking"

    d.text((x, H * 0.395), line1, font=fitted(d, line1, int(H * 0.135), avail),
           fill=(255, 255, 255), anchor="lm")
    d.text((x, H * 0.565), line2, font=fitted(d, line2, int(H * 0.058), avail),
           fill=AMBER, anchor="lm")
    d.text((x, H * 0.655), line3, font=fitted(d, line3, int(H * 0.048), avail),
           fill=DIM, anchor="lm")

    img = img.resize((w, h), Image.LANCZOS)
    path = os.path.join(OUT, "play-feature-graphic-1024x500.png")
    img.save(path)
    return path


if __name__ == "__main__":
    for p in (store_icon(), feature_graphic()):
        print("wrote", p)
