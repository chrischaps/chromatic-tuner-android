# Chromatic Tuner

A calm, accurate Android tuner for guitar, ukulele, and anything else that holds a pitch, built with Kotlin and Jetpack Compose.

## Features

- **Accurate to about a cent** from C2 (65 Hz) to 1400 Hz, using the McLeod Pitch Method, and down to B0 (31 Hz) in bass tunings.
- **Steady but responsive.** About 47 readings a second are smoothed with a median filter, note hysteresis, and a 1€ filter. A held note reads still, and a turning peg is followed without lag.
- **Instrument presets:** Chromatic; guitar (standard, drop D, half-step down, DADGAD, open G, open D); bass (4- and 5-string); ukulele (standard, low G, baritone); violin, viola, cello, mandolin; and banjo (open G). In a preset the tuner targets the nearest *string*, and each string you bring into tune keeps a small mark.
- **Custom tunings** of one to eight strings, built a semitone at a time. Each tuning is spelled with sharps or flats, so half-step down reads E♭ A♭ D♭ G♭ B♭ E♭.
- **Microtonal strings.** Any custom string can be moved off equal temperament by up to ±50¢, for just intonation, quarter-tones or a sweetened tuning. The tuner then reads "in tune" at that offset pitch.
- **Reference pitch** (A4) adjustable from 432 to 446 Hz.
- **Reference tones, to tune by ear.** Tap a string to hear it plucked, at your A4 and with any microtonal offset. In Chromatic, pick any note from E1 to C6. The tone is a Karplus-Strong string rather than a sine, tuned to within half a cent, and the tuner ignores the microphone while it rings.
- **Pitch practice, for voice, fretless and violin.** In Chromatic, the Practice view draws the last 30 seconds of pitch on a piano roll. The line glides between notes as you sing a scale instead of snapping to each one, sage on a note and amber between. Tap the note for a drone: a tanpura-like cycle of plucks on that note and its octave. It plays on while you sing, and the tuner cancels it out of what it hears.
- **Lock bloom.** When a note holds within ±4¢, the display glows and gives a single soft haptic tick.
- **Drift trace.** The last 8 seconds of cents-from-target, centered on "in tune".
- **Colorblind-friendly.** Drift is amber on both sides of center. Direction is shown by position and ♭/♯, never by red versus green.
- **Light and dark themes**, drawn from the logo's slate-green and glow. Portrait and landscape layouts.
- **Microphone only while visible.** Listening stops a couple of seconds after the app leaves the screen.

## How it works

```
AudioRecorder ──► PitchDetector ──► TuningProcessor ──► TunerViewModel ──► TunerScreen
 mic, 48 kHz       MPM / NSDF        median, hysteresis,   StateFlow,         meter, note,
 4096 window,      clarity + RMS     1€ filter, lock,      lifecycle-aware    strings, trace
 1024 hop                            hold, history
```

1. **Capture** (`audio/AudioRecorder.kt`) is a cold `Flow`. The `AudioRecord` opens when collection starts and is released when it stops, on the same worker thread. It reads float PCM at 48 kHz (44.1 kHz fallback) from the `UNPROCESSED` source where supported, otherwise `VOICE_RECOGNITION`, so no automatic gain or noise suppression is applied. A `CaptureProfile` sets the sliding window and search range. `Standard` is a 4096-sample window advanced 1024 samples at a time, searching 60–1400 Hz. `Low`, used when a tuning's lowest string is B1 or below, is 8192 / 2048 and searches down to 28 Hz. Changing between the two reopens the microphone.
2. **Detection** (`audio/PitchDetector.kt`) runs the McLeod Pitch Method: DC removal, a 2 kHz low-pass, an FFT autocorrelation, and the normalized square difference function. It picks the first key maximum within 90% of the tallest, then refines it with parabolic interpolation. Working in lag rather than frequency bins keeps resolution fine for low strings, and the first-peak rule prevents octave errors.
3. **Interpretation** (`tuning/TuningProcessor.kt`) turns noisy per-frame readings into a steady state:
   - It gates frames on clarity and level.
   - It requires a steady onset and rejects murky stray frames.
   - It holds back octave jumps. In a preset, a jump that didn't come with a fresh pluck is folded back onto the string. A phone microphone barely hears a low string's fundamental, so a ringing low E can otherwise read as E3, which the nearest-string rule would call D3.
   - It holds a note through brief dropouts and lets it fade over 1.5 s.
   - It detects the "locked in tune" moment.
4. **Reference tones** (`audio/PluckVoice.kt`, `audio/TonePlayer.kt`) are synthesized live by Karplus-Strong: a seeded noise burst, notched at the pick position, circulates through a one-period delay line. A first-order allpass, solved for the delay at the fundamental, trims the loop to the exact period. High notes soften less on each pass, so every string rings for about the same time. One streaming `AudioTrack` plays them and crossfades between plucks. Until 400 ms after the last sample, the ViewModel hands the processor silence instead of what the microphone hears.
5. **The drone** (`audio/Tanpura.kt`) re-plucks the note and its octave in a slow cycle, and every string rings into the next. Because each string is the drone note or its octave, the sound repeats exactly once per period of that note, and the room's echo of it does too. `audio/DroneCanceller.kt` uses that: subtracting the signal one period ago (a comb filter) leaves almost nothing of the drone, while a voice at any other pitch passes through. A voice on a harmonic of the drone (unison, octave, twelfth…) is cancelled too, so there it reads the raw signal instead, but only while the voice is clearly louder than the drone alone. That level is learned when the drone starts, and learned again after a volume change. Same-pitched plucks would interfere and make the level lurch, so each pitch is plucked the same way, on whole periods, landing in phase.
6. **Pitch line** (`ui/components/PitchLine.kt`). Alongside its note-relative cents, the processor keeps a smoothed fractional MIDI pitch that isn't reset at note changes. The practice view plots it on a semitone grid with a keyboard down the side, and frames the view on the middle 90% of the last 30 seconds, so a stray sound doesn't push your scale off screen.

## Project structure

```
app/src/main/java/com/chrischappelear/tuner/
├── audio/
│   ├── AudioRecorder.kt     # Microphone capture as a Flow of pitch readings
│   ├── CaptureProfile.kt    # Window, hop and pitch range: Standard, or Low for bass
│   ├── PitchDetector.kt     # McLeod Pitch Method
│   ├── FFT.kt               # Iterative radix-2 FFT
│   ├── PluckVoice.kt        # Karplus-Strong plucked string, tuned to the period
│   ├── Tanpura.kt           # The practice drone: a cycle of plucks, periodic at its note
│   ├── DroneCanceller.kt    # Comb-filters the drone out of what the microphone hears
│   └── TonePlayer.kt        # Plays tones and drone; gates or cancels them for the mic
├── tuning/
│   ├── Note.kt              # Notes, MIDI and frequency math, A4 calibration
│   ├── Tunings.kt           # Instrument presets and groups
│   ├── TuningCodec.kt       # Text form of custom tunings for DataStore
│   ├── TuningProcessor.kt   # Smoothing, hysteresis, lock, hold and fade
│   ├── OneEuroFilter.kt     # Adaptive low-pass for the cents reading
│   └── PitchHistory.kt      # Rolling 30 s of cents and gliding pitch
├── data/
│   └── SettingsRepository.kt  # DataStore: selected tuning, A4, custom tunings
├── ui/
│   ├── TunerScreen.kt       # Layout, haptics, previews
│   ├── components/          # TuningMeter, NoteDisplay, StringRow, CentsTrace, PitchLine, SettingsSheet, CustomTuningEditor, PermissionScreen
│   └── theme/               # Colors, type, theme
├── MainActivity.kt          # Edge-to-edge, permission flow
└── TunerViewModel.kt        # Wires capture → processing → UI state
```

## Building

Requires JDK 17+ and the Android SDK (compile SDK 36).

```bash
./gradlew app:assembleDebug   # Build debug APK
./gradlew app:installDebug    # Install on a connected device
./gradlew test                # Unit tests: detector accuracy, note math, processor behavior
./gradlew app:lint            # Lint
```

The unit tests synthesize signals with `SignalGen`: pure and plucked tones for every open string, bass strings down to B0, detuned notes, a missing fundamental, a strong second harmonic, and noise at 10 dB SNR. They assert accuracy in cents. The reference tones are checked by the app's own detector, which must read every string, bass included, within half a cent. The drone tests mix a tanpura with a sung, vibrato'd voice at every interval and at several levels. They check that the voice is what gets read and that a drone alone never registers as a note.

## License

This project is open source. See the [LICENSE](LICENSE) file for details.
