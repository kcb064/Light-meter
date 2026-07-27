"""Make phone screenshots acceptable to Play, then drop them in screenshots/.

    python store/prepare_screenshot.py ~/Downloads/Screenshot_*.png

Play rejects phone screenshots whose long side is more than twice the short
side, and most current phones are taller than that (1080x2400 is 2.22:1). Rather
than crop away UI, this pads the short axis out to exactly 2:1 with the app's
own background colour, which is near-invisible against Third Stop's dark
screens.

Files are written to store/screenshots/ under their original name. Rename them
to the 01-..08- prefixes afterwards — Play shows screenshots in upload order.
"""

import os
import shutil
import sys

from PIL import Image

BG = (14, 14, 16)  # ic_launcher_background, matches the app's dark surfaces
MAX_RATIO = 2.0
MIN_SIDE, MAX_SIDE = 320, 3840

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "screenshots")


def prepare(path):
    img = Image.open(path)
    if img.mode not in ("RGB", "RGBA"):
        img = img.convert("RGB")
    w, h = img.size
    notes = []

    if max(w, h) > MAX_SIDE:
        scale = MAX_SIDE / max(w, h)
        w, h = round(w * scale), round(h * scale)
        img = img.resize((w, h), Image.LANCZOS)
        notes.append(f"downscaled to {w}x{h}")

    # Pad the short axis until the frame is within Play's 2:1 limit.
    tw = max(w, round(h / MAX_RATIO)) if h > w else w
    th = max(h, round(w / MAX_RATIO)) if w > h else h
    if (tw, th) != (w, h):
        canvas = Image.new(img.mode, (tw, th), BG + ((255,) if img.mode == "RGBA" else ()))
        canvas.paste(img, ((tw - w) // 2, (th - h) // 2))
        img = canvas
        notes.append(f"padded {w}x{h} -> {tw}x{th}")
        w, h = tw, th

    if min(w, h) < MIN_SIDE:
        notes.append(f"WARNING: {min(w, h)}px short side is below Play's {MIN_SIDE}px minimum")

    os.makedirs(OUT_DIR, exist_ok=True)
    dest = os.path.join(OUT_DIR, os.path.basename(path))
    if not notes:
        shutil.copyfile(path, dest)
        notes.append("already compliant, copied as-is")
    else:
        img.save(dest)
    return dest, notes


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    for src in sys.argv[1:]:
        dest, notes = prepare(src)
        print(f"{os.path.basename(src)} -> {dest}\n    {'; '.join(notes)}")
