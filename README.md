# Chromatic Tuner

A calm, accurate Android tuner for guitar, ukulele, and anything else that holds a pitch, built with Kotlin and Jetpack Compose.

## Features

- **Accurate to about a cent** from C2 (65 Hz) to 1400 Hz, using the McLeod Pitch Method, and down to B0 (31 Hz) in bass tunings.
- **Steady but responsive.** About 47 readings a second are smoothed with a median filter, note hysteresis, and a 1€ filter. A held note reads still, and a turning peg is followed without lag.
- **Instrument presets:** Chromatic; guitar (standard, drop D, half-step down, DADGAD, open G, open D); bass (4- and 5-string); ukulele (standard, low G, baritone); violin, viola, cello, mandolin; and banjo (open G). In a preset the tuner targets the nearest *string*, and each string you bring into tune keeps a small mark.
- **Custom tunings** of one to eight strings, built a semitone at a time. Each tuning is spelled with sharps or flats, so half-step down reads E♭ A♭ D♭ G♭ B♭ E♭.
- **Microtonal strings.** Any custom string can be moved off equal temperament by up to ±50¢, for just intonation, quarter-tones or a sweetened tuning. The tuner then reads "in tune" at that offset pitch.
- **Reference pitch** (A4) adjustable from 432 to 446 Hz.
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

## Project structure

```
app/src/main/java/com/chrischappelear/tuner/
├── audio/
│   ├── AudioRecorder.kt     # Microphone capture as a Flow of pitch readings
│   ├── CaptureProfile.kt    # Window, hop and pitch range: Standard, or Low for bass
│   ├── PitchDetector.kt     # McLeod Pitch Method
│   └── FFT.kt               # Iterative radix-2 FFT
├── tuning/
│   ├── Note.kt              # Notes, MIDI and frequency math, A4 calibration
│   ├── Tunings.kt           # Instrument presets and groups
│   ├── TuningCodec.kt       # Text form of custom tunings for DataStore
│   ├── TuningProcessor.kt   # Smoothing, hysteresis, lock, hold and fade
│   ├── OneEuroFilter.kt     # Adaptive low-pass for the cents reading
│   └── PitchHistory.kt      # Rolling cents trace
├── data/
│   └── SettingsRepository.kt  # DataStore: selected tuning, A4, custom tunings
├── ui/
│   ├── TunerScreen.kt       # Layout, haptics, previews
│   ├── components/          # TuningMeter, NoteDisplay, StringRow, CentsTrace, SettingsSheet, CustomTuningEditor, PermissionScreen
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

The unit tests synthesize signals with `SignalGen`: pure and plucked tones for every open string, bass strings down to B0, detuned notes, a missing fundamental, a strong second harmonic, and noise at 10 dB SNR. They assert accuracy in cents.

## License

This project is open source. See the [LICENSE](LICENSE) file for details.
