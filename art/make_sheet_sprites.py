"""Cut Jason's character sheet (art/source/character-sheet.jpg) into game sprites.

The sheet has 4 poses per character on a flat grey background. The printed labels on the sheet
are wrong, so characters are assigned by look (see ROWS). For each pose:
  background key -> keep the figure -> (Maui) orange to brown, (Kara) black to charcoal ->
  denoise -> shrink to pixel size per life stage -> clean edges -> 16 colors -> dark outline.

Writes char_<name>_<stage>_<pose>.png (pose 0-3) into <out_dir>, and for fine-tuning,
one big preview file per character into art/build/preview/.

Usage (pip install opencv-python-headless pillow numpy):
  python art/make_sheet_sprites.py app/src/main/res/drawable-nodpi
"""
import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image

HERE = Path(__file__).parent
OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
PREVIEW = HERE / "build" / "preview"
PREVIEW.mkdir(parents=True, exist_ok=True)

def load(name):
    return cv2.cvtColor(cv2.imread(str(HERE / "source" / name)), cv2.COLOR_BGR2RGB)


# Sheet 1 (the first character sheet): rows top to bottom, 4 poses per character in a row.
ROWS = [
    ((120, 520), ["jason", "kona", "jun"]),      # straw hat / red hair / pink hoodie
    ((780, 1100), ["rusty", "kara"]),            # sable collie / tricolor collie
    ((1240, 1520), ["maui", "dj"]),              # cat (recolored brown) / tuxedo cat
]
# Sheet 2 (Karta and Nathan): each character is a 2x2 block - top row stand, wave;
# bottom row cheer, walk. Two row bands; each band holds both characters' two poses.
GRID_SHEET = "sheet-karta-nathan.jpg"
GRID_BANDS = [(50, 300), (305, 570)]
GRID_NAMES = ["karta", "nathan"]

# Pixel height of the ADULT sprite, per kind; baby and kid are made smaller from the same pose.
ADULT_PX = {"jason": 64, "kona": 64, "jun": 64, "karta": 64, "nathan": 64, "rusty": 50, "kara": 50, "maui": 34, "dj": 34}
STAGES = {"baby": 0.62, "kid": 0.8, "adult": 1.0, "old": 0.97}
COLORS = 16


def background_mask(img):
    """True where the pixel is the sheet's flat grey-blue background (it has a slight gradient,
    so compare with the background color of each column's top and bottom edge)."""
    bg = np.array([201, 204, 216], dtype=np.int32)
    d = np.abs(img.astype(np.int32) - bg).sum(axis=2)
    return d < 34


def figures_in(y0, y1):
    """Connected figures in a row band, left to right, ignoring the printed labels (thin, short text)."""
    band = sheet[y0:y1]
    fg = (~background_mask(band)).astype(np.uint8)
    fg = cv2.morphologyEx(fg, cv2.MORPH_CLOSE, np.ones((7, 7), np.uint8))
    joined = cv2.dilate(fg, np.ones((5, 5), np.uint8))            # stick a tail tip to its body (hat brims nearly touch, so keep it small)
    n, labels, stats, _ = cv2.connectedComponentsWithStats(joined, 8)
    found = []
    for i in range(1, n):
        x, y, w, h, area = stats[i]
        if h < 90 or area < 6000:                                  # labels and specks
            continue
        found.append((x, y0 + y, w, h, labels == i))
    found.sort(key=lambda f: f[0])
    return found, fg


def cut(fig, fg_band, y0):
    x, y, w, h, comp = fig
    sub_fg = fg_band[y - y0:y - y0 + h, x:x + w] & comp[y - y0:y - y0 + h, x:x + w].astype(np.uint8)
    rgb = sheet[y:y + h, x:x + w]
    # keep the largest solid piece, and fill enclosed holes that are really background-colored
    # highlights (e.g. a white collar touching the grey) - only fill tiny ones
    n, labels, stats, _ = cv2.connectedComponentsWithStats(sub_fg, 8)
    if n > 1:
        biggest = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
        keep = np.isin(labels, [i for i in range(1, n) if stats[i, cv2.CC_STAT_AREA] > 150 or i == biggest])
        sub_fg = keep.astype(np.uint8)
    inv = (1 - sub_fg).astype(np.uint8)
    n, labels, stats, _ = cv2.connectedComponentsWithStats(inv, 4)
    for i in range(1, n):
        bx, by, bw, bh, area = stats[i]
        if bx > 0 and by > 0 and bx + bw < w and by + bh < h and area < 60:
            sub_fg[labels == i] = 1
    return np.dstack([rgb, sub_fg * 255]).astype(np.uint8)


def orange_to_brown(rgba):
    hsv = cv2.cvtColor(np.ascontiguousarray(rgba[..., :3]), cv2.COLOR_RGB2HSV).astype(np.int32)
    h, s, v = hsv[..., 0], hsv[..., 1], hsv[..., 2]
    orange = (h >= 5) & (h <= 24) & (s > 90)
    hsv[..., 0] = np.where(orange, 12, h)
    hsv[..., 1] = np.where(orange, s * 0.62, s)
    hsv[..., 2] = np.where(orange, v * 0.62, v)
    rgba[..., :3] = cv2.cvtColor(hsv.clip(0, 255).astype(np.uint8), cv2.COLOR_HSV2RGB)
    return rgba


def black_to_charcoal(rgba):
    """Kara is charcoal-grey, not jet black (from Jason's photos): lift the dark, colorless fur."""
    hsv = cv2.cvtColor(np.ascontiguousarray(rgba[..., :3]), cv2.COLOR_RGB2HSV).astype(np.int32)
    dark = (hsv[..., 2] < 90) & (hsv[..., 1] < 120)
    v = hsv[..., 2]
    hsv[..., 2] = np.where(dark, 52 + v * 0.75, v)
    hsv[..., 1] = np.where(dark, hsv[..., 1] * 0.35, hsv[..., 1])
    rgba[..., :3] = cv2.cvtColor(hsv.clip(0, 255).astype(np.uint8), cv2.COLOR_HSV2RGB)
    return rgba


def tidy_alpha(a):
    on = (a > 0).astype(np.uint8)
    pad = np.pad(on, 1)
    n = pad[:-2, 1:-1] + pad[2:, 1:-1] + pad[1:-1, :-2] + pad[1:-1, 2:]
    on = np.where((on == 1) & (n < 2), 0, on)
    on = np.where((on == 0) & (n >= 3), 1, on)
    return on * 255


def outline(arr):
    arr = np.pad(arr, ((1, 1), (1, 1), (0, 0)))
    on = arr[..., 3] > 0
    out = arr.copy()
    hh, ww = on.shape
    for y in range(hh):
        for x in range(ww):
            if on[y, x]:
                continue
            for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                ny, nx = y + dy, x + dx
                if 0 <= ny < hh and 0 <= nx < ww and on[ny, nx]:
                    out[y, x, :3] = (arr[ny, nx, :3] * 0.30).astype(np.uint8)
                    out[y, x, 3] = 255
                    break
    return out


def pixelate(rgba, height, ref_height):
    """Shrink so the figure's height (measured against the tallest pose) becomes [height] pixels."""
    rgb = np.ascontiguousarray(rgba[..., :3])
    clean = rgba.copy()
    clean[..., :3] = cv2.bilateralFilter(cv2.medianBlur(rgb, 3), 7, 40, 7)
    scale = height / ref_height
    im = Image.fromarray(clean, "RGBA").convert("RGBa")
    w, h = max(1, round(im.width * scale)), max(1, round(im.height * scale))
    arr = np.array(im.resize((w, h), Image.Resampling.BOX).convert("RGBA"))
    arr[..., 3] = np.where(arr[..., 3] >= 120, 255, 0)
    arr[..., 3] = tidy_alpha(arr[..., 3])
    solid = arr[..., 3] > 0
    q = Image.fromarray(arr[..., :3]).quantize(colors=COLORS, method=Image.Quantize.MEDIANCUT,
                                               dither=Image.Dither.NONE).convert("RGB")
    arr[..., :3] = np.array(q)
    arr[~solid, :3] = 0
    return outline(arr)


PEOPLE = {"jason", "kona", "jun", "karta", "nathan"}


def resize_rgba(a, w, h):
    return np.array(Image.fromarray(a, "RGBA").convert("RGBa").resize((max(1, w), max(1, h)), Image.Resampling.LANCZOS).convert("RGBA"))


def proportions(rgba, head_frac, head_keep, body_keep):
    """Re-proportion a figure: the top [head_frac] (head) is scaled by [head_keep], the rest
    (body and legs) squashed to [body_keep] of its height. Babies are mostly head."""
    h, w = rgba.shape[:2]
    cut_y = int(h * head_frac)
    head = rgba[:cut_y]
    body = rgba[cut_y:]
    head = resize_rgba(head, int(w * head_keep), int(head.shape[0] * head_keep))
    body = resize_rgba(body, int(w * (body_keep * 0.35 + 0.65 * head_keep)), int(body.shape[0] * body_keep))
    W = max(head.shape[1], body.shape[1])
    out = np.zeros((head.shape[0] + body.shape[0] - 2, W, 4), np.uint8)
    bx = (W - body.shape[1]) // 2
    out[head.shape[0] - 2:, bx:bx + body.shape[1]] = body
    hx = (W - head.shape[1]) // 2
    m = head[..., 3] > 0
    out[:head.shape[0], hx:hx + head.shape[1]][m] = head[m]
    return out


def grow_old(rgba, name):
    """Old age: grey hair and beard for people (only in the top of the figure, so skin and
    clothes keep their color); a greyer, softer coat for the animals."""
    out = rgba.copy()
    hsv = cv2.cvtColor(np.ascontiguousarray(rgba[..., :3]), cv2.COLOR_RGB2HSV).astype(np.int32)
    hh, s, v = hsv[..., 0], hsv[..., 1], hsv[..., 2]
    if name in PEOPLE:
        top = np.zeros(v.shape, bool)
        top[: int(v.shape[0] * 0.50)] = True
        if name == "kona":
            hair = ((hh <= 10) | (hh >= 170)) & (s > 110) & (v > 60)             # red hair
        elif name == "karta":
            # reddish-brown hair, close in colour to the maroon shirt: look only at the top third
            top[int(v.shape[0] * 0.36):] = False
            hair = ((hh <= 22) | (hh >= 160)) & (s > 50) & (v > 30) & (v < 150)
        elif name == "nathan":
            hair = (hh >= 5) & (hh <= 25) & (s > 45) & (v > 60) & (v < 190)      # light brown hair and beard
        else:
            hair = (hh >= 3) & (hh <= 28) & (s > 50) & (v > 35) & (v < 115)      # dark brown hair, beard
        hair &= top & (rgba[..., 3] > 0)
        # only the hair itself: the biggest connected patch of hair color (plus, for Jason, his beard
        # under the hat) - so lips, scarf, and darker skin shading keep their own colors
        n, labels, stats, _ = cv2.connectedComponentsWithStats(hair.astype(np.uint8), 8)
        if n > 1:
            order = 1 + np.argsort(-stats[1:, cv2.CC_STAT_AREA])
            keep_n = 4 if name in ("jason", "nathan") else 1        # bearded: beard and sideburns too
            keep = [i for i in order[:keep_n] if stats[i, cv2.CC_STAT_AREA] > 0.03 * stats[order[0], cv2.CC_STAT_AREA]]
            hair = np.isin(labels, keep)
        grey = np.clip(v * 0.9 + 95, 0, 235)
        out[hair, 0] = grey[hair]; out[hair, 1] = grey[hair]; out[hair, 2] = (grey[hair] * 1.02).clip(0, 255)
    else:
        rgb = rgba[..., :3].astype(np.float32)
        lum = (rgb @ np.array([0.3, 0.59, 0.11], np.float32))[..., None]
        out[..., :3] = np.clip(rgb * 0.62 + lum * 0.38 + 10, 0, 255).astype(np.uint8)
    return out


def wrinkles(arr):
    """Old-age wrinkles on a finished (pixel-size) person sprite: a forehead line and crow's feet,
    drawn one pixel at a time in a darker shade of the skin right under them."""
    out = arr.copy()
    h, w = arr.shape[:2]
    hsv = cv2.cvtColor(np.ascontiguousarray(arr[..., :3]), cv2.COLOR_RGB2HSV).astype(np.int32)
    skin = (hsv[..., 0] >= 4) & (hsv[..., 0] <= 24) & (hsv[..., 1] >= 40) & (hsv[..., 1] <= 190) \
        & (hsv[..., 2] >= 110) & (arr[..., 3] > 0)
    skin[int(h * 0.5):] = False                                   # the face is in the top half
    n, labels, stats, _ = cv2.connectedComponentsWithStats(skin.astype(np.uint8), 4)
    if n < 2:
        return out
    face = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    fx, fy, fw, fh, _ = stats[face]
    if fw < 6 or fh < 6:
        return out
    m = labels == face

    def mark(x, y):
        if 0 <= y < h and 0 <= x < w and m[y, x]:
            out[y, x, :3] = (arr[y, x, :3] * 0.72).astype(np.uint8)

    # forehead: a short line across the middle of the top of the face (skipped where hair or a hat covers it)
    for x in range(fx + fw // 3, fx + fw - fw // 3):
        mark(x, fy + 1)
    # crow's feet: little two-pixel diagonals at the outer corners, about eye height
    ey = fy + int(fh * 0.42)
    for dx, dy in ((0, 0), (-1, 1)):
        mark(fx + 1 + dx, ey + dy)
        mark(fx + fw - 2 - dx, ey + dy)
    # smile lines beside the mouth
    my = fy + int(fh * 0.72)
    mark(fx + fw // 4, my)
    mark(fx + fw - 1 - fw // 4, my)
    return out


def sick(rgba):
    """Under the weather: colors washed out toward a pale green-grey, and a slight droop."""
    out = rgba.copy()
    rgb = rgba[..., :3].astype(np.float32)
    lum = (rgb @ np.array([0.3, 0.59, 0.11], np.float32))[..., None]
    queasy = np.array([150, 185, 140], np.float32)
    out[..., :3] = np.clip(rgb * 0.55 + lum * 0.25 + queasy * 0.20, 0, 255).astype(np.uint8)
    h, w = out.shape[:2]
    return resize_rgba(out, int(w * 1.02), int(h * 0.97))


def squash(rgba, sx, sy):
    """Landing squash (sx>1, sy<1) or take-off stretch (sx<1, sy>1) of a pose."""
    h, w = rgba.shape[:2]
    return resize_rgba(rgba, int(w * sx), int(h * sy))


def bottom_align(frames):
    """Pad poses to one common size, feet on the bottom row, centred, so they don't jump around."""
    w = max(f.shape[1] for f in frames)
    h = max(f.shape[0] for f in frames)
    out = []
    for f in frames:
        canvas = np.zeros((h, w, 4), np.uint8)
        x = (w - f.shape[1]) // 2
        canvas[h - f.shape[0]:, x:x + f.shape[1]] = f
        out.append(canvas)
    return out


# Collect each character's 4 drawn poses (stand, wave, cheer, walk/step) from whichever sheet has them.
drawn = {}
sheet = load("character-sheet.jpg")
for (y0, y1), names in ROWS:
    figs, fg_band = figures_in(y0, y1)
    expected = 4 * len(names)
    if len(figs) != expected:
        sys.exit(f"row {y0}-{y1}: found {len(figs)} figures, expected {expected}: {[f[:4] for f in figs]}")
    for ci, name in enumerate(names):
        drawn[name] = [cut(f, fg_band, y0) for f in figs[ci * 4:(ci + 1) * 4]]

sheet = load(GRID_SHEET)
for band, (y0, y1) in enumerate(GRID_BANDS):
    figs, fg_band = figures_in(y0, y1)
    if len(figs) != 2 * len(GRID_NAMES):
        sys.exit(f"grid band {y0}-{y1}: found {len(figs)} figures: {[f[:4] for f in figs]}")
    for ci, name in enumerate(GRID_NAMES):
        drawn.setdefault(name, []).extend(cut(f, fg_band, y0) for f in figs[ci * 2:(ci + 1) * 2])

only = set(sys.argv[2:])          # optional: just these characters (leaves the others' files alone)
rows_by_stage = {}
for name, poses in drawn.items():
    if only and name not in only:
        continue
    if True:
        if name == "maui":
            poses = [orange_to_brown(p) for p in poses]
        if name == "kara":
            poses = [black_to_charcoal(p) for p in poses]
        # two extra poses made from the standing pose: 4 = landing squash, 5 = jump stretch
        # kept subtle (Jason: the 12% squash read as "short Jason"), just enough to feel bouncy
        poses = poses + [squash(poses[0], 1.04, 0.95), squash(poses[0], 0.97, 1.03)]
        # 6 = sick: the standing pose, pale and droopy
        poses = poses + [sick(poses[0])]
        ref = max(p.shape[0] for p in poses[:4])
        for stage, k in STAGES.items():
            if stage == "old":
                staged = [grow_old(p, name) for p in poses]
            elif stage == "baby":
                staged = [proportions(p, 0.46 if name in PEOPLE else 0.5, 0.9, 0.55) for p in poses]
            elif stage == "kid":
                staged = [proportions(p, 0.44 if name in PEOPLE else 0.5, 0.95, 0.8) for p in poses]
            else:
                staged = poses
            px = max(8, round(ADULT_PX[name] * k))
            sref = ref * (max(s.shape[0] for s in staged[:4]) / ref)
            frames = [pixelate(p, px * p.shape[0] / sref, p.shape[0]) for p in staged]
            if stage == "old" and name in PEOPLE:
                frames = [wrinkles(f) for f in frames]
            frames = bottom_align(frames)
            for i, fr in enumerate(frames):
                Image.fromarray(fr, "RGBA").save(OUT / f"char_{name}_{stage}_{i}.png")
            rows_by_stage.setdefault(name, {})[stage] = frames
        # fine-tuning preview: one file per character, a row per life stage, all 6 poses, 6x
        rows = []
        for stage in STAGES:
            fr = rows_by_stage[name][stage]
            rows.append(np.concatenate([np.pad(f, ((3, 3), (3, 3), (0, 0))) for f in fr], axis=1))
        width = max(r.shape[1] for r in rows)
        rows = [np.pad(r, ((0, 0), (0, width - r.shape[1]), (0, 0))) for r in rows]
        grid = np.concatenate(rows, axis=0)
        prev = Image.new("RGBA", (grid.shape[1], grid.shape[0]), (201, 204, 216, 255))
        prev.alpha_composite(Image.fromarray(grid, "RGBA"))
        prev.resize((prev.width * 6, prev.height * 6), Image.Resampling.NEAREST).save(PREVIEW / f"{name}.png")
        print(f"{name:6s} stages={list(STAGES)} poses={len(rows_by_stage[name]['adult'])}")
print("ok")
