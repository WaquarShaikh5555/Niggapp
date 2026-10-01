Bundled audio tracks for SmileBeat.

This folder contains royalty-free placeholder tracks generated synthetically for development:

- phonk_vibe_01.wav : 12s synthetic dark phonk-inspired loop (808 + pad + bell) - original, royalty-free
- midnight_phonk.wav : 10s alternative vibe

Replace these with your own licensed/royalty-free Miguel-inspired dark R&B/phonk tracks.
Supported formats: .wav, .mp3, .ogg, .m4a placed in res/raw.

The app dynamically lists all audio files in this folder via reflection over R.raw.
To add a new track, just drop the file here and rebuild - it will appear in Settings > Track Picker.

IMPORTANT:
- Do not bundle copyrighted music unless you have distribution rights.
- Keep files under ~5MB each for APK size.
- Prefer 44100Hz, stereo or mono.

Track picker displays filename (without extension) as title.
