"""Scenes for the 1.0 Play screenshots. Run one at a time: python scenes.py <scene>.

Scenes that read the microphone play tones from the PC speakers, so they need a
quiet room with the phone beside the speaker. The others don't.
"""
import re
import sys

from cap import *


def bounds(label, wait=4.0):
    """Centre of the node whose text or content description is [label], waiting out animations."""
    deadline = time.time() + wait
    while True:
        xml = adb("exec-out", "uiautomator", "dump", "/dev/tty")
        for node in re.findall(r"<node [^>]*>", xml):
            names = re.findall(r'(?:text|content-desc)="([^"]*)"', node)
            box = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
            if label in names and box:
                x1, y1, x2, y2 = map(int, box.groups())
                return (x1 + x2) // 2, (y1 + y2) // 2
        if time.time() > deadline:
            return None
        time.sleep(0.4)


def tap(label, times=1, pause=0.25):
    pos = bounds(label)
    if pos is None:
        raise RuntimeError(f"'{label}' not on screen")
    for _ in range(times):
        adb("shell", "input", "tap", str(pos[0]), str(pos[1]))
        time.sleep(pause)
    time.sleep(0.5)


def open_sheet():
    """Both top chips open the settings sheet; the A4 one reads the same whatever the tuning."""
    xml = adb("exec-out", "uiautomator", "dump", "/dev/tty")
    m = re.search(r'text="A4 [^"]*"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    x1, y1, x2, y2 = map(int, m.groups())
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(1.2)


def tap_edit(tuning):
    """The Edit button on [tuning]'s row of the sheet."""
    _, y = bounds(tuning)
    x, _ = bounds("Edit")
    adb("shell", "input", "tap", str(x), str(y + 20))
    time.sleep(1.0)


def back():
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1.0)


def choose(current, group, target):
    """Pick a preset from the settings sheet."""
    tap(current)
    if group:
        tap(group)
    tap(target)
    time.sleep(0.8)


def type_text(text):
    """Types as key presses, which the keyboard's auto-capitals leave alone; Shift for capitals."""
    keys = {" ": "KEYCODE_SPACE", ",": "KEYCODE_COMMA", "-": "KEYCODE_MINUS"}
    for ch in text:
        if ch.isupper():
            adb("shell", "input", "keycombination", "KEYCODE_SHIFT_LEFT", "KEYCODE_" + ch)
        else:
            adb("shell", "input", "keyevent", keys.get(ch) or "KEYCODE_" + ch.upper())


def field_text(like):
    """The text of the node that matches [like] ignoring case."""
    xml = adb("exec-out", "uiautomator", "dump", "/dev/tty")
    for text in re.findall(r'text="([^"]*)"', xml):
        if text.lower() == like.lower():
            return text
    return None


def name_tuning(name, current="Custom tuning"):
    tap(current)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    adb("shell", "input", "keyevent", *["KEYCODE_DEL"] * (len(current) + 8))
    type_text(name)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    back()


def new_tuning(name, steps, remove=()):
    """From the editor's default guitar standard: remove strings, then step each one."""
    tap("+  New tuning")
    name_tuning(name)
    for string in sorted(remove, reverse=True):
        tap(f"Remove string {string}")
    for string, step in steps.items():
        if step:
            tap(f"{'Raise' if step > 0 else 'Lower'} string {string} a semitone", abs(step), pause=0.15)


def custom():
    """Two custom tunings: "Just Open C" (C2 G2 C3 G3 C4 E4−14¢), then tenor guitar (C3 G3 D4 A4).
    A new tuning starts as a copy of the selected one: guitar standard from Chromatic, then Open C.
    Shoots the sheet with both on top, then reopens Open C to show the fine-tune."""
    restart_app()
    open_sheet()
    tap("Chromatic")
    open_sheet()
    new_tuning("Just Open C", {1: -4, 2: -2, 3: -2, 5: 1})
    tap("Fine-tune string 6 in cents")
    tap("Lower string 6 by one cent", 14, pause=0.15)
    tap("Save")
    time.sleep(1.2)
    new_tuning("Tenor guitar", {1: 12, 2: 12, 3: 14, 4: 14}, remove=(5, 6))
    tap("Save")
    time.sleep(1.5)
    shot("n_sheet")
    # Reopened for editing, the name isn't focused, so no cursor sits in the shot.
    # (Names avoid commas: the keyboard capitalises after one and fights corrections.)
    tap_edit("Just Open C")
    tap("Fine-tune string 6 in cents")
    time.sleep(1.0)
    shot("n_editor")
    tap("Cancel")


def bass_listen():
    """Bass 5-string with its low B plucked: the reference tone rippling. The microphone isn't needed."""
    restart_app()
    open_sheet()
    tap("Bass")
    tap("5-string")
    back_to_tuner()
    tap("B0")
    time.sleep(0.7)
    for take in "abc":
        shot("n_bass_" + take)
        time.sleep(0.35)


def back_to_tuner():
    """Close the sheet if it's still open."""
    if bounds("TUNING", wait=0.5):
        back()


def midi_hz(m, cents=0.0):
    return 440.0 * 2 ** ((m - 69 + cents / 100) / 12)


def lock():
    """Guitar standard: five strings plucked in tune, each leaving its sage dot, then the high E
    eased up from flat into the lock bloom. Needs the quiet room."""
    restart_app()
    open_sheet()
    tap("Guitar")
    tap("Standard")
    back_to_tuner()
    seq = []
    for m in (40, 45, 50, 55, 59):
        seq += pluck([(1.9, midi_hz(m), midi_hz(m))], amp=0.45) + silence(0.25)
    seq += pluck([(1.2, midi_hz(64, -24), midi_hz(64, -1)), (3.6, midi_hz(64, -1), midi_hz(64))], amp=0.42, decay=0.07)
    perform(write_wav("lock", seq), [(12.9, "n_lock_a"), (13.6, "n_lock_b"), (14.3, "n_lock_c"), (15.0, "n_lock_d")])


def turn():
    """Half-step down: the B♭ string coming up from far flat, caught amber, to show the turn hint
    and the flat spelling. Needs the quiet room."""
    restart_app()
    open_sheet()
    tap("Guitar")
    tap("Half-step down")
    back_to_tuner()
    tone = pluck([(1.6, midi_hz(58, -46), midi_hz(58, -21)), (2.6, midi_hz(58, -21), midi_hz(58, -19))], amp=0.45, decay=0.07)
    perform(write_wav("turn", tone), [(2.6, "n_turn_a"), (3.1, "n_turn_b"), (3.6, "n_turn_c")])


def sung_scale(path):
    """A D major scale up and down, a breath, then an arpeggio whose first F♯ sits 22¢ flat and is
    corrected: a harmonic voice with a 5 Hz vibrato, gliding 90 ms between steps."""
    import numpy as np
    sr = 48000

    def phrase(steps):
        segs, prev = [], steps[0][0]
        for m, hold in steps:
            seg = np.full(int(hold * sr), float(m))
            g = int(0.09 * sr)
            seg[:g] = np.linspace(prev, m, g)
            segs.append(seg)
            prev = m
        return np.concatenate(segs)

    def voice(mid):
        t = np.arange(len(mid)) / sr
        mid = mid + 0.15 * np.sin(2 * np.pi * 5.2 * t) * np.clip((t % 0.75) / 0.4, 0, 1)
        phase = 2 * np.pi * np.cumsum(440 * 2 ** ((mid - 69) / 12)) / sr
        v = sum(np.sin(h * phase + h * 0.7) / h for h in range(1, 9))
        env = np.minimum(1, t / 0.05) * np.minimum(1, (t[-1] - t) / 0.1)
        return v / np.abs(v).max() * env

    scale = phrase([(m, 0.75) for m in (62, 64, 66, 67, 69, 71, 73, 74, 73, 71, 69, 67, 66, 64, 62)])
    arpeggio = phrase([(62, 0.8), (65.78, 0.55), (66, 0.6), (69, 0.8), (74, 1.0), (69, 0.7), (66, 0.7), (62, 1.4)])
    # Loud: on the drone's harmonics, the voice must clearly outweigh the drone to be read.
    out = np.concatenate([np.zeros(sr // 2), voice(scale), np.zeros(int(0.6 * sr)), voice(arpeggio)]) * 0.92
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes((out * 32767).astype(np.int16).tobytes())


def practice():
    """Chromatic practice with a D3 drone and a sung scale and arpeggio over it from the PC.
    Needs the quiet room."""
    restart_app()
    open_sheet()
    tap("Chromatic")
    back_to_tuner()
    tap("Practice")
    # A4 down to D3. Each step plucks the note, which keeps the microphone shut meanwhile.
    tap("Drone a semitone lower", 19, pause=0.12)
    time.sleep(4.5)
    tap("D3")  # start the drone
    time.sleep(4.0)
    sung = os.path.join(os.path.dirname(os.path.abspath(__file__)), "scale.wav")
    if not os.path.exists(sung):
        sung_scale(sung)
    perform(sung, [(18.2, "n_practice_a")])
    time.sleep(0.4)
    shot("n_practice_b")
    tap("D3")  # stop the drone


if __name__ == "__main__":
    for name in sys.argv[1:]:
        globals()[name]()
