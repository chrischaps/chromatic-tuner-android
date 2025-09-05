# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android chromatic tuner app built with Kotlin and Jetpack Compose that uses device microphone input to detect musical notes and provide tuning feedback. The app targets instruments like guitar and ukulele with real-time frequency analysis and visual feedback.

## Build Configuration

- **Package**: `com.chrischappelear.tuner`
- **Min SDK**: 26 (Android 8.0) - Required for adaptive icons and modern audio APIs
- **Target SDK**: 34
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

The app follows MVVM architecture with reactive state management using Kotlin coroutines and flows:

### Core Modules

**Audio Processing (`audio/`)**
- `AudioRecorder`: Captures microphone input, performs real-time FFT analysis
- `FFT`: Custom Fast Fourier Transform implementation for frequency detection
- Uses 44.1kHz sample rate with 4096 sample buffer size

**Tuning Logic (`tuning/`)**
- `TunerEngine`: Orchestrates audio processing and note detection
- `Note`: Musical note calculations with MIDI number conversions
- `TuningResult`: Data class representing current tuning state

**UI Layer (`ui/`)**
- `TunerScreen`: Main Compose UI with visual tuning meter
- `TunerViewModel`: State management between UI and tuning engine
- `MainActivity`: Permission handling and Compose setup

### Data Flow Architecture

```
AudioRecorder → TunerEngine → TunerViewModel → TunerScreen
     ↓              ↓              ↓             ↓
   FFT Analysis → Note Detection → State Flow → UI Updates
```

### Key Technical Details

**Audio Processing Pipeline:**
1. Microphone capture via `AudioRecord`
2. Real-time FFT analysis with parabolic interpolation for precise frequency detection
3. Note detection using equal temperament tuning (A4 = 440Hz)
4. Cents offset calculation for tuning accuracy

**Compose Integration:**
- Uses Kotlin 2.0 Compose Compiler plugin
- Material 3 design system with AppCompat themes
- Custom Canvas drawing for tuning meter visualization
- Reactive UI updates via StateFlow

**Permission Requirements:**
- `RECORD_AUDIO`: Required for microphone access
- Runtime permission handling with proper fallback UI

## Development Notes

**Math Functions**: The project uses imported Kotlin math functions (`log2`, `exp`, `ln`, etc.) rather than fully qualified names. Avoid using `pow` function - use `exp(x * ln(base))` pattern instead.

**Compose Imports**: Use explicit imports for layout functions rather than wildcard imports to avoid compilation issues with `weight` and other modifiers.

**Audio Processing**: Frequency detection operates in 70-1200 Hz range optimized for guitar/ukulele. Uses minimum amplitude threshold of 0.001 to filter noise.