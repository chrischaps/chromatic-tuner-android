# Play feature graphic (1024x500): the chaps.dev cover's arc and A4 bloom on
# the right, the name set in the app's wordmark voice on the left.
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H = 1024, 500
BG = np.array([26, 38, 34], float)
INK = (243, 245, 238); MUTED = (167, 182, 174); SAGE = (156, 197, 176)
src = Image.open(r'C:/Users/chris/dev/chaps-dev/public/projects/chromatic-tuner/cover.jpg').convert('RGB')

# Backdrop: flat dusk with a faint glow behind the meter, like the app's backgroundGlow.
yy, xx = np.mgrid[0:H, 0:W].astype(float)
cx, cy = 690, 300
r = np.sqrt(((xx - cx) / 520) ** 2 + ((yy - cy) / 360) ** 2)
glow = np.clip(1 - r, 0, 1) ** 2
canvas = BG + glow[..., None] * (np.array([44, 61, 55]) - BG)

# Meter crop, bled off the top, bottom and right edges; only the left edge,
# where the cover is already plain dusk, needs feathering.
crop = src.crop((250, 70, 1350, 900))
s = H / crop.height
crop = crop.resize((round(crop.width * s), H), Image.LANCZOS)
cw, ch = crop.size
ox, oy = W - cw, 0
ramp = np.clip(np.arange(cw) / 90.0, 0, 1)
alpha = np.broadcast_to((ramp * ramp * (3 - 2 * ramp))[None, :, None], (ch, cw, 1))
# Extend each row's edge color leftward so the backdrop is the cover, continued.
edge = np.asarray(crop, float)[:, 2:10].mean(axis=1)
canvas[:] = edge[:, None, :]
x0, x1 = max(ox, 0), min(ox + cw, W)
region = canvas[oy:oy + ch, x0:x1]
c = np.asarray(crop, float)[:, x0 - ox:x1 - ox]
a = alpha[:, x0 - ox:x1 - ox]
canvas[oy:oy + ch, x0:x1] = region * (1 - a) + c * a

# Zero-centered grain so the flat dusk doesn't band.
rng = np.random.default_rng(7)
canvas += rng.normal(0, 1.4, canvas.shape[:2])[..., None]
img = Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8))

# Wordmark: a small spaced "CHAPS" over a light "Tuner", like the in-app TUNER mark.
dr = ImageDraw.Draw(img)
light = r'C:/Windows/Fonts/segoeuil.ttf'; semi = r'C:/Windows/Fonts/segoeuisl.ttf'
def spaced(x, y, text, font, fill, track):
    for chr_ in text:
        dr.text((x, y), chr_, font=font, fill=fill)
        x += dr.textlength(chr_, font=font) + track
    return x
L = 72
spaced(L + 3, 168, 'CHAPS', ImageFont.truetype(semi, 17), SAGE, 7)
dr.text((L, 186), 'Tuner', font=ImageFont.truetype(light, 84), fill=INK)
dr.line((L + 4, 302, L + 52, 302), fill=(91, 108, 101), width=1)
sub = ImageFont.truetype(semi, 19)
dr.text((L + 3, 318), 'guitar · bass · ukulele · voice', font=sub, fill=MUTED)

import os
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'metadata', 'android', 'en-US', 'images', 'featureGraphic.png')
img.save(out)
print(img.size, img.mode)
