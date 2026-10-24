"""Makes the iPhone app icon (1024x1024, no transparency, square corners - iOS rounds them itself) from
the same 12x12 pixel sprout as the Android icon (app/src/main/res/drawable/ic_launcher.xml).

    python iosApp/make_icon.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

SCALE = 1024 / 12
# (colour, [(x, y, w, h), ...]) in the 12x12 grid, copied from ic_launcher.xml
RECTS = [
    ("#2E5E3A", [(0, 0, 12, 12)]),
    ("#7A4A24", [(3, 9, 6, 2)]),
    ("#5BC236", [(5.5, 5, 1, 4)]),
    ("#7BE04A", [(2, 3, 3, 1), (3, 4, 3, 1), (7, 2, 3, 1), (6, 3, 3, 1), (6, 4, 1, 1)]),
]

img = Image.new("RGB", (1024, 1024))
draw = ImageDraw.Draw(img)
for colour, rects in RECTS:
    for x, y, w, h in rects:
        draw.rectangle([round(x * SCALE), round(y * SCALE), round((x + w) * SCALE) - 1, round((y + h) * SCALE) - 1], fill=colour)
out = Path(__file__).parent / "Resources" / "Assets.xcassets" / "AppIcon.appiconset" / "icon-1024.png"
out.parent.mkdir(parents=True, exist_ok=True)
img.save(out)
print(out)
