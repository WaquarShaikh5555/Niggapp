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

Download from **Releases** on your phone:

- **`UNDO-Lite-N.apk` — start here.** It installs straight from your browser. It leaves out notification access, so it can't detect payments, renewals or swiped-away notifications automatically. Deleted-media restore, setting restore, uninstalled apps and every recovery guide still work.
- **`UNDO-N.apk` — full edition.** It declares a notification listener. In some regions, including India, Google Play Protect's *enhanced fraud protection* blocks any APK downloaded from a browser, chat app or file manager if it declares notification, SMS or accessibility access. This is because scam apps abuse them to steal OTPs. Play Protect has no "install anyway" button for this. Your options:
  1. Install it from a computer: `adb install UNDO-N.apk`. Play Protect's check targets downloads from browsers, chat apps and file managers, so a USB install should get through.
  2. Turn off "Scan apps with Play Protect" in Play Store → profile → Play Protect → ⚙, install, then **turn it straight back on**. Only do this for an APK you built yourself or trust.
  3. Wait for a Play Store listing. Play Store installs aren't affected.

  On Android 13+, a sideloaded full edition also needs **App info → ⋮ → Allow restricted settings** before notification access can be switched on.

Both editions share an app ID and signing key, so you can install one over the other and keep your history.

## Build

```bash
./gradlew :app:testFullReleaseUnitTest :app:assembleFullRelease :app:assembleLiteRelease   # JDK 17, Android SDK 34
```

CI (`.github/workflows/build-apk.yml`) runs the engine unit tests and builds a minified release APK.

## Architecture

- `engine/`: pure Kotlin with no Android imports, unit-tested. Covers event models, the payment parser, classifier, priority ranking and playbooks (the recovery text and actions).
- `capture/`: the legitimate Android signals. Notification listener, MediaStore trash, settings observers, package changes and optional usage stats.
- `data/`: local SQLite and preferences.
- `ui/`: Jetpack Compose with a bespoke design system (Space Grotesk, ink/paper/coral palette, light and dark).

Font: Space Grotesk, SIL Open Font License 1.1.
