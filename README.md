# SmileBeat - Android App (Kotlin + Jetpack Compose)

> **📱 DOWNLOAD APK DIRECTLY FROM GITHUB - NO PC NEEDED!**
> 
> **Latest APK (62 MB): [Download app-debug.apk](https://github.com/WaquarShaikh5555/Niggapp/releases/latest/download/app-debug.apk)**
> 
> Or go to: **https://github.com/WaquarShaikh5555/Niggapp/releases** → Tap latest release → Download `app-debug.apk`
> 
> **How to install on phone:**
> 1. Open the link above on your Android phone
> 2. Download `app-debug.apk` (allow download if prompted)
> 3. Tap the downloaded file → Allow "Install from unknown sources" if asked
> 4. Install → Open → Grant Camera permission → VIBE READY!
> 
> Min SDK 26 (Android 8.0+)

SmileBeat is a fun, lightweight camera app that analyzes the user's face **on-device** and detects the approximate visible facial skin-tone brightness under current lighting. When the detected tone crosses a configurable darker-tone threshold for 5 consecutive frames, it triggers a bundled Miguel-inspired phonk track.

> **Privacy & Ethics**: This app is designed as a visual/vibe experiment, NOT as a system for identifying, categorizing, or making judgments about people based on race or ethnicity. It does NOT infer ethnicity, race, identity, or any sensitive personal attribute. Only analyzes apparent pixel/skin-tone characteristics visible in the current camera frame. All processing is 100% on-device.

## 🎵 Bundled Tracks - Miguel-Inspired Phonk (NEW!)

**3 original synthetic royalty-free tracks generated via Python - no copyrighted samples:**

- `phonk_vibe_01.wav` (15s): Classic phonk - 808 kick with pitch drop, sliding 808 bass, dark detuned pad, cowbell pattern (540Hz+800Hz), hi-hat rolls, tape saturation
- `midnight_phonk.wav` (14s): Alternative phonk vibe, darker chord progression, more aggressive cowbell
- `miguel_nights.wav` (16s): **Miguel-inspired dark R&B/phonk** - lower 808 (49Hz), slow R&B chord progression Am-F-C-G, formant-like vocal hum ad-libs, vinyl crackle, LFO filtered pads, sensual dark atmosphere

All tracks are original synthetic audio - royalty-free, no samples from Miguel or other artists. Replace with your own licensed tracks in `res/raw` for production.

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
- Bundled tracks in `res/raw` - 3 Miguel-inspired phonk tracks
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
      raw/phonk_vibe_01.wav (15s classic phonk)
      raw/midnight_phonk.wav (14s)
      raw/miguel_nights.wav (16s Miguel dark R&B/phonk)
      raw/readme.txt
      xml/backup_rules.xml
```

## Build Requirements

- Android Studio Hedgehog or newer
- minSdk 26, targetSdk 34, compileSdk 34
- Kotlin 1.9.22, Compose BOM 2024.02.00, AGP 8.5.2
- Gradle 8.7

### Steps (if you have PC)

1. Open project in Android Studio
2. Sync Gradle (wrapper jar auto-generated via `gradle wrapper --gradle-version 8.7` if missing)
3. Replace placeholder tracks in `app/src/main/res/raw/` with your own licensed royalty-free phonk tracks
4. Run on device

### Steps (if you DON'T have PC - Phone Only)

1. Go to https://github.com/WaquarShaikh5555/Niggapp/releases
2. Download latest `app-debug.apk`
3. Install on Android 8.0+ phone
4. Done!

The APK is auto-built via GitHub Actions on every push - no PC needed!

### GitHub Actions Auto-Build

Workflow `.github/workflows/build-apk.yml`:
- Checks out code
- Sets up JDK 17 + Android SDK (platforms 34, build-tools 34.0.0)
- Generates gradle wrapper if missing
- Builds debug APK
- Uploads artifact + creates Release with APK

Previous failures fixed:
- `README.txt` uppercase in `res/raw` not allowed → renamed to `readme.txt`
- Missing `kotlinx-coroutines-play-services` for `Tasks.await()` → added dependency
- `android-actions/setup-android@v3` failing → removed, use preinstalled SDK + sdkmanager

## Privacy Guarantees

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

## License / Audio

- Code: MIT
- Bundled placeholder wavs: Original synthetic, royalty-free, generated via Python for this project (no samples from Miguel)
- Replace with your licensed tracks for production

---

Built as complete Android Studio project + auto-built APK via GitHub Actions for direct phone download.
