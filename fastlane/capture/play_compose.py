"""Frame phone captures as 1080x1920 Play screenshots: a caption over the screen,
on the app's own dusk (or paper) backdrop."""
import os
from PIL import Image, ImageDraw, ImageFont
import compose
from compose import backdrop, screen, place, raw

W, H = 1080, 1920
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "metadata", "android", "en-US", "images", "phoneScreenshots")
os.makedirs(OUT, exist_ok=True)
LIGHT = r"C:/Windows/Fonts/segoeuil.ttf"; SEMI = r"C:/Windows/Fonts/segoeuisl.ttf"

DUSK = dict(bg=(16, 24, 21), glow=(37, 55, 49), ink=(243, 245, 238), muted=(167, 182, 174), edge=(70, 92, 84, 150))
PAPER = dict(bg=(226, 233, 226), glow=(248, 250, 245), ink=(27, 38, 34), muted=(85, 101, 94), edge=(163, 177, 170, 160))

SHOTS = [
    ("n_lock_a", "Hear it come true", "A soft bloom and one gentle tick, string by string.", DUSK),
    ("n_practice_a", "See your voice", "Sing over a drone and watch your pitch glide.", DUSK),
    ("n_turn_b", "See which way to turn", "Cents, pitch and direction, in sharps or flats.", DUSK),
    ("n_bass_a", "Tune by ear", "Tap any string to hear it plucked, down to low B.", DUSK),
    ("n_sheet", "A tuning for every string", "Guitar, bass, ukulele, strings, banjo, or your own.", DUSK),
    ("n_editor", "Make it yours", "Build any tuning and fine-tune each string in cents.", DUSK),
    ("n_light", "Dusk or paper", "Follows your phone's light and dark theme.", PAPER),
    ("n_permission", "Nothing is recorded", "It listens only while it's on screen, and saves nothing.", DUSK),
]

# Shots where the settings sheet reaches up under the status bar. Stretching the row below the
# bar would stretch the sheet's rounded corners into stripes; fill with the dimmed backdrop instead.
SHEET_TOP = {"n_sheet", "n_editor"}
_current = None


def clean_chrome(im):
    # The phone runs at 1008x2244; the status-bar and pill trims are sized to match.
    im = im.copy()
    top, bottom = 92, im.height - 50
    if _current in SHEET_TOP:
        im.paste(im.getpixel((3, top + 2)), (0, 0, im.width, top))
    else:
        row = im.crop((0, top, im.width, top + 1))
        for y in range(top):
            im.paste(row, (0, y))
    row = im.crop((0, bottom - 1, im.width, bottom))
    for y in range(bottom, im.height):
        im.paste(row, (0, y))
    return im
compose.clean_chrome = clean_chrome

def frame(name, title, sub, pal, i):
    global _current
    _current = name
    canvas = backdrop(W, H, center=(0.5, 0.55), glow=pal["glow"], bg=pal["bg"]).convert("RGBA")
    d = ImageDraw.Draw(canvas)
    tf, sf = ImageFont.truetype(LIGHT, 74), ImageFont.truetype(SEMI, 33)
    d.text((W / 2, 150), title, font=tf, fill=pal["ink"], anchor="mm")
    d.text((W / 2, 236), sub, font=sf, fill=pal["muted"], anchor="mm")
    s = screen(raw(name), height=1500)
    if pal is PAPER:  # a softer hairline for the light screen
        e = Image.new("RGBA", s.size, (0, 0, 0, 0))
        ImageDraw.Draw(e).rounded_rectangle((0, 0, s.width - 1, s.height - 1), round(0.075 * s.width), outline=pal["edge"], width=2)
        s = Image.alpha_composite(s, e)
    place(canvas, s, (W - s.width) // 2, 340)
    path = os.path.join(OUT, f"{i}_{name.split('_')[1]}.png")
    canvas.convert("RGB").save(path, optimize=True)
    print(os.path.basename(path), os.path.getsize(path) // 1024, "KB", "| sub width", round(d.textlength(sub, font=sf)))

if __name__ == "__main__":
    for old in os.listdir(OUT):
        if old.endswith(".png"):
            os.remove(os.path.join(OUT, old))
    for i, args in enumerate(SHOTS, 1):
        frame(*args, i)
