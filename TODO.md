# TODO

Feature ideas for upcoming Chaps Tuner releases, roughly in the order to build them. Items 1 and 2 are small and the most requested. Item 4 is the biggest quality-of-life win. Items 3 and 5 are what would set the app apart in the store.

## 1. More tunings, and a bass mode ✅ (v2.1, versionCode 3; tested on a real bass)

- [x] More presets in `Tunings.kt`:
  - Guitar: half-step down, DADGAD, Open G, Open D
  - Ukulele: baritone (DGBE)
  - Other instruments: mandolin, violin, viola, cello, banjo (open G)
- [x] **Ukulele · Low G** preset (G3 C4 E4 A4). The current preset assumes the high G4. A low-G player's G3 is closer to the C4 string, so `nearestString` sends them toward C.
- [x] **Bass mode.** `PitchDetector.minFrequency` is 60 Hz, which is below 4-string bass E1 (41.2 Hz) and 5-string B0 (30.9 Hz).
  - Lower the floor in bass presets only, so the noise rejection for guitar stays as it is.
  - Bass probably needs a longer window (8192), because 4096 samples at 48 kHz cover only about 2.6 periods of B0. That adds lag, which is fine for bass.
  - Add tests with `SignalGen`: plucked E1 and B0, and a missing fundamental.
- [x] **Custom tunings.** A small editor in the settings sheet that saves to `SettingsRepository`, so `Tunings.byId` resolves custom IDs too.
- [x] **Microtonal strings.** Any custom string can be offset by ±50¢ from its note.

## 2. Reference tones (tune by ear)

- [ ] Tap a string chip in `StringRow` to play that note.
- [ ] Make the tone with a Karplus-Strong plucked-string synth through `AudioTrack`, so it sounds like a string rather than a sine.
- [ ] Ignore or gate mic frames while the tone plays, so the tuner doesn't detect its own output.
- [ ] Use the current A4 setting.
- [ ] In Chromatic mode, offer a reference-tone picker or long-press instead.

## 3. Intonation check (guitar setup)

- [ ] A guided flow: play the 12th-fret harmonic, then the fretted 12th. Show the difference in cents and which way to move the saddle ("fretted +6¢ sharp: move saddle back").
- [ ] A fine readout to 0.1¢. Consider a strobe-style view, since the ±4¢ lock is too coarse here.
- [ ] Remember the result per string, so a full setup pass shows all six at a glance.
- [ ] Build it as a new screen on top of `TuningProcessor`, with fine mode skipping the lock and the hysteresis.

## 4. Using the selected tuning to cope with noisy rooms

- [ ] When a preset is active, narrow the detector's search to about ±3 semitones around the lowest and highest strings. This rejects room noise and nearby instruments.
- [ ] Consider weighting MPM peak choice toward periods near a string. The trade-off is that the guess about which note is playing gets stricter.
- [ ] Add a "loud room" setting that tightens the clarity and RMS gates in `TuningProcessor`'s companion.
- [ ] Test it with the acoustic WAV method plus background noise and crosstalk (another instrument playing a different note).

## 5. Pitch practice (voice, fretless, violin)

- [ ] In Chromatic mode, add a practice view: a longer scrolling pitch line (30–60 s) with note gridlines, built on `PitchHistory` and `CentsTrace`.
- [ ] Add an optional drone on a chosen note, reusing the synth from #2.
- [ ] Make the pitch line glide between notes when you sing a scale, instead of resetting to the nearest semitone.

## Store and site media reshoot

Do this once the next batch of features has landed, so one shoot covers them all.

- [ ] **Replace `5_settings.png` before uploading 2.1.** The current one shows the old list of four tunings.
- [ ] Shot list, adding any new features by then:
  - **Settings sheet:** a couple of real custom tunings on top, e.g. "Open C · just third" with `E−14¢` visible, and the instrument chips below.
  - **Custom editor with fine-tune open:** the high E's chip in sage at −14¢, with the slider showing.
  - **Bass · 5-string:** pills filling in, with low B locked in sage. Use a real bass, because PC speakers can't play 31–41 Hz.
  - **Half-step down:** a big E♭ easing into lock, to show the flat spelling.
- [ ] Reuse the capture script `cap.py`, which plays tones and taps through screenshots on cue. It's in the scratchpad of the Oct 1 Tuner session, `994c28f7-…/scratchpad/cap/`; copy it into the repo or a stable place first.
- [ ] Rebuild the compositor, which wasn't saved. It has to:
  - Paint over the status bar and the gesture pill, so no personal notification icons ship.
  - Frame shots at 1080×1920 with captions for Play, and at 1920×1080 for the chaps.dev grid.
- [ ] Prepare the room: Do Not Disturb on, the phone beside the speaker, the laptop fan away from the mic, and quiet during takes.
- [ ] Update the chaps.dev Tuner page stills and copy to match: bass, custom and microtonal tunings.

## Also considered

- [ ] A Quick Settings tile, so the tuner opens in one tap
- [ ] A choice between sharps and flats, and solfège note names
- [ ] Transposing-instrument display (B♭ and E♭ instruments)
- [x] Sweetened or stretch tunings (per-string cent offsets): possible with microtonal custom strings since 2.1
