# TODO

Feature ideas for upcoming Chaps Tuner releases, roughly in the order to build them. Items 1 and 2 are small and the most requested. Item 4 is the biggest quality-of-life win. Items 3 and 5 are what would set the app apart in the store.

## 1. More tunings, and a bass mode ✅ (in 1.0; tested on a real bass)

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

## 2. Reference tones (tune by ear) ✅ (tested on the Pixel)

- [x] Tap a string chip in `StringRow` to play that note. It ripples while it rings, slower for low strings.
- [x] Make the tone with a Karplus-Strong plucked-string synth through `AudioTrack`, so it sounds like a string rather than a sine. (`PluckVoice`, tuned to within 0.5¢ by an allpass, checked by `PitchDetector` in tests.)
- [x] Ignore or gate mic frames while the tone plays, so the tuner doesn't detect its own output. (`TonePlayer.isGating`, plus 400 ms after.)
- [x] Use the current A4 setting, and any microtonal string offset.
- [x] In Chromatic mode, offer a reference-tone picker or long-press instead. (A − / note / + picker, E1–C6; stepping plucks too.)
- [x] Checked on the Pixel: the tones sound good, and the tuner doesn't hear itself.
- Note for the future: the phone speaker can't reproduce 31–41 Hz, so the low bass strings rely on their harmonics. If a bass player finds them faint, try a brighter pluck for low notes.

## 3. Intonation check (guitar setup)

- [ ] A guided flow: play the 12th-fret harmonic, then the fretted 12th. Show the difference in cents and which way to move the saddle ("fretted +6¢ sharp: move saddle back").
- [ ] A fine readout to 0.1¢. Consider a strobe-style view, since the ±4¢ lock is too coarse here.
- [ ] Remember the result per string, so a full setup pass shows all six at a glance.
- [ ] Build it as a new screen on top of `TuningProcessor`, with fine mode skipping the lock and the hysteresis.

## 4. Using the selected tuning to cope with noisy rooms

- [ ] **Cheap first step: ignore readings far outside the instrument.** In a preset, every sound is measured against the nearest string, however far away it is. On Bass · Standard, a 158 Hz room sound read as "G2 +831¢". In `TuningProcessor`, treat a reading more than about a fifth (700¢) beyond the lowest or highest string as silence. Guitar has the same issue, but bass's narrow range makes it show more often.
- [ ] When a preset is active, narrow the detector's search to about ±3 semitones around the lowest and highest strings. This rejects room noise and nearby instruments.
- [ ] Consider weighting MPM peak choice toward periods near a string. The trade-off is that the guess about which note is playing gets stricter.
- [ ] Add a "loud room" setting that tightens the clarity and RMS gates in `TuningProcessor`'s companion.
- [ ] Test it with the acoustic WAV method plus background noise and crosstalk (another instrument playing a different note).

## 5. Pitch practice (voice, fretless, violin) ✅ (checked on the Pixel)

- [x] In Chromatic mode, add a practice view: a longer scrolling pitch line (30–60 s) with note gridlines. (`PitchLine`: 30 s on a piano roll, with the drone's note glowing in every octave; behind a Tune/Practice switch.)
- [x] Add an optional drone on a chosen note, reusing the synth from #2. (`Tanpura`: plucks of the note and its octave, in phase. It plays on while you sing, and `DroneCanceller` comb-filters it out of the microphone.)
- [x] Make the pitch line glide between notes when you sing a scale, instead of resetting to the nearest semitone. (`TracePoint.midi`.)
- [x] Checked on the Pixel: a D3 drone through the speaker stays off the line. A scale played from the PC over it reads clean, D4, A4 and D5 on the drone's harmonics included, and Chris sang over it.
- [ ] One blip (E4–F4) appeared while the drone was stepped 19 semitones in quick succession. It's probably the old drone's tail outliving the 400 ms gate after a retune. Worth a look if it shows up with normal stepping.
- Note for the future: a voice in unison with the drone only reads when it's clearly louder than the drone (8 dB). With headphones that's always the case.

## Polish

- [ ] **The settings sheet jumps when you switch instrument chips.** The sheet is only as tall as its content, so going from Guitar (6 tunings) to Bass (2) drops it by several rows, and a tap aimed at a row can land outside the sheet and close it. Give the tuning list a minimum height equal to the longest group.

## Store and site media reshoot ✅ (Play images, Oct 4)

- [x] Eight new Play screenshots and a feature graphic tagged "guitar · bass · ukulele · voice". The shots: lock with the strings filled in, practice over a drone, half-step down in flats, low B ringing on the 5-string bass, the settings sheet with two custom tunings, the custom editor's fine-tune, light theme, and the permission screen.
- [x] The capture kit lives in `fastlane/capture/`:
  - `cap.py` plays tones and takes screenshots on cue.
  - `scenes.py` has one function per shot. The microphone scenes need the quiet room.
  - `play_compose.py` frames the 1080×1920 images with captions and paints out the status bar.
  - `compose.py` makes the 16:9 site stills, and `feature.py` makes the feature graphic.
- Captions and images can't say "free" or "no ads": Play treats that as promotional.
- [ ] Update the chaps.dev Tuner page stills and copy to match: bass, custom and microtonal tunings, reference tones, practice. `compose.py` makes the 1920×1080 stills from the same raw captures.

## Also considered

- [ ] A Quick Settings tile, so the tuner opens in one tap
- [ ] **Usage analytics, after launch, and only if Play Console's own stats aren't enough.** These come free with no code in the app: installs, retention, devices, crashes and ANRs (deobfuscated from the bundle's mapping file), and ratings. If something more is needed:
  - Make it opt-in, asked once, with a few anonymous events: which tuning is chosen, whether Practice and the drone get used.
  - Use a privacy-focused service (e.g. TelemetryDeck) or our own endpoint, not Firebase or Google Analytics.
  - The same release has to update the Data safety form ("collected"), the privacy page, and the full description, which promises "no internet access … no tracking". It also needs the INTERNET permission and a line in the changelog.
- [ ] A choice between sharps and flats, and solfège note names
- [ ] Transposing-instrument display (B♭ and E♭ instruments)
- [x] Sweetened or stretch tunings (per-string cent offsets): possible with microtonal custom strings
