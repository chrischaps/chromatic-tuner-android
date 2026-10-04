"""Compose phone captures into 16:9 showcase stills on a dusk backdrop."""
import os
from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
OUT = os.path.join(HERE, "out")
os.makedirs(OUT, exist_ok=True)

W, H = 1920, 1080
BG = (16, 24, 21)
GLOW = (37, 55, 49)


def raw(name):
    return Image.open(os.path.join(RAW, name + ".png")).convert("RGB")


def backdrop(w=W, h=H, center=(0.5, 0.42), glow=GLOW, bg=BG):
    """Radial lift of light, like the app's own background and the logo's halo."""
    small = Image.new("RGB", (w // 8, h // 8))
    px = small.load()
    cx, cy = center[0] * small.width, center[1] * small.height
    radius = 0.75 * max(small.width, small.height)
    for y in range(small.height):
        for x in range(small.width):
            d = min(1.0, ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 / radius)
            t = d * d * (3 - 2 * d)
            px[x, y] = tuple(int(glow[i] + (bg[i] - glow[i]) * t) for i in range(3))
    smooth = small.resize((w, h), Image.BICUBIC).filter(ImageFilter.GaussianBlur(4))
    # A whisper of grain so the gradient can't band once it's a JPEG.
    noise = Image.effect_noise((w, h), 6).convert("RGB")
    return ImageChops.add(smooth, noise, 1.0, -128)


def clean_chrome(im):
    """Hide the status bar and gesture pill by extending the app background over them."""
    im = im.copy()
    portrait = im.height > im.width
    top = 122 if portrait else 66
    bottom = im.height - (66 if portrait else 56)
    row = im.crop((0, top, im.width, top + 1))
    for y in range(top):
        im.paste(row, (0, y))
    row = im.crop((0, bottom - 1, im.width, bottom))
    for y in range(bottom, im.height):
        im.paste(row, (0, y))
    return im


def screen(im, height=None, width=None, radius_frac=0.075):
    """Scale a capture and give it rounded corners; returns (RGBA image)."""
    im = clean_chrome(im)
    if height:
        size = (round(im.width * height / im.height), height)
    else:
        size = (width, round(im.height * width / im.width))
    im = im.resize(size, Image.LANCZOS)
    r = round(radius_frac * min(size))
    mask = Image.new("L", (size[0] * 4, size[1] * 4), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] * 4 - 1, size[1] * 4 - 1), r * 4, fill=255)
    mask = mask.resize(size, Image.LANCZOS)
    out = im.convert("RGBA")
    out.putalpha(mask)
    # A hairline edge so dark screens still separate from the dark backdrop.
    edge = Image.new("RGBA", size, (0, 0, 0, 0))
    ImageDraw.Draw(edge).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), r, outline=(70, 92, 84, 150), width=2)
    return Image.alpha_composite(out, edge)


def place(canvas, scr, x, y, shadow=True):
    if shadow:
        a = scr.split()[3]
        sh = Image.new("RGBA", (scr.width + 160, scr.height + 160), (0, 0, 0, 0))
        blk = Image.new("RGBA", scr.size, (0, 0, 0, 165))
        blk.putalpha(ImageChops.multiply(a, Image.new("L", scr.size, 165)))
        sh.paste(blk, (80, 80), blk)
        sh = sh.filter(ImageFilter.GaussianBlur(34))
        canvas.alpha_composite(sh, (x - 80, y - 80 + 22))
    canvas.alpha_composite(scr, (x, y))


def row_of(names, height=930, gap=64, glow=GLOW):
    canvas = backdrop(glow=glow).convert("RGBA")
    scrs = [screen(raw(n), height=height) for n in names]
    total = sum(s.width for s in scrs) + gap * (len(scrs) - 1)
    x = (W - total) // 2
    y = (H - height) // 2
    for s in scrs:
        place(canvas, s, x, y)
        x += s.width + gap
    return canvas.convert("RGB")


def landscape(name, width=1720):
    canvas = backdrop().convert("RGBA")
    s = screen(raw(name), width=width, radius_frac=0.075)
    place(canvas, s, (W - s.width) // 2, (H - s.height) // 2)
    return canvas.convert("RGB")


def save(im, name, quality=90):
    path = os.path.join(OUT, name + ".jpg")
    im.save(path, quality=quality, optimize=True, progressive=True)
    print(name, im.size, os.path.getsize(path) // 1024, "KB")
    return path


if __name__ == "__main__":
    import sys
    save(row_of(sys.argv[2:]), sys.argv[1])
