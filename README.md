# SmileBeat - Android App (Kotlin + Jetpack Compose)

SmileBeat is a fun, lightweight camera app that analyzes the user's face **on-device** and detects the approximate visible facial skin-tone brightness under current lighting. When the detected tone crosses a configurable darker-tone threshold for 5 consecutive frames, it triggers a bundled phonk track.

> **Privacy & Ethics**: This app is designed as a visual/vibe experiment, NOT as a system for identifying, categorizing, or making judgments about people based on race or ethnicity. It does NOT infer ethnicity, race, identity, or any sensitive personal attribute. Only analyzes apparent pixel/skin-tone characteristics visible in the current camera frame. All processing is 100% on-device.

## Features

### 1. Camera Permission
- Runtime CAMERA permission with clear rationale screen
- Friendly denied state + button to open system settings
- Never accesses camera without permission

### 2. Live Camera Preview (CameraX)
- Front camera default, flip button for rear
- "Camera active" indicator
- Stops processing when app goes background
- Rounded preview with neon glowing borders (dark theme)

### 3. On-Device Face & Tone Detection (ML Kit)
- Google ML Kit Face Detection to locate face bounding box
- Ignores frames with no face
- Crops central facial region (25-75% width, 35-70% height) to avoid eyes/lips/hair/background
- Robust color analysis using HSV + normalized luminance (ITU BT.709)
- Filters low-saturation glare, near-black shadows
- Median + mean blend for robust tone score 0..1 (0=dark appearance, 1=bright)
- No frame saving, no upload, no embeddings, no identity recognition

### 4. Darker-Tone Trigger
- Configurable threshold (0.05-0.95, default 0.45)
- Logic: `toneScore <= threshold` → darker-tone state
- Requires 5 consecutive valid frames to prevent flicker
- Fade out when condition ends, configurable cooldown (default 3s, 1-15s)

### 5. Audio (Media3 ExoPlayer)
- Bundled tracks in `res/raw` (placeholder royalty-free synthetic phonk loops)
- Default: `phonk_vibe_01.wav` + `midnight_phonk.wav`
- Fade in 800ms, fade out 600ms
- Audio focus handling (duck/pause on loss)
- Respects system volume, never forces max

### 6. Volume
- In-app slider 0-100%, default 70%
- Controls player volume only

### 7. Settings (DataStore)
- Detection mode: Darker visible tone / Experimental inverted
- Tone sensitivity slider
- Volume slider
- Cooldown duration
- Track picker (lists all `res/raw` via reflection)
- Camera preview on/off toggle (detection stays active)
- Persisted via Jetpack DataStore Preferences

## Architecture (MVVM + StateFlow)

```
MainActivity
├── MainViewModel (StateFlow UI state)
│   ├── CameraController (CameraX, lifecycle, frame provider)
│   ├── FaceDetectorWrapper (ML Kit)
│   ├── SkinToneAnalyzer (HSV/luma median)
│   ├── ToneTriggerController (5 frames + cooldown)
│   ├── AudioPlayerManager (ExoPlayer, fade, audio focus)
│   └── SettingsRepository (DataStore)
└── SettingsViewModel
```

- `CameraController`: Configure CameraX, front/rear, lifecycle, provide frames, stop/release correctly
- `FaceDetectorWrapper`: ML Kit face detection, returns sorted largest first
- `SkinToneAnalyzer`: Samples central region, filters non-skin, calculates normalized tone score, never persists
- `ToneTriggerController`: Tracks consecutive frames, cooldown, state transitions
- `AudioPlayerManager`: Load bundled track, playback, fade, audio focus, release
- `SettingsRepository`: DataStore for volume, threshold, cooldown, track, mode, preview, mute

## UI - Dark Neon Aesthetic

- Background: #0A0A0F black void
- Neon accents: Purple #B026FF, Red #FF2A6D, Cyan #05FFA1
- Large rounded camera preview, glowing borders
- Circular Tone Meter with threshold indicator, pulsing animation when VIBING
- States: Searching, Face detected, Analyzing, Normal, Dark-tone trigger, Cooldown, Poor lighting, Multiple faces
- Music Status: VIBE READY / VIBING / COOLDOWN with pulse overlay
- Controls: Flip camera, Settings, Mute/unmute
- Privacy note always visible

## Project Structure

```
settings.gradle.kts
build.gradle.kts
gradle.properties
app/
  build.gradle.kts
  src/main/
    AndroidManifest.xml
    java/com/smilebeat/
      MainActivity.kt
      camera/CameraController.kt
      detection/FaceDetectorWrapper.kt
      detection/SkinToneAnalyzer.kt
      detection/ToneTriggerController.kt
      audio/AudioPlayerManager.kt
      data/SettingsRepository.kt
      viewmodel/MainViewModel.kt
      viewmodel/SettingsViewModel.kt
      ui/theme/
      ui/components/
      ui/screens/
    res/
      values/strings.xml, colors.xml, themes.xml
      drawable/ic_launcher.xml
      raw/phonk_vibe_01.wav (synthetic placeholder)
      raw/midnight_phonk.wav
      xml/backup_rules.xml
```

## Build Requirements

- Android Studio Hedgehog or newer
- minSdk 26, targetSdk 34, compileSdk 34
- Kotlin 1.9.22, Compose BOM 2024.06.00, AGP 8.5.2
- Gradle 8.7

### Steps

1. Open project in Android Studio
2. If `gradle-wrapper.jar` missing (network restricted), let Android Studio generate wrapper or run `gradle wrapper` locally
3. Sync Gradle
4. Replace placeholder tracks in `app/src/main/res/raw/` with your own licensed royalty-free phonk tracks (keep filenames or update SettingsRepository default)
5. Run on device (emulator camera may need virtual scene)

### Audio Placeholder

`phonk_vibe_01.wav` and `midnight_phonk.wav` are **original synthetic** audio generated via Python (808 kick + dark pad + bell) for development. They are royalty-free. Replace with your own Miguel-inspired dark R&B/phonk atmosphere tracks. Do NOT bundle copyrighted music without rights.

Track picker uses reflection over `R.raw` fields to list all audio files.

## Privacy Guarantees (in-app + code)

- All face detection & tone analysis 100% on-device
- No camera frames saved
- No face images stored
- No upload
- No facial embeddings
- No recognition / identity matching
- No analytics based on facial characteristics
- No race/ethnicity classification
- Camera processing stops in background
- Clearly shows "Camera active" when running

In-app text: *"Your camera stays on your device. SmileBeat analyzes the current camera image locally and does not save or upload your face."*

## Performance

- Analysis throttled to ~150ms interval (6-7 fps) vs 30fps preview
- Drops stale frames when busy (`STRATEGY_KEEP_ONLY_LATEST`)
- Off main thread via Executor + coroutines
- No unnecessary bitmap allocations, reuses YUV->JPEG path, recycles bitmaps immediately
- ML Kit FAST mode, no landmarks/classification

## Error Handling

- Camera permission denied → rationale + settings button
- Camera unavailable → error message
- No face → IDLE / Searching state
- Multiple faces → uses largest, shows chip "Multiple faces — analyzing largest"
- Poor lighting → detection confidence penalty, UI shows "Poor lighting"
- Face partial outside frame → clamped rect
- ML Kit init failure → Result.failure handling
- Audio playback failure → ERROR state with message
- Missing track → logs error, shows in UI
- Audio focus loss → pause/duck
- Camera switching failure → try/catch + error message

## Lifecycle

- Start camera only after permission granted
- Bind CameraX to lifecycle
- Stop image analysis in ON_PAUSE
- Stop/release in onStop()
- Release audio player in ViewModel.onCleared()
- Avoid leaks, avoid keeping frames alive

## License / Audio

- Code: MIT (or your choice)
- Bundled placeholder wavs: Original synthetic, royalty-free, generated for this project
- Replace with your licensed tracks for production

## Future Experimental Modes

Settings includes `DetectionMode.EXPERIMENTAL_INVERTED` placeholder for testing brighter-tone trigger. Can be extended.

---

Built as a complete compilable Android Studio project in under 35 minutes concept.
