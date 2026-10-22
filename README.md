# HypnoBloom v0.1

Native Kotlin Android OpenGL ES 2.0 live wallpaper with continuously animated checkerboard rings and optional music responsiveness.

## Requirements
- Android Studio, JDK 17, Android SDK 35
- Android 8.0+ device with OpenGL ES 2.0

## Build
Open the project in Android Studio, sync Gradle, run the `app` module, optionally grant RECORD_AUDIO, and select Preview / set wallpaper.

The Gradle wrapper is not yet included; use a local Gradle 8.9 installation or generate the wrapper.

## Source files
- `MainActivity.kt`: setup screen and permission flow
- `HypnoWallpaperService.kt`: wallpaper engine / EGL rendering
- `BloomRenderer.kt`: fragment shader animation
- `AudioAnalyzer.kt`: optional Android Visualizer input

Not yet build-tested on an Android device.
