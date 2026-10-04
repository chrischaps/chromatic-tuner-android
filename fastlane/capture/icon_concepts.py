"""Icon concepts, drawn from the app's own marks, rendered for comparison.

Each concept is drawn on the adaptive-icon canvas (108 units, with the content kept inside
the 66-unit safe circle), supersampled, then shown masked as a circle and a squircle at
store and launcher sizes, on dark and light wallpapers, with its monochrome (themed) form.
"""
import math
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
S = 1024                  # render size of the 108-unit canvas
U = S / 108               # pixels per unit

BG = (22, 32, 29)
GLOW = (37, 55, 49)
INK = (243, 245, 238)
MUTED = (167, 182, 174)
FAINT = (91, 108, 101)
SAGE = (156, 197, 176)
HALO = (212, 237, 223)
AMBER = (227, 166, 91)


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def backdrop(center=(54, 50), radius=62, lift=0.55):
    """Dusk with the app's lift of light behind the mark."""
    small = Image.new("RGB", (128, 128))
    px = small.load()
    for y in range(128):
        for x in range(128):
            d = min(1.0, math.hypot(x / 128 * 108 - center[0], y / 128 * 108 - center[1]) / radius)
            t = d * d * (3 - 2 * d)
            px[x, y] = lerp(lerp(BG, GLOW, lift), BG, t)
    return small.resize((S, S), Image.BICUBIC).filter(ImageFilter.GaussianBlur(6))


def halo(img, at, radius, color, strength):
    layer = Image.new("L", (S, S), 0)
    ImageDraw.Draw(layer).ellipse(
        [at[0] - radius, at[1] - radius, at[0] + radius, at[1] + radius], fill=int(255 * strength)
    )
    layer = layer.filter(ImageFilter.GaussianBlur(radius / 2.2))
    return Image.composite(Image.new("RGB", (S, S), color), img, layer)


def arc_point(cx, cy, r, deg):
    a = math.radians(deg)
    return cx + r * math.sin(a), cy - r * math.cos(a)


def meter(d, cx, cy, r, span, ticks, width, color_fn):
    """Tick marks along an arc, like the app's dial; span in degrees either side of top."""
    for i in range(ticks):
        deg = -span + 2 * span * i / (ticks - 1)
        major = i % 4 == 0
        inner = r - (6 if major else 3.5) * U
        x0, y0 = arc_point(cx, cy, inner, deg)
        x1, y1 = arc_point(cx, cy, r, deg)
        d.line([x0, y0, x1, y1], fill=color_fn(deg), width=round(width * U))


def comes_true(mono=False):
    """The app's lock moment: the marker easing in along the arc, amber to sage, to centre."""
    white = (255, 255, 255)
    img = Image.new("RGB", (S, S), (0, 0, 0)) if mono else backdrop(center=(54, 50), radius=46)
    d = ImageDraw.Draw(img)
    cx, cy, r = 54 * U, 89 * U, 38 * U
    span = 50
    box = [cx - r, cy - r, cx + r, cy + r]
    d.arc(box, 270 - span, 270 + span, fill=white if mono else lerp(BG, MUTED, 0.45), width=round(1.8 * U))
    # Centre tick, standing up from the arc.
    x0, y0 = arc_point(cx, cy, r + 5.5 * U, 0)
    x1, y1 = arc_point(cx, cy, r + 12 * U, 0)
    d.line([x0, y0, x1, y1], fill=white if mono else SAGE, width=round(2.2 * U))
    # The trail: each reading closer and brighter than the last, warming from amber to sage.
    steps = [-42, -29.5, -19.5, -12, -6.8]
    for i, deg in enumerate(steps):
        t = i / (len(steps) - 1)
        rad = (1.7 + 1.3 * t) * U
        x, y = arc_point(cx, cy, r, deg)
        if mono:
            col = white
        else:
            col = lerp(AMBER, SAGE, t * t * (3 - 2 * t))
            col = lerp(BG, col, 0.35 + 0.6 * t)
        d.ellipse([x - rad, y - rad, x + rad, y + rad], fill=col)
    # The arrival: the brightest dot, at centre, in the lock bloom.
    x, y = arc_point(cx, cy, r, 0)
    if not mono:
        img = halo(img, (x, y), 15 * U, HALO, 0.42)
        d = ImageDraw.Draw(img)
    rad = 4.6 * U
    d.ellipse([x - rad, y - rad, x + rad, y + rad], fill=white if mono else HALO)
    return img


def bloom(mono=False):
    """Quieter: the dial at rest, every tick in place, and the note come true at its top."""
    img = Image.new("RGB", (S, S), (0, 0, 0)) if mono else backdrop(center=(54, 44), radius=46)
    d = ImageDraw.Draw(img)
    cx, cy, r = 54 * U, 80 * U, 40 * U
    white = (255, 255, 255)
    meter(d, cx, cy, r, 56, 17, 1.6,
          (lambda deg: white) if mono else (lambda deg: lerp(BG, MUTED, 0.85 - abs(deg) / 56 * 0.6)))
    x, y = arc_point(cx, cy, r - 1.5 * U, 0)
    if not mono:
        img = halo(img, (x, y), 18 * U, HALO, 0.45)
        d = ImageDraw.Draw(img)
    rad = 7 * U
    d.ellipse([x - rad, y - rad, x + rad, y + rad], fill=white if mono else HALO)
    return img


def ring(mono=False):
    """The reference tone: a string's pill, sounding, its rings spreading out."""
    img = Image.new("RGB", (S, S), (0, 0, 0)) if mono else backdrop(center=(54, 54), radius=46)
    d = ImageDraw.Draw(img)
    c = 54 * U
    white = (255, 255, 255)
    for k, (r, a) in enumerate([(30, 0.22), (23.5, 0.42), (17.5, 0.75)]):
        col = white if mono else lerp(BG, SAGE, a)
        rr = r * U
        d.ellipse([c - rr, c - rr, c + rr, c + rr], outline=col, width=round((1.4 + 0.5 * k) * U))
    if not mono:
        img = halo(img, (c, c), 14 * U, HALO, 0.45)
        d = ImageDraw.Draw(img)
    rr = 7.5 * U
    d.ellipse([c - rr, c - rr, c + rr, c + rr], fill=white if mono else HALO)
    return img


CONCEPTS = [("A · Comes true", comes_true), ("B · Bloom", bloom), ("C · Ring", ring)]


def mask(size, shape):
    m = Image.new("L", (size * 4, size * 4), 0)
    dr = ImageDraw.Draw(m)
    if shape == "circle":
        dr.ellipse([0, 0, size * 4 - 1, size * 4 - 1], fill=255)
    else:
        dr.rounded_rectangle([0, 0, size * 4 - 1, size * 4 - 1], radius=size * 4 * 0.3, fill=255)
    return m.resize((size, size), Image.LANCZOS)


def launcher(icon, size, shape):
    """The visible part of an adaptive icon is the middle 72 of its 108 units."""
    crop = icon.crop((round(18 * U), round(18 * U), round(90 * U), round(90 * U))).resize((size, size), Image.LANCZOS)
    out = crop.convert("RGBA")
    out.putalpha(mask(size, shape))
    return out


def themed(mono, size, light):
    """Android's themed icon: the monochrome layer tinted, on a tonal disc."""
    bg, fg = ((214, 232, 222), (35, 74, 58)) if light else ((35, 52, 45), (190, 225, 205))
    alpha = mono.convert("L").crop((round(18 * U), round(18 * U), round(90 * U), round(90 * U))).resize((size, size), Image.LANCZOS)
    tile = Image.new("RGB", (size, size), bg)
    tile = Image.composite(Image.new("RGB", (size, size), fg), tile, alpha).convert("RGBA")
    tile.putalpha(mask(size, "circle"))
    return tile


def sheet():
    from PIL import ImageFont
    title = ImageFont.truetype(r"C:/Windows/Fonts/segoeuil.ttf", 34)
    small = ImageFont.truetype(r"C:/Windows/Fonts/segoeuisl.ttf", 18)
    col_w, W = 560, 560 * len(CONCEPTS)
    H = 1060
    canvas = Image.new("RGB", (W, H), (236, 240, 235))
    d = ImageDraw.Draw(canvas)
    for i, (name, fn) in enumerate(CONCEPTS):
        x0 = i * col_w
        icon, mono = fn(), fn(mono=True)
        d.text((x0 + 40, 30), name, font=title, fill=(27, 38, 34))
        # Store icon: full square, as Play shows it.
        store = icon.crop((round(18 * U), round(18 * U), round(90 * U), round(90 * U))).resize((300, 300), Image.LANCZOS)
        st = store.convert("RGBA")
        st.putalpha(mask(300, "squircle"))
        canvas.paste(st, (x0 + 40, 100), st)
        d.text((x0 + 40, 410), "Play store", font=small, fill=(85, 101, 94))
        # Launcher sizes, on a dark wallpaper strip and a light one.
        dark = Image.new("RGB", (480, 140), (30, 34, 40))
        lite = Image.new("RGB", (480, 140), (222, 214, 200))
        for strip in (dark, lite):
            px = 20
            for size, shape in ((96, "circle"), (64, "squircle"), (48, "circle")):
                ic = launcher(icon, size, shape)
                strip.paste(ic, (px, (140 - size) // 2), ic)
                px += size + 26
            tm = themed(mono, 64, light=strip is lite)
            strip.paste(tm, (px + 20, 38), tm)
        canvas.paste(dark, (x0 + 40, 450))
        canvas.paste(lite, (x0 + 40, 600))
        d.text((x0 + 40, 750), "launcher 96 / 64 / 48 px, and themed (monochrome)", font=small, fill=(85, 101, 94))
        # The full adaptive canvas with the safe zone marked.
        full = icon.resize((220, 220), Image.LANCZOS)
        fd = ImageDraw.Draw(full)
        r = 33 / 108 * 220
        fd.ellipse([110 - r, 110 - r, 110 + r, 110 + r], outline=(227, 166, 91), width=1)
        canvas.paste(full, (x0 + 40, 790))
        d.text((x0 + 280, 800), "full 108-unit canvas;\namber circle is the\nsafe zone every\nlauncher shape keeps", font=small, fill=(85, 101, 94))
    out = os.path.join(HERE, "raw", "icon_concepts.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    canvas.save(out)
    print(out)


if __name__ == "__main__":
    sheet()
