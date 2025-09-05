# Chromatic Tuner

A modern Android chromatic tuner app built with Jetpack Compose that provides real-time instrument tuning for guitar, ukulele, and other instruments.

## Features

- **Real-time audio analysis** using FFT (Fast Fourier Transform)
- **Automatic note detection** with chromatic tuning support
- **Visual tuning meter** with precise cents offset display
- **Volume threshold filtering** to ignore background noise
- **Material 3 design** with dark/light theme support
- **Portrait and landscape** orientation support
- **Always listening** - no need to tap start/stop buttons

## Technical Details

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM with reactive state management
- **Audio Processing**: Custom FFT implementation with parabolic interpolation
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34

## Audio Processing Pipeline

1. **Microphone Capture** - 44.1kHz sample rate with 4096 sample buffer
2. **Volume Threshold** - Filters out background noise (RMS amplitude < 0.005)
3. **FFT Analysis** - Real-time frequency domain conversion
4. **Peak Detection** - Identifies fundamental frequency with parabolic interpolation
5. **Note Mapping** - Converts frequency to musical note using equal temperament tuning
6. **Cents Calculation** - Displays tuning accuracy in cents (±50 cent range)

## Project Structure

```
app/src/main/java/com/chrischappelear/tuner/
├── audio/
│   ├── AudioRecorder.kt          # Microphone capture and FFT processing
│   └── FFT.kt                    # Fast Fourier Transform implementation
├── tuning/
│   ├── Note.kt                   # Musical note calculations and MIDI conversion
│   └── TunerEngine.kt           # Core tuning logic and state management
├── ui/
│   ├── TunerScreen.kt           # Main Compose UI with visual meter
│   └── theme/
│       └── Theme.kt             # Material 3 theme configuration
├── MainActivity.kt               # Permission handling and app setup
└── TunerViewModel.kt            # State management between UI and engine
```

## Building the Project

1. **Prerequisites**:
   - Android Studio Hedgehog (2023.1.1) or newer
   - Kotlin 2.0.20
   - Gradle 8.13

2. **Build Commands**:
   ```bash
   ./gradlew build                # Build the project
   ./gradlew assembleDebug        # Build debug APK
   ./gradlew installDebug         # Install on connected device
   ```

3. **Testing**:
   ```bash
   ./gradlew test                 # Run unit tests
   ./gradlew connectedAndroidTest # Run instrumented tests
   ```

## Permissions

The app requires microphone permission (`RECORD_AUDIO`) to function. Permission is requested at runtime with a user-friendly explanation.

## Usage

1. Open the app
2. Grant microphone permission when prompted
3. The app automatically starts listening
4. Play your instrument near the device
5. View the detected note and tuning accuracy on the visual meter
6. Green indication = in tune, red = sharp, blue = flat

## License

This project is open source. See the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.