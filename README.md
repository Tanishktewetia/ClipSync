# ClipSync

Local-network clipboard sync for **Windows and Android**.

- PC → phone: automatic while connected and unlocked.
- Phone → PC: copy, then tap **Send clipboard now** in the notification or **Send to PC** in Quick Settings. Copying alone never sends.
- No account, cloud clipboard history, USB, Developer options, root, helper app, or keyboard switching.
- Mutual TLS 1.3, six-digit code-confirmed pairing, pinned identities, discovery and reconnect.

**Phase 7 / 0.7.0 is built for manual acceptance testing.** The APK is a debug-signed beta, not a public production release. The generated website is local, not deployed.

## First connection

1. Install the matching builds. Secure Android syncing requires Android 10+ with TLS 1.3; the target test phone is the Edge 20 Pro on Android 13.
2. Join the same private Wi-Fi, or connect the phone to the Windows PC hotspot.
3. Choose **Pair new device** in the Windows tray app, then **Find & pair my PC** on Android.
4. Compare all six digits, confirm only if they match, and wait for Connected.
5. Copy on Windows, then paste on the unlocked phone. In the other direction, copy on the phone and deliberately tap the notification action or tile.

A **Quick Settings tile** is a shortcut beside Wi-Fi/Bluetooth when you swipe down twice. Android's main screen and offline Guide explain how to add it. The notification action is an alternative; adding a tile is optional.

## Guides

- [Complete setup, tile, reconnect and privacy guide](site/docs.html) — view through the generated site for the rendered version.
- [Website build, preview and publishing](site/README.md).
- [Phase 7 report and exact no-USB tests](docs/phase-7-report.md).
- [Phase 7 wire extension](docs/phase-7-protocol.md).
- [Phase history](LOG.md), [plan](plan.md), [architecture](architecture.md). Historical design assumptions are not silently rewritten; reports document actual behavior.
- [Report an issue](https://github.com/Tanishktewetia/ClipSync/issues).

## Build locally

Current projects require .NET **10** SDK, JDK 17 and Android SDK platform 34. Node.js packages the optional website. No new application package dependencies were introduced in Phase 7.

From the repository root, use scripts/build-windows.ps1, scripts/run-windows.ps1 and scripts/build-android.ps1. Quit the existing Windows tray instance before launching a replacement; the launcher never kills it for you.

Build the matching APK, then run **scripts/build-windows-download.ps1** for the self-contained Windows x64 EXE. Run **node site/build.cjs**, then **node site/serve.cjs**. Preview at http://127.0.0.1:4177. The Windows EXE, APK and website are in ignored dist/. The Windows download is unsigned, portable and includes its .NET runtime; the Android APK remains debug-signed. Both real downloads and their SHA-256 checksums are listed on the page. Use Telegram or scripts/serve-apk.ps1 for no-USB sideloading. The website may also be deliberately served on private Wi-Fi using **node site/serve.cjs --lan**; stop that server when finished.

## Background and reconnect

Closing Android's screen leaves its enabled foreground service running. After reboot, unlock once. A force-stop in Android Settings requires reopening the app. While locked, only the latest incoming PC text waits in memory.

Newest **known** text is reconciled with logical counters and a deterministic device-ID tie-break. Phone text is known only after an explicit send tap. Pending text is memory-only and lost on process/service death or reboot. Disable **Apply latest text on reconnect** in Android Advanced or the Windows tray menu to preserve that side's clipboard when connecting.

Pause stops both directions. Stop background connection disables Android recovery. Images and hotspot management are not included. Sensitive flags hide Android preview contents but cannot guarantee the OS shows no indicator.

## Diagnostics

Android: Advanced → Diagnostics → Share logs / Save to Downloads. Windows: Open Log Folder in the tray menu. Include versions, numbered test step, network mode and expected/actual behavior. Never send confidential clipboard text. Logs contain status, lengths and short hash prefixes only.

## Website walkthrough

The preview loops through automatic PC → phone copying, one-time Quick Settings tile setup, then copying on Android and tapping Send to PC before phone → Windows transfer. Chapter buttons let you jump to any part; Pause stops motion, and reduced-motion users get static steps. The synthetic demonstration never accesses your real clipboard.
