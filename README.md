# UNDO — Ctrl+Z for your phone

> UNDO either fixes what you just did, or tells you exactly what you can do next.

An honest, private Android app for recent mistakes. Every event answers four questions:
**What happened? When? Can it actually be undone? If not, what's the best recovery?**

| Situation | What UNDO really does |
|---|---|
| Deleted photo / video | Restores it from Android's system trash (Android 11+, with Android's own confirmation, verified afterwards). Otherwise points you to your gallery's bin. |
| Wrong message or photo | Opens the chat and shows the app's exact unsend path and time limit. UNDO never deletes messages and never claims to. |
| Payment | Shows amount, time, payee and reference, plus the real recovery path (ask payee → bank/app dispute → NPCI → RBI Ombudsman; 1930 for fraud in India). It cannot reverse payments. |
| Swiped-away notification | Shows its text and reopens it where the original link is still valid; links to Android's notification history. |
| Subscription / renewal | Warns before it renews; opens Google Play subscriptions or the app. |
| Changed setting | Switches rotation, brightness mode, timeout, ringer and DND back (with permission); opens the right Settings screen otherwise. |
| Uninstalled app | Reinstall link to the Play Store plus data-recovery tips. |
| Anything else | Explains the limitation and gives the closest recovery path. |

"Help me fix it" gives a step checklist and editable message templates (friendly or formal). You copy, share or send them yourself; UNDO never sends anything.

## Privacy

- **No `INTERNET` permission.** Nothing can leave the phone.
- No account, no analytics, no ads. Excluded from cloud backup and device transfer.
- Short-term history only (1 hour to 7 days, default 24 hours). "Delete everything" is in Settings.
- One-time codes and promotions are never stored. Recents preview is hidden; optional screenshot blocking.
- Each permission is optional, asked for only when you need it, and explained first. The app works without any of them.
- No screen recording, no keystroke logging, no accessibility service.

## Install

1. Open the **Actions** tab, pick the latest **Build APK** run, and download the `undo-apk` artifact. Tagged runs also publish a GitHub Release.
2. On your phone, open the APK and allow "install unknown apps" for your browser or file manager.
3. On Android 13+, sideloaded apps need one extra step before notification access can be turned on: **App info → ⋮ → Allow restricted settings**. UNDO walks you through it.

## Build

```bash
./gradlew :app:testReleaseUnitTest :app:assembleRelease   # JDK 17, Android SDK 34
```

CI (`.github/workflows/build-apk.yml`) runs the engine unit tests and builds a minified release APK.

## Architecture

- `engine/`: pure Kotlin with no Android imports, unit-tested. Covers event models, the payment parser, classifier, priority ranking and playbooks (the recovery text and actions).
- `capture/`: the legitimate Android signals. Notification listener, MediaStore trash, settings observers, package changes and optional usage stats.
- `data/`: local SQLite and preferences.
- `ui/`: Jetpack Compose with a bespoke design system (Space Grotesk, ink/paper/coral palette, light and dark).

Font: Space Grotesk, SIL Open Font License 1.1.
