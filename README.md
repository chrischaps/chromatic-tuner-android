# Chromatic Tuner

A calm, accurate Android tuner for guitar, ukulele, and anything else that holds a pitch, built with Kotlin and Jetpack Compose.

## Features

- **Accurate to about a cent** from C2 (65 Hz) to 1400 Hz, using the McLeod Pitch Method.
- **Steady but responsive.** About 47 readings a second are smoothed with a median filter, note hysteresis, and a 1€ filter. A held note reads still, and a turning peg is followed without lag.
- **Instrument presets:** Chromatic, Guitar Standard, Drop D, and Ukulele (GCEA). In a preset the tuner targets the nearest *string*, and each string you bring into tune keeps a small mark.
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

1. **Capture** (`audio/AudioRecorder.kt`) is a cold `Flow`. The `AudioRecord` opens when collection starts and is released when it stops, on the same worker thread. It reads float PCM at 48 kHz (44.1 kHz fallback) from the `UNPROCESSED` source where supported, otherwise `VOICE_RECOGNITION`, so no automatic gain or noise suppression is applied. It keeps a 4096-sample sliding window advanced 1024 samples at a time.
2. **Detection** (`audio/PitchDetector.kt`) runs the McLeod Pitch Method: DC removal, a 2 kHz low-pass, an FFT autocorrelation, and the normalized square difference function. It picks the first key maximum within 90% of the tallest, then refines it with parabolic interpolation. Working in lag rather than frequency bins keeps resolution fine for low strings, and the first-peak rule prevents octave errors.
3. **Interpretation** (`tuning/TuningProcessor.kt`) turns noisy per-frame readings into a steady state:
   - It gates frames on clarity and level.
   - It requires a steady onset and rejects murky stray frames.
   - It holds a note through brief dropouts and lets it fade over 1.5 s.
   - It detects the "locked in tune" moment.

## Project structure

```
app/src/main/java/com/chrischappelear/tuner/
├── audio/
│   ├── AudioRecorder.kt     # Microphone capture as a Flow of pitch readings
│   ├── PitchDetector.kt     # McLeod Pitch Method
│   └── FFT.kt               # Iterative radix-2 FFT
├── tuning/
│   ├── Note.kt              # Notes, MIDI and frequency math, A4 calibration
│   ├── Tunings.kt           # Instrument presets
│   ├── TuningProcessor.kt   # Smoothing, hysteresis, lock, hold and fade
│   ├── OneEuroFilter.kt     # Adaptive low-pass for the cents reading
│   └── PitchHistory.kt      # Rolling cents trace
├── data/
│   └── SettingsRepository.kt  # DataStore: selected tuning and A4
├── ui/
│   ├── TunerScreen.kt       # Layout, haptics, previews
│   ├── components/          # TuningMeter, NoteDisplay, StringRow, CentsTrace, SettingsSheet, PermissionScreen
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

The unit tests synthesize signals with `SignalGen`: pure and plucked tones for every open string, detuned notes, a missing fundamental, a strong second harmonic, and noise at 10 dB SNR. They assert accuracy in cents.

## License

This project is open source. See the [LICENSE](LICENSE) file for details.
