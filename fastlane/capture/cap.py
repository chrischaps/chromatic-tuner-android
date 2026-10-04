"""Showcase capture kit: synthesize plucked tones, play them from the PC speakers,
and grab screenshots of the tuner on the phone at scripted moments."""
import math
import os
import re
import struct
import subprocess
import threading
import time
import wave

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
os.makedirs(RAW, exist_ok=True)
SR = 44100
PKG = "dev.chaps.tuner"

NOTE = {"E2": 82.4069, "A2": 110.0, "D3": 146.832, "G3": 195.998, "B3": 246.942,
        "E4": 329.628, "C4": 261.626, "G4": 391.995, "A4": 440.0}


def adb(*args, binary=False):
    out = subprocess.run([ADB, *args], capture_output=True)
    return out.stdout if binary else out.stdout.decode("utf-8", "replace")


def shot(name):
    data = adb("exec-out", "screencap", "-p", binary=True)
    path = os.path.join(RAW, name + ".png")
    with open(path, "wb") as f:
        f.write(data)
    return path


def _norm(s):
    """The dump mangles non-ASCII (the middle dot in 'A4 · 440'), so compare loosely."""
    return re.sub(r"[^ -~]+", "?", s)


def ui_bounds(text):
    xml = adb("exec-out", "uiautomator", "dump", "/dev/tty")
    for m in re.finditer(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        if _norm(m.group(1)) == _norm(text):
            x1, y1, x2, y2 = map(int, m.groups()[1:])
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap_text(text):
    pos = ui_bounds(text)
    if pos is None:
        raise RuntimeError(f"'{text}' not on screen")
    adb("shell", "input", "tap", str(pos[0]), str(pos[1]))
    time.sleep(0.8)


def restart_app():
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/com.chrischappelear.tuner.MainActivity")
    time.sleep(2.5)


def pluck(segments, decay=0.22, amp=0.32):
    """segments: list of (seconds, freq_start, freq_end) for one sustained pluck,
    gliding geometrically within each segment. Returns samples."""
    out = []
    phase = [0.0] * 9
    t_total = 0.0
    for dur, f0, f1 in segments:
        n = int(SR * dur)
        for i in range(n):
            frac = i / max(n - 1, 1)
            f = f0 * (f1 / f0) ** frac
            t = t_total + i / SR
            env = min(1.0, t / 0.008) * math.exp(-decay * t)
            v = 0.0
            for h in range(1, 9):
                phase[h] += 2 * math.pi * f * h / SR
                v += math.sin(phase[h] + h * 0.7) / h
            out.append(amp * env * v / 2.2)
        t_total += dur
    return out


def cents(note, c):
    return NOTE[note] * 2 ** (c / 1200)


def silence(sec):
    return [0.0] * int(SR * sec)


def write_wav(name, samples):
    path = os.path.join(HERE, name + ".wav")
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, x)) * 32000)) for x in samples))
    return path


def perform(wav, captures):
    """Play wav and take screenshots at (seconds_from_start, name) moments."""
    import winsound
    player = threading.Thread(target=winsound.PlaySound, args=(wav, winsound.SND_FILENAME))
    start = time.time()
    player.start()
    for at, name in captures:
        delay = at - (time.time() - start)
        if delay > 0:
            time.sleep(delay)
        shot(name)
        print(f"  {name} at {time.time() - start:.2f}s")
    player.join()
