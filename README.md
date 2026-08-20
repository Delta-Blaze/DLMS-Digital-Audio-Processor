# DLMS Digital Audio Processor

Production-oriented Android audio processor scaffold implementing a single DSP chain for microphone and decoded audio-file input, with Compose UI, preset catalog, lifecycle-safe engine ownership, diagnostics, persistence, and tests.

## Build

Use Android Studio with JDK 17 and Gradle 9.4.1. The project targets compileSdk/targetSdk 37 and AGP 9.2.0. The current Compose setup follows Android's August 2026 guidance: Kotlin/Compose compiler plugin 2.3.21 and Compose BOM 2026.08.00.

Open the project root in Android Studio, sync Gradle, then run the `app` configuration on an Android device/emulator. Runtime audio validation requires an actual Android audio stack.

## Runtime architecture

`Compose UI -> ViewModel -> AudioEngine -> EffectRack -> AudioTrack`

Microphone uses `AudioRecord`. Files use `MediaExtractor + MediaCodec` to decode the selected audio stream into PCM before the same DSP chain. No UI element directly owns an audio node.

## Important verification boundary

This environment has no Android SDK/emulator, so device-level audio I/O, codec availability, audio focus, Bluetooth transitions, underrun behavior and lifecycle hardware events are marked **NOT VERIFIED** in QA rather than reported as PASS.

## Gradle Wrapper

This project includes `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` so GitHub Actions can invoke Gradle from the repository root.

The wrapper is configured for Gradle 9.4.1 in `gradle/wrapper/gradle-wrapper.properties`.
