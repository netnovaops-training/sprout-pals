"""Cut the seven characters out of Jason's reference frame with OpenCV GrabCut, then pixelate.

GrabCut starts from a box around the character plus hints: patches that are surely the
character (fg) and patches that are surely something else, like a neighbour overlapping (bg).
Maui (the orange cat in the frame) is recolored brown, per Jason.

Usage (needs: pip install opencv-python-headless pillow numpy):
  python art/make_sprites.py app/src/main/res/drawable-nodpi 0.25
  scale = sprite size as a fraction of the frame size (0.25 = 4x smaller)

Source: art/source/reference-frame.png, the first frame of Jason's reference video.
Writes char_<name>_<baby|kid|adult>.png into <out_dir>; full-size cutouts, debug files and
contact sheets go to art/build/ (not committed).
"""
import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image

HERE = Path(__file__).parent
frame_bgr = cv2.imread(str(HERE / "source" / "reference-frame.png"))
frame2_bgr = cv2.imread(str(HERE / "source" / "reference-frame-2.png"))
OUT = Path(sys.argv[1])
BUILD = HERE / "build"
BUILD.mkdir(exist_ok=True)
SCALE = float(sys.argv[2]) if len(sys.argv) > 2 else 0.25
OUT.mkdir(parents=True, exist_ok=True)

# name: (box, [sure-character patches], [sure-not-character patches])   all in frame pixels
CAST = {
    # Kona and Jun are cut at the waist (Rusty and Kara's tail hide their legs); legs are added below.
    "kona":  ((188, 168, 402, 386), [(230, 215, 330, 300), (240, 300, 320, 360)],
              [(150, 300, 232, 410), (372, 318, 470, 410)]),
    "jason": ((508, 268, 802, 618), [(580, 330, 720, 560), (530, 290, 760, 360)],
              [(508, 268, 545, 300), (735, 268, 802, 300), (508, 450, 540, 618), (770, 450, 802, 618)]),
    "jun":   ((893, 158, 1188, 408), [(960, 190, 1080, 400), (905, 262, 950, 300)],
              [(828, 300, 960, 418), (1095, 345, 1230, 482), (893, 185, 956, 262), (1074, 185, 1128, 258),
               (1000, 345, 1060, 410)]),
    "rusty": ((150, 300, 497, 607), [(250, 400, 440, 520), (190, 470, 260, 560)],
              [(236, 280, 362, 340), (330, 280, 400, 330)]),
    "kara":  ((790, 403, 1082, 622), [(850, 460, 1030, 560)], [(990, 398, 1035, 470)]),
    "maui":  ((830, 290, 984, 417), [(850, 320, 940, 395)], [(955, 300, 990, 420)]),
    "dj":    ((1090, 338, 1230, 482), [(1110, 380, 1200, 455)], []),
}


# Segment Anything prompts: (box, points on the character, points on neighbours to leave out).
# Better than GrabCut at white fur against the bright lawn; used when rembg[sam] is installed.
# Kona and Jun come from the second frame (3.25 s into the video), where nobody stands in front of them.
SAM_PROMPTS = {
    "kona":  ((185, 165, 412, 505), [(280, 255), (285, 340), (385, 300), (250, 200), (265, 430), (250, 485), (310, 485)],
              [(130, 300), (420, 420)], 2),
    "jason": ((505, 277, 805, 620), [(640, 420), (640, 320), (770, 415), (560, 520), (620, 590), (680, 590)],
              [(640, 268)]),
    "jun":   ((912, 150, 1185, 492), [(1030, 260), (1050, 360), (935, 255), (1160, 285), (1040, 190), (1010, 440),
                                      (1070, 440), (990, 465), (1075, 470)],
              [(885, 350), (1150, 410), (1190, 320), (928, 205), (915, 290), (912, 240), (918, 275), (908, 320)], 2),
    # extra points all over the white collar/ruff so none of it is left behind
    "rusty": ((145, 285, 500, 612), [(330, 440), (410, 400), (430, 470), (200, 520), (195, 340), (330, 580), (440, 560),
                                     (350, 470), (380, 500), (410, 520), (440, 500), (455, 460), (360, 420), (395, 440),
                                     # his dark sable head, ears and the dark fur around the collar
                                     (420, 325), (440, 345), (455, 385), (430, 365), (400, 380), (380, 410), (300, 400),
                                     (470, 420)],
              [(280, 330), (270, 250)]),
    "kara":  ((785, 390, 1088, 625), [(980, 500), (850, 480), (900, 520), (1060, 420), (830, 600), (1000, 600)],
              [(1020, 440)]),
    "maui":  ((828, 288, 988, 420), [(880, 350), (930, 380), (860, 400)], [(985, 330)]),
    "dj":    ((1088, 335, 1232, 485), [(1160, 410), (1215, 360), (1120, 460)], []),
}
_sam = None


def sam_cutout(name):
    """Cut [name] with Segment Anything; returns None if rembg's SAM model isn't available."""
    global _sam
    try:
        from rembg import new_session, remove
        if _sam is None:
            _sam = new_session("sam")
    except Exception:
        return None
    box, pos, neg, *which = SAM_PROMPTS[name]
    prompt = [{"type": "rectangle", "label": 1, "data": list(box)}]
    prompt += [{"type": "point", "label": 1, "data": list(p)} for p in pos]
    prompt += [{"type": "point", "label": 0, "data": list(p)} for p in neg]
    src = frame2_bgr if which and which[0] == 2 else frame_bgr
    rgb = cv2.cvtColor(src, cv2.COLOR_BGR2RGB)
    out = np.array(remove(Image.fromarray(rgb), session=_sam, sam_prompt=prompt)).copy()
    x0, y0, x1, y1 = box
    rgba = out[y0:y1, x0:x1].copy()
    rgba[..., 3] = np.where(rgba[..., 3] > 127, 255, 0)
    keep = (rgba[..., 3] > 0).astype(np.uint8)
    n, labels, stats, _ = cv2.connectedComponentsWithStats(keep, 8)
    if n > 1:
        biggest = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
        rgba[labels != biggest, 3] = 0
    rgba = strip_outside_green(rgba)
    ys, xs = np.nonzero(rgba[..., 3])
    return rgba[ys.min():ys.max() + 1, xs.min():xs.max() + 1].copy()


def cutout(box, fg, bg):
    mask = np.zeros(frame_bgr.shape[:2], np.uint8)          # 0 = sure background outside the box
    x0, y0, x1, y1 = box
    mask[y0:y1, x0:x1] = cv2.GC_PR_FGD
    for a, b, c, d in bg:
        mask[b:d, a:c] = np.where(mask[b:d, a:c] == cv2.GC_PR_FGD, cv2.GC_BGD, mask[b:d, a:c])
    for a, b, c, d in fg:
        mask[b:d, a:c] = cv2.GC_FGD
    bgd, fgd = np.zeros((1, 65), np.float64), np.zeros((1, 65), np.float64)
    cv2.grabCut(frame_bgr, mask, None, bgd, fgd, 10, cv2.GC_INIT_WITH_MASK)
    keep = np.where((mask == cv2.GC_FGD) | (mask == cv2.GC_PR_FGD), 255, 0).astype(np.uint8)
    # tidy: close pin-holes, keep the largest connected piece
    keep = cv2.morphologyEx(keep, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    n, labels, stats, _ = cv2.connectedComponentsWithStats(keep, 8)
    if n > 1:
        biggest = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
        keep = np.where(labels == biggest, 255, 0).astype(np.uint8)
    rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
    rgba = np.dstack([rgb, keep])[y0:y1, x0:x1].copy()
    rgba = strip_outside_green(rgba)
    ys, xs = np.nonzero(rgba[..., 3])
    return rgba[ys.min():ys.max() + 1, xs.min():xs.max() + 1].copy()


def strip_outside_green(rgba):
    """Remove leftover lawn and leaves: green pixels reachable from the outside through other green pixels.
    Green inside the character (DJ's eyes, Kona's scarf) isn't reachable from outside, so it stays."""
    hsv = cv2.cvtColor(np.ascontiguousarray(rgba[..., :3]), cv2.COLOR_RGB2HSV)
    green = (hsv[..., 0] >= 33) & (hsv[..., 0] <= 95) & (hsv[..., 1] > 70) & (hsv[..., 2] > 50)
    opaque = rgba[..., 3] > 0
    h, w = opaque.shape
    gone = np.zeros_like(opaque)
    stack = [(y, x) for y in range(h) for x in range(w) if not opaque[y, x]]
    for y in range(h):
        for x in (0, w - 1):
            if green[y, x]:
                stack.append((y, x))
    for x in range(w):
        for y in (0, h - 1):
            if green[y, x]:
                stack.append((y, x))
    seen = np.zeros_like(opaque)
    while stack:
        y, x = stack.pop()
        if seen[y, x]:
            continue
        seen[y, x] = True
        for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
            if 0 <= ny < h and 0 <= nx < w and not seen[ny, nx] and opaque[ny, nx] and green[ny, nx]:
                gone[ny, nx] = True
                stack.append((ny, nx))
    rgba[gone, 3] = 0
    # drop specks left behind
    keep = (rgba[..., 3] > 0).astype(np.uint8)
    n, labels, stats, _ = cv2.connectedComponentsWithStats(keep, 8)
    if n > 1:
        biggest = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
        rgba[labels != biggest, 3] = 0
    return rgba


def recolor(rgba, rules):
    """rules: list of (hue_lo, hue_hi, sat_min, val_lo, val_hi, new_rgb) in OpenCV HSV; keeps shading."""
    hsv = cv2.cvtColor(np.ascontiguousarray(rgba[..., :3]), cv2.COLOR_RGB2HSV).astype(np.int32)
    out = rgba.copy()
    for hlo, hhi, smin, vlo, vhi, new in rules:
        m = (hsv[..., 0] >= hlo) & (hsv[..., 0] <= hhi) & (hsv[..., 1] >= smin) & (hsv[..., 2] >= vlo) & (hsv[..., 2] <= vhi) & (rgba[..., 3] > 0)
        shade = (hsv[..., 2] / 255.0)[..., None]
        base = np.array(new, dtype=np.float64)[None, None, :]
        out[m, :3] = np.clip(base * (0.55 + 0.75 * shade), 0, 255)[m].astype(np.uint8)
    return out


def with_legs(top, legs):
    """Stack a waist-up cutout on a pair of legs, centred, the legs scaled to the waist width."""
    rows = np.nonzero(top[..., 3].any(axis=1))[0]
    # the waist = the bottom 15 rows; their widest extent sets the leg width
    bottom_band = (top[rows[-1] - 14:rows[-1] + 1, :, 3] > 0).any(axis=0)
    cols = np.nonzero(bottom_band)[0]
    # but don't count a raised hand far out to the side: use the middle 60% of the figure
    mid_lo, mid_hi = int(top.shape[1] * 0.2), int(top.shape[1] * 0.8)
    inner = cols[(cols >= mid_lo) & (cols <= mid_hi)]
    if len(inner) > 4:
        cols = inner
    waist_w = cols[-1] - cols[0] + 1
    lw = legs.shape[1]
    scale = min(1.0, waist_w / lw * 1.05)
    lg = np.array(Image.fromarray(legs, "RGBA").resize((max(1, int(lw * scale)), max(1, int(legs.shape[0] * scale))), Image.Resampling.NEAREST))
    W = max(top.shape[1], lg.shape[1])
    H = top.shape[0] + lg.shape[0] - 4
    out = np.zeros((H, W, 4), np.uint8)
    cx = (cols[0] + cols[-1]) // 2
    lx = max(0, min(W - lg.shape[1], cx - lg.shape[1] // 2))
    ly = top.shape[0] - 4
    m = lg[..., 3] > 0
    out[ly:ly + lg.shape[0], lx:lx + lg.shape[1]][m] = lg[m]
    tm = top[..., 3] > 0
    out[:top.shape[0], :top.shape[1]][tm] = top[tm]
    return out


def orange_to_brown(rgba):
    hsv = cv2.cvtColor(np.ascontiguousarray(rgba[..., :3]), cv2.COLOR_RGB2HSV).astype(np.int32)
    h, s, v = hsv[..., 0], hsv[..., 1], hsv[..., 2]          # OpenCV hue is 0-179
    orange = (h >= 5) & (h <= 22) & (s > 100)
    hsv[..., 0] = np.where(orange, 12, h)
    hsv[..., 1] = np.where(orange, s * 0.60, s)
    hsv[..., 2] = np.where(orange, v * 0.60, v)
    rgba[..., :3] = cv2.cvtColor(hsv.clip(0, 255).astype(np.uint8), cv2.COLOR_HSV2RGB)
    return rgba


COLORS = 16      # per sprite; fewer colors = cleaner pixel art, less video noise


def denoise(rgba):
    """Video-compression speckle off the full-size cutout, edges kept sharp (bilateral filter)."""
    out = rgba.copy()
    rgb = np.ascontiguousarray(rgba[..., :3])
    out[..., :3] = cv2.bilateralFilter(cv2.medianBlur(rgb, 3), 7, 40, 7)
    return out


def tidy_alpha(a):
    """Drop lone pixels (fewer than 2 solid neighbours) and fill pinholes (3+ solid neighbours)."""
    on = (a > 0).astype(np.uint8)
    pad = np.pad(on, 1)
    n = (pad[:-2, 1:-1] + pad[2:, 1:-1] + pad[1:-1, :-2] + pad[1:-1, 2:])
    on = np.where((on == 1) & (n < 2), 0, on)
    on = np.where((on == 0) & (n >= 3), 1, on)
    return on * 255


def outline(arr):
    """SNES-style dark outline: transparent pixels touching the sprite take a dark version of the
    neighbouring color. The sprite gets 1 pixel bigger on every side."""
    arr = np.pad(arr, ((1, 1), (1, 1), (0, 0)))
    on = arr[..., 3] > 0
    h, w = on.shape
    out = arr.copy()
    for y in range(h):
        for x in range(w):
            if on[y, x]:
                continue
            for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                ny, nx = y + dy, x + dx
                if 0 <= ny < h and 0 <= nx < w and on[ny, nx]:
                    out[y, x, :3] = (arr[ny, nx, :3] * 0.30).astype(np.uint8)
                    out[y, x, 3] = 255
                    break
    return out


def pixelate(rgba):
    clean = denoise(rgba)
    im = Image.fromarray(clean, "RGBA").convert("RGBa")       # premultiplied: no halo from old background
    w, h = max(1, round(im.width * SCALE)), max(1, round(im.height * SCALE))
    small = im.resize((w, h), Image.Resampling.BOX).convert("RGBA")
    arr = np.array(small)
    arr[..., 3] = np.where(arr[..., 3] >= 120, 255, 0)
    arr[..., 3] = tidy_alpha(arr[..., 3])
    solid = arr[..., 3] > 0
    q = Image.fromarray(arr[..., :3]).quantize(colors=COLORS, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGB")
    arr[..., :3] = np.array(q)
    arr[~solid, :3] = 0
    return Image.fromarray(outline(arr), "RGBA")


cuts = {}
used_sam = {}
for name, (box, fg, bg) in CAST.items():
    c = sam_cutout(name)
    used_sam[name] = c is not None
    cuts[name] = c if c is not None else cutout(box, fg, bg)
    print(f"{name:6s} cut with {'Segment Anything' if c is not None else 'GrabCut'}")

# Nobody but Kona (scarf) and DJ (eyes) has green on them, so any green left inside
# the others is background showing through a gap - clear it.
for name in ("jason", "jun", "rusty", "kara", "maui"):
    c = cuts[name]
    hsv = cv2.cvtColor(np.ascontiguousarray(c[..., :3]), cv2.COLOR_RGB2HSV)
    g = ((hsv[..., 0] >= 33) & (hsv[..., 0] <= 95) & (hsv[..., 1] > 60) & (hsv[..., 2] > 40)).astype(np.uint8)
    # inside the silhouette: paint over from the surrounding colors; at the edge: just remove
    filled = np.ascontiguousarray(c[..., :3])
    filled = cv2.inpaint(filled, g, 4, cv2.INPAINT_TELEA)
    c[..., :3] = filled
    edge = strip_outside_green(c.copy())[..., 3] == 0
    c[edge, 3] = 0
cuts["maui"] = orange_to_brown(cuts["maui"])


def fill_holes(rgba):
    """See-through specks fully enclosed by the body are segmentation misses, not real gaps:
    paint them from the surrounding fur and make them solid. (Not used on the people, whose
    arm-to-body gaps are real.)"""
    clear = (rgba[..., 3] == 0).astype(np.uint8)
    n, labels, stats, _ = cv2.connectedComponentsWithStats(clear, 4)
    h, w = clear.shape
    holes = np.zeros_like(clear)
    for i in range(1, n):
        x, y, bw, bh, area = stats[i]
        touches_edge = x == 0 or y == 0 or x + bw >= w or y + bh >= h
        if not touches_edge and area < 400:
            holes[labels == i] = 1
    rgb = cv2.inpaint(np.ascontiguousarray(rgba[..., :3]), holes, 3, cv2.INPAINT_TELEA)
    rgba[..., :3] = np.where(holes[..., None] == 1, rgb, rgba[..., :3])
    rgba[holes == 1, 3] = 255
    return rgba


for name in ("rusty", "kara", "maui", "dj"):
    cuts[name] = fill_holes(cuts[name])

# Jason's jeans and sneakers, from just below his hands down: the donor legs for Kona and Jun.
j = cuts["jason"]
legs = j[int(j.shape[0] * 0.80):].copy()
legs[:, : int(legs.shape[1] * 0.22), 3] = 0          # drop his hands at the sides
legs[:, int(legs.shape[1] * 0.78):, 3] = 0
ys, xs = np.nonzero(legs[..., 3])
legs = legs[ys.min():, xs.min():xs.max() + 1]
Image.fromarray(legs, "RGBA").save(BUILD / "debug_legs.png")
NAVY = (100, 125, 0, 0, 255)                          # hue 100-125 = his dark-blue jeans
# recolor rules: (hue lo, hue hi, min saturation, min value, max value, new color);
# bright pixels (value >= 200) are his white sneakers
jun_legs = recolor(legs, [(NAVY[0], NAVY[1], 40, 0, 255, (90, 135, 200))])
jun_legs = recolor(jun_legs, [(0, 179, 0, 200, 255, (110, 70, 36))])
kona_legs = recolor(legs, [(NAVY[0], NAVY[1], 40, 0, 255, (70, 70, 88))])
kona_legs = recolor(kona_legs, [(0, 179, 0, 200, 255, (110, 70, 36))])
# Only needed when Kona or Jun had to come from the first frame (GrabCut fallback), where they're hidden from the waist down.
if not used_sam.get("jun"):
    cuts["jun"] = with_legs(cuts["jun"], jun_legs)
if not used_sam.get("kona"):
    cuts["kona"] = with_legs(cuts["kona"], kona_legs)

# Each life stage is cut from the same picture at a different size, so a baby is a small
# version of its grown-up self - and nobody can ever turn into someone else.
STAGES = {"baby": SCALE * 0.55, "kid": SCALE * 0.78, "adult": SCALE}
big, small = [], []
base_scale = SCALE
for name, c in cuts.items():
    Image.fromarray(c, "RGBA").save(BUILD / f"full_{name}.png")
    for stage, sc in STAGES.items():
        SCALE = sc
        s = pixelate(c)
        s.save(OUT / f"char_{name}_{stage}.png")
        if stage == "adult":
            small.append(s)
            print(f"{name:6s} full {c.shape[1]}x{c.shape[0]}  adult {s.width}x{s.height}")
    SCALE = base_scale
    big.append(Image.fromarray(c, "RGBA"))


def sheet(ims, path, zoom):
    pad = 6
    W = sum(i.width for i in ims) + pad * (len(ims) + 1)
    H = max(i.height for i in ims) + pad * 2
    b = Image.new("RGBA", (W, H), (91, 194, 54, 255))
    x = pad
    for i in ims:
        b.alpha_composite(i, (x, H - pad - i.height))
        x += i.width + pad
    b.resize((W * zoom, H * zoom), Image.Resampling.NEAREST).save(path)


sheet(big, BUILD / "full_sheet.png", 1)
sheet(small, BUILD / "sprite_sheet.png", 4)
print("ok")
