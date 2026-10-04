# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android chromatic tuner app built with Kotlin and Jetpack Compose that uses device microphone input to detect musical notes and provide tuning feedback. The app targets instruments like guitar and ukulele with real-time frequency analysis and visual feedback.

## Build Configuration

- **Package**: `com.chrischappelear.tuner`
- **Min SDK**: 26 (Android 8.0) - Required for adaptive icons and modern audio APIs
- **Target SDK**: 36 (Play requires 36 for new apps and updates since Aug 31 2026)
- **Compile SDK**: 36
- **Kotlin**: 2.0.20 with Compose Compiler plugin
- **Gradle**: 8.13

## Common Commands

Navigate to the project root directory first:

**Build Commands:**
```bash
./gradlew build                    # Build the entire project
./gradlew app:assembleDebug        # Build debug APK
./gradlew app:assembleRelease      # Build release APK
./gradlew app:bundleRelease        # Build the signed AAB for Play
./gradlew clean                    # Clean build artifacts
```

**Testing:**
```bash
./gradlew test                     # Run unit tests
./gradlew app:connectedAndroidTest # Run instrumented tests on connected device
```

**Installation:**
```bash
./gradlew app:installDebug         # Install debug APK on connected device
```

**Linting:**
```bash
./gradlew app:lint                 # Run Android lint checks
```

## Architecture Overview

MVVM with a reactive pipeline; see README.md for the full walkthrough.

```
AudioRecorder → PitchDetector → TuningProcessor → TunerViewModel → TunerScreen
```

- **`audio/`**: `AudioRecorder.frames(profile)` is a cold `Flow<PitchEstimate?>`. The mic opens on collect and is released on cancel, on one worker thread. Float PCM at 48 kHz (44.1 kHz fallback), `UNPROCESSED`/`VOICE_RECOGNITION` source. `CaptureProfile` sets the window, hop and range: `Standard` is 4096 / 1024 / 60–1400 Hz, and `Low` is 8192 / 2048 / 28–1400 Hz for tunings whose lowest string is ≤ B1. Every profile keeps window/hop = 4, which the processor's frame counts rely on. `PitchDetector` is the McLeod Pitch Method (NSDF via FFT autocorrelation, 2 kHz low-pass, first key max ≥ 0.9 × highest, parabolic interpolation). `FFT` is an in-place radix-2 transform on primitive arrays. `PluckVoice` is the Karplus-Strong reference tone: pure, with an allpass that trims the loop to the exact period, and tests that run it through `PitchDetector`. `TonePlayer` streams it through one `AudioTrack`, and its `isGating(now)` makes the ViewModel pass `null` frames to the processor while a tone plays and for 400 ms after.
- **`tuning/`**: `TuningProcessor` is pure and synchronous; call `process(estimate, nowMs)` once per frame. Pipeline:
  - Clarity and RMS gate.
  - Steady-onset check, then a 5-frame median.
  - Stray-frame rejection.
  - Octave guard: a jump of an octave is held for 6 frames, as a transient. In a preset, an octave jump with no fresh attack (no 1.5× level rise in the last 4 frames) is folded back onto the string. Phone mics barely hear a low string's fundamental, so a ringing low E can read as E3 for a second.
  - Note hysteresis (65¢, 3 frames).
  - `OneEuroFilter` on cents.
  - Lock at ±4¢ held for 400 ms.
  - Dropout hold, then a 1.5 s fade.

  The tunable constants are in its companion object. `Note` is identified by MIDI number; `Note.spelled(flats)` gives the written name, and each `Tuning` chooses sharps or flats. `NoteMath` takes an `a4` parameter. `Tunings` holds the presets in `TuningGroup`s. A `Tuning`'s strings are `TuningString`s: a `Note` plus a microtonal `cents` offset (±50, custom tunings only), whose fractional `midi` is the pitch tuned to. With strings, the target is the nearest string, and `TunerState.stringCents` carries the offset to the UI. Custom tunings are `TuningGroup.Custom`, stored as text via `TuningCodec`.
- **`data/`**: `SettingsRepository` (DataStore) holds the selected tuning, A4 (432–446) and custom tunings. The app is pre-production, so ids and keys can change without migrations.
- **`ui/`**: `TunerScreen` lays out the components in `ui/components/`. Tapping a `StringRow` pill plucks that string. In Chromatic, a `ReferencePicker` takes its place. While a tone rings, the dial shows the reference note instead of the reading. Colors come from `TunerTheme.colors` (`TunerColors`, dusk/paper) rather than raw `Color` values; `TunerColors.forCents()` is the sage→amber ramp. `@Preview`s live at the bottom of `TunerScreen.kt`.
- **Lifecycle**: the ViewModel uses `stateIn(WhileSubscribed(2000))`, and `MainActivity` collects with `collectAsStateWithLifecycle`, so the mic is held only while the screen is visible and survives rotation. The capture profile comes from the repository flow, so a change between bass and other tunings reopens the mic.

## Release

The store name is **Chaps Tuner** (the "Chaps" prefix is shared across Chris's published apps, with developer name `chaps.dev`). Release builds run R8 with resource shrinking. They are signed with the upload key at `~/.keystores/tuner-upload.jks`, whose path and passwords come from the `TUNER_UPLOAD_*` properties in `~/.gradle/gradle.properties`. Without those, the release build is unsigned, so never commit them. Play listing text, images and per-version changelogs live in `fastlane/metadata/android/en-US/`, and `fastlane/PLAY_CONSOLE.md` holds the Console answers and the release path. Bump `versionCode` and add `changelogs/<versionCode>.txt` for each upload. The privacy policy is `src/pages/tuner/privacy.astro` in the chaps-dev site.

## Development Notes

**Math Functions**: The project uses imported Kotlin math functions (`log2`, `exp`, `ln`, etc.) rather than fully qualified names. Avoid using `pow` function - use `exp(x * ln(base))` pattern instead.

**Compose Imports**: Use explicit imports for layout functions rather than wildcard imports to avoid compilation issues with `weight` and other modifiers.

**Testing detection changes**: Unit tests in `app/src/test` use `SignalGen` to synthesize tones and assert accuracy in cents. Run `./gradlew test` after any change to the detector or processor.

**Acoustic testing**: a WAV played from the PC speakers into the device mic is a good end-to-end check. Clarity in a real room is much lower than with synthetic signals (often 0.5–0.9), which is why the gates are where they are.
